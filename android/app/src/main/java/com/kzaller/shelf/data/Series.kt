package com.kzaller.shelf.data

import com.kzaller.shelf.data.models.ItemDto

/** A cluster of shelf items that look like they belong to the same series/franchise. */
data class SeriesGroup(
    /** Display name of the series, or null for the leftover "standalone" bucket. */
    val name: String?,
    val items: List<ItemDto>,
)

/**
 * Heuristic series/franchise grouping. There's no reliable collection metadata stored, so we
 * normalize titles (drop a leading "The", any subtitle after ":"/"-", and trailing sequel
 * markers like numbers, roman numerals, "Part N") and cluster items that share the result.
 * Only clusters of 2+ become named groups; everything else falls into one "Standalone" bucket.
 */
object Series {
    private val sequelWord = Regex("\\b(part|vol|volume|book|chapter|episode)\\s+[0-9ivxlc]+$")
    private val trailingNumber = Regex("\\s+[0-9]+$")
    private val trailingRoman = Regex("\\s+[ivxlc]+$")

    fun key(title: String): String {
        var t = title.lowercase().trim()
        if (t.startsWith("the ")) t = t.substring(4)
        for (sep in listOf(": ", " - ", " – ", ": ")) {
            val idx = t.indexOf(sep)
            if (idx > 0) t = t.substring(0, idx)
        }
        // A bare colon with no space (rare) still splits.
        t.indexOf(':').let { if (it > 0) t = t.substring(0, it) }
        t = sequelWord.replace(t, "")
        t = trailingNumber.replace(t, "")
        t = trailingRoman.replace(t, "")
        return t.trim().trimEnd(',', '.', ':', '-').trim()
    }

    fun group(items: List<ItemDto>): List<SeriesGroup> {
        val byKey = items.groupBy { key(it.title) }
        val groups = byKey.entries
            .filter { it.value.size >= 2 }
            .sortedBy { it.key }
            .map { (_, list) ->
                // The shortest title in a cluster is usually the base entry -> best display name.
                val name = list.minByOrNull { it.title.length }?.title ?: list.first().title
                SeriesGroup(name, list.sortedBy { it.year ?: 0 })
            }
            .toMutableList()

        val singles = byKey.values.filter { it.size < 2 }.flatten()
        if (singles.isNotEmpty()) {
            groups.add(SeriesGroup(null, singles.sortedBy { it.title.lowercase() }))
        }
        return groups
    }
}
