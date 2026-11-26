package dev.nagi.app.data

import dev.nagi.sdk.CommunityItem
import dev.nagi.sdk.Forum
import dev.nagi.sdk.ImageUpload
import dev.nagi.sdk.ImageUploadResult
import dev.nagi.sdk.MessageConversationPage
import dev.nagi.sdk.MessagePostPage
import dev.nagi.sdk.MessageRequest
import dev.nagi.sdk.NgaClient
import dev.nagi.sdk.NgaConfig
import dev.nagi.sdk.NgaDomain
import dev.nagi.sdk.NgaUser
import dev.nagi.sdk.NotificationFeed
import dev.nagi.sdk.PostPage
import dev.nagi.sdk.PostRating
import dev.nagi.sdk.PostReference
import dev.nagi.sdk.PublishPreparation
import dev.nagi.sdk.PublishRequest
import dev.nagi.sdk.PublishTarget
import dev.nagi.sdk.SessionStore
import dev.nagi.sdk.TopicPage
import dev.nagi.sdk.TopicSort
import dev.nagi.sdk.UserReplyPage
import dev.nagi.sdk.WriteResult
import dev.nagi.sdk.createNgaClient

/**
 * [NgaClient] delegator that follows the user's preferred [NgaDomain] without restarting the app
 * while keeping each in-flight call bound to a single client instance.
 */
internal class DynamicDomainNgaClient(
    private val sessions: SessionStore,
    private val preferences: PreferenceStore,
    private val factory: (NgaDomain, SessionStore) -> NgaClient = { domain, store ->
        createNgaClient(config = NgaConfig(domain = domain), sessions = store)
    },
) : NgaClient {
    private class DomainDelegate(
        val domain: NgaDomain,
        val client: NgaClient,
        var activeCalls: Int = 0,
        var retired: Boolean = false,
    )

    private val lock = Any()
    @Volatile private var cached: DomainDelegate? = null

    private fun acquireDelegate(domain: NgaDomain): DomainDelegate {
        var staleToClose: NgaClient? = null
        val acquired = synchronized(lock) {
            val existing = cached
            if (existing != null && existing.domain == domain && !existing.retired) {
                existing.activeCalls++
                existing
            } else {
                if (existing != null && !existing.retired) {
                    existing.retired = true
                    if (existing.activeCalls == 0) staleToClose = existing.client
                }
                val created = DomainDelegate(domain, factory(domain, sessions), activeCalls = 1)
                cached = created
                created
            }
        }
        if (staleToClose is AutoCloseable) {
            runCatching { staleToClose.close() }
        }
        return acquired
    }

    private fun releaseDelegate(delegate: DomainDelegate) {
        val toClose = synchronized(lock) {
            delegate.activeCalls--
            if (delegate.retired && delegate.activeCalls <= 0) delegate.client else null
        }
        if (toClose is AutoCloseable) {
            runCatching { toClose.close() }
        }
    }

    private suspend inline fun <T> withDelegate(crossinline block: suspend NgaClient.() -> T): T {
        val domain = preferences.currentNgaDomain()
        val active = acquireDelegate(domain)
        return try {
            active.client.block()
        } finally {
            releaseDelegate(active)
        }
    }

    override suspend fun getForums(): List<Forum> = withDelegate { getForums() }

    override suspend fun getTopics(forumId: String, page: Int, collectionId: String?, sort: TopicSort): TopicPage =
        withDelegate { getTopics(forumId, page, collectionId, sort) }

    override suspend fun getPosts(topicId: String, page: Int, authorId: String?): PostPage =
        withDelegate { getPosts(topicId, page, authorId) }

    override suspend fun getPostPage(reference: PostReference): PostPage =
        withDelegate { getPostPage(reference) }

    override suspend fun searchTopics(keyword: String, forumId: String?, page: Int, collectionId: String?): TopicPage =
        withDelegate { searchTopics(keyword, forumId, page, collectionId) }

    override suspend fun findUser(username: String): NgaUser? = withDelegate { findUser(username) }

    override suspend fun getUser(userId: String): NgaUser = withDelegate { getUser(userId) }

    override suspend fun getUserTopics(userId: String, page: Int): TopicPage =
        withDelegate { getUserTopics(userId, page) }

    override suspend fun getUserReplies(userId: String, page: Int): UserReplyPage =
        withDelegate { getUserReplies(userId, page) }

    override suspend fun getNotifications(since: Long): List<CommunityItem> =
        withDelegate { getNotifications(since) }

    override suspend fun getNotificationFeed(): NotificationFeed = withDelegate { getNotificationFeed() }

    override suspend fun getPrivateMessages(page: Int): List<CommunityItem> =
        withDelegate { getPrivateMessages(page) }

    override suspend fun getMessageConversations(page: Int): MessageConversationPage =
        withDelegate { getMessageConversations(page) }

    override suspend fun getMessagePosts(conversationId: String, page: Int): MessagePostPage =
        withDelegate { getMessagePosts(conversationId, page) }

    override suspend fun sendMessage(request: MessageRequest): WriteResult =
        withDelegate { sendMessage(request) }

    override suspend fun ratePost(topicId: String, postId: String, rating: PostRating): WriteResult =
        withDelegate { ratePost(topicId, postId, rating) }

    override suspend fun preparePublish(target: PublishTarget): PublishPreparation =
        withDelegate { preparePublish(target) }

    override suspend fun setSubforumVisibility(parentForumId: String, filterId: String, visible: Boolean): WriteResult =
        withDelegate { setSubforumVisibility(parentForumId, filterId, visible) }

    override suspend fun getFavoriteForums(): List<Forum> = withDelegate { getFavoriteForums() }

    override suspend fun setForumFavorite(forum: Forum, favorite: Boolean): WriteResult =
        withDelegate { setForumFavorite(forum, favorite) }

    override suspend fun publish(request: PublishRequest): WriteResult = withDelegate { publish(request) }

    override suspend fun uploadImage(target: PublishTarget, image: ImageUpload): ImageUploadResult =
        withDelegate { uploadImage(target, image) }
}
