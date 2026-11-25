package dev.nagi.app.ui.post

import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.NagiIconAction
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.nagiPressFeedback
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.sdk.ContentColors
import dev.nagi.sdk.NgaSmile
import dev.nagi.sdk.NgaSmiles

internal enum class EditorDockPanel {
    NONE,
    SMILE,
    COLOR,
}

/**
 * Coordinates the inline emoji/color dock inside the active editor window (`Dialog` /
 * `ModalBottomSheet`) so it and the system IME are never visible at the same time.
 */
@Stable
internal class EditorDockController(
    val focusRequester: FocusRequester,
    private val requestedPanelState: MutableState<EditorDockPanel>,
    private val imeVisibleState: State<Boolean>,
    private val editableState: State<Boolean>,
    private val previewState: State<Boolean>,
    private val showKeyboard: () -> Unit,
    private val hideKeyboard: () -> Unit,
) {
    private var requestedPanel: EditorDockPanel by requestedPanelState

    val visiblePanel: EditorDockPanel
        get() = if (editableState.value && !imeVisibleState.value) requestedPanel else EditorDockPanel.NONE

    val activeToolbarPanel: EditorDockPanel
        get() = if (editableState.value) requestedPanel else EditorDockPanel.NONE

    fun openPanel(panel: EditorDockPanel) {
        if (!editableState.value) return
        hideKeyboard()
        requestedPanel = panel
    }

    fun togglePanel(panel: EditorDockPanel) {
        if (requestedPanel == panel) {
            returnToKeyboard()
        } else {
            openPanel(panel)
        }
    }

    fun closePanel() {
        requestedPanel = EditorDockPanel.NONE
    }

    fun returnToKeyboard() {
        requestedPanel = EditorDockPanel.NONE
        if (!previewState.value) {
            runCatching { focusRequester.requestFocus() }
            showKeyboard()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberEditorDockController(
    editable: Boolean,
    preview: Boolean,
    imeVisibleOverride: Boolean? = null,
): EditorDockController {
    val focusRequester = remember { FocusRequester() }
    val requestedPanelState = remember { mutableStateOf(EditorDockPanel.NONE) }
    val editableState = rememberUpdatedState(editable)
    val previewState = rememberUpdatedState(preview)

    // Must be resolved inside the popup/dialog window so hide/show and insets target that window.
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val currentImeVisible = imeVisibleOverride
        ?: (WindowInsets.isImeVisible || WindowInsets.ime.getBottom(density) > 0)
    val imeVisibleState = rememberUpdatedState(currentImeVisible)

    val hideKeyboardAction = remember(focusManager, keyboard, view) {
        {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            view.context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
            Unit
        }
    }
    val showKeyboardAction = remember(keyboard, view) {
        {
            keyboard?.show()
            Unit
        }
    }

    LaunchedEffect(editable, preview) {
        if (!editable || (preview && requestedPanelState.value == EditorDockPanel.COLOR)) {
            requestedPanelState.value = EditorDockPanel.NONE
        }
    }

    var wasImeVisible by remember { mutableStateOf(currentImeVisible) }
    val requestedPanel = requestedPanelState.value
    LaunchedEffect(currentImeVisible, requestedPanel) {
        val justOpenedIme = !wasImeVisible && currentImeVisible
        wasImeVisible = currentImeVisible
        if (justOpenedIme && requestedPanel != EditorDockPanel.NONE) {
            requestedPanelState.value = EditorDockPanel.NONE
        } else if (requestedPanel != EditorDockPanel.NONE && currentImeVisible) {
            delay(120)
            if (requestedPanelState.value != EditorDockPanel.NONE && imeVisibleState.value) {
                hideKeyboardAction()
            }
        }
    }

    BackHandler(enabled = requestedPanel != EditorDockPanel.NONE) {
        requestedPanelState.value = EditorDockPanel.NONE
    }

    return remember(focusRequester, showKeyboardAction, hideKeyboardAction) {
        EditorDockController(
            focusRequester = focusRequester,
            requestedPanelState = requestedPanelState,
            imeVisibleState = imeVisibleState,
            editableState = editableState,
            previewState = previewState,
            showKeyboard = showKeyboardAction,
            hideKeyboard = hideKeyboardAction,
        )
    }
}

@Immutable
class RecentSmilesAccess(
    val smiles: List<NgaSmile> = emptyList(),
    val record: (NgaSmile) -> Unit = {},
    val isBackedByStore: Boolean = false,
)

val LocalRecentSmiles = compositionLocalOf { RecentSmilesAccess() }

private const val RECENT_GROUP_ID = "recent"
private const val MAX_SESSION_RECENT_SMILES = 24

internal fun insertSmile(field: TextFieldValue, smile: NgaSmile): TextFieldValue {
    val start = field.selection.min
    return TextFieldValue(
        text = field.text.replaceRange(start, field.selection.max, smile.code),
        selection = TextRange(start + smile.code.length),
    )
}

@Composable
internal fun rememberRecentSmilesController(): Pair<List<NgaSmile>, (NgaSmile) -> Unit> {
    val storeAccess = LocalRecentSmiles.current
    var fallbackKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    return if (storeAccess.isBackedByStore) {
        storeAccess.smiles to storeAccess.record
    } else {
        val resolved = remember(fallbackKeys) { fallbackKeys.mapNotNull(NgaSmiles::resolve) }
        resolved to { smile ->
            fallbackKeys = (listOf(smile.key) + fallbackKeys.filterNot { it == smile.key })
                .take(MAX_SESSION_RECENT_SMILES)
        }
    }
}

@Composable
internal fun SmilePickerButton(
    open: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    iconOnly: Boolean = false,
    iconSize: Dp = 18.dp,
    visualSize: Dp = 36.dp,
) {
    val colors = NagiThemeTokens.colors
    if (iconOnly) {
        NagiIconAction(
            icon = NagiIcons.Smile,
            description = "表情",
            enabled = enabled,
            selected = open,
            iconSize = iconSize,
            visualSize = visualSize,
            tint = if (open) colors.goldAccent else colors.textSecondary,
            onClick = onClick,
        )
    } else {
        CapsuleButton(
            text = "表情",
            icon = NagiIcons.Smile,
            enabled = enabled,
            style = if (open) CapsuleStyle.SelectedGold else CapsuleStyle.Neutral,
            onClick = onClick,
        )
    }
}

@Composable
internal fun InlineEditorDock(
    panel: EditorDockPanel,
    onReturnToKeyboard: () -> Unit,
    onSelectSmile: (NgaSmile) -> Unit,
    onSelectColor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (panel) {
        EditorDockPanel.NONE -> Unit
        EditorDockPanel.SMILE -> InlineSmileDock(
            onReturnToKeyboard = onReturnToKeyboard,
            onSelectSmile = onSelectSmile,
            modifier = modifier,
        )
        EditorDockPanel.COLOR -> InlineColorDock(
            onReturnToKeyboard = onReturnToKeyboard,
            onSelectColor = onSelectColor,
            modifier = modifier,
        )
    }
}

@Composable
private fun InlineSmileDock(
    onReturnToKeyboard: () -> Unit,
    onSelectSmile: (NgaSmile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (recentSmiles, recordRecent) = rememberRecentSmilesController()
    var groupId by rememberSaveable {
        mutableStateOf(if (recentSmiles.isNotEmpty()) RECENT_GROUP_ID else NgaSmiles.groups.first().id)
    }
    val displayedSmiles = remember(groupId, recentSmiles) {
        if (groupId == RECENT_GROUP_ID) {
            recentSmiles
        } else {
            NgaSmiles.groups.firstOrNull { it.id == groupId }?.smiles ?: NgaSmiles.groups.first().smiles
        }
    }
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography

    FlatCard(
        modifier = modifier
            .fillMaxWidth()
            .height(236.dp)
            .semantics { contentDescription = "选择表情" },
        shape = NagiThemeTokens.shapes.flatCard,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.sm, end = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(colors.insetContainer)
                        .padding(horizontal = 4.dp),
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "表情分类" },
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item(key = RECENT_GROUP_ID) {
                            DockCategoryTab(
                                label = "最近",
                                icon = NagiIcons.History,
                                selected = groupId == RECENT_GROUP_ID,
                                onClick = { groupId = RECENT_GROUP_ID },
                            )
                        }
                        items(NgaSmiles.groups, key = { it.id }) { category ->
                            DockCategoryTab(
                                label = category.name,
                                selected = category.id == groupId,
                                onClick = { groupId = category.id },
                            )
                        }
                    }
                }
                NagiIconAction(
                    icon = NagiIcons.Keyboard,
                    description = "返回输入",
                    onClick = onReturnToKeyboard,
                    visualSize = 34.dp,
                    iconSize = 18.dp,
                    tint = colors.textPrimary,
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = colors.cardBorder)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (displayedSmiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Space.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            Text(
                                text = "暂无最近使用表情",
                                style = typography.cardTitle,
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "点选其他分组的表情后会自动记录在这里",
                                style = typography.metadata,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    androidx.compose.runtime.key(groupId) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(52.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Space.xs),
                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            items(displayedSmiles, key = { it.key }) { smile ->
                                val interactionSource = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .aspectRatio(1f)
                                        .nagiPressFeedback(
                                            interactionSource = interactionSource,
                                            pressedScale = 0.90f,
                                            shape = CircleShape,
                                        )
                                        .clip(CircleShape)
                                        .clickable(
                                            role = Role.Button,
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = {
                                                recordRecent(smile)
                                                onSelectSmile(smile)
                                            },
                                        )
                                        .semantics { contentDescription = "插入表情：${smile.name}" }
                                        .padding(Space.sm),
                                ) {
                                    SmileImage(smile, Modifier.fillMaxSize().clearAndSetSemantics {})
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockCategoryTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .nagiPressFeedback(
                    interactionSource = interactionSource,
                    pressedScale = 0.96f,
                    shape = CircleShape,
                )
                .clip(CircleShape)
                .background(if (selected) colors.cardSurface else Color.Transparent)
                .then(
                    if (selected) Modifier.border(1.dp, colors.goldBorder, CircleShape)
                    else Modifier,
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (selected) colors.goldText else colors.textSecondary,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = typography.metadata.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (selected) colors.goldText else colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

internal data class NgaColorOption(
    val id: String,
    val label: String,
    val color: Color,
)

private val COLOR_LABELS: Map<String, String> = mapOf(
    "skyblue" to "天蓝",
    "royalblue" to "品蓝",
    "blue" to "蓝色",
    "darkblue" to "深蓝",
    "orange" to "橙色",
    "orangered" to "橘红",
    "crimson" to "绯红",
    "red" to "红色",
    "firebrick" to "砖红",
    "darkred" to "暗红",
    "green" to "绿色",
    "limegreen" to "亮绿",
    "seagreen" to "海绿",
    "teal" to "青绿",
    "deeppink" to "深粉",
    "tomato" to "番茄",
    "coral" to "珊瑚",
    "purple" to "紫色",
    "indigo" to "靛蓝",
    "burlywood" to "浅棕",
    "sandybrown" to "沙褐",
    "chocolate" to "巧克",
    "sienna" to "赭石",
    "silver" to "银灰",
)

internal val NGA_COLOR_OPTIONS: List<NgaColorOption> = ContentColors.rgb.map { (id, rgb) ->
    NgaColorOption(
        id = id,
        label = COLOR_LABELS[id] ?: id,
        color = Color(rgb or 0xFF000000.toInt()),
    )
}

@Composable
private fun InlineColorDock(
    onReturnToKeyboard: () -> Unit,
    onSelectColor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    FlatCard(
        modifier = modifier
            .fillMaxWidth()
            .height(236.dp)
            .semantics { contentDescription = "选择字体颜色" },
        shape = NagiThemeTokens.shapes.flatCard,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.md, end = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text(
                    text = "字体颜色",
                    style = typography.sectionHeader,
                    color = colors.textPrimary,
                )
                Text(
                    text = "点击包裹选区或插入标签",
                    modifier = Modifier.weight(1f),
                    style = typography.metadata,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                NagiIconAction(
                    icon = NagiIcons.Keyboard,
                    description = "返回输入",
                    onClick = onReturnToKeyboard,
                    visualSize = 34.dp,
                    iconSize = 18.dp,
                    tint = colors.textPrimary,
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = colors.cardBorder)

            LazyVerticalGrid(
                columns = GridCells.Adaptive(78.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                items(NGA_COLOR_OPTIONS, key = { it.id }) { option ->
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .nagiPressFeedback(
                                interactionSource = interactionSource,
                                pressedScale = 0.95f,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.insetContainer)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                            .clickable(
                                role = Role.Button,
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelectColor(option.id) },
                            )
                            .semantics { contentDescription = "字体颜色：${option.label}" }
                            .padding(horizontal = Space.sm, vertical = Space.xs),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(option.color)
                                    .border(1.dp, colors.cardBorder, CircleShape),
                            )
                            Text(
                                text = option.label,
                                style = typography.metadata,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
