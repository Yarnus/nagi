package dev.nagi.app

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
import dev.nagi.app.data.isNewerRelease
import dev.nagi.app.data.parseReleaseTagFromLocation
import dev.nagi.app.data.parseSemVer
import kotlinx.coroutines.test.runTest
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
    }
}
