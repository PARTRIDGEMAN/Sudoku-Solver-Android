package com.partridgeman.sudokusolver.recognition

import android.graphics.Bitmap
import android.graphics.Rect
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
import com.partridgeman.sudokusolver.vision.BoardGeometry
import com.partridgeman.sudokusolver.vision.GridBoardScanner
import com.partridgeman.sudokusolver.vision.ImageRect
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

        // OCR real visual context instead of rebuilding isolated synthetic glyph sheets.
        // Pass 1 sees the exact captured UI. Pass 2 sees the rectified full board, which
        // removes perspective/unequal-cell effects while preserving the original glyphs.
        val sourceText = process(bitmap)
        val sourceDigits = BoardOcrMapper.map(extractTokens(sourceText), detection.geometry)

        val normalizedBitmap = Bitmap.createBitmap(normalized.copyPixels(), normalized.width, normalized.height, Bitmap.Config.ARGB_8888)
        val normalizedText = process(normalizedBitmap, recycle = true)
        val normalizedGeometry = BoardGeometry(
            (0..9).map { it * 100.0 },
            (0..9).map { it * 100.0 },
        )
        val normalizedDigits = BoardOcrMapper.map(extractTokens(normalizedText), normalizedGeometry)

        val cells = inks.mapIndexed { index, ink ->
            val contextual = RecognitionPolicy.resolve(sourceDigits[index], normalizedDigits[index])
            val shape = if (ink.kind == InkKind.BLANK) null else CurvedDigitTopology.classify(ink.mask)
            val resolved = CurvedDigitTopology.reconcile(contextual, shape)
            when (ink.kind) {
                InkKind.BLANK -> CellReading(0, 1.0, ink.reason)
                InkKind.DIGIT -> resolved
                InkKind.AMBIGUOUS -> {
                    // A decorated/highlighted cell may confuse the ink segmentation. Rescue it
                    // when either both contextual OCR passes agree strongly, or when the glyph
                    // itself has very strong curved-digit topology. Tiny pencil-note shapes do
                    // not satisfy the topology size/hole checks.
                    val first = sourceDigits[index]
                    val second = normalizedDigits[index]
                    val contextualAgreement = first != null && second != null && first.text == second.text &&
                        resolved.accepted && resolved.confidence >= 0.90
                    val topologyRescue = shape != null && shape.confidence >= 0.94 && resolved.accepted
                    if (contextualAgreement || topologyRescue) resolved
                    else CellReading(null, resolved.confidence, ink.reason)
                }
            }
        }

        val board = RecognitionPolicy.board(cells)
        val plan = AutofillPlan.create(cells, detection.geometry)
        val accessibleMap = KeypadDetector.find(accessibleKeys, detection.geometry.bounds)
        val keyCandidates = if (accessibleMap != null) accessibleKeys else readKeypad(sourceText)
        val keys = accessibleMap ?: KeypadDetector.find(keyCandidates, detection.geometry.bounds)
        val message = if (board == null) {
            val unclear = cells.indices.filter { !cells[it].accepted }.joinToString { "R${it / 9 + 1}C${it % 9 + 1}" }
            "Unclear cells: $unclear. Keep the board unobstructed and rescan."
        } else when (SudokuSolver.analyze(board)) {
            SolveResult.InvalidGivens -> "The read digits conflict. Check the puzzle and rescan."
            SolveResult.Unsatisfiable -> "These digits have no solution. Check the puzzle and rescan."
            SolveResult.MultipleSolutions -> "More than one solution. Autofill is disabled."
            is SolveResult.Unique -> if (keys == null) "Solved uniquely. Number buttons could not be verified; solution preview is available."
                else "Solved uniquely. ${plan?.entries?.size ?: 0} blank cells ready. Use cell-first input with pencil mode off."
        }
        PuzzleAnalysis(detection, cells, plan, keys, keyCandidates, inks.map { it.background }, message)
    }

    private fun extractTokens(text: Text): List<OcrToken> = text.textBlocks.flatMap { it.lines }.flatMap { line ->
        line.elements.flatMap { element ->
            if (element.symbols.isNotEmpty()) {
                element.symbols.mapNotNull { symbol ->
                    val box = symbol.boundingBox?.takeIf { it.width() > 0 && it.height() > 0 } ?: return@mapNotNull null
                    val confidence = symbol.confidence.takeIf { it.isFinite() && it > 0f } ?: element.confidence
                    OcrToken(symbol.text, confidence, box.toImageRect())
                }
            } else {
                val box = element.boundingBox?.takeIf { it.width() > 0 && it.height() > 0 }
                if (box == null) emptyList() else listOf(OcrToken(element.text, element.confidence, box.toImageRect()))
            }
        }
    }

    private fun readKeypad(text: Text): List<NumberTarget> = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
        val digit = element.text.takeIf { it.matches(Regex("[1-9]")) }?.toInt() ?: return@mapNotNull null
        val box = element.boundingBox?.takeIf { it.width() > 0 && it.height() > 0 } ?: return@mapNotNull null
        NumberTarget(digit, box.toImageRect(), element.confidence.toDouble())
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

private fun Rect.toImageRect() = ImageRect(left.toDouble(), top.toDouble(), right.toDouble(), bottom.toDouble())

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
