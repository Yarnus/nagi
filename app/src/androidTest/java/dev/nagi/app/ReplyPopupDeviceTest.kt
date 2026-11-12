package dev.nagi.app

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.ReplyPopup
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.*
import dev.nagi.sdk.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReplyPopupDeviceTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "interaction-bc").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { image ->
            java.io.File(output, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun quoteSeparatesContextFromKeyboardInput() {
        var session by mutableStateOf(ReplySession(PublishTarget(PublishAction.QUOTE, topicId = "123", postId = "0"),
            "引用回复 测试作者 · #0", prefill = "[quote]这是只读的引用上下文。[/quote]", phase = ComposePhase.Editing))
        compose.setContent { NagiTheme {
            Surface(Modifier.fillMaxSize()) {}
            ReplyPopup(session, ReadingPreferences(), { session = session.copy(input = it) }, {}, {},
                { session = session.copy(preview = !session.preview) }, {},
                { session = session.copy(expandedContext = !session.expandedContext) })
        } }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithText("写下你的回复…").performClick().performTextInput("Mock quote reply")
        compose.onNodeWithText("发送").assertIsDisplayed().assertIsEnabled()
        capture("quote-ime")
        compose.onNodeWithText("查看完整引用").performClick()
        compose.onNodeWithText("收起引用").assertIsDisplayed()
    }

    @Test fun newMessageKeepsRecipientTitleAndSendReachableWithKeyboard() {
        var state by mutableStateOf(ComposerState(privateMessage = true, newMessage = true))
        compose.setContent { NagiTheme {
            dev.nagi.app.ui.post.ComposerScreen(state, ReadingPreferences(),
                { text, cursor -> state = state.copy(content = text, selection = cursor) }, {}, {}, {},
                { title, recipients -> state = state.copy(title = title, recipients = recipients) })
        } }
        compose.onNodeWithText("收件人完整用户名（空格分隔）").performTextInput("SyntheticUser")
        compose.onNodeWithText("标题").performTextInput("Synthetic title")
        compose.onNodeWithText("写下私信…").performClick().performTextInput("Mock message body")
        compose.onNodeWithText("发送").assertIsDisplayed().assertIsEnabled()
        capture("new-message-ime")
    }

    @Test fun commentBackHidesKeyboardThenDiscards() {
        var session by mutableStateOf<ReplySession?>(ReplySession(
            PublishTarget(PublishAction.COMMENT, topicId = "123", postId = "12"), "评论 测试作者 · #12",
            phase = ComposePhase.Editing))
        compose.setContent { NagiTheme {
            Surface(Modifier.fillMaxSize()) {}
            session?.let { value -> ReplyPopup(value, ReadingPreferences(), { session = value.copy(input = it) }, {}, {}, {}, { session = null }) }
        } }
        compose.onNodeWithText("写下贴条评论…").performClick().performTextInput("Mock comment")
        compose.onNodeWithText("发送").assertIsDisplayed().assertIsEnabled()
        fun back() {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close()
            Thread.sleep(600)
            compose.waitForIdle()
        }
        back()
        compose.runOnIdle { assertNotNull(session) }
        back()
        compose.runOnIdle { assertNull(session) }
    }
}
