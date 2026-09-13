package com.partridgeman.sudokusolver

import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.partridgeman.sudokusolver.capture.CaptureStore
import com.partridgeman.sudokusolver.capture.ScreenCaptureService
import com.partridgeman.sudokusolver.ui.SudokuSolverApp

class MainActivity : ComponentActivity() {
    private var consentPending = false
    private val consentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        consentPending = false
        val consent = result.data
        if (result.resultCode != RESULT_OK || consent == null) {
            CaptureStore.failed("Screen capture cancelled. You can scan again whenever you’re ready.")
        } else {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(ScreenCaptureService.EXTRA_CONSENT, consent)
            try {
                CaptureStore.capturing()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
                // Reveal the previous task; the service gives the user time to open their puzzle.
                moveTaskToBack(true)
            } catch (error: RuntimeException) {
                CaptureStore.failed("Could not start screen capture. Please try again.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consentPending = savedInstanceState?.getBoolean("consentPending") ?: false
        if (consentPending) CaptureStore.awaitingConsent()
        setContent {
            val captureState = CaptureStore.state.collectAsStateWithLifecycle().value
            SudokuSolverApp(
                captureState = captureState,
                onScan = ::requestCapture,
                onCancelCapture = {
                    startService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_CANCEL))
                },
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("consentPending", consentPending)
        super.onSaveInstanceState(outState)
    }

    private fun requestCapture() {
        if (CaptureStore.state.value.busy || consentPending) return
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            } else {
                manager.createScreenCaptureIntent()
            }
            consentPending = true
            CaptureStore.awaitingConsent()
            consentLauncher.launch(intent)
        } catch (error: RuntimeException) {
            consentPending = false
            CaptureStore.failed("Screen capture is unavailable. Please try again.")
        }
    }
}
