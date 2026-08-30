/**
 * Media Shelf Worker
 *
 * Routes:
 *   GET    /items?kind=book|movie|tv|game
 *   POST   /items                       body: Item
 *   PATCH  /items/:id                   body: partial Item
 *   POST   /items/:id/refresh           re-runs source enrichment on an existing row
 *   GET    /items/:id/covers            -> { covers: [{url,label}, ...] }
 *   DELETE /items/:id
 *   GET    /search/books?q=...          (or ?isbn=...)
 *   GET    /search/movies?q=...         (or ?id=tmdb_id)
 *   GET    /search/tv?q=...             (or ?id=tmdb_id)
 *   GET    /search/games?q=...          (or ?slug=rawg_slug)
 *   POST   /identify                    body: { image: base64-JPEG }  -> Claude Haiku vision
 *   GET    /k                           public read-only HTML view of all shelves
 *   GET    /health
 *
 * Auth: every request must send `Authorization: Bearer <SHELF_TOKEN>`. The /k namespace
 * and /health are the exceptions; both are intentionally public.
 */

export interface Env {
  DB: D1Database;
  SHELF_TOKEN: string;
  TMDB_API_KEY: string;
  RAWG_API_KEY: string;
  ANTHROPIC_API_KEY: string;
  STEAMGRIDDB_API_KEY?: string; // optional: when set, augments game covers with SteamGridDB box art
  IGDB_CLIENT_ID?: string;      // optional: when both set, game search augments RAWG with IGDB
  IGDB_CLIENT_SECRET?: string;
  PLEX_WEBHOOK_SECRET?: string; // optional: dedicated secret for the Plex webhook URL (falls back to SHELF_TOKEN)
  GOOGLE_CLIENT_ID?: string;    // optional: when set, requests may authenticate with a Google ID token (aud = this)
  OWNER_EMAIL?: string;         // optional: the Google account that claims the pre-auth '__legacy__' library
  SESSION_SECRET?: string;      // optional: HMAC key for app session tokens (falls back to SHELF_TOKEN)
  GOOGLE_BOOKS_API_KEY?: string; // optional: lifts Google Books off the shared anonymous quota
}

/** Bucket that owns all pre-multi-user rows until the owner claims them on first Google sign-in. */
const LEGACY_USER = "__legacy__";

type Kind = "book" | "movie" | "tv" | "game";

interface Item {
  id: string;
  user_id?: string | null;
  kind: Kind;
  title: string;
  subtitle?: string | null;
  year?: number | null;
  cover_url?: string | null;
  external_id?: string | null;
  external_src?: string | null;
  description?: string | null;
  rating?: number | null;
  status?: string; // CSV: any of 'owned','seen','wishlist'
  notes?: string | null;
  user_platform?: string | null; // CSV: any of 'pc','xbox','playstation','nintendo','mobile' (games)
  consoles?: string | null;      // CSV of console codes within the active platforms (games)
  format?: string | null;        // CSV: any of 'physical','digital'
  seasons?: number | null;       // TV: total seasons
  episodes?: number | null;      // TV: total episodes
  cur_season?: number | null;    // TV: user's current season
  cur_episode?: number | null;   // TV: user's current episode
  completed_at?: number | null;  // epoch ms when the user marked the item as finished
  show_to?: string | null;       // movies/TV: CSV of people to show it to
  season_episodes?: string | null; // TV: CSV of "seasonNumber:episodeCount" pairs
  series_status?: string | null;   // TV: 'continuing' or 'ended'
  added_at?: number;
  updated_at?: number;
}

interface SearchHit {
  external_id: string;
  external_src: string;
  title: string;
  subtitle?: string | null;
  year?: number | null;
  cover_url?: string | null;
  description?: string | null;
}

const json = (data: unknown, init: ResponseInit = {}): Response =>
  new Response(JSON.stringify(data), {
    ...init,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": "*",
      ...(init.headers || {}),
    },
  });

const err = (status: number, message: string) => json({ error: message }, { status });

const uuid = () =>
  crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).slice(2) + Date.now().toString(36);

function bearer(req: Request): string {
  const h = req.headers.get("authorization") || "";
  return h.startsWith("Bearer ") ? h.slice(7) : "";
}

function b64urlToBytes(s: string): Uint8Array {
  let t = s.replace(/-/g, "+").replace(/_/g, "/");
  while (t.length % 4) t += "=";
  const bin = atob(t);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function bytesToB64url(bytes: Uint8Array): string {
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// ---- App session tokens: after a Google sign-in the app exchanges the short-lived Google ID
// token for a 30-day HMAC-signed session token, so it never has to re-prompt Google on launch.
const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000;

async function sessionKey(env: Env): Promise<CryptoKey> {
  const secret = env.SESSION_SECRET || env.SHELF_TOKEN || "media-shelf";
  return crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

async function mintSession(uid: string, env: Env): Promise<{ token: string; expiresAt: number }> {
  const expiresAt = Date.now() + SESSION_TTL_MS;
  const payload = bytesToB64url(new TextEncoder().encode(JSON.stringify({ uid, exp: expiresAt })));
  const key = await sessionKey(env);
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(payload)));
  return { token: `s.${payload}.${bytesToB64url(sig)}`, expiresAt };
}

async function verifySession(token: string, env: Env): Promise<string | null> {
  if (!token.startsWith("s.")) return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [, payload, sig] = parts;
  const key = await sessionKey(env);
  const expected = new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(payload)));
  if (bytesToB64url(expected) !== sig) return null;
  try {
    const data = JSON.parse(new TextDecoder().decode(b64urlToBytes(payload)));
    if (typeof data.exp !== "number" || data.exp < Date.now()) return null;
    return typeof data.uid === "string" ? data.uid : null;
  } catch {
    return null;
  }
}

// Google's signing keys rotate; cache them for an hour so we're not fetching JWKS per request.
let googleJwks: { keys: any[]; at: number } | null = null;
async function getGoogleJwks(): Promise<any[]> {
  const now = Date.now();
  if (googleJwks && now - googleJwks.at < 60 * 60 * 1000) return googleJwks.keys;
  const r = await fetchWithTimeout("https://www.googleapis.com/oauth2/v3/certs", {}, 5000);
  if (!r.ok) return googleJwks?.keys ?? [];
  const d = (await r.json()) as any;
  googleJwks = { keys: Array.isArray(d.keys) ? d.keys : [], at: now };
  return googleJwks.keys;
}

interface GoogleClaims {
  sub: string;
  email?: string;
  email_verified?: boolean;
  aud: string;
  iss: string;
  exp: number;
}

/** Verify a Google ID token (RS256 signature via JWKS + issuer/audience/expiry). Null if invalid. */
async function verifyGoogleToken(token: string, clientId: string): Promise<GoogleClaims | null> {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [h, p, s] = parts;
  let header: any;
  let payload: GoogleClaims;
  try {
    header = JSON.parse(new TextDecoder().decode(b64urlToBytes(h)));
    payload = JSON.parse(new TextDecoder().decode(b64urlToBytes(p)));
  } catch {
    return null;
  }
  if (header.alg !== "RS256" || !header.kid) return null;
  const jwk = (await getGoogleJwks()).find((k) => k.kid === header.kid);
  if (!jwk) return null;
  const key = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"],
  );
  const ok = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    key,
    b64urlToBytes(s),
    new TextEncoder().encode(`${h}.${p}`),
  );
  if (!ok) return null;
  if (payload.iss !== "accounts.google.com" && payload.iss !== "https://accounts.google.com") return null;
  if (payload.aud !== clientId) return null;
  if (typeof payload.exp !== "number" || payload.exp * 1000 < Date.now()) return null;
  return payload;
}

/** One-time: hand the pre-auth library over to the owner's real account on first sign-in. */
async function claimLegacyData(env: Env, userId: string): Promise<void> {
  const now = Date.now();
  await env.DB.prepare("UPDATE items SET user_id = ?1, updated_at = ?2 WHERE user_id = ?3")
    .bind(userId, now, LEGACY_USER)
    .run();
  await env.DB.prepare("UPDATE OR IGNORE user_settings SET user_id = ?1 WHERE user_id = ?2")
    .bind(userId, LEGACY_USER)
    .run();
  await settingSetGlobal(env, "owner_user_id", userId);
}

/**
 * Resolve the caller to a user id, or null if unauthorized. The legacy shared token maps to the
 * pre-auth owner bucket so the current (pre-sign-in) app keeps working during the transition; a
 * Google ID token maps to that Google account.
 */
async function resolveUser(req: Request, env: Env): Promise<string | null> {
  const token = bearer(req);
  if (!token) return null;
  if (env.SHELF_TOKEN && token === env.SHELF_TOKEN) return LEGACY_USER;
  // App session token (the common case after sign-in) — a fast local HMAC check, no network.
  const sessionUid = await verifySession(token, env);
  if (sessionUid) return sessionUid;
  if (env.GOOGLE_CLIENT_ID) {
    const claims = await verifyGoogleToken(token, env.GOOGLE_CLIENT_ID);
    if (claims?.sub) {
      const userId = `g:${claims.sub}`;
      if (
        env.OWNER_EMAIL &&
        claims.email &&
        claims.email.toLowerCase() === env.OWNER_EMAIL.toLowerCase()
      ) {
        await claimLegacyData(env, userId);
      }
      return userId;
    }
  }
  return null;
}

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);

    if (req.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "access-control-allow-origin": "*",
          "access-control-allow-methods": "GET,POST,PATCH,DELETE,OPTIONS",
          "access-control-allow-headers": "authorization,content-type",
          "access-control-max-age": "86400",
        },
      });
    }

    if (url.pathname === "/health") return json({ ok: true });

    // Uploaded covers are served unauthenticated: the app's image loader has no way to attach
    // a token, and the id is an unguessable uuid. Same exposure as any cover URL we already use.
    const coverMatch = url.pathname.match(/^\/covers\/([0-9a-f-]{36})$/);
    if (coverMatch && req.method === "GET") return serveCustomCover(coverMatch[1], env);
    // Public read-only shelf view -- intentionally pre-auth.
    if (url.pathname === "/k" || url.pathname === "/k/") return publicShelves(env);
    // Plex webhook: Plex can't send an Authorization header, so this route authenticates via a
    // secret query param (?token=) instead of the usual Bearer check.
    if (url.pathname === "/plex/webhook" && req.method === "POST") return plexWebhook(req, url, env);

    const userId = await resolveUser(req, env);
    if (!userId) return err(401, "unauthorized");

    try {
      if (url.pathname === "/items") {
        if (req.method === "GET") return listItems(url, env, userId);
        if (req.method === "POST") return createItem(req, env, userId);
      }

      if (url.pathname === "/items/bulk" && req.method === "POST") return bulkCreate(req, env, userId);

      const idMatch = url.pathname.match(/^\/items\/([^/]+)$/);
      if (idMatch) {
        const id = idMatch[1];
        if (req.method === "PATCH") return updateItem(id, req, env, userId);
        if (req.method === "DELETE") return deleteItem(id, env, userId);
      }

      const refreshMatch = url.pathname.match(/^\/items\/([^/]+)\/refresh$/);
      if (refreshMatch && req.method === "POST") return refreshItem(refreshMatch[1], env, userId);

      const coversMatch = url.pathname.match(/^\/items\/([^/]+)\/covers$/);
      if (coversMatch && req.method === "GET") return listCovers(coversMatch[1], env, userId);

      const scoresMatch = url.pathname.match(/^\/items\/([^/]+)\/scores$/);
      if (scoresMatch && req.method === "GET") return itemScores(scoresMatch[1], env, userId);

      if (url.pathname === "/search/books") return searchBooks(url, env);
      if (url.pathname === "/search/movies") return searchTmdb(url, env, "movie");
      if (url.pathname === "/search/tv") return searchTmdb(url, env, "tv");
      if (url.pathname === "/search/games") return searchGames(url, env);
      if (url.pathname === "/lookup/barcode") return lookupBarcode(url, env);
      if (url.pathname === "/covers/upload" && req.method === "POST") {
        return uploadCustomCover(req, url, env, userId);
      }
      if (url.pathname === "/identify" && req.method === "POST") return identifyImage(req, env);
      if (url.pathname === "/identify/shelf" && req.method === "POST") return identifyShelf(req, env);

      // Exchange a verified Google sign-in (userId already resolved) for a long-lived app session
      // token, so the app doesn't have to re-prompt Google on every launch.
      if (url.pathname === "/auth/session" && req.method === "POST") return json(await mintSession(userId, env));

      // Steam: store the API key + SteamID so the daily cron can auto-add new purchases,
      // and expose a manual sync for testing / immediate refresh.
      if (url.pathname === "/steam/config" && req.method === "POST") return steamConfig(req, env, userId);
      if (url.pathname === "/steam/sync" && req.method === "POST") return json(await syncSteamLibrary(env, userId));
      if (url.pathname === "/steam/status" && req.method === "GET") return steamStatus(env, userId);

      // Plex live sync: hand the user their personal webhook URL to paste into Plex.
      if (url.pathname === "/plex/config" && req.method === "POST") return plexConfig(req, url, env, userId);

      return err(404, "not found");
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      return err(500, msg);
    }
  },

  // Daily cron: poll Steam for newly-purchased games for every user who has connected Steam.
  async scheduled(_controller: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(syncAllUsersSteam(env).catch(() => {}));
  },
} satisfies ExportedHandler<Env>;

