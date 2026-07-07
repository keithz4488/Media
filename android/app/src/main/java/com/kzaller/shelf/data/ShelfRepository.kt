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

    suspend fun refreshAll(): Result<Unit> = runCatching {
        MediaKind.values().forEach { refresh(it).getOrThrow() }
    }

    suspend fun refresh(kind: MediaKind): Result<Unit> = runCatching {
        val resp = api.list(kind = kind.wire)
        db.items().clearKind(kind.wire)
        db.items().upsertAll(resp.items.map(ItemEntity::fromDto))
    }

    suspend fun add(
        kind: MediaKind,
        hit: SearchHit,
        status: String = "owned",
        format: String? = null,
    ): Result<ItemDto> = runCatching {
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
}
