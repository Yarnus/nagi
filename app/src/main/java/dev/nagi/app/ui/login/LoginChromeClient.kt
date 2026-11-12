package dev.nagi.app.ui.login

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient

internal class LoginChromeClient : WebChromeClient() {
    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = true
}
