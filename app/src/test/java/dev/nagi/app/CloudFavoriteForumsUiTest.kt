package dev.nagi.app

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.nagi.app.data.AccountManager
import dev.nagi.app.data.PreferenceStore
import dev.nagi.app.ui.components.LocalAccountIdentity
import dev.nagi.app.ui.components.LocalOpenLogin
import dev.nagi.app.ui.forum.ForumDirectory
import dev.nagi.app.ui.library.FavoritesScreen
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.ui.topic.TopicsScreen
import dev.nagi.app.viewmodel.AccountIdentity
import dev.nagi.app.viewmodel.LibraryViewModel
import dev.nagi.app.viewmodel.ListState
import dev.nagi.app.viewmodel.TopicsViewModel
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CloudFavoriteForumsUiTest {
    @get:Rule val compose = createComposeRule()

    private val directory = listOf(
        Forum("7", "艾泽拉斯议事厅", "魔兽世界", "魔兽世界", description = "魔兽主讨论区"),
        Forum("0", "精品合集", "魔兽世界", "魔兽世界", collectionId = "99", description = "合集描述"),
    )

    @Test fun forumDirectoryShowsCloudSectionAndLongPressMenuAndFilter() {
        var toggledCloud: Forum? = null
        var loginOpened = false
        var signedIn by mutableStateOf(true)
        var cloudState by mutableStateOf(ListState(items = listOf(Forum("0", "精品合集", "", "", collectionId = "99"))))
        var uncertain by mutableStateOf(emptySet<String>())

        compose.setContent {
            NagiTheme {
                CompositionLocalProvider(LocalOpenLogin provides { loginOpened = true }) {
                    ForumDirectory(
                        state = ListState(items = directory),
                        refresh = {},
                        onSelect = {},
                        onForum = {},
                        isSignedIn = signedIn,
                        cloudFavoriteForums = cloudState,
                        cloudUncertainKeys = uncertain,
                        onToggleCloudFavorite = { toggledCloud = it },
                    )
                }
            }
        }

        // Cloud section appears at top; local forum favorites section does not exist
        compose.onNodeWithText("NGA 收藏版块").assertIsDisplayed()
        compose.onNodeWithText("NGA").assertIsDisplayed()
        compose.onNodeWithText("本地收藏").assertDoesNotExist()

        // Filter to NGA favorites only
        compose.onNodeWithContentDescription("版块选项").performClick()
        compose.onNodeWithText("只看 NGA 收藏").performClick()
        compose.onNodeWithText("只看 NGA 收藏").assertIsDisplayed()
        compose.onNodeWithText("精品合集").assertIsDisplayed()
        compose.onNodeWithText("艾泽拉斯议事厅").assertDoesNotExist()

        // Clear filter
        compose.onNodeWithContentDescription("版块选项").performClick()
        compose.onNodeWithText("显示全部版块").performClick()
        compose.onAllNodesWithText("艾泽拉斯议事厅").onFirst().assertIsDisplayed()

        // Long press directory row opens NGA cloud favorite menu
        compose.onAllNodesWithText("艾泽拉斯议事厅").onLast().performTouchInput { longClick() }
        compose.onNodeWithText("本地收藏版块").assertDoesNotExist()
        compose.onNodeWithText("收藏到 NGA 版块").performClick()
        compose.runOnIdle { assertEquals("forum:7", toggledCloud?.key) }

        // Uncertain lock disables cloud favorite action
        compose.runOnIdle { uncertain = setOf("forum:7") }
        compose.onAllNodesWithText("艾泽拉斯议事厅").onLast().performTouchInput { longClick() }
        compose.onNodeWithText("结果未确认，请先刷新核对").assertIsDisplayed()
        compose.onNodeWithText("收藏到 NGA 版块").performClick()
        compose.runOnIdle { assertEquals("forum:7", toggledCloud?.key) }
        compose.runOnIdle { toggledCloud = null }

        // Guest sees login prompt in long press menu
        compose.runOnIdle {
            signedIn = false
            uncertain = emptySet()
        }
        compose.onAllNodesWithText("艾泽拉斯议事厅").onLast().performTouchInput { longClick() }
        compose.onNodeWithText("登录 NGA 后可用").assertIsDisplayed()
        compose.onNodeWithText("收藏到 NGA 版块").performClick()
        compose.runOnIdle { assertTrue(loginOpened) }
    }

    @Test fun favoritesScreenSeparatesLocalAndCloudForumTabsAndConfirmsRemoval() {
        val sessions = MemorySessionStore().apply { runBlocking { saveSession(NgaSession("7", "token")) } }
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { runBlocking { it.initialize() } }
        val preferences = PreferenceStore(RuntimeEnvironment.getApplication())
        val removed = mutableListOf<String>()
        val delegate = FakeClient()
        val client = object : NgaClient by delegate {
            override suspend fun getFavoriteForums(): List<Forum> = listOf(
                Forum("7", "Forum", "", ""),
                Forum("0", "合集", "", "", collectionId = "99"),
                Forum("0", "非目录合集", "", "", collectionId = "888"),
            )
            override suspend fun setForumFavorite(forum: Forum, favorite: Boolean): WriteResult {
                if (!favorite) removed += forum.key
                return WriteResult.Confirmed("已取消")
            }
        }
        val vm = LibraryViewModel(client, FakeDaoProvider(), sessions, preferences, accounts)

        compose.setContent {
            NagiTheme {
                CompositionLocalProvider(LocalAccountIdentity provides AccountIdentity(1, "7")) {
                    FavoritesScreen(vm = vm, back = {}, onSelect = {}, onRead = {})
                }
            }
        }

        // Default tab is local favorites
        compose.onNodeWithText("仅保存在此设备，不同步 NGA").assertIsDisplayed()

        // Switch to NGA 收藏版块 tab
        compose.onNodeWithText("NGA 收藏版块").performClick()
        compose.waitUntil(3000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodesWithText("合集").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("合集").assertIsDisplayed()
        compose.onNodeWithText("非目录合集").assertIsDisplayed()
        compose.onAllNodesWithText("主题合集").assertCountEquals(2)

        // Removal requires confirmation dialog
        compose.onAllNodesWithText("移除").onLast().performClick()
        compose.onNodeWithText("取消 NGA 收藏版块？").assertIsDisplayed()
        compose.onNode(hasText("移除") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(3000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            removed.isNotEmpty()
        }
        assertEquals(listOf("collection:888"), removed)
    }

    @Config(qualifiers = "w320dp-h850dp")
    @Test fun narrowLargeFontCloudFavoritesGuestStateShowsLoginAction() {
        RuntimeEnvironment.setFontScale(2f)
        val sessions = MemorySessionStore()
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { runBlocking { it.initialize() } }
        val preferences = PreferenceStore(RuntimeEnvironment.getApplication())
        val vm = LibraryViewModel(FakeClient(), FakeDaoProvider(), sessions, preferences, accounts)
        var route = ""

        compose.setContent {
            NagiTheme("dark") {
                FavoritesScreen(vm = vm, back = {}, onSelect = { route = it }, onRead = {})
            }
        }

        compose.onNodeWithText("NGA 收藏版块").performClick()
        compose.onNodeWithText("登录 NGA").performClick()
        compose.runOnIdle { assertEquals("login", route) }
        RuntimeEnvironment.setFontScale(1f)
    }

    @Test fun topicsScreenMenuUsesForumKeyForCloudFavorites() {
        val sessions = MemorySessionStore()
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { runBlocking { it.initialize() } }
        val vm = TopicsViewModel(FakeClient(), androidx.lifecycle.SavedStateHandle(), accounts)
        val composer = dev.nagi.app.viewmodel.ComposerViewModel(FakeClient(), accounts, androidx.lifecycle.SavedStateHandle())
        var cloudToggled: Forum? = null

        compose.setContent {
            NagiTheme {
                TopicsScreen(
                    forumId = "7",
                    collection = null,
                    title = "Forum",
                    expanded = false,
                    preferences = dev.nagi.app.data.ReadingPreferences(),
                    back = {},
                    onRead = {},
                    onSearch = {},
                    vm = vm,
                    composer = composer,
                    isSignedIn = true,
                    cloudFavoriteForumKeys = setOf("forum:7"),
                    onToggleCloudForumFavorite = { cloudToggled = it },
                )
            }
        }

        compose.onNodeWithContentDescription("更多选项").performClick()
        compose.onNodeWithText("取消本地收藏版块").assertDoesNotExist()
        compose.onNodeWithText("取消 NGA 收藏版块").performClick()
        compose.runOnIdle { assertEquals("forum:7", cloudToggled?.key) }
    }
}