// ---------- settings + Steam library sync ----------

async function settingGet(env: Env, userId: string, key: string): Promise<string | null> {
  const row = await env.DB.prepare("SELECT value FROM user_settings WHERE user_id = ?1 AND key = ?2")
    .bind(userId, key)
    .first<{ value: string }>();
  return row?.value ?? null;
}

async function settingSet(env: Env, userId: string, key: string, value: string): Promise<void> {
  await env.DB.prepare(
    "INSERT INTO user_settings (user_id, key, value) VALUES (?1, ?2, ?3)" +
      " ON CONFLICT(user_id, key) DO UPDATE SET value = excluded.value",
  )
    .bind(userId, key, value)
    .run();
}

// App-wide (not per-user) settings live under a reserved bucket, e.g. which user owns the /k view.
const GLOBAL_SETTINGS = "__global__";
const settingGetGlobal = (env: Env, key: string) => settingGet(env, GLOBAL_SETTINGS, key);
const settingSetGlobal = (env: Env, key: string, value: string) =>
  settingSet(env, GLOBAL_SETTINGS, key, value);

/** The account that owns single-owner integrations (Plex, /k view) until per-user wiring lands. */
async function ownerUserId(env: Env): Promise<string> {
  return (await settingGetGlobal(env, "owner_user_id")) ?? LEGACY_USER;
}

/** Cron helper: run the Steam sync for every user who has stored Steam credentials. */
async function syncAllUsersSteam(env: Env): Promise<void> {
  const rows = await env.DB.prepare(
    "SELECT DISTINCT user_id FROM user_settings WHERE key = 'steam_api_key'",
  ).all<{ user_id: string }>();
  for (const r of rows.results ?? []) {
    await syncSteamLibrary(env, r.user_id).catch(() => ({ added: 0, updated: 0 }));
  }
}

/** Persist the Steam credentials the daily sync needs (sent by the app when connecting Steam). */
async function steamConfig(req: Request, env: Env, userId: string): Promise<Response> {
  const body = (await req.json().catch(() => null)) as { apiKey?: string; steamId?: string } | null;
  const apiKey = body?.apiKey?.trim();
  const steamId = body?.steamId?.trim();
  if (!apiKey || !steamId) return err(400, "apiKey and steamId required");
  await settingSet(env, userId, "steam_api_key", apiKey);
  await settingSet(env, userId, "steam_id", steamId);
  return json({ ok: true });
}

/** Whether this user has Steam credentials (i.e. the cron is armed) + how many are on their shelf. */
async function steamStatus(env: Env, userId: string): Promise<Response> {
  const apiKey = await settingGet(env, userId, "steam_api_key");
  const steamId = await settingGet(env, userId, "steam_id");
  const row = await env.DB.prepare(
    "SELECT COUNT(*) AS n FROM items WHERE user_id = ?1 AND kind = 'game' AND external_src = 'steam'",
  )
    .bind(userId)
    .first<{ n: number }>();
  return json({ connected: !!(apiKey && steamId), games: row?.n ?? 0 });
}

interface SteamOwnedGame {
  appid: number;
  name?: string;
  playtime_forever?: number;
}

/**
 * Resolve a Steam id input to a 64-bit SteamID. Accepts a raw 64-bit id, a vanity name, or a
 * profile URL — GetOwnedGames only takes the numeric id, so a stored vanity ("KeithZ488") must
 * be resolved first or the library comes back empty.
 */
