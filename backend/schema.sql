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
  status        TEXT NOT NULL DEFAULT 'owned', -- CSV: any of 'owned','seen','wishlist'
  notes         TEXT,
  user_platform TEXT,                            -- CSV: any of 'pc','xbox','playstation','nintendo','mobile' (games only)
  consoles      TEXT,                            -- CSV of console codes within the selected platforms (games only)
  format        TEXT,                            -- CSV: any of 'physical','digital'
  seasons       INTEGER,                         -- TV: total seasons (auto from TMDB)
  episodes      INTEGER,                         -- TV: total episodes (auto from TMDB)
  cur_season    INTEGER,                         -- TV: user's current season
  cur_episode   INTEGER,                         -- TV: user's current episode
  completed_at  INTEGER,                         -- epoch ms when the user marked the item as finished/read/watched/played
  show_to       TEXT,                            -- movies/TV: CSV of people to show it to
  season_episodes TEXT,                          -- TV: CSV of "seasonNumber:episodeCount" (auto from TMDB)
  added_at      INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL,
  UNIQUE(kind, external_src, external_id)
);

CREATE INDEX IF NOT EXISTS items_kind_added ON items(kind, added_at DESC);
CREATE INDEX IF NOT EXISTS items_title      ON items(title COLLATE NOCASE);
