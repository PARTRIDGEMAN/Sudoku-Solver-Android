# Live Sudoku assistant (0.2.0)

## Install and try it

Install `app/build/outputs/apk/debug/app-debug.apk` over the earlier debug build.
Open Sudoku Solver, tap **Accessibility settings**, and enable **Sudoku Solver
Autofill**. Android may require **Allow restricted settings** in the app's App
info menu for a sideloaded APK before it lets you enable accessibility. See
[Android restricted settings](https://support.google.com/android/answer/12623953?hl=en).

Tap **Show movable assistant**, then open your Sudoku app. Drag the panel by its
**Sudoku · drag** title; its position is remembered and clamped after resizing or
rotation. The minus button minimizes it and × closes it. Show it again from the
main app. Place it outside the board and number buttons before filling.

Use cell-first input, turn pencil/notes mode off, and remove existing pencil notes.
In Manual mode, tap **Scan puzzle**, compare the scanned digits with the puzzle,
and check the review box. **Show solution** displays the solved board. Tap
**Autofill blanks** to enter it. Auto is an explicit opt-in: enabling it means
that the next **Scan puzzle** proceeds to fill if every verification passes.
It does not continuously scan the screen in the background.

**Stop** cancels further input. Dragging the overlay during filling also stops.
An already dispatched 80 ms tap may finish; no subsequent number is queued.
Do not touch the puzzle while it is filling. A new scan is required after a stop.

## Android behavior

The Compose panel uses `TYPE_ACCESSIBILITY_OVERLAY` and does not need the separate
"display over other apps" permission. It is non-focusable so the underlying game
keeps focus. The service provides Compose lifecycle and saved-state owners and
removes its window when disabled. Disabling accessibility revokes the assistant.

Live scans and per-entry verification require **Android 11 / API 30 or newer**.
They use the accessibility screenshot capability (`canTakeScreenshot`), hide the
panel briefly before capture, and rate-limit captures to at least 700 ms apart.
Protected screens or denied screenshots stop the operation. System dialogs,
keyboards, app/window changes, display size changes, and rotation invalidate input.

The existing one-shot MediaProjection flow remains available on Android 7+ from
**Scan Puzzle** in the main app. It still requests fresh consent for each capture;
its preview now also reads digits and shows a unique solution. On older Android
versions that preview does not grant permission to autofill an unverified screen.
Close the overlay before using this separate capture-preview flow.

## Local recognition and solving

Board geometry comes from the deterministic grid detector. Recognition rectifies
the board to 900×900, inspects each interior with connected components, and rejects
faint marks, small pencil notes, multiple glyphs, and clipped ink. Ambiguous large
glyphs get wider crops, which must independently pass the same checks. Visible
uncertainty never becomes an invented zero. The excluded grid border is not OCR
input; unusual edge-aligned text and heavily decorated cells remain unsupported.

The bundled ML Kit Latin recognizer (`com.google.mlkit:text-recognition:16.0.1`)
reads binary and contrast-normalized digit sheets. Both readings must agree on
one digit 1–9 and have confidence at least 0.90. This score is a gate, not a
calibrated guarantee of correctness. The model ships inside the APK, and the
manifest removes inherited INTERNET permission; there is no cloud OCR or model
download. See the [bundled recognizer documentation](https://developers.google.com/ml-kit/vision/text-recognition/v2/android). Screenshots and puzzle state remain in memory.

The Android-independent solver distinguishes invalid givens, unsatisfiable boards,
multiple solutions, and one unique solution. Only the last creates a fill plan.
Every originally nonzero cell, including any numbers already entered by the user,
is excluded from that plan.

## Autofill checks and current limits

Number buttons are discovered from clickable accessibility labels first, then
from local screen OCR. Initial detection requires all nine labels in a regular,
ascending keypad with one to three rows, outside the Sudoku board. Duplicate,
missing, or ambiguous labels block filling. Coordinates and target package names
are never configured or hard-coded.

Before input, a fresh scan must match the original board and geometry. Each entry
then follows: tap its blank cell, capture again, verify the selection, tap the
freshly verified digit label, and capture again to check the exact expected board.
Selection needs either an accessibility selected-cell node or a newly acquired,
unique colored cell background. Outline-only selections without accessibility
selection information are deliberately rejected. Missing completed-number buttons
are tolerated only if the next needed button is still at its verified position;
a keypad reflow stops filling. The overlay cannot cover a tap target.

This is a conservative live-testing build, **not verified across every Sudoku
app**. Digit-first interfaces, pencil notes, animations, unusual keypad ordering,
obscured keys, perspective skew, and some decorated/highlighted cells can prevent
recognition or filling. Per-entry OCR makes filling slower than blind tapping.

## Validation and remaining device checks

JVM tests cover solver classifications, real grid geometry and perturbations,
recognition consensus/blank/notes handling, real screenshot ink segmentation,
keypad rejection, preservation of clues, exact progress checking, wrong selection,
changed windows/keypads, cancellation, and overlay clamping. Run:

```powershell
.\gradlew.bat test assembleDebug lintDebug --console=plain
```

The user reported successful physical-device capture and grid detection on the
previous APK. OCR model accuracy, floating-window rendering, and actual gestures
in this new build still need phone testing; JVM tests do not exercise ML Kit or
Android's window/input system. Try Manual mode first on a disposable puzzle, then
check Stop, moving/minimizing the panel, changing apps, rotating, an unclear scan,
and switching notes mode. Verify that existing clues remain unchanged.
