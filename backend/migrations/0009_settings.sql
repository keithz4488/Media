-- Key/value app settings, used server-side by scheduled jobs (e.g. the daily Steam
-- library sync needs the Steam API key + SteamID, which live on-device otherwise).
CREATE TABLE IF NOT EXISTS settings (
  key   TEXT PRIMARY KEY,
  value TEXT
);
