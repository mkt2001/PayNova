package com.paynova.upi.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.paynova.upi.payment.Draft
import com.paynova.upi.payment.QrType
import com.paynova.upi.payment.UpiParser
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private class QrAnalyzer(private val onResult: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null) {
            imageProxy.close()
            return
        }
        val img = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        scanner.process(img)
            .addOnSuccessListener { list -> list.firstOrNull()?.rawValue?.let(onResult) }
            .addOnCompleteListener { imageProxy.close() }
    }
}

@Composable
fun ScanScreen(nav: NavHostController, vm: PayViewModel) {
    val ctx = LocalContext.current
    val owner = ctx.activity()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var error by remember { mutableStateOf<String?>(null) }
    var torch by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(0f) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val busy = remember { AtomicBoolean(false) }          // duplicate-scan prevention
    val previewView = remember { PreviewView(ctx) }
    val mainExec = remember { ContextCompat.getMainExecutor(ctx) }
    val galleryScanner = remember { BarcodeScanning.getClient() }

    fun process(raw: String) {
        val q = UpiParser.parse(raw)
        if (q.type == QrType.INVALID) {
            error = q.error ?: "Invalid QR code"
            Handler(Looper.getMainLooper()).postDelayed({ busy.set(false) }, 2500)
            return
        }
        error = null
        vm.begin(
            Draft(
                method = if (q.type == QrType.MERCHANT) "MERCHANT" else "QR",
                vpa = q.vpa,
                qrName = q.name,
                qr = q,
                amountPaise = UpiParser.toPaise(q.amount),
                amountLocked = q.amount != null,
                note = q.note.orEmpty()
            )
        )
        nav.navigate("resolve")
        Handler(Looper.getMainLooper()).postDelayed({ busy.set(false) }, 1500)
    }

    val onCode: (String) -> Unit = { raw ->
        if (busy.compareAndSet(false, true)) mainExec.execute { process(raw) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val img = InputImage.fromFilePath(ctx, uri)
                galleryScanner.process(img)
                    .addOnSuccessListener { l ->
                        val raw = l.firstOrNull()?.rawValue
                        if (raw == null) error = "No QR code found in this image" else onCode(raw)
                    }
                    .addOnFailureListener { error = "Could not read this image" }
            } catch (e: Exception) {
                error = "Could not read this image"
            }
        }
    }

    DisposableEffect(granted) {
        var camProvider: ProcessCameraProvider? = null
        val executor = Executors.newSingleThreadExecutor()
        if (granted) {
            val fut = ProcessCameraProvider.getInstance(ctx)
            fut.addListener({
                val p = fut.get()
                camProvider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, QrAnalyzer(onCode))
                p.unbindAll()
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, mainExec)
        }
        onDispose {
            camProvider?.unbindAll()
            executor.shutdown()
        }
    }

    Screen("Scan & Pay", { nav.popBackStack() }, scroll = false) {
        if (!granted) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission is needed to scan QR codes.", fontSize = 15.sp)
                Box(Modifier.padding(16.dp))
                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                Box(Modifier.padding(8.dp))
                Button(onClick = { picker.launch("image/*") }) { Text("Scan QR from gallery instead") }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                val inf = rememberInfiniteTransition(label = "scan")
                val prog by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "line")
                Canvas(Modifier.fillMaxSize()) {
                    val side = size.minDimension * 0.7f
                    val left = (size.width - side) / 2f
                    val top = (size.height - side) / 2f
                    drawRoundRect(Color.White, Offset(left, top), Size(side, side), CornerRadius(32f, 32f), style = Stroke(width = 6f))
                    drawLine(Color(0xFF8C35C9), Offset(left + 16f, top + side * prog), Offset(left + side - 16f, top + side * prog), strokeWidth = 6f)
                }
                error?.let {
                    Box(Modifier.align(Alignment.TopCenter).padding(12.dp)) {
                        Box(Modifier.padding(0.dp)) { Text(it, color = Color.White, fontSize = 14.sp, modifier = Modifier.background(Color(0xCC000000)).padding(10.dp)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    Pill(if (torch) "Flash: On" else "Flash: Off", torch) {
                        torch = !torch
                        camera?.cameraControl?.enableTorch(torch)
                    }
                }
                Pill("Gallery") { picker.launch("image/*") }
            }
            Text("Zoom", fontSize = 12.sp)
            Slider(value = zoom, onValueChange = { zoom = it; camera?.cameraControl?.setLinearZoom(it) })
        }
    }
}
