# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 2B: IMPLEMENTATION

## Role

You are working in the existing SameView Android repository.

Block 2A analysis/scope was reviewed and approved with the following explicit scope corrections:

1. `WackelbildViewModel` must **not** parse `metadata.json` for preview sizing.
2. Do **not** add `sessionViewportRatio`.
3. Do **not** add any `9:16` fallback ratio.
4. The preview size/orientation must follow the intrinsic dimensions/aspect ratio of the persisted `reference.jpg`.
5. `File.exists()` alone is not enough for the local error state — Coil decode/load failure must also show the same local fallback.
6. `CompareScreenTest.kt` is **not** part of Block 2; Block 1 already proved the callback behavior.

This prompt authorizes implementation of **Block 2 only**.

Do not begin Block 3.
Do not add sensor, swipe, date, HQ, network, manifest, Gradle, API, or Custom Tab code.

Implement exactly the approved seven-file scope and nothing else.

---

# 1. Authoritative Inputs

Read before changing anything:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/COMPARE_FLOW_V1.md`
- `docs/COMPARE_SESSION_RENDERING_V1.md`
- `docs/RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `docs/SHARE_COMPARISON_IMAGE_V1.md`

Inspect the current versions of:

- `app/src/main/java/com/isardomains/sameview/MainActivity.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonPreview.kt`
- current string resources
- current navigation/screen tests.

If repository state differs materially from the approved Block 2A analysis, STOP and report the difference before editing.

---

# 2. Repository Baseline

Before modification, record:

- branch
- HEAD
- `git status --short`

Do not touch unrelated pre-existing untracked prompt archives or any other unrelated working-tree state.

---

# 3. Exact Authorized File Scope

You may modify/create exactly these seven files:

1. `app/src/main/java/com/isardomains/sameview/MainActivity.kt`
2. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt`
3. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`
4. `app/src/main/res/values/strings.xml`
5. `app/src/main/res/values-de/strings.xml`
6. `app/src/androidTest/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreenTest.kt`
7. `docs/IMPLEMENTATION_NOTES.md`

No other file may be modified.

If a sixth/eighth file becomes necessary, STOP and report why. Do not expand scope.

---

# 4. MainActivity Navigation

Implement the real destination using the existing Share Comparison route pattern.

Add:

- `ROUTE_WACKELBILD = "wackelbild"`
- `ARG_WACKELBILD_SESSION_ID = "sessionId"`
- `ROUTE_WACKELBILD_WITH_ARGS = "$ROUTE_WACKELBILD/{$ARG_WACKELBILD_SESSION_ID}"`

Add route builder:

`private fun wackelbildRoute(sessionId: String): String = "$ROUTE_WACKELBILD/${Uri.encode(sessionId)}"`

Wire the existing Block-1 callback in the existing `CompareScreen(...)` call:

`onCreateWackelbild = if (sessionId != null) { { navController.navigate(wackelbildRoute(sessionId)) } } else null`

Add a new `composable(...)` destination using a `NavType.StringType` `sessionId` nav argument.

Destination body:

`WackelbildScreen(onBack = { navController.popBackStack() })`

Follow the exact structural pattern used by `ShareComparisonScreen`/`CreateVideoScreen`.

Do not change existing routes or callbacks.

Do not add deep links.

---

# 5. WackelbildViewModel — Minimal State Only

Create:

`app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`

Requirements:

- `@HiltViewModel`
- inject `SavedStateHandle`
- inject `@ApplicationContext Context`
- obtain `sessionId` from `SavedStateHandle["sessionId"]`
- resolve:
  - session directory using the established repository pattern
  - `reference.jpg` as `File(sessionDir, "reference.jpg")`

Expose only the minimal state needed by Block 2.

Do **not** add:

- `sessionViewportRatio`
- metadata parsing
- date state
- sensor state
- swipe state
- operation phase
- upload state
- network state
- temp-file state
- fallback-quality logic.

A simple Block-2 state shape may contain:

- `referenceFile: File`
- whether the file is structurally present before loading if useful

But the screen must still handle actual Coil load failure independently.

No DataStore.
No session metadata write.
No file mutation.

---

# 6. WackelbildScreen — Screen Shell

Create:

`app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt`

Use the established SameView destination pattern:

- `Scaffold`
- `TopAppBar`
- Back navigation icon
- screen title resource
- content area
- `hiltViewModel<WackelbildViewModel>()` following existing screen conventions

