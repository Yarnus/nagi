package dev.nagi.app

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.*
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.PostReferenceState
import dev.nagi.app.viewmodel.key
import dev.nagi.sdk.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w320dp-h850dp")
class ReplyReferenceCardTest {
    @get:Rule val compose = createComposeRule()
    private val target = PostReference("123", "42", 3)

    @Test fun snapshotIsImmediatelyReadableAndFullOriginalLoadsOnlyOnOpen() {
        var reads = 0
        var located: PostReference? = null
        var states by mutableStateOf(emptyMap<String, PostReferenceState>())
        val quote = ContentBlock.Quote(listOf(ContentBlock.Text("Quoted snippet")), "Author", reference = target)
        compose.setContent { NagiTheme("light") {
            CompositionLocalProvider(LocalPostReferences provides PostReferences(states, { _, _ -> reads++ }, { located = it })) {
                ContentBlocks(listOf(quote, ContentBlock.Text("Current reply")), ReadingPreferences(), {})
            }
        } }
        compose.onNodeWithText("Quoted snippet").assertIsDisplayed()
        compose.onNodeWithText("Current reply").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, reads) }
        compose.onNodeWithText("Quoted snippet").performClick()
        compose.onNodeWithText("引用片段").assertExists()
        compose.runOnIdle {
            assertEquals(1, reads)
            states = mapOf(target.key to PostReferenceState(post = Post("42", 43, "Author", "7", "", "",
                listOf(ContentBlock.Text("Complete original body")))))
        }
        compose.onNodeWithText("完整原帖").assertExists()
        compose.onNodeWithText("Complete original body").assertExists()
        compose.onNodeWithText("定位到原楼层").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals(target, located) }
        compose.onNodeWithText("Complete original body").assertDoesNotExist()
        compose.onNodeWithText("Current reply").assertIsDisplayed()
    }

    @Test fun missingOriginalLoadsOnAppearanceAndErrorHasExplicitRetry() {
        val attempts = mutableListOf<Boolean>()
        var states by mutableStateOf(emptyMap<String, PostReferenceState>())
        compose.setContent { NagiTheme("dark") {
            CompositionLocalProvider(LocalPostReferences provides PostReferences(states, { _, retry -> attempts += retry }, {})) {
                ContentBlocks(listOf(ContentBlock.Quote(emptyList(), "Author", reference = target)), ReadingPreferences(), {})
            }
        } }
        compose.runOnIdle {
            assertEquals(listOf(false), attempts)
            states = mapOf(target.key to PostReferenceState(error = "原文加载失败"))
        }
        compose.onNodeWithText("原文加载失败").performClick()
        compose.onNodeWithText("重试加载原文").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals(listOf(false, false, true), attempts) }
    }

    @Test fun previewDoesNotExpandAncestorQuotesOrLoadImages() {
        assertEquals("My body\n[图片]", referencePreview(listOf(
            ContentBlock.Quote(listOf(ContentBlock.Text("Ancestor body"))),
            ContentBlock.Text("My body"), ContentBlock.Image("https://img.nga.cn/example.png"),
        )))
        assertEquals("First\nSecond", referencePreview(listOf(
            ContentBlock.Text("First\n\n"),
            ContentBlock.Quote(listOf(ContentBlock.Text("Ancestor"))),
            ContentBlock.Text("\n\nSecond"),
        )))
    }

    @Test fun previewDisplaysUpToFiveLinesBeforeEllipsis() {
        val sixLines = (1..6).joinToString("\n") { "Line $it" }
        val quote = ContentBlock.Quote(listOf(ContentBlock.Text(sixLines)), "Author", reference = target)
        compose.setContent { NagiTheme("light") {
            ContentBlocks(listOf(quote), ReadingPreferences(), {})
        } }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(sixLines, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(5, layout.lineCount)
        assertTrue(layout.isLineEllipsized(4))
    }
}
