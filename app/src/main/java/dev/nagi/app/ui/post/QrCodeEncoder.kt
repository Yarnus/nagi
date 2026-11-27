package dev.nagi.app.ui.post

/**
 * Pure-Kotlin ISO/IEC 18004 QR code matrix encoder (Byte mode, ECC Level L, Versions 1..5).
 * Supports payloads up to 106 UTF-8 bytes without external dependencies.
 */
internal class QrMatrix(
    val size: Int,
    val modules: BooleanArray,
) {
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QrMatrix) return false
        return size == other.size && modules.contentEquals(other.modules)
    }

    override fun hashCode(): Int = 31 * size + modules.contentHashCode()
}

internal object QrCodeEncoder {
    private data class VersionSpec(
        val version: Int,
        val size: Int,
        val dataCodewords: Int,
        val ecCodewords: Int,
        val alignmentCenters: IntArray,
    )

    // Single-block ECC Level L tables for QR Versions 1..5.
    private val versions = listOf(
        VersionSpec(version = 1, size = 21, dataCodewords = 19, ecCodewords = 7, alignmentCenters = intArrayOf()),
        VersionSpec(version = 2, size = 25, dataCodewords = 34, ecCodewords = 10, alignmentCenters = intArrayOf(6, 18)),
        VersionSpec(version = 3, size = 29, dataCodewords = 55, ecCodewords = 15, alignmentCenters = intArrayOf(6, 22)),
        VersionSpec(version = 4, size = 33, dataCodewords = 80, ecCodewords = 20, alignmentCenters = intArrayOf(6, 26)),
        VersionSpec(version = 5, size = 37, dataCodewords = 108, ecCodewords = 26, alignmentCenters = intArrayOf(6, 30)),
    )

    private val finderPenaltyA = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
    private val finderPenaltyB = booleanArrayOf(false, false, false, false, true, false, true, true, true, false, true)

    private val gfExp = IntArray(512)
    private val gfLog = IntArray(256)

    init {
        var v = 1
        for (i in 0 until 255) {
            gfExp[i] = v
            gfLog[v] = i
            v = v shl 1
            if (v and 0x100 != 0) v = v xor 0x11D
        }
        for (i in 255 until 512) {
            gfExp[i] = gfExp[i - 255]
        }
    }

    fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return gfExp[gfLog[a] + gfLog[b]]
    }

    private fun generatorPoly(ecCount: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until ecCount) {
            val factor = intArrayOf(1, gfExp[i])
            val next = IntArray(poly.size + 1)
            for (j in poly.indices) {
                for (k in factor.indices) {
                    next[j + k] = next[j + k] xor gfMul(poly[j], factor[k])
                }
            }
            poly = next
        }
        return poly
    }

    private fun reedSolomon(data: IntArray, ecCount: Int): IntArray {
        val gen = generatorPoly(ecCount)
        val remainder = IntArray(ecCount)
        for (byte in data) {
            val factor = byte xor remainder[0]
            System.arraycopy(remainder, 1, remainder, 0, ecCount - 1)
            remainder[ecCount - 1] = 0
            for (i in 0 until ecCount) {
                remainder[i] = remainder[i] xor gfMul(gen[i + 1], factor)
            }
        }
        return remainder
    }

    fun encode(content: String): QrMatrix? {
        if (content.isEmpty()) return null
        val payload = content.encodeToByteArray()
        val spec = versions.firstOrNull { payload.size <= (it.dataCodewords * 8 - 12) / 8 } ?: return null

        val dataBytes = encodeDataCodewords(payload, spec.dataCodewords)
        val ecBytes = reedSolomon(dataBytes, spec.ecCodewords)
        val allBytes = IntArray(dataBytes.size + ecBytes.size)
        System.arraycopy(dataBytes, 0, allBytes, 0, dataBytes.size)
        System.arraycopy(ecBytes, 0, allBytes, dataBytes.size, ecBytes.size)

        val size = spec.size
        val baseModules = BooleanArray(size * size)
        val isFunction = BooleanArray(size * size)

        placeFunctionPatterns(spec, baseModules, isFunction)
        placeDataBits(size, allBytes, baseModules, isFunction)

        var bestScore = Int.MAX_VALUE
        var bestModules = BooleanArray(0)
        for (mask in 0..7) {
            val candidate = baseModules.copyOf()
            applyMask(size, mask, candidate, isFunction)
            writeFormatInfo(size, mask, candidate)
            val score = penaltyScore(size, candidate)
            if (mask == 0 || score < bestScore) {
                bestScore = score
                bestModules = candidate
            }
        }
        return QrMatrix(size, bestModules)
    }

    private fun encodeDataCodewords(payload: ByteArray, capacityBytes: Int): IntArray {
        val bits = ArrayList<Int>(capacityBytes * 8)
        fun appendBits(value: Int, count: Int) {
            for (shift in (count - 1) downTo 0) {
                bits += (value ushr shift) and 1
            }
        }
        // Byte mode indicator (0100) + 8-bit byte length (Versions 1..9).
        appendBits(0b0100, 4)
        appendBits(payload.size, 8)
        for (b in payload) {
            appendBits(b.toInt() and 0xFF, 8)
        }
        val totalBits = capacityBytes * 8
        val terminatorBits = minOf(4, totalBits - bits.size)
        repeat(terminatorBits) { bits += 0 }
        while (bits.size % 8 != 0) bits += 0

        val out = IntArray(capacityBytes)
        val byteCountFromBits = bits.size / 8
        for (i in 0 until byteCountFromBits) {
            var v = 0
            for (bit in 0 until 8) {
                v = (v shl 1) or bits[i * 8 + bit]
            }
            out[i] = v
        }
        var padIndex = 0
        for (i in byteCountFromBits until capacityBytes) {
            out[i] = if (padIndex % 2 == 0) 0xEC else 0x11
            padIndex++
        }
        return out
    }

    private fun placeFunctionPatterns(spec: VersionSpec, modules: BooleanArray, isFunction: BooleanArray) {
        val size = spec.size
        fun setFunc(x: Int, y: Int, black: Boolean) {
            if (x in 0 until size && y in 0 until size) {
                val idx = y * size + x
                modules[idx] = black
                isFunction[idx] = true
            }
        }

        fun placeFinder(startX: Int, startY: Int) {
            for (dy in -1..7) {
                for (dx in -1..7) {
                    val x = startX + dx
                    val y = startY + dy
                    if (x !in 0 until size || y !in 0 until size) continue
                    val black = if (dx in 0..6 && dy in 0..6) {
                        dx == 0 || dx == 6 || dy == 0 || dy == 6 || (dx in 2..4 && dy in 2..4)
                    } else {
                        false // 1-module white separator
                    }
                    setFunc(x, y, black)
                }
            }
        }

        placeFinder(0, 0)
        placeFinder(size - 7, 0)
        placeFinder(0, size - 7)

        // Timing patterns
        for (i in 8 until size - 8) {
            val black = i % 2 == 0
            setFunc(i, 6, black)
            setFunc(6, i, black)
        }

        // Alignment patterns
        val centers = spec.alignmentCenters
        for (cy in centers) {
            for (cx in centers) {
                val overlapsTopLeft = cx <= 8 && cy <= 8
                val overlapsTopRight = cx >= size - 9 && cy <= 8
                val overlapsBottomLeft = cx <= 8 && cy >= size - 9
                if (overlapsTopLeft || overlapsTopRight || overlapsBottomLeft) continue
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val black = kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1
                        setFunc(cx + dx, cy + dy, black)
                    }
                }
            }
        }

        // Dark module
        setFunc(8, 4 * spec.version + 9, true)

        // Reserve format info modules so data bits skip them
        for (i in 0..8) {
            if (i != 6) {
                isFunction[8 * size + i] = true
                isFunction[i * size + 8] = true
            }
        }
        for (i in 0..7) {
            isFunction[8 * size + (size - 1 - i)] = true
        }
        for (i in 0..6) {
            isFunction[(size - 1 - i) * size + 8] = true
        }
    }

    private fun placeDataBits(size: Int, codewords: IntArray, modules: BooleanArray, isFunction: BooleanArray) {
        val totalBits = codewords.size * 8
        var bitIndex = 0
        var upward = true
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right--
            for (step in 0 until size) {
                val y = if (upward) size - 1 - step else step
                for (dx in 0..1) {
                    val x = right - dx
                    val idx = y * size + x
                    if (!isFunction[idx]) {
                        val bit = if (bitIndex < totalBits) {
                            (codewords[bitIndex ushr 3] ushr (7 - (bitIndex and 7))) and 1 != 0
                        } else {
                            false
                        }
                        modules[idx] = bit
                        bitIndex++
                    }
                }
            }
            upward = !upward
            right -= 2
        }
    }

    private fun applyMask(size: Int, mask: Int, modules: BooleanArray, isFunction: BooleanArray) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val idx = y * size + x
                if (isFunction[idx]) continue
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
                if (invert) modules[idx] = !modules[idx]
            }
        }
    }

    fun formatBits(mask: Int): Int {
        // ECC Level L = 0b01
        val data = (0b01 shl 3) or (mask and 7)
        var rem = data shl 10
        val poly = 0x537
        for (bit in 14 downTo 10) {
            if ((rem ushr bit) and 1 != 0) {
                rem = rem xor (poly shl (bit - 10))
            }
        }
        return ((data shl 10) or rem) xor 0x5412
    }

    private fun writeFormatInfo(size: Int, mask: Int, modules: BooleanArray) {
        val bits = formatBits(mask)
        fun bit(pos: Int): Boolean = ((bits ushr pos) and 1) != 0

        // Copy 1: around top-left finder
        for (i in 0..5) modules[8 * size + i] = bit(14 - i)
        modules[8 * size + 7] = bit(8)
        modules[8 * size + 8] = bit(7)
        modules[7 * size + 8] = bit(6)
        for (i in 0..5) modules[(5 - i) * size + 8] = bit(5 - i)

        // Copy 2: bottom-left (bits 14..8) and top-right (bits 7..0)
        for (i in 0..6) modules[(size - 1 - i) * size + 8] = bit(14 - i)
        for (i in 0..7) modules[8 * size + (size - 8 + i)] = bit(7 - i)
    }

    private fun penaltyScore(size: Int, modules: BooleanArray): Int {
        var score = 0
        // N1: 5+ consecutive modules of same color in rows and columns
        for (y in 0 until size) {
            var runColor = modules[y * size]
            var runLen = 1
            for (x in 1 until size) {
                val c = modules[y * size + x]
                if (c == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) score += 3 + (runLen - 5)
                    runColor = c
                    runLen = 1
                }
            }
            if (runLen >= 5) score += 3 + (runLen - 5)
        }
        for (x in 0 until size) {
            var runColor = modules[x]
            var runLen = 1
            for (y in 1 until size) {
                val c = modules[y * size + x]
                if (c == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) score += 3 + (runLen - 5)
                    runColor = c
                    runLen = 1
                }
            }
            if (runLen >= 5) score += 3 + (runLen - 5)
        }
        // N2: 2x2 blocks of same color
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = modules[y * size + x]
                if (c == modules[y * size + x + 1] &&
                    c == modules[(y + 1) * size + x] &&
                    c == modules[(y + 1) * size + x + 1]
                ) {
                    score += 3
                }
            }
        }
        // N3: finder-like 1:1:3:1:1 patterns with 4 white modules on either side
        for (y in 0 until size) {
            for (x in 0..size - 11) {
                var matchA = true
                var matchB = true
                for (k in 0 until 11) {
                    val v = modules[y * size + x + k]
                    if (v != finderPenaltyA[k]) matchA = false
                    if (v != finderPenaltyB[k]) matchB = false
                }
                if (matchA || matchB) score += 40
            }
        }
        for (x in 0 until size) {
            for (y in 0..size - 11) {
                var matchA = true
                var matchB = true
                for (k in 0 until 11) {
                    val v = modules[(y + k) * size + x]
                    if (v != finderPenaltyA[k]) matchA = false
                    if (v != finderPenaltyB[k]) matchB = false
                }
                if (matchA || matchB) score += 40
            }
        }
        // N4: dark module ratio deviation from 50%
        var darkCount = 0
        for (m in modules) if (m) darkCount++
        val total = size * size
        val percent = (darkCount * 100) / total
        val prev5 = kotlin.math.abs((percent / 5) * 5 - 50) / 5
        val next5 = kotlin.math.abs(((percent + 4) / 5) * 5 - 50) / 5
        score += minOf(prev5, next5) * 10
        return score
    }
}
