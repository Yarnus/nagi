package dev.nagi.sdk

data class Forum(val id: String, val name: String, val category: String, val group: String, val collectionId: String? = null,
    val description: String = "",
    val iconUrl: String? = null,
) {
    val key: String get() = collectionId?.let { "collection:$it" } ?: "forum:$id"
}
data class Topic(
    val id: String, val title: String, val author: String, val authorId: String,
    val createdAt: Long = 0, val lastActiveAt: Long = 0, val replies: Int = 0,
    val recommended: Boolean = false,
)
enum class TopicSort { LAST_ACTIVE, NEWEST }

data class Subforum(val forum: Forum, val filterId: String, val attributes: Long?) {
    val filterable: Boolean get() = attributes != null && attributes > 40 && filterId.toLongOrNull() != null
    val selected: Boolean get() = attributes in setOf(7L, 558L, 542L, 2606L, 2590L, 4654L)
}

data class TopicPage(val items: List<Topic>, val page: Int, val total: Int, val hasMore: Boolean,
    val subforums: List<Subforum> = emptyList())
data class Post(
    val id: String, val floor: Int, val author: String, val authorId: String,
    val avatar: String, val date: String, val content: List<ContentBlock>,
    val comments: List<Post> = emptyList(),
    val score: Int = 0,
    val authorLevel: String? = null,
    val authorReputation: String? = null,
    val authorPosts: Int? = null,
    val clientPlatform: String? = null,
)
data class PostPage(
    val items: List<Post>, val title: String, val authorId: String,
    val page: Int, val total: Int, val hasMore: Boolean,
    val pageSize: Int = 0,
    val totalPages: Int = 0,
) {
    fun pageOfFloor(floor: Int): Int? = if (pageSize > 0 && floor >= 0) floor / pageSize + 1 else null
}
data class NgaUser(
    val id: String, val name: String, val avatar: String, val posts: Int,
    val ipLocation: String? = null, val registeredAt: Long? = null,
    val signature: List<ContentBlock> = emptyList(),
)

data class UserReply(val topic: Topic, val postId: String, val authorId: String, val createdAt: Long, val content: List<ContentBlock>)
data class UserReplyPage(val items: List<UserReply>, val page: Int, val hasMore: Boolean?)

data class TextSpan(
    val start: Int, val end: Int,
    val bold: Boolean = false, val italic: Boolean = false,
    val underline: Boolean = false, val strikethrough: Boolean = false,
    val url: String? = null,
)

data class InlineSmile(val start: Int, val end: Int, val smile: NgaSmile)

sealed interface ContentBlock {
    data class Text(
        val value: String, val spans: List<TextSpan> = emptyList(),
        val smiles: List<InlineSmile> = emptyList(),
    ) : ContentBlock
    data class Image(val url: String) : ContentBlock
    data class Quote(val children: List<ContentBlock>, val author: String? = null, val date: String? = null) : ContentBlock
    data class Code(val value: String) : ContentBlock
    data class Table(val rows: List<List<String>>) : ContentBlock
    data class Collapse(val title: String, val children: List<ContentBlock>) : ContentBlock
}

data class CommunityItem(
    val id: String, val title: String, val summary: String, val actor: String,
    val topicId: String, val postId: String, val createdAt: Long, val unread: Boolean,
    val unreadKnown: Boolean = true, val privateMessage: Boolean = false,
)

data class NotificationFeed(val items: List<CommunityItem>, val unreadCount: Int?)

enum class PostRating(val value: Int) { UP(1), DOWN(-1) }

data class PostRatingReceipt(val delta: Int, val currentRating: PostRating?)

enum class PublishAction(val wireValue: String) { NEW_TOPIC("new"), REPLY("reply"), QUOTE("quote"), COMMENT("reply"), MODIFY("modify") }

data class PublishTarget(
    val action: PublishAction, val forumId: String = "", val topicId: String? = null,
    val postId: String? = null, val collectionId: String? = null,
) {
    init {
        require(collectionId == null || collectionId.isNotBlank())
        require(topicId == null || topicId.isNotBlank())
        require(postId == null || postId.isNotBlank())
        require(action != PublishAction.NEW_TOPIC || forumId.isNotBlank() || collectionId != null)
        require(action == PublishAction.NEW_TOPIC || topicId != null)
        require(action !in setOf(PublishAction.QUOTE, PublishAction.COMMENT, PublishAction.MODIFY) || postId != null)
    }
}

data class PublishPreparation(val title: String, val content: String)

