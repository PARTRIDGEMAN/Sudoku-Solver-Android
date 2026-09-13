package com.partridgeman.sudokusolver.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager

internal class CaptureException(message: String) : Exception(message)

/** Owns one consent token and one virtual display. All capture work is serialized off the UI thread. */
internal class ScreenCaptureSession(
    private val context: Context,
    private val onComplete: (Result<Bitmap>) -> Unit,
) {
    private val thread = HandlerThread("SudokuScreenCapture").apply { start() }
    private val handler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val resources = CaptureResources()
    private var finished = false
    private var size: Pair<Int, Int>? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            finish(Result.failure(CaptureException("Screen capture stopped. Please scan again.")))
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            if (size != null && size != (width to height)) {
                finish(Result.failure(CaptureException("Screen size changed. Keep the display steady and scan again.")))
            }
        }
    }

    fun start(resultCode: Int, consent: Intent) {
        handler.post {
            if (finished) return@post
            try {
                val manager = context.getSystemService(MediaProjectionManager::class.java)
                val projection = resources.own(requireNotNull(manager.getMediaProjection(resultCode, consent))) {
                    try { it.unregisterCallback(projectionCallback) } finally { it.stop() }
                }
                // Required before createVirtualDisplay on Android 14+, also handles system revocation.
                projection.registerCallback(projectionCallback, handler)
                handler.postDelayed({ createDisplay(projection) }, SWITCH_DELAY_MS)
                handler.postDelayed({
                    finish(Result.failure(CaptureException("No screenshot arrived. Please scan again.")))
                }, SWITCH_DELAY_MS + FRAME_TIMEOUT_MS)
            } catch (error: Exception) {
                finish(Result.failure(error))
            }
        }
    }

    fun cancel() {
        handler.post { finish(Result.failure(CaptureException("Screen capture cancelled."))) }
    }

    private fun createDisplay(projection: MediaProjection) {
        if (finished) return
        try {
            val dimensions = displaySize()
            size = dimensions
            val (width, height) = dimensions
            val reader = resources.own(ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)) { it.close() }
            reader.setOnImageAvailableListener({ source ->
                if (!finished) readFrame(source, dimensions)
            }, handler)
            resources.own(requireNotNull(projection.createVirtualDisplay(
                "Sudoku one-shot capture", width, height, context.resources.configuration.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, handler,
            ))) { it.release() }
        } catch (error: Exception) {
            finish(Result.failure(error))
        }
    }

    private fun readFrame(reader: ImageReader, dimensions: Pair<Int, Int>) {
        try {
            // Always close the acquired Image before finish releases its reader and display.
            val bitmap = reader.acquireLatestImage()?.use { image ->
                if (displaySize() != dimensions) throw CaptureException("Screen size changed. Please scan again.")
                check(image.width == dimensions.first && image.height == dimensions.second) { "Unexpected frame dimensions" }
                val crop = image.cropRect
                val plane = image.planes.single()
                val buffer = plane.buffer.duplicate()
                buffer.position(buffer.position() + crop.top * plane.rowStride + crop.left * plane.pixelStride)
                val pixels = RgbaFrame.toArgb(buffer, crop.width(), crop.height(), plane.pixelStride, plane.rowStride)
                Bitmap.createBitmap(pixels, crop.width(), crop.height(), Bitmap.Config.ARGB_8888)
            } ?: return
            finish(Result.success(bitmap))
        } catch (error: Exception) {
            finish(Result.failure(error))
        }
    }

    @Suppress("DEPRECATION")
    private fun displaySize(): Pair<Int, Int> {
        val windowManager = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
        }
    }

    private fun finish(result: Result<Bitmap>) {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        var outcome = result
        try {
            resources.close()
        } catch (error: Exception) {
            outcome = Result.failure(error)
        } finally {
            thread.quitSafely()
        }
        mainHandler.post { onComplete(outcome) }
    }

    companion object {
        const val SWITCH_DELAY_MS = 5_000L
        private const val FRAME_TIMEOUT_MS = 10_000L
    }
}
