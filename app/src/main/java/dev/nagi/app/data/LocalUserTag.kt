package dev.nagi.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_tags")
data class LocalUserTag(@PrimaryKey val userId: String, val text: String)

internal const val USER_TAG_MAX_LENGTH = 24

internal fun validUserTag(text: String): Boolean =
    text.codePointCount(0, text.length) <= USER_TAG_MAX_LENGTH && text.none { it.isISOControl() }
