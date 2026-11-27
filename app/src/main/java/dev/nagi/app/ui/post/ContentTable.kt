package dev.nagi.app.ui.post

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.sdk.ContentBlock
import kotlin.math.max

internal data class TableCellSpec(
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    val widthPercent: Int? = null,
)

internal data class TablePlacement(
    val rowIndex: Int,
    val cellIndex: Int,
    val gridRow: Int,
    val gridCol: Int,
    val colSpan: Int,
    val rowSpan: Int,
    val widthPercent: Int?,
)

internal data class TableGrid(
    val rowCount: Int,
    val colCount: Int,
    val placements: List<TablePlacement>,
    val colWidthPercents: List<Int?>,
)

internal fun computeTableGrid(rows: List<List<TableCellSpec>>): TableGrid {
    val rowCount = rows.size
    if (rowCount == 0) return TableGrid(0, 0, emptyList(), emptyList())

    val occupied = List(rowCount) { mutableListOf<Boolean>() }
    fun isOccupied(r: Int, c: Int): Boolean = c < occupied[r].size && occupied[r][c]
    fun mark(r: Int, c: Int) {
        val rowList = occupied[r]
        while (rowList.size <= c) rowList.add(false)
        rowList[c] = true
    }

    val placements = mutableListOf<TablePlacement>()
    for (r in 0 until rowCount) {
        var col = 0
        for ((cellIndex, cell) in rows[r].withIndex()) {
            val cSpan = cell.colSpan.coerceIn(1, 20)
            val rSpan = cell.rowSpan.coerceIn(1, (rowCount - r).coerceAtLeast(1))
            while ((0 until cSpan).any { offset -> isOccupied(r, col + offset) }) {
                col++
            }
            for (rr in r until (r + rSpan)) {
                for (cc in col until (col + cSpan)) {
                    mark(rr, cc)
                }
            }
            placements += TablePlacement(
                rowIndex = r,
                cellIndex = cellIndex,
                gridRow = r,
                gridCol = col,
                colSpan = cSpan,
                rowSpan = rSpan,
                widthPercent = cell.widthPercent,
            )
            col += cSpan
        }
    }

    val colCount = placements.maxOfOrNull { it.gridCol + it.colSpan } ?: 0
    val colWidthPercents = List(colCount) { c ->
        placements
            .filter { it.gridCol == c && it.colSpan == 1 }
            .mapNotNull { it.widthPercent }
            .maxOrNull()
    }
    return TableGrid(
        rowCount = rowCount,
        colCount = colCount,
        placements = placements,
        colWidthPercents = colWidthPercents,
    )
}

private class CellRectsHolder {
    var rects: List<IntRect> = emptyList()
}

@Composable
internal fun ContentTable(
    table: ContentBlock.Table,
    blockShape: Shape,
    cellContent: @Composable (List<ContentBlock>) -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val grid = remember(table) {
        computeTableGrid(
            table.rows.map { row ->
                row.map { cell -> TableCellSpec(cell.colSpan, cell.rowSpan, cell.widthPercent) }
            },
        )
    }

    FlatCard(
        modifier = Modifier.fillMaxWidth(),
        shape = blockShape,
        containerColor = colors.insetContainer,
        borderColor = colors.cardBorder,
    ) {
        if (grid.placements.isEmpty()) return@FlatCard
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val availableWidthDp = if (maxWidth == Dp.Infinity) 320f else maxWidth.value
            val referenceWidthDp = max(availableWidthDp, 320f)
            val colWidthsDp = remember(grid.colWidthPercents, referenceWidthDp) {
                grid.colWidthPercents.map { pct ->
                    if (pct == null) 160.dp
                    else (referenceWidthDp * pct / 100f).coerceIn(64f, 480f).dp
                }
            }
            val rectsHolder = remember { CellRectsHolder() }
            val borderColor = colors.cardBorder

            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Layout(
                    content = {
                        grid.placements.forEach { placement ->
                            Box(Modifier.padding(Space.sm)) {
                                cellContent(table.rows[placement.rowIndex][placement.cellIndex].content)
                            }
                        }
                    },
                    modifier = Modifier.drawWithContent {
                        drawContent()
                        val strokeWidth = 0.5.dp.toPx()
                        rectsHolder.rects.forEach { rect ->
                            drawRect(
                                color = borderColor,
                                topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                                size = Size(rect.width.toFloat(), rect.height.toFloat()),
                                style = Stroke(width = strokeWidth),
                            )
                        }
                    },
                ) { measurables, _ ->
                    val colWidthsPx = IntArray(grid.colCount) { colWidthsDp[it].roundToPx() }
                    val colX = IntArray(grid.colCount + 1)
                    for (c in 0 until grid.colCount) {
                        colX[c + 1] = colX[c] + colWidthsPx[c]
                    }

                    val placeables = grid.placements.mapIndexed { index, placement ->
                        val cellWidth = colX[placement.gridCol + placement.colSpan] - colX[placement.gridCol]
                        measurables[index].measure(
                            Constraints(
                                minWidth = cellWidth,
                                maxWidth = cellWidth,
                                minHeight = 0,
                                maxHeight = Constraints.Infinity,
                            ),
                        )
                    }

                    val rowHeights = IntArray(grid.rowCount)
                    grid.placements.forEachIndexed { index, placement ->
                        if (placement.rowSpan == 1) {
                            rowHeights[placement.gridRow] = maxOf(rowHeights[placement.gridRow], placeables[index].height)
                        }
                    }
                    grid.placements.forEachIndexed { index, placement ->
                        if (placement.rowSpan > 1) {
                            val endRow = (placement.gridRow + placement.rowSpan).coerceAtMost(grid.rowCount)
                            var spanHeight = 0
                            for (r in placement.gridRow until endRow) spanHeight += rowHeights[r]
                            if (placeables[index].height > spanHeight) {
                                rowHeights[endRow - 1] += placeables[index].height - spanHeight
                            }
                        }
                    }

                    val rowY = IntArray(grid.rowCount + 1)
                    for (r in 0 until grid.rowCount) {
                        rowY[r + 1] = rowY[r] + rowHeights[r]
                    }

                    val rects = ArrayList<IntRect>(grid.placements.size)
                    grid.placements.forEach { placement ->
                        val left = colX[placement.gridCol]
                        val right = colX[placement.gridCol + placement.colSpan]
                        val top = rowY[placement.gridRow]
                        val bottom = rowY[(placement.gridRow + placement.rowSpan).coerceAtMost(grid.rowCount)]
                        rects += IntRect(left, top, right, bottom)
                    }
                    rectsHolder.rects = rects

                    layout(colX[grid.colCount], rowY[grid.rowCount]) {
                        grid.placements.forEachIndexed { index, placement ->
                            placeables[index].placeRelative(
                                x = colX[placement.gridCol],
                                y = rowY[placement.gridRow],
                            )
                        }
                    }
                }
            }
        }
    }
}
