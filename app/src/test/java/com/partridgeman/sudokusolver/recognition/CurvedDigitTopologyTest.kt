package com.partridgeman.sudokusolver.recognition

import com.partridgeman.sudokusolver.vision.PixelImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class CurvedDigitTopologyTest {
    private val black = 0xff000000.toInt()
    private val white = -1

    private fun image(draw: (Int, Int) -> Boolean): PixelImage =
        PixelImage(64, 64, IntArray(64 * 64) { index -> if (draw(index % 64, index / 64)) black else white })

    private fun ring(cx: Double, cy: Double, rx: Double, ry: Double, thickness: Double, x: Int, y: Int): Boolean {
        val outer = ((x - cx) / rx).pow(2) + ((y - cy) / ry).pow(2)
        val innerRx = rx - thickness
        val innerRy = ry - thickness
        val inner = if (innerRx > 0 && innerRy > 0)
            ((x - cx) / innerRx).pow(2) + ((y - cy) / innerRy).pow(2)
        else Double.POSITIVE_INFINITY
        return outer <= 1.0 && inner >= 1.0
    }

    @Test fun recognizesEightByTwoSeparatedHoles() {
        val mask = image { x, y ->
            ring(32.0, 21.0, 13.0, 12.0, 4.0, x, y) ||
                ring(32.0, 43.0, 14.0, 13.0, 4.0, x, y) ||
                (x in 28..36 && y in 29..35)
        }
        val result = CurvedDigitTopology.classify(mask)
        requireNotNull(result)
        assertEquals(8, result.digit)
        assertEquals(2, result.holeCount)
        assertTrue(result.confidence >= 0.93)
    }

    @Test fun recognizesSixByLowHole() {
        val mask = image { x, y ->
            ring(32.0, 40.0, 14.0, 13.0, 4.0, x, y) ||
                (x in 18..25 && y in 10..39) ||
                (x in 22..34 && y in 10..17)
        }
        val result = CurvedDigitTopology.classify(mask)
        requireNotNull(result)
        assertEquals(6, result.digit)
        assertEquals(1, result.holeCount)
        assertTrue(requireNotNull(result.holeCenterY) > 0.5)
    }

    @Test fun recognizesNineByHighHole() {
        val mask = image { x, y ->
            ring(32.0, 24.0, 14.0, 13.0, 4.0, x, y) ||
                (x in 39..46 && y in 24..54) ||
                (x in 28..42 && y in 47..54)
        }
        val result = CurvedDigitTopology.classify(mask)
        requireNotNull(result)
        assertEquals(9, result.digit)
        assertEquals(1, result.holeCount)
        assertTrue(requireNotNull(result.holeCenterY) < 0.5)
    }

    @Test fun openCurvesDoNotInventCurvedDigits() {
        val mask = image { x, y ->
            (x in 22..42 && y in 10..15) ||
                (x in 37..43 && y in 10..54) ||
                (x in 22..42 && y in 29..35) ||
                (x in 22..42 && y in 49..54)
        }
        assertNull(CurvedDigitTopology.classify(mask))
    }

    @Test fun topologyResolvesCurvedOcrConflictsButProtectsClosedTopFour() {
        val eight = CurvedDigitReading(8, 0.97, 2, null)
        val six = CurvedDigitReading(6, 0.96, 1, 0.68)
        val nine = CurvedDigitReading(9, 0.96, 1, 0.31)

        assertEquals(8, CurvedDigitTopology.reconcile(CellReading(6, 0.99, "ocr"), eight).value)
        assertEquals(6, CurvedDigitTopology.reconcile(CellReading(5, 0.93, "ocr"), six).value)
        assertEquals(9, CurvedDigitTopology.reconcile(CellReading(3, 0.91, "ocr"), nine).value)
        assertEquals(4, CurvedDigitTopology.reconcile(CellReading(4, 0.95, "ocr"), six).value)
    }
}
