package dev.nagi.app.data

import android.content.Context
import androidx.room.*
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "favorites")
data class LocalFavorite(@PrimaryKey val topicId: String, val title: String, val savedAt: Long = System.currentTimeMillis())

@Entity(tableName = "forum_favorites")
data class LocalForumFavorite(
    @PrimaryKey val forumKey: String,
    val forumId: String,
    val collectionId: String? = null,
    val name: String,
    val description: String = "",
    val category: String = "",
    val groupName: String = "",
    val savedAt: Long = System.currentTimeMillis(),
) {
    @get:Ignore
    val key: String get() = forumKey
}

@Entity(tableName = "positions")
data class ReadingPosition(@PrimaryKey val topicId: String, val page: Int, val index: Int, val offset: Int)

@Entity(tableName = "history")
data class ReadingHistory(@PrimaryKey val topicId: String, val title: String, val viewedAt: Long)

@Dao
interface ReadingDao {
    @Query("SELECT * FROM user_tags")
    fun userTags(): kotlinx.coroutines.flow.Flow<List<LocalUserTag>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserTag(tag: LocalUserTag)
    @Query("DELETE FROM user_tags WHERE userId = :userId")
    suspend fun removeUserTag(userId: String)
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC, id ASC")
    fun notifications(): kotlinx.coroutines.flow.Flow<List<LocalNotification>>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun receiveNotifications(items: List<LocalNotification>)
    @Query("UPDATE notifications SET `read` = 1 WHERE id IN (:ids)")
    suspend fun readNotifications(ids: List<String>)
    @Query("SELECT * FROM history ORDER BY viewedAt DESC, topicId ASC LIMIT 100")
    fun history(): kotlinx.coroutines.flow.Flow<List<ReadingHistory>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveHistory(item: ReadingHistory)
    @Query("SELECT * FROM favorites ORDER BY savedAt DESC") fun favorites(): kotlinx.coroutines.flow.Flow<List<LocalFavorite>>
    @Query("SELECT * FROM favorites WHERE topicId = :id") suspend fun favorite(id: String): LocalFavorite?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveFavorite(item: LocalFavorite)
    @Transaction
    suspend fun toggleFavorite(item: LocalFavorite) {
        if (favorite(item.topicId) == null) saveFavorite(item) else removeFavorite(item.topicId)
    }
    @Query("DELETE FROM favorites WHERE topicId = :id") suspend fun removeFavorite(id: String)
    @Query("SELECT * FROM forum_favorites ORDER BY savedAt DESC")
    fun forumFavorites(): kotlinx.coroutines.flow.Flow<List<LocalForumFavorite>>
    @Query("SELECT * FROM forum_favorites WHERE forumKey = :key")
    suspend fun forumFavorite(key: String): LocalForumFavorite?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveForumFavorite(item: LocalForumFavorite)
    @Transaction
    suspend fun toggleForumFavorite(item: LocalForumFavorite) {
        if (forumFavorite(item.forumKey) == null) saveForumFavorite(item) else removeForumFavorite(item.forumKey)
    }
    @Query("DELETE FROM forum_favorites WHERE forumKey = :key")
    suspend fun removeForumFavorite(key: String)
    @Query("SELECT * FROM positions WHERE topicId = :id") suspend fun position(id: String): ReadingPosition?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePosition(item: ReadingPosition)
}

@Database(
    entities = [LocalFavorite::class, LocalForumFavorite::class, ReadingPosition::class, ReadingHistory::class, LocalNotification::class, LocalUserTag::class],
    version = 5,
    exportSchema = false,
)
abstract class ReadingDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao
    companion object {
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_tags` (`userId` TEXT NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`userId`))")
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `notifications` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `summary` TEXT NOT NULL, `actor` TEXT NOT NULL, `topicId` TEXT NOT NULL, `postId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `privateMessage` INTEGER NOT NULL, `read` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `history` (`topicId` TEXT NOT NULL, `title` TEXT NOT NULL, `viewedAt` INTEGER NOT NULL, PRIMARY KEY(`topicId`))")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `forum_favorites` (`forumKey` TEXT NOT NULL, `forumId` TEXT NOT NULL, `collectionId` TEXT, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL DEFAULT '', `groupName` TEXT NOT NULL DEFAULT '', `savedAt` INTEGER NOT NULL, PRIMARY KEY(`forumKey`))")
            }
        }
    }
}

private val Context.readingPreferences by preferencesDataStore("reading")
data class ReadingPreferences(val fontSize: Int = 16, val lineHeight: Int = 26, val theme: String = "system")

@Singleton
class PreferenceStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    val preferences = context.readingPreferences.data.map {
        ReadingPreferences(it[intPreferencesKey("font")] ?: 16, it[intPreferencesKey("line")] ?: 26, it[stringPreferencesKey("theme")] ?: "system")
    }
    suspend fun font(size: Int) = context.readingPreferences.edit { it[intPreferencesKey("font")] = size.coerceIn(14, 24) }
    suspend fun lineHeight(size: Int) = context.readingPreferences.edit { it[intPreferencesKey("line")] = size.coerceIn(24, 40) }
    suspend fun theme(value: String) = context.readingPreferences.edit { it[stringPreferencesKey("theme")] = value }
}
