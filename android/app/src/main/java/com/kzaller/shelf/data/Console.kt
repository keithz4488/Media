package com.kzaller.shelf.data

/**
 * Specific console codes within each platform "company." Stored separately from
 * user_platform as a CSV in the `consoles` column. Console.forPlatform(platform)
 * returns the valid codes for a given platform; PC and Mobile have no sub-consoles.
 */
object Console {
    // Xbox lineage
    const val XBOX_OG     = "xbox_og"
    const val XBOX_360    = "xbox_360"
    const val XBOX_ONE    = "xbox_one"
    const val XBOX_SERIES = "xbox_series"

    // PlayStation lineage
    const val PS1     = "ps1"
    const val PS2     = "ps2"
    const val PS3     = "ps3"
    const val PS4     = "ps4"
    const val PS5     = "ps5"
    const val PSP     = "psp"
    const val PS_VITA = "ps_vita"

    // Nintendo lineage
    const val NES              = "nes"
    const val SNES             = "snes"
    const val N64              = "n64"
    const val GAMECUBE         = "gamecube"
    const val WII              = "wii"
    const val WIIU             = "wiiu"
    const val SWITCH           = "switch"
    const val SWITCH_2         = "switch_2"
    const val GAMEBOY          = "gameboy"
    const val GAMEBOY_COLOR    = "gameboy_color"
    const val GAMEBOY_ADVANCE  = "gameboy_advance"
    const val DS               = "ds"
    const val THREEDS          = "3ds"

    fun forPlatform(platform: String): List<String> = when (platform) {
        Platform.XBOX -> listOf(XBOX_OG, XBOX_360, XBOX_ONE, XBOX_SERIES)
        Platform.PLAYSTATION -> listOf(PS1, PS2, PS3, PS4, PS5, PSP, PS_VITA)
        Platform.NINTENDO -> listOf(
            NES, SNES, N64, GAMECUBE, WII, WIIU, SWITCH, SWITCH_2,
            GAMEBOY, GAMEBOY_COLOR, GAMEBOY_ADVANCE, DS, THREEDS,
        )
        else -> emptyList() // PC and Mobile have no sub-consoles
    }

    fun label(code: String): String = when (code) {
        XBOX_OG     -> "Xbox OG"
        XBOX_360    -> "Xbox 360"
        XBOX_ONE    -> "Xbox One"
        XBOX_SERIES -> "Xbox Series X/S"

        PS1     -> "PS1"
        PS2     -> "PS2"
        PS3     -> "PS3"
        PS4     -> "PS4"
        PS5     -> "PS5"
        PSP     -> "PSP"
        PS_VITA -> "PS Vita"

        NES              -> "NES"
        SNES             -> "SNES"
        N64              -> "N64"
        GAMECUBE         -> "GameCube"
        WII              -> "Wii"
        WIIU             -> "Wii U"
        SWITCH           -> "Switch"
        SWITCH_2         -> "Switch 2"
        GAMEBOY          -> "Game Boy"
        GAMEBOY_COLOR    -> "Game Boy Color"
        GAMEBOY_ADVANCE  -> "Game Boy Advance"
        DS               -> "DS"
        THREEDS          -> "3DS"
        else             -> code.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun parse(csv: String?): Set<String> =
        csv?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet().orEmpty()

    fun toggle(csv: String?, code: String): String {
        val cur = parse(csv).toMutableSet()
        if (!cur.add(code)) cur.remove(code)
        // Stable order: by platform group, then by enum order within the group.
        val ordered = Platform.ALL.flatMap { forPlatform(it) }
        return ordered.filter { it in cur }.joinToString(",")
    }

    /** Returns the same CSV with any consoles whose parent platform is no longer
     *  selected stripped out. Used after the user removes a platform chip. */
    fun pruneToPlatforms(csv: String?, activePlatforms: Set<String>): String {
        val parsed = parse(csv)
        val allowed = activePlatforms.flatMap { forPlatform(it) }.toSet()
        return parsed.filter { it in allowed }.joinToString(",")
    }
}
