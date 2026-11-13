package dev.nagi.sdk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody
import okhttp3.Request
import retrofit2.Converter
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.QueryMap
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Url
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.lang.reflect.Type
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface NgaClient {
    suspend fun getForums(): List<Forum>
    suspend fun getTopics(forumId: String, page: Int = 1, collectionId: String? = null, sort: TopicSort = TopicSort.LAST_ACTIVE): TopicPage
    suspend fun getPosts(topicId: String, page: Int = 1, authorId: String? = null): PostPage
    suspend fun searchTopics(keyword: String, forumId: String? = null, page: Int = 1, collectionId: String? = null): TopicPage
    suspend fun findUser(username: String): NgaUser?
    suspend fun getUser(userId: String): NgaUser
    suspend fun getUserTopics(userId: String, page: Int = 1): TopicPage
    suspend fun getUserReplies(userId: String, page: Int = 1): UserReplyPage
    suspend fun getNotifications(since: Long = 0): List<CommunityItem>
    suspend fun getNotificationFeed(): NotificationFeed = NotificationFeed(getNotifications(), null)
    suspend fun getPrivateMessages(page: Int = 1): List<CommunityItem>
    suspend fun getMessageConversations(page: Int = 1): MessageConversationPage
    /** Reading a conversation may mark it read on the server. */
    suspend fun getMessagePosts(conversationId: String, page: Int = 1): MessagePostPage
    suspend fun sendMessage(request: MessageRequest): WriteResult
    suspend fun ratePost(topicId: String, postId: String, rating: PostRating): WriteResult
    suspend fun preparePublish(target: PublishTarget): PublishPreparation
    suspend fun setSubforumVisibility(parentForumId: String, filterId: String, visible: Boolean): WriteResult =
        throw UnsupportedOperationException("Subforum settings unavailable")
    suspend fun publish(request: PublishRequest): WriteResult
    /** Preparation failures throw before upload; upload outcomes never imply a usable receipt. */
    suspend fun uploadImage(target: PublishTarget, image: ImageUpload): ImageUploadResult =
        throw UnsupportedOperationException("Image upload unavailable")
}

enum class NgaDomain(val baseUrl: String) {
    PRIMARY("https://bbs.nga.cn/"), APP("https://ngabbs.com/"), ALTERNATE("https://nga.178.com/"), LEGACY("https://bbs.ngacn.cc/")
}

data class NgaConfig(val domain: NgaDomain = NgaDomain.PRIMARY, val timeoutSeconds: Long = 25,
    val writeDiagnostics: Boolean = false,
) {
    init { require(timeoutSeconds > 0) }
}

fun createNgaClient(config: NgaConfig = NgaConfig(), sessions: SessionStore = MemorySessionStore()): NgaClient {
    val primary = config.domain.readOrigin()
    val backups = listOf(NgaDomain.APP, NgaDomain.PRIMARY)
        .map { it.baseUrl.toHttpUrl() }
        .filter { it != primary }
    return HttpNgaClient(primary, sessions, config.timeoutSeconds, backups, config.writeDiagnostics)
}

internal fun NgaDomain.readOrigin(): HttpUrl =
    (if (this == NgaDomain.ALTERNATE) NgaDomain.PRIMARY else this).baseUrl.toHttpUrl()

internal interface NgaApi {
    @Multipart @POST
    suspend fun upload(@Url url: String, @Part parts: List<MultipartBody.Part>): Response<String>
    @FormUrlEncoded @POST("app_api.php")
    suspend fun forums(@QueryMap query: Map<String, String>, @FieldMap fields: Map<String, String> = emptyMap()): String
    @FormUrlEncoded @POST("thread.php")
    suspend fun topicsPost(@QueryMap query: Map<String, String>, @FieldMap fields: Map<String, String> = emptyMap()): String
    @FormUrlEncoded @POST("read.php")
    suspend fun postsPost(@QueryMap query: Map<String, String>, @FieldMap fields: Map<String, String> = emptyMap()): String
    @FormUrlEncoded @POST("nuke.php")
    suspend fun user(@QueryMap query: Map<String, String>, @FieldMap fields: Map<String, String> = emptyMap()): String
    @FormUrlEncoded @POST("post.php")
    suspend fun publish(@QueryMap query: Map<String, String>, @FieldMap fields: Map<String, String>): String
    @FormUrlEncoded @POST("nuke.php")
    suspend fun userWrite(@QueryMap query: Map<String, String>, @FieldMap fields: Map<String, String>): Response<String>
    @FormUrlEncoded @POST("post.php")
    suspend fun publishWrite(@QueryMap query: Map<String, String>, @FieldMap fields: Map<String, String>): Response<String>
}