class PublishRequest(
    val action: PublishAction, val forumId: String, val content: String,
    val title: String = "", val topicId: String? = null, val postId: String? = null,
    val collectionId: String? = null,
    val attachments: List<UploadedAttachment> = emptyList(),
) {
    val target = PublishTarget(action, forumId, topicId, postId, collectionId)
    init {
        require(content.isNotBlank())
        require(action != PublishAction.NEW_TOPIC || title.isNotBlank())
    }
    override fun toString(): String = "PublishRequest(action=$action, content=redacted)"
}

sealed interface WriteResult {
    val diagnostic: WriteDiagnostic? get() = null
    data class Confirmed(val message: String, val ratingReceipt: PostRatingReceipt? = null) : WriteResult
    data class Rejected(val reason: NgaException, override val diagnostic: WriteDiagnostic? = null) : WriteResult
    data class Unconfirmed(override val diagnostic: WriteDiagnostic? = null) : WriteResult
}

enum class NgaRejectionReason {
    PERMISSION, MUTED, RATE_LIMIT, CONTENT_LENGTH, CONTENT_FILTER, VERIFICATION, INVALID_PARAMETERS, DUPLICATE, UNKNOWN,
}

sealed class NgaException(message: String) : Exception(message) {
    class Network(cause: Throwable? = null) : NgaException("Network request failed") { init { cause?.let(::initCause) } }
    class Http(val status: Int) : NgaException("NGA HTTP $status")
    class Authentication : NgaException("NGA login required or session expired")
    class Restricted(val reason: NgaRejectionReason = NgaRejectionReason.UNKNOWN) : NgaException("NGA access restricted or rate limited")
    class NotFound : NgaException("NGA user not found")
    class Business(val reason: NgaRejectionReason = NgaRejectionReason.UNKNOWN) : NgaException("NGA rejected the request")
    class Protocol private constructor(val code: String) : NgaException("Unsupported or malformed NGA response") {
        constructor(reason: ProtocolReason) : this(reason.code)
        fun then(next: Protocol): Protocol = Protocol("$code>${next.code}")
    }
}

enum class ProtocolReason(number: Int) {
    XML_MALFORMED(1000), XML_EMPTY(1001), XML_DOCTYPE(1002), XML_NO_ROOT(1003), XML_ROOT_TAG(1004),
    XML_TOPICS_NODE(1010), XML_TOPIC_ID(1011),
    XML_USER_TOPICS_NODE(1020), XML_USER_POST_NODE(1021), XML_USER_POST_ID(1022), XML_USER_POST_TOPIC(1023), XML_USER_POST_CONTENT(1024),
    XML_THREAD_NODE(1030), XML_REPLIES_NODE(1031), XML_COMMENT_DEPTH(1032), XML_NO_REPLIES(1033),
    XML_PUBLISH_MISMATCH(1040), XML_PUBLISH_CONTENT(1041),

    JSON_MALFORMED(2000), JSON_DATA_MISSING(2001), JSON_RECORD_INVALID(2002), JSON_RECORDS_SHAPE(2003),
    JSON_FORUM_INVALID(2004), JSON_TOPICS_NODE(2005), JSON_TOPIC_ID(2006), JSON_THREAD_NODE(2007),
    JSON_COMMENT_DEPTH(2008), JSON_COLLECT_DEPTH(2009), JSON_COMMUNITY_EMPTY(2010),
    JSON_USER_TOPICS_NODE(2011), JSON_USER_POST_NODE(2012), JSON_USER_POST_ID(2013), JSON_USER_POST_TOPIC(2014),
    JSON_USER_POST_CONTENT(2015), JSON_USER_NODE(2016), JSON_USER_FIELDS(2017),

    HTML_UNEXPECTED(3000), HTML_INCOMPLETE(3001), HTML_TOPIC_MISMATCH(3002), HTML_AUTHOR_FILTER_MISMATCH(3003),
    HTML_DEFAULTS_MISSING(3004), HTML_DEFAULTS_INVALID(3005), HTML_PAGINATION_INVALID(3006), HTML_USERS_INVALID(3007),
    HTML_TITLE_MISSING(3008), HTML_POSTS_CONTAINER(3009), HTML_POST_COUNT_MISMATCH(3010), HTML_POST_CONTENT(3011),
    HTML_POST_AUTHOR(3012), HTML_POST_AUTHOR_FILTER(3013), HTML_POST_ANCHOR(3014), HTML_POST_DATE(3015),
    HTML_POSTS_INVALID(3016),

    MESSAGE_RECORD_INVALID(4001), MESSAGE_RECORDS_SHAPE(4002), MESSAGE_IDENTITY(4003), MESSAGE_NEXT_PAGE(4004),
    MESSAGE_DATA_NODE(4005), MESSAGE_CONTENT(4006),

    UNKNOWN(9000);

    val code: String = "P-$number"
}
