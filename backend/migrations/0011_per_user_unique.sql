-- Make item uniqueness per-user so two people can own the same movie/game without colliding.
-- SQLite can't alter a UNIQUE constraint in place, so we rebuild the table. A full backup copy
-- is taken first (items_backup_0011) so the data is recoverable if anything goes wrong.

CREATE TABLE IF NOT EXISTS items_backup_0011 AS SELECT * FROM items;

CREATE TABLE items_new (
  id            TEXT PRIMARY KEY,
  user_id       TEXT,
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
  user_platform TEXT,
  consoles      TEXT,
  format        TEXT,
  seasons       INTEGER,
  episodes      INTEGER,
  cur_season    INTEGER,
  cur_episode   INTEGER,
  completed_at  INTEGER,
  show_to       TEXT,
  season_episodes TEXT,
  added_at      INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL,
  UNIQUE(user_id, kind, external_src, external_id)
);

INSERT INTO items_new
  (id, user_id, kind, title, subtitle, year, cover_url, external_id, external_src, description,
   rating, status, notes, user_platform, consoles, format, seasons, episodes, cur_season,
   cur_episode, completed_at, show_to, season_episodes, added_at, updated_at)
SELECT
   id, user_id, kind, title, subtitle, year, cover_url, external_id, external_src, description,
   rating, status, notes, user_platform, consoles, format, seasons, episodes, cur_season,
   cur_episode, completed_at, show_to, season_episodes, added_at, updated_at
FROM items;

DROP TABLE items;
ALTER TABLE items_new RENAME TO items;

CREATE INDEX IF NOT EXISTS items_kind_added       ON items(kind, added_at DESC);
CREATE INDEX IF NOT EXISTS items_title            ON items(title COLLATE NOCASE);
CREATE INDEX IF NOT EXISTS items_user_kind_added  ON items(user_id, kind, added_at DESC);