async function steamResolveId(apiKey: string, input: string): Promise<string | null> {
  let v = input.trim().replace(/\/+$/, "");
  const prof = v.match(/steamcommunity\.com\/profiles\/(\d+)/);
  if (prof) return prof[1];
  const vanity = v.match(/steamcommunity\.com\/id\/([^/?#]+)/);
  if (vanity) v = vanity[1];
  if (/^\d{17}$/.test(v)) return v;
  const r = await fetchWithTimeout(
    "https://api.steampowered.com/ISteamUser/ResolveVanityURL/v1/" +
      `?key=${apiKey}&vanityurl=${encodeURIComponent(v)}`,
    {},
    8000,
  );
  if (!r.ok) return null;
  const d = (await r.json()) as any;
  return d?.response?.success === 1 ? d.response.steamid || null : null;
}

async function steamOwnedGames(apiKey: string, steamId: string): Promise<SteamOwnedGame[]> {
  const r = await fetchWithTimeout(
    "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/" +
      `?key=${apiKey}&steamid=${steamId}&include_appinfo=1&include_played_free_games=1&format=json`,
    {},
    10000,
  );
  if (!r.ok) return [];
  const data = (await r.json()) as any;
  const games = data?.response?.games;
  return Array.isArray(games) ? games : [];
}

/**
 * Add any owned Steam games not already on the shelf. Mirrors the in-app import (no per-game
 * enrichment here — the app backfills cover/description on first open), and bounds the work per
 * run so a first sync of a big library can't blow past Worker subrequest limits.
 */
async function syncSteamLibrary(env: Env, userId: string): Promise<{ added: number; updated: number }> {
  const apiKey = await settingGet(env, userId, "steam_api_key");
  const rawId = await settingGet(env, userId, "steam_id");
  if (!apiKey || !rawId) return { added: 0, updated: 0 };

  // Stored id may be a vanity name / URL — GetOwnedGames needs the 64-bit id.
  const steamId = await steamResolveId(apiKey, rawId);
  if (!steamId) return { added: 0, updated: 0 };
  // Cache the resolved id so future runs skip the vanity lookup.
  if (steamId !== rawId) await settingSet(env, userId, "steam_id", steamId);

  const owned = await steamOwnedGames(apiKey, steamId);
  if (owned.length === 0) return { added: 0, updated: 0 };

  const existing = await env.DB.prepare(
    "SELECT external_id FROM items WHERE user_id = ?1 AND kind = 'game' AND external_src = 'steam'",
  ).bind(userId).all<{ external_id: string }>();
  const have = new Set((existing.results ?? []).map((r) => String(r.external_id)));

  const fresh = owned.filter((g) => g.appid && g.name && !have.has(String(g.appid))).slice(0, 60);
  let added = 0;
  if (fresh.length > 0) {
    const now = Date.now();
    const stmt = env.DB.prepare(
      `INSERT INTO items
        (id, user_id, kind, title, subtitle, cover_url, external_id, external_src, status, user_platform, format, added_at, updated_at)
       VALUES
        (?1,?2,'game',?3,'PC',?4,?5,'steam',?6,'pc','digital',?7,?7)
       ON CONFLICT(user_id, kind, external_src, external_id) DO NOTHING`,
    );
    const batch = fresh.map((g) => {
      const status = (g.playtime_forever ?? 0) > 0 ? "owned,played" : "owned";
      const cover = `https://cdn.cloudflare.steamstatic.com/steam/apps/${g.appid}/library_600x900.jpg`;
      return stmt.bind(uuid(), userId, g.name, cover, String(g.appid), status, now);
    });
    const results = await env.DB.batch(batch);
    added = results.reduce((n, r) => n + (r.meta?.changes ?? 0), 0);
  }

  // Steam imports land without a release year — backfill a batch of the ones still missing it
  // (bounded per run to respect Worker subrequest limits; converges over a few runs).
  const updated = await backfillSteamYears(env, userId, 25);
  return { added, updated };
}

/** Fill in the release year for up to `limit` of a user's Steam games still missing one. */
async function backfillSteamYears(env: Env, userId: string, limit: number): Promise<number> {
  const rows = await env.DB.prepare(
    "SELECT id, external_id FROM items WHERE user_id = ?1 AND kind = 'game' AND external_src = 'steam'" +
      " AND year IS NULL AND external_id IS NOT NULL LIMIT ?2",
  ).bind(userId, limit).all<{ id: string; external_id: string }>();
  const list = rows.results ?? [];
  let n = 0;
  for (const r of list) {
    const d = await steamStoreDetails(r.external_id);
    const year = steamReleaseYear(d?.release_date?.date);
    if (year) {
      await env.DB.prepare("UPDATE items SET year = ?1, updated_at = ?2 WHERE id = ?3")
        .bind(year, Date.now(), r.id)
        .run();
      n++;
    }
  }
  return n;
}

// ---------- shelf CRUD ----------

async function listItems(url: URL, env: Env, userId: string): Promise<Response> {
  const kind = url.searchParams.get("kind");
  const stmt = kind
    ? env.DB.prepare("SELECT * FROM items WHERE user_id = ?1 AND kind = ?2 ORDER BY added_at DESC").bind(userId, kind)
    : env.DB.prepare("SELECT * FROM items WHERE user_id = ?1 ORDER BY added_at DESC").bind(userId);
  const { results } = await stmt.all<Item>();
  return json({ items: results || [] });
}

/**
 * Bulk insert for imports (e.g. Plex). Caller supplies fully-formed items (cover, description
 * etc. already resolved client-side) so we skip per-item enrichment and just batch-write.
 *
 * On conflict (item already imported) we DON'T no-op: instead we sync the play state by
 * appending "watched"/"watching" to the existing status when the incoming item has it and
 * the row doesn't yet. Only the status column is touched -- ratings, notes, covers, format
 * and any other statuses the user added are all preserved. This makes a re-import double as
 * a "pull watched state from Plex" sync for items already on the shelf.
 */
async function bulkCreate(req: Request, env: Env, userId: string): Promise<Response> {
  const body = (await req.json().catch(() => null)) as { items?: Partial<Item>[] } | null;
  const list = body?.items;
  if (!Array.isArray(list) || list.length === 0) return err(400, "items array required");
  if (list.length > 200) return err(400, "max 200 items per request");

  const now = Date.now();
  const stmt = env.DB.prepare(
    `INSERT INTO items
      (id, user_id, kind, title, subtitle, year, cover_url, external_id, external_src,
       description, rating, status, notes, user_platform, consoles, format,
       seasons, episodes, cur_season, cur_episode, completed_at, added_at, updated_at)
     VALUES
      (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?17,?18,?19,?20,?21,?22,?23)
     ON CONFLICT(user_id, kind, external_src, external_id) DO UPDATE SET
       status = CASE
         WHEN excluded.status LIKE '%watched%' AND COALESCE(items.status,'') NOT LIKE '%watched%'
           THEN TRIM(COALESCE(items.status,'') || ',watched', ',')
         WHEN excluded.status LIKE '%watching%'
              AND COALESCE(items.status,'') NOT LIKE '%watching%'
              AND COALESCE(items.status,'') NOT LIKE '%watched%'
           THEN TRIM(COALESCE(items.status,'') || ',watching', ',')
         ELSE items.status
       END`,
  );

  const batch = list
    .filter((b) => b.kind && b.title)
    .map((b) =>
      stmt.bind(
        b.id || uuid(),
        userId,
        b.kind,
        b.title,
        b.subtitle ?? null,
        b.year ?? null,
        b.cover_url ?? null,
        b.external_id ?? null,
        b.external_src ?? "manual",
        b.description ?? null,
        b.rating ?? null,
        b.status ?? "owned",
        b.notes ?? null,
        b.user_platform ?? null,
        b.consoles ?? null,
        b.format ?? null,
        b.seasons ?? null,
        b.episodes ?? null,
        b.cur_season ?? null,
        b.cur_episode ?? null,
        b.completed_at ?? null,
        b.added_at ?? now,
        now,
      ),
    );

  if (batch.length === 0) return json({ inserted: 0 });
  const results = await env.DB.batch(batch);
  const inserted = results.reduce((n, r) => n + (r.meta?.changes ?? 0), 0);
  return json({ inserted, received: list.length });
}

async function createItem(req: Request, env: Env, userId: string): Promise<Response> {
  const body = (await req.json()) as Partial<Item>;
  if (!body.kind || !body.title) return err(400, "kind and title required");

  const now = Date.now();
  let item: Item = {
    id: body.id || uuid(),
    user_id: userId,
    kind: body.kind,
    title: body.title,
    subtitle: body.subtitle ?? null,
    year: body.year ?? null,
    cover_url: body.cover_url ?? null,
    external_id: body.external_id ?? null,
    external_src: body.external_src ?? "manual",
    description: body.description ?? null,
    rating: body.rating ?? null,
    status: body.status ?? "owned",
    notes: body.notes ?? null,
    user_platform: body.user_platform ?? null,
    consoles: body.consoles ?? null,
    format: body.format ?? null,
    seasons: body.seasons ?? null,
    episodes: body.episodes ?? null,
    cur_season: body.cur_season ?? null,
    cur_episode: body.cur_episode ?? null,
    completed_at: body.completed_at ?? null,
    added_at: body.added_at ?? now,
    updated_at: now,
  };

  // Fetch a description (and sometimes a better cover) from the source's detail
  // endpoint when we don't already have one. Search endpoints return minimal data;
  // the per-item detail endpoint is where the synopses live.
  item = await enrichForCreate(item, env);

  // ON CONFLICT: if the user re-adds the same external item, just touch updated_at.
  await env.DB.prepare(
    `INSERT INTO items
      (id, user_id, kind, title, subtitle, year, cover_url, external_id, external_src,
       description, rating, status, notes, user_platform, consoles, format,
       seasons, episodes, cur_season, cur_episode, completed_at, series_status, added_at, updated_at)
     VALUES
      (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?17,?18,?19,?20,?21,?22,?23,?24)
     ON CONFLICT(user_id, kind, external_src, external_id) DO UPDATE SET
       updated_at = excluded.updated_at,
       status     = excluded.status`,
  )
    .bind(
      item.id,
      item.user_id,
      item.kind,
      item.title,
      item.subtitle,
      item.year,
      item.cover_url,
      item.external_id,
      item.external_src,
      item.description,
      item.rating,
      item.status,
      item.notes,
      item.user_platform,
      item.consoles,
      item.format,
      item.seasons,
      item.episodes,
      item.cur_season,
      item.cur_episode,
      item.completed_at,
      item.series_status ?? null,
      item.added_at,
      item.updated_at,
    )
    .run();

  // Return the canonical row. If external_id is set we look it up via the natural
  // key (catches the upsert case where the existing row's id wins). Otherwise by id.
  const row = item.external_id
    ? await env.DB.prepare(
        "SELECT * FROM items WHERE user_id=?1 AND kind=?2 AND external_src=?3 AND external_id=?4 LIMIT 1",
      )
        .bind(userId, item.kind, item.external_src, item.external_id)
        .first<Item>()
    : await env.DB.prepare("SELECT * FROM items WHERE id=?1 AND user_id=?2").bind(item.id, userId).first<Item>();

  return json({ item: row || item }, { status: 201 });
}

const PATCHABLE = ["title", "subtitle", "year", "cover_url", "description", "rating", "status", "notes", "user_platform", "consoles", "format", "seasons", "episodes", "cur_season", "cur_episode", "completed_at", "show_to"] as const;

async function updateItem(id: string, req: Request, env: Env, userId: string): Promise<Response> {
  const body = (await req.json()) as Partial<Item> & { clear?: unknown };
  const fields: string[] = [];
  const values: unknown[] = [];
  let i = 1;
  for (const k of PATCHABLE) {
    if (k in body) {
      fields.push(`${k} = ?${i++}`);
      values.push(body[k] ?? null);
    }
  }

  // Columns the client asked to empty, named rather than sent as nulls. The Android client
  // serializes with kotlinx, which omits a property equal to its default -- so a null rating
  // simply vanishes from the JSON and there is no way to tell "leave this alone" from "clear
  // it". Un-rating an item used to arrive here as an empty body and get rejected outright.
  const clear = Array.isArray(body.clear) ? body.clear : [];
  for (const k of clear) {
    if (typeof k !== "string" || k in body) continue;
    if (!(PATCHABLE as readonly string[]).includes(k)) continue;
    fields.push(`${k} = NULL`);
  }

  if (!fields.length) return err(400, "no fields to update");
  fields.push(`updated_at = ?${i++}`);
  values.push(Date.now());
  // Scope to the owner so one user can't patch another's row.
  const idParam = i++;
  const userParam = i;
  values.push(id);
  values.push(userId);

  const res = await env.DB.prepare(
    `UPDATE items SET ${fields.join(", ")} WHERE id = ?${idParam} AND user_id = ?${userParam}`,
  )
    .bind(...values)
    .run();
  if (res.meta.changes === 0) return err(404, "item not found");

  // If the user changed TV progress (e.g. bumped the stepper), auto-flip to Watched when that
  // reaches the end of the series -- same episode-precise check the Plex webhook uses.
  if ("cur_season" in body || "cur_episode" in body) {
    const mid = await env.DB.prepare("SELECT * FROM items WHERE id = ?1").bind(id).first<Item>();
    if (mid) await flipIfCaughtUp(mid, env);
  }

  const row = await env.DB.prepare("SELECT * FROM items WHERE id = ?1").bind(id).first<Item>();
  return json({ item: row });
}

async function deleteItem(id: string, env: Env, userId: string): Promise<Response> {
  const res = await env.DB.prepare("DELETE FROM items WHERE id = ?1 AND user_id = ?2")
    .bind(id, userId)
    .run();
  if (res.meta.changes === 0) return err(404, "item not found");
  return json({ ok: true });
}

// ---------- external lookups ----------

const OL_UA = { "user-agent": "media-shelf/0.1 (kzaller.com)" };

/** Open Library publish dates are free text ("2005", "Aug 01, 2005", "1965-10"). */
function parseYear(raw: unknown): number | null {
  const m = typeof raw === "string" ? raw.match(/(1[0-9]{3}|20[0-9]{2})/) : null;
  return m ? Number(m[1]) : null;
}

/** Google Books. Keyless works but shares an anonymous quota that is often exhausted, so a
 *  failure here is an ordinary miss, never an error -- set GOOGLE_BOOKS_API_KEY to rely on it. */
async function googleBooksHits(q: string, env: Env, limit: number): Promise<SearchHit[]> {
  const api = new URL("https://www.googleapis.com/books/v1/volumes");
  api.searchParams.set("q", q);
  api.searchParams.set("maxResults", String(Math.min(Math.max(limit, 1), 20)));
  // Workers call from datacenter IPs, which Google can't geolocate -- without an explicit
  // country it rejects the request outright ("cannot determine user location").
  api.searchParams.set("country", "US");
  if (env.GOOGLE_BOOKS_API_KEY) api.searchParams.set("key", env.GOOGLE_BOOKS_API_KEY);
  const r = await fetchWithTimeout(api.toString(), {}, 7000).catch(() => null);
  if (!r || !r.ok) return [];
  const data = (await r.json().catch(() => null)) as any;
  const items: any[] = Array.isArray(data?.items) ? data.items : [];
  return items.flatMap((it): SearchHit[] => {
    const v = it?.volumeInfo;
    if (!v?.title) return [];
    const isbn13 = (v.industryIdentifiers ?? []).find((i: any) => i?.type === "ISBN_13")?.identifier;
    const authors = Array.isArray(v.authors) ? v.authors.join(", ") : null;
    // Google serves covers over http and at thumbnail size by default; ask for something better.
    const thumb: string | null = v.imageLinks?.thumbnail ?? v.imageLinks?.smallThumbnail ?? null;
    const cover = thumb ? thumb.replace(/^http:/, "https:").replace(/&zoom=\d+/, "&zoom=1") : null;
    return [{
      external_id: String(isbn13 || it.id),
      external_src: "google_books",
      title: v.title,
      subtitle: [authors, v.subtitle].filter(Boolean).join(" \u00b7 ") || null,
      year: parseYear(v.publishedDate),
      cover_url: cover,
      description: trimDescription(v.description ?? null),
    }];
  });
}

/** Open Library keyed by ISBN. The search index misses many individual editions, so ask the
 *  edition record directly -- it resolves far more scanned barcodes. */
async function openLibraryIsbnHit(isbn: string): Promise<SearchHit | null> {
  const r = await fetchWithTimeout(
    `https://openlibrary.org/api/books?bibkeys=ISBN:${encodeURIComponent(isbn)}&format=json&jscmd=data`,
    { headers: OL_UA },
    7000,
  ).catch(() => null);
  if (!r || !r.ok) return null;
  const data = (await r.json().catch(() => null)) as any;
  const rec = data?.[`ISBN:${isbn}`];
  if (!rec?.title) return null;
  const authors = Array.isArray(rec.authors)
    ? rec.authors.map((a: any) => a?.name).filter(Boolean).join(", ")
    : null;
  return {
    external_id: isbn,
    external_src: "open_library",
    title: rec.title,
    subtitle: [authors, rec.subtitle].filter(Boolean).join(" \u00b7 ") || null,
    year: parseYear(rec.publish_date),
    cover_url: rec.cover?.large ?? rec.cover?.medium ?? null,
    description: null,
  };
}

/** Open Library's search index: good for free-text, patchy for a specific ISBN. */
async function openLibrarySearchHits(params: { isbn?: string; q?: string }): Promise<SearchHit[]> {
  const api = new URL("https://openlibrary.org/search.json");
  if (params.isbn) api.searchParams.set("isbn", params.isbn);
  else if (params.q) api.searchParams.set("q", params.q);
  api.searchParams.set("limit", "20");
  api.searchParams.set("fields", "key,title,subtitle,author_name,first_publish_year,isbn,cover_i");
  const r = await fetchWithTimeout(api.toString(), { headers: OL_UA }, 8000).catch(() => null);
  if (!r || !r.ok) return [];
  const data = (await r.json().catch(() => null)) as any;
  const docs: any[] = data?.docs || [];
  return docs.map((d: any): SearchHit => {
    const firstIsbn = Array.isArray(d.isbn) && d.isbn.length > 0 ? String(d.isbn[0]) : null;
    const workId = d.key ? String(d.key).replace("/works/", "") : "";
    const authors = Array.isArray(d.author_name) ? d.author_name.join(", ") : null;
    return {
      external_id: firstIsbn || workId,
      external_src: "open_library",
      title: d.title || "Untitled",
      subtitle: [authors, d.subtitle].filter(Boolean).join(" \u00b7 ") || null,
      year: typeof d.first_publish_year === "number" ? d.first_publish_year : null,
      cover_url: d.cover_i ? `https://covers.openlibrary.org/b/id/${d.cover_i}-L.jpg` : null,
      description: null,
    };
  });
}

/** Drop repeats of the same book and put the best-described copies first. */
function mergeBookHits(...groups: SearchHit[][]): SearchHit[] {
  const seen = new Map<string, SearchHit>();
  for (const hit of groups.flat()) {
    const key = `${hit.title.toLowerCase().replace(/[^a-z0-9]+/g, "")}|${hit.year ?? ""}`;
    const kept = seen.get(key);
    if (!kept) {
      seen.set(key, hit);
      continue;
    }
    // Prefer whichever copy carries more: a cover beats none, a synopsis beats none.
    const score = (h: SearchHit) => (h.cover_url ? 2 : 0) + (h.description ? 1 : 0);
    if (score(hit) > score(kept)) seen.set(key, hit);
  }
  return [...seen.values()].sort(
    (a, b) =>
      (b.cover_url ? 2 : 0) + (b.description ? 1 : 0) -
      ((a.cover_url ? 2 : 0) + (a.description ? 1 : 0)),
  );
}

async function searchBooks(url: URL, env: Env): Promise<Response> {
  const isbn = url.searchParams.get("isbn");
  const q = url.searchParams.get("q");
  if (!isbn && !q) return err(400, "q or isbn required");

  if (isbn) {
    // A scanned barcode: ask both catalogues, since either alone misses plenty of editions.
    const [edition, google] = await Promise.all([
      openLibraryIsbnHit(isbn),
      googleBooksHits(`isbn:${isbn}`, env, 3),
    ]);
    let hits = mergeBookHits(edition ? [edition] : [], google);
    // Only if neither knew the ISBN, fall back to the (patchier) search index.
    if (hits.length === 0) hits = await openLibrarySearchHits({ isbn });
    return json({ hits });
  }

  const [ol, google] = await Promise.all([
    openLibrarySearchHits({ q: q! }),
    googleBooksHits(q!, env, 10),
  ]);
  return json({ hits: mergeBookHits(ol, google).slice(0, 25) });
}

async function searchTmdb(url: URL, env: Env, kind: "movie" | "tv"): Promise<Response> {
  const id = url.searchParams.get("id");
  const q = url.searchParams.get("q");
  if (!env.TMDB_API_KEY) return err(500, "TMDB_API_KEY not configured");

  let api: URL;
  if (id) {
    api = new URL(`https://api.themoviedb.org/3/${kind}/${id}`);
  } else if (q) {
    api = new URL(`https://api.themoviedb.org/3/search/${kind}`);
    api.searchParams.set("query", q);
  } else {
    return err(400, "q or id required");
  }
  api.searchParams.set("api_key", env.TMDB_API_KEY);

  const r = await fetch(api.toString());
  if (!r.ok) {
    const body = await r.text().catch(() => "");
    return err(502, `tmdb ${r.status}: ${body.slice(0, 200)}`);
  }
  const data = (await r.json()) as any;
  const list = id ? [data] : data.results || [];
  const hits: SearchHit[] = list.map((m: any): SearchHit => {
    const title = kind === "movie" ? m.title || m.original_title : m.name || m.original_name;
    const date = kind === "movie" ? m.release_date : m.first_air_date;
    return {
      external_id: String(m.id),
      external_src: "tmdb",
      title: title || "Untitled",
      subtitle: kind === "tv" ? "TV Series" : null,
      year: date ? Number(date.slice(0, 4)) || null : null,
      cover_url: m.poster_path ? `https://image.tmdb.org/t/p/w500${m.poster_path}` : null,
      description: m.overview || null,
    };
  });
  return json({ hits });
}

/** Abort a fetch that takes too long, so one slow upstream can't hang the whole request. */
async function fetchWithTimeout(input: string, init: RequestInit = {}, ms = 8000): Promise<Response> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), ms);
  try {
    return await fetch(input, { ...init, signal: ctrl.signal });
  } finally {
    clearTimeout(timer);
  }
}

