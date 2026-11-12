package dev.nagi.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.nagi.app.data.AndroidSessionStore
import dev.nagi.sdk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class ReplyPreparationReadTest {
    @Test fun mainAndTopicPreparation(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("nagiReadSmoke") == "true")
        val store = AndroidSessionStore(ApplicationProvider.getApplicationContext())
        try {
            assumeTrue("An encrypted device session is required", store.snapshot().session != null)
            val client = createNgaClient(NgaConfig(timeoutSeconds = 15), store)
            for (pid in listOf("0", null)) {
                val result = client.preparePublish(PublishTarget(PublishAction.REPLY, topicId = "47863625", postId = pid))
                assertEquals("Main/topic replies should start without directed prefill", "", result.content)
            }
        } finally { store.close() }
    }
}
