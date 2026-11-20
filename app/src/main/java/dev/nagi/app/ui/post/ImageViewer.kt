package dev.nagi.app.ui.post

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.Coil
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.theme.NagiThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val MAX_IMAGE_DOWNLOAD_BYTES = 30 * 1024 * 1024

internal data class ImageTransform(val scale: Float = 1f, val offset: Offset = Offset.Zero) {
    fun bounded(viewport: Size, image: Size): ImageTransform {
        if (viewport.isEmpty() || image.isEmpty()) return ImageTransform()
        val fit = minOf(viewport.width / image.width, viewport.height / image.height)
        val x = ((image.width * fit * scale - viewport.width) / 2).coerceAtLeast(0f)
        val y = ((image.height * fit * scale - viewport.height) / 2).coerceAtLeast(0f)
        return copy(offset = Offset(if (x == 0f) 0f else offset.x.coerceIn(-x, x), if (y == 0f) 0f else offset.y.coerceIn(-y, y)))
    }

    fun zoom(centroid: Offset, pan: Offset, factor: Float, viewport: Size, image: Size): ImageTransform {
        val next = (scale * factor).coerceIn(1f, 5f)
        val anchor = centroid - Offset(viewport.width / 2, viewport.height / 2)
        return ImageTransform(next, anchor - (anchor - offset) * (next / scale) + pan)
            .bounded(viewport, image)
    }
}

internal data class DecodedImageFormat(val mimeType: String, val extension: String)

internal fun detectImageFormat(bytes: ByteArray): DecodedImageFormat {
    if (bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
    ) return DecodedImageFormat("image/png", "png")

    if (bytes.size >= 6 &&
        bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()
    ) return DecodedImageFormat("image/gif", "gif")

    if (bytes.size >= 12 &&
        bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
        bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
        bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
    ) return DecodedImageFormat("image/webp", "webp")

    return DecodedImageFormat("image/jpeg", "jpg")
}

@OptIn(coil.annotation.ExperimentalCoilApi::class)
private suspend fun loadImageBytes(context: Context, url: String): ByteArray? = withContext(Dispatchers.IO) {
    Coil.imageLoader(context).diskCache?.openSnapshot(url)?.use { snapshot ->
        snapshot.data.toFile().takeIf { it.isFile && it.length() <= MAX_IMAGE_DOWNLOAD_BYTES }?.readBytes()
    }?.let { return@withContext it }

    val parsed = runCatching { URL(url) }.getOrNull() ?: return@withContext null
    if (!parsed.protocol.equals("https", ignoreCase = true) && !parsed.protocol.equals("http", ignoreCase = true)) {
        return@withContext null
    }
    var connection: HttpURLConnection? = null
    try {
        connection = (parsed.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            allowUserInteraction = false
            connectTimeout = 10_000
            readTimeout = 20_000
        }
        if (connection.responseCode !in 200..299) return@withContext null
        val contentLength = connection.contentLengthLong
        if (contentLength > MAX_IMAGE_DOWNLOAD_BYTES) return@withContext null
        connection.inputStream.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                total += read
                if (total > MAX_IMAGE_DOWNLOAD_BYTES) return@withContext null
                out.write(buf, 0, read)
            }
            out.toByteArray().takeIf { it.isNotEmpty() }
        }
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

private suspend fun saveImageToGallery(context: Context, bytes: ByteArray, format: DecodedImageFormat): Boolean =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "nagi_${System.currentTimeMillis()}.${format.extension}")
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Nagi")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        try {
            val stream = resolver.openOutputStream(uri) ?: run {
                resolver.delete(uri, null, null)
                return@withContext false
            }
            stream.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

internal suspend fun prepareShareImageUri(
    context: Context,
    fileName: String,
    write: (File) -> Unit,
): Uri = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "shared-images").apply { mkdirs() }
    dir.listFiles()?.forEach { old -> old.delete() }
    val file = File(dir, fileName)
    try {
        write(file)
    } catch (error: Throwable) {
        file.delete()
        throw error
    }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

