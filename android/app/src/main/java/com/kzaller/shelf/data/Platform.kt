package com.kzaller.shelf.data

/**
 * Per-game user-selected platform. Stored as a CSV (e.g. "pc,playstation") in the
 * dedicated `user_platform` column so a game can be on more than one console.
 */
object Platform {
    const val PC          = "pc"
    const val XBOX        = "xbox"
    const val PLAYSTATION = "playstation"
    const val NINTENDO    = "nintendo"
    const val MOBILE      = "mobile"

    val ALL: List<String> = listOf(PC, XBOX, PLAYSTATION, NINTENDO, MOBILE)

    fun label(code: String): String = when (code) {
        PC          -> "PC"
        XBOX        -> "Xbox"
        PLAYSTATION -> "PlayStation"
        NINTENDO    -> "Nintendo"
        MOBILE      -> "Mobile"
        else        -> code.replaceFirstChar { it.uppercase() }
    }

    fun parse(csv: String?): Set<String> =
        csv?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet().orEmpty()

    fun toggle(csv: String?, value: String): String {
        val cur = parse(csv).toMutableSet()
        if (!cur.add(value)) cur.remove(value)
        return ALL.filter { it in cur }.joinToString(",")
    }
}