No dialog/bottom sheet.

No network.

No future controls.

---

# 7. Reference Preview

The initial and only image shown in Block 2 is persisted:

`reference.jpg`

Use the existing Coil `AsyncImage` dependency/pattern.

Requirements:

- model = actual `File`
- `ContentScale.Fit`
- no crop
- no slider
- no frame
- no animation
- no Capture image yet
- no date overlay
- no labels
- no metadata header

## 7.1 Aspect ratio source

The preview sizing must derive from the **intrinsic dimensions of the successfully loaded `reference.jpg`**, not from `metadata.json`.

Do not parse viewport metadata.

Do not use a hardcoded Portrait/Landscape fallback ratio.

Use Coil/Compose image state or another existing lightweight image-loading mechanism to obtain the successful drawable/intrinsic size and derive:

`aspectRatio = intrinsicWidth / intrinsicHeight`

Then size the preview container proportionally.

If intrinsic dimensions are unavailable/invalid, treat this as the same local preview-load error.

The persisted `reference.jpg` itself is the visual source of truth for Block 2.

## 7.2 Moderate sizing

The image should be centered and intentionally moderate in size.

Reuse the **behavioral pattern** from `ShareComparisonPreview`:

- fit within available width/height
- cap preview height
- preserve actual image aspect ratio
- never stretch to fill all available screen space

Do not copy slider/caption/chrome behavior.

For Expanded layouts, keep the existing SameView content max-width pattern (`widthIn(max = 680.dp)` where appropriate).

Do not invent a new responsive-layout system.

---

# 8. Local Error State

The screen must handle both:

1. `reference.jpg` missing/not present
2. file exists but Coil cannot decode/load it

Both cases use the same minimal local fallback state.

Do not add the later full ordering/preparation error model.

Use a simple SameView-style local message following the existing Compare fallback pattern.

The fallback must:

- stay on the Wackelbild screen
- retain the normal TopAppBar and Back action
- show no retry button unless an existing direct screen-shell precedent clearly requires it
- perform no file repair
- perform no navigation elsewhere
- perform no network request

Do not invent an edit flow.

---

# 9. Strings

Use existing Block-1 menu string unchanged.

Add only strings needed by Block 2.

At minimum:

## English

- `wackelbild_screen_title` = `Create lenticular print`
- a Back content description if the existing screen pattern uses a dedicated resource
- minimal preview-load fallback title/body

## German

- `wackelbild_screen_title` = `Wackelbild erstellen`
- corresponding Back description
- corresponding minimal fallback title/body

Keep the fallback copy generic and local to "this comparison/image cannot be shown" rather than using later upload/preparation wording.

Do not add:

- sensor hints
- swipe hints
- date strings
- ordering CTA
- transfer disclosure
- loading strings
- error/retry upload strings
- fallback-quality strings

Those belong to later blocks.

---

# 10. Instrumentation Tests

Create exactly:

`app/src/androidTest/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreenTest.kt`

Do not modify `CompareScreenTest.kt`.

Add focused coverage for Block 2 only.

At minimum test:

1. screen renders the expected title
2. Back callback is invoked
3. valid `reference.jpg` displays successfully
4. intrinsic aspect ratio is preserved for:
   - Portrait reference
   - Landscape reference
5. no additional crop is applied (`ContentScale.Fit` behavior, asserted via stable UI/layout evidence rather than screenshot-only brittle checks)
6. missing `reference.jpg` shows local fallback
7. undecodable/corrupt existing `reference.jpg` also shows the same local fallback
8. no Capture/date/sensor/order UI exists yet
9. the screen remains usable in Compact and Expanded width constraints where existing test utilities allow this

If direct Hilt/ViewModel filesystem setup requires an existing test helper pattern, reuse that pattern.

Do not add a new test library.

## Navigation wiring

Do not re-test the Block-1 callback in `CompareScreenTest`.

If the repository has no established MainActivity navigation-test harness, do not invent a large new harness in Block 2.

It is sufficient to:

- compile the MainActivity route wiring
- test the Wackelbild screen itself
- manually verify the route on a Managed Device/debug build after implementation.

---

# 11. Documentation — IMPLEMENTATION_NOTES Only

Update:

`docs/IMPLEMENTATION_NOTES.md`

Add a concise DeinWackelbild Block 2 status entry consistent with the file's existing per-feature implementation logging style.

