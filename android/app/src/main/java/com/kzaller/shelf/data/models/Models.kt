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
    @SerialName("user_platform") val userPlatform: String? = null,
    val consoles: String? = null,
    val format: String? = null,
    val seasons: Int? = null,
    val episodes: Int? = null,
    @SerialName("cur_season") val curSeason: Int? = null,
    @SerialName("cur_episode") val curEpisode: Int? = null,
    @SerialName("completed_at") val completedAt: Long? = null,
    @SerialName("show_to") val showTo: String? = null,
    @SerialName("season_episodes") val seasonEpisodes: String? = null,
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
    val format: String? = null,
    @SerialName("user_platform") val userPlatform: String? = null,
    val consoles: String? = null,
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
    @SerialName("user_platform") val userPlatform: String? = null,
    val consoles: String? = null,
    val format: String? = null,
    @SerialName("cur_season") val curSeason: Int? = null,
    @SerialName("cur_episode") val curEpisode: Int? = null,
    @SerialName("completed_at") val completedAt: Long? = null,
    @SerialName("show_to") val showTo: String? = null,
)

@Serializable
data class IdentifyRequest(val image: String)

@Serializable
data class IdentifyResponse(val result: IdentifyResult)

@Serializable
data class IdentifyShelfResponse(val results: List<IdentifyResult> = emptyList())

@Serializable
data class IdentifyResult(
    val kind: String,
    val title: String,
    val year: Int? = null,
)

@Serializable
data class CoverOption(
    val url: String,
    val label: String,
)

@Serializable
data class CoversResponse(
    val covers: List<CoverOption> = emptyList(),
)

@Serializable
data class BulkCreateRequest(val items: List<CreateItemRequest>)

@Serializable
data class BulkCreateResponse(val inserted: Int = 0, val received: Int = 0)

@Serializable
data class SteamConfigRequest(val apiKey: String, val steamId: String)

@Serializable
data class SteamSyncResponse(val added: Int = 0, val updated: Int = 0)

@Serializable
data class SteamStatusResponse(val connected: Boolean = false, val games: Int = 0)

@Serializable
data class PlexConfigResponse(val secret: String = "", val url: String = "")

@Serializable
data class SessionResponse(val token: String = "", val expiresAt: Long = 0)

@Serializable
data class ScoresResponse(val scores: Scores)

@Serializable
data class Scores(
    val players: Int? = null,
    val playersCount: Int? = null,
    val critics: Int? = null,
    val criticsCount: Int? = null,
) {
    val hasAny: Boolean get() = players != null || critics != null
}
