package com.kzaller.shelf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kzaller.shelf.ui.theme.MediaShelfTheme

private val gold = Color(0xFFE5C07B)

@Composable
fun SignInScreen(vm: AuthViewModel) {
    val state by vm.state.collectAsState()
    val error by vm.error.collectAsState()
    val context = LocalContext.current
    val busy = state is AuthState.Loading

    MediaShelfTheme(dark = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF2B1B0E), Color(0xFF160D06))))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.AutoStories, contentDescription = null, tint = gold, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                "Media Shelf",
                style = MaterialTheme.typography.headlineMedium,
                color = gold,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Sign in to sync your books, movies, shows and games to your account.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD8CBB4),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { if (!busy) vm.signIn(context) },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color(0xFF160D06)),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Color(0xFF160D06), strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Signing in…", fontWeight = FontWeight.Bold)
                } else {
                    Text("Sign in with Google", fontWeight = FontWeight.Bold)
                }
            }
            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(error!!, color = Color(0xFFF0B7A8), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
}
