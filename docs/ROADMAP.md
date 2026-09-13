# Roadmap

## Phase 1 — Foundation
- [x] Android/Kotlin/Compose scaffold
- [x] Sudoku board model and validation
- [x] Deterministic solver
- [x] Solver unit tests
- [x] Accessibility service boundary

## Phase 2 — Capture
- [ ] MediaProjection permission flow
- [ ] Foreground capture service for modern Android
- [ ] One-shot screen capture
- [ ] Debug screen for saving/previewing captures

## Phase 3 — Universal board detection
- [ ] Threshold/grayscale preprocessing
- [ ] Locate dominant square Sudoku grid
- [ ] Perspective correction
- [ ] Calculate 81 cell rectangles dynamically
- [ ] Unit/instrumentation tests using sample screenshots from multiple apps

## Phase 4 — Digit recognition
- [ ] Blank-cell detection
- [ ] Normalize digit crops
- [ ] Classify digits 1–9
- [ ] Confidence scoring
- [ ] Reject ambiguous scans

## Phase 5 — Manual mode
- [ ] Floating accessibility overlay
- [ ] Scan button
- [ ] Solved-board preview
- [ ] “Autofill?” confirmation
- [ ] Detect target cell and number-button geometry
- [ ] Fill only originally empty cells

## Phase 6 — Auto mode
- [ ] Explicit Auto toggle
- [ ] Scan → validate → solve → fill flow
- [ ] Abort immediately on layout change or recognition uncertainty
- [ ] Adjustable gesture delay

## Phase 7 — Hardening
- [ ] Test several unrelated Sudoku apps
- [ ] Handle dark mode and colored themes
- [ ] Handle animations and selected-cell highlights
- [ ] Rotation / different resolutions / aspect ratios
- [ ] Accessibility-node-first optimization where available
