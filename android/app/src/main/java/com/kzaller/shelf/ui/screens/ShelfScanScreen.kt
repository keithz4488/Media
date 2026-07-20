package com.kzaller.shelf.ui.screens

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.kzaller.shelf.ui.theme.MediaShelfTheme
import java.util.concurrent.Executors

private val gold = Color(0xFFE5C07B)
private val panel = Color(0xFF2A1B0E)

/**
 * "Scan a shelf": capture one photo of a row of covers/spines, identify every item with vision,
 * match each to the catalog, then confirm and bulk-add.
 */
@Composable
fun ShelfScanScreen(vm: ShelfScanViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val state by vm.state.collectAsState()

    MediaShelfTheme(dark = true) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF160D06))) {
            when (val s = state) {
                is ScanState.Camera -> ShelfCameraCapture(onCapture = vm::scan, onBack = onBack)
                is ScanState.Identifying -> Busy("Reading the shelf…")
                is ScanState.Matching -> Busy("Matching titles… ${s.done}/${s.total}")
                is ScanState.Importing -> Busy("Adding to your shelves…")
                is ScanState.Review -> ReviewList(s.items, onToggle = vm::toggle, onAdd = vm::confirmAndAdd, onRescan = vm::rescan)
                is ScanState.Done -> DoneView(s.added, onDone)
                is ScanState.Error -> ErrorView(s.message, onRescan = vm::rescan, onBack = onBack)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ShelfCameraCapture(onCapture: (ByteArray) -> Unit, onBack: () -> Unit) {
    val perm = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) { if (!perm.status.isGranted) perm.launchPermissionRequest() }

    if (!perm.status.isGranted) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Camera permission is required to scan a shelf.", color = Color.White)
            Button(onClick = { perm.launchPermissionRequest() }, modifier = Modifier.padding(top = 16.dp)) {
                Text("Grant permission")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("Cancel") }
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    var capturing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            captureExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.ui.viewinterop.AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
                .height(230.dp)
                .border(2.dp, gold, RoundedCornerShape(12.dp)),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Line up a row of covers or spines in the frame, then capture.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) { Text("Cancel") }
                Button(
                    onClick = {
                        if (capturing) return@Button
                        capturing = true
                        imageCapture.takePicture(
                            captureExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bytes = jpegBytes(image)
                                    image.close()
                                    if (bytes.isNotEmpty()) onCapture(bytes)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    capturing = false
                                }
                            },
                        )
                    },
                    enabled = !capturing,
                    colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color(0xFF160D06)),
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (capturing) "Capturing…" else "Capture shelf", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReviewList(
    items: List<ScannedItem>,
    onToggle: (Int) -> Unit,
    onAdd: () -> Unit,
    onRescan: () -> Unit,
) {
    val matched = items.count { it.match != null }
    val willAdd = items.count { it.include && it.match != null }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Found $matched of ${items.size} items. Tap to include or exclude, then add.",
            color = Color(0xFFE8E8EA),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(items) { index, item -> ScanRow(item, onClick = { onToggle(index) }) }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onRescan, modifier = Modifier.weight(1f)) { Text("Rescan", color = gold) }
            Button(
                onClick = onAdd,
                enabled = willAdd > 0,
                colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color(0xFF160D06)),
                modifier = Modifier.weight(2f),
            ) {
                Text(if (willAdd > 0) "Add $willAdd items" else "Nothing to add", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScanRow(item: ScannedItem, onClick: () -> Unit) {
    val enabled = item.match != null
    val active = enabled && item.include
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) gold.copy(alpha = 0.14f) else panel)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.match?.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(width = 40.dp, height = 58.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A1109)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.match?.title ?: item.readTitle,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            val sub = if (item.match != null) {
                listOfNotNull(item.kind.label.trimEnd('s'), item.match.year?.toString()).joinToString(" · ")
            } else {
                "No match for \"${item.readTitle}\""
            }
            Text(
                sub,
                color = if (item.match != null) Color(0xFFB9A88F) else Color(0xFFB9756A),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        if (enabled) {
            Icon(
                if (active) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (active) "Included" else "Excluded",
                tint = if (active) gold else Color(0xFF7A6B54),
            )
        }
    }
}

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
private fun DoneView(added: Int, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = gold, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Added $added items", color = gold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color(0xFF160D06)),
        ) { Text("Done", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ErrorView(message: String, onRescan: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = Color(0xFFF0B7A8), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back", color = gold) }
            Button(
                onClick = onRescan,
                colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color(0xFF160D06)),
            ) { Text("Try again", fontWeight = FontWeight.Bold) }
        }
    }
}

/** ImageCapture is JPEG by default, so the first plane already holds the encoded bytes. */
private fun jpegBytes(image: ImageProxy): ByteArray {
    val plane = image.planes.firstOrNull() ?: return ByteArray(0)
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return bytes
}
