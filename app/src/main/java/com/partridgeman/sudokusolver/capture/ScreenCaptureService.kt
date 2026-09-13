package com.partridgeman.sudokusolver.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.partridgeman.sudokusolver.MainActivity
import com.partridgeman.sudokusolver.R

class ScreenCaptureService : Service() {
    private var session: ScreenCaptureSession? = null
    private var latestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == ACTION_CANCEL) {
            session?.cancel() ?: stopSelf(startId)
            return START_NOT_STICKY
        }
        // A second request must not consume another consent token while a capture is active.
        if (session != null) return START_NOT_STICKY
        val consent = intent?.consentData()
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        if (consent == null || resultCode != Activity.RESULT_OK) {
            CaptureStore.failed("Screen capture permission was not granted.")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            showForegroundNotification()
            CaptureStore.capturing()
            lateinit var capture: ScreenCaptureSession
            capture = ScreenCaptureSession(applicationContext) { result ->
                if (session === capture) {
                    session = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(latestStartId)
                    result.fold(CaptureStore::complete) { error ->
                        Log.w(TAG, "One-shot capture failed", error)
                        CaptureStore.failed((error as? CaptureException)?.message ?: "Screen capture failed. Please try again.")
                    }
                }
            }
            session = capture
            capture.start(resultCode, consent)
        } catch (error: Exception) {
            Log.w(TAG, "Unable to start capture service", error)
            session?.cancel()
            session = null
            CaptureStore.failed("Could not start screen capture. Please try again.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        // Android must never restart this service with a previously consumed consent token.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        session?.cancel()
        if (session != null) CaptureStore.failed("Screen capture stopped. Please scan again.")
        session = null
        super.onDestroy()
    }

    private fun showForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Screen capture", NotificationManager.IMPORTANCE_LOW))
        }
        val returnIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancelIntent = PendingIntent.getService(this, 1,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_capture)
            .setContentTitle("Capturing one screenshot")
            .setContentText("Open your puzzle. Capture starts after five seconds.")
            .setContentIntent(returnIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "Cancel", cancelIntent).build())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.consentData(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(EXTRA_CONSENT, Intent::class.java)
    } else {
        getParcelableExtra(EXTRA_CONSENT)
    }

    companion object {
        const val EXTRA_RESULT_CODE = "capture.resultCode"
        const val EXTRA_CONSENT = "capture.consent"
        const val ACTION_CANCEL = "com.partridgeman.sudokusolver.capture.CANCEL"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "ScreenCapture"
    }
}
