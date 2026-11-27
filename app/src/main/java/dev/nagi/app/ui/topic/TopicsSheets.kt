package dev.nagi.app.ui.topic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.NeutralTagBadge
import dev.nagi.app.ui.components.FrostedDropdownItem
import dev.nagi.app.ui.components.FrostedDropdownMenu
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.NagiFrostedSheet
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.NagiSearchTextField
import dev.nagi.app.ui.components.NagiSheetHeader
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.sdk.Forum
import dev.nagi.sdk.TopicSort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RelatedForumsSheet(
    currentForum: Forum?,
    directoryForums: List<Forum>,
    onSelectForum: (Forum) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val related = remember(currentForum, directoryForums) {
        if (currentForum == null || directoryForums.isEmpty()) {
            emptyList()
        } else {
            val matched = directoryForums.firstOrNull { it.key == currentForum.key }
                ?: directoryForums.firstOrNull { it.id == currentForum.id }
            when {
                matched != null && (matched.category.isNotBlank() || matched.group.isNotBlank()) ->
                    directoryForums.filter { it.category == matched.category && it.group == matched.group }
                else -> directoryForums.filter { it.id == currentForum.id }
            }
        }
    }

    val dividerColor = colors.cardBorder.copy(alpha = 0.55f)
    NagiFrostedSheet(
        onDismissRequest = onDismiss,
        maxHeight = 520.dp,
    ) {
        NagiSheetHeader(
            title = "同组版块与主题合集",
            subtitle = "点按切换同组版块或主题合集",
            onClose = onDismiss,
        )
        HorizontalDivider(thickness = 0.5.dp, color = dividerColor)

        if (related.isEmpty()) {
            val single = currentForum
            if (single != null) {
                Box(Modifier.padding(bottom = Space.sm)) {
                    RelatedForumRow(
                        forum = single,
                        isCurrent = true,
                        onClick = onDismiss,
                    )
                }
            } else {
                Box(Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
                    Notice("暂无同组版块或合集")
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = Space.md)) {
                itemsIndexed(related, key = { _, forum -> forum.key }) { index, forum ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = Space.lg),
                            thickness = 0.5.dp,
                            color = dividerColor,
                        )
                    }
                    RelatedForumRow(
                        forum = forum,
                        isCurrent = forum.key == currentForum?.key,
                        onClick = { onSelectForum(forum) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedForumRow(
    forum: Forum,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 54.dp)
            .padding(horizontal = Space.lg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text(
                    text = forum.name,
                    style = typography.topicTitle,
                    color = if (isCurrent) colors.goldText else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (forum.collectionId != null) {
                    NeutralTagBadge(text = "合集")
                }
                if (isCurrent) {
                    GoldTagBadge(text = "当前")
                }
            }
            if (forum.description.isNotBlank()) {
                Text(
                    text = forum.description,
                    style = typography.metadata,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = NagiIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.textSecondary,
        )
    }
}

@Composable
internal fun TopicSortMenu(current: TopicSort, recommendAvailable: Boolean, onSelect: (TopicSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val allOptions = listOf(
        TopicSort.NEWEST to "最新发布",
        TopicSort.LAST_ACTIVE to "最近活跃",
        TopicSort.RECOMMENDED to "精华区",
    )
    val options = allOptions.filter { it.first != TopicSort.RECOMMENDED || recommendAvailable }
    val activeLabel = allOptions.first { it.first == current }.second
    Box {
        CapsuleButton(
            text = activeLabel,
            onClick = { expanded = true },
            icon = NagiIcons.Sort,
            trailingIcon = NagiIcons.ChevronDown,
            style = CapsuleStyle.Neutral,
            contentDescription = "排序方式",
            stateDescription = "当前：$activeLabel",
        )
        FrostedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (option, label) ->
                val active = current == option
                FrostedDropdownItem(
                    text = label,
                    selected = active,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    modifier = Modifier.semantics { selected = active },
                )
            }
        }
    }
}

@Composable
internal fun SearchControls(
    scope: Forum?,
    query: String,
    onQueryChange: (String) -> Unit,
    onChooseScope: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        CapsuleButton(
            text = scope?.let { "搜索范围：${it.name}（切换）" } ?: "选择搜索版块",
            onClick = onChooseScope,
            icon = NagiIcons.Forums,
            trailingIcon = NagiIcons.ChevronDown,
            style = if (scope != null) CapsuleStyle.SelectedGold else CapsuleStyle.Neutral,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            NagiSearchTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "搜索主题",
                clearDescription = "清空搜索词",
                onSubmit = onSubmit,
                modifier = Modifier.weight(1f),
            )
            CapsuleButton(
                text = "搜索",
                onClick = onSubmit,
                icon = NagiIcons.Search,
                enabled = query.isNotBlank() && scope != null,
                style = CapsuleStyle.SolidGold,
            )
        }
    }
}

