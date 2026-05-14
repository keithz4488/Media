/**
 * Media Shelf Worker
 *
 * Routes:
 *   GET    /items?kind=book|movie|tv|game
 *   POST   /items                       body: Item
 *   PATCH  /items/:id                   body: partial Item
 *   DELETE /items/:id
 *   GET    /search/books?q=...          (or ?isbn=...)
 *   GET    /search/movies?q=...         (or ?id=tmdb_id)
 *   GET    /search/tv?q=...             (or ?id=tmdb_id)
 *   GET    /search/games?q=...          (or ?slug=rawg_slug)
 *   GET    /health
 *
 * Auth: every request must send `Authorization: Bearer <SHELF_TOKEN>`.
 */

export interface Env {
  DB: D1Database;
  SHELF_TOKEN: string;
  TMDB_API_KEY: string;
  RAWG_API_KEY: string;
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
  status?: "owned" | "seen" | "wishlist";
  notes?: string | null;
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

      if (url.pathname === "/search/books") return searchBooks(url, env);
      if (url.pathname === "/search/movies") return searchTmdb(url, env, "movie");
      if (url.pathname === "/search/tv") return searchTmdb(url, env, "tv");
      if (url.pathname === "/search/games") return searchGames(url, env);

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
  const item: Item = {
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
    added_at: body.added_at ?? now,
    updated_at: now,
  };

  // ON CONFLICT: if the user re-adds the same external item, just touch updated_at.
  await env.DB.prepare(
    `INSERT INTO items
      (id, kind, title, subtitle, year, cover_url, external_id, external_src,
       description, rating, status, notes, added_at, updated_at)
     VALUES
      (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14)
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
  for (const k of ["title", "subtitle", "year", "cover_url", "description", "rating", "status", "notes"] as const) {
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
  const isbn = url.searchParams.get("isbn");
  const q = url.searchParams.get("q");
  const query = isbn ? `isbn:${isbn}` : q;
  if (!query) return err(400, "q or isbn required");

  const api = new URL("https://www.googleapis.com/books/v1/volumes");
  api.searchParams.set("q", query);
  api.searchParams.set("maxResults", "12");
  const r = await fetch(api.toString());
  if (!r.ok) return err(502, "google books error");
  const data = (await r.json()) as any;

  const hits: SearchHit[] = (data.items || []).map((it: any): SearchHit => {
    const v = it.volumeInfo || {};
    const ids = v.industryIdentifiers || [];
    const picked = ids.find((i: any) => i.type === "ISBN_13") || ids.find((i: any) => i.type === "ISBN_10") || ids[0];
    const cover = v.imageLinks?.thumbnail?.replace(/^http:/, "https:") || null;
    return {
      external_id: picked?.identifier || it.id,
      external_src: "google_books",
      title: v.title || "Untitled",
      subtitle: (v.authors || []).join(", ") || null,
      year: v.publishedDate ? Number(v.publishedDate.slice(0, 4)) || null : null,
      cover_url: cover,
      description: v.description || null,
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
  if (!r.ok) return err(502, "tmdb error");
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
    api.searchParams.set("page_size", "12");
  }

  const r = await fetch(api.toString());
  if (!r.ok) return err(502, "rawg error");
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
