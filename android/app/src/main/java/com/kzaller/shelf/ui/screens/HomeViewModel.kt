package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.Status
import com.kzaller.shelf.data.models.ItemDto
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repo: ShelfRepository) : ViewModel() {
    val counts: StateFlow<Map<MediaKind, Int>> =
        repo.observeCounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** In-progress items (watching / playing / reading) for the "Jump back in" home row,
     *  most-recently-touched first. */
    val inProgress: StateFlow<List<ItemDto>> =
        repo.observeAll()
            .map { all ->
                all.filter { item ->
                    val s = Status.parse(item.status)
                    Status.WATCHING in s || Status.PLAYING in s || Status.READING in s
                }.sortedByDescending { it.updatedAt ?: it.addedAt ?: 0L }.take(15)
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
