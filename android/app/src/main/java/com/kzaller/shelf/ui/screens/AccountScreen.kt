package com.kzaller.shelf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kzaller.shelf.ui.theme.MediaShelfTheme

private val gold = Color(0xFFE5C07B)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(email: String, onSignOut: () -> Unit, onBack: () -> Unit) {
    var confirmSignOut by remember { mutableStateOf(false) }

    MediaShelfTheme(dark = true) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Account") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = gold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = gold,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF2B1B0E), Color(0xFF160D06))))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = gold, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(12.dp))
                Text("Signed in as", color = Color(0xFFB9A98C), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    email.ifBlank { "your Google account" },
                    color = Color(0xFFEDE3CE),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(36.dp))
                OutlinedButton(
                    onClick = { confirmSignOut = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = gold)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out", color = gold, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Your shelf is tied to this account. Signing out returns to the sign-in screen; " +
                        "sign back in (or in as someone else) any time.",
                    color = Color(0xFF9C8E76),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("You'll need to sign in again to see your shelf.") },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            },
        )
    }
}
