package dev.nagi.sdk

import org.junit.Assert.*
import org.junit.Test

class NgaUserLinksTest {
    @Test fun onlyExactTrustedPublicUserLinksAreRecognized() {
        val path = "/nuke.php?__lib=ucp&__act=get&uid=7"
        assertEquals("7", NgaUserLinks.userId("https://bbs.nga.cn$path"))
        for (url in listOf(
            "http://bbs.nga.cn$path", "https://bbs.nga.cn.evil.test$path", "https://user@bbs.nga.cn$path",
            "https://bbs.nga.cn:8443$path", "https://bbs.nga.cn$path&uid=8", "https://bbs.nga.cn$path#x",
            "https://bbs.nga.cn$path&action=delete", "https://bbs.nga.cn${path.replace("uid=7", "uid=-1")}",
        )) assertNull(url, NgaUserLinks.userId(url))
    }
}
