package dev.nagi.app.data

import android.webkit.CookieManager
import android.webkit.WebStorage
import dev.nagi.sdk.NgaDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.URI
import kotlin.coroutines.resume

fun isTrustedLoginUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme.equals("https", true) && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
        NgaDomain.entries.any { URI(it.baseUrl).host.equals(uri.host, true) }
}.getOrDefault(false)

fun isAllowedLoginNavigation(url: String, mainFrame: Boolean): Boolean =
    if (mainFrame) isTrustedLoginUrl(url)
    else url == "about:blank" || runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", true) && uri.host != null && uri.userInfo == null && (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)

const val LOGIN_URL = "https://bbs.nga.cn/nuke.php?__lib=login&__act=login_ui"

interface BrowserCookies {
    suspend fun read(url: String): String?
    suspend fun clear()
}

class AndroidBrowserCookies : BrowserCookies {
    override suspend fun read(url: String): String? = withContext(Dispatchers.Main.immediate) {
        require(isTrustedLoginUrl(url))
        CookieManager.getInstance().getCookie(url)
    }
    override suspend fun clear() {
        withContext(Dispatchers.Main.immediate) {
            WebStorage.getInstance().deleteAllData()
            suspendCancellableCoroutine { continuation ->
                CookieManager.getInstance().removeAllCookies {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
        withContext(Dispatchers.IO) { CookieManager.getInstance().flush() }
    }
}
