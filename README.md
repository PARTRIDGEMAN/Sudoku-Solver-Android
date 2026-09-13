# Sudoku Solver Android

Universal Android Sudoku assistant that detects a visible 9×9 Sudoku board, solves it locally, and can optionally autofill the answer through Android accessibility gestures.

## Modes

- **Manual** — scan and solve the visible puzzle, then ask before autofilling.
- **Auto** — scan, validate, solve, and autofill automatically.

## Design goals

- Work across Sudoku apps rather than relying on hard-coded coordinates.
- Keep solving and recognition local on-device.
- Validate detected clues before any automated input.
- Keep screen capture, vision, solving, and accessibility automation separated into testable modules.

## Tech

- Kotlin
- Android Studio
- Jetpack Compose
- MediaProjection for screen capture
- AccessibilityService for cross-app gestures
- Deterministic Sudoku solver
- Vision pipeline for grid and digit recognition

See `docs/ARCHITECTURE.md` and `docs/ROADMAP.md` for the planned implementation.

## Build

Use JDK 17 and the checked-in Gradle wrapper. See [Build setup](docs/BUILDING.md)
for Android SDK requirements and clean build commands.

## Live testing

Build/install `app/build/outputs/apk/debug/app-debug.apk` (version 0.2.0).
Enable **Sudoku Solver Autofill** in Accessibility settings, tap **Show movable
assistant**, and open your Sudoku app. Drag the panel away from the board and
keypad. Use cell-first input with pencil mode off. **Scan puzzle**, check the
recognized digits, then **Autofill blanks**. **Stop** cancels further input.
Auto mode explicitly enables fill after Scan.

Live autofill requires Android 11+. The separate **Scan Puzzle** capture flow in
the main app provides screenshot and solution previews on Android 7+.
Recognition and filling are conservative and still need physical-device testing
across apps. See [Live assistant](docs/LIVE_ASSISTANT.md) for setup and limitations.
