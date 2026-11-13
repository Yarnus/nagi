package dev.nagi.app

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import dev.nagi.app.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class HistoryPersistenceTest {
    @Test fun migrateVersionOnePreservesFavoritesAndPositionAndIsolatesHistory() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val base = "history-migration-test"
        val file = context.getDatabasePath("$base.db")
        file.parentFile!!.mkdirs()
        context.deleteDatabase("$base.db"); context.deleteDatabase("${base}_7.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE favorites (topicId TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, savedAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE positions (topicId TEXT NOT NULL PRIMARY KEY, page INTEGER NOT NULL, `index` INTEGER NOT NULL, `offset` INTEGER NOT NULL)")
            db.execSQL("INSERT INTO favorites VALUES ('123', 'Preserved', 1)")
            db.execSQL("INSERT INTO positions VALUES ('123', 2, 3, 4)")
            db.version = 1
        }
        var databases = AccountDatabases(context, base)
        try {
            val guest = databases.forUser(null)
            assertEquals("Preserved", guest.favorite("123")?.title)
            assertEquals(ReadingPosition("123", 2, 3, 4), guest.position("123"))
            assertTrue(guest.history().first().isEmpty())
            assertTrue(guest.notifications().first().isEmpty())
            val notification = LocalNotification("one", "Title", "", "Actor", "123", "0", 42, false)
            guest.receiveNotifications(listOf(notification))
            guest.readNotifications(listOf("one"))
            guest.saveHistory(ReadingHistory("123", "Guest title", 2))
            guest.saveHistory(ReadingHistory("456", "Newest", 3))
            assertEquals(listOf("456", "123"), guest.history().first().map { it.topicId })
            assertTrue(databases.forUser("7").history().first().isEmpty())
            databases.forUser("7").saveHistory(ReadingHistory("123", "Private title", 4))
            databases.close()
            databases = AccountDatabases(context, base)
            assertEquals("Guest title", databases.forUser(null).history().first().last().title)
            assertTrue(databases.forUser(null).notifications().first().single().read)
            assertEquals("Private title", databases.forUser("7").history().first().single().title)
        } finally {
            databases.close()
            context.deleteDatabase("$base.db"); context.deleteDatabase("${base}_7.db")
        }
    }
}
