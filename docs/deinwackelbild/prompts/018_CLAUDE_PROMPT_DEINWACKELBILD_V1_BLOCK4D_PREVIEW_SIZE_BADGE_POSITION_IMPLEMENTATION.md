# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 4D: PREVIEW SIZE + DATE-BADGE POSITION — IMPLEMENTATION

## Role

You are working in the existing SameView Android repository.

DeinWackelbild V1 Blocks 1–3 are complete and committed.
Block 4 date-overlay work is implemented and currently uncommitted.

Real-device visual validation exposed two concrete Block-4 layout bugs:

1. The Wackelbild preview is too large and dominates the phone screen.
2. The date badge is aligned to the outer preview container rather than the actual displayed image bounds when the preview height cap engages, so the badge can appear outside the visible image.

Block 4C analysis identified the root cause and an exact minimal fix.

This prompt authorizes that implementation only.

Do not begin Block 5.
Do not change date logic.
Do not change sensor/swipe behavior.
Do not add HQ/network/order functionality.

---

# 1. Authoritative Inputs

Read before changing anything:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Inspect current implementation:

- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt`
- `app/src/androidTest/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreenTest.kt`

Read-only precedent:

- current preview sizing in `CreateVideoScreen.kt`

If repository state differs materially from the completed Block 4C analysis, STOP and report the difference before editing.

---

# 2. Repository Baseline

Before modification record:

- branch
- HEAD
- `git status --short`

Expected state:

- Block 4 date-overlay files are currently uncommitted
- no unrelated tracked modifications
- pre-existing prompt-log directories may remain untracked.

Do not disturb unrelated working-tree state.

---

# 3. Exact Authorized File Scope

Modify exactly these two files:

1. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt`
2. `app/src/androidTest/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreenTest.kt`

No third file may be changed.

If another file becomes necessary, STOP and report why.

Do not modify `docs/IMPLEMENTATION_NOTES.md` because the current notes do not record the old fixed 500dp formula and remain factually correct.

---

# 4. Problem A — Preview Too Large

Current behavior uses:

- `MAX_PREVIEW_HEIGHT_DP = 500`
- height derived from full available width
- no relationship to actual available content height.

This causes Portrait previews to consume too much of a normal phone screen and leaves insufficient visible room below for:

- date toggle
- interaction hint
- later CTA/disclosure.

The product intent is:

- preview should be moderate in size;
- main controls should fit on a normal phone screen without scrolling where reasonably possible;
- lower scroll remains only as a safety net for genuinely small/short layouts.

---

# 5. Exact Preview-Sizing Fix

Reuse the existing SameView precedent from `CreateVideoScreen.kt`:

- max media height = `availableContentHeight * 0.62f`

Do not invent a different percentage.

## 5.1 Obtain actual available content height

In `WackelbildScreen.kt`:

- after applying Scaffold `paddingValues`,
- wrap the existing outer content area in `BoxWithConstraints`
- use `maxHeight` from that actual available content region.

Do not use full device height including system bars/top bar.

## 5.2 Exact sizing algorithm

For the loaded image ratio:

`ratio = imageWidth / imageHeight`

Use exactly:

1. `maxPreviewHeight = availableContentHeight * 0.62f`
2. `heightFromWidth = availableWidth / ratio`
3. `effectiveHeight = min(heightFromWidth, maxPreviewHeight)`
4. `effectiveWidth = effectiveHeight * ratio`

Then center the preview container.

Important:

- when `heightFromWidth <= maxPreviewHeight`, the image may still use the full available width;
- when the height cap engages, **width must be recomputed** from `effectiveHeight * ratio`;
- never keep full `availableWidth` after the height has been capped.

Remove/retire the old fixed `MAX_PREVIEW_HEIGHT_DP = 500` behavior if no longer used.

No hardcoded replacement dp cap.

---

# 6. Preview Container Must Equal Actual Image Bounds

The preview `Box` that hosts:

- visible `Image`
- date badge

must itself be exactly:

`effectiveWidth × effectiveHeight`

This container is the actual displayed image-bounds container.

The image must fill that container with:

- `ContentScale.Fit`

Because the container already matches the image ratio, there must be no letterbox space inside it.

Do not add coordinate math.

Do not add a second overlay container if unnecessary.

Do not crop.

Do not stretch.

---

# 7. Date Badge Position Fix

Keep the current badge implementation and styling unchanged:

- `Alignment.BottomEnd`
- 8.dp right/bottom margin
- 6.dp corner radius
- 8.dp horizontal padding
- 4.dp vertical padding
- `labelMedium`
- `SameViewAppSurface`
- white text
- no shadow
- no border.

The fix is structural:

- the badge must remain a sibling of the `Image`
- inside the exact image-bounds preview container
- therefore its 8.dp margin is measured from the actual image edge.

Do not change DateBadgeFormatter.
Do not change date state.
Do not change toggle availability.
Do not change badge text.

---

# 8. Fallback Preview

The current local fallback also uses the old fixed preview-height behavior.

