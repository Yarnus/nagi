package dev.nagi.app.ui.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.sdk.NgaSmile
import dev.nagi.sdk.NgaSmiles

internal fun insertSmile(field: TextFieldValue, smile: NgaSmile): TextFieldValue {
    val start = field.selection.min
    return TextFieldValue(
        text = field.text.replaceRange(start, field.selection.max, smile.code),
        selection = TextRange(start + smile.code.length),
    )
}

@Composable
internal fun SmilePickerButton(value: TextFieldValue, edit: (TextFieldValue) -> Unit, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(enabled) { if (!enabled) open = false }
    CapsuleButton(
        text = "表情",
        enabled = enabled,
        style = if (open) CapsuleStyle.SelectedGold else CapsuleStyle.Neutral,
        onClick = { keyboard?.hide(); open = true },
    )
    if (open && enabled) {
        SmilePicker(
            close = { open = false; keyboard?.show() },
            select = { smile -> if (enabled) edit(insertSmile(value, smile)) },
        )
    }
}

@Composable
private fun SmilePicker(close: () -> Unit, select: (NgaSmile) -> Unit) {
    val colors = NagiThemeTokens.colors
    var groupId by rememberSaveable { mutableStateOf(NgaSmiles.groups.first().id) }
    val group = NgaSmiles.groups.first { it.id == groupId }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.safeDrawingPadding().padding(Space.lg).widthIn(max = 560.dp)
                .fillMaxWidth().heightIn(max = 440.dp),
            shape = NagiThemeTokens.shapes.topicCard,
            color = colors.cardSurface,
            border = BorderStroke(1.dp, colors.cardBorder),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("选择表情", style = NagiThemeTokens.typography.sectionTitle, color = colors.textPrimary,
                        modifier = Modifier.weight(1f).padding(vertical = Space.sm))
                    CapsuleButton("返回输入", close, style = CapsuleStyle.Neutral)
                }
                LazyRow(
                    modifier = Modifier.semantics { contentDescription = "表情分类" },
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    items(NgaSmiles.groups, key = { it.id }) { category ->
                        Box(Modifier.semantics { selected = category.id == groupId }) {
                            CapsuleButton(category.name, { groupId = category.id },
                                style = if (category.id == groupId) CapsuleStyle.SelectedGold else CapsuleStyle.Neutral)
                        }
                    }
                }
                androidx.compose.runtime.key(groupId) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(56.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 260.dp),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        items(group.smiles, key = { it.key }) { smile ->
                            Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).aspectRatio(1f)
                                .clip(CircleShape).clickable(role = Role.Button) { select(smile) }
                                .semantics { contentDescription = "插入表情：${smile.name}" }
                                .padding(Space.sm)) {
                                SmileImage(smile, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }
}
