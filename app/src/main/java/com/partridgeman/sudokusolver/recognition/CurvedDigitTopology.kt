package com.partridgeman.sudokusolver.recognition

import com.partridgeman.sudokusolver.vision.PixelImage
import kotlin.math.abs
import kotlin.math.max

/**
 * Deterministic shape resolver for the three Sudoku digits that generic OCR most
 * often confuses: 6, 8 and 9. It inspects enclosed background regions in the
 * already-segmented cell glyph; it does not infer a digit from Sudoku rules.
 */
data class CurvedDigitReading(
    val digit: Int,
    val confidence: Double,
    val holeCount: Int,
    val holeCenterY: Double?,
)

object CurvedDigitTopology {
    private data class Hole(val area: Int, val centerY: Double)

    fun classify(mask: PixelImage): CurvedDigitReading? {
        val width = mask.width
        val height = mask.height
        val ink = BooleanArray(width * height) { index -> isInk(mask[index % width, index / width]) }
        val points = ink.indices.filter { ink[it] }
        if (points.isEmpty()) return null

        val left = points.minOf { it % width }
        val right = points.maxOf { it % width }
        val top = points.minOf { it / width }
        val bottom = points.maxOf { it / width }
        val glyphWidth = right - left + 1
        val glyphHeight = bottom - top + 1
        if (glyphHeight < height * 0.38 || glyphWidth < width * 0.14 || glyphWidth > width * 0.92) return null

        val localWidth = glyphWidth + 2
        val localHeight = glyphHeight + 2
        val localInk = BooleanArray(localWidth * localHeight)
        for (y in top..bottom) for (x in left..right) {
            if (ink[y * width + x]) localInk[(y - top + 1) * localWidth + (x - left + 1)] = true
        }

        val visited = BooleanArray(localInk.size)
        val holes = mutableListOf<Hole>()
        val minimumHoleArea = max(4, (glyphWidth * glyphHeight * 0.012).toInt())
        val maximumHoleArea = (glyphWidth * glyphHeight * 0.42).toInt()

        for (start in localInk.indices) {
            if (localInk[start] || visited[start]) continue
            val queue = ArrayDeque<Int>()
            queue.add(start)
            visited[start] = true
            var area = 0
            var ySum = 0L
            var touchesBorder = false
            while (queue.isNotEmpty()) {
                val index = queue.removeFirst()
                val x = index % localWidth
                val y = index / localWidth
                area++
                ySum += y
                if (x == 0 || y == 0 || x == localWidth - 1 || y == localHeight - 1) touchesBorder = true
                for ((dx, dy) in NEIGHBORS) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until localWidth || ny !in 0 until localHeight) continue
                    val next = ny * localWidth + nx
                    if (!localInk[next] && !visited[next]) {
                        visited[next] = true
                        queue.add(next)
                    }
                }
            }
            if (!touchesBorder && area in minimumHoleArea..maximumHoleArea) {
                val center = ((ySum.toDouble() / area) - 1.0) / glyphHeight
                holes += Hole(area, center.coerceIn(0.0, 1.0))
            }
        }

        val meaningful = holes.sortedByDescending { it.area }.take(3)
        if (meaningful.size >= 2) {
            val two = meaningful.take(2).sortedBy { it.centerY }
            val separation = two[1].centerY - two[0].centerY
            if (separation < 0.16) return null
            val confidence = (0.93 + (separation - 0.16).coerceIn(0.0, 0.24) * 0.25).coerceAtMost(0.99)
            return CurvedDigitReading(8, confidence, meaningful.size, null)
        }

        val hole = meaningful.singleOrNull() ?: return null
        val offset = hole.centerY - 0.5
        // Fonts vary, so leave near-center single loops unresolved rather than
        // risking a 6/9 swap. Extreme upper/lower loops are highly diagnostic.
        if (abs(offset) < 0.055) return null
        val digit = if (offset > 0) 6 else 9
        val certainty = ((abs(offset) - 0.055) / 0.20).coerceIn(0.0, 1.0)
        val confidence = 0.88 + certainty * 0.10
        return CurvedDigitReading(digit, confidence, 1, hole.centerY)
    }

    /**
     * Fuse topology with contextual OCR. Shape evidence is allowed to resolve
     * disagreements inside {6,8,9}; it only overrides a different digit when the
     * OCR read is weak and the topology evidence is exceptionally strong.
     */
    fun reconcile(ocr: CellReading, shape: CurvedDigitReading?): CellReading {
        shape ?: return ocr
        val curved = setOf(6, 8, 9)
        val value = ocr.value

        if (ocr.accepted && value == shape.digit) {
            return CellReading(
                shape.digit,
                maxOf(ocr.confidence, 1.0 - (1.0 - ocr.confidence) * (1.0 - shape.confidence)),
                "OCR and curved-digit topology agree",
            )
        }

        if (value in curved && shape.confidence >= 0.88) {
            return CellReading(shape.digit, shape.confidence, "Curved-digit topology resolved OCR ambiguity")
        }

        if (!ocr.accepted && shape.confidence >= 0.94) {
            return CellReading(shape.digit, shape.confidence, "Strong curved-digit topology rescued an unclear OCR cell")
        }

        if (ocr.accepted && value !in curved && ocr.confidence < 0.80 && shape.confidence >= 0.97) {
            return CellReading(shape.digit, shape.confidence, "Very strong curved-digit topology overrode weak OCR")
        }

        return ocr
    }

    private fun isInk(pixel: Int): Boolean {
        val red = pixel ushr 16 and 255
        val green = pixel ushr 8 and 255
        val blue = pixel and 255
        return (red + green + blue) / 3 < 128
    }

    private val NEIGHBORS = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
}
