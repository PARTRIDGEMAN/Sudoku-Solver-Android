package com.partridgeman.sudokusolver.automation

import com.partridgeman.sudokusolver.vision.BoardGeometry
import com.partridgeman.sudokusolver.vision.ImagePoint
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Cheap state used while a previously validated Sudoku is being filled.
 *
 * Full OCR belongs at scan time. During autofill we only need to prove that the
 * same app/grid is still on screen, the expected cells are occupied, and the
 * intended cell is selected before a verified keypad coordinate is tapped.
 */
data class FillVisualSnapshot(
    val window: TargetWindow,
    val geometry: BoardGeometry,
    val occupied: List<Boolean>,
    val backgrounds: List<Int>,
    val selectedCell: Int?,
)

interface FastAutofillPort {
    suspend fun inspect(): FillVisualSnapshot
    suspend fun tap(point: ImagePoint, expectedWindow: TargetWindow)
}

object FillSelectionVerifier {
    fun isSelected(snapshot: FillVisualSnapshot, index: Int): Boolean {
        if (index !in 0..80) return false
        if (snapshot.selectedCell != null) return snapshot.selectedCell == index
        if (snapshot.backgrounds.size != 81) return false
        return uniqueColored(snapshot, index)
    }

    fun matches(before: FillVisualSnapshot, after: FillVisualSnapshot, index: Int): Boolean {
        if (after.selectedCell != null) return after.selectedCell == index
        if (before.backgrounds.size != 81 || after.backgrounds.size != 81 || index !in 0..80) return false

        val distance = colorDistance(before.backgrounds[index], after.backgrounds[index])
        // Most apps change the target's fill when selected. Some start with a blank
        // already selected; accept a persistent highlight only when it is uniquely
        // identifiable both before and after the tap.
        val becameSelected = distance >= 18 && uniqueColored(after, index)
        val stayedSelected = distance < 12 && uniqueColored(before, index) && uniqueColored(after, index)
        return becameSelected || stayedSelected
    }

    private fun uniqueColored(snapshot: FillVisualSnapshot, index: Int): Boolean {
        val color = snapshot.backgrounds[index]
        val channels = (0..16 step 8).map { shift -> color ushr shift and 255 }
        if (channels.max() - channels.min() < 24) return false
        return snapshot.backgrounds.indices.filter { it != index }
            .all { other -> colorDistance(snapshot.backgrounds[other], color) >= 12 }
    }

    private fun colorDistance(a: Int, b: Int): Int = (0..16 step 8).maxOf { shift ->
        abs((a ushr shift and 255) - (b ushr shift and 255))
    }
}

/**
 * Autofill runner that never invokes OCR or the Sudoku solver inside the hot loop.
 *
 * The loop is pipelined so, after the first cell, one screenshot verifies both the
 * previous number entry and selection of the next cell. This keeps visual safety
 * checks while avoiding two screenshot round-trips for every blank.
 */
class FastAutofillRunner {
    suspend fun run(
        plan: AutofillPlan,
        target: TargetWindow,
        keys: Map<Int, NumberTarget>,
        port: FastAutofillPort,
        progress: (Int, Int) -> Unit = { _, _ -> },
    ) {
        require(keys.keys == (1..9).toSet()) { "Number buttons were not verified" }
        if (plan.entries.isEmpty()) return

        var state = port.inspect()
        validate(state, plan, target, 0)
        var currentAlreadySelected = FillSelectionVerifier.isSelected(state, plan.entries.first().index)

        for ((completed, entry) in plan.entries.withIndex()) {
            coroutineContext.ensureActive()
            validate(state, plan, target, completed)

            val selected = if (currentAlreadySelected) {
                check(FillSelectionVerifier.isSelected(state, entry.index)) {
                    "Could not verify the selected cell. Use cell-first input and rescan."
                }
                state
            } else {
                port.tap(plan.cellTarget(entry), target)
                val snapshot = port.inspect()
                validate(snapshot, plan, target, completed)
                check(FillSelectionVerifier.matches(state, snapshot, entry.index)) {
                    "Could not verify the selected cell. Use cell-first input and rescan."
                }
                snapshot
            }

            coroutineContext.ensureActive()
            port.tap(keys.getValue(entry.digit).bounds.center, target)

            val nextEntry = plan.entries.getOrNull(completed + 1)
            if (nextEntry == null) {
                val after = port.inspect()
                validate(after, plan, target, completed + 1)
                progress(completed + 1, plan.entries.size)
                state = after
                currentAlreadySelected = false
            } else {
                // Select the next blank before capturing. The next screenshot now proves
                // both that this number was entered into the intended cell and that the
                // next target is selected. If the number tap failed or another cell
                // changed, occupancy validation fails before another digit is emitted.
                port.tap(plan.cellTarget(nextEntry), target)
                val nextSelected = port.inspect()
                validate(nextSelected, plan, target, completed + 1)
                check(FillSelectionVerifier.isSelected(nextSelected, nextEntry.index)) {
                    "Could not verify the next selected cell. Autofill stopped."
                }
                progress(completed + 1, plan.entries.size)
                state = nextSelected
                currentAlreadySelected = true
            }
        }
    }

    private fun validate(snapshot: FillVisualSnapshot, plan: AutofillPlan, target: TargetWindow, completed: Int) {
        check(snapshot.window == target) { "The foreground app or screen changed. Rescan before filling." }
        check(sameGeometry(plan.geometry, snapshot.geometry)) { "The Sudoku grid moved. Autofill stopped." }
        check(snapshot.occupied.size == 81) { "Could not verify the Sudoku cells. Autofill stopped." }

        val expected = plan.expected(completed).cells.map { it != 0 }
        check(snapshot.occupied == expected) {
            "The puzzle changed unexpectedly while filling. Autofill stopped."
        }
    }

    private fun sameGeometry(expected: BoardGeometry, current: BoardGeometry): Boolean {
        val tolerance = minOf(expected.bounds.width, expected.bounds.height) / 9 * 0.06
        return expected.horizontal.zip(current.horizontal).all { (a, b) -> abs(a - b) <= tolerance } &&
            expected.vertical.zip(current.vertical).all { (a, b) -> abs(a - b) <= tolerance }
    }
}