/** Reject a promise if it doesn't settle within `ms`, so a slow provider is dropped, not awaited. */
function withTimeout<T>(p: Promise<T>, ms: number, label: string): Promise<T> {
  return Promise.race([
    p,
    new Promise<T>((_, reject) => setTimeout(() => reject(new Error(`${label} timed out`)), ms)),
  ]);
}

// ---------- Plex live sync (webhooks) ----------

/** Pull a TMDB id out of a Plex webhook's Metadata (Guid array or legacy agent guid). */
function extractTmdbFromMetadata(md: any): string | null {
  const guids: any[] = Array.isArray(md?.Guid) ? md.Guid : [];
  for (const g of guids) {
    const id = typeof g?.id === "string" ? g.id : "";
    if (id.startsWith("tmdb://")) return id.slice("tmdb://".length);
  }
  const guid = typeof md?.guid === "string" ? md.guid : "";
  const m = guid.match(/themoviedb:\/\/(\d+)/) || guid.match(/tmdb:\/\/(\d+)/);
  return m ? m[1] : null;
}

/** Resolve a movie/show to a SearchHit via TMDB, by id (exact) or a title query (fallback). */
async function tmdbLookup(kind: "movie" | "tv", opts: { id?: string; q?: string }, env: Env): Promise<SearchHit | null> {
  if (!env.TMDB_API_KEY) return null;
  let api: URL;
  if (opts.id) {
    api = new URL(`https://api.themoviedb.org/3/${kind}/${opts.id}`);
  } else if (opts.q) {
    api = new URL(`https://api.themoviedb.org/3/search/${kind}`);
    api.searchParams.set("query", opts.q);
  } else {
    return null;
  }
  api.searchParams.set("api_key", env.TMDB_API_KEY);
  const r = await fetchWithTimeout(api.toString(), {}, 7000);
  if (!r.ok) return null;
  const data = (await r.json()) as any;
  const m = opts.id ? data : (data.results && data.results[0]);
  if (!m || !m.id) return null;
  const title = kind === "movie" ? (m.title || m.original_title) : (m.name || m.original_name);
  const date = kind === "movie" ? m.release_date : m.first_air_date;
  return {
    external_id: String(m.id),
    external_src: "tmdb",
    title: title || "Untitled",
    subtitle: kind === "tv" ? "TV Series" : null,
    year: date ? Number(date.slice(0, 4)) || null : null,
    cover_url: m.poster_path ? `https://image.tmdb.org/t/p/w500${m.poster_path}` : null,
    description: m.overview || null,
  };
}

/**
 * Plex webhook receiver. When Plex adds a new movie or show to the library it POSTs a
 * multipart form with a JSON `payload`. On `library.new` for a movie/show we resolve it to
 * TMDB (by guid, else by title) and add it as a digitally-owned item. Idempotent: an item that
 * already exists is left untouched (ON CONFLICT DO NOTHING). Always returns 200 for handled or
 * ignored events so Plex doesn't retry-storm.
 */
/** Look up which user owns a Plex webhook secret. */
async function userByPlexSecret(env: Env, secret: string | null): Promise<string | null> {
  if (!secret) return null;
  const row = await env.DB.prepare(
    "SELECT user_id FROM user_settings WHERE key = 'plex_webhook_secret' AND value = ?1 LIMIT 1",
  ).bind(secret).first<{ user_id: string }>();
  return row?.user_id ?? null;
}

/** Generate (once) and return this user's personal Plex webhook URL. */
async function plexConfig(req: Request, url: URL, env: Env, userId: string): Promise<Response> {
  let secret = await settingGet(env, userId, "plex_webhook_secret");
  if (!secret) {
    secret = uuid();
    await settingSet(env, userId, "plex_webhook_secret", secret);
  }

  // The app reports which Plex account is this user's own, read from their server. It's the only
  // thing that reliably tells one person's playback from another's in a webhook -- see the
  // account check in plexWebhook. The body is optional, so a client that doesn't send one still
  // gets its webhook URL.
  let body: any = null;
  try { body = await req.json(); } catch { /* no body, or not JSON */ }
  if (body && typeof body.account === "string" && body.account.trim()) {
    await settingSet(env, userId, "plex_account", body.account.trim());
  }

  const account = await settingGet(env, userId, "plex_account");
  return json({ secret, url: `${url.origin}/plex/webhook?u=${secret}`, account: account ?? "" });
}

async function plexWebhook(req: Request, url: URL, env: Env): Promise<Response> {
  const secret = url.searchParams.get("u") || url.searchParams.get("token") || url.searchParams.get("s");
  // Resolve which user this webhook belongs to by their per-user secret. Fall back to the legacy
  // shared secret (mapped to the owner) so an already-configured webhook keeps working.
  let webhookUser = await userByPlexSecret(env, secret);
  if (!webhookUser) {
    const legacy = env.PLEX_WEBHOOK_SECRET || env.SHELF_TOKEN;
    if (secret && legacy && secret === legacy) webhookUser = await ownerUserId(env);
  }
  if (!webhookUser) return err(401, "unauthorized");

  let payloadStr: string | null = null;
  try {
    const form = await req.formData();
    const p = form.get("payload");
    if (typeof p === "string") payloadStr = p;
  } catch {
    // Some proxies deliver the JSON directly rather than as multipart.
    try { payloadStr = await req.text(); } catch { /* ignore */ }
  }
  if (!payloadStr) return json({ ok: true, skipped: "no payload" });

  let payload: any;
  try { payload = JSON.parse(payloadStr); } catch { return json({ ok: true, skipped: "bad payload" }); }

  const event = payload.event;
  const md = payload.Metadata || {};
  const type = md.type;

  // Everything this webhook sends lands on its owner's shelf (resolved from the secret above).
  const owner = webhookUser;

  // Plex fires webhooks for EVERY account on the server -- shared users with access to the
  // library included -- so a scrobble here is not necessarily this user's own play.
  //
  // The account name is what settles it. The `owner` flag was tried first and isn't dependable:
  // it can be absent, and treating absent as "allow" let other people's plays through, which is
  // exactly what put films nobody here watched into Recently completed. When we know the user's
  // Plex account name (the app reports it from their server), an exact match is required and
  // anything else is dropped. Until it's known we fall back to the old flag check, so a webhook
  // configured before this still works rather than going silent.
  if (event === "media.scrobble") {
    const expected = (await settingGet(env, owner, "plex_account"))?.trim().toLowerCase();
    const account = typeof payload.Account?.title === "string"
      ? payload.Account.title.trim().toLowerCase()
      : null;
    if (expected) {
      if (account !== expected) {
        return json({ ok: true, skipped: "another account", account: payload.Account?.title ?? null });
      }
    } else if (payload.owner === false) {
      return json({ ok: true, skipped: "non-owner scrobble", account: payload.Account?.title ?? null });
    }
  }

  // "Watched" sync: Plex scrobbles when playback finishes (~90%). A movie scrobble means the
  // movie is watched; an episode scrobble advances the show's progress (a whole series can't be
  // flipped to Watched from webhooks alone -- that needs the server's episode counts).
  if (event === "media.scrobble" && type === "movie") return scrobbleMovieWatched(md, env, owner);
  if (event === "media.scrobble" && type === "episode") return scrobbleEpisodeProgress(md, env, owner);

  if (event !== "library.new" || (type !== "movie" && type !== "show")) {
    return json({ ok: true, skipped: `${event}/${type}` });
  }

  const kind: "movie" | "tv" = type === "movie" ? "movie" : "tv";
  const tmdbId = extractTmdbFromMetadata(md);
  const title: string | null = typeof md.title === "string" ? md.title : null;
  const year: number | null = typeof md.year === "number" ? md.year : null;

  let hit = tmdbId ? await tmdbLookup(kind, { id: tmdbId }, env) : null;
  if (!hit && title) hit = await tmdbLookup(kind, { q: title }, env);
  if (!hit) return json({ ok: true, skipped: "no tmdb match", title });

  const now = Date.now();
  let item: Item = {
    id: uuid(),
    user_id: owner,
    kind,
    title: hit.title,
    subtitle: hit.subtitle ?? null,
    year: hit.year ?? year ?? null,
    cover_url: hit.cover_url ?? null,
    external_id: hit.external_id,
    external_src: "tmdb",
    description: hit.description ?? null,
    rating: null,
    status: "owned",
    notes: null,
    user_platform: null,
    consoles: null,
    format: "digital",
    seasons: null,
    episodes: null,
    cur_season: null,
    cur_episode: null,
    completed_at: null,
    show_to: null,
    added_at: now,
    updated_at: now,
  };
  item = await enrichForCreate(item, env);

  await env.DB.prepare(
    `INSERT INTO items
      (id, user_id, kind, title, subtitle, year, cover_url, external_id, external_src,
       description, rating, status, notes, user_platform, consoles, format,
       seasons, episodes, cur_season, cur_episode, completed_at, added_at, updated_at)
     VALUES
      (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?17,?18,?19,?20,?21,?22,?23)
     ON CONFLICT(user_id, kind, external_src, external_id) DO NOTHING`,
  )
    .bind(
      item.id, item.user_id, item.kind, item.title, item.subtitle, item.year, item.cover_url,
      item.external_id, item.external_src, item.description, item.rating, item.status,
      item.notes, item.user_platform, item.consoles, item.format, item.seasons,
      item.episodes, item.cur_season, item.cur_episode, item.completed_at,
      item.added_at, item.updated_at,
    )
    .run();

  return json({ ok: true, added: item.title, kind });
}

/** Add a status code to a CSV if it's not already present. */
function addStatusCsv(csv: string | null | undefined, value: string): string {
  const parts = (csv || "").split(",").map((s) => s.trim()).filter(Boolean);
  if (!parts.includes(value)) parts.push(value);
  return parts.join(",");
}

/** Remove a status code from a CSV. */
function removeStatusCsv(csv: string | null | undefined, value: string): string {
  return (csv || "").split(",").map((s) => s.trim()).filter((s) => s && s !== value).join(",");
}

/** Number of episodes in a TMDB TV season, or null if unavailable. */
async function tmdbSeasonEpisodeCount(tvId: string, seasonNumber: number, env: Env): Promise<number | null> {
  if (!env.TMDB_API_KEY) return null;
  const api = new URL(`https://api.themoviedb.org/3/tv/${tvId}/season/${seasonNumber}`);
  api.searchParams.set("api_key", env.TMDB_API_KEY);
  const r = await fetchWithTimeout(api.toString(), {}, 7000);
  if (!r.ok) return null;
  const data = (await r.json()) as any;
  return Array.isArray(data.episodes) ? data.episodes.length : null;
}

/**
 * If a TV item's progress reaches the end of the series, flip it to Watched (dropping Watching)
 * and stamp a completion date. "The end" is episode-precise: on the final season we look up that
 * season's episode count from TMDB and require the current episode to be at/past the last one. If
 * the count can't be fetched we fall back to season-based (entering the final season). Returns
 * true if a flip was applied.
 */
