package com.partridgeman.sudokusolver.vision

/** Geometry only. Digit recognition and permission to autofill belong to later stages. */
fun interface BoardScanner {
    fun scan(image: PixelImage): BoardDetection
}
