package dev.nagi.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nagi.app.data.AccountDatabases
import dev.nagi.app.data.LocalFavorite
import dev.nagi.app.data.ReadingDatabase
import dev.nagi.app.data.ReadingPosition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingPersistenceTest {
    @Test fun localFavoritesAndReadingPositionSurviveDatabaseReopen(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "reading-instrumentation-test.db"
        context.deleteDatabase(name)
        var database = Room.databaseBuilder(context, ReadingDatabase::class.java, name).build()
        try {
            database.readingDao().savePosition(ReadingPosition("test-topic", 4, 7, 24))
            database.readingDao().toggleFavorite(LocalFavorite("test-topic", "Local favorite"))
            database.close()
            database = Room.databaseBuilder(context, ReadingDatabase::class.java, name).build()
            assertEquals(ReadingPosition("test-topic", 4, 7, 24), database.readingDao().position("test-topic"))
            assertEquals("Local favorite", database.readingDao().favorite("test-topic")!!.title)
            database.readingDao().toggleFavorite(LocalFavorite("test-topic", "Local favorite"))
            assertNull(database.readingDao().favorite("test-topic"))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun accountDatabasesPreserveGuestFileAndIsolatePerUser(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = "reading-acct-test"
        listOf("$base.db", "${base}_77.db", "${base}_88.db").forEach { context.deleteDatabase(it) }
        val legacy = Room.databaseBuilder(context, ReadingDatabase::class.java, "$base.db").build()
        try {
            legacy.readingDao().savePosition(ReadingPosition("t1", 2, 3, 4))
            legacy.readingDao().saveFavorite(LocalFavorite("t1", "Guest favorite"))
        } finally {
            legacy.close()
        }
        val provider = AccountDatabases(context, base)
        try {
            assertEquals(ReadingPosition("t1", 2, 3, 4), provider.forUser(null).position("t1"))
            assertNull(provider.forUser("77").position("t1"))
            provider.forUser("77").savePosition(ReadingPosition("t1", 5, 1, 0))
            assertEquals(ReadingPosition("t1", 5, 1, 0), provider.forUser("77").position("t1"))
            assertNull(provider.forUser("88").position("t1"))
            assertEquals(ReadingPosition("t1", 2, 3, 4), provider.forUser(null).position("t1"))
        } finally {
            provider.close()
            listOf("$base.db", "${base}_77.db", "${base}_88.db").forEach { context.deleteDatabase(it) }
        }
    }
}
