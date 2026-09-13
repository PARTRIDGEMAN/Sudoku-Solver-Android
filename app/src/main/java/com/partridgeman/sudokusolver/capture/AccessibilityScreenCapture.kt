package com.partridgeman.sudokusolver.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.Display
import androidx.annotation.RequiresApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Independent capture boundary for live verification; requires user-enabled accessibility on API 30+. */
class AccessibilityScreenCapture(private val service: AccessibilityService) {
    private var lastRequest = 0L

    /**
     * Android's accessibility screenshot service enforces an approximately 333 ms
     * request interval. Keep a small safety margin rather than the old 700 ms delay,
     * and retry a framework rate-limit response instead of failing the whole fill.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun capture(): Bitmap {
        repeat(3) { attempt ->
            delay((360 - (SystemClock.elapsedRealtime() - lastRequest)).coerceAtLeast(0))
            lastRequest = SystemClock.elapsedRealtime()
            try {
                return captureOnce()
            } catch (error: ScreenshotFailure) {
                if (error.code != AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT || attempt == 2) {
                    throw IllegalStateException(
                        "Could not capture this screen (${error.code}). Keep the phone unlocked and try again.",
                    )
                }
                // OEM timing can be a little stricter than AOSP; back off once and retry.
                delay(180)
            }
        }
        error("Could not capture this screen.")
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureOnce(): Bitmap = suspendCancellableCoroutine { continuation ->
        service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                val buffer = screenshot.hardwareBuffer
                try {
                    val hardware = requireNotNull(Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace))
                    val copy = try { requireNotNull(hardware.copy(Bitmap.Config.ARGB_8888, false)) } finally { hardware.recycle() }
                    if (continuation.isActive) continuation.resume(copy) else copy.recycle()
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally {
                    buffer.close()
                }
            }

            override fun onFailure(errorCode: Int) {
                if (continuation.isActive) continuation.resumeWithException(ScreenshotFailure(errorCode))
            }
        })
    }

    private class ScreenshotFailure(val code: Int) : Exception()
}
