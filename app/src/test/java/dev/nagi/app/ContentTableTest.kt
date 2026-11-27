package dev.nagi.app

import androidx.compose.ui.graphics.Color
import dev.nagi.app.ui.post.TableCellSpec
import dev.nagi.app.ui.post.computeTableGrid
import dev.nagi.app.ui.post.resolveStyledTextColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentTableTest {
    @Test
    fun computeTableGridHandlesColspanAndRowspan() {
        val grid = computeTableGrid(
            listOf(
                listOf(
                    TableCellSpec(colSpan = 1, rowSpan = 2, widthPercent = 20),
                    TableCellSpec(colSpan = 2, rowSpan = 1, widthPercent = 80),
                ),
                listOf(
                    TableCellSpec(widthPercent = 30),
                    TableCellSpec(widthPercent = 50),
                ),
            ),
        )
        assertEquals(2, grid.rowCount)
        assertEquals(3, grid.colCount)
        assertEquals(4, grid.placements.size)

        val p00 = grid.placements[0]
        assertEquals(0, p00.gridRow)
        assertEquals(0, p00.gridCol)
        assertEquals(2, p00.rowSpan)

        val p01 = grid.placements[1]
        assertEquals(0, p01.gridRow)
        assertEquals(1, p01.gridCol)
        assertEquals(2, p01.colSpan)

        // Row 1 col 0 is occupied by p00's rowSpan=2, so row 1 cells start at col 1 and col 2.
        val p10 = grid.placements[2]
        assertEquals(1, p10.gridRow)
        assertEquals(1, p10.gridCol)

        val p11 = grid.placements[3]
        assertEquals(1, p11.gridRow)
        assertEquals(2, p11.gridCol)

        // Column widths ignore colSpan > 1 cells (80% on p01 is ignored for col 1).
        assertEquals(listOf(20, 30, 50), grid.colWidthPercents)
    }

    @Test
    fun computeTableGridTruncatesRowspanAtTableBottom() {
        val grid = computeTableGrid(
            listOf(
                listOf(TableCellSpec(rowSpan = 5), TableCellSpec()),
                listOf(TableCellSpec()),
            ),
        )
        assertEquals(2, grid.rowCount)
        assertEquals(2, grid.colCount)
        assertEquals(2, grid.placements[0].rowSpan)
        assertEquals(1, grid.placements[2].gridCol)
        assertEquals(listOf<Int?>(null, null), grid.colWidthPercents)
    }

    @Test
    fun computeTableGridHandlesMixedSpansAndColumnWidthMax() {
        val grid = computeTableGrid(
            listOf(
                listOf(TableCellSpec(widthPercent = 15), TableCellSpec(colSpan = 2, rowSpan = 2, widthPercent = 70)),
                listOf(TableCellSpec(widthPercent = 25)),
                listOf(TableCellSpec(), TableCellSpec(widthPercent = 40), TableCellSpec()),
            ),
        )
        assertEquals(3, grid.rowCount)
        assertEquals(3, grid.colCount)
        assertEquals(25, grid.colWidthPercents[0])
        assertEquals(40, grid.colWidthPercents[1])
        assertNull(grid.colWidthPercents[2])
    }

    @Test
    fun resolveStyledTextColorEnforcesContrastAndFallbacks() {
        // High contrast author color on white card background is kept.
        assertEquals(
            Color(0xFF112233),
            resolveStyledTextColor(
                authorTextColor = 0xFF112233L,
                authorBackground = null,
                effectiveBackground = Color.White,
                inheritedTextColor = null,
                defaultTextColor = Color.Black,
            ),
        )
        // Low contrast author color with bright author background falls back to Black.
        assertEquals(
            Color.Black,
            resolveStyledTextColor(
                authorTextColor = 0xFFEEEEEEL,
                authorBackground = Color.White,
                effectiveBackground = Color.White,
                inheritedTextColor = null,
                defaultTextColor = Color.Black,
            ),
        )
        // Low contrast author color with dark author background falls back to White.
        assertEquals(
            Color.White,
            resolveStyledTextColor(
                authorTextColor = 0xFF222222L,
                authorBackground = Color.Black,
                effectiveBackground = Color.Black,
                inheritedTextColor = null,
                defaultTextColor = Color.White,
            ),
        )
        // Low contrast author color without author background falls back to null (theme default).
        assertNull(
            resolveStyledTextColor(
                authorTextColor = 0xFFEEEEEEL,
                authorBackground = null,
                effectiveBackground = Color.White,
                inheritedTextColor = Color.Red,
                defaultTextColor = Color.Black,
            ),
        )
    }
}