private fun Response<String>.writeBody(): String {
    if (!isSuccessful) throw HttpException(this)
    return body().orEmpty()
}

internal enum class ClientProfile(val userAgent: String, val xUserAgent: String?) {
    WP_READER("NGA_WP_JW/(;WINDOWS)", "NGA_WP_JW/(;WINDOWS)"),
    APPLE("NGA_skull/7.3.1(iPhone17,1;iOS 26.0)", "NGA_skull/7.3.1(iPhone17,1;iOS 26.0)"),
}

internal class HttpNgaClient(
    private val origin: HttpUrl,
    private val sessions: SessionStore,
    private val timeoutSeconds: Long = 25,
    private val backupOrigins: List<HttpUrl> = emptyList(),
    private val writeDiagnostics: Boolean = false,
) : NgaClient {
    private enum class ReadFormat(val parameters: Map<String, String>) {
        JSON(mapOf("__output" to "8")),
        XML_LITE(mapOf("lite" to "xml")),
        XML_COMPACT(mapOf("__output" to "10")),
    }
    private data class ReadAttempt(val origin: HttpUrl, val format: ReadFormat)
    private val xmlFormats = listOf(ReadFormat.XML_LITE, ReadFormat.XML_COMPACT)
    private val connectSeconds = timeoutSeconds.coerceAtMost(5L)

    @Volatile private var readTransport = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .retryOnConnectionFailure(true)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(connectSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    private val writeTransport = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(connectSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    private val preferredReads = ConcurrentHashMap<String, ReadAttempt>()

    private suspend fun <T> exchange(
        requireAuth: Boolean = false,
        isWrite: Boolean = false,
        targetOrigin: HttpUrl = origin,
        profile: ClientProfile = ClientProfile.APPLE,
        expectedSession: SessionSnapshot? = null,
        readStatusBody: Boolean = false,
        recordDiagnostic: ((WriteDiagnostic) -> Unit)? = null,
        request: suspend (NgaApi) -> String,
        parse: (String) -> T,
    ): T {
        val snapshot = expectedSession ?: sessions.snapshot()
        requireCurrent(snapshot)
        if (requireAuth && snapshot.session == null) throw NgaException.Authentication()
        val sent = AtomicBoolean(false)
        var responseStatus: Int? = null
        val baseTransport = if (isWrite) writeTransport else readTransport
        val client = baseTransport.newBuilder().addNetworkInterceptor { chain ->
            if (isWrite && !sent.compareAndSet(false, true)) throw IOException("Automatic request replay blocked")
            val current = try { runBlocking { sessions.snapshot() } }
                catch (_: Exception) { throw IOException("Unable to check NGA session") }
            if (current.revision != snapshot.revision) throw IOException("Account changed before transmission")
            chain.proceed(chain.request()).also { responseStatus = it.code }
        }.addInterceptor { chain ->
            val target = chain.request()
            val builder = target.newBuilder()
                .url(target.url.newBuilder().setQueryParameter("__inchst", "UTF8").build())
                .header("User-Agent", profile.userAgent)
                .header("Referer", targetOrigin.toString())
            profile.xUserAgent?.let { builder.header("X-User-Agent", it) }
            chain.proceed(if (isWrite) builder.build().withNgaSession(snapshot.session) else builder.build())
        }.apply {
            if (!isWrite) addInterceptor(NgaReadRedirectInterceptor(sessions, snapshot))
        }.build()
        val api = Retrofit.Builder().baseUrl(targetOrigin).client(client)
            .addConverterFactory(NgaStringConverter()).build().create(NgaApi::class.java)
        try {
            val raw = request(api)
            requireCurrent(snapshot)
            val result = withContext(Dispatchers.Default) {
                if (isWrite && writeDiagnostics) recordDiagnostic?.invoke(NgaParser.writeDiagnostic(raw, responseStatus))
                parse(raw)
            }
            requireCurrent(snapshot)
            return result
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpException) {
            requireCurrent(snapshot)
            if (isWrite && writeDiagnostics) recordDiagnostic?.invoke(WriteDiagnostic(httpStatus = error.code()))
            val errorBody = error.response()?.errorBody()
            val bytes = try { errorBody?.bytes() } catch (io: IOException) { throw NgaException.Network(io) }
            requireCurrent(snapshot)
            val decoded = bytes?.takeIf { it.isNotEmpty() }?.let { decodeNgaPayload(it, errorBody?.contentType()) }
            if (isWrite && writeDiagnostics) recordDiagnostic?.invoke(
                if (decoded != null) withContext(Dispatchers.Default) { NgaParser.writeDiagnostic(decoded, error.code()) }
                else WriteDiagnostic(httpStatus = error.code()))
            val bodyError = decoded?.let { NgaXmlParser.extractErrorOrNull(it) ?: NgaParser.extractErrorOrNull(it) }
            if (bodyError != null) throw bodyError
            if (readStatusBody) {
                if (decoded == null) throw NgaException.Http(error.code())
                val result = withContext(Dispatchers.Default) { parse(decoded) }
                requireCurrent(snapshot)
                return result
            }
            throw when (error.code()) {
                401 -> NgaException.Authentication()
                403, 429 -> NgaException.Restricted()
                else -> NgaException.Http(error.code())
            }
        } catch (error: IOException) {
            requireCurrent(snapshot)
            if (isWrite && writeDiagnostics) recordDiagnostic?.invoke(WriteDiagnostic(httpStatus = responseStatus))
            throw NgaException.Network(error)
        }
    }

    private suspend fun <T> exchangeRead(
        requireAuth: Boolean = false,
        formats: List<ReadFormat> = listOf(ReadFormat.JSON),
        cacheKey: String? = null,
        profile: ClientProfile = ClientProfile.APPLE,
        request: suspend (NgaApi, Map<String, String>) -> String,
        parse: (String, ReadFormat) -> T,
    ): T {
        val snapshot = sessions.snapshot()
        val attempts = (listOf(origin) + backupOrigins).distinct().flatMap { host ->
            formats.map { ReadAttempt(host, it) }
        }.toMutableList()
        cacheKey?.let { preferredReads[it] }?.let { preferred ->
            if (attempts.remove(preferred)) attempts.add(0, preferred)
        }
        val outcome = withTimeoutOrNull(TimeUnit.SECONDS.toMillis(timeoutSeconds)) {
            val unavailable = HashSet<HttpUrl>()
            var firstError: NgaException? = null
            for (attempt in attempts) {
                if (attempt.origin in unavailable) continue
                try {
                    val result = exchange(
                        requireAuth = requireAuth,
                        targetOrigin = attempt.origin,
                        profile = profile,
                        expectedSession = snapshot,
                        request = { request(it, attempt.format.parameters) },
                        parse = { parse(it, attempt.format) },
                    )
                    if (cacheKey != null) {
                        if (preferredReads.size >= 512) preferredReads.clear()
                        preferredReads[cacheKey] = attempt
                    }
                    return@withTimeoutOrNull Result.success(result)
                } catch (error: NgaException) {
                    when (error) {
                        is NgaException.Protocol -> if (formats.size == 1) throw error
                        is NgaException.Network -> {
                            if (error.cause is ProtocolException) throw error
                            if (error.cause is ConnectException || error.cause is UnknownHostException ||
                                error.cause is InterruptedIOException) unavailable.add(attempt.origin)
                        }
                        is NgaException.Http -> {
                            if (error.status !in 500..599) throw error
                            unavailable.add(attempt.origin)
                        }
                        else -> throw error
                    }
                    if (firstError == null) firstError = error
                }
            }
            throw firstError ?: NgaException.Network()
        } ?: throw NgaException.Network(SocketTimeoutException("NGA read deadline exceeded"))
        return outcome.getOrThrow()
    }

    private suspend fun requireCurrent(snapshot: SessionSnapshot) {
        if (sessions.snapshot().revision != snapshot.revision) throw CancellationException("Account changed")
    }

    override suspend fun getForums(): List<Forum> = exchangeRead(
        request = { api, format -> api.forums(query("__lib" to "home", "__act" to "category") + format) },
        parse = { raw, _ -> NgaParser.forums(raw) },
    )

    override suspend fun getTopics(forumId: String, page: Int, collectionId: String?, sort: TopicSort): TopicPage {
        require(page > 0); require(forumId.isNotBlank() || !collectionId.isNullOrBlank())
        require(collectionId == null || collectionId.isNotBlank())
        val scope = if (collectionId == null) "fid" to forumId else "stid" to collectionId
        val parameters = mapOf(scope, "page" to "$page").toMutableMap()
        if (sort == TopicSort.NEWEST) parameters["order_by"] = "postdatedesc"
        return exchangeRead(formats = xmlFormats, cacheKey = "topics",
            request = { api, format -> api.topicsPost(parameters + format) },
            parse = { raw, _ -> parseStructuredTopics(raw, page) },
        )
    }

    override suspend fun getPosts(topicId: String, page: Int, authorId: String?): PostPage {
        require(topicId.isNotBlank() && page > 0)
        val parameters = postQuery(topicId, page, authorId)
        val snapshot = sessions.snapshot()
        val key = "posts:$topicId:${authorId.orEmpty()}"
        val formats = xmlFormats.toMutableList()
        preferredReads[key]?.format?.let { if (formats.remove(it)) formats.add(0, it) }
        return withTimeoutOrNull(TimeUnit.SECONDS.toMillis(timeoutSeconds)) {
            var firstError: NgaException? = null
            for (format in formats) {
                try {
                    val result = exchange(
                        profile = ClientProfile.WP_READER, expectedSession = snapshot, readStatusBody = true,
                        request = { it.postsPost(parameters + format.parameters) },
                        parse = { parseStructuredPosts(it, page) },
                    )
                    if (firstError != null) {
                        if (preferredReads.size >= 512) preferredReads.clear()
                        preferredReads[key] = ReadAttempt(origin, format)
                    }
                    return@withTimeoutOrNull result
                } catch (error: NgaException) {
                    if (error !is NgaException.Protocol && error !is NgaException.Http) throw error
                    if (firstError == null) firstError = error
                    readTransport = readTransport.newBuilder().connectionPool(ConnectionPool()).build()
                }
            }
            if (firstError !is NgaException.Protocol) throw firstError ?: NgaException.Protocol(ProtocolReason.UNKNOWN)
            try {
                exchange(
                    profile = ClientProfile.WP_READER, expectedSession = snapshot, readStatusBody = true,
                    request = { it.postsPost(parameters) },
                    parse = { NgaHtmlPostParser.posts(it, topicId, page, authorId) },
                )
            } catch (htmlError: NgaException.Protocol) {
                throw firstError.then(htmlError)
            }
        } ?: throw NgaException.Network(SocketTimeoutException("NGA read deadline exceeded"))
    }

    override suspend fun searchTopics(keyword: String, forumId: String?, page: Int, collectionId: String?): TopicPage {
        require(keyword.isNotBlank() && page > 0)
        require(forumId == null || forumId.isNotBlank())
        require(collectionId == null || collectionId.isNotBlank())
        val parameters = query("key" to keyword.trim(), "page" to "$page", "__inchst" to "UTF8").toMutableMap()
        if (collectionId != null) parameters["stid"] = collectionId
        else if (forumId != null) parameters["fid"] = forumId
        parameters.remove("__output")
        return exchangeRead(formats = xmlFormats, cacheKey = "search",
            request = { api, format -> api.topicsPost(parameters + format) },
            parse = { raw, _ -> parseStructuredTopics(raw, page) },
        )
    }

    override suspend fun findUser(username: String): NgaUser? {
        require(username.isNotBlank())
        return try {
            exchangeRead(request = { api, format ->
                api.user(query("__lib" to "ucp", "__act" to "get", "username" to username.trim()) + format)
            }, parse = { raw, _ -> NgaParser.user(raw, "") })
        } catch (_: NgaException.NotFound) { null }
    }

    override suspend fun getUser(userId: String): NgaUser {
        require(userId.isNotBlank())
        return exchangeRead(request = { api, format -> api.user(query("__lib" to "ucp", "__act" to "get", "uid" to userId) + format) },
            parse = { raw, _ -> NgaParser.user(raw, userId) })
    }

    override suspend fun getUserTopics(userId: String, page: Int): TopicPage {
        require(userId.matches(Regex("[1-9][0-9]*")) && page > 0)
        return exchangeRead(formats = xmlFormats, cacheKey = "user-topics",
            request = { api, format -> api.topicsPost(mapOf("authorid" to userId, "page" to "$page") + format) },
            parse = { raw, _ ->
                if (NgaXmlParser.looksLikeXml(raw)) NgaXmlParser.topics(raw, page, 35)
                else NgaParser.topics(raw, page, 35)
            })
    }

    override suspend fun getUserReplies(userId: String, page: Int): UserReplyPage {
        require(userId.matches(Regex("[1-9][0-9]*")) && page > 0)
        return exchangeRead(formats = xmlFormats, cacheKey = "user-replies",
            request = { api, format -> api.topicsPost(mapOf("searchpost" to "1", "authorid" to userId, "page" to "$page") + format) },
            parse = { raw, _ -> if (NgaXmlParser.looksLikeXml(raw)) NgaXmlParser.userReplies(raw, page) else NgaParser.userReplies(raw, page) })
    }

    override suspend fun getNotificationFeed(): NotificationFeed = readNotifications(0)

    override suspend fun getNotifications(since: Long): List<CommunityItem> = readNotifications(since).items

    private suspend fun readNotifications(since: Long): NotificationFeed {
        require(since >= 0)
        return exchangeRead(requireAuth = true, request = { api, format ->
            api.user(query("__lib" to "noti", "__act" to "get_all", "raw" to "3", "time_limit" to "$since") + format)
        }, parse = { raw, _ -> NgaParser.notifications(raw) })
    }

    override suspend fun getPrivateMessages(page: Int): List<CommunityItem> =
        getMessageConversations(page).items.map {
            CommunityItem(it.id, it.title, "", it.senderName, "", "", it.createdAt, unread = false)
        }

    override suspend fun getMessageConversations(page: Int): MessageConversationPage {
        require(page > 0)
        return exchangeRead(requireAuth = true, request = { api, format ->
            api.user(query("__lib" to "message", "__act" to "message", "act" to "list", "page" to "$page") + format)
        }, parse = { raw, _ -> NgaInteractionParser.conversations(raw, page) })
    }

    override suspend fun getMessagePosts(conversationId: String, page: Int): MessagePostPage {
        require(conversationId.isNotBlank() && page > 0)
        return exchangeRead(requireAuth = true, request = { api, format ->
            api.user(query("__lib" to "message", "__act" to "message", "act" to "read", "mid" to conversationId, "page" to "$page") + format)
        }, parse = { raw, _ -> NgaInteractionParser.messagePosts(raw, page) })
    }

    override suspend fun sendMessage(request: MessageRequest): WriteResult = write { diagnostic ->
        val fields = buildMap {
            put("subject", escapeNgaSubmission(request.title))
            put("content", escapeNgaSubmission(request.content))
            if (request.recipients.isNotEmpty()) put("to", request.recipients.joinToString(" "))
            request.conversationId?.let { put("mid", it) }
        }
        exchange(requireAuth = true, isWrite = true,
            recordDiagnostic = diagnostic,
            request = { it.userWrite(query("__lib" to "message", "__act" to "message", "act" to request.action.wireValue), fields).writeBody() },
            parse = { raw ->
                NgaParser.writeErrorOrNull(raw)?.let { throw it }
                WriteResult.Confirmed("私信已发送")
            },
        )
    }

    override suspend fun ratePost(topicId: String, postId: String, rating: PostRating): WriteResult {
        require(topicId.toLongOrNull()?.let { it > 0 } == true)
        require(postId.toLongOrNull()?.let { it >= 0 } == true)
        return write { diagnostic ->
            exchange(requireAuth = true, isWrite = true, recordDiagnostic = diagnostic,
                request = { it.userWrite(query("__lib" to "topic_recommend", "__act" to "add"),
                    mapOf("tid" to topicId, "pid" to postId, "value" to rating.value.toString(), "raw" to "3")).writeBody() },
                parse = { NgaParser.rating(it, rating) })
        }
    }

    override suspend fun setSubforumVisibility(parentForumId: String, filterId: String, visible: Boolean): WriteResult {
        require(parentForumId.toLongOrNull() != null && filterId.toLongOrNull() != null)
        return write { diagnostic ->
            exchange(requireAuth = true, isWrite = true, recordDiagnostic = diagnostic,
                request = { it.userWrite(query("__lib" to "user_option", "__act" to "set",
                    (if (visible) "del" else "add") to filterId),
                    mapOf("fid" to parentForumId, "type" to "1", "info" to "add_to_block_tids")).writeBody() },
                parse = { raw ->
                    NgaParser.writeErrorOrNull(raw)?.let { throw it }
                    WriteResult.Confirmed("子板块设置已更新")
                })
        }
    }

    override suspend fun preparePublish(target: PublishTarget): PublishPreparation = exchangeRead(
        requireAuth = true, formats = listOf(ReadFormat.XML_LITE),
        request = { api, format -> api.publish(publishParameters(target) + format, emptyMap()) },
        parse = { raw, _ -> NgaXmlParser.publishPreparation(raw, target) },
    )

    override suspend fun uploadImage(target: PublishTarget, image: ImageUpload): ImageUploadResult {
        require(target.action in setOf(PublishAction.NEW_TOPIC, PublishAction.REPLY, PublishAction.QUOTE))
        val snapshot = sessions.snapshot()
        val userId = snapshot.session?.userId ?: throw NgaException.Authentication()
        val grant = exchange(requireAuth = true, expectedSession = snapshot,
            request = { it.publish(publishParameters(target) + mapOf("lite" to "xml"), emptyMap()) },
            parse = { NgaXmlParser.uploadGrant(it, target, userId) })
        val url = origin.resolve(grant.url)?.takeIf(::isTrustedUploadUrl)
            ?: throw NgaException.Restricted()
        val parts = imageUploadParts(grant, image, origin.host)
        var attachment: UploadedAttachment? = null
        val outcome = write { diagnostic ->
            exchange(requireAuth = true, isWrite = true, expectedSession = snapshot, recordDiagnostic = diagnostic,
                request = { it.upload(url.toString(), parts).writeBody() },
                parse = { raw ->
                    NgaParser.writeErrorOrNull(raw)?.let { throw it }
                    attachment = NgaXmlParser.uploadedAttachment(raw)
                    WriteResult.Confirmed("图片上传已接受")
                })
        }
        return ImageUploadResult(outcome, attachment)
    }

    override suspend fun publish(request: PublishRequest): WriteResult = write { diagnostic ->
        exchange(
            requireAuth = true, isWrite = true, recordDiagnostic = diagnostic,
            request = { it.publishWrite(query() + publishParameters(request.target), mapOf(
                "step" to "2", "post_subject" to escapeNgaSubmission(request.title),
                "post_content" to escapeNgaSubmission(request.content),
                "attachments" to request.attachments.joinToString("\t") { it.name },
                "attachments_check" to request.attachments.joinToString("\t") { it.check },
            )).writeBody() },
            parse = { NgaParser.publish(it, request.target) },
        )
    }

    private fun publishParameters(target: PublishTarget): Map<String, String> = buildMap {
        put("action", target.action.wireValue)
        if (target.action == PublishAction.NEW_TOPIC) {
            if (target.collectionId != null) put("stid", target.collectionId)
            else put("fid", target.forumId)
        } else {
            put("tid", requireNotNull(target.topicId))
            target.postId?.let { put("pid", it) }
        }
        if (target.action == PublishAction.COMMENT) put("comment", "1")
    }

    private suspend fun write(block: suspend ((WriteDiagnostic) -> Unit) -> WriteResult): WriteResult {
        var diagnostic: WriteDiagnostic? = null
        val result = try { block { diagnostic = it } } catch (error: NgaException) {
            if (writeDiagnostics) diagnostic = (diagnostic ?: WriteDiagnostic()).copy(
                httpStatus = (error as? NgaException.Http)?.status ?: diagnostic?.httpStatus,
                failure = when (error) {
                    is NgaException.Network -> WriteFailure.NETWORK
                    is NgaException.Http -> WriteFailure.HTTP
                    is NgaException.Authentication -> WriteFailure.AUTHENTICATION
                    is NgaException.Restricted -> WriteFailure.RESTRICTED
                    is NgaException.Business, is NgaException.NotFound -> WriteFailure.BUSINESS
                    is NgaException.Protocol -> WriteFailure.PROTOCOL
                })
            when (error) {
                is NgaException.Authentication, is NgaException.Restricted, is NgaException.NotFound -> WriteResult.Rejected(error)
                is NgaException.Business -> if (error.reason == NgaRejectionReason.UNKNOWN) WriteResult.Unconfirmed() else WriteResult.Rejected(error)
                else -> WriteResult.Unconfirmed()
            }
        }
        if (!writeDiagnostics) return result
        return when (result) {
            is WriteResult.Confirmed -> result
            is WriteResult.Rejected -> result.copy(diagnostic = diagnostic ?: WriteDiagnostic())
            is WriteResult.Unconfirmed -> result.copy(diagnostic = diagnostic ?: WriteDiagnostic())
        }
    }

    private fun postQuery(topicId: String, page: Int, authorId: String?): Map<String, String> =
        buildMap {
            put("tid", topicId)
            put("page", "$page")
            authorId?.let { put("authorid", it) }
        }

    private fun parseStructuredTopics(raw: String, page: Int): TopicPage =
        if (NgaXmlParser.looksLikeXml(raw)) NgaXmlParser.topics(raw, page) else NgaParser.topics(raw, page)

    private fun parseStructuredPosts(raw: String, page: Int): PostPage {
        NgaHtmlPostParser.extractErrorOrNull(raw)?.let { throw it }
        return if (NgaXmlParser.looksLikeXml(raw)) NgaXmlParser.posts(raw, page) else NgaParser.posts(raw, page)
    }

    private fun query(vararg values: Pair<String, String>): Map<String, String> = mapOf("__output" to "8", *values)
}

internal fun Request.withNgaSession(session: NgaSession?): Request {
    if (session == null || !isCredentialOrigin(url)) return this
    val builder = newBuilder().header("Cookie", "ngaPassportUid=${session.userId}; ngaPassportCid=${session.token}")
    val form = body
    if (form is FormBody) {
        val authenticated = FormBody.Builder()
        for (index in 0 until form.size) authenticated.add(form.name(index), form.value(index))
        authenticated.add("access_uid", session.userId).add("access_token", session.token)
        builder.method(method, authenticated.build())
    }
    return builder.build()
}

internal fun isCredentialOrigin(url: HttpUrl): Boolean =
    url.isHttps && url.port == 443 && NgaDomain.entries.any { it.baseUrl.toHttpUrl().host == url.host }

internal fun decodeNgaPayload(bytes: ByteArray, contentType: MediaType?): String {
    val declared = contentType?.charset(null)
    return if (declared != null && declared.name().startsWith("GB", ignoreCase = true)) {
        String(bytes, declared)
    } else {
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            String(bytes, charset("GB18030"))
        }
    }
}

internal class NgaStringConverter : Converter.Factory() {
    override fun responseBodyConverter(type: Type, annotations: Array<out Annotation>, retrofit: Retrofit): Converter<ResponseBody, *>? {
        if (type != String::class.java) return null
        return Converter<ResponseBody, String> { body ->
            body.use { decodeNgaPayload(it.bytes(), it.contentType()) }
        }
    }
}
