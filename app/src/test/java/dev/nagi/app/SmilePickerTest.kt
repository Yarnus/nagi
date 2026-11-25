package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import coil.Coil
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ErrorResult
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.ComposerScreen
import dev.nagi.app.ui.post.EditorDockController
import dev.nagi.app.ui.post.EditorDockPanel
import dev.nagi.app.ui.post.ReplyPopup
import dev.nagi.app.ui.post.insertSmile
import dev.nagi.app.ui.post.rememberEditorDockController
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.ComposePhase
import dev.nagi.app.viewmodel.ComposerState
import dev.nagi.app.viewmodel.ReplySession
import dev.nagi.sdk.NgaSmiles
import dev.nagi.sdk.PublishAction
import dev.nagi.sdk.PublishTarget
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
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
class SmilePickerTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var loader: ImageLoader
    @Before fun setupImages() {
        loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).components {
            add(Interceptor { chain -> ErrorResult(null, chain.request, IllegalStateException("Mock image failure")) })
        }.build()
        Coil.setImageLoader(loader)
    }
    @After fun cleanupImages() { loader.shutdown(); Coil.reset() }

    @Test fun insertionReplacesOnlySelectionAndEndsComposition() {
        val smile = NgaSmiles.resolve("ac:哭笑")!!
        val value = TextFieldValue("前😀选中后", TextRange(5, 3), TextRange(3, 5))
        val result = insertSmile(value, smile)
        assertEquals("前😀${smile.code}后", result.text)
        assertEquals(TextRange(3 + smile.code.length), result.selection)
        assertNull(result.composition)
        assertEquals("${result.text}${smile.code}", insertSmile(result.copy(selection = TextRange(result.text.length)), smile).text)
    }

    @Test fun fullEditorInsertsAtSelectionKeepsInputVisibleAndRecordsRecent() = composer("light", 1f, false)
    @Test fun messageEditorUsesTheSameInlinePicker() = composer("dark", 1f, true)
    @Config(qualifiers = "w320dp-h850dp")
    @Test fun pickerWorksAtNarrowWidthAndTwoTimesFontScale() = composer("light", 2f, false)

    private fun composer(theme: String, scale: Float, message: Boolean) {
        RuntimeEnvironment.setFontScale(scale)
        var state by mutableStateOf(ComposerState(content = "前选中后", selectionStart = 1, selection = 3,
            privateMessage = message, newMessage = message, title = "标题", recipients = "Alice"))
        compose.setContent { NagiTheme(theme) {
            ComposerScreen(state, ReadingPreferences(), { _, _ -> }, {}, {}, {}, editValue = { value ->
                state = state.copy(content = value.text, selectionStart = value.selection.start, selection = value.selection.end,
                    composition = value.composition)
            })
        } }
        compose.onNodeWithText("表情").performClick()
        compose.onNodeWithContentDescription("选择表情").assertExists()
        // Input box stays visible above the inline picker instead of being obscured.
        compose.onNodeWithText("前选中后").assertIsDisplayed()
        val entry = compose.onNodeWithContentDescription("插入表情：blink")
        entry.assertIsDisplayed()
        val bounds = entry.getUnclippedBoundsInRoot()
        assertTrue((bounds.right - bounds.left).value >= 48f); assertTrue((bounds.bottom - bounds.top).value >= 48f)
        entry.performClick()
        compose.runOnIdle { assertEquals("前[s:ac:blink]后", state.content) }
        compose.onNodeWithText(state.content).assertIsDisplayed()
        compose.onNodeWithContentDescription("表情分类").performScrollToNode(hasText("默认"))
        compose.onNodeWithText("默认").performClick()
        compose.onNodeWithContentDescription("插入表情：1").performClick()
        compose.runOnIdle { assertEquals("前[s:ac:blink][s:0:1]后", state.content) }
        compose.onNodeWithContentDescription("已插入 2 个表情，点击预览").assertIsDisplayed()

        // Recent tab records both inserted smiles in most-recent-first order.
        compose.onNodeWithContentDescription("表情分类").performScrollToNode(hasText("最近"))
        compose.onNodeWithText("最近").performClick()
        compose.onNodeWithContentDescription("插入表情：1").assertIsDisplayed()
        compose.onNodeWithContentDescription("插入表情：blink").assertIsDisplayed()

        capture("smile-picker-$theme-$scale")
        compose.onNodeWithContentDescription("返回输入").performClick()
        compose.onNodeWithText(state.content).assertExists()
        compose.onNodeWithContentDescription("选择表情").assertDoesNotExist()
        compose.onNodeWithText("预览").performClick()
        compose.onNodeWithContentDescription("表情：blink").assertExists()
    }

    @Test fun replyPickerKeepsInputVisibleAndNeverChangesServerQuotePrefix() = reply(PublishAction.QUOTE)
    @Test fun commentPickerSupportsInlineEchoAndPreview() = reply(PublishAction.COMMENT)

    private fun reply(action: PublishAction) {
        var session by mutableStateOf(ReplySession(PublishTarget(action, topicId = "123", postId = "12"), "回复",
            prefill = "[quote]服务器原文[/quote]", input = TextFieldValue("前后", TextRange(1)), phase = ComposePhase.Editing))
        compose.setContent { NagiTheme {
            ReplyPopup(session, ReadingPreferences(), { session = session.copy(input = it) }, {}, {},
                { session = session.copy(preview = !session.preview) }, {})
        } }
        compose.onNodeWithContentDescription("表情").performClick()
        compose.onNodeWithContentDescription("选择表情").assertExists()
        compose.onNodeWithText("前后").assertIsDisplayed()
        compose.onNodeWithContentDescription("插入表情：blink").performClick()
        compose.runOnIdle {
            assertEquals("[quote]服务器原文[/quote]", session.prefill)
            assertEquals("前[s:ac:blink]后", session.input.text)
            assertTrue(session.content.startsWith(session.prefill))
        }
        compose.onNodeWithText("前[s:ac:blink]后").assertIsDisplayed()
        // Switch straight to preview while keeping the inline emoji dock visible.
        compose.onNodeWithContentDescription("预览").performClick()
        compose.onNodeWithContentDescription("表情：blink").assertIsDisplayed()
        compose.onNodeWithContentDescription("选择表情").assertExists()
        compose.onNodeWithContentDescription("返回输入").performClick()
        compose.onNodeWithContentDescription("选择表情").assertDoesNotExist()
    }

    @Test fun unavailableEditorStatesDisablePickerAndCloseAnOpenPanel() {
        var state by mutableStateOf(ComposerState(content = "正文"))
        compose.setContent { NagiTheme {
            ComposerScreen(state, ReadingPreferences(), { _, _ -> }, {}, {}, {})
        } }
        compose.onNodeWithText("表情").performClick()
        compose.onNodeWithContentDescription("选择表情").assertExists()
        listOf(ComposePhase.Submitting, ComposePhase.Unconfirmed, ComposePhase.Preparing, ComposePhase.Confirmed).forEach { phase ->
            compose.runOnIdle { state = state.copy(phase = phase) }
            compose.onNodeWithContentDescription("选择表情").assertDoesNotExist()
            compose.onNodeWithText("表情").assertIsNotEnabled()
        }
    }

    @Test fun editorDockControllerIsMutuallyExclusiveWithImeForSmileAndColor() {
        var imeVisible by mutableStateOf(true)
        lateinit var controller: EditorDockController
        compose.setContent {
            NagiTheme {
                controller = rememberEditorDockController(
                    editable = true,
                    preview = false,
                    imeVisibleOverride = imeVisible,
                )
            }
        }
        compose.runOnIdle {
            // Requesting smile while IME is open waits until IME hides before displaying dock.
            controller.togglePanel(EditorDockPanel.SMILE)
            assertEquals(EditorDockPanel.SMILE, controller.activeToolbarPanel)
            assertEquals(EditorDockPanel.NONE, controller.visiblePanel)
            imeVisible = false
        }
        compose.runOnIdle {
            assertEquals(EditorDockPanel.SMILE, controller.visiblePanel)
            // Re-opening IME automatically closes the inline smile dock.
            imeVisible = true
        }
        compose.runOnIdle {
            assertEquals(EditorDockPanel.NONE, controller.visiblePanel)
            assertEquals(EditorDockPanel.NONE, controller.activeToolbarPanel)
            // Same mutual exclusion applies to the color dock.
            controller.togglePanel(EditorDockPanel.COLOR)
            assertEquals(EditorDockPanel.NONE, controller.visiblePanel)
            imeVisible = false
        }
        compose.runOnIdle {
            assertEquals(EditorDockPanel.COLOR, controller.visiblePanel)
            imeVisible = true
        }
        compose.runOnIdle {
            assertEquals(EditorDockPanel.NONE, controller.visiblePanel)
            assertEquals(EditorDockPanel.NONE, controller.activeToolbarPanel)
        }
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().last()
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            File("build/ui-captures/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
