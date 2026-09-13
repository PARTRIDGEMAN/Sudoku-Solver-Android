package com.partridgeman.sudokusolver.capture

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RgbaFrameTest {
    @Test
    fun convertsChannelsAndAlphaWithoutChangingBufferPosition() {
        val bytes = byteArrayOf(0, 1, 2, 3, 4, 0xff.toByte(), 0x80.toByte(), 0, 0xff.toByte())
        val buffer = ByteBuffer.wrap(bytes).apply { position(1) }.asReadOnlyBuffer()
        assertArrayEquals(intArrayOf(0x04010203, 0xffff8000.toInt()), RgbaFrame.toArgb(buffer, 2, 1, 4, 8))
        assertEquals(1, buffer.position())
    }

    @Test
    fun handlesPixelAndRowPaddingWithNoPaddingAfterLastPixel() {
        val buffer = ByteBuffer.allocateDirect(24)
        // Two 2-pixel rows, pixel stride 6, row stride 14; final row needs only 10 bytes.
        listOf(0, 6, 14, 20).forEachIndexed { index, offset ->
            buffer.put(offset, (index + 1).toByte())
            buffer.put(offset + 1, 0)
            buffer.put(offset + 2, 0)
            buffer.put(offset + 3, 0xff.toByte())
        }
        assertArrayEquals(intArrayOf(0xff010000.toInt(), 0xff020000.toInt(), 0xff030000.toInt(), 0xff040000.toInt()),
            RgbaFrame.toArgb(buffer, 2, 2, 6, 14))
    }

    @Test
    fun respectsLimitInsteadOfReadingUnusedCapacity() {
        val buffer = ByteBuffer.allocate(32).apply { limit(7) }
        assertThrows(IllegalArgumentException::class.java) { RgbaFrame.toArgb(buffer, 2, 1, 4, 8) }
    }

    @Test
    fun rejectsInvalidOrOverflowingGeometry() {
        val buffer = ByteBuffer.allocate(16)
        listOf(
            intArrayOf(0, 1, 4, 4), intArrayOf(1, -1, 4, 4),
            intArrayOf(1, 1, 3, 4), intArrayOf(2, 2, 4, 7),
            intArrayOf(Int.MAX_VALUE, Int.MAX_VALUE, 4, Int.MAX_VALUE),
        ).forEach { (width, height, pixelStride, rowStride) ->
            assertThrows(IllegalArgumentException::class.java) {
                RgbaFrame.toArgb(buffer, width, height, pixelStride, rowStride)
            }
        }
    }

    @Test
    fun preservesPixelsAcrossDifferentResolutionsAndStrides() {
        for ((width, height) in listOf(1 to 1, 9 to 17, 113 to 57)) {
            for (stride in listOf(4, 8)) {
                val rowStride = width * stride + 12
                val buffer = ByteBuffer.allocate(rowStride * height)
                val expected = IntArray(width * height) { index ->
                    val offset = (index / width) * rowStride + (index % width) * stride
                    val red = index and 0xff
                    val green = (index / 3) and 0xff
                    val blue = (index / 7) and 0xff
                    buffer.put(offset, red.toByte()).put(offset + 1, green.toByte())
                        .put(offset + 2, blue.toByte()).put(offset + 3, 0xff.toByte())
                    (0xff shl 24) or (red shl 16) or (green shl 8) or blue
                }
                assertArrayEquals(expected, RgbaFrame.toArgb(buffer, width, height, stride, rowStride))
            }
        }
    }
}
