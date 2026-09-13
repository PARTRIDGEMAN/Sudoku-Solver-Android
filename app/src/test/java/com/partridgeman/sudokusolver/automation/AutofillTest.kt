package com.partridgeman.sudokusolver.automation

import com.partridgeman.sudokusolver.recognition.CellReading
import com.partridgeman.sudokusolver.vision.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AutofillTest {
    private val solved = "534678912672195348198342567859761423426853791713924856961537284287419635345286179".map { it.digitToInt() }
    private val geometry = BoardGeometry(List(10) { 100.0 + it * 50 }, List(10) { 20.0 + it * 50 })
    private val window = TargetWindow("test.puzzle", 1, ImageRect(0.0, 0.0, 500.0, 800.0), 500, 800, 0)
    private val keys = (1..9).map { NumberTarget(it, ImageRect(15.0 + (it - 1) * 50, 630.0, 45.0 + (it - 1) * 50, 670.0), .99) }
    private fun readings(values: List<Int>) = values.map { CellReading(it, 1.0, "test") }
    private fun plan() = requireNotNull(AutofillPlan.create(readings(solved.toMutableList().also { it[0] = 0; it[80] = 0 }), geometry))
    private fun snapshot(completed: Int, selected: Int? = null) = LiveBoard(window, plan().expected(completed), geometry, keys, List(81) { -1 }, selected)

    @Test fun onlyOriginalBlanksEnterThePlan() {
        val plan = plan()
        assertEquals(listOf(0, 80), plan.entries.map { it.index })
        assertEquals(solved, plan.expected(2).cells)
        assertEquals(geometry.cells[80].center, plan.cellTarget(plan.entries[1]))
        for (index in 1..79) assertEquals(solved[index], plan.expected(1).cells[index])
        assertThrows(IllegalArgumentException::class.java) { plan.cellTarget(FillEntry(1, solved[1])) }
        assertThrows(UnsupportedOperationException::class.java) { (plan.entries as MutableList).clear() }
    }
    @Test fun uncertainInvalidAndNonUniquePuzzlesCannotCreatePlan() {
        assertNull(AutofillPlan.create(readings(List(81) { 0 }), geometry))
        assertNull(AutofillPlan.create(readings(solved.toMutableList().also { it[0] = it[1] }), geometry))
        assertNull(AutofillPlan.create(readings(solved).toMutableList().also { it[0] = CellReading(null, 0.0, "unknown") }, geometry))
    }
    @Test fun changedGeometryAndWrongProgressReject() {
        val plan = plan()
        assertTrue(plan.matches(plan.original, geometry, 0))
        assertFalse(plan.matches(plan.expected(1), geometry, 0))
        assertFalse(plan.matches(plan.original, BoardGeometry(geometry.horizontal.map { it + 5 }, geometry.vertical), 0))
    }
    @Test fun keypadSupportsRowAndThreeByThree() {
        assertNotNull(KeypadDetector.find(keys, geometry.bounds))
        val grid = (1..9).map { NumberTarget(it, ImageRect(60.0 + (it - 1) % 3 * 100, 580.0 + (it - 1) / 3 * 50,
            100.0 + (it - 1) % 3 * 100, 620.0 + (it - 1) / 3 * 50), 1.0) }
        assertNotNull(KeypadDetector.find(grid, geometry.bounds))
    }
    @Test fun keypadRejectsDuplicatesMissingLowConfidenceAndBoardLabels() {
        for (candidates in listOf(keys + keys[0], keys.dropLast(1), keys.map { it.copy(confidence = .5) },
            keys.map { it.copy(bounds = ImageRect(it.bounds.left, 300.0, it.bounds.right, 340.0)) },
            keys.reversed().mapIndexed { index, key -> key.copy(digit = index + 1) })) {
            assertNull(KeypadDetector.find(candidates, geometry.bounds))
        }
    }
    private class Port(val snapshots: List<LiveBoard>) : AutofillPort {
        var cursor = 0
        val taps = mutableListOf<ImagePoint>()
        override suspend fun inspect() = snapshots[cursor++]
        override suspend fun tap(point: ImagePoint, expectedWindow: TargetWindow) { taps += point }
    }
    @Test fun fillsOnlyBlanksAndVerifiesEachSelectionAndEntry() = runBlocking {
        val port = Port(listOf(snapshot(0), snapshot(0, 0), snapshot(1), snapshot(1, 80), snapshot(2)))
        AutofillRunner().run(plan(), window, keys.associateBy { it.digit }, port)
        assertEquals(listOf(geometry.cells[0].center, keys[4].bounds.center, geometry.cells[80].center, keys[8].bounds.center), port.taps)
        assertEquals(5, port.cursor)
    }
    @Test fun changedPuzzleAppAndKeypadAbortBeforeAnyTap() = runBlocking {
        for (first in listOf(snapshot(1), snapshot(0).copy(window = window.copy(id = 2)),
            snapshot(0).copy(keys = keys.drop(5)), snapshot(0).copy(keys = keys.map { it.copy(bounds =
                ImageRect(it.bounds.left + 10, it.bounds.top, it.bounds.right + 10, it.bounds.bottom)) }))) {
            val port = Port(listOf(first))
            try { AutofillRunner().run(plan(), window, keys.associateBy { it.digit }, port); fail("Should stop") }
            catch (_: IllegalStateException) { assertTrue(port.taps.isEmpty()) }
        }
    }
    @Test fun unknownOrWrongSelectionNeverReceivesNumberTap() = runBlocking {
        for (selected in listOf(null, 1)) {
            val port = Port(listOf(snapshot(0), snapshot(0, selected)))
            try { AutofillRunner().run(plan(), window, keys.associateBy { it.digit }, port); fail("Should stop") }
            catch (_: IllegalStateException) { assertEquals(listOf(geometry.cells[0].center), port.taps) }
        }
    }
    @Test fun wrongEntryStopsBeforeAnotherCell() = runBlocking {
        val port = Port(listOf(snapshot(0), snapshot(0, 0), snapshot(0)))
        try { AutofillRunner().run(plan(), window, keys.associateBy { it.digit }, port); fail("Should stop") }
        catch (_: IllegalStateException) { assertEquals(2, port.taps.size) }
    }
    @Test fun cancellationAfterCellTapPreventsNumberTap() = runBlocking {
        var taps = 0
        val job = launch {
            AutofillRunner().run(plan(), window, keys.associateBy { it.digit }, object : AutofillPort {
                override suspend fun inspect() = snapshot(0, if (taps > 0) 0 else null)
                override suspend fun tap(point: ImagePoint, expectedWindow: TargetWindow) { taps++; currentCoroutineContext().cancel() }
            })
        }
        job.join()
        assertEquals(1, taps)
    }
    @Test fun visualSelectionMustBeUniqueChangedAndColored() {
        val before = snapshot(0)
        val colors = List(81) { if (it == 0) 0xffaabbff.toInt() else -1 }
        val after = before.copy(backgrounds = colors)
        assertTrue(SelectionVerifier.matches(before, after, 0))
        assertFalse(SelectionVerifier.matches(before, after, 1))
        assertFalse(SelectionVerifier.matches(after, after, 0))
        assertFalse(SelectionVerifier.matches(before, after.copy(backgrounds = List(81) { 0xffaabbff.toInt() }), 0))
        assertFalse(SelectionVerifier.matches(before, after.copy(selectedCell = 1), 0))
    }
    @Test fun draggedOverlayStaysWithinScreenIncludingAfterRotation() {
        assertEquals(OverlayPosition(0, 600), OverlayPosition(-50, 1000).clamped(500, 800, 250, 200))
        assertEquals(OverlayPosition(0, 0), OverlayPosition(300, 500).clamped(200, 200, 250, 300))
    }
}
