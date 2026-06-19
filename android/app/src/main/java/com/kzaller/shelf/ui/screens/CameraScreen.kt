package com.kzaller.shelf.ui.screens

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * Live camera with a three-stage identify pipeline:
 *   1. Barcode for ~2.5s (most reliable when an ISBN/UPC is visible)
 *   2. OCR for the next ~2.5s (reads visible text and uses it as a search query)
 *   3. AI identify: captures a still JPEG and sends to the /identify endpoint, which
 *      asks Claude Haiku what cover/poster this is. Result drives a search by title.
 *
 * Each stage's success short-circuits the rest. The user can also force "Identify now"
 * to skip straight to stage 3.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onBarcode: (String) -> Unit,
    onText: (String) -> Unit,
    onIdentify: (ByteArray) -> Unit,
    onClose: () -> Unit,
) {
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

    var stage by remember { mutableStateOf(Stage.BARCODE) }
    val startTime = remember { System.currentTimeMillis() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }

    // When we reach AI stage, take a still picture and hand the JPEG up.
    LaunchedEffect(stage) {
        if (stage == Stage.AI) {
            imageCapture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bytes = jpegBytesFromCapturedImage(image)
                        image.close()
                        if (bytes.isNotEmpty()) onIdentify(bytes)
                        stage = Stage.DONE
                    }

                    override fun onError(exception: ImageCaptureException) {
                        stage = Stage.DONE
                    }
                },
            )
        }
    }

    DisposableEffect(Unit) { onDispose { captureExecutor.shutdown() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(
            imageCapture = imageCapture,
            onFrame = { proxy ->
                if (stage == Stage.DONE) { proxy.close(); return@CameraPreview }
                val elapsed = System.currentTimeMillis() - startTime
                val target = when {
                    elapsed < BARCODE_MS -> Stage.BARCODE
                    elapsed < BARCODE_MS + OCR_MS -> Stage.OCR
                    else -> Stage.AI
                }
                if (target != stage && stage != Stage.AI) stage = target

                when (stage) {
                    Stage.BARCODE -> analyzeBarcode(proxy) { value ->
                        if (stage != Stage.DONE) { stage = Stage.DONE; onBarcode(value) }
                    }
                    Stage.OCR -> analyzeText(proxy) { value ->
                        if (stage != Stage.DONE) { stage = Stage.DONE; onText(value) }
                    }
                    Stage.AI, Stage.DONE -> proxy.close()
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
            Text(stage.hint, color = Color.White)
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onClose) { Text("Cancel") }
                Button(
                    onClick = { if (stage != Stage.AI && stage != Stage.DONE) stage = Stage.AI },
                    enabled = stage == Stage.BARCODE || stage == Stage.OCR,
                ) { Text("Identify now") }
            }
        }
    }
}

private enum class Stage(val hint: String) {
    BARCODE("Looking for a barcode…"),
    OCR("Reading text…"),
    AI("Identifying with AI…"),
    DONE(""),
}

private const val BARCODE_MS = 2500L
private const val OCR_MS = 2500L

@Composable
private fun CameraPreview(
    imageCapture: ImageCapture,
    onFrame: (ImageProxy) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analyzerExecutor) { proxy -> onFrame(proxy) }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
                imageCapture,
            )
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
            analyzerExecutor.shutdown()
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
            val biggest = result.textBlocks.maxByOrNull {
                it.boundingBox?.let { b -> b.width() * b.height() } ?: 0
            }?.text?.trim().orEmpty()
            if (biggest.length >= 3) onMatch(biggest)
        }
        .addOnCompleteListener { proxy.close() }
}

/** Extracts a JPEG ByteArray from an ImageProxy produced by ImageCapture (which is
 *  configured for JPEG by default, so the first plane already holds the encoded bytes). */
private fun jpegBytesFromCapturedImage(image: ImageProxy): ByteArray {
    val plane = image.planes.firstOrNull() ?: return ByteArray(0)
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return bytes
}
