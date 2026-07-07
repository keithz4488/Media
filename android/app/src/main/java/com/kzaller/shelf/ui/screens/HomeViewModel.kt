package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.Format
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

/** One figure in the rotating "collection at a glance" ticker. */
data class GlanceStat(val value: String, val label: String)

class HomeViewModel(private val repo: ShelfRepository) : ViewModel() {
    val counts: StateFlow<Map<MediaKind, Int>> =
        repo.observeCounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * A pool of at-a-glance library figures the home screen rotates through like a news ticker.
     * Only meaningful (non-zero) stats are included, so the feed adapts to what's actually there.
     */
    val glanceStats: StateFlow<List<GlanceStat>> =
        repo.observeAll()
            .map { all ->
                if (all.isEmpty()) return@map emptyList<GlanceStat>()
                val year = LocalDate.now().year
                val zone = ZoneId.systemDefault()
                val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()

                fun pct(n: Int, d: Int) = if (d > 0) "${n * 100 / d}%" else "0%"

                val completedThisYear = all.count { it.completedAt != null && it.completedAt in start until end }
                val games = all.filter { it.kind == MediaKind.GAME }
                val gamesComplete = games.count { Status.COMPLETE in Status.parse(it.status) }
                val movies = all.filter { it.kind == MediaKind.MOVIE }
                val moviesWatched = movies.count { Status.WATCHED in Status.parse(it.status) }
                val books = all.filter { it.kind == MediaKind.BOOK }
                val booksRead = books.count { Status.READ in Status.parse(it.status) }
                val wishlist = all.count { Status.WISHLIST in Status.parse(it.status) }
                val favorites = all.count { it.rating == 5 }
                val withFormat = all.filter { !it.format.isNullOrBlank() }
                val digital = withFormat.count { Format.DIGITAL in Format.parse(it.format) }
                val rated = all.mapNotNull { it.rating }
                val avg = if (rated.isNotEmpty()) rated.average() else null

                buildList {
                    add(GlanceStat(all.size.toString(), "in library"))
                    add(GlanceStat(completedThisYear.toString(), "done in $year"))
                    if (games.isNotEmpty()) add(GlanceStat(pct(gamesComplete, games.size), "games 100%"))
                    if (moviesWatched > 0) add(GlanceStat(moviesWatched.toString(), "movies watched"))
                    if (booksRead > 0) add(GlanceStat(booksRead.toString(), "books read"))
                    if (wishlist > 0) add(GlanceStat(wishlist.toString(), "on wishlist"))
                    if (favorites > 0) add(GlanceStat(favorites.toString(), "5★ favorites"))
                    if (withFormat.isNotEmpty()) add(GlanceStat(pct(digital, withFormat.size), "digital"))
                    if (avg != null) add(GlanceStat(String.format("%.1f", avg), "avg rating"))
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
