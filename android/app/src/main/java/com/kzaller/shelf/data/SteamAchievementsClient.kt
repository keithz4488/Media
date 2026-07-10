package com.kzaller.shelf.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One Steam achievement, merged from the player's unlock state + schema + global rarity. */
data class SteamAchievement(
    val apiName: String,
    val name: String,
    val description: String,
    val iconUrl: String,       // full-color icon (shown when unlocked)
    val iconGrayUrl: String,   // greyed icon (shown when locked)
    val unlocked: Boolean,
    val unlockTime: Long,      // epoch seconds, 0 if locked
    val percent: Double?,      // global % of players who have it (rarity)
)

data class SteamAchievementData(
    val unlocked: Int,
    val total: Int,
    val achievements: List<SteamAchievement>,
) {
    val pct: Int get() = if (total > 0) unlocked * 100 / total else 0
    /** The rarest achievement the player has actually earned. */
    val rarestUnlocked: SteamAchievement?
        get() = achievements.filter { it.unlocked && it.percent != null }.minByOrNull { it.percent!! }
}

/**
 * Fetches a game's achievements for a player and merges the three Steam sources: the player's
 * unlock state, the game schema (names + icons), and global unlock percentages (rarity). Returns
 * null when the game has no achievements or the profile isn't readable.
 */
class SteamAchievementsClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun get(url: String): JSONObject? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (resp.code !in 200..299) return null
            JSONObject(resp.body?.string().orEmpty())
        }
    }.getOrNull()

    suspend fun fetch(apiKey: String, steamId: String, appId: String): SteamAchievementData? =
        withContext(Dispatchers.IO) {
            // 1) Player unlock state — the source of truth for which achievements exist.
            val player = get(
                "https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v1/" +
                    "?appid=$appId&key=$apiKey&steamid=$steamId&l=english",
            )?.optJSONObject("playerstats")
            if (player == null || !player.optBoolean("success", false)) return@withContext null
            val list = player.optJSONArray("achievements") ?: return@withContext null
            if (list.length() == 0) return@withContext null

            // 2) Schema — display names, descriptions, icons.
            val schema = get(
                "https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/" +
                    "?key=$apiKey&appid=$appId&l=english",
            )?.optJSONObject("game")?.optJSONObject("availableGameStats")?.optJSONArray("achievements")
            val meta = HashMap<String, JSONObject>()
            if (schema != null) {
                for (i in 0 until schema.length()) {
                    val s = schema.getJSONObject(i)
                    meta[s.optString("name")] = s
                }
            }

            // 3) Global unlock percentages (rarity) — no key required.
            val global = get(
                "https://api.steampowered.com/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v2/" +
                    "?gameid=$appId&format=json",
            )?.optJSONObject("achievementpercentages")?.optJSONArray("achievements")
            val pctMap = HashMap<String, Double>()
            if (global != null) {
                for (i in 0 until global.length()) {
                    val g = global.getJSONObject(i)
                    pctMap[g.optString("name")] = g.optDouble("percent", Double.NaN).takeIf { !it.isNaN() } ?: continue
                }
            }

            val merged = ArrayList<SteamAchievement>(list.length())
            for (i in 0 until list.length()) {
                val a = list.getJSONObject(i)
                val api = a.optString("apiname")
                val m = meta[api]
                merged.add(
                    SteamAchievement(
                        apiName = api,
                        name = m?.optString("displayName")?.ifBlank { api } ?: api,
                        description = m?.optString("description").orEmpty(),
                        iconUrl = m?.optString("icon").orEmpty(),
                        iconGrayUrl = m?.optString("icongray").orEmpty(),
                        unlocked = a.optInt("achieved", 0) == 1,
                        unlockTime = a.optLong("unlocktime", 0),
                        percent = pctMap[api],
                    ),
                )
            }
            // Unlocked first, rarest (lowest %) first within each group.
            merged.sortWith(compareByDescending<SteamAchievement> { it.unlocked }.thenBy { it.percent ?: 101.0 })
            SteamAchievementData(
                unlocked = merged.count { it.unlocked },
                total = merged.size,
                achievements = merged,
            )
        }
}
