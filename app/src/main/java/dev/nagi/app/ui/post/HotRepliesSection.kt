package dev.nagi.app.ui.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.EdgeAlignedMetaRow
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.LocalOpenUser
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.UserAvatar
import dev.nagi.app.ui.components.formatAuthorDisplayName
import dev.nagi.app.ui.components.UserTagBadge
import dev.nagi.app.ui.components.isPublicUserId
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.InlineSmile
import dev.nagi.sdk.Post

internal data class HotReplyPreview(
    val text: String,
    val thumbnailUrl: String?,
    val smiles: List<InlineSmile> = emptyList(),
)

private val ValidPostIdRegex = Regex("[1-9][0-9]*")

internal fun extractHotReplyPreview(blocks: List<ContentBlock>): HotReplyPreview {
    var firstImage: String? = null
    val out = StringBuilder()
    val outSmiles = mutableListOf<InlineSmile>()

    fun appendPlain(value: String) {
        if (value.isBlank()) return
        if (out.isNotEmpty()) out.append('\n')
        out.append(value.trim())
    }

    fun appendTextBlock(block: ContentBlock.Text) {
        if (block.value.isBlank() && block.smiles.isEmpty()) return
        if (out.isNotEmpty()) out.append('\n')
        val baseOffset = out.length
        out.append(block.value)
        block.smiles.forEach { smile ->
            if (smile.start in 0..block.value.length && smile.end in smile.start..block.value.length) {
                outSmiles += InlineSmile(baseOffset + smile.start, baseOffset + smile.end, smile.smile)
            }
        }
    }

    fun walk(items: List<ContentBlock>) {
        items.forEach { block ->
            when (block) {
                is ContentBlock.Text -> appendTextBlock(block)
                is ContentBlock.Image -> if (firstImage == null) firstImage = block.url
                is ContentBlock.Quote -> Unit
                is ContentBlock.Heading -> walk(block.children)
                is ContentBlock.Divider -> walk(block.children)
                is ContentBlock.Aligned -> walk(block.children)
                is ContentBlock.Styled -> walk(block.children)
                is ContentBlock.ListBlock -> block.items.forEach(::walk)
                is ContentBlock.Code -> appendPlain(block.value)
                is ContentBlock.Table -> appendPlain("[表格]")
                is ContentBlock.Collapse -> appendPlain("[${block.title}]")
                is ContentBlock.Media -> appendPlain("[附件]")
                is ContentBlock.Dice -> appendPlain(if (block.total == null) "[骰子]" else "[骰子 ${block.total}]")
            }
        }
    }

    walk(blocks)
    val rawText = out.toString()
    val leadingTrim = rawText.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) rawText.length else it }
    val trimmedText = rawText.trim()
    val adjustedSmiles = if (trimmedText.isEmpty()) {
        emptyList()
    } else {
        outSmiles.mapNotNull { smile ->
            val start = smile.start - leadingTrim
            val end = smile.end - leadingTrim
            if (start >= 0 && end <= trimmedText.length) InlineSmile(start, end, smile.smile) else null
        }
    }
    val displayText = when {
        trimmedText.isNotEmpty() -> trimmedText
        firstImage != null -> "[图片]"
        blocks.any { it is ContentBlock.Quote } -> "[引用内容]"
        else -> ""
    }
    return HotReplyPreview(text = displayText, thumbnailUrl = firstImage, smiles = adjustedSmiles)
}