async function flipIfCaughtUp(row: Item, env: Env): Promise<boolean> {
  if (row.kind !== "tv") return false;
  const seasons = row.seasons ?? 0;
  const curS = row.cur_season ?? 0;
  if (seasons <= 0 || curS < seasons) return false;
  if ((row.status || "").split(",").map((s) => s.trim()).includes("watched")) return false;

  let caughtUp = true; // fallback: on the final season => caught up
  if (row.external_src === "tmdb" && row.external_id) {
    const count = await tmdbSeasonEpisodeCount(row.external_id, curS, env);
    if (count != null && count > 0) caughtUp = (row.cur_episode ?? 0) >= count;
  }
  if (!caughtUp) return false;

  const now = Date.now();
  const status = addStatusCsv(removeStatusCsv(row.status, "watching"), "watched");
  await env.DB
    .prepare("UPDATE items SET status=?1, completed_at=COALESCE(completed_at, ?2), updated_at=?3 WHERE id=?4")
    .bind(status, now, now, row.id)
    .run();
  return true;
}

/** Movie finished on Plex -> mark it Watched (adding it as owned+digital if it's not on a shelf yet). */
async function scrobbleMovieWatched(md: any, env: Env, owner: string): Promise<Response> {
  const tmdbId = extractTmdbFromMetadata(md);
  const title: string | null = typeof md.title === "string" ? md.title : null;
  let hit = tmdbId ? await tmdbLookup("movie", { id: tmdbId }, env) : null;
  if (!hit && title) hit = await tmdbLookup("movie", { q: title }, env);
  if (!hit) return json({ ok: true, skipped: "no tmdb match", title });

  const now = Date.now();
  let item: Item = {
    id: uuid(),
    user_id: owner,
    kind: "movie",
    title: hit.title,
    subtitle: hit.subtitle ?? null,
    year: hit.year ?? null,
    cover_url: hit.cover_url ?? null,
    external_id: hit.external_id,
    external_src: "tmdb",
    description: hit.description ?? null,
    rating: null,
    status: "owned,watched",
    notes: null,
    user_platform: null,
    consoles: null,
    format: "digital",
    seasons: null,
    episodes: null,
    cur_season: null,
    cur_episode: null,
    completed_at: now,
    show_to: null,
    added_at: now,
    updated_at: now,
  };
  item = await enrichForCreate(item, env);

  // Insert if new; if it already exists, append "watched" (preserving other statuses) and stamp
  // a completion date if it didn't have one.
  await env.DB.prepare(
    `INSERT INTO items
      (id, user_id, kind, title, subtitle, year, cover_url, external_id, external_src,
       description, rating, status, notes, user_platform, consoles, format,
       seasons, episodes, cur_season, cur_episode, completed_at, added_at, updated_at)
     VALUES
      (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?17,?18,?19,?20,?21,?22,?23)
     ON CONFLICT(user_id, kind, external_src, external_id) DO UPDATE SET
       status = CASE
         WHEN COALESCE(items.status,'') LIKE '%watched%' THEN items.status
         ELSE TRIM(COALESCE(items.status,'') || ',watched', ',')
       END,
       completed_at = COALESCE(items.completed_at, excluded.completed_at),
       updated_at = excluded.updated_at`,
  )
    .bind(
      item.id, item.user_id, item.kind, item.title, item.subtitle, item.year, item.cover_url,
      item.external_id, item.external_src, item.description, item.rating, item.status,
      item.notes, item.user_platform, item.consoles, item.format, item.seasons,
      item.episodes, item.cur_season, item.cur_episode, item.completed_at,
      item.added_at, item.updated_at,
    )
    .run();

  return json({ ok: true, watched: item.title });
}

/** Episode finished on Plex -> mark the show Watching and advance its season/episode progress. */
async function scrobbleEpisodeProgress(md: any, env: Env, owner: string): Promise<Response> {
  const show: string | null = typeof md.grandparentTitle === "string" ? md.grandparentTitle : null;
  const season: number | null = typeof md.parentIndex === "number" ? md.parentIndex : null;
  const ep: number | null = typeof md.index === "number" ? md.index : null;
  if (!show) return json({ ok: true, skipped: "no show title" });

  // Only touch the owner's shows already on the shelf; don't add a series from a single episode play.
  const row = await env.DB
    .prepare("SELECT * FROM items WHERE user_id = ?1 AND kind='tv' AND lower(title) = lower(?2) LIMIT 1")
    .bind(owner, show)
    .first<Item>();
  if (!row) return json({ ok: true, skipped: "show not on shelf", show });

  const curS = row.cur_season ?? 0;
  const curE = row.cur_episode ?? 0;
  const isNewer = season != null && (season > curS || (season === curS && (ep ?? 0) > curE));
  const newS = isNewer ? season : curS;
  const newE = isNewer ? ep : curE;

  // Record progress and (unless already Watched) mark the show Watching.
  const hasWatched = (row.status || "").split(",").map((s) => s.trim()).includes("watched");
  const status = hasWatched ? (row.status || "") : addStatusCsv(row.status, "watching");
  await env.DB
    .prepare("UPDATE items SET status=?1, cur_season=?2, cur_episode=?3, updated_at=?4 WHERE id=?5")
    .bind(status, newS || null, newE || null, Date.now(), row.id)
    .run();

  // Then flip to Watched if that put us at the end of the final season (episode-precise).
  const flipped = await flipIfCaughtUp({ ...row, status, cur_season: newS, cur_episode: newE }, env);

  return json({ ok: true, progress: `${row.title} S${newS}E${newE}`, watched: flipped });
}

async function searchGames(url: URL, env: Env): Promise<Response> {
  const slug = url.searchParams.get("slug");
  const q = url.searchParams.get("q");
  if (!env.RAWG_API_KEY) return err(500, "RAWG_API_KEY not configured");
  if (!slug && !q) return err(400, "q or slug required");

  // Direct slug lookup is always against RAWG (since the slug only identifies a RAWG game).
  if (slug) {
    const r = await fetchWithTimeout(`https://api.rawg.io/api/games/${encodeURIComponent(slug)}?key=${env.RAWG_API_KEY}`);
    if (!r.ok) {
      const body = await r.text().catch(() => "");
      return err(502, `rawg ${r.status}: ${body.slice(0, 200)}`);
    }
    const data = (await r.json()) as any;
    return json({ hits: [data].map(rawgGameToHit) });
  }

  // Run RAWG and IGDB in parallel, each capped so a slow/hung provider is dropped rather than
  // blocking the whole search (which used to hang until the app's socket timeout). RAWG is the
  // primary source, so it gets the longer budget; IGDB is best-effort enrichment.
  const [rawgResult, igdbResult] = await Promise.allSettled([
    withTimeout(rawgSearch(q!, env), 8000, "rawg"),
    withTimeout(igdbSearch(q!, env), 6000, "igdb"),
  ]);
  const rawgHits = rawgResult.status === "fulfilled" ? rawgResult.value : [];
  const igdbHits = igdbResult.status === "fulfilled" ? igdbResult.value : [];

  // Dedupe by lowercased title. IGDB processed first wins on tiebreak (its titles
  // are usually more canonical, e.g. consistent "Halo 5: Guardians" capitalization);
  // RAWG fills in titles IGDB doesn't have.
  const merged: SearchHit[] = [];
  const seen = new Set<string>();
  for (const h of igdbHits.concat(rawgHits)) {
    const key = h.title.trim().toLowerCase();
    if (!key || seen.has(key)) continue;
    seen.add(key);
    merged.push(h);
  }
  return json({
    hits: merged.slice(0, 30),
    _sources: {
      rawg: rawgHits.length,
      igdb: igdbHits.length,
      rawg_error: rawgResult.status === "rejected" ? String(rawgResult.reason).slice(0, 120) : null,
      igdb_error: igdbResult.status === "rejected" ? String(igdbResult.reason).slice(0, 120) : null,
      igdb_configured: !!(env.IGDB_CLIENT_ID && env.IGDB_CLIENT_SECRET),
    },
  });
}

function rawgGameToHit(g: any): SearchHit {
  const platforms = (g.platforms || []).map((p: any) => p.platform?.name).filter(Boolean);
  return {
    external_id: g.slug || String(g.id),
    external_src: "rawg",
    title: g.name || "Untitled",
    subtitle: platforms.length ? platforms.join(", ") : null,
    year: g.released ? Number(g.released.slice(0, 4)) || null : null,
    cover_url: g.background_image || null,
    description: g.description_raw || g.description || null,
  };
}

async function rawgSearch(query: string, env: Env): Promise<SearchHit[]> {
  const api = new URL("https://api.rawg.io/api/games");
  api.searchParams.set("key", env.RAWG_API_KEY);
  api.searchParams.set("search", query);
  api.searchParams.set("page_size", "20");
  const r = await fetchWithTimeout(api.toString(), {}, 7000);
  if (!r.ok) return [];
  const data = (await r.json()) as any;
  const list: any[] = Array.isArray(data.results) ? data.results : [];
  return list.map(rawgGameToHit);
}

// ---------- IGDB via Twitch OAuth ----------

let igdbTokenCache: { token: string; expiresAt: number } | null = null;

async function igdbToken(env: Env): Promise<string | null> {
  if (!env.IGDB_CLIENT_ID || !env.IGDB_CLIENT_SECRET) return null;
  const now = Date.now();
  // 60s grace window so we don't return a token that'll expire mid-call.
  if (igdbTokenCache && igdbTokenCache.expiresAt > now + 60_000) {
    return igdbTokenCache.token;
  }
  const url = new URL("https://id.twitch.tv/oauth2/token");
  url.searchParams.set("client_id", env.IGDB_CLIENT_ID);
  url.searchParams.set("client_secret", env.IGDB_CLIENT_SECRET);
  url.searchParams.set("grant_type", "client_credentials");
  const r = await fetchWithTimeout(url.toString(), { method: "POST" }, 5000);
  if (!r.ok) return null;
  const data = (await r.json()) as any;
  if (!data.access_token) return null;
  const ttlMs = Math.max(60_000, Number(data.expires_in || 3600) * 1000);
  igdbTokenCache = { token: data.access_token, expiresAt: now + ttlMs };
  return data.access_token;
}

async function igdbSearch(query: string, env: Env): Promise<SearchHit[]> {
  if (!env.IGDB_CLIENT_ID || !env.IGDB_CLIENT_SECRET) {
    throw new Error("igdb: IGDB_CLIENT_ID or IGDB_CLIENT_SECRET not set");
  }
  const token = await igdbToken(env);
  if (!token) throw new Error("igdb: twitch token request failed");
  // IGDB uses an apicalypse-style query language in the request body.
  const safe = query.replace(/"/g, '\\"');
  const body =
    `search "${safe}";` +
    ` fields id,name,first_release_date,summary,cover.image_id,platforms.name;` +
    ` limit 25;`;
  const r = await fetchWithTimeout("https://api.igdb.com/v4/games", {
    method: "POST",
    headers: {
      "Client-ID": env.IGDB_CLIENT_ID,
      "Authorization": `Bearer ${token}`,
      "Content-Type": "text/plain",
    },
    body,
  }, 6000);
  if (r.status === 401) {
    igdbTokenCache = null;
    throw new Error("igdb: 401 (cached token stale, will refetch)");
  }
  if (!r.ok) {
    const errBody = await r.text().catch(() => "");
    throw new Error(`igdb ${r.status}: ${errBody.slice(0, 100)}`);
  }
  const list = (await r.json()) as any[];
  return list.map((g: any): SearchHit => {
    const platforms: string[] = Array.isArray(g.platforms)
      ? g.platforms.map((p: any) => p.name).filter(Boolean)
      : [];
    const year = typeof g.first_release_date === "number"
      ? new Date(g.first_release_date * 1000).getUTCFullYear()
      : null;
    const cover = g.cover?.image_id
      ? `https://images.igdb.com/igdb/image/upload/t_cover_big/${g.cover.image_id}.jpg`
      : null;
    return {
      external_id: String(g.id),
      external_src: "igdb",
      title: g.name || "Untitled",
      subtitle: platforms.length ? platforms.join(", ") : null,
      year,
      cover_url: cover,
      description: typeof g.summary === "string" ? g.summary : null,
    };
  });
}

// ---------- image identify via Claude Haiku vision ----------

interface IdentifyResult {
  kind: "book" | "movie" | "tv" | "game" | "unknown";
  title: string;
  year: number | null;
}

const IDENTIFY_PROMPT = `You are identifying media in a photograph -- a book cover, movie poster, TV show poster, or video game cover/box art.

Respond with ONLY one line of JSON in exactly this format, no other text:
{"kind":"book|movie|tv|game|unknown","title":"...","year":<integer or null>}

Rules:
- kind must be exactly one of: book, movie, tv, game, unknown
- title is the work's title as it appears (no subtitle unless part of the official title)
- year is the original release/publication year if you are confident, otherwise null
- If the image isn't a recognizable cover/poster, or you can't identify the work, return {"kind":"unknown","title":"","year":null}
- No markdown, no preamble, no explanation`;

async function identifyImage(req: Request, env: Env): Promise<Response> {
  if (!env.ANTHROPIC_API_KEY) return err(500, "ANTHROPIC_API_KEY not configured");

  const body = (await req.json().catch(() => null)) as { image?: string } | null;
  const b64 = body?.image;
  if (!b64) return err(400, "image (base64 JPEG) required");

  const r = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "x-api-key": env.ANTHROPIC_API_KEY,
      "anthropic-version": "2023-06-01",
      "content-type": "application/json",
    },
    body: JSON.stringify({
      model: "claude-sonnet-5",
      max_tokens: 200,
      messages: [
        {
          role: "user",
          content: [
            {
              type: "image",
              source: { type: "base64", media_type: "image/jpeg", data: b64 },
            },
            { type: "text", text: IDENTIFY_PROMPT },
          ],
        },
      ],
    }),
  });

  if (!r.ok) {
    const errBody = await r.text().catch(() => "");
    return err(502, `claude ${r.status}: ${errBody.slice(0, 200)}`);
  }
  const data = (await r.json()) as any;
  const text = String(data?.content?.[0]?.text ?? "").trim();

  // Be liberal in parsing -- if the model wraps in markdown or adds whitespace.
  const jsonMatch = text.match(/\{[\s\S]*\}/);
  let parsed: IdentifyResult = { kind: "unknown", title: "", year: null };
  if (jsonMatch) {
    try {
      const candidate = JSON.parse(jsonMatch[0]);
      const kind = ["book", "movie", "tv", "game", "unknown"].includes(candidate.kind)
        ? candidate.kind
        : "unknown";
      parsed = {
        kind,
        title: typeof candidate.title === "string" ? candidate.title : "",
        year:
          typeof candidate.year === "number" && Number.isFinite(candidate.year)
            ? candidate.year
            : null,
      };
    } catch {
      // fall through; parsed stays unknown
    }
  }
  return json({ result: parsed });
}

