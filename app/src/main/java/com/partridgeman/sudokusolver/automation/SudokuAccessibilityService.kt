package com.partridgeman.sudokusolver.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Android boundary for cross-app gestures.
 *
 * Gesture generation is intentionally not implemented in the initial scaffold.
 * Autofill must only run after the scanner result has passed board validation and
 * a complete solution has been produced.
 */
class SudokuAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
