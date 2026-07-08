package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.Format
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.Platform
import com.kzaller.shelf.data.SteamClient
import com.kzaller.shelf.data.SteamGame
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.Status
import com.kzaller.shelf.data.models.CreateItemRequest
import com.kzaller.shelf.data.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface SteamImportState {
    data object Idle : SteamImportState
    data object Scanning : SteamImportState
    data class Importing(val total: Int) : SteamImportState
    data class Done(val imported: Int) : SteamImportState
    data class Error(val message: String) : SteamImportState
}

class SteamImportViewModel(
    private val repo: ShelfRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val steam = SteamClient()

    private val _state = MutableStateFlow<SteamImportState>(SteamImportState.Idle)
    val state: StateFlow<SteamImportState> = _state.asStateFlow()

    val savedKey = MutableStateFlow("")
    val savedId = MutableStateFlow("")

    init {
        viewModelScope.launch {
            savedKey.value = prefs.observeSteamKey().first()
            savedId.value = prefs.observeSteamId().first()
        }
    }

    fun connectAndImport(apiKey: String, idOrVanity: String) {
        viewModelScope.launch {
            prefs.setSteam(apiKey, idOrVanity)
            _state.value = SteamImportState.Scanning
            val games = runCatching {
                val steamId = steam.resolveSteamId(apiKey, idOrVanity)
                steam.fetchOwnedGames(apiKey, steamId)
            }.getOrElse {
                _state.value = SteamImportState.Error(it.message ?: "Couldn't reach Steam")
                return@launch
            }
            if (games.isEmpty()) {
                // Almost always a privacy setting rather than an empty library.
                _state.value = SteamImportState.Error("No games found — is your Steam profile's \"Game details\" set to Public?")
                return@launch
            }
            _state.value = SteamImportState.Importing(games.size)
            val requests = games.map { it.toRequest() }
            val inserted = repo.bulkImport(requests).getOrElse {
                _state.value = SteamImportState.Error(it.message ?: "Import failed")
                return@launch
            }
            repo.refresh(MediaKind.GAME, force = true)
            _state.value = SteamImportState.Done(inserted)
        }
    }

    private fun SteamGame.toRequest(): CreateItemRequest {
        // Everything from Steam is a PC game you own digitally; playtime > 0 means you've played it.
        val statuses = buildList {
            add(Status.OWNED)
            if (playtimeMinutes > 0) add(Status.PLAYED)
        }
        return CreateItemRequest(
            kind = MediaKind.GAME,
            title = name,
            subtitle = "PC",
            coverUrl = coverUrl,
            externalId = appId.toString(),
            externalSrc = "steam",
            status = statuses.joinToString(","),
            format = Format.DIGITAL,
            userPlatform = Platform.PC,
        )
    }

    companion object {
        fun factory(repo: ShelfRepository, prefs: AppPreferences): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SteamImportViewModel(repo, prefs) as T
            }
    }
}
