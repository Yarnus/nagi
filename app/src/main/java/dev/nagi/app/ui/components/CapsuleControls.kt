package dev.nagi.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space

@Composable
fun FrostedSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    backgroundColor: Color = NagiThemeTokens.colors.frostedChrome,
    borderColor: Color = NagiThemeTokens.colors.cardBorder,
    content: @Composable () -> Unit,
) {
    val isDark = NagiThemeTokens.colors.isDark
    val effectiveBorder = if (borderColor == NagiThemeTokens.colors.cardBorder) {
        if (isDark) Color(0xFF2E3138) else Color(0xFFE2E5EB)
    } else {
        borderColor
    }
    Surface(
        modifier = modifier
            .frostedCapsuleBackdrop(backgroundColor, shape),
        shape = shape,
        color = Color.Transparent,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, effectiveBorder),
        content = content,
    )
}

@Composable
fun ChromeIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: String? = null,
    iconSize: Dp = 20.dp,
    visualSize: Dp = 42.dp,
    tint: Color = NagiThemeTokens.colors.textPrimary,
    containerColor: Color = NagiThemeTokens.colors.frostedChrome,
    borderColor: Color = NagiThemeTokens.colors.cardBorder,
) {
    val isDark = NagiThemeTokens.colors.isDark
    val effectiveBorder = if (borderColor == NagiThemeTokens.colors.cardBorder) {
        if (isDark) Color(0xFF2E3138) else Color(0xFFE2E5EB)
    } else {
        borderColor
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 21.dp),
                onClick = onClick,
            )
            .semantics {
                contentDescription = description
                if (state != null) stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(visualSize)
                .frostedCapsuleBackdrop(containerColor, CircleShape)
                .border(1.dp, effectiveBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = tint.copy(alpha = if (enabled) 1f else 0.38f),
            )
        }
    }
}

enum class CapsuleStyle {
    Neutral,
    FrostedGold,
    SelectedGold,
    SolidGold,
    Destructive,
}

@Composable
fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    style: CapsuleStyle = CapsuleStyle.Neutral,
    enabled: Boolean = true,
    minTouchHeight: Dp = 48.dp,
    visualHeight: Dp = 36.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    contentDescription: String? = null,
    stateDescription: String? = null,
) {
    val tokens = NagiThemeTokens.colors
    val (bg, fg, border) = when (style) {
        CapsuleStyle.Neutral -> Triple(tokens.cardSurface, tokens.textPrimary, tokens.cardBorder)
        CapsuleStyle.FrostedGold -> Triple(tokens.frostedChrome, tokens.goldText, tokens.cardBorder)
        CapsuleStyle.SelectedGold -> Triple(tokens.goldContainer, tokens.goldText, tokens.goldBorder)
        CapsuleStyle.SolidGold -> Triple(tokens.goldSolidFill, tokens.goldSolidContent, tokens.goldSolidFill)
        CapsuleStyle.Destructive -> Triple(tokens.destructiveContainer, tokens.destructive, tokens.destructive.copy(alpha = 0.35f))
    }

    val effectiveBg = if (enabled) bg else tokens.insetContainer
    val effectiveFg = if (enabled) fg else tokens.textMuted
    val effectiveBorder = if (enabled) border else tokens.cardBorder

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = minTouchHeight)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                if (stateDescription != null) this.stateDescription = stateDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = visualHeight)
                .clip(CircleShape)
                .then(
                    if (enabled && style == CapsuleStyle.FrostedGold) Modifier.frostedCapsuleBackdrop(effectiveBg, CircleShape)
                    else Modifier.background(effectiveBg),
                )
                .border(1.dp, effectiveBorder, CircleShape)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = effectiveFg)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                modifier = if (contentDescription != null) Modifier.clearAndSetSemantics {} else Modifier,
                style = NagiThemeTokens.typography.capsuleButton.copy(
                    fontWeight = if (style == CapsuleStyle.SelectedGold || style == CapsuleStyle.SolidGold) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (enabled && style == CapsuleStyle.SolidGold) tokens.goldSolidText else effectiveFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingIcon != null) {
                Spacer(Modifier.width(4.dp))
                Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(14.dp), tint = effectiveFg)
            }
        }
    }
}

