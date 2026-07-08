-- Migration: per-season episode counts for TV shows.
--   season_episodes : CSV of "seasonNumber:episodeCount" pairs (e.g. "1:13,2:22,3:22").
ALTER TABLE items ADD COLUMN season_episodes TEXT;
