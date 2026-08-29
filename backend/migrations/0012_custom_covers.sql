-- Covers the user uploads from their own photos, for items no catalogue has art for.
-- Held here rather than in object storage: R2 wants a card on file, and a personal library's
-- worth of covers is small enough that the database carries them comfortably.
--
-- Image bytes are kept base64-encoded rather than as a BLOB, which costs about a third more
-- space but avoids depending on how the driver round-trips binary.
CREATE TABLE IF NOT EXISTS custom_covers (
  id         TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL,
  item_id    TEXT,
  mime       TEXT NOT NULL DEFAULT 'image/jpeg',
  data       TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS custom_covers_user_item ON custom_covers(user_id, item_id);
