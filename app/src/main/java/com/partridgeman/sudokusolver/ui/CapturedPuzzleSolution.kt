package com.partridgeman.sudokusolver.ui

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.partridgeman.sudokusolver.recognition.OfflinePuzzleReader
import com.partridgeman.sudokusolver.recognition.PuzzleAnalysis
import kotlinx.coroutines.CancellationException

@Composable
fun CapturedPuzzleSolution(bitmap: Bitmap) {
    val reader = remember { OfflinePuzzleReader() }
    DisposableEffect(reader) { onDispose { reader.close() } }
    var message by remember(bitmap) { mutableStateOf("Reading digits locally…") }
    val analysis by produceState<PuzzleAnalysis?>(null, bitmap) {
        try { value = reader.read(bitmap); message = value!!.message }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { message = error.message ?: "Could not read this puzzle." }
    }
    Text(message)
    analysis?.let {
        Text("Scanned digits (? means uncertain)")
        PuzzleDigits(it.cells.map { cell -> cell.value })
        it.plan?.let { plan ->
            Text("Solution")
            PuzzleDigits(it.cells.map { cell -> cell.value }, plan.solution.cells)
        }
    }
}
