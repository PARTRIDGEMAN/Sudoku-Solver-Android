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
