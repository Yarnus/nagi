package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import java.io.File
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.ComposerScreen
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.*
import dev.nagi.sdk.PublishAction
import dev.nagi.sdk.PublishTarget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun imageUploadIsOfferedForNewPostsAndBusyStateDisablesSendAndClose() {
        var state by mutableStateOf(ComposerState(target = PublishTarget(PublishAction.NEW_TOPIC, "7"), title = "Title", content = "Text"))
        compose.setContent { NagiTheme {
            ComposerScreen(state, ReadingPreferences(), { _, _ -> }, {}, {}, {}, uploadImage = {})
        } }
        compose.onNodeWithText("图片").assertIsEnabled()
        compose.runOnIdle { state = state.copy(images = DraftImages(uploading = true)) }
        compose.onNodeWithText("上传中…").assertIsNotEnabled()
        compose.onNodeWithText("发送").assertIsNotEnabled()
        compose.onNodeWithContentDescription("关闭发布帖子").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(images = DraftImages(blocked = true, error = "图片上传结果不明，请勿重复上传。")) }
        compose.onNodeWithText("图片").assertIsNotEnabled()
        compose.onNodeWithText("图片上传结果不明，请勿重复上传。").assertExists()
    }

    @Test fun editPreviewAndKeepDraftOnReturn() {
        var state by mutableStateOf(ComposerState(context = "回复 测试作者 · #12"))
        var closed = false
        compose.setContent {
            NagiTheme {
                ComposerScreen(state, ReadingPreferences(), { text, cursor -> state = state.copy(content = text, selection = cursor) },
                    {}, {}, { closed = true })
            }
        }
        compose.onNodeWithText("发送").assertIsNotEnabled()
        compose.onNodeWithText("写下帖子正文…").performTextInput("中文回复 [b]加粗[/b]")
        compose.onNodeWithText("发送").assertIsEnabled()
        compose.onNodeWithText("预览").performClick()
        compose.onNodeWithText("中文回复 加粗").assertExists()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithContentDescription("关闭发布帖子").performClick()
        compose.onNodeWithText("继续编辑").performClick()
        compose.runOnIdle { assertFalse(closed); assertEquals("中文回复 [b]加粗[/b]", state.content) }
    }

    @Test fun editingPostUsesEditLabelAndKeepsRawMarkupInEditor() {
        val original = "[b]原文[/b]\n[quote]引用[/quote]"
        var state by mutableStateOf(ComposerState(
            target = PublishTarget(PublishAction.MODIFY, topicId = "123", postId = "12"),
            content = original, title = "原标题"))
        compose.setContent { NagiTheme {
            ComposerScreen(state, ReadingPreferences(), { text, cursor -> state = state.copy(content = text, selection = cursor) },
                {}, {}, {})
        } }
        compose.onNodeWithText("编辑回复").assertExists()
        compose.onNodeWithText("原标题").assertExists()
        compose.onNodeWithText(original).assertExists()
        compose.onNodeWithText("保存修改").assertIsEnabled()
        compose.onNodeWithText("发送").assertDoesNotExist()
        compose.onNodeWithContentDescription("关闭编辑回复").performClick()
        compose.onNodeWithText("保留并返回").assertExists()
    }

    @Test fun captureUnconfirmedEditLight() = capture("light", 1f, uncertainEdit = true)
    @Test fun captureUnconfirmedEditDark() = capture("dark", 1f, uncertainEdit = true)
    @Test fun captureComposer() = capture("light", 1f)
    @Test fun captureDarkComposer() = capture("dark", 1f)
    @Test fun captureLargeComposer() = capture("light", 1.5f)
    @Test fun captureLargestComposer() = capture("light", 2f)

    @Test fun captureNewMessage() = capture("light", 1f, message = true)
    @Test fun captureMessageReply() = capture("light", 1f, message = true, reply = true)

    @Test fun privateReplyHasExplicitDiscardAndNoRetentionAction() {
        var state by mutableStateOf(ComposerState(privateMessage = true))
        var closed = false
        compose.setContent { NagiTheme {
            ComposerScreen(state, ReadingPreferences(), { text, cursor -> state = state.copy(content = text, selection = cursor) },
                {}, {}, { closed = true })
        } }
        compose.onNodeWithText("写下私信…").performTextInput("私信回复")
        compose.onNodeWithContentDescription("关闭回复私信").performClick()
        compose.onNodeWithText("保留并返回").assertDoesNotExist()
        compose.onNodeWithText("放弃并返回").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    private fun capture(theme: String, scale: Float, message: Boolean = false, reply: Boolean = false, uncertainEdit: Boolean = false) {
        org.robolectric.RuntimeEnvironment.setFontScale(scale)
        compose.setContent {
                NagiTheme(theme) {
                    ComposerScreen(ComposerState(context = if (uncertainEdit) "编辑原帖" else if (message) "发送给完整用户名" else "发布到 测试版块",
                            target = if (uncertainEdit) PublishTarget(PublishAction.MODIFY, topicId = "123", postId = "12") else null,
                            phase = if (uncertainEdit) ComposePhase.Unconfirmed else ComposePhase.Editing,
                            error = if (uncertainEdit) "暂未收到修改确认，可查看帖子核对，请勿重复提交。" else null,
                            privateMessage = message, newMessage = message && !reply,
                            title = if (reply) "" else "测试标题", recipients = if (message && !reply) "测试收件人" else "",
                            content = "[quote]这是被引用的原文[/quote]\n\n这是一段中文回复，保留阅读上下文与编辑空间。"),
                        ReadingPreferences(), { _, _ -> }, {}, {}, {})
                }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().last()
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val file = File("build/ui-captures/${if (uncertainEdit) "edit-uncertain" else if (message) (if (reply) "message-reply" else "message-new") else "composer"}-400-$theme-$scale.png").apply { parentFile?.mkdirs() }
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun messageEditorCollectsRecipientTitleAndBody() {
        var state by mutableStateOf(ComposerState(privateMessage = true, newMessage = true))
        compose.setContent { NagiTheme {
            ComposerScreen(state, ReadingPreferences(), { text, cursor -> state = state.copy(content = text, selection = cursor) },
                {}, {}, {}, details = { title, recipients -> state = state.copy(title = title, recipients = recipients) })
        } }
        compose.onNodeWithText("新建私信").assertExists()
        compose.onNodeWithText("发送").assertIsNotEnabled()
        compose.onNodeWithText("收件人").performTextInput("Alice")
        compose.onNodeWithText("标题").performTextInput("测试标题")
        compose.onNodeWithText("写下私信…").performTextInput("只进行本地测试")
        compose.onNodeWithText("发送").assertIsEnabled()
    }

    @Test fun unknownOutcomeIsNeutralAndCannotResubmit() {
        var expectedColor = Color.Unspecified
        compose.setContent { NagiTheme {
            expectedColor = NagiThemeTokens.colors.textSecondary
            ComposerScreen(ComposerState(content = "草稿", phase = ComposePhase.Unconfirmed), ReadingPreferences(), { _, _ -> }, {}, {}, {})
        } }
        compose.onNodeWithText("发送").assertIsNotEnabled()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("暂未收到提交确认，可查看帖子核对，请勿重复发送。")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(expectedColor, layouts.single().layoutInput.style.color)
        compose.onNodeWithText("复制内容").assertExists()
        compose.onNodeWithText("发布成功").assertDoesNotExist()
    }

    @Test fun unknownEditDoesNotClaimFailureOrEnableSavingAgain() {
        compose.setContent { NagiTheme {
            ComposerScreen(ComposerState(target = PublishTarget(PublishAction.MODIFY, topicId = "123", postId = "12"),
                content = "修改内容", phase = ComposePhase.Unconfirmed,
                error = "暂未收到修改确认，可查看帖子核对，请勿重复提交。"),
                ReadingPreferences(), { _, _ -> }, {}, {}, {})
        } }
        compose.onNodeWithText("保存修改").assertIsNotEnabled()
        compose.onNodeWithText("暂未收到修改确认，可查看帖子核对，请勿重复提交。").assertExists()
        compose.onNodeWithText("修改已确认").assertDoesNotExist()
    }
}
