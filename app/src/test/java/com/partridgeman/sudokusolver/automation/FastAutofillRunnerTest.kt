package com.partridgeman.sudokusolver.automation

import com.partridgeman.sudokusolver.recognition.CellReading
import com.partridgeman.sudokusolver.vision.BoardGeometry
import com.partridgeman.sudokusolver.vision.ImagePoint
import com.partridgeman.sudokusolver.vision.ImageRect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FastAutofillRunnerTest {
    private val solved = "534678912672195348198342567859761423426853791713924856961537284287419635345286179".map { it.digitToInt() }
    private val geometry = BoardGeometry(List(10) { 100.0 + it * 50 }, List(10) { 20.0 + it * 50 })
    private val window = TargetWindow("test.puzzle", 1, ImageRect(0.0, 0.0, 500.0, 800.0), 500, 800, 0)
    private val keys = (1..9).associateWith { digit ->
        NumberTarget(digit, ImageRect(15.0 + (digit - 1) * 50, 630.0, 45.0 + (digit - 1) * 50, 670.0), .99)
    }

    private fun plan(): AutofillPlan {
        val values = solved.toMutableList().also { it[0] = 0; it[80] = 0 }
        return requireNotNull(AutofillPlan.create(values.map { CellReading(it, 1.0, "test") }, geometry))
    }

    private fun snapshot(completed: Int, selected: Int? = null, highlighted: Int? = null): FillVisualSnapshot {
        val expected = plan().expected(completed)
        val backgrounds = MutableList(81) { 0xffffffff.toInt() }
        if (highlighted != null) backgrounds[highlighted] = 0xff99ccff.toInt()
        return FillVisualSnapshot(
            window,
            geometry,
            expected.cells.map { it != 0 },
            backgrounds,
            selected,
        )
    }

    private class Port(private val snapshots: List<FillVisualSnapshot>) : FastAutofillPort {
        var cursor = 0
        val taps = mutableListOf<ImagePoint>()
        override suspend fun inspect(): FillVisualSnapshot = snapshots[cursor++]
        override suspend fun tap(point: ImagePoint, expectedWindow: TargetWindow) { taps += point }
    }

    @Test
    fun pipelinesEntryVerificationIntoNextSelection() = runBlocking {
        val p = plan()
        // R1C1 begins selected, so the first cell tap is skipped. After entering its
        // digit, selecting R9C9 and inspecting once simultaneously proves the first
        // entry and the next selection. The final screenshot proves the last entry.
        val port = Port(listOf(
            snapshot(0, highlighted = 0),
            snapshot(1, highlighted = 80),
            snapshot(2, highlighted = 80),
        ))

        FastAutofillRunner().run(p, window, keys, port)

        assertEquals(3, port.taps.size)
        assertEquals(keys.getValue(p.entries[0].digit).bounds.center, port.taps[0])
        assertEquals(p.cellTarget(p.entries[1]), port.taps[1])
        assertEquals(keys.getValue(p.entries[1].digit).bounds.center, port.taps[2])
        assertEquals(3, port.cursor)
    }

    @Test
    fun nonPreselectedFirstCellGetsExplicitTapAndVerification() = runBlocking {
        val p = plan()
        val port = Port(listOf(
            snapshot(0),
            snapshot(0, highlighted = 0),
            snapshot(1, highlighted = 80),
            snapshot(2, highlighted = 80),
        ))

        FastAutofillRunner().run(p, window, keys, port)

        assertEquals(p.cellTarget(p.entries[0]), port.taps[0])
        assertEquals(keys.getValue(p.entries[0].digit).bounds.center, port.taps[1])
    }

    @Test
    fun persistentPreselectedHighlightIsAcceptedOnlyWhenUnique() {
        val before = snapshot(0, highlighted = 0)
        val after = snapshot(0, highlighted = 0)
        assertTrue(FillSelectionVerifier.matches(before, after, 0))
        assertTrue(FillSelectionVerifier.isSelected(before, 0))

        val ambiguous = after.copy(backgrounds = after.backgrounds.toMutableList().also { it[1] = it[0] })
        assertTrue(!FillSelectionVerifier.matches(before, ambiguous, 0))
        assertTrue(!FillSelectionVerifier.isSelected(ambiguous, 0))
    }

    @Test
    fun unexpectedOccupiedCellStopsBeforeContinuing() {
        val p = plan()
        val wrong = snapshot(0, highlighted = 0).copy(
            occupied = snapshot(0).occupied.toMutableList().also { it[1] = true },
        )
        val port = Port(listOf(wrong))

        assertThrows(IllegalStateException::class.java) {
            runBlocking { FastAutofillRunner().run(p, window, keys, port) }
        }
        assertTrue(port.taps.isEmpty())
    }

    @Test
    fun wrongCellAfterNumberTapFailsBeforeAnotherDigit() {
        val p = plan()
        val afterWrong = snapshot(0, highlighted = 80).copy(
            occupied = snapshot(0).occupied.toMutableList().also { it[1] = true },
        )
        val port = Port(listOf(
            snapshot(0, highlighted = 0),
            afterWrong,
        ))

        assertThrows(IllegalStateException::class.java) {
            runBlocking { FastAutofillRunner().run(p, window, keys, port) }
        }
        // First digit target, then only a harmless next-cell selection. No second
        // keypad digit is emitted after validation detects the wrong change.
        assertEquals(2, port.taps.size)
        assertEquals(keys.getValue(p.entries[0].digit).bounds.center, port.taps[0])
        assertEquals(p.cellTarget(p.entries[1]), port.taps[1])
    }
}
