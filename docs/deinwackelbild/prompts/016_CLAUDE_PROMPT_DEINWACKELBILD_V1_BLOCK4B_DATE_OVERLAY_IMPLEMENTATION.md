# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 4B: DATE OVERLAY — IMPLEMENTATION

## Role

You are working in the existing SameView Android repository.

DeinWackelbild V1 Blocks 1–3 are complete and committed.

Block 4A analysis/scope has been reviewed and approved with two mandatory corrections:

1. **Date-toggle availability depends only on a usable Reference date.**
   `capture.timestampMs` must NOT become an additional availability gate.
2. **Preview badge geometry is fixed for Block 4**:
   - corner radius: `6.dp`
   - horizontal internal padding: `8.dp`
   - vertical internal padding: `4.dp`
   - right/bottom image-edge margin: `8.dp`
   - typography: `MaterialTheme.typography.labelMedium`
   - background: `SameViewAppSurface`
   - text: white
   - no shadow
   - no border/outline

This prompt authorizes **Implementation Block 4 only**.

Implement exactly the approved Date Overlay preview behavior.

Do not begin Block 5.
Do not add HQ/print rendering.
Do not render the date into any persisted or transfer JPEG.
Do not add network/API/order work.

---

# 1. Authoritative Inputs

Read before changing anything:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/SESSION_METADATA_V1.md`
- `docs/DE_LOCALIZATION_UX_REWORK_V1.md`
- `docs/RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `docs/COMPARE_SESSION_RENDERING_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Inspect current implementation and precedent:

- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/CompareLabelLogic.kt`
- relevant metadata-reading code in `ShareComparisonViewModel.kt`
- relevant locale handling in `ShareComparisonViewModel.kt`
- `SettingsSwitchRow`
- the local `InfoToggleRow` pattern in `ShareComparisonScreen.kt`
- SameView theme/color definitions
- current Wackelbild unit/instrumentation tests.

If repository reality materially differs from the completed Block 4A analysis, STOP and report the discrepancy rather than silently expanding scope.

---

# 2. Repository Baseline

Before modification record:

- branch
- HEAD
- `git status --short`

Expected baseline from Block 4A:

- Blocks 1–3 committed
- working tree otherwise clean except the two pre-existing unrelated prompt-log directories.

Do not touch those prompt directories.

---

# 3. Exact Authorized File Scope

Modify/create only these nine files:

1. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`
2. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt`
3. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/DateBadgeFormatter.kt` — new
4. `app/src/main/res/values/strings.xml`
5. `app/src/main/res/values-de/strings.xml`
6. `app/src/androidTest/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreenTest.kt`
7. `app/src/test/java/com/isardomains/sameview/ui/wackelbild/DateBadgeFormatterTest.kt` — new
8. `app/src/test/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModelTest.kt`
9. `docs/IMPLEMENTATION_NOTES.md`

No tenth file may be changed.

If another file becomes necessary, STOP and report why.

---

# 4. Metadata Read — Narrow Scope Only

`WackelbildViewModel` currently does not read metadata.

Add the smallest safe read of:

`filesDir/sessions/<sessionId>/metadata.json`

Read only:

- `reference.date`
- `capture.timestampMs`

Use the existing repository `JSONObject` / `optJSONObject` style and defensive failure behavior.

Do not read or expose:

- title
- description
- location
- branding
- GPS
- visibility
- favorite
- any unrelated metadata.

Missing/corrupt metadata must not crash the screen.

Do not modify metadata.

Do not write metadata.

Do not use filesystem timestamps as a substitute.

Do not substitute the current date.

Use an injectable metadata-reader seam in the ViewModel tests if that is the minimal pattern consistent with the current test architecture.

---

# 5. Reference Date Precision — Preserve Exactly

Reference date is stored as a nullable ISO-style string with variable known precision.

Preserve exactly:

- `YYYY`
- `YYYY-MM`
- `YYYY-MM-DD`

Use the already-established `CompareLabelLogic` precision technique as precedent:

- length 4 → year only
- length >= 7 → year + month
- length >= 10 → full date

Validate enough to avoid crashing on malformed strings.

Never invent:

- a month for year-only data
- a day for year-month data.

Do not change `CompareLabelLogic.kt`.

Do not extract/refactor its private helpers.

The new `DateBadgeFormatter.kt` should remain a small pure helper for this feature.

---

# 6. Capture Date Source

Capture date comes only from:

`capture.timestampMs`

Use the current metadata contract.

For valid current SameView sessions this should exist.

If it is `0`, absent, malformed, or otherwise unusable:

- do not invent a date;
- do not use current device date;
- do not use file modification time;
- do not crash.

### Mandatory availability rule

**The toggle availability is NOT allowed to depend on Capture date validity.**

It depends only on a usable Reference date.

Therefore:

- usable Reference date → toggle enabled
- missing/unusable Reference date → toggle disabled

If Reference is usable but Capture date is unexpectedly unavailable:

- the toggle remains enabled;
- Reference badge still works;
- when Capture is visible, do not display an invented Capture badge;
- keep the rest of the preview fully functional.

Do not silently change this rule to require both dates.

---

# 7. Locale-Aware Date Formatting

Use the app/UI locale precedent:

`context.resources.configuration.locales.get(0)`

Do not use `Locale.getDefault()` as the authoritative UI locale.

Keep locale injection testable in the same narrow style already used by `ShareComparisonViewModel`.

`DateBadgeFormatter` must accept an explicit `Locale`.

Expected semantics:

### Reference

- year-only → localized representation of that year only
- year+month → localized month/year representation without invented day
- full date → localized full numeric/normal app-style date representation

### Capture

- full date from `capture.timestampMs`

Use existing SameView formatting conventions where applicable.

Do not add a new date/time dependency.

Do not include time-of-day.

---

# 8. Temporary Toggle State

Add temporary ViewModel state:

- `dateOverlayEnabled`, initial `false`

Requirements:

- default OFF every fresh Wackelbild screen/ViewModel instance
- no DataStore
- no metadata persistence
- no `SavedStateHandle` persistence
- no session mutation
- no global setting.

Expose only the minimal state/actions needed by the screen.

When the Reference date is unavailable, attempts to enable the overlay must not leave it enabled.

---

# 9. Toggle Availability

Add:

- `isDateOverlayAvailable`

This is `true` **only based on whether the Reference date is usable**.

Again:

`capture.timestampMs` is not part of this availability decision.

When unavailable:

- toggle disabled
- overlay remains OFF
- supporting text shown
- Wackelbild preview remains usable
- tilt/swipe remain usable
- no error/fallback screen
- no navigation to metadata editing.

---

# 10. Toggle Placement and UI

Place the date toggle:

- directly below the preview
- above the existing interaction hint
- inside the existing lower scrollable content region
- no card
- no separate "Options" heading.

Use the existing `SettingsSwitchRow` component.

Because it has no `supportingText` parameter, follow the already-proven local composition pattern from `ShareComparisonScreen`:

- local `Column`
- `SettingsSwitchRow(...)`
- supporting `Text(...)` only when needed.

Do not modify `SettingsSwitchRow`.

### Strings

German:

- toggle: `Datum anzeigen`
- unavailable supporting text:
  `Referenzdatum hinzufügen, um das Datum anzuzeigen.`

English:

- toggle: `Show date`
- unavailable supporting text:
  `Add a reference date to show the date.`

Use feature-scoped string resource names.

Do not add metadata-editing wording.

---

# 11. Date Badge — Fixed Preview Geometry

The badge is a runtime Compose overlay inside the existing preview image area.

It must be aligned to the currently displayed image's bottom-right.

Use exactly:

- corner radius: `6.dp`
- horizontal internal padding: `8.dp`
- vertical internal padding: `4.dp`
- right margin from image edge: `8.dp`
- bottom margin from image edge: `8.dp`
- typography: `MaterialTheme.typography.labelMedium`
- background: `SameViewAppSurface`
- text color: `Color.White`
- no shadow
- no border
- no outline.

Do not leave these values for implementation-time tuning.

Do not make them user-configurable.

Do not add opacity controls.

Do not use a pill shape.

Do not add a label such as "Reference" or "Capture".

Do not add title/location/branding.

---

# 12. Badge Must Align to the Actual Image, Not Merely the Outer Preview Box

This is important because Block 2/3 preserves intrinsic aspect ratio with `ContentScale.Fit`.

Analyze the existing preview geometry while implementing.

The badge must visually sit 8.dp from the **actual displayed image's** bottom-right edge, not from unrelated letterbox/unused container space.

Do not introduce cropping.

Do not stretch images.

Do not alter the existing intrinsic-ratio behavior.

If the current preview `Box` already exactly matches the displayed image bounds, reuse it.

If not, make the smallest local layout adjustment necessary so the overlay is anchored to the image bounds.

Do not redesign the preview.

---

# 13. Live Reference / Capture Badge Switching

When overlay is enabled:

- visible Reference → Reference date badge
- visible Capture → Capture date badge

Use the existing `visibleImage` state from Block 3.

Do not create a second visible-image state.

Switch immediately with the image.

No:

- fade
- crossfade
- animation
- haptic
- sound.

If Capture date is defensively unavailable:

- Capture image remains visible and interactive;
- no invented badge is shown on Capture;
- switching back to Reference shows its valid Reference badge again.

---

# 14. No Persisted Image Mutation

Block 4 is preview/UI only.

Absolutely do not:

- draw into `reference.jpg`
- draw into `capture.jpg`
- draw into `reference-original.jpg`
- draw into any original
- rewrite any session file
- create replacement session JPEGs
- create transfer JPEGs
- invoke HQ rendering.

The badge exists only in Compose preview state.

Later Block 5 will implement the separate print/HQ pipeline.

---

# 15. Preview vs. Future Print Geometry

The fixed Block-4 dp/sp values define the approved **screen-preview appearance**.

Do not convert these values mechanically into future bitmap pixels.

Do not implement print mapping now.

Block 5 must separately derive suitable bitmap-space geometry so the printed/transfer result has the same relative visual character at arbitrary output resolution.

No Block-5 code or abstraction is authorized here.

---

# 16. Accessibility

Preserve the Block-3 preview accessibility action.

Do not break swipe/tilt accessibility behavior.

The date toggle should use standard Material/`SettingsSwitchRow` semantics.

For the badge:

- expose the currently displayed date meaningfully to accessibility;
- avoid creating another interactive element;
- avoid duplicate announcements if the existing preview semantics already provide the cleanest place to expose it.

Use the smallest semantics change possible.

Do not merge semantics in a way that again hides existing test tags or child nodes, as encountered during Block 3.

---

# 17. DateBadgeFormatter Tests

Create pure JVM tests covering at minimum:

### Reference precision

- valid `YYYY`
- valid `YYYY-MM`
- valid `YYYY-MM-DD`
- malformed/empty/null input → unavailable/no formatted value
- year-only never invents month/day
- year-month never invents day

### Locale

Cover at least:

- German
- English

Verify output according to the chosen existing SameView formatting convention.

### Capture

- valid timestamp → full date
- zero/invalid timestamp → unavailable/no formatted value
- no time-of-day

Do not test Android `Context` in this pure formatter test.

---

# 18. WackelbildViewModel Tests

Extend additively.

Cover at minimum:

1. Reference date present → `isDateOverlayAvailable = true`
2. Reference date missing → false
3. malformed Reference date → false
4. Capture timestamp missing/zero while Reference date valid → **availability remains true**
5. default overlay state = OFF
6. enabling when available → ON
7. disabling → OFF
8. enabling when unavailable → remains OFF
9. date state does not interfere with visible-image state
10. date state does not interfere with swipe/tilt arbitration
11. metadata read failure does not crash
12. no invented Capture date.

Do not weaken existing Block-3 tests.

---

# 19. WackelbildScreen Instrumentation Tests

Extend `WackelbildScreenTest.kt` additively.

Cover at minimum:

1. toggle visible
2. default OFF
3. valid Reference date → toggle enabled
4. no Reference date → toggle disabled
5. disabled supporting text shown
6. overlay OFF → no date badge
7. overlay ON + Reference visible → Reference badge visible
8. switch to Capture → Capture badge updates immediately
9. switch back → Reference badge returns
10. Reference date year-only displays only year
11. Reference year-month does not invent a day
12. missing Capture timestamp with valid Reference:
    - toggle remains enabled
    - Reference badge works
    - Capture shows no invented badge
13. tilt/swipe interaction remains functional with toggle row present
14. existing portrait/landscape no-crop/intrinsic-ratio behavior remains green
15. fallback behavior remains unchanged
16. no later-block order/upload/loading UI exists.

Use stable test tags only where needed.

Do not add tags merely to test visual implementation details that can be asserted semantically.

Do not introduce a new test library.

---

# 20. Documentation

Update only:

`docs/IMPLEMENTATION_NOTES.md`

Append a concise Block-4 completion entry recording:

- date toggle added
- default OFF / temporary only
- availability based only on Reference date
- Reference precision preserved
- Capture date from `capture.timestampMs`
- locale-aware formatting
- runtime-only Compose badge
- fixed preview geometry: 6dp radius, 8dp horizontal, 4dp vertical, 8dp edge margin
- white text / `SameViewAppSurface`
- no shadow/border
- no persisted-file mutation
- no HQ/transfer rendering yet.

Do not rewrite previous block entries.

---

# 21. Explicitly Forbidden Files / Areas

Do not modify:

- `MainActivity.kt`
- `CompareScreen.kt`
- `CompareLabelLogic.kt`
- `ShareComparisonViewModel.kt`
- `ShareComparisonScreen.kt`
- `SettingsComponents.kt`
- `TiltProvider.kt`
- `TiltHysteresisStateMachine.kt`
- `CompassProvider.kt`
- any Block-3 sensor tests except the already-authorized `WackelbildViewModelTest.kt`
- `ShareImageRenderer.kt`
- `ReferenceRenderer.kt`
- `CaptionRenderer.kt`
- `SessionStorage.kt`
- Gradle files
- AndroidManifest
- network/API files
- partner-key config
- Custom Tabs
- release/privacy docs
- DeinWackelbild feature spec
- DeinWackelbild implementation plan
- project instruction docs.

No unrelated refactor.
No cleanup.
No renames.
No formatting unrelated code.

---

# 22. Regression Safety

Explicitly preserve:

- initial Reference visibility
- direct Reference/Capture switching
- 9° trigger / 6° re-arm
- swipe behavior
- swipe override arbitration
- sensor lifecycle
- no sensor permission
- preview fallback
- intrinsic image ratio
- no crop
- Back navigation
- Block-1 export menu
- Block-2 route
- Block-3 accessibility action.

If implementing the badge would require changing any of those contracts, STOP rather than proceeding.

---

# 23. Verification

After implementation run:

1. `./gradlew testDebugUnitTest`
2. `./gradlew compileDebugAndroidTestKotlin`
3. `./gradlew pixel2Api29DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.wackelbild.WackelbildScreenTest`
4. `./gradlew assembleDebug`
5. `git diff --check`
6. `git status --short`

Verify:

- all existing unit tests remain green
- new formatter/ViewModel tests green
- all Wackelbild instrumentation tests green
- exactly the nine authorized files changed
- no forbidden file changed
- no persisted session image is written by Block 4
- no diagnostic logging remains.

Do not suppress failing tests.
Do not disable tests.
Do not add lint suppressions/baselines.

---

# 24. Real-Device Validation

Automated tests do not replace visual validation.

After implementation, explicitly report that physical-device validation remains required unless actually performed.

Check physically:

- toggle placement looks correct below preview
- badge is clearly bottom-right of the actual image
- 8.dp image-edge margin looks correct
- 6.dp radius is visibly rounded but not pill-like
- 8/4.dp internal padding looks balanced
- `labelMedium` is readable
- white-on-`SameViewAppSurface` has good contrast on light/dark photos
- badge switches instantly with tilt/swipe
- no flicker
- Portrait and Landscape comparisons look correct
- German/English formatting is correct.

Do not change geometry during implementation merely based on emulator preference. If real-device validation later shows a visual issue, tune it in a separate iteration.

---

# 25. Required Final Report

Return exactly:

## 1. Repository Baseline

- branch
- HEAD
- initial status

## 2. Files Modified / Created

List exactly the nine authorized files actually changed.

Confirm whether any unauthorized file changed.

## 3. Implementation Summary

Cover:

- narrow metadata read
- Reference precision
- Capture timestamp source
- locale formatting
- toggle default/state
- Reference-only availability rule
- defensive missing-Capture behavior
- exact badge geometry/style
- actual-image-edge anchoring
- live Reference/Capture switching
- accessibility
- no persisted mutation
- tests
- documentation.

Explicitly confirm:

- `capture.timestampMs` does NOT gate toggle availability
- no invented Capture date
- no HQ/transfer rendering
- no sensor/swipe/lifecycle changes.

## 4. Regression Safety

Confirm Blocks 1–3 behavior remained unchanged.

## 5. Tests / Verification

Report exact commands and results:

- unit tests
- AndroidTest compile
- Managed Device Wackelbild tests
- assembleDebug
- diff check
- final status.

## 6. Real-Device Validation

State exactly what was or was not physically validated.

## 7. Diff Scope

Confirm exact file scope and no unrelated edits.

## 8. Remaining Work

State only:

- real-device visual validation if pending
- Block 5 will add the separate print/HQ two-file renderer and transfer-image date rendering
- no Block-5 work was performed.

## 9. Gate Result

Choose exactly one:

- **BLOCK 4 COMPLETE — READY FOR REVIEW**
- **BLOCK 4 INCOMPLETE — USER DECISION REQUIRED**

Then STOP.

Do not begin Block 5.

---

# Final Rule

Implement exactly Block 4.

The two review corrections are mandatory:

1. **Reference date alone controls toggle availability.**
2. **Preview badge geometry is fixed at 6dp radius / 8dp horizontal / 4dp vertical / 8dp image-edge margin / labelMedium.**

No HQ.
No transfer rendering.
No network.
No unrelated changes.
