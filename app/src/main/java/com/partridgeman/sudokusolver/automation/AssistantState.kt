package com.partridgeman.sudokusolver.automation

import com.partridgeman.sudokusolver.recognition.PuzzleAnalysis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AssistantState(
    val connected: Boolean = false,
    val busy: Boolean = false,
    val filling: Boolean = false,
    val auto: Boolean = false,
    val reviewed: Boolean = false,
    val analysis: PuzzleAnalysis? = null,
    val message: String = "Enable the accessibility assistant to scan from a movable overlay.",
)

object AssistantStore {
    private val mutable = MutableStateFlow(AssistantState())
    val state = mutable.asStateFlow()
    fun update(change: (AssistantState) -> AssistantState) = mutable.update(change)
}

data class OverlayPosition(val x: Int, val y: Int) {
    fun clamped(screenWidth: Int, screenHeight: Int, panelWidth: Int, panelHeight: Int) = OverlayPosition(
        x.coerceIn(0, (screenWidth - panelWidth).coerceAtLeast(0)),
        y.coerceIn(0, (screenHeight - panelHeight).coerceAtLeast(0)),
    )
}
