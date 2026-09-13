package com.partridgeman.sudokusolver.capture

import java.nio.ByteBuffer

/** Converts an RGBA plane to packed ARGB, excluding row padding and respecting buffer bounds. */
object RgbaFrame {
    fun toArgb(buffer: ByteBuffer, width: Int, height: Int, pixelStride: Int, rowStride: Int): IntArray {
        require(width > 0 && height > 0) { "Empty capture dimensions" }
        require(pixelStride >= 4) { "Expected an RGBA plane" }
        val rowBytes = (width - 1L) * pixelStride + 4
        require(rowStride.toLong() >= rowBytes) { "Overlapping capture rows" }
        val requiredBytes = (height - 1L) * rowStride + rowBytes
        require(requiredBytes <= buffer.remaining().toLong()) { "Truncated capture plane" }
        val count = width.toLong() * height
        require(count <= Int.MAX_VALUE) { "Capture is too large" }
        val pixels = IntArray(count.toInt())
        val origin = buffer.position()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val offset = origin + y * rowStride + x * pixelStride
                val red = buffer.get(offset).toInt() and 0xff
                val green = buffer.get(offset + 1).toInt() and 0xff
                val blue = buffer.get(offset + 2).toInt() and 0xff
                val alpha = buffer.get(offset + 3).toInt() and 0xff
                pixels[y * width + x] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        return pixels
    }
}
