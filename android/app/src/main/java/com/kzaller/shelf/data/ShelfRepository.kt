package com.kzaller.shelf.data

import android.content.Context
import android.util.Base64
import com.kzaller.shelf.data.api.ApiClient
import com.kzaller.shelf.data.local.ItemEntity
import com.kzaller.shelf.data.local.ShelfDatabase
import com.kzaller.shelf.data.models.CoverOption
import com.kzaller.shelf.data.models.CreateItemRequest
import com.kzaller.shelf.data.models.IdentifyRequest
import com.kzaller.shelf.data.models.IdentifyResult
import com.kzaller.shelf.data.models.ItemDto
import com.kzaller.shelf.data.models.SearchHit
import com.kzaller.shelf.data.models.UpdateItemRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

class ShelfRepository(context: Context) {
    private val db = ShelfDatabase.get(context)
    private val api = ApiClient.api

    /**
     * A shelf's items, hot and already converted. See [shelfFlow] for why this is cached rather
     * than a fresh query each call.
     */
    fun observeShelf(kind: MediaKind): Flow<List<ItemDto>> = shelfFlow(db, kind)

    /**
     * Start building every shelf now so tapping one has nothing left to do. Called once at
     * startup, while the user is still looking at the home screen.
     */
    fun warmShelves() {
        MediaKind.values().forEach { shelfFlow(db, it) }
    }

    /**
     * The shelf as it stands in memory, or null if it hasn't been read yet. Lets a screen render
     * the real thing on its first frame instead of a placeholder it immediately replaces.
     */
    fun cachedShelf(kind: MediaKind): List<ItemDto>? =
        shelfFlow(db, kind).replayCache.firstOrNull()

    fun observeItem(id: String): Flow<ItemDto?> =
        db.items().observe(id).map { it?.toDto() }

    fun observeCounts(): Flow<Map<MediaKind, Int>> =
        db.items().observeCounts().map { rows ->
            rows.associate { MediaKind.fromWire(it.kind) to it.n }
        }

    /** Every item on every shelf, newest-first. Backs the cross-shelf search screen. */
    fun observeAll(): Flow<List<ItemDto>> =
        db.items().observeAll()
            .map { rows -> rows.map(ItemEntity::toDto) }
            .flowOn(Dispatchers.Default)

    /**
     * Pull every shelf, one at a time. Deliberately not concurrent: each shelf's refresh clears
     * and rewrites its rows, and four of those landing together invalidate the database over and
     * over, making every open shelf re-filter and re-sort on each one -- which is felt as lag
     * exactly when the user is navigating at startup.
     *
     * Each shelf does stand or fall on its own, though; the first failure no longer aborts the
     * rest and leaves them stale.
     */
    suspend fun refreshAll(force: Boolean = false): Result<Unit> = runCatching {
        MediaKind.values().forEach { refresh(it, force) }
    }

