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
    private const val MIN_CONFIDENCE = 0.60
    private const val MAX_CANDIDATES_PER_DIGIT = 3

    /**
     * Find a coherent 1-9 keypad cluster outside the Sudoku board.
     *
     * Older builds required every digit to occur exactly once anywhere outside the
     * board. Real phone UIs contain timers, scores, battery percentages and call chips,
     * so one unrelated duplicate was enough to disable autofill. Instead, partition
     * candidates into spatial zones around the board and search each zone for one
     * geometrically valid keypad. Multiple distinct valid keypads still fail closed.
     */
    fun find(candidates: List<NumberTarget>, board: ImageRect): Map<Int, NumberTarget>? {
        val minimumHeight = board.height / 9 * 0.20
        val filtered = candidates.filter {
            it.digit in 1..9 && it.confidence.isFinite() && it.confidence >= MIN_CONFIDENCE &&
                !intersects(it.bounds, board) && it.bounds.height >= minimumHeight
        }
        if (filtered.isEmpty()) return null

        val zones = listOf(
            filtered.filter { it.bounds.center.y >= board.bottom },
            filtered.filter { it.bounds.center.y <= board.top },
            filtered.filter { it.bounds.center.x <= board.left },
            filtered.filter { it.bounds.center.x >= board.right },
        ).filter { it.isNotEmpty() }

        val matches = zones.mapNotNull { findInZone(it, board) }
            .distinctBy { keypadSignature(it) }
        return matches.singleOrNull()
    }

    private fun findInZone(candidates: List<NumberTarget>, board: ImageRect): Map<Int, NumberTarget>? {
        val choices = (1..9).associateWith { digit ->
            candidates.filter { it.digit == digit }
                .sortedByDescending { it.confidence }
                .take(MAX_CANDIDATES_PER_DIGIT)
        }
        if (choices.values.any { it.isEmpty() }) return null

        val valid = mutableListOf<List<NumberTarget>>()
        val picked = ArrayList<NumberTarget>(9)

        fun search(digit: Int) {
            if (valid.size > 1) return
            if (digit == 10) {
                if (validateCluster(picked, board)) valid += picked.toList()
                return
            }
            for (candidate in choices.getValue(digit)) {
                if (picked.any { intersects(it.bounds, candidate.bounds) }) continue
                picked += candidate
                search(digit + 1)
                picked.removeAt(picked.lastIndex)
                if (valid.size > 1) return
            }
        }

        search(1)
        return valid.singleOrNull()?.associateBy { it.digit }
    }

    private fun validateCluster(keys: List<NumberTarget>, board: ImageRect): Boolean {
        if (keys.size != 9 || keys.map { it.digit }.toSet() != (1..9).toSet()) return false
        val medianHeight = keys.map { it.bounds.height }.sorted()[4]
        if (medianHeight <= 0.0 || keys.any { it.bounds.height !in medianHeight * 0.55..medianHeight * 1.8 }) return false

        val bounds = ImageRect(
            keys.minOf { it.bounds.left },
            keys.minOf { it.bounds.top },
            keys.maxOf { it.bounds.right },
            keys.maxOf { it.bounds.bottom },
        )
        if (intersects(bounds, board) || bounds.width > board.width * 1.5 || bounds.height > board.height * 0.65) return false

        val rows = mutableListOf<MutableList<NumberTarget>>()
        for (key in keys.sortedBy { it.bounds.center.y }) {
            val row = rows.firstOrNull {
                abs(it.map { member -> member.bounds.center.y }.average() - key.bounds.center.y) <= medianHeight * 0.75
            }
            if (row == null) rows += mutableListOf(key) else row += key
        }
        if (rows.size !in 1..3 || rows.any { it.size < 3 }) return false

        val orderedRows = rows.sortedBy { row -> row.map { it.bounds.center.y }.average() }
        val flattened = orderedRows.flatMap { row -> row.sortedBy { it.bounds.center.x } }
        if (flattened.map { it.digit } != (1..9).toList()) return false

        for (row in orderedRows) {
            val sorted = row.sortedBy { it.bounds.center.x }
            val gaps = sorted.zipWithNext().map { (a, b) -> b.bounds.center.x - a.bounds.center.x }
            if (gaps.isEmpty() || gaps.any { it <= 0.0 }) return false
            val mean = gaps.average()
            if (!mean.isFinite() || gaps.any { abs(it / mean - 1.0) > 0.40 }) return false
        }
        return true
    }

    private fun keypadSignature(keys: Map<Int, NumberTarget>): String = (1..9).joinToString("|") { digit ->
        val b = keys.getValue(digit).bounds
        "$digit:${b.left.toInt()},${b.top.toInt()},${b.right.toInt()},${b.bottom.toInt()}"
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
