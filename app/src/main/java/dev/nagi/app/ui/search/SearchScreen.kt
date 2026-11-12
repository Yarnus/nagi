package dev.nagi.app.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.ProgressiveBlurScaffold
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.ui.topic.FlatTopicCard
import dev.nagi.app.viewmodel.LibraryViewModel
import dev.nagi.app.viewmodel.SearchMode
import dev.nagi.app.viewmodel.SearchViewModel
import dev.nagi.sdk.Forum
import dev.nagi.sdk.Topic
import dev.nagi.sdk.TopicSort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    library: LibraryViewModel,
    onCancel: () -> Unit,
    onForum: (Forum) -> Unit,
    onRead: (String) -> Unit,
    onUserContent: (String, String) -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val state by vm.state.collectAsStateWithLifecycle()
    val scoped = state.forumId != null || state.collectionId != null
    val directory by library.forums.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var choosingScope by rememberSaveable { mutableStateOf(false) }
    var scopeFilter by rememberSaveable { mutableStateOf("") }
    var topicDetails by remember { mutableStateOf<Topic?>(null) }
    val cancel = {
        focusManager.clearFocus()
        keyboard?.hide()
        onCancel()
    }
    val submit: (SearchMode) -> Unit = { mode ->
        vm.submit(mode)
        focusManager.clearFocus()
        keyboard?.hide()
    }
    BackHandler { cancel() }
    LaunchedEffect(Unit) {
        if (state.mode == null) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    val matchingForums = remember(directory.items, state.submitted) {
        directory.items.filter { it.name.contains(state.submitted, ignoreCase = true) }
    }
    ProgressiveBlurScaffold(
        modifier = Modifier.imePadding(),
        topBar = {},
        topVisible = { 0f },
        bottomEdgeBlur = false,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::editQuery,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .focusRequester(focus),
                    textStyle = typography.body.copy(color = colors.textPrimary),
                    placeholder = {
                        Text(
                            text = if (scoped) "搜索当前版面" else "搜索",
                            style = typography.body,
                            color = colors.textSecondary,
                        )
                    },
                    singleLine = true,
                    shape = CircleShape,
                    leadingIcon = {
                        Icon(
                            imageVector = NagiIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.textSecondary,
                        )
                    },
                    trailingIcon = if (state.query.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = {
                                    vm.editQuery("")
                                    focus.requestFocus()
                                    keyboard?.show()
                                },
                            ) {
                                Icon(
                                    imageVector = NagiIcons.ClearCircle,
                                    contentDescription = "清空搜索",
                                    modifier = Modifier.size(18.dp),
                                    tint = colors.textSecondary,
                                )
                            }
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (state.query.isNotBlank()) submit(state.mode ?: SearchMode.TOPICS)
                        },
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.frostedChrome,
                        unfocusedContainerColor = colors.frostedChrome,
                        focusedBorderColor = colors.goldAccent,
                        unfocusedBorderColor = colors.cardBorder,
                    ),
                )
                ChromeIconButton(
                    icon = NagiIcons.Close,
                    description = "取消搜索",
                    onClick = cancel,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item(key = "heading") {
                Column(
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Text(
                        text = if (state.query.isNotBlank()) {
                            "搜索“${state.query}”"
                        } else if (scoped) {
                            "搜索当前版面的话题"
                        } else {
                            "搜索版块、话题或用户"
                        },
                        style = typography.sectionHeader,
                        color = colors.textSecondary,
                    )
                    if (scoped || state.mode == SearchMode.TOPICS) {
                        CapsuleButton(
                            text = "搜索范围：${state.forumName ?: "所有版块"}（切换）",
                            onClick = { choosingScope = true },
                            icon = NagiIcons.ForumStack,
                            style = CapsuleStyle.SelectedGold,
                        )
                    }
                }
            }
            if (state.mode == null && scoped) {
                item(key = "scoped-topics-option") {
                    SearchOption(
                        label = "搜索当前版面",
                        icon = NagiIcons.TopicDocument,
                        position = CardGroupPosition.Single,
                        enabled = state.query.isNotBlank(),
                    ) { submit(SearchMode.TOPICS) }
                }
            } else if (state.mode == null) {
                item(key = "forums-option") {
                    SearchOption(
                        label = "所有版块",
                        icon = NagiIcons.ForumStack,
                        position = CardGroupPosition.First,
                        enabled = state.query.isNotBlank(),
                    ) { submit(SearchMode.FORUMS) }
                }
                item(key = "topics-option") {
                    SearchOption(
                        label = "所有话题",
                        icon = NagiIcons.TopicDocument,
                        position = CardGroupPosition.Middle,
                        enabled = state.query.isNotBlank(),
                    ) { submit(SearchMode.TOPICS) }
                }
                item(key = "users-option") {
                    SearchOption(
                        label = "所有用户",
                        icon = NagiIcons.Users,
                        position = CardGroupPosition.Last,
                        enabled = state.query.isNotBlank(),
                        supporting = "完整用户名或 UID；纯数字按 UID 查询",
                    ) { submit(SearchMode.USERS) }
                }
            } else {
                if (state.loading) item(key = "loading") { LoadingLine() }
                state.error?.let { error -> item(key = "error") { Notice(error, vm::retry) } }
                when (requireNotNull(state.mode)) {
                    SearchMode.FORUMS -> {
                        if (directory.loading) item(key = "directory-loading") { LoadingLine() }
                        directory.error?.let { error -> item(key = "directory-error") { Notice(error, library::loadForums) } }
                        itemsIndexed(matchingForums, key = { _, forum -> forum.key }) { index, forum ->
                            SearchOption(
                                label = forum.name,
                                icon = NagiIcons.ForumStack,
                                position = CardGroupPosition.at(index, matchingForums.size),
                                enabled = true,
                                supporting = forum.description.ifBlank { null },
                            ) { onForum(forum) }
                        }
                        if (!directory.loading && directory.error == null && matchingForums.isEmpty()) {
                            item(key = "empty") { Notice("没有匹配的版块") }
                        }
                    }
                    SearchMode.TOPICS -> {
                        itemsIndexed(state.topics, key = { _, topic -> topic.id }) { _, topic ->
                            FlatTopicCard(
                                topic = topic,
                                sort = TopicSort.NEWEST,
                                selected = false,
                                selectableMode = false,
                                margin = Space.lg,
                                onDetails = { topicDetails = topic },
                                onClick = { onRead(topic.id) },
                            )
                        }
                        if (state.hasMore) {
                            item(key = "more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Space.md),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CapsuleButton(
                                        text = "加载更多",
                                        onClick = vm::loadMore,
                                        enabled = !state.loading,
                                        style = CapsuleStyle.Neutral,
                                    )
                                }
                            }
                        }
                        if (!state.loading && state.error == null && state.topics.isEmpty()) {
                            item(key = "empty") { Notice("没有匹配的主题") }
                        }
                    }
                    SearchMode.USERS -> {
                        itemsIndexed(state.users, key = { _, user -> user.id }) { index, user ->
                            SearchOption(
                                label = user.name,
                                icon = NagiIcons.Users,
                                position = CardGroupPosition.at(index, state.users.size),
                                enabled = true,
                                supporting = "UID ${user.id}",
                            ) { onUserContent(user.id, "topics") }
                        }
                        if (!state.loading && state.error == null && state.users.isEmpty()) {
                            item(key = "empty") { Notice("没有找到该用户，请输入完整用户名或 UID") }
                        }
                    }
                }
            }
        }
    }

    if (choosingScope) {
        val filtered = remember(directory.items, scopeFilter) {
            directory.items.filter { it.name.contains(scopeFilter, ignoreCase = true) }
        }
        ModalBottomSheet(
            onDismissRequest = { choosingScope = false },
            shape = NagiThemeTokens.shapes.bottomSheet,
            containerColor = colors.groupedCanvas,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg)
                    .padding(bottom = Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text("选择搜索版块", style = typography.sectionTitle, color = colors.textPrimary)
                OutlinedTextField(
                    value = scopeFilter,
                    onValueChange = { scopeFilter = it },
                    placeholder = { Text("筛选版块", style = typography.body, color = colors.textSecondary) },
                    singleLine = true,
                    shape = CircleShape,
                    leadingIcon = {
                        Icon(NagiIcons.Search, null, Modifier.size(18.dp), tint = colors.textSecondary)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.cardSurface,
                        unfocusedContainerColor = colors.cardSurface,
                        focusedBorderColor = colors.goldAccent,
                        unfocusedBorderColor = colors.cardBorder,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    contentPadding = PaddingValues(vertical = Space.xs),
                ) {
                    val total = filtered.size + 1
                    item(key = "__all_forums__") {
                        SectionCardItem(
                            position = CardGroupPosition.at(0, total),
                            margin = 0.dp,
                            dividerIndent = Space.lg,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        choosingScope = false
                                        scopeFilter = ""
                                        vm.scope(null)
                                    }
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = Space.lg, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "所有版块",
                                    modifier = Modifier.weight(1f),
                                    style = typography.topicTitle,
                                    color = if (state.forumId == null && state.collectionId == null) colors.goldText else colors.textPrimary,
                                )
                                if (state.forumId == null && state.collectionId == null) {
                                    Icon(NagiIcons.Check, null, Modifier.size(18.dp), tint = colors.goldText)
                                }
                            }
                        }
                    }
                    itemsIndexed(filtered, key = { _, forum -> forum.key }) { idx, forum ->
                        SectionCardItem(
                            position = CardGroupPosition.at(idx + 1, total),
                            margin = 0.dp,
                            dividerIndent = Space.lg,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        choosingScope = false
                                        scopeFilter = ""
                                        vm.scope(forum)
                                    }
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = Space.lg, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = forum.name,
                                    modifier = Modifier.weight(1f),
                                    style = typography.topicTitle,
                                    color = colors.textPrimary,
                                )
                                Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
                            }
                        }
                    }
                    if (directory.loading) item { LoadingLine() }
                    directory.error?.let { error -> item { Notice(error, library::loadForums) } }
                }
            }
        }
    }

    topicDetails?.let { topic ->
        AlertDialog(
            onDismissRequest = { topicDetails = null },
            shape = NagiThemeTokens.shapes.topicCard,
            containerColor = colors.cardSurface,
            tonalElevation = 0.dp,
            title = { Text(topic.title, style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Text(
                    text = "${topic.author}\n${topic.replies} 条回复",
                    style = typography.body,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                CapsuleButton(
                    text = "关闭",
                    onClick = { topicDetails = null },
                    style = CapsuleStyle.Neutral,
                )
            },
        )
    }
}

@Composable
private fun SearchOption(
    label: String,
    icon: ImageVector,
    position: CardGroupPosition,
    enabled: Boolean,
    supporting: String? = null,
    onClick: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    SectionCardItem(position = position, dividerIndent = 54.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .heightIn(min = 54.dp)
                .padding(horizontal = Space.lg, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (enabled) colors.goldAccent else colors.textSecondary.copy(alpha = 0.45f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    style = typography.topicTitle,
                    color = if (enabled) colors.textPrimary else colors.textSecondary,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = typography.metadata,
                        color = colors.textSecondary,
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
}
