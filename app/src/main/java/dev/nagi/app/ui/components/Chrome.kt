package dev.nagi.app.ui.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space

@Composable
internal fun safeBottomPadding(): Dp =
    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NagiPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    topPadding: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    Box(
        modifier = modifier.pullToRefresh(
            isRefreshing = isRefreshing,
            state = state,
            enabled = enabled,
            onRefresh = onRefresh,
        ),
    ) {
        content()
        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = isRefreshing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topPadding),
            containerColor = NagiThemeTokens.colors.cardSurface,
            color = NagiThemeTokens.colors.goldAccent,
        )
    }
}

@Composable
fun NagiHeader(
    title: String,
    subtitle: String? = null,
    back: (() -> Unit)? = null,
    supporting: (@Composable ColumnScope.() -> Unit)? = null,
    horizontalPadding: Dp = Space.lg,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Surface(
        color = colors.frostedChrome,
        contentColor = colors.textPrimary,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(
                        start = if (back != null) (horizontalPadding - 4.dp).coerceAtLeast(Space.xs) else horizontalPadding,
                        end = (horizontalPadding - 4.dp).coerceAtLeast(Space.sm),
                        top = Space.xs,
                        bottom = Space.xs,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (back != null) {
                    ChromeIconButton(
                        icon = NagiIcons.Back,
                        description = "返回",
                        onClick = back,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = if (back != null) Space.sm else 0.dp,
                            end = Space.sm,
                            top = Space.xs,
                            bottom = if (supporting != null) 2.dp else Space.xs,
                        ),
                ) {
                    Text(
                        text = title,
                        style = typography.appBarTitle,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = typography.metadata,
                            color = colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        content = actions,
                    )
                }
            }
            if (supporting != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = horizontalPadding, end = horizontalPadding, bottom = Space.sm),
                    content = supporting,
                )
            }
            HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
        }
    }
}

private class NagiMenuPositionProvider(
    private val density: Density,
    private val shadowPadH: Int,
    private val shadowPadTop: Int,
    private val shadowPadBottom: Int,
    private val onTransformOrigin: (TransformOrigin) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val marginH = with(density) { 12.dp.roundToPx() }
        val marginV = with(density) { 48.dp.roundToPx() }
        val gap = with(density) { 4.dp.roundToPx() }
        val menuW = (popupContentSize.width - shadowPadH * 2).coerceAtLeast(1)
        val menuH = (popupContentSize.height - shadowPadTop - shadowPadBottom).coerceAtLeast(1)

        val alignRight = anchorBounds.left + menuW > windowSize.width - marginH
        val menuX = if (alignRight) {
            (anchorBounds.right - menuW).coerceIn(marginH, (windowSize.width - menuW - marginH).coerceAtLeast(marginH))
        } else {
            anchorBounds.left.coerceIn(marginH, (windowSize.width - menuW - marginH).coerceAtLeast(marginH))
        }

        val belowY = anchorBounds.bottom + gap
        val aboveY = anchorBounds.top - gap - menuH
        val showAbove = belowY + menuH > windowSize.height - marginV && aboveY >= marginV
        val menuY = if (showAbove) {
            aboveY.coerceAtLeast(marginV)
        } else {
            belowY.coerceIn(marginV, (windowSize.height - menuH - marginV).coerceAtLeast(marginV))
        }

        val originX = ((anchorBounds.left + anchorBounds.right) / 2f - menuX) / menuW.toFloat()
        val originY = if (showAbove) 1f else 0f
        onTransformOrigin(TransformOrigin(originX.coerceIn(0f, 1f), originY))

        return IntOffset(menuX - shadowPadH, menuY - shadowPadTop)
    }
}

@Composable
fun FrostedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val shape = NagiThemeTokens.shapes.dropdown
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded
    if (!expandedState.currentState && !expandedState.targetState) return

    val density = LocalDensity.current
    val isInspecting = LocalInspectionMode.current
    val transition = rememberTransition(expandedState, label = "FrostedDropdownMenu")
    val scale by transition.animateFloat(
        transitionSpec = { tween(if (targetState) 140 else 90) },
        label = "menu-scale",
    ) { if (it) 1f else 0.92f }
    val alpha by transition.animateFloat(
        transitionSpec = { tween(if (targetState) 120 else 85) },
        label = "menu-alpha",
    ) { if (it) 1f else 0f }
    val transformOrigin = remember { mutableStateOf(TransformOrigin(0.5f, 0f)) }

    val padH = 20.dp
    val padTop = 14.dp
    val padBottom = 26.dp
    val padHPx = with(density) { padH.roundToPx() }
    val padTopPx = with(density) { padTop.roundToPx() }
    val padBottomPx = with(density) { padBottom.roundToPx() }
    val positionProvider = remember(density, padHPx, padTopPx, padBottomPx) {
        NagiMenuPositionProvider(density, padHPx, padTopPx, padBottomPx) {
            transformOrigin.value = it
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .padding(start = padH, end = padH, top = padTop, bottom = padBottom)
                .graphicsLayer {
                    if (!isInspecting) {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        this.transformOrigin = transformOrigin.value
                    }
                },
        ) {
            FrostedSurface(
                modifier = Modifier
                    .widthIn(min = 200.dp)
                    .then(modifier),
                shape = shape,
                backgroundColor = colors.frostedDropdown,
            ) {
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    content = content,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FrostedDropdownItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    badge: String? = null,
    leadingIcon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val labelColor = when {
        !enabled -> colors.textMuted
        destructive -> colors.destructive
        selected -> colors.goldText
        else -> colors.textPrimary
    }
    DropdownMenuItem(
        text = {
            Column {
                if (badge != null) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = text,
                            style = typography.body,
                            color = labelColor,
                        )
                        GoldTagBadge(badge)
                    }
                } else {
                    Text(
                        text = text,
                        style = typography.body,
                        color = labelColor,
                    )
                }
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = typography.metadata,
                        color = if (enabled) colors.textSecondary else colors.textMuted,
                    )
                }
            }
        },
        leadingIcon = leadingContent ?: leadingIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = when {
                        !enabled -> colors.textMuted
                        destructive -> colors.destructive
                        selected -> colors.goldText
                        else -> colors.textSecondary
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = NagiIcons.Check,
                    contentDescription = null,
                    tint = if (enabled) colors.goldText else colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else null,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
    )
}

