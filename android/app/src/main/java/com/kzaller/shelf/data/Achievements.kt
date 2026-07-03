package com.kzaller.shelf.data

import com.kzaller.shelf.data.models.ItemDto

/** Rarity tiers, used to color the achievement UI. */
enum class Rarity { COMMON, RARE, EPIC, LEGENDARY }

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
    val balancedMin: Int,        // smallest per-shelf count (min across the 4 shelves)
    val withNotes: Int,
    val oldest: Int?,
    val newest: Int?,
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
            val balancedMin = MediaKind.values().minOf { byKind[it] ?: 0 }
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
                balancedMin = balancedMin,
                withNotes = items.count { !it.notes.isNullOrBlank() },
                oldest = years.minOrNull(),
                newest = years.maxOrNull(),
                distinctYears = years.toSet().size,
            )
        }
    }
}

object Achievements {

    private val ROMAN = listOf(
        "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
        "XI", "XII", "XIII", "XIV", "XV", "XVI",
    )

    private fun rarityForTier(index: Int, count: Int): Rarity {
        if (index == count - 1) return Rarity.LEGENDARY
        val f = if (count <= 1) 1f else index.toFloat() / (count - 1)
        return when {
            f < 0.34f -> Rarity.COMMON
            f < 0.67f -> Rarity.RARE
            else -> Rarity.EPIC
        }
    }

    /** Build a family of escalating-threshold achievements sharing a name + emoji. */
    private fun tiers(
        idBase: String,
        name: String,
        emoji: String,
        thresholds: List<Int>,
        desc: (Int) -> String,
        metric: (AchievementStats) -> Int,
    ): List<Achievement> = thresholds.mapIndexed { i, t ->
        Achievement(
            id = "${idBase}_$i",
            title = "$name ${ROMAN[i]}",
            description = desc(t),
            emoji = emoji,
            target = t,
            rarity = rarityForTier(i, thresholds.size),
            progress = metric,
        )
    }

    val ALL: List<Achievement> = buildList {
        addAll(tiers("total", "Collector", "📚",
            listOf(1, 5, 10, 25, 50, 75, 100, 150, 200, 300, 400, 500, 750, 1000, 1500, 2000),
            { "Own $it items" }) { it.total })
        addAll(tiers("book", "Bookworm", "📖",
            listOf(1, 5, 10, 25, 50, 100, 200), { "Shelve $it books" }) { it.byKind[MediaKind.BOOK] ?: 0 })
        addAll(tiers("movie", "Cinephile", "🎬",
            listOf(1, 5, 10, 25, 50, 100, 200), { "Shelve $it movies" }) { it.byKind[MediaKind.MOVIE] ?: 0 })
        addAll(tiers("tv", "Binger", "📺",
            listOf(1, 5, 10, 25, 50, 100), { "Shelve $it TV shows" }) { it.byKind[MediaKind.TV] ?: 0 })
        addAll(tiers("game", "Gamer", "🎮",
            listOf(1, 5, 10, 25, 50, 100, 200), { "Shelve $it games" }) { it.byKind[MediaKind.GAME] ?: 0 })
        addAll(tiers("done", "Finisher", "✅",
            listOf(1, 5, 10, 25, 50, 100, 150, 250, 500), { "Finish $it items" }) { it.completed })
        addAll(tiers("rated", "Critic", "⭐",
            listOf(1, 10, 25, 50, 100, 200, 500), { "Rate $it items" }) { it.rated })
        addAll(tiers("five", "Superfan", "🌟",
            listOf(1, 5, 10, 25, 50, 100), { "Give $it five-star ratings" }) { it.fiveStars })
        addAll(tiers("notes", "Annotator", "📝",
            listOf(1, 5, 15, 30, 60), { "Add notes to $it items" }) { it.withNotes })
        addAll(tiers("years", "Time Traveler", "🕰️",
            listOf(3, 5, 10, 20, 30, 50), { "Own items from $it different years" }) { it.distinctYears })
        addAll(tiers("plat", "Platform Hopper", "🕹️",
            listOf(2, 3, 4, 5), { "Own games on $it platforms" }) { it.gamePlatforms })
        addAll(tiers("cons", "Console Collector", "🎛️",
            listOf(2, 4, 6, 8, 12), { "Own games across $it consoles" }) { it.consoles })
        addAll(tiers("hundo", "Perfectionist", "💯",
            listOf(1, 3, 5, 10, 25, 50), { "100% $it games" }) { it.gamesComplete100 })

        // --- unique specials (9) ---
        add(Achievement("well_rounded", "Well Rounded", "Have something on all 4 shelves", "🧭", 4, Rarity.RARE) { it.kindsWithItems })
        add(Achievement("full_house", "Full House", "10+ on every shelf", "🏠", 10, Rarity.EPIC) { it.balancedMin })
        add(Achievement("renaissance", "Renaissance", "25+ on every shelf", "⚖️", 25, Rarity.EPIC) { it.balancedMin })
        add(Achievement("grandmaster", "Grandmaster", "50+ on every shelf", "👑", 50, Rarity.LEGENDARY) { it.balancedMin })
        add(Achievement("hall_of_fame", "Hall of Fame", "100+ on every shelf", "🏆", 100, Rarity.LEGENDARY) { it.balancedMin })
        add(Achievement("retro", "Retro Hunter", "Own something from before 1990", "📼", 1, Rarity.RARE) { if ((it.oldest ?: 9999) < 1990) 1 else 0 })
        add(Achievement("vintage", "Vintage Vault", "Own something from before 1980", "💿", 1, Rarity.EPIC) { if ((it.oldest ?: 9999) < 1980) 1 else 0 })
        add(Achievement("antiquarian", "Antiquarian", "Own something from before 1970", "📜", 1, Rarity.LEGENDARY) { if ((it.oldest ?: 9999) < 1970) 1 else 0 })
        add(Achievement("day_one", "Day One", "Own something from 2024 or later", "🚀", 1, Rarity.RARE) { if ((it.newest ?: 0) >= 2024) 1 else 0 })
    }

    fun unlockedIds(stats: AchievementStats): Set<String> =
        ALL.filter { it.unlocked(stats) }.map { it.id }.toSet()
}
