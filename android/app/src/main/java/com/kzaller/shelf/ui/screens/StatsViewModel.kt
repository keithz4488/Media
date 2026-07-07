package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.models.ItemDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class StatsSnapshot(
    val totalsByKind: Map<MediaKind, Int>,
    val completedYtdByKind: Map<MediaKind, Int>,
    val avgRatingByKind: Map<MediaKind, Double?>,
    val recentlyCompleted: List<ItemDto>,
    val currentYear: Int,
) {
    val totalAll: Int get() = totalsByKind.values.sum()
    val completedYtdAll: Int get() = completedYtdByKind.values.sum()
}

class StatsViewModel(private val repo: ShelfRepository) : ViewModel() {
    /** The year the review is showing; defaults to the current year, user-selectable. */
    private val _year = MutableStateFlow(LocalDate.now().year)
    val year: StateFlow<Int> = _year.asStateFlow()

    fun setYear(y: Int) { _year.value = y }

    val snapshot: StateFlow<StatsSnapshot> =
        combine(repo.observeAll(), _year) { items, year -> aggregate(items, year) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, empty())

    /** Years that have any completed items, newest first, always including the current year. */
    val availableYears: StateFlow<List<Int>> = repo.observeAll()
        .map { items ->
            val zone = ZoneId.systemDefault()
            val years = items.mapNotNull { it.completedAt }
                .map { java.time.Instant.ofEpochMilli(it).atZone(zone).year }
                .toMutableSet()
            years.add(LocalDate.now().year)
            years.sortedDescending()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(LocalDate.now().year))

    /** Full library for export/backup. */
    val allItems: StateFlow<List<ItemDto>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Fresh data before rendering stats off stale cache.
        viewModelScope.launch { repo.refreshAll() }
    }

    private fun empty() = StatsSnapshot(
        totalsByKind = emptyMap(),
        completedYtdByKind = emptyMap(),
        avgRatingByKind = emptyMap(),
        recentlyCompleted = emptyList(),
        currentYear = LocalDate.now().year,
    )

    private fun aggregate(items: List<ItemDto>, year: Int): StatsSnapshot {
        val zone = ZoneId.systemDefault()
        val startOfYear = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfNextYear = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()

        val totals = items.groupBy { it.kind }.mapValues { it.value.size }

        val completedYtd = items
            .filter { it.completedAt != null && it.completedAt in startOfYear until startOfNextYear }
            .groupBy { it.kind }
            .mapValues { it.value.size }

        val avgRating = items
            .filter { it.rating != null }
            .groupBy { it.kind }
            .mapValues { entry ->
                val ratings = entry.value.mapNotNull { it.rating }
                if (ratings.isEmpty()) null else ratings.average()
            }

        // Recently completed within the selected year (newest first).
        val recent = items
            .filter { it.completedAt != null && it.completedAt in startOfYear until startOfNextYear }
            .sortedByDescending { it.completedAt }
            .take(6)

        return StatsSnapshot(
            totalsByKind = totals,
            completedYtdByKind = completedYtd,
            avgRatingByKind = avgRating,
            recentlyCompleted = recent,
            currentYear = year,
        )
    }

    companion object {
        fun factory(repo: ShelfRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StatsViewModel(repo) as T
            }
    }
}
