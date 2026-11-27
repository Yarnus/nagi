package dev.nagi.app.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nagi.app.data.MAX_FONT_SIZE
import dev.nagi.app.data.MAX_LINE_HEIGHT
import dev.nagi.app.data.MIN_FONT_SIZE
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.NagiSheetHeader
import dev.nagi.app.ui.components.TypographyStepperRowContent
import dev.nagi.app.ui.components.lineHeightValueLabel
import dev.nagi.app.ui.components.stepDownFontSize
import dev.nagi.app.ui.components.stepDownLineHeight
import dev.nagi.app.ui.components.stepUpFontSize
import dev.nagi.app.ui.components.stepUpLineHeight
import dev.nagi.app.ui.components.NagiDialogActions
import dev.nagi.app.ui.components.NagiFrostedSheet
import dev.nagi.app.ui.components.NagiSheetHeader
import dev.nagi.app.ui.components.SegmentedCapsuleControl
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import kotlinx.coroutines.flow.filter

@Composable
internal fun ReaderEdgeButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = !loading, onClick = onClick)
            .padding(Space.lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = colors.goldAccent,
            )
        }
        Text(
            text = label,
            modifier = Modifier.padding(start = if (loading) Space.sm else 0.dp),
            style = typography.capsuleLabel,
            color = colors.textSecondary,
        )
    }
}

@Composable
internal fun PageJumpSheet(
    currentPage: Int,
    totalPages: Int,
    maxFloor: Int,
    canJumpFloor: Boolean,
    dismiss: () -> Unit,
    onPage: (Int) -> Unit,
    onFloor: (Int) -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    var byFloor by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf(currentPage.toString()) }
    val floorMode = byFloor && canJumpFloor
    val limit = if (floorMode) maxFloor else totalPages
    val value = input.toIntOrNull()
    val valid = value != null && (if (floorMode) value in 0..maxFloor else value >= 1 && (totalPages == 0 || value <= totalPages))
    val submit = {
        if (valid) {
            if (floorMode) onFloor(value!!) else onPage(value!!)
        }
    }
    NagiFrostedSheet(
        onDismissRequest = dismiss,
        maxWidth = 480.dp,
    ) {
        NagiSheetHeader(
            title = if (canJumpFloor) "快速跳转" else "跳转页码",
            onClose = dismiss,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.lg, end = Space.lg, bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            if (canJumpFloor) {
                SegmentedCapsuleControl(
                    options = listOf("按页码", "按楼层"),
                    selectedIndex = if (floorMode) 1 else 0,
                    onSelect = { idx ->
                        byFloor = idx == 1
                        input = if (byFloor) "0" else currentPage.toString()
                    },
                )
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(9) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = NagiThemeTokens.shapes.topicCard,
                textStyle = typography.body.copy(color = colors.textPrimary),
                label = {
                    Text(
                        text = if (floorMode) "输入楼层（0–$maxFloor 楼）" else if (limit > 0) "输入页码（1–$limit 页）" else "输入页码",
                        style = typography.metadata,
                    )
                },
                isError = input.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.insetContainer.copy(alpha = if (colors.isDark) 0.85f else 0.90f),
                    unfocusedContainerColor = colors.insetContainer.copy(alpha = if (colors.isDark) 0.72f else 0.78f),
                    focusedBorderColor = colors.goldAccent,
                    unfocusedBorderColor = colors.cardBorder.copy(alpha = 0.75f),
                ),
            )
            if (!floorMode && totalPages > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    CapsuleButton(
                        text = "首页",
                        onClick = { onPage(1) },
                        style = CapsuleStyle.Neutral,
                    )
                    CapsuleButton(
                        text = "末页",
                        onClick = { onPage(totalPages) },
                        style = CapsuleStyle.Neutral,
                    )
                }
            }
            NagiDialogActions(
                confirmLabel = if (valid) (if (floorMode) "前往 #$value" else "前往第 $value 页") else "前往",
                onConfirm = { submit() },
                confirmEnabled = valid,
                confirmStyle = CapsuleStyle.SolidGold,
            )
        }
    }
}

@Composable
internal fun ReaderTypographySheet(
    preferences: ReadingPreferences,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Int) -> Unit,
    onReset: (() -> Unit)?,
    dismiss: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val dividerColor = colors.cardBorder.copy(alpha = 0.55f)
    NagiFrostedSheet(
        onDismissRequest = dismiss,
        maxWidth = 480.dp,
    ) {
        NagiSheetHeader(
            title = "阅读排版",
            subtitle = "正文字号 ${preferences.fontSize} sp · 行高 ${preferences.effectiveLineHeight} sp",
            onClose = dismiss,
        )
        HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
        TypographyStepperRowContent(
            title = "正文字号",
            valueLabel = "${preferences.fontSize} sp",
            decreaseDescription = "减小字号",
            onDecrease = { stepDownFontSize(preferences, onFontSizeChange) },
            decreaseEnabled = preferences.fontSize > MIN_FONT_SIZE,
            increaseDescription = "增大字号",
            onIncrease = { stepUpFontSize(preferences, onFontSizeChange) },
            increaseEnabled = preferences.fontSize < MAX_FONT_SIZE,
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = Space.lg),
            thickness = 0.5.dp,
            color = dividerColor,
        )
        TypographyStepperRowContent(
            title = "正文行高",
            valueLabel = lineHeightValueLabel(preferences),
            decreaseDescription = "紧凑",
            onDecrease = { stepDownLineHeight(preferences, onLineHeightChange) },
            decreaseEnabled = preferences.effectiveLineHeight > preferences.minLineHeight,
            increaseDescription = "宽松",
            onIncrease = { stepUpLineHeight(preferences, onLineHeightChange) },
            increaseEnabled = preferences.effectiveLineHeight < MAX_LINE_HEIGHT,
        )
        if (onReset != null) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Space.lg),
                thickness = 0.5.dp,
                color = dividerColor,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.md),
                contentAlignment = Alignment.CenterEnd,
            ) {
                CapsuleButton(
                    text = "恢复默认",
                    onClick = onReset,
                    enabled = !preferences.isDefaultTypography,
                    style = CapsuleStyle.Neutral,
                )
            }
        } else {
            Spacer(Modifier.height(Space.sm))
        }
    }
}

