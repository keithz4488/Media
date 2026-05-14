package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.models.ItemDto
import com.kzaller.shelf.data.models.UpdateItemRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    private val repo: ShelfRepository,
    private val id: String,
) : ViewModel() {

    val item: StateFlow<ItemDto?> =
        repo.observeItem(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun setStatus(status: String) = launchUpdate(UpdateItemRequest(status = status))
    fun setRating(rating: Int?) = launchUpdate(UpdateItemRequest(rating = rating))
    fun setNotes(notes: String) = launchUpdate(UpdateItemRequest(notes = notes))

    private fun launchUpdate(req: UpdateItemRequest) {
        viewModelScope.launch {
            _busy.value = true
            repo.update(id, req)
            _busy.value = false
        }
    }

    fun delete(after: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            repo.delete(id).onSuccess { after() }
            _busy.value = false
        }
    }

    companion object {
        fun factory(repo: ShelfRepository, id: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DetailViewModel(repo, id) as T
            }
    }
}
