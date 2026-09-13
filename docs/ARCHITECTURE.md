# Architecture

The app is deliberately split into independent stages so recognition mistakes cannot directly become taps.

```text
Screen Capture
    ↓
Board Detection / Perspective Normalization
    ↓
Cell Segmentation (81 cells)
    ↓
Digit Recognition (blank or 1–9 + confidence)
    ↓
Board Validation
    ↓
Sudoku Solver
    ↓
Manual confirmation OR Auto policy
    ↓
Input Target Detection
    ↓
Accessibility Gestures
```

## Core rules

1. **No fixed screen coordinates in production.** Board and keypad geometry must be detected at runtime.
2. **Never autofill an invalid scan.** Rows, columns, and boxes are validated before solving.
3. **Preserve original clues.** The automation may only write cells that were blank in the scanned board.
4. **Separate recognition from action.** Vision code returns structured geometry/data; it never sends taps.
5. **Local processing first.** Sudoku solving and image recognition should not require a network connection.

## Recognition strategy

Start with deterministic computer vision because Sudoku screens are unusually structured:

- grayscale / adaptive threshold
- detect the dominant near-square grid
- perspective-correct the board when necessary
- split into 81 normalized cell images
- classify empty cells by foreground-pixel density
- recognize 1–9 using a small classifier/template pipeline
- attach a confidence score to every recognized clue

A production scanner should reject low-confidence boards rather than guessing.

## Input strategy

Prefer accessibility nodes when a target Sudoku app exposes useful cell/keypad semantics. Otherwise use detected visual geometry and `AccessibilityService.dispatchGesture`.

Manual mode requires confirmation after solve. Auto mode may proceed immediately, but only after all validation gates pass.
