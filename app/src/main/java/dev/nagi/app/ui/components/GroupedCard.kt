package dev.nagi.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.theme.NagiThemeTokens

enum class CardGroupPosition {
    Single, First, Middle, Last;

    companion object {
        fun at(index: Int, count: Int): CardGroupPosition = when {
            count <= 1 -> Single
            index == 0 -> First
            index == count - 1 -> Last
            else -> Middle
        }
    }
}

@Composable
fun FlatCard(
    modifier: Modifier = Modifier,
    shape: Shape = NagiThemeTokens.shapes.flatCard,
    containerColor: Color = NagiThemeTokens.colors.cardSurface,
    borderColor: Color = NagiThemeTokens.colors.cardBorder,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, borderColor),
        content = content,
    )
}

@Composable
fun SectionCardItem(
    position: CardGroupPosition,
    margin: Dp = 16.dp,
    cornerRadius: Dp = 20.dp,
    dividerIndent: Dp = 16.dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val topRadius = if (position == CardGroupPosition.First || position == CardGroupPosition.Single) cornerRadius else 0.dp
    val bottomRadius = if (position == CardGroupPosition.Last || position == CardGroupPosition.Single) cornerRadius else 0.dp
    val shape = RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius,
    )
    val surfaceColor = NagiThemeTokens.colors.cardSurface
    val borderColor = NagiThemeTokens.colors.cardBorder

    val baseModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = margin)

    if (position == CardGroupPosition.Single) {
        Box(
            modifier = baseModifier
                .clip(shape)
                .background(surfaceColor)
                .border(1.dp, borderColor, shape),
            content = content,
        )
    } else {
        Box(
            modifier = baseModifier
                .clip(shape)
                .background(surfaceColor)
                .drawWithContent {
                    drawContent()
                    val stroke = 1.dp.toPx()
                    val half = stroke / 2f
                    val w = size.width
                    val h = size.height
                    val r = cornerRadius.toPx().coerceAtMost(minOf(w, h) / 2f)

                    when (position) {
                        CardGroupPosition.First -> {
                            val path = Path().apply {
                                moveTo(half, h)
                                lineTo(half, r)
                                arcTo(Rect(half, half, r * 2f - half, r * 2f - half), 180f, 90f, false)
                                lineTo(w - r, half)
                                arcTo(Rect(w - r * 2f + half, half, w - half, r * 2f - half), 270f, 90f, false)
                                lineTo(w - half, h)
                            }
                            drawPath(path, borderColor, style = Stroke(width = stroke))
                            drawLine(
                                color = borderColor,
                                start = Offset(dividerIndent.toPx(), h - half),
                                end = Offset(w - half, h - half),
                                strokeWidth = stroke,
                            )
                        }
                        CardGroupPosition.Middle -> {
                            drawLine(borderColor, Offset(half, 0f), Offset(half, h), stroke)
                            drawLine(borderColor, Offset(w - half, 0f), Offset(w - half, h), stroke)
                            drawLine(
                                color = borderColor,
                                start = Offset(dividerIndent.toPx(), h - half),
                                end = Offset(w - half, h - half),
                                strokeWidth = stroke,
                            )
                        }
                        CardGroupPosition.Last -> {
                            val path = Path().apply {
                                moveTo(half, 0f)
                                lineTo(half, h - r)
                                arcTo(Rect(half, h - r * 2f + half, r * 2f - half, h - half), 180f, -90f, false)
                                lineTo(w - r, h - half)
                                arcTo(Rect(w - r * 2f + half, h - r * 2f + half, w - half, h - half), 90f, -90f, false)
                                lineTo(w - half, 0f)
                            }
                            drawPath(path, borderColor, style = Stroke(width = stroke))
                        }
                        CardGroupPosition.Single -> Unit
                    }
                },
            content = content,
        )
    }
}
