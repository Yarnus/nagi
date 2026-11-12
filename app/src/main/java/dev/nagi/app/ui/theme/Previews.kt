package dev.nagi.app.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.HomeActions
import dev.nagi.app.ui.components.NagiHeader
import dev.nagi.app.ui.components.SearchCapsuleBar
import dev.nagi.app.ui.components.SectionCardItem

@Preview(name = "Compact light", widthDp = 390, heightDp = 820, showBackground = true)
@Composable
fun CompactPreview() {
    ReadingPreview("light")
}

@Preview(name = "Medium dark", widthDp = 700, heightDp = 820, showBackground = true)
@Composable
fun MediumPreview() {
    ReadingPreview("dark")
}

@Preview(name = "Expanded light", widthDp = 1000, heightDp = 820, showBackground = true)
@Composable
fun ExpandedPreview() {
    ReadingPreview("light", true)
}

@Composable
private fun ReadingPreview(theme: String, expanded: Boolean = false) {
    NagiTheme(theme) {
        val colors = NagiThemeTokens.colors
        val typography = NagiThemeTokens.typography
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.groupedCanvas,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Row {
                Column(Modifier.weight(1f)) {
                    HomeActions({})
                    Text(
                        text = "Nagi.",
                        modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                        style = typography.largeTitle,
                        color = colors.textPrimary,
                    )
                    SectionCardItem(CardGroupPosition.Single) {
                        Column(Modifier.padding(Space.lg)) {
                            Text("分享今天的阅读与发现", style = typography.topicTitle, color = colors.textPrimary)
                            Text("Pure Flat · Minimal", style = typography.metadata, color = colors.textSecondary)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    SearchCapsuleBar(
                        placeholder = "搜索版块、话题或用户",
                        onClick = {},
                        modifier = Modifier.padding(Space.lg),
                    )
                }
                if (expanded) {
                    VerticalDivider(Modifier.fillMaxHeight(), thickness = 1.dp, color = colors.cardBorder)
                    Column(Modifier.weight(1.5f)) {
                        NagiHeader("主题阅读")
                        SectionCardItem(CardGroupPosition.Single, modifier = Modifier.padding(top = Space.md)) {
                            Text(
                                text = "正文默认使用系统字体与 16sp 字号。保留清晰的楼层、作者与内容层级。",
                                modifier = Modifier.padding(Space.lg),
                                style = typography.postBody,
                                color = colors.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
