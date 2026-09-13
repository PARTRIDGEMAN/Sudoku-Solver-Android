package com.partridgeman.sudokusolver.vision

import org.junit.Assert.*
import org.junit.Test

class PixelGeometryTest {
    @Test fun pixelStorageIsImmutableAndChecksDimensions() {
        val pixels = intArrayOf(1, 2, 3, 4)
        val image = PixelImage(2, 2, pixels)
        pixels[0] = 9
        image.copyPixels()[0] = 8
        assertEquals(1, image[0, 0])
        assertThrows(IllegalArgumentException::class.java) { PixelImage(Int.MAX_VALUE, 2, pixels) }
        assertThrows(IllegalArgumentException::class.java) { image[2, 0] }
        assertThrows(IllegalArgumentException::class.java) { image.sample(Double.NaN, 0.0) }
    }

    @Test fun bilinearSamplingAndResizePreserveChannels() {
        val image = PixelImage(2, 2, intArrayOf(0xff000000.toInt(), 0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt()))
        assertEquals(0xff404040.toInt(), image.sample(0.5, 0.5))
        assertArrayEquals(image.copyPixels(), image.resize(2, 2).copyPixels())
    }

    @Test fun rectificationMapsEachCellToItsSourceCenter() {
        val horizontal = List(10) { 10.0 + it * 12 }
        val vertical = List(10) { 20.0 + it * 10 }
        val geometry = BoardGeometry(horizontal, vertical)
        geometry.cells.forEach { cell ->
            val mapped = geometry.sourcePoint((cell.column + 0.5) * 50, (cell.row + 0.5) * 50, 450)
            assertEquals(cell.center, mapped)
        }
        assertEquals(ImagePoint(20.0, 10.0), geometry.sourcePoint(0.0, 0.0, 450))
        assertEquals(ImagePoint(110.0, 118.0), geometry.sourcePoint(450.0, 450.0, 450))
        val pixels = IntArray(150 * 150) { 0xff000000.toInt() or ((it % 150) shl 16) or ((it / 150) shl 8) }
        val normalized = geometry.normalize(PixelImage(150, 150, pixels), 90)
        assertEquals(0xff150b00.toInt(), normalized[0, 0])
    }

    @Test fun geometryRejectsUnorderedOrNonFiniteBoundaries() {
        assertThrows(IllegalArgumentException::class.java) { BoardGeometry(List(10) { 0.0 }, List(10) { it.toDouble() }) }
        assertThrows(IllegalArgumentException::class.java) { BoardGeometry(List(10) { Double.NaN }, List(10) { it.toDouble() }) }
        assertThrows(IllegalArgumentException::class.java) { BoardGeometry(List(10) { it.toDouble() }, List(9) { it.toDouble() }) }
    }
}
