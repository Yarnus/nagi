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
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.nagi.app.navigation.ClipboardLinkTracker
import dev.nagi.app.navigation.NagiApp
import dev.nagi.sdk.ExtractedExternalLink

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pendingDeepLink by mutableStateOf<String?>(null)
    private var pendingClipboardLink by mutableStateOf<ExtractedExternalLink?>(null)
    private var pendingClipboardTimestamp: Long = 0L
    private var needsClipboardCheck: Boolean = false
    private var clipboardRetryCount: Int = 0
    private val clipboardCheckRunnable = Runnable { runPendingClipboardCheck() }

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
                    ClipboardLinkTracker.markHandled(handled.url, pendingClipboardTimestamp)
                    pendingClipboardLink = null
                    pendingClipboardTimestamp = 0L
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        needsClipboardCheck = pendingDeepLink == null
        clipboardRetryCount = 0
    }

    override fun onResume() {
        super.onResume()
        scheduleClipboardCheck()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            scheduleClipboardCheck()
        }
    }

    override fun onPause() {
        super.onPause()
        window?.decorView?.removeCallbacks(clipboardCheckRunnable)
    }

    private fun scheduleClipboardCheck(delayMs: Long = 0L) {
        if (!needsClipboardCheck || pendingDeepLink != null) return
        val decor = window?.decorView ?: return
        decor.removeCallbacks(clipboardCheckRunnable)
        if (delayMs > 0L) decor.postDelayed(clipboardCheckRunnable, delayMs)
        else decor.post(clipboardCheckRunnable)
    }

    private fun runPendingClipboardCheck() {
        if (!needsClipboardCheck || pendingDeepLink != null) return
        val focused = hasWindowFocus()
        if (!focused && clipboardRetryCount < MAX_CLIPBOARD_RETRIES) {
            clipboardRetryCount++
            scheduleClipboardCheck(delayMs = CLIPBOARD_RETRY_DELAY_MS)
            return
        }
        if (!focused && !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            return
        }
        val result = ClipboardLinkTracker.inspectClipboardResult(this)
        when {
            result.readAttempted -> {
                needsClipboardCheck = false
                result.link?.let { extracted ->
                    pendingClipboardLink = extracted
                    pendingClipboardTimestamp = result.timestamp
                }
            }
            result.hasCandidateClip && clipboardRetryCount < MAX_CLIPBOARD_RETRIES -> {
                clipboardRetryCount++
                scheduleClipboardCheck(delayMs = CLIPBOARD_RETRY_DELAY_MS)
            }
            else -> needsClipboardCheck = false
        }
    }

    internal fun handleDeepLinkIntent(intent: Intent?) {
        setIntent(intent)
        extractDeepLink(intent)?.let {
            needsClipboardCheck = false
            pendingClipboardLink = null
            pendingClipboardTimestamp = 0L
            pendingDeepLink = it
        }
    }

    companion object {
        private const val MAX_CLIPBOARD_RETRIES = 2
        private const val CLIPBOARD_RETRY_DELAY_MS = 120L

        internal fun extractDeepLink(intent: Intent?): String? {
            if (intent?.action != Intent.ACTION_VIEW) return null
            return intent.dataString?.takeIf { it.isNotBlank() }
        }
    }
}
