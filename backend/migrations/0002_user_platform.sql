-- Migration: add user_platform column so games can record which console the user
-- owns them on (CSV like 'pc,playstation'). NULL on existing rows (means "unset").
-- ALTER TABLE ADD COLUMN is non-destructive and runs in O(1) on SQLite.
ALTER TABLE items ADD COLUMN user_platform TEXT;
