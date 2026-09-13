package com.partridgeman.sudokusolver.recognition

import com.partridgeman.sudokusolver.model.SudokuBoard
import com.partridgeman.sudokusolver.solver.SolveResult
import com.partridgeman.sudokusolver.solver.SudokuSolver

/**
 * Conservative error correction for a tiny number of cells that are visually
 * occupied by full-sized clue glyphs but were not confidently recognized.
 *
 * Candidate sets come only from visual evidence (OCR/topology, or 1..9 for a
 * full-sized glyph that OCR completely missed). A repair is accepted only when
 * exactly one candidate assignment produces a uniquely solvable Sudoku. Visually
 * blank cells are never promoted into clues.
 */
object RecognitionConstraintRepair {
    private const val MAX_UNCLEAR_OCCUPIED_CELLS = 5
    private const val MAX_ASSIGNMENTS_TO_TRY = 4096L

    fun repair(
        readings: List<CellReading>,
        candidates: Map<Int, Set<Int>>,
    ): List<CellReading> {
        if (readings.size != 81) return readings
        val unclear = readings.indices.filter { !readings[it].accepted }
        if (unclear.isEmpty() || unclear.size > MAX_UNCLEAR_OCCUPIED_CELLS) return readings
        if (unclear.any { index -> candidates[index].isNullOrEmpty() }) return readings

        val normalizedCandidates = unclear.associateWith { index ->
            candidates.getValue(index).filter { it in 1..9 }.distinct().sorted()
        }
        if (normalizedCandidates.values.any { it.isEmpty() }) return readings

        var combinations = 1L
        for (options in normalizedCandidates.values) {
            combinations *= options.size
            if (combinations > MAX_ASSIGNMENTS_TO_TRY) return readings
        }

        val uniqueAssignments = mutableListOf<Map<Int, Int>>()
        val assignment = linkedMapOf<Int, Int>()

        fun conflicts(index: Int, digit: Int): Boolean {
            val row = index / 9
            val column = index % 9
            fun valueAt(other: Int): Int? = when {
                other == index -> digit
                readings[other].accepted -> readings[other].value
                else -> assignment[other]
            }
            for (c in 0 until 9) {
                val other = row * 9 + c
                if (other != index && valueAt(other) == digit) return true
            }
            for (r in 0 until 9) {
                val other = r * 9 + column
                if (other != index && valueAt(other) == digit) return true
            }
            val boxRow = row / 3 * 3
            val boxColumn = column / 3 * 3
            for (r in boxRow until boxRow + 3) for (c in boxColumn until boxColumn + 3) {
                val other = r * 9 + c
                if (other != index && valueAt(other) == digit) return true
            }
            return false
        }

        fun search(position: Int) {
            if (uniqueAssignments.size > 1) return
            if (position == unclear.size) {
                val values = readings.mapIndexed { index, reading ->
                    if (reading.accepted) requireNotNull(reading.value) else assignment.getValue(index)
                }
                val board = runCatching { SudokuBoard(values) }.getOrNull() ?: return
                if (SudokuSolver.analyze(board) is SolveResult.Unique) {
                    uniqueAssignments += assignment.toMap()
                }
                return
            }
            val index = unclear[position]
            for (digit in normalizedCandidates.getValue(index)) {
                if (conflicts(index, digit)) continue
                assignment[index] = digit
                search(position + 1)
                assignment.remove(index)
                if (uniqueAssignments.size > 1) return
            }
        }

        search(0)
        val winner = uniqueAssignments.singleOrNull() ?: return readings
        return readings.mapIndexed { index, reading ->
            val digit = winner[index] ?: return@mapIndexed reading
            CellReading(digit, 0.90, "Visual candidates plus Sudoku uniqueness resolved this clue")
        }
    }
}
