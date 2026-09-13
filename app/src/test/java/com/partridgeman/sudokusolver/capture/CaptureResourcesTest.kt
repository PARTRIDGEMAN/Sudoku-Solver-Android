package com.partridgeman.sudokusolver.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CaptureResourcesTest {
    @Test
    fun releasesDependentResourcesInReverseOrderExactlyOnce() {
        val released = mutableListOf<String>()
        val resources = CaptureResources()
        listOf("projection", "reader", "display").forEach { name -> resources.own(name) { released.add(it) } }
        resources.close()
        resources.close()
        assertEquals(listOf("display", "reader", "projection"), released)
    }

    @Test
    fun releasesEarlierResourcesWhenSetupFails() {
        val released = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            CaptureResources().use { resources ->
                resources.own("projection") { released.add(it) }
                resources.own("reader") { released.add(it) }
                throw IllegalStateException("Virtual display creation failed")
            }
        }
        assertEquals(listOf("reader", "projection"), released)
    }

    @Test
    fun cleanupFailureDoesNotPreventRemainingReleasesOrRepeatThem() {
        val released = mutableListOf<String>()
        val resources = CaptureResources()
        resources.own("projection") { released.add(it); throw IllegalArgumentException("stop") }
        resources.own("reader") { released.add(it) }
        resources.own("display") { released.add(it); throw IllegalStateException("release") }
        val error = assertThrows(IllegalStateException::class.java) { resources.close() }
        resources.close()
        assertEquals(listOf("display", "reader", "projection"), released)
        assertEquals("stop", error.suppressed.single().message)
    }

    @Test
    fun repeatedSessionsOwnIndependentResources() {
        var releases = 0
        repeat(20) { CaptureResources().use { resources -> resources.own(Unit) { releases++ } } }
        assertEquals(20, releases)
    }
}
