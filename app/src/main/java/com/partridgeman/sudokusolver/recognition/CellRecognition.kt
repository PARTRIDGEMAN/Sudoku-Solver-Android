package com.partridgeman.sudokusolver.recognition

import com.partridgeman.sudokusolver.model.SudokuBoard
import com.partridgeman.sudokusolver.vision.PixelImage
import kotlin.math.abs
import kotlin.math.max

data class DigitReading(val text: String, val confidence: Float)
data class CellReading(val value: Int?, val confidence: Double, val reason: String) {
    val accepted: Boolean get() = value != null && confidence.isFinite() && confidence >= MIN_CONFIDENCE
    companion object { const val MIN_CONFIDENCE = 0.65 }
}

object RecognitionPolicy {
    private const val SINGLE_PASS_MIN = 0.72f
    private const val AGREEMENT_SOURCE_MIN = 0.25f

    /** Legacy strict helper retained for tests/callers that explicitly require two strong matching reads. */
    fun agree(first: DigitReading?, second: DigitReading?): CellReading {
        if (first == null || second == null || first.text != second.text || !first.text.matches(Regex("[1-9]"))) {
            return CellReading(null, 0.0, "Digit readings disagree or contain notes")
        }
        val confidence = minOf(first.confidence, second.confidence).toDouble()
        return if (confidence.isFinite() && confidence >= CellReading.MIN_CONFIDENCE)
            CellReading(first.text.toInt(), confidence, "Two matching strong digit readings")
        else CellReading(null, confidence.takeIf { it.isFinite() } ?: 0.0, "Digit confidence is too low")
    }

    /**
     * Fuse two contextual OCR passes without requiring both engines to report 90%+.
     * Agreement is stronger evidence than either confidence alone; a single read
     * is accepted only when it is independently strong. Conflicting reads fail closed.
     */
    fun resolve(first: DigitReading?, second: DigitReading?): CellReading {
        fun valid(reading: DigitReading?): DigitReading? = reading?.takeIf {
            it.text.matches(Regex("[1-9]")) && it.confidence.isFinite() && it.confidence in 0f..1f
        }
        val a = valid(first)
        val b = valid(second)
        if (a == null && b == null) return CellReading(null, 0.0, "No contextual digit reading")

        if (a != null && b != null && a.text == b.text) {
            val low = minOf(a.confidence, b.confidence)
            val combined = 1.0 - (1.0 - a.confidence) * (1.0 - b.confidence)
            return if (low >= AGREEMENT_SOURCE_MIN && combined >= CellReading.MIN_CONFIDENCE)
                CellReading(a.text.toInt(), combined, "Contextual OCR passes agree")
            else CellReading(null, combined, "Matching OCR readings are still too weak")
        }

        if (a == null || b == null) {
            val only = a ?: b!!
            return if (only.confidence >= SINGLE_PASS_MIN)
                CellReading(only.text.toInt(), only.confidence.toDouble(), "One strong contextual OCR reading")
            else CellReading(null, only.confidence.toDouble(), "Only one weak contextual OCR reading")
        }

        val high = if (a.confidence >= b.confidence) a else b
        val low = if (high === a) b else a
        return if (high.confidence >= 0.96f && high.confidence - low.confidence >= 0.50f)
            CellReading(high.text.toInt(), high.confidence.toDouble(), "One contextual OCR reading strongly dominates")
        else CellReading(null, maxOf(a.confidence, b.confidence).toDouble(), "Contextual OCR readings disagree")
    }

    fun board(readings: List<CellReading>): SudokuBoard? = if (readings.size == 81 && readings.all { it.accepted && it.value in 0..9 })
        SudokuBoard(readings.map { requireNotNull(it.value) }) else null
}

enum class InkKind { BLANK, DIGIT, AMBIGUOUS }
data class CellInk(val kind: InkKind, val mask: PixelImage, val contrast: PixelImage, val background: Int, val reason: String)

