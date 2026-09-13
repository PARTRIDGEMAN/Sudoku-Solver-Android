package com.partridgeman.sudokusolver.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.partridgeman.sudokusolver.capture.CapturePhase
import com.partridgeman.sudokusolver.capture.CaptureState

@Composable
fun SudokuSolverApp(captureState: CaptureState, onScan: () -> Unit, onCancelCapture: () -> Unit) {
    var autoMode by rememberSaveable { mutableStateOf(false) }

    MaterialTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Sudoku Solver", style = MaterialTheme.typography.headlineMedium)
                Text("Universal on-device Sudoku detection and autofill prototype.")

                Text(if (autoMode) "Mode: Auto" else "Mode: Manual")
                Switch(checked = autoMode, onCheckedChange = { autoMode = it })

                Text("After allowing screen capture, open your Sudoku puzzle within five seconds. Return here to see the screenshot.")
                Button(onClick = onScan, enabled = !captureState.busy) {
                    Text("Scan Puzzle")
                }
                if (captureState.phase == CapturePhase.CAPTURING) {
                    Button(onClick = onCancelCapture) { Text("Cancel capture") }
                }
                Text(captureState.message)
                Text("Manual and Auto modes currently capture a preview only. Autofill is not available yet.")
                captureState.image?.let { bitmap ->
                    Text("Last successful capture · ${bitmap.width} × ${bitmap.height}")
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Last captured screen",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}
