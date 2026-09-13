# Sudoku screenshot fixture sources

The real-world screenshots under `app/src/test/resources/sudoku/fixtures/real/` are **test-only assets**. They are not packaged into the production APK. They exist to make universal board detection and false-positive rejection reproducible across multiple Android Sudoku UIs.

Do not silently replace these files with newer upstream versions. The import workflow pins exact upstream commits so test failures remain reproducible.

## Fixture corpus

### Material Sudoku — `material-sudoku/`

- Upstream: `jdamcd/material-sudoku`
- Pinned commit: `5564b6a7a3d2f9685a50c21971004363ddfbfc40`
- License: Apache-2.0
- Copyright: Jamie McDonald
- Files:
  - `material_01_puzzle.png` ← `screenshots/1.png` — positive board fixture
  - `material_02_scoreboard.png` ← `screenshots/2.png` — likely negative/non-game fixture
  - `material_03_selection.png` ← `screenshots/3.png` — positive board fixture
  - `material_04_dark_mode.png` ← `screenshots/4.png` — positive dark-mode board fixture

Upstream source and license: https://github.com/jdamcd/material-sudoku

### Sudoku by Likhith Praveen K — `likhith-sudoku/`

- Upstream: `likhithpraveenk/sudoku`
- Pinned commit: `cfea7c60c473e90fa34640d46dede14df690790b`
- License: GPL-3.0-or-later
- Copyright: Likhith Praveen K
- Files:
  - `likhith_01_mid_game.png` ← `metadata/android/en-US/images/phoneScreenshots/1.png` — positive board fixture
  - `likhith_02_home.png` ← `.../2.png` — negative fixture
  - `likhith_03_settings.png` ← `.../3.png` — negative fixture
  - `likhith_04_statistics.png` ← `.../4.png` — negative fixture

The upstream README explicitly labels these screenshots as mid-game, home, settings, and statistics. Upstream source and license: https://github.com/likhithpraveenk/sudoku

### dudozermaks/sudoku — `dudozer-sudoku/`

- Upstream: `dudozermaks/sudoku`
- Pinned commit: `8c73f73dfd91a46e220c9bc4e8bceeed9bb84452`
- License: GPL-3.0
- Files:
  - `dudozer_01_home.png` ← `integration_test/screenshots/home_page.png` — negative fixture
  - `dudozer_02_solving.png` ← `integration_test/screenshots/solving_page.png` — positive board fixture
  - `dudozer_03_statistics.png` ← `integration_test/screenshots/statistics_page.png` — negative fixture

Upstream source and license: https://github.com/dudozermaks/sudoku

### SudokuEink — `sudoku-eink/`

- Upstream: `ktacrack/SudokuEink`
- Pinned commit: `4a24dc9d6c802e374ea12d011fb008d7e090ea7d`
- License: MIT
- Author: Jordi Navarro / ktacrack
- Files:
  - `eink_01_game_en.png` ← `images/Game_english.png` — positive high-contrast board fixture
  - `eink_02_game_ca.png` ← `images/Joc_catala.png` — positive high-contrast board fixture in Catalan UI
  - `eink_03_menu_en.png` ← `images/Menu_english.png` — negative fixture
  - `eink_04_menu_ca.png` ← `images/Menu_catala.png` — negative fixture
  - `eink_05_stats_en.png` ← `images/Stats_english.png` — negative fixture
  - `eink_06_stats_ca.png` ← `images/Stats_catala.png` — negative fixture

Upstream source and license: https://github.com/ktacrack/SudokuEink

## Test policy

For every **positive** fixture, Phase 3 must add independently transcribed ground truth containing:

- exact 9×9 clue matrix (`0` for blank),
- expected board bounding rectangle with a reasonable tolerance,
- whether the screenshot is light/dark/high-contrast,
- and any UI condition that makes the fixture noteworthy.

For every **negative** fixture, the detector must return `no board` rather than selecting arbitrary square UI regions.

Ground truth must be reviewed manually from the image; do **not** create expected digits by running the recognition algorithm and then asserting its own output.

The corpus should later grow with screenshots from the target phone and additional open-source apps, but existing fixtures stay pinned for regression testing.
