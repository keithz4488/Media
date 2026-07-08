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
}

type Kind = "book" | "movie" | "tv" | "game";

interface Item {
  id: string;
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

function authed(req: Request, env: Env): boolean {
  const h = req.headers.get("authorization") || "";
  const token = h.startsWith("Bearer ") ? h.slice(7) : "";
  return !!env.SHELF_TOKEN && token === env.SHELF_TOKEN;
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
    // Public read-only shelf view -- intentionally pre-auth.
    if (url.pathname === "/k" || url.pathname === "/k/") return publicShelves(env);
    // Plex webhook: Plex can't send an Authorization header, so this route authenticates via a
    // secret query param (?token=) instead of the usual Bearer check.
    if (url.pathname === "/plex/webhook" && req.method === "POST") return plexWebhook(req, url, env);

    if (!authed(req, env)) return err(401, "unauthorized");

    try {
      if (url.pathname === "/items") {
        if (req.method === "GET") return listItems(url, env);
        if (req.method === "POST") return createItem(req, env);
      }

      if (url.pathname === "/items/bulk" && req.method === "POST") return bulkCreate(req, env);

      const idMatch = url.pathname.match(/^\/items\/([^/]+)$/);
      if (idMatch) {
        const id = idMatch[1];
        if (req.method === "PATCH") return updateItem(id, req, env);
        if (req.method === "DELETE") return deleteItem(id, env);
      }

      const refreshMatch = url.pathname.match(/^\/items\/([^/]+)\/refresh$/);
      if (refreshMatch && req.method === "POST") return refreshItem(refreshMatch[1], env);

      const coversMatch = url.pathname.match(/^\/items\/([^/]+)\/covers$/);
      if (coversMatch && req.method === "GET") return listCovers(coversMatch[1], env);

      const scoresMatch = url.pathname.match(/^\/items\/([^/]+)\/scores$/);
      if (scoresMatch && req.method === "GET") return itemScores(scoresMatch[1], env);

      if (url.pathname === "/search/books") return searchBooks(url, env);
      if (url.pathname === "/search/movies") return searchTmdb(url, env, "movie");
      if (url.pathname === "/search/tv") return searchTmdb(url, env, "tv");
      if (url.pathname === "/search/games") return searchGames(url, env);
      if (url.pathname === "/identify" && req.method === "POST") return identifyImage(req, env);

      return err(404, "not found");
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      return err(500, msg);
    }
  },
} satisfies ExportedHandler<Env>;

// ---------- shelf CRUD ----------

