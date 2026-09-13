package com.partridgeman.sudokusolver.automation

import com.partridgeman.sudokusolver.model.SudokuBoard
import com.partridgeman.sudokusolver.vision.BoardGeometry
import com.partridgeman.sudokusolver.vision.ImagePoint
import com.partridgeman.sudokusolver.vision.ImageRect
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.hypot

data class TargetWindow(val packageName: String, val id: Int, val bounds: ImageRect, val width: Int, val height: Int, val rotation: Int)
data class LiveBoard(val window: TargetWindow, val board: SudokuBoard, val geometry: BoardGeometry,
    val keys: List<NumberTarget>, val backgrounds: List<Int>, val selectedCell: Int?)

interface AutofillPort {
    suspend fun inspect(): LiveBoard
    suspend fun tap(point: ImagePoint, expectedWindow: TargetWindow)
}

object SelectionVerifier {
    fun matches(before: LiveBoard, after: LiveBoard, index: Int): Boolean {
        if (after.selectedCell != null) return after.selectedCell == index
        if (before.backgrounds.size != 81 || after.backgrounds.size != 81) return false

        // Some Sudoku apps (including the one used in device testing) visually select
        // the first empty cell as soon as a puzzle opens, but do not expose that state
        // through AccessibilityNodeInfo.isSelected. In that case tapping the already-
        // selected target causes no color transition at all. Accept either:
        //   1) a newly acquired, unique visual selection highlight, or
        //   2) the same target being uniquely highlighted both before and after.
        val afterHighlighted = visuallySelected(after, index)
        if (!afterHighlighted) return false
        if (visuallySelected(before, index)) return true

        return distance(before.backgrounds[index], after.backgrounds[index]) >= 18
    }

    private fun visuallySelected(snapshot: LiveBoard, index: Int): Boolean {
        if (snapshot.backgrounds.size != 81 || index !in snapshot.backgrounds.indices) return false
        val color = snapshot.backgrounds[index]
        val channels = (0..16 step 8).map { color ushr it and 255 }
        if (channels.max() - channels.min() < 24) return false

        // The actual selected square should have a stronger fill than ordinary white
        // cells and than row/column peer highlighting. Fail closed if another cell is
        // effectively the same color.
        return snapshot.backgrounds.indices
            .filter { it != index }
            .all { distance(snapshot.backgrounds[it], color) >= 12 }
    }

    private fun distance(a: Int, b: Int): Int =
        (0..16 step 8).maxOf { abs((a ushr it and 255) - (b ushr it and 255)) }
}

/** No input is emitted until a fresh board exactly matches the validated plan's expected progress. */
class AutofillRunner {
    suspend fun run(plan: AutofillPlan, target: TargetWindow, initialKeys: Map<Int, NumberTarget>, port: AutofillPort,
        progress: (Int, Int) -> Unit = { _, _ -> }) {
        require(initialKeys.keys == (1..9).toSet()) { "Number buttons were not verified" }
        var before = port.inspect()
        fun validate(snapshot: LiveBoard, completed: Int) {
            check(snapshot.window == target) { "The foreground app or screen changed. Rescan before filling." }
            check(plan.matches(snapshot.board, snapshot.geometry, completed)) { "The puzzle or board position changed. Autofill stopped." }
        }
        for ((completed, entry) in plan.entries.withIndex()) {
            coroutineContext.ensureActive()
            validate(before, completed)
            resolveKey(initialKeys.getValue(entry.digit), before.keys, plan.geometry)
            port.tap(plan.cellTarget(entry), target)
            val selected = port.inspect()
            validate(selected, completed)
            check(SelectionVerifier.matches(before, selected, entry.index)) {
                "Could not verify the selected cell. Use cell-first input and rescan."
            }
            val key = resolveKey(initialKeys.getValue(entry.digit), selected.keys, plan.geometry)
            coroutineContext.ensureActive()
            port.tap(key.bounds.center, target)
            val after = port.inspect()
            validate(after, completed + 1)
            progress(completed + 1, plan.entries.size)
            before = after
        }
    }

    private fun resolveKey(reference: NumberTarget, current: List<NumberTarget>, geometry: BoardGeometry): NumberTarget {
        val tolerance = minOf(geometry.bounds.width, geometry.bounds.height) / 9 * 0.10
        val matching = current.filter { candidate -> candidate.digit == reference.digit && candidate.confidence.isFinite() && candidate.confidence >= 0.90 &&
            !KeypadDetector.intersects(candidate.bounds, geometry.bounds) &&
            hypot(candidate.bounds.center.x - reference.bounds.center.x, candidate.bounds.center.y - reference.bounds.center.y) <= tolerance }
        return matching.singleOrNull() ?: error("A number button moved or disappeared. Autofill stopped.")
    }
}
