package dev.nagi.app.ui.post

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.EdgeAlignedMetaRow
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.LocalOpenUser
import dev.nagi.app.ui.components.NagiDialog
import dev.nagi.app.ui.components.NagiDialogActions
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.UserAvatar
import dev.nagi.app.ui.theme.DarkSemanticColors
import dev.nagi.app.ui.theme.LightSemanticColors
import dev.nagi.app.ui.theme.LocalNagiColors
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.ui.topic.formatTimeText
import dev.nagi.app.viewmodel.MainPostShareState
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.Post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

private const val MAX_CARD_EXPORT_DIMENSION_PX = 16384
private const val MAX_CARD_EXPORT_PIXELS = 24_000_000L

internal data class ShareCardContent(
    val blocks: List<ContentBlock>,
    val truncated: Boolean,
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
    is ContentBlock.ListBlock -> block.items.fold(BlockStats(0, 0)) { acc, item ->
        val stats = measureBlocks(item)
        BlockStats(acc.chars + stats.chars, acc.images + stats.images)
    }
    is ContentBlock.Table -> block.rows.fold(BlockStats(0, 0)) { acc, row ->
        row.fold(acc) { rowAcc, cell ->
            val stats = measureBlocks(cell)
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
    maxBlocks: Int = 36,
    maxChars: Int = 3200,
    maxImages: Int = 6,
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

internal fun Bitmap.toSoftwareArgb8888(): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE) {
        return copy(Bitmap.Config.ARGB_8888, false)
            ?: error("Unable to convert hardware bitmap to software ARGB_8888")
    }
    return this
}

internal suspend fun exportShareCardPng(
    context: Context,
    topicId: String,
    graphicsLayer: GraphicsLayer,
) {
    val size = graphicsLayer.size
    check(size.width > 0 && size.height > 0) { "Card has not been drawn yet" }
    check(size.width <= MAX_CARD_EXPORT_DIMENSION_PX && size.height <= MAX_CARD_EXPORT_DIMENSION_PX) {
        "Card exceeds maximum supported image dimension"
    }
    check(size.width.toLong() * size.height.toLong() <= MAX_CARD_EXPORT_PIXELS) {
        "Card exceeds maximum supported pixel budget"
    }
    val rawBitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
    val softwareBitmap = withContext(Dispatchers.Default) { rawBitmap.toSoftwareArgb8888() }
    val uri = try {
        prepareShareImageUri(context, "nagi_card_${topicId}_${System.currentTimeMillis()}.png") { file ->
            file.outputStream().buffered().use { output ->
                check(softwareBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Failed to encode share card PNG"
                }
            }
        }
    } finally {
        if (softwareBitmap !== rawBitmap) {
            softwareBitmap.recycle()
        }
    }
    launchShareImageIntent(context, uri, "image/png", "分享主楼卡片")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PostShareCardDialog(
    topicId: String,
    topicTitle: String,
    post: Post?,
    shareState: MainPostShareState,
    preferences: ReadingPreferences,
    onRetry: () -> Unit,
    close: () -> Unit,
) {
    val context = LocalContext.current
    val currentColors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    var cardDark by rememberSaveable { mutableStateOf(currentColors.isDark) }
    var exporting by remember { mutableStateOf(false) }
    var pendingImages by remember { mutableIntStateOf(0) }

    val trimmed = remember(post?.id, post?.content) {
        post?.let { trimShareCardBlocks(it.content) } ?: ShareCardContent(emptyList(), false)
    }

    LaunchedEffect(post?.id, trimmed.blocks) {
        if (post == null) {
            pendingImages = 0
            return@LaunchedEffect
        }
        val urls = buildList {
            if (post.avatar.isNotBlank()) add(post.avatar)
            addAll(collectVisibleCardMediaUrls(trimmed.blocks))
        }.distinct()
        pendingImages = urls.size
        if (urls.isEmpty()) return@LaunchedEffect
        val loader = context.imageLoader
        val remaining = AtomicInteger(urls.size)
        urls.map { url ->
            async {
                withContext(Dispatchers.IO) {
                    runCatching {
                        loader.execute(
                            ImageRequest.Builder(context)
                                .data(url)
                                .allowHardware(false)
                                .build(),
                        )
                    }
                }
                pendingImages = remaining.decrementAndGet().coerceAtLeast(0)
            }
        }.awaitAll()
    }

    NagiDialog(
        onDismissRequest = { if (!exporting) close() },
        modifier = Modifier.testTag("share-card-dialog"),
        maxWidth = 440.dp,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Space.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "分享主楼卡片",
                style = typography.sectionTitle,
                color = currentColors.textPrimary,
            )
            if (post != null) {
                CapsuleButton(
                    text = if (cardDark) "深色卡片" else "浅色卡片",
                    onClick = { cardDark = !cardDark },
                    modifier = Modifier.testTag("share-card-theme-toggle"),
                    icon = NagiIcons.Tuning,
                    style = CapsuleStyle.Neutral,
                    enabled = !exporting,
                    stateDescription = if (cardDark) "已选深色，点击切换为浅色" else "已选浅色，点击切换为深色",
                )
            }
        }

        when {
            shareState.loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = currentColors.goldAccent,
                    )
                    Text(
                        text = "正在加载主楼内容…",
                        style = typography.metadata,
                        color = currentColors.textSecondary,
                    )
                }
            }

            post == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Text(
                        text = shareState.error ?: "无法加载主楼内容",
                        style = typography.body,
                        color = currentColors.textSecondary,
                    )
                    CapsuleButton(
                        text = "重试",
                        onClick = onRetry,
                        style = CapsuleStyle.SelectedGold,
                    )
                }
            }

            else -> {
                if (pendingImages > 0) {
                    Text(
                        text = "正在准备图片…",
                        style = typography.metadata,
                        color = currentColors.textSecondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(16.dp))
                        .verticalScroll(rememberScrollState()),
                ) {
                    PostShareCardSurface(
                        topicId = topicId,
                        topicTitle = topicTitle,
                        post = post,
                        content = trimmed,
                        preferences = preferences,
                        darkCard = cardDark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share-card-canvas")
                            .drawWithContent {
                                graphicsLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                                drawLayer(graphicsLayer)
                            },
                    )
                }
            }
        }

        if (post != null && !shareState.loading) {
            NagiDialogActions(
                confirmLabel = if (exporting) "生成中…" else "分享 PNG",
                onConfirm = {
                    if (exporting) return@NagiDialogActions
                    exporting = true
                    scope.launch {
                        try {
                            exportShareCardPng(context, topicId, graphicsLayer)
                        } catch (_: Throwable) {
                            Toast.makeText(context, "卡片内容过长或生成失败", Toast.LENGTH_SHORT).show()
                        } finally {
                            exporting = false
                        }
                    }
                },
                confirmStyle = CapsuleStyle.SolidGold,
                confirmEnabled = !exporting,
                dismissLabel = "关闭",
                onDismiss = close,
                dismissEnabled = !exporting,
            )
        } else {
            NagiDialogActions(
                confirmLabel = "关闭",
                onConfirm = close,
                confirmStyle = CapsuleStyle.Neutral,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PostShareCardSurface(
    topicId: String,
    topicTitle: String,
    post: Post,
    content: ShareCardContent,
    preferences: ReadingPreferences,
    darkCard: Boolean,
    modifier: Modifier = Modifier,
) {
    val cardColors = if (darkCard) DarkSemanticColors else LightSemanticColors
    val typography = NagiThemeTokens.typography
    val hasAuthorStats = !post.authorReputation.isNullOrBlank() || post.authorPosts != null

    CompositionLocalProvider(
        LocalNagiColors provides cardColors,
        LocalAllowHardwareImages provides false,
        LocalPostReferences provides null,
        LocalOpenUser provides null,
    ) {
        val canvasGradient = remember(darkCard, cardColors) {
            if (darkCard) {
                Brush.verticalGradient(listOf(cardColors.groupedCanvas, Color(0xFF13110E)))
            } else {
                Brush.verticalGradient(listOf(cardColors.groupedCanvas, Color(0xFFF3EFE8)))
            }
        }
        val cardBorderBrush = remember(darkCard, cardColors) {
            Brush.linearGradient(
                listOf(
                    cardColors.goldAccent.copy(alpha = if (darkCard) 0.50f else 0.55f),
                    cardColors.cardBorder,
                    cardColors.goldAccent.copy(alpha = if (darkCard) 0.25f else 0.30f),
                ),
            )
        }

        Column(
            modifier = modifier
                .background(canvasGradient)
                .drawWithContent {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                cardColors.goldAccent.copy(alpha = if (darkCard) 0.10f else 0.14f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.15f, size.height * 0.08f),
                            radius = size.maxDimension * 0.5f,
                        ),
                    )
                    drawContent()
                }
                .padding(horizontal = Space.lg, vertical = 20.dp),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.xs)
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Space.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = cardColors.cardSurface,
                        border = BorderStroke(1.dp, cardColors.goldBorder),
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                    ) {
                        Box(
                            modifier = Modifier.size(26.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            NagiMonogram(
                                color = cardColors.goldAccent,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Text(
                        text = "Nagi",
                        style = typography.bodyText.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp,
                        ),
                        color = cardColors.textPrimary,
                    )
                }
                if (content.truncated) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = cardColors.goldContainer,
                        border = BorderStroke(1.dp, cardColors.goldBorder),
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = "已节选",
                            style = typography.metadata.copy(fontWeight = FontWeight.Medium),
                            color = cardColors.goldText,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = cardColors.cardSurface,
                border = BorderStroke(1.5.dp, cardBorderBrush),
                shadowElevation = if (darkCard) 8.dp else 6.dp,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    EdgeAlignedMetaRow(
                        verticalAlignment = Alignment.Top,
                        leading = {
                            Row(verticalAlignment = Alignment.Top) {
                                UserAvatar(
                                    name = post.author,
                                    avatarUrl = post.avatar,
                                    uid = post.authorId,
                                    size = 32.dp,
                                    colors = cardColors,
                                    imageModel = rememberPostImageModel(post.avatar),
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = Space.md),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                                        itemVerticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = post.author,
                                            style = typography.bodyText.copy(fontWeight = FontWeight.Medium),
                                            color = cardColors.textPrimary,
                                        )
                                        GoldTagBadge(text = "楼主")
                                    }
                                    if (hasAuthorStats) {
                                        PostAuthorMetaStats(
                                            reputation = post.authorReputation,
                                            posts = post.authorPosts,
                                        )
                                    }
                                }
                            }
                        },
                        trailing = {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                if (post.date.isNotBlank()) {
                                    Text(
                                        text = formatTimeText(post.date),
                                        style = typography.metadata,
                                        color = cardColors.textSecondary,
                                    )
                                }
                                Text(
                                    text = "#${post.floor}",
                                    style = typography.metadata,
                                    color = cardColors.textSecondary,
                                )
                            }
                        },
                    )

                    if (topicTitle.isNotBlank()) {
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            text = topicTitle,
                            style = typography.sectionTitle.copy(
                                fontSize = 18.5.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = cardColors.textPrimary,
                            modifier = Modifier.semantics { heading() },
                        )
                    }

                    ContentBlocks(
                        blocks = content.blocks,
                        preferences = preferences,
                        onImage = {},
                    )

                    if (content.truncated) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardColors.insetContainer)
                                .padding(horizontal = Space.md, vertical = Space.sm),
                        ) {
                            Text(
                                text = "内容已节选 · 完整内容请查看原帖",
                                style = typography.metadata,
                                color = cardColors.textSecondary,
                            )
                        }
                    }
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.xs)
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Space.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "NGA · tid $topicId",
                    style = typography.metadata,
                    color = cardColors.textSecondary,
                )
                NagiMonogram(
                    color = cardColors.goldText,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/** Minimal geometric N monogram matching ic_nagi.xml, tinted with the active card theme. */
@Composable
private fun NagiMonogram(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.14f
        val inset = strokeWidth / 2f + size.minDimension * 0.06f
        val left = inset
        val right = size.width - inset
        val top = inset
        val bottom = size.height - inset
        val path = Path().apply {
            moveTo(left, bottom)
            lineTo(left, top)
            lineTo(right, bottom)
            lineTo(right, top)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
