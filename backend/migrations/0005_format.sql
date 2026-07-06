-- Migration: add `format` column so items can be flagged physical and/or digital.
-- CSV of 'physical','digital'; NULL/empty means unspecified. Non-destructive.
ALTER TABLE items ADD COLUMN format TEXT;
