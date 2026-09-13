package com.partridgeman.sudokusolver.recognition

import com.partridgeman.sudokusolver.model.SudokuBoard
import com.partridgeman.sudokusolver.solver.SolveResult
import com.partridgeman.sudokusolver.solver.SudokuSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionConstraintRepairTest {
    @Test
    fun repairsExactExtremePuzzleCurvedMissesOnlyWhenAssignmentIsUnique() {
        // Screenshot regression: R3C5 is a visible 9 and R9C2 is a visible 6.
        // Pretend OCR missed both while topology can only establish one-loop {6,9}.
        val rows = listOf(
            "000000000",
            "900302678",
            "000000005",
            "746210000",
            "500030000",
            "003600090",
            "007020000",
            "300801060",
            "005000809",
        )
        val readings = rows.joinToString("").map { char ->
            if (char == '0') CellReading(0, 1.0, "blank")
            else CellReading(char.digitToInt(), 0.99, "recognized clue")
        }.toMutableList().also {
            it[2 * 9 + 4] = CellReading(null, 0.0, "missed 9")
            it[8 * 9 + 1] = CellReading(null, 0.0, "missed 6")
        }

        val repaired = RecognitionConstraintRepair.repair(
            readings,
            mapOf(2 * 9 + 4 to setOf(6, 9), 8 * 9 + 1 to setOf(6, 9)),
        )

        assertEquals(9, repaired[2 * 9 + 4].value)
        assertEquals(6, repaired[8 * 9 + 1].value)
        val board = SudokuBoard(repaired.map { requireNotNull(it.value) })
        assertTrue(SudokuSolver.analyze(board) is SolveResult.Unique)
        assertEquals("Curved glyph family plus Sudoku consistency uniquely resolved this clue", repaired[2 * 9 + 4].reason)
    }

    @Test
    fun refusesRepairWhenAnUnclearCellHasNoShapeCandidates() {
        val readings = List(81) { CellReading(0, 1.0, "blank") }.toMutableList().also {
            it[0] = CellReading(null, 0.0, "unclear")
        }
        val repaired = RecognitionConstraintRepair.repair(readings, emptyMap())
        assertSame(readings, repaired)
    }
}
