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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kzaller.shelf.ui.theme.MediaShelfTheme

private val steamGold = Color(0xFFE5C07B)

private fun steamWall() = Brush.verticalGradient(
    colors = listOf(Color(0xFF1B2838), Color(0xFF16202D), Color(0xFF0E1520)),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SteamImportScreen(vm: SteamImportViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val state by vm.state.collectAsState()
    val savedKey by vm.savedKey.collectAsState()
    val savedId by vm.savedId.collectAsState()

    MediaShelfTheme(dark = true) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Import from Steam") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = steamGold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = steamGold,
                    ),
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(steamWall()),
            ) {
                when (val s = state) {
                    is SteamImportState.Idle ->
                        ConnectForm(savedKey, savedId, onImport = vm::connectAndImport)
                    is SteamImportState.Scanning ->
                        Busy("Fetching your Steam library…")
                    is SteamImportState.Importing ->
                        Busy("Adding ${s.total} games to your shelf…")
                    is SteamImportState.Done ->
                        DoneSummary(s.imported, onDone)
                    is SteamImportState.Error ->
                        ErrorView(s.message, onBack)
                }
            }
        }
    }
}

@Composable
private fun ConnectForm(
    initialKey: String,
    initialId: String,
    onImport: (String, String) -> Unit,
) {
    var key by rememberSaveable(initialKey) { mutableStateOf(initialKey) }
    var id by rememberSaveable(initialId) { mutableStateOf(initialId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Bring in every game you own on Steam as digitally-owned PC games.",
            color = Color(0xFFE8E8EA),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("Steam Web API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = id,
            onValueChange = { id = it },
            label = { Text("SteamID, vanity name, or profile URL") },
            placeholder = { Text("76561198… or your custom URL") },
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Get a free API key at steamcommunity.com/dev/apikey. Your profile's Privacy → " +
                "\"Game details\" must be set to Public for the list to come through.",
            color = Color(0xFFB9C4D0),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { onImport(key.trim(), id.trim()) },
            enabled = key.isNotBlank() && id.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = steamGold, contentColor = Color(0xFF0E1520)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Import library", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = steamGold,
    unfocusedBorderColor = Color(0xFF3A4C60),
    focusedLabelColor = steamGold,
    unfocusedLabelColor = Color(0xFFB9C4D0),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = steamGold,
)

@Composable
private fun Busy(label: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = steamGold)
        Spacer(Modifier.height(16.dp))
        Text(label, color = Color(0xFFE8E8EA))
    }
}

@Composable
private fun DoneSummary(imported: Int, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = steamGold, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Added $imported games",
            color = steamGold,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = steamGold, contentColor = Color(0xFF0E1520)),
        ) {
            Text("Done", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ErrorView(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = Color(0xFFF0B7A8), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = steamGold, contentColor = Color(0xFF0E1520)),
        ) {
            Text("Back", fontWeight = FontWeight.Bold)
        }
    }
}