async function listItems(url: URL, env: Env): Promise<Response> {
  const kind = url.searchParams.get("kind");
  const stmt = kind
    ? env.DB.prepare("SELECT * FROM items WHERE kind = ?1 ORDER BY added_at DESC").bind(kind)
    : env.DB.prepare("SELECT * FROM items ORDER BY added_at DESC");
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
async function bulkCreate(req: Request, env: Env): Promise<Response> {
  const body = (await req.json().catch(() => null)) as { items?: Partial<Item>[] } | null;
  const list = body?.items;
  if (!Array.isArray(list) || list.length === 0) return err(400, "items array required");
  if (list.length > 200) return err(400, "max 200 items per request");

  const now = Date.now();
  const stmt = env.DB.prepare(
    `INSERT INTO items
      (id, kind, title, subtitle, year, cover_url, external_id, external_src,
       description, rating, status, notes, user_platform, consoles, format,
       seasons, episodes, cur_season, cur_episode, completed_at, added_at, updated_at)
     VALUES
      (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?17,?18,?19,?20,?21,?22)
     ON CONFLICT(kind, external_src, external_id) DO UPDATE SET
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

async function createItem(req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as Partial<Item>;
  if (!body.kind || !body.title) return err(400, "kind and title required");

  const now = Date.now();
  let item: Item = {
    id: body.id || uuid(),
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
      (id, kind, title, subtitle, year, cover_url, external_id, external_src,
       description, rating, status, notes, user_platform, consoles, format,
       seasons, episodes, cur_season, cur_episode, completed_at, added_at, updated_at)
     VALUES
      (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?17,?18,?19,?20,?21,?22)
     ON CONFLICT(kind, external_src, external_id) DO UPDATE SET
       updated_at = excluded.updated_at,
       status     = excluded.status`,
  )
    .bind(
      item.id,
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
      item.added_at,
      item.updated_at,
    )
    .run();

  // Return the canonical row. If external_id is set we look it up via the natural
  // key (catches the upsert case where the existing row's id wins). Otherwise by id.
  const row = item.external_id
    ? await env.DB.prepare(
        "SELECT * FROM items WHERE kind=?1 AND external_src=?2 AND external_id=?3 LIMIT 1",
      )
        .bind(item.kind, item.external_src, item.external_id)
        .first<Item>()
    : await env.DB.prepare("SELECT * FROM items WHERE id=?1").bind(item.id).first<Item>();

  return json({ item: row || item }, { status: 201 });
}

async function updateItem(id: string, req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as Partial<Item>;
  const fields: string[] = [];
  const values: unknown[] = [];
  let i = 1;
  for (const k of ["title", "subtitle", "year", "cover_url", "description", "rating", "status", "notes", "user_platform", "consoles", "format", "seasons", "episodes", "cur_season", "cur_episode", "completed_at", "show_to"] as const) {
    if (k in body) {
      fields.push(`${k} = ?${i++}`);
      values.push(body[k] ?? null);
    }
  }
  if (!fields.length) return err(400, "no fields to update");
  fields.push(`updated_at = ?${i++}`);
  values.push(Date.now());
  values.push(id);

  const res = await env.DB.prepare(`UPDATE items SET ${fields.join(", ")} WHERE id = ?${i}`)
    .bind(...values)
    .run();
  if (res.meta.changes === 0) return err(404, "item not found");

  const row = await env.DB.prepare("SELECT * FROM items WHERE id = ?1").bind(id).first<Item>();
  return json({ item: row });
}

async function deleteItem(id: string, env: Env): Promise<Response> {
  const res = await env.DB.prepare("DELETE FROM items WHERE id = ?1").bind(id).run();
  if (res.meta.changes === 0) return err(404, "item not found");
  return json({ ok: true });
}

// ---------- external lookups ----------

async function searchBooks(url: URL, env: Env): Promise<Response> {
  // Open Library: keyless, no quota issues, mature search + cover CDN.
  const isbn = url.searchParams.get("isbn");
  const q = url.searchParams.get("q");
  if (!isbn && !q) return err(400, "q or isbn required");

  const api = new URL("https://openlibrary.org/search.json");
  if (isbn) api.searchParams.set("isbn", isbn);
  else api.searchParams.set("q", q!);
  api.searchParams.set("limit", "20");
  api.searchParams.set(
    "fields",
    "key,title,subtitle,author_name,first_publish_year,isbn,cover_i",
  );

  const r = await fetch(api.toString(), { headers: { "user-agent": "media-shelf/0.1 (kzaller.com)" } });
  if (!r.ok) {
    const body = await r.text().catch(() => "");
    return err(502, `open library ${r.status}: ${body.slice(0, 200)}`);
  }
  const data = (await r.json()) as any;
  const docs: any[] = data.docs || [];

  const hits: SearchHit[] = docs.map((d: any): SearchHit => {
    const firstIsbn = Array.isArray(d.isbn) && d.isbn.length > 0 ? String(d.isbn[0]) : null;
    const workId = d.key ? String(d.key).replace("/works/", "") : "";
    const externalId = firstIsbn || workId;
    const cover = d.cover_i ? `https://covers.openlibrary.org/b/id/${d.cover_i}-L.jpg` : null;
    const authors = Array.isArray(d.author_name) ? d.author_name.join(", ") : null;
    const subtitle = [authors, d.subtitle].filter(Boolean).join(" · ") || null;
    return {
      external_id: externalId,
      external_src: "open_library",
      title: d.title || "Untitled",
      subtitle,
      year: typeof d.first_publish_year === "number" ? d.first_publish_year : null,
      cover_url: cover,
      description: null,
    };
  });
  return json({ hits });
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
async function plexWebhook(req: Request, url: URL, env: Env): Promise<Response> {
  const secret = url.searchParams.get("token") || url.searchParams.get("s");
  // Prefer a dedicated webhook secret (so the app's main token stays out of Plex config/logs),
  // but fall back to SHELF_TOKEN if no dedicated one is set.
  const expected = env.PLEX_WEBHOOK_SECRET || env.SHELF_TOKEN;
  if (!expected || secret !== expected) return err(401, "unauthorized");

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

  // "Watched" sync: Plex scrobbles when playback finishes (~90%). A movie scrobble means the
  // movie is watched; an episode scrobble advances the show's progress (a whole series can't be
  // flipped to Watched from webhooks alone -- that needs the server's episode counts).
  if (event === "media.scrobble" && type === "movie") return scrobbleMovieWatched(md, env);
  if (event === "media.scrobble" && type === "episode") return scrobbleEpisodeProgress(md, env);

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
      (id, kind, title, subtitle, year, cover_url, external_id, external_src,
       description, rating, status, notes, user_platform, consoles, format,
       seasons, episodes, cur_season, cur_episode, completed_at, added_at, updated_at)
     VALUES
      (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?17,?18,?19,?20,?21,?22)
     ON CONFLICT(kind, external_src, external_id) DO NOTHING`,
  )
    .bind(
      item.id, item.kind, item.title, item.subtitle, item.year, item.cover_url,
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

/** Movie finished on Plex -> mark it Watched (adding it as owned+digital if it's not on a shelf yet). */
async function scrobbleMovieWatched(md: any, env: Env): Promise<Response> {
  const tmdbId = extractTmdbFromMetadata(md);
  const title: string | null = typeof md.title === "string" ? md.title : null;
  let hit = tmdbId ? await tmdbLookup("movie", { id: tmdbId }, env) : null;
  if (!hit && title) hit = await tmdbLookup("movie", { q: title }, env);
  if (!hit) return json({ ok: true, skipped: "no tmdb match", title });

  const now = Date.now();
  let item: Item = {
    id: uuid(),
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
      (id, kind, title, subtitle, year, cover_url, external_id, external_src,
       description, rating, status, notes, user_platform, consoles, format,
       seasons, episodes, cur_season, cur_episode, completed_at, added_at, updated_at)
     VALUES
      (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?17,?18,?19,?20,?21,?22)
     ON CONFLICT(kind, external_src, external_id) DO UPDATE SET
       status = CASE
         WHEN COALESCE(items.status,'') LIKE '%watched%' THEN items.status
         ELSE TRIM(COALESCE(items.status,'') || ',watched', ',')
       END,
       completed_at = COALESCE(items.completed_at, excluded.completed_at),
       updated_at = excluded.updated_at`,
  )
    .bind(
      item.id, item.kind, item.title, item.subtitle, item.year, item.cover_url,
      item.external_id, item.external_src, item.description, item.rating, item.status,
      item.notes, item.user_platform, item.consoles, item.format, item.seasons,
      item.episodes, item.cur_season, item.cur_episode, item.completed_at,
      item.added_at, item.updated_at,
    )
    .run();

  return json({ ok: true, watched: item.title });
}

/** Episode finished on Plex -> mark the show Watching and advance its season/episode progress. */
async function scrobbleEpisodeProgress(md: any, env: Env): Promise<Response> {
  const show: string | null = typeof md.grandparentTitle === "string" ? md.grandparentTitle : null;
  const season: number | null = typeof md.parentIndex === "number" ? md.parentIndex : null;
  const ep: number | null = typeof md.index === "number" ? md.index : null;
  if (!show) return json({ ok: true, skipped: "no show title" });

  // Only touch shows already on the shelf; don't add a series from a single episode play.
  const row = await env.DB
    .prepare("SELECT * FROM items WHERE kind='tv' AND lower(title) = lower(?1) LIMIT 1")
    .bind(show)
    .first<Item>();
  if (!row) return json({ ok: true, skipped: "show not on shelf", show });

  const curS = row.cur_season ?? 0;
  const curE = row.cur_episode ?? 0;
  const isNewer = season != null && (season > curS || (season === curS && (ep ?? 0) > curE));
  const newS = isNewer ? season : curS;
  const newE = isNewer ? ep : curE;

  const hasWatched = (row.status || "").split(",").map((s) => s.trim()).includes("watched");
  // "Caught up" = on/past the final season (the same definition the app displays). When progress
  // reaches it, flip the show to Watched and drop the Watching flag.
  const caughtUp = (row.seasons ?? 0) > 0 && (newS ?? 0) >= (row.seasons ?? 0);
  const now = Date.now();

  let status: string;
  let completedAt = row.completed_at ?? null;
  if (hasWatched) {
    status = row.status || "";
  } else if (caughtUp) {
    status = addStatusCsv(removeStatusCsv(row.status, "watching"), "watched");
    completedAt = completedAt ?? now;
  } else {
    status = addStatusCsv(row.status, "watching");
  }

  await env.DB
    .prepare("UPDATE items SET status=?1, cur_season=?2, cur_episode=?3, completed_at=?4, updated_at=?5 WHERE id=?6")
    .bind(status, newS || null, newE || null, completedAt, now, row.id)
    .run();

  return json({ ok: true, progress: `${row.title} S${newS}E${newE}`, caughtUp });
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

// ---------- per-source enrichment at item-create time ----------

/** Cap free-form description fields so a single huge synopsis can't bloat the row. */
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
      case "rawg":
        return await enrichRawg(item, env);
      case "tmdb":
        return await enrichTmdb(item, env);
      default:
        return item;
    }
  } catch {
    return item; // best-effort: enrichment errors never block item creation
  }
}

