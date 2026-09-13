package com.partridgeman.sudokusolver.vision

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Deterministic axis-aligned screenshot detector. Requires supported lines on both grid axes. */
class GridBoardScanner : BoardScanner {
    override fun scan(image: PixelImage): BoardDetection {
        if (min(image.width, image.height) < 90) return BoardDetection.NoBoard("Image is too small to resolve nine cells")
        val scale = min(1.0, 900.0 / max(image.width, image.height))
        val search = if (scale < 1) image.resize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()) else image
        val edges = Edges(search)
        val candidates = mutableListOf<CandidateScore>()
        val accepted = mutableListOf<Pair<BoardGeometry, Double>>()
        for (isHorizontal in listOf(true, false)) {
            val segments = edges.segments(isHorizontal)
            for (i in segments.indices) for (j in i + 1 until segments.size) {
                val top = segments[i]
                val bottom = segments[j]
                val left = max(top.start, bottom.start).toDouble()
                val right = min(top.end, bottom.end).toDouble()
                val height = bottom.position - top.position
                val width = right - left
                if (width < min(search.width, search.height) * 0.18 || abs(height / width - 1) > 0.08) continue
                if (abs(top.start - bottom.start) > width * 0.04 || abs(top.end - bottom.end) > width * 0.04) continue
                val bounds = if (isHorizontal) ImageRect(left, top.position, right, bottom.position)
                    else ImageRect(top.position, left, bottom.position, right)
                val evaluated = evaluate(edges, bounds)
                candidates.add(CandidateScore(bounds, evaluated.score, evaluated.reason))
                evaluated.geometry?.let { geometry ->
                    if (accepted.none { overlap(it.first.bounds, geometry.bounds) > 0.85 }) accepted.add(geometry to evaluated.score)
                    else {
                        val index = accepted.indexOfFirst { overlap(it.first.bounds, geometry.bounds) > 0.85 }
                        if (evaluated.score > accepted[index].second) accepted[index] = geometry to evaluated.score
                    }
                }
            }
        }
        val sx = image.width.toDouble() / search.width
        val sy = image.height.toDouble() / search.height
        val diagnostics = candidates.sortedByDescending { it.confidence }.take(16).map {
            it.copy(bounds = ImageRect(it.bounds.left * sx, it.bounds.top * sy, it.bounds.right * sx, it.bounds.bottom * sy))
        }
        if (accepted.isEmpty()) return BoardDetection.NoBoard("No complete, regular 9×9 grid with sufficient line support", diagnostics)
        if (accepted.size > 1) return BoardDetection.NoBoard("Multiple plausible boards; refusing ambiguous geometry", diagnostics)
        val (rough, _) = accepted.single()
        val sourceBounds = ImageRect(rough.bounds.left * sx, rough.bounds.top * sy, rough.bounds.right * sx, rough.bounds.bottom * sy)
        val refined = evaluate(if (search === image) edges else Edges(image), sourceBounds)
        val geometry = refined.geometry ?: return BoardDetection.NoBoard("Source-resolution refinement failed: ${refined.reason}", diagnostics)
        return BoardDetection.Detected(geometry, refined.score, geometry.normalize(image), diagnostics)
    }

    private data class Evaluation(val geometry: BoardGeometry?, val score: Double, val reason: String)
    private data class Axis(val positions: List<Double>, val support: List<Double>, val regularity: Double)

    private fun evaluate(edges: Edges, bounds: ImageRect): Evaluation {
        val horizontal = axis(edges, bounds.top, bounds.bottom, bounds.left, bounds.right, true)
        val vertical = axis(edges, bounds.left, bounds.right, bounds.top, bounds.bottom, false)
        val support = horizontal.support + vertical.support
        val mean = support.average()
        val regularity = min(horizontal.regularity, vertical.regularity)
        val score = (mean * 0.85 + regularity * 0.15).coerceIn(0.0, 1.0)
        if (support.any { it < 0.64 }) return Evaluation(null, score, "Missing grid line: minimum support ${support.minOrNull()}")
        if (regularity < 0.85) return Evaluation(null, score, "Unequal cell spacing: regularity $regularity")
        // Non-grid stripes or heavy texture must not be mistaken for grid support everywhere.
        val offGrid = (0 until 9).flatMap { index ->
            listOf(edges.support((horizontal.positions[index] + horizontal.positions[index + 1]) / 2, bounds.left, bounds.right, true),
                edges.support((vertical.positions[index] + vertical.positions[index + 1]) / 2, bounds.top, bounds.bottom, false))
        }.average()
        if (mean - offGrid < 0.35) return Evaluation(null, score, "Grid has insufficient contrast over non-grid structure")
        return Evaluation(BoardGeometry(horizontal.positions, vertical.positions), score, "Supported 10×10 boundaries")
    }

    private fun axis(edges: Edges, start: Double, end: Double, acrossStart: Double, acrossEnd: Double, horizontal: Boolean): Axis {
        val pitch = (end - start) / 9
        val positions = mutableListOf<Double>()
        val supports = mutableListOf<Double>()
        val limit = if (horizontal) edges.height else edges.width
        for (index in 0..9) {
            val expected = start + pitch * index
            // A board touching the image edge may have an outer stroke clipped. Only that boundary is inferred.
            if ((index == 0 && expected <= 2) || (index == 9 && expected >= limit - 2)) {
                positions.add(if (index == 0) 0.0 else limit.toDouble())
                supports.add(1.0)
                continue
            }
            val radius = max(2, (pitch * 0.10).roundToInt())
            val options = ((expected.roundToInt() - radius).coerceAtLeast(0)..(expected.roundToInt() + radius).coerceAtMost(limit - 1))
                .map { it to edges.support(it.toDouble(), acrossStart, acrossEnd, horizontal) }
            val peak = options.maxOf { it.second }
            val nearPeak = options.filter { it.second >= max(0.64, peak * 0.96) }
            val position = if (nearPeak.isEmpty()) expected else nearPeak.map { it.first }.average()
            positions.add(position)
            supports.add(peak)
        }
        // Fit the eight observed internal boundaries, then disambiguate decorative outer frames
        // using the grid's pitch. An outer border still needs image evidence (or actual clipping).
        val meanPosition = (1..8).map { positions[it] }.average()
        val fittedPitch = (1..8).sumOf { (it - 4.5) * (positions[it] - meanPosition) } / 42.0
        val intercept = meanPosition - 4.5 * fittedPitch
        for (index in listOf(0, 9)) {
            val expected = intercept + fittedPitch * index
            if ((index == 0 && expected <= 2) || (index == 9 && expected >= limit - 2)) {
                positions[index] = if (index == 0) 0.0 else limit.toDouble()
                supports[index] = 1.0
                continue
            }
            val radius = max(2, (pitch * 0.10).roundToInt())
            val options = ((expected.roundToInt() - radius).coerceAtLeast(0)..(expected.roundToInt() + radius).coerceAtMost(limit - 1))
                .filter { edges.support(it.toDouble(), acrossStart, acrossEnd, horizontal) >= 0.64 }
            val bands = mutableListOf<MutableList<Int>>()
            for (position in options) {
                if (bands.isEmpty() || position > bands.last().last() + 1) bands.add(mutableListOf(position)) else bands.last().add(position)
            }
            val band = bands.minByOrNull { abs(it.average() - expected) }
            positions[index] = band?.average() ?: expected.coerceIn(0.0, limit.toDouble())
            supports[index] = band?.maxOf { edges.support(it.toDouble(), acrossStart, acrossEnd, horizontal) } ?: 0.0
        }
        val deviation = positions.zipWithNext().maxOf { (a, b) -> abs((b - a) / pitch - 1) }
        return Axis(positions, supports, (1 - deviation).coerceIn(0.0, 1.0))
    }

    private fun overlap(a: ImageRect, b: ImageRect): Double {
        val intersection = max(0.0, min(a.right, b.right) - max(a.left, b.left)) * max(0.0, min(a.bottom, b.bottom) - max(a.top, b.top))
        return intersection / (a.width * a.height + b.width * b.height - intersection)
    }

    private data class Segment(val position: Double, val start: Int, val end: Int)

    private class Edges(image: PixelImage) {
        val width = image.width
        val height = image.height
        private val horizontal = BooleanArray(width * height)
        private val vertical = BooleanArray(width * height)
        init {
            val gray = IntArray(width * height) { index ->
                val pixel = image[index % width, index / width]
                val luminance = (((pixel ushr 16) and 255) * 77 + ((pixel ushr 8) and 255) * 150 + (pixel and 255) * 29) / 256
                val alpha = pixel ushr 24
                (luminance * alpha + 255 * (255 - alpha)) / 255
            }
            for (y in 0 until height) for (x in 0 until width) {
                val center = gray[y * width + x]
                for (radius in 1..2) {
                    horizontal[y * width + x] = horizontal[y * width + x] ||
                        abs(center - gray[(y - radius).coerceAtLeast(0) * width + x]) >= 12 ||
                        abs(center - gray[(y + radius).coerceAtMost(height - 1) * width + x]) >= 12
                    vertical[y * width + x] = vertical[y * width + x] ||
                        abs(center - gray[y * width + (x - radius).coerceAtLeast(0)]) >= 12 ||
                        abs(center - gray[y * width + (x + radius).coerceAtMost(width - 1)]) >= 12
                }
            }
        }

        fun support(position: Double, start: Double, end: Double, isHorizontal: Boolean): Double {
            val limit = if (isHorizontal) width else height
            val from = start.roundToInt().coerceIn(0, limit - 1)
            val to = end.roundToInt().coerceIn(from + 1, limit)
            val line = position.roundToInt().coerceIn(0, (if (isHorizontal) height else width) - 1)
            var count = 0
            for (along in from until to) {
                if (if (isHorizontal) horizontal[line * width + along] else vertical[along * width + line]) count++
            }
            return count.toDouble() / (to - from)
        }

        fun segments(isHorizontal: Boolean): List<Segment> {
            val raw = mutableListOf<Segment>()
            val minimum = min(width, height) * 0.18
            val alongSize = if (isHorizontal) width else height
            val acrossSize = if (isHorizontal) height else width
            for (y in 0 until acrossSize) {
                var start = -1
                var last = -1
                for (x in 0..alongSize) {
                    val hit = x < alongSize && (if (isHorizontal) horizontal[y * width + x] else vertical[x * width + y])
                    if (hit) {
                        if (start < 0) start = x
                        last = x
                    }
                    if (start >= 0 && (x - last > 2 || x == alongSize)) {
                        if (last - start + 1 >= minimum) raw.add(Segment(y.toDouble(), start, last + 1))
                        start = -1
                    }
                }
            }
            val groups = mutableListOf<MutableList<Segment>>()
            val gap = max(2.0, min(width, height) / 100.0)
            for (segment in raw) {
                val group = groups.lastOrNull { group ->
                    val last = group.last()
                    segment.position - last.position <= gap && abs(segment.start - last.start) < minimum * 0.08 && abs(segment.end - last.end) < minimum * 0.08
                }
                if (group == null) groups.add(mutableListOf(segment)) else group.add(segment)
            }
            return groups.map { group -> Segment(group.map { it.position }.average(), group.map { it.start }.sorted()[group.size / 2], group.map { it.end }.sorted()[group.size / 2]) }
                .sortedBy { it.position }.take(400)
        }
    }
}
