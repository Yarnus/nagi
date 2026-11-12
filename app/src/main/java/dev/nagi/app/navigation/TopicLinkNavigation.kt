package dev.nagi.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import dev.nagi.sdk.NgaTopicLinks

@Composable
internal fun rememberTopicLinkHandler(onRead: (String) -> Unit): UriHandler {
    val external = LocalUriHandler.current
    return remember(external, onRead) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val id = NgaTopicLinks.topicId(uri)
                if (id != null) onRead(id) else external.openUri(uri)
            }
        }
    }
}
