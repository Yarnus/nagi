package dev.nagi.app.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.nagi.app.ui.theme.NagiThemeTokens

internal enum class NagiMaterialTier {
    Chrome,
    Capsule,
    Menu,
}

@Immutable
internal data class NagiMaterialSpec(
    val style: HazeStyle,
    val fallbackColor: Color,
    val fallbackBorder: Color,
)

internal object NagiMaterials {
    val DefaultInputScale: HazeInputScale = HazeInputScale.Fixed(0.5f)

    @Composable
    @ReadOnlyComposable
    fun chrome(): NagiMaterialSpec {
        val colors = NagiThemeTokens.colors
        val isDark = colors.isDark
        val baseColor = if (isDark) Color(0xFF16171B) else Color.White
        val vibrancyTint = if (isDark) {
            HazeTint(Color(0xFF9C9C9C).copy(alpha = 0.18f), BlendMode.Overlay)
        } else {
            HazeTint(Color(0xFFA6A6A6).copy(alpha = 0.25f), BlendMode.ColorDodge)
        }
        val mainTint = HazeTint(baseColor.copy(alpha = if (isDark) 0.70f else 0.60f))
        val fallbackColor = colors.groupedCanvas.copy(alpha = 0.98f)
        return NagiMaterialSpec(
            style = HazeStyle(
                backgroundColor = colors.groupedCanvas,
                tints = listOf(vibrancyTint, mainTint),
                blurRadius = 24.dp,
                noiseFactor = if (isDark) 0.04f else 0f,
                fallbackTint = HazeTint(fallbackColor),
            ),
            fallbackColor = fallbackColor,
            fallbackBorder = colors.cardBorder,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun capsule(): NagiMaterialSpec {
        val colors = NagiThemeTokens.colors
        val isDark = colors.isDark
        val baseColor = if (isDark) Color(0xFF1C1D22) else Color.White
        val vibrancyTint = if (isDark) {
            HazeTint(Color(0xFF9C9C9C).copy(alpha = 0.18f), BlendMode.Overlay)
        } else {
            HazeTint(Color(0xFFA6A6A6).copy(alpha = 0.28f), BlendMode.ColorDodge)
        }
        val mainTint = HazeTint(baseColor.copy(alpha = if (isDark) 0.70f else 0.62f))
        val fallbackColor = if (isDark) Color(0xFF1C1D22) else Color.White
        return NagiMaterialSpec(
            style = HazeStyle(
                backgroundColor = fallbackColor,
                tints = listOf(vibrancyTint, mainTint),
                blurRadius = 20.dp,
                noiseFactor = if (isDark) 0.04f else 0f,
                fallbackTint = HazeTint(fallbackColor),
            ),
            fallbackColor = fallbackColor,
            fallbackBorder = if (isDark) Color(0xFF2E3138) else Color(0xFFE2E5EB),
        )
    }

    @Composable
    @ReadOnlyComposable
    fun menu(): NagiMaterialSpec {
        val colors = NagiThemeTokens.colors
        val isDark = colors.isDark
        val baseColor = if (isDark) Color(0xFF1C1D22) else Color.White
        val vibrancyTint = if (isDark) {
            HazeTint(Color(0xFF9C9C9C).copy(alpha = 0.16f), BlendMode.Overlay)
        } else {
            HazeTint(Color(0xFFA6A6A6).copy(alpha = 0.20f), BlendMode.ColorDodge)
        }
        val mainTint = HazeTint(baseColor.copy(alpha = if (isDark) 0.78f else 0.72f))
        val fallbackColor = if (isDark) Color(0xFF1C1D22).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.96f)
        return NagiMaterialSpec(
            style = HazeStyle(
                backgroundColor = if (isDark) Color(0xFF1C1D22) else Color.White,
                tints = listOf(vibrancyTint, mainTint),
                blurRadius = 30.dp,
                noiseFactor = if (isDark) 0.04f else 0f,
                fallbackTint = HazeTint(fallbackColor),
            ),
            fallbackColor = fallbackColor,
            fallbackBorder = colors.cardBorder,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun specFor(tier: NagiMaterialTier): NagiMaterialSpec = when (tier) {
        NagiMaterialTier.Chrome -> chrome()
        NagiMaterialTier.Capsule -> capsule()
        NagiMaterialTier.Menu -> menu()
    }
}

@Composable
internal fun Modifier.glassShadow(
    shape: Shape = CircleShape,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val isDark = NagiThemeTokens.colors.isDark
    val shadowAlpha = if (isDark) 0.03f else 0.09f
    return this.dropShadow(
        shape = shape,
        shadow = Shadow(
            radius = 20.dp,
            color = Color.Black,
            alpha = shadowAlpha,
            offset = DpOffset(0.dp, 6.dp),
        ),
    )
}

@Composable
internal fun Modifier.glassRim(
    shape: Shape,
    customBorderColor: Color? = null,
    customBorderWidth: Dp = 1.dp,
): Modifier {
    val isDark = NagiThemeTokens.colors.isDark
    return this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val hairlineWidth = if (customBorderColor != null) customBorderWidth.toPx() else 0.5.dp.toPx()
        val hairlineColor = customBorderColor ?: if (isDark) {
            Color.White.copy(alpha = 0.12f)
        } else {
            Color.Black.copy(alpha = 0.08f)
        }
        val highlightPeak = if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.50f)
        val highlightHeight = (size.height * 0.40f).coerceAtLeast(1f)
        val highlightBrush = Brush.verticalGradient(
            0f to highlightPeak,
            1f to Color.Transparent,
            startY = 0f,
            endY = highlightHeight,
        )
        val highlightWidth = 1.dp.toPx()
        onDrawWithContent {
            drawContent()
            drawOutline(
                outline = outline,
                brush = highlightBrush,
                style = Stroke(width = highlightWidth),
            )
            drawOutline(
                outline = outline,
                color = hairlineColor,
                style = Stroke(width = hairlineWidth),
            )
        }
    }
}

/**
 * Spring press scale + bounded soft wash inside [shape] outline without clipping outer shadows.
 */
@Composable
internal fun Modifier.nagiPressFeedback(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    shape: Shape = CircleShape,
    overlayColor: Color? = null,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) pressedScale else 1f,
        animationSpec = if (pressed) {
            tween(durationMillis = 70, easing = FastOutLinearInEasing)
        } else {
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "nagi-press-scale",
    )
    val overlayAlpha by animateFloatAsState(
        targetValue = if (enabled && pressed) 1f else 0f,
        animationSpec = if (pressed) {
            tween(durationMillis = 50, easing = FastOutLinearInEasing)
        } else {
            tween(durationMillis = 160, easing = FastOutSlowInEasing)
        },
        label = "nagi-press-overlay",
    )
    val isDark = NagiThemeTokens.colors.isDark
    val effectiveOverlay = overlayColor ?: if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color(0xFF1F1A12).copy(alpha = 0.045f)
    }
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            onDrawWithContent {
                drawContent()
                if (overlayAlpha > 0.001f) {
                    drawOutline(
                        outline = outline,
                        color = effectiveOverlay.copy(alpha = effectiveOverlay.alpha * overlayAlpha),
                    )
                }
            }
        }
}
