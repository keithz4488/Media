-- Migration: add completed_at column for tracking when the user finished/read/watched/played
-- an item. Used by the stats dashboard and detail editor. NULL = not finished yet (or never set).
ALTER TABLE items ADD COLUMN completed_at INTEGER;
