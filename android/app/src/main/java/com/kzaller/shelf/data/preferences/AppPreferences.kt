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

    fun observeViewMode(kind: MediaKind): Flow<ViewMode> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[viewKey(kind)] ?: return@map ViewMode.GRID
            runCatching { ViewMode.valueOf(raw) }.getOrDefault(ViewMode.GRID)
        }

    suspend fun setViewMode(kind: MediaKind, mode: ViewMode) {
        context.dataStore.edit { it[viewKey(kind)] = mode.name }
    }
}
