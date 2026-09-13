package com.partridgeman.sudokusolver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SudokuSolverApp() {
    var autoMode by remember { mutableStateOf(false) }

    MaterialTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Sudoku Solver", style = MaterialTheme.typography.headlineMedium)
                Text("Universal on-device Sudoku detection and autofill prototype.")

                Text(if (autoMode) "Mode: Auto" else "Mode: Manual")
                Switch(checked = autoMode, onCheckedChange = { autoMode = it })

                Button(onClick = { /* Screen capture pipeline is implemented in Phase 2. */ }) {
                    Text(if (autoMode) "Scan & Autofill" else "Scan Puzzle")
                }

                Text("Phase 1 scaffold: solver core and Android service boundary are ready.")
            }
        }
    }
}
