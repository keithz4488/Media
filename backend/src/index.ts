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
 *   GET    /health
 *
 * Auth: every request must send `Authorization: Bearer <SHELF_TOKEN>`.
 */

export interface Env {
  DB: D1Database;
  SHELF_TOKEN: string;
  TMDB_API_KEY: string;
  RAWG_API_KEY: string;
  ANTHROPIC_API_KEY: string;
  STEAMGRIDDB_API_KEY?: string; // optional: when set, augments game covers with SteamGridDB box art
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

    if (!authed(req, env)) return err(401, "unauthorized");

    try {
      if (url.pathname === "/items") {
        if (req.method === "GET") return listItems(url, env);
        if (req.method === "POST") return createItem(req, env);
      }

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
       description, rating, status, notes, user_platform, added_at, updated_at)
     VALUES
      (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15)
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
  for (const k of ["title", "subtitle", "year", "cover_url", "description", "rating", "status", "notes", "user_platform"] as const) {
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

async function searchGames(url: URL, env: Env): Promise<Response> {
  const slug = url.searchParams.get("slug");
  const q = url.searchParams.get("q");
  if (!env.RAWG_API_KEY) return err(500, "RAWG_API_KEY not configured");
  if (!slug && !q) return err(400, "q or slug required");

  const api = slug
    ? new URL(`https://api.rawg.io/api/games/${slug}`)
    : new URL("https://api.rawg.io/api/games");
  api.searchParams.set("key", env.RAWG_API_KEY);
  if (q) {
    api.searchParams.set("search", q);
    api.searchParams.set("page_size", "20");
  }

  const r = await fetch(api.toString());
  if (!r.ok) {
    const body = await r.text().catch(() => "");
    return err(502, `rawg ${r.status}: ${body.slice(0, 200)}`);
  }
  const data = (await r.json()) as any;
  const list = slug ? [data] : data.results || [];
  const hits: SearchHit[] = list.map((g: any): SearchHit => {
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
  });
  return json({ hits });
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
      model: "claude-haiku-4-5-20251001",
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
  // Skip enrichment if the caller already supplied a description (unless we're
  // explicitly forcing a refresh) or if there's no external reference to look up.
  if (!force && item.description && item.description.trim().length > 0) return item;
  if (!item.external_src || !item.external_id) return item;

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
           updated_at  = ?3
     WHERE id = ?4`,
  )
    .bind(refreshed.description ?? null, refreshed.cover_url ?? null, Date.now(), id)
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
    workId = item.external_id;
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
  const r = await fetch(`https://api.rawg.io/api/games/${slug}?key=${env.RAWG_API_KEY}`);
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
  return { ...item, description: trimDescription(d.overview) ?? item.description };
}
