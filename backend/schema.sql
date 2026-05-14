-- Single user, four shelves. Items are unique per (kind, external_id).
CREATE TABLE IF NOT EXISTS items (
  id            TEXT PRIMARY KEY,
  kind          TEXT NOT NULL CHECK (kind IN ('book','movie','tv','game')),
  title         TEXT NOT NULL,
  subtitle      TEXT,           -- author / director / studio / platform list
  year          INTEGER,
  cover_url     TEXT,
  external_id   TEXT,           -- ISBN, TMDB id, RAWG slug
  external_src  TEXT,           -- 'google_books','tmdb','rawg','manual'
  description   TEXT,
  rating        INTEGER,        -- 1..5, nullable
  status        TEXT NOT NULL DEFAULT 'owned' CHECK (status IN ('owned','seen','wishlist')),
  notes         TEXT,
  added_at      INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL,
  UNIQUE(kind, external_src, external_id)
);

CREATE INDEX IF NOT EXISTS items_kind_added ON items(kind, added_at DESC);
CREATE INDEX IF NOT EXISTS items_title      ON items(title COLLATE NOCASE);
