package dev.nagi.app.data

import android.content.Context
import androidx.room.Room

interface ReadingDaoProvider {
    fun forUser(userId: String?): ReadingDao
    fun closeInactive(activeUserId: String?) {}
}

class AccountDatabases(context: Context, private val name: String = "reading") : ReadingDaoProvider {
    private val context = context.applicationContext
    private val lock = Any()
    private val databases = mutableMapOf<String, ReadingDatabase>()

    private fun fileName(userId: String?): String {
        require(userId == null || (userId.isNotEmpty() && userId.all { it in '0'..'9' }))
        return if (userId == null) "$name.db" else "${name}_$userId.db"
    }

    override fun forUser(userId: String?): ReadingDao = synchronized(lock) {
        val file = fileName(userId)
        databases.getOrPut(file) {
            Room.databaseBuilder(context, ReadingDatabase::class.java, file)
                .addMigrations(
                    ReadingDatabase.MIGRATION_1_2,
                    ReadingDatabase.MIGRATION_2_3,
                    ReadingDatabase.MIGRATION_3_4,
                    ReadingDatabase.MIGRATION_4_5,
                    ReadingDatabase.MIGRATION_5_6,
                    ReadingDatabase.MIGRATION_6_7,
                    ReadingDatabase.MIGRATION_7_8,
                    ReadingDatabase.MIGRATION_8_9,
                ).build()
        }.readingDao()
    }

    override fun closeInactive(activeUserId: String?) = synchronized(lock) {
        val activeFile = fileName(activeUserId)
        val iterator = databases.entries.iterator()
        while (iterator.hasNext()) {
            val (file, db) = iterator.next()
            if (file != activeFile) {
                db.close()
                iterator.remove()
            }
        }
    }

    fun close() = synchronized(lock) { databases.values.forEach { it.close() }; databases.clear() }
}
