package com.tejeet.carddrop.ui

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import android.util.Size
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.tejeet.carddrop.util.CardFrame
import com.tejeet.carddrop.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * ~3 MP captures: plenty for a card that fills the frame, and far quicker to save, crop and read
 * than the full sensor. Preview and capture are both 4:3 so the crop matches what's on screen.
 */
private val CardCaptureResolution = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(
        ResolutionStrategy(Size(2048, 1536), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
    )
    .build()
private val PreviewResolution = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .build()

/** Front is drawn white and back teal, everywhere in the scanner, so the current side is obvious at a glance. */
private val FrontColor = Color.White
private val BackColor = Color(0xFF03D3C0)
private val FrontChipColor = Color(0xFF404B69)
private val BackChipColor = Color(0xFF019E90)

/**
 * In-app card scanner: live camera preview with a card-shaped frame, QR-scanner style.
 * Only the region inside the frame is kept, so OCR isn't distracted by the table, hands or
 * neighbouring cards. Stays open between cards so a stack can be scanned in one go.
 *
 * The two-step flow is made explicit: a Front → Back step indicator, a side label and
 * colour on the frame, a confirmation after each capture, and a thumbnail of the front
 * (with Retake) while the back is being scanned.
 */
@Composable
fun CardScannerScreen(
    scanningBack: Boolean,
    savedCount: Int,
    /** The front just captured for the current card, shown while scanning its back. */
    frontPreviewPath: String?,
    /** Front photo of the most recently saved card, shown between cards. */
    lastSavedPath: String?,
    /** True while the conversation is being recorded; it attaches to the next card saved. */
    recording: Boolean,
    recordingElapsed: String,
    onToggleRecording: () -> Unit,
    onCaptured: (String) -> Unit,
    onSkipBack: () -> Unit,
    onRetakeFront: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var verticalCard by rememberSaveable { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var shutterTick by remember { mutableIntStateOf(0) }
    val flashAlpha = remember { Animatable(0f) }
    var confirmation by remember { mutableStateOf<String?>(null) }

    val sideColor = if (scanningBack) BackColor else FrontColor

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(CardCaptureResolution)
            .build()
    }

    DisposableEffect(lifecycleOwner) {
        var disposed = false
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (disposed) return@addListener
            val provider = providerFuture.get()
            val preview = Preview.Builder().setResolutionSelector(PreviewResolution).build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (e: Exception) {
                error = "Couldn't start the camera: ${e.message ?: "unknown error"}"
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
            camera = null
        }
    }

    // Shutter flash on every capture.
    LaunchedEffect(shutterTick) {
        if (shutterTick > 0) {
            flashAlpha.snapTo(0.75f)
            flashAlpha.animateTo(0f, tween(durationMillis = 350))
        }
    }

    // Say what just happened and what to do next, whenever the step changes.
    LaunchedEffect(scanningBack, savedCount) {
        confirmation = when {
            scanningBack -> "Front captured ✓  Now flip the card over"
            savedCount > 0 -> "Card $savedCount saved ✓  Scan the next card"
            else -> null
        }
        if (confirmation != null) {
            delay(2500)
            confirmation = null
        }
    }

    fun capture() {
        if (capturing) return
        capturing = true
        error = null
        val raw = File(context.cacheDir, "scan_raw_${System.currentTimeMillis()}.jpg")
        val dest = ImageUtils.newImageFile(context, if (scanningBack) "card_back" else "card_front")
        val viewWidth = previewView.width
        val viewHeight = previewView.height
        val frame = CardFrame.forView(viewWidth.toFloat(), viewHeight.toFloat(), verticalCard)

        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(raw).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    shutterTick++
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { ImageUtils.cropToFrame(raw, dest, frame, viewWidth, viewHeight) }
                                .getOrDefault(false)
                                .also { raw.delete() }
                        }
                        capturing = false
                        if (ok) onCaptured(dest.absolutePath) else error = "Couldn't process that photo - try again."
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    raw.delete()
                    capturing = false
                    error = "Capture failed: ${exception.message ?: "unknown error"}"
                }
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val frame = CardFrame.forView(widthPx, heightPx, verticalCard)

        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Dimmed surround with a clear card window; the border colour marks the side. Tap anywhere to focus.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                        camera?.cameraControl?.startFocusAndMetering(
                            FocusMeteringAction.Builder(point).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                        )
                    }
                }
        ) {
            val rect = Rect(frame.left * size.width, frame.top * size.height, frame.right * size.width, frame.bottom * size.height)
            val corner = CornerRadius(16.dp.toPx())
            val surround = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(RoundRect(rect, corner))
            }
            drawPath(surround, Color.Black.copy(alpha = 0.6f))
            drawRoundRect(
                color = sideColor,
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = corner,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Side label sitting just above the frame.
        Surface(
            color = if (scanningBack) BackChipColor else FrontChipColor,
            contentColor = Color.White,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (frame.top * heightPx).roundToInt() - 40.dp.roundToPx()) }
        ) {
            Text(
                if (scanningBack) "BACK SIDE" else "FRONT SIDE",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Shutter flash
        Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha.value)))

        // Top bar: close, step indicator, orientation, flash
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.Filled.Close, contentDescription = "Close scanner", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (scanningBack) "Scan the back" else "Scan the front",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepDot(number = 1, label = "Front", done = scanningBack, active = !scanningBack, color = FrontChipColor)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .width(28.dp)
                            .height(2.dp)
                            .background(if (scanningBack) BackColor else Color.White.copy(alpha = 0.4f))
                    )
                    StepDot(number = 2, label = "Back", done = false, active = scanningBack, color = BackChipColor)
                }
            }
            IconButton(onClick = { verticalCard = !verticalCard }) {
                Icon(
                    if (verticalCard) Icons.Filled.CropPortrait else Icons.Filled.CropLandscape,
                    contentDescription = if (verticalCard) "Switch to horizontal card" else "Switch to vertical card",
                    tint = Color.White
                )
            }
            IconButton(onClick = onToggleRecording) {
                Icon(
                    if (recording) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (recording) "Stop recording the visit" else "Start visit recording",
                    tint = if (recording) Color(0xFFFF6B6B) else Color.White
                )
            }
            IconButton(onClick = {
                torchOn = !torchOn
                camera?.cameraControl?.enableTorch(torchOn)
            }) {
                Icon(
                    if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = if (torchOn) "Turn flash off" else "Turn flash on",
                    tint = Color.White
                )
            }
        }

        // Bottom: confirmation, hint, controls
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = confirmation != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Surface(
                    color = Color(0xFF10B981),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(confirmation.orEmpty(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (recording) {
                Surface(
                    color = Color(0xFFFF4D4D),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            "Recording the visit · $recordingElapsed",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Text(
                if (scanningBack) "Flip the card over and fit the BACK inside the teal frame"
                else "Fit the FRONT of the card inside the white frame · tap to focus",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            error?.let {
                Text(
                    it,
                    color = Color(0xFFFFB4AB),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: what's already captured
                Box(modifier = Modifier.width(96.dp), contentAlignment = Alignment.CenterStart) {
                    when {
                        scanningBack && frontPreviewPath != null -> CapturedThumb(
                            path = frontPreviewPath,
                            caption = "Front ✓",
                            borderColor = FrontColor,
                            actionLabel = "Retake",
                            onAction = onRetakeFront
                        )
                        !scanningBack && lastSavedPath != null && savedCount > 0 -> CapturedThumb(
                            path = lastSavedPath,
                            caption = "$savedCount saved",
                            borderColor = Color(0xFF10B981),
                            actionLabel = null,
                            onAction = {}
                        )
                    }
                }

                // Shutter, ringed in the current side's colour
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(CircleShape)
                        .border(4.dp, sideColor.copy(alpha = 0.8f), CircleShape)
                        .padding(7.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(enabled = !capturing && camera != null) { capture() },
                    contentAlignment = Alignment.Center
                ) {
                    if (capturing) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp, color = FrontChipColor)
                    }
                }

                // Right: skip the back, or finish
                Box(modifier = Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = if (scanningBack) onSkipBack else onDone) {
                        Text(
                            if (scanningBack) "Skip back" else "Done",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepDot(number: Int, label: String, done: Boolean, active: Boolean, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    when {
                        done -> Color(0xFF10B981)
                        active -> color
                        else -> Color.Transparent
                    }
                )
                .border(1.5.dp, if (done || active) Color.Transparent else Color.White.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (done) "✓" else "$number",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            color = if (done || active) Color.White else Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun CapturedThumb(
    path: String,
    caption: String,
    borderColor: Color,
    actionLabel: String?,
    onAction: () -> Unit
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { ImageUtils.decodeUprightSampledBitmap(File(path), 320)?.asImageBitmap() }.getOrNull()
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            bitmap?.let {
                Image(bitmap = it, contentDescription = caption, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Text(caption, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
        if (actionLabel != null) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = BackColor, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(actionLabel, color = BackColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
