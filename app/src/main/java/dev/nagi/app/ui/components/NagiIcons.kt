package dev.nagi.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

object NagiIcons {
    private const val MONOLINE_STROKE = 1.6f

    private fun strokeIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathBuilder().apply(block).nodes,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = MONOLINE_STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    val Back: ImageVector = strokeIcon("Back") {
        moveTo(14.5f, 5.5f)
        lineTo(8f, 12f)
        lineTo(14.5f, 18.5f)
    }

    val ChevronLeft: ImageVector = strokeIcon("ChevronLeft") {
        moveTo(14.5f, 6.5f)
        lineTo(9f, 12f)
        lineTo(14.5f, 17.5f)
    }

    val ChevronRight: ImageVector = strokeIcon("ChevronRight") {
        moveTo(9.5f, 6.5f)
        lineTo(15f, 12f)
        lineTo(9.5f, 17.5f)
    }

    val ChevronDown: ImageVector = strokeIcon("ChevronDown") {
        moveTo(6.5f, 9.5f)
        lineTo(12f, 15f)
        lineTo(17.5f, 9.5f)
    }

    val ChevronUp: ImageVector = strokeIcon("ChevronUp") {
        moveTo(6.5f, 14.5f)
        lineTo(12f, 9f)
        lineTo(17.5f, 14.5f)
    }

    val Hamburger: ImageVector = strokeIcon("Hamburger") {
        moveTo(4.5f, 7f); lineTo(19.5f, 7f)
        moveTo(4.5f, 12f); lineTo(19.5f, 12f)
        moveTo(4.5f, 17f); lineTo(19.5f, 17f)
    }

    val Subforums: ImageVector = strokeIcon("Subforums") {
        moveTo(4.5f, 7f); lineTo(6.5f, 7f)
        moveTo(9.5f, 7f); lineTo(19.5f, 7f)
        moveTo(4.5f, 12f); lineTo(6.5f, 12f)
        moveTo(9.5f, 12f); lineTo(19.5f, 12f)
        moveTo(4.5f, 17f); lineTo(6.5f, 17f)
        moveTo(9.5f, 17f); lineTo(19.5f, 17f)
    }

    val More: ImageVector = strokeIcon("More") {
        listOf(5.5f, 12f, 18.5f).forEach { cx ->
            moveTo(cx - 1.1f, 12f)
            arcTo(1.1f, 1.1f, 0f, true, true, cx + 1.1f, 12f)
            arcTo(1.1f, 1.1f, 0f, true, true, cx - 1.1f, 12f)
        }
    }

