# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 4C: PREVIEW SIZE + DATE-BADGE POSITION — ANALYSIS & SCOPE CONFIRMATION ONLY

## Role

You are working in the existing SameView Android repository.

DeinWackelbild V1 Block 4 has been implemented, but real-device visual validation exposed exactly two UI problems:

1. The Wackelbild preview is too large vertically.
2. The date badge is not visually anchored inside the actual displayed image area.

This prompt is **ANALYSIS + SCOPE CONFIRMATION ONLY**.

Do not modify files yet.
Do not output code.
Do not begin Block 5.
Do not change unrelated Block-4 behavior.

---

# 1. User-Visible Problem

Real-device validation shows:

- The preview occupies too much of the phone screen.
- With the date toggle present, the lower content is already pushed toward/beyond the visible viewport.
- The product intent was that the Wackelbild screen should fit **as much as reasonably possible on one phone screen without scrolling**.
- The preview was intentionally meant to be smaller than the maximum available space.
- The date badge currently appears visually outside the actual image content / attached to the wrong bounds.
- The badge must be inside the actual visible image, bottom-right.

The desired correction is limited to:

### A. Smaller preview

Reduce the preview's maximum size so that on a normal phone screen there is room for:

- top app bar
- preview
- date toggle
- interaction hint
- later CTA/disclosure content

without making the preview dominate the screen.

The screen should prefer fitting the main controls without scrolling on normal phone heights.

### B. Badge inside actual image

The date badge must sit:

- inside the actual displayed image bounds
- bottom-right
- 8.dp from the image's right edge
- 8.dp from the image's bottom edge

Existing badge style remains unchanged:

- 6.dp radius
- 8.dp horizontal internal padding
- 4.dp vertical internal padding
- `MaterialTheme.typography.labelMedium`
- `SameViewAppSurface`
- white text
- no shadow
- no border.

---

# 2. Authoritative Inputs

Read before analysis:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Inspect current implementation:

- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt`
- `app/src/androidTest/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreenTest.kt`

Inspect related preview/layout constants or helpers only if `WackelbildScreen.kt` directly reuses them.

Do not inspect or change unrelated features.

---

# 3. Repository Baseline

Report:

- branch
- HEAD
- `git status --short`

Preserve all current working-tree state.

---

# 4. Exact Analysis Required

Determine from the actual current code:

1. What exact modifier/layout chain currently sizes the preview?
2. What exact max-height/width constraints are currently used?
3. Why does the preview become as tall as shown on a normal phone?
4. Is the current screen still vertically scrollable?
5. What content is below the preview today?
6. What exact bounds does the date badge currently align against?
7. Why can the badge visually land outside the actual displayed image?
8. Does the preview container exactly match the image bounds, or does it include letterbox/extra layout space?
9. What is the smallest layout change that makes the preview container match the actual displayed image bounds?
10. What is the smallest max-size rule that makes the screen substantially more compact without changing image aspect ratio or cropping?

Do not guess from the screenshot alone. Confirm from the code.

---

# 5. Product Constraints That Must Not Change

Preserve exactly:

- Portrait stays Portrait.
- Landscape stays Landscape.
- No additional crop.
- `ContentScale.Fit`.
- Reference/Capture switching unchanged.
- Tilt unchanged.
- Swipe unchanged.
- 9° trigger / 6° re-arm unchanged.
- Date toggle unchanged.
- Date formatting unchanged.
- Date availability unchanged.
- Badge visual style unchanged.
- Badge remains runtime-only.
- No persisted image mutation.
- No navigation changes.
- No sensor lifecycle changes.
- No network/HQ work.

This is a layout-positioning correction only.

---

# 6. Preview Size Requirement

The corrected layout should prioritize **one-screen usability** on a typical phone.

The preview must no longer consume the majority of the available height.

Analyze a concrete, deterministic sizing rule.

The rule should:

- preserve intrinsic image aspect ratio
- preserve no-crop behavior
- use available width/height
- cap preview height substantially below current behavior
- remain sensible for both Portrait and Landscape comparisons
- remain responsive on Compact / Medium / Expanded widths
- avoid arbitrary device-specific hacks
- leave visible room beneath the preview for the current date row + interaction hint and future CTA/disclosure.

Do not leave the new max-size value as "implementer's choice."

Propose one concrete max-height strategy/value based on current SameView spacing/layout conventions and the real screen structure.

If a fixed `dp` max-height is the smallest safe solution, state the exact value.

If a fraction of available screen/content height is better, state the exact formula.

Do not implement yet.

---

# 7. Badge Position Requirement

The badge must be inside the true displayed image bounds.

If the current outer preview `Box` contains unused area due to `ContentScale.Fit`, the badge must not align to that outer Box.

Analyze the smallest correct structure, for example:

- a child Box whose dimensions exactly match the image's intrinsic aspect-ratio bounds, and
- both `Image` and badge placed inside that same child Box.

Do not add coordinate math if layout structure can solve it cleanly.

The desired geometry is:

- child image-bounds container = actual displayed image area
- image fills that child container with `ContentScale.Fit`
- badge uses `Alignment.BottomEnd`
- 8.dp right/bottom padding from that child container edge.

No overlay outside the image-bounds container.

---

# 8. Strict Scope

Expected likely files:

- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt`
- `app/src/androidTest/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreenTest.kt`
- `docs/IMPLEMENTATION_NOTES.md` only if current Block-4 notes contain a factual layout statement that must be corrected.

