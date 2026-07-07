package com.kzaller.shelf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kzaller.shelf.data.models.SearchHit
import com.kzaller.shelf.ui.theme.MediaShelfTheme

private val gold = Color(0xFFE5C07B)
private val panel = Color(0xFF2A1B0E)

private fun wall() = Brush.verticalGradient(
    colors = listOf(Color(0xFF6E4E2E), Color(0xFF4A331C), Color(0xFF2E1F11)),
)

@Composable
fun ImportScreen(vm: ImportViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val state by vm.state.collectAsState()
    val savedUrl by vm.savedUrl.collectAsState()
    val savedToken by vm.savedToken.collectAsState()

    MediaShelfTheme(dark = true) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Import from Plex") },
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
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(wall()),
            ) {
                when (val s = state) {
                    is ImportState.Idle ->
                        ConnectForm(savedUrl, savedToken, onScan = vm::connectAndScan)
                    is ImportState.Scanning ->
                        Busy("Scanning your Plex library…")
                    is ImportState.Matching ->
                        Progress("Matching titles", s.done, s.total)
                    is ImportState.Importing ->
                        Progress("Adding to your shelves", s.done, s.total)
                    is ImportState.Review ->
                        ReviewList(
                            state = s,
                            onChoose = vm::chooseCandidate,
                            onSkip = vm::skipCandidate,
                            onImport = vm::finishReviewAndImport,
                        )
                    is ImportState.Done ->
                        DoneSummary(s.imported, s.skipped, onDone)
                    is ImportState.Error ->
                        ErrorView(s.message, onRetry = onBack)
                }
            }
        }
    }
}

@Composable
private fun ConnectForm(
    initialUrl: String,
    initialToken: String,
    onScan: (String, String) -> Unit,
) {
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var token by rememberSaveable(initialToken) { mutableStateOf(initialToken) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Connect to your Plex Media Server to bring in every movie and show as digitally owned.",
            color = Color(0xFFE8E8EA),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Server URL") },
            placeholder = { Text("http://192.168.1.10:32400") },
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Plex token (X-Plex-Token)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Find your token: play any item in Plex Web → ⋮ → Get Info → View XML, then copy the X-Plex-Token value from the URL.",
            color = Color(0xFFB9A88F),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { onScan(url.trim(), token.trim()) },
            enabled = url.isNotBlank() && token.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color(0xFF2E1F11)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Scan library", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun fieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = gold,
    unfocusedBorderColor = Color(0xFF7A5C3A),
    focusedLabelColor = gold,
    unfocusedLabelColor = Color(0xFFB9A88F),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = gold,
)

@Composable
private fun Busy(label: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = gold)
        Spacer(Modifier.height(16.dp))
        Text(label, color = Color(0xFFE8E8EA))
    }
}

@Composable
private fun Progress(label: String, done: Int, total: Int) {
    val frac = if (total > 0) done.toFloat() / total else 0f
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = gold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        LinearProgressIndicator(
            progress = { frac },
            color = gold,
            trackColor = Color(0xFF4A331C),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.height(10.dp))
        Text("$done / $total", color = Color(0xFFE8E8EA))
    }
}

@Composable
private fun ReviewList(
    state: ImportState.Review,
    onChoose: (Int, SearchHit) -> Unit,
    onSkip: (Int) -> Unit,
    onImport: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "${state.confirmed} matched automatically. Review the ${state.ambiguous.size} we weren't sure about — pick the right one or skip it.",
            color = Color(0xFFE8E8EA),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(state.ambiguous) { index, match ->
                ReviewCard(
                    index = index,
                    match = match,
                    onChoose = onChoose,
                    onSkip = onSkip,
                )
            }
        }
        val willAdd = state.confirmed + state.ambiguous.count { it.chosen != null }
        Button(
            onClick = onImport,
            colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color(0xFF2E1F11)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text("Import $willAdd items", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReviewCard(
    index: Int,
    match: AmbiguousMatch,
    onChoose: (Int, SearchHit) -> Unit,
    onSkip: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(panel)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    match.plex.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    listOfNotNull(match.plex.year?.toString(), if (match.skipped) "Skipped" else null)
                        .joinToString(" · ").ifBlank { "From Plex" },
                    color = if (match.skipped) Color(0xFFB9756A) else Color(0xFFB9A88F),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { onSkip(index) }) {
                Text("Skip", color = if (match.skipped) gold else Color(0xFFB9A88F))
            }
        }
        if (match.candidates.isEmpty()) {
            Text(
                "No matches found on TMDB.",
                color = Color(0xFFB9A88F),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
            match.candidates.forEachIndexed { ci, hit ->
                CandidateRow(
                    hit = hit,
                    selected = match.chosen == hit && !match.skipped,
                    onClick = { onChoose(index, hit) },
                )
                if (ci < match.candidates.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun CandidateRow(hit: SearchHit, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) gold.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = hit.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(width = 34.dp, height = 50.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A1109)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(hit.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                listOfNotNull(hit.year?.toString(), hit.subtitle).joinToString(" · "),
                color = Color(0xFFB9A88F),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        if (selected) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = gold)
        }
    }
}

@Composable
private fun DoneSummary(imported: Int, skipped: Int, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = gold,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Added $imported items",
            color = gold,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (skipped > 0) {
            Spacer(Modifier.height(6.dp))
            Text("$skipped skipped", color = Color(0xFFB9A88F))
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color(0xFF2E1F11)),
        ) {
            Text("Done", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            color = Color(0xFFF0B7A8),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color(0xFF2E1F11)),
        ) {
            Text("Back", fontWeight = FontWeight.Bold)
        }
    }
}
