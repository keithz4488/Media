package com.kzaller.shelf.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One movie/show pulled from a Plex library. */
data class PlexItem(
    val kind: MediaKind,       // MOVIE or TV
    val title: String,
    val year: Int?,
    val tmdbId: String?,       // extracted from Plex Guid array when present
)

/**
 * Minimal Plex Media Server client. Talks directly to the user's server (usually on the LAN),
 * requesting JSON, and pulls every item from the Movie and TV Show library sections. Extracts
 * the TMDB id from each item's Guid list so most matches can skip fuzzy search entirely.
 */
class PlexClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun get(url: String, token: String): JSONObject {
        val req = Request.Builder()
            .url(url)
            .header("X-Plex-Token", token)
            .header("Accept", "application/json")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.code.let { it in 200..299 }) {
                error("Plex responded ${resp.code}")
            }
            val body = resp.body?.string().orEmpty()
            return JSONObject(body)
        }
    }

    /** Returns all movies + shows across the server's libraries. */
    suspend fun fetchLibrary(baseUrl: String, token: String): List<PlexItem> = withContext(Dispatchers.IO) {
        val base = baseUrl.trimEnd('/')
        val sections = get("$base/library/sections", token)
            .optJSONObject("MediaContainer")
            ?.optJSONArray("Directory")
            ?: return@withContext emptyList()

        val out = ArrayList<PlexItem>()
        for (i in 0 until sections.length()) {
            val dir = sections.getJSONObject(i)
            val type = dir.optString("type")
            val kind = when (type) {
                "movie" -> MediaKind.MOVIE
                "show" -> MediaKind.TV
                else -> continue
            }
            val key = dir.optString("key")
            val items = get("$base/library/sections/$key/all?includeGuids=1", token)
                .optJSONObject("MediaContainer")
                ?.optJSONArray("Metadata")
                ?: continue
            for (j in 0 until items.length()) {
                val m = items.getJSONObject(j)
                val title = m.optString("title")
                if (title.isBlank()) continue
                val year = m.optInt("year", 0).takeIf { it > 0 }
                out.add(PlexItem(kind, title, year, extractTmdbId(m)))
            }
        }
        out
    }

    /** Pull a tmdb:// id out of the Plex Guid array, if present. */
    private fun extractTmdbId(m: JSONObject): String? {
        val guids = m.optJSONArray("Guid") ?: return null
        for (i in 0 until guids.length()) {
            val id = guids.getJSONObject(i).optString("id")
            if (id.startsWith("tmdb://")) return id.removePrefix("tmdb://")
        }
        return null
    }
}
