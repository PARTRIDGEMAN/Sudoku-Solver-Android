package com.partridgeman.sudokusolver.automation

import com.partridgeman.sudokusolver.recognition.InkKind

/** Autofill-specific interpretation of the cheap per-cell ink detector. */
object FillVisualPolicy {
    /**
     * Only a full-sized digit glyph proves that a cell is occupied during autofill.
     *
     * The initial puzzle scan intentionally treats AMBIGUOUS as unsafe, but the live
     * Sudoku UI can add selection/row/column shading that makes a genuinely empty cell
     * ambiguous. Counting that decoration as a number caused false "puzzle changed"
     * stops immediately after selecting the next blank.
     */
    fun occupied(kinds: List<InkKind>): List<Boolean> = kinds.map { it == InkKind.DIGIT }
}
