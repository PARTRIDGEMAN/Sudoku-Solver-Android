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

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun capture(): Bitmap {
        delay((700 - (SystemClock.elapsedRealtime() - lastRequest)).coerceAtLeast(0))
        lastRequest = SystemClock.elapsedRealtime()
        return suspendCancellableCoroutine { continuation ->
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    try {
                        val hardware = requireNotNull(Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace))
                        val copy = try { requireNotNull(hardware.copy(Bitmap.Config.ARGB_8888, false)) } finally { hardware.recycle() }
                        if (continuation.isActive) continuation.resume(copy) else copy.recycle()
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } finally { buffer.close() }
                }

                override fun onFailure(errorCode: Int) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(
                        "Could not capture this screen ($errorCode). Keep the phone unlocked and try again."))
                }
            })
        }
    }
}
