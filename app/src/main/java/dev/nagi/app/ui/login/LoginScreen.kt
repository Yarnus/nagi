package dev.nagi.app.ui.login

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nagi.app.data.LOGIN_URL
import dev.nagi.app.data.isAllowedLoginNavigation
import dev.nagi.app.data.isTrustedLoginUrl
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.NagiHeader
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.LoginViewModel

@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(back: () -> Unit, vm: LoginViewModel = hiltViewModel()) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val state by vm.state.collectAsStateWithLifecycle()
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        val wasSecure = (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var currentUrl by remember { mutableStateOf(LOGIN_URL) }
    var loading by remember { mutableStateOf(true) }
    var webError by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Column(Modifier.fillMaxSize().imePadding()) {
        NagiHeader("登录 NGA", "仅官方 HTTPS 域允许捕获凭证", back = back) {
            CapsuleButton(
                text = "刷新",
                onClick = {
                    webError = false
                    loading = true
                    webView?.reload()
                },
                icon = NagiIcons.Refresh,
                enabled = state.ready,
                style = CapsuleStyle.Neutral,
            )
        }
        if (loading || state.checking || !state.ready) LoadingLine()
        Box(Modifier.weight(1f).fillMaxWidth().background(colors.cardSurface)) {
            if (state.ready) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView.setWebContentsDebuggingEnabled(false)
                        CookieManager.getInstance().setAcceptCookie(true)
                        WebView(ctx).apply {
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.safeBrowsingEnabled = true
                            settings.saveFormData = false
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            webChromeClient = LoginChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    if (request == null) return true
                                    return !isAllowedLoginNavigation(request.url.toString(), request.isForMainFrame)
                                }
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    webError = false
                                    loading = true
                                    if (url != null && isTrustedLoginUrl(url)) currentUrl = url
                                }
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    if (url != null && isTrustedLoginUrl(url)) {
                                        currentUrl = url
                                        vm.onPageLoaded(url)
                                    }
                                }
                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    if (request?.isForMainFrame == true) {
                                        webError = true
                                        loading = false
                                    }
                                }
                            }
                            loadUrl(LOGIN_URL)
                        }.also { webView = it }
                    },
                    onRelease = { view ->
                        webView = null
                        view.stopLoading()
                        view.clearCache(true)
                        view.clearHistory()
                        view.destroy()
                    },
                )
            }
            if (webError) {
                Box(Modifier.fillMaxSize().background(colors.groupedCanvas), contentAlignment = Alignment.Center) {
                    Notice("登录页面加载失败") {
                        webError = false
                        loading = true
                        webView?.loadUrl(LOGIN_URL)
                    }
                }
            } else if (!state.ready) {
                Box(Modifier.fillMaxSize().background(colors.groupedCanvas), contentAlignment = Alignment.Center) {
                    Notice(state.error ?: "正在准备安全登录环境…") { vm.prepare() }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
        Surface(
            color = colors.frostedChrome,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Button(
                    onClick = { vm.finish(webView?.url ?: currentUrl) },
                    enabled = state.ready && !state.checking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = CircleShape,
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.goldText,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = if (state.checking) "正在保存安全凭证…" else "完成登录",
                        style = typography.capsuleLabel,
                    )
                }
                state.error?.let {
                    Text(it, color = colors.destructive, style = typography.metadata)
                }
            }
        }
    }
}
