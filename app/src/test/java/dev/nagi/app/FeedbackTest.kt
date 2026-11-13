package dev.nagi.app

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.nagi.app.ui.library.AboutScreen
import dev.nagi.app.ui.library.feedbackUri
import dev.nagi.app.ui.theme.NagiTheme
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
class FeedbackTest {
    @get:Rule val compose = createComposeRule()

    private fun showAbout(theme: String = "light", fontScale: Float = 1f, start: (Intent) -> Unit) {
        compose.setContent {
            val base = LocalContext.current
            val context = remember(base) {
                object : ContextWrapper(base) {
                    override fun startActivity(intent: Intent) { start(intent) }
                }
            }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                NagiTheme(theme) { AboutScreen {} }
            }
        }
    }

    @Test fun feedbackUriEncodesOnlyBasicEnvironmentFields() {
        val uri = feedbackUri("0.1.4 + debug&test", "16", "设备 / Pixel & Pro?#")
        assertEquals("https", uri.scheme)
        assertEquals("github.com", uri.host)
        assertEquals("/Yarnus/nagi/issues/new/choose", uri.path)
        assertEquals(setOf("app_version", "android_version", "device"), uri.queryParameterNames)
        assertEquals("0.1.4 + debug&test", uri.getQueryParameter("app_version"))
        assertEquals("16", uri.getQueryParameter("android_version"))
        assertEquals("设备 / Pixel & Pro?#", uri.getQueryParameter("device"))
        assertNull(uri.fragment)
        assertNull(uri.userInfo)
    }

    @Test fun aboutOpensGitHubChooserWithEnvironmentAndNoSharePayload() {
        var opened: Intent? = null
        showAbout { opened = it }
        compose.onNodeWithText("需要登录 GitHub", substring = true).assertIsDisplayed()
        compose.onNodeWithText("前往 GitHub 反馈").performClick()
        compose.runOnIdle {
            val intent = requireNotNull(opened)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertNull(intent.extras)
            val uri = requireNotNull(intent.data)
            assertEquals("/Yarnus/nagi/issues/new/choose", uri.path)
            assertEquals(Build.VERSION.RELEASE, uri.getQueryParameter("android_version"))
            assertEquals("${Build.MANUFACTURER} ${Build.MODEL}", uri.getQueryParameter("device"))
            assertEquals(RuntimeEnvironment.getApplication().packageManager.getPackageInfo(
                RuntimeEnvironment.getApplication().packageName, 0,
            ).versionName.orEmpty(), uri.getQueryParameter("app_version"))
        }
        compose.onNodeWithText("开源许可").assertIsDisplayed()
    }

    @Test fun missingBrowserOffersCopyWithoutCrashing() {
        showAbout { throw ActivityNotFoundException() }
        compose.onNodeWithText("前往 GitHub 反馈").performClick()
        compose.onNodeWithText("无法打开 GitHub", substring = true).assertIsDisplayed()
        compose.onNodeWithText("复制反馈链接").performClick()
        compose.onNodeWithText("无法打开 GitHub", substring = true).assertDoesNotExist()
        compose.runOnIdle {
            val clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
            val copied = clipboard.primaryClip!!.getItemAt(0).text.toString()
            assertEquals(feedbackUri(
                RuntimeEnvironment.getApplication().packageManager.getPackageInfo(
                    RuntimeEnvironment.getApplication().packageName, 0,
                ).versionName.orEmpty(), Build.VERSION.RELEASE, "${Build.MANUFACTURER} ${Build.MODEL}",
            ).toString(), copied)
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun narrowDarkLargeTextKeepsFeedbackAndLicenseReachable() {
        var opened = false
        showAbout(theme = "dark", fontScale = 2f) { opened = true }
        compose.onNodeWithText("前往 GitHub 反馈").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(opened) }
        compose.onNodeWithText("开源许可").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("前往 GitHub 反馈").performScrollTo().assertIsDisplayed()
    }

    @Test fun missingBrowserDialogCanCloseAndRetry() {
        var attempts = 0
        showAbout {
            attempts++
            if (attempts == 1) throw ActivityNotFoundException()
        }
        compose.onNodeWithText("前往 GitHub 反馈").performClick()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("前往 GitHub 反馈").performClick()
        compose.onNodeWithText("无法打开 GitHub", substring = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, attempts) }
    }
}
