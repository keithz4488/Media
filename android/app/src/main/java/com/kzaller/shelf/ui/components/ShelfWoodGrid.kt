package com.kzaller.shelf.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.models.ItemDto
import com.kzaller.shelf.ui.theme.LocalShelfFlavor

private const val COLUMNS = 3
private const val FALL_MS = 950
private const val RISE_MS = 1400

/**
 * Cibby-style shelf: rows of standing covers resting on tinted wooden planks, with quiet
 * titles beneath each cover. Items are chunked into rows of [COLUMNS]; each row draws its
 * own plank so partial last rows still get a shelf to stand on.
 *
 * When a filter removes items, the outgoing covers "fall off the shelf": we hold them in
 * place for one beat (so the surviving covers don't jump yet) while they tip over, drop, and
 * fade, then prune them so the shelf reflows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfWoodGrid(
    items: List<ItemDto>,
    kind: MediaKind,
    selection: Set<String>,
    inSelectionMode: Boolean,
    onItem: (String) -> Unit,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    frozen: Boolean = false,
) {
    // What's actually laid out. Usually equals `items`, but during a fall it keeps the outgoing
    // covers on the shelf (in their old slots) so the survivors stay put until the drop finishes.
    var display by remember { mutableStateOf(items) }
    var falling by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Covers that just returned to the shelf (e.g. a filter cleared) — they settle back into place.
    var rising by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(items, frozen) {
        // While the filter sheet is open the shelf is frozen: we don't touch the layout or start
        // any drop, so the fall only plays once the user commits the filter (hits Done) and the
        // sheet is out of the way to reveal it.
        if (frozen) return@LaunchedEffect
        val newIds = items.mapTo(HashSet()) { it.id }
        val oldIds = display.mapTo(HashSet()) { it.id }
        val outgoing = display.filter { it.id !in newIds }
        val incoming = items.filter { it.id !in oldIds }
        when {
            outgoing.isNotEmpty() -> {
                // Keep the current layout, mark the removed covers as falling, let them drop,
                // then swap to the filtered set so the shelf reflows.
                falling = outgoing.mapTo(HashSet()) { it.id }
                kotlinx.coroutines.delay(FALL_MS.toLong())
                display = items
                falling = emptySet()
            }
            incoming.isNotEmpty() -> {
                // Items came back (filter cleared/loosened): lay them out now and let the new
                // covers settle down onto the shelf instead of snapping in.
                display = items
                rising = incoming.mapTo(HashSet()) { it.id }
                kotlinx.coroutines.delay(RISE_MS.toLong())
                rising = emptySet()
            }
            else -> {
                // Reorder / first load — nothing to animate.
                display = items
                falling = emptySet()
            }
        }
    }

    val rows = remember(display) { display.chunked(COLUMNS) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
    ) {
        items(rows, key = { row -> row.first().let { "${it.kind.wire}:${it.id}" } }) { row ->
            ShelfRow(
                row = row,
                kind = kind,
                selection = selection,
                falling = falling,
                rising = rising,
                inSelectionMode = inSelectionMode,
                onItem = onItem,
                onToggle = onToggle,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfRow(
    row: List<ItemDto>,
    kind: MediaKind,
    selection: Set<String>,
    falling: Set<String>,
    rising: Set<String>,
    inSelectionMode: Boolean,
    onItem: (String) -> Unit,
    onToggle: (String) -> Unit,
) {
    val flavor = LocalShelfFlavor.current
    val coverAspect = when (kind) {
        MediaKind.GAME -> 3f / 4f
        else -> 2f / 3f
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // The cover band: covers sit at the bottom, the plank is drawn right under them.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // plank drawn behind, anchored to the bottom of this row
                .drawBehind {
                    val plankTopThickness = 14.dp.toPx()
                    val plankFront = 20.dp.toPx()
                    val plankTop = size.height - plankTopThickness - plankFront
                    drawPlank(
                        flavor = flavor,
                        topY = plankTop,
                        left = 0f,
                        right = size.width,
                        topThickness = plankTopThickness,
                        frontThickness = plankFront,
                    )
                }
                .padding(bottom = 34.dp), // leave room for the plank under the covers
            verticalAlignment = Alignment.Bottom,
        ) {
            for (col in 0 until COLUMNS) {
                val item = row.getOrNull(col)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    if (item != null) {
                        StandingCover(
                            item = item,
                            aspect = coverAspect,
                            selected = item.id in selection,
                            falling = item.id in falling,
                            rising = item.id in rising,
                            onClick = {
                                if (inSelectionMode) onToggle(item.id) else onItem(item.id)
                            },
                            onLongClick = { onToggle(item.id) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Quiet titles beneath, aligned to each column.
        Row(modifier = Modifier.fillMaxWidth()) {
            for (col in 0 until COLUMNS) {
                val item = row.getOrNull(col)
                Box(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    if (item != null) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = woodTextColor(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (item.year != null) {
                                Text(
                                    text = item.year.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = woodTextColor().copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StandingCover(
    item: ItemDto,
    aspect: Float,
    selected: Boolean,
    falling: Boolean,
    rising: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val flavor = LocalShelfFlavor.current
    val shape = RoundedCornerShape(8.dp)
    val density = LocalDensity.current

    // Fall-off-the-shelf: covers pivot at their base, tip a little, then accelerate off-screen
    // and fade. The tip direction is stable per-item so the same cover always falls the same way.
    val fall by animateFloatAsState(
        targetValue = if (falling) 1f else 0f,
        animationSpec = tween(durationMillis = FALL_MS, easing = EaseIn),
        label = "fall",
    )
    val fallPx = with(density) { 900.dp.toPx() }
    val tipDir = if (item.id.hashCode() and 1 == 0) 1f else -1f

    // Settle-back-onto-the-shelf: a cover that just returned starts a touch above its slot,
    // faded, and drops into place with a small bounce. 1 = above/faded, 0 = home.
    val settle = remember { Animatable(if (rising) 1f else 0f) }
    LaunchedEffect(Unit) {
        // FastOutSlowIn (not a decelerate curve): spreads the travel across the whole duration
        // so the drop stays visibly slow instead of front-loading into a snap.
        if (rising) settle.animateTo(0f, tween(durationMillis = RISE_MS, easing = FastOutSlowInEasing))
    }
    val risePx = with(density) { 120.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .graphicsLayer {
                if (fall > 0f) {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    // Ease into the tip early, then let the drop take over.
                    rotationZ = tipDir * 42f * fall
                    translationY = fallPx * fall * fall
                    alpha = (1f - fall * 1.3f).coerceIn(0f, 1f)
                } else if (settle.value > 0f) {
                    translationY = -risePx * settle.value
                    alpha = (1f - settle.value).coerceIn(0f, 1f)
                }
            }
            // soft drop shadow so the cover looks like it's standing on the plank
            .shadow(elevation = 10.dp, shape = shape, clip = false)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.25f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        if (item.coverUrl != null) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(aspect),
            )
        } else {
            Text(
                text = item.title.take(2).uppercase(),
                style = flavor.titleStyle.copy(color = flavor.accent),
            )
        }
        FormatBadge(
            formatCsv = item.format,
            kind = item.kind,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .background(flavor.accent.copy(alpha = 0.35f)),
            )
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = flavor.accent,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** Readable warm-cream text that sits well on every tinted wood. */
@Composable
private fun woodTextColor(): Color = Color(0xFFF3E7CE)