Document only what actually landed:

- real route/navigation wiring
- dedicated Wackelbild screen
- initial local Reference preview
- Back
- local missing/decode-failure fallback
- no sensor/date/HQ/network yet

Do not update unrelated documentation.

Do not change release/privacy claims yet because no network capability exists in Block 2.

---

# 12. Files Explicitly Forbidden

Do not modify/create:

- `CompareScreen.kt`
- `CompareScreenTest.kt`
- sensor files
- `TiltProvider`
- hysteresis logic
- swipe logic
- date overlay files
- date formatting
- HQ renderer files
- `ShareImageRenderer.kt`
- `WackelbildPrintRenderer`
- `WackelbildDimensionResolver`
- temp-file manager
- network/API files
- OkHttp
- Gradle files
- AndroidManifest
- `INTERNET`
- partner key/build config
- `androidx.browser`
- Custom Tabs
- upload/loading/error state machine
- fallback-quality dialog
- release/privacy docs
- `COMPARE_FLOW_V1.md`
- DeinWackelbild spec/plan
- project instructions
- unrelated code/docs.

No refactoring.
No formatting cleanup.
No renames.

---

# 13. Regression Safety

Existing behavior must remain unchanged:

- CompareScreen menu content from Block 1
- Share Image
- Create Video
- existing navigation destinations
- existing back stack
- session files
- responsive behavior of existing screens
- all existing tests

The only new behavior is:

`CompareScreen callback → wackelbild/{sessionId} → WackelbildScreen → reference.jpg preview → Back`

No other product behavior is introduced.

---

# 14. Verification

After implementation, run:

1. `./gradlew testDebugUnitTest`
2. `./gradlew compileDebugAndroidTestKotlin`
3. the narrowest relevant `WackelbildScreenTest` instrumentation/Managed Device command
4. a relevant existing navigation/screen test suite if practical
5. `./gradlew assembleDebug`
6. `git diff --check`
7. `git status --short`

Manual Managed Device validation:

- open a saved Comparison
- Share menu
- tap Wackelbild item
- confirm Wackelbild screen opens
- confirm Reference image is visible
- confirm Portrait and Landscape sessions preserve orientation/aspect ratio
- press Back and confirm return to CompareScreen
- confirm no network behavior exists

Real physical-device validation is **not required** for Block 2.

Do not suppress failures.

If unrelated pre-existing failures occur, establish baseline evidence before declaring them pre-existing.

---

# 15. Required Final Report

Return exactly:

## 1. Repository Baseline

- branch
- HEAD
- initial working-tree state

## 2. Files Modified / Created

List exactly the seven authorized files.

State explicitly whether any unauthorized file changed.

## 3. Implementation Summary

Describe:

- route constants
- route builder
- Compare callback wiring
- navigation destination
- ViewModel minimal state
- intrinsic-ratio Reference preview
- local missing/decode fallback
- strings
- tests
- IMPLEMENTATION_NOTES update

Explicitly confirm:

- no metadata parsing
- no `sessionViewportRatio`
- no hardcoded preview ratio
- `CompareScreenTest.kt` untouched
- no sensor/date/HQ/network work

## 4. Regression Safety

Confirm existing Compare/Share Image/Create Video/navigation behavior was not changed.

## 5. Tests / Verification

Report exact commands and results:

- unit tests
- AndroidTest compile
- Wackelbild instrumentation/Managed Device tests
- assembleDebug
- `git diff --check`
- final `git status --short`

State anything not run and why.

## 6. Manual Validation

Report the route/preview/Back checks performed, if any.

## 7. Diff Scope

Confirm exactly the authorized seven files changed and no unrelated edits exist.

## 8. Remaining Work

State only:

- Block 3 will add tilt/swipe interaction.
- No Block 3 work was performed here.

## 9. Gate Result

Choose exactly one:

- **BLOCK 2 COMPLETE — READY FOR REVIEW**
- **BLOCK 2 INCOMPLETE — USER DECISION REQUIRED**

Do not begin Block 3 automatically.

---

# Final Rule

Implement exactly Block 2.

Seven files maximum.

Reference preview only.

No metadata ratio.
No sensor.
No swipe.
No date.
No HQ.
No network.
No manifest.
No Gradle.
No Custom Tabs.
No unrelated cleanup.

If anything requires scope expansion, stop and report it instead of implementing it.
