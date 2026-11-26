package dev.nagi.app.data

import android.content.Context
import androidx.room.*
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.nagi.sdk.NgaDomain
import kotlinx.coroutines.flow.first
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

@Entity(tableName = "cloud_forum_favorite_order")
data class CloudForumFavoriteOrder(
    @PrimaryKey val forumKey: String,
    val sortOrder: Int,
)

const val MAX_HISTORY_ENTRIES = 100
const val PRUNE_HISTORY_TRIGGER = MAX_HISTORY_ENTRIES + 20
const val MAX_UNFAVORITED_POSITIONS = 500
const val PRUNE_POSITIONS_TRIGGER = MAX_UNFAVORITED_POSITIONS + 50
const val MAX_UNFAVORITED_READ_STATES = 2000
const val PRUNE_READ_STATES_TRIGGER = MAX_UNFAVORITED_READ_STATES + 50
const val MAX_RECENT_SMILES = 24

@Entity(
    tableName = "recent_smiles",
    indices = [Index(value = ["usedAt", "smileKey"])],
)
data class RecentSmileEntity(
    @PrimaryKey val smileKey: String,
    val usedAt: Long,
)

@Entity(
    tableName = "topic_read_states",
    indices = [Index(value = ["lastReadAt", "topicId"])],
)
data class TopicReadState(
    @PrimaryKey val topicId: String,
    /** Reply count recorded when last read (`-1` if unknown, e.g. migrated or author-filtered). */
    val lastReadReplies: Int,
    /** Epoch seconds (`System.currentTimeMillis() / 1000L`) when last read. */
    val lastReadAt: Long,
)

sealed interface TopicReadStatus {
    data object Unread : TopicReadStatus
    data object Read : TopicReadStatus
    data class Updated(val newReplies: Int?) : TopicReadStatus
}

fun TopicReadStatus.accessibilityStateDescription(): String? = when (this) {
    TopicReadStatus.Unread -> null
    TopicReadStatus.Read -> "已读"
    is TopicReadStatus.Updated -> newReplies?.let { "有 $it 条新回复" } ?: "有新回复"
}

fun TopicReadStatus.formatReplySummary(replies: Int): String = when (this) {
    TopicReadStatus.Unread -> "$replies 条回复"
    TopicReadStatus.Read -> "$replies 条回复 · 已读"
    is TopicReadStatus.Updated -> if (newReplies != null) {
        "$replies 条回复 · 新增 $newReplies 条"
    } else {
        "$replies 条回复 · 有新动态"
    }
}

fun resolveTopicReadStatus(topic: dev.nagi.sdk.Topic, readState: TopicReadState?): TopicReadStatus {
    if (readState == null) return TopicReadStatus.Unread
    if (readState.lastReadReplies >= 0) {
        val delta = topic.replies - readState.lastReadReplies
        return if (delta > 0) TopicReadStatus.Updated(delta) else TopicReadStatus.Read
    }
    return if (readState.lastReadAt > 0L && topic.lastActiveAt > readState.lastReadAt) {
        TopicReadStatus.Updated(newReplies = null)
    } else {
        TopicReadStatus.Read
    }
}

@Entity(
    tableName = "positions",
    indices = [Index(value = ["updatedAt", "topicId"])],
)
data class ReadingPosition(
    @PrimaryKey val topicId: String,
    val page: Int,
    val index: Int,
    val offset: Int,
    val updatedAt: Long = 0L,
) {
    override fun equals(other: Any?): Boolean = other is ReadingPosition &&
        topicId == other.topicId && page == other.page && index == other.index && offset == other.offset

    override fun hashCode(): Int {
        var result = topicId.hashCode()
        result = 31 * result + page
        result = 31 * result + index
        result = 31 * result + offset
        return result
    }
}

