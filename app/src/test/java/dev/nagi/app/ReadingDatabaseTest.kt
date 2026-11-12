package dev.nagi.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.nagi.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReadingDatabaseTest {
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
