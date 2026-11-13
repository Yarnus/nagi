package dev.nagi.app

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.ContentBlocks
import dev.nagi.app.ui.post.contentTextColor
import dev.nagi.app.ui.post.paragraphText
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.ContentColors
import dev.nagi.sdk.ContentParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w320dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContentFormattingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inlineStylesPreserveFontSizeWeightAndCopyText() {
        val block = ContentParser.parse("[color=red][size=150%][b]标题[/b][/size][/color]").single() as ContentBlock.Text
        val text = paragraphText(block, Color.Blue)
        assertEquals("标题", text.text)
        val style = text.spanStyles.single().item
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(1.5f, style.fontSize.value)
        assertNotEquals(Color.Unspecified, style.color)
    }

    @Test fun namedColorsStayReadableAgainstBothContentSurfaces() {
        for (background in listOf(Color.White, Color(0xFFF0F2F5), Color(0xFF18191D), Color(0xFF22242A))) {
            for (name in ContentColors.rgb.keys) {
                val foreground = contentTextColor(name, background)!!
                val light = maxOf(foreground.luminance(), background.luminance())
                val dark = minOf(foreground.luminance(), background.luminance())
                assertTrue("$name against $background", (light + 0.05f) / (dark + 0.05f) >= 4.5f)
            }
        }
        assertNull(contentTextColor("unknown", Color.White))
    }

    @Test fun publicUidLinksUseTheExistingUserCallback() {
        var selected = ""
        val block = ContentParser.parse("[uid=7]作者[/uid]").single() as ContentBlock.Text
        val text = paragraphText(block, Color.Blue, onOpenUser = { selected = it })
        val link = text.getLinkAnnotations(0, text.length).single().item as LinkAnnotation.Url
        link.linkInteractionListener!!.onClick(link)
        assertEquals("7", selected)
    }

    @Test fun headingsListsAlignmentAndRichCellsRenderNatively() {
        val blocks = ContentParser.parse("[h]天气查询[/h][align=right]靠右正文[/align][list=1][*]第一项[*]第二项[/list][table][tr][td1][b]单元格粗体[/b][/td][td][url=https://example.com]单元格链接[/url][/td][/tr][/table]")
        compose.setContent { NagiTheme("light") {
            Column(Modifier.width(300.dp)) { ContentBlocks(blocks, ReadingPreferences(), {}) }
        } }
        compose.onNodeWithText("天气查询").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertCountEquals(1)
        compose.onNodeWithText("第一项").assertIsDisplayed()
        compose.onNodeWithText("2. ").assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("靠右正文").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(TextAlign.End, results.single().layoutInput.style.textAlign)
        results.clear()
        compose.onNodeWithText("单元格粗体").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(FontWeight.Bold, results.single().layoutInput.text.spanStyles.single().item.fontWeight)
        compose.onNodeWithText("单元格链接").assertExists()
    }

    @Test fun darkNarrowLargeTextWrapsWithoutLosingHeadingSemantics() {
        RuntimeEnvironment.setFontScale(2f)
        try {
            compose.setContent { NagiTheme("dark") {
                Column(Modifier.width(280.dp)) {
                    ContentBlocks(ContentParser.parse("[h]把城市天气放进桌面副屏[/h][color=skyblue][size=150%]大字号彩色正文自然换行[/size][/color]"), ReadingPreferences(), {})
                }
            } }
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertCountEquals(1)
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("大字号彩色正文自然换行").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertTrue(results.single().lineCount > 1)
            assertFalse(results.single().hasVisualOverflow)
        } finally {
            RuntimeEnvironment.setFontScale(1f)
        }
    }

    @Test fun mediaAndAttachmentsOpenTheirExactTargets() {
        val opened = mutableListOf<String>()
        compose.setContent { NagiTheme("dark") {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened += uri }
            }) {
                ContentBlocks(ContentParser.parse("[flash=audio]https://example.com/a.mp3?token=public[/flash][attach]https://example.com/a.zip[/attach]"), ReadingPreferences(), {})
            }
        } }
        compose.onNodeWithText("打开音频：a.mp3").performClick()
        compose.onNodeWithText("打开附件：a.zip").performClick()
        assertEquals(listOf("https://example.com/a.mp3?token=public", "https://example.com/a.zip"), opened)
    }
}
