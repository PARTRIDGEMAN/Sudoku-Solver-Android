package com.partridgeman.sudokusolver.automation

import com.partridgeman.sudokusolver.recognition.InkKind
import org.junit.Assert.assertEquals
import org.junit.Test

class FillVisualPolicyTest {
    @Test
    fun anyVisibleMarkCountsAsPresentForCheckpointLedger() {
        assertEquals(
            listOf(false, true, true),
            FillVisualPolicy.occupied(listOf(InkKind.BLANK, InkKind.AMBIGUOUS, InkKind.DIGIT)),
        )
    }
}
