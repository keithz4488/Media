package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.Status
import com.kzaller.shelf.data.models.ItemDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortMode(val label: String) {
    RECENT("Recently added"),
    TITLE_ASC("Title A-Z"),
    TITLE_DESC("Title Z-A"),
    YEAR_DESC("Year (newest)"),
    YEAR_ASC("Year (oldest)"),
}

class ShelfViewModel(
    private val repo: ShelfRepository,
    private val kind: MediaKind,
) : ViewModel() {

    private val _filters = MutableStateFlow<Set<String>>(emptySet())
    val filters: StateFlow<Set<String>> = _filters.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(SortMode.RECENT)
    val sort: StateFlow<SortMode> = _sort.asStateFlow()

    /** Items visible on the shelf: kind -> status filter -> text search -> sort. */
    val items: StateFlow<List<ItemDto>> =
        combine(repo.observeShelf(kind), _filters, _query, _sort) { all, filters, query, sort ->
            // Defensive: the DAO already filters by kind in SQL, but enforce here too
            // so a stale emission can't slip a different-kind item into the grid.
            val ofKind = all.filter { it.kind == kind }

            val statusFiltered = if (filters.isEmpty()) ofKind
                else ofKind.filter { item ->
                    val s = Status.parse(item.status)
                    s.any { it in filters }
                }

            val searched = if (query.isBlank()) statusFiltered
                else statusFiltered.filter { item ->
                    item.title.contains(query, ignoreCase = true) ||
                        (item.subtitle?.contains(query, ignoreCase = true) == true)
                }

            when (sort) {
                SortMode.RECENT     -> searched.sortedByDescending { it.addedAt ?: 0L }
                SortMode.TITLE_ASC  -> searched.sortedBy { it.title.lowercase() }
                SortMode.TITLE_DESC -> searched.sortedByDescending { it.title.lowercase() }
                SortMode.YEAR_DESC  -> searched.sortedByDescending { it.year ?: Int.MIN_VALUE }
                SortMode.YEAR_ASC   -> searched.sortedBy { it.year ?: Int.MAX_VALUE }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Full unfiltered count, for a "showing X of Y" label when filters/search are active. */
    val totalCount: StateFlow<Int> =
        repo.observeShelf(kind)
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            repo.refresh(kind).onFailure { _error.value = it.message }
            _refreshing.value = false
        }
    }

    fun toggleFilter(code: String) {
        _filters.value = _filters.value.toMutableSet().apply {
            if (!add(code)) remove(code)
        }
    }

    fun clearFilters() { _filters.value = emptySet() }

    fun setSearch(q: String) { _query.value = q }
    fun clearSearch() { _query.value = "" }

    fun setSort(mode: SortMode) { _sort.value = mode }

    fun clearError() { _error.value = null }

    companion object {
        fun factory(repo: ShelfRepository, kind: MediaKind): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ShelfViewModel(repo, kind) as T
            }
    }
}
