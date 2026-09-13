package com.partridgeman.sudokusolver.recognition

import com.partridgeman.sudokusolver.model.SudokuBoard
import com.partridgeman.sudokusolver.solver.SolveResult
import com.partridgeman.sudokusolver.solver.SudokuSolver

/**
 * Conservative error correction for a tiny number of already-visible curved
 * clue glyphs. This never turns a visually blank cell into a clue and never
 * chooses among multiple Sudoku-consistent assignments.
 */
object RecognitionConstraintRepair {
    private const val MAX_UNCLEAR_CURVED_CELLS = 4

    fun repair(
        readings: List<CellReading>,
        candidates: Map<Int, Set<Int>>,
    ): List<CellReading> {
        if (readings.size != 81) return readings
        val unclear = readings.indices.filter { !readings[it].accepted }
        if (unclear.isEmpty() || unclear.size > MAX_UNCLEAR_CURVED_CELLS) return readings
        if (unclear.any { index -> candidates[index].isNullOrEmpty() }) return readings

        val normalizedCandidates = unclear.associateWith { index ->
            candidates.getValue(index).filter { it in 1..9 }.distinct().sorted()
        }
        if (normalizedCandidates.values.any { it.isEmpty() }) return readings

        val uniqueAssignments = mutableListOf<Map<Int, Int>>()
        val assignment = linkedMapOf<Int, Int>()

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
            CellReading(digit, 0.90, "Curved glyph family plus Sudoku consistency uniquely resolved this clue")
        }
    }
}
