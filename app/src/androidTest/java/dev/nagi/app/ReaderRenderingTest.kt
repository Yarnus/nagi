package dev.nagi.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.post.ReaderPost
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.ContentParser
import dev.nagi.sdk.Post
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun floorAndQuoteLayersRenderAcrossAppearanceChanges() {
        val title = "折叠屏长帖阅读体验：主楼完整标题、楼层边界与引用内容应当清晰可读"
        val posts = listOf(
            Post("0", 0, "测试楼主", "7", "", "2026-11-05 16:27", listOf(ContentBlock.Text("主楼正文，不使用真实账号或图片。"))),
            Post("1", 1, "名字较长的测试论坛用户", "8", "", "2026-11-05 16:30",
                ContentParser.parse("[quote][b]Post by [uid=7]测试楼主[/uid] (2026-11-05 16:27)[/b]引用正文与当前回复分开。[/quote]当前回复正文。")),
            Post("2", 2, "测试楼主", "7", "", "2026-11-05 16:32", listOf(ContentBlock.Text("楼主在后续楼层回复，仍然带有楼主标记。"))),
        )
        var theme by mutableStateOf("light")
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                NagiTheme(theme) {
                    Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
                        LazyColumn(contentPadding = PaddingValues(vertical = 16.dp)) {
                            itemsIndexed(posts, key = { _, post -> post.id }, contentType = { _, _ -> "post" }) { index, post ->
                                Column {
                                    if (index == 1) Spacer(Modifier.height(20.dp))
                                    SectionCardItem(if (index == 0) CardGroupPosition.Single else CardGroupPosition.at(index - 1, posts.size - 1)) {
                                        ReaderPost(post, ReadingPreferences(), post.authorId == "7", title.takeIf { index == 0 }, {})
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("#0").assertIsDisplayed()
        compose.onNodeWithText("回复 测试楼主").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasText("[uid=", substring = true)).assertCountEquals(0)
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "reader-ui")
        output.mkdirs()
        fun capture(name: String) {
            compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
                File(output, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        capture("floors-light")
        compose.runOnIdle { theme = "dark"; scale = 1.5f }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("回复 测试楼主").assertIsDisplayed()
        compose.onNodeWithText("当前回复正文。").assertIsDisplayed()
        capture("floors-dark-1.5")
    }
}
