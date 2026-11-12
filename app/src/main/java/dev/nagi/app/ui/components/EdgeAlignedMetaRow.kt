package dev.nagi.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.theme.Space
import kotlin.math.max

@Composable
internal fun EdgeAlignedMetaRow(
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    Layout(content = {
        Box { leading() }
        Box { trailing() }
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val end = measurables[1].measure(Constraints(maxWidth = width))
        val gap = if (end.width > 0) Space.md.roundToPx() else 0
        val remaining = width - end.width - gap
        val start = measurables[0].measure(
            Constraints(maxWidth = if (remaining >= (132.dp * fontScale).roundToPx()) remaining else width),
        )
        if (start.width + gap + end.width <= width) {
            val height = max(start.height, end.height)
            layout(width, height) {
                start.placeRelative(0, verticalAlignment.align(start.height, height))
                end.placeRelative(width - end.width, verticalAlignment.align(end.height, height))
            }
        } else {
            val verticalGap = Space.xs.roundToPx()
            layout(width, start.height + verticalGap + end.height) {
                start.placeRelative(0, 0)
                end.placeRelative(width - end.width, start.height + verticalGap)
            }
        }
    }
}
