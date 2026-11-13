package dev.nagi.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dagger.hilt.android.testing.*
import dev.nagi.app.data.ReadingDaoProvider
import dev.nagi.app.data.ReadingPosition
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.ReaderScreen
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.sdk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

private const val READER_TITLE = "[阅读体验] 2026 年折叠屏长帖阅读、图片显示与多层引用排版的实际体验：这是一条需要完整显示并支持复制的长中文标题"

class ReaderFixtureClient : NgaClient by FakeClient() {
    var failPosts = false
    var failPage: Int? = null
    var emptyPosts = false
    var bodyText: String? = null
    var totalPages = 2
    var gate: CompletableDeferred<Unit>? = null
    val requests = mutableListOf<Pair<Int, String?>>()
    private val image by lazy {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "reader-fixture.png")
        val bitmap = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(221, 231, 236))
        Canvas(bitmap).drawRect(60f, 80f, 540f, 220f, android.graphics.Paint().apply { color = Color.rgb(172, 194, 205) })
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        file.toURI().toString()
    }
    override suspend fun getPosts(topicId: String, page: Int, authorId: String?): PostPage {
        requests += page to authorId
        gate?.await()
        if (failPosts || page == failPage) throw NgaException.Network()
        val posts = (0..4).map { index ->
            val floor = if (page == 1) index else 30 + index
            Post("$page:$index", floor, if (index % 2 == 0) "测试楼主" else "这是一位名字很长的论坛测试用户", if (index % 2 == 0) "7" else "8", "", "2026-11-05 16:27",
                when (index) {
                    0 -> listOf(ContentBlock.Text(bodyText ?: "正文应当是页面主角。完整标题、作者与楼层边界需要清晰，但不要给每层回复都套上厚重的独立卡片。"), ContentBlock.Image(image))
                    1 -> ContentParser.parse("[quote][b]Reply to [pid=101,202,1]Reply[/pid] Post by [uid=7]测试楼主[/uid] (2026-11-05 16:27)[/b]引用正文：保持中文阅读节奏。[quote]第二层引用。[quote]第三层引用仍然需要有足够的正文宽度。[/quote][/quote][/quote]当前回复：引用与回复必须分层，不能混成一段文字。")
                    2 -> listOf(ContentBlock.Text("楼主再次回复时，也应显示楼主标识，而不只是把零楼称为楼主。"))
                    3 -> listOf(ContentBlock.Code("val layout = ReaderLayout(title = fullTitle)"), ContentBlock.Table(listOf(listOf("设备", "阅读体验"), listOf("手机", "单栏")).map { row -> row.map { listOf(ContentBlock.Text(it)) } }), ContentBlock.Collapse("补充说明", listOf(ContentBlock.Text("折叠内容保持原有状态恢复行为。"))))
                    else -> listOf(ContentBlock.Text("本页最后一层回复，不应被底部翻页控件遮挡。"))
                })
        }
        return PostPage(if (emptyPosts) emptyList() else posts.filter { authorId == null || it.authorId == authorId }, READER_TITLE, "7", page, 35, page < 2, 30, totalPages)
    }
}

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = "w1200dp-h1200dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderPresentationTest {
    private val fixture = ReaderFixtureClient()
    @get:Rule(order = 0) val clientRule = object : ExternalResource() {
        override fun before() { FakeAppModule.clientOverride = fixture }
        override fun after() { FakeAppModule.clientOverride = null }
    }
    @get:Rule(order = 1) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()
    private var width by mutableIntStateOf(400)
    private var height by mutableIntStateOf(860)
    private var scale by mutableFloatStateOf(1f)
    private var theme by mutableStateOf("light")
    private lateinit var view: View
    @Inject lateinit var daos: ReadingDaoProvider
    @Before fun inject() { hilt.inject() }

    private fun showReader() {
        compose.onNodeWithText("Forum").assertExists()
        compose.runOnIdle {
            compose.activity.setContent {
                view = LocalView.current
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    NagiTheme(theme) {
                        Layout(content = { ReaderScreen("reader", ReadingPreferences(), back = {}) }) { children, _ ->
                            val w = width.dp.roundToPx(); val h = height.dp.roundToPx()
                            val child = children.single().measure(Constraints.fixed(w, h))
                            layout(w, h) { child.place(0, 0) }
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("返回").assertExists()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val density = view.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap((width * density).roundToInt(), (height * density).roundToInt(), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val phase = System.getenv("NAGI_CAPTURE_PHASE") ?: "check"
        val output = File("build/ui-captures/reader-$phase/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun replyCapsuleFloatsOverFullHeightViewportWithoutReservingABar() {
        showReader()
        val list = compose.onNode(hasScrollToIndexAction())
        compose.onNodeWithTag("reader-reply-bar").assertDoesNotExist()
        for (floor in listOf(0, 1, 2, 3)) {
            list.performScrollToIndex(floor)
            val viewport = list.getUnclippedBoundsInRoot()
            val reply = compose.onNodeWithContentDescription("回复帖子")
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            assertEquals(height.dp, viewport.bottom - viewport.top)
            assertTrue(reply.bottom <= viewport.bottom)
            assertTrue(reply.right <= viewport.right)
            assertTrue("A single reply action must not become a full-width bar", reply.right - reply.left < 160.dp)
        }
    }

    @Test fun topicReplyAffordanceStaysInsideReaderAndReturnsToSameFloor() {
        showReader()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToIndex(2)
        compose.onNodeWithContentDescription("回复帖子").assertIsDisplayed()
        capture("topic-reply-button")
        compose.onNodeWithContentDescription("回复帖子").performClick()
        compose.onNodeWithText("回复帖子").assertExists()
        compose.onNodeWithContentDescription("关闭回复").performClick()
        compose.onNodeWithText("#2").assertIsDisplayed()
    }

    @Test fun fullTitleAndFloorHeadersAreReadableAndImagesRemainOpenable() {
        showReader()
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(READER_TITLE).assertIsDisplayed().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
        assertFalse(results.single().hasVisualOverflow)
        compose.onNodeWithText("#0").assertExists()
        compose.onNodeWithContentDescription("帖子图片，点击查看").performScrollTo().performClick()
        compose.onNodeWithContentDescription("帖子图片").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("回复 测试楼主").assertIsDisplayed()
        compose.onNodeWithText("第三层引用仍然需要有足够的正文宽度。").assertIsDisplayed()
        compose.onAllNodes(hasText("[pid=", substring = true) or hasText("[uid=", substring = true)).assertCountEquals(0)
    }

    @Test fun themeMenuPreservesLocalFavoriteAndAuthorFilterSemantics() {
        showReader()
        compose.onNodeWithContentDescription("主题操作").performClick()
        compose.onNodeWithText("本地收藏").performClick()
        compose.onNodeWithContentDescription("主题操作").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已收藏"))
        compose.onNodeWithContentDescription("主题操作").performClick()
        compose.onNodeWithText("只看楼主").performClick()
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "只看楼主"))
        assertEquals(1 to "7", fixture.requests.last())
        compose.onNodeWithText("这是一位名字很长的论坛测试用户").assertDoesNotExist()
        compose.onNodeWithContentDescription("主题操作").performClick()
        compose.onNodeWithText("查看全部").performClick()
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
        assertEquals(1 to null, fixture.requests.last())
    }

    @Test fun authorFilterKeepsPageControlsInsideNarrowLargeTextViewport() {
        compose.runOnIdle { width = 320; scale = 2f }
        showReader()
        compose.onNodeWithContentDescription("主题操作").performClick()
        compose.onNodeWithText("只看楼主").performClick()
        val back = compose.onNodeWithContentDescription("返回").getUnclippedBoundsInRoot()
        val page = compose.onNodeWithText("1 / 2").getUnclippedBoundsInRoot()
        val menu = compose.onNodeWithContentDescription("主题操作").getUnclippedBoundsInRoot()
        assertTrue(back.right <= page.left)
        assertTrue(page.right <= menu.left)
        assertTrue(menu.right <= 320.dp)
        compose.onNodeWithText("1 / 2").performClick()
        compose.onNodeWithText("跳转").assertExists()
    }

    @Test fun restoredPostIndexSurvivesResizeAndActivityRecreation() {
        showReader()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNode(hasScrollToIndexAction()).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 24f) }
        compose.waitUntil { runBlocking { daos.forUser(null).position("reader") == ReadingPosition("reader", 1, 1, 24) } }
        compose.runOnIdle { width = 900; height = 820 }
        compose.onNodeWithText("#1").assertIsDisplayed()
        compose.runOnIdle { width = 400; height = 860 }
        compose.onNodeWithText("#1").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        showReader()
        compose.onNodeWithText("#1").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(ReadingPosition("reader", 1, 1, 24), runBlocking { daos.forUser(null).position("reader") })
        }
    }

    @Test fun errorPhrasesInPostContentAreNotReaderErrorStates() {
        val text = "讨论加载失败、无法识别 NGA 响应和重试按钮，不代表阅读页面发生错误。"
        fixture.bodyText = text
        showReader()
        compose.onNodeWithText(text).assertIsDisplayed()
        compose.onNodeWithContentDescription("主题操作").assertExists()
        compose.onNodeWithTag("reader-error").assertDoesNotExist()
    }

    @Test fun failedAutoAppendKeepsContentAndRetriesTheFailedPage() {
        fixture.failPage = 2
        showReader()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        compose.onNodeWithText("加载失败，请检查网络后重试，点击重试").assertExists()
        compose.onNodeWithTag("reader-error").assertExists()
        compose.onNodeWithText(READER_TITLE).assertExists()
        fixture.failPage = null
        compose.onNodeWithText("加载失败，请检查网络后重试，点击重试").performClick()
        compose.waitForIdle()
        assertEquals(2 to null, fixture.requests.last())
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(6)
        compose.onNodeWithText("#31").assertExists()
        compose.onNodeWithTag("reader-error").assertDoesNotExist()
    }

    @Test fun scrollingNearTheEndAppendsTheNextPageOnce() {
        showReader()
        compose.waitForIdle()
        assertEquals(listOf(1 to null), fixture.requests)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        compose.waitForIdle()
        assertEquals(listOf(1 to null, 2 to null), fixture.requests)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(6)
        compose.onNodeWithText("#31").assertIsDisplayed()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
    }

    @Test fun pageJumpUsesValidatedInputWithoutASlider() {
        showReader()
        compose.onNodeWithText("1 / 2").performClick()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetProgress)).assertCountEquals(0)
        for (invalid in listOf("", "0", "3")) {
            compose.onNode(hasSetTextAction()).performTextReplacement(invalid)
            compose.onNodeWithText("跳转").assertIsNotEnabled()
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("2")
        compose.onNodeWithText("跳转").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(2 to null, fixture.requests.last())
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
    }

    @Test fun unknownTotalAllowsPageInputWithoutOfferingFloorJumps() {
        fixture.totalPages = 0
        showReader()
        compose.onNodeWithContentDescription("第 1 页").performClick()
        compose.onNodeWithText("跳楼").assertDoesNotExist()
        compose.onNodeWithText("末页").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextReplacement("2")
        compose.onNodeWithText("跳转").performClick()
        compose.waitForIdle()
        assertEquals(2 to null, fixture.requests.last())
        compose.onNodeWithContentDescription("第 2 页").assertIsDisplayed()
    }

    @Test fun pageIndicatorOpensJumpSheetAndFloorJumpLoadsTheServerPage() {
        showReader()
        compose.onNodeWithText("1 / 2").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("跳楼").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("31")
        compose.onNodeWithText("跳转").performClick()
        compose.waitForIdle()
        assertEquals(2 to null, fixture.requests.last())
        compose.onNodeWithText("#31").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("加载第 1 页").performClick()
        compose.waitForIdle()
        assertEquals(1 to null, fixture.requests.last())
        compose.onNodeWithText("#0").assertExists()
    }

    @Test fun initialLoadingFailureAndEmptyPageStayActionable() {
        val gate = CompletableDeferred<Unit>()
        fixture.gate = gate
        showReader()
        compose.onNodeWithText("正在加载帖子…").assertIsDisplayed()
        fixture.failPosts = true
        compose.runOnIdle { gate.complete(Unit) }
        compose.onNodeWithText("加载失败，请检查网络后重试").assertIsDisplayed()
        compose.onNodeWithTag("reader-error").assertIsDisplayed()
        fixture.failPosts = false
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithContentDescription("主题操作").assertExists()
        compose.onNodeWithTag("reader-error").assertDoesNotExist()
        fixture.emptyPosts = true
        compose.onNodeWithContentDescription("主题操作").performClick()
        compose.onNodeWithText("刷新").performClick()
        compose.onNodeWithText("本页没有可显示的帖子").assertIsDisplayed()
    }

    @Test fun finalReplyCanScrollFullyAboveTheCapsuleAtLargeFont() {
        compose.runOnIdle { width = 320; scale = 2f }
        showReader()
        compose.onNodeWithText("1 / 2").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("2")
        compose.onNodeWithText("跳转").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        val body = compose.onNodeWithText("本页最后一层回复，不应被底部翻页控件遮挡。")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val reply = compose.onNodeWithContentDescription("回复帖子")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("The final reply must be reachable above the floating capsule", body.bottom <= reply.top)
        compose.onNodeWithText("2 / 2").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("reader-reply-bar").assertDoesNotExist()
    }

    @Test fun scrollingHidesReplyActionWithoutChangingViewportOrSlotBounds() {
        showReader()
        val list = compose.onNode(hasScrollToIndexAction())
        val viewport = list.getUnclippedBoundsInRoot()
        val slot = compose.onNodeWithTag("reader-reply-slot").getUnclippedBoundsInRoot()
        list.performTouchInput {
            down(center)
            moveTo(center - androidx.compose.ui.geometry.Offset(0f, 160f), delayMillis = 100)
        }
        compose.onNodeWithContentDescription("回复帖子").assertDoesNotExist()
        assertEquals(viewport, list.getUnclippedBoundsInRoot())
        assertEquals(slot, compose.onNodeWithTag("reader-reply-slot").getUnclippedBoundsInRoot())
        list.performTouchInput { up() }
        compose.onNodeWithContentDescription("回复帖子").assertIsDisplayed().assertHasClickAction()
        assertEquals(viewport, list.getUnclippedBoundsInRoot())
        assertEquals(slot, compose.onNodeWithTag("reader-reply-slot").getUnclippedBoundsInRoot())
    }

    @Test fun captureReaderMatrix() {
        showReader()
        capture("main-400-light")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        capture("replies-400-light")
        compose.runOnIdle { theme = "dark" }
        capture("replies-400-dark")
        compose.runOnIdle { width = 320; height = 880; scale = 1.5f; theme = "light" }
        capture("replies-320-light-1.5")
        compose.runOnIdle { width = 400; height = 920; scale = 2f }
        capture("replies-400-light-2.0")
        compose.runOnIdle { width = 610; height = 860; scale = 1f }
        capture("replies-610-light")
        compose.runOnIdle { width = 900; height = 820 }
        capture("replies-900-light")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.runOnIdle { width = 400; height = 860; theme = "dark" }
        capture("main-400-dark")
        compose.runOnIdle { width = 320; height = 880; scale = 1.5f; theme = "light" }
        capture("main-320-light-1.5")
        compose.runOnIdle { width = 400; height = 920; scale = 2f }
        capture("main-400-light-2.0")
    }
}
