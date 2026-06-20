package com.kzaller.shelf.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.ui.screens.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "media_shelf_prefs")

/**
 * App-wide user preferences. Currently just per-shelf sort mode; designed so we can drop more
 * settings in here (theme override, default add status, etc.) without restructuring.
 */
class AppPreferences(private val context: Context) {
    private fun sortKey(kind: MediaKind) = stringPreferencesKey("sort_${kind.wire}")

    fun observeSort(kind: MediaKind): Flow<SortMode> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[sortKey(kind)] ?: return@map SortMode.RECENT
            runCatching { SortMode.valueOf(raw) }.getOrDefault(SortMode.RECENT)
        }

    suspend fun setSort(kind: MediaKind, mode: SortMode) {
        context.dataStore.edit { it[sortKey(kind)] = mode.name }
    }
}
