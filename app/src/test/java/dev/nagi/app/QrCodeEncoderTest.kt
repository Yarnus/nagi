package dev.nagi.app

import dev.nagi.app.ui.post.QrCodeEncoder
import dev.nagi.app.ui.post.QrMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeEncoderTest {
    @Test
    fun encodesStandardNgaTopicUrlIntoValidVersion3Matrix() {
        val url = "https://bbs.nga.cn/read.php?tid=47841712"
        val matrix = QrCodeEncoder.encode(url)
        assertNotNull(matrix)
        matrix!!
        // 40 UTF-8 bytes fits in QR Version 3 (29x29, max 53 bytes at ECC L).
        assertEquals(29, matrix.size)

        // Verify all three 7x7 finder patterns and their 1-module white separators.
        assertFinderAndSeparator(matrix, 0, 0)
        assertFinderAndSeparator(matrix, matrix.size - 7, 0)
        assertFinderAndSeparator(matrix, 0, matrix.size - 7)

        // Verify timing patterns along row 6 and col 6.
        for (i in 8 until matrix.size - 8) {
            val expected = i % 2 == 0
            assertEquals("Horizontal timing mismatch at x=$i", expected, matrix[i, 6])
            assertEquals("Vertical timing mismatch at y=$i", expected, matrix[6, i])
        }

        // Verify Version 3 dark module at (8, 4*3 + 9 = 21).
        assertTrue(matrix[8, 21])

        // Verify Version 3 alignment pattern centered at (22, 22).
        for (dy in -2..2) {
            for (dx in -2..2) {
                val expected = kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1
                assertEquals("Alignment mismatch at (${22 + dx}, ${22 + dy})", expected, matrix[22 + dx, 22 + dy])
            }
        }

        // Verify both format info copies match and encode ECC Level L (0b01) with valid BCH(15, 5).
        val copy1 = readFormatCopy1(matrix)
        val copy2 = readFormatCopy2(matrix)
        assertEquals(copy1, copy2)
        val unmaskedFormat = copy1 xor 0x5412
        assertEquals(0, bchSyndrome(unmaskedFormat))
        val eccLevel = unmaskedFormat ushr 13
        val maskPattern = (unmaskedFormat ushr 10) and 0b111
        assertEquals(0b01, eccLevel)
        assertEquals(QrCodeEncoder.formatBits(maskPattern), copy1)

        // Extract codewords back from the matrix and verify GF(256) Reed-Solomon syndromes are all zero.
        val codewords = extractCodewords(matrix, maskPattern, totalCodewords = 70)
        assertReedSolomonZeroSyndrome(codewords, ecCount = 15)

        // Verify decoded byte payload matches input URL.
        assertEquals(0b0100, codewords[0] ushr 4)
        val charCount = ((codewords[0] and 0x0F) shl 4) or (codewords[1] ushr 4)
        assertEquals(url.length, charCount)
        val decodedBytes = ByteArray(charCount) { idx ->
            (((codewords[idx + 1] and 0x0F) shl 4) or (codewords[idx + 2] ushr 4)).toByte()
        }
        assertEquals(url, decodedBytes.decodeToString())
    }

    @Test
    fun returnsNullForEmptyOrOversizedPayloads() {
        assertNull(QrCodeEncoder.encode(""))
        assertNull(QrCodeEncoder.encode("x".repeat(107)))
        assertNotNull(QrCodeEncoder.encode("x".repeat(106)))
    }

    @Test
    fun encodesVersions1Through5AndUtf8PayloadsWithZeroSyndromes() {
        data class Case(
            val payload: String,
            val size: Int,
            val dataCodewords: Int,
            val ecCodewords: Int,
            val alignmentCenter: Int?,
        )
        val cases = listOf(
            Case("https://nga.cn/1", size = 21, dataCodewords = 19, ecCodewords = 7, alignmentCenter = null),
            Case("https://bbs.nga.cn/read.php", size = 25, dataCodewords = 34, ecCodewords = 10, alignmentCenter = 18),
            Case("暖纸主题测试 · tid=47841712 · 多字节UTF8", size = 29, dataCodewords = 55, ecCodewords = 15, alignmentCenter = 22),
            Case("a".repeat(78), size = 33, dataCodewords = 80, ecCodewords = 20, alignmentCenter = 26),
            Case("x".repeat(106), size = 37, dataCodewords = 108, ecCodewords = 26, alignmentCenter = 30),
        )
        for (case in cases) {
            val matrix = QrCodeEncoder.encode(case.payload)
            assertNotNull("Expected matrix for size=${case.size}", matrix)
            matrix!!
            assertEquals(case.size, matrix.size)
            assertFinderAndSeparator(matrix, 0, 0)
            assertFinderAndSeparator(matrix, matrix.size - 7, 0)
            assertFinderAndSeparator(matrix, 0, matrix.size - 7)
            assertTrue("Dark module missing for size=${case.size}", matrix[8, case.size - 8])
            if (case.alignmentCenter != null) {
                val c = case.alignmentCenter
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val expected = kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1
                        assertEquals("Alignment mismatch at (${c + dx}, ${c + dy})", expected, matrix[c + dx, c + dy])
                    }
                }
            }
            val copy1 = readFormatCopy1(matrix)
            assertEquals(copy1, readFormatCopy2(matrix))
            val unmasked = copy1 xor 0x5412
            assertEquals(0, bchSyndrome(unmasked))
            val mask = (unmasked ushr 10) and 0b111
            val codewords = extractCodewords(
                matrix = matrix,
                mask = mask,
                totalCodewords = case.dataCodewords + case.ecCodewords,
                alignmentCenter = case.alignmentCenter,
            )
            assertReedSolomonZeroSyndrome(codewords, ecCount = case.ecCodewords)
            val byteLen = ((codewords[0] and 0x0F) shl 4) or (codewords[1] ushr 4)
            val expectedBytes = case.payload.encodeToByteArray()
            assertEquals(expectedBytes.size, byteLen)
            val decoded = ByteArray(byteLen) { idx ->
                (((codewords[idx + 1] and 0x0F) shl 4) or (codewords[idx + 2] ushr 4)).toByte()
            }
            assertEquals(case.payload, decoded.decodeToString())
        }
    }

    private fun assertFinderAndSeparator(matrix: QrMatrix, startX: Int, startY: Int) {
        for (dy in -1..7) {
            for (dx in -1..7) {
                val x = startX + dx
                val y = startY + dy
                if (x !in 0 until matrix.size || y !in 0 until matrix.size) continue
                val expected = if (dx in 0..6 && dy in 0..6) {
                    dx == 0 || dx == 6 || dy == 0 || dy == 6 || (dx in 2..4 && dy in 2..4)
                } else {
                    false
                }
                assertEquals("Finder mismatch at ($x, $y)", expected, matrix[x, y])
            }
        }
    }

    private fun readFormatCopy1(matrix: QrMatrix): Int {
        var bits = 0
        for (i in 0..5) bits = (bits shl 1) or if (matrix[i, 8]) 1 else 0
        bits = (bits shl 1) or if (matrix[7, 8]) 1 else 0
        bits = (bits shl 1) or if (matrix[8, 8]) 1 else 0
        bits = (bits shl 1) or if (matrix[8, 7]) 1 else 0
        for (i in 5 downTo 0) bits = (bits shl 1) or if (matrix[8, i]) 1 else 0
        return bits
    }

    private fun readFormatCopy2(matrix: QrMatrix): Int {
        val size = matrix.size
        var bits = 0
        for (i in 0..6) bits = (bits shl 1) or if (matrix[8, size - 1 - i]) 1 else 0
        for (i in 0..7) bits = (bits shl 1) or if (matrix[size - 8 + i, 8]) 1 else 0
        return bits
    }

    private fun bchSyndrome(value: Int): Int {
        var rem = value
        val poly = 0x537
        for (bit in 14 downTo 10) {
            if ((rem ushr bit) and 1 != 0) rem = rem xor (poly shl (bit - 10))
        }
        return rem
    }

    private fun extractCodewords(
        matrix: QrMatrix,
        mask: Int,
        totalCodewords: Int,
        alignmentCenter: Int? = 22,
    ): IntArray {
        val size = matrix.size
        fun isFunction(x: Int, y: Int): Boolean {
            if (x <= 8 && y <= 8) return true
            if (x >= size - 8 && y <= 8) return true
            if (x <= 8 && y >= size - 8) return true
            if (x == 6 || y == 6) return true
            if (alignmentCenter != null && x in (alignmentCenter - 2)..(alignmentCenter + 2) &&
                y in (alignmentCenter - 2)..(alignmentCenter + 2)
            ) {
                return true
            }
            return false
        }

        val bits = IntArray(totalCodewords * 8)
        var bitIndex = 0
        var upward = true
        var right = size - 1
        while (right >= 1 && bitIndex < bits.size) {
            if (right == 6) right--
            for (step in 0 until size) {
                val y = if (upward) size - 1 - step else step
                for (dx in 0..1) {
                    val x = right - dx
                    if (!isFunction(x, y) && bitIndex < bits.size) {
                        val raw = matrix[x, y]
                        val invert = when (mask) {
                            0 -> (y + x) % 2 == 0
                            1 -> y % 2 == 0
                            2 -> x % 3 == 0
                            3 -> (y + x) % 3 == 0
                            4 -> ((y / 2) + (x / 3)) % 2 == 0
                            5 -> ((y * x) % 2) + ((y * x) % 3) == 0
                            6 -> (((y * x) % 2) + ((y * x) % 3)) % 2 == 0
                            else -> (((y + x) % 2) + ((y * x) % 3)) % 2 == 0
                        }
                        bits[bitIndex++] = if (raw xor invert) 1 else 0
                    }
                }
            }
            upward = !upward
            right -= 2
        }

        return IntArray(totalCodewords) { byteIdx ->
            var v = 0
            for (b in 0 until 8) v = (v shl 1) or bits[byteIdx * 8 + b]
            v
        }
    }

    private fun assertReedSolomonZeroSyndrome(codewords: IntArray, ecCount: Int) {
        val gfExp = IntArray(255)
        var v = 1
        for (i in 0 until 255) {
            gfExp[i] = v
            v = v shl 1
            if (v and 0x100 != 0) v = v xor 0x11D
        }
        for (i in 0 until ecCount) {
            var eval = 0
            val x = gfExp[i]
            for (cw in codewords) {
                eval = QrCodeEncoder.gfMul(eval, x) xor cw
            }
            assertEquals("Non-zero RS syndrome at root $i", 0, eval)
        }
        assertFalse(codewords.all { it == 0 })
    }
}
