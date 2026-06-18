package com.kzaller.shelf.data

object Status {
    const val OWNED    = "owned"
    const val SEEN     = "seen"
    const val WISHLIST = "wishlist"
    val ALL = listOf(OWNED, SEEN, WISHLIST)

    fun parse(csv: String?): Set<String> =
        csv?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet().orEmpty()

    fun toggle(csv: String?, value: String): String {
        val cur = parse(csv).toMutableSet()
        if (!cur.add(value)) cur.remove(value)
        // Preserve ALL's canonical order so we don't get random reorderings.
        return ALL.filter { it in cur }.joinToString(",")
    }
}