Do not assume docs need changing. Confirm from content.

No other file should be necessary.

If another file appears necessary, STOP and explain why.

---

# 9. Tests to Plan

Plan focused tests for:

### Preview size

- Portrait preview no longer exceeds the new max-height rule.
- Landscape preview remains proportionally correct.
- No crop / intrinsic ratio behavior remains green.
- Main lower controls remain reachable/visible on a typical compact phone test size if the current test harness supports viewport sizing.

### Badge position

- Badge bounds are fully inside image bounds.
- Right spacing from image bound ≈ 8.dp.
- Bottom spacing from image bound ≈ 8.dp.
- Portrait and Landscape both pass.
- Badge remains inside after Reference/Capture switching.

Do not introduce screenshot-golden infrastructure.

Use stable bounds assertions already present in `WackelbildScreenTest.kt`.

---

# 10. Regression Safety

Assess only real risks:

- shrinking preview too aggressively
- clipping on small devices
- broken aspect ratio
- accidental crop
- badge overlap with image content edge
- layout regressions in Landscape
- scroll behavior changing unexpectedly.

Do not propose unrelated redesign.

---

# 11. Required Scope Confirmation Output

Return exactly:

## 1. Repository Baseline

- branch
- HEAD
- working-tree state

## 2. Current Layout Evidence

State:

- current preview-sizing code
- current scroll structure
- current badge-parent bounds
- why each visible problem occurs

Use exact paths/line ranges.

## 3. Root Cause

Explain separately:

### A. Preview too large

### B. Badge outside actual image

## 4. Proposed Correction

Give one exact deterministic solution for:

- preview max-size rule
- badge image-bounds container

No "implementer's choice."

## 5. Files Proposed for Modification

| File | Modify / Create | Exact change | Why required |
|---|---|---|---|

List all files.

## 6. Files Explicitly NOT Touched

Confirm no changes to:

- ViewModel
- sensor files
- date formatter
- strings
- navigation
- HQ
- network
- Gradle
- manifest.

## 7. Tests to Add / Update

List exact tests and assertions.

## 8. Risks

List real risks and mitigations only.

## 9. Verification Planned After Implementation

At minimum assess:

- `./gradlew testDebugUnitTest`
- `./gradlew compileDebugAndroidTestKotlin`
- WackelbildScreenTest on `pixel2Api29`
- `./gradlew assembleDebug`
- `git diff --check`

State that real-device visual revalidation is required.

## 10. Scope Confirmation

End exactly with:

**BLOCK 4C SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

Then STOP.

---

# Final Rule

Analysis only.

Fix exactly two things:

1. Preview significantly smaller for one-screen usability.
2. Date badge inside the actual displayed image bounds.

No other behavior changes.