const IDENTIFY_SHELF_PROMPT = `You are looking at a photo of a shelf or row of media -- book spines, video game cases, movie/TV cases, or a group of covers side by side.

Identify EVERY distinct item you can read. Respond with ONLY a JSON array, no other text, in exactly this format:
[{"kind":"book|movie|tv|game|unknown","title":"...","year":<integer or null>}]

Rules:
- One array element per distinct item visible on the shelf.
- kind must be exactly one of: book, movie, tv, game, unknown
- title is the work's title as printed on the spine/cover (no subtitle unless part of the official title)
- year is the original release/publication year if you are confident, otherwise null
- Read spines even at an angle. Skip any item whose title you cannot confidently read.
- If you can't read any items, return []
- No markdown, no preamble, no explanation.`;

/** Identify every readable item in a shelf/row photo (bulk companion to /identify). */
async function identifyShelf(req: Request, env: Env): Promise<Response> {
  if (!env.ANTHROPIC_API_KEY) return err(500, "ANTHROPIC_API_KEY not configured");

  const body = (await req.json().catch(() => null)) as { image?: string } | null;
  const b64 = body?.image;
  if (!b64) return err(400, "image (base64 JPEG) required");

  const r = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "x-api-key": env.ANTHROPIC_API_KEY,
      "anthropic-version": "2023-06-01",
      "content-type": "application/json",
    },
    body: JSON.stringify({
      model: "claude-sonnet-5",
      max_tokens: 1500,
      messages: [
        {
          role: "user",
          content: [
            { type: "image", source: { type: "base64", media_type: "image/jpeg", data: b64 } },
            { type: "text", text: IDENTIFY_SHELF_PROMPT },
          ],
        },
      ],
    }),
  });

  if (!r.ok) {
    const errBody = await r.text().catch(() => "");
    return err(502, `claude ${r.status}: ${errBody.slice(0, 200)}`);
  }
  const data = (await r.json()) as any;
  const text = String(data?.content?.[0]?.text ?? "").trim();

  const match = text.match(/\[[\s\S]*\]/);
  const results: IdentifyResult[] = [];
  if (match) {
    try {
      const arr = JSON.parse(match[0]);
      if (Array.isArray(arr)) {
        for (const c of arr) {
          const title = typeof c?.title === "string" ? c.title.trim() : "";
          if (!title) continue;
          const kind = ["book", "movie", "tv", "game", "unknown"].includes(c?.kind) ? c.kind : "unknown";
          const year =
            typeof c?.year === "number" && Number.isFinite(c.year) ? c.year : null;
          results.push({ kind, title, year });
        }
      }
    } catch {
      // fall through with whatever we parsed
    }
  }
  return json({ results });
}

// ---------- barcode lookup ----------

/**
 * A retail barcode is only ever a product id -- there is no free database that returns good
 * media metadata for one. Books are the exception: an EAN starting 978/979 *is* an ISBN, which
 * Open Library resolves directly.
 *
 * For everything else we go via a generic product database to turn the barcode into the product's
 * name, tidy the retail packaging noise out of it, then run that through the catalogue we already
 * trust (TMDB / RAWG) to get real metadata.
 */
function isIsbn(code: string): boolean {
  const d = code.replace(/[^0-9Xx]/g, "");
  return (d.length === 13 && (d.startsWith("978") || d.startsWith("979"))) || d.length === 10;
}

/** Ask a keyless product database what this barcode is called. Null when it can't say. */
async function upcProductName(code: string): Promise<string | null> {
  // The trial tier needs no key; it is rate limited, so a miss here is normal and must not throw.
  const r = await fetchWithTimeout(
    `https://api.upcitemdb.com/prod/trial/lookup?upc=${encodeURIComponent(code)}`,
    { headers: { accept: "application/json" } },
    7000,
  ).catch(() => null);
  if (!r || !r.ok) return null;
  const data = (await r.json().catch(() => null)) as any;
  const item = data?.items?.[0];
  const title = item?.title ?? item?.brand ?? null;
  return typeof title === "string" && title.trim() ? title.trim() : null;
}

/**
 * Retail titles carry a lot that would wreck a catalogue search: format tags, edition suffixes,
 * platform names, region codes. Strip those back to something close to the work's actual title.
 */
function cleanProductTitle(raw: string): string {
  let t = raw;
  t = t.replace(/\[[^\]]*\]/g, " ");                  // [Blu-ray], [DVD]
  t = t.replace(/\((?:[^)]*(?:blu-?ray|dvd|4k|uhd|hd|edition|region|disc|import|widescreen)[^)]*)\)/gi, " ");
  t = t.replace(
    /\b(blu-?ray|dvd|4k(?:\s*ultra)?(?:\s*hd)?|uhd|steelbook|digital\s*copy|widescreen|fullscreen|region\s*[0-9a-z]|multi-?format|combo\s*pack|box\s*set|w\/\s*digital)\b/gi,
    " ",
  );
  t = t.replace(
    /\b(xbox(?:\s*(?:one|360|series\s*[sx]))?|playstation\s*[1-5]?|ps[1-5]|nintendo(?:\s*switch)?|switch|wii\s*u?|pc(?:\s*dvd)?|steam)\b/gi,
    " ",
  );
  t = t.replace(/\((?:19|20)\d{2}\)/g, " ");           // a bare year confuses catalogue search
  t = t.replace(/\s{2,}/g, " ").trim();
  // Stripping the format tags leaves dangling joiners ("Dune +", "Alien -"); take those off both ends.
  t = t.replace(/^[-–—:|,+&\s]+|[-–—:|,+&\s]+$/g, "").trim();
  return t;
}

/** Resolve a scanned barcode to catalogue hits for the shelf being added to. */
async function lookupBarcode(url: URL, env: Env): Promise<Response> {
  const code = (url.searchParams.get("code") || "").trim();
  const kind = url.searchParams.get("kind") || "";
  if (!code) return err(400, "code required");
  if (!["book", "movie", "tv", "game"].includes(kind)) return err(400, "valid kind required");

  const search = async (q: string): Promise<SearchHit[]> => {
    const u = new URL(`https://shelf.local/search?q=${encodeURIComponent(q)}`);
    const resp = kind === "book" ? await searchBooks(u, env)
      : kind === "game" ? await searchGames(u, env)
      : await searchTmdb(u, env, kind as "movie" | "tv");
    const body = (await resp.json().catch(() => null)) as any;
    return Array.isArray(body?.hits) ? body.hits : [];
  };

  // Books: the barcode is the ISBN, so go straight at it.
  if (kind === "book" && isIsbn(code)) {
    const u = new URL(`https://shelf.local/search?isbn=${encodeURIComponent(code)}`);
    const body = (await (await searchBooks(u, env)).json().catch(() => null)) as any;
    const hits: SearchHit[] = Array.isArray(body?.hits) ? body.hits : [];
    if (hits.length > 0) return json({ hits, via: "isbn" });
  }

  // Everything else: barcode -> product name -> catalogue.
  const product = await upcProductName(code);
  if (!product) {
    return json({ hits: [], via: "upc", matched: null, reason: "barcode not in product database" });
  }
  const title = cleanProductTitle(product);
  let hits = title ? await search(title) : [];
  // Retail names often trail a subtitle the catalogue doesn't use; retry on the leading phrase.
  if (hits.length === 0) {
    const head = title.split(/\s+[-–—:]\s+/)[0]?.trim();
    if (head && head !== title && head.length >= 3) hits = await search(head);
  }
  return json({ hits, via: "upc", matched: product, query: title });
}

// ---------- user-uploaded covers ----------

/** Roughly the largest image we'll accept, before base64 inflates it by a third. */
const MAX_COVER_BYTES = 1_200_000;

/**
 * Store a cover the user picked from their own photos and hand back a URL for it. The app
 * downscales before sending, so this is a guard against something unreasonable rather than the
 * normal path.
 */
async function uploadCustomCover(req: Request, url: URL, env: Env, userId: string): Promise<Response> {
  const body = (await req.json().catch(() => null)) as
    | { image?: string; mime?: string; item_id?: string }
    | null;
  const data = body?.image;
  if (!data) return err(400, "image (base64) required");
  // base64 carries 3 bytes per 4 characters.
  if (data.length * 0.75 > MAX_COVER_BYTES) return err(413, "image too large");

  const mime = body?.mime === "image/png" ? "image/png" : "image/jpeg";
  const id = uuid();
  await env.DB.prepare(
    `INSERT INTO custom_covers (id, user_id, item_id, mime, data, created_at)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6)`,
  )
    .bind(id, userId, body?.item_id ?? null, mime, data, Date.now())
    .run();

  return json({ url: `${url.origin}/covers/${id}`, id }, { status: 201 });
}

/** Serve a stored cover. Immutable: the id is only ever minted for one set of bytes. */
async function serveCustomCover(id: string, env: Env): Promise<Response> {
  const row = await env.DB.prepare("SELECT mime, data FROM custom_covers WHERE id = ?1")
    .bind(id)
    .first<{ mime: string; data: string }>();
  if (!row) return err(404, "cover not found");
  // b64urlToBytes also accepts the standard alphabet, which is what the app sends.
  return new Response(b64urlToBytes(row.data), {
    headers: {
      "content-type": row.mime,
      "cache-control": "public, max-age=31536000, immutable",
      "access-control-allow-origin": "*",
    },
  });
}

// ---------- per-source enrichment at item-create time ----------
const MAX_DESC_CHARS = 1500;

function trimDescription(s: string | null | undefined): string | null {
  if (!s) return null;
  const cleaned = s
    .replace(/<[^>]+>/g, " ")        // strip simple HTML tags
    .replace(/\s+/g, " ")            // collapse whitespace
    .trim();
  if (!cleaned) return null;
  return cleaned.length > MAX_DESC_CHARS
    ? cleaned.slice(0, MAX_DESC_CHARS - 1).trimEnd() + "…"
    : cleaned;
}

