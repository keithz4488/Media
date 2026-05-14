package com.kzaller.shelf.data

import android.content.Context
import com.kzaller.shelf.data.api.ApiClient
import com.kzaller.shelf.data.local.ItemEntity
import com.kzaller.shelf.data.local.ShelfDatabase
import com.kzaller.shelf.data.models.CreateItemRequest
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

    suspend fun refresh(kind: MediaKind): Result<Unit> = runCatching {
        val resp = api.list(kind = kind.wire)
        db.items().clearKind(kind.wire)
        db.items().upsertAll(resp.items.map(ItemEntity::fromDto))
    }

    suspend fun add(kind: MediaKind, hit: SearchHit, status: String = "owned"): Result<ItemDto> = runCatching {
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

    suspend fun search(kind: MediaKind, query: String): Result<List<SearchHit>> = runCatching {
        when (kind) {
            MediaKind.BOOK  -> api.searchBooks(q = query).hits
            MediaKind.MOVIE -> api.searchMovies(q = query).hits
            MediaKind.TV    -> api.searchTv(q = query).hits
            MediaKind.GAME  -> api.searchGames(q = query).hits
        }
    }

    suspend fun lookupBookByIsbn(isbn: String): Result<List<SearchHit>> = runCatching {
        api.searchBooks(isbn = isbn).hits
    }
}
