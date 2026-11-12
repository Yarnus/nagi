package dev.nagi.app.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle

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

@Composable
internal fun ImageViewer(url: String, close: () -> Unit) {
    val context = LocalContext.current
    // Bound decoded dimensions rather than decoding arbitrarily large originals.
    var attempt by remember(url) { mutableIntStateOf(0) }
    val request = remember(url, context, attempt) { ImageRequest.Builder(context).data(url).size(4096, 4096).setParameter("viewer-attempt", attempt).build() }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var imageSize by remember(url) { mutableStateOf(Size.Zero) }
    var transform by remember(url) { mutableStateOf(ImageTransform()) }
    var loadState by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val current by remember(url) { derivedStateOf { transform.bounded(viewport, imageSize) } }
    val center = Offset(viewport.width / 2, viewport.height / 2)
    val ready = loadState is AsyncImagePainter.State.Success

    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ready) CapsuleButton("还原", { transform = ImageTransform() }, style = CapsuleStyle.Neutral)
                CapsuleButton("关闭", close, style = CapsuleStyle.Neutral)
            }
            Box(
                Modifier.fillMaxWidth().weight(1f).clipToBounds().testTag("image-viewport")
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
                    .pointerInput(url, ready) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            if (ready) transform = current.zoom(centroid, pan, zoom, viewport, imageSize)
                        }
                    }
                    .pointerInput(url, ready) {
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
                        loadState = state
                        if (state is AsyncImagePainter.State.Success) imageSize = state.painter.intrinsicSize
                    },
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = current.scale
                        scaleY = current.scale
                        translationX = current.offset.x
                        translationY = current.offset.y
                    },
                )
                when (loadState) {
                    is AsyncImagePainter.State.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("图片加载失败")
                        CapsuleButton("重试", { attempt++ }, style = CapsuleStyle.Neutral)
                    }
                    is AsyncImagePainter.State.Success -> Unit
                    else -> CircularProgressIndicator()
                }
            }
        }
    }
}
