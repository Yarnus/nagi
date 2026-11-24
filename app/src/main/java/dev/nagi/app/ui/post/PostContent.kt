package dev.nagi.app.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.TrackSlotImageReadiness
import dev.nagi.app.ui.components.formatAuthorDisplayName
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.sdk.ContentAlignment
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.MediaKind
import kotlin.math.roundToInt

private val MaxInlineImageHeight = 460.dp
private val InlineImagePlaceholderHeight = 140.dp
private val InlineImageFailedHeight = 120.dp

internal val LocalAllowHardwareImages = staticCompositionLocalOf { true }

@Composable
internal fun rememberPostImageModel(url: String, retryKey: Int = 0): Any {
    val allowHardware = LocalAllowHardwareImages.current
    if (allowHardware && retryKey == 0) return url
    val context = LocalContext.current
    return remember(context, url, allowHardware, retryKey) {
        ImageRequest.Builder(context)
            .data(url)
            .apply {
                if (!allowHardware) allowHardware(false)
                if (retryKey > 0) setParameter("retry", retryKey)
            }
            .build()
    }
}

@Composable
fun ContentBlocks(
    blocks: List<ContentBlock>,
    preferences: ReadingPreferences,
    onImage: (String) -> Unit,
) {
    RenderContentBlocks(blocks, preferences, onImage)
}

internal fun collectContentImages(blocks: List<ContentBlock>): List<String> {
    val result = LinkedHashSet<String>()
    fun walk(items: List<ContentBlock>) {
        items.forEach { block ->
            when (block) {
                is ContentBlock.Image -> result.add(block.url)
                is ContentBlock.Heading -> walk(block.children)
                is ContentBlock.Divider -> walk(block.children)
                is ContentBlock.Aligned -> walk(block.children)
                is ContentBlock.ListBlock -> block.items.forEach(::walk)
                is ContentBlock.Table -> block.rows.forEach { row -> row.forEach(::walk) }
                is ContentBlock.Collapse -> walk(block.children)
                is ContentBlock.Quote,
                is ContentBlock.Text,
                is ContentBlock.Code,
                is ContentBlock.Media,
                is ContentBlock.Dice,
                -> Unit
            }
        }
    }
    walk(blocks)
    return result.toList()
}

internal fun collectVisibleCardMediaUrls(blocks: List<ContentBlock>): List<String> {
    val result = LinkedHashSet<String>()
    fun walk(items: List<ContentBlock>) {
        items.forEach { block ->
            when (block) {
                is ContentBlock.Text -> block.smiles.forEach { result.add(it.smile.url) }
                is ContentBlock.Image -> result.add(block.url)
                is ContentBlock.Heading -> walk(block.children)
                is ContentBlock.Divider -> walk(block.children)
                is ContentBlock.Aligned -> walk(block.children)
                is ContentBlock.ListBlock -> block.items.forEach(::walk)
                is ContentBlock.Table -> block.rows.forEach { row -> row.forEach(::walk) }
                // Collapse blocks render closed on share cards, and Quote blocks render text-only summaries.
                is ContentBlock.Collapse,
                is ContentBlock.Quote,
                is ContentBlock.Code,
                is ContentBlock.Media,
                is ContentBlock.Dice,
                -> Unit
            }
        }
    }
    walk(blocks)
    return result.toList()
}

internal fun contentPlainText(blocks: List<ContentBlock>): String {
    fun render(items: List<ContentBlock>): String = items.mapNotNull { block ->
        when (block) {
            is ContentBlock.Text -> block.value.takeIf { it.isNotEmpty() }
            is ContentBlock.Heading -> render(block.children).takeIf { it.isNotBlank() }
            is ContentBlock.Divider -> {
                val title = render(block.children)
                if (title.isBlank()) "------" else "=== $title ==="
            }
            is ContentBlock.Aligned -> render(block.children).takeIf { it.isNotBlank() }
            is ContentBlock.ListBlock -> block.items.mapIndexedNotNull { index, item ->
                render(item).takeIf { it.isNotBlank() }?.let { body ->
                    if (block.ordered) "${index + 1}. $body" else "• $body"
                }
            }.joinToString("\n").takeIf { it.isNotBlank() }
            is ContentBlock.Code -> block.value.takeIf { it.isNotBlank() }
            is ContentBlock.Quote -> {
                val body = render(block.children)
                val header = block.author?.let { "引用 ${formatAuthorDisplayName(it)}：" } ?: "引用："
                if (body.isBlank()) null else "$header\n$body"
            }
            is ContentBlock.Table -> block.rows.joinToString("\n") { row ->
                row.joinToString(" | ") { cell -> render(cell).replace('\n', ' ') }
            }.takeIf { it.isNotBlank() }
            is ContentBlock.Collapse -> {
                val body = render(block.children)
                if (body.isBlank()) "[${block.title}]" else "[${block.title}]\n$body"
            }
            is ContentBlock.Image -> "[图片] ${block.url}"
            is ContentBlock.Media -> block.url
            is ContentBlock.Dice -> if (block.total == null) "[骰子 ${block.expression}]" else "[骰子 ${block.expression} = ${block.total}]"
        }
    }.joinToString("\n").trim()
    return render(blocks)
}

