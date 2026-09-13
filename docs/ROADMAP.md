# Roadmap

## Implemented
- Kotlin / Compose app, immutable board model, deterministic unique-solution solver.
- One-shot MediaProjection capture and preview. User confirmed successful scanning
  on a physical phone; the full capture lifecycle checklist remains to be completed.
- Deterministic axis-aligned grid detection, normalization, runtime cell geometry,
  and regression checks using two approved screenshots and synthetic negatives.
- Bundled offline OCR, blank/notes checks, confidence and solution validation.
- Draggable, minimized/expanded accessibility overlay with Scan, solution preview,
  Manual review and Autofill, explicit Auto-after-Scan, and Stop.
- Runtime accessibility/OCR keypad discovery, blank-only fill plans, selected-cell
  verification, and per-entry recapture. Live autofill requires Android 11+.

## Next: physical-device validation and hardening
- [ ] Validate OCR, overlay lifecycle, and real gestures in Manual mode on a phone.
- [ ] Verify Stop, window/layout changes, rotation, notes mode, and ambiguous scans.
- [ ] Complete the capture cancellation/repeated-capture/resource cleanup checklist.
- [ ] Test several unrelated apps, dark themes, different fonts, and resolutions.
- [ ] Expand human-approved positive and real negative fixtures.
- [ ] Support additional keypad layouts and selection styles with reliable evidence.
- [ ] Perspective correction for skewed boards, if needed.
- [ ] Configurable gesture delay after device behavior has been measured.

See `LIVE_ASSISTANT.md` for setup, safety checks, supported behavior, and limitations.
Implemented behavior is not a claim that every Sudoku app has been device-tested.
