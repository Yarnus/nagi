package dev.nagi.sdk

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ForumIconTest {
    private fun metadata() = JSONObject("""{
        "f":" -7,?776,843,?186", "s":" 99,?665",
        "f_px_l":"https://img4.nga.cn/ngabbs/nga_classic/f/app/", "f_sx_l":".png",
        "s_px_l":"https://img4.nga.cn/ngabbs/nga_classic/f/app/", "s_sx_l":".png"
    }""")

    private fun parse(icons: JSONObject? = metadata()): List<Forum> {
        val root = JSONObject("""{"data":{"0":{"name":"Category","groups":[{"name":"Group","forums":[
            {"fid":-7,"name":"Forum","icon":""},
            {"fid":843,"name":"News"},
            {"fid":-7,"stid":99,"name":"Collection"},
            {"fid":-7,"stid":100,"name":"Missing collection"},
            {"fid":999,"name":"Missing forum"}
        ]}]}}}""")
        if (icons != null) root.put("other", JSONObject().put("forum_icon_list", icons))
        return NgaParser.forums(root.toString())
    }

    @Test fun resolvesOfficialForumAndCollectionIconsWithVersions() {
        val forums = parse()
        val base = "https://img4.nga.cn/ngabbs/nga_classic/f/app/"
        assertEquals("${base}-7.png?776", forums[0].iconUrl)
        assertEquals("${base}843.png?186", forums[1].iconUrl)
        assertEquals("${base}99.png?665", forums[2].iconUrl)
        assertNull(forums[3].iconUrl)
        assertNull(forums[4].iconUrl)
    }

    @Test fun missingMetadataDoesNotBreakDirectory() {
        assertEquals(5, parse(null).size)
        assertTrue(parse(null).all { it.iconUrl == null })
    }

    @Test fun rejectsUnsafePrefixesAndSuffixesWithoutDroppingForums() {
        listOf(
            "http://img4.nga.cn/icons/", "https://evil.example/icons/",
            "https://img4.nga.cn.evil.example/", "https://user:secret@img4.nga.cn/",
            "https://img4.nga.cn:8443/", "https://img4.nga.cn/icons/?redirect=",
            "https://img4.nga.cn/icons/#fragment", "https://img4.nga.cn/icons",
        ).forEach { prefix ->
            val forums = parse(metadata().put("f_px_l", prefix))
            assertEquals(5, forums.size)
            assertNull(prefix, forums[0].iconUrl)
        }
        assertNull(parse(metadata().put("f_sx_l", ".png?redirect=https://evil.example"))[0].iconUrl)
    }

    @Test fun malformedIndexEntriesStayEmptyAndValidEntriesStillResolve() {
        val forums = parse(metadata().put("f", " -7,?bad,843,?186,../escape,?1,999"))
        assertNull(forums[0].iconUrl)
        assertNotNull(forums[1].iconUrl)
        assertNull(forums[4].iconUrl)
    }
}
