# One-shot screen capture

## User flow

1. Open the Sudoku app, then open Sudoku Solver and tap **Scan Puzzle**.
2. Allow the Android screen capture prompt. Each attempt needs fresh consent.
3. Sudoku Solver moves its task to the background. Bring the desired puzzle into
   view within five seconds; keep it visible until capture ends.
4. Return to Sudoku Solver to see **Last successful capture**, including its
   pixel dimensions. No OCR, board detection, solving, or gestures run.

The five-second delay starts when the service obtains the projection. If the
previous task is not the puzzle, open the puzzle manually during that interval.
The screenshot reflects whatever is visible when the first frame arrives;
Phase 2 cannot verify that it contains a puzzle. Manual and Auto currently use
the same capture flow.

Cancel from the foreground notification or return to the app and tap **Cancel
capture**. Android's projection stop control also ends the attempt. Denial,
cancellation, timeout, and a display-size change show a status message and leave
the last successful preview intact. The preview is explicitly labelled so an
older image is not confused with the failed attempt.

## Android permission and lifecycle behavior

- The activity uses Activity Result APIs and preserves a pending consent request
  across activity recreation. Consent data is passed only to a non-exported
  service. It is never saved to disk or reused.
- Android 14+ is asked to capture the default display with
  `MediaProjectionConfig.createConfigForDefaultDisplay()`. Earlier versions use
  the standard display-capture intent. This captures the full visible screen,
  including system UI. A device may override the default-display preference;
  an unexpected capture size ends the attempt instead of assuming a mapping.
- The manifest declares `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PROJECTION`, and the `mediaProjection` service type.
  The activity starts the service after consent, while it is foregrounded.
  The service calls `startForeground` before `getMediaProjection` and registers
  the projection callback before creating the virtual display.
- Notification permission is not required to start a foreground service.
  This phase does not request `POST_NOTIFICATIONS`; Android 13+ may hide the
  notification from the drawer when notifications are not allowed. The in-app
  cancel button and the system's foreground-service/projection controls remain
  available. Where notifications are allowed, immediate display is requested
  so the short-lived capture notification is not deferred.
- Each session creates exactly one virtual display. The service returns
  `START_NOT_STICKY` so Android cannot restart it with a consumed consent token.
  A repeated request while capturing does not consume another token.
- Capture runs on a dedicated handler thread. The acquired `Image` closes before
  cleanup releases the virtual display, `ImageReader` (including its surface),
  projection callback, and projection. Cleanup is idempotent and attempts every
  release even if one fails. Pending timers are removed and the thread quits.
  The service removes its foreground notification and stops after completion.
- Capture times out if no frame arrives within ten seconds after the five-second
  switch delay. System revocation, screen lock, service destruction, setup errors,
  and cancellation use the same cleanup path.

See Android's [MediaProjection guide](https://developer.android.com/media/grow/media-projection),
[foreground service type requirements](https://developer.android.com/develop/background-work/services/fgs/service-types#media-projection),
and [notification permission guidance](https://developer.android.com/develop/ui/views/notifications/notification-permission).

## Geometry and memory

The service reads the display's maximum window bounds at capture time on API
30+, or real display metrics on older versions. Density comes from the current
configuration. No phone resolution, target package, board, or keypad geometry
is fixed in code. A size change during the one-shot attempt fails with a retry
message; it never creates a second display from the same token.

The Android-independent RGBA converter respects crop offsets, pixel stride, row
stride, and buffer limits, producing an ARGB bitmap without padded columns.
Only the last successful bitmap is kept in process memory via `CaptureStore`.
It survives activity recreation but disappears when the process is killed.
Images are not written to storage or uploaded. Protected windows may appear
blank because Android restricts their capture; this phase does not classify
blank images or infer Sudoku content.

## Verification

Automated checks: `gradlew.bat test assembleDebug lintDebug` with JDK 17. JVM
tests cover RGBA channels, alpha, crop-position offsets, nontrivial strides,
truncated buffers, invalid geometry, multiple dimensions, and resource cleanup
after success, partial setup failure, repeated close, and release failure.

Physical-device checks are still pending; no device was connected during
implementation. The unit tests do not prove platform resource release or the
permission flow. Run these checks before treating issue #1 as device-verified:

- [ ] Install `app/build/outputs/apk/debug/app-debug.apk` on an Android 14+ device.
      Scan a visible puzzle and verify the preview's content, aspect ratio, and
      dimensions. Repeat on an older supported Android version when available.
- [ ] Deny/back out of consent; verify the app remains usable and can scan again.
- [ ] Rotate/recreate the activity during consent and while viewing the preview.
      Confirm one consent request and a retained preview, with no duplicate capture.
- [ ] Repeat at least 20 captures, including switching between unrelated apps and
      portrait/landscape orientations before scanning. Every attempt requests
      fresh consent and preserves the full captured image without padded columns.
- [ ] Cancel during the delay, stop via Android's projection control, and lock the
      screen during capture. Verify a status message, retained previous preview,
      and successful retry. Change display size while a frame is pending and
      verify the retry message.
- [ ] With notifications disabled, verify capture still works and in-app cancel
      is available. With notifications enabled, verify Cancel in the notification.
- [ ] After each success/failure/cancel, inspect `adb shell dumpsys media_projection`,
      `adb shell dumpsys display`, and `adb shell dumpsys activity services`:
      no projection, `Sudoku one-shot capture` virtual display, or capture service
      should remain. Use Android Studio Profiler to check repeated captures do not
      accumulate `ImageReader`, image buffers, or `SudokuScreenCapture` threads.
- [ ] Kill the app process after a capture and reopen it; the preview should be
      empty and a new scan should require consent.
