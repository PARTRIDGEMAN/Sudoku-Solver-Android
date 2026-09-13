package com.partridgeman.sudokusolver.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.partridgeman.sudokusolver.vision.BoardDetection
import com.partridgeman.sudokusolver.vision.GridBoardScanner
import com.partridgeman.sudokusolver.vision.android.toPixelImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min

@Composable
fun BoardDetectionPreview(bitmap: Bitmap) {
    val result by produceState<BoardDetection?>(null, bitmap) {
        value = null
        value = withContext(Dispatchers.Default) {
            try {
                GridBoardScanner().scan(bitmap.toPixelImage()).also { detection ->
                    Log.d("BoardDetection", "${detection::class.simpleName}: ${detection.candidates}")
                }
            } catch (error: Exception) {
                Log.w("BoardDetection", "Detection failed", error)
                BoardDetection.NoBoard("Could not inspect this screenshot")
            }
        }
    }
    when (val detection = result) {
        null -> Text("Looking for a Sudoku grid…")
        is BoardDetection.NoBoard -> Text("No board accepted: ${detection.reason}")
        is BoardDetection.Detected -> Text("9×9 grid detected · confidence ${String.format(Locale.ROOT, "%.0f%%", detection.confidence * 100)}")
    }
    Box(Modifier.fillMaxWidth()) {
        Image(bitmap.asImageBitmap(), "Last captured screen with detected grid overlay",
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp), contentScale = ContentScale.Fit)
        Canvas(Modifier.matchParentSize()) {
            val detection = result as? BoardDetection.Detected ?: return@Canvas
            val scale = min(size.width / bitmap.width, size.height / bitmap.height)
            val left = (size.width - bitmap.width * scale) / 2
            val top = (size.height - bitmap.height * scale) / 2
            fun point(x: Double, y: Double) = Offset(left + x.toFloat() * scale, top + y.toFloat() * scale)
            val geometry = detection.geometry
            geometry.horizontal.forEachIndexed { index, y ->
                drawLine(Color.Green, point(geometry.bounds.left, y), point(geometry.bounds.right, y), if (index % 3 == 0) 2.dp.toPx() else 1.dp.toPx())
            }
            geometry.vertical.forEachIndexed { index, x ->
                drawLine(Color.Green, point(x, geometry.bounds.top), point(x, geometry.bounds.bottom), if (index % 3 == 0) 2.dp.toPx() else 1.dp.toPx())
            }
            geometry.cells.forEach { drawCircle(Color.Magenta, 1.5.dp.toPx(), point(it.center.x, it.center.y)) }
        }
    }
}
