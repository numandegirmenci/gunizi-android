package com.numandegirmenci.belgeizi.camera

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

/**
 * CameraX photo capture with a higher-resolution request if the phone exposes
 * it to third-party applications. The JPEG is first written unchanged to disk.
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
@Composable
fun UltraCameraScreen(
    activity: ComponentActivity,
    onBack: () -> Unit,
    onPhoto: (uri: Uri, width: Int, height: Int) -> Unit
) {
    val previewView = remember {
        PreviewView(activity).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val available = remember { CameraCapabilities.backCameras(activity) }
    var ultra by remember { mutableStateOf(true) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraId by remember { mutableStateOf<String?>(null) }
    var torch by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(ultra) {
        var disposed = false
        val future = ProcessCameraProvider.getInstance(activity)
        capture = null
        future.addListener({
            if (!disposed) {
                try {
                    val provider = future.get()
                    provider.unbindAll()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .apply {
                            if (ultra) setResolutionSelector(
                                ResolutionSelector.Builder()
                                    .setAllowedResolutionMode(
                                        ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
                                    ).build()
                            )
                        }.build()
                    val bound = provider.bindToLifecycle(
                        activity, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                    )
                    capture = imageCapture
                    camera = bound
                    cameraId = Camera2CameraInfo.from(bound.cameraInfo).cameraId
                    if (torch && bound.cameraInfo.hasFlashUnit()) {
                        bound.cameraControl.enableTorch(true)
                    }
                    error = null
                } catch (ex: Exception) {
                    capture = null
                    camera = null
                    error = ex.message ?: "Kamera açılamadı."
                }
            }
        }, ContextCompat.getMainExecutor(activity))
        onDispose {
            disposed = true
            capture = null
            camera = null
            runCatching { if (future.isDone) future.get().unbindAll() }
        }
    }

    val info = available.firstOrNull { it.id == cameraId }
    val maximum = if (ultra) info?.maximum else info?.normal
    val mp = maximum?.let { CameraCapabilities.pixels(it) / 1_000_000.0 }
    val reported = if (mp == null) "Kamera çözünürlüğü okunuyor"
        else "Kameranın bildirdiği üst sınır: " +
            String.format(Locale.US, "%.1f MP", mp)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xDF08141D))
                    .padding(start = 7.dp, end = 15.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Geri", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text("Belge İzi Kamera", color = Color.White,
                        style = MaterialTheme.typography.titleMedium)
                    Text("Gerçek piksel bilgisiyle çekim",
                        color = Color.LightGray, fontSize = 11.sp)
                }
                IconButton(onClick = {
                    val active = camera
                    if (active != null && active.cameraInfo.hasFlashUnit()) {
                        torch = !torch
                        active.cameraControl.enableTorch(torch)
                    }
                }) {
                    Icon(if (torch) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                        "Flaş", tint = if (torch) Color(0xFF7EE4CA) else Color.White)
                }
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth().background(Color(0xEE07111A))
                    .padding(horizontal = 18.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(reported, color = Color(0xFF81E4C9), fontSize = 12.sp)
                Text("Gerçek çekim çözünürlüğü dosyadan doğrulanır.",
                    color = Color(0xFFB4C3CE), fontSize = 10.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = !ultra,
                        onClick = { if (!busy) ultra = false },
                        label = { Text("Standart") }
                    )
                    FilterChip(
                        selected = ultra,
                        onClick = { if (!busy) ultra = true },
                        label = { Text("Ultra HD") },
                        leadingIcon = {
                            Icon(Icons.Outlined.HighQuality, null,
                                modifier = Modifier.size(16.dp))
                        }
                    )
                }
                Spacer(Modifier.height(14.dp))
                Surface(
                    onClick = {
                        val imageCapture = capture
                        if (imageCapture == null || busy) return@Surface
                        busy = true
                        error = null
                        val file = File(activity.cacheDir,
                            "belgeizi_capture_" + System.currentTimeMillis() + ".jpg")
                        imageCapture.takePicture(
                            ImageCapture.OutputFileOptions.Builder(file).build(),
                            ContextCompat.getMainExecutor(activity),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(
                                    result: ImageCapture.OutputFileResults
                                ) {
                                    busy = false
                                    val bounds = BitmapFactory.Options().apply {
                                        inJustDecodeBounds = true
                                    }
                                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                                    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                                        onPhoto(Uri.fromFile(file), bounds.outWidth, bounds.outHeight)
                                    } else {
                                        file.delete()
                                        error = "Çekilen görüntü okunamadı."
                                    }
                                }

                                override fun onError(exc: ImageCaptureException) {
                                    busy = false
                                    file.delete()
                                    error = exc.message ?: "Fotoğraf çekilemedi."
                                }
                            }
                        )
                    },
                    enabled = capture != null && !busy,
                    shape = CircleShape,
                    border = BorderStroke(5.dp, Color.White),
                    color = Color(0xFF7DE4C8),
                    modifier = Modifier.size(74.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (busy) CircularProgressIndicator(
                            color = Color(0xFF09202A),
                            modifier = Modifier.size(32.dp)
                        ) else Icon(
                            Icons.Outlined.CameraAlt, "Fotoğraf çek",
                            tint = Color(0xFF09202A),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error.orEmpty(), color = Color(0xFFFF9595), fontSize = 12.sp)
                }
            }
        }
    }
}
