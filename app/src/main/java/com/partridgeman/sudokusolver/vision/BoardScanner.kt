package com.partridgeman.sudokusolver.vision

import android.graphics.Bitmap
import android.graphics.RectF
import com.partridgeman.sudokusolver.model.SudokuBoard

data class BoardScanResult(
    val board: SudokuBoard,
    val boardBounds: RectF,
    val confidence: Float,
)

/**
 * Converts a screen capture into Sudoku geometry and recognized clue digits.
 * Implementations must discover board bounds dynamically; app-specific fixed
 * coordinates do not belong in this interface or its production implementations.
 */
interface BoardScanner {
    fun scan(bitmap: Bitmap): BoardScanResult?
}
