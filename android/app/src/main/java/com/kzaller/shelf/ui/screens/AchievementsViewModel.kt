package com.kzaller.shelf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.Achievement
import com.kzaller.shelf.data.AchievementStats
import com.kzaller.shelf.data.Achievements
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AchievementUi(
    val achievement: Achievement,
    val current: Int,
    val unlocked: Boolean,
)

class AchievementsViewModel(
    private val repo: ShelfRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    val ui: StateFlow<List<AchievementUi>> =
        repo.observeAll().map { items ->
            val s = AchievementStats.from(items)
            Achievements.ALL.map { AchievementUi(it, it.current(s), it.unlocked(s)) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** FIFO queue of achievements just unlocked, surfaced as toasts on the home screen. */
    private val _queue = MutableStateFlow<List<Achievement>>(emptyList())
    val queue: StateFlow<List<Achievement>> = _queue.asStateFlow()

    init {
        viewModelScope.launch {
            // Load the real library from the server BEFORE we seed/diff. Otherwise the first
            // emission is the empty local cache: we'd bank an empty set, then treat the whole
            // library as "newly unlocked" the moment it loads — a toast storm on every fresh
            // install (which is exactly when the DataStore "seeded" flag is missing).
            repo.refreshAll()

            // Seeding is silent, so a reinstall quietly banks everything already earned and
            // only achievements crossed AFTER this point ever toast.
            var seeded = prefs.observeAchievementsSeeded().first()
            repo.observeAll().collect { items ->
                val computed = Achievements.unlockedIds(AchievementStats.from(items))
                if (!seeded) {
                    prefs.setUnlockedAchievements(computed)
                    prefs.setAchievementsSeeded()
                    seeded = true
                    return@collect
                }
                val stored = prefs.observeUnlockedAchievements().first()
                val fresh = computed - stored
                if (fresh.isNotEmpty()) {
                    prefs.setUnlockedAchievements(stored + computed)
                    _queue.value = _queue.value + Achievements.ALL.filter { it.id in fresh }
                }
            }
        }
    }

    fun consume() { _queue.value = _queue.value.drop(1) }

    companion object {
        fun factory(repo: ShelfRepository, prefs: AppPreferences): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AchievementsViewModel(repo, prefs) as T
            }
    }
}
