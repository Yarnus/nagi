package dev.nagi.app.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.EaseInOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.nagi.app.ui.theme.NagiThemeTokens

internal val LocalNagiHaze = compositionLocalOf<HazeState?> { null }
internal val LocalNagiBlurEnabled = compositionLocalOf { false }

@Composable
internal fun hardwareBlurAllowed(): Boolean {
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

/**
 * Scaffold with bounded progressive edge blur for top/bottom chrome.
 * Performance constraints:
 * - Blur is bounded strictly to top/bottom bar areas and floating controls; body items never apply hazeEffect.
 * - Uses [NagiMaterials.DefaultInputScale] (0.5x) to downscale blur input before RenderEffect sampling.
 * - Scroll-driven [topVisible] alpha is read in graphicsLayer phase to avoid recomposition during scroll.
 */
@Composable
fun ProgressiveBlurScaffold(
    modifier: Modifier = Modifier,
    topVisible: () -> Float = { 1f },
    blurEnabled: Boolean = true,
    bottomEdgeBlur: Boolean = true,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical),
    topBar: @Composable () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val hardware = hardwareBlurAllowed() && blurEnabled
    val hazeState = rememberHazeState(blurEnabled = hardware)
    SideEffect { hazeState.blurEnabled = hardware }
    val chromeSpec = NagiMaterials.chrome()
    val topInsets = contentWindowInsets.only(WindowInsetsSides.Top)
    val bottomInsets = contentWindowInsets.only(WindowInsetsSides.Bottom)
    var topSize by remember { mutableStateOf(IntSize.Zero) }
    var bottomSize by remember { mutableStateOf(IntSize.Zero) }
    val topPadding = with(density) { topSize.height.toDp() }
    val bottomPadding = with(density) { bottomSize.height.toDp() }
    val fallbackBottomPadding = with(density) { bottomInsets.getBottom(density).toDp() }
    val effectiveBottomPadding = if (bottomBar != null) bottomPadding else fallbackBottomPadding

    CompositionLocalProvider(
        LocalNagiHaze provides hazeState,
        LocalNagiBlurEnabled provides hardware,
    ) {
        Box(
            modifier
                .fillMaxSize()
                .clipToBounds()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(Modifier.fillMaxSize().hazeSource(state = hazeState)) {
                content(PaddingValues(top = topPadding + 8.dp, bottom = effectiveBottomPadding + 24.dp))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topPadding + 10.dp)
                    .graphicsLayer { alpha = topVisible().coerceIn(0f, 1f) }
                    .hazeEffect(state = hazeState, style = chromeSpec.style) {
                        this.blurEnabled = hardware
                        this.inputScale = NagiMaterials.DefaultInputScale
                        this.progressive = HazeProgressive.verticalGradient(
                            easing = EaseInOut,
                            startIntensity = 1f,
                            endIntensity = 0f,
                        )
                    },
            )
            if (bottomBar != null && bottomEdgeBlur) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(bottomPadding + 28.dp)
                        .hazeEffect(state = hazeState, style = chromeSpec.style) {
                            this.blurEnabled = hardware
                            this.inputScale = NagiMaterials.DefaultInputScale
                            this.progressive = HazeProgressive.verticalGradient(
                                easing = EaseInOut,
                                startIntensity = 0f,
                                endIntensity = 1f,
                            )
                        },
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { topSize = it }
                    .windowInsetsPadding(topInsets),
            ) { topBar() }
            if (bottomBar != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { bottomSize = it }
                        .windowInsetsPadding(bottomInsets),
                ) { bottomBar() }
            }
        }
    }
}

@Composable
internal fun Modifier.frostedCapsuleBackdrop(
    backgroundColor: Color,
    shape: Shape,
    borderColor: Color? = null,
    tier: NagiMaterialTier? = null,
    dropShadow: Boolean = true,
): Modifier {
    val colors = NagiThemeTokens.colors
    val hazeState = LocalNagiHaze.current
    val blurEnabled = LocalNagiBlurEnabled.current
    val effectiveTier = tier ?: if (backgroundColor == colors.frostedDropdown) {
        NagiMaterialTier.Menu
    } else {
        NagiMaterialTier.Capsule
    }
    val isCustomTint = backgroundColor != colors.frostedChrome &&
        backgroundColor != colors.frostedDropdown &&
        backgroundColor != Color.Transparent

    if (isCustomTint) {
        return this
            .glassShadow(shape = shape, enabled = dropShadow)
            .clip(shape)
            .background(backgroundColor)
            .glassRim(shape = shape, customBorderColor = borderColor)
    }

    val spec = NagiMaterials.specFor(effectiveTier)
    val hardwareActive = hazeState != null && blurEnabled
    val backdropModifier = if (hardwareActive) {
        Modifier
            .clip(shape)
            .hazeEffect(state = hazeState, style = spec.style) {
                this.blurEnabled = true
                this.inputScale = NagiMaterials.DefaultInputScale
            }
    } else {
        Modifier
            .clip(shape)
            .background(spec.fallbackColor)
    }
    return this
        .glassShadow(shape = shape, enabled = dropShadow)
        .then(backdropModifier)
        .glassRim(shape = shape, customBorderColor = borderColor)
}
