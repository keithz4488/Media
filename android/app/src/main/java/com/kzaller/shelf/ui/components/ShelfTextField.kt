package com.kzaller.shelf.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import com.kzaller.shelf.ui.theme.LocalShelfFlavor

/** TextField color set that stays readable on top of every shelf flavor's background. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun shelfTextFieldColors(): TextFieldColors {
    val flavor = LocalShelfFlavor.current
    val onBg = MaterialTheme.colorScheme.onBackground
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = onBg,
        unfocusedTextColor = onBg,
        disabledTextColor = onBg.copy(alpha = 0.6f),
        cursorColor = flavor.accent,
        focusedBorderColor = flavor.accent,
        unfocusedBorderColor = onBg.copy(alpha = 0.4f),
        focusedLabelColor = flavor.accent,
        unfocusedLabelColor = onBg.copy(alpha = 0.7f),
        focusedPlaceholderColor = onBg.copy(alpha = 0.5f),
        unfocusedPlaceholderColor = onBg.copy(alpha = 0.5f),
        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
    )
}
