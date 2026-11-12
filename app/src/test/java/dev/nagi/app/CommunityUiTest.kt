package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import dev.nagi.app.data.AccountManager
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.*
import dev.nagi.app.ui.library.*
import dev.nagi.app.ui.post.ReaderPost
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.*
import dev.nagi.sdk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CommunityUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unreadAvatarAnnouncesRealCountAndOpensNotifications() {
        var route = ""
        compose.setContent {
            NagiTheme("light") {
                CompositionLocalProvider(
                    LocalAccountIdentity provides AccountIdentity(1, "7"),
                    LocalNotifications provides NotificationsState(1, "7", unreadCount = 105),
                ) { AccountMenuButton { route = it } }
            }
        }
        compose.onNodeWithContentDescription("头像菜单，105 条未读通知")
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("105 条未读").assertIsDisplayed()
        compose.onNodeWithText("通知").performClick()
        compose.runOnIdle { assertEquals("notifications", route) }
    }

    @Test fun mismatchedAccountNeverShowsOldUnreadBadge() {
        compose.setContent {
            NagiTheme("light") {
                CompositionLocalProvider(
                    LocalAccountIdentity provides AccountIdentity(2, "8"),
                    LocalNotifications provides NotificationsState(1, "7", unreadCount = 99),
                ) { AccountMenuButton {} }
            }
        }
        compose.onNodeWithContentDescription("头像菜单").assertExists()
        compose.onNodeWithContentDescription("头像菜单，99 条未读通知").assertDoesNotExist()
    }

    @Test fun missingMetadataIsOmittedAndSelfSignatureCanExpand() {
        val user = NgaUser("8", "用户", "", 1, signature = listOf(ContentBlock.Text("长签名需要允许展开，并且不能影响正文列表。".repeat(12))))
        compose.setContent {
            NagiTheme("light") {
                CompositionLocalProvider(LocalAccountIdentity provides AccountIdentity(1, "8")) {
                    UserProfileCard(UserProfileState(user), {}, {})
                }
            }
        }
        compose.onNodeWithText("IP 属地：", substring = true).assertDoesNotExist()
        compose.onNodeWithText("注册时间：", substring = true).assertDoesNotExist()
        compose.onNodeWithText("发送短消息").assertDoesNotExist()
        compose.onNodeWithText("展开签名").performClick()
        compose.onNodeWithText("收起签名").performClick()
        compose.onNodeWithText("展开签名").assertIsDisplayed()
    }

    @Test fun notificationListPreservesItemsWhenRefreshFails() {
        val item = CommunityItem("1", "测试主题", "有人回复了你的帖子", "测试用户", "123", "456", 42, true)
        var opened = ""
        compose.setContent {
            NagiTheme("dark") {
                Surface(Modifier.fillMaxSize(), color = dev.nagi.app.ui.theme.NagiThemeTokens.colors.groupedCanvas) {
                    NotificationsScreen(NotificationsState(1, "7", listOf(item), 1, loaded = true, error = "网络失败"),
                        {}, {}, {}, { opened = it.topicId })
                }
            }
        }
        compose.onNodeWithText("网络失败").assertIsDisplayed()
        compose.onNodeWithText("未读").assertIsDisplayed()
        compose.onNodeWithText("测试主题").performClick()
        compose.runOnIdle { assertEquals("123", opened) }
        capture("notifications-dark")
    }

    @Test fun profileTabsAndMessageTarget() = renderProfile("light", 1f)
    @Test fun darkProfile() = renderProfile("dark", 1f)
    @Test fun repliesWithMissingDateRemainReadable() = renderProfile("light", 1f, replyDate = 0)
    @Config(qualifiers = "w1000dp-h850dp")
    @Test fun wideProfileRemainsConstrained() = renderProfile("light", 1f)
    @Config(qualifiers = "w320dp-h850dp")
    @Test fun narrowLargeTextProfile() = renderProfile("light", 2f)

    private fun renderProfile(theme: String, scale: Float, replyDate: Long = 42) {
        RuntimeEnvironment.setFontScale(scale)
        val accounts = runBlocking { AccountManager(MemorySessionStore(), FakeBrowserCookies()).also { it.initialize() } }
        val client = object : NgaClient by FakeClient() {
            override suspend fun getUser(userId: String) = NgaUser(userId, "这是名字很长的测试用户", "", 123, "上海", 1000000000L,
                listOf(ContentBlock.Text("保持好奇，认真讨论。")))
            override suspend fun getUserTopics(userId: String, page: Int) = TopicPage(listOf(Topic("1", "用户发表的话题", "User", userId)), page, 1, false)
            override suspend fun getUserReplies(userId: String, page: Int) = UserReplyPage(listOf(UserReply(
                Topic("1", "回复所属主题", "User", userId), "2", userId, replyDate, listOf(ContentBlock.Text("我的回复摘要")))), page, false)
        }
        val saved = SavedStateHandle(mapOf("uid" to "8"))
        val contentVm = UserContentViewModel(client, accounts, saved)
        val profileVm = UserProfileViewModel(client, accounts, saved)
        var target = ""
        compose.setContent {
            NagiTheme(theme) {
                Surface(Modifier.fillMaxSize(), color = dev.nagi.app.ui.theme.NagiThemeTokens.colors.groupedCanvas) {
                    UserContentScreen({}, {}, contentVm, profileVm, { target = it })
                }
            }
        }
        compose.onNodeWithText("IP 属地：上海").assertIsDisplayed()
        compose.onNodeWithText("注册时间：2001-09-09").performScrollTo().assertIsDisplayed()
        val width = RuntimeEnvironment.getApplication().resources.configuration.screenWidthDp
        capture("profile-$width-$theme-$scale")
        if (width > 760) {
            val card = compose.onNodeWithText("IP 属地：上海").fetchSemanticsNode().boundsInRoot
            assertTrue(card.left > 100f)
        }
        compose.onNodeWithText("发送短消息").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("这是名字很长的测试用户", target) }
        compose.onNodeWithText("回复").performScrollTo().performClick()
        compose.onNodeWithText("我的回复摘要").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("话题").performScrollTo().performClick()
        compose.onNodeWithText("用户发表的话题").performScrollTo().assertIsDisplayed()
    }

    @Test fun switchingTabsRestoresTopicScrollPosition() {
        val accounts = runBlocking { AccountManager(MemorySessionStore(), FakeBrowserCookies()).also { it.initialize() } }
        val client = object : NgaClient by FakeClient() {
            override suspend fun getUserTopics(userId: String, page: Int) = TopicPage(
                (1..40).map { Topic("$it", "话题 $it", "User", userId) }, page, 40, false)
            override suspend fun getUserReplies(userId: String, page: Int) = UserReplyPage(emptyList(), page, false)
        }
        val saved = SavedStateHandle(mapOf("uid" to "8"))
        val vm = UserContentViewModel(client, accounts, saved)
        val profile = UserProfileViewModel(client, accounts, saved)
        compose.setContent { NagiTheme("light") { UserContentScreen({}, {}, vm, profile) } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("话题 25"))
        compose.onNodeWithText("话题 25").assertIsDisplayed()
        compose.onNodeWithText("回复").performClick()
        compose.onNodeWithText("此页没有内容").assertExists()
        compose.onNodeWithText("话题").performClick()
        capture("scroll-restored")
        compose.onNodeWithText("话题 25").assertIsDisplayed()
    }

    @Test fun readerAuthorOpensProfileButAnonymousDoesNot() {
        var opened = ""
        compose.setContent {
            NagiTheme("light") {
                CompositionLocalProvider(LocalOpenUser provides { opened = it }) {
                    Column {
                        ReaderPost(Post("1", 0, "测试作者", "8", "", "", emptyList()), ReadingPreferences(), false, null, {})
                        ReaderPost(Post("2", 1, "匿名作者", "", "", "", emptyList()), ReadingPreferences(), false, null, {})
                    }
                }
            }
        }
        compose.onNodeWithText("测试作者").performClick()
        compose.runOnIdle { assertEquals("8", opened) }
        compose.onNodeWithText("匿名作者").assertHasNoClickAction()
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().first()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/ui-captures/community/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
