package dev.nagi.app

import dev.nagi.app.ui.post.imageFallbackCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageFallbackTest {
    @Test
    fun repairsMissingAttachmentsAndAddsMirrorHost() {
        val candidates = imageFallbackCandidates("https://img.nga.cn/mon_202611/20/-7Q46-kgwsK1xT3cSlc-sg.jpeg")
        assertEquals(
            listOf(
                "https://img.nga.cn/mon_202611/20/-7Q46-kgwsK1xT3cSlc-sg.jpeg",
                "https://img.nga.cn/attachments/mon_202611/20/-7Q46-kgwsK1xT3cSlc-sg.jpeg",
                "https://img8.nga.cn/mon_202611/20/-7Q46-kgwsK1xT3cSlc-sg.jpeg",
                "https://img8.nga.cn/attachments/mon_202611/20/-7Q46-kgwsK1xT3cSlc-sg.jpeg",
            ),
            candidates,
        )
    }

    @Test
    fun stripsThumbnailSuffixAndMirrorsHostWithoutDuplicates() {
        val candidates = imageFallbackCandidates("https://img.nga.cn/attachments/mon_202611/20/sample.png.thumb.jpg")
        assertEquals(
            listOf(
                "https://img.nga.cn/attachments/mon_202611/20/sample.png.thumb.jpg",
                "https://img.nga.cn/attachments/mon_202611/20/sample.png",
                "https://img8.nga.cn/attachments/mon_202611/20/sample.png.thumb.jpg",
                "https://img8.nga.cn/attachments/mon_202611/20/sample.png",
            ),
            candidates,
        )
        assertEquals(candidates.size, candidates.toSet().size)
    }

    @Test
    fun nonNgaHostKeepsSingleCandidate() {
        val url = "https://example.com/pic.png"
        assertEquals(listOf(url), imageFallbackCandidates(url))
        assertTrue(imageFallbackCandidates("").isEmpty())
    }
}
