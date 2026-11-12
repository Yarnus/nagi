package dev.nagi.app

import android.app.Application
import dev.nagi.sdk.NotificationFeed
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class NotificationParserAndroidTest {
    private fun parse(raw: String): NotificationFeed {
        val parser = Class.forName("dev.nagi.sdk.NgaParser")
        return parser.getMethod("notifications", String::class.java)
            .invoke(parser.getField("INSTANCE").get(null), raw) as NotificationFeed
    }

    @Test fun unquotedNumericNotificationKeysWorkOnAndroid() {
        val result = parse("""{"data":{"0":{"0":[{0:1,1:7,2:"用户",5:"测试主题",6:123,7:456,9:42}],"unread":1}}}""")
        assertEquals(1, result.unreadCount)
        assertEquals("用户", result.items.single().actor)
        assertEquals("123", result.items.single().topicId)
        assertEquals("456", result.items.single().postId)
    }

    @Test fun unquotedEmptyStringNotificationFeedWorksOnAndroid() {
        val result = parse("""{"data":{0:""},"time":1}""")
        assertTrue(result.items.isEmpty())
        assertNull(result.unreadCount)
    }

    @Test fun keyLookingStringsRemainUnchanged() {
        val result = parse("""{"data":{0:{0:[{0:1,1:7,2:"名字",5:"{0:123, 42:456} and \\\"9:1\\\"",6:123,7:456,9:42}],unread:1}}}""")
        assertTrue(result.items.single().title.startsWith("{0:123, 42:456}"))
    }
}
