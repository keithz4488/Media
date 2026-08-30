package com.kzaller.shelf.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * The ids of the shelf exactly as it was on screen when an item was opened: filtered, searched
 * and sorted the way the user had it.
 *
 * The detail page swipes between siblings, and it used to walk the database's own order instead
 * -- so opening a title from an A-Z shelf and swiping landed on whatever happened to be added
 * next, which looked random. Recorded at tap time rather than derived, because the shelf's
 * filters and sort live in a ViewModel the detail page has no handle on.
 */
val LocalShelfOrder = compositionLocalOf { mutableStateOf<List<String>>(emptyList()) }
