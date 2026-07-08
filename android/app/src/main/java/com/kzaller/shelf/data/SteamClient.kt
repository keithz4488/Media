package com.kzaller.shelf.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One owned Steam game. */
data class SteamGame(
    val appId: Long,
    val name: String,
    val playtimeMinutes: Int,
) {
    /** Steam's portrait "library" art (nice for the shelf); falls back handled by the UI. */
    val coverUrl: String
        get() = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
}

/**
 * Minimal Steam Web API client. Resolves a SteamID (accepting a 64-bit id, a vanity name, or a
 * full profile URL) and fetches the account's owned games. Requires a Steam Web API key and the
 * profile's "Game details" set to Public.
 */
class SteamClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun get(url: String): JSONObject {
        val resp = http.newCall(Request.Builder().url(url).build()).execute()
        resp.use {
            if (it.code !in 200..299) error("Steam responded ${it.code}")
            return JSONObject(it.body?.string().orEmpty())
        }
    }

    /**
     * Turn whatever the user typed into a 64-bit SteamID. Accepts a raw id, a vanity handle, a
     * steamcommunity.com/profiles/<id> or /id/<vanity> URL.
     */
    suspend fun resolveSteamId(apiKey: String, input: String): String = withContext(Dispatchers.IO) {
        var v = input.trim().trimEnd('/')
        // Pull the tail off a profile URL if they pasted one.
        Regex("steamcommunity\\.com/profiles/(\\d+)").find(v)?.let { return@withContext it.groupValues[1] }
        Regex("steamcommunity\\.com/id/([^/?#]+)").find(v)?.let { v = it.groupValues[1] }
        // A 17-digit number is already a SteamID64.
        if (v.matches(Regex("\\d{17}"))) return@withContext v
        // Otherwise treat it as a vanity name and resolve it.
        val url = "https://api.steampowered.com/ISteamUser/ResolveVanityURL/v1/?key=$apiKey&vanityurl=$v"
        val r = get(url).optJSONObject("response")
        if (r?.optInt("success") == 1) {
            r.optString("steamid").ifBlank { error("Couldn't resolve that profile") }
        } else {
            error("No Steam profile matched \"$v\"")
        }
    }

    /** All owned games (including played free games) for the resolved SteamID. */
    suspend fun fetchOwnedGames(apiKey: String, steamId: String): List<SteamGame> = withContext(Dispatchers.IO) {
        val url = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/" +
            "?key=$apiKey&steamid=$steamId&include_appinfo=1&include_played_free_games=1&format=json"
        val games = get(url).optJSONObject("response")?.optJSONArray("games")
            ?: return@withContext emptyList()
        val out = ArrayList<SteamGame>(games.length())
        for (i in 0 until games.length()) {
            val g = games.getJSONObject(i)
            val name = g.optString("name")
            if (name.isBlank()) continue
            out.add(
                SteamGame(
                    appId = g.optLong("appid"),
                    name = name,
                    playtimeMinutes = g.optInt("playtime_forever", 0),
                ),
            )
        }
        out
    }
}
