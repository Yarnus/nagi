package dev.nagi.app

import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.ComposerScreen
import dev.nagi.app.ui.post.ContentBlocks
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.ComposerState
import dev.nagi.sdk.ContentParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class SmileDeviceTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "smile-ui").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { image ->
            File(output, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun publicPngAndGifResourcesLoadInsideNativeParagraphs() {
        var theme by mutableStateOf("light")
        val blocks = ContentParser.parse("原生行内：[s:ac:哭笑]接着文字[s:a2:doge]与默认 GIF[s:1]。\n[quote]引用中的表情[s:pg:吃瓜][/quote]")
        compose.setContent { NagiTheme(theme) {
            Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(Modifier.padding(16.dp)) {
                    SelectionContainer { ContentBlocks(blocks, ReadingPreferences(), { error("Smiles must not open attachment previews") }) }
                }
            }
        } }
        compose.waitUntil(30_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已加载"))
                .fetchSemanticsNodes().size == 4
        }
        capture("inline-light")
        compose.runOnIdle { theme = "dark" }
        compose.onNodeWithContentDescription("表情：哭笑").assertIsDisplayed()
        capture("inline-dark")
    }

    @Test fun pickerInsertsIntoSyntheticDraftAndReturnsToTypingWithoutSubmitting() {
        var state by mutableStateOf(ComposerState(content = "before after", title = "Synthetic draft"))
        compose.setContent { NagiTheme {
            ComposerScreen(state, ReadingPreferences(), { _, _ -> }, { error("Real writes are prohibited") }, {}, {},
                editValue = { value -> state = state.copy(content = value.text, selection = value.selection.end,
                    selectionStart = value.selection.start, composition = value.composition) })
        } }
        compose.onNodeWithText("before after").performClick().performTextInputSelection(TextRange(7, 6))
        compose.onNodeWithText("表情").performClick()
        compose.onNodeWithText("选择表情").assertIsDisplayed()
        compose.onNodeWithContentDescription("插入表情：blink").performClick()
        capture("picker")
        compose.onNodeWithText("返回输入").performClick()
        compose.onNodeWithText("before[s:ac:blink]after").assertIsDisplayed().performTextInput("!")
        compose.runOnIdle { assertEquals("before[s:ac:blink]!after", state.content) }
        compose.onNodeWithText("发送").assertIsDisplayed()
        capture("editor-ime")
        compose.onNodeWithText("预览").performClick()
        compose.onNodeWithContentDescription("表情：blink").assertIsDisplayed()
        capture("editor-preview")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun copyingAParagraphRetainsSmileCodes() {
        val raw = "copy before[s:ac:哭笑]after"
        compose.setContent { NagiTheme {
            Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(Modifier.padding(16.dp)) {
                    SelectionContainer { ContentBlocks(ContentParser.parse(raw), ReadingPreferences(), {}) }
                }
            }
        } }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.onNodeWithText(raw).performTouchInput { down(Offset(10f, center.y)) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText(raw).performTouchInput {
            moveTo(Offset(width - 1f, center.y), 500)
            up()
        }
        capture("copy-selection")
        compose.onNodeWithText(raw).performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.C)
            keyUp(Key.CtrlLeft)
        }
        compose.runOnIdle {
            val clipboard = instrumentation.targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(raw, clipboard.primaryClip!!.getItemAt(0).coerceToText(instrumentation.targetContext).toString())
        }
    }

    @Test fun existingImageLoaderDecodesGeneratedAnimatedGif() = runBlocking {
        val header = byteArrayOf(71,73,70,56,57,97,1,0,1,0,128.toByte(),0,0,255.toByte(),0,0,0,0,255.toByte())
        val loop = byteArrayOf(33,255.toByte(),11) + "NETSCAPE2.0".toByteArray() + byteArrayOf(3,1,0,0,0)
        fun frame(pixel: Byte) = byteArrayOf(33,249.toByte(),4,0,10,0,0,0,44,0,0,0,0,1,0,1,0,0,2,2,pixel,1,0)
        val bytes = header + loop + frame(68) + frame(76) + byteArrayOf(59)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = Coil.imageLoader(context).execute(ImageRequest.Builder(context).data(ByteBuffer.wrap(bytes)).build())
        assertTrue(result.toString(), result is SuccessResult)
        assertTrue((result as SuccessResult).drawable is Animatable)
    }
}
