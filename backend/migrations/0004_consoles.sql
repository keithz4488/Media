-- Migration: add `consoles` column for game-specific sub-platform selection
-- (e.g. user_platform = "nintendo", consoles = "switch,switch_2"). CSV of console
-- codes; NULL/empty on existing rows.
ALTER TABLE items ADD COLUMN consoles TEXT;
