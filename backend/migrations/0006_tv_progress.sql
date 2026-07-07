-- Migration: TV season/episode tracking.
--   seasons / episodes : total counts, auto-filled from TMDB on add/refresh.
--   cur_season / cur_episode : how far the user has watched (user-set).
ALTER TABLE items ADD COLUMN seasons INTEGER;
ALTER TABLE items ADD COLUMN episodes INTEGER;
ALTER TABLE items ADD COLUMN cur_season INTEGER;
ALTER TABLE items ADD COLUMN cur_episode INTEGER;
