package dev.nagi.app.data

import android.content.Context
import androidx.room.Room

interface ReadingDaoProvider {
    fun forUser(userId: String?): ReadingDao
}

class AccountDatabases(context: Context, private val name: String = "reading") : ReadingDaoProvider {
    private val context = context.applicationContext
    private val lock = Any()
    private val databases = mutableMapOf<String, ReadingDatabase>()

    override fun forUser(userId: String?): ReadingDao = synchronized(lock) {
        require(userId == null || (userId.isNotEmpty() && userId.all { it in '0'..'9' }))
        val file = if (userId == null) "$name.db" else "${name}_$userId.db"
        databases.getOrPut(file) {
            Room.databaseBuilder(context, ReadingDatabase::class.java, file)
                .addMigrations(ReadingDatabase.MIGRATION_1_2, ReadingDatabase.MIGRATION_2_3).build()
        }.readingDao()
    }

    fun close() = synchronized(lock) { databases.values.forEach { it.close() }; databases.clear() }
}
