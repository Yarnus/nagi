package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.BitmapDrawable
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.SuccessResult
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.ContentBlocks
import dev.nagi.app.ui.post.paragraphText
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.ui.theme.LightSemanticColors
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.ContentParser
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
class SmileRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun placeholdersPreserveCopyTextAndStyleOffsets() {
        val block = ContentParser.parse("前😀[b][s:ac:哭笑][/b][url=https://bbs.nga.cn/]后[s:1][/url]").single() as ContentBlock.Text
        val text = paragraphText(block, Color.Blue)
        assertEquals(block.value, text.text)
        assertEquals(block.spans.size, text.spanStyles.size)
        block.spans.forEach { span -> assertTrue(text.spanStyles.any { it.start == span.start && it.end == span.end }) }
        assertEquals("https://bbs.nga.cn/", (text.getLinkAnnotations(0, text.length).single().item as androidx.compose.ui.text.LinkAnnotation.Url).url)
    }

    @Test fun smilesFlowWithTextAndRepeatedNamesKeepDistinctSlots() = render("light", 1f, false)
    @Test fun darkSmilesKeepOriginalImageColor() = render("dark", 1f, false)
    @Test fun darkLineArtKeepsOriginalPixelsOnReadableBacking() = render("dark", 1f, false, lineArt = true)
    @Config(qualifiers = "w320dp-h850dp")
    @Test fun narrowLargeTextWrapsWithFontRelativeSlots() = render("light", 2f, false)
    @Test fun failedImagesKeepReadableNamesAndStableInlineSlots() = render("light", 1f, true)

    private fun render(theme: String, scale: Float, failed: Boolean, lineArt: Boolean = false) {
        RuntimeEnvironment.setFontScale(scale)
        val drawable = if (lineArt) {
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            for (x in 2..5) for (y in 2..5) bitmap.setPixel(x, y, android.graphics.Color.BLACK)
            BitmapDrawable(RuntimeEnvironment.getApplication().resources, bitmap)
        } else ColorDrawable(android.graphics.Color.RED)
        val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).components {
            add(Interceptor { chain ->
                if (failed) ErrorResult(null, chain.request, IllegalStateException("Unavailable"))
                else SuccessResult(drawable, chain.request, DataSource.MEMORY)
            })
        }.build()
        Coil.setImageLoader(loader)
        val raw = "前[s:ac:哭笑]后[s:ac:哭笑]结束继续阅读和自然换行"
        val block = ContentParser.parse(raw).single() as ContentBlock.Text
        var previews = 0
        lateinit var view: View
        var expectedSize = 0f
        compose.setContent { NagiTheme(theme) {
            view = LocalView.current
            expectedSize = with(LocalDensity.current) { 16.sp.toPx() * 1.6f }
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.width(280.dp)) {
                    SelectionContainer { ContentBlocks(listOf(block), ReadingPreferences(), { previews++ }) }
                }
            }
        } }
        try {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(raw).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val layout = results.single()
            assertEquals(2, layout.placeholderRects.size)
            assertTrue(layout.placeholderRects.all { it != null })
            val first = layout.placeholderRects.first()!!
            assertEquals(expectedSize, first.width, 2f)
            if (scale == 1f) assertEquals(1, layout.lineCount) else assertTrue(layout.lineCount > 1)
            compose.onAllNodesWithContentDescription("表情：哭笑").assertCountEquals(2)
            compose.onAllNodesWithContentDescription("帖子图片，点击查看").assertCountEquals(0)
            compose.onAllNodesWithContentDescription("表情：哭笑").onFirst().performTouchInput { click() }
            compose.runOnIdle { assertEquals(0, previews) }
            val expectedState = if (failed) "加载失败" else "已加载"
            compose.waitUntil(5_000) {
                compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expectedState))
                    .fetchSemanticsNodes().size == 2
            }
            compose.runOnIdle {
                val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(image))
                File("build/ui-captures/smiles-$theme-$scale-$failed-$lineArt.png").apply { parentFile?.mkdirs() }.outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                if (!failed) {
                    val x = first.center.x.toInt()
                    val y = first.center.y.toInt()
                    assertEquals(if (lineArt) android.graphics.Color.BLACK else android.graphics.Color.RED, image.getPixel(x, y))
                    if (lineArt) assertEquals(LightSemanticColors.insetContainer.toArgb(),
                        image.getPixel(first.left.toInt() + 4, first.top.toInt() + 4))
                }
            }
        } finally {
            loader.shutdown()
            Coil.reset()
        }
    }
}
