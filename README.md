# Media Shelf

Personal Android app + Cloudflare backend for tracking books, movies, TV shows, and games you own or have seen. Snap a photo (barcode or cover), search if you'd rather, and the item lands on its themed shelf.

## Architecture

```
Android app (Kotlin + Jetpack Compose)
    |
    |  HTTPS, bearer token
    v
Cloudflare Worker @ media.kzaller.com
    |- D1 (SQLite)        -> your shelves
    |- R2 (object store)  -> uploaded photos (optional)
    |
    |- proxies external lookups so API keys stay server-side:
       - Google Books (no key)        -> books
       - TMDB                         -> movies + tv
       - RAWG.io                      -> games
```

The phone uses **ML Kit on-device** for barcode scanning and OCR (no network round-trip, no cost). Lookups go through your Worker so API keys never ship in the APK.

Four shelves, each with its own visual theme:

| Shelf  | Vibe                          |
|--------|-------------------------------|
| Books  | Warm wood library, serif type |
| Movies | Cinema noir, red + gold       |
| TV     | Retro CRT phosphor + scanlines|
| Games  | Arcade neon, magenta + cyan   |

## Repo layout

```
backend/   Cloudflare Worker + D1 schema + wrangler config
android/   Android Studio project (Kotlin + Compose)
```

## One-time setup

### 1. Backend (Cloudflare)

```bash
cd backend
npm install
npx wrangler login
npx wrangler d1 create media-shelf            # copy the database_id into wrangler.toml
npx wrangler d1 execute media-shelf --remote --file=./schema.sql
npx wrangler secret put TMDB_API_KEY          # paste your TMDB v3 key
npx wrangler secret put RAWG_API_KEY          # paste your RAWG key
npx wrangler secret put SHELF_TOKEN           # any long random string -- this is your phone's password
npx wrangler deploy
```

Then in the Cloudflare dashboard:
1. Workers & Pages -> `media-shelf` -> Settings -> Triggers -> add custom domain `media.kzaller.com`
2. Cloudflare auto-creates the DNS record on `kzaller.com` since the zone is in your account.

Get keys here:
- TMDB: https://www.themoviedb.org/settings/api (free, instant)
- RAWG: https://rawg.io/apidocs (free, instant)

### 2. Android app

1. Install Android Studio (Hedgehog or newer).
2. Open the `android/` folder. Let Gradle sync; it will download the wrapper.
3. Create `android/local.properties` (Android Studio usually does this) and add:
   ```
   sdk.dir=/path/to/Android/Sdk
   shelf.api.base=https://media.kzaller.com
   shelf.api.token=<the SHELF_TOKEN you set above>
   ```
4. Plug in your phone with USB debugging on, hit Run.

## Day-to-day usage

- Tap a shelf, then `+`. Choose **Camera** or **Search**.
- Camera: point at a book ISBN barcode (most reliable), a game UPC, a movie poster, or any cover. The app will:
  1. Try barcode first.
  2. If no barcode in 2 seconds, switch to OCR and read text from the frame.
  3. Show candidate matches; tap one to add.
  4. If nothing matches, drop you into a search box pre-filled with what it read so you can correct it.
- Hold an item to delete or edit notes/rating.

## What's free, what isn't

Cloudflare Workers free tier: 100k requests/day, D1 free up to 5GB. A personal media tracker is nowhere near that. R2 has a free egress allowance too. You will not be billed for normal use.

External APIs (Google Books / TMDB / RAWG) are all free for personal use within their published rate limits.
