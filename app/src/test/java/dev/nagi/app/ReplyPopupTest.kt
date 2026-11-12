package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.ReplyPopup
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.*
import dev.nagi.sdk.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplyPopupTest {
    @get:Rule val compose = createComposeRule()
    private fun session(comment: Boolean = false) = ReplySession(
        PublishTarget(if (comment) PublishAction.COMMENT else PublishAction.REPLY, topicId = "123", postId = "12"),
        "${if (comment) "评论" else "回复"} 测试作者 · #12", phase = ComposePhase.Editing)

    @Test fun replyRequiresDiscardWithoutSaveOption() {
        var state by mutableStateOf(session())
        var closed = false
        compose.setContent { NagiTheme { ReplyPopup(state, ReadingPreferences(), { state = state.copy(input = it) }, {}, {},
            { state = state.copy(preview = !state.preview) }, { closed = true }) } }
        compose.onNodeWithText("写下你的回复…").performTextInput("中文回复 [b]加粗[/b]")
        compose.onNodeWithText("预览").performClick()
        compose.onNodeWithText("中文回复 加粗").assertExists()
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithContentDescription("关闭回复").performClick()
        compose.onNodeWithText("放弃这次回复？").assertExists()
        compose.onNodeWithText("继续编辑").performClick()
        compose.runOnIdle { assertFalse(closed); assertEquals("中文回复 [b]加粗[/b]", state.input.text) }
        compose.onNodeWithContentDescription("关闭回复").performClick()
        compose.onNodeWithText("放弃回复").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
    @Test fun commentClosesWithoutDraftPromptOrToolbar() {
        var state by mutableStateOf(session(true))
        var closed = false
        compose.setContent { NagiTheme { ReplyPopup(state, ReadingPreferences(), { state = state.copy(input = it) }, {}, {}, {}, { closed = true }) } }
        compose.onNodeWithText("写下贴条评论…").performTextInput("评论")
        compose.onNodeWithText("预览").assertDoesNotExist()
        compose.onNodeWithContentDescription("关闭贴条评论").performClick()
        compose.runOnIdle { assertTrue(closed) }
        compose.onNodeWithText("放弃这次回复？").assertDoesNotExist()
    }
    @Test fun quoteContextIsReadOnlyAndUserInputStartsEmpty() {
        var state by mutableStateOf(session().copy(target = session().target.copy(action = PublishAction.QUOTE),
            context = "引用回复 测试作者 · #12", prefill = "[quote]这是被引用的原文[/quote]"))
        var closed = false
        compose.setContent { NagiTheme { ReplyPopup(state, ReadingPreferences(), { state = state.copy(input = it) }, {}, {},
            { state = state.copy(preview = !state.preview) }, { closed = true },
            { state = state.copy(expandedContext = !state.expandedContext) }) } }
        compose.onNodeWithText("写下你的回复…").assertExists()
        compose.onNodeWithText("查看完整引用").performClick()
        compose.onNodeWithText("收起引用").assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithContentDescription("关闭回复").performClick()
        compose.runOnIdle { assertTrue(closed) }
        compose.onNodeWithText("放弃这次回复？").assertDoesNotExist()
    }

    @Test fun failedPreparationDoesNotExposeAnUnusableEditor() {
        val state = session().copy(phase = ComposePhase.Preparing, error = "无法准备回复：访问受限")
        compose.setContent { NagiTheme { ReplyPopup(state, ReadingPreferences(), {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("重新准备").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("发送").assertDoesNotExist()
    }

    @Test fun unconfirmedReplyDisplaysSafeDiagnosticAndDoesNotEnableSending() {
        val message = writeFeedback("暂未收到提交确认，可查看帖子核对，请勿重复发送。",
            WriteResult.Unconfirmed(WriteDiagnostic(httpStatus = 200, flags = 0)))
        val state = session().copy(phase = ComposePhase.Unconfirmed, error = message)
        var expectedColor = Color.Unspecified
        compose.setContent { NagiTheme {
            expectedColor = NagiThemeTokens.colors.textSecondary
            ReplyPopup(state, ReadingPreferences(), {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText(message).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(message).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(expectedColor, layouts.single().layoutInput.style.color)
        compose.onNodeWithText("发送").assertIsNotEnabled()
        compose.onNodeWithText("复制内容").assertExists()
    }

    @Test fun captureUnconfirmedLight() = capture("light", 1f, uncertain = true)
    @Test fun captureUnconfirmedDark() = capture("dark", 1f, uncertain = true)
    @Test fun captureLight() = capture("light", 1f)
    @Test fun captureDark() = capture("dark", 1f)
    @Test fun captureQuote() = capture("light", 1f, quote = true)
    @Test fun captureLarge() = capture("light", 1.5f)
    @Test fun captureLargest() = capture("light", 2f)
    @Config(qualifiers = "w610dp-h850dp")
    @Test fun captureMedium() = capture("light", 1f)
    @Config(qualifiers = "w900dp-h850dp")
    @Test fun captureExpanded() = capture("dark", 1f)
    private fun capture(theme: String, scale: Float, quote: Boolean = false, uncertain: Boolean = false) {
        RuntimeEnvironment.setFontScale(scale)
        val state = session().copy(
            target = session().target.copy(action = if (quote) PublishAction.QUOTE else PublishAction.REPLY),
            context = if (quote) "引用回复 测试作者 · #12" else "回复 测试作者 · #12",
            phase = if (uncertain) ComposePhase.Unconfirmed else ComposePhase.Editing,
            prefill = if (quote) "[quote]这是被引用的原文[/quote]" else "",
            input = androidx.compose.ui.text.input.TextFieldValue(if (quote) "这是一段中文回复，保留阅读上下文与编辑空间。"
                else "[quote]这是被引用的原文[/quote]\n\n这是一段中文回复，保留阅读上下文与编辑空间。"))
        compose.setContent { NagiTheme(theme) { ReplyPopup(state, ReadingPreferences(), {}, {}, {}, {}, {}) } }
        compose.waitForIdle()
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().last()
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val width = RuntimeEnvironment.getApplication().resources.configuration.screenWidthDp
            File("build/ui-captures/${if (uncertain) "reply-uncertain" else if (quote) "quote" else "reply"}-$width-$theme-$scale.png").apply { parentFile?.mkdirs() }.outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
