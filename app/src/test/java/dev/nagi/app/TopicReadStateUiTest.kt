package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import dev.nagi.app.data.TopicReadState
import dev.nagi.app.data.TopicReadStatus
import dev.nagi.app.data.resolveTopicReadStatus
import dev.nagi.app.ui.components.LocalTopicReadStates
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.topic.FlatTopicCard
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
class TopicReadStateUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun resolveTopicReadStatusHandlesAllStates() {
        val topic = Topic("1", "标题", "作者", "7", createdAt = 100L, lastActiveAt = 500L, replies = 15)
        assertEquals(TopicReadStatus.Unread, resolveTopicReadStatus(topic, null))
        assertEquals(TopicReadStatus.Read, resolveTopicReadStatus(topic, TopicReadState("1", 15, 400L)))
        assertEquals(TopicReadStatus.Read, resolveTopicReadStatus(topic, TopicReadState("1", 18, 400L)))
        assertEquals(TopicReadStatus.Updated(5), resolveTopicReadStatus(topic, TopicReadState("1", 10, 400L)))
        assertEquals(TopicReadStatus.Updated(null), resolveTopicReadStatus(topic, TopicReadState("1", -1, 400L)))
        assertEquals(TopicReadStatus.Read, resolveTopicReadStatus(topic, TopicReadState("1", -1, 500L)))
        assertEquals(TopicReadStatus.Read, resolveTopicReadStatus(topic, TopicReadState("1", -1, 0L)))
    }

    @Test fun topicCardsReflectUnreadReadAndUpdatedStatesInBothThemes() {
        var theme by mutableStateOf("light")
        val readStates = mapOf(
            "read" to TopicReadState("read", lastReadReplies = 10, lastReadAt = 100L),
            "updated" to TopicReadState("updated", lastReadReplies = 10, lastReadAt = 100L),
            "many" to TopicReadState("many", lastReadReplies = 10, lastReadAt = 100L),
            "migrated" to TopicReadState("migrated", lastReadReplies = -1, lastReadAt = 100L),
        )
        compose.setContent {
            NagiTheme(theme) {
                CompositionLocalProvider(LocalTopicReadStates provides readStates) {
                    Column {
                        FlatTopicCard(
                            topic = Topic("unread", "未读主题", "作者", "7", replies = 10),
                            sort = TopicSort.NEWEST,
                            selected = false,
                            selectableMode = false,
                            onDetails = {},
                            onClick = {},
                        )
                        FlatTopicCard(
                            topic = Topic("read", "已读主题", "作者", "7", replies = 10),
                            sort = TopicSort.NEWEST,
                            selected = false,
                            selectableMode = false,
                            onDetails = {},
                            onClick = {},
                        )
                        FlatTopicCard(
                            topic = Topic("updated", "更新主题", "作者", "7", replies = 15),
                            sort = TopicSort.NEWEST,
                            selected = false,
                            selectableMode = false,
                            onDetails = {},
                            onClick = {},
                        )
                        FlatTopicCard(
                            topic = Topic("many", "暴增主题", "作者", "7", replies = 160),
                            sort = TopicSort.NEWEST,
                            selected = false,
                            selectableMode = false,
                            onDetails = {},
                            onClick = {},
                        )
                        FlatTopicCard(
                            topic = Topic("migrated", "迁移更新主题", "作者", "7", lastActiveAt = 200L, replies = 12),
                            sort = TopicSort.NEWEST,
                            selected = false,
                            selectableMode = false,
                            onDetails = {},
                            onClick = {},
                        )
                    }
                }
            }
        }

        for (appearance in listOf("light", "dark")) {
            compose.runOnIdle { theme = appearance }
            compose.onNodeWithTag("topic-read").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已读"),
            )
            compose.onNodeWithTag("topic-updated").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "有 5 条新回复"),
            )
            compose.onNodeWithTag("topic-migrated").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "有新回复"),
            )
            compose.onNodeWithText("+5", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("15 条回复，新增 5 条").assertExists()
            compose.onNodeWithText("+99+", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("160 条回复，新增 150 条").assertExists()
            compose.onNodeWithContentDescription("12 条回复，有新动态").assertExists()
        }
    }

    @Test fun readTitleUsesSecondaryColorWhileUnreadUpdatedAndSelectedUsePrimaryColor() {
        var theme by mutableStateOf("light")
        var readState by mutableStateOf<TopicReadState?>(null)
        var selected by mutableStateOf(false)
        var textPrimary = Color.Unspecified
        lateinit var view: android.view.View

        compose.setContent {
            NagiTheme(theme) {
                view = LocalView.current
                textPrimary = NagiThemeTokens.colors.textPrimary
                FlatTopicCard(
                    topic = Topic("7", "测试长标题用于检查实际像素颜色对比是否符合预期", "", "8", replies = 10),
                    sort = TopicSort.NEWEST,
                    selected = selected,
                    selectableMode = selected,
                    readState = readState,
                    onDetails = {},
                    onClick = {},
                )
            }
        }

        for (appearance in listOf("light", "dark")) {
            compose.runOnIdle {
                theme = appearance
                readState = null
                selected = false
            }
            compose.runOnIdle {
                assertTrue("$appearance unread has primary title pixels", viewContainsColor(view, textPrimary))
            }

            compose.runOnIdle {
                readState = TopicReadState("7", lastReadReplies = 10, lastReadAt = 100L)
            }
            compose.runOnIdle {
                assertFalse("$appearance read removes primary title pixels", viewContainsColor(view, textPrimary))
            }

            compose.runOnIdle {
                readState = TopicReadState("7", lastReadReplies = 4, lastReadAt = 100L)
            }
            compose.runOnIdle {
                assertTrue("$appearance updated restores primary title pixels", viewContainsColor(view, textPrimary))
            }

            compose.runOnIdle {
                readState = TopicReadState("7", lastReadReplies = 10, lastReadAt = 100L)
                selected = true
            }
            compose.runOnIdle {
                assertTrue("$appearance selected read restores primary title pixels", viewContainsColor(view, textPrimary))
            }
        }
    }

    @Test fun updatedPillAndSelectedReadTitleStayLegibleOnNarrowDoubleFont() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                NagiTheme("light") {
                    FlatTopicCard(
                        topic = Topic("sel", "[讨论] 窄屏大字号选中已读长标题测试用于验证不会截断或挤压胶囊", "长昵称作者", "7", replies = 128),
                        sort = TopicSort.NEWEST,
                        selected = true,
                        selectableMode = true,
                        readState = TopicReadState("sel", lastReadReplies = 120, lastReadAt = 100L),
                        onDetails = {},
                        onClick = {},
                    )
                }
            }
        }
        compose.onNodeWithText("+8", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("128 条回复，新增 8 条").assertExists()
    }

    private fun viewContainsColor(view: android.view.View, color: Color): Boolean {
        val target = color.toArgb()
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (bitmap.getPixel(x, y) == target) return true
            }
        }
        return false
    }
}
