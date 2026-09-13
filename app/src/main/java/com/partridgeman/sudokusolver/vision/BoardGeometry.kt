package com.partridgeman.sudokusolver.vision

data class ImagePoint(val x: Double, val y: Double)

data class ImageRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init { require(listOf(left, top, right, bottom).all { it.isFinite() } && right > left && bottom > top) }
    val width get() = right - left
    val height get() = bottom - top
    val center get() = ImagePoint((left + right) / 2, (top + bottom) / 2)
    fun contains(point: ImagePoint) = point.x in left..right && point.y in top..bottom
    val corners get() = listOf(ImagePoint(left, top), ImagePoint(right, top), ImagePoint(right, bottom), ImagePoint(left, bottom))
}

data class BoardCell(val row: Int, val column: Int, val bounds: ImageRect) {
    val center get() = bounds.center
}

class BoardGeometry(horizontal: List<Double>, vertical: List<Double>) {
    val horizontal: List<Double> = java.util.Collections.unmodifiableList(ArrayList(horizontal))
    val vertical: List<Double> = java.util.Collections.unmodifiableList(ArrayList(vertical))
    init {
        for (lines in listOf(this.horizontal, this.vertical)) {
            require(lines.size == 10 && lines.all { it.isFinite() && it >= 0 } && lines.zipWithNext().all { (a, b) -> b > a })
        }
    }
    val bounds = ImageRect(vertical.first(), horizontal.first(), vertical.last(), horizontal.last())
    val cells: List<BoardCell> = java.util.Collections.unmodifiableList(List(81) { index ->
        val row = index / 9
        val column = index % 9
        BoardCell(row, column, ImageRect(vertical[column], horizontal[row], vertical[column + 1], horizontal[row + 1]))
    })

    /** Piecewise rectification aligns each measured cell with an equal-sized normalized cell. */
    fun sourcePoint(normalizedX: Double, normalizedY: Double, normalizedSize: Int): ImagePoint {
        require(normalizedSize > 0 && normalizedX in 0.0..normalizedSize.toDouble() && normalizedY in 0.0..normalizedSize.toDouble())
        fun map(value: Double, lines: List<Double>): Double {
            val cell = value * 9 / normalizedSize
            val index = cell.toInt().coerceAtMost(8)
            return lines[index] + (lines[index + 1] - lines[index]) * (cell - index)
        }
        return ImagePoint(map(normalizedX, vertical), map(normalizedY, horizontal))
    }

    fun normalize(source: PixelImage, size: Int = 450): PixelImage {
        require(size > 0)
        return PixelImage(size, size, IntArray(Math.multiplyExact(size, size)) { index ->
            val point = sourcePoint(index % size + 0.5, index / size + 0.5, size)
            source.sample(point.x, point.y)
        })
    }
}

data class CandidateScore(val bounds: ImageRect, val confidence: Double, val reason: String)

sealed interface BoardDetection {
    val candidates: List<CandidateScore>
    data class NoBoard(val reason: String, override val candidates: List<CandidateScore> = emptyList()) : BoardDetection
    data class Detected(
        val geometry: BoardGeometry,
        val confidence: Double,
        val normalized: PixelImage,
        override val candidates: List<CandidateScore>,
    ) : BoardDetection
}