async function enrichForCreate(item: Item, env: Env, force = false): Promise<Item> {
  if (!item.external_src || !item.external_id) return item;
  // TV items always fetch (to get season/episode counts even when a description exists);
  // otherwise skip when we already have a description and aren't forcing a refresh.
  const isTv = item.external_src === "tmdb" && item.kind === "tv";
  if (!force && !isTv && item.description && item.description.trim().length > 0) return item;

  try {
    switch (item.external_src) {
      case "open_library":
        return await enrichOpenLibrary(item);
      case "google_books":
        return await enrichGoogleBooks(item, env);
      case "rawg":
        return await enrichRawg(item, env);
      case "tmdb":
        return await enrichTmdb(item, env);
      case "steam":
        return await enrichSteam(item, env);
      default:
        return item;
    }
  } catch {
    return item; // best-effort: enrichment errors never block item creation
  }
}

async function refreshItem(id: string, env: Env, userId: string): Promise<Response> {
  const row = await env.DB.prepare("SELECT * FROM items WHERE id = ?1 AND user_id = ?2")
    .bind(id, userId)
    .first<Item>();
  if (!row) return err(404, "item not found");

  const refreshed = await enrichForCreate(row, env, /* force */ true);

  await env.DB.prepare(
    `UPDATE items
       SET description = ?1,
           cover_url   = COALESCE(?2, cover_url),
           seasons     = COALESCE(?3, seasons),
           episodes    = COALESCE(?4, episodes),
           season_episodes = COALESCE(?5, season_episodes),
           series_status = COALESCE(?6, series_status),
           year        = COALESCE(?7, year),
           updated_at  = ?8
     WHERE id = ?9`,
  )
    .bind(
      refreshed.description ?? null,
      refreshed.cover_url ?? null,
      refreshed.seasons ?? null,
      refreshed.episodes ?? null,
      refreshed.season_episodes ?? null,
      refreshed.series_status ?? null,
      refreshed.year ?? null,
      Date.now(),
      id,
    )
    .run();

  const updated = await env.DB.prepare("SELECT * FROM items WHERE id = ?1").bind(id).first<Item>();
  return json({ item: updated });
}

interface CoverOption {
  url: string;
  label: string;
}

async function listCovers(id: string, env: Env, userId: string): Promise<Response> {
  const row = await env.DB.prepare("SELECT * FROM items WHERE id = ?1 AND user_id = ?2")
    .bind(id, userId)
    .first<Item>();
  if (!row) return err(404, "item not found");

  let covers: CoverOption[] = [];
  try {
    switch (row.external_src) {
      case "tmdb":
        covers = await tmdbCovers(row, env);
        break;
      case "rawg":
        covers = await rawgCovers(row, env);
        break;
      case "igdb":
        covers = await igdbCovers(row, env);
        break;
      case "steam":
        covers = await steamCovers(row, env);
        break;
      case "open_library":
        covers = await openLibraryCovers(row);
        break;
      case "google_books":
        covers = await googleBooksCovers(row, env);
        break;
    }
  } catch {
    covers = [];
  }
  // Always include the currently-stored cover at the front so the user can revert.
  if (row.cover_url) {
    covers = [{ url: row.cover_url, label: "Current" }, ...covers.filter((c) => c.url !== row.cover_url)];
  }
  // Dedupe by url; multiple sources can hand back the same image (especially when
  // SteamGridDB and RAWG agree, or when an upstream returns the same grid twice).
  // Without this the LazyGrid in the app crashes from duplicate item keys.
  const seenUrls = new Set<string>();
  covers = covers.filter((c) => {
    if (!c.url) return false;
    if (seenUrls.has(c.url)) return false;
    seenUrls.add(c.url);
    return true;
  });
  return json({ covers });
}

async function tmdbCovers(item: Item, env: Env): Promise<CoverOption[]> {
  if (!env.TMDB_API_KEY) return [];
  const path = item.kind === "movie" ? "movie" : item.kind === "tv" ? "tv" : null;
  if (!path) return [];
  const r = await fetch(
    `https://api.themoviedb.org/3/${path}/${item.external_id}/images?api_key=${env.TMDB_API_KEY}&include_image_language=en,null`,
  );
  if (!r.ok) return [];
  const d = (await r.json()) as any;
  const posters: any[] = Array.isArray(d.posters) ? d.posters : [];
  return posters.slice(0, 16).map((p, i) => ({
    url: `https://image.tmdb.org/t/p/w500${p.file_path}`,
    label: p.iso_639_1 ? `Poster (${String(p.iso_639_1).toUpperCase()})` : `Poster ${i + 1}`,
  }));
}

async function rawgCovers(item: Item, env: Env): Promise<CoverOption[]> {
  if (!env.RAWG_API_KEY || !item.external_id) return [];
  const slug = encodeURIComponent(item.external_id);
  const opts: CoverOption[] = [];

  // SteamGridDB box art first (these are the proper game covers users want).
  // Fail-soft: if the key isn't set or the lookup misses, fall through to RAWG.
  const sgdb = await steamGridDbCovers(item, env);
  opts.push(...sgdb);

  const detailR = await fetch(`https://api.rawg.io/api/games/${slug}?key=${env.RAWG_API_KEY}`);
  if (detailR.ok) {
    const d = (await detailR.json()) as any;
    if (d.background_image) opts.push({ url: d.background_image, label: "Main artwork" });
    if (d.background_image_additional) {
      opts.push({ url: d.background_image_additional, label: "Alternate artwork" });
    }
  }

  const ssR = await fetch(`https://api.rawg.io/api/games/${slug}/screenshots?key=${env.RAWG_API_KEY}`);
  if (ssR.ok) {
    const data = (await ssR.json()) as any;
    const shots: any[] = Array.isArray(data.results) ? data.results : [];
    shots.slice(0, 10).forEach((s, i) => {
      if (s?.image) opts.push({ url: s.image, label: `Screenshot ${i + 1}` });
    });
  }

  return opts;
}

/**
 * Review scores for a game. IGDB exposes two aggregate ratings (0-100): `rating` (IGDB
 * users) and `aggregated_rating` (external critics). We surface both with their counts.
 * Returns nulls for non-IGDB items so the client can simply hide the section.
 */
async function itemScores(id: string, env: Env, userId: string): Promise<Response> {
  const row = await env.DB.prepare("SELECT * FROM items WHERE id = ?1 AND user_id = ?2")
    .bind(id, userId)
    .first<Item>();
  if (!row) return err(404, "item not found");

  const empty = { players: null, playersCount: null, critics: null, criticsCount: null };

  // Steam games carry a Metacritic critic score in the store API (no IGDB match).
  if (row.external_src === "steam" && row.external_id) {
    const d = await steamStoreDetails(row.external_id);
    const score = typeof d?.metacritic?.score === "number" ? d.metacritic.score : null;
    return json({ scores: { ...empty, critics: score } });
  }

  if (row.external_src !== "igdb" || !row.external_id || !env.IGDB_CLIENT_ID) {
    return json({ scores: empty });
  }
  const token = await igdbToken(env);
  if (!token) return json({ scores: empty });

  const body =
    `fields rating,rating_count,aggregated_rating,aggregated_rating_count;` +
    ` where id = ${Number(row.external_id) || 0};`;
  const r = await fetchWithTimeout("https://api.igdb.com/v4/games", {
    method: "POST",
    headers: {
      "Client-ID": env.IGDB_CLIENT_ID,
      "Authorization": `Bearer ${token}`,
      "Content-Type": "text/plain",
    },
    body,
  }, 6000);
  if (r.status === 401) igdbTokenCache = null;
  if (!r.ok) return json({ scores: empty });
  const list = (await r.json()) as any[];
  const g = list[0];
  if (!g) return json({ scores: empty });

  const round = (n: any) => (typeof n === "number" ? Math.round(n) : null);
  return json({
    scores: {
      players: round(g.rating),
      playersCount: typeof g.rating_count === "number" ? g.rating_count : null,
      critics: round(g.aggregated_rating),
      criticsCount: typeof g.aggregated_rating_count === "number" ? g.aggregated_rating_count : null,
    },
  });
}

async function igdbCovers(item: Item, env: Env): Promise<CoverOption[]> {
  const opts: CoverOption[] = [];

  // SteamGridDB by title first (its box art is title-keyed, so it works for
  // any source).
  opts.push(...(await steamGridDbCovers(item, env)));

  if (!env.IGDB_CLIENT_ID || !item.external_id) return opts;
  const token = await igdbToken(env);
  if (!token) return opts;

  // One call returns cover + every artwork + every screenshot for the game.
  const body = `fields cover.image_id,artworks.image_id,screenshots.image_id; where id = ${Number(item.external_id) || 0};`;
  const r = await fetchWithTimeout("https://api.igdb.com/v4/games", {
    method: "POST",
    headers: {
      "Client-ID": env.IGDB_CLIENT_ID,
      "Authorization": `Bearer ${token}`,
      "Content-Type": "text/plain",
    },
    body,
  }, 6000);
  if (r.status === 401) igdbTokenCache = null;
  if (!r.ok) return opts;
  const list = (await r.json()) as any[];
  const g = list[0];
  if (!g) return opts;

  const toUrl = (imageId: string) =>
    `https://images.igdb.com/igdb/image/upload/t_1080p/${imageId}.jpg`;

  if (g.cover?.image_id) opts.push({ url: toUrl(g.cover.image_id), label: "Main cover" });

  const artworks: any[] = Array.isArray(g.artworks) ? g.artworks : [];
  artworks.slice(0, 8).forEach((a, i) => {
    if (a?.image_id) opts.push({ url: toUrl(a.image_id), label: `Artwork ${i + 1}` });
  });

  const screenshots: any[] = Array.isArray(g.screenshots) ? g.screenshots : [];
  screenshots.slice(0, 8).forEach((s, i) => {
    if (s?.image_id) opts.push({ url: toUrl(s.image_id), label: `Screenshot ${i + 1}` });
  });

  return opts;
}

/** Fetch a game's Steam store details (description, metacritic, etc.) by app id. */
async function steamStoreDetails(appId: string): Promise<any | null> {
  const r = await fetchWithTimeout(
    `https://store.steampowered.com/api/appdetails?appids=${appId}&l=english`,
    {},
    7000,
  );
  if (!r.ok) return null;
  const data = (await r.json()) as any;
  const entry = data?.[appId];
  return entry && entry.success ? entry.data ?? null : null;
}

/** Quick liveness check for a remote asset (used to tell a working cover from a 404). */
async function urlOk(url: string): Promise<boolean> {
  try {
    const r = await fetchWithTimeout(url, {}, 5000);
    return r.ok;
  } catch {
    return false;
  }
}

/**
 * Steam-imported game: fill in the description from the Steam store. Keep the game's existing
 * Steam portrait when it actually resolves — only reach for SteamGridDB box art (or the Steam
 * header) when the current cover is missing/broken, so we never clobber good official art.
 */
async function enrichSteam(item: Item, env: Env): Promise<Item> {
  const details = item.external_id ? await steamStoreDetails(item.external_id) : null;
  const desc = trimDescription(details?.short_description) ?? trimDescription(details?.about_the_game);
  const year = item.year ?? steamReleaseYear(details?.release_date?.date);

  let cover = item.cover_url ?? null;
  if (!cover || !(await urlOk(cover))) {
    const covers = await steamGridDbCovers(item, env);
    cover = covers.find((c) => c.url)?.url ?? details?.header_image ?? cover;
  }
  return {
    ...item,
    cover_url: cover,
    description: desc ?? item.description,
    year,
  };
}

/** Pull a 4-digit release year out of Steam's free-form release date (e.g. "23 Nov, 2018"). */
function steamReleaseYear(date?: string | null): number | null {
  if (!date) return null;
  const m = String(date).match(/\b(19|20)\d{2}\b/);
  return m ? Number(m[0]) : null;
}

/** Cover choices for a Steam game: official Steam portrait/header + SteamGridDB box art. */
async function steamCovers(item: Item, env: Env): Promise<CoverOption[]> {
  const opts: CoverOption[] = [];
  if (item.external_id) {
    const base = `https://cdn.cloudflare.steamstatic.com/steam/apps/${item.external_id}`;
    opts.push({ url: `${base}/library_600x900.jpg`, label: "Steam portrait" });
    opts.push({ url: `${base}/header.jpg`, label: "Steam header" });
  }
  opts.push(...(await steamGridDbCovers(item, env)));
  return opts;
}

