package dev.nagi.app.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Bitmap as AndroidBitmap
import android.graphics.BitmapShader as AndroidBitmapShader
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter as AndroidColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.theme.NagiThemeTokens

internal class BackdropBlurScope(
    val source: GraphicsLayer,
    val hardware: Boolean,
    val rootCoordinates: () -> LayoutCoordinates?,
)

internal val LocalBackdropBlur = compositionLocalOf<BackdropBlurScope?> { null }

@Composable
private fun hardwareBlurAllowed(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var powerSave by remember { mutableStateOf(power.isPowerSaveMode) }
    DisposableEffect(context, power) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { powerSave = power.isPowerSaveMode }
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    val resolver = context.contentResolver
    var animations by remember(resolver) {
        mutableFloatStateOf(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                animations = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return Build.VERSION.SDK_INT >= 31 && !powerSave && animations > 0f
}

@Composable
fun ProgressiveBlurScaffold(
    modifier: Modifier = Modifier,
    topVisible: () -> Float = { 1f },
    blurEnabled: Boolean = true,
    bottomEdgeBlur: Boolean = true,
    topBar: @Composable () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val source = rememberGraphicsLayer()
    val density = LocalDensity.current
    val hardware = hardwareBlurAllowed() && blurEnabled
    var topSize by remember { mutableStateOf(IntSize.Zero) }
    var bottomSize by remember { mutableStateOf(IntSize.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val backdropScope = remember(source, hardware) {
        BackdropBlurScope(
            source = source,
            hardware = hardware,
            rootCoordinates = { rootCoords },
        )
    }
    val topPadding = with(density) { topSize.height.toDp() }
    val bottomPadding = with(density) { bottomSize.height.toDp() }
    CompositionLocalProvider(LocalBackdropBlur provides backdropScope) {
        Box(
            modifier
                .fillMaxSize()
                .clipToBounds()
                .background(MaterialTheme.colorScheme.background)
                .onGloballyPositioned {
                    rootCoords = it
                    viewport = it.size
                },
        ) {
            Box(Modifier.fillMaxSize().drawWithContent {
                if (hardware) {
                    source.record { this@drawWithContent.drawContent() }
                    drawLayer(source)
                } else drawContent()
            }) {
                content(PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding + 24.dp))
            }
            EdgeBlur(
                source = source,
                hardware = hardware,
                top = true,
                sourceHeight = viewport.height,
                strength = topVisible,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topPadding + 32.dp),
            )
            if (bottomBar != null && bottomEdgeBlur) {
                EdgeBlur(
                    source = source,
                    hardware = hardware,
                    top = false,
                    sourceHeight = viewport.height,
                    strength = { 1f },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(bottomPadding + 28.dp),
                )
            }
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topSize = it }) { topBar() }
            if (bottomBar != null) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { bottomSize = it }) { bottomBar() }
            }
        }
    }
}

@Composable
private fun EdgeBlur(
    source: GraphicsLayer,
    hardware: Boolean,
    top: Boolean,
    sourceHeight: Int,
    strength: () -> Float,
    modifier: Modifier,
) {
    val band = rememberGraphicsLayer()
    val colors = NagiThemeTokens.colors
    val radius = with(LocalDensity.current) { 36.dp.toPx() }
    val effect = remember(radius, hardware, colors.isDark) {
        if (hardware && Build.VERSION.SDK_INT >= 31) {
            val blur = AndroidRenderEffect.createBlurEffect(radius, radius, AndroidShader.TileMode.CLAMP)
            val matrix = AndroidColorMatrix().apply { setSaturation(if (colors.isDark) 1.35f else 1.25f) }
            val sat = AndroidRenderEffect.createColorFilterEffect(AndroidColorMatrixColorFilter(matrix))
            AndroidRenderEffect.createChainEffect(blur, sat).asComposeRenderEffect()
        } else null
    }
    val canvasColor = colors.groupedCanvas
    val peakAlpha = when {
        !hardware -> 0.98f
        top -> colors.frostedChrome.alpha
        else -> if (colors.isDark) 0.56f else 0.48f
    }
    Box(modifier.graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        alpha = strength().coerceIn(0f, 1f)
    }.drawWithCache {
        fun stops(color: Color, peak: Float): Array<Pair<Float, Color>> {
            val values = listOf(0f to 1f, .25f to .96f, .5f to .7f, .75f to .25f, 1f to 0f)
            return values.map { (p, a) -> (if (top) p else 1f - p) to color.copy(alpha = a * peak) }
                .sortedBy { it.first }.toTypedArray()
        }
        val mask = Brush.verticalGradient(*stops(Color.White, 1f), endY = size.height)
        val tint = Brush.verticalGradient(*stops(canvasColor, peakAlpha), endY = size.height)
        onDrawBehind {
            if (hardware && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                val overscan = (radius * 1.5f).toInt()
                val cropTop = if (top) 0f else sourceHeight - size.height
                band.renderEffect = effect
                band.record(size = IntSize(size.width.toInt(), size.height.toInt() + overscan * 2)) {
                    translate(top = overscan - cropTop) { drawLayer(source) }
                }
                translate(top = -overscan.toFloat()) { drawLayer(band) }
                drawRect(mask, blendMode = BlendMode.DstIn)
            }
            drawRect(tint)
        }
    })
}

