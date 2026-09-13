package com.partridgeman.sudokusolver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.partridgeman.sudokusolver.capture.CapturePhase
import com.partridgeman.sudokusolver.capture.CaptureState
import com.partridgeman.sudokusolver.automation.AssistantState

@Composable
fun SudokuSolverApp(captureState: CaptureState, onScan: () -> Unit, onCancelCapture: () -> Unit,
    assistantState: AssistantState, onEnableAssistant: () -> Unit, onShowOverlay: () -> Unit) {

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
                Text("Scan, solve, and fill Sudoku puzzles locally on your phone.")
                Text("Enable Sudoku Assistant in Accessibility settings. It reads the visible puzzle and taps blank cells only after a verified solution. Screenshots stay on your phone.")
                Button(onClick = onEnableAssistant) { Text("Accessibility settings") }
                Button(onClick = onShowOverlay, enabled = assistantState.connected) { Text("Show movable assistant") }
                Text("In your Sudoku app, drag the overlay by its title. Move it clear of the board and number buttons. Use cell-first input with pencil mode off. Scan, check the digits, then Autofill. Auto mode fills after you tap Scan.")
                Text("Live overlay scanning and autofill require Android 11+. Other versions can use the capture and solution preview below.")
                Text(assistantState.message)

                Text("After allowing screen capture, open your Sudoku puzzle within five seconds. Return here to see the screenshot.")
                Button(onClick = onScan, enabled = !captureState.busy) {
                    Text("Scan Puzzle")
                }
                if (captureState.phase == CapturePhase.CAPTURING) {
                    Button(onClick = onCancelCapture) { Text("Cancel capture") }
                }
                Text(captureState.message)
                captureState.image?.let { bitmap ->
                    Text("Last successful capture · ${bitmap.width} × ${bitmap.height}")
                    androidx.compose.runtime.key(bitmap) {
                        BoardDetectionPreview(bitmap)
                        CapturedPuzzleSolution(bitmap)
                    }
                }
            }
        }
    }
}
