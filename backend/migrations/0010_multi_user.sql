-- Multi-tenancy: every item belongs to a user. Existing rows are stamped '__legacy__' and
-- claimed by the owner on their first Google sign-in. (The per-user UNIQUE constraint on
-- external items is a later, table-rebuild migration done with a backup; while there is
-- effectively one data owner the existing global UNIQUE is fine.)
ALTER TABLE items ADD COLUMN user_id TEXT;
UPDATE items SET user_id = '__legacy__' WHERE user_id IS NULL;
CREATE INDEX IF NOT EXISTS items_user_kind_added ON items(user_id, kind, added_at DESC);

-- Per-user settings (Steam credentials, etc.). The old single-row `settings` table is kept
-- for safety; its rows are copied into the legacy bucket.
CREATE TABLE IF NOT EXISTS user_settings (
  user_id TEXT NOT NULL,
  key     TEXT NOT NULL,
  value   TEXT,
  PRIMARY KEY (user_id, key)
);
INSERT OR IGNORE INTO user_settings (user_id, key, value)
  SELECT '__legacy__', key, value FROM settings;
