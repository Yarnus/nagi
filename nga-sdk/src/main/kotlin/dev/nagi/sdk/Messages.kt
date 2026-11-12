package dev.nagi.sdk

data class MessageParticipant(val userId: String, val name: String)
data class MessageConversation(
    val id: String, val title: String, val senderId: String, val senderName: String,
    val createdAt: Long, val lastActiveAt: Long, val postCount: Int,
    val participants: List<MessageParticipant>,
)
data class MessageConversationPage(val items: List<MessageConversation>, val page: Int, val hasMore: Boolean)
data class MessagePost(
    val id: String, val authorId: String, val title: String,
    val content: List<ContentBlock>, val createdAt: Long,
)
data class MessagePostPage(
    val items: List<MessagePost>, val participants: List<MessageParticipant>, val page: Int, val hasMore: Boolean,
)

enum class MessageAction(val wireValue: String) { NEW("new"), REPLY("reply") }

class MessageRequest(
    val action: MessageAction, val content: String, val title: String = "",
    recipients: List<String> = emptyList(), val conversationId: String? = null,
) {
    val recipients = recipients.toList()
    init {
        require(content.isNotBlank())
        require(conversationId == null || conversationId.isNotBlank())
        require(this.recipients.all { it.isNotBlank() && it.none(Char::isWhitespace) })
        when (action) {
            MessageAction.NEW -> require(title.isNotBlank() && this.recipients.isNotEmpty() && conversationId == null)
            MessageAction.REPLY -> require(conversationId != null && this.recipients.isEmpty())
        }
    }
    override fun toString(): String = "MessageRequest(action=$action, content=redacted)"
}
