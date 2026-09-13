# Deterministic board geometry

`GridBoardScanner` implements `BoardScanner` over immutable `PixelImage` ARGB
buffers. The detector, geometry, image normalization, solver, and model run in
ordinary JVM tests. Android adaptation lives in `vision/android/BitmapPixels.kt`;
AWT/ImageIO are used only in test sources. The capture service is unchanged.

## Search and acceptance

1. Search at at most 900 pixels on the longest image dimension, with bilinear
   scaling and separate source-coordinate scale factors. Screens with fewer than
   90 pixels on their short side are too small for this implementation.
2. Convert to integer luminance, compositing any transparency onto white. Find
   horizontal and vertical contrast responses using neighbors one and two pixels
   away. The contrast threshold is 12 out of 255, independent of polarity, so
   light and dark grids follow the same path.
3. Find long line segments on both axes, bridging at most two missing pixels.
   Segment length must be at least 18% of the short image dimension. Group nearby
   parallel responses from the same stroke and consider up to 400 segments per
   axis. Form square candidate bounds from aligned opposite segments; aspect
   ratio must be within 8%, endpoint differences within 4% of their span.
4. Search near each of ten predicted boundaries per axis (within 10% of cell
   pitch, with a two-pixel minimum radius). Every line needs at least 64% support
   across its span. All eight internal lines are mandatory on each axis. Only an
   outer boundary at the image edge may be inferred when the stroke is clipped.
5. Fit the internal boundaries to a regular lattice. Use its extrapolated ends
   to choose between the grid border and nearby decorative framing; the selected
   outer border still requires contrast evidence. Reject cell spacing deviations
   greater than 15% of pitch. Grid support must exceed average mid-cell edge
   support by at least 0.35, rejecting dense texture and stripes.
6. Deduplicate equivalent candidates with intersection-over-union above 0.85.
   If distinct supported boards remain, return `NoBoard` rather than choosing
   the largest square. Refine the selected geometry at source resolution and
   repeat the structural gates there before accepting it.

Confidence is a deterministic diagnostic score: 85% mean line support plus 15%
spacing regularity. It is **not a calibrated probability**. Every structural
gate must pass regardless of that average. The result records candidate bounds,
scores, and explicit rejection reasons; tests print them and the Android preview
logs them under `BoardDetection`. The top 16 search candidates are retained.

Constants are general search/quality limits, not coordinates or thresholds for
individual apps. Production code has no fixture names or target package checks.

## Coordinates and normalization

Detected lines are in original screenshot coordinates. Image pixels have integer
center coordinates; an outer boundary clipped by the image can lie at zero or
the width/height extent. `BoardGeometry` owns ten ordered boundaries per axis and
81 row-major cells with shared edges and no overlapping interiors. Each cell
exposes its bounds and center; the outer rectangle exposes four corners.

`sourcePoint` maps the normalized board back to the measured source boundaries.
Piecewise bilinear sampling produces a 450×450 board with 50 pixels per cell,
removing surrounding UI and compensating for small variations in measured cell
widths. This resolution is an OCR input format, not an assumed phone geometry.
The tests verify source mappings, channels, and independently calculated cell
centers. Real-fixture tests write overlay and normalized PNGs under
`app/build/reports/vision/` for visual inspection.

## Android preview and later safety gates

The Compose preview runs detection on `Dispatchers.Default` after a new bitmap
arrives, displays status and confidence, and draws all boundaries plus cell
centers using the same fit/centering transform as the image. A new image resets
the result; cancellation prevents an older computation from publishing over a
newer image. No capture resource is retained by detection.

Geometry cannot identify digits or establish puzzle validity. Even a perfect
empty 9×9 table can be visually indistinguishable from a Sudoku grid. Digit
recognition and clue validation are mandatory before any input. See `LIVE_ASSISTANT.md` for the separate recognition and automation layers.
`SudokuSolver.analyze` classifies `InvalidGivens`, `Unsatisfiable`, `Unique`, and
`MultipleSolutions`, stopping search after its second solution. The compatibility
`solve` method returns a board only for a unique solution. Neither method grants
permission to tap; geometry detection itself performs no accessibility gestures.

## Supported scope and limitations

This implementation supports axis-aligned screen grids, including thick beveled
lines, thin lines, light/dark colors, edge-clipped outer strokes, full landscape
rotation, and moderate image perturbations. It does not implement perspective
homography for camera photographs or arbitrary-angle skew, disconnected dot-only
grids, severely obscured/internal missing lines, or boards smaller than the search
limits. These inputs must pass the same structural gates or be rejected; there
is no app-specific recovery path. Perspective correction remains future work.

The currently inspected corpus is narrow. Regression success demonstrates those
images and transforms, not every Sudoku app or every visual style. The user reported successful phone scanning on the prior build. The new OCR and
autofill overlay still need physical-device validation.
