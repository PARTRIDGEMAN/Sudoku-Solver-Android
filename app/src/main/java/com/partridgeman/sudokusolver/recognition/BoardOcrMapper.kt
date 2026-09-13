package com.partridgeman.sudokusolver.recognition

import com.partridgeman.sudokusolver.vision.BoardGeometry
import com.partridgeman.sudokusolver.vision.ImageRect

/** A single OCR glyph in image coordinates. Kept platform-neutral for JVM regression tests. */
data class OcrToken(val text: String, val confidence: Float, val bounds: ImageRect)

/**
 * Maps OCR glyphs back to Sudoku cells using the already-detected grid geometry.
 *
 * This deliberately rejects tiny glyphs (pencil notes), glyphs that spill across
 * cells, and genuinely conflicting readings in the same cell. It never infers a
 * digit from Sudoku constraints; it only reports what OCR actually saw.
 */
object BoardOcrMapper {
    fun map(tokens: List<OcrToken>, geometry: BoardGeometry): Map<Int, DigitReading> =
        map(tokens, geometry.cells.map { it.bounds })

    internal fun map(tokens: List<OcrToken>, cells: List<ImageRect>): Map<Int, DigitReading> {
        require(cells.size == 81)
        val found = Array(81) { mutableListOf<DigitReading>() }

        for (token in tokens) {
            val text = token.text.trim()
            if (!text.matches(Regex("[1-9]")) || !token.confidence.isFinite()) continue
            val index = cells.indexOfFirst { it.contains(token.bounds.center) }
            if (index < 0) continue

            val cell = cells[index]
            val heightRatio = token.bounds.height / cell.height
            val widthRatio = token.bounds.width / cell.width
            if (heightRatio !in 0.30..0.94 || widthRatio !in 0.04..0.94) continue

            val overlapWidth = (minOf(token.bounds.right, cell.right) - maxOf(token.bounds.left, cell.left)).coerceAtLeast(0.0)
            val overlapHeight = (minOf(token.bounds.bottom, cell.bottom) - maxOf(token.bounds.top, cell.top)).coerceAtLeast(0.0)
            val tokenArea = token.bounds.width * token.bounds.height
            if (tokenArea <= 0.0 || overlapWidth * overlapHeight / tokenArea < 0.82) continue

            found[index] += DigitReading(text, token.confidence.coerceIn(0f, 1f))
        }

        return found.mapIndexedNotNull { index, readings ->
            if (readings.isEmpty()) return@mapIndexedNotNull null
            val bestPerDigit = readings.groupBy { it.text }.values.map { group -> group.maxBy { it.confidence } }
                .sortedByDescending { it.confidence }
            val best = bestPerDigit.first()
            val runnerUp = bestPerDigit.getOrNull(1)
            // Multiple OCR boxes for the same digit are harmless. Conflicting digits
            // are accepted only when the strongest reading clearly dominates.
            if (runnerUp != null && runnerUp.confidence >= best.confidence - 0.25f) null
            else index to best
        }.toMap()
    }
}
