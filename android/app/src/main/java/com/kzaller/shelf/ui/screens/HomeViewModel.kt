package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.Status
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** A few at-a-glance library figures for the home screen. */
data class GlanceStats(
    val total: Int,
    val completedThisYear: Int,
    val gamesComplete: Int,
    val gamesTotal: Int,
    val year: Int,
)

class HomeViewModel(private val repo: ShelfRepository) : ViewModel() {
    val counts: StateFlow<Map<MediaKind, Int>> =
        repo.observeCounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** "Collection at a glance": total items, things finished this year, and games 100%'d. */
    val glance: StateFlow<GlanceStats> =
        repo.observeAll()
            .map { all ->
                val year = LocalDate.now().year
                val zone = ZoneId.systemDefault()
                val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
                val completed = all.count { it.completedAt != null && it.completedAt in start until end }
                val games = all.filter { it.kind == MediaKind.GAME }
                val gamesComplete = games.count { Status.COMPLETE in Status.parse(it.status) }
                GlanceStats(all.size, completed, gamesComplete, games.size, year)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, GlanceStats(0, 0, 0, 0, LocalDate.now().year))

    init {
        // Pull fresh data on home open so counts reflect the server, not just the local cache.
        viewModelScope.launch { repo.refreshAll() }
    }

    companion object {
        fun factory(repo: ShelfRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(repo) as T
            }
    }
}
