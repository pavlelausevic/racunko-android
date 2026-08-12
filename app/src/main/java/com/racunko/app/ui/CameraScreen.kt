package com.racunko.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.racunko.app.R
import com.racunko.platform.Engines
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 8d: live IPS-QR scanning. CameraX Preview + ImageAnalysis
 * (STRATEGY_KEEP_ONLY_LATEST) feeds each frame — rotated per
 * `rotationDegrees` so portrait decodes — to the flavor's [LiveQrScanner]. The
 * scanner locks after the same `K:PR` payload on 3 consecutive frames (its own
 * debounce). Successful decode is the sole quality gate; there is no blur score.
 * No full-page photo/OCR (product decision) — the payload alone yields the bill.
 */
@Composable
fun CameraScreen(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
    onFallbackPhoto: () -> Unit
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var denied by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> granted = ok; denied = !ok }

    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        PermissionRationale(
            denied = denied,
            onRequest = { permLauncher.launch(Manifest.permission.CAMERA) },
            onFallback = onFallbackPhoto,
            onCancel = onCancel
        )
        return
    }

    CameraContent(onScanned = onScanned, onCancel = onCancel, onFallbackPhoto = onFallbackPhoto)
}

@Composable
private fun CameraContent(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
    onFallbackPhoto: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    val scanner = remember { Engines.require().newLiveQrScanner() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val lockedFlag = remember { AtomicBoolean(false) }
    var uiLocked by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }
    var showGuidance by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) { delay(8000); if (!uiLocked) showGuidance = true }

    // On lock: haptic buzz, brief „QR uhvaćen ✓", then hand back the payload.
    LaunchedEffect(pending) {
        val p = pending ?: return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(350)
        onScanned(p)
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
            analysisExecutor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { proxy ->
                        try {
                            if (!lockedFlag.get()) {
                                val rotation = proxy.imageInfo.rotationDegrees
                                val bmp = proxy.toBitmap()
                                val frame = if (rotation != 0) rotate(bmp, rotation) else bmp
                                val hit = scanner.onFrame(frame)
                                if (frame != bmp) frame.recycle()
                                bmp.recycle()
                                if (hit != null && lockedFlag.compareAndSet(false, true)) {
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        uiLocked = true
                                        pending = hit
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // a bad frame must never crash the scan loop
                        } finally {
                            proxy.close()
                        }
                    }
                    runCatching {
                        provider.unbindAll()
                        val camera = provider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                        )
                        cameraControl = camera.cameraControl
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // framing box
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.72f)
                .aspectRatio(1f)
                .border(
                    3.dp,
                    if (uiLocked) Palette.Green else Color.White.copy(alpha = 0.85f),
                    RoundedCornerShape(16.dp)
                )
        )

        // top bar: back + torch
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onCancel) {
                Icon(
                    RIcons.ArrowBack, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(stringResource(R.string.back), color = Color.White, fontSize = 15.sp)
            }
            TextButton(onClick = {
                torchOn = !torchOn
                cameraControl?.enableTorch(torchOn)
            }) {
                // the torch state is the tint, not a different glyph
                val tint = if (torchOn) Palette.Amber else Color.White
                Icon(
                    RIcons.Torch, contentDescription = null,
                    tint = tint, modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(stringResource(R.string.scan_torch), color = tint, fontSize = 15.sp)
            }
        }

        // bottom: status / guidance / fallback
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiLocked) {
                Text(
                    stringResource(R.string.scan_captured),
                    color = Palette.Green, fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    stringResource(if (showGuidance) R.string.scan_guidance else R.string.scan_hint),
                    color = Color.White, fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onFallbackPhoto,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Text(stringResource(R.string.scan_use_photo))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PermissionRationale(
    denied: Boolean,
    onRequest: () -> Unit,
    onFallback: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.scan_perm_title),
            color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.scan_perm_text),
            color = Palette.Muted, fontSize = 14.sp, lineHeight = 20.sp
        )
        Spacer(Modifier.height(20.dp))
        if (!denied) {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Blue, contentColor = Palette.Bg)
            ) { Text(stringResource(R.string.scan_perm_allow), fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onFallback) {
            Text(stringResource(R.string.scan_use_photo), color = Palette.Blue)
        }
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.btn_otkazi), color = Palette.Muted)
        }
    }
}

private fun rotate(src: Bitmap, degrees: Int): Bitmap {
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}