internal fun launchShareImageIntent(
    context: Context,
    uri: Uri,
    mimeType: String,
    chooserTitle: String,
) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }.onFailure {
        Toast.makeText(context, "无法打开分享菜单", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun prepareShareImageUri(context: Context, url: String, bytes: ByteArray, format: DecodedImageFormat) =
    prepareShareImageUri(context, "nagi_${url.hashCode().toUInt()}.${format.extension}") { file ->
        file.writeBytes(bytes)
    }

private suspend fun PointerInputScope.detectImageZoomAndPan(
    allowSingleFingerPan: () -> Boolean,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val pressedCount = event.changes.count { it.pressed }
                if (pressedCount >= 2 || allowSingleFingerPan()) {
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        onTransform(centroid, panChange, zoomChange)
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
internal fun ImageViewer(url: String, close: () -> Unit) {
    ImageViewer(urls = listOf(url), initialIndex = 0, close = close)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImageViewer(
    urls: List<String>,
    initialIndex: Int = 0,
    close: () -> Unit,
) {
    if (urls.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val safeInitial = initialIndex.coerceIn(0, urls.lastIndex)
    val pagerState = key(urls, safeInitial) {
        rememberPagerState(initialPage = safeInitial) { urls.size }
    }
    val zoomStates = remember(urls) { mutableStateMapOf<Int, Boolean>() }
    val readyStates = remember(urls) { mutableStateMapOf<Int, Boolean>() }
    val resetSignals = remember(urls) { mutableStateMapOf<Int, Int>() }
    var busyAction by remember { mutableStateOf(false) }
    var pendingSaveBytes by remember { mutableStateOf<ByteArray?>(null) }
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/*"),
    ) { targetUri ->
        val bytes = pendingSaveBytes
        pendingSaveBytes = null
        if (targetUri == null || bytes == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(targetUri)?.use { it.write(bytes) } != null
                }.getOrDefault(false)
            }
            Toast.makeText(context, if (ok) "图片已保存" else "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    val currentPage = pagerState.currentPage.coerceIn(0, urls.lastIndex)
    val currentUrl = urls[currentPage]
    val currentReady = readyStates[currentPage] == true
    val currentZoomed = zoomStates[currentPage] == true

    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (urls.size > 1) {
                    Text(
                        text = "${currentPage + 1} / ${urls.size}",
                        style = typography.capsuleLabel,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                } else {
                    Box(Modifier)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (currentReady) {
                        CapsuleButton(
                            text = "保存",
                            icon = NagiIcons.Download,
                            enabled = !busyAction,
                            onClick = {
                                if (busyAction) return@CapsuleButton
                                busyAction = true
                                scope.launch {
                                    try {
                                        val bytes = loadImageBytes(context, currentUrl)
                                        if (bytes == null) {
                                            Toast.makeText(context, "获取原图失败", Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                        val format = detectImageFormat(bytes)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            val ok = saveImageToGallery(context, bytes, format)
                                            Toast.makeText(context, if (ok) "已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
                                        } else {
                                            pendingSaveBytes = bytes
                                            saveDocumentLauncher.launch("nagi_${System.currentTimeMillis()}.${format.extension}")
                                        }
                                    } finally {
                                        busyAction = false
                                    }
                                }
                            },
                            style = CapsuleStyle.Neutral,
                        )
                        CapsuleButton(
                            text = "分享",
                            icon = NagiIcons.Share,
                            enabled = !busyAction,
                            onClick = {
                                if (busyAction) return@CapsuleButton
                                busyAction = true
                                scope.launch {
                                    try {
                                        val bytes = loadImageBytes(context, currentUrl)
                                        if (bytes == null) {
                                            Toast.makeText(context, "获取原图失败", Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                        val format = detectImageFormat(bytes)
                                        val uri = prepareShareImageUri(context, currentUrl, bytes, format)
                                        launchShareImageIntent(context, uri, format.mimeType, "分享图片")
                                    } finally {
                                        busyAction = false
                                    }
                                }
                            },
                            style = CapsuleStyle.Neutral,
                        )
                        CapsuleButton(
                            text = "还原",
                            icon = NagiIcons.Refresh,
                            onClick = { resetSignals[currentPage] = (resetSignals[currentPage] ?: 0) + 1 },
                            style = CapsuleStyle.Neutral,
                        )
                    }
                    ChromeIconButton(
                        icon = NagiIcons.Close,
                        description = "关闭",
                        onClick = close,
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = urls.size > 1 && !currentZoomed,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { pageIndex ->
                val url = urls[pageIndex]
                ImageViewerPage(
                    url = url,
                    isActivePage = pageIndex == currentPage,
                    resetToken = resetSignals[pageIndex] ?: 0,
                    onReadyChanged = { readyStates[pageIndex] = it },
                    onZoomedChanged = { zoomStates[pageIndex] = it },
                )
            }
        }
    }
}

@Composable
private fun ImageViewerPage(
    url: String,
    isActivePage: Boolean,
    resetToken: Int,
    onReadyChanged: (Boolean) -> Unit,
    onZoomedChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val candidates = remember(url) { imageFallbackCandidates(url) }
    var candidateIndex by remember(url) { mutableIntStateOf(0) }
    val activeUrl = candidates.getOrElse(candidateIndex) { url }
    // Bound decoded dimensions rather than decoding arbitrarily large originals.
    var attempt by remember(url) { mutableIntStateOf(0) }
    val request = remember(activeUrl, context, attempt) {
        ImageRequest.Builder(context)
            .data(activeUrl)
            .size(4096, 4096)
            .setParameter("viewer-attempt", attempt)
            .build()
    }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var imageSize by remember(url) { mutableStateOf(Size.Zero) }
    var transform by remember(url, resetToken) { mutableStateOf(ImageTransform()) }
    var loadState by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val current by remember(url, resetToken) { derivedStateOf { transform.bounded(viewport, imageSize) } }
    val center = Offset(viewport.width / 2, viewport.height / 2)
    val ready = loadState is AsyncImagePainter.State.Success
    val zoomed = current.scale > 1.01f

    androidx.compose.runtime.LaunchedEffect(ready) { onReadyChanged(ready) }
    androidx.compose.runtime.LaunchedEffect(zoomed) { onZoomedChanged(zoomed) }

    Box(
        Modifier.fillMaxSize()
            .clipToBounds()
            .then(if (isActivePage) Modifier.testTag("image-viewport") else Modifier)
            .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
            .semantics {
                stateDescription = "缩放 ${(current.scale * 100).toInt()}%"
                if (ready) customActions = listOf(
                    CustomAccessibilityAction("放大图片") {
                        transform = current.zoom(center, Offset.Zero, 1.5f, viewport, imageSize); true
                    },
                    CustomAccessibilityAction("缩小图片") {
                        transform = current.zoom(center, Offset.Zero, 1 / 1.5f, viewport, imageSize); true
                    },
                    CustomAccessibilityAction("还原图片") { transform = ImageTransform(); true },
                )
            }
            .pointerInput(url, ready, resetToken) {
                detectImageZoomAndPan(
                    allowSingleFingerPan = { current.scale > 1.01f },
                    onTransform = { centroid, pan, zoom ->
                        if (ready) transform = current.zoom(centroid, pan, zoom, viewport, imageSize)
                    },
                )
            }
            .pointerInput(url, ready, resetToken) {
                detectTapGestures(onDoubleTap = { point ->
                    if (ready) transform = if (current.scale > 1f) ImageTransform()
                    else current.zoom(point, Offset.Zero, 2.5f, viewport, imageSize)
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = "帖子图片",
            contentScale = ContentScale.Fit,
            onState = { state ->
                if (state is AsyncImagePainter.State.Error &&
                    isHttp404Error(state.result.throwable) &&
                    candidateIndex + 1 < candidates.size
                ) {
                    candidateIndex++
                } else {
                    loadState = state
                    if (state is AsyncImagePainter.State.Success) imageSize = state.painter.intrinsicSize
                }
            },
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = current.scale
                scaleY = current.scale
                translationX = current.offset.x
                translationY = current.offset.y
            },
        )
        when (loadState) {
            is AsyncImagePainter.State.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "图片加载失败",
                    style = NagiThemeTokens.typography.body,
                    color = NagiThemeTokens.colors.textSecondary,
                )
                CapsuleButton(
                    text = "重试",
                    onClick = {
                        candidateIndex = 0
                        attempt++
                    },
                    icon = NagiIcons.Refresh,
                    style = CapsuleStyle.Neutral,
                )
            }
            is AsyncImagePainter.State.Success -> Unit
            else -> CircularProgressIndicator(
                color = NagiThemeTokens.colors.goldAccent,
                strokeWidth = 2.dp,
            )
        }
    }
}
