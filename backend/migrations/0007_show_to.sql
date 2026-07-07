-- Migration: "Show To" list for movies/TV.
--   show_to : CSV of people the user wants to show this movie/show to.
ALTER TABLE items ADD COLUMN show_to TEXT;
