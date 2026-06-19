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

## Building a release APK

The release build is configured to read its signing keystore from
`android/local.properties` (locally) or `KEYSTORE_*` env vars (CI). Once that's
set up, `gradle assembleRelease` (or `./gradlew assembleRelease`) drops a
signed APK at `android/app/build/outputs/apk/release/app-release.apk`.

### One-time keystore setup

Generate a keystore and back it up somewhere safe. From PowerShell:

```powershell
$keystoreDir = "$HOME\Documents\keystores"
mkdir $keystoreDir -ErrorAction SilentlyContinue
& "$Env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
  -keystore "$keystoreDir\media-shelf.jks" `
  -alias media-shelf -keyalg RSA -keysize 2048 -validity 25000
```

If `$Env:JAVA_HOME` isn't set, use the JDK bundled with Android Studio:
`C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe`.

Then add the credentials to `android/local.properties`:

```
keystore.path=C\:\\Users\\<you>\\Documents\\keystores\\media-shelf.jks
keystore.password=<the password you set>
key.alias=media-shelf
key.password=<the key password you set>
```

### CI builds (GitHub Actions)

`.github/workflows/release-apk.yml` builds a signed APK on every push to the
working branch and attaches it as both a workflow artifact and a GitHub Release.

Configure these repository secrets at
`Settings -> Secrets and variables -> Actions -> New repository secret`:

| Secret              | Value                                                     |
| ------------------- | --------------------------------------------------------- |
| `KEYSTORE_BASE64`   | `base64 -w0 < media-shelf.jks` (one-line base64 of the .jks) |
| `KEYSTORE_PASSWORD` | the keystore password                                     |
| `KEY_ALIAS`         | `media-shelf`                                             |
| `KEY_PASSWORD`      | the key password                                          |
| `SHELF_API_TOKEN`   | the `SHELF_TOKEN` value from your Cloudflare secret       |

After the workflow runs, find the APK at
`https://github.com/<you>/<repo>/releases` -> latest Build N -> Assets ->
`media-shelf-build-N.apk`.

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
