package dev.nagi.app.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.EdgeAlignedMetaRow
import dev.nagi.app.ui.components.FrostedDropdownDivider
import dev.nagi.app.ui.components.FrostedDropdownItem
import dev.nagi.app.ui.components.FrostedDropdownMenu
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.NagiSheetHeader
import dev.nagi.app.ui.components.UserTagBadge
import dev.nagi.app.ui.components.NagiDialog
import dev.nagi.app.ui.components.NagiIconAction
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.NagiSheetHeader
import dev.nagi.app.ui.components.nagiPressFeedback
import dev.nagi.app.ui.components.UserAvatar
import dev.nagi.app.ui.components.formatAuthorDisplayName
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.ui.topic.formatTimeText
import dev.nagi.app.viewmodel.ComposePhase
import dev.nagi.app.viewmodel.PostRatingState
import dev.nagi.sdk.Post
import dev.nagi.sdk.PostRating

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderPost(
    post: Post,
    preferences: ReadingPreferences,
    isAuthor: Boolean,
    title: String?,
    onImage: (String) -> Unit,
    highlighted: Boolean = false,
    onReply: (() -> Unit)? = null,
    onComment: (() -> Unit)? = null,
    ratingState: PostRatingState? = null,
    onRating: ((PostRating) -> Unit)? = null,
    viewerId: String? = null,
    onEdit: (() -> Unit)? = null,
    filteringThisAuthor: Boolean = false,
    onFilterAuthor: (() -> Unit)? = null,
    onCopyText: (() -> Unit)? = null,
    onCopyLink: (() -> Unit)? = null,
    onShareCard: (() -> Unit)? = null,
    topicAuthorId: String = "",
    showHotReplies: Boolean = true,
    onLocateHotReply: ((Post) -> Unit)? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val openUser = dev.nagi.app.ui.components.LocalOpenUser.current
    val canOpenUser = openUser != null && dev.nagi.app.ui.components.isPublicUserId(post.authorId)
    val tint = if (highlighted) colors.goldContainer else Color.Transparent
    val hasAuthorStats = !post.authorReputation.isNullOrBlank() || post.authorPosts != null
    val displayScore = post.score + (ratingState?.scoreDelta ?: 0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint)
            .padding(horizontal = Space.lg)
            .padding(top = Space.md, bottom = Space.sm),
    ) {
        EdgeAlignedMetaRow(
            verticalAlignment = Alignment.Top,
            leading = {
                Row(
                    modifier = if (canOpenUser) Modifier
                        .clickable(onClickLabel = "查看 ${formatAuthorDisplayName(post.author)} 的用户详情") { openUser?.invoke(post.authorId) }
                        .heightIn(min = 48.dp) else Modifier,
                    verticalAlignment = Alignment.Top,
                ) {
                    UserAvatar(
                        name = post.author,
                        avatarUrl = post.avatar,
                        uid = post.authorId,
                        size = 32.dp,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Space.md),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatAuthorDisplayName(post.author),
                                style = typography.bodyText.copy(
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 19.sp,
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                                ),
                                color = colors.textPrimary,
                            )
                            if (isAuthor) {
                                GoldTagBadge(text = "楼主")
                            }
                            UserTagBadge(post.authorId)
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
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (post.date.isNotBlank()) {
                        Text(
                            text = formatTimeText(post.date),
                            style = typography.metadata,
                            color = colors.textSecondary,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ReaderClientBadge(post.clientPlatform)
                        Text(
                            text = "#${post.floor}",
                            modifier = Modifier.semantics { contentDescription = "第 ${post.floor} 楼" },
                            style = typography.metadata,
                            color = colors.textSecondary,
                        )
                    }
                }
            },
        )
        if (!title.isNullOrBlank()) {
            Spacer(Modifier.height(Space.md))
            SelectionContainer {
                Text(
                    text = title,
                    style = typography.sectionTitle.copy(fontSize = 18.5.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        SelectionContainer { ContentBlocks(post.content, preferences, onImage) }
        if (post.comments.isNotEmpty()) {
            var expandedComments by rememberSaveable(post.id) { mutableStateOf(false) }
            val previewComments = post.comments.take(3)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.md),
                shape = RoundedCornerShape(12.dp),
                color = colors.insetContainer,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Space.md, vertical = Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    Text(
                        text = "贴条 · ${post.comments.size}",
                        style = typography.metadata.copy(fontWeight = FontWeight.Medium),
                        color = colors.textSecondary,
                    )
                    previewComments.forEachIndexed { index, comment ->
                        if (index > 0) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = colors.cardBorder.copy(alpha = 0.55f),
                            )
                        }
                        val canOpenCommentUser = openUser != null && dev.nagi.app.ui.components.isPublicUserId(comment.authorId)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            FlowRow(
                                modifier = if (canOpenCommentUser) {
                                    Modifier
                                        .heightIn(min = 48.dp)
                                        .clickable(onClickLabel = "查看用户详情") { openUser?.invoke(comment.authorId) }
                                } else {
                                    Modifier
                                },
                                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                                verticalArrangement = Arrangement.spacedBy(Space.xs),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = formatAuthorDisplayName(comment.author),
                                    style = typography.capsuleLabel,
                                    color = colors.goldText,
                                )
                                UserTagBadge(comment.authorId)
                                if (comment.date.isNotBlank()) {
                                    Text(
                                        text = formatTimeText(comment.date),
                                        style = typography.metadata,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                            SelectionContainer { ContentBlocks(comment.content, preferences, onImage) }
                        }
                    }
                }
            }
            if (post.comments.size > 3) {
                Box(Modifier.padding(top = Space.xs)) {
                    CapsuleButton(
                        text = "查看贴条（${post.comments.size}）",
                        onClick = { expandedComments = !expandedComments },
                        icon = NagiIcons.Message,
                        style = CapsuleStyle.Neutral,
                    )
                }
            }
            if (expandedComments) {
                NagiDialog(
                    onDismissRequest = { expandedComments = false },
                    modifier = Modifier.heightIn(max = 600.dp),
                ) {
                    NagiSheetHeader(
                        title = "全部贴条",
                        closeDescription = "关闭贴条",
                        onClose = { expandedComments = false },
                    )
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        itemsIndexed(post.comments, key = { index, comment -> "${comment.id}:$index" }) { idx, comment ->
                            Column(Modifier.padding(vertical = 10.dp)) {
                                FlowRow(
                                    modifier = Modifier.heightIn(min = 48.dp)
                                        .clickable(enabled = openUser != null && dev.nagi.app.ui.components.isPublicUserId(comment.authorId), onClickLabel = "查看用户详情") { expandedComments = false; openUser?.invoke(comment.authorId) },
                                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                                    itemVerticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                    text = formatAuthorDisplayName(comment.author),
                                    style = typography.capsuleLabel,
                                    color = colors.goldText,
                                )
                                    UserTagBadge(comment.authorId)
                                }
                                SelectionContainer { ContentBlocks(comment.content, preferences, onImage) }
                            }
                            if (idx < post.comments.lastIndex) {
                                HorizontalDivider(thickness = 0.5.dp, color = colors.cardBorder.copy(alpha = 0.55f))
                            }
                        }
                    }
                }
            }
        }
        if (post.floor == 0 && post.hotReplies.isNotEmpty() && showHotReplies) {
            HotRepliesSection(
                replies = post.hotReplies,
                topicAuthorId = topicAuthorId.ifBlank { if (isAuthor) post.authorId else "" },
                preferences = preferences,
                onLocate = onLocateHotReply,
            )
        }
        if (post.id.toLongOrNull()?.let { it >= 0 } == true && onReply != null) {
            var actions by remember { mutableStateOf(false) }
            val ratingEnabled = ratingState?.enabled != false
            val upConfirmed = ratingState?.rating == PostRating.UP
            val downConfirmed = ratingState?.rating == PostRating.DOWN
            val pendingRating = ratingState?.pendingRating.takeIf { ratingState?.phase == ComposePhase.Submitting }
            val voteDescription = when (ratingState?.phase) {
                ComposePhase.Submitting -> "处理中"
                ComposePhase.Unconfirmed -> "结果未确认，请刷新核对"
                else -> null
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onRating != null) {
                        val upInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable(
                                    enabled = ratingEnabled,
                                    role = Role.Button,
                                    interactionSource = upInteraction,
                                    indication = null,
                                    onClick = { onRating(PostRating.UP) },
                                )
                                .semantics {
                                    contentDescription = "点赞第 ${post.floor} 楼"
                                    selected = upConfirmed
                                    stateDescription = voteDescription ?: if (upConfirmed) "已点赞，再次点击取消；净赞 $displayScore" else "未点赞；净赞 $displayScore"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .nagiPressFeedback(
                                        interactionSource = upInteraction,
                                        enabled = ratingEnabled,
                                        pressedScale = 0.92f,
                                        shape = CircleShape,
                                    )
                                    .padding(start = 12.dp, end = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                if (pendingRating == PostRating.UP) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.goldAccent)
                                } else {
                                    Icon(
                                        imageVector = NagiIcons.ThumbUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = (if (upConfirmed) colors.goldText else colors.textSecondary)
                                            .copy(alpha = if (ratingEnabled) 1f else 0.38f),
                                    )
                                }
                                Text(
                                    text = displayScore.toString(),
                                    style = typography.metadata.copy(
                                        fontWeight = if (displayScore > 0 || upConfirmed) FontWeight.SemiBold else FontWeight.Normal,
                                        fontFeatureSettings = "tnum",
                                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                                    ),
                                    color = (if (upConfirmed) colors.goldText else colors.textSecondary)
                                        .copy(alpha = if (ratingEnabled) 1f else 0.38f),
                                )
                            }
                        }
                        NagiIconAction(
                            icon = NagiIcons.ThumbDown,
                            description = "点踩第 ${post.floor} 楼",
                            onClick = { onRating(PostRating.DOWN) },
                            enabled = ratingEnabled,
                            loading = pendingRating == PostRating.DOWN,
                            selected = downConfirmed,
                            state = voteDescription ?: if (downConfirmed) "已点踩，再次点击取消" else "未点踩",
                            iconSize = 18.dp,
                            tint = if (downConfirmed) colors.goldText else colors.textSecondary,
                        )
                    }
                    NagiIconAction(
                        icon = NagiIcons.Reply,
                        description = "回复第 ${post.floor} 楼",
                        onClick = onReply,
                        iconSize = 18.dp,
                        tint = colors.textSecondary,
                    )
                    Box {
                        NagiIconAction(
                            icon = NagiIcons.More,
                            description = "第 ${post.floor} 楼更多操作",
                            onClick = { actions = true },
                            iconSize = 18.dp,
                            tint = colors.textSecondary,
                        )
                        FrostedDropdownMenu(
                            expanded = actions,
                            onDismissRequest = { actions = false },
                            modifier = Modifier.widthIn(min = 184.dp),
                        ) {
                            if (onRating != null) {
                                listOf(PostRating.UP to "点赞", PostRating.DOWN to "踩").forEach { (rating, label) ->
                                    val selectedRating = ratingState?.rating == rating
                                    FrostedDropdownItem(
                                        text = if (selectedRating) "取消$label" else label,
                                        leadingIcon = if (rating == PostRating.UP) NagiIcons.ThumbUp else NagiIcons.ThumbDown,
                                        selected = selectedRating,
                                        enabled = ratingEnabled,
                                        modifier = Modifier.semantics { selected = selectedRating },
                                        onClick = {
                                            actions = false
                                            onRating(rating)
                                        },
                                    )
                                }
                            }
                            if (onEdit != null && !viewerId.isNullOrBlank() && viewerId == post.authorId) {
                                FrostedDropdownItem(
                                    text = if (post.floor == 0) "编辑帖子" else "编辑回复",
                                    leadingIcon = NagiIcons.Compose,
                                    onClick = { actions = false; onEdit() },
                                )
                            }
                            if (onComment != null) {
                                FrostedDropdownItem(
                                    text = "贴条评论",
                                    leadingIcon = NagiIcons.Message,
                                    onClick = {
                                        actions = false
                                        onComment()
                                    },
                                )
                            }
                            if (onFilterAuthor != null) {
                                FrostedDropdownItem(
                                    text = if (filteringThisAuthor) "查看全部" else "只看该层主",
                                    leadingIcon = NagiIcons.Person,
                                    selected = filteringThisAuthor,
                                    modifier = Modifier.semantics { selected = filteringThisAuthor },
                                    onClick = {
                                        actions = false
                                        onFilterAuthor()
                                    },
                                )
                            }
                            if (onCopyText != null || onCopyLink != null || onShareCard != null) {
                                FrostedDropdownDivider()
                            }
                            if (onCopyText != null) {
                                FrostedDropdownItem(
                                    text = "复制内容",
                                    leadingIcon = NagiIcons.Copy,
                                    onClick = {
                                        actions = false
                                        onCopyText()
                                    },
                                )
                            }
                            if (onCopyLink != null) {
                                FrostedDropdownItem(
                                    text = "复制楼层链接",
                                    leadingIcon = NagiIcons.Link,
                                    onClick = {
                                        actions = false
                                        onCopyLink()
                                    },
                                )
                            }
                            if (onShareCard != null) {
                                FrostedDropdownItem(
                                    text = "分享主楼卡片",
                                    leadingIcon = NagiIcons.ShareCard,
                                    onClick = {
                                        actions = false
                                        onShareCard()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


/** Compact iconified reputation & post count stats shared by reader posts and share cards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PostAuthorMetaStats(
    reputation: String?,
    posts: Int?,
    modifier: Modifier = Modifier,
) {
    val rep = reputation?.takeIf { it.isNotBlank() }
    if (rep == null && posts == null) return
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val statStyle = typography.metadata.copy(
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum",
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
    )
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.xxs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (rep != null) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "威望 $rep"
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    imageVector = NagiIcons.Star,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = colors.textMuted,
                )
                Text(
                    text = rep,
                    style = statStyle,
                    color = colors.textMuted,
                )
            }
        }
        if (posts != null) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "发帖 $posts"
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    imageVector = NagiIcons.Message,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = colors.textMuted,
                )
                Text(
                    text = posts.toString(),
                    style = statStyle,
                    color = colors.textMuted,
                )
            }
        }
    }
}
