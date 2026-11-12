package dev.nagi.sdk

import org.junit.Assert.*
import org.junit.Test

class NgaTopicLinksTest {
    @Test fun recognizesSupportedOrigins() {
        NgaDomain.entries.forEach {
            assertEquals("45905669", NgaTopicLinks.topicId("${it.baseUrl}read.php?tid=45905669"))
        }
    }

    @Test fun rejectsUntrustedOrUnsupportedTargets() {
        listOf(
            "http://ngabbs.com/read.php?tid=1",
            "https://ngabbs.com.evil.example/read.php?tid=1",
            "https://evil.example/read.php?tid=1",
            "https://ngabbs.com:444/read.php?tid=1",
            "https://user:password@ngabbs.com/read.php?tid=1",
            "https://ngabbs.com/post.php?tid=1",
            "https://ngabbs.com/read.php?tid=0",
            "https://ngabbs.com/read.php?tid=-1",
            "https://ngabbs.com/read.php?tid=abc",
            "https://ngabbs.com/read.php",
            "https://ngabbs.com/read.php?tid=1&tid=2",
            "https://ngabbs.com/read.php?tid=1&page=2",
            "https://ngabbs.com/read.php?tid=1&pid=2",
            "https://ngabbs.com/read.php?tid=1&__act=delete",
            "https://ngabbs.com/read.php?tid=1#pid2",
            "javascript:alert(1)",
        ).forEach { assertNull(it, NgaTopicLinks.topicId(it)) }
    }
}
