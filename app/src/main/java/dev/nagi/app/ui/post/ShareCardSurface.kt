package dev.nagi.app.ui.post

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
    darkCard: Boolean,
    modifier: Modifier = Modifier,
    earlyReplies: List<Post> = emptyList(),
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
                                            text = formatAuthorDisplayName(post.author),
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

@Composable
private fun ShareCardCommentsSection(
    comments: List<Post>,
    preferences: ReadingPreferences,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.insetContainer)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(
            text = "贴条 · ${comments.size}",
            style = typography.metadata.copy(fontWeight = FontWeight.Medium),
            color = colors.textSecondary,
        )
        comments.forEachIndexed { index, comment ->
            if (index > 0) {
                HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.6f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        HorizontalDivider(color = colors.cardBorder)
        Text(
            text = title,
            style = typography.metadata.copy(fontWeight = FontWeight.SemiBold),
            color = colors.goldText,
        )
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
                                size = 22.dp,
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
