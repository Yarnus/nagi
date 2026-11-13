package dev.nagi.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.nagi.sdk.CommunityItem

@Entity(tableName = "notifications")
data class LocalNotification(
    @PrimaryKey val id: String,
    val title: String, val summary: String, val actor: String,
    val topicId: String, val postId: String, val createdAt: Long,
    val privateMessage: Boolean, val read: Boolean = false,
) {
    fun item() = CommunityItem(id, title, summary, actor, topicId, postId, createdAt,
        unread = !read, unreadKnown = true, privateMessage = privateMessage)

    companion object {
        fun received(item: CommunityItem) = LocalNotification(item.id, item.title, item.summary, item.actor,
            item.topicId, item.postId, item.createdAt, item.privateMessage)
    }
}
