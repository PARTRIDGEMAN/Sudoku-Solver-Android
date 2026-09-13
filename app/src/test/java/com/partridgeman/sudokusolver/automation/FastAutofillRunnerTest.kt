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

    private fun plan(blanks: List<Int>): AutofillPlan {
        val values = solved.toMutableList()
        blanks.forEach { values[it] = 0 }
        return requireNotNull(AutofillPlan.create(values.map { CellReading(it, 1.0, "test") }, geometry))
    }

    private fun snapshot(
        plan: AutofillPlan,
        completed: Int,
        overrides: Map<Int, Boolean> = emptyMap(),
        highlighted: Int? = null,
    ): FillVisualSnapshot {
        val occupied = plan.original.cells.map { it != 0 }.toMutableList()
        plan.entries.take(completed).forEach { occupied[it.index] = true }
        overrides.forEach { (index, value) -> occupied[index] = value }
        val backgrounds = MutableList(81) { 0xffffffff.toInt() }
        if (highlighted != null) backgrounds[highlighted] = 0xff99ccff.toInt()
        return FillVisualSnapshot(window, geometry, occupied, backgrounds, null)
    }

    private class Port(private val snapshots: List<FillVisualSnapshot>) : FastAutofillPort {
        var cursor = 0
        val taps = mutableListOf<ImagePoint>()
        override suspend fun inspect(): FillVisualSnapshot = snapshots[cursor++]
        override suspend fun tap(point: ImagePoint, expectedWindow: TargetWindow) { taps += point }
    }

    @Test
    fun usesCellFirstInputAndOnlyChecksAtBatchBoundaries() = runBlocking {
        val p = plan(listOf(0, 1, 2, 3, 4))
        val port = Port(listOf(
            snapshot(p, 0),
            snapshot(p, 4),
            snapshot(p, 5),
        ))
        val progress = mutableListOf<Int>()

        FastAutofillRunner(checkpointSize = 4).run(p, window, keys, port) { completed, _ ->
            progress += completed
        }

        assertEquals(3, port.cursor) // initial + after four + final
        assertEquals(10, port.taps.size) // explicit cell tap + keypad tap for every entry
        p.entries.forEachIndexed { index, entry ->
            assertEquals(p.cellTarget(entry), port.taps[index * 2])
            assertEquals(keys.getValue(entry.digit).bounds.center, port.taps[index * 2 + 1])
        }
        assertEquals(listOf(1, 2, 3, 4, 5), progress)
    }

    @Test
    fun futureHighlightedCellDoesNotInvalidateCheckpoint() = runBlocking {
        val p = plan(listOf(0, 1, 2, 3, 4))
        val future = p.entries[4].index
        val port = Port(listOf(
            snapshot(p, 0),
            // Simulates Sudoku auto-select/row shading being classified as visible
            // content in a cell we have not issued yet. The ledger must ignore it.
            snapshot(p, 4, overrides = mapOf(future to true), highlighted = future),
            snapshot(p, 5),
        ))

        FastAutofillRunner(checkpointSize = 4).run(p, window, keys, port)

        assertEquals(3, port.cursor)
        assertEquals(10, port.taps.size)
    }

    @Test
    fun issuedCellStillBlankStopsAtNextCheckpoint() {
        val p = plan(listOf(0, 1, 2, 3, 4))
        val missing = p.entries[2].index
        val port = Port(listOf(
            snapshot(p, 0),
            snapshot(p, 4, overrides = mapOf(missing to false)),
        ))

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { FastAutofillRunner(checkpointSize = 4).run(p, window, keys, port) }
        }

        assertTrue(error.message!!.contains("still looks blank"))
        assertEquals(8, port.taps.size) // exactly four attempted entries before checkpoint
    }

    @Test
    fun originalClueDisappearingStopsBeforeAnyInput() {
        val p = plan(listOf(0, 1, 2, 3, 4))
        val clue = p.original.cells.indexOfFirst { it != 0 }
        val port = Port(listOf(snapshot(p, 0, overrides = mapOf(clue to false))))

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { FastAutofillRunner(checkpointSize = 4).run(p, window, keys, port) }
        }

        assertTrue(error.message!!.contains("original clue", ignoreCase = true))
        assertTrue(port.taps.isEmpty())
    }
}
