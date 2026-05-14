package com.kzaller.shelf.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.models.ItemDto

@Entity(
    tableName = "items",
    indices = [Index("kind"), Index("addedAt")],
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val subtitle: String?,
    val year: Int?,
    val coverUrl: String?,
    val externalId: String?,
    val externalSrc: String?,
    val description: String?,
    val rating: Int?,
    val status: String,
    val notes: String?,
    val addedAt: Long,
    val updatedAt: Long,
) {
    fun toDto(): ItemDto = ItemDto(
        id = id,
        kind = MediaKind.fromWire(kind),
        title = title,
        subtitle = subtitle,
        year = year,
        coverUrl = coverUrl,
        externalId = externalId,
        externalSrc = externalSrc,
        description = description,
        rating = rating,
        status = status,
        notes = notes,
        addedAt = addedAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDto(dto: ItemDto): ItemEntity = ItemEntity(
            id = dto.id,
            kind = dto.kind.wire,
            title = dto.title,
            subtitle = dto.subtitle,
            year = dto.year,
            coverUrl = dto.coverUrl,
            externalId = dto.externalId,
            externalSrc = dto.externalSrc,
            description = dto.description,
            rating = dto.rating,
            status = dto.status ?: "owned",
            notes = dto.notes,
            addedAt = dto.addedAt ?: System.currentTimeMillis(),
            updatedAt = dto.updatedAt ?: System.currentTimeMillis(),
        )
    }
}
