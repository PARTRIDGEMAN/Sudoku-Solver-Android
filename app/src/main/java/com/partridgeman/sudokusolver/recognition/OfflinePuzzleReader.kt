package com.partridgeman.sudokusolver.recognition

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.partridgeman.sudokusolver.automation.AutofillPlan
import com.partridgeman.sudokusolver.automation.KeypadDetector
import com.partridgeman.sudokusolver.automation.NumberTarget
import com.partridgeman.sudokusolver.solver.SolveResult
import com.partridgeman.sudokusolver.solver.SudokuSolver
import com.partridgeman.sudokusolver.vision.BoardDetection
import com.partridgeman.sudokusolver.vision.GridBoardScanner
import com.partridgeman.sudokusolver.vision.ImageRect
import com.partridgeman.sudokusolver.vision.PixelImage
import com.partridgeman.sudokusolver.vision.android.toPixelImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PuzzleAnalysis(val detection: BoardDetection.Detected, val cells: List<CellReading>,
    val plan: AutofillPlan?, val keypad: Map<Int, NumberTarget>?, val keyCandidates: List<NumberTarget>,
    val backgrounds: List<Int>, val message: String)

/** The bundled model runs locally. Recognition never dispatches gestures or consumes capture tokens. */
class OfflinePuzzleReader : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(bitmap: Bitmap, accessibleKeys: List<NumberTarget> = emptyList()): PuzzleAnalysis = withContext(Dispatchers.Default) {
        val pixels = bitmap.toPixelImage()
        val detection = GridBoardScanner().scan(pixels) as? BoardDetection.Detected
            ?: throw IllegalStateException("No clear Sudoku grid found. Keep the whole board visible and scan again.")
        val normalized = detection.geometry.normalize(pixels, 900)
        val inks = (0 until 81).map { CellCropAnalyzer.analyze(normalized, it) }
        val first = readSheet(inks, 96, false)
        val second = readSheet(inks, 128, true)
        val cells = inks.mapIndexed { index, ink ->
            when (ink.kind) {
                InkKind.BLANK -> CellReading(0, 1.0, ink.reason)
                InkKind.AMBIGUOUS -> CellReading(null, 0.0, ink.reason)
                InkKind.DIGIT -> RecognitionPolicy.agree(first[index], second[index])
            }
        }
        val board = RecognitionPolicy.board(cells)
        val plan = AutofillPlan.create(cells, detection.geometry)
        val accessibleMap = KeypadDetector.find(accessibleKeys, detection.geometry.bounds)
        val keyCandidates = if (accessibleMap != null) accessibleKeys else readKeypad(bitmap)
        val keys = accessibleMap ?: KeypadDetector.find(keyCandidates, detection.geometry.bounds)
        val message = if (board == null) {
            val unclear = cells.indices.filter { !cells[it].accepted }.joinToString { "R${it / 9 + 1}C${it % 9 + 1}" }
            "Unclear cells: $unclear. Remove pencil notes or highlights and rescan."
        } else when (SudokuSolver.analyze(board)) {
            SolveResult.InvalidGivens -> "The read digits conflict. Check the puzzle and rescan."
            SolveResult.Unsatisfiable -> "These digits have no solution. Check the puzzle and rescan."
            SolveResult.MultipleSolutions -> "More than one solution. Autofill is disabled."
            is SolveResult.Unique -> if (keys == null) "Solved uniquely. Number buttons could not be verified; solution preview is available."
                else "Solved uniquely. ${plan?.entries?.size ?: 0} blank cells ready. Use cell-first input with pencil mode off."
        }
        PuzzleAnalysis(detection, cells, plan, keys, keyCandidates, inks.map { it.background }, message)
    }

    private suspend fun readSheet(inks: List<CellInk>, tile: Int, grayscale: Boolean): Map<Int, DigitReading> {
        coroutineContext.ensureActive()
        val side = tile * 9
        val contentSize = tile * 2 / 3
        val inset = (tile - contentSize) / 2
        val pixels = IntArray(side * side) { -1 }
        inks.forEachIndexed { index, ink ->
            if (ink.kind == InkKind.DIGIT) {
                val resized = (if (grayscale) ink.contrast else ink.mask).resize(contentSize, contentSize)
                for (y in 0 until contentSize) for (x in 0 until contentSize) {
                    pixels[(index / 9 * tile + inset + y) * side + index % 9 * tile + inset + x] = resized[x, y]
                }
            }
        }
        val sheet = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
        val text = process(sheet, recycle = true)
        data class Token(val text: String, val confidence: Float, val box: android.graphics.Rect)
        val tokens = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.flatMap { element ->
            if (element.symbols.isNotEmpty()) element.symbols.mapNotNull { symbol ->
                symbol.boundingBox?.let { Token(symbol.text, minOf(symbol.confidence, element.confidence), it) }
            } else listOfNotNull(element.boundingBox?.let { Token(element.text, element.confidence, it) })
        }
        return tokens.filter { it.box.centerX() in 0 until side && it.box.centerY() in 0 until side }
            .groupBy { it.box.centerY() / tile * 9 + it.box.centerX() / tile }
            .mapNotNull { (index, found) ->
                val token = found.singleOrNull() ?: return@mapNotNull null
                val left = index % 9 * tile; val top = index / 9 * tile
                if (token.box.left < left || token.box.right > left + tile || token.box.top < top || token.box.bottom > top + tile) null
                else index to DigitReading(token.text, token.confidence)
            }.toMap()
    }

    private suspend fun readKeypad(bitmap: Bitmap): List<NumberTarget> = process(bitmap).textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
        val digit = element.text.takeIf { it.matches(Regex("[1-9]")) }?.toInt() ?: return@mapNotNull null
        val box = element.boundingBox?.takeIf { it.width() > 0 && it.height() > 0 } ?: return@mapNotNull null
        NumberTarget(digit, ImageRect(box.left.toDouble(), box.top.toDouble(), box.right.toDouble(), box.bottom.toDouble()), element.confidence.toDouble())
    }

    private suspend fun process(bitmap: Bitmap, recycle: Boolean = false): Text {
        // The SDK may finish after coroutine cancellation; recycle owned images only once its task completes.
        val task = try { recognizer.process(InputImage.fromBitmap(bitmap, 0)) } catch (error: Exception) {
            if (recycle) bitmap.recycle()
            throw error
        }
        if (recycle) task.addOnCompleteListener { bitmap.recycle() }
        return task.awaitResult()
    }

    override fun close() = recognizer.close()
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
