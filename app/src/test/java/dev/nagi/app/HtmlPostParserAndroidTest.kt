package dev.nagi.app

import dev.nagi.sdk.NgaException
import dev.nagi.sdk.PostPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HtmlPostParserAndroidTest {
    private val raw = """
            <html><head><script>
            var __CURRENT_TID=123, __CURRENT_PAGE=1, __CURRENT_AUTHORID=0;
            </script></head><body>
            <h1 id="currentTopicName">Synthetic topic</h1>
            <div id="m_posts_c">
              <a id="postauthor0" href="nuke.php?uid=7">Reader</a>
              <div id="postcontainer0"><a id="pid0Anchor"></a>
                <span id="postdate0">2026-11-07 12:00</span>
                <span id="postcontent0">Synthetic content</span>
              </div>
            </div>
            <script>
            commonui.userInfo.setAll({"7":{"username":"Reader","avatar":""}});
            commonui.postArg.setDefault(-7,0,123,7,0,'Synthetic topic','',null,'',null,0,40,1234,20);
            var __PAGE = {0:'/read.php?tid=123',1:3,2:1,3:20};
            </script></body></html>
        """.trimIndent()

    private fun parse(source: String): PostPage {
        val parser = Class.forName("dev.nagi.sdk.NgaHtmlPostParser")
        return try {
            parser.getMethod("posts", String::class.java, String::class.java,
                Int::class.javaPrimitiveType, String::class.java)
                .invoke(parser.getField("INSTANCE").get(null), source, "123", 1, null) as PostPage
        } catch (error: java.lang.reflect.InvocationTargetException) { throw requireNotNull(error.cause) }
    }

    @Test fun nativeHtmlPaginationAcceptsNgaNumericJavascriptKeys() {
        val result = parse(raw)
        assertEquals(1, result.page)
        assertEquals(3, result.totalPages)
        assertEquals(20, result.pageSize)
        assertEquals("Reader", result.items.single().author)
    }

    @Test fun numericUserKeysAndQuotedObjectSyntaxRemainReadable() {
        val source = raw.replace("\"7\":", "7:")
            .replace("\"username\":\"Reader\"", "\"username\":\"Reader, {1:999,2:888}\"")
            .replace("'/read.php?tid=123'", "'/read.php?tid=123&note={1:999,2:888}'")
        assertEquals("Reader, {1:999,2:888}", parse(source).items.single().author)
        val escaped = """Reader "quoted", {1:999} \ backslash"""
        val withEscapes = raw.replace("\"username\":\"Reader\"", "\"username\":" + org.json.JSONObject.quote(escaped))
        assertEquals(escaped, parse(withEscapes).items.single().author)
    }

    @Test fun quotedNumericKeysStillWork() {
        val source = raw.replace("0:'/read.php?tid=123',1:3,2:1,3:20",
            "\"0\":\"/read.php?tid=123\",\"1\":3,\"2\":1,\"3\":20")
        assertEquals(3, parse(source).totalPages)
    }

    @Test fun missingFloorsDoNotRequireMatchingDeclaredCounts() {
        val source = raw.replace("var __PAGE = {0:'/read.php?tid=123',1:3,2:1,3:20};", "")
            .replace("postcontainer0", "postcontainer6").replace("postcontent0", "postcontent6")
            .replace("postauthor0", "postauthor6").replace("postdate0", "postdate6")
        val result = parse(source)
        assertEquals(listOf(6), result.items.map { it.floor })
        assertEquals(41, result.total)
        assertEquals(0, result.pageSize)
    }

    @Test fun protocolDiagnosticsDoNotExposeAndroidParserInput() {
        val marker = "SENSITIVE_RESPONSE_MARKER"
        val htmlError = assertThrows(NgaException.Protocol::class.java) {
            parse(raw.replace("1:3,2:1,3:20", "1:'$marker',2:1,3:20"))
        }
        val parser = Class.forName("dev.nagi.sdk.NgaParser")
        val invocation = assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            parser.getMethod("data", String::class.java).invoke(parser.getField("INSTANCE").get(null),
                "{\"private\":\"$marker\",\"data\":")
        }
        val jsonError = invocation.cause as NgaException.Protocol
        assertEquals("P-3006", htmlError.code)
        assertEquals("P-2000", jsonError.code)
        for (error in listOf(htmlError, jsonError)) {
            assertFalse(error.stackTraceToString().contains(marker))
            assertNull(error.cause)
        }
    }

    @Test fun invalidPaginationIsStillRejected() {
        for (source in listOf(
            raw.replace("1:3,2:1,3:20", "1:0,2:1,3:20"),
            raw.replace("1:3,2:1,3:20", "1:3,2:2,3:20"),
            raw.replace("1:3,2:1,3:20", "1:3,2:1,3:0"),
            raw.replace("1:3,2:1,3:20", "1:3,2:1,3:'bad'"),
            raw.replace("var __PAGE =", "var __PAGE = invalid"),
        )) {
            val error = assertThrows(NgaException.Protocol::class.java) { parse(source) }
            assertEquals("P-3006", error.code)
        }
    }
}
