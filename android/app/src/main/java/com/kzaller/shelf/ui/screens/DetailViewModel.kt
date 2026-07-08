package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.Status
import com.kzaller.shelf.data.models.CoverOption
import com.kzaller.shelf.data.models.ItemDto
import com.kzaller.shelf.data.models.Scores
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

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()

    private val _scores = MutableStateFlow<Scores?>(null)
    val scores = _scores.asStateFlow()

    /** Fetch IGDB review scores once. No-op result for non-game / non-IGDB items. */
    fun loadScores() {
        if (_scores.value != null) return
        viewModelScope.launch {
            repo.loadScores(id).onSuccess { _scores.value = it }
        }
    }

    fun setStatus(status: String) = launchUpdate(UpdateItemRequest(status = status))
    fun setRating(rating: Int?) = launchUpdate(UpdateItemRequest(rating = rating), toast = if (rating == null) "Rating cleared" else "Rated $rating")
    fun setNotes(notes: String) = launchUpdate(UpdateItemRequest(notes = notes), toast = "Notes saved")
    fun setCover(url: String) = launchUpdate(UpdateItemRequest(coverUrl = url), toast = "Cover updated")
    fun setPlatform(csv: String) = launchUpdate(UpdateItemRequest(userPlatform = csv))
    /** Update both platform + consoles atomically. Used when a platform toggle off
     *  also needs to strip its now-orphaned consoles. */
    fun setPlatformAndConsoles(platformsCsv: String, consolesCsv: String) =
        launchUpdate(UpdateItemRequest(userPlatform = platformsCsv, consoles = consolesCsv))
    fun setConsoles(csv: String) = launchUpdate(UpdateItemRequest(consoles = csv))
    fun setFormat(csv: String) = launchUpdate(UpdateItemRequest(format = csv))
    fun setShowTo(csv: String) = launchUpdate(UpdateItemRequest(showTo = csv))
    // The backend applies the episode-precise "caught up -> Watched" flip on any progress change
    // (from here or the Plex webhook) and returns the updated item, so we just send the progress.
    fun setProgress(season: Int?, episode: Int?) =
        launchUpdate(UpdateItemRequest(curSeason = season, curEpisode = episode))
    fun setCompletedAt(millis: Long?) =
        launchUpdate(UpdateItemRequest(completedAt = millis), toast = if (millis == null) "Cleared completion" else "Marked complete")

    /**
     * Movies/TV: keep the "watched" status and the Watched section in lockstep. Marking watched
     * adds the status and stamps a completion date (using the picked date if given); un-marking
     * removes the status. Driven off the current item, so it merges cleanly with other statuses.
     */
    fun setWatched(watched: Boolean, at: Long? = null) {
        val cur = item.value ?: return
        val newStatus =
            if (watched) Status.ensure(cur.status, Status.WATCHED)
            else Status.without(cur.status, Status.WATCHED)
        val date = if (watched) (at ?: cur.completedAt ?: System.currentTimeMillis()) else cur.completedAt
        launchUpdate(UpdateItemRequest(status = newStatus, completedAt = date))
    }

    /** Lazily fill in TV season/episode counts (including the per-season breakdown) for items
     *  that were imported or added before that data existed. */
    fun ensureEnriched() {
        val cur = item.value ?: return
        if (cur.kind == MediaKind.TV && (cur.seasons == null || cur.seasonEpisodes == null)) {
            viewModelScope.launch { repo.refreshDetails(id) }
        }
    }

    private val _covers = MutableStateFlow<List<CoverOption>>(emptyList())
    val covers = _covers.asStateFlow()

    private val _loadingCovers = MutableStateFlow(false)
    val loadingCovers = _loadingCovers.asStateFlow()

    fun loadCovers() {
        viewModelScope.launch {
            _loadingCovers.value = true
            repo.listCovers(id)
                .onSuccess { _covers.value = it }
                .onFailure { _error.value = it.message ?: "couldn't load covers" }
            _loadingCovers.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _busy.value = true
            _toast.value = "Refreshing details…"
            repo.refreshDetails(id)
                .onSuccess { _toast.value = "Details refreshed" }
                .onFailure { _error.value = it.message ?: "refresh failed" }
            _busy.value = false
        }
    }

    private fun launchUpdate(req: UpdateItemRequest, toast: String? = null) {
        viewModelScope.launch {
            _busy.value = true
            repo.update(id, req)
                .onSuccess { if (toast != null) _toast.value = toast }
                .onFailure { _error.value = it.message ?: "Update failed" }
            _busy.value = false
        }
    }

    fun delete(after: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            repo.delete(id)
                .onSuccess { after() }
                .onFailure { _error.value = it.message ?: "Delete failed" }
            _busy.value = false
        }
    }

    fun clearError() { _error.value = null }
    fun clearToast() { _toast.value = null }

    companion object {
        fun factory(repo: ShelfRepository, id: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DetailViewModel(repo, id) as T
            }
    }
}
