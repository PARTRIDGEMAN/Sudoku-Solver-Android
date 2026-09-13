package com.partridgeman.sudokusolver.automation

import com.partridgeman.sudokusolver.vision.BoardGeometry
import com.partridgeman.sudokusolver.vision.ImageRect
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KeypadDetectorRegressionTest {
    private val geometry = BoardGeometry(List(10) { 100.0 + it * 50 }, List(10) { 20.0 + it * 50 })
    private val keypad = (1..9).map { digit ->
        NumberTarget(
            digit,
            ImageRect(15.0 + (digit - 1) * 50, 630.0, 45.0 + (digit - 1) * 50, 670.0),
            0.92,
        )
    }

    @Test
    fun ignoresUnrelatedNumericUiOutsideTheKeypadZone() {
        // Simulates score/timer/mistake/battery/call-chip digits elsewhere on screen.
        val noise = listOf(
            NumberTarget(1, ImageRect(40.0, 20.0, 65.0, 45.0), 0.99),
            NumberTarget(2, ImageRect(180.0, 25.0, 205.0, 50.0), 0.98),
            NumberTarget(4, ImageRect(330.0, 12.0, 355.0, 37.0), 0.97),
            NumberTarget(8, ImageRect(440.0, 30.0, 465.0, 55.0), 0.96),
        )

        val result = KeypadDetector.find(keypad + noise, geometry.bounds)
        assertNotNull(result)
    }

    @Test
    fun coherentKeypadDoesNotNeedNinetyPercentPerDigit() {
        val moderateConfidence = keypad.map { it.copy(confidence = 0.68) }
        assertNotNull(KeypadDetector.find(moderateConfidence, geometry.bounds))
    }

    @Test
    fun twoDistinctValidKeypadsStillFailClosed() {
        val second = keypad.map { key ->
            key.copy(bounds = ImageRect(key.bounds.left, key.bounds.top + 90, key.bounds.right, key.bounds.bottom + 90))
        }
        assertNull(KeypadDetector.find(keypad + second, geometry.bounds))
    }
}
