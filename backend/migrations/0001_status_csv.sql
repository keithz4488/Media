-- Migration: relax the `status` CHECK so the column can hold a CSV like 'owned,seen'.
-- Idempotent: safe to re-run.
BEGIN TRANSACTION;

CREATE TABLE IF NOT EXISTS items_new (
  id            TEXT PRIMARY KEY,
  kind          TEXT NOT NULL CHECK (kind IN ('book','movie','tv','game')),
  title         TEXT NOT NULL,
  subtitle      TEXT,
  year          INTEGER,
  cover_url     TEXT,
  external_id   TEXT,
  external_src  TEXT,
  description   TEXT,
  rating        INTEGER,
  status        TEXT NOT NULL DEFAULT 'owned',
  notes         TEXT,
  added_at      INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL,
  UNIQUE(kind, external_src, external_id)
);

INSERT OR IGNORE INTO items_new
  SELECT id, kind, title, subtitle, year, cover_url, external_id, external_src,
         description, rating, status, notes, added_at, updated_at
  FROM items;

DROP TABLE items;
ALTER TABLE items_new RENAME TO items;

CREATE INDEX IF NOT EXISTS items_kind_added ON items(kind, added_at DESC);
CREATE INDEX IF NOT EXISTS items_title      ON items(title COLLATE NOCASE);

COMMIT;
