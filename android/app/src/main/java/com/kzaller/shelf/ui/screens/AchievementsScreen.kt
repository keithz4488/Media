package com.kzaller.shelf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kzaller.shelf.ui.components.rarityColor
import com.kzaller.shelf.ui.components.rarityLabel
import com.kzaller.shelf.ui.theme.MediaShelfTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    vm: AchievementsViewModel,
    onBack: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val unlockedCount = ui.count { it.unlocked }

    MediaShelfTheme(dark = true) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Achievements") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0A0A0F), Color(0xFF13131C), Color(0xFF1A1424)),
                        ),
                    ),
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            text = "$unlockedCount / ${ui.size} unlocked",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFFE5C07B),
                                fontWeight = FontWeight.Bold,
                            ),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    items(ui, key = { it.achievement.id }) { row -> AchievementRow(row) }
                }
            }
        }
    }
}

@Composable
private fun AchievementRow(row: AchievementUi) {
    val a = row.achievement
    val color = rarityColor(a.rarity)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (row.unlocked) Modifier.border(1.2.dp, color.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
                else Modifier,
            ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (row.unlocked)
                        Brush.horizontalGradient(
                            0f to Color(0xFF17141F),
                            1f to color.copy(alpha = 0.16f),
                        )
                    else Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = 0.04f),
                        1f to Color.White.copy(alpha = 0.04f),
                    ),
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (row.unlocked) color.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                    .then(if (row.unlocked) Modifier.border(1.dp, color, RoundedCornerShape(12.dp)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = a.emoji,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (row.unlocked) Color.White else Color.White.copy(alpha = 0.30f),
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = a.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (row.unlocked) Color.White else Color.White.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.size(8.dp))
                    // rarity chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(color.copy(alpha = if (row.unlocked) 0.28f else 0.12f))
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = rarityLabel(a.rarity),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (row.unlocked) color else color.copy(alpha = 0.7f),
                        )
                    }
                }
                Text(
                    text = a.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
                if (!row.unlocked && a.target > 1) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { row.current.toFloat() / a.target.toFloat() },
                        color = color,
                        trackColor = Color.White.copy(alpha = 0.10f),
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    Text(
                        text = "${row.current} / ${a.target}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }
            if (row.unlocked) {
                Text("✓", style = MaterialTheme.typography.titleMedium, color = color)
            }
        }
    }
}