@Entity(
    tableName = "history",
    indices = [Index(value = ["viewedAt", "topicId"])],
)
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
    @Query("SELECT * FROM history ORDER BY viewedAt DESC, topicId ASC LIMIT $MAX_HISTORY_ENTRIES")
    fun history(): kotlinx.coroutines.flow.Flow<List<ReadingHistory>>
    @Query("SELECT title FROM history WHERE topicId = :id LIMIT 1")
    suspend fun historyTitle(id: String): String?
    @Query("SELECT COUNT(*) FROM history") suspend fun historyCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertHistory(item: ReadingHistory)
    @Query("DELETE FROM history WHERE topicId NOT IN (SELECT topicId FROM history ORDER BY viewedAt DESC, topicId ASC LIMIT :limit)")
    suspend fun pruneHistory(limit: Int = MAX_HISTORY_ENTRIES)
    @Transaction
    suspend fun saveHistory(item: ReadingHistory) {
        upsertHistory(item)
        if (historyCount() > PRUNE_HISTORY_TRIGGER) pruneHistory()
    }
    @Query("DELETE FROM history WHERE topicId = :topicId") suspend fun removeHistory(topicId: String)
    @Query("DELETE FROM history") suspend fun clearHistory()
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
    @Query("SELECT * FROM cloud_forum_favorite_order ORDER BY sortOrder ASC")
    fun cloudForumFavoriteOrders(): kotlinx.coroutines.flow.Flow<List<CloudForumFavoriteOrder>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCloudForumFavoriteOrders(items: List<CloudForumFavoriteOrder>)
    @Query("DELETE FROM cloud_forum_favorite_order")
    suspend fun clearCloudForumFavoriteOrders()
    @Transaction
    suspend fun replaceCloudForumFavoriteOrders(keys: List<String>) {
        clearCloudForumFavoriteOrders()
        if (keys.isNotEmpty()) {
            insertCloudForumFavoriteOrders(
                keys.distinct().mapIndexed { index, key -> CloudForumFavoriteOrder(key, index) },
            )
        }
    }
    @Query("SELECT COUNT(*) FROM positions WHERE topicId NOT IN (SELECT topicId FROM favorites)") suspend fun positionCount(): Int
    @Query("SELECT * FROM positions WHERE topicId = :id") suspend fun position(id: String): ReadingPosition?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPosition(item: ReadingPosition)
    @Query(
        "DELETE FROM positions WHERE topicId NOT IN (SELECT topicId FROM favorites) " +
            "AND topicId NOT IN (SELECT topicId FROM positions WHERE topicId NOT IN (SELECT topicId FROM favorites) ORDER BY updatedAt DESC, topicId ASC LIMIT :limit)",
    )
    suspend fun prunePositions(limit: Int = MAX_UNFAVORITED_POSITIONS)
    @Transaction
    suspend fun savePosition(item: ReadingPosition) {
        upsertPosition(item)
        if (positionCount() > PRUNE_POSITIONS_TRIGGER) prunePositions()
    }
    @Query("DELETE FROM positions WHERE topicId NOT IN (SELECT topicId FROM favorites)")
    suspend fun clearUnfavoritedPositions()
    @Query("SELECT COUNT(*) FROM topic_read_states WHERE topicId NOT IN (SELECT topicId FROM favorites)") suspend fun topicReadStateCount(): Int
    @Query("SELECT * FROM topic_read_states")
    fun topicReadStates(): kotlinx.coroutines.flow.Flow<List<TopicReadState>>
    @Query("SELECT * FROM topic_read_states WHERE topicId = :id")
    suspend fun topicReadState(id: String): TopicReadState?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopicReadState(item: TopicReadState)
    @Query(
        "DELETE FROM topic_read_states WHERE topicId NOT IN (SELECT topicId FROM favorites) " +
            "AND topicId NOT IN (SELECT topicId FROM topic_read_states WHERE topicId NOT IN (SELECT topicId FROM favorites) ORDER BY lastReadAt DESC, topicId ASC LIMIT :limit)",
    )
    suspend fun pruneTopicReadStates(limit: Int = MAX_UNFAVORITED_READ_STATES)
    @Transaction
    suspend fun recordTopicRead(topicId: String, knownReplies: Int?, readAtSeconds: Long) {
        val existing = topicReadState(topicId)
        val mergedReplies = when {
            knownReplies != null && knownReplies >= 0 -> knownReplies
            else -> existing?.lastReadReplies ?: -1
        }
        val mergedAt = maxOf(existing?.lastReadAt ?: 0L, readAtSeconds)
        upsertTopicReadState(TopicReadState(topicId, mergedReplies, mergedAt))
        if (topicReadStateCount() > PRUNE_READ_STATES_TRIGGER) pruneTopicReadStates()
    }
    @Query("DELETE FROM topic_read_states WHERE topicId NOT IN (SELECT topicId FROM favorites)")
    suspend fun clearUnfavoritedReadStates()
    @Query("SELECT COUNT(*) FROM recent_smiles")
    suspend fun recentSmileCount(): Int
    @Query("SELECT * FROM recent_smiles ORDER BY usedAt DESC, smileKey ASC LIMIT :limit")
    fun recentSmiles(limit: Int = MAX_RECENT_SMILES): kotlinx.coroutines.flow.Flow<List<RecentSmileEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentSmile(item: RecentSmileEntity)
    @Query(
        "DELETE FROM recent_smiles WHERE smileKey NOT IN " +
            "(SELECT smileKey FROM recent_smiles ORDER BY usedAt DESC, smileKey ASC LIMIT :limit)",
    )
    suspend fun pruneRecentSmiles(limit: Int = MAX_RECENT_SMILES)
    @Query("DELETE FROM recent_smiles")
    suspend fun clearRecentSmiles()
    @Transaction
    suspend fun recordRecentSmile(smileKey: String, usedAt: Long) {
        upsertRecentSmile(RecentSmileEntity(smileKey, usedAt))
        if (recentSmileCount() > MAX_RECENT_SMILES) pruneRecentSmiles()
    }
}

