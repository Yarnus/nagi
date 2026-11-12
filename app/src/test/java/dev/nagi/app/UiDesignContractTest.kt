package dev.nagi.app

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.GoldSwitch
import dev.nagi.app.ui.components.SegmentedCapsuleControl
import dev.nagi.app.ui.post.ReaderPost
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.topic.FlatTopicCard
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.Post
import dev.nagi.sdk.Topic
import dev.nagi.sdk.TopicSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w320dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiDesignContractTest {
    @get:Rule val compose = createComposeRule()

    @Test fun topicDockComposeUsesNeutralSurfaceAndGoldIconInBothThemes() {
        var theme by mutableStateOf("light")
        var gold = Color.Unspecified
        var solid = Color.Unspecified
        var clicks = 0
        lateinit var view: android.view.View
        compose.setContent { NagiTheme(theme) {
            view = LocalView.current
            gold = NagiThemeTokens.colors.goldText
            solid = NagiThemeTokens.colors.goldSolidFill
            dev.nagi.app.ui.components.TopicListFloatingDock({}, {}, {}, { clicks++ })
        } }
        for (appearance in listOf("light", "dark")) {
            compose.runOnIdle { theme = appearance }
            val action = compose.onNodeWithContentDescription("发帖")
            action.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            compose.runOnIdle {
                val pixels = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(pixels))
                var goldPixels = 0
                var solidPixels = 0
                for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
                    if (pixels.getPixel(x, y) == gold.toArgb()) goldPixels++
                    if (pixels.getPixel(x, y) == solid.toArgb()) solidPixels++
                }
                assertTrue("$appearance gold pen", goldPixels > 0)
                assertEquals("$appearance no solid gold fill", 0, solidPixels)
            }
            action.performClick()
        }
        compose.runOnIdle { assertEquals(2, clicks) }
    }

    @Test fun ordinaryTopicPrefixesRemainNeutralInBothThemes() {
        var theme by mutableStateOf("light")
        var expected = Color.Unspecified
        compose.setContent {
            NagiTheme(theme) {
                expected = NagiThemeTokens.colors.textSecondary
                FlatTopicCard(
                    topic = Topic("7", "[讨论] 主题标题", "作者", "8", recommended = true),
                    sort = TopicSort.NEWEST,
                    selected = false,
                    selectableMode = false,
                    onDetails = {},
                    onClick = {},
                )
            }
        }
        for (appearance in listOf("light", "dark")) {
            compose.runOnIdle { theme = appearance }
            val layout = textLayout("讨论")
            compose.runOnIdle { assertEquals(expected, layout.layoutInput.style.color) }
            compose.onNodeWithText("精选").assertExists()
        }
    }

    @Test fun longTopicAuthorWrapsOnNarrowLargeText() {
        val author = "这是一个名字非常长的测试论坛老用户_2026"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                NagiTheme {
                    FlatTopicCard(
                        topic = Topic("7", "主题标题", author, "8", createdAt = 1_700_000_000),
                        sort = TopicSort.NEWEST,
                        selected = false,
                        selectableMode = false,
                        onDetails = {},
                        onClick = {},
                    )
                }
            }
        }
        assertNoTextOverflow(author)
    }

    @Test fun solidGoldButtonTextMeetsNormalTextContrastInBothThemes() {
        var theme by mutableStateOf("light")
        var background = Color.Unspecified
        compose.setContent { NagiTheme(theme) {
            background = NagiThemeTokens.colors.goldSolidFill
            CapsuleButton("发送", {}, style = CapsuleStyle.SolidGold)
        } }
        for (appearance in listOf("light", "dark")) {
            compose.runOnIdle { theme = appearance }
            val foreground = textLayout("发送").layoutInput.style.color
            val first = foreground.luminance()
            val second = background.luminance()
            assertTrue("$appearance solid button text needs 4.5:1", (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f) >= 4.5f)
        }
    }

    @Test fun shortCapsuleHasAnIndependent48dpTouchTarget() {
        var clicks = 0
        compose.setContent { NagiTheme { CapsuleButton("1", { clicks++ }) } }
        compose.onNodeWithText("1").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun everySegmentHasAnIndependent48dpTouchTarget() {
        var selected by mutableStateOf(0)
        compose.setContent {
            NagiTheme {
                SegmentedCapsuleControl(listOf("纯文本", "排版", "预览"), selected, { selected = it })
            }
        }
        listOf("纯文本", "排版", "预览").forEach { label ->
            compose.onNodeWithText(label).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
        }
    }

    @Test fun segmentedLabelsRemainReadableAtDoubleFontScale() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                NagiTheme {
                    Column(Modifier.fillMaxWidth()) {
                        SegmentedCapsuleControl(listOf("继续编辑", "排版", "预览"), 2, {})
                    }
                }
            }
        }
        listOf("继续编辑", "排版", "预览").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
            assertNoTextOverflow(label)
        }
    }

    @Test fun forumFavoriteSwitchExposesAndUpdatesItsCheckedState() {
        var checked by mutableStateOf(false)
        compose.setContent { NagiTheme {
            GoldSwitch(checked, { checked = it }, contentDescription = "收藏版块")
        } }
        compose.onNodeWithContentDescription("收藏版块").assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertTrue(checked) }
    }

    @Test fun readerHeaderRetainsAuthorAndMetadataOnNarrowLargeText() {
        val author = "名字很长的测试论坛用户"
        val metadata = "威望: 1.0  发帖: 560"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                NagiTheme {
                    ReaderPost(
                        post = Post("7", 0, author, "8", "", "2025-05-26 17:27", listOf(ContentBlock.Text("正文")),
                            authorLevel = "学徒", authorReputation = "1.0", authorPosts = 560, clientPlatform = "Android"),
                        preferences = ReadingPreferences(),
                        isAuthor = true,
                        title = null,
                        onImage = {},
                    )
                }
            }
        }
        compose.onAllNodesWithText("级别:", substring = true).assertCountEquals(0)
        for (label in listOf(author, metadata, "2025-05-26 17:27", "#0", "楼主")) {
            compose.onNodeWithText(label).assertIsDisplayed()
            assertNoTextOverflow(label)
        }
    }

    @Test fun readerStatsOmitLevelAndOnlyShowAvailableReputationAndPostCount() {
        var post by mutableStateOf(
            Post("7", 0, "测试作者", "8", "", "", listOf(ContentBlock.Text("正文")),
                authorLevel = "学徒", authorReputation = "1.1", authorPosts = 749),
        )
        compose.setContent { NagiTheme {
            ReaderPost(post, ReadingPreferences(), false, null, {})
        } }
        compose.onNodeWithText("威望: 1.1  发帖: 749").assertIsDisplayed()
        compose.runOnIdle { post = post.copy(authorReputation = null) }
        compose.onNodeWithText("发帖: 749").assertIsDisplayed()
        compose.runOnIdle { post = post.copy(authorReputation = "1.1", authorPosts = null) }
        compose.onNodeWithText("威望: 1.1").assertIsDisplayed()
        compose.runOnIdle { post = post.copy(authorReputation = "  ") }
        compose.onAllNodesWithText("威望:", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("发帖:", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("级别:", substring = true).assertCountEquals(0)
        compose.onNodeWithText("正文").assertIsDisplayed()
    }

    private fun assertNoTextOverflow(label: String) {
        val layout = textLayout(label)
        assertFalse("$label exceeds its line limit", layout.multiParagraph.didExceedMaxLines)
        for (line in 0 until layout.lineCount) {
            assertTrue("$label exceeds its width", layout.getLineRight(line) <= layout.size.width + 1f)
            assertTrue("$label exceeds its height", layout.getLineBottom(line) <= layout.size.height + 1f)
            assertFalse("$label is ellipsized", layout.isLineEllipsized(line))
        }
    }

    private fun textLayout(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
}
