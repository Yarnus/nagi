package dev.nagi.app.ui.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.EdgeAlignedMetaRow
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.LocalOpenUser
import dev.nagi.app.ui.components.UserAvatar
import dev.nagi.app.ui.components.formatAuthorDisplayName
import dev.nagi.app.ui.theme.DarkSemanticColors
import dev.nagi.app.ui.theme.LightSemanticColors
import dev.nagi.app.ui.theme.LocalNagiColors
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.PaperSemanticColors
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.ui.topic.formatTimeText
import dev.nagi.sdk.Post

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PostShareCardSurface(
    topicId: String,
    topicTitle: String,
    post: Post,
    content: ShareCardContent,
    preferences: ReadingPreferences,
    cardTheme: ShareCardTheme = ShareCardTheme.LIGHT,
    modifier: Modifier = Modifier,
    earlyReplies: List<Post> = emptyList(),
) {
    val cardColors = when (cardTheme) {
        ShareCardTheme.LIGHT -> LightSemanticColors
        ShareCardTheme.DARK -> DarkSemanticColors
        ShareCardTheme.PAPER -> PaperSemanticColors
    }
    val typography = NagiThemeTokens.typography

    CompositionLocalProvider(
        LocalNagiColors provides cardColors,
        LocalAllowHardwareImages provides false,
        LocalPostReferences provides null,
        LocalOpenUser provides null,
        LocalShareCardMode provides true,
    ) {
        val canvasGradient = remember(cardTheme, cardColors) {
            val endColor = when (cardTheme) {
                ShareCardTheme.DARK -> Color(0xFF13110E)
                ShareCardTheme.PAPER -> Color(0xFFEDE5D6)
                ShareCardTheme.LIGHT -> Color(0xFFF3EFE8)
            }
            Brush.verticalGradient(listOf(cardColors.groupedCanvas, endColor))
        }
        val cardBorderBrush = remember(cardTheme, cardColors) {
            val startAlpha = when (cardTheme) {
                ShareCardTheme.DARK -> 0.50f
                ShareCardTheme.PAPER -> 0.50f
                ShareCardTheme.LIGHT -> 0.55f
            }
            val endAlpha = when (cardTheme) {
                ShareCardTheme.DARK -> 0.25f
                ShareCardTheme.PAPER -> 0.26f
                ShareCardTheme.LIGHT -> 0.30f
            }
            Brush.linearGradient(
                listOf(
                    cardColors.goldAccent.copy(alpha = startAlpha),
                    cardColors.cardBorder,
                    cardColors.goldAccent.copy(alpha = endAlpha),
                ),
            )
        }
        val innerHighlight = remember(cardTheme) {
            when (cardTheme) {
                ShareCardTheme.DARK -> Color.White.copy(alpha = 0.08f)
                ShareCardTheme.PAPER -> Color(0xFFFFFDF8).copy(alpha = 0.92f)
                ShareCardTheme.LIGHT -> Color.White.copy(alpha = 0.90f)
            }
        }
        val glowAlpha = when (cardTheme) {
            ShareCardTheme.DARK -> 0.10f
            ShareCardTheme.PAPER -> 0.15f
            ShareCardTheme.LIGHT -> 0.14f
        }

        Column(
            modifier = modifier
                .background(canvasGradient)
                .drawWithContent {
                    // Anchor radial glow to width so tall exported cards do not drift or over-tint.
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                cardColors.goldAccent.copy(alpha = glowAlpha),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.15f, size.width * 0.18f),
                            radius = size.width * 0.85f,
                        ),
                    )
                    drawContent()
                }
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            if (content.truncated) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.xs)
                        .padding(bottom = 10.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
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
                border = BorderStroke(1.dp, cardBorderBrush),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            drawContent()
                            val strokePx = 1.dp.toPx()
                            val inset = 20.dp.toPx()
                            if (size.width > inset * 2f) {
                                drawLine(
                                    color = innerHighlight,
                                    start = Offset(inset, strokePx),
                                    end = Offset(size.width - inset, strokePx),
                                    strokeWidth = strokePx,
                                )
                            }
                        }
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    if (topicTitle.isNotBlank()) {
                        Text(
                            text = topicTitle,
                            style = typography.sectionTitle.copy(
                                fontSize = 20.sp,
                                lineHeight = 27.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = cardColors.textPrimary,
                            modifier = Modifier.semantics { heading() },
                        )
                    }

                    EdgeAlignedMetaRow(
                        verticalAlignment = Alignment.CenterVertically,
                        leading = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            ) {
                                UserAvatar(
                                    name = post.author,
                                    avatarUrl = post.avatar,
                                    uid = post.authorId,
                                    size = 28.dp,
                                    colors = cardColors,
                                    imageModel = rememberPostImageModel(post.avatar),
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                                    itemVerticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = formatAuthorDisplayName(post.author),
                                        style = typography.bodyText.copy(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        color = cardColors.textPrimary,
                                    )
                                    GoldTagBadge(text = "楼主")
                                }
                            }
                        },
                        trailing = {
                            if (post.date.isNotBlank()) {
                                Text(
                                    text = formatTimeText(post.date),
                                    style = typography.metadata,
                                    color = cardColors.textSecondary,
                                )
                            }
                        },
                    )

                    HorizontalDivider(thickness = 0.5.dp, color = cardColors.cardBorder)

                    ContentBlocks(
                        blocks = content.blocks,
                        preferences = preferences,
                        onImage = {},
                    )

                    if (content.truncated) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(cardColors.goldContainer)
                                .border(0.5.dp, cardColors.goldBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = Space.md, vertical = Space.sm),
                        ) {
                            Text(
                                text = "内容已节选 · 完整内容请查看原帖",
                                style = typography.metadata,
                                color = cardColors.goldText,
                            )
                        }
                    }

                    if (content.includeExtras && post.comments.isNotEmpty()) {
                        ShareCardCommentsSection(
                            comments = post.comments,
                            preferences = preferences,
                        )
                    }

                    if (content.includeExtras && post.hotReplies.isNotEmpty()) {
                        ShareCardRepliesSection(
                            title = "热门回复",
                            replies = post.hotReplies,
                            preferences = preferences,
                            showScore = true,
                        )
                    }

                    if (content.includeExtras && earlyReplies.isNotEmpty()) {
                        ShareCardRepliesSection(
                            title = "前排回复",
                            replies = earlyReplies,
                            preferences = preferences,
                            showFloor = true,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.xs)
                    .padding(top = 16.dp),
            ) {
                EdgeAlignedMetaRow(
                    verticalAlignment = Alignment.CenterVertically,
                    leading = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = cardColors.cardSurface,
                                border = BorderStroke(1.dp, cardColors.goldBorder),
                                shadowElevation = 0.dp,
                                tonalElevation = 0.dp,
                            ) {
                                Box(
                                    modifier = Modifier.size(28.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    NagiMonogram(
                                        color = cardColors.goldAccent,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = "Nagi",
                                    style = typography.bodyText.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.2.sp,
                                    ),
                                    color = cardColors.textPrimary,
                                )
                                Text(
                                    text = "NGA · tid $topicId",
                                    style = typography.metadata.copy(fontSize = 11.5.sp),
                                    color = cardColors.textSecondary,
                                )
                            }
                        }
                    },
                    trailing = {
                        ShareCardQrBadge(
                            url = "https://bbs.nga.cn/read.php?tid=$topicId",
                            cardTheme = cardTheme,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ShareCardQrBadge(
    url: String,
    cardTheme: ShareCardTheme,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(url) { QrCodeEncoder.encode(url) } ?: return
    val colors = NagiThemeTokens.colors
    val badgeBg = when (cardTheme) {
        ShareCardTheme.LIGHT -> LightSemanticColors.cardSurface
        ShareCardTheme.PAPER -> PaperSemanticColors.cardSurface
        ShareCardTheme.DARK -> DarkSemanticColors.goldSolidFill
    }
    val moduleColor = LightSemanticColors.textPrimary

    Surface(
        modifier = modifier
            .size(44.dp)
            .testTag("share-card-qr")
            .semantics { contentDescription = "原帖二维码" },
        shape = RoundedCornerShape(8.dp),
        color = badgeBg,
        border = BorderStroke(0.5.dp, colors.cardBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Canvas(
            modifier = Modifier
                .size(44.dp)
                .padding(4.dp),
        ) {
            val quietModules = 2
            val total = matrix.size + quietModules * 2
            val cellSize = size.minDimension / total.toFloat()
            val offsetX = (size.width - cellSize * total) / 2f
            val offsetY = (size.height - cellSize * total) / 2f
            for (y in 0 until matrix.size) {
                val y0 = (offsetY + (y + quietModules) * cellSize).toInt().toFloat()
                val y1 = (offsetY + (y + quietModules + 1) * cellSize).toInt().toFloat()
                for (x in 0 until matrix.size) {
                    if (matrix[x, y]) {
                        val x0 = (offsetX + (x + quietModules) * cellSize).toInt().toFloat()
                        val x1 = (offsetX + (x + quietModules + 1) * cellSize).toInt().toFloat()
                        drawRect(
                            color = moduleColor,
                            topLeft = Offset(x0, y0),
                            size = Size(x1 - x0, y1 - y0),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareCardCommentsSection(
    comments: List<Post>,
    preferences: ReadingPreferences,
    nested: Boolean = false,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val content: @Composable (Modifier) -> Unit = { containerModifier ->
        Column(
            modifier = containerModifier,
            verticalArrangement = Arrangement.spacedBy(if (nested) Space.xs else Space.sm),
        ) {
            Text(
                text = "贴条 · ${comments.size}",
                style = typography.metadata.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textSecondary,
            )
            comments.forEachIndexed { index, comment ->
                if (!nested && index > 0) {
                    HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.6f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EdgeAlignedMetaRow(
                        leading = {
                            Text(
                                text = formatAuthorDisplayName(comment.author),
                                style = typography.metadata.copy(fontWeight = FontWeight.Medium),
                                color = colors.textPrimary,
                            )
                        },
                        trailing = {
                            if (comment.date.isNotBlank()) {
                                Text(
                                    text = formatTimeText(comment.date),
                                    style = typography.metadata,
                                    color = colors.textSecondary,
                                )
                            }
                        },
                    )
                    ContentBlocks(
                        blocks = comment.content,
                        preferences = preferences,
                        onImage = {},
                    )
                }
            }
        }
    }

    if (nested) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(top = Space.xxs),
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.dp))
                    .background(colors.goldAccent.copy(alpha = 0.48f)),
            )
            content(
                Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            )
        }
    } else {
        content(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.insetContainer)
                .padding(Space.md),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareCardRepliesSection(
    title: String,
    replies: List<Post>,
    preferences: ReadingPreferences,
    showScore: Boolean = false,
    showFloor: Boolean = false,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.xs),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = colors.cardBorder)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.goldAccent),
            )
            Text(
                text = title,
                style = typography.metadata.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimary,
            )
            Text(
                text = "· ${replies.size}",
                style = typography.metadata,
                color = colors.textSecondary,
            )
        }
        replies.forEach { reply ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.insetContainer)
                    .padding(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                EdgeAlignedMetaRow(
                    verticalAlignment = Alignment.CenterVertically,
                    leading = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            UserAvatar(
                                name = reply.author,
                                avatarUrl = reply.avatar,
                                uid = reply.authorId,
                                size = 24.dp,
                                colors = colors,
                                imageModel = rememberPostImageModel(reply.avatar),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                                verticalArrangement = Arrangement.spacedBy(Space.xs),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = formatAuthorDisplayName(reply.author),
                                    style = typography.metadata.copy(fontWeight = FontWeight.Medium),
                                    color = colors.textPrimary,
                                )
                                if (showScore && reply.score > 0) {
                                    GoldTagBadge(text = "赞 ${reply.score}")
                                }
                            }
                        }
                    },
                    trailing = {
                        val meta = buildList {
                            if (showFloor) add("#${reply.floor}")
                            if (reply.date.isNotBlank()) add(formatTimeText(reply.date))
                        }.joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = typography.metadata,
                                color = colors.textSecondary,
                            )
                        }
                    },
                )
                ContentBlocks(
                    blocks = reply.content,
                    preferences = preferences,
                    onImage = {},
                )
                if (reply.comments.isNotEmpty()) {
                    ShareCardCommentsSection(
                        comments = reply.comments,
                        preferences = preferences,
                        nested = true,
                    )
                }
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