Update the fallback preview sizing to use the same dynamic available-height cap concept so it does not again dominate the screen.

Keep fallback behavior otherwise unchanged:

- same copy
- same semantics
- same Back/navigation behavior
- no retry
- no file repair.

Do not invent an intrinsic aspect ratio for a missing/corrupt file.

Use the smallest consistent fallback sizing compatible with the new max-height rule.

---

# 9. Scroll Behavior

Preserve the current architecture:

- preview outside the vertical scroll region
- lower content column remains scrollable as safety net.

Do not remove scrolling completely.

The intended outcome is:

- on a normal phone, date toggle **and interaction hint** should be visible without scrolling;
- on very short/small layouts, scrolling may still be necessary.

Do not add a second scroll container.

---

# 10. Product Behavior Explicitly Unchanged

Do not change:

- initial Reference image
- Reference/Capture switching
- tilt
- swipe
- 9° trigger
- 6° re-arm
- swipe override
- sensor lifecycle
- date toggle
- date availability
- date formatting
- date badge style
- accessibility action
- local fallback logic
- navigation
- persisted files
- strings
- metadata handling.

This is layout geometry only.

---

# 11. Instrumentation Tests — Required Complete Set

Modify only:

`WackelbildScreenTest.kt`

Keep all existing tests unless they need a minimal expectation update because the preview is intentionally smaller.

Do not weaken no-crop/intrinsic-ratio assertions.

Add focused regression tests.

## 11.1 Preview height cap — Portrait

Add:

`preview_tallPortraitImage_heightDoesNotExceed62PercentOfAvailableContent`

Use a tall Portrait test image, e.g. 1080×1920.

Assert:

- preview height is no greater than 62% of the actual Wackelbild content-area height, with a small tolerance;
- preview remains non-zero and visible.

Do not compare against full device/window height if the Scaffold/top bar/system insets are excluded in production.

Use the same content-area semantics/bounds that correspond to the production `BoxWithConstraints`.

If needed, add one stable test tag to the outer content-bounds container in `WackelbildScreen.kt`; do not add unrelated test tags.

## 11.2 Portrait aspect ratio preserved

Existing Portrait ratio/no-crop tests must remain green.

If needed add/assert:

`effectiveWidth / effectiveHeight ≈ intrinsicWidth / intrinsicHeight`

with small tolerance.

## 11.3 Landscape aspect ratio preserved

Existing Landscape ratio/no-crop tests must remain green.

No stretched Landscape preview.

## 11.4 Date toggle visible without scrolling

Add:

`dateToggleRow_isDisplayedWithoutScrolling_forTallPortraitImage`

On a normal phone-sized Managed Device viewport:

- load a tall Portrait reference/capture pair;
- do not call `performScrollTo`;
- assert the date-toggle row is displayed.

This directly protects the reported symptom.

## 11.5 Interaction hint also visible without scrolling

Add:

`interactionHint_isDisplayedWithoutScrolling_forTallPortraitImage`

On the same normal phone-sized layout:

- do not scroll;
- assert the interaction hint title is displayed;
- assert the supporting subtitle is displayed.

This is mandatory.

It is not sufficient that only the toggle is visible.

The goal is that the currently available main controls/content below the preview fit naturally on screen.

## 11.6 Badge fully inside actual image — Portrait

Enable the date overlay.

Get bounds for:

- actual displayed Reference image node
- date badge node.

Assert:

- badge left >= image left
- badge top >= image top
- badge right <= image right
- badge bottom <= image bottom.

Do not assert against the outer screen/container.

## 11.7 Badge right/bottom spacing — Portrait

Assert approximately:

- `image.right - badge.right ≈ 8.dp`
- `image.bottom - badge.bottom ≈ 8.dp`

Use small density-safe tolerance.

## 11.8 Badge inside image — Landscape

Repeat containment for Landscape.

## 11.9 Badge spacing — Landscape

Repeat right/bottom 8.dp spacing for Landscape.

## 11.10 Badge after switching to Capture

Enable date overlay.

Switch Reference → Capture using the existing test mechanism.

Get:

- Capture image bounds
- badge bounds.

Assert:

- badge is fully inside Capture image bounds
- right/bottom spacing remains ≈8.dp.

This protects against future divergence between the image nodes.

## 11.11 No-crop regression

Existing `ContentScale.Fit` / no-additional-crop test must remain green.

Do not replace it with only bounds tests.

## 11.12 Current Block-4 functionality remains green

Existing tests for:

- toggle default OFF
- Reference-date availability
- Capture missing-date behavior
- badge Reference/Capture text switching
- year-only/year-month precision
- fallback
- tilt/swipe interaction
- accessibility

must remain green.

Do not remove or weaken them to accommodate layout changes.

---

# 12. Test Implementation Guidance

Use existing test patterns already present in `WackelbildScreenTest.kt`, especially:

- `getUnclippedBoundsInRoot()`
- existing test image helpers
- existing Portrait/Landscape fixtures
- stable test tags.

Do not introduce:

