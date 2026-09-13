package com.partridgeman.sudokusolver.automation

import com.partridgeman.sudokusolver.recognition.InkKind

/** Autofill-specific interpretation of the cheap per-cell ink detector. */
object FillVisualPolicy {
    /**
     * During checkpointed autofill we only need to know whether a cell visibly contains
     * something. User-entered digits can become AMBIGUOUS while selected/highlighted,
     * so both DIGIT and AMBIGUOUS count as present.
     *
     * This is safe because the runner no longer compares all 81 cells for exact occupancy:
     * it only requires original clues and cells already issued by the ledger to remain
     * non-blank. Future cells are ignored until their turn, so selection/row/column shading
     * cannot masquerade as a puzzle mutation.
     */
    fun occupied(kinds: List<InkKind>): List<Boolean> = kinds.map { it != InkKind.BLANK }
}
