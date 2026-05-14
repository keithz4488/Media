package com.kzaller.shelf.data.models

import com.kzaller.shelf.data.MediaKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemDto(
    val id: String,
    val kind: MediaKind,
    val title: String,
    val subtitle: String? = null,
    val year: Int? = null,
    @SerialName("cover_url")    val coverUrl: String? = null,
    @SerialName("external_id")  val externalId: String? = null,
    @SerialName("external_src") val externalSrc: String? = null,
    val description: String? = null,
    val rating: Int? = null,
    val status: String? = "owned",
    val notes: String? = null,
    @SerialName("added_at")   val addedAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
)

@Serializable
data class ItemsResponse(val items: List<ItemDto> = emptyList())

@Serializable
data class ItemResponse(val item: ItemDto)

@Serializable
data class SearchHit(
    @SerialName("external_id")  val externalId: String,
    @SerialName("external_src") val externalSrc: String,
    val title: String,
    val subtitle: String? = null,
    val year: Int? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val description: String? = null,
)

@Serializable
data class SearchResponse(val hits: List<SearchHit> = emptyList())

@Serializable
data class CreateItemRequest(
    val kind: MediaKind,
    val title: String,
    val subtitle: String? = null,
    val year: Int? = null,
    @SerialName("cover_url")    val coverUrl: String? = null,
    @SerialName("external_id")  val externalId: String? = null,
    @SerialName("external_src") val externalSrc: String? = null,
    val description: String? = null,
    val status: String? = "owned",
)

@Serializable
data class UpdateItemRequest(
    val title: String? = null,
    val subtitle: String? = null,
    val year: Int? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val description: String? = null,
    val rating: Int? = null,
    val status: String? = null,
    val notes: String? = null,
)