- screenshot golden tests
- pixel-diff framework
- new libraries
- brittle child-index assertions.

If the exact available-content bounds cannot currently be queried reliably, add one narrowly scoped semantic test tag to the existing production content container.

Do not tag every layout node.

---

# 13. Regression Safety

Main risks and required mitigation:

## Preview too small

Mitigation:

- use existing SameView 62% precedent
- preserve intrinsic ratio
- verify Portrait and Landscape.

## Badge still outside image

Mitigation:

- width recomputation after height cap
- assert against actual image bounds.

## Controls still below fold

Mitigation:

- assert both date-toggle row **and interaction hint/subtitle** are displayed without scrolling on the Managed Device normal-phone case.

## Landscape layout regression

Mitigation:

- keep existing Landscape tests and add badge containment there.

## Scroll regression

Mitigation:

- keep lower safety-net scroll in place
- do not add/remove scroll containers beyond the approved geometry fix.

---

# 14. Files Explicitly Forbidden

Do not modify:

- `WackelbildViewModel.kt`
- `DateBadgeFormatter.kt`
- `DateBadgeFormatterTest.kt`
- `WackelbildViewModelTest.kt`
- strings.xml
- strings-de.xml
- sensor files
- `TiltProvider`
- `TiltHysteresisStateMachine`
- `CompassProvider`
- `MainActivity.kt`
- `CompareScreen.kt`
- HQ/rendering files
- network/API files
- Gradle
- AndroidManifest
- docs
- feature spec
- implementation plan.

No refactor.
No cleanup.
No renames.
No unrelated formatting.

---

# 15. Verification

After implementation run:

1. `./gradlew testDebugUnitTest`
2. `./gradlew compileDebugAndroidTestKotlin`
3. `./gradlew pixel2Api29DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.wackelbild.WackelbildScreenTest`
4. `./gradlew assembleDebug`
5. `git diff --check`
6. `git status --short`

Confirm:

- full unit suite green
- full WackelbildScreenTest suite green
- exactly two authorized files changed in this correction step
- no debug logging remains
- no other production behavior changed.

Do not suppress failures.

If unrelated pre-existing Managed Device failures appear outside the Wackelbild test class, do not fix them here.

---

# 16. Physical-Device Validation

This layout fix requires physical validation after automated tests.

On the same phone where the bug was observed, verify:

1. Preview is visibly smaller than before.
2. Date toggle is visible without scrolling.
3. Interaction hint title + subtitle are visible without scrolling.
4. There is still comfortable room below for the later CTA/disclosure additions.
5. Portrait preview still looks proportional.
6. Landscape preview still looks proportional.
7. Date badge is visibly inside the photo.
8. Badge has the expected 8dp visual edge spacing.
9. Badge remains inside after Reference/Capture switching.
10. Tilt/swipe interaction still feels unchanged.

Do not change the 62% value during implementation based solely on emulator appearance.

If physical validation later shows that 62% is still too large/small, handle that as a separate tuning iteration.

---

# 17. Required Final Report

Return exactly:

## 1. Repository Baseline

- branch
- HEAD
- initial status

## 2. Files Modified

List exactly:

- `WackelbildScreen.kt`
- `WackelbildScreenTest.kt`

Confirm no unauthorized file changed in this correction step.

## 3. Implementation Summary

Cover:

- removal/replacement of the fixed 500dp preview cap
- `maxHeight * 0.62f`
- width recomputation from capped height
- exact image-bounds container
- date-badge anchoring
- fallback sizing
- unchanged scroll safety net.

## 4. Test Changes

List every new/updated regression test, including explicitly:

- preview 62% cap
- date toggle visible without scrolling
- interaction hint title visible without scrolling
- interaction hint subtitle visible without scrolling
- Portrait badge containment
- Portrait 8dp right/bottom spacing
- Landscape badge containment
- Landscape 8dp right/bottom spacing
- Capture-switch badge containment
- existing no-crop tests preserved.

## 5. Regression Safety

Confirm all date, sensor, swipe, accessibility, navigation, fallback behavior remained unchanged.

## 6. Verification

Report exact commands/results:

- unit tests
- AndroidTest compile
- Managed Device Wackelbild tests
- assembleDebug
- diff check
- final status.

## 7. Physical-Device Status

State exactly what remains to be revalidated on the physical phone.

## 8. Diff Scope

Confirm exactly two files changed in this correction iteration.

## 9. Gate Result

Choose exactly one:

- **BLOCK 4D IMPLEMENTED — READY FOR PHYSICAL UI REVALIDATION**
- **BLOCK 4D INCOMPLETE — USER DECISION REQUIRED**

Then STOP.

Do not begin Block 5.

---

# Final Rule

Fix exactly the two validated UI defects:

1. Preview significantly smaller using the existing 62%-of-content-height pattern.
2. Date badge anchored inside the actual displayed image bounds.

The complete currently-visible lower content must be tested without scrolling:

- date toggle
- interaction hint title
- interaction hint subtitle.

No other behavior changes.
