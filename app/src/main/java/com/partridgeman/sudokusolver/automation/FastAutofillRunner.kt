package com.partridgeman.sudokusolver.automation

import com.partridgeman.sudokusolver.vision.BoardGeometry
import com.partridgeman.sudokusolver.vision.ImagePoint
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Cheap state used while a previously validated Sudoku is being filled.
 *
 * Full OCR belongs at scan time. During autofill we keep a ledger of the entries we
 * have issued and periodically prove that those cells are no longer blank while the
 * original clues and board geometry are still intact.
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
 * Stateful/checkpointed autofill runner.
 *
 * Every entry is still cell-first: explicitly tap the planned cell, then the already
 * verified keypad coordinate for its planned digit. We do not re-read the entire board
 * after every tap because normal Sudoku selection/row/column highlighting makes that
 * brittle. Instead, the validated plan itself is the ledger and one cheap screenshot
 * every few entries verifies that:
 *  - the same app and same grid are still present,
 *  - all original clues are still visibly present, and
 *  - every ledger entry issued so far is visibly non-blank.
 *
 * Cells that have not been issued yet are intentionally ignored at checkpoints; apps
 * are free to auto-select/highlight them. A missed cell tap is still caught because the
 * intended issued cell remains blank at the next checkpoint.
 */
class FastAutofillRunner(
    private val checkpointSize: Int = DEFAULT_CHECKPOINT_SIZE,
) {
    init {
        require(checkpointSize > 0)
    }

    suspend fun run(
        plan: AutofillPlan,
        target: TargetWindow,
        keys: Map<Int, NumberTarget>,
        port: FastAutofillPort,
        progress: (Int, Int) -> Unit = { _, _ -> },
    ) {
        require(keys.keys == (1..9).toSet()) { "Number buttons were not verified" }
        if (plan.entries.isEmpty()) return

        // Initial checkpoint proves we are still on the validated puzzle before any
        // gestures are emitted. Future blank/highlight state is deliberately ignored.
        validateCheckpoint(port.inspect(), plan, target, completed = 0)

        for ((zeroBased, entry) in plan.entries.withIndex()) {
            coroutineContext.ensureActive()

            // Always select the target explicitly. We do not depend on any app's
            // auto-advance behavior, which varies between Sudoku implementations.
            port.tap(plan.cellTarget(entry), target)
            coroutineContext.ensureActive()
            port.tap(keys.getValue(entry.digit).bounds.center, target)

            val completed = zeroBased + 1
            progress(completed, plan.entries.size)

            if (completed % checkpointSize == 0 || completed == plan.entries.size) {
                validateCheckpoint(port.inspect(), plan, target, completed)
            }
        }
    }

    internal fun validateCheckpoint(
        snapshot: FillVisualSnapshot,
        plan: AutofillPlan,
        target: TargetWindow,
        completed: Int,
    ) {
        check(completed in 0..plan.entries.size)
        check(snapshot.window == target) { "The foreground app or screen changed. Rescan before filling." }
        check(sameGeometry(plan.geometry, snapshot.geometry)) { "The Sudoku grid moved. Autofill stopped." }
        check(snapshot.occupied.size == 81) { "Could not verify the Sudoku cells. Autofill stopped." }

        // Clues are immutable anchors. We only require visible content here; highlighted
        // clues may be classified as ambiguous by the cheap visual path and are still OK.
        plan.original.cells.forEachIndexed { index, value ->
            if (value != 0) {
                check(snapshot.occupied[index]) {
                    "An original clue at ${label(index)} disappeared. Autofill stopped."
                }
            }
        }

        // The plan is our transaction ledger. Every cell whose input has already been
        // issued must now contain visible content. We deliberately do not compare future
        // cells: a selected/highlighted blank may look non-empty even though the puzzle
        // itself has not changed.
        for (entry in plan.entries.take(completed)) {
            check(snapshot.occupied[entry.index]) {
                "${label(entry.index)} still looks blank after input. Autofill stopped."
            }
        }
    }

    private fun sameGeometry(expected: BoardGeometry, current: BoardGeometry): Boolean {
        val tolerance = minOf(expected.bounds.width, expected.bounds.height) / 9 * 0.06
        return expected.horizontal.zip(current.horizontal).all { (a, b) -> abs(a - b) <= tolerance } &&
            expected.vertical.zip(current.vertical).all { (a, b) -> abs(a - b) <= tolerance }
    }

    private fun label(index: Int): String = "R${index / 9 + 1}C${index % 9 + 1}"

    companion object {
        const val DEFAULT_CHECKPOINT_SIZE = 4
    }
}
