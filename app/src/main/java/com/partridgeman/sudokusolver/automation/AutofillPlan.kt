package com.partridgeman.sudokusolver.automation

import com.partridgeman.sudokusolver.model.SudokuBoard
import com.partridgeman.sudokusolver.recognition.CellReading
import com.partridgeman.sudokusolver.recognition.RecognitionPolicy
import com.partridgeman.sudokusolver.solver.SolveResult
import com.partridgeman.sudokusolver.solver.SudokuSolver
import com.partridgeman.sudokusolver.vision.BoardGeometry
import com.partridgeman.sudokusolver.vision.ImagePoint
import com.partridgeman.sudokusolver.vision.ImageRect
import kotlin.math.abs

data class NumberTarget(val digit: Int, val bounds: ImageRect, val confidence: Double)
data class FillEntry(val index: Int, val digit: Int)

object KeypadDetector {
    fun find(candidates: List<NumberTarget>, board: ImageRect): Map<Int, NumberTarget>? {
        val filtered = candidates.filter { it.digit in 1..9 && it.confidence.isFinite() && it.confidence >= 0.90 &&
            !intersects(it.bounds, board) && it.bounds.height >= board.height / 9 * 0.25 }
        // Require one unambiguous target for each label. Duplicate counters/toolbars fail closed.
        val groups = filtered.groupBy { it.digit }
        if (groups.keys != (1..9).toSet() || groups.values.any { it.size != 1 }) return null
        val keys = (1..9).map { groups.getValue(it).single() }
        val medianHeight = keys.map { it.bounds.height }.sorted()[4]
        if (keys.any { it.bounds.height !in medianHeight * 0.55..medianHeight * 1.8 }) return null
        for (a in keys.indices) for (b in a + 1 until keys.size) if (intersects(keys[a].bounds, keys[b].bounds)) return null
        val bounds = ImageRect(keys.minOf { it.bounds.left }, keys.minOf { it.bounds.top }, keys.maxOf { it.bounds.right }, keys.maxOf { it.bounds.bottom })
        if (intersects(bounds, board) || bounds.width > board.width * 1.5 || bounds.height > board.height * 0.65) return null
        val rows = mutableListOf<MutableList<NumberTarget>>()
        for (key in keys.sortedBy { it.bounds.center.y }) {
            val row = rows.firstOrNull { abs(it.first().bounds.center.y - key.bounds.center.y) <= medianHeight * 0.6 }
            if (row == null) rows.add(mutableListOf(key)) else row.add(key)
        }
        if (rows.size !in 1..3 || rows.any { it.size < 3 }) return null
        if (rows.flatMap { it.sortedBy { key -> key.bounds.center.x } }.map { it.digit } != (1..9).toList()) return null
        for (row in rows) {
            val gaps = row.sortedBy { it.bounds.center.x }.zipWithNext().map { (a, b) -> b.bounds.center.x - a.bounds.center.x }
            val mean = gaps.average()
            if (gaps.any { abs(it / mean - 1) > 0.35 }) return null
        }
        return keys.associateBy { it.digit }
    }

    fun intersects(a: ImageRect, b: ImageRect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
}

class AutofillPlan private constructor(val original: SudokuBoard, val solution: SudokuBoard, val geometry: BoardGeometry) {
    val entries: List<FillEntry> = java.util.Collections.unmodifiableList(original.cells.mapIndexedNotNull { index, value ->
        if (value == 0) FillEntry(index, solution.cells[index]) else null
    })

    fun expected(completed: Int): SudokuBoard {
        require(completed in 0..entries.size)
        val cells = original.cells.toMutableList()
        entries.take(completed).forEach { cells[it.index] = it.digit }
        return SudokuBoard(cells)
    }

    fun matches(board: SudokuBoard, current: BoardGeometry, completed: Int): Boolean {
        if (board != expected(completed)) return false
        val tolerance = minOf(geometry.bounds.width, geometry.bounds.height) / 9 * 0.06
        return geometry.horizontal.zip(current.horizontal).all { (a, b) -> abs(a - b) <= tolerance } &&
            geometry.vertical.zip(current.vertical).all { (a, b) -> abs(a - b) <= tolerance }
    }

    fun cellTarget(entry: FillEntry): ImagePoint {
        require(entry in entries && original.cells[entry.index] == 0 && solution.cells[entry.index] == entry.digit)
        return geometry.cells[entry.index].center
    }

    companion object {
        fun create(readings: List<CellReading>, geometry: BoardGeometry): AutofillPlan? {
            val board = RecognitionPolicy.board(readings) ?: return null
            val result = SudokuSolver.analyze(board) as? SolveResult.Unique ?: return null
            return AutofillPlan(board, result.solution, geometry)
        }
    }
}
