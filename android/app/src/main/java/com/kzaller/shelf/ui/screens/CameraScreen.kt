package com.kzaller.shelf.ui.screens

import android.Manifest
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/**
 * Live camera preview. For the first ~2.5 seconds we look for a barcode
 * (book ISBN / game UPC). If we don't find one, switch to OCR and run text
 * recognition on each frame; first non-trivial text triggers `onText`.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onBarcode: (String) -> Unit,
    onText: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val perm = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) { if (!perm.status.isGranted) perm.launchPermissionRequest() }

    if (!perm.status.isGranted) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Camera permission is required to scan covers and barcodes.", color = Color.White)
            Button(onClick = { perm.launchPermissionRequest() }, modifier = Modifier.padding(top = 16.dp)) {
                Text("Grant permission")
            }
            Button(onClick = onClose, modifier = Modifier.padding(top = 8.dp)) {
                Text("Cancel")
            }
        }
        return
    }

    var hint by remember { mutableStateOf("Looking for a barcode…") }
    var didFire by remember { mutableStateOf(false) }
    val startTime = remember { System.currentTimeMillis() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(
            onFrame = { proxy ->
                if (didFire) { proxy.close(); return@CameraPreview }
                val barcodeMode = (System.currentTimeMillis() - startTime) < 2500
                hint = if (barcodeMode) "Looking for a barcode…" else "Reading text…"
                if (barcodeMode) analyzeBarcode(proxy) { value ->
                    if (!didFire) { didFire = true; onBarcode(value) }
                } else analyzeText(proxy) { value ->
                    if (!didFire) { didFire = true; onText(value) }
                }
            },
        )
        // viewfinder
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 280.dp, height = 380.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(hint, color = Color.White)
            Button(onClick = onClose, modifier = Modifier.padding(top = 12.dp)) { Text("Cancel") }
        }
    }
}

@Composable
private fun CameraPreview(onFrame: (ImageProxy) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy -> onFrame(proxy) }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private val barcodeScanner by lazy {
    BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
            )
            .build(),
    )
}

private val textRecognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
}

private fun analyzeBarcode(proxy: ImageProxy, onMatch: (String) -> Unit) {
    val media = proxy.image
    if (media == null) { proxy.close(); return }
    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    barcodeScanner.process(image)
        .addOnSuccessListener { codes ->
            codes.firstOrNull()?.rawValue?.let(onMatch)
        }
        .addOnCompleteListener { proxy.close() }
}

private fun analyzeText(proxy: ImageProxy, onMatch: (String) -> Unit) {
    val media = proxy.image
    if (media == null) { proxy.close(); return }
    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    textRecognizer.process(image)
        .addOnSuccessListener { result ->
            // Pick the largest text block as a heuristic for the title.
            val biggest = result.textBlocks.maxByOrNull {
                it.boundingBox?.let { b -> b.width() * b.height() } ?: 0
            }?.text?.trim().orEmpty()
            if (biggest.length >= 3) onMatch(biggest)
        }
        .addOnCompleteListener { proxy.close() }
}