@Composable
private fun RenderContentBlocks(
    blocks: List<ContentBlock>,
    preferences: ReadingPreferences,
    onImage: (String) -> Unit,
    textAlign: TextAlign = TextAlign.Start,
    isHeading: Boolean = false,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val blockShape = RoundedCornerShape(12.dp)
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = when (textAlign) {
            TextAlign.Center -> Alignment.CenterHorizontally
            TextAlign.End -> Alignment.End
            else -> Alignment.Start
        },
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.Text -> PostParagraph(block, preferences, textAlign, isHeading)

                is ContentBlock.Heading -> Column(Modifier.fillMaxWidth().semantics { heading() }) {
                    RenderContentBlocks(block.children, preferences, onImage, textAlign, isHeading = true)
                    HorizontalDivider(Modifier.padding(top = Space.xs), color = colors.cardBorder)
                }

                is ContentBlock.Divider -> Column(Modifier.fillMaxWidth()) {
                    RenderContentBlocks(block.children, preferences, onImage, textAlign, isHeading = true)
                    HorizontalDivider(Modifier.padding(top = Space.xs), color = colors.cardBorder)
                }

                is ContentBlock.Aligned -> RenderContentBlocks(block.children, preferences, onImage,
                    when (block.alignment) {
                        ContentAlignment.START -> TextAlign.Start
                        ContentAlignment.CENTER -> TextAlign.Center
                        ContentAlignment.END -> TextAlign.End
                    }, isHeading)

                is ContentBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    block.items.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(if (block.ordered) "${index + 1}. " else "• ", color = colors.textPrimary,
                                style = typography.postBody.copy(fontSize = preferences.fontSize.sp))
                            Box(Modifier.weight(1f)) {
                                RenderContentBlocks(item, preferences, onImage, textAlign, isHeading)
                            }
                        }
                    }
                }

                is ContentBlock.Media -> {
                    val label = when (block.kind) {
                        MediaKind.AUDIO -> "打开音频"
                        MediaKind.VIDEO -> "打开视频"
                        MediaKind.ATTACHMENT -> "打开附件"
                    }
                    CapsuleButton(
                        text = "$label：${block.url.substringBefore('?').substringAfterLast('/').ifBlank { block.url }}",
                        onClick = { uriHandler.openUri(block.url) },
                        icon = NagiIcons.Link,
                        style = CapsuleStyle.Neutral,
                    )
                }

                is ContentBlock.Dice -> Text(
                    text = if (block.total == null) "骰子：${block.expression}（结果不可用）"
                        else "骰子：${block.expression} = ${block.total}" +
                            if (block.rolls.isEmpty()) "" else "（投掷 ${block.rolls.joinToString(" + ")}）",
                    color = colors.textPrimary,
                    style = typography.postBody.copy(fontSize = preferences.fontSize.sp),
                )

                is ContentBlock.Image -> PostImageBlock(block.url, onImage)

                is ContentBlock.Code -> FlatCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = blockShape,
                    containerColor = colors.insetContainer,
                    borderColor = colors.cardBorder,
                ) {
                    Text(
                        text = block.value,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(Space.md),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = colors.textPrimary,
                    )
                }

                is ContentBlock.Table -> FlatCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = blockShape,
                    containerColor = colors.insetContainer,
                    borderColor = colors.cardBorder,
                ) {
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        block.rows.forEach { cells ->
                            Row {
                                cells.forEach { cell ->
                                    Box(Modifier.width(160.dp).border(0.5.dp, colors.cardBorder).padding(Space.sm)) {
                                        RenderContentBlocks(cell, preferences, onImage, textAlign, isHeading)
                                    }
                                }
                            }
                        }
                    }
                }

                is ContentBlock.Quote -> ReplyReferenceCard(block, preferences, onImage)

                is ContentBlock.Collapse -> {
                    var open by rememberSaveable { mutableStateOf(false) }
                    FlatCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = blockShape,
                        containerColor = colors.insetContainer,
                        borderColor = colors.cardBorder,
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(role = Role.Button, onClick = { open = !open })
                                    .semantics { stateDescription = if (open) "已展开" else "已折叠" }
                                    .padding(horizontal = Space.md, vertical = Space.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = if (open) "收起：${block.title}" else "展开：${block.title}",
                                    modifier = Modifier.weight(1f),
                                    style = typography.capsuleLabel,
                                    color = colors.goldText,
                                )
                                Icon(
                                    imageVector = NagiIcons.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .graphicsLayer { rotationZ = if (open) 90f else 0f },
                                    tint = colors.goldText,
                                )
                            }
                            if (open) {
                                HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
                                Box(Modifier.padding(Space.md)) {
                                    RenderContentBlocks(block.children, preferences, onImage, textAlign, isHeading)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders an inline post image adapted to the container width while preserving aspect ratio
 * and capping overly tall images at [MaxInlineImageHeight] without cropping horizontal edges.
 */
@Composable
private fun PostImageBlock(
    url: String,
    onImage: (String) -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val density = LocalDensity.current
    val blockShape = RoundedCornerShape(12.dp)
    val candidates = remember(url) { imageFallbackCandidates(url) }
    var candidateIndex by remember(url) { mutableStateOf(0) }
    val activeUrl = candidates.getOrElse(candidateIndex) { url }
    var loaded by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    var intrinsicSize by remember(url) { mutableStateOf(Size.Unspecified) }
    var containerWidthPx by remember(url) { mutableIntStateOf(0) }
    var attempt by remember(url) { mutableStateOf(0) }
    val trackerToken = remember(url) { Any() }
    TrackSlotImageReadiness(token = trackerToken, settled = loaded || failed)

    val maxHeightPx = with(density) { MaxInlineImageHeight.roundToPx() }
    val placeholderHeightPx = with(density) { InlineImagePlaceholderHeight.roundToPx() }
    val failedHeightPx = with(density) { InlineImageFailedHeight.roundToPx() }
    val hasIntrinsic = intrinsicSize.isSpecified && intrinsicSize.width > 0f && intrinsicSize.height > 0f
    val isTall = loaded && !failed && hasIntrinsic && containerWidthPx > 0 &&
        (containerWidthPx * (intrinsicSize.height / intrinsicSize.width)).roundToInt() > maxHeightPx

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val width = constraints.maxWidth
                val desiredHeight = when {
                    failed -> failedHeightPx
                    !loaded -> placeholderHeightPx
                    hasIntrinsic && width != Constraints.Infinity && width > 0 ->
                        (width * (intrinsicSize.height / intrinsicSize.width)).roundToInt()
                            .coerceIn(1, maxHeightPx)
                    else -> placeholderHeightPx
                }
                val minH = constraints.minHeight
                val maxH = constraints.maxHeight.coerceAtLeast(minH)
                val clamped = desiredHeight.coerceIn(minH, maxH)
                val childConstraints = constraints.copy(
                    minWidth = if (width != Constraints.Infinity) width else constraints.minWidth,
                    maxWidth = width,
                    minHeight = clamped,
                    maxHeight = clamped,
                )
                val placeable = measurable.measure(childConstraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            }
            .onSizeChanged { containerWidthPx = it.width }
            .clip(blockShape)
            .background(colors.insetContainer)
            .border(1.dp, colors.cardBorder, blockShape),
    ) {
        if (failed) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClickLabel = "重试加载图片") {
                        candidateIndex = 0
                        failed = false
                        loaded = false
                        intrinsicSize = Size.Unspecified
                        attempt++
                    }
                    .padding(horizontal = Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.xs, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = NagiIcons.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.textSecondary,
                )
                Text(
                    text = "图片加载失败，点击重试",
                    style = typography.metadata,
                    color = colors.textSecondary,
                )
            }
        } else {
            AsyncImage(
                model = rememberPostImageModel(activeUrl, attempt),
                contentDescription = "帖子图片，点击查看",
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopCenter,
                onSuccess = { state ->
                    loaded = true
                    failed = false
                    intrinsicSize = state.painter.intrinsicSize
                },
                onError = { state ->
                    if (isHttp404Error(state.result.throwable) && candidateIndex + 1 < candidates.size) {
                        candidateIndex++
                    } else {
                        loaded = false
                        failed = true
                        intrinsicSize = Size.Unspecified
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onImage(url) },
            )
            if (isTall) {
                Text(
                    text = "长图",
                    style = typography.tagBadge,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Space.sm)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
