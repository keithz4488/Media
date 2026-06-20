package com.kzaller.shelf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.models.ItemDto
import com.kzaller.shelf.ui.components.ItemCard
import com.kzaller.shelf.ui.components.shelfTextFieldColors
import com.kzaller.shelf.ui.theme.MediaShelfTheme
import com.kzaller.shelf.ui.theme.flavorFor

/**
 * Cross-shelf search: one input that matches title/subtitle across every kind. Results are
 * rendered using the per-item kind's flavor so a book card still looks like a book card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAllScreen(
    vm: SearchAllViewModel,
    onBack: () -> Unit,
    onItem: (MediaKind, String) -> Unit,
) {
    MediaShelfTheme(dark = true) {
        val query by vm.query.collectAsState()
        val results by vm.results.collectAsState()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Search all shelves") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
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
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = vm::setQuery,
                        placeholder = { Text("Title, author, director…") },
                        singleLine = true,
                        colors = shelfTextFieldColors(),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { vm.setQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                    when {
                        query.isBlank() -> CenteredText("Type to search every shelf")
                        results.isEmpty() -> CenteredText("No matches")
                        else -> ResultsGrid(results = results, onItem = onItem)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsGrid(
    results: List<ItemDto>,
    onItem: (MediaKind, String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(results, key = { "${it.kind.wire}:${it.id}" }) { item ->
            // Render each result in its own flavor so a book card still looks "warm wood".
            MediaShelfTheme(flavor = flavorFor(item.kind, dark = true), dark = true) {
                Column {
                    Text(
                        text = item.kind.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    ItemCard(
                        item = item,
                        onClick = { onItem(item.kind, item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