@Database(
    entities = [
        LocalFavorite::class,
        LocalForumFavorite::class,
        CloudForumFavoriteOrder::class,
        ReadingPosition::class,
        ReadingHistory::class,
        LocalNotification::class,
        LocalUserTag::class,
        TopicReadState::class,
        RecentSmileEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
abstract class ReadingDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao
    companion object {
        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `recent_smiles` (`smileKey` TEXT NOT NULL, `usedAt` INTEGER NOT NULL, PRIMARY KEY(`smileKey`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recent_smiles_usedAt_smileKey` ON `recent_smiles` (`usedAt`, `smileKey`)")
            }
        }
        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_viewedAt_topicId` ON `history` (`viewedAt`, `topicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_positions_updatedAt_topicId` ON `positions` (`updatedAt`, `topicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topic_read_states_lastReadAt_topicId` ON `topic_read_states` (`lastReadAt`, `topicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_createdAt_id` ON `notifications` (`createdAt`, `id`)")
            }
        }
        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `topic_read_states` (`topicId` TEXT NOT NULL, `lastReadReplies` INTEGER NOT NULL, `lastReadAt` INTEGER NOT NULL, PRIMARY KEY(`topicId`))",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `topic_read_states` (`topicId`, `lastReadReplies`, `lastReadAt`) " +
                        "SELECT `topicId`, -1, CASE WHEN `updatedAt` > 0 THEN `updatedAt` / 1000 ELSE 0 END FROM `positions`",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `topic_read_states` (`topicId`, `lastReadReplies`, `lastReadAt`) " +
                        "SELECT `topicId`, -1, CASE WHEN `viewedAt` > 0 THEN `viewedAt` / 1000 ELSE 0 END FROM `history`",
                )
            }
        }
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `cloud_forum_favorite_order` (`forumKey` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`forumKey`))")
            }
        }
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                var hasUpdatedAt = false
                db.query("PRAGMA table_info(`positions`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0 && cursor.getString(nameIdx) == "updatedAt") {
                            hasUpdatedAt = true
                            break
                        }
                    }
                }
                if (!hasUpdatedAt) {
                    db.execSQL("ALTER TABLE `positions` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                }
            }
        }
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

const val DEFAULT_FONT_SIZE = 16
const val MIN_FONT_SIZE = 14
const val MAX_FONT_SIZE = 24
const val DEFAULT_LINE_HEIGHT = 26
const val MIN_LINE_HEIGHT = 24
const val MAX_LINE_HEIGHT = 40
const val LINE_HEIGHT_STEP = 2
const val MIN_LINE_HEIGHT_GAP = 6

val SUPPORTED_NGA_DOMAINS: List<NgaDomain> = listOf(
    NgaDomain.PRIMARY,
    NgaDomain.APP,
    NgaDomain.LEGACY,
)

fun NgaDomain.displayName(): String = when (this) {
    NgaDomain.PRIMARY -> "主站"
    NgaDomain.APP -> "客户端线路"
    NgaDomain.ALTERNATE -> "备用主站"
    NgaDomain.LEGACY -> "旧版线路"
}

fun NgaDomain.hostLabel(): String =
    baseUrl.removePrefix("https://").trimEnd('/')

private val Context.readingPreferences by preferencesDataStore("reading")

