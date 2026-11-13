package dev.nagi.sdk

import org.w3c.dom.Element
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal object NgaXmlParser {
    private val illegalNumericEntity = Regex("""&#(?:x0*[0-8bcefBCEF]|x0*1[0-9a-fA-F]|0*(?:[0-8]|1[12]|1[4-9]|2\d|3[01]));""")

    fun looksLikeXml(raw: String): Boolean {
        val trimmed = raw.trimStart().removePrefix("\uFEFF").trimStart()
        return trimmed.startsWith("<?xml", ignoreCase = true) || trimmed.startsWith("<root", ignoreCase = true)
    }

    fun extractErrorOrNull(raw: String): NgaException? {
        if (!looksLikeXml(raw)) return null
        val root = try { parseRoot(raw) } catch (_: Exception) { return null }
        return rootErrorOrNull(root)
    }

    fun topics(raw: String, page: Int, defaultPageSize: Int = 30): TopicPage {
        val root = parseRoot(raw)
        rootErrorOrNull(root)?.let { throw it }
        val topicsNode = root.directChild("__T") ?: throw NgaException.Protocol(ProtocolReason.XML_TOPICS_NODE)
        val items = topicsNode.directChildren("item").map { item ->
            val id = item.field("tid")
            if (id.isBlank()) throw NgaException.Protocol(ProtocolReason.XML_TOPIC_ID)
            Topic(
                id = id,
                title = plain(item.field("subject")),
                author = plain(item.field("author")),
                authorId = item.field("authorid"),
                createdAt = item.field("postdate").toLongOrNull() ?: 0L,
                lastActiveAt = item.field("lastpost").toLongOrNull() ?: 0L,
                replies = item.field("replies").toIntOrNull() ?: 0,
                recommended = (item.field("recommend").toIntOrNull() ?: 0) > 0,
            )
        }
        val total = (root.fieldOrNull("__ROWS")?.toIntOrNull() ?: items.size).coerceAtLeast(0)
        val pageSize = (root.fieldOrNull("__T__ROWS_PAGE")?.toIntOrNull() ?: defaultPageSize).coerceAtLeast(1)
        val subforums = root.directChild("__F")?.directChild("sub_forums")?.let { node ->
            (0 until node.childNodes.length).mapNotNull { node.childNodes.item(it) as? Element }.mapNotNull { entry ->
                val values = (0 until entry.childNodes.length).mapNotNull { entry.childNodes.item(it) as? Element }
                    .map { it.textContent.trim() }
                val id = values.getOrNull(0)?.takeIf { it.toLongOrNull() != null } ?: return@mapNotNull null
                val name = values.getOrNull(1)?.let(::plain)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Subforum(Forum(if (entry.tagName == "item") id else "0", name, "", "",
                    collectionId = if (entry.tagName == "item") null else id,
                    description = values.getOrNull(2)?.let(::plain).orEmpty()),
                    values.getOrNull(3).orEmpty(), values.getOrNull(4)?.toLongOrNull())
            }.distinctBy { it.forum.key }
        }.orEmpty()
        return TopicPage(items, page, total, items.isNotEmpty() && page.toLong() * pageSize < total, subforums)
    }

    fun userReplies(raw: String, page: Int): UserReplyPage {
        val root = parseRoot(raw)
        rootErrorOrNull(root)?.let { throw it }
        val topics = topics(raw, page)
        val nodes = root.directChild("__T")?.directChildren("item") ?: throw NgaException.Protocol(ProtocolReason.XML_USER_TOPICS_NODE)
        val items = nodes.zip(topics.items).map { (item, topic) ->
            val post = item.directChild("__P") ?: throw NgaException.Protocol(ProtocolReason.XML_USER_POST_NODE)
            val pid = post.field("pid")
            if (!pid.matches(Regex("[0-9]+"))) throw NgaException.Protocol(ProtocolReason.XML_USER_POST_ID)
            if (post.field("tid") != topic.id) throw NgaException.Protocol(ProtocolReason.XML_USER_POST_TOPIC)
            if (post.fieldOrNull("content") == null) throw NgaException.Protocol(ProtocolReason.XML_USER_POST_CONTENT)
            UserReply(topic, pid, post.field("authorid"), post.field("postdate").toLongOrNull() ?: 0,
                ContentParser.parse(post.field("content"), context = ContentContext(post.field("authorid"), topic.id, pid, post.field("postdate"))))
        }
        val hasMore = if (items.isEmpty()) false else if (root.fieldOrNull("__ROWS") != null &&
            (root.fieldOrNull("__T__ROWS_PAGE")?.toIntOrNull() ?: 0) > 0) topics.hasMore else null
        return UserReplyPage(items, page, hasMore)
    }

    fun posts(raw: String, page: Int): PostPage {
        val root = parseRoot(raw)
        rootErrorOrNull(root)?.let { throw it }
        val topic = root.directChild("__T") ?: throw NgaException.Protocol(ProtocolReason.XML_THREAD_NODE)
        val repliesNode = root.directChild("__R") ?: throw NgaException.Protocol(ProtocolReason.XML_REPLIES_NODE)
        val usersNode = root.directChild("__U")
        val users = usersNode?.directChildren("item")
            ?.mapNotNull { user -> user.field("uid").takeIf(String::isNotBlank)?.let { it to user } }
            ?.toMap().orEmpty()
        val groupsNode = usersNode?.directChild("__GROUPS") ?: root.directChild("__GROUPS")
        val groups = groupsNode?.directChildren("item")?.mapNotNull { g ->
            val subItems = g.directChildren("item")
            val gid = g.getAttribute("id").ifBlank { g.getAttribute("i") }.ifBlank { g.field("id") }
                .ifBlank { g.field("2") }.ifBlank { subItems.getOrNull(2)?.textContent.orEmpty() }
            val name = plain(
                g.field("0").ifBlank { g.field("name") }.ifBlank { subItems.getOrNull(0)?.textContent.orEmpty() },
            ).trim()
            if (gid.isNotBlank() && name.isNotBlank()) gid to name else null
        }?.toMap().orEmpty()
        val base = root.directChild("__GLOBAL")?.field("_ATTACH_BASE_VIEW")
            ?.takeIf(String::isNotBlank) ?: "https://img.nga.cn/attachments/"
        val size = (root.fieldOrNull("__R__ROWS_PAGE")?.toIntOrNull() ?: 0).coerceAtLeast(0)
        val paginationSize = size.takeIf { it > 0 } ?: 20
        fun parsePost(item: Element, fallbackFloor: Int, depth: Int): Post {
            if (depth > 8) throw NgaException.Protocol(ProtocolReason.XML_COMMENT_DEPTH)
            val uid = item.field("authorid")
            val user = users[uid]
            val floor = item.field("lou").toIntOrNull() ?: fallbackFloor
            val pid = item.field("pid").ifBlank { "floor:$floor" }
            val authorName = plain(item.field("author").ifBlank { user?.field("username").orEmpty().ifBlank { uid } })
            val body = item.fieldOrNull("content")?.takeIf(String::isNotEmpty) ?: item.field("subject")
            val memberId = user?.field("memberid").orEmpty().ifBlank { user?.field("groupid").orEmpty() }
            val level = groups[memberId] ?: user?.field("group")?.takeIf(String::isNotBlank)?.let(::plain)
            val reputation = formatReputation(user?.field("rvrc").orEmpty().ifBlank { user?.field("fame").orEmpty() })
            val postCount = parsePostCount(user?.field("postnum").orEmpty().ifBlank { user?.field("posts").orEmpty() })
            val clientPlatform = normalizeClientPlatform(item.field("from_client"))
            return Post(
                id = pid,
                floor = floor,
                author = authorName,
                authorId = uid,
                avatar = resourceUrl(user?.field("avatar").orEmpty(), "https://img.nga.cn/"),
                date = item.field("postdate"),
                content = ContentParser.parse(body, base, ContentContext(uid, topic.field("tid"), pid, item.field("postdate"))),
                comments = item.directChild("comment")?.directChildren("item")?.mapIndexed { index, comment ->
                    parsePost(comment, index, depth + 1)
                }.orEmpty(),
                score = item.field("score").toIntOrNull() ?: 0,
                authorLevel = level,
                authorReputation = reputation,
                authorPosts = postCount,
                clientPlatform = clientPlatform,
            )
        }
        val items = repliesNode.directChildren("item").mapIndexed { index, item ->
            parsePost(item, (page - 1) * paginationSize + index, 0)
        }
        if (items.isEmpty()) throw NgaException.Protocol(ProtocolReason.XML_NO_REPLIES)
        val rowsValue = root.fieldOrNull("__ROWS")?.toIntOrNull()
        val total = (rowsValue ?: items.size).coerceAtLeast(0)
        val totalPages = if (rowsValue != null && size > 0) ((total + size - 1L) / size).toInt().coerceAtLeast(page) else 0
        return PostPage(
            items = items,
            title = plain(topic.field("subject")),
            authorId = topic.field("authorid"),
            page = page,
            total = total,
            hasMore = items.isNotEmpty() && page.toLong() * paginationSize < total,
            pageSize = size,
            totalPages = totalPages,
        )
    }

    fun publishPreparation(raw: String, target: PublishTarget? = null): PublishPreparation {
        val root = parseRoot(raw)
        rootErrorOrNull(root)?.let { throw it }
        if (target != null && target.action != PublishAction.NEW_TOPIC) {
            if (root.field("action") != target.action.wireValue || root.field("tid") != target.topicId ||
                root.field("pid").ifBlank { "0" } != (target.postId ?: "0") ||
                (target.action != PublishAction.MODIFY && root.field("auth").isBlank()) ||
                root.directChild("__CU")?.field("uid")?.toLongOrNull()?.let { it > 0 } != true) {
                throw NgaException.Protocol(ProtocolReason.XML_PUBLISH_MISMATCH)
            }
        }
        val emptyReply = target?.action == PublishAction.REPLY && target.postId in listOf(null, "0") &&
            root.field("action") == "reply" && root.field("tid") == target.topicId &&
            root.field("pid") in listOf("", "0") && root.field("auth").isNotBlank() &&
            root.directChild("__CU")?.field("uid")?.toLongOrNull()?.let { it > 0 } == true
        val content = root.fieldOrNull("content") ?: if (emptyReply) "" else throw NgaException.Protocol(ProtocolReason.XML_PUBLISH_CONTENT)
        return PublishPreparation(decodeNgaEditor(root.field("subject")), decodeNgaEditor(content))
    }

    fun uploadGrant(raw: String, target: PublishTarget, userId: String): UploadGrant {
        val root = parseRoot(raw)
        rootErrorOrNull(root)?.let { throw it }
        val fid = root.field("fid").ifBlank { root.directChild("__F")?.field("fid").orEmpty() }
        val validTarget = if (target.action == PublishAction.NEW_TOPIC) {
            root.field("action") == "new" && (target.collectionId != null || fid == target.forumId)
        } else {
            root.field("action") == target.action.wireValue && root.field("tid") == target.topicId &&
                root.field("pid").ifBlank { "0" } == (target.postId ?: "0")
        }
        if (!validTarget || root.directChild("__CU")?.field("uid") != userId || fid.isBlank() ||
            root.field("auth").isBlank() || root.field("attach_url").isBlank()) {
            throw NgaException.Protocol(ProtocolReason.XML_PUBLISH_MISMATCH)
        }
        return UploadGrant(root.field("attach_url"), root.field("auth"), fid)
    }

    fun uploadedAttachment(raw: String): UploadedAttachment? = try {
        val root = parseRoot(raw)
        val url = root.field("url")
        if (url.startsWith("./mon_") || url.startsWith("mon_") || url.startsWith("https://img.nga.cn/attachments/") ||
            url.startsWith("https://img8.nga.cn/attachments/")) {
            UploadedAttachment(root.field("attachments"), root.field("attachments_check"), url)
        } else null
    } catch (_: NgaException.Protocol) { null } catch (_: IllegalArgumentException) { null }

    private fun rootErrorOrNull(root: Element): NgaException? {
        val explicitError = root.directChild("error")
        if (explicitError != null) return classifyNgaError(explicitError.collectText())
        val message = root.directChild("__MESSAGE")
        if (message != null && root.directChild("__T") == null) {
            return classifyNgaError(message.collectText())
        }
        return null
    }

    private fun parseRoot(raw: String): Element {
        try {
            val sanitized = sanitizeXml(raw)
            if (sanitized.contains("<!DOCTYPE", ignoreCase = true) || sanitized.contains("<!ENTITY", ignoreCase = true)) {
                throw NgaException.Protocol(ProtocolReason.XML_DOCTYPE)
            }
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
                runCatching { isXIncludeAware = false }
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            }
            val builder = factory.newDocumentBuilder().apply {
                setEntityResolver(EntityResolver { _, _ -> InputSource(StringReader("")) })
                setErrorHandler(null)
            }
            val document = builder.parse(InputSource(StringReader(sanitized)))
            val root = document.documentElement ?: throw NgaException.Protocol(ProtocolReason.XML_NO_ROOT)
            if (root.tagName != "root") throw NgaException.Protocol(ProtocolReason.XML_ROOT_TAG)
            return root
        } catch (error: NgaException) {
            throw error
        } catch (_: Exception) {
            throw NgaException.Protocol(ProtocolReason.XML_MALFORMED)
        }
    }

    private fun sanitizeXml(raw: String): String {
        val trimmed = raw.trimStart().removePrefix("\uFEFF").trim()
        if (trimmed.isEmpty()) throw NgaException.Protocol(ProtocolReason.XML_EMPTY)
        val cleaned = StringBuilder(trimmed.length)
        var index = 0
        while (index < trimmed.length) {
            val code = trimmed.codePointAt(index)
            val validXmlChar = code == 0x09 || code == 0x0A || code == 0x0D ||
                code in 0x20..0xD7FF || code in 0xE000..0xFFFD || code in 0x10000..0x10FFFF
            if (validXmlChar) cleaned.appendCodePoint(code)
            index += Character.charCount(code)
        }
        return illegalNumericEntity.replace(cleaned.toString(), "")
    }

    private fun Element.directChild(name: String): Element? {
        var child = firstChild
        while (child != null) {
            if (child is Element && child.tagName == name) return child
            child = child.nextSibling
        }
        return null
    }

    private fun Element.directChildren(name: String): List<Element> {
        val list = ArrayList<Element>()
        var child = firstChild
        while (child != null) {
            if (child is Element && child.tagName == name) list.add(child)
            child = child.nextSibling
        }
        return list
    }

    private fun Element.fieldOrNull(name: String): String? {
        if (hasAttribute(name)) return getAttribute(name)
        return directChild(name)?.textContent
    }

    private fun Element.field(name: String): String = fieldOrNull(name).orEmpty()

    private fun Element.collectText(): String {
        val items = directChildren("item")
        return if (items.isNotEmpty()) items.joinToString(" ") { it.textContent.orEmpty() }
        else textContent.orEmpty()
    }
}
