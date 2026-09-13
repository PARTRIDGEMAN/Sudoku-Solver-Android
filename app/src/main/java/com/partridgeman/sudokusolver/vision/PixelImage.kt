package com.partridgeman.sudokusolver.vision

import kotlin.math.floor
import kotlin.math.roundToInt

/** Immutable, platform-neutral packed ARGB pixels. Coordinates use pixel centers at integer positions. */
class PixelImage(val width: Int, val height: Int, pixels: IntArray) {
    private val pixels = pixels.copyOf()
    init {
        require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong())
    }
    operator fun get(x: Int, y: Int): Int {
        require(x in 0 until width && y in 0 until height)
        return pixels[y * width + x]
    }
    fun copyPixels(): IntArray = pixels.copyOf()

    fun sample(x: Double, y: Double): Int {
        require(x.isFinite() && y.isFinite())
        val sx = x.coerceIn(0.0, width - 1.0)
        val sy = y.coerceIn(0.0, height - 1.0)
        val left = floor(sx).toInt()
        val top = floor(sy).toInt()
        val right = (left + 1).coerceAtMost(width - 1)
        val bottom = (top + 1).coerceAtMost(height - 1)
        val fx = sx - left
        val fy = sy - top
        var result = 0
        for (shift in 0..24 step 8) {
            fun channel(px: Int, py: Int) = (get(px, py) ushr shift) and 255
            val a = channel(left, top) * (1 - fx) + channel(right, top) * fx
            val b = channel(left, bottom) * (1 - fx) + channel(right, bottom) * fx
            result = result or ((a * (1 - fy) + b * fy).roundToInt() shl shift)
        }
        return result
    }

    fun resize(newWidth: Int, newHeight: Int): PixelImage {
        require(newWidth > 0 && newHeight > 0)
        return PixelImage(newWidth, newHeight, IntArray(Math.multiplyExact(newWidth, newHeight)) { index ->
            sample((index % newWidth + 0.5) * width / newWidth - 0.5,
                (index / newWidth + 0.5) * height / newHeight - 0.5)
        })
    }
}
