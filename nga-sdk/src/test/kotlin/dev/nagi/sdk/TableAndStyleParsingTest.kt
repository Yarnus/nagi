package dev.nagi.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableAndStyleParsingTest {
    @Test
    fun bbcodeTableParsesSpansWidthAndClampsOutOfRange() {
        val table = ContentParser.parse(
            """[table][tr][td colspan=2 rowspan="3" width='25%']A[/td][td30]B[/td][td colspan=99 rowspan=0 width=150]C[/td][td120 width=bad]D[/td][/tr][/table]""",
        ).single() as ContentBlock.Table

        val row = table.rows.single()
        assertEquals(4, row.size)

        assertEquals(2, row[0].colSpan)
        assertEquals(3, row[0].rowSpan)
        assertEquals(25, row[0].widthPercent)

        assertEquals(1, row[1].colSpan)
        assertEquals(1, row[1].rowSpan)
        assertEquals(30, row[1].widthPercent)

        assertEquals(20, row[2].colSpan)
        assertEquals(1, row[2].rowSpan)
        assertEquals(100, row[2].widthPercent)

        assertEquals(1, row[3].colSpan)
        assertEquals(1, row[3].rowSpan)
        assertNull(row[3].widthPercent)
    }

    @Test
    fun htmlTableParsesSpansAndNumericOrPercentWidthsOnly() {
        val table = ContentParser.parse(
            """<table><tr><th colspan="3" rowspan="2" width="40%">H1</th><td width="120px">C1</td><td width="200">C2</td><td colspan="50">C3</td></tr></table>""",
        ).single() as ContentBlock.Table

        val row = table.rows.single()
        assertEquals(4, row.size)

        assertEquals(3, row[0].colSpan)
        assertEquals(2, row[0].rowSpan)
        assertEquals(40, row[0].widthPercent)

        assertEquals(1, row[1].colSpan)
        assertNull(row[1].widthPercent)

        assertEquals(100, row[2].widthPercent)

        assertEquals(20, row[3].colSpan)
    }

    @Test
    fun nestedStyleAndFixsizeParseSafeSubsetAndIgnorePositioningOrDybg() {
        val blocks = ContentParser.parse(
            """
            [fixsize width 90 180 height 19.2 background #93ce26 #93ce26]
                [style width 153.8 height 100% right -76.9 top 0 dybg 100%;0%;0%;0%;0%;./mon_xxx.png]
                    [style left 0.3 top 9.6 font 1.5 color #fff align center]版头标题[/style]
                [/style]
            [/fixsize]
            """.trimIndent(),
        )
        val outer = blocks.single() as ContentBlock.Styled
        assertEquals(0xFF93CE26L, outer.style.background)
        assertNull(outer.style.textColor)
        assertNull(outer.style.fontScale)
        assertNull(outer.style.alignment)

        // Middle [style] had only ignored positioning/dybg attributes, so it unwraps directly to inner [style].
        val inner = outer.children.single() as ContentBlock.Styled
        assertNull(inner.style.background)
        assertEquals(0xFFFFFFFFL, inner.style.textColor)
        assertEquals(1.5f, inner.style.fontScale)
        assertEquals(ContentAlignment.CENTER, inner.style.alignment)
        assertEquals(listOf(ContentBlock.Text("版头标题")), inner.children)
    }

    @Test
    fun styleSupportsNamedColorsThreeDigitAndSixDigitHexAndFontBounds() {
        val styled = ContentParser.parse(
            "[style background #f4f7f9 color crimson font 0.5 align right]ok[/style]",
        ).single() as ContentBlock.Styled
        assertEquals(0xFFF4F7F9L, styled.style.background)
        assertEquals(0xFFC2185BL, styled.style.textColor)
        assertEquals(0.5f, styled.style.fontScale)
        assertEquals(ContentAlignment.END, styled.style.alignment)

        // Out-of-bounds font (0.4, 3.0), signed invalid hex, and unknown attributes unwrap without creating Styled.
        val unwrapped = ContentParser.parse(
            "[style font 3.0 width 50% height 10]plain[/style][style background #-12345 color #+12345]signed[/style]",
        )
        assertEquals(listOf(ContentBlock.Text("plain"), ContentBlock.Text("signed")), unwrapped)
    }

    @Test
    fun styleInsideCodeOrPreIsNotInterpreted() {
        val bbCode = ContentParser.parse("[code][style color red]literal[/style][/code]").single() as ContentBlock.Code
        assertEquals("[style color red]literal[/style]", bbCode.value)

        val pre = ContentParser.parse("<pre>[fixsize background #123456]literal[/fixsize]</pre>").single() as ContentBlock.Code
        assertEquals("[fixsize background #123456]literal[/fixsize]", pre.value)

        // Randomblock still strips its tags cleanly.
        val random = ContentParser.parse("[randomblock]inside[/randomblock]")
        assertEquals(listOf(ContentBlock.Text("inside")), random)
    }
}
