package dev.nagi.app.ui.topic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nagi.app.data.TopicReadState
import dev.nagi.app.data.TopicReadStatus
import dev.nagi.app.data.accessibilityStateDescription
import dev.nagi.app.data.resolveTopicReadStatus
import dev.nagi.app.ui.components.LocalTopicReadStates
import dev.nagi.app.ui.components.formatAuthorDisplayName
import dev.nagi.app.ui.components.EdgeAlignedMetaRow
import dev.nagi.app.ui.components.NeutralTagBadge
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.ReplyCountPill
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.sdk.Topic
import dev.nagi.sdk.TopicSort
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun FlatTopicCard(
    topic: Topic,
    sort: TopicSort,
    selected: Boolean,
    selectableMode: Boolean,
    margin: Dp = Space.lg,
    readState: TopicReadState? = LocalTopicReadStates.current[topic.id],
    onDetails: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    FlatCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = margin, vertical = 4.dp),
        shape = NagiThemeTokens.shapes.topicCard,
        containerColor = if (selected) colors.selectedRowFill else colors.cardSurface,
        borderColor = if (selected) colors.goldAccent else colors.cardBorder,
    ) {
        TopicRow(
            topic = topic,
            sort = sort,
            selected = selected,
            selectableMode = selectableMode,
            gutter = 14.dp,
            readState = readState,
            onDetails = onDetails,
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TopicRow(
    topic: Topic,
    sort: TopicSort,
    selected: Boolean,
    selectableMode: Boolean,
    gutter: Dp,
    readState: TopicReadState? = LocalTopicReadStates.current[topic.id],
    onDetails: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val readStatus = remember(topic.id, topic.replies, topic.lastActiveAt, readState) {
        resolveTopicReadStatus(topic, readState)
    }
    val titleColor = when {
        selected -> colors.textPrimary
        readStatus is TopicReadStatus.Read -> colors.textSecondary
        else -> colors.textPrimary
    }
    val updatedNewReplies = (readStatus as? TopicReadStatus.Updated)?.newReplies
    val hasNewActivity = readStatus is TopicReadStatus.Updated
    val interactionModifier = Modifier
        .testTag("topic-${topic.id}")
        .combinedClickable(
            onClick = onClick,
            onLongClick = onDetails,
            onLongClickLabel = "查看完整标题和时间",
        )
        .semantics {
            if (selectableMode) this.selected = selected
            readStatus.accessibilityStateDescription()?.let { stateDescription = it }
            customActions = listOf(CustomAccessibilityAction("查看主题信息") { onDetails(); true })
        }

    val parsed = remember(topic.title) { parseTopicSubject(topic.title) }
    val hasTopBadges = topic.recommended || parsed.tag != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(interactionModifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = gutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            if (hasTopBadges) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (topic.recommended) {
                            GoldTagBadge(text = "精选")
                        }
                        if (parsed.tag != null) {
                            NeutralTagBadge(text = parsed.tag)
                        }
                    }
                    ReplyCountPill(
                        count = topic.replies,
                        newReplies = updatedNewReplies,
                        hasNewActivity = hasNewActivity,
                    )
                }
                Text(
                    text = parsed.cleanTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics { text = AnnotatedString(topic.title) },
                    style = typography.topicTitle,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = titleColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    Text(
                        text = parsed.cleanTitle,
                        modifier = Modifier
                            .weight(1f)
                            .clearAndSetSemantics { text = AnnotatedString(topic.title) },
                        style = typography.topicTitle,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = titleColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ReplyCountPill(
                        count = topic.replies,
                        newReplies = updatedNewReplies,
                        hasNewActivity = hasNewActivity,
                    )
                }
            }

            EdgeAlignedMetaRow(
                leading = {
                    Text(
                        text = formatAuthorDisplayName(topic.author),
                        style = typography.metadata,
                        color = colors.textSecondary,
                    )
                },
                trailing = {
                    val primaryTime = if (sort == TopicSort.NEWEST || sort == TopicSort.RECOMMENDED) topic.createdAt else topic.lastActiveAt
                    val timestamp = primaryTime.takeIf { it > 0 } ?: topic.createdAt.takeIf { it > 0 } ?: topic.lastActiveAt
                    if (timestamp > 0) {
                        val timeText = remember(timestamp) { formatTime(timestamp) }
                        Text(
                            text = timeText,
                            style = typography.metadata,
                            color = colors.textSecondary,
                            maxLines = 1,
                        )
                    } else {
                        Spacer(Modifier.size(0.dp))
                    }
                },
            )
        }
    }
}

internal fun isNearTopicListEnd(
    layoutInfo: androidx.compose.foundation.lazy.LazyListLayoutInfo,
    headerCount: Int,
    topicCount: Int,
    scrolled: Boolean,
    canScrollForward: Boolean,
    distance: Int = 3,
): Boolean {
    if (topicCount <= 0 || (!scrolled && canScrollForward)) return false
    val lastTopicLayoutIndex = layoutInfo.visibleItemsInfo.lastOrNull { it.contentType == "topic" }?.index ?: return false
    val lastTopicPosition = lastTopicLayoutIndex - headerCount
    return lastTopicPosition >= topicCount - distance
}

private data class ParsedTopicSubject(val tag: String?, val cleanTitle: String)

private fun parseTopicSubject(rawTitle: String): ParsedTopicSubject {
    val trimmed = rawTitle.trim()
    if (trimmed.startsWith("[")) {
        val end = trimmed.indexOf(']')
        if (end in 2..16) {
            val tag = trimmed.substring(1, end).trim()
            val remainder = trimmed.substring(end + 1).trimStart()
            if (tag.isNotEmpty() && remainder.isNotEmpty()) {
                return ParsedTopicSubject(tag = tag, cleanTitle = remainder)
            }
        }
    }
    return ParsedTopicSubject(tag = null, cleanTitle = rawTitle)
}

private val SameYearDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val FullYearDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val RawPostSecondsFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun formatTime(
    seconds: Long,
    nowSeconds: Long = Instant.now().epochSecond,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    if (seconds <= 0L) return "—"
    val target = Instant.ofEpochSecond(seconds).atZone(zone)
    val now = Instant.ofEpochSecond(nowSeconds).atZone(zone)
    if (target.toLocalDate() == now.toLocalDate()) {
        val diffSeconds = (nowSeconds - seconds).coerceAtLeast(0L)
        return when {
            diffSeconds < 60L -> "刚刚"
            diffSeconds < 3600L -> "${diffSeconds / 60L} 分钟之前"
            else -> "${diffSeconds / 3600L} 小时之前"
        }
    }
    return if (target.year == now.year) {
        SameYearDateFormatter.format(target)
    } else {
        FullYearDateFormatter.format(target)
    }
}

fun formatTimeText(
    rawDate: String,
    nowSeconds: Long = Instant.now().epochSecond,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val trimmed = rawDate.trim()
    if (trimmed.isEmpty()) return rawDate
    trimmed.toLongOrNull()?.takeIf { it > 0L }?.let { epoch ->
        return formatTime(epoch, nowSeconds, zone)
    }
    val parsed = runCatching { LocalDateTime.parse(trimmed, RawPostSecondsFormatter) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(trimmed, FullYearDateFormatter) }.getOrNull()
    return if (parsed != null) {
        formatTime(parsed.atZone(zone).toEpochSecond(), nowSeconds, zone)
    } else {
        rawDate
    }
}
