package dev.nagi.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.sdk.Forum

/**
 * Shared floating glass dialog/sheet window frame that preserves shadow breathing room
 * and softens the Android Dialog dim scrim so underlying frosted blur stays bright.
 */
@Composable
private fun NagiFloatingDialogFrame(
    onDismissRequest: () -> Unit,
    alignment: Alignment,
    maxWidth: Dp,
    properties: DialogProperties,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val dialogProperties = remember(properties) {
        DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            securePolicy = properties.securePolicy,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = properties.decorFitsSystemWindows,
        )
    }
    Dialog(onDismissRequest = onDismissRequest, properties = dialogProperties) {
        val view = LocalView.current
        val dimAmount = if (colors.isDark) 0.45f else 0.24f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.setDimAmount(dimAmount)
        }
        val outsideDismissModifier = if (properties.dismissOnClickOutside) {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest,
            )
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .then(outsideDismissModifier)
                .padding(horizontal = Space.lg, vertical = Space.md),
            contentAlignment = alignment,
        ) {
            FrostedSurface(
                modifier = modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = NagiThemeTokens.shapes.flatCard,
                backgroundColor = colors.frostedDropdown,
            ) {
                content()
            }
        }
    }
}

/**
 * Shared Nagi modal container using a floating frosted glass card.
 */
@Composable
fun NagiDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 440.dp,
    properties: DialogProperties = DialogProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    NagiFloatingDialogFrame(
        onDismissRequest = onDismissRequest,
        alignment = Alignment.Center,
        maxWidth = maxWidth,
        properties = properties,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
            content = content,
        )
    }
}

/**
 * Floating bottom frosted glass sheet for compact panels and action lists.
 */
@Composable
fun NagiFrostedSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 560.dp,
    maxHeight: Dp = Dp.Unspecified,
    properties: DialogProperties = DialogProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    NagiFloatingDialogFrame(
        onDismissRequest = onDismissRequest,
        alignment = Alignment.BottomCenter,
        maxWidth = maxWidth,
        properties = properties,
        modifier = modifier,
    ) {
        val heightModifier = if (maxHeight != Dp.Unspecified) {
            Modifier.heightIn(max = maxHeight)
        } else {
            Modifier
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(heightModifier),
            content = content,
        )
    }
}

/**
 * Shared frosted scope-picker sheet used by topic search and global search.
 */
@Composable
fun ForumScopePickerSheet(
    forums: List<Forum>,
    onSelectForum: (Forum) -> Unit,
    onDismissRequest: () -> Unit,
    selectedForumKey: String? = null,
    includeAllOption: Boolean = false,
    allSelected: Boolean = false,
    onSelectAll: (() -> Unit)? = null,
    loading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    emptyMessage: String = "没有匹配的版块",
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    var filter by rememberSaveable { mutableStateOf("") }
    val filtered = remember(forums, filter) {
        if (filter.isBlank()) forums else forums.filter { it.name.contains(filter, ignoreCase = true) }
    }
    val dividerColor = colors.cardBorder.copy(alpha = 0.55f)

    NagiFrostedSheet(
        onDismissRequest = onDismissRequest,
        maxHeight = 560.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.lg, end = Space.sm, top = Space.md, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("选择搜索版块", style = typography.sectionTitle, color = colors.textPrimary)
            ChromeIconButton(
                icon = NagiIcons.Close,
                description = "关闭面板",
                onClick = onDismissRequest,
            )
        }
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.sm),
            singleLine = true,
            shape = CircleShape,
            textStyle = typography.body.copy(color = colors.textPrimary),
            placeholder = { Text("筛选版块", style = typography.body, color = colors.textSecondary) },
            leadingIcon = {
                Icon(
                    imageVector = NagiIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.textSecondary,
                )
            },
            trailingIcon = if (filter.isNotEmpty()) {
                {
                    IconButton(onClick = { filter = "" }) {
                        Icon(
                            imageVector = NagiIcons.ClearCircle,
                            contentDescription = "清空筛选",
                            modifier = Modifier.size(18.dp),
                            tint = colors.textSecondary,
                        )
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = colors.insetContainer.copy(alpha = if (colors.isDark) 0.72f else 0.78f),
                focusedContainerColor = colors.insetContainer.copy(alpha = if (colors.isDark) 0.85f else 0.90f),
                unfocusedBorderColor = colors.cardBorder.copy(alpha = 0.75f),
                focusedBorderColor = colors.goldAccent,
            ),
        )
        HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            contentPadding = PaddingValues(bottom = Space.md),
        ) {
            if (includeAllOption && onSelectAll != null) {
                item(key = "__all_forums__") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                filter = ""
                                onSelectAll()
                            }
                            .heightIn(min = 50.dp)
                            .padding(horizontal = Space.lg, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "所有版块",
                            modifier = Modifier.weight(1f),
                            style = typography.topicTitle,
                            color = if (allSelected) colors.goldText else colors.textPrimary,
                        )
                        if (allSelected) {
                            Icon(NagiIcons.Check, contentDescription = "当前已选", modifier = Modifier.size(18.dp), tint = colors.goldText)
                        }
                    }
                    if (filtered.isNotEmpty()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = Space.lg),
                            thickness = 0.5.dp,
                            color = dividerColor,
                        )
                    }
                }
            }
            itemsIndexed(filtered, key = { _, forum -> forum.key }) { index, forum ->
                val active = forum.key == selectedForumKey
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable {
                            filter = ""
                            onSelectForum(forum)
                        }
                        .padding(horizontal = Space.lg, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = forum.name,
                            style = typography.topicTitle,
                            color = if (active) colors.goldText else colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val groupLine = listOf(forum.category, forum.group)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(" / ")
                        if (groupLine.isNotBlank()) {
                            Text(
                                text = groupLine,
                                style = typography.metadata,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (active) {
                        Icon(
                            imageVector = NagiIcons.Check,
                            contentDescription = "当前已选",
                            modifier = Modifier.size(18.dp),
                            tint = colors.goldText,
                        )
                    } else if (includeAllOption) {
                        Icon(
                            imageVector = NagiIcons.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.textSecondary,
                        )
                    }
                }
                if (index < filtered.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Space.lg),
                        thickness = 0.5.dp,
                        color = dividerColor,
                    )
                }
            }
            if (loading) {
                item { LoadingLine() }
            }
            if (error != null) {
                item { Notice(error, onRetry) }
            } else if (!loading && filtered.isEmpty()) {
                item { Notice(emptyMessage) }
            }
        }
    }
}