async function refreshItem(id: string, env: Env): Promise<Response> {
  const row = await env.DB.prepare("SELECT * FROM items WHERE id = ?1").bind(id).first<Item>();
  if (!row) return err(404, "item not found");

  const refreshed = await enrichForCreate(row, env, /* force */ true);

  await env.DB.prepare(
    `UPDATE items
       SET description = ?1,
           cover_url   = COALESCE(?2, cover_url),
           seasons     = COALESCE(?3, seasons),
           episodes    = COALESCE(?4, episodes),
           updated_at  = ?5
     WHERE id = ?6`,
  )
    .bind(
      refreshed.description ?? null,
      refreshed.cover_url ?? null,
      refreshed.seasons ?? null,
      refreshed.episodes ?? null,
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

async function listCovers(id: string, env: Env): Promise<Response> {
  const row = await env.DB.prepare("SELECT * FROM items WHERE id = ?1").bind(id).first<Item>();
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
      case "open_library":
        covers = await openLibraryCovers(row);
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
async function itemScores(id: string, env: Env): Promise<Response> {
  const row = await env.DB.prepare("SELECT * FROM items WHERE id = ?1").bind(id).first<Item>();
  if (!row) return err(404, "item not found");

  const empty = { players: null, playersCount: null, critics: null, criticsCount: null };
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

async function steamGridDbCovers(item: Item, env: Env): Promise<CoverOption[]> {
  const key = env.STEAMGRIDDB_API_KEY;
  if (!key || !item.title) return [];

  const auth = { authorization: `Bearer ${key}` };

  // 1) Find the SteamGridDB game id by title.
  const search = await fetch(
    `https://www.steamgriddb.com/api/v2/search/autocomplete/${encodeURIComponent(item.title)}`,
    { headers: auth },
  );
  if (!search.ok) return [];
  const sdata = (await search.json()) as any;
  const games: any[] = Array.isArray(sdata?.data) ? sdata.data : [];
  if (games.length === 0) return [];

  // Take the top-matched game.
  const gameId = games[0].id;

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
  }
  return enriched;
}

// ---------- public read-only HTML view at /k ----------

async function publicShelves(env: Env): Promise<Response> {
  const { results } = await env.DB
    .prepare("SELECT * FROM items ORDER BY kind, added_at DESC")
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
