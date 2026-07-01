package com.kzaller.shelf.data

import com.kzaller.shelf.data.models.ItemDto

/** A single unlockable achievement, defined declaratively against a stats snapshot. */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val target: Int,                 // for progress display
    val rarity: Rarity = Rarity.COMMON,
    val progress: (AchievementStats) -> Int,
) {
    fun current(stats: AchievementStats): Int = progress(stats).coerceAtMost(target)
    fun unlocked(stats: AchievementStats): Boolean = progress(stats) >= target
}

/** Rarity tiers, used to color the achievement UI. */
enum class Rarity { COMMON, RARE, EPIC, LEGENDARY }

/** Everything the achievement predicates need, computed once from the full library. */
data class AchievementStats(
    val total: Int,
    val byKind: Map<MediaKind, Int>,
    val completed: Int,
    val rated: Int,
    val fiveStars: Int,
    val gamePlatforms: Int,
    val consoles: Int,
    val gamesComplete100: Int,
    val kindsWithItems: Int,
    val balanced25: Boolean,     // 25+ on every shelf
    val withNotes: Int,
    val oldest: Int?,            // earliest year in the library
    val distinctYears: Int,
) {
    companion object {
        fun from(items: List<ItemDto>): AchievementStats {
            val byKind = items.groupBy { it.kind }.mapValues { it.value.size }
            val platforms = items
                .filter { it.kind == MediaKind.GAME }
                .flatMap { Platform.parse(it.userPlatform) }
                .toSet()
            val consoles = items
                .filter { it.kind == MediaKind.GAME }
                .flatMap { Console.parse(it.consoles) }
                .toSet()
            val complete100 = items.count { it.kind == MediaKind.GAME && Status.parse(it.status).contains(Status.COMPLETE) }
            val years = items.mapNotNull { it.year }.filter { it > 0 }
            val balanced = MediaKind.values().all { (byKind[it] ?: 0) >= 25 }
            return AchievementStats(
                total = items.size,
                byKind = byKind,
                completed = items.count { it.completedAt != null },
                rated = items.count { (it.rating ?: 0) > 0 },
                fiveStars = items.count { it.rating == 5 },
                gamePlatforms = platforms.size,
                consoles = consoles.size,
                gamesComplete100 = complete100,
                kindsWithItems = byKind.keys.size,
                balanced25 = balanced,
                withNotes = items.count { !it.notes.isNullOrBlank() },
                oldest = years.minOrNull(),
                distinctYears = years.toSet().size,
            )
        }
    }
}

object Achievements {
    val ALL: List<Achievement> = listOf(
        // --- getting started ---
        Achievement("first", "First Find", "Add your first item", "🌱", 1, Rarity.COMMON) { it.total },
        Achievement("well_rounded", "Well Rounded", "Have something on all 4 shelves", "🧭", 4, Rarity.COMMON) { it.kindsWithItems },

        // --- size of collection ---
        Achievement("starter", "Getting Started", "Own 10 items", "🗂️", 10, Rarity.COMMON) { it.total },
        Achievement("collector", "Collector", "Own 50 items", "📚", 50, Rarity.RARE) { it.total },
        Achievement("curator", "Curator", "Own 100 items", "🖼️", 100, Rarity.RARE) { it.total },
        Achievement("hoarder", "Hoarder", "Own 250 items", "🏛️", 250, Rarity.EPIC) { it.total },
        Achievement("archivist", "Archivist", "Own 500 items", "🗄️", 500, Rarity.LEGENDARY) { it.total },

        // --- finishing things ---
        Achievement("finisher", "Finisher", "Finish your first item", "🎯", 1, Rarity.COMMON) { it.completed },
        Achievement("completionist", "Completionist", "Finish 10 items", "✅", 10, Rarity.RARE) { it.completed },
        Achievement("marathoner", "Marathoner", "Finish 50 items", "🏅", 50, Rarity.EPIC) { it.completed },
        Achievement("no_backlog", "Backlog Slayer", "Finish 150 items", "⚔️", 150, Rarity.LEGENDARY) { it.completed },

        // --- rating / curation ---
        Achievement("critic", "Critic", "Rate 20 items", "⭐", 20, Rarity.RARE) { it.rated },
        Achievement("tastemaker", "Tastemaker", "Give 10 five-star ratings", "🌟", 10, Rarity.EPIC) { it.fiveStars },
        Achievement("annotator", "Annotator", "Add notes to 15 items", "📝", 15, Rarity.RARE) { it.withNotes },

        // --- per-shelf ---
        Achievement("bookworm", "Bookworm", "Shelve 25 books", "📖", 25, Rarity.RARE) { it.byKind[MediaKind.BOOK] ?: 0 },
        Achievement("cinephile", "Cinephile", "Shelve 25 movies", "🎬", 25, Rarity.RARE) { it.byKind[MediaKind.MOVIE] ?: 0 },
        Achievement("binger", "Binger", "Shelve 25 TV shows", "📺", 25, Rarity.RARE) { it.byKind[MediaKind.TV] ?: 0 },
        Achievement("gamer", "Gamer", "Shelve 25 games", "🎮", 25, Rarity.RARE) { it.byKind[MediaKind.GAME] ?: 0 },
        Achievement("balanced", "Renaissance", "25+ on every shelf", "⚖️", 1, Rarity.LEGENDARY) { if (it.balanced25) 1 else 0 },

        // --- games specifically ---
        Achievement("platform_agnostic", "Platform Agnostic", "Own games on 3 platforms", "🕹️", 3, Rarity.RARE) { it.gamePlatforms },
        Achievement("console_wars", "Console Wars", "Own games across 6 consoles", "🎛️", 6, Rarity.EPIC) { it.consoles },
        Achievement("perfectionist", "Perfectionist", "100% five games", "💯", 5, Rarity.EPIC) { it.gamesComplete100 },

        // --- collection depth / history ---
        Achievement("time_traveler", "Time Traveler", "Own items from 20 different years", "🕰️", 20, Rarity.EPIC) { it.distinctYears },
        Achievement("retro", "Retro Hunter", "Own something from before 1990", "📼", 1, Rarity.RARE) { if ((it.oldest ?: 9999) < 1990) 1 else 0 },
    )

    fun unlockedIds(stats: AchievementStats): Set<String> =
        ALL.filter { it.unlocked(stats) }.map { it.id }.toSet()
}
