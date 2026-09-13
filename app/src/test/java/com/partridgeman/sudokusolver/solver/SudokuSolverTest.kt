package com.partridgeman.sudokusolver.solver

import com.partridgeman.sudokusolver.model.SudokuBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuSolverTest {
    @Test
    fun solvesKnownPuzzle() {
        val puzzle = SudokuBoard.fromRows(
            listOf(
                listOf(5, 3, 0, 0, 7, 0, 0, 0, 0),
                listOf(6, 0, 0, 1, 9, 5, 0, 0, 0),
                listOf(0, 9, 8, 0, 0, 0, 0, 6, 0),
                listOf(8, 0, 0, 0, 6, 0, 0, 0, 3),
                listOf(4, 0, 0, 8, 0, 3, 0, 0, 1),
                listOf(7, 0, 0, 0, 2, 0, 0, 0, 6),
                listOf(0, 6, 0, 0, 0, 0, 2, 8, 0),
                listOf(0, 0, 0, 4, 1, 9, 0, 0, 5),
                listOf(0, 0, 0, 0, 8, 0, 0, 7, 9),
            )
        )

        val solved = SudokuSolver.solve(puzzle)
        requireNotNull(solved)
        assertTrue(solved.isSolved())
        assertEquals(4, solved[0, 2])
        assertEquals(1, solved[8, 6])
    }

    @Test
    fun solvesLiveExpertRegressionPuzzleUniquely() {
        // Transcribed from the clean physical-device screenshot that exposed the
        // old OCR pipeline rejecting nearly every clue.
        val puzzle = SudokuBoard.fromRows(
            listOf(
                listOf(0, 0, 0, 2, 4, 7, 0, 0, 3),
                listOf(0, 0, 0, 0, 0, 0, 6, 0, 0),
                listOf(0, 7, 9, 8, 6, 3, 2, 5, 0),
                listOf(0, 9, 0, 6, 0, 0, 0, 0, 0),
                listOf(0, 0, 8, 3, 1, 0, 0, 0, 0),
                listOf(7, 4, 0, 0, 0, 0, 1, 0, 0),
                listOf(9, 0, 2, 0, 0, 0, 3, 0, 0),
                listOf(0, 0, 0, 4, 0, 0, 0, 0, 6),
                listOf(0, 0, 7, 5, 2, 6, 0, 0, 1),
            )
        )

        val result = SudokuSolver.analyze(puzzle)
        assertTrue(result is SolveResult.Unique)
        val solved = (result as SolveResult.Unique).solution
        assertTrue(solved.isSolved())
        assertEquals(listOf(5, 8, 6, 2, 4, 7, 9, 1, 3), (0..8).map { solved[0, it] })
        assertEquals(listOf(4, 3, 7, 5, 2, 6, 8, 9, 1), (0..8).map { solved[8, it] })
    }

    @Test
    fun rejectsInvalidPuzzle() {
        val invalid = SudokuBoard.fromRows(
            listOf(
                listOf(5, 5, 0, 0, 0, 0, 0, 0, 0),
                List(9) { 0 }, List(9) { 0 }, List(9) { 0 }, List(9) { 0 },
                List(9) { 0 }, List(9) { 0 }, List(9) { 0 }, List(9) { 0 },
            )
        )

        assertNull(SudokuSolver.solve(invalid))
    }
}
