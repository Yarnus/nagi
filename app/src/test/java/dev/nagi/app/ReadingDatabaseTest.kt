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

    @Test fun positionPruningProtectsFavoritesAndHistoryManagementWorks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, ReadingDatabase::class.java).build()
        try {
            val dao = database.readingDao()
            dao.saveFavorite(LocalFavorite("fav", "Favorite Topic"))
            dao.upsertPosition(ReadingPosition("fav", 4, 1, 0, updatedAt = 10L))
            dao.upsertPosition(ReadingPosition("old-1", 1, 0, 0, updatedAt = 20L))
            dao.upsertPosition(ReadingPosition("old-2", 2, 0, 0, updatedAt = 30L))
            dao.upsertPosition(ReadingPosition("new-1", 3, 0, 0, updatedAt = 40L))
            dao.prunePositions(limit = 2)

            assertNotNull(dao.position("fav"))
            assertNull(dao.position("old-1"))
            assertNotNull(dao.position("old-2"))
            assertNotNull(dao.position("new-1"))

            dao.upsertHistory(ReadingHistory("t1", "Topic 1", 100L))
            dao.upsertHistory(ReadingHistory("t2", "Topic 2", 200L))
            dao.upsertHistory(ReadingHistory("t3", "Topic 3", 300L))
            dao.pruneHistory(limit = 2)
            assertEquals(listOf("t3", "t2"), dao.history().first().map { it.topicId })

            dao.removeHistory("t3")
            assertEquals(listOf("t2"), dao.history().first().map { it.topicId })

            dao.clearHistory()
            assertTrue(dao.history().first().isEmpty())

            dao.clearUnfavoritedPositions()
            assertNotNull(dao.position("fav"))
            assertNull(dao.position("old-2"))
            assertNull(dao.position("new-1"))
        } finally { database.close() }
    }

    @Test fun migration5To6PreservesPositions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "position-migration-v5"
        context.deleteDatabase("${name}_7.db")
        var databases = AccountDatabases(context, name)
        try {
            databases.forUser("7").upsertPosition(ReadingPosition("42", 5, 2, 16, updatedAt = 999L))
            databases.close()
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("${name}_7.db").path, null, 0).use { db ->
                db.execSQL("CREATE TABLE positions_v5 (topicId TEXT NOT NULL PRIMARY KEY, page INTEGER NOT NULL, `index` INTEGER NOT NULL, `offset` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO positions_v5 SELECT topicId, page, `index`, `offset` FROM positions")
                db.execSQL("DROP TABLE positions")
                db.execSQL("ALTER TABLE positions_v5 RENAME TO positions")
                db.version = 5
            }
            databases = AccountDatabases(context, name)
            val migrated = databases.forUser("7").position("42")
            assertEquals(ReadingPosition("42", 5, 2, 16), migrated)
            assertEquals(0L, migrated!!.updatedAt)
        } finally {
            databases.close()
            context.deleteDatabase("${name}_7.db")
        }
    }

    @Test fun migration6To7AddsCloudForumOrderPerAccount() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "cloud-forum-order-migration"
        context.deleteDatabase("${name}_7.db"); context.deleteDatabase("${name}_8.db")
        var databases = AccountDatabases(context, name)
        try {
            databases.forUser("7").saveFavorite(LocalFavorite("123", "Preserved", 1))
            databases.close()
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("${name}_7.db").path, null, 0).use {
                it.execSQL("DROP TABLE cloud_forum_favorite_order")
                it.version = 6
            }
            databases = AccountDatabases(context, name)
            val dao7 = databases.forUser("7")
            assertEquals("Preserved", dao7.favorite("123")?.title)
            assertTrue(dao7.cloudForumFavoriteOrders().first().isEmpty())

            dao7.replaceCloudForumFavoriteOrders(listOf("collection:99", "forum:7"))
            assertEquals(listOf("collection:99", "forum:7"), dao7.cloudForumFavoriteOrders().first().map { it.forumKey })
            assertTrue(databases.forUser("8").cloudForumFavoriteOrders().first().isEmpty())

            dao7.clearCloudForumFavoriteOrders()
            assertTrue(dao7.cloudForumFavoriteOrders().first().isEmpty())
        } finally {
            databases.close()
            context.deleteDatabase("${name}_7.db"); context.deleteDatabase("${name}_8.db")
        }
    }

    @Test fun migration7To8SeedsReadStatesAndProtectsFavoritesOnPruneAndClear() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "topic-read-state-migration"
        context.deleteDatabase("${name}_7.db"); context.deleteDatabase("${name}_8.db")
        var databases = AccountDatabases(context, name)
        try {
            val pre7 = databases.forUser("7")
            pre7.saveFavorite(LocalFavorite("fav", "Fav", 1L))
            pre7.upsertPosition(ReadingPosition("fav", 2, 1, 0, updatedAt = 5_000L))
            pre7.upsertPosition(ReadingPosition("pos-only", 1, 0, 0, updatedAt = 9_000L))
            pre7.upsertHistory(ReadingHistory("hist-only", "History", 12_000L))
            databases.close()

            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("${name}_7.db").path, null, 0).use { db ->
                db.execSQL("DROP TABLE topic_read_states")
                db.version = 7
            }

            databases = AccountDatabases(context, name)
            val dao7 = databases.forUser("7")
            assertEquals(TopicReadState("fav", -1, 5L), dao7.topicReadState("fav"))
            assertEquals(TopicReadState("pos-only", -1, 9L), dao7.topicReadState("pos-only"))
            assertEquals(TopicReadState("hist-only", -1, 12L), dao7.topicReadState("hist-only"))
            assertTrue(databases.forUser("8").topicReadStates().first().isEmpty())

            dao7.recordTopicRead("pos-only", knownReplies = 25, readAtSeconds = 100L)
            assertEquals(TopicReadState("pos-only", 25, 100L), dao7.topicReadState("pos-only"))
            dao7.recordTopicRead("pos-only", knownReplies = null, readAtSeconds = 110L)
            assertEquals(TopicReadState("pos-only", 25, 110L), dao7.topicReadState("pos-only"))

            dao7.pruneTopicReadStates(limit = 1)
            assertNotNull(dao7.topicReadState("fav"))
            assertNotNull(dao7.topicReadState("pos-only"))
            assertNull(dao7.topicReadState("hist-only"))

            dao7.clearUnfavoritedReadStates()
            assertNotNull(dao7.topicReadState("fav"))
            assertNull(dao7.topicReadState("pos-only"))
        } finally {
            databases.close()
            context.deleteDatabase("${name}_7.db"); context.deleteDatabase("${name}_8.db")
        }
    }

    @Test fun migration8To9CreatesIndicesAndCloseInactiveReleasesOldAccounts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "index-and-close-test"
        context.deleteDatabase("${name}_7.db"); context.deleteDatabase("${name}_8.db")
        var databases = AccountDatabases(context, name)
        try {
            databases.forUser("7").saveHistory(ReadingHistory("101", "History 7", 10L))
            databases.forUser("7").savePosition(ReadingPosition("101", 2, 1, 0, updatedAt = 20L))
            databases.forUser("8").saveFavorite(LocalFavorite("202", "Fav 8", 30L))
            databases.close()

            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("${name}_7.db").path, null, 0).use { db ->
                db.execSQL("DROP INDEX IF EXISTS `index_history_viewedAt_topicId`")
                db.execSQL("DROP INDEX IF EXISTS `index_positions_updatedAt_topicId`")
                db.execSQL("DROP INDEX IF EXISTS `index_topic_read_states_lastReadAt_topicId`")
                db.execSQL("DROP INDEX IF EXISTS `index_notifications_createdAt_id`")
                db.version = 8
            }

            databases = AccountDatabases(context, name)
            val dao7 = databases.forUser("7")
            assertEquals("History 7", dao7.history().first().single().title)
            assertEquals(2, dao7.position("101")?.page)
            databases.close()

            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("${name}_7.db").path, null, 0).use { db ->
                fun indexNames(table: String): Set<String> {
                    val names = mutableSetOf<String>()
                    db.rawQuery("PRAGMA index_list(`$table`)", null).use { cursor ->
                        val nameIdx = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) {
                            if (nameIdx >= 0) names += cursor.getString(nameIdx)
                        }
                    }
                    return names
                }
                assertTrue("index_history_viewedAt_topicId" in indexNames("history"))
                assertTrue("index_positions_updatedAt_topicId" in indexNames("positions"))
                assertTrue("index_topic_read_states_lastReadAt_topicId" in indexNames("topic_read_states"))
                assertTrue("index_notifications_createdAt_id" in indexNames("notifications"))
            }

            databases = AccountDatabases(context, name)
            databases.forUser("7")
            databases.forUser("8")
            databases.closeInactive("8")
            assertEquals("Fav 8", databases.forUser("8").favorite("202")?.title)
            assertEquals("History 7", databases.forUser("7").history().first().single().title)
        } finally {
            databases.close()
            context.deleteDatabase("${name}_7.db"); context.deleteDatabase("${name}_8.db")
        }
    }
}
