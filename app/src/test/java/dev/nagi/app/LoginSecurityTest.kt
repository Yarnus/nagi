package dev.nagi.app

import android.app.Application
import android.webkit.ConsoleMessage
import dev.nagi.app.data.isAllowedLoginNavigation
import dev.nagi.app.data.isTrustedLoginUrl
import dev.nagi.app.ui.login.LoginChromeClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LoginSecurityTest {
    @Test fun websiteConsoleArgumentsAreConsumedWithoutLogging() {
        ShadowLog.clear()
        assertTrue(LoginChromeClient().onConsoleMessage(ConsoleMessage("test-secret-callback", "https://bbs.nga.cn/", 1, ConsoleMessage.MessageLevel.LOG)))
        assertTrue(ShadowLog.getLogs().none { it.msg.contains("test-secret-callback") })
    }
    @Test fun credentialCaptureAndTopNavigationOnlyTrustExactSecureNgaOrigins() {
        assertTrue(isTrustedLoginUrl("https://bbs.nga.cn/nuke.php"))
        listOf("http://bbs.nga.cn/", "https://bbs.nga.cn.evil.test/", "https://user@bbs.nga.cn/", "https://bbs.nga.cn:444/", "file:///tmp/page", "intent://login").forEach {
            assertFalse(isTrustedLoginUrl(it))
            assertFalse(isAllowedLoginNavigation(it, mainFrame = true))
        }
        assertFalse(isAllowedLoginNavigation("about:blank", mainFrame = true))
    }
    @Test fun captchaFramesDoNotExpandCredentialTrustOrAllowInsecureSchemes() {
        assertTrue(isAllowedLoginNavigation("about:blank", mainFrame = false))
        assertTrue(isAllowedLoginNavigation("https://t.captcha.qq.com/frame", mainFrame = false))
        assertFalse(isTrustedLoginUrl("https://t.captcha.qq.com/frame"))
        listOf("http://example.test/", "file:///tmp/page", "content://data", "intent://login", "javascript:alert(1)").forEach {
            assertFalse(isAllowedLoginNavigation(it, mainFrame = false))
        }
    }
}
