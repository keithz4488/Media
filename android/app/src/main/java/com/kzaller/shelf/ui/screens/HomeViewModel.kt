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

class HomeViewModel(private val repo: ShelfRepository) : ViewModel() {
    val counts: StateFlow<Map<MediaKind, Int>> =
        repo.observeCounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The most recently added items across all shelves, for the "Recently added" home row. */
    val recentlyAdded: StateFlow<List<ItemDto>> =
        repo.observeAll()
            .map { all -> all.sortedByDescending { it.addedAt ?: 0L }.take(15) }
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
