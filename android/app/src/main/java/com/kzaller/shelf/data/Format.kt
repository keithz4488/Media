package com.kzaller.shelf.data

/** Whether the user owns an item physically and/or digitally. Stored as a CSV so both apply. */
object Format {
    const val PHYSICAL = "physical"
    const val DIGITAL = "digital"
    val ALL = listOf(PHYSICAL, DIGITAL)

    fun label(code: String): String = when (code) {
        PHYSICAL -> "Physical"
        DIGITAL -> "Digital"
        else -> code.replaceFirstChar { it.uppercase() }
    }

    fun parse(csv: String?): Set<String> =
        csv?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet().orEmpty()

    fun toggle(csv: String?, value: String): String {
        val cur = parse(csv).toMutableSet()
        if (!cur.add(value)) cur.remove(value)
        return ALL.filter { it in cur }.joinToString(",")
    }
}
