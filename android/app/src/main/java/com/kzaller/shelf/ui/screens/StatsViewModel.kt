package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.models.ItemDto
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    val snapshot: StateFlow<StatsSnapshot> = repo.observeAll()
        .map(::aggregate)
        .stateIn(viewModelScope, SharingStarted.Eagerly, empty())

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

    private fun aggregate(items: List<ItemDto>): StatsSnapshot {
        val year = LocalDate.now().year
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

        val recent = items
            .filter { it.completedAt != null }
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
