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

class ShelfViewModel(
    private val repo: ShelfRepository,
    private val kind: MediaKind,
) : ViewModel() {

    private val _filters = MutableStateFlow<Set<String>>(emptySet())
    val filters: StateFlow<Set<String>> = _filters.asStateFlow()

    /** Items visible on the shelf: full list, intersected with active status filters. */
    val items: StateFlow<List<ItemDto>> =
        combine(repo.observeShelf(kind), _filters) { all, filters ->
            // Defensive: the DAO already filters by kind in SQL, but enforce here too
            // so a stale emission can't slip a different-kind item into the grid.
            val ofKind = all.filter { it.kind == kind }
            if (filters.isEmpty()) ofKind
            else ofKind.filter { item ->
                val s = Status.parse(item.status)
                s.any { it in filters }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Full unfiltered count, for a "showing X of Y" label when filters are active. */
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
