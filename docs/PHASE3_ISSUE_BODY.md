## Goal
Make the solver production-safe and implement universal, deterministic 9×9 Sudoku board detection that can be fully regression-tested from screenshots before device testing.
> Historical geometry-only brief. The subsequent user request adds OCR and autofill;
> see `PHASE3_STATUS.md` and `LIVE_ASSISTANT.md` for current scope.


## Read first

- `AGENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/FIXTURE_SOURCES.md`
- `docs/PHASE3_VISION_REQUIREMENTS.md`
- `docs/PHASE3_TEST_MATRIX.md`
- `docs/SOLVER_REQUIREMENTS.md`
- `docs/PHASE3_CODEX_PROMPT.md`

## Required work

1. Verify the two approved JPEG fixtures and `SHA256SUMS.txt` under `app/src/test/resources/sudoku/fixtures/approved/`.
2. Add a platform-neutral pixel-image type plus Android `Bitmap` adapter so vision core runs in normal JVM tests.
3. Harden the solver to classify **invalid givens**, **unsatisfiable**, **unique**, and **multiple-solution** puzzles. Only unique puzzles may ever become autofill-eligible in later phases.
4. Implement deterministic universal board detection: locate a real 9×9 Sudoku grid, estimate/refine board geometry, derive 81 cell rectangles/centers, provide confidence/rejection reasons, and create a normalized/rectified board image for Phase 4 OCR.
5. Manually inspect positive fixtures and add reviewed ground truth. Never use detector output as its own expected value.
6. Reject all documented negative fixtures as `NoBoard`.
7. Add deterministic transformed variants covering padding/translation, scale, brightness/contrast, and light noise/blur, with geometry expectations transformed mathematically.
8. Integrate detection into the capture preview with a debug geometry overlay and status/confidence. **No OCR and no accessibility taps in this issue.**
9. Document the general algorithm and thresholds. Do not add app-specific conditions.
10. Run `gradlew test assembleDebug lintDebug` and resolve failures.

## Hard constraints

- No hard-coded screen resolutions or board coordinates.
- No package-name checks.
- No `when (fixtureName)` or source-specific thresholds/geometry.
- No hidden fallback that assumes the largest square is a Sudoku.
- Core vision code must not depend on Android framework classes.
- Keep existing MediaProjection lifecycle/cleanup behavior intact.
- Existing real screenshots remain test-only and must not be shipped in the APK.

## Acceptance criteria

- Every positive real fixture detects a 9×9 board within manually reviewed tolerance.
- Every negative fixture returns `NoBoard`.
- All 81 cell centers are inside the proper ordered/non-overlapping cells and board.
- Transform regression cases pass deterministically.
- Solver tests cover invalid, unsatisfiable, unique, multiple, solved, clue preservation, input immutability, and repeated deterministic solves.
- Uniqueness checking stops after finding a second solution.
- Capture preview can show the detected board/grid overlay without OCR/autofill.
- `gradlew test assembleDebug lintDebug` passes.
- PR documentation includes test counts/results and any general unsupported visual pattern.

Do not merge the implementation PR until its automated Codex review has completed.