@Composable
internal fun HotRepliesSection(
    replies: List<Post>,
    topicAuthorId: String,
    preferences: ReadingPreferences,
    onLocate: ((Post) -> Unit)?,
) {
    if (replies.isEmpty()) return
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.md, bottom = Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.cardBorder.copy(alpha = 0.55f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.goldAccent),
            )
            Text(
                text = "热点回复",
                style = typography.sectionHeader,
                color = colors.textSecondary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "· ${replies.size}",
                style = typography.metaTabular,
                color = colors.textSecondary,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 520.dp && replies.size > 1) 2 else 1
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                replies.chunked(columns).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        rowItems.forEach { reply ->
                            Box(Modifier.weight(1f)) {
                                HotReplyCard(
                                    reply = reply,
                                    isTopicAuthor = topicAuthorId.isNotBlank() && reply.authorId == topicAuthorId,
                                    preferences = preferences,
                                    onLocate = onLocate,
                                )
                            }
                        }
                        if (columns == 2 && rowItems.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HotReplyCard(
    reply: Post,
    isTopicAuthor: Boolean,
    preferences: ReadingPreferences,
    onLocate: ((Post) -> Unit)?,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val openUser = LocalOpenUser.current
    val canOpenUser = openUser != null && isPublicUserId(reply.authorId)
    val canLocate = onLocate != null && ValidPostIdRegex.matches(reply.id)
    val preview = remember(reply.id, reply.content) { extractHotReplyPreview(reply.content) }
    val previewSize = (preferences.fontSize - 1).coerceAtLeast(13)
    val previewStyle = typography.bodyText.copy(
        fontSize = previewSize.sp,
        lineHeight = (previewSize + 6).sp,
    )

    val authorName = formatAuthorDisplayName(reply.author)
    val cardSummary = buildString {
        append(authorName)
        if (reply.score != 0) append("，赞 ${reply.score}")
        if (reply.floor > 0) append("，第 ${reply.floor} 楼")
        if (preview.text.isNotBlank()) append("：${preview.text}")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.insetContainer,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(
                    if (canLocate) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = "跳转到原楼层",
                        ) {
                            onLocate?.invoke(reply)
                        }
                    } else {
                        Modifier
                    }
                )
                .semantics { contentDescription = cardSummary }
                .padding(
                    start = Space.md,
                    end = Space.md,
                    top = if (canOpenUser) 0.dp else Space.sm,
                    bottom = Space.sm,
                ),
            verticalArrangement = Arrangement.spacedBy(if (canOpenUser) 2.dp else Space.xs),
        ) {
            EdgeAlignedMetaRow(
                leading = {
                    Row(
                        modifier = if (canOpenUser) {
                            Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable(onClickLabel = "查看用户详情") { openUser?.invoke(reply.authorId) }
                        } else {
                            Modifier
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        UserAvatar(
                            name = reply.author,
                            avatarUrl = reply.avatar,
                            uid = reply.authorId,
                            size = 22.dp,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                            verticalArrangement = Arrangement.spacedBy(Space.xxs),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = authorName,
                                style = typography.capsuleLabel.copy(fontWeight = FontWeight.Medium),
                                color = colors.textPrimary,
                            )
                            if (isTopicAuthor) {
                                GoldTagBadge(text = "楼主")
                            }
                            UserTagBadge(reply.authorId)
                        }
                    }
                },
                trailing = {
                    if (reply.score != 0) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = colors.goldContainer,
                            shadowElevation = 0.dp,
                            tonalElevation = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "赞 ${reply.score}"
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = NagiIcons.ThumbUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = colors.goldText,
                                )
                                Text(
                                    text = reply.score.toString(),
                                    style = typography.metaTabular.copy(fontWeight = FontWeight.Medium),
                                    color = colors.goldText,
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.size(0.dp))
                    }
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.Top,
            ) {
                if (preview.thumbnailUrl != null) {
                    val thumbShape = RoundedCornerShape(8.dp)
                    AsyncImage(
                        model = preview.thumbnailUrl,
                        contentDescription = "热点回复附图",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(thumbShape)
                            .background(colors.cardSurface)
                            .border(1.dp, colors.cardBorder, thumbShape),
                    )
                }
                if (preview.text.isNotBlank()) {
                    val annotated = remember(preview.text, preview.smiles) {
                        buildAnnotatedString { appendSmileText(preview.text, preview.smiles) }
                    }
                    val inlineContent = remember(preview.smiles) { smileInlineContent(preview.smiles) }
                    Text(
                        text = annotated,
                        modifier = Modifier.weight(1f),
                        inlineContent = inlineContent,
                        style = previewStyle,
                        color = colors.textSecondary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (reply.floor > 0 || canLocate) {
                EdgeAlignedMetaRow(
                    leading = {
                        if (reply.floor > 0) {
                            Text(
                                text = "#${reply.floor}",
                                modifier = Modifier.semantics { contentDescription = "第 ${reply.floor} 楼" },
                                style = typography.metaTabular,
                                color = colors.textSecondary,
                            )
                        } else {
                            Spacer(Modifier.size(0.dp))
                        }
                    },
                    trailing = {
                        if (canLocate) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "原帖",
                                    style = typography.capsuleLabel,
                                    color = colors.goldText,
                                )
                                Icon(
                                    imageVector = NagiIcons.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = colors.goldText,
                                )
                            }
                        } else {
                            Spacer(Modifier.size(0.dp))
                        }
                    },
                )
            }
        }
    }
}

@Preview(name = "Hot replies narrow", widthDp = 360, showBackground = true)
@Preview(name = "Hot replies wide", widthDp = 720, showBackground = true)
@Composable
private fun HotRepliesSectionPreview() {
    NagiTheme("light") {
        Surface(color = NagiThemeTokens.colors.cardSurface) {
            Box(Modifier.padding(Space.lg)) {
                HotRepliesSection(
                    replies = listOf(
                        Post("901", 9, "热评甲", "1002", "", "", listOf(ContentBlock.Text("核心回复内容，支持多行截断预览。")), score = 384),
                        Post("902", 0, "热评乙", "1003", "", "", listOf(ContentBlock.Text("另一条热点回复。")), score = 122),
                    ),
                    topicAuthorId = "1001",
                    preferences = ReadingPreferences(),
                    onLocate = {},
                )
            }
        }
    }
}
