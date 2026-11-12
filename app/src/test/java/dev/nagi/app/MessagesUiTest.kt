package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.MessagesContent
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.MessagesState
import dev.nagi.sdk.MessageConversation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessagesUiTest {
    @get:Rule val compose = createComposeRule()
    private val items = listOf(
        MessageConversation("1", "你的发言被评分", "0", "#SYSTEM#", 0, 1700000000, 65, emptyList()),
        MessageConversation("2", "关于阅读排版的一点建议", "7", "测试读者", 0, 1700000000, 4, emptyList()),
        MessageConversation("3", "[讨论] 折叠屏展开后的 Android 原生阅读体验与长标题换行", "8", "很长很长的测试参与者名称", 0, 1700000000, 2, emptyList()),
    )
    @Test fun emptyInbox() {
        compose.setContent { NagiTheme { MessagesContent(MessagesState(), ReadingPreferences(), {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("暂无短消息").assertExists()
    }
    @Test fun failedInboxCanRetry() {
        var retried = false
        compose.setContent { NagiTheme { MessagesContent(MessagesState(error = "短消息网络请求失败，请重试"),
            ReadingPreferences(), {}, {}, { retried = true }, {}, {}) } }
        compose.onNodeWithText("暂无短消息").assertDoesNotExist()
        compose.onNodeWithText("重试").performClick()
        compose.runOnIdle { assertEquals(true, retried) }
    }
    @Test fun detailShowsAuthorContentAndPagination() {
        val state = MessagesState(conversationId = "1", hasMore = true,
            participants = listOf(dev.nagi.sdk.MessageParticipant("7", "测试读者")),
            posts = listOf(dev.nagi.sdk.MessagePost("90", "7", "测试标题", listOf(dev.nagi.sdk.ContentBlock.Text("合成私信正文")), 1700000000)))
        var loaded = false
        compose.setContent { NagiTheme { MessagesContent(state, ReadingPreferences(), {}, {}, {}, { loaded = true }, {}) } }
        compose.onNodeWithText("测试读者").assertExists()
        compose.onNodeWithText("合成私信正文").assertExists()
        compose.onNodeWithContentDescription("回复私信").assertIsEnabled()
        compose.onNodeWithText("加载更多").performClick()
        compose.runOnIdle { assertEquals(true, loaded) }
    }
    @Test fun lightInbox() = capture("light", 1f)
    @Test fun darkInbox() = capture("dark", 1f)
    @Test fun largeInbox() = capture("light", 2f)
    @Config(qualifiers = "w320dp-h850dp")
    @Test fun narrowInbox() = capture("light", 1.5f)
    @Config(qualifiers = "w610dp-h850dp")
    @Test fun mediumInbox() = capture("light", 1f)
    @Config(qualifiers = "w900dp-h850dp")
    @Test fun wideInbox() = capture("dark", 1f)

    private fun capture(theme: String, scale: Float) {
        RuntimeEnvironment.setFontScale(scale)
        var opened: String? = null
        var refreshed = false
        var composing = false
        compose.setContent { NagiTheme(theme) {
            MessagesContent(MessagesState(conversations = items), ReadingPreferences(), {}, { opened = it },
                { refreshed = true }, {}, { composing = true })
        } }
        compose.onNodeWithText(items[1].title).performClick()
        compose.runOnIdle { assertEquals("2", opened) }
        compose.onNodeWithContentDescription("65 条消息").assertExists()
        compose.onNodeWithContentDescription("刷新短消息").performClick()
        compose.onNodeWithContentDescription("新建私信").performClick()
        compose.runOnIdle {
            assertEquals(true, refreshed)
            assertEquals(true, composing)
            val view = WindowInspector.getGlobalWindowViews().last()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/ui-captures/messages-${view.width}-$theme-$scale.png").apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
