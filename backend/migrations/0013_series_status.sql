-- Migration: whether a TV series is still running.
--   series_status : TMDB's own wording, normalised to 'continuing' or 'ended'.
--                   Null for anything that isn't a show, or a show TMDB hasn't told us about yet.
ALTER TABLE items ADD COLUMN series_status TEXT;