async function steamGridDbCovers(item: Item, env: Env): Promise<CoverOption[]> {
  const key = env.STEAMGRIDDB_API_KEY;
  if (!key) return [];

  const auth = { authorization: `Bearer ${key}` };

  // 1) Find the SteamGridDB game id. For Steam-imported games resolve it EXACTLY by app id;
  //    otherwise fall back to a title search.
  let gameId: number | null = null;
  if (item.external_src === "steam" && item.external_id) {
    const byApp = await fetchWithTimeout(
      `https://www.steamgriddb.com/api/v2/games/steam/${item.external_id}`,
      { headers: auth },
      7000,
    );
    if (byApp.ok) {
      const d = (await byApp.json()) as any;
      gameId = typeof d?.data?.id === "number" ? d.data.id : null;
    }
  }
  if (gameId == null) {
    if (!item.title) return [];
    const search = await fetchWithTimeout(
      `https://www.steamgriddb.com/api/v2/search/autocomplete/${encodeURIComponent(item.title)}`,
      { headers: auth },
      7000,
    );
    if (!search.ok) return [];
    const sdata = (await search.json()) as any;
    const games: any[] = Array.isArray(sdata?.data) ? sdata.data : [];
    if (games.length === 0) return [];
    gameId = games[0].id;
  }

  // 2) Fetch portrait box-art grids (the 600x900 family of dimensions).
  const grids = await fetch(
    `https://www.steamgriddb.com/api/v2/grids/game/${gameId}?dimensions=600x900,342x482,660x930&types=static&limit=16`,
    { headers: auth },
  );
  if (!grids.ok) return [];
  const gdata = (await grids.json()) as any;
  const list: any[] = Array.isArray(gdata?.data) ? gdata.data : [];
  return list.slice(0, 12).map((g, i) => ({
    url: g.url || g.thumb,
    label: `Box art ${i + 1}`,
  }));
}

async function openLibraryCovers(item: Item): Promise<CoverOption[]> {
  if (!item.external_id) return [];
  const isIsbn = /^\d{10,13}$/.test(item.external_id);
  let workId: string | null = null;

  if (isIsbn) {
    const r = await fetch(`https://openlibrary.org/isbn/${item.external_id}.json`, {
      headers: { "user-agent": "media-shelf/0.1 (kzaller.com)" },
    });
    if (r.ok) {
      const data = (await r.json()) as any;
      const w = Array.isArray(data?.works) ? data.works[0] : null;
      if (w?.key) workId = String(w.key).replace("/works/", "");
    }
  } else {
    workId = item.external_id;
  }

  if (!workId) return [];
  const er = await fetch(`https://openlibrary.org/works/${workId}/editions.json?limit=40`, {
    headers: { "user-agent": "media-shelf/0.1 (kzaller.com)" },
  });
  if (!er.ok) return [];
  const data = (await er.json()) as any;
  const editions: any[] = Array.isArray(data.entries) ? data.entries : [];

  const seen = new Set<number>();
  const opts: CoverOption[] = [];
  for (const ed of editions) {
    const covers: number[] = Array.isArray(ed.covers) ? ed.covers : [];
    for (const c of covers) {
      if (c > 0 && !seen.has(c)) {
        seen.add(c);
        opts.push({
          url: `https://covers.openlibrary.org/b/id/${c}-L.jpg`,
          label: ed.publish_date || `Edition`,
        });
        if (opts.length >= 16) break;
      }
    }
    if (opts.length >= 16) break;
  }
  return opts;
}

/** Google Books usually hands us a synopsis up front; this covers the case where it didn't. */
async function enrichGoogleBooks(item: Item, env: Env): Promise<Item> {
  const id = item.external_id;
  if (!id) return item;
  // external_id is an ISBN when the volume had one, otherwise a Google volume id.
  const q = /^\d{10,13}$/.test(id) ? `isbn:${id}` : `id:${id}`;
  const hits = await googleBooksHits(q, env, 1);
  const desc = hits[0]?.description;
  return desc ? { ...item, description: desc } : item;
}

/** The same jacket at a few sizes, so the cover picker has something to offer. */
async function googleBooksCovers(item: Item, env: Env): Promise<CoverOption[]> {
  const id = item.external_id;
  if (!id) return [];
  const q = /^\d{10,13}$/.test(id) ? `isbn:${id}` : `id:${id}`;
  const hit = (await googleBooksHits(q, env, 1))[0];
  if (!hit?.cover_url) return [];
  return [
    { url: hit.cover_url.replace(/&zoom=\d+/, "&zoom=1"), label: "Google Books" },
    { url: hit.cover_url.replace(/&zoom=\d+/, "&zoom=0"), label: "Google Books (large)" },
  ];
}

async function enrichOpenLibrary(item: Item): Promise<Item> {
  // external_id is either an ISBN (digits) or a work id like "OL45804W".
  const isIsbn = /^\d{10,13}$/.test(item.external_id || "");
  let workId: string | null = null;

  if (isIsbn) {
    // ISBN endpoint returns the editions; it points us at the canonical work.
    const r = await fetch(`https://openlibrary.org/isbn/${item.external_id}.json`, {
      headers: { "user-agent": "media-shelf/0.1 (kzaller.com)" },
    });
    if (r.ok) {
      const data = (await r.json()) as any;
      const w = Array.isArray(data?.works) ? data.works[0] : null;
      if (w?.key) workId = String(w.key).replace("/works/", "");
    }
  } else {
    workId = item.external_id ?? null;
  }

  if (!workId) return item;
  const wr = await fetch(`https://openlibrary.org/works/${workId}.json`, {
    headers: { "user-agent": "media-shelf/0.1 (kzaller.com)" },
  });
  if (!wr.ok) return item;
  const w = (await wr.json()) as any;

  // Open Library returns description as either a plain string or an object
  // { value: "...", type: "/type/text" }.
  const rawDesc: string | null =
    typeof w?.description === "string"
      ? w.description
      : typeof w?.description?.value === "string"
        ? w.description.value
        : null;

  return { ...item, description: trimDescription(rawDesc) ?? item.description };
}

async function enrichRawg(item: Item, env: Env): Promise<Item> {
  if (!env.RAWG_API_KEY) return item;
  const slug = encodeURIComponent(item.external_id || "");
  const r = await fetchWithTimeout(`https://api.rawg.io/api/games/${slug}?key=${env.RAWG_API_KEY}`, {}, 7000);
  if (!r.ok) return item;
  const g = (await r.json()) as any;

  return {
    ...item,
    description:
      trimDescription(g.description_raw) ??
      trimDescription(g.description) ??
      item.description,
    // RAWG's detail endpoint often has a higher-quality background image.
    cover_url: item.cover_url || g.background_image || null,
  };
}

async function enrichTmdb(item: Item, env: Env): Promise<Item> {
  // Most TMDB descriptions are already on the search result `overview`. Re-fetch
  // only when we somehow ended up without one.
  if (!env.TMDB_API_KEY) return item;
  const kindPath = item.kind === "movie" ? "movie" : item.kind === "tv" ? "tv" : null;
  if (!kindPath) return item;
  const url = `https://api.themoviedb.org/3/${kindPath}/${item.external_id}?api_key=${env.TMDB_API_KEY}`;
  const r = await fetch(url);
  if (!r.ok) return item;
  const d = (await r.json()) as any;
  const enriched: Item = { ...item, description: trimDescription(d.overview) ?? item.description };
  if (kindPath === "tv") {
    enriched.seasons = typeof d.number_of_seasons === "number" ? d.number_of_seasons : item.seasons ?? null;
    enriched.episodes = typeof d.number_of_episodes === "number" ? d.number_of_episodes : item.episodes ?? null;
    if (Array.isArray(d.seasons)) {
      const pairs = d.seasons
        .filter((s: any) => typeof s.season_number === "number" && s.season_number > 0 && typeof s.episode_count === "number")
        .map((s: any) => `${s.season_number}:${s.episode_count}`);
      if (pairs.length) enriched.season_episodes = pairs.join(",");
    }
    enriched.series_status = seriesStatus(d.status) ?? item.series_status ?? null;
  }
  return enriched;
}

/**
 * Reduce TMDB's series status to the only distinction that matters here: is there more of this
 * coming or not. TMDB uses "Returning Series", "Ended", "Canceled", "In Production", "Planned"
 * and "Pilot" -- a cancelled show is finished from a viewer's point of view, and one still in
 * production has more to come, so the six collapse to two. Anything unrecognised is left null
 * rather than guessed at.
 */
function seriesStatus(raw: unknown): "continuing" | "ended" | null {
  if (typeof raw !== "string") return null;
  switch (raw.trim().toLowerCase()) {
    case "returning series":
    case "in production":
    case "planned":
    case "pilot":
      return "continuing";
    case "ended":
    case "canceled":
    case "cancelled":
      return "ended";
    default:
      return null;
  }
}

// ---------- public read-only HTML view at /k ----------

async function publicShelves(env: Env): Promise<Response> {
  // The public view shows only the owner's shelf (the account that claimed the legacy library),
  // falling back to the legacy bucket before any Google sign-in has happened.
  const owner = (await settingGetGlobal(env, "owner_user_id")) ?? LEGACY_USER;
  const { results } = await env.DB
    .prepare("SELECT * FROM items WHERE user_id = ?1 ORDER BY kind, added_at DESC")
    .bind(owner)
    .all<Item>();
  return new Response(renderShelvesHtml(results || []), {
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "public, max-age=60",
    },
  });
}

function htmlEscape(s: string | null | undefined): string {
  if (s == null) return "";
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]!));
}

const KIND_META: Record<string, { label: string; accent: string; bg: string }> = {
  book:  { label: "Books",  accent: "#E5C07B", bg: "linear-gradient(180deg,#3B2310,#A56A2C)" },
  movie: { label: "Movies", accent: "#E4C46B", bg: "linear-gradient(180deg,#050714,#1C2541)" },
  tv:    { label: "TV",     accent: "#52FF8A", bg: "linear-gradient(180deg,#021008,#0F3B22)" },
  game:  { label: "Games",  accent: "#FF3DBE", bg: "linear-gradient(180deg,#06021A,#3A0E5C)" },
};

function renderShelvesHtml(items: Item[]): string {
  const groups: Record<string, Item[]> = { book: [], movie: [], tv: [], game: [] };
  for (const it of items) {
    if (it.kind in groups) groups[it.kind].push(it);
  }

  let body = "";
  for (const kind of ["book", "movie", "tv", "game"] as const) {
    const list = groups[kind];
    if (list.length === 0) continue;
    const meta = KIND_META[kind];
    body += `<section class="shelf" style="background:${meta.bg}">
      <h2 style="color:${meta.accent}">${meta.label} <span class="count">${list.length}</span></h2>
      <div class="grid">`;
    for (const it of list) {
      const cover = it.cover_url
        ? `<div class="cover"><img src="${htmlEscape(it.cover_url)}" alt="" loading="lazy"></div>`
        : `<div class="cover no-cover" style="color:${meta.accent}">${htmlEscape(it.title.slice(0, 2).toUpperCase())}</div>`;
      const sub = [it.subtitle, it.year].filter(Boolean).join(" · ");
      body += `<article class="card">${cover}<h3>${htmlEscape(it.title)}</h3>${sub ? `<p>${htmlEscape(sub)}</p>` : ""}</article>`;
    }
    body += "</div></section>";
  }

  if (body === "") {
    body = `<section class="shelf" style="background:#171423">
      <p style="color:#bbb;padding:24px">Nothing on the shelves yet.</p>
    </section>`;
  }

  const css = `
    *,*::before,*::after { box-sizing: border-box; }
    body { margin: 0; background: #0E0F14; color: #E8E8EA; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
    header { padding: 28px 20px 12px; }
    h1 { margin: 0; font-size: 28px; letter-spacing: 6px; color: #E5C07B; font-weight: 900; }
    h1 span { display: block; font-weight: 300; letter-spacing: 2px; font-size: 20px; color: #E8E8EA; margin-top: 4px; }
    .shelf { margin: 16px; padding: 18px 16px 22px; border-radius: 16px; }
    .shelf h2 { margin: 0 0 14px; font-size: 22px; }
    .shelf h2 .count { font-size: 14px; opacity: 0.7; font-weight: 400; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 14px; }
    .card { background: rgba(0,0,0,0.35); border-radius: 8px; overflow: hidden; padding-bottom: 8px; }
    .cover { width: 100%; aspect-ratio: 2/3; background: rgba(0,0,0,0.25); display: grid; place-items: center; }
    .cover.no-cover { font-weight: 900; font-size: 28px; }
    .cover img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .card h3 { margin: 8px 10px 2px; font-size: 14px; font-weight: 600; }
    .card p { margin: 0 10px; font-size: 12px; color: rgba(255,255,255,0.6); }
    footer { padding: 24px 20px 32px; color: rgba(255,255,255,0.5); font-size: 12px; }
  `;

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>Media Shelf</title>
<style>${css}</style>
</head>
<body>
<header><h1>MEDIA<span>shelf</span></h1></header>
${body}
<footer>Read-only public view. Sharing is from media.kzaller.com.</footer>
</body>
</html>`;
}
