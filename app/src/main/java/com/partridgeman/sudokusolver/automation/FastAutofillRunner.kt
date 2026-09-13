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
    fun matches(before: FillVisualSnapshot, after: FillVisualSnapshot, index: Int): Boolean {
        if (after.selectedCell != null) return after.selectedCell == index
        if (before.backgrounds.size != 81 || after.backgrounds.size != 81 || index !in 0..80) return false

        val color = after.backgrounds[index]
        fun distance(a: Int, b: Int) = (0..16 step 8).maxOf { shift ->
            abs((a ushr shift and 255) - (b ushr shift and 255))
        }
        fun colored(value: Int): Boolean {
            val channels = (0..16 step 8).map { shift -> value ushr shift and 255 }
            return channels.max() - channels.min() >= 24
        }
        fun uniqueIn(snapshot: FillVisualSnapshot): Boolean = colored(snapshot.backgrounds[index]) &&
            snapshot.backgrounds.indices.filter { it != index }
                .all { other -> distance(snapshot.backgrounds[other], snapshot.backgrounds[index]) >= 12 }

        // Most apps change the target's fill when selected. Some, including the
        // device fixture that motivated this path, start with R1C1 already selected;
        // accept a persistent highlight only when it is uniquely identifiable both
        // before and after the tap.
        val becameSelected = distance(before.backgrounds[index], color) >= 18 && uniqueIn(after)
        val stayedSelected = distance(before.backgrounds[index], color) < 12 && uniqueIn(before) && uniqueIn(after)
        return becameSelected || stayedSelected
    }
}

/**
 * Autofill runner that never invokes OCR or the Sudoku solver inside the hot loop.
 * Each step still fails closed if the app/grid moves, another cell changes, the
 * requested cell cannot be visually verified, or the intended blank does not
 * become occupied after the keypad tap.
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

        var before = port.inspect()
        validate(before, plan, target, 0)

        for ((completed, entry) in plan.entries.withIndex()) {
            coroutineContext.ensureActive()
            validate(before, plan, target, completed)

            port.tap(plan.cellTarget(entry), target)
            val selected = port.inspect()
            validate(selected, plan, target, completed)
            check(FillSelectionVerifier.matches(before, selected, entry.index)) {
                "Could not verify the selected cell. Use cell-first input and rescan."
            }

            coroutineContext.ensureActive()
            port.tap(keys.getValue(entry.digit).bounds.center, target)
            val after = port.inspect()
            validate(after, plan, target, completed + 1)

            progress(completed + 1, plan.entries.size)
            before = after
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