@Composable
fun GoldTagBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tokens = NagiThemeTokens.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(tokens.goldContainer)
            .padding(horizontal = 8.dp, vertical = 2.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = NagiThemeTokens.typography.tagBadge,
            color = tokens.goldText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NeutralTagBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tokens = NagiThemeTokens.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(tokens.insetContainer)
            .padding(horizontal = 8.dp, vertical = 2.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = NagiThemeTokens.typography.tagBadge,
            color = tokens.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ReplyCountPill(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val tokens = NagiThemeTokens.colors
    val isHot = count >= 100
    val bg = if (isHot) tokens.goldContainer else tokens.insetContainer
    val fg = when {
        isHot -> tokens.goldText
        count > 0 -> tokens.textSecondary
        else -> tokens.textMuted
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.5.dp)
            .clearAndSetSemantics { contentDescription = "$count 条回复" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = NagiThemeTokens.typography.replyCount,
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
fun GoldSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val tokens = NagiThemeTokens.colors
    val trackColor by animateColorAsState(
        targetValue = if (checked) tokens.switchActiveTrack else tokens.switchInactiveTrack,
        animationSpec = tween(180),
        label = "goldSwitchTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(180),
        label = "goldSwitchThumb",
    )

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = onCheckedChange,
            )
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(trackColor.copy(alpha = if (enabled) 1f else 0.5f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(start = thumbOffset)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
fun SegmentedCapsuleControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabledIndices: Set<Int>? = null,
) {
    val tokens = NagiThemeTokens.colors
    FrostedSurface(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = CircleShape,
        backgroundColor = tokens.frostedChrome,
        borderColor = tokens.cardBorder,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                val isEnabled = enabledIndices == null || index in enabledIndices
                val pillBg = if (isSelected) tokens.goldContainer else Color.Transparent
                val pillBorder = if (isSelected) tokens.goldBorder else Color.Transparent
                val textColor = when {
                    !isEnabled -> tokens.textMuted
                    isSelected -> tokens.goldText
                    else -> tokens.textSecondary
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(CircleShape)
                        .background(pillBg)
                        .then(if (isSelected) Modifier.border(1.dp, pillBorder, CircleShape) else Modifier)
                        .clickable(
                            enabled = isEnabled,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = { onSelect(index) },
                        )
                        .semantics { selected = isSelected }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.fillMaxWidth(),
                        style = NagiThemeTokens.typography.capsuleButton.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = textColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun SearchCapsuleBar(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionDescription: String = "搜索",
) {
    val tokens = NagiThemeTokens.colors
    FrostedSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(Space.dockHeight)
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .semantics { contentDescription = actionDescription },
        shape = CircleShape,
        backgroundColor = tokens.frostedChrome,
        borderColor = tokens.cardBorder,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = NagiIcons.Search,
                contentDescription = null,
                tint = tokens.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = placeholder,
                style = NagiThemeTokens.typography.bodyText,
                color = tokens.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TopicListFloatingDock(
    onSubforumsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onComposeClick: () -> Unit,
    modifier: Modifier = Modifier,
    subforumsContentDescription: String = "子板块筛选",
    subforumFilterActive: Boolean = false,
    searchLabel: String = "搜索话题",
    searchPlaceholder: String = searchLabel,
    searchContentDescription: String = "搜索当前版面",
    refreshContentDescription: String = "刷新列表",
    composeContentDescription: String = "发帖",
) {
    val tokens = NagiThemeTokens.colors
    val composeFill = tokens.cardSurface
    val composeIconTint = tokens.goldText

    FrostedSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(Space.dockHeight),
        shape = CircleShape,
        backgroundColor = tokens.frostedChrome,
        borderColor = tokens.cardBorder,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockIconAction(
                icon = NagiIcons.Subforums,
                description = subforumsContentDescription,
                onClick = onSubforumsClick,
                tint = if (subforumFilterActive) tokens.goldText else tokens.textPrimary,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onClick = onSearchClick,
                    )
                    .semantics { contentDescription = searchContentDescription },
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(CircleShape)
                        .background(
                            tokens.insetContainer,
                        )
                        .border(
                            0.85.dp,
                            if (tokens.isDark) Color.White.copy(alpha = 0.10f)
                            else Color(0xFFE2E5EB),
                            CircleShape,
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = NagiIcons.Search,
                        contentDescription = null,
                        tint = tokens.textSecondary,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = searchPlaceholder,
                        style = NagiThemeTokens.typography.capsuleButton,
                        color = tokens.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            DockIconAction(
                icon = NagiIcons.Refresh,
                description = refreshContentDescription,
                onClick = onRefreshClick,
                tint = tokens.textPrimary,
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 20.dp),
                        onClick = onComposeClick,
                    )
                    .semantics { contentDescription = composeContentDescription },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(composeFill)
                        .border(1.dp, tokens.cardBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = NagiIcons.Compose,
                        contentDescription = null,
                        tint = composeIconTint,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DockIconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 20.dp),
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}