/**
 * Shared wide capsule action bar for Nagi dialogs, stacking vertically on narrow or large-font layouts.
 */
@Composable
fun NagiDialogActions(
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmStyle: CapsuleStyle = CapsuleStyle.SelectedGold,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = {},
    dismissStyle: CapsuleStyle = CapsuleStyle.Neutral,
    dismissEnabled: Boolean = true,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stackVertically = dismissLabel != null && (maxWidth < 260.dp || fontScale >= 1.5f)
        if (stackVertically) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                CapsuleButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = confirmEnabled,
                    style = confirmStyle,
                    visualHeight = 40.dp,
                    fillWidth = true,
                )
                CapsuleButton(
                    text = dismissLabel!!,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = dismissEnabled,
                    style = dismissStyle,
                    visualHeight = 40.dp,
                    fillWidth = true,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                if (dismissLabel != null) {
                    CapsuleButton(
                        text = dismissLabel,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = dismissEnabled,
                        style = dismissStyle,
                        visualHeight = 40.dp,
                        fillWidth = true,
                    )
                }
                CapsuleButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = confirmEnabled,
                    style = confirmStyle,
                    visualHeight = 40.dp,
                    fillWidth = true,
                )
            }
        }
    }
}

/**
 * Standard Nagi confirmation and notice dialog with wide capsule actions.
 */
@Composable
fun NagiConfirmDialog(
    onDismissRequest: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
    confirmStyle: CapsuleStyle = CapsuleStyle.SelectedGold,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    dismissStyle: CapsuleStyle = CapsuleStyle.Neutral,
    dismissEnabled: Boolean = true,
    maxWidth: Dp = 440.dp,
    properties: DialogProperties = DialogProperties(),
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    NagiDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        maxWidth = maxWidth,
        properties = properties,
    ) {
        if (title != null) {
            Text(
                text = title,
                style = typography.sectionTitle,
                color = colors.textPrimary,
            )
        }
        if (message != null) {
            Text(
                text = message,
                style = typography.body,
                color = colors.textSecondary,
            )
        }
        if (content != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                content = content,
            )
        }
        NagiDialogActions(
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            confirmStyle = confirmStyle,
            confirmEnabled = confirmEnabled,
            dismissLabel = dismissLabel,
            onDismiss = onDismiss,
            dismissStyle = dismissStyle,
            dismissEnabled = dismissEnabled,
        )
    }
}
