package dev.nagi.app.data

import android.content.SharedPreferences
import java.security.MessageDigest

class MessageWriteGuard(private val preferences: SharedPreferences? = null) {
    private val pending = preferences?.getStringSet("pending", emptySet()).orEmpty().toMutableSet()
    fun key(userId: String?, destination: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$userId:$destination".toByteArray()).joinToString("") { "%02x".format(it) }
    @Synchronized fun contains(key: String) = key in pending
    suspend fun add(key: String) = update(key, true)
    suspend fun remove(key: String) = update(key, false)
    private suspend fun update(key: String, add: Boolean) {
        if (preferences == null) change(key, add)
        else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { change(key, add) }
    }
    @Synchronized private fun change(key: String, add: Boolean) {
        val next = if (add) pending + key else pending - key
        check(preferences?.edit()?.putStringSet("pending", next)?.commit() != false) { "Cannot persist write guard" }
        pending.clear()
        pending.addAll(next)
    }
}
