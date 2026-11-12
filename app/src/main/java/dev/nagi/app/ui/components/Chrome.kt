package dev.nagi.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space

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
        Column(Modifier.fillMaxWidth()) {
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

@Composable
fun FrostedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NagiThemeTokens.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 200.dp),
        shape = NagiThemeTokens.shapes.dropdown,
        containerColor = colors.frostedDropdown,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, colors.cardBorder),
        content = content,
    )
}

@Composable
fun FrostedDropdownItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val labelColor = when {
        destructive -> colors.destructive
        selected -> colors.goldText
        else -> colors.textPrimary
    }
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    text = text,
                    style = typography.body,
                    color = labelColor,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = typography.metadata,
                        color = colors.textSecondary,
                    )
                }
            }
        },
        leadingIcon = leadingIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (destructive) colors.destructive else if (selected) colors.goldText else colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = NagiIcons.Check,
                    contentDescription = null,
                    tint = colors.goldText,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else null,
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
    )
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
                text = message,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Space.xs),
                style = typography.body,
                color = colors.destructive,
            )
            if (retry != null) {
                CapsuleButton(
                    text = "重试",
                    onClick = retry,
                    style = CapsuleStyle.Destructive,
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