    /**
     * Pull a shelf from the server into the local cache. Since this device is the only writer,
     * the cache is authoritative right after edits, so we skip a server round-trip when we just
     * refreshed (many screens refresh on open). Pull-to-refresh and post-import pass force=true.
     */
    suspend fun refresh(kind: MediaKind, force: Boolean = false): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        if (!force && now - (lastRefresh[kind.wire] ?: 0L) < REFRESH_INTERVAL_MS) return@runCatching
        val resp = api.list(kind = kind.wire)
        db.items().clearKind(kind.wire)
        db.items().upsertAll(resp.items.map(ItemEntity::fromDto))
        lastRefresh[kind.wire] = System.currentTimeMillis()
    }

    suspend fun add(
        kind: MediaKind,
        hit: SearchHit,
        status: String = "owned",
        format: String? = null,
    ): Result<ItemDto> = runCatching {
        // For a game that shipped on only one system, pre-fill the platform + console from the
        // hit's platform list (its subtitle). Multi-platform games are left for the user to pick.
        val (userPlatform, consoles) =
            if (kind == MediaKind.GAME) Console.autoFill(hit.subtitle) else (null to null)
        val resp = api.create(
            CreateItemRequest(
                kind = kind,
                title = hit.title,
                subtitle = hit.subtitle,
                year = hit.year,
                coverUrl = hit.coverUrl,
                externalId = hit.externalId,
                externalSrc = hit.externalSrc,
                description = hit.description,
                status = status,
                format = format,
                userPlatform = userPlatform,
                consoles = consoles,
            ),
        )
        db.items().upsert(ItemEntity.fromDto(resp.item))
        resp.item
    }

    suspend fun addManual(
        kind: MediaKind,
        title: String,
        subtitle: String?,
        year: Int?,
        coverUrl: String?,
        status: String = "owned",
        format: String? = null,
    ): Result<ItemDto> = runCatching {
        val resp = api.create(
            CreateItemRequest(
                kind = kind,
                title = title,
                subtitle = subtitle,
                year = year,
                coverUrl = coverUrl,
                externalSrc = "manual",
                status = status,
                format = format,
            ),
        )
        db.items().upsert(ItemEntity.fromDto(resp.item))
        resp.item
    }

    suspend fun update(id: String, patch: UpdateItemRequest): Result<ItemDto> = runCatching {
        val resp = api.update(id, patch)
        db.items().upsert(ItemEntity.fromDto(resp.item))
        resp.item
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        api.delete(id)
        db.items().delete(id)
    }

    suspend fun deleteMany(ids: Collection<String>): Result<Int> = runCatching {
        ids.forEach { id ->
            runCatching {
                api.delete(id)
                db.items().delete(id)
            }
        }
        ids.size
    }

    suspend fun setStatusMany(ids: Collection<String>, status: String): Result<Int> = runCatching {
        ids.forEach { id ->
            runCatching {
                val resp = api.update(id, UpdateItemRequest(status = status))
                db.items().upsert(ItemEntity.fromDto(resp.item))
            }
        }
        ids.size
    }

    suspend fun search(kind: MediaKind, query: String): Result<List<SearchHit>> = runCatching {
        when (kind) {
            MediaKind.BOOK  -> api.searchBooks(q = query).hits
            MediaKind.MOVIE -> api.searchMovies(q = query).hits
            MediaKind.TV    -> api.searchTv(q = query).hits
            MediaKind.GAME  -> api.searchGames(q = query).hits
        }
    }

    /** Resolve a TMDB id directly to a hit (used by the Plex import to skip fuzzy matching). */
    suspend fun searchTmdbById(kind: MediaKind, tmdbId: String): Result<SearchHit?> = runCatching {
        when (kind) {
            MediaKind.MOVIE -> api.searchMovies(tmdbId = tmdbId).hits.firstOrNull()
            MediaKind.TV    -> api.searchTv(tmdbId = tmdbId).hits.firstOrNull()
            else -> null
        }
    }

    /** Fast bulk insert for imports; items are pre-resolved (cover/desc already filled). */
    suspend fun bulkImport(requests: List<com.kzaller.shelf.data.models.CreateItemRequest>): Result<Int> = runCatching {
        var total = 0
        requests.chunked(100).forEach { chunk ->
            total += api.bulkCreate(com.kzaller.shelf.data.models.BulkCreateRequest(chunk)).inserted
        }
        total
    }

    suspend fun lookupBookByIsbn(isbn: String): Result<List<SearchHit>> = runCatching {
        api.searchBooks(isbn = isbn).hits
    }

    /** Resolve a scanned barcode for a shelf: ISBN direct, otherwise via the product database. */
    suspend fun lookupBarcode(code: String, kind: MediaKind): Result<com.kzaller.shelf.data.models.BarcodeResponse> =
        runCatching { api.lookupBarcode(code = code, kind = kind.wire) }

    /** Register the Steam credentials with the backend so its daily cron can auto-add purchases. */
    suspend fun saveSteamConfig(apiKey: String, steamId: String): Result<Unit> = runCatching {
        api.saveSteamConfig(com.kzaller.shelf.data.models.SteamConfigRequest(apiKey, steamId))
    }

    /** Run the Steam library sync now; returns games added + release years backfilled. */
    suspend fun syncSteam(): Result<com.kzaller.shelf.data.models.SteamSyncResponse> = runCatching {
        api.syncSteam()
    }

    /** Backend Steam connection status (credentials present + games already synced). */
    suspend fun steamStatus(): Result<com.kzaller.shelf.data.models.SteamStatusResponse> = runCatching {
        api.steamStatus()
    }

    /**
     * This user's personal Plex webhook URL for live sync (generated on first request).
     *
     * [account] is the Plex account name read from their own server; passing it is what lets the
     * webhook drop playback by everyone else who has access to the library.
     */
    suspend fun plexConfig(account: String? = null): Result<com.kzaller.shelf.data.models.PlexConfigResponse> = runCatching {
        api.plexConfig(com.kzaller.shelf.data.models.PlexConfigRequest(account))
    }

    /**
     * Read the Plex account name off the user's server and register it with the backend. Runs at
     * startup when Plex is already connected, so an install that predates the account check heals
     * itself the first time the app is opened at home -- no reconnecting, no settings to find.
     */
    suspend fun registerPlexAccount(baseUrl: String, token: String) {
        if (baseUrl.isBlank() || token.isBlank()) return
        val account = PlexClient().fetchAccountName(baseUrl, token) ?: return
        runCatching { api.plexConfig(com.kzaller.shelf.data.models.PlexConfigRequest(account)) }
    }

    /** Exchange the current Google auth for a long-lived app session token. */
    suspend fun createSession(): Result<com.kzaller.shelf.data.models.SessionResponse> = runCatching {
        api.createSession()
    }

    suspend fun identify(jpegBytes: ByteArray): Result<IdentifyResult> = runCatching {
        val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        api.identify(IdentifyRequest(image = b64)).result
    }

    /** Identify every readable item in one shelf/row photo. */
    suspend fun identifyShelf(jpegBytes: ByteArray): Result<List<IdentifyResult>> = runCatching {
        val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        api.identifyShelf(IdentifyRequest(image = b64)).results
    }

    suspend fun refreshDetails(id: String): Result<ItemDto> = runCatching {
        val resp = api.refresh(id)
        db.items().upsert(ItemEntity.fromDto(resp.item))
        resp.item
    }

    /**
     * Upload a cover the user picked from their photos. Downscaled and re-compressed first: the
     * originals are many megapixels, far more than a shelf thumbnail needs, and the backend caps
     * what it will accept.
     */
    suspend fun uploadCover(itemId: String, source: android.net.Uri, context: Context): Result<String> =
        runCatching {
            val bytes = withContext(Dispatchers.IO) { compressForCover(context, source) }
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            api.uploadCover(
                com.kzaller.shelf.data.models.UploadCoverRequest(image = b64, itemId = itemId),
            ).url
        }

    suspend fun listCovers(id: String): Result<List<CoverOption>> = runCatching {
        api.covers(id).covers
    }

    suspend fun loadScores(id: String): Result<com.kzaller.shelf.data.models.Scores> = runCatching {
        api.scores(id).scores
    }

    /** Wipe the local cache so a different account's shelf doesn't linger after switching users. */
    suspend fun clearLocal() {
        MediaKind.values().forEach { db.items().clearKind(it.wire) }
        lastRefresh.clear()
    }

    companion object {
        // Shared across repository instances (Room is a singleton): the last time each shelf
        // was pulled from the server, so back-to-back screen opens don't all re-fetch.
        private val lastRefresh = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private const val REFRESH_INTERVAL_MS = 30_000L

        /**
         * One hot flow per shelf, process-wide.
         *
         * A shelf ViewModel is built from scratch every time its screen opens, and each one used
         * to run its own Room query and convert every row again -- a couple of thousand entities
         * on the movies shelf, which is what made opening one feel slow. Sharing the flow means
         * the rows are converted once and every later open replays what's already in memory.
         *
         * Eagerly, not WhileSubscribed: the flow has to stay current between screens, otherwise
         * the replayed value could be stale by the time the next shelf opens. It's also what lets
         * [warmShelves] do the work up front. Kept off the main thread throughout.
         */
        private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val shelfFlows = java.util.concurrent.ConcurrentHashMap<String, SharedFlow<List<ItemDto>>>()

        private fun shelfFlow(db: ShelfDatabase, kind: MediaKind): SharedFlow<List<ItemDto>> =
            shelfFlows.getOrPut(kind.wire) {
                db.items().observeByKind(kind.wire)
                    .map { rows -> rows.map(ItemEntity::toDto) }
                    .shareIn(cacheScope, SharingStarted.Eagerly, replay = 1)
            }
    }
}

