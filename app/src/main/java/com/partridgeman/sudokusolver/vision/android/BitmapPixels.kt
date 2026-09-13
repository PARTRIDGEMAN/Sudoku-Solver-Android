package com.partridgeman.sudokusolver.vision.android

import android.graphics.Bitmap
import android.os.Build
import com.partridgeman.sudokusolver.vision.PixelImage

/** Android adaptation only; the detector and its image/geometry types have no framework dependencies. */
fun Bitmap.toPixelImage(): PixelImage {
    val readable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE) requireNotNull(copy(Bitmap.Config.ARGB_8888, false)) else this
    return try {
        val pixels = IntArray(Math.multiplyExact(width, height))
        readable.getPixels(pixels, 0, width, 0, 0, width, height)
        PixelImage(width, height, pixels)
    } finally {
        if (readable !== this) readable.recycle()
    }
}
