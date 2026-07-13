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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ShelfRepository(context: Context) {
    private val db = ShelfDatabase.get(context)
    private val api = ApiClient.api

    fun observeShelf(kind: MediaKind): Flow<List<ItemDto>> =
        db.items().observeByKind(kind.wire).map { rows -> rows.map(ItemEntity::toDto) }

    fun observeItem(id: String): Flow<ItemDto?> =
        db.items().observe(id).map { it?.toDto() }

    fun observeCounts(): Flow<Map<MediaKind, Int>> =
        db.items().observeCounts().map { rows ->
            rows.associate { MediaKind.fromWire(it.kind) to it.n }
        }

    /** Every item on every shelf, newest-first. Backs the cross-shelf search screen. */
    fun observeAll(): Flow<List<ItemDto>> =
        db.items().observeAll().map { rows -> rows.map(ItemEntity::toDto) }

    suspend fun refreshAll(force: Boolean = false): Result<Unit> = runCatching {
        MediaKind.values().forEach { refresh(it, force).getOrThrow() }
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

    suspend fun identify(jpegBytes: ByteArray): Result<IdentifyResult> = runCatching {
        val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        api.identify(IdentifyRequest(image = b64)).result
    }

    suspend fun refreshDetails(id: String): Result<ItemDto> = runCatching {
        val resp = api.refresh(id)
        db.items().upsert(ItemEntity.fromDto(resp.item))
        resp.item
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
    }
}
