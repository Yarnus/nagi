package dev.nagi.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NgaExternalLinksTest {
    @Test fun parsesTopicWithOptionalPageAndPost() {
        NgaDomain.entries.forEach { domain ->
            assertEquals(
                NgaExternalTarget.Topic("45905669"),
                NgaExternalLinks.parse("${domain.baseUrl}read.php?tid=45905669"),
            )
        }
        assertEquals(
            NgaExternalTarget.Topic("45905669", postId = "812345", page = 3),
            NgaExternalLinks.parse("https://bbs.nga.cn/read.php?tid=45905669&pid=812345&page=3"),
        )
        assertEquals(
            NgaExternalTarget.Topic("45905669", postId = "812345", page = 1),
            NgaExternalLinks.parse("http://bbs.nga.cn/read.php?tid=45905669#pid812345Anchor"),
        )
    }

    @Test fun parsesForumAndCollection() {
        assertEquals(
            NgaExternalTarget.Forum(forumId = "-7", collectionId = null),
            NgaExternalLinks.parse("https://bbs.nga.cn/thread.php?fid=-7"),
        )
        assertEquals(
            NgaExternalTarget.Forum(forumId = "0", collectionId = "99"),
            NgaExternalLinks.parse("https://ngabbs.com/thread.php?stid=99"),
        )
        assertEquals(
            NgaExternalTarget.Forum(forumId = "-7", collectionId = "99"),
            NgaExternalLinks.parse("https://bbs.nga.cn/thread.php?fid=-7&stid=99"),
        )
    }

    @Test fun parsesUserProfileLinks() {
        assertEquals(
            NgaExternalTarget.User("7"),
            NgaExternalLinks.parse("https://bbs.nga.cn/nuke.php?__lib=ucp&__act=get&uid=7"),
        )
        assertEquals(
            NgaExternalTarget.User("42"),
            NgaExternalLinks.parse("https://bbs.nga.cn/nuke.php?func=ucp&uid=42"),
        )
    }

    @Test fun rejectsUntrustedOrUnsupportedUrls() {
        listOf(
            "https://bbs.nga.cn.evil.test/read.php?tid=1",
            "https://user:pass@bbs.nga.cn/read.php?tid=1",
            "https://bbs.nga.cn:8443/read.php?tid=1",
            "http://bbs.nga.cn:8080/read.php?tid=1",
            "ftp://bbs.nga.cn/read.php?tid=1",
            "https://bbs.nga.cn/read.php?tid=0",
            "https://bbs.nga.cn/read.php?tid=1&page=0",
            "https://bbs.nga.cn/read.php?tid=1&pid=abc",
            "https://bbs.nga.cn/read.php?tid=1&action=modify",
            "https://bbs.nga.cn/read.php?tid=1&authorid=7",
            "https://bbs.nga.cn/thread.php?fid=0",
            "https://bbs.nga.cn/thread.php?fid=-7&key=search",
            "https://bbs.nga.cn/nuke.php?__lib=ucp&__act=get&uid=-1",
            "https://bbs.nga.cn/nuke.php?__lib=user_option&__act=set",
            "https://bbs.nga.cn/post.php?tid=1",
        ).forEach { assertNull(it, NgaExternalLinks.parse(it)) }
    }

    @Test fun extractsTrustedNgaLinkFromSharedText() {
        val shared = "看看这个帖子：https://bbs.nga.cn/read.php?tid=45905669&page=2（来自 NGA）"
        val extracted = NgaExternalLinks.extractFromText(shared)
        assertEquals(
            ExtractedExternalLink(
                url = "https://bbs.nga.cn/read.php?tid=45905669&page=2",
                target = NgaExternalTarget.Topic("45905669", postId = null, page = 2),
            ),
            extracted,
        )

        val withUnrelatedFirst = "参考 https://example.com/a 和 https://ngabbs.com/thread.php?fid=-7。"
        assertEquals(
            NgaExternalTarget.Forum("-7", null),
            NgaExternalLinks.extractFromText(withUnrelatedFirst)?.target,
        )
        assertEquals(
            NgaExternalTarget.Topic("45905669", page = 1),
            NgaExternalLinks.parse("https://bbs.nga.cn/read.php?tid=45905669&page=e"),
        )
        assertNull(NgaExternalLinks.extractFromText("普通文字 https://evil.example/read.php?tid=1"))
    }
}
