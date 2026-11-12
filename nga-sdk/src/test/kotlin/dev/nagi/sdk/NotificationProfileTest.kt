package dev.nagi.sdk

import org.junit.Assert.*
import org.junit.Test

class NotificationProfileTest {
    @Test fun missingReadFlagDoesNotInventUnread() {
        val item = NgaParser.community("""{"data":{"0":{"id":1,"tid":2,"subject":"Test"}}}""").single()
        assertFalse(item.unread)
        assertFalse(item.unreadKnown)
        assertNull(NgaParser.notifications("""{"data":{"0":{"id":1,"tid":2}}}""").unreadCount)
    }

    @Test fun emptyStringFeedHasNoItemsOrInventedUnreadCount() {
        val feed = NgaParser.notifications("""{"data":{"0":""},"time":1}""")
        assertTrue(feed.items.isEmpty())
        assertNull(feed.unreadCount)
    }

    @Test fun numericEnvelopeUsesAggregateNotGuessedPerItemUnread() {
        val feed = NgaParser.notifications("""{"data":{"0":{"unread":3,"0":[{"0":1,"1":7,"2":"Actor","5":"Title","6":123,"7":456,"9":42}],"1":[{"0":2,"1":8,"2":"Sender"}]}}}""")
        assertEquals(3, feed.unreadCount)
        assertEquals(2, feed.items.size)
        assertEquals("123", feed.items.first().topicId)
        assertEquals("456", feed.items.first().postId)
        assertTrue(feed.items.none { it.unreadKnown || it.unread })
    }

    @Test fun explicitFlagsEmptyFeedsAndUnknownCounts() {
        val feed = NgaParser.notifications("""{"data":{"0":{"id":1,"read":0},"1":{"id":2,"read":1}}}""")
        assertEquals(1, feed.unreadCount)
        assertEquals(0, NgaParser.notifications("""{"data":{"0":{"unread":0,"0":[],"1":[]}}}""").unreadCount)
        assertNull(NgaParser.notifications("""{"data":{"0":{"unread":-1,"0":[]}}}""").unreadCount)
        assertNull(NgaParser.notifications("""{"data":{"0":{"id":1,"read":"bad"}}}""").unreadCount)
    }

    @Test fun malformedNotificationGroupsAndUnknownPayloadsAreNotEmptySuccess() {
        listOf(
            """{"data":{"0":{"unread":2,"0":"bad"}}}""",
            """{"data":{"0":{"unread":2,"0":[1]}}}""",
            """{"data":{"unexpected":"value"}}""",
            """{"data":{"0":"unexpected"}}""",
            """{"data":{"0":" "}}""",
            """{"data":{"0":null}}""",
            """{"data":{"0":0}}""",
            """{"data":{"0":"","unexpected":"value"}}""",
        ).forEach { raw -> assertThrows(NgaException.Protocol::class.java) { NgaParser.notifications(raw) } }
    }

    @Test fun typedFeedRetainsServerCountAndUsesAuthenticatedRead() = kotlinx.coroutines.runBlocking {
        okhttp3.mockwebserver.MockWebServer().use { server ->
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("""{"data":{"0":{"unread":5,"0":[],"1":[]}}}"""))
            val store = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
            val client = HttpNgaClient(server.url("/"), store)
            assertEquals(5, client.getNotificationFeed().unreadCount)
            val request = server.takeRequest()
            assertEquals("noti", request.requestUrl!!.queryParameter("__lib"))
            assertEquals("0", request.requestUrl!!.queryParameter("time_limit"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun authenticatedEmptyStringFeedDoesNotFailAsMalformed() = kotlinx.coroutines.runBlocking {
        okhttp3.mockwebserver.MockWebServer().use { server ->
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("""{"data":{0:""},"time":1}"""))
            val store = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
            val client = HttpNgaClient(server.url("/"), store)
            val feed = client.getNotificationFeed()
            assertTrue(feed.items.isEmpty())
            assertNull(feed.unreadCount)
            assertEquals(1, server.requestCount)
            assertEquals("0", server.takeRequest().requestUrl!!.queryParameter("time_limit"))
        }
    }

    @Test fun profileOptionalFieldsAreTypedAndIdentityMustMatch() {
        val user = NgaParser.user("""{"data":{"0":{"uid":7,"username":"User","ipLoc":" 上海 ","regdate":"1000000000","sign":"[b]Hello[/b]"}}}""", "7")
        assertEquals("上海", user.ipLocation)
        assertEquals(1000000000L, user.registeredAt)
        assertEquals("Hello", (user.signature.single() as ContentBlock.Text).value)
        val missing = NgaParser.user("""{"data":{"0":{"uid":7,"username":"User","ipLoc":{},"regdate":-1,"sign":null}}}""", "7")
        assertNull(missing.ipLocation)
        assertNull(missing.registeredAt)
        assertTrue(missing.signature.isEmpty())
        assertThrows(NgaException.Protocol::class.java) { NgaParser.user("""{"data":{"uid":8,"username":"Other"}}""", "7") }
    }
}
