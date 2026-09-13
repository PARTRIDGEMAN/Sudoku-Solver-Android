package com.partridgeman.sudokusolver.capture

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class CapturePhase { IDLE, CONSENT, CAPTURING }

data class CaptureState(
    val phase: CapturePhase = CapturePhase.IDLE,
    val image: Bitmap? = null,
    val message: String = "Ready to capture a puzzle.",
) {
    val busy: Boolean get() = phase != CapturePhase.IDLE
}

/** Process-local preview only: screenshots and consent tokens are never persisted. */
object CaptureStore {
    private val mutableState = MutableStateFlow(CaptureState())
    val state = mutableState.asStateFlow()

    fun awaitingConsent() {
        mutableState.update { it.copy(phase = CapturePhase.CONSENT, message = "Waiting for screen capture consent…") }
    }

    fun capturing() {
        mutableState.update {
            it.copy(phase = CapturePhase.CAPTURING, message = "Open your puzzle. Capturing after five seconds…")
        }
    }

    fun complete(image: Bitmap) {
        mutableState.value = CaptureState(image = image, message = "Screenshot captured. Preview only; no puzzle recognition yet.")
    }

    fun failed(message: String) {
        // A failed attempt retains the previous successful preview, explicitly labelled in the UI.
        mutableState.update { it.copy(phase = CapturePhase.IDLE, message = message) }
    }
}
