package dev.nagi.app.ui.post

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Picture
import androidx.core.graphics.createBitmap
import dev.nagi.sdk.ContentBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class ShareCardTheme(val label: String) {
    LIGHT("浅色卡片"),
    DARK("深色卡片"),
    PAPER("暖纸卡片");

    fun next(): ShareCardTheme = when (this) {
        LIGHT -> DARK
        DARK -> PAPER
        PAPER -> LIGHT
    }
}

internal const val SHARE_CARD_EXPORT_DENSITY = 3.0f
internal const val SHARE_CARD_EXPORT_WIDTH_PX = 1200
internal const val MAX_CARD_EXPORT_HEIGHT_PX = 16000
internal const val SHARE_CARD_IMAGE_WAIT_TIMEOUT_MS = 3_000L
private const val SHARE_CARD_JPEG_QUALITY = 96
private const val MAX_CARD_EXPORT_DIMENSION_PX = 16384
private const val MAX_CARD_EXPORT_PIXELS = 24_000_000L

internal data class ShareCardContent(
    val blocks: List<ContentBlock>,
    val truncated: Boolean,
    val includeExtras: Boolean = true,
)

private data class BlockStats(
    val chars: Int,
    val images: Int,
)

private fun measureBlock(block: ContentBlock): BlockStats = when (block) {
    is ContentBlock.Text -> BlockStats(chars = block.value.length, images = 0)
    is ContentBlock.Code -> BlockStats(chars = block.value.length, images = 0)
    is ContentBlock.Image -> BlockStats(chars = 0, images = 1)
    is ContentBlock.Media -> BlockStats(chars = block.url.length.coerceAtMost(48), images = 0)
    is ContentBlock.Dice -> BlockStats(chars = block.expression.length + 12, images = 0)
    is ContentBlock.Collapse -> BlockStats(chars = block.title.length + 6, images = 0)
    is ContentBlock.Quote -> BlockStats(
        chars = referencePreview(block.children).length + (block.author?.length ?: 0) + 8,
        images = 0,
    )
    is ContentBlock.Heading -> measureBlocks(block.children)
    is ContentBlock.Divider -> measureBlocks(block.children)
    is ContentBlock.Aligned -> measureBlocks(block.children)
    is ContentBlock.Styled -> measureBlocks(block.children)
    is ContentBlock.ListBlock -> block.items.fold(BlockStats(0, 0)) { acc, item ->
        val stats = measureBlocks(item)
        BlockStats(acc.chars + stats.chars, acc.images + stats.images)
    }
    is ContentBlock.Table -> block.rows.fold(BlockStats(0, 0)) { acc, row ->
        row.fold(acc) { rowAcc, cell ->
            val stats = measureBlocks(cell.content)
            BlockStats(rowAcc.chars + stats.chars, rowAcc.images + stats.images)
        }
    }
}

private fun measureBlocks(blocks: List<ContentBlock>): BlockStats =
    blocks.fold(BlockStats(0, 0)) { acc, block ->
        val stats = measureBlock(block)
        BlockStats(acc.chars + stats.chars, acc.images + stats.images)
    }

private fun truncateTextBlock(block: ContentBlock.Text, maxChars: Int): ContentBlock.Text? {
    if (maxChars <= 0 || block.value.isEmpty()) return null
    var cut = maxChars.coerceAtMost(block.value.length)
    block.smiles.forEach { smile ->
        if (cut in (smile.start + 1) until smile.end) {
            cut = smile.start
        }
    }
    if (cut <= 0) return null
    val trimmedText = block.value.substring(0, cut).trimEnd() + "…"
    val trimmedSmiles = block.smiles.filter { it.end <= cut }
    val trimmedSpans = block.spans.mapNotNull { span ->
        if (span.start >= cut) null
        else span.copy(end = span.end.coerceAtMost(cut))
    }
    return ContentBlock.Text(trimmedText, trimmedSpans, trimmedSmiles)
}

