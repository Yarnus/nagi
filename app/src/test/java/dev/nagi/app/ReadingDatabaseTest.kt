package dev.nagi.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.nagi.app.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReadingDatabaseTest {
    @Test fun notificationsPersistPerAccountAndDuplicateFetchDoesNotResetRead() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "notification-test"
        context.deleteDatabase("${name}_7.db"); context.deleteDatabase("${name}_8.db")
        val record = LocalNotification("one", "Title", "Summary", "Actor", "2", "3", 42, false)
        var databases = AccountDatabases(context, name)
        try {
            databases.forUser("7").receiveNotifications(listOf(record))
            databases.forUser("7").readNotifications(listOf(record.id))
            databases.forUser("7").receiveNotifications(listOf(record))
            assertTrue(databases.forUser("7").notifications().first().single().read)
            assertTrue(databases.forUser("8").notifications().first().isEmpty())
            databases.close()
            databases = AccountDatabases(context, name)
            assertEquals(listOf(record.copy(read = true)), databases.forUser("7").notifications().first())
        } finally {
            databases.close()
            context.deleteDatabase("${name}_7.db"); context.deleteDatabase("${name}_8.db")
        }
    }

    @Test fun userTagMigrationPreservesExistingDataAndPersistsPerAccount() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "user-tag-migration"
        context.deleteDatabase("${name}_7.db"); context.deleteDatabase("${name}_8.db")
        var databases = AccountDatabases(context, name)
        try {
            databases.forUser("7").saveFavorite(LocalFavorite("123", "Preserved", 1))
            val notification = LocalNotification("one", "Title", "", "Actor", "123", "0", 42, false, true)
            databases.forUser("7").receiveNotifications(listOf(notification))
            databases.close()
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("${name}_7.db").path, null, 0).use {
                it.execSQL("DROP TABLE user_tags")
                it.version = 4
            }
            databases = AccountDatabases(context, name)
            val dao = databases.forUser("7")
            assertEquals("Preserved", dao.favorite("123")?.title)
            assertEquals(notification, dao.notifications().first().single())
            assertTrue(dao.userTags().first().isEmpty())
            dao.saveUserTag(LocalUserTag("42", "攻略作者"))
            dao.saveUserTag(LocalUserTag("42", "朋友"))
            assertEquals(listOf(LocalUserTag("42", "朋友")), dao.userTags().first())
            assertTrue(databases.forUser("8").userTags().first().isEmpty())
            databases.close()
            databases = AccountDatabases(context, name)
            assertEquals("朋友", databases.forUser("7").userTags().first().single().text)
            databases.forUser("7").removeUserTag("42")
            assertTrue(databases.forUser("7").userTags().first().isEmpty())
        } finally {
            databases.close()
            context.deleteDatabase("${name}_7.db"); context.deleteDatabase("${name}_8.db")
        }
    }

    @Test fun positionAndFavoriteUseRealRoomTransactions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, ReadingDatabase::class.java).build()
        try {
            val dao = database.readingDao()
            dao.savePosition(ReadingPosition("topic", 3, 2, 12))
            assertEquals(ReadingPosition("topic", 3, 2, 12), dao.position("topic"))
            val favorite = LocalFavorite("topic", "Title")
            dao.toggleFavorite(favorite)
            assertEquals(favorite, dao.favorite("topic"))
            dao.toggleFavorite(favorite)
            assertNull(dao.favorite("topic"))
            val forumFav = LocalForumFavorite("forum:7", "7", null, "艾泽拉斯议事厅", "魔兽主讨论区")
            dao.toggleForumFavorite(forumFav)
            assertEquals(forumFav, dao.forumFavorite("forum:7"))
            dao.toggleForumFavorite(forumFav)
            assertNull(dao.forumFavorite("forum:7"))
            assertEquals(3, dao.position("topic")!!.page)
        } finally { database.close() }
    }
}
