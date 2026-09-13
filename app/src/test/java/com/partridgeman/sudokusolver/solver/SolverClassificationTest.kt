package com.partridgeman.sudokusolver.solver

import com.partridgeman.sudokusolver.model.SudokuBoard
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class SolverClassificationTest {
    private fun board(text: String) = SudokuBoard(text.filter { it.isDigit() }.map { it.digitToInt() })
    private val solved = board("534678912672195348198342567859761423426853791713924856961537284287419635345286179")
    private val easy = board("530070000600195000098000060800060003400803001700020006060000280000419005000080079")
    private val medium = board("000260701680070090190004500820100040004602900050003028009300074040050036703018000")
    private val hard = board("100007090030020008009600500005300900010080002600004000300000010040000007007000300")

    private fun assertSolution(input: SudokuBoard, solution: SudokuBoard) {
        assertTrue(solution.isSolved())
        for (index in 0..8) {
            assertEquals((1..9).toSet(), (0..8).map { solution[index, it] }.toSet())
            assertEquals((1..9).toSet(), (0..8).map { solution[it, index] }.toSet())
            val row = index / 3 * 3
            val col = index % 3 * 3
            assertEquals((1..9).toSet(), (0..8).map { solution[row + it / 3, col + it % 3] }.toSet())
        }
        input.cells.forEachIndexed { index, clue -> if (clue != 0) assertEquals(clue, solution.cells[index]) }
    }

    @Test fun easyMediumHardAreUniqueAndDeterministic() {
        for (input in listOf(easy, medium, hard)) {
            val original = input.cells.toList()
            val result = SudokuSolver.analyze(input)
            assertTrue("$input -> $result", result is SolveResult.Unique)
            assertSolution(input, (result as SolveResult.Unique).solution)
            assertEquals(original, input.cells)
            repeat(3) { assertEquals(result, SudokuSolver.analyze(input)) }
        }
    }

    @Test fun solvedBoardIsUnique() {
        assertEquals(SolveResult.Unique(solved), SudokuSolver.analyze(solved))
    }

    @Test fun invalidRowColumnAndBoxAreRejected() {
        for ((first, second) in listOf(0 to 8, 0 to 72, 0 to 10)) {
            val cells = MutableList(81) { 0 }
            cells[first] = 5
            cells[second] = 5
            assertEquals(SolveResult.InvalidGivens, SudokuSolver.analyze(SudokuBoard(cells)))
        }
    }

    @Test fun validGivensCanBeUnsatisfiable() {
        // Row 1 must put 9 in its last cell, but that column already contains 9.
        val input = board("123456780000000009000000000000000000000000000000000000000000000000000000000000000")
        assertTrue(input.isValid())
        assertEquals(SolveResult.Unsatisfiable, SudokuSolver.analyze(input))
        assertNull(SudokuSolver.solve(input))
    }

    @Test(timeout = 5000) fun ambiguousSearchStopsAtSecondSolution() {
        // Enumerating every completion of an empty Sudoku cannot finish within this generous bound.
        val empty = SudokuBoard(List(81) { 0 })
        repeat(3) { assertEquals(SolveResult.MultipleSolutions, SudokuSolver.analyze(empty)) }
        assertNull(SudokuSolver.solve(empty))
        val twoDigitsMissing = SudokuBoard(solved.cells.map { if (it in 1..2) 0 else it })
        assertEquals(SolveResult.MultipleSolutions, SudokuSolver.analyze(twoDigitsMissing))
    }

    @Test fun modelDefensivelyCopiesAndPreventsMutation() {
        val input = easy.cells.toMutableList()
        val snapshot = SudokuBoard(input)
        input[0] = 9
        assertEquals(easy, snapshot)
        assertThrows(UnsupportedOperationException::class.java) { (snapshot.cells as MutableList<Int>)[0] = 9 }
        assertEquals(easy.hashCode(), snapshot.hashCode())
        assertThrows(IllegalArgumentException::class.java) { snapshot[0, 9] }
    }

    @Test fun deterministicGeneratedBlankPatternsPreserveCluesAndInput() {
        val random = Random(9876)
        repeat(40) { variant ->
            // Removing deterministic cell subsets produces many patterns, while the known completion
            // independently proves satisfiability. Classification itself still checks uniqueness.
            val indices = (0 until 81).shuffled(random).take(8 + variant % 30).toSet()
            val input = SudokuBoard(solved.cells.mapIndexed { index, value -> if (index in indices) 0 else value })
            val original = input.cells.toList()
            when (val result = SudokuSolver.analyze(input)) {
                is SolveResult.Unique -> {
                    assertSolution(input, result.solution)
                    assertEquals(solved, result.solution)
                }
                SolveResult.MultipleSolutions -> assertNull(SudokuSolver.solve(input))
                else -> fail("Known completion was lost: $variant -> $result")
            }
            assertEquals(original, input.cells)
        }
    }
}
