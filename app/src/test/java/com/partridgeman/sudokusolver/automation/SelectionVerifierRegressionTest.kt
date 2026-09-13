package com.partridgeman.sudokusolver.automation

import com.partridgeman.sudokusolver.model.SudokuBoard
import com.partridgeman.sudokusolver.vision.BoardGeometry
import com.partridgeman.sudokusolver.vision.ImageRect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionVerifierRegressionTest {
    private val geometry = BoardGeometry(List(10) { 100.0 + it * 50 }, List(10) { 20.0 + it * 50 })
    private val window = TargetWindow("test.puzzle", 1, ImageRect(0.0, 0.0, 500.0, 800.0), 500, 800, 0)
    private val board = SudokuBoard(List(81) { 0 })

    private fun snapshot(backgrounds: List<Int>, selectedCell: Int? = null) = LiveBoard(
        window = window,
        board = board,
        geometry = geometry,
        keys = emptyList(),
        backgrounds = backgrounds,
        selectedCell = selectedCell,
    )

    @Test
    fun alreadyHighlightedTargetRemainsValidWhenTapDoesNotChangeColor() {
        // Regression from device test: the Sudoku app preselects R1C1 before autofill.
        // Accessibility does not expose isSelected, so selection must be inferred from
        // the unique saturated blue cell even though tapping it causes no color change.
        val selectedBlue = 0xff8fd0f4.toInt()
        val white = 0xffffffff.toInt()
        val backgrounds = List(81) { index -> if (index == 0) selectedBlue else white }
        val before = snapshot(backgrounds)
        val after = snapshot(backgrounds)

        assertTrue(SelectionVerifier.matches(before, after, 0))
        assertFalse(SelectionVerifier.matches(before, after, 1))
    }

    @Test
    fun ambiguousRepeatedHighlightStillFailsClosed() {
        val selectedBlue = 0xff8fd0f4.toInt()
        val white = 0xffffffff.toInt()
        val before = snapshot(List(81) { white })
        val ambiguous = snapshot(List(81) { index -> if (index == 0 || index == 9) selectedBlue else white })

        assertFalse(SelectionVerifier.matches(before, ambiguous, 0))
    }
}
