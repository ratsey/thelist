package com.ratsey.thelist.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size as CameraSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ratsey.thelist.R
import com.ratsey.thelist.data.ShelfTag
import com.ratsey.thelist.ui.LocalCurrency
import com.ratsey.thelist.ui.formatMoney
import com.ratsey.thelist.data.TagLine
import com.ratsey.thelist.data.priceWithUnit
import com.ratsey.thelist.data.readShelfTag
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The whole screen while a shelf label is being read.
 *
 * The camera looks at the tag itself rather than at the product: the price is printed there in the
 * largest type on the shelf, and reading it needs nothing from the retailer or the network. It stops
 * when two frames agree on the same price, because one frame's misread digit is a wrong price and
 * the second look is nearly free.
 *
 * While it works, the pill at the bottom repeats the tallest line it has read, so a label it cannot
 * price still shows whether the camera is reading the tag at all. The box in the middle is both the
 * aim and the crop: only what is inside it is read.
 *
 * The two decisions are announced separately. The first frame to price the label puts the figure in
 * the pill and offers it as a button; the second frame agreeing closes the screen and lands in the
 * prompt. Nothing is saved either way without a tap on Save there.
 */
@Composable
fun ShelfTagScannerScreen(
    onTag: (ShelfTag) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var seen by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf<ShelfTag?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val viewAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 0.5f
        if (granted) {
            TagCamera(
                onTag = onTag,
                onSeen = { seen = it },
                onRead = { reading = it },
                viewAspect = viewAspect,
            )
            TagFrame()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.scan_permission_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.scan_permission_grant))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.tag_scan_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val read = reading
            val readPrice = read?.price
            val readRate = read?.rate
            if (read != null && readPrice != null) {
                // One frame is enough for a shopper to look at, and waiting for a second is their
                // choice rather than a requirement: the button takes the reading the prompt would
                // have opened with, and Save there is still the only thing that stores it.
                Button(onClick = { onTag(read) }) {
                    Text(
                        stringResource(
                            R.string.tag_scan_use,
                            formatMoney(readPrice, LocalCurrency.current),
                        ),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sell,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (readPrice != null && readRate != null) {
                            // The rate is the half that used to be invisible: the label prints it
                            // and the row keeps it, so say it while the tag is being read.
                            stringResource(
                                R.string.tag_scan_reading_rate,
                                formatMoney(readPrice, LocalCurrency.current),
                                priceWithUnit(
                                    formatMoney(readRate.per, LocalCurrency.current),
                                    readRate.label,
                                ),
                            )
                        } else if (readPrice != null) {
                            stringResource(
                                R.string.tag_scan_reading,
                                formatMoney(readPrice, LocalCurrency.current),
                            )
                        } else {
                            seen?.let { stringResource(R.string.tag_scan_seeing, it) }
                                ?: stringResource(R.string.tag_scan_hint)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TagCamera(
    onTag: (ShelfTag) -> Unit,
    onSeen: (String?) -> Unit,
    onRead: (ShelfTag) -> Unit,
    viewAspect: Float,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )

    // The screen recomposes whenever the prompt behind it changes, and those lambdas are new objects
    // each time; keying the effect on them tore the camera down mid-scan and threw away every frame
    // read so far, so no two reads ever agreed. Read them through, and key on the lifecycle alone.
    val latestOnTag = rememberUpdatedState(onTag)
    val latestOnSeen = rememberUpdatedState(onSeen)
    val latestOnRead = rememberUpdatedState(onRead)
    // A fold or a rotation can resize the preview without recreating the activity.
    val latestAspect = rememberUpdatedState(viewAspect)

    DisposableEffect(lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var recognizer: TextRecognizer? = null
        // Frame decoding and cropping is too much for the thread drawing the preview.
        val analysisExecutor = Executors.newSingleThreadExecutor()

        // Frame state is touched from the analyzer thread and from the listeners' main thread.
        val done = AtomicBoolean(false)
        val busy = AtomicBoolean(false)
        // Every price read so far. The second frame that agrees need not be its neighbour: a frame
        // that reads nothing is the camera moving, not a disagreement about the digits.
        val agreed = ConcurrentHashMap<Double, ShelfTag>()

        providerFuture.addListener({
            val bound = providerFuture.get()
            provider = bound
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            // A shelf label is small print read at arm's length, and the default analysis frame is
            // 640x480: the cents end up a few pixels tall, below what the model can recognise.
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                CameraSize(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        )
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer = reader

            analysis.setAnalyzer(analysisExecutor) { frame ->
                val media = frame.image
                if (done.get() || media == null || !busy.compareAndSet(false, true)) {
                    frame.close()
                    return@setAnalyzer
                }
                // Only the framed part of the frame is read: fitting the label to the box puts its
                // print in front of the model instead of a metre of shelf either side of it.
                val framed = runCatching { framedBitmap(frame, latestAspect.value) }.getOrNull()
                val input = framed?.let { InputImage.fromBitmap(it, 0) }
                    ?: InputImage.fromMediaImage(media, frame.imageInfo.rotationDegrees)
                reader.process(input)
                    .addOnSuccessListener { text ->
                        val lines = text.textBlocks.flatMap { block -> block.lines }
                            .map { line -> TagLine(line.text, line.boundingBox?.height() ?: 0) }
                        lines.maxByOrNull { it.height }?.text?.trim()?.take(32)
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { latestOnSeen.value(it) }
                        val tag = readShelfTag(lines)
                        val price = tag?.price ?: return@addOnSuccessListener
                        if (agreed.putIfAbsent(price, tag) != null) {
                            done.set(true)
                            latestOnTag.value(tag)
                        } else {
                            latestOnRead.value(tag)
                        }
                    }
                    .addOnCompleteListener {
                        framed?.recycle()
                        busy.set(false)
                        frame.close()
                    }
            }

            bound.unbindAll()
            bound.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }, executor)

        onDispose {
            analysisExecutor.shutdown()
            recognizer?.close()
            provider?.unbindAll()
        }
    }
}

/** The box the label is fitted to: 88% of the width, 22% of the height, centred. */
private const val FRAME_WIDTH = 0.88f
private const val FRAME_HEIGHT = 0.22f

/**
 * The aim: a lit box with the shelf around it dimmed, so the label is held where it is read.
 *
 * It is drawn at fractions of the preview rather than measured pixels, because the reader maps the
 * same fractions onto the camera frame — the box is the crop, not decoration.
 */
@Composable
private fun TagFrame() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val left = size.width * (1f - FRAME_WIDTH) / 2f
        val top = size.height * (1f - FRAME_HEIGHT) / 2f
        val right = size.width - left
        val bottom = size.height - top
        val scrim = Color.Black.copy(alpha = 0.45f)
        drawRect(scrim, size = Size(left, size.height))
        drawRect(scrim, topLeft = Offset(right, 0f), size = Size(size.width - right, size.height))
        drawRect(scrim, topLeft = Offset(left, 0f), size = Size(right - left, top))
        drawRect(scrim, topLeft = Offset(left, bottom), size = Size(right - left, size.height - bottom))
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

/**
 * The framed part of the camera frame, turned upright.
 *
 * The preview fills its view and crops whatever does not fit, so the box drawn on screen lands on a
 * smaller rectangle inside the sensor image; this is that mapping. Null when the frame cannot be
 * decoded, and the caller then reads the whole frame instead.
 */
private fun framedBitmap(frame: ImageProxy, viewAspect: Float): Bitmap? {
    if (viewAspect <= 0f) return null
    val source = frame.toBitmap()
    val rotation = frame.imageInfo.rotationDegrees
    val upright = if (rotation == 0) {
        source
    } else {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            .also { if (it !== source) source.recycle() }
    }
    val width = upright.width
    val height = upright.height
    val imageAspect = width.toFloat() / height
    // Fill-centre: the preview shows the largest centred part of the image with the view's shape.
    val visibleWidth = minOf(1f, viewAspect / imageAspect)
    val visibleHeight = minOf(1f, imageAspect / viewAspect)
    val left = ((1f - visibleWidth) + (1f - FRAME_WIDTH) * visibleWidth) / 2f * width
    val top = ((1f - visibleHeight) + (1f - FRAME_HEIGHT) * visibleHeight) / 2f * height
    val x = left.toInt().coerceIn(0, width - 1)
    val y = top.toInt().coerceIn(0, height - 1)
    val w = (FRAME_WIDTH * visibleWidth * width).toInt().coerceAtMost(width - x)
    val h = (FRAME_HEIGHT * visibleHeight * height).toInt().coerceAtMost(height - y)
    if (w < 16 || h < 16) return upright
    return Bitmap.createBitmap(upright, x, y, w, h).also { if (it !== upright) upright.recycle() }
}
