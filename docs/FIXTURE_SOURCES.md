# Sudoku screenshot fixtures

The active test corpus consists of the two user-approved screenshots in
`app/src/test/resources/sudoku/fixtures/approved/`. On 2026-09-13 the user explicitly
confirmed keeping the deletions of approved-01.jpg and approved-05.jpg and updating
the inventory to these two remaining images.

| Fixture | Size | Visible features |
|---|---|---|
| approved-04.jpg | 1080×1920 | Thick beveled grid, large bold digits, outline-selected cell, one-row keypad |
| approved-10.jpg | 450×800 | Thin grid, colored highlights, pencil notes, multi-row keypad and an overlapping tooltip |

`APPROVED_URLS.txt` records the supplied download URLs; `approved/SHA256SUMS.txt`
pins the exact bytes. These supplied images do not inherit the licenses or source
attributions of the previously rejected open-source corpus. That old `real/`
corpus is no longer used. These are test-only resources, never APK assets.

`geometry.csv` records bounds independently inspected from each image before
running the detector. `RealCellInkTest` contains visually transcribed visible
number matrices and note positions, independent of OCR output. Its safety checks
ensure visible digits are never classified blank and the note clusters reject.
Some decorated cells remain uncertain; these are not full OCR success fixtures.

Grid tests include both images, deterministic geometric/image transformations,
and synthetic negatives. There are currently no retained real negative fixtures.
Success on this narrow corpus does not establish compatibility with every app.
New images require human selection and independent ground-truth review.
