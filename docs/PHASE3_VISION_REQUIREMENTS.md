# Phase 3 vision requirements

Phase 3 turns a captured screen into reliable Sudoku **geometry**. It does not enter digits or perform accessibility gestures.

## Required output

Given a platform-neutral pixel image, board detection returns either:

- `NoBoard`, with a reason/confidence useful for debugging, or
- one detected 9×9 board containing:
  - board bounds or four corners in source-image coordinates,
  - ten horizontal and ten vertical grid boundaries (or an equivalent homography/rectification model),
  - 81 cell rectangles and centers mapped back to source-image coordinates,
  - a detection confidence score,
  - a normalized/rectified board image suitable for the later digit-recognition phase.

The detector must not depend on a package name, phone model, fixed resolution, fixed board position, or one upstream fixture source.

## Platform boundary

Keep the core detector JVM-testable. Production Android code may adapt `Bitmap` to a simple pixel buffer, but the vision algorithm must not require Android framework classes.

A recommended boundary is a small immutable image type containing width, height, and packed ARGB pixels. JVM tests may decode PNG fixtures with `ImageIO`; Android production code should provide a `Bitmap` adapter without bringing AWT into the APK.

## Detection strategy

Codex may choose the exact deterministic image-processing method, but it should be explainable and testable. A reasonable pipeline is:

1. downscale large screenshots for search while preserving aspect ratio,
2. luminance/grayscale conversion,
3. adaptive thresholding and/or edge extraction robust to light and dark themes,
4. locate square-ish regions with repeated near-regular horizontal/vertical structure,
5. score candidates by expected Sudoku geometry (9 roughly equal rows/columns; stronger 3×3 separators may help but are not mandatory),
6. reject candidates that do not meet structural confidence thresholds,
7. refine the winning candidate at source resolution,
8. derive all 81 cells and a rectified board image.

Do not identify a Sudoku merely because the screen contains one large square.

## Regression policy

Real screenshots are stored under `app/src/test/resources/sudoku/fixtures/real/` and documented in `docs/FIXTURE_SOURCES.md`.

Positive fixtures must have manually reviewed expected geometry. Negative fixtures must return `NoBoard`.

Tests must also create deterministic variants of positive fixtures that cover at least:

- surrounding padding/translation,
- multiple image scales,
- moderate brightness/contrast changes,
- light blur/noise or compression-like perturbation,
- portrait screenshots with UI outside the board,
- dark/high-contrast themes where present in the real corpus.

Transforms must not alter expected board geometry without updating the expected transform mathematically.

## Safety requirement for later autofill

Detection confidence is not permission to tap. Later phases must require successful digit recognition, Sudoku validation, and a unique solution before any automated input. Phase 3 should expose enough geometry/confidence data to support that policy.
