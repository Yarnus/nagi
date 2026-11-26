package dev.nagi.app

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nagi.app.data.AccountManager
import dev.nagi.app.data.DynamicDomainNgaClient
import dev.nagi.app.data.MAX_FONT_SIZE
import dev.nagi.app.data.MIN_FONT_SIZE
import dev.nagi.app.data.PreferenceStore
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.data.calculateAppCacheBytes
import dev.nagi.app.data.clearAppCache
import dev.nagi.app.data.formatCacheSize
import dev.nagi.app.ui.library.SettingsScreen
import dev.nagi.app.ui.post.ReaderScreen
import dev.nagi.app.ui.post.ReaderTopBar
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.LibraryViewModel
import dev.nagi.app.viewmodel.PostRatingViewModel
import dev.nagi.app.viewmodel.ReaderViewModel
import dev.nagi.app.viewmodel.ReplySessionViewModel
import dev.nagi.sdk.Forum
import dev.nagi.sdk.MemorySessionStore
import dev.nagi.sdk.NgaClient
import dev.nagi.sdk.NgaDomain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SettingsLogicTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        runBlocking {
            val prefs = PreferenceStore(RuntimeEnvironment.getApplication())
            prefs.resetTypography()
            prefs.theme("system")
            prefs.clipboardLinkDetection(true)
            prefs.autoCheckUpdates(true)
            prefs.ngaDomain(NgaDomain.PRIMARY)
        }
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun preferenceStoreEnforcesBoundsCouplesLineHeightAndPersistsOptions() = runTest(dispatcher) {
        val prefs = PreferenceStore(RuntimeEnvironment.getApplication())
        val initial = prefs.preferences.first()
        assertEquals(ReadingPreferences(), initial)

        prefs.font(10)
        assertEquals(MIN_FONT_SIZE, prefs.preferences.first().fontSize)
        prefs.font(30)
        assertEquals(MAX_FONT_SIZE, prefs.preferences.first().fontSize)

        // When font is 22 and stored lineHeight is 26, effective line height tracks font + 6 = 28
        prefs.font(22)
        val coupled = prefs.preferences.first()
        assertEquals(28, coupled.minLineHeight)
        assertEquals(28, coupled.effectiveLineHeight)

        // Lowering font back to 16 restores stored 26 sp line height
        prefs.font(16)
        assertEquals(26, prefs.preferences.first().effectiveLineHeight)

        // Reset restores typography defaults without wiping browsing preferences
        prefs.clipboardLinkDetection(false)
        prefs.autoCheckUpdates(false)
        prefs.ngaDomain(NgaDomain.APP)
        prefs.font(20)
        prefs.lineHeight(34)
        prefs.resetTypography()

        val afterReset = prefs.preferences.first()
        assertTrue(afterReset.isDefaultTypography)
        assertFalse(afterReset.clipboardLinkDetection)
        assertFalse(afterReset.autoCheckUpdates)
        assertEquals(NgaDomain.APP, afterReset.ngaDomain)

        // Unsupported ALTERNATE maps safely back to PRIMARY
        prefs.ngaDomain(NgaDomain.ALTERNATE)
        assertEquals(NgaDomain.PRIMARY, prefs.preferences.first().ngaDomain)
    }

    @Test fun dynamicDomainClientSwitchesDelegateWhenPreferenceChanges() = runTest(dispatcher) {
        val prefs = PreferenceStore(RuntimeEnvironment.getApplication())
        val sessions = MemorySessionStore()
        val createdDomains = mutableListOf<NgaDomain>()
        val client = DynamicDomainNgaClient(sessions, prefs) { domain, _ ->
            createdDomains += domain
            FakeClient()
        }

        client.getForums()
        assertEquals(listOf(NgaDomain.PRIMARY), createdDomains)

        prefs.ngaDomain(NgaDomain.LEGACY)
        client.getForums()
        assertEquals(listOf(NgaDomain.PRIMARY, NgaDomain.LEGACY), createdDomains)

        // Same domain reuses cached delegate
        client.getForums()
        assertEquals(listOf(NgaDomain.PRIMARY, NgaDomain.LEGACY), createdDomains)
    }

    @Test fun dynamicDomainClientClosesRetiredDelegatesAfterInFlightCallsFinish() = runTest(dispatcher) {
        val prefs = PreferenceStore(RuntimeEnvironment.getApplication())
        val sessions = MemorySessionStore()
        val closed = mutableListOf<NgaDomain>()
        val inFlightGate = CompletableDeferred<Unit>()

        class ClosableClient(private val domain: NgaDomain) : NgaClient by FakeClient(), AutoCloseable {
            override suspend fun getForums(): List<Forum> {
                if (domain == NgaDomain.PRIMARY) inFlightGate.await()
                return emptyList()
            }
            override fun close() {
                closed += domain
            }
        }

        val client = DynamicDomainNgaClient(sessions, prefs) { domain, _ -> ClosableClient(domain) }
        val primaryCall = launch { client.getForums() }
        runCurrent()
        assertTrue(closed.isEmpty())

        prefs.ngaDomain(NgaDomain.APP)
        client.getForums()
        // Primary has an active in-flight call so it must stay open until finished.
        assertTrue(closed.isEmpty())

        inFlightGate.complete(Unit)
        runCurrent()
        assertTrue(primaryCall.isCompleted)
        assertEquals(listOf(NgaDomain.PRIMARY), closed)

        // Switching again when APP is idle closes APP immediately.
        prefs.ngaDomain(NgaDomain.LEGACY)
        client.getForums()
        assertEquals(listOf(NgaDomain.PRIMARY, NgaDomain.APP), closed)
    }

    @Test fun cacheStorageFormatsAndClearsSharedImagesDir() = runBlocking {
        assertEquals("暂无缓存", formatCacheSize(0))
        assertEquals("0.5 KB", formatCacheSize(512))
        assertEquals("2.0 MB", formatCacheSize(2L * 1024L * 1024L))

        val context = RuntimeEnvironment.getApplication()
        val sharedDir = File(context.cacheDir, "shared-images").apply { mkdirs() }
        File(sharedDir, "sample.jpg").writeBytes(ByteArray(2048))

        val before = calculateAppCacheBytes(context)
        assertTrue(before >= 2048L)

        val after = clearAppCache(context)
        assertEquals(0L, after)
        assertFalse(File(sharedDir, "sample.jpg").exists())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w320dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Before fun resetPreferences() {
        runBlocking {
            val prefs = PreferenceStore(RuntimeEnvironment.getApplication())
            prefs.resetTypography()
            prefs.theme("system")
            prefs.clipboardLinkDetection(true)
            prefs.autoCheckUpdates(true)
            prefs.ngaDomain(NgaDomain.PRIMARY)
        }
    }

    @Test fun settingsScreenHandlesStepperBoundsSwitchesDomainSheetCacheAndLargeFont() {
        val context = RuntimeEnvironment.getApplication()
        val sharedDir = File(context.cacheDir, "shared-images").apply { mkdirs() }
        File(sharedDir, "cached.jpg").writeBytes(ByteArray(4096))

        val sessions = MemorySessionStore()
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { runBlocking { it.initialize() } }
        val prefs = PreferenceStore(context)
        val vm = LibraryViewModel(FakeClient(), FakeDaoProvider(), sessions, prefs, accounts)
        var selectedRoute: String? = null

        compose.setContent {
            val density = LocalDensity.current
            val currentPrefs by prefs.preferences.collectAsStateWithLifecycle(ReadingPreferences())
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                NagiTheme(currentPrefs.theme) {
                    SettingsScreen(
                        vm = vm,
                        preferences = currentPrefs,
                        accountLabel = "当前：访客模式",
                        back = {},
                        onSelect = { selectedRoute = it },
                    )
                }
            }
        }
        compose.waitForIdle()

        // Stepper lower bound disables decrease button
        runBlocking { prefs.font(MIN_FONT_SIZE) }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("${MIN_FONT_SIZE} sp").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("正文字号").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("减小字号").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("增大字号").assertIsEnabled()

        // Stepper upper bound disables increase button and shows coupled line height minimum
        runBlocking { prefs.font(MAX_FONT_SIZE) }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("${MAX_FONT_SIZE} sp").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("增大字号").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("减小字号").assertIsEnabled()

        runBlocking { prefs.font(22) }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("28 sp（最小 28 sp）").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("28 sp（最小 28 sp）").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("紧凑").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("宽松").assertIsEnabled().performClick()
        compose.waitUntil(5000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            runBlocking { prefs.preferences.first().effectiveLineHeight == 30 }
        }

        // Reset restores defaults
        compose.onNodeWithText("恢复默认排版").performScrollTo().performClick()
        compose.waitUntil(5000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            runBlocking { prefs.preferences.first().isDefaultTypography }
        }
        compose.onNodeWithText("已默认").performScrollTo().assertIsDisplayed()

        // Switches update preferences
        compose.onNodeWithContentDescription("剪贴板链接识别").performScrollTo().assertIsOn().performClick()
        compose.waitUntil(5000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            runBlocking { !prefs.preferences.first().clipboardLinkDetection }
        }
        compose.onNodeWithContentDescription("剪贴板链接识别").assertIsOff()

        compose.onNodeWithContentDescription("自动检查更新").performScrollTo().assertIsOn().performClick()
        compose.waitUntil(5000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            runBlocking { !prefs.preferences.first().autoCheckUpdates }
        }
        compose.onNodeWithContentDescription("自动检查更新").assertIsOff()

        // Domain picker sheet updates preferred domain
        compose.onNodeWithText("NGA 访问线路").performScrollTo().performClick()
        compose.waitUntil(5000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodesWithText("选择 NGA 访问线路").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("客户端线路").performClick()
        compose.waitUntil(5000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            runBlocking { prefs.preferences.first().ngaDomain == NgaDomain.APP }
        }
        compose.onNodeWithText("客户端线路 · ngabbs.com").performScrollTo().assertIsDisplayed()

        // Cache clear confirmation cleans up shared-images
        compose.onNodeWithText("清理图片与临时缓存").performScrollTo().performClick()
        compose.onNodeWithText("清理图片与临时缓存？").assertIsDisplayed()
        compose.onNode(hasText("清理") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodesWithText("暂无缓存").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("暂无缓存").performScrollTo().assertIsDisplayed()

        // Account entry navigates to accounts
        compose.onNodeWithText("账号管理").performScrollTo().performClick()
        assertEquals("accounts", selectedRoute)
    }

    @Test fun readerTopBarExposesTypographyActionOnlyWhenProvided() {
        var typographyClicks = 0
        var showTypography by mutableStateOf(false)
        compose.setContent {
            NagiTheme {
                ReaderTopBar(
                    title = "测试主题",
                    showTitle = true,
                    favorite = false,
                    authorOnly = false,
                    enabled = true,
                    canFilter = true,
                    back = {},
                    onFavorite = {},
                    onFilter = {},
                    onRefresh = {},
                    onTypography = if (showTypography) ({ typographyClicks++ }) else null,
                )
            }
        }
        compose.onNodeWithContentDescription("主题操作").performClick()
        compose.onNodeWithText("阅读排版").assertDoesNotExist()
        compose.onNodeWithText("刷新").performClick()

        compose.runOnIdle { showTypography = true }
        compose.onNodeWithContentDescription("主题操作").performClick()
        compose.onNodeWithText("阅读排版").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, typographyClicks) }
    }

    @Test fun readerScreenTypographySheetUpdatesAndResetsPreferencesInPlace() {
        val context = RuntimeEnvironment.getApplication()
        val sessions = MemorySessionStore()
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { runBlocking { it.initialize() } }
        val daos = FakeDaoProvider()
        val prefs = PreferenceStore(context)
        val client = FakeClient()
        val readerVm = ReaderViewModel(client, daos, sessions, accounts)
        val replyVm = ReplySessionViewModel(client, accounts, SavedStateHandle())
        val ratingVm = PostRatingViewModel(client, accounts, SavedStateHandle())

        compose.setContent {
            val currentPrefs by prefs.preferences.collectAsStateWithLifecycle(ReadingPreferences())
            NagiTheme(currentPrefs.theme) {
                ReaderScreen(
                    id = "10",
                    preferences = currentPrefs,
                    back = {},
                    vm = readerVm,
                    replies = replyVm,
                    ratings = ratingVm,
                    onFontSizeChange = { runBlocking { prefs.font(it) } },
                    onLineHeightChange = { runBlocking { prefs.lineHeight(it) } },
                    onResetTypography = { runBlocking { prefs.resetTypography() } },
                )
            }
        }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Post 0").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("主题操作").performClick()
        compose.onNodeWithText("阅读排版").performClick()
        compose.waitUntil(5000) {
            compose.onNodeWithText("正文字号 16 sp · 行高 26 sp").isDisplayed()
        }
        compose.onNodeWithText("恢复默认").assertIsNotEnabled()

        compose.onNodeWithContentDescription("增大字号").performClick()
        compose.waitUntil(5000) {
            runBlocking { prefs.preferences.first().fontSize == 17 }
        }
        compose.onNodeWithText("正文字号 17 sp · 行高 26 sp").assertIsDisplayed()

        compose.onNodeWithText("恢复默认").assertIsEnabled().performClick()
        compose.waitUntil(5000) {
            runBlocking { prefs.preferences.first().isDefaultTypography }
        }
        compose.onNodeWithContentDescription("关闭面板").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("正文字号 16 sp · 行高 26 sp").fetchSemanticsNodes().isEmpty()
        }
    }
}