private val FrostedGrainBrush: ShaderBrush by lazy {
    val size = 64
    val pixels = IntArray(size * size)
    var seed = 0x6E616769
    for (i in pixels.indices) {
        seed = seed * 1664525 + 1013904223
        val v = (seed ushr 24) and 0xFF
        val isSpeck = v > 128
        val alpha = ((if (isSpeck) v - 128 else 127 - v) / 14).coerceIn(0, 8)
        val channel = if (isSpeck) 248 else 28
        pixels[i] = AndroidColor.argb(alpha, channel, channel, channel)
    }
    val bmp = AndroidBitmap.createBitmap(pixels, size, size, AndroidBitmap.Config.ARGB_8888)
    ShaderBrush(AndroidBitmapShader(bmp, AndroidShader.TileMode.REPEAT, AndroidShader.TileMode.REPEAT))
}

@Composable
internal fun Modifier.frostedCapsuleBackdrop(
    backgroundColor: Color,
    shape: Shape,
): Modifier {
    val backdrop = LocalBackdropBlur.current
    val isDark = NagiThemeTokens.colors.isDark
    val capsuleLayer = rememberGraphicsLayer()
    val radius = with(LocalDensity.current) { 36.dp.toPx() }
    val effect = remember(radius, backdrop?.hardware, isDark) {
        if (backdrop?.hardware == true && Build.VERSION.SDK_INT >= 31) {
            val blur = AndroidRenderEffect.createBlurEffect(radius, radius, AndroidShader.TileMode.CLAMP)
            val satMatrix = AndroidColorMatrix().apply { setSaturation(if (isDark) 1.45f else 1.35f) }
            val colorFilter = AndroidRenderEffect.createColorFilterEffect(AndroidColorMatrixColorFilter(satMatrix))
            AndroidRenderEffect.createChainEffect(blur, colorFilter).asComposeRenderEffect()
        } else null
    }
    var capsuleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return this
        .onGloballyPositioned { coords ->
            capsuleCoords = coords
        }
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val topGlaze = if (isDark) Color(0xFF1E2026).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.80f)
            val midGlaze = if (isDark) Color(0xFF18191D).copy(alpha = 0.76f) else Color.White.copy(alpha = 0.74f)
            val bottomGlaze = if (isDark) Color(0xFF141519).copy(alpha = 0.80f) else Color(0xFFF8FAFC).copy(alpha = 0.78f)
            val glazeBrush = Brush.verticalGradient(
                0f to topGlaze,
                0.5f to midGlaze,
                1f to bottomGlaze,
            )
            val rimBrush = Brush.verticalGradient(
                0f to (if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.92f)),
                0.5f to (if (isDark) Color.White.copy(alpha = 0.06f) else Color(0xFFE2E6EC).copy(alpha = 0.55f)),
                1f to (if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFD5DBE3).copy(alpha = 0.70f)),
            )
            val fallbackTint = if (isDark) Color(0xFF18191D).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.90f)
            val innerRimStroke = 1.dp.toPx()
            onDrawWithContent {
                val activeHardware = backdrop != null &&
                    backdrop.hardware &&
                    effect != null &&
                    Build.VERSION.SDK_INT >= 31 &&
                    drawContext.canvas.nativeCanvas.isHardwareAccelerated &&
                    size.width > 1f &&
                    size.height > 1f
                if (activeHardware) {
                    val root = backdrop.rootCoordinates()
                    val currentCoords = capsuleCoords
                    val offsetInRoot = if (root != null && root.isAttached && currentCoords != null && currentCoords.isAttached) {
                        root.localPositionOf(currentCoords, Offset.Zero)
                    } else {
                        Offset.Zero
                    }
                    val overscan = (radius * 1.5f).toInt()
                    val w = size.width.toInt().coerceAtLeast(1)
                    val h = size.height.toInt().coerceAtLeast(1)
                    capsuleLayer.renderEffect = effect
                    capsuleLayer.record(size = IntSize(w + overscan * 2, h + overscan * 2)) {
                        translate(
                            left = overscan - offsetInRoot.x,
                            top = overscan - offsetInRoot.y,
                        ) {
                            drawLayer(backdrop.source)
                        }
                    }
                    translate(left = -overscan.toFloat(), top = -overscan.toFloat()) {
                        drawLayer(capsuleLayer)
                    }
                    drawRect(glazeBrush)
                    drawRect(FrostedGrainBrush)
                } else {
                    drawRect(if (backgroundColor == Color.Transparent) fallbackTint else backgroundColor)
                }
                drawContent()
                drawOutline(
                    outline = outline,
                    brush = rimBrush,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = innerRimStroke),
                )
            }
        }
}
