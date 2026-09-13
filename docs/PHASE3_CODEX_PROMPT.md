# Codex implementation brief — Phase 3

Implement universal Sudoku **board detection** and harden the solver before any digit OCR or autofill work.

Read first:

- `AGENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/FIXTURE_SOURCES.md`
- `docs/PHASE3_VISION_REQUIREMENTS.md`
- `docs/PHASE3_TEST_MATRIX.md`
- `docs/SOLVER_REQUIREMENTS.md`

Do not hard-code phone resolutions, fixture filenames, app package names, or source-specific board coordinates. Do not add per-fixture branches to make tests pass.

## Work order

1. Verify the 17 PNG fixtures and `SHA256SUMS.txt` are present under `app/src/test/resources/sudoku/fixtures/real/`.
2. Introduce a platform-neutral image representation and Android `Bitmap` adapter so the detector can run in ordinary JVM tests.
3. Harden the Sudoku solver so it explicitly distinguishes invalid givens, unsatisfiable puzzles, unique solutions, and multiple solutions. Preserve clues and input immutability.
4. Implement deterministic 9×9 board detection according to `PHASE3_VISION_REQUIREMENTS.md`.
5. Manually inspect positive fixtures and add reviewed machine-readable ground truth for board geometry. Do not generate expected geometry using the detector itself.
6. Make all documented negative fixtures reject with `NoBoard`.
7. Add deterministic transform tests for translation/padding, scaling, luminance/contrast, and light noise/blur. Expected transformed geometry must be calculated from the transform.
8. Integrate board detection into the existing capture preview: after a successful screenshot, show whether a board was detected and draw a debug overlay for the detected outer board/corners and grid/cell geometry. Do not add OCR or automated taps yet.
9. Keep failures explainable with candidate scores/rejection reasons in test output/logging.
10. Run `gradlew test assembleDebug lintDebug` and fix errors rather than weakening tests.

## Completion bar

The PR is ready only if:

- all solver classifications in `SOLVER_REQUIREMENTS.md` are covered by passing JVM tests,
- every positive real fixture detects a 9×9 board within reviewed geometry tolerance,
- every negative real fixture returns `NoBoard`,
- all 81 cell centers/rectangles are ordered, non-overlapping, and contained in the detected board,
- deterministic transformed variants pass,
- detection is independent of Android framework classes,
- Android integration compiles and only visualizes geometry,
- `test`, `assembleDebug`, and `lintDebug` pass,
- documentation explains the algorithm and confidence/rejection policy,
- no fixture-specific hacks, fixed screen coordinates, package checks, or hidden fallback paths are introduced.

If one visual style cannot be supported generically, document the limitation and leave a failing/disabled test with a clear reason rather than special-casing that screenshot.
