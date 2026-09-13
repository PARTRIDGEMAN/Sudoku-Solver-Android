# Phase 3 test matrix

Phase 3 is complete only when the detector and solver pass this matrix locally with no device attached.

| Area | Required behavior |
|---|---|
| Positive real screenshots | Detect the Sudoku board for every fixture documented as positive in `docs/FIXTURE_SOURCES.md`. |
| Negative real screenshots | Return `NoBoard` for home/settings/statistics/scoreboard fixtures that do not contain a playable Sudoku grid. |
| Geometry | All 81 derived cell centers lie inside the detected board and inside their corresponding cell. Rows/columns are ordered and non-overlapping. |
| Translation | Adding deterministic padding around a positive screenshot preserves detection after expected coordinate translation. |
| Scale | Detection works after deterministic downscale/upscale within sensible phone-screenshot ranges. |
| Luminance | Moderate brightness/contrast changes do not change board identity. |
| Noise/blur | Light deterministic perturbation does not produce a false negative on the core positive corpus. |
| Rejection | Arbitrary square UI regions, menus, and statistics pages do not pass Sudoku structural scoring. |
| Determinism | The same image returns the same geometry/confidence values within defined numerical tolerance. |
| Solver invalid | Duplicate givens are rejected before search. |
| Solver unsatisfiable | Valid-looking unsatisfiable input is distinguished from invalid givens. |
| Solver unique | A unique puzzle returns its one solution and preserves clues/input. |
| Solver multiple | A multiple-solution puzzle is classified as ambiguous after finding a second solution. |
| Build | `gradlew test assembleDebug lintDebug` passes. |

When a fixture fails, tests should print enough diagnostics to identify the file, candidate score/rejection reason, and expected versus actual geometry. Do not weaken thresholds for one named fixture; fix the general detector or document a genuinely unsupported visual pattern.
