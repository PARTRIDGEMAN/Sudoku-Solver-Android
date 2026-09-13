# Codex Instructions

## Product goal
Build a universal Android Sudoku assistant with Manual and Auto modes. It must discover Sudoku board/keypad geometry at runtime and must not be tied to one Sudoku app or one phone resolution.

## Engineering constraints
- Kotlin only for app code unless a dependency makes another language unavoidable.
- Keep Android UI in Jetpack Compose.
- Keep solver/model code Android-independent and unit-testable.
- Do not hard-code board cell coordinates, keypad coordinates, package names, or a specific Sudoku app layout.
- Do not add cloud OCR or external APIs without an explicit design decision.
- Recognition must produce confidence information and fail closed on ambiguous scans.
- Never send accessibility gestures unless the parsed board is valid and a complete solution exists.
- Autofill must never overwrite original clue cells.
- Keep screen capture, computer vision, solver, and accessibility automation as separate layers.

## Workflow
- Make small, reviewable changes.
- Add or update tests for solver/geometry/recognition logic.
- Run unit tests before considering a task complete.
- Prefer deterministic CV before adding ML complexity.
- Document non-obvious Android permission/service behavior in `docs/`.

## Current phase
Phase 2 screen capture is implemented. Continue from `docs/ROADMAP.md`; physical-device capture validation is next, before Phase 3 board detection.