    val Close: ImageVector = strokeIcon("Close") {
        moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
        moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f)
    }

    val ClearCircle: ImageVector = strokeIcon("ClearCircle") {
        moveTo(12f, 3.5f)
        arcTo(8.5f, 8.5f, 0f, true, true, 12f, 20.5f)
        arcTo(8.5f, 8.5f, 0f, true, true, 12f, 3.5f)
        moveTo(9.3f, 9.3f); lineTo(14.7f, 14.7f)
        moveTo(14.7f, 9.3f); lineTo(9.3f, 14.7f)
    }

    val Check: ImageVector = strokeIcon("Check") {
        moveTo(5.5f, 12.5f)
        lineTo(10f, 17f)
        lineTo(18.5f, 7.5f)
    }

    val Search: ImageVector = strokeIcon("Search") {
        moveTo(4.8f, 10.8f)
        arcTo(6f, 6f, 0f, true, true, 16.8f, 10.8f)
        arcTo(6f, 6f, 0f, true, true, 4.8f, 10.8f)
        moveTo(15.2f, 15.2f)
        lineTo(19.5f, 19.5f)
    }

    val Refresh: ImageVector = strokeIcon("Refresh") {
        moveTo(19.2f, 12f)
        arcTo(7.2f, 7.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16.9f, 6.7f)
        moveTo(17.2f, 3.6f)
        lineTo(17.2f, 7.1f)
        lineTo(13.7f, 7.1f)
    }

    val Sort: ImageVector = strokeIcon("Sort") {
        moveTo(10f, 6.5f); lineTo(19.5f, 6.5f)
        moveTo(10f, 12f); lineTo(16.8f, 12f)
        moveTo(10f, 17.5f); lineTo(14f, 17.5f)
        moveTo(5.5f, 5.5f); lineTo(5.5f, 18.5f)
        moveTo(3f, 16f); lineTo(5.5f, 18.5f); lineTo(8f, 16f)
    }

    val Compose: ImageVector = strokeIcon("Compose") {
        moveTo(14.2f, 5.2f); lineTo(18.8f, 9.8f)
        moveTo(4.5f, 15.2f); lineTo(15f, 4.7f)
        curveTo(16.8f, 2.9f, 21.1f, 7.2f, 19.3f, 9f)
        lineTo(8.8f, 19.5f); lineTo(3.8f, 20.2f); lineTo(4.5f, 15.2f); close()
    }

    val Reply: ImageVector = strokeIcon("Reply") {
        moveTo(9.8f, 5.5f); lineTo(4.2f, 10.2f); lineTo(9.8f, 14.9f)
        moveTo(4.2f, 10.2f); lineTo(13f, 10.2f)
        curveTo(17.8f, 10.2f, 19.8f, 14f, 19.8f, 18.8f)
    }

    val Trash: ImageVector = strokeIcon("Trash") {
        moveTo(4.5f, 6.8f); lineTo(19.5f, 6.8f)
        moveTo(9.2f, 6.8f); lineTo(9.2f, 4.3f); lineTo(14.8f, 4.3f); lineTo(14.8f, 6.8f)
        moveTo(6.5f, 6.8f); lineTo(7.4f, 19.5f); lineTo(16.6f, 19.5f); lineTo(17.5f, 6.8f)
        moveTo(10.2f, 10.5f); lineTo(10.2f, 16f)
        moveTo(13.8f, 10.5f); lineTo(13.8f, 16f)
    }

    val Person: ImageVector = strokeIcon("Person") {
        moveTo(8.2f, 7.6f)
        arcTo(3.8f, 3.8f, 0f, true, true, 15.8f, 7.6f)
        arcTo(3.8f, 3.8f, 0f, true, true, 8.2f, 7.6f)
        moveTo(4.8f, 19.8f)
        curveTo(4.8f, 13.4f, 19.2f, 13.4f, 19.2f, 19.8f)
    }

    val Users: ImageVector = strokeIcon("Users") {
        moveTo(6.2f, 8.2f); arcTo(3f, 3f, 0f, true, true, 12.2f, 8.2f); arcTo(3f, 3f, 0f, true, true, 6.2f, 8.2f)
        moveTo(3.2f, 18.5f); curveTo(3.2f, 13.8f, 15.2f, 13.8f, 15.2f, 18.5f)
        moveTo(14.5f, 5.8f); arcTo(2.4f, 2.4f, 0f, true, true, 18.8f, 5.8f)
        moveTo(16.8f, 17.5f); curveTo(17.8f, 13.6f, 21.2f, 13.6f, 21.2f, 17.5f)
    }

    val Notification: ImageVector = strokeIcon("Notification") {
        moveTo(5.5f, 17f); lineTo(7.3f, 14.2f); lineTo(7.3f, 9.5f)
        curveTo(7.3f, 3.4f, 16.7f, 3.4f, 16.7f, 9.5f)
        lineTo(16.7f, 14.2f); lineTo(18.5f, 17f); close()
        moveTo(10f, 19.8f); curveTo(11f, 20.8f, 13f, 20.8f, 14f, 19.8f)
    }

    val Message: ImageVector = strokeIcon("Message") {
        moveTo(5f, 16f)
        curveTo(-1.2f, 3.8f, 22.2f, -0.2f, 20.8f, 12f)
        curveTo(19.8f, 17.8f, 12f, 19.8f, 8.3f, 17.2f)
        lineTo(3.5f, 19.8f); close()
    }

    val Bookmark: ImageVector = strokeIcon("Bookmark") {
        moveTo(6.5f, 4.5f)
        lineTo(17.5f, 4.5f)
        lineTo(17.5f, 19.5f)
        lineTo(12f, 15.8f)
        lineTo(6.5f, 19.5f)
        close()
    }

    val Star: ImageVector = strokeIcon("Star") {
        moveTo(12f, 3.8f)
        lineTo(14.5f, 8.9f)
        lineTo(20.1f, 9.7f)
        lineTo(16f, 13.7f)
        lineTo(17f, 19.3f)
        lineTo(12f, 16.6f)
        lineTo(7f, 19.3f)
        lineTo(8f, 13.7f)
        lineTo(3.9f, 9.7f)
        lineTo(9.5f, 8.9f)
        close()
    }

    val History: ImageVector = strokeIcon("History") {
        moveTo(3.5f, 12f)
        arcTo(8.5f, 8.5f, 0f, true, true, 20.5f, 12f)
        arcTo(8.5f, 8.5f, 0f, true, true, 3.5f, 12f)
        moveTo(12f, 6.8f); lineTo(12f, 12f); lineTo(15.6f, 12f)
    }

    val Info: ImageVector = strokeIcon("Info") {
        moveTo(3.5f, 12f)
        arcTo(8.5f, 8.5f, 0f, true, true, 20.5f, 12f)
        arcTo(8.5f, 8.5f, 0f, true, true, 3.5f, 12f)
        moveTo(12f, 11.2f); lineTo(12f, 16.5f)
        moveTo(12f, 7.6f); lineTo(12f, 7.8f)
    }

    val Tuning: ImageVector = strokeIcon("Tuning") {
        moveTo(4.5f, 7f); lineTo(13.5f, 7f)
        moveTo(17.5f, 7f); lineTo(19.5f, 7f)
        moveTo(13.5f, 7f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.5f, 7f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 13.5f, 7f)

        moveTo(4.5f, 12f); lineTo(6.5f, 12f)
        moveTo(10.5f, 12f); lineTo(19.5f, 12f)
        moveTo(6.5f, 12f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 10.5f, 12f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6.5f, 12f)

        moveTo(4.5f, 17f); lineTo(12.5f, 17f)
        moveTo(16.5f, 17f); lineTo(19.5f, 17f)
        moveTo(12.5f, 17f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16.5f, 17f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12.5f, 17f)
    }

    val Forums: ImageVector = strokeIcon("Forums") {
        moveTo(4.5f, 4.5f); lineTo(10.3f, 4.5f); lineTo(10.3f, 10.3f); lineTo(4.5f, 10.3f); close()
        moveTo(13.7f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 10.3f); lineTo(13.7f, 10.3f); close()
        moveTo(4.5f, 13.7f); lineTo(10.3f, 13.7f); lineTo(10.3f, 19.5f); lineTo(4.5f, 19.5f); close()
        moveTo(13.7f, 13.7f); lineTo(19.5f, 13.7f); lineTo(19.5f, 19.5f); lineTo(13.7f, 19.5f); close()
    }

    val ForumStack: ImageVector = strokeIcon("ForumStack") {
        moveTo(12f, 3.5f); lineTo(20.5f, 7.8f); lineTo(12f, 12f); lineTo(3.5f, 7.8f); close()
        moveTo(3.5f, 12f); lineTo(12f, 16.2f); lineTo(20.5f, 12f)
        moveTo(3.5f, 16.2f); lineTo(12f, 20.5f); lineTo(20.5f, 16.2f)
    }

    val TopicDocument: ImageVector = strokeIcon("TopicDocument") {
        moveTo(5.5f, 4f); lineTo(14.5f, 4f); lineTo(18.5f, 8f); lineTo(18.5f, 20f); lineTo(5.5f, 20f); close()
        moveTo(14f, 4f); lineTo(14f, 8.5f); lineTo(18.5f, 8.5f)
        moveTo(8.5f, 12.8f); lineTo(15.5f, 12.8f)
        moveTo(8.5f, 16.2f); lineTo(13f, 16.2f)
    }

    val Share: ImageVector = strokeIcon("Share") {
        moveTo(12f, 3.8f); lineTo(12f, 15f)
        moveTo(7.8f, 8f); lineTo(12f, 3.8f); lineTo(16.2f, 8f)
        moveTo(5.2f, 11.5f); lineTo(5.2f, 19.8f); lineTo(18.8f, 19.8f); lineTo(18.8f, 11.5f)
    }

    val Globe: ImageVector = strokeIcon("Globe") {
        moveTo(3.5f, 12f); arcTo(8.5f, 8.5f, 0f, true, true, 20.5f, 12f); arcTo(8.5f, 8.5f, 0f, true, true, 3.5f, 12f)
        moveTo(3.5f, 12f); lineTo(20.5f, 12f)
        moveTo(12f, 3.5f); curveTo(15f, 7.5f, 15f, 16.5f, 12f, 20.5f); curveTo(9f, 16.5f, 9f, 7.5f, 12f, 3.5f)
    }

    val ThumbUp: ImageVector = strokeIcon("ThumbUp") {
        moveTo(8f, 10f); lineTo(12f, 3.5f); lineTo(14f, 3.5f); lineTo(14f, 9f)
        lineTo(19.8f, 9f); lineTo(19.8f, 13f); lineTo(17f, 20.5f); lineTo(8f, 20.5f); close()
        moveTo(3.5f, 10f); lineTo(6.2f, 10f); lineTo(6.2f, 20.5f); lineTo(3.5f, 20.5f); close()
    }

    val ThumbDown: ImageVector = strokeIcon("ThumbDown") {
        moveTo(8f, 14f); lineTo(12f, 20.5f); lineTo(14f, 20.5f); lineTo(14f, 15f)
        lineTo(19.8f, 15f); lineTo(19.8f, 11f); lineTo(17f, 3.5f); lineTo(8f, 3.5f); close()
        moveTo(3.5f, 14f); lineTo(6.2f, 14f); lineTo(6.2f, 3.5f); lineTo(3.5f, 3.5f); close()
    }
}