internal fun trimShareCardBlocks(
    blocks: List<ContentBlock>,
    maxBlocks: Int = 100,
    maxChars: Int = 8000,
    maxImages: Int = 12,
): ShareCardContent {
    if (blocks.isEmpty()) return ShareCardContent(emptyList(), truncated = false)
    val result = mutableListOf<ContentBlock>()
    var usedChars = 0
    var usedImages = 0
    var truncated = false
    for (block in blocks) {
        if (result.size >= maxBlocks) {
            truncated = true
            break
        }
        val stats = measureBlock(block)
        if (usedImages + stats.images > maxImages && result.isNotEmpty()) {
            truncated = true
            break
        }
        val remainingChars = maxChars - usedChars
        if (stats.chars > remainingChars) {
            if (block is ContentBlock.Text && remainingChars >= 120) {
                truncateTextBlock(block, remainingChars)?.let { result += it }
            } else if (result.isEmpty() && block is ContentBlock.Text) {
                truncateTextBlock(block, maxChars.coerceAtLeast(120))?.let { result += it }
            }
            truncated = true
            break
        }
        result += block
        usedChars += stats.chars
        usedImages += stats.images
    }
    return ShareCardContent(result, truncated)
}

internal class ShareCardPictureRecorder {
    @Volatile
    var picture: Picture? = null
}

internal suspend fun exportShareCardImage(
    context: Context,
    topicId: String,
    recorder: ShareCardPictureRecorder,
) {
    val picture = recorder.picture
    check(picture != null && picture.width > 0 && picture.height > 0) { "Card has not been drawn yet" }
    check(picture.width <= MAX_CARD_EXPORT_DIMENSION_PX && picture.height <= MAX_CARD_EXPORT_DIMENSION_PX) {
        "Card exceeds maximum supported image dimension"
    }
    check(picture.width.toLong() * picture.height.toLong() <= MAX_CARD_EXPORT_PIXELS) {
        "Card exceeds maximum supported pixel budget"
    }
    val uri = withContext(Dispatchers.Default) {
        val bitmap = createBitmap(picture.width, picture.height)
        try {
            android.graphics.Canvas(bitmap).drawPicture(picture)
            prepareShareImageUri(context, "nagi_card_${topicId}_${System.currentTimeMillis()}.jpg") { file ->
                file.outputStream().buffered().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, SHARE_CARD_JPEG_QUALITY, output)) {
                        "Failed to encode share card JPEG"
                    }
                }
            }
        } finally {
            bitmap.recycle()
        }
    }
    launchShareImageIntent(context, uri, "image/jpeg", "分享主楼卡片")
}


internal fun fitShareCardByHeight(
    blocks: List<ContentBlock>,
    fits: (ShareCardContent) -> Boolean,
): ShareCardContent {
    if (!fits(ShareCardContent(emptyList(), truncated = true, includeExtras = true))) {
        return fitBlocksForHeight(blocks) { blocksCandidate ->
            fits(ShareCardContent(blocksCandidate, truncated = true, includeExtras = false))
        }.copy(includeExtras = false)
    }
    return fitBlocksForHeight(blocks) { blocksCandidate ->
        fits(ShareCardContent(blocksCandidate, truncated = true, includeExtras = true))
    }
}

private fun fitBlocksForHeight(
    blocks: List<ContentBlock>,
    fits: (List<ContentBlock>) -> Boolean,
): ShareCardContent {
    if (blocks.isEmpty()) return ShareCardContent(emptyList(), truncated = true)

    var low = 0
    var high = blocks.size
    var bestBlockCount = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (fits(blocks.take(mid))) {
            bestBlockCount = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    val prefix = blocks.take(bestBlockCount)
    val boundary = blocks.getOrNull(bestBlockCount)
    if (boundary is ContentBlock.Text && boundary.value.isNotEmpty()) {
        var charLow = 1
        var charHigh = boundary.value.length
        var bestTextBlock: ContentBlock.Text? = null
        while (charLow <= charHigh) {
            val midChars = (charLow + charHigh) ushr 1
            val trimmedBoundary = truncateTextBlock(boundary, midChars)
            if (trimmedBoundary != null && fits(prefix + trimmedBoundary)) {
                bestTextBlock = trimmedBoundary
                charLow = midChars + 1
            } else {
                charHigh = midChars - 1
            }
        }
        if (bestTextBlock != null) {
            return ShareCardContent(prefix + bestTextBlock, truncated = true)
        }
    }

    return ShareCardContent(prefix, truncated = true)
}

