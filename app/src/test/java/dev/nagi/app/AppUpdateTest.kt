package dev.nagi.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.nagi.app.data.AppUpdateState
import dev.nagi.app.data.GitHubReleaseChecker
import dev.nagi.app.data.InMemoryUpdateCacheStore
import dev.nagi.app.data.ReleaseChecker
import dev.nagi.app.data.ReleaseUpdateRepository
import dev.nagi.app.data.UPDATE_CHECK_INTERVAL_MILLIS
import dev.nagi.app.data.UpdateCheckCache
import dev.nagi.app.data.UpdateCheckStatus
import dev.nagi.app.data.isNewerRelease
import dev.nagi.app.data.parseReleaseTagFromLocation
import dev.nagi.app.data.parseSemVer
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class AppUpdateLogicTest {
    @Test fun semverComparisonHandlesMultiDigitComponentsAndInvalidTags() {
        assertTrue(isNewerRelease("0.2.0", "v0.2.1"))
        assertTrue(isNewerRelease("0.2.0", "v0.10.0"))
        assertTrue(isNewerRelease("0.2.0", "1.0.0"))
        assertFalse(isNewerRelease("0.2.0", "v0.2.0"))
        assertFalse(isNewerRelease("0.2.1", "v0.2.0"))
        assertFalse(isNewerRelease("0.2.0", "v0.2.1-beta.1"))
        assertFalse(isNewerRelease("0.2.0", "latest"))
        assertNull(parseSemVer("v0.2"))
    }

    @Test fun parseReleaseTagFromLocationAcceptsOnlyTrustedRepositoryReleaseTags() {
        assertEquals(
            "v0.2.1",
            parseReleaseTagFromLocation("https://github.com/Yarnus/nagi/releases/tag/v0.2.1"),
        )
        assertEquals(
            "v0.10.0",
            parseReleaseTagFromLocation("/Yarnus/nagi/releases/tag/0.10.0"),
        )
        assertNull(parseReleaseTagFromLocation("http://github.com/Yarnus/nagi/releases/tag/v0.2.1"))
        assertNull(parseReleaseTagFromLocation("https://evil.example/Yarnus/nagi/releases/tag/v0.2.1"))
        assertNull(parseReleaseTagFromLocation("//evil.example/Yarnus/nagi/releases/tag/v0.2.1"))
        assertNull(parseReleaseTagFromLocation("https://user@github.com/Yarnus/nagi/releases/tag/v0.2.1"))
        assertNull(parseReleaseTagFromLocation("https://github.com/other/nagi/releases/tag/v0.2.1"))
        assertNull(parseReleaseTagFromLocation("https://github.com/Yarnus/nagi/releases/tag/v0.2.1/extra"))
        assertNull(parseReleaseTagFromLocation("https://github.com/Yarnus/nagi/releases/tag/v0.2.1-rc1"))
    }

    @Test fun checkerRejectsNonHttpsUrlWithoutThrowing() = runTest {
        val checker = GitHubReleaseChecker(url = "http://github.com/Yarnus/nagi/releases/latest")
        assertNull(checker.fetchLatestReleaseTag())
    }

    @Test fun checkerUsesProvidedOkHttpClientWithoutFollowingRedirectsAndFallsBackFromHead405() = runTest {
        val methods = mutableListOf<String>()
        var nextHeadCode = 302
        var nextLocation = "https://github.com/Yarnus/nagi/releases/tag/v0.4.1"
        val baseClient = OkHttpClient.Builder()
            .followRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request()
                methods += req.method
                val code = if (req.method == "HEAD") nextHeadCode else 302
                val builder = Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("Mock")
                    .body(ByteArray(0).toResponseBody(null))
                if (code in 300..399) {
                    builder.header("Location", nextLocation)
                }
                builder.build()
            }
            .build()

        val checker = GitHubReleaseChecker(httpClient = baseClient)
        assertEquals("v0.4.1", checker.fetchLatestReleaseTag())
        assertEquals(listOf("HEAD"), methods)

        methods.clear()
        nextHeadCode = 405
        nextLocation = "https://github.com/Yarnus/nagi/releases/tag/v0.4.2"
        assertEquals("v0.4.2", checker.fetchLatestReleaseTag())
        assertEquals(listOf("HEAD", "GET"), methods)

        methods.clear()
        nextHeadCode = 200
        assertNull(checker.fetchLatestReleaseTag())
        assertEquals(listOf("HEAD"), methods)
    }

    @Test fun repositoryChecksOncePerDayCachesResultAndClearsAfterUpgrade() = runTest {
        var now = 1_000_000L
        var installedVersion = "0.2.0"
        var remoteTag: String? = "v0.2.1"
        var networkCalls = 0
        val cache = InMemoryUpdateCacheStore()

        val repo = ReleaseUpdateRepository(
            checker = ReleaseChecker {
                networkCalls++
                remoteTag
            },
            cacheStore = cache,
            currentVersion = { installedVersion },
            nowMillis = { now },
        )

        repo.checkIfNeeded()
        assertEquals(1, networkCalls)
        assertEquals(AppUpdateState(latestTag = "v0.2.1", hasUpdate = true), repo.state.value)

        // Within 24h: skips network and preserves cached update banner
        now += UPDATE_CHECK_INTERVAL_MILLIS - 1L
        repo.checkIfNeeded()
        assertEquals(1, networkCalls)
        assertEquals(AppUpdateState(latestTag = "v0.2.1", hasUpdate = true), repo.state.value)

        // Simulating app upgraded to 0.2.1 within same day: immediately clears badge without network call
        installedVersion = "0.2.1"
        repo.checkIfNeeded()
        assertEquals(1, networkCalls)
        assertEquals(AppUpdateState(latestTag = null, hasUpdate = false), repo.state.value)

        // After 24h: checks again and picks up newer version
        now += 2L
        remoteTag = "v0.3.0"
        repo.checkIfNeeded()
        assertEquals(2, networkCalls)
        assertEquals(AppUpdateState(latestTag = "v0.3.0", hasUpdate = true), repo.state.value)
    }

    @Test fun failedCheckIsSilentPreservesCacheAndDoesNotRetryInSameProcessDay() = runTest {
        var now = UPDATE_CHECK_INTERVAL_MILLIS * 2
        var networkCalls = 0
        val cache = InMemoryUpdateCacheStore(
            UpdateCheckCache(
                lastCheckedAtMillis = now - UPDATE_CHECK_INTERVAL_MILLIS - 1L,
                latestTag = "v0.2.1",
            ),
        )
        val repo = ReleaseUpdateRepository(
            checker = ReleaseChecker {
                networkCalls++
                null
            },
            cacheStore = cache,
            currentVersion = { "0.2.0" },
            nowMillis = { now },
        )

        repo.checkIfNeeded()
        assertEquals(1, networkCalls)
        assertEquals(AppUpdateState(latestTag = "v0.2.1", hasUpdate = true), repo.state.value)

        // Repeated foreground switches in the same process within 24h do not spam failed requests
        now += 60_000L
        repo.checkIfNeeded()
        assertEquals(1, networkCalls)
        assertEquals(AppUpdateState(latestTag = "v0.2.1", hasUpdate = true), repo.state.value)
    }

    @Test fun manualCheckNowBypassesThrottleReportsStatusesAndPreservesCachedUpdateOnFailure() = runTest {
        var now = 10_000_000L
        var installedVersion = "0.3.2"
        var remoteTag: String? = "v0.3.2"
        var networkCalls = 0
        val cache = InMemoryUpdateCacheStore()

        val repo = ReleaseUpdateRepository(
            checker = ReleaseChecker {
                networkCalls++
                remoteTag
            },
            cacheStore = cache,
            currentVersion = { installedVersion },
            nowMillis = { now },
        )

        repo.checkIfNeeded()
        assertEquals(1, networkCalls)
        assertEquals(UpdateCheckStatus.IDLE, repo.state.value.manualStatus)

        // Manual check within 24h bypasses throttle and reports up-to-date
        now += 1_000L
        repo.checkNow()
        assertEquals(2, networkCalls)
        assertFalse(repo.state.value.hasUpdate)
        assertFalse(repo.state.value.checking)
        assertEquals(UpdateCheckStatus.UP_TO_DATE, repo.state.value.manualStatus)

        // Manual check discovers a newer release
        now += 1_000L
        remoteTag = "v0.4.0"
        repo.checkNow()
        assertEquals(3, networkCalls)
        assertTrue(repo.state.value.hasUpdate)
        assertEquals("v0.4.0", repo.state.value.latestTag)
        assertEquals(UpdateCheckStatus.UPDATE_AVAILABLE, repo.state.value.manualStatus)

        // Failed manual check reports FAILED while preserving previously known update
        now += 1_000L
        remoteTag = null
        repo.checkNow()
        assertEquals(4, networkCalls)
        assertTrue(repo.state.value.hasUpdate)
        assertEquals("v0.4.0", repo.state.value.latestTag)
        assertEquals(UpdateCheckStatus.FAILED, repo.state.value.manualStatus)

        // Leaving screen resets manual status while keeping cached update state
        repo.resetManualStatus()
        assertEquals(UpdateCheckStatus.IDLE, repo.state.value.manualStatus)
        assertTrue(repo.state.value.hasUpdate)

        // Installed version upgraded clears badge
        installedVersion = "0.4.0"
        repo.checkIfNeeded()
        assertFalse(repo.state.value.hasUpdate)
    }

    @Test fun checkIfNeededWithoutNetworkSkipsRemoteRequestButClearsStaleBadge() = runTest {
        var now = 10_000_000L
        var installedVersion = "0.3.2"
        var networkCalls = 0
        val cache = InMemoryUpdateCacheStore(
            UpdateCheckCache(lastCheckedAtMillis = 0L, latestTag = "v0.4.0"),
        )
        val repo = ReleaseUpdateRepository(
            checker = ReleaseChecker {
                networkCalls++
                "v0.5.0"
            },
            cacheStore = cache,
            currentVersion = { installedVersion },
            nowMillis = { now },
        )

        repo.checkIfNeeded(allowNetwork = false)
        assertEquals(0, networkCalls)
        assertTrue(repo.state.value.hasUpdate)
        assertEquals("v0.4.0", repo.state.value.latestTag)

        installedVersion = "0.4.0"
        repo.checkIfNeeded(allowNetwork = false)
        assertEquals(0, networkCalls)
        assertFalse(repo.state.value.hasUpdate)
        assertNull(repo.state.value.latestTag)
    }
}

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppUpdateFlowTest {
    @get:Rule(order = 0) val fixture = object : ExternalResource() {
        override fun before() {
            FakeAppModule.releaseCheckerOverride = ReleaseChecker { "v99.0.0" }
        }
        override fun after() {
            FakeAppModule.releaseCheckerOverride = null
        }
    }
    @get:Rule(order = 1) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()

    @Before fun inject() { hilt.inject() }

    @Test fun startupSilentCheckSurfacesNewVersionBadgeInMenuSettingsAndAbout() {
        compose.onNodeWithContentDescription("头像菜单").performClick()
        compose.onNodeWithText("关于与反馈").assertIsDisplayed()
        compose.onNodeWithText("新版本").assertIsDisplayed()
        compose.onNodeWithText("设置").performClick()

        compose.onNodeWithText("关于与反馈").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("新版本").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("关于与反馈").performClick()

        compose.onNodeWithText("查看最新版本").assertIsDisplayed()
        compose.onNodeWithText("v99.0.0").assertIsDisplayed()
        compose.onNodeWithText("检查更新").performClick()
        compose.onNodeWithText("发现新版本 v99.0.0，请前往发布页下载").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35], qualifiers = "w320dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutCheckUpdateUiTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createComposeRule()

    @Test fun checkUpdateButtonAndStatusesRenderCleanlyOnNarrowLargeFont() {
        org.robolectric.RuntimeEnvironment.setFontScale(2f)
        var state by androidx.compose.runtime.mutableStateOf(AppUpdateState())
        var clicks = 0

        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                dev.nagi.app.ui.components.LocalAppUpdate provides state,
            ) {
                dev.nagi.app.ui.theme.NagiTheme("dark") {
                    dev.nagi.app.ui.library.AboutScreen(
                        back = {},
                        onCheckUpdate = { clicks++ },
                    )
                }
            }
        }

        compose.onNodeWithText("检查更新").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }

        compose.runOnIdle {
            state = AppUpdateState(checking = true, manualStatus = UpdateCheckStatus.CHECKING)
        }
        compose.onNodeWithText("检查中…").assertIsDisplayed()
        compose.onNodeWithText("正在检查 GitHub 最新版本…").assertIsDisplayed()

        compose.runOnIdle {
            state = AppUpdateState(checking = false, manualStatus = UpdateCheckStatus.UP_TO_DATE)
        }
        compose.onNodeWithText("当前已是最新版本").assertIsDisplayed()

        compose.runOnIdle {
            state = AppUpdateState(checking = false, manualStatus = UpdateCheckStatus.FAILED)
        }
        compose.onNodeWithText("检查更新失败，请检查网络后重试").assertIsDisplayed()

        compose.runOnIdle {
            state = AppUpdateState(
                latestTag = "v99.0.0",
                hasUpdate = true,
                manualStatus = UpdateCheckStatus.UPDATE_AVAILABLE,
            )
        }
        compose.onNodeWithText("发现新版本 v99.0.0，请前往发布页下载").assertIsDisplayed()
        org.robolectric.RuntimeEnvironment.setFontScale(1f)
    }
}