@Composable
fun FrostedDropdownDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = Space.lg, vertical = Space.xs),
        thickness = 1.dp,
        color = NagiThemeTokens.colors.cardBorder,
    )
}

/**
 * Shared header row for Nagi dialogs and floating frosted sheets.
 */
@Composable
fun NagiSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    closeDescription: String = "关闭面板",
    onClose: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Space.lg,
                end = if (onClose != null && trailing == null) Space.sm else Space.lg,
                top = Space.md,
                bottom = Space.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = typography.sectionTitle,
                color = colors.textPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = typography.metadata,
                    color = colors.textSecondary,
                )
            }
        }
        if (trailing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                content = trailing,
            )
        }
        if (onClose != null) {
            ChromeIconButton(
                icon = NagiIcons.Close,
                description = closeDescription,
                onClick = onClose,
            )
        }
    }
}

@Composable
fun Notice(message: String, retry: (() -> Unit)? = null) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xl, vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(
            text = message,
            color = colors.textSecondary,
            style = typography.body,
            textAlign = TextAlign.Center,
        )
        if (retry != null) {
            CapsuleButton(
                text = "重试",
                onClick = retry,
                icon = NagiIcons.Refresh,
                style = CapsuleStyle.Neutral,
            )
        }
    }
}

@Composable
fun InlineStatusBanner(
    message: String,
    retry: (() -> Unit)? = null,
    horizontalPadding: Dp = Space.lg,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val identity = LocalAccountIdentity.current
    val openLogin = LocalOpenLogin.current
    val needsLogin = identity.userId == null && openLogin != null && message.startsWith("请先登录 NGA")
    val displayMessage = if (needsLogin) "请先登录 NGA" else message
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = Space.sm),
        shape = NagiThemeTokens.shapes.topicCard,
        color = colors.destructiveContainer,
        border = BorderStroke(1.dp, colors.destructive.copy(alpha = 0.25f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.md, end = Space.sm, top = Space.xs, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                text = displayMessage,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Space.xs),
                style = typography.body,
                color = colors.destructive,
            )
            if (needsLogin) {
                CapsuleButton(
                    text = "登录",
                    onClick = openLogin!!,
                    icon = NagiIcons.Person,
                    style = CapsuleStyle.Neutral,
                )
            } else if (retry != null) {
                CapsuleButton(
                    text = "重试",
                    onClick = retry,
                    icon = NagiIcons.Refresh,
                    style = CapsuleStyle.Neutral,
                )
            }
        }
    }
}

@Composable
fun LoadingLine() {
    LinearProgressIndicator(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp),
        color = NagiThemeTokens.colors.goldAccent,
        trackColor = Color.Transparent,
    )
}

/**
 * Shared Nagi capsule search/filter input field with a 48x48dp clear action and press feedback.
 */
@Composable
fun NagiSearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    clearDescription: String = "清除输入",
    onClear: () -> Unit = { onValueChange("") },
    onSubmit: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Search,
    shape: Shape = CircleShape,
    containerColor: Color = NagiThemeTokens.colors.cardSurface,
    focusedContainerColor: Color = containerColor,
    borderColor: Color = NagiThemeTokens.colors.cardBorder,
    focusedBorderColor: Color = NagiThemeTokens.colors.goldAccent,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        shape = shape,
        textStyle = typography.body.copy(color = colors.textPrimary),
        placeholder = {
            Text(
                text = placeholder,
                style = typography.body,
                color = colors.textSecondary,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = NagiIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.textSecondary,
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                NagiIconAction(
                    icon = NagiIcons.ClearCircle,
                    description = clearDescription,
                    onClick = onClear,
                    iconSize = 18.dp,
                    visualSize = 36.dp,
                    tint = colors.textSecondary,
                )
            }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onSearch = { onSubmit?.invoke() },
            onDone = { onSubmit?.invoke() },
            onGo = { onSubmit?.invoke() },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = containerColor,
            focusedContainerColor = focusedContainerColor,
            unfocusedBorderColor = borderColor,
            focusedBorderColor = focusedBorderColor,
            cursorColor = colors.goldAccent,
        ),
    )
}

@Composable
fun HomeActions(onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountMenuButton(onSelect)
        ChromeIconButton(NagiIcons.Search, "搜索", onClick = { onSelect("search") })
    }
}
