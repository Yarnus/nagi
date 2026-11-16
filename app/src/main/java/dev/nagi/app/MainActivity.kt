package dev.nagi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import dev.nagi.app.navigation.ClipboardLinkTracker
import dev.nagi.app.navigation.NagiApp
import dev.nagi.sdk.ExtractedExternalLink

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pendingDeepLink by mutableStateOf<String?>(null)
    private var pendingClipboardLink by mutableStateOf<ExtractedExternalLink?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            pendingDeepLink = extractDeepLink(intent)
        }
        enableEdgeToEdge()
        setContent {
            NagiApp(
                width = calculateWindowSizeClass(this).widthSizeClass,
                deepLinkUri = pendingDeepLink,
                onDeepLinkConsumed = { pendingDeepLink = null },
                clipboardLink = pendingClipboardLink,
                onClipboardLinkHandled = { handled ->
                    ClipboardLinkTracker.markHandled(handled.url)
                    pendingClipboardLink = null
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pendingDeepLink == null) {
            ClipboardLinkTracker.inspectClipboard(this)?.let { extracted ->
                pendingClipboardLink = extracted
            }
        }
    }

    internal fun handleDeepLinkIntent(intent: Intent?) {
        setIntent(intent)
        extractDeepLink(intent)?.let {
            pendingClipboardLink = null
            pendingDeepLink = it
        }
    }

    companion object {
        internal fun extractDeepLink(intent: Intent?): String? {
            if (intent?.action != Intent.ACTION_VIEW) return null
            return intent.dataString?.takeIf { it.isNotBlank() }
        }
    }
}
