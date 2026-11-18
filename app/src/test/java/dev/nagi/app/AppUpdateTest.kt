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
import dev.nagi.app.data.ReleaseDownloadResult
import dev.nagi.app.data.ReleaseDownloader
import dev.nagi.app.data.ReleaseUpdateRepository
import dev.nagi.app.data.UPDATE_CHECK_INTERVAL_MILLIS
import dev.nagi.app.data.UpdateCheckCache
import dev.nagi.app.data.UpdateCheckStatus
import dev.nagi.app.data.UpdateDownloadState
import dev.nagi.app.data.isNewerRelease
import dev.nagi.app.data.isTrustedReleaseDownloadUrl
import dev.nagi.app.data.parseReleaseTagFromLocation
import dev.nagi.app.data.parseSemVer
import dev.nagi.app.data.parseSha256Sums
import dev.nagi.app.data.selectReleaseAbi
import dev.nagi.app.data.sha256Hex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
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

    @Test fun abiSelectionSha256SumsAndTrustedDownloadUrlValidation() {
        assertEquals("arm64-v8a", selectReleaseAbi(arrayOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("x86_64", selectReleaseAbi(arrayOf("x86_64", "arm64-v8a")))
        assertEquals("armeabi-v7a", selectReleaseAbi(arrayOf("mips", "armeabi-v7a")))
        assertNull(selectReleaseAbi(arrayOf("mips", "riscv64")))

        val validHash = "a".repeat(64)
        val secondHash = "B".repeat(64)
        val parsed = parseSha256Sums(
            """
            $validHash  ./nagi-v0.4.0-arm64-v8a.apk
            $secondHash *dist/nagi-v0.4.0-x86_64.apk
            short  nagi-v0.4.0-invalid.apk
            """.trimIndent(),
        )
        assertEquals(validHash, parsed["nagi-v0.4.0-arm64-v8a.apk"])
        assertEquals(secondHash.lowercase(), parsed["nagi-v0.4.0-x86_64.apk"])
        assertNull(parsed["nagi-v0.4.0-invalid.apk"])

        val temp = kotlin.io.path.createTempFile("nagi-hash-test", ".bin").toFile()
        try {
            temp.writeBytes("nagi".encodeToByteArray())
            assertEquals("7037c5865b4629a983e7a3bb2fec71ac1bf504d99702fc433fe801b1a909d2b7", sha256Hex(temp))
        } finally {
            temp.delete()
        }

        assertTrue(isTrustedReleaseDownloadUrl("https://github.com/Yarnus/nagi/releases/download/v0.4.0/SHA256SUMS"))
        assertTrue(isTrustedReleaseDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset/123"))
        assertTrue(isTrustedReleaseDownloadUrl("https://release-assets.githubusercontent.com/asset.apk"))
        assertFalse(isTrustedReleaseDownloadUrl("http://github.com/Yarnus/nagi/releases"))
        assertFalse(isTrustedReleaseDownloadUrl("https://user@github.com/Yarnus/nagi/releases"))
        assertFalse(isTrustedReleaseDownloadUrl("https://github.com:8443/Yarnus/nagi/releases"))
        assertFalse(isTrustedReleaseDownloadUrl("https://notgithub.com/asset.apk"))
        assertFalse(isTrustedReleaseDownloadUrl("https://githubusercontent.com.evil.com/asset.apk"))
    }

    @Test fun downloadUpdateTransitionsPermissionRefreshCancelAndCleanup() = runTest {
        var canInstall = false
        var cleanupCalls = 0
        var nextResult: ReleaseDownloadResult = ReleaseDownloadResult.Ready("/tmp/nagi-v0.4.0.apk")
        val blocker = CompletableDeferred<Unit>()
        var blockOnDownload = false
        var observedVerifying = false
        val fakeDownloader = object : ReleaseDownloader {
            override suspend fun downloadAndVerify(
                tag: String,
                onProgress: (Long, Long?) -> Unit,
                onVerifying: () -> Unit,
            ): ReleaseDownloadResult {
                onProgress(512L, 1024L)
                if (blockOnDownload) {
                    blocker.await()
                }
                onVerifying()
                observedVerifying = true
                return nextResult
            }

            override fun canInstallPackages(): Boolean = canInstall

            override fun cleanupCachedUpdates() {
                cleanupCalls++
            }
        }
        var installedVersion = "0.3.0"
        var remoteTag: String? = "v0.4.0"
        val repo = ReleaseUpdateRepository(
            checker = ReleaseChecker { remoteTag },
            cacheStore = InMemoryUpdateCacheStore(),
            currentVersion = { installedVersion },
            nowMillis = { 10_000L },
            downloader = fakeDownloader,
        )

        // Cannot download before update is known
        assertNull(repo.downloadUpdate())

        repo.checkNow()
        assertTrue(repo.state.value.hasUpdate)

        // Download ready with missing install permission -> ReadyToInstall(needsInstallPermission = true)
        val firstReady = repo.downloadUpdate() as UpdateDownloadState.ReadyToInstall
        assertTrue(observedVerifying)
        assertEquals("v0.4.0", firstReady.tag)
        assertEquals("/tmp/nagi-v0.4.0.apk", firstReady.apkPath)
        assertTrue(firstReady.needsInstallPermission)

        // Granting permission & refreshing clears needsInstallPermission
        canInstall = true
        repo.refreshInstallPermission()
        val refreshed = repo.state.value.download as UpdateDownloadState.ReadyToInstall
        assertFalse(refreshed.needsInstallPermission)

        // Cancellation resets state to Idle
        blockOnDownload = true
        val started = CompletableDeferred<Unit>()
        val job = launch {
            started.complete(Unit)
            repo.downloadUpdate()
        }
        started.await()
        testScheduler.advanceUntilIdle()
        assertTrue(repo.state.value.download is UpdateDownloadState.Downloading)
        job.cancel()
        job.join()
        assertEquals(UpdateDownloadState.Idle, repo.state.value.download)

        // Rejected/Failed download transitions to Failed with message
        blockOnDownload = false
        nextResult = ReleaseDownloadResult.Rejected("校验失败")
        val failed = repo.downloadUpdate() as UpdateDownloadState.Failed
        assertEquals("校验失败", failed.message)

        // Upgraded version clears badge & cleans cached updates
        installedVersion = "0.4.0"
        remoteTag = "v0.4.0"
        repo.checkNow()
        assertFalse(repo.state.value.hasUpdate)
        assertEquals(UpdateDownloadState.Idle, repo.state.value.download)
        assertTrue(cleanupCalls >= 1)
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
        compose.onNodeWithText("发现新版本 v99.0.0，可直接下载或前往发布页查看").assertIsDisplayed()
        compose.onNodeWithText("下载更新").performClick()
        compose.onNodeWithText("暂不支持应用内下载，请前往发布页下载").assertIsDisplayed()
        compose.onNodeWithText("重试下载").assertIsDisplayed()
        compose.onNodeWithText("前往发布页").assertIsDisplayed()
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
                download = UpdateDownloadState.Idle,
            )
        }
        compose.onNodeWithText("下载更新").assertIsDisplayed()

        compose.runOnIdle {
            state = AppUpdateState(
                latestTag = "v99.0.0",
                hasUpdate = true,
                manualStatus = UpdateCheckStatus.UPDATE_AVAILABLE,
                download = UpdateDownloadState.Downloading("v99.0.0", 512L * 1024L, 1024L * 1024L),
            )
        }
        compose.onNodeWithText("取消下载").assertIsDisplayed()

        compose.runOnIdle {
            state = AppUpdateState(
                latestTag = "v99.0.0",
                hasUpdate = true,
                manualStatus = UpdateCheckStatus.UPDATE_AVAILABLE,
                download = UpdateDownloadState.Verifying("v99.0.0"),
            )
        }
        compose.onNodeWithText("正在校验 v99.0.0 安装包…").assertIsDisplayed()

        compose.runOnIdle {
            state = AppUpdateState(
                latestTag = "v99.0.0",
                hasUpdate = true,
                manualStatus = UpdateCheckStatus.UPDATE_AVAILABLE,
                download = UpdateDownloadState.ReadyToInstall("v99.0.0", "/tmp/nagi.apk", needsInstallPermission = true),
            )
        }
        compose.onNodeWithText("授权并安装").assertIsDisplayed()

        compose.runOnIdle {
            state = AppUpdateState(
                latestTag = "v99.0.0",
                hasUpdate = true,
                manualStatus = UpdateCheckStatus.UPDATE_AVAILABLE,
                download = UpdateDownloadState.ReadyToInstall("v99.0.0", "/tmp/nagi.apk", needsInstallPermission = false),
            )
        }
        compose.onNodeWithText("安装更新").assertIsDisplayed()
        org.robolectric.RuntimeEnvironment.setFontScale(1f)
    }
}
