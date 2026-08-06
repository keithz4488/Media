package com.kzaller.shelf.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.ui.screens.SortMode
import com.kzaller.shelf.ui.screens.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "media_shelf_prefs")

/**
 * App-wide user preferences. Currently per-shelf sort mode + view mode (grid vs list);
 * designed so we can drop more settings in here without restructuring.
 */
class AppPreferences(private val context: Context) {
    private fun sortKey(kind: MediaKind) = stringPreferencesKey("sort_${kind.wire}")
    private fun viewKey(kind: MediaKind) = stringPreferencesKey("view_${kind.wire}")

    fun observeSort(kind: MediaKind): Flow<SortMode> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[sortKey(kind)] ?: return@map SortMode.RECENT
            runCatching { SortMode.valueOf(raw) }.getOrDefault(SortMode.RECENT)
        }

    suspend fun setSort(kind: MediaKind, mode: SortMode) {
        context.dataStore.edit { it[sortKey(kind)] = mode.name }
    }

    private val plexUrlKey = stringPreferencesKey("plex_url")
    private val plexTokenKey = stringPreferencesKey("plex_token")

    fun observePlexUrl(): Flow<String> = context.dataStore.data.map { it[plexUrlKey] ?: "" }
    fun observePlexToken(): Flow<String> = context.dataStore.data.map { it[plexTokenKey] ?: "" }

    suspend fun setPlex(url: String, token: String) {
        context.dataStore.edit {
            it[plexUrlKey] = url.trim().trimEnd('/')
            it[plexTokenKey] = token.trim()
        }
    }

    private val steamKeyKey = stringPreferencesKey("steam_key")
    private val steamIdKey = stringPreferencesKey("steam_id")

    fun observeSteamKey(): Flow<String> = context.dataStore.data.map { it[steamKeyKey] ?: "" }
    fun observeSteamId(): Flow<String> = context.dataStore.data.map { it[steamIdKey] ?: "" }

    suspend fun setSteam(key: String, id: String) {
        context.dataStore.edit {
            it[steamKeyKey] = key.trim()
            it[steamIdKey] = id.trim()
        }
    }

    private val unlockedKey = stringPreferencesKey("achievements_unlocked")

    /** Set of achievement ids already unlocked (so we only toast newly-earned ones). */
    fun observeUnlockedAchievements(): Flow<Set<String>> =
        context.dataStore.data.map { prefs ->
            prefs[unlockedKey]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        }

    suspend fun setUnlockedAchievements(ids: Set<String>) {
        context.dataStore.edit { it[unlockedKey] = ids.joinToString(",") }
    }

    private val seededKey = booleanPreferencesKey("achievements_seeded")

    fun observeAchievementsSeeded(): Flow<Boolean> =
        context.dataStore.data.map { it[seededKey] ?: false }

    suspend fun setAchievementsSeeded() {
        context.dataStore.edit { it[seededKey] = true }
    }

    private val knownAchKey = stringPreferencesKey("achievements_known")

    /** IDs of achievements the app knew about last run, so newly-added ones don't toast-storm. */
    fun observeKnownAchievements(): Flow<Set<String>> =
        context.dataStore.data.map { prefs ->
            prefs[knownAchKey]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        }

    suspend fun setKnownAchievements(ids: Set<String>) {
        context.dataStore.edit { it[knownAchKey] = ids.joinToString(",") }
    }

    private val sessionTokenKey = stringPreferencesKey("auth_session_token")
    private val sessionExpiryKey = androidx.datastore.preferences.core.longPreferencesKey("auth_session_expiry")
    private val emailKey = stringPreferencesKey("auth_email")

    /** The long-lived app session token sent as the API bearer (empty when signed out). */
    fun observeSessionToken(): Flow<String> = context.dataStore.data.map { it[sessionTokenKey] ?: "" }
    /** Epoch millis when the session expires (0 if unknown). */
    fun observeSessionExpiry(): Flow<Long> = context.dataStore.data.map { it[sessionExpiryKey] ?: 0L }
    /** The signed-in Google account email, or "" when signed out. */
    fun observeEmail(): Flow<String> = context.dataStore.data.map { it[emailKey] ?: "" }

    suspend fun setSession(token: String, expiresAt: Long, email: String) {
        context.dataStore.edit {
            it[sessionTokenKey] = token
            it[sessionExpiryKey] = expiresAt
            it[emailKey] = email
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit {
            it.remove(sessionTokenKey)
            it.remove(sessionExpiryKey)
            it.remove(emailKey)
        }
    }

    private val columnsKey = androidx.datastore.preferences.core.intPreferencesKey("shelf_columns")

    /** How many covers sit across a shelf row (2–6). Global; higher = smaller covers. */
    fun observeColumns(): Flow<Int> =
        context.dataStore.data.map { (it[columnsKey] ?: 3).coerceIn(2, 6) }

    suspend fun setColumns(n: Int) {
        context.dataStore.edit { it[columnsKey] = n.coerceIn(2, 6) }
    }

    fun observeViewMode(kind: MediaKind): Flow<ViewMode> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[viewKey(kind)] ?: return@map ViewMode.GRID
            runCatching { ViewMode.valueOf(raw) }.getOrDefault(ViewMode.GRID)
        }

    suspend fun setViewMode(kind: MediaKind, mode: ViewMode) {
        context.dataStore.edit { it[viewKey(kind)] = mode.name }
    }
}
