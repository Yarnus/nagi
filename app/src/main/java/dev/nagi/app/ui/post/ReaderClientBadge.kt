package dev.nagi.app.ui.post

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.theme.NagiThemeTokens

@Composable
internal fun ReaderClientBadge(platform: String?) {
    val label = platform?.trim().orEmpty()
    val colors = NagiThemeTokens.colors
    val (icon, description) = when (label) {
        "/" -> ReaderClientIcons.Browser to "网页版"
        "Android" -> ReaderClientIcons.Android to "Android 客户端"
        "iOS" -> ReaderClientIcons.Phone to "iOS 客户端"
        else -> null to label
    }
    if (icon != null) {
        Icon(icon, description, Modifier.size(16.dp), tint = colors.textSecondary)
    } else if (label.isNotEmpty()) {
        Text(label, style = NagiThemeTokens.typography.meta, color = colors.textSecondary)
    }
}

private object ReaderClientIcons {
    private fun strokeIcon(name: String, path: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathBuilder().apply(path).nodes,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    val Browser = strokeIcon("ReaderBrowser") {
        moveTo(5f, 4f); lineTo(19f, 4f)
        quadTo(21f, 4f, 21f, 6f); lineTo(21f, 18f)
        quadTo(21f, 20f, 19f, 20f); lineTo(5f, 20f)
        quadTo(3f, 20f, 3f, 18f); lineTo(3f, 6f)
        quadTo(3f, 4f, 5f, 4f); close()
        moveTo(3f, 9f); lineTo(21f, 9f)
        moveTo(6f, 6.5f); lineTo(7f, 6.5f)
        moveTo(10f, 6.5f); lineTo(11f, 6.5f)
    }

    val Android = strokeIcon("ReaderAndroid") {
        moveTo(8f, 5f); lineTo(6.5f, 2.5f)
        moveTo(16f, 5f); lineTo(17.5f, 2.5f)
        moveTo(5f, 11f); quadTo(5f, 4f, 12f, 4f)
        quadTo(19f, 4f, 19f, 11f); close()
        moveTo(8.5f, 7.5f); lineTo(8.5f, 8f)
        moveTo(15.5f, 7.5f); lineTo(15.5f, 8f)
        moveTo(5f, 14f); lineTo(5f, 18f)
        quadTo(5f, 20f, 7f, 20f); lineTo(17f, 20f)
        quadTo(19f, 20f, 19f, 18f); lineTo(19f, 14f)
        moveTo(9f, 20f); lineTo(9f, 22f)
        moveTo(15f, 20f); lineTo(15f, 22f)
    }

    val Phone = strokeIcon("ReaderPhone") {
        moveTo(8f, 2.5f); lineTo(16f, 2.5f)
        quadTo(18f, 2.5f, 18f, 4.5f); lineTo(18f, 19.5f)
        quadTo(18f, 21.5f, 16f, 21.5f); lineTo(8f, 21.5f)
        quadTo(6f, 21.5f, 6f, 19.5f); lineTo(6f, 4.5f)
        quadTo(6f, 2.5f, 8f, 2.5f); close()
        moveTo(10f, 5f); lineTo(14f, 5f)
        moveTo(10f, 19f); lineTo(14f, 19f)
    }
}
