package dev.nagi.app.ui.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.NagiFrostedSheet
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.NagiSheetHeader
import dev.nagi.app.ui.components.nagiPressFeedback
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
        icon = NagiIcons.Smile,
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
    var groupId by rememberSaveable { mutableStateOf(NgaSmiles.groups.first().id) }
    val group = NgaSmiles.groups.first { it.id == groupId }
    NagiFrostedSheet(
        onDismissRequest = close,
        maxWidth = 560.dp,
        maxHeight = 440.dp,
    ) {
        NagiSheetHeader(
            title = "选择表情",
            trailing = {
                CapsuleButton("返回输入", close, icon = NagiIcons.Close, style = CapsuleStyle.Neutral)
            },
        )
        Column(
            modifier = Modifier.padding(start = Space.md, end = Space.md, bottom = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            LazyRow(
                modifier = Modifier.semantics { contentDescription = "表情分类" },
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                items(NgaSmiles.groups, key = { it.id }) { category ->
                    Box(Modifier.semantics { selected = category.id == groupId }) {
                        CapsuleButton(
                            text = category.name,
                            onClick = { groupId = category.id },
                            style = if (category.id == groupId) CapsuleStyle.SelectedGold else CapsuleStyle.Neutral,
                        )
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
                                    onClick = { select(smile) },
                                )
                                .semantics { contentDescription = "插入表情：${smile.name}" }
                                .padding(Space.sm),
                        ) {
                            SmileImage(smile, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}
