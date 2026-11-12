package dev.nagi.app

import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.*
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dev.nagi.app.data.AccountManager
import dev.nagi.app.data.BrowserCookies
import dev.nagi.app.data.LOGIN_URL
import kotlinx.coroutines.runBlocking
import org.junit.Before
import javax.inject.Inject
import dev.nagi.app.data.ReadingDaoProvider
import dev.nagi.app.di.AppModule
import dev.nagi.sdk.MemorySessionStore
import dev.nagi.sdk.NgaClient
import dev.nagi.sdk.SessionStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
object FakeAppModule {
    var clientOverride: NgaClient? = null
    @Provides @Singleton fun sessions(): SessionStore = MemorySessionStore()
    @Provides @Singleton fun browser(): BrowserCookies = FakeBrowserCookies()
    @Provides @Singleton fun client(): NgaClient = clientOverride ?: FakeClient()
    @Provides @Singleton fun daos(): ReadingDaoProvider = FakeDaoProvider()
    @Provides @Singleton fun messageWrites() = dev.nagi.app.data.MessageWriteGuard()
}

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationFlowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var accounts: AccountManager
    @Inject lateinit var cookies: BrowserCookies
    @Inject lateinit var client: NgaClient
    @Before fun inject() { hilt.inject() }

    private fun openAccountItem(label: String) {
        compose.onNodeWithContentDescription("头像菜单").performClick()
        compose.onNodeWithText(label).performClick()
    }

    @Test fun accountPageBackWorksDuringEnterTransition() {
        compose.onNodeWithContentDescription("头像菜单").performClick()
        compose.onNodeWithText("通知").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("通知").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("Forum").assertIsDisplayed()
    }

    @Test fun repeatedAccountPageBackCallbacksKeepHome() {
        listOf("短消息", "通知", "短消息", "通知").forEach { label ->
            openAccountItem(label)
            val back = compose.onNodeWithContentDescription("返回").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
            compose.runOnIdle { repeat(3) { back() } }
            compose.waitForIdle()
            compose.onNodeWithText("Forum").assertIsDisplayed()
            compose.onNodeWithContentDescription("头像菜单").assertExists()
        }
    }

    @Test fun homeHasTopActionsWithoutHeaderOrBottomTabs() {
        compose.onNodeWithText("Nagi").assertDoesNotExist()
        compose.onNodeWithText("版块").assertDoesNotExist()
        compose.onNodeWithText("搜索").assertDoesNotExist()
        compose.onNodeWithContentDescription("首页").assertDoesNotExist()
        compose.onNode(androidx.compose.ui.test.hasAnyAncestor(isRoot()) and isSelectable()).assertDoesNotExist()
        compose.onNodeWithContentDescription("筛选版块").assertDoesNotExist()
        compose.onNodeWithContentDescription("刷新").assertDoesNotExist()
        compose.onNodeWithContentDescription("头像菜单")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        listOf("未登录", "登录 NGA", "通知", "短消息", "收藏话题", "浏览历史", "设置", "关于与反馈")
            .forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("通知").performClick()
        compose.onNodeWithText("请先登录 NGA").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("Forum").assertIsDisplayed()
        openAccountItem("设置")
        compose.onNodeWithText("退出当前账号").assertDoesNotExist()
        compose.onNodeWithText("正文字号", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp")
    fun narrowHomeHasIndependentTopAndBottomCapsuleTouchTargets() {
        val avatar = compose.onNodeWithContentDescription("头像菜单")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        val options = compose.onNodeWithContentDescription("版块选项")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithContentDescription("搜索")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Top actions must not overlap", avatar.right <= options.left)
        org.junit.Assert.assertTrue("Search capsule must sit below top bar", search.top > avatar.bottom)
    }

    @Test fun signedInIdentityOpensProfileAndAccountManagementSeparately() {
        compose.runOnIdle {
            runBlocking {
                val attempt = accounts.beginLogin()
                (cookies as FakeBrowserCookies).cookies[LOGIN_URL] = "ngaPassportUid=77; ngaPassportCid=test-token"
                check(accounts.captureAndSave(attempt, LOGIN_URL))
            }
        }
        compose.waitUntil(5000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodes(hasContentDescription("头像菜单") and SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "User")).fetchSemanticsNodes().isNotEmpty()
        }
        openAccountItem("User")
        compose.onNodeWithText("我的档案").assertIsDisplayed()
        compose.onNodeWithText("UID 77").assertIsDisplayed()
        compose.onNodeWithText("我的主题").performClick()
        compose.onNodeWithText("用户详情").assertIsDisplayed()
        compose.onNodeWithText("话题").assertIsSelected()
        compose.onNodeWithText("此页没有内容").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("我的回复").performClick()
        compose.onNodeWithText("用户详情").assertIsDisplayed()
        compose.onNodeWithText("回复").assertIsSelected()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        openAccountItem("管理账号")
        compose.onNodeWithText("账号管理").assertIsDisplayed()
        compose.onNodeWithText("退出当前账号").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        openAccountItem("设置")
        compose.onNodeWithText("退出当前账号").assertDoesNotExist()
        compose.onNodeWithText("当前登录 UID：77").assertDoesNotExist()
        compose.onNodeWithText("关于与反馈").performClick()
        compose.onNodeWithText("分享反馈信息").assertIsDisplayed()
    }

    @Test fun readerProfileTabsAndPrefilledMessageSurviveNavigation() {
        compose.runOnIdle {
            runBlocking {
                val attempt = accounts.beginLogin()
                (cookies as FakeBrowserCookies).cookies[LOGIN_URL] = "ngaPassportUid=77; ngaPassportCid=test-token"
                check(accounts.captureAndSave(attempt, LOGIN_URL))
            }
        }
        compose.onNodeWithText("Forum").performClick()
        compose.onNodeWithText("Title 1").performClick()
        compose.onAllNodesWithText("Author").onFirst().performClick()
        compose.onNodeWithText("用户详情").assertIsDisplayed()
        compose.onNodeWithText("回复").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("回复").assertIsSelected()
        compose.onNodeWithText("发送短消息").performScrollTo().performClick()
        compose.onNodeWithText("新建私信").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("User")).assertExists()
        compose.onNodeWithContentDescription("关闭新建私信").performClick()
        compose.onNodeWithText("用户详情").assertIsDisplayed()
        compose.onNodeWithText("短消息").assertDoesNotExist()
        compose.onNodeWithText("回复").assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("新建私信").assertDoesNotExist()
        compose.onNodeWithText("用户详情").assertIsDisplayed()
        compose.onNodeWithText("回复").assertIsSelected()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("Post 0").assertIsDisplayed()
    }

    @Test fun directorySearchRestoresQueryAndCancelsWithoutHiddenFiltering() {
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索").performClick()
        compose.onNode(hasSetTextAction()).assertIsFocused().performTextInput("no match")
        compose.onNodeWithText("所有版块").performClick()
        compose.onNodeWithText("没有匹配的版块").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNode(hasSetTextAction()).assertTextContains("no match")
        compose.onNodeWithContentDescription("清空搜索").performClick()
        compose.onNodeWithText("所有话题").assertIsNotEnabled()
        compose.onNodeWithContentDescription("取消搜索").performClick()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Forum").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp")
    fun topicHeaderHasIndependentCircularIconActions() {
        compose.onNodeWithText("Forum").performClick()
        val actions = listOf("返回", "搜索当前版面", "发帖").map { label ->
            compose.onNodeWithContentDescription(label).assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
        }
        actions.zipWithNext().forEach { (left, right) ->
            org.junit.Assert.assertTrue(left.right <= right.left)
        }
        compose.onNodeWithText("发帖").assertDoesNotExist()
        compose.onNodeWithContentDescription("刷新").assertDoesNotExist()
        compose.onNodeWithText("Title 1").assertIsDisplayed()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            java.io.File("build/ui-captures/topic-header-320-light.png").apply { parentFile?.mkdirs() }.outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithContentDescription("发帖").performClick()
        compose.onNodeWithText("发布帖子").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭发布帖子").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("Forum").assertIsDisplayed()
    }

    @Test fun topicSearchStartsInCurrentForumAndReturnsToList() {
        compose.onNodeWithText("Forum").performClick()
        compose.onNodeWithContentDescription("排序方式").performClick()
        compose.onNodeWithText("最近活跃").performClick()
        compose.onNodeWithContentDescription("搜索当前版面").performClick()
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.onNodeWithText("搜索范围：Forum（切换）").assertIsDisplayed()
        compose.onNodeWithText("所有版块").assertDoesNotExist()
        compose.onNodeWithText("所有用户").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("query")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("Title 1").assertIsDisplayed()
        compose.runOnIdle { org.junit.Assert.assertEquals("7" to null, (client as FakeClient).searchScopes.last()) }
        compose.activityRule.scenario.recreate()
        compose.onNode(hasSetTextAction()).assertTextEquals("query")
        compose.onNodeWithText("搜索范围：Forum（切换）").assertIsDisplayed()
        compose.onNodeWithContentDescription("取消搜索").performClick()
        compose.onNodeWithContentDescription("排序方式")
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "当前：最近活跃"))
        compose.onNodeWithText("Title 1").assertIsDisplayed()
    }

    @Test fun forumTopicReaderLocalFavoriteAndBack() {
        compose.onNodeWithText("Forum").performClick()
        compose.onNodeWithText("Title 1").performClick()
        compose.onNodeWithText("Post 0").assertIsDisplayed()
        compose.onNodeWithContentDescription("主题操作").performClick()
        compose.onNodeWithText("本地收藏").performClick()
        compose.onNodeWithContentDescription("主题操作").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已收藏"))
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("Title 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        openAccountItem("收藏话题")
        compose.onNodeWithText("Topic").assertIsDisplayed()
        compose.onNodeWithText("Topic").performClick()
        compose.onNodeWithText("Post 0").assertIsDisplayed()
    }

    @Test fun historyReopensPreviouslyReadTopic() {
        compose.onNodeWithText("Forum").performClick()
        compose.onNodeWithText("Title 1").performClick()
        compose.onNodeWithText("Post 0").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        openAccountItem("浏览历史")
        compose.onNodeWithText("继续阅读").assertIsDisplayed()
        compose.onNodeWithText("Topic").performClick()
        compose.onNodeWithText("Post 0").assertIsDisplayed()
    }

    @Test fun loginAndLogoutResetReadingScopesAndPreserveGuestFavorites() {
        compose.onNodeWithText("Forum").performClick()
        compose.onNodeWithText("Title 1").performClick()
        compose.onNodeWithContentDescription("主题操作").performClick()
        compose.onNodeWithText("本地收藏").performClick()
        compose.onNodeWithContentDescription("主题操作").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已收藏"))
        compose.runOnIdle {
            runBlocking {
                val attempt = accounts.beginLogin()
                (cookies as FakeBrowserCookies).cookies[LOGIN_URL] = "ngaPassportUid=77; ngaPassportCid=test-token"
                check(accounts.captureAndSave(attempt, LOGIN_URL))
            }
        }
        compose.onNodeWithText("Forum").assertIsDisplayed()
        openAccountItem("收藏话题")
        compose.onNodeWithText("Topic").assertDoesNotExist()
        openAccountItem("管理账号")
        compose.onNodeWithText("退出当前账号").performClick()
        compose.onNodeWithText("Forum").assertIsDisplayed()
        openAccountItem("收藏话题")
        compose.onNodeWithText("Topic").assertIsDisplayed()
    }
    @Test fun settingsExposePackagedGplLicense() {
        openAccountItem("关于与反馈")
        compose.onNodeWithText("协议适配", substring = true).assertDoesNotExist()
        compose.onNodeWithText("开源许可").performClick()
        compose.onNode(hasText("开源许可") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.runOnIdle {
            val bundledLicense = compose.activity.assets.open("LICENSE").bufferedReader().use { it.readText() }
            org.junit.Assert.assertTrue(bundledLicense.contains("GNU GENERAL PUBLIC LICENSE"))
        }
        compose.waitUntil(5000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodes(hasText("GNU GENERAL PUBLIC LICENSE", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("关于与反馈").assertIsDisplayed()
    }
    @Test fun globalTopicAndExactUserSearchAreAvailableWithoutChoosingAForum() {
        compose.onNodeWithContentDescription("搜索").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("query")
        compose.onNodeWithText("所有话题").performClick()
        compose.onNodeWithText("Title 1").assertIsDisplayed()
        compose.onNodeWithText("搜索范围：所有版块（切换）").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("Test User")
        compose.onNodeWithText("所有用户").performClick()
        compose.onNode(hasText("Test User") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.onNodeWithText("用户详情").assertIsDisplayed()
        compose.onNodeWithText("发帖：1").assertIsDisplayed()
        compose.onNodeWithText("发送短消息").assertIsDisplayed()
    }
    @Test fun searchResultsSurviveActivityRecreation() {
        compose.onNodeWithContentDescription("搜索").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("query")
        compose.onNodeWithText("所有话题").performClick()
        compose.onNodeWithText("搜索范围：所有版块（切换）").performClick()
        compose.onNodeWithText("Forum").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("搜索范围：Forum（切换）").assertIsDisplayed()
        compose.onNodeWithText("Title 1").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Title 1").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextEquals("query")
        compose.onNodeWithText("搜索范围：Forum（切换）").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1000dp-h600dp")
    fun wideHomeUsesTopActionsWithoutPersistentNavigation() {
        compose.onAllNodes(isSelectable()).assertCountEquals(0)
        compose.onNodeWithText("首页").assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("取消搜索").performClick()
        openAccountItem("收藏话题")
        compose.onNodeWithText("本地收藏").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        openAccountItem("设置")
        compose.onNodeWithText("正文字号", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("Forum").performClick()
        compose.onNodeWithText("Title 1").assertLeftPositionInRootIsEqualTo(16.dp)
        compose.onNodeWithText("Title 1").performClick()
        compose.onNodeWithText("Post 0").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w610dp-h800dp")
    fun mediumAndCompactWindowsDoNotReintroduceNavigationRail() {
        listOf("w610dp-h800dp", "w1000dp-h600dp", "w400dp-h800dp", "w1000dp-h800dp").forEach { qualifiers ->
            compose.runOnIdle { RuntimeEnvironment.setQualifiers(qualifiers) }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Forum").assertIsDisplayed()
            compose.onAllNodes(isSelectable()).assertCountEquals(0)
            compose.onNodeWithContentDescription("搜索").assertIsDisplayed()
            compose.onNodeWithContentDescription("头像菜单").assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun selectedReaderSurvivesExpandedToCompactTransition() {
        compose.onNodeWithText("Forum").performClick()
        compose.onNodeWithText("Title 1").performClick()
        compose.onNodeWithText("Title 1").assertIsDisplayed()
        compose.onNodeWithText("Post 0").assertIsDisplayed()
        compose.runOnIdle { RuntimeEnvironment.setQualifiers("w400dp-h800dp") }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Post 0").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("Title 1").assertIsDisplayed()
    }

    @Test fun subforumFilterEntryDoesNotExposeFavoriteSwitchesAsFilters() {
        compose.onNodeWithText("Forum").performClick()
        compose.onNodeWithContentDescription("子板块筛选").performClick()
        compose.onNodeWithText("子板块筛选").assertIsDisplayed()
        compose.onNodeWithText("当前版块暂无子板块筛选").assertIsDisplayed()
        compose.onNodeWithText("刷新核对").assertIsDisplayed()
        compose.onNodeWithText("同组版块与主题合集").assertDoesNotExist()
    }

    @Test
    fun subforumSheetSwitchesForumInPlaceAndSingleBackReturnsHome() {
        compose.onNodeWithText("Forum").performClick()
        compose.onNodeWithText("Title 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("更多选项").performClick()
        compose.onNodeWithText("同组版块与主题合集").performClick()
        compose.onNodeWithText("同组版块与主题合集").assertIsDisplayed()
        compose.onNodeWithText("Collection").performClick()
        compose.onNodeWithText("Collection").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("Nagi.").assertIsDisplayed()
    }

    @Test
    fun globalTimeFormatterDistinguishesSameDaySameYearAndOtherYear() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val now = java.time.ZonedDateTime.of(2026, 11, 8, 14, 30, 0, 0, zone).toEpochSecond()

        org.junit.Assert.assertEquals("刚刚", dev.nagi.app.ui.topic.formatTime(now - 20, now, zone))
        org.junit.Assert.assertEquals("1 分钟之前", dev.nagi.app.ui.topic.formatTime(now - 60, now, zone))
        org.junit.Assert.assertEquals("15 分钟之前", dev.nagi.app.ui.topic.formatTime(now - 15 * 60, now, zone))
        org.junit.Assert.assertEquals("3 小时之前", dev.nagi.app.ui.topic.formatTime(now - 3 * 3600, now, zone))

        val yesterdaySameYear = java.time.ZonedDateTime.of(2026, 11, 7, 22, 46, 0, 0, zone).toEpochSecond()
        org.junit.Assert.assertEquals("11-07 22:46", dev.nagi.app.ui.topic.formatTime(yesterdaySameYear, now, zone))

        val lastYear = java.time.ZonedDateTime.of(2025, 5, 26, 17, 27, 0, 0, zone).toEpochSecond()
        org.junit.Assert.assertEquals("2025-05-26 17:27", dev.nagi.app.ui.topic.formatTime(lastYear, now, zone))

        org.junit.Assert.assertEquals("10 分钟之前", dev.nagi.app.ui.topic.formatTimeText("2026-11-08 14:20:00", now, zone))
        org.junit.Assert.assertEquals("2025-05-26 17:27", dev.nagi.app.ui.topic.formatTimeText("2025-05-26 17:27", now, zone))
    }
}