data class ReadingPreferences(
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val lineHeight: Int = DEFAULT_LINE_HEIGHT,
    val theme: String = "system",
    val clipboardLinkDetection: Boolean = true,
    val autoCheckUpdates: Boolean = true,
    val ngaDomain: NgaDomain = NgaDomain.PRIMARY,
) {
    val minLineHeight: Int
        get() = maxOf(MIN_LINE_HEIGHT, fontSize + MIN_LINE_HEIGHT_GAP)

    val effectiveLineHeight: Int
        get() = lineHeight.coerceIn(minLineHeight, MAX_LINE_HEIGHT)

    val isDefaultTypography: Boolean
        get() = fontSize == DEFAULT_FONT_SIZE && lineHeight == DEFAULT_LINE_HEIGHT
}

@Singleton
class PreferenceStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val fontKey = intPreferencesKey("font")
    private val lineKey = intPreferencesKey("line")
    private val themeKey = stringPreferencesKey("theme")
    private val clipboardLinkKey = booleanPreferencesKey("clipboard_link_detection")
    private val autoCheckUpdatesKey = booleanPreferencesKey("auto_check_updates")
    private val ngaDomainKey = stringPreferencesKey("nga_domain")
    @Volatile private var cachedNgaDomain: NgaDomain? = null
    private val domainWriteEpoch = java.util.concurrent.atomic.AtomicLong(0L)

    val preferences = context.readingPreferences.data.map {
        val epoch = domainWriteEpoch.get()
        val fontSize = (it[fontKey] ?: DEFAULT_FONT_SIZE).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        val lineHeight = (it[lineKey] ?: DEFAULT_LINE_HEIGHT).coerceIn(MIN_LINE_HEIGHT, MAX_LINE_HEIGHT)
        val theme = it[themeKey]?.takeIf { value -> value in setOf("system", "light", "dark") } ?: "system"
        val clipboardDetection = it[clipboardLinkKey] ?: true
        val autoUpdates = it[autoCheckUpdatesKey] ?: true
        val domainName = it[ngaDomainKey]
        val domain = SUPPORTED_NGA_DOMAINS.firstOrNull { candidate -> candidate.name == domainName }
            ?: NgaDomain.PRIMARY
        if (domainWriteEpoch.get() == epoch) {
            cachedNgaDomain = domain
        }
        ReadingPreferences(
            fontSize = fontSize,
            lineHeight = lineHeight,
            theme = theme,
            clipboardLinkDetection = clipboardDetection,
            autoCheckUpdates = autoUpdates,
            ngaDomain = domain,
        )
    }

    suspend fun currentNgaDomain(): NgaDomain =
        cachedNgaDomain ?: preferences.first().ngaDomain

    suspend fun font(size: Int) {
        context.readingPreferences.edit { it[fontKey] = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE) }
    }

    suspend fun lineHeight(size: Int) {
        context.readingPreferences.edit { prefs ->
            val currentFont = (prefs[fontKey] ?: DEFAULT_FONT_SIZE).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
            val minAllowed = maxOf(MIN_LINE_HEIGHT, currentFont + MIN_LINE_HEIGHT_GAP)
            prefs[lineKey] = size.coerceIn(minAllowed, MAX_LINE_HEIGHT)
        }
    }

    suspend fun resetTypography() {
        context.readingPreferences.edit {
            it[fontKey] = DEFAULT_FONT_SIZE
            it[lineKey] = DEFAULT_LINE_HEIGHT
        }
    }

    suspend fun theme(value: String) {
        val normalized = if (value in setOf("system", "light", "dark")) value else "system"
        context.readingPreferences.edit { it[themeKey] = normalized }
    }

    suspend fun clipboardLinkDetection(enabled: Boolean) {
        context.readingPreferences.edit { it[clipboardLinkKey] = enabled }
    }

    suspend fun autoCheckUpdates(enabled: Boolean) {
        context.readingPreferences.edit { it[autoCheckUpdatesKey] = enabled }
    }

    suspend fun ngaDomain(domain: NgaDomain) {
        domainWriteEpoch.incrementAndGet()
        val normalized = if (domain in SUPPORTED_NGA_DOMAINS) domain else NgaDomain.PRIMARY
        context.readingPreferences.edit { it[ngaDomainKey] = normalized.name }
        cachedNgaDomain = normalized
    }
}
