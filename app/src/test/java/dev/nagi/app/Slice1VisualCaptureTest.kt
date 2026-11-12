package dev.nagi.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.nagi.app.data.PreferenceStore
import dev.nagi.app.navigation.NagiApp
import dev.nagi.sdk.CommunityItem
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.Forum
import dev.nagi.sdk.MessageConversationPage
import dev.nagi.sdk.MessagePostPage
import dev.nagi.sdk.MessageRequest
import dev.nagi.sdk.PublishPreparation
import dev.nagi.sdk.PublishTarget
import dev.nagi.sdk.NgaClient
import dev.nagi.sdk.NgaException
import dev.nagi.sdk.NgaUser
import dev.nagi.sdk.Post
import dev.nagi.sdk.PostRating
import dev.nagi.sdk.PostPage
import dev.nagi.sdk.PublishRequest
import dev.nagi.sdk.Topic
import dev.nagi.sdk.TopicPage
import dev.nagi.sdk.UserReplyPage
import dev.nagi.sdk.TopicSort
import dev.nagi.sdk.WriteResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.math.roundToInt

private fun epochAt(month: Int, day: Int, hour: Int, minute: Int): Long =
    ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, ZoneId.systemDefault()).toEpochSecond()

class FixtureNgaClient(
    var failTopics: Boolean = false,
    var emptySearch: Boolean = false,
) : NgaClient {
    var topicsGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var failMore: Boolean = false
    val searchScopes = mutableListOf<Pair<String?, String?>>()
    val forums = listOf(
        Forum("436", "家居装修", "生活与消费", "居家生活"),
        Forum("7", "网事杂谈", "综合讨论", "晴风村"),
        Forum("459", "消费电子", "数码科技", "硬件设备", collectionId = "1024"),
    )

    val topics = listOf(
        Topic(
            id = "101",
            title = "装修避坑经验汇总",
            author = "板凳超人",
            authorId = "1001",
            createdAt = epochAt(11, 3, 22, 9),
            lastActiveAt = epochAt(11, 4, 16, 11),
            replies = 1284,
            recommended = true,
        ),
        Topic(
            id = "102",
            title = "[选购求助] 2026 年双十一 Pixel 9 Pro、Galaxy Z Fold 8 与 Vivo X200 屏幕护眼和弱光续航实测对比讨论",
            author = "这是一个名字非常长的测试论坛老用户_2026",
            authorId = "1002",
            createdAt = epochAt(11, 4, 9, 20),
            lastActiveAt = epochAt(11, 4, 16, 5),
            replies = 28,
        ),
        Topic(
            id = "103",
            title = "拼多多百亿补贴入手 TOTO 智能马桶盖需要注意哪些水电配件尺寸？",
            author = "沙加传说",
            authorId = "1003",
            createdAt = 0L,
            lastActiveAt = epochAt(11, 4, 15, 47),
            replies = 0,
        ),
        Topic(
            id = "104",
            title = "手持花洒增压效果实测",
            author = "Nanozzle",
            authorId = "1004",
            createdAt = 0L,
            lastActiveAt = 0L,
            replies = 569,
        ),
        Topic(
            id = "105",
            title = "书房人体工学椅与升降桌高度搭配指南（175cm 参考）",
            author = "topcai69",
            authorId = "1005",
            createdAt = epochAt(10, 28, 14, 5),
            lastActiveAt = epochAt(11, 4, 14, 50),
            replies = 42,
        ),
    )

    override suspend fun getForums(): List<Forum> = forums

    override suspend fun getTopics(forumId: String, page: Int, collectionId: String?, sort: TopicSort): TopicPage {
        topicsGate?.await()
        if (failTopics || (failMore && page > 1)) throw NgaException.Network()
        val ordered = if (sort == TopicSort.NEWEST) topics.sortedByDescending { it.createdAt } else topics
        val pageItems = if (page == 1) ordered else listOf(
            Topic("201", "第二页加载主题：厨下净水器滤芯长期成本对比", "suidoriimu", "1006", epochAt(11, 1, 10, 0), epochAt(11, 4, 12, 30), 17)
        )
        return TopicPage(pageItems, page = page, total = 35, hasMore = page < 2)
    }

    override suspend fun getPosts(topicId: String, page: Int, authorId: String?): PostPage {
        val selected = topics.firstOrNull { it.id == topicId } ?: topics[1]
        return PostPage(
            items = listOf(
                Post(
                    id = "$topicId:0",
                    floor = 0,
                    author = selected.author,
                    authorId = selected.authorId,
                    avatar = "",
                    date = "2026-11-04 09:20",
                    content = listOf(
                        ContentBlock.Text("主要关心内屏低亮度下的阅读舒适度，以及分屏浏览长帖时的发热与续航表现，欢迎有实际使用体验的朋友补充。"),
                    ),
                ),
                Post(
                    id = "$topicId:1",
                    floor = 1,
                    author = "好好刷牙",
                    authorId = "2002",
                    avatar = "",
                    date = "2026-11-04 10:15",
                    content = listOf(
                        ContentBlock.Text("日常文字阅读开浅色或柔和深色都很稳，建议把字号调到 16sp 以上。"),
                    ),
                ),
            ),
            title = selected.title,
            authorId = selected.authorId,
            page = page,
            total = 2,
            hasMore = false,
        )
    }

    override suspend fun searchTopics(keyword: String, forumId: String?, page: Int, collectionId: String?): TopicPage {
        searchScopes += forumId to collectionId
        if (failTopics) throw NgaException.Network()
        if (emptySearch) return TopicPage(emptyList(), page = 1, total = 0, hasMore = false)
        return TopicPage(topics.take(3), page = page, total = 3, hasMore = false)
    }

    override suspend fun findUser(username: String) = NgaUser("7", username, "", 12)
    override suspend fun getUser(userId: String) = NgaUser(userId, "测试用户", "", 12)
    override suspend fun getUserTopics(userId: String, page: Int) = TopicPage(emptyList(), page, 0, false)
    override suspend fun getUserReplies(userId: String, page: Int) = UserReplyPage(emptyList(), page, false)
    override suspend fun getNotifications(since: Long) = emptyList<CommunityItem>()
    override suspend fun getPrivateMessages(page: Int) = emptyList<CommunityItem>()
    override suspend fun getMessageConversations(page: Int): MessageConversationPage = error("Unused")
    override suspend fun getMessagePosts(conversationId: String, page: Int): MessagePostPage = error("Unused")
    override suspend fun sendMessage(request: MessageRequest): WriteResult = error("Unused")
    override suspend fun ratePost(topicId: String, postId: String, rating: PostRating): WriteResult = error("Unused")
    override suspend fun preparePublish(target: PublishTarget): PublishPreparation = error("Unused")
    override suspend fun publish(request: PublishRequest): WriteResult = WriteResult.Unconfirmed()
}

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = "w1200dp-h1200dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Slice1VisualCaptureTest {
    private val fixture = FixtureNgaClient()

    @get:Rule(order = 0)
    val clientRule = object : ExternalResource() {
        override fun before() { FakeAppModule.clientOverride = fixture }
        override fun after() { FakeAppModule.clientOverride = null }
    }

    @get:Rule(order = 1) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var preferenceStore: PreferenceStore

    @Before fun setUp() { hilt.inject() }
    @After fun tearDown() { FakeAppModule.clientOverride = null }

    @Test fun captureVisualMatrix() {
        val phase = System.getenv("NAGI_CAPTURE_PHASE") ?: System.getProperty("nagi.capture.phase") ?: "check"
        val outDir = if (phase == "before" || phase == "after") {
            File("../docs/design/evidence/grouped-topics/$phase")
        } else {
            File("build/ui-captures/slice1-$phase")
        }
        outDir.mkdirs()

        var widthDp by mutableIntStateOf(400)
        var heightDp by mutableIntStateOf(860)
        var fontScale by mutableFloatStateOf(1.0f)
        var sizeClass by mutableStateOf(WindowWidthSizeClass.Compact)
        lateinit var hostView: android.view.View

        compose.runOnIdle {
            compose.activity.setContent {
                hostView = LocalView.current
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale)) {
                    Layout(
                        content = { NagiApp(sizeClass) },
                        modifier = Modifier,
                    ) { measurables, _ ->
                        val pxW = widthDp.dp.roundToPx()
                        val pxH = heightDp.dp.roundToPx()
                        val placeable = measurables.first().measure(Constraints.fixed(pxW, pxH))
                        layout(pxW, pxH) { placeable.place(0, 0) }
                    }
                }
            }
        }

        fun setAppearance(w: Int, h: Int, theme: String, scale: Float = 1.0f) {
            runBlocking { preferenceStore.theme(theme) }
            compose.runOnIdle {
                widthDp = w
                heightDp = h
                fontScale = scale
                sizeClass = when {
                    w < 600 -> WindowWidthSizeClass.Compact
                    w < 840 -> WindowWidthSizeClass.Medium
                    else -> WindowWidthSizeClass.Expanded
                }
            }
            compose.waitForIdle()
        }

        fun saveCapture(name: String) {
            compose.waitForIdle()
            val density = hostView.resources.displayMetrics.density
            val pxW = (widthDp * density).roundToInt().coerceAtLeast(1)
            val pxH = (heightDp * density).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(pxW, pxH, Bitmap.Config.ARGB_8888)
            hostView.draw(Canvas(bitmap))
            File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

        fun performBack() {
            compose.onNode(hasContentDescription("返回") or hasText("返回")).performClick()
            compose.waitForIdle()
        }

        setAppearance(400, 860, "light", 1.0f)
        compose.onNodeWithText("家居装修").assertExists()
        saveCapture("chrome-directory-400-light-1.0")
        setAppearance(320, 880, "light", 1.5f)
        saveCapture("directory-320-light-1.5")
        setAppearance(400, 860, "dark", 1.0f)
        saveCapture("directory-400-dark-1.0")
        setAppearance(400, 860, "light", 1.0f)

        compose.onNodeWithText("家居装修").performClick()
        compose.onNodeWithText("装修避坑经验汇总").assertExists()

        saveCapture("topics-400-light-1.0")
        compose.onNodeWithContentDescription("排序方式").performClick()
        compose.onNodeWithText("最近活跃").assertIsDisplayed()
        compose.onNodeWithText("最新发布").performClick()
        setAppearance(400, 860, "dark", 1.0f)
        saveCapture("topics-400-dark-1.0")

        compose.onNodeWithContentDescription("搜索当前版面").performClick()
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.onNodeWithText("搜索范围：家居装修（切换）").assertIsDisplayed()
        saveCapture("scoped-search-400-dark-1.0")
        setAppearance(400, 860, "light", 1.0f)
        saveCapture("scoped-search-400-light-1.0")
        setAppearance(320, 880, "light", 1.5f)
        saveCapture("scoped-search-320-light-1.5")
        compose.onNode(hasSetTextAction()).performTextInput("双十一")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("装修避坑经验汇总").assertExists()
        org.junit.Assert.assertEquals("436" to null, fixture.searchScopes.last())
        saveCapture("scoped-results-320-light-1.5")
        compose.onNodeWithContentDescription("取消搜索").performClick()

        setAppearance(320, 880, "light", 1.5f)
        saveCapture("topics-320-light-1.5")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        saveCapture("topics-320-light-1.5-scrolled")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)

        setAppearance(400, 920, "light", 2.0f)
        saveCapture("topics-400-light-2.0")

        setAppearance(610, 860, "light", 1.0f)
        saveCapture("topics-610-light-1.0")

        setAppearance(900, 820, "light", 1.0f)
        compose.onNodeWithText("装修避坑经验汇总").performClick()
        compose.onNodeWithText("主要关心内屏低亮度下的阅读舒适度", substring = true).assertExists()
        saveCapture("topics-900-light-1.0")

        setAppearance(900, 820, "dark", 1.0f)
        saveCapture("topics-900-dark-1.0")

        setAppearance(400, 860, "light", 1.0f)
        compose.onNodeWithText("主要关心内屏低亮度下的阅读舒适度", substring = true).assertExists()
        saveCapture("chrome-reader-400-light-1.0")

        performBack()
        compose.onNodeWithText("装修避坑经验汇总").assertExists()
        performBack()
        compose.onNodeWithText("家居装修").assertExists()

        compose.onNodeWithContentDescription("搜索").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("双十一")
        saveCapture("search-options-400-light-1.0")
        setAppearance(320, 880, "light", 1.5f)
        saveCapture("search-options-320-light-1.5")
        setAppearance(400, 860, "dark", 1.0f)
        saveCapture("search-options-400-dark-1.0")
        setAppearance(400, 860, "light", 1.0f)
        compose.onNodeWithText("所有话题").performClick()
        compose.onNodeWithText("装修避坑经验汇总").assertExists()
        saveCapture("search-400-light-1.0")

        compose.onNodeWithContentDescription("取消搜索").performClick()
        compose.onNodeWithContentDescription("头像菜单").performClick()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("正文字号", substring = true).assertExists()
        saveCapture("chrome-settings-400-light-1.0")
    }

    @Test fun slice1SemanticsTouchTargetsAndFailureStates() {
        var sizeClass by mutableStateOf(WindowWidthSizeClass.Compact)
        var widthDp by mutableIntStateOf(400)
        compose.runOnIdle {
            compose.activity.setContent {
                Layout(
                    content = { NagiApp(sizeClass) },
                    modifier = Modifier,
                ) { measurables, _ ->
                    val pxW = widthDp.dp.roundToPx()
                    val pxH = 860.dp.roundToPx()
                    val placeable = measurables.first().measure(Constraints.fixed(pxW, pxH))
                    layout(pxW, pxH) { placeable.place(0, 0) }
                }
            }
        }

        compose.onNodeWithContentDescription("首页").assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("头像菜单").assertHeightIsAtLeast(48.dp)

        compose.onNodeWithText("家居装修").performClick()
        compose.onNodeWithText("装修避坑经验汇总").assertIsDisplayed()

        val backNode = compose.onNodeWithContentDescription("返回")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        val moreNode = compose.onNodeWithContentDescription("更多选项")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        val backBounds = backNode.fetchSemanticsNode().boundsInRoot
        val moreBounds = moreNode.fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Header targets must not overlap", backBounds.right < moreBounds.left)

        val dockBounds = listOf("子板块筛选", "搜索当前版面", "刷新列表", "发帖").map { label ->
            compose.onNodeWithContentDescription(label)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .fetchSemanticsNode().boundsInRoot
        }
        dockBounds.zipWithNext().forEach { (left, right) ->
            org.junit.Assert.assertTrue("Dock targets must not overlap", left.right <= right.left + 1f)
        }

        compose.onNodeWithContentDescription("排序方式")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onAllNodesWithText("最新发布").filter(androidx.compose.ui.test.isSelectable()).onFirst()
            .assertIsSelected().assertHeightIsAtLeast(48.dp)
        compose.onAllNodesWithText("最近活跃").filter(androidx.compose.ui.test.isSelectable()).onFirst()
            .assertIsNotSelected().assertHeightIsAtLeast(48.dp).performClick()
        org.junit.Assert.assertEquals(
            "当前：最近活跃",
            compose.onNodeWithContentDescription("排序方式").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription],
        )
        compose.onNodeWithContentDescription("排序方式").performClick()
        compose.onAllNodesWithText("最近活跃").filter(androidx.compose.ui.test.isSelectable()).onFirst()
            .assertIsSelected()
        compose.onAllNodesWithText("最新发布").filter(androidx.compose.ui.test.isSelectable()).onFirst()
            .assertIsNotSelected().performClick()

        compose.onNodeWithText("装修避坑经验汇总").performTouchInput { longClick() }
        compose.onNodeWithText("发布 11-03 22:09").assertIsDisplayed()
        compose.onNodeWithText("活跃 11-04 16:11").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("发布 —", substring = true).assertDoesNotExist()
        compose.onNodeWithText("活跃 —", substring = true).assertDoesNotExist()

        fixture.failMore = true
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("重试"))
        compose.onNodeWithText("重试").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("装修避坑经验汇总").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("重试"))
        compose.onNodeWithText("重试").assertIsDisplayed()
        fixture.failMore = false
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("第二页加载主题：厨下净水器滤芯长期成本对比").performScrollTo().assertIsDisplayed()

        compose.runOnIdle {
            widthDp = 900
            sizeClass = WindowWidthSizeClass.Expanded
        }
        compose.waitForIdle()
        compose.onNode(hasText("手持花洒增压效果实测") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasText("手持花洒增压效果实测") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("装修避坑经验汇总") and hasClickAction()).performScrollTo().assertIsNotSelected()
        compose.onNodeWithText("主要关心内屏低亮度下的阅读舒适度", substring = true).assertIsDisplayed()

        compose.runOnIdle {
            widthDp = 400
            sizeClass = WindowWidthSizeClass.Compact
        }
        compose.waitForIdle()
        compose.onNodeWithText("主要关心内屏低亮度下的阅读舒适度", substring = true).assertIsDisplayed()

        compose.runOnIdle {
            widthDp = 900
            sizeClass = WindowWidthSizeClass.Expanded
        }
        compose.waitForIdle()
        compose.onNode(hasText("手持花洒增压效果实测") and hasClickAction()).performScrollTo().assertIsSelected()
        compose.onNodeWithText("主要关心内屏低亮度下的阅读舒适度", substring = true).assertIsDisplayed()
    }

    @Test fun collectionSearchEntryPreservesScopeAcrossRecreationAndReading() {
        compose.onNodeWithText("消费电子").performClick()
        compose.onNodeWithContentDescription("搜索当前版面").performClick()
        compose.onNodeWithText("搜索范围：消费电子（切换）").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("屏幕")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("装修避坑经验汇总").assertIsDisplayed()
        org.junit.Assert.assertEquals("459" to "1024", fixture.searchScopes.last())
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("搜索范围：消费电子（切换）").assertIsDisplayed()
        compose.onNodeWithText("装修避坑经验汇总").performClick()
        compose.onNodeWithText("主要关心内屏低亮度下的阅读舒适度", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("搜索范围：消费电子（切换）").assertIsDisplayed()
        compose.onNodeWithContentDescription("取消搜索").performClick()
        compose.onNodeWithContentDescription("搜索当前版面").assertIsDisplayed()
        compose.onNodeWithText("消费电子").assertIsDisplayed()
    }

    @Test fun initialLoadRetryAndEmptySearchRemainActionable() {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        fixture.topicsGate = gate
        compose.onNodeWithText("家居装修").performClick()
        compose.onNodeWithText("正在加载主题…").assertIsDisplayed()
        fixture.failTopics = true
        compose.runOnIdle { gate.complete(Unit) }
        compose.waitForIdle()
        compose.onNodeWithText("加载失败，请检查网络后重试").assertIsDisplayed()
        compose.onNodeWithContentDescription("排序方式").performClick()
        compose.onNodeWithText("最新发布").assertIsSelected().performClick()
        fixture.failTopics = false
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("装修避坑经验汇总").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("搜索").performClick()
        fixture.emptySearch = true
        compose.onNode(hasSetTextAction()).performTextInput("不存在的主题")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        compose.onNodeWithText("没有匹配的主题").performScrollTo().assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextEquals("不存在的主题")
        compose.onNodeWithText("搜索范围：所有版块（切换）").assertIsDisplayed()
    }
}