/** Uses only the inner cell, estimates background from its perimeter, and rejects small note clusters. */
object CellInkAnalyzer {
    fun analyze(image: PixelImage): CellInk {
        val width = image.width
        val height = image.height
        val border = buildList {
            for (x in 0 until width) { add(image[x, 0]); add(image[x, height - 1]) }
            for (y in 0 until height) { add(image[0, y]); add(image[width - 1, y]) }
        }
        val background = (0..16 step 8).associateWith { shift -> border.map { it ushr shift and 255 }.sorted()[border.size / 2] }
        val backgroundColor = 0xff000000.toInt() or (background.getValue(16) shl 16) or (background.getValue(8) shl 8) or background.getValue(0)
        val differences = IntArray(width * height) { index ->
            val pixel = image[index % width, index / width]
            (0..16 step 8).maxOf { shift -> abs((pixel ushr shift and 255) - background.getValue(shift)) }
        }
        val ink = differences.map { it >= 24 }.toBooleanArray()
        val mask = PixelImage(width, height, IntArray(ink.size) { if (ink[it]) 0xff000000.toInt() else -1 })
        val contrast = PixelImage(width, height, IntArray(ink.size) {
            val value = 255 - (differences[it] * 4).coerceAtMost(255)
            0xff000000.toInt() or (value shl 16) or (value shl 8) or value
        })
        // Even weak visible marks prohibit treating a cell as blank.
        if (differences.count { it > 10 } <= 1 && differences.maxOrNull()!! < 24)
            return CellInk(InkKind.BLANK, mask, contrast, backgroundColor, "Uniform empty cell")
        val visited = BooleanArray(ink.size)
        data class Component(val area: Int, val width: Int, val height: Int, val touchesEdge: Boolean)
        val components = mutableListOf<Component>()
        for (start in ink.indices) {
            if (!ink[start] || visited[start]) continue
            val queue = ArrayDeque<Int>()
            queue.add(start)
            visited[start] = true
            var area = 0
            var left = width
            var right = 0
            var top = height
            var bottom = 0
            while (queue.isNotEmpty()) {
                val index = queue.removeFirst()
                val x = index % width
                val y = index / width
                area++
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
                for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val next = ny * width + nx
                    if (ink[next] && !visited[next]) { visited[next] = true; queue.add(next) }
                }
            }
            if (area >= max(2, ink.size / 500)) components.add(Component(area, right - left + 1, bottom - top + 1,
                left == 0 || top == 0 || right == width - 1 || bottom == height - 1))
        }
        val main = components.maxByOrNull { it.area }
        val digit = main != null && main.height >= height * 0.40 && main.width <= width * 0.95 && !main.touchesEdge &&
            components.count { it.area > main.area * 0.12 } == 1 && ink.count { it }.toDouble() / ink.size < 0.65
        return CellInk(if (digit) InkKind.DIGIT else InkKind.AMBIGUOUS, mask, contrast, backgroundColor,
            if (digit) "One full-sized glyph" else "Faint mark, pencil notes, clipped digit, or cell decoration")
    }
}

/** Inspect the usual interior first; retry clipped glyphs with a wider, independently checked crop. */
object CellCropAnalyzer {
    fun analyze(normalized: PixelImage, index: Int): CellInk {
        require(normalized.width == 900 && normalized.height == 900 && index in 0..80)
        fun crop(margin: Int): CellInk {
            val size = 100 - margin * 2
            return CellInkAnalyzer.analyze(PixelImage(size, size, IntArray(size * size) { pixel ->
                normalized[index % 9 * 100 + margin + pixel % size, index / 9 * 100 + margin + pixel / size]
            }))
        }
        val inner = crop(8)
        if (inner.kind != InkKind.AMBIGUOUS) return inner
        for (margin in listOf(7, 6)) {
            val wider = crop(margin)
            if (wider.kind == InkKind.DIGIT) return wider
        }
        return inner
    }
}
