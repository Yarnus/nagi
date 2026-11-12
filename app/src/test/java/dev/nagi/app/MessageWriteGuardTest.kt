package dev.nagi.app

import android.app.Application
import dev.nagi.app.data.MessageWriteGuard
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MessageWriteGuardTest {
    @Test fun onlyOpaqueAccountScopedIdentitiesSurviveRecreation() = runBlocking {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("guard-test", 0)
        prefs.edit().clear().commit()
        val guard = MessageWriteGuard(prefs)
        val key = guard.key("7", "conversation:123")
        guard.add(key)
        val restored = MessageWriteGuard(prefs)
        assertTrue(restored.contains(key))
        assertFalse(restored.contains(restored.key("8", "conversation:123")))
        assertFalse(prefs.all.toString().contains("conversation:123"))
        restored.remove(key)
        assertFalse(MessageWriteGuard(prefs).contains(key))
    }
}