/**
 * Turn a picked photo into something sensible to store as a cover: phone photos are many
 * megapixels, far more than a shelf thumbnail needs and larger than the backend accepts.
 * Caps the long edge and re-encodes as JPEG.
 */
private fun compressForCover(context: Context, uri: android.net.Uri): ByteArray {
    val maxEdge = 1000
    var bitmap: android.graphics.Bitmap =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            // ImageDecoder applies the photo's EXIF orientation, so portraits don't arrive sideways.
            val src = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val scale = maxOf(info.size.width, info.size.height).toFloat() / maxEdge
                if (scale > 1f) {
                    decoder.setTargetSize(
                        (info.size.width / scale).toInt().coerceAtLeast(1),
                        (info.size.height / scale).toInt().coerceAtLeast(1),
                    )
                }
                // Hardware bitmaps can't be read back, and we need to compress this one.
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            // Older devices: sample down while decoding so a huge photo can't blow the heap.
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use {
                android.graphics.BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) sample *= 2
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri).use {
                android.graphics.BitmapFactory.decodeStream(it, null, opts)
            } ?: error("Couldn't read that image")
        }

    // Whichever path decoded it, hold the cap.
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest > maxEdge) {
        val k = maxEdge.toFloat() / longest
        bitmap = android.graphics.Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * k).toInt().coerceAtLeast(1),
            (bitmap.height * k).toInt().coerceAtLeast(1),
            true,
        )
    }

    val out = java.io.ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
    return out.toByteArray()
}
