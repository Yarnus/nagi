package dev.nagi.app

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.nagi.app.data.AndroidSessionStore
import dev.nagi.sdk.createNgaClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationReadTest {
    @Test fun existingAccountFeedIsReadable() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("nagiNotificationRead") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sessions = AndroidSessionStore(instrumentation.targetContext)
        assumeTrue(sessions.snapshot().session != null)
        val feed = createNgaClient(sessions = sessions).getNotificationFeed()
        feed.unreadCount?.let { assertTrue("Server unread count must be non-negative", it >= 0) }
        assertTrue(feed.items.all { it.id.isNotBlank() })
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "Notification read verified: ${feed.items.size} items, unread count known=${feed.unreadCount != null}\n")
        })
    }
}
