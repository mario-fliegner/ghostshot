# Shared Constrained Viewport — Implementation Plan

**Related specs:** `ALIGNMENT_POINTS_V1.md` §6.3 ("Der Rahmen folgt dem transformierten sichtbaren Referenzbild-Bereich, gekürzt auf den Marker-Viewport"), `CAMERA_WORKFLOW_UX_V1.md`, `REFERENCE_MARKER_DRAG_LOUPE_V1_IMPLEMENTATION_PLAN.md` §2.5 (`computeVisibleImageRect` strategy — already implemented, not part of this plan)

---

# Document Status

- **Planning status:** Complete. Blocks below were derived from direct inspection of current repository source, not copied from any single chat message.
- **Implementation status:** Complete. All four blocks (Test Harness Correction, Production Constrained-Viewport Wiring Fix, CameraScreen Production-Wiring Integration Test, Full Regression Verification & Plan Closure) implemented and genuinely verified as of 2026-07-14 — see Current Progress, each block's Progress record, and the Change Log's closure entry / Amendment (Part 3) for full evidence.
- **Creation date:** 2026-07-13
- **Approved scope:** Exactly these four files —
  - `app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt`
  - `app/src/androidTest/java/com/isardomains/sameview/ui/camera/ReferenceMarkersOverlayUITest.kt`
  - `app/src/androidTest/java/com/isardomains/sameview/ui/camera/CameraScreenConstrainedViewportTest.kt` (new file)
  - `docs/implementation_plans/SHARED_CONSTRAINED_VIEWPORT_IMPLEMENTATION_PLAN.md` (this file)
- **Source-of-truth relationship:** This file is the durable, authoritative record of this investigation and its remaining implementation work. It supersedes any chat-only summary. A future Claude session with no chat history must be able to resume solely from this file plus current repository state.
- **No production or test code was modified while creating this plan.** Only this Markdown file was written. `git status` was inspected but not acted upon beyond reading.

---

# Current Repository State

**`git status --porcelain` at plan creation time:**

```
 M app/src/androidTest/java/com/isardomains/sameview/storage/MediaStoreWriterGpsTest.kt
 M app/src/androidTest/java/com/isardomains/sameview/ui/compare/ShareComparisonScreenTest.kt
 M app/src/androidTest/java/com/isardomains/sameview/ui/settings/SettingsScreenTest.kt
 M app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt
?? -Pandroid
```

**Approved pre-existing changes (unrelated to this plan — do not touch, do not revert, do not re-analyze):**

| File | Origin |
|---|---|
| `app/src/androidTest/java/com/isardomains/sameview/storage/MediaStoreWriterGpsTest.kt` | Prior approved fix (separate API-29 investigation group, already verified) |
| `app/src/androidTest/java/com/isardomains/sameview/ui/settings/SettingsScreenTest.kt` | Prior approved fix (separate API-29 investigation group, already verified) |
| `app/src/androidTest/java/com/isardomains/sameview/ui/compare/ShareComparisonScreenTest.kt` | Prior approved fix (separate API-29 investigation group, already verified) |
| `app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt` | Prior approved fix — `pointerInput(imageBounds)` → `pointerInput(Unit)` + `rememberUpdatedState` race fix for `CompareGuideTipIntegrationTest`. Fully unrelated to the camera viewport work in this plan (different screen, different composable). Verified: 5/5 genuine API-29 runs, S23 clean. |

**Pre-existing untracked file to ignore:** `-Pandroid` (0-byte file at repo root, harmless build-arg debris, unrelated to any of this work — do not delete, do not investigate).

**Files that must remain untouched by this plan** (confirmed via direct source inspection to be unnecessary — see "Approved Scope" below for the evidence):

- `app/src/main/java/com/isardomains/sameview/ui/camera/ReferenceMarkerOverlay.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/CameraViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/ReferenceRenderer.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt`
- `app/src/androidTest/java/com/isardomains/sameview/ui/camera/CameraGuideTipIntegrationTest.kt` (read-only reference for its reusable harness pattern; not modified)
- Any other test file, any other documentation file (including `docs/IMPLEMENTATION_NOTES.md` — a closure entry there would be a reasonable follow-up but is outside this plan's approved file boundary and is not scheduled as a block)

**Discrepancy check before implementing any block:** a fresh session must re-run `git status --porcelain` and confirm it matches the block above (four pre-existing changes + the untracked `-Pandroid` file) before touching anything. If it does not match — stop and investigate before proceeding; do not assume this plan's file list is still accurate without checking.

---

# Problem Statement

This is a real, live production defect, not merely a test artifact. Two related but independently-caused symptoms exist:

**1. Dual/inconsistent viewport in the live camera screen.** `CameraScreen`'s own onSizeChanged handler (on the outer preview-scrim `Box`, `CameraScreen.kt:663-690`) already computes the correct, system-bar-and-aspect-constrained viewport size — `effectiveHeight`/`effectiveWidth`, published via `viewModel.onReferenceViewportChanged(width, height)` into `CameraViewModel`'s `uiState.viewportWidth`/`uiState.viewportHeight` (`CameraViewModel.kt:128-129, 649-669`), with `frameLeftDp`/`frameTopDp` computed to center that constrained frame within the available space. However, the three composables that actually render on top of that frame — `ReferenceImageOverlay`, `ReferenceMarkerOverlay`, and `MarkerEditBorder` (all called from `CameraScreen.kt:780-849`) — ignore this already-correct shared value entirely. Each is instead given `Modifier.fillMaxWidth().aspectRatio(9f/16f).align(Alignment.Center)` (portrait) or the `fillMaxHeight().aspectRatio(16f/9f)` landscape equivalent, and each self-measures its own notion of "viewport" via its own internal `onSizeChanged`. On the API-29 Pixel 2 managed device (and, per direct production instrumentation, in principle on any device where available height is less than the naive 16:9-implied height), this self-measurement yields an idealized ~1080×1920 logical viewport, while the real, correctly-constrained viewport (matching `uiState.viewportWidth/Height`) is ~1080×1731.

**2. Live-vs-capture geometry mismatch (a related but independently provable consequence).** Live overlay drag/scale gestures in `ReferenceImageOverlay` normalize `pan.x/size.width`, `pan.y/size.height` using that same self-measured (idealized) live size (`CameraScreen.kt:1132-1141`). But `CaptureSessionSnapshot` and `ReferenceRenderer.render()` are fed `uiState.viewportWidth`/`uiState.viewportHeight` — the already-correct, CameraScreen-published value (`CameraViewModel.kt:846-847`, confirmed call site in `SessionStorage.kt`). This means the geometry the user manipulates live and the geometry baked into `reference.jpg` at capture time are, right now, computed against two different viewport heights. This is a genuine, independent production correctness issue — it exists regardless of whether any test passes or fails.

Both symptoms share one root mechanism: composables that self-measure their own viewport via `Modifier.aspectRatio(...)` + `onSizeChanged` produce a value that does not match the viewport `CameraScreen` has already correctly computed and published.

---

# Confirmed Root Cause

**Correct value, computed once:** `CameraScreen.kt:663-690` (Box A) — `onSizeChanged` on the outer scrim `Box` computes `effectiveHeight = minOf(availableHeight, size.width * 16 / 9)` (portrait; symmetric `effectiveWidth` logic in landscape), then calls `viewModel.onReferenceViewportChanged(size.width, effectiveHeight)`. `CameraViewModel.onReferenceViewportChanged` (`CameraViewModel.kt:649-669`) guards `width <= 0 || height <= 0` and otherwise updates `_uiState` with `viewportWidth`/`viewportHeight`, plus recomputes `referenceImageDisplayMode`/`referenceImageHasViewportMismatch`. This value is published as `viewModel.uiState: StateFlow<CameraUiState>` (`CameraViewModel.kt:450`) and is the single correct source of truth.

**Three independent, incorrect consumers**, all called from `CameraScreen.kt:780-849`, all given the identical faulty modifier pattern:

```
Modifier.fillMaxWidth().aspectRatio(9f / 16f).align(Alignment.Center)   // portrait
Modifier.fillMaxHeight().aspectRatio(16f / 9f).align(Alignment.Center) // landscape
```

- `ReferenceImageOverlay` (`CameraScreen.kt:780-801`) — internal `var viewportSize by remember { mutableStateOf(IntSize.Zero) }` + `.onSizeChanged { viewportSize = it }` (`CameraScreen.kt:1123, 1131`).
- `ReferenceMarkerOverlay` (`CameraScreen.kt:809-833`) — same self-measurement pattern internally (`ReferenceMarkerOverlay.kt:298, 424`, confirmed but **not part of this plan's file scope** — see Explicit Non-Goals).
- `MarkerEditBorder` (`CameraScreen.kt:837-849`, composable body at `CameraScreen.kt:2595-2653`) — `var viewportSize by remember { mutableStateOf(IntSize.Zero) }` + `Box(modifier = modifier.fillMaxSize().onSizeChanged { viewportSize = it })` (`CameraScreen.kt:2607, 2609-2612`), feeding `vW`/`vH` into `computeVisibleImageRect(...)`.

**Proven, empirically measured discrepancy** (direct production instrumentation added and reverted during this investigation — see "Proven Diagnostics" below): on the API-29 Pixel 2 managed device, `MarkerEditBorder`'s self-measured `onSizeChanged` reports **1080.0 × 1920.0**, while the *same Box's* actual rendered/placed footprint (`onGloballyPositioned`/`boundsInRoot()`) is **(0,0)–(1080,1731)**. `1920 = 1080 × 16/9` exactly (the naive aspect-ratio-implied height, ignoring real system-bar-constrained space). `1731` matches Box A's own independently-computed `effectiveHeight`.

**Test harness reproduces the identical mechanism independently.** `ReferenceMarkersOverlayUITest.setBorderContent` (`ReferenceMarkersOverlayUITest.kt:701-740`) wraps `MarkerEditBorder` in its own, separately-constructed `Modifier.fillMaxWidth().aspectRatio(9f / 16f).testTag("test_viewport")` (or the `16f/9f` landscape variant, line 721-724) — the *exact same* modifier expression as the production call sites, but written independently in the test file with zero reference to `CameraScreen`, `CameraViewModel`, or Box A. This is why a change confined to `CameraScreen.kt` cannot, by itself, affect what these tests observe: the test never invokes `CameraScreen.kt` code at all (confirmed: `setBorderContent` calls `activity.setContent { ... MarkerEditBorder(...) }` directly, `ReferenceMarkersOverlayUITest.kt:714-737`).

Crucially, the existing test assertions are **not** hardcoded against either 1920 or 1731. All five `border_*` tests compute their expected geometry from `composeRule.onNodeWithTag("test_viewport").fetchSemanticsNode().boundsInRoot` — the *true* rendered size of the test's own wrapper — and compare it against `onNodeWithTag("marker_edit_border").boundsInRoot()` (lines 380-390, 415-423, 440-448, 468-480, 502-510). The tests' ground truth is already correct; the defect is purely that `MarkerEditBorder`'s *internal* `onSizeChanged`-derived `vH`/`vW` diverges from that true rendered size.

---

# Proven Diagnostics

Direct production instrumentation (temporary `Log.d("MarkerBorderProdDiag", ...)` inside `MarkerEditBorder`, fully reverted after use — confirmed via empty `git diff`) recorded, on two genuine isolated single-method API-29 runs:

**`border_framesVisibleImageRect_whenLetterboxed`** (image 1000×500, `overlayOffsetY=+0.05`, `SHOW_FULL_IMAGE`):
```
viewportSize (onSizeChanged): width=1080.0 height=1920.0
computed rect: top=786.0 bottom=1326.0
rootBounds (onGloballyPositioned/boundsInRoot): Rect.fromLTRB(0.0, 0.0, 1080.0, 1731.0)
Actual observed test failure: expected top=682.05, actual=692.0
```

**`border_topAndBottom_correctForLandscapeImageWithNonZeroOffsetY`** (image 1600×900, `overlayOffsetY=-0.1`, `SHOW_FULL_IMAGE`):
```
viewportSize (onSizeChanged): width=1080.0 height=1920.0
computed rect: top=464.25 bottom=1071.75
rootBounds (onGloballyPositioned/boundsInRoot): Rect.fromLTRB(0.0, 0.0, 1080.0, 1731.0)
Actual observed test failure: expected top=388.65, actual=370.0
```

Both discrepancies (`786.0−692.0=94.0`, `464.25−370.0=94.25`) closely match `(1920−1731)/2 = 94.5` — consistent with content computed in a naive 1920-tall internal frame being effectively centered within only 1731px of real available space.

**Failed, reverted fix attempt — do not repeat.** A `MarkerEditBorder`-only change from `onSizeChanged` to `onGloballyPositioned`/`boundsInRoot()` (isolated to that one composable) was implemented and tested. Result: the two original tests still failed with *different*, still-wrong numbers (692.0→588.0; 370.0→295.0), **and** a previously-passing test (`border_matchesViewport_whenImageFillsViewport`) newly regressed (expected bottom=1731.0, actual=1637.0 — a third, distinct value, neither 1731 nor 1920). This indicates `onGloballyPositioned`/`boundsInRoot()` was not stable across a full 21-test class run the way it was in isolated single-method runs — the exact mechanism was never root-caused (see "Settled Findings" — accepted residual risk, not to be reopened). This attempt was fully reverted (`git checkout --`, confirmed via empty diff). **The chosen strategy in this plan (Design B, below) does not use `onGloballyPositioned`/`boundsInRoot()` anywhere and therefore does not depend on resolving that open question.**

---

# Settled Findings

### Proven facts
- `CameraScreen.kt`'s Box A (`663-690`) already computes the correct constrained viewport and publishes it via `viewModel.onReferenceViewportChanged` → `uiState.viewportWidth`/`viewportHeight` (`CameraViewModel.kt:128-129,649-669`).
- The three call sites at `CameraScreen.kt:780-849` all use the identical, independently-idealized `aspectRatio(9f/16f)`/`16f/9f` modifier pattern.
- `viewModel.uiState` is a public `StateFlow<CameraUiState>` (`CameraViewModel.kt:450`) — already sufficient to drive and observe a fix without any new public API.
- `CameraViewModel` has a plain, non-Hilt-dependent constructor (`CameraViewModel(context, SettingsRepository)`), confirmed in use at `CameraGuideTipIntegrationTest.kt:341`.
- `CameraViewModel.enterMarkerEditMode()` (`CameraViewModel.kt:995`) and `CameraViewModel.onReferenceImageSelected(uri)` (`CameraViewModel.kt:495`) exist and are directly callable from a test holding a `CameraViewModel` reference.
- `CameraScreen(...)`'s `viewModel` parameter defaults to `hiltViewModel()` but can be freely overridden (`CameraScreen.kt:248`); `guideTipController: GuideTipController? = null` is optional (`CameraScreen.kt:257`) and can be omitted entirely in a new test.
- **A real-`CameraScreen()`-mounting instrumentation test already exists**: `CameraGuideTipIntegrationTest.mountCameraScreenForGuideTipTest()` (`CameraGuideTipIntegrationTest.kt:334-367`) mounts real `CameraScreen()` with a directly-constructed `CameraViewModel`, `GrantPermissionRule.grant(Manifest.permission.CAMERA)` (line 66), and a working real-reference-image-loading pattern (`createReferenceImageUri()`, lines 396-403, using a real asset `portrait_tall.jpg`). This corrects an earlier, incorrect claim (from an intermediate point in this investigation) that no existing test composes `CameraScreen`. This harness *pattern* is the intended reuse target for Block 3 below — the file itself (`CameraGuideTipIntegrationTest.kt`) is not modified.
- Only `ReferenceMarkersOverlayUITest.setBorderContent` uses the faulty `aspectRatio` wrapper. `setOverlayContent` and `setLoupeOverlayContent` (same file) apply `Modifier.fillMaxSize()` directly to `ReferenceMarkerOverlay` with no `aspectRatio` step, and derive their expected geometry from `composeRule.onRoot().boundsInRoot` (the real activity root) — confirmed unaffected by this defect, confirmed not scheduled for any change in this plan.
- All five `border_*` test assertions derive expected values from live `boundsInRoot()` reads, not hardcoded constants — confirmed valid as-is; only the harness's viewport-construction mechanism needs correction (Block 1).
- `loadReferenceImage` (`CameraViewModel.kt:505-519`) is asynchronous (`viewModelScope.launch`, IO dispatcher metadata read) — any new test driving it must `waitUntil` on `uiState.value.referenceImageMetadata != null` before asserting geometry, matching the existing `waitForReferenceTipActive`-style pattern already used in `CameraGuideTipIntegrationTest.kt:375-382`.
- `ReferenceImageOverlay`'s own outer `Box` (`CameraScreen.kt:1128-1143`) carries no `testTag` — confirmed by direct inspection. Direct pixel-level assertion of its live denominator is not possible without adding one; a behavioral/differential assertion is the fallback (see Block 3).
- No `testTag` exists on Box A/Box B or any "constrained frame" container in production — confirmed by grepping every `testTag(` in `CameraScreen.kt`. `viewModel.uiState.value.viewportWidth/Height` is therefore the correct and only ground-truth source for a new integration test, not a semantics query.

### Disproven / rejected approaches
- **"Split formula" hypothesis** (an early black-box reverse-engineering finding suggesting two different heights fed two different terms of the border formula) — disproven. `computeVisibleImageRect()`'s source takes one single `viewportHeight` parameter used consistently throughout; the apparent "split" was an artifact of the reverse-engineering method, not a real code branch.
- **A single, uniform, constant "wrong" viewport height** applied everywhere — disproven. Solving independently per failing test for the production height that would explain each observed value gives inconsistent numbers (≈1749 vs ≈1684) — ruling out a single constant substituted value; the real per-test discrepancy is fully explained by the aspect-ratio/self-measurement mechanism above, not a fixed wrong constant.
- **`MarkerEditBorder`-only switch to `onGloballyPositioned`/`boundsInRoot()`** — implemented, tested, reverted (see "Proven Diagnostics" above). Introduced a regression in a previously-passing test and did not fix the two target tests either. Do not re-attempt in this form.
- **Component-test-only verification of the CameraScreen-level fix** — rejected. `ReferenceMarkersOverlayUITest`'s tests (even after Block 1's harness correction) construct their own independent viewport and never invoke `CameraScreen.kt`; they cannot prove the production wiring is correct. A dedicated CameraScreen-level integration test is mandatory (Block 3).
- **A smaller "production-wiring" test that re-implements the three call-site modifier expressions by hand, without mounting real `CameraScreen`** — rejected. This would duplicate the exact code under test rather than observe it, and there is no existing sub-composable factored out of the three call sites that could be tested in isolation without either mounting `CameraScreen` or hand-copying its wiring.

### Accepted residual risks (do not reopen unless current source directly contradicts them)
- No session migration, no metadata migration, no saved-session rewrite, no `ReferenceRenderer.kt` change, no `SessionStorage.kt` change, no `CameraViewModel.kt` geometry change, no marker persistence migration, no HQ Original migration required. Rationale (already fully traced): `CaptureSessionSnapshot.viewportWidth/Height`, `ReferenceRenderer.render()`'s inputs, and `metadata.json`'s `viewport.width/height` have always used the correct, Box-A-computed value — never the idealized aspect-ratio value. The three-composable bug is confined to the *live* rendering layer and has never touched capture-time rendering or persistence. Markers are in-memory only and unconditionally cleared on reference-image change/removal. `CameraViewModel` has no `SavedStateHandle` and never survives process death.
- No measurement feedback loop / no circular dependency: Box A's own measurement depends only on its parent's constraints, with zero dependency on any descendant (including the three overlay composables) — confirmed from source, not assumed.
- Zero viewport values are safely guarded: `onReferenceViewportChanged` guards `width <= 0 || height <= 0` (`CameraViewModel.kt:1513` region); `MarkerEditBorder`, `ReferenceMarkerOverlay`, and `ReferenceImageOverlay`/`CompareReferenceImage` all already have pervasive `> 0`/null-safe guards around every geometry computation. No NaN/Infinity/division-by-zero risk was found anywhere.
- Rotation may transiently expose stale (pre-rotation, non-zero) geometry due to `key(isLandscape)` (`CameraScreen.kt:696`) forcing disposal/recreation of the overlay subtree before Box A's `onSizeChanged` re-fires — bounded, self-correcting, visual-only. Not eliminated by this plan's fix; not required to be eliminated (would need a `CameraViewModel.kt` change, explicitly out of scope).
- No permission impact, no navigation impact, no CameraX lifecycle impact, no privacy/offline impact — this fix only changes how the three composables are *sized* within `CameraScreen.kt`; it introduces no new permission, no new navigation destination, no new camera binding behavior, no new network/analytics surface.
- **Genuinely open, not resolved, and not required to be resolved by this plan:** the exact Compose-internals mechanism by which `onSizeChanged` (1920) and `onGloballyPositioned`/`boundsInRoot()` (1731) diverge for the *same* `aspectRatio`-constrained `Box`, and why `boundsInRoot()` was observed to be unstable across a full 21-test class run despite being reliable in isolated single-method runs. This plan's chosen strategy (Design B — an explicit, externally-supplied size, never `aspectRatio`, never `onGloballyPositioned`) sidesteps this question entirely rather than resolving it. Do not elevate either "timing" or "test-execution-order" theory to fact without new direct evidence.

---

# Approved Scope

Exactly these files may be modified or created by this plan's blocks:

1. `app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt` (Block 2)
2. `app/src/androidTest/java/com/isardomains/sameview/ui/camera/ReferenceMarkersOverlayUITest.kt` (Block 1)
3. `app/src/androidTest/java/com/isardomains/sameview/ui/camera/CameraScreenConstrainedViewportTest.kt` — new file (Block 3)
4. `docs/implementation_plans/SHARED_CONSTRAINED_VIEWPORT_IMPLEMENTATION_PLAN.md` — this file, updated only per "Progress Update Rules"

No other file is touched by any block in this plan. If, during implementation of any block, source inspection reveals that a file outside this list is actually required, the implementing session must **stop**, document the evidence in an Amendment (see "Progress Update Rules"), and request fresh scope approval before proceeding — it must not silently add the file.

---

# Explicit Non-Goals

- No change to `ReferenceMarkerOverlay.kt` (its internal `onSizeChanged` self-measurement becomes correct automatically once its `CameraScreen.kt` call site supplies a correct constraint — no internal change needed).
- No change to `CameraViewModel.kt` (the correct viewport value already exists and is already published; only its *consumption* at three call sites is wrong).
- No change to `ReferenceRenderer.kt` or `SessionStorage.kt` (capture-time/persistence path already uses the correct value — see "Accepted residual risks").
- No change to `computeVisibleImageRect()` itself (its formula is already correct; the defect is entirely in what viewport size is passed to it, and only at the `MarkerEditBorder` call site does `CameraScreen.kt` construct that value).
- No change to navigation, CameraX lifecycle, permissions, exports, or metadata schemas.
- No change to any unrelated test file (`setOverlayContent`/`setLoupeOverlayContent`-based tests, or any other camera instrumentation test besides the two named above).
- No `docs/IMPLEMENTATION_NOTES.md` completion entry as part of this plan — reasonable as a future follow-up, but outside the approved file boundary; not scheduled as a block.
- No fix for the residual rotation-timing gap (`key(isLandscape)` stale-value window) — accepted, bounded, out of scope.
- No resolution of the open `onSizeChanged`-vs-`boundsInRoot()` Compose-internals question — sidestepped by design, not solved.
- No pixel-perfect assertions for behavior unrelated to this fix.
- No new test-only public API on production classes unless a specific block explicitly calls it out as unavoidable (see Block 3, assertion 4 caveat).

---

# Implementation Strategy

**"Design B"**: at each of the three `CameraScreen.kt` call sites (`ReferenceImageOverlay`, `ReferenceMarkerOverlay`, `MarkerEditBorder`), replace the `aspectRatio(9f/16f)`/`16f/9f` self-measuring modifier with an explicit, externally-supplied size derived from the already-correct `uiState.viewportWidth`/`uiState.viewportHeight`, converted to Dp via the existing `density` value already in scope in `CameraScreen`'s composable body. Composition of all three is additionally gated on `uiState.viewportWidth > 0 && uiState.viewportHeight > 0` (extending the guard pattern `ReferenceImageOverlay`/`ReferenceMarkerOverlay` already partially have via `referenceUri != null`, and which `MarkerEditBorder`'s call site currently lacks entirely). `Alignment.Center` is retained — since Box B's own available space equals the pre-clamp `availableHeight` Box A computes, centering an explicitly-sized child within it is mathematically identical to Box A's own `frameLeftDp`/`frameTopDp` centering math, so the three overlay composables end up sharing exactly the same rect as the camera preview frame itself.

This requires zero changes inside `ReferenceMarkerOverlay.kt`, `MarkerEditBorder`'s own body, or `computeVisibleImageRect()` — each already accepts whatever size its `modifier`/self-measurement reports; today that self-measurement is wrong only because the *external* constraint it's given is wrong.

Separately, the test harness (`setBorderContent`) must stop constructing its own independent `aspectRatio`-based wrapper and instead use an explicit, test-owned, fixed dp size — decoupling the isolated component test from any device's real screen geometry entirely, and making its already-correct assertions (which read `boundsInRoot()` of that same wrapper) internally consistent with `MarkerEditBorder`'s own self-measurement by construction.

Because the isolated test harness change cannot, by construction, verify the actual `CameraScreen.kt` wiring, a third, independent block adds a minimal new integration test reusing `CameraGuideTipIntegrationTest`'s already-proven real-`CameraScreen()`-mounting harness pattern, asserting against `viewModel.uiState` directly (never re-deriving `computeVisibleImageRect()`'s formula in test code).

No alternative design is presented because none of the "Settled Findings" leave an unresolved technical question that would require one.

---

# Implementation Blocks

## Block 1 — Test Harness Correction (`setBorderContent`)

### Status
Complete

### Goal
Replace `setBorderContent`'s independent `aspectRatio`-based wrapper with an explicit, fixed, test-owned viewport size, so the five `border_*` tests' already-correct assertions become internally consistent (their `test_viewport`-derived expectation and `MarkerEditBorder`'s own self-measurement will then be driven by the identical explicit value). This block touches **only test code** and has zero production risk.

### Files
- `app/src/androidTest/java/com/isardomains/sameview/ui/camera/ReferenceMarkersOverlayUITest.kt`

### Preconditions
- None. This block is independent of Block 2 and may be implemented, reviewed, and verified in any order relative to it.

### Exact implementation instructions

In `setBorderContent` (`ReferenceMarkersOverlayUITest.kt:701-740`):

- Add two new parameters with orientation-appropriate fixed defaults, e.g. `viewportWidthDp: Dp = 1080.dp` is **not** appropriate as a literal (it would need density-independent px reasoning) — instead express the fixed size in a way that is stable regardless of device density, e.g. as `Dp` values chosen so they do not coincide with either the idealized `9f/16f`/`16f/9f` ratio or with any known real-device dimension. Example (illustrative, not prescriptive of the exact literal): a portrait default distinctly shaped taller-than-wide (e.g. `540.dp` × `800.dp`) and a landscape default distinctly shaped wider-than-tall (e.g. `800.dp` × `450.dp`). The exact numbers are an implementation-time choice; the constraint is: (a) fixed/hardcoded in the test, never derived from `composeRule`/`Activity`/device metrics; (b) distinct portrait vs. landscape shape so `border_recomputesCorrectly_inLandscapeViewport` (which asserts a *fill*, not letterbox, outcome) keeps working; (c) not equal to any exact `9:16`/`16:9` ratio multiple, to avoid any accidental coincidence with the idealized buggy value.
- Replace the wrapper `Box`'s modifier:
  - Portrait branch (currently `Modifier.fillMaxWidth().aspectRatio(9f / 16f).testTag("test_viewport")`, line 722) → `Modifier.size(width = viewportWidthDp, height = viewportHeightDp).testTag("test_viewport")`.
  - Landscape branch (currently `Modifier.fillMaxHeight().aspectRatio(16f / 9f).testTag("test_viewport")`, line 724) → `Modifier.size(width = viewportWidthDp, height = viewportHeightDp).testTag("test_viewport")` using the landscape-shaped defaults.
- Do **not** change the `MarkerEditBorder(...)` call inside the wrapper (lines 726-733) — its `modifier` parameter is not overridden today and must remain not-overridden; it will continue to receive the wrapper's exact size via its own `modifier.fillMaxSize()` (`CameraScreen.kt:2609-2612`), which is exactly the mechanism being exercised.
- Do **not** change `setOverlayContent` or `setLoupeOverlayContent` — confirmed unaffected by this defect (see "Settled Findings").
- Do **not** change any `@Test` method body, any assertion, any tolerance value, or `computeVisibleImageRect`-equivalent formula written inline in the tests (lines 385-390, 420-423, 445-448, 473-480, 507-510) — these are already correct per the root-cause analysis and must not be touched.
- Imports: adding `androidx.compose.foundation.layout.size` (or equivalent `.width(...).height(...)` modifiers) is allowed; removing the now-unused `aspectRatio`/`fillMaxWidth`/`fillMaxHeight` imports is allowed **only if** they become genuinely unused elsewhere in the file (verify via grep before removing — `fillMaxSize` etc. are used elsewhere in the same file and must not be removed).
- Test-observability: the `test_viewport` tag stays exactly where it is (on the wrapper `Box`); no new tag is needed for this block.
- Forbidden adjacent changes: no change to `editModeBorder_visibleInEditMode`/`editModeBorder_absentWhenNotInEditMode` (they only assert tag presence/absence, unaffected either way — confirm they still pass, do not "improve" them).

### Explicitly untouched
- `CameraScreen.kt`, `ReferenceMarkerOverlay.kt`, `MarkerEditBorder`'s body, `computeVisibleImageRect()`.
- `setOverlayContent`, `setLoupeOverlayContent`, and every test that uses them (14 tests).
- Every assertion body and tolerance value in the five `border_*` tests.

### Risks
- Choosing a fixed size too small relative to the test images' metadata (e.g. smaller than a `4000×4000` test image at `overlayScale=1.0` in `border_neverExceedsViewport_whenImageLargerThanViewport`) could change which coercion branch `computeVisibleImageRect` takes. Mitigate: keep the chosen fixed size in the same order of magnitude as a typical phone viewport (hundreds to low thousands of dp/px), not an extreme value.
- Removing an import that turns out to still be used elsewhere in the file would break compilation. Mitigate: grep the whole file for the symbol before removing any import.

### Fail-fast verification

```
./gradlew compileDebugAndroidTestKotlin
./gradlew pixel2Api29DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceMarkersOverlayUITest#border_framesVisibleImageRect_whenLetterboxed"
./gradlew pixel2Api29DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceMarkersOverlayUITest#border_topAndBottom_correctForLandscapeImageWithNonZeroOffsetY"
./gradlew pixel2Api29DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceMarkersOverlayUITest#border_matchesViewport_whenImageFillsViewport"
./gradlew pixel2Api29DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceMarkersOverlayUITest"
```

Then repeat the full-class run on the S23:

```
./gradlew connectedDebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceMarkersOverlayUITest"
```

Every managed-device invocation above must show, in the Gradle output, the literal lines `Starting 21 tests on pixel2Api29` and `Finished 21 tests on pixel2Api29` (or the appropriate count for a filtered single-method run) — a cached/UP-TO-DATE result without these lines is not evidence and must be discarded; re-run with `--rerun`.

### Completion criteria
- All 21 tests in `ReferenceMarkersOverlayUITest` pass on a genuine (`--rerun`, evidenced by `Starting`/`Finished` lines) API-29 managed-device run.
- All 21 tests pass on a genuine S23 connected-device run.
- `git diff` for this block touches only `ReferenceMarkersOverlayUITest.kt`, and only within `setBorderContent`'s signature/body (plus, if needed, import lines).

### Rollback boundary
`git checkout -- app/src/androidTest/java/com/isardomains/sameview/ui/camera/ReferenceMarkersOverlayUITest.kt`

### Progress record
- Date: 2026-07-13
- Implemented by: Claude (Sonnet 5), STEP 3 implementation session
- Files changed: `app/src/androidTest/java/com/isardomains/sameview/ui/camera/ReferenceMarkersOverlayUITest.kt` (imports + `setBorderContent` only, per diff review before verification)
- Commands run:
  - `./gradlew compileDebugAndroidTestKotlin` — BUILD SUCCESSFUL
  - `./gradlew pixel2Api29DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceMarkersOverlayUITest#border_framesVisibleImageRect_whenLetterboxed"` — genuine (`Starting 1 tests on pixel2Api29` / `Finished 1 tests on pixel2Api29`)
  - `./gradlew pixel2Api29DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceMarkersOverlayUITest#border_topAndBottom_correctForLandscapeImageWithNonZeroOffsetY"` — genuine (`Starting 1 tests on pixel2Api29` / `Finished 1 tests on pixel2Api29`)
  - `./gradlew pixel2Api29DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceMarkersOverlayUITest#border_matchesViewport_whenImageFillsViewport"` — genuine (`Starting 1 tests on pixel2Api29` / `Finished 1 tests on pixel2Api29`)
  - `./gradlew pixel2Api29DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceMarkersOverlayUITest"` — genuine (`Starting 21 tests on pixel2Api29` / `Finished 21 tests on pixel2Api29`)
  - `./gradlew connectedDebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceMarkersOverlayUITest"` — genuine (`Starting 21 tests on SM-S911B - 16` / `Finished 21 tests on SM-S911B - 16`)
- Test counts:
  - API 29 — `border_framesVisibleImageRect_whenLetterboxed`: tests="1" failures="0" errors="0" skipped="0"
  - API 29 — `border_topAndBottom_correctForLandscapeImageWithNonZeroOffsetY`: tests="1" failures="0" errors="0" skipped="0"
  - API 29 — `border_matchesViewport_whenImageFillsViewport`: tests="1" failures="0" errors="0" skipped="0"
  - API 29 — full class: tests="21" failures="0" errors="0" skipped="0"
  - S23 (SM-S911B, Android 16) — full class: tests="21" failures="0" errors="0" skipped="0"
- Result: All mandatory Block 1 verification passed genuinely on both devices. The two previously-failing tests are now green; the previously-passing test remained green; the full 21-test class passed on both API 29 and S23.
- Remaining issues: None for Block 1. (Implementation note, not a deviation requiring an Amendment: rather than keeping a redundant `if (!isLandscape) ... else ...` branch inside the modifier expression with two near-identical `Modifier.size(...).testTag(...)` arms, the orientation branching was consolidated into the two new parameters' default-value expressions — `viewportWidthDp`/`viewportHeightDp` already resolve to the orientation-correct fixed value via `isLandscape`, so the wrapper `Box`'s modifier is a single non-branching `Modifier.size(width = viewportWidthDp, height = viewportHeightDp).testTag("test_viewport")` line. Net effect on every call site — including the one landscape caller, `border_recomputesCorrectly_inLandscapeViewport`, which does not pass these new parameters explicitly — is identical to the plan's literal instruction: each orientation still receives its own fixed, distinct, non-16:9 dp size.)
- User approval for next block: Pending — do not start Block 2 without explicit user approval.

---

## Block 2 — Production Constrained-Viewport Wiring Fix

### Status
Complete — see Amendment 2026-07-14 (Part 2) under Change Log for the corrected verification gate and the reasoning for marking Complete. (Superseded prior status: "Blocked — implemented, but mandatory verification did not pass; see Amendment 2026-07-13," which relied on an invalid gate — see below.)

### Goal
Make `ReferenceImageOverlay`, `ReferenceMarkerOverlay`, and `MarkerEditBorder`'s call sites in `CameraScreen.kt` consume the already-correct `uiState.viewportWidth`/`uiState.viewportHeight` instead of an independently-idealized `aspectRatio(9f/16f)`/`16f/9f` self-measurement. This is the actual production defect fix (both the border-geometry symptom and the live-vs-capture mismatch symptom in the Problem Statement stem from this single change).

### Files
- `app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt`

### Preconditions
- None strictly required from Block 1 — this is an independent file. Recommended order: after Block 1, so the (still test-only) two currently-red tests are already green before this riskier production change lands, but this is not a hard dependency.

### Exact implementation instructions

Inside the `CameraScreen` composable function body (`CameraScreen.kt:247` onward), at the three call sites currently at lines **796-849**:

- `density` (a `LocalDensity.current`, already obtained and in scope earlier in the same function — used at lines 669, 674, 681, 687) is directly usable at these call sites without any new `LocalDensity.current` call.
- Introduce one derived value, computed once, immediately before the three call sites (illustrative, not a mandated literal):

```kotlin
val hasConstrainedViewport = uiState.viewportWidth > 0 && uiState.viewportHeight > 0
val constrainedOverlayModifier = if (hasConstrainedViewport) {
    Modifier
        .size(
            width = with(density) { uiState.viewportWidth.toDp() },
            height = with(density) { uiState.viewportHeight.toDp() }
        )
        .align(Alignment.Center)
} else null
```

- **`ReferenceImageOverlay`** (currently `CameraScreen.kt:780-802`): change the outer guard from `if (referenceUri != null)` to `if (referenceUri != null && hasConstrainedViewport)`, and replace the `modifier = if (!isLandscape) { ... } else { ... }` expression (lines 796-800) with `modifier = constrainedOverlayModifier!!` (safe under the guard) — or equivalently inline the null-check. All other parameters (`referenceUri`, `metadata`, `displayMode`, `offsetX`, `offsetY`, `scale`, `alpha`, `onDragged`, `onScaled`) are unchanged.
- **`ReferenceMarkerOverlay`** (currently `CameraScreen.kt:806-834`): change the guard from `if (referenceUri != null && (markersState.markersVisible || markersState.isEditModeActive))` to additionally require `&& hasConstrainedViewport`, and replace its `modifier = if (!isLandscape) { ... } else { ... }` expression (lines 828-832) the same way. All other parameters unchanged.
- **`MarkerEditBorder`** (currently `CameraScreen.kt:837-849`, currently unguarded at the call site): add a new guard `if (hasConstrainedViewport) { MarkerEditBorder(...) }` around the existing call, and replace its `modifier = if (!isLandscape) { ... } else { ... }` expression (lines 844-848) the same way. All other parameters (`isEditModeActive`, `metadata`, `displayMode`, `overlayOffsetX`, `overlayOffsetY`, `overlayScale`) unchanged. Note `MarkerEditBorder`'s own body already has `if (!isEditModeActive) return` (`CameraScreen.kt:2604`) — the new outer guard is additive, not a replacement for that internal check.
- Do **not** change anything inside `ReferenceImageOverlay`'s, `ReferenceMarkerOverlay`'s (defined in `ReferenceMarkerOverlay.kt` — out of file scope for this block), or `MarkerEditBorder`'s composable *bodies*. Each already correctly consumes whatever size its `modifier` constrains it to.
- Do **not** change `computeVisibleImageRect()`, `normalizedToScreen()`, `screenToNormalized()`, Box A (`663-690`), Box B (`718`), `key(isLandscape)` (`696`), `frameLeftDp`/`frameTopDp` (`417-418`), or `CameraViewModel.kt` in any way.
- Do **not** touch any other call site, control, or layer in `CameraScreen.kt` (grid overlay at line 854 onward, controls overlay, guide tip host, etc.) — this block's diff must be confined to the three call-site expressions plus the one new derived-value declaration immediately above them.
- Imports: `androidx.compose.foundation.layout.size` may need to be added if not already imported (verify via grep — `Modifier.size(` may already appear elsewhere in this large file); `aspectRatio`/`fillMaxWidth`/`fillMaxHeight` imports must **not** be removed even if these three call sites no longer use them, since the file is large and likely uses them elsewhere (verify via grep before touching any import).
- Test-observability: no new `testTag` is introduced by this block. `marker_edit_border`'s existing tag (`CameraScreen.kt:2640, 2648`) is unaffected.
- Expected resulting contract: with a valid (non-zero) `uiState.viewportWidth`/`viewportHeight`, all three composables receive an explicit size equal to that value, centered within Box B exactly as the camera preview frame itself is positioned via `frameLeftDp`/`frameTopDp`. Before the first valid `onSizeChanged` callback from Box A (i.e., `viewportWidth`/`viewportHeight` still `0`), none of the three composables are composed at all (matching the "no zero-size composition" safety property already established for this fix — see "Accepted residual risks").

### Explicitly untouched
- `ReferenceMarkerOverlay.kt`, `CameraViewModel.kt`, `ReferenceRenderer.kt`, `SessionStorage.kt`.
- `computeVisibleImageRect()`, `normalizedToScreen()`, `screenToNormalized()`.
- Box A, Box B, `key(isLandscape)`, camera preview/CameraX binding code, guide-tip orchestration, controls overlay, grid overlay, any top-bar/navigation code in `CameraScreen.kt`.
- `ReferenceMarkersOverlayUITest.kt` (Block 1's concern, not this block's).

### Risks
- **Compile-time null-safety**: `constrainedOverlayModifier` is nullable; every use site must be behind the `hasConstrainedViewport` guard or use a safe/non-null pattern — a naive `!!` outside the guard would crash. Mitigate: structure each of the three call sites so the modifier expression is only evaluated inside the already-guarded `if` block.
- **Regression in `CameraGuideTipIntegrationTest`**: that test's `mountCameraScreenForGuideTipTest()` composes real `CameraScreen()` and asserts on `guide_tip_card`/`camera_history_button`, and one test (`referenceTipVisible_referenceImageSelected_completesTipAndDoesNotReappear`) loads a real reference image — this exercises the exact call sites being changed. Mitigate: run this test class as a regression gate (see verification below) before considering the block complete.
- **First-frame flash**: composing nothing until `hasConstrainedViewport` is true is a *behavior change* from today's aspect-ratio-based immediate composition — today the three composables render (with the wrong size) from the very first frame; after this change they render one frame later (once Box A's real `onSizeChanged` has fired). This is expected and desired (matches "Zero-size safety" already established as safe), but should be visually sanity-checked, not just test-verified, since it is a timing change in when these layers first appear.

### Fail-fast verification

**Corrected 2026-07-14 (Part 2) — see Amendment under Change Log for full evidence.** The original list below included `pixel2Api29DebugAndroidTest CameraGuideTipIntegrationTest` as a mandatory gate. That gate is **invalid** and has been removed: it is proven, via genuine API-29 baseline A/B comparison (pre-Block-2 `CameraScreen.kt` vs. post-Block-2 `CameraScreen.kt`, both reproducing the identical failure at a 100% rate — 2/2 and 3/3 respectively), that `CameraGuideTipIntegrationTest#referenceTipVisible_referenceImageSelected_completesTipAndDoesNotReappear`'s failure on API 29 is a pre-existing race in `CameraScreen.kt`'s guide-tip orchestration (`LaunchedEffect(referenceUri)` vs. the `cameraEligibleTipIds`-keyed effect, `CameraScreen.kt:453-461` and `479-495`) that is causally unrelated to Block 2's diff and reproduces identically with or without Block 2's change present. It is not a valid pass/fail gate for the constrained-viewport fix.

**Corrected mandatory verification (all already run genuinely and passed — see Progress record):**

```
./gradlew compileDebugKotlin
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceMarkersOverlayUITest"
```

Plus a **diff review** (not a Gradle command): confirm via `git diff -- CameraScreen.kt` that (a) exactly one shared `hasConstrainedViewport`/`constrainedOverlayModifier` derived-value declaration exists, (b) all three call sites (`ReferenceImageOverlay`, `ReferenceMarkerOverlay`, `MarkerEditBorder`) consume that same derived value via `modifier = constrainedOverlayModifier!!`, and (c) the `aspectRatio(9f/16f)`/`16f/9f` sizing expressions are absent from exactly those three call sites and only those three (confirmed via grep: 15 remaining usages of `aspectRatio`/`fillMaxWidth`/`fillMaxHeight` elsewhere in the file, none at the three call sites).

**Informative, non-mandatory (retained for historical record, not a gate):**

```
./gradlew connectedDebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.CameraGuideTipIntegrationTest"
```

This exercises real `CameraScreen()` composition with a loaded reference image on S23 and passed 8/8 genuinely (see Progress record) — useful confirmation that the change works end-to-end on a device where the unrelated guide-tip race does not manifest, but it is not required for Block 2's own completion, and its API-29 counterpart is explicitly **not** run as part of this block's gate (see above). `CameraGuideTipIntegrationTest` remains a valid, relevant test for its own feature (guide tips) and is unaffected as a suite — only its one specific, proven-racy assertion is excluded from Block 2's gate.

The managed-device evidence requirement (`Starting N tests on pixel2Api29` / `Finished N tests on pixel2Api29`, `--rerun` mandatory) applies to every command above that targets `pixel2Api29DebugAndroidTest`. (Note: the corrected mandatory list above contains no `pixel2Api29DebugAndroidTest` command for this block — `ReferenceMarkersOverlayUITest`'s API-29 coverage was already established in Block 1, on a file `CameraScreen.kt` cannot possibly affect, per the root-cause analysis; see Amendment for why a fresh API-29 re-run adds no information.)

### Completion criteria

**Corrected 2026-07-14 (Part 2):**
- `compileDebugKotlin` and `assembleDebug` succeed. — **PASSED** (genuine, see Progress record).
- Diff review confirms the change is confined to the one shared derived-value declaration and the three call-site guards/modifiers, with `aspectRatio`-based sizing removed from exactly those three call sites. — **PASSED** (see Progress record).
- `ReferenceMarkersOverlayUITest` (the existing test suite most directly coupled to the geometry Block 2 changes) passes in full on S23. — **PASSED, 21/21** (genuine, see Progress record). (Its API-29 run is Block 1's concern, already proven 21/21, and is structurally incapable of observing any `CameraScreen.kt` change — not re-required here.)
- `git diff` for this block touches only `CameraScreen.kt`, confined to the three call-site expressions and their guards plus the one new derived-value declaration. — **PASSED** (confirmed via diff review).
- ~~`CameraGuideTipIntegrationTest` passes in full, genuinely, on both API 29 and S23.~~ **Removed as an invalid gate** — proven (via baseline A/B comparison) to fail identically with or without Block 2's change present on API 29, due to a pre-existing, unrelated race in `CameraScreen.kt`'s guide-tip orchestration. Retained as an informative, non-mandatory S23 check only (passed 8/8).

### Rollback boundary
`git checkout -- app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt`

### Progress record
- Date: 2026-07-13
- Implemented by: Claude (Sonnet 5), STEP 3 implementation session
- Files changed: `app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt` (one derived-value declaration + three call-site guards/modifiers only, per diff review before verification — confirmed no other line touched, no import changed since `androidx.compose.foundation.layout.size` was already imported and `aspectRatio`/`fillMaxWidth`/`fillMaxHeight` remain used 15 times elsewhere in the file)
- Commands run:
  - `./gradlew compileDebugKotlin` — BUILD SUCCESSFUL
  - `./gradlew assembleDebug` — BUILD SUCCESSFUL
  - `./gradlew connectedDebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.CameraGuideTipIntegrationTest"` (S23) — genuine, `Starting 8 tests on SM-S911B - 16` / `Finished 8 tests on SM-S911B - 16`, 8/8 PASS
  - `./gradlew connectedDebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceMarkersOverlayUITest"` (S23) — genuine, `Starting 21 tests on SM-S911B - 16` / `Finished 21 tests on SM-S911B - 16`, 21/21 PASS (unchanged from Block 1)
  - `./gradlew pixel2Api29DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.CameraGuideTipIntegrationTest"` (API 29) — **first attempt**: BUILD FAILED before any test ran (`tests="0"`, `Failed to install split APK(s)` / ddmlib `InstallException` — infrastructure-level APK install failure, no `Starting`/`Finished` lines, discarded as non-evidence per plan's own caching/genuineness rules). **Retry, same exact command**: genuine execution (`Starting 8 tests on pixel2Api29` / `Finished 8 tests on pixel2Api29`), `tests="8" failures="1" errors="0" skipped="0"` — `referenceTipVisible_referenceImageSelected_completesTipAndDoesNotReappear` FAILED (`AssertionError` at `CameraGuideTipIntegrationTest.kt:267`, `assertTrue(GuideTipId.REFERENCE in seenTipIds)`); the other 7 tests in the class passed.
- Test counts:
  - S23 — `CameraGuideTipIntegrationTest`: tests="8" failures="0" errors="0" skipped="0"
  - S23 — `ReferenceMarkersOverlayUITest`: tests="21" failures="0" errors="0" skipped="0"
  - API 29 — `CameraGuideTipIntegrationTest`: tests="8" failures="1" errors="0" skipped="0" (one genuine retry after one discarded install-failure attempt)
- Result: **Block 2's mandatory verification did not fully pass.** `compileDebugKotlin`, `assembleDebug`, and both S23 regression runs passed. The mandatory API-29 `CameraGuideTipIntegrationTest` run failed genuinely on `referenceTipVisible_referenceImageSelected_completesTipAndDoesNotReappear`. Per instruction, Block 2 is not marked Complete and no further block was started.
- Remaining issues: See `## Amendment — 2026-07-13 (Block 2 verification failure)` under Change Log for full evidence and root-cause tracing. Summary: the failure traces to a pre-existing race in `CameraScreen.kt`'s guide-tip orchestration (between the synchronous `clearActiveTipWithoutMarkingSeen` eligibility-loss effect at lines 479-495 and the asynchronous `completeTip`/`markTipSeen` effect at lines 453-461), neither of which is touched by Block 2's diff. The production `CameraScreen.kt` edit for Block 2 remains in the working tree, uncommitted, pending user decision (see final report) — it has not been reverted, and Block 2 has not been marked Complete.
- User approval for next block: N/A — Block 2 is not complete; do not proceed to Block 3 or Block 4. Awaiting user decision on the Amendment.

**Update — 2026-07-14 (Part 2), gate correction (historical entries above preserved verbatim, not rewritten):**
- The `pixel2Api29DebugAndroidTest CameraGuideTipIntegrationTest` mandatory gate referenced in "Result" and "Remaining issues" above is now proven **invalid** for this block — see Amendment 2026-07-14 (Part 2) under Change Log for the genuine API-29 baseline A/B comparison (pre-Block-2 `CameraScreen.kt`: 2/2 identical failures; post-Block-2 `CameraScreen.kt`: 3/3 identical failures) proving the failure is a pre-existing, causally unrelated race.
- Corrected Result: **all mandatory verification under the corrected gate has passed genuinely**: `compileDebugKotlin` (PASS), `assembleDebug` (PASS), diff review (PASS — confirmed exact scope), `ReferenceMarkersOverlayUITest` on S23 (PASS, 21/21). `CameraGuideTipIntegrationTest` on S23 (8/8 PASS) is retained as informative, non-mandatory evidence.
- Corrected Remaining issues: none blocking Block 2 itself. The proven-unrelated API-29 guide-tip race is tracked separately — see "Separate Issue Record: API-29 REFERENCE Guide-Tip Completion Race" under the 2026-07-14 (Part 2) Amendment. It is explicitly not a Block 2 blocker and not scheduled into any current block.
- Block 2 Status corrected to **Complete**.
- User approval for next block: superseded — see Amendment for whether Block 3 may now begin.

---

## Block 3 — CameraScreen Production-Wiring Integration Test

### Status
Complete

### Goal
Add one new instrumentation test file that mounts the real `CameraScreen()` (reusing `CameraGuideTipIntegrationTest`'s already-proven harness pattern) and directly proves that `ReferenceImageOverlay`, `ReferenceMarkerOverlay`, and `MarkerEditBorder` all receive the same, correct, `uiState`-published viewport — the one proof that neither `ReferenceMarkersOverlayUITest` (isolated harness) nor any existing test can provide.

### Files
- `app/src/androidTest/java/com/isardomains/sameview/ui/camera/CameraScreenConstrainedViewportTest.kt` (new file)

### Preconditions
- **Block 2 must be complete** (this test is meaningless/would fail against the pre-fix code — that is in fact its purpose: it should fail if run against pre-Block-2 code and pass after).
- Block 1 is not a precondition (independent files) but should realistically already be done first per the recommended overall order.

### Exact implementation instructions

Create a new `@RunWith(AndroidJUnit4::class)` test class. Reuse, do not copy-modify, the pattern already proven in `CameraGuideTipIntegrationTest.kt:334-367`:

- `@get:Rule val composeRule = createEmptyComposeRule()`.
- `@get:Rule val cameraPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)`.
- A private harness function (e.g. `mountCameraScreenForViewportTest()`) that: wakes the test device (reuse the existing `wakeTestDevice()` pattern seen in both existing test files), constructs `CameraViewModel(context, SettingsRepository(settingsPrefs))` directly (no Hilt — `settingsPrefs` via `PreferenceDataStoreFactory.create { File(context.cacheDir, "camera_settings_${UUID.randomUUID()}.preferences_pb") }`, matching `CameraGuideTipIntegrationTest.kt:338-341`), launches `ActivityScenario.launch(ComponentActivity::class.java)`, and calls `activity.setContent { SameViewTheme { CameraScreen(viewModel = viewModel) } }` — `guideTipController` and all other `CameraScreen` parameters may be omitted (all have defaults; `guideTipController: GuideTipController? = null` is nullable and not needed for this test's purpose).
- Reuse the `createReferenceImageUri()` pattern (copy a real test asset, e.g. the same `portrait_tall.jpg` used in `CameraGuideTipIntegrationTest.kt:396-403`, to a `context.cacheDir` file, return its `Uri`) to load a real reference image via `viewModel.onReferenceImageSelected(uri)`, then `composeRule.waitUntil(timeoutMillis = 10_000) { viewModel.uiState.value.referenceImageMetadata != null }` before any geometry assertion (matching the async-load pattern already established).
- After `composeRule.waitForIdle()`, also `composeRule.waitUntil { viewModel.uiState.value.viewportWidth > 0 && viewModel.uiState.value.viewportHeight > 0 }` before any geometry assertion, since Box A's `onSizeChanged` fires asynchronously relative to first composition.
- Call `viewModel.enterMarkerEditMode()` (`CameraViewModel.kt:995`) to activate `MarkerEditBorder`.

**Required assertions (minimum set):**

1. **`MarkerEditBorder` bounds use the constrained viewport.** Use a reference image + `COMPARE_WITH_PREVIEW` combination that fills/overflows the viewport (mirroring the existing, already-proven `border_matchesViewport_whenImageFillsViewport`/`border_neverExceedsViewport_whenImageLargerThanViewport` degenerate case, where `computeVisibleImageRect`'s own `max()`-scale branch collapses the rect to exactly the viewport). Assert `onNodeWithTag("marker_edit_border").fetchSemanticsNode().boundsInRoot.width` and `.height` are each within a small tolerance of `viewModel.uiState.value.viewportWidth.toFloat()` / `.viewportHeight.toFloat()`. **Do not** assert against a hardcoded 1731 or 1920 — the comparison must be against the live `uiState` value, which is itself the thing under test for correct production wiring, not a re-derivation of `computeVisibleImageRect`. (This assertion is width-and-height, not left/top, because there is no `testTag` on the constrained frame's own root-relative origin in production — see "Settled Findings" — so only the size, not the absolute position, can be checked without recomputing `frameLeftDp`/`frameTopDp` in test code.)
2. **Non-zero vertical offset maps correctly.** Repeat assertion 1's setup but with a non-zero `overlayOffsetY` — reachable via the existing overlay-drag path (`viewModel.onOverlayDragged(dx, dy)`) or, if simpler, whatever public `CameraViewModel` entry point already sets `overlayOffsetY` — and confirm the resulting border position shifts by an amount consistent with `overlayOffsetY * uiState.viewportHeight`. A differential check (comparing the offset-applied case against the zero-offset case) is preferred over re-deriving the absolute formula, since a zero-offset-only check is exactly what let the original defect hide undetected.
3. **`ReferenceMarkerOverlay` marker projection uses the same viewport.** `viewModel.addMarker(nx, ny)` for a known `(nx, ny)`, and confirm the marker's rendered position is consistent with the same `uiState.viewportWidth/Height` (cross-checked against assertion 1/2's `MarkerEditBorder` result for the same reference image/offset, rather than an independently hardcoded expectation).
4. **All three remain mutually aligned.** A marker placed at an extreme normalized coordinate (e.g. `normalizedY = 0` or `1`) should land at (or very near) `MarkerEditBorder`'s own edge for the same geometry — proving `ReferenceMarkerOverlay` and `MarkerEditBorder` agree with each other, not just each independently with `uiState`.
5. **`ReferenceImageOverlay`'s live-offset denominator** (that its internal `pointerInput`'s `size.height` — CameraScreen.kt:1132-1141 — now matches `uiState.viewportHeight`) is the hardest to assert directly: `ReferenceImageOverlay`'s own `Box` carries no `testTag` today. Two options, left to implementation time, not pre-decided by this plan: (a) an indirect behavioral check — a known synthetic drag distance via `performTouchInput` should produce an `overlayOffsetY` change consistent with dividing by `uiState.viewportHeight` rather than an idealized value; or (b) add a minimal `testTag` to `ReferenceImageOverlay`'s outer `Box` in `CameraScreen.kt` (this would be a small, additional, otherwise-unplanned production change, and must not be added silently — if chosen, document it explicitly in this block's Progress Record as a deviation, and confirm it doesn't affect any existing test that queries that subtree). Given "do not add test-only public APIs unless unavoidable," prefer (a) unless it proves impractical.

**Cleanup:** mirror `CameraGuideTipIntegrationTest`'s `@After tearDown()` — close the `ActivityScenario`, delete any temp reference-image file, cancel any DataStore scope created for `SettingsRepository`.

### Explicitly untouched
- `CameraGuideTipIntegrationTest.kt` (read as a pattern reference only, not modified).
- `ReferenceMarkersOverlayUITest.kt`.
- All production files except via the (already-completed, precondition) Block 2 change — this block adds no production code.

### Risks
- **Camera hardware dependency on the managed device**: `CameraScreen()` binds a real `CameraX` `Preview`/`ImageCapture` session, which requires the API-29 Pixel 2 AOSP image to provide a working (possibly virtual/emulated) camera. `CameraGuideTipIntegrationTest` already proves this works in this environment — no new risk introduced, but this new test inherits the same dependency.
- **Async timing**: both `onReferenceImageSelected` (metadata load) and Box A's `onSizeChanged` (viewport publish) are asynchronous relative to `setContent`; missing either `waitUntil` will produce a flaky, not a deterministically-failing, test. Mitigate: both `waitUntil` calls are mandatory, as specified above.
- **Assertion 5's design is intentionally left open** — if implementation time shows the behavioral check (option a) is too imprecise to be a meaningful regression guard, the minimal-`testTag` option (b) is the documented fallback, not a silent scope expansion.

### Fail-fast verification

```
./gradlew compileDebugAndroidTestKotlin
./gradlew pixel2Api29DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.CameraScreenConstrainedViewportTest"
./gradlew connectedDebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.CameraScreenConstrainedViewportTest"
```

Managed-device evidence requirement (`Starting N tests on pixel2Api29` / `Finished N tests on pixel2Api29`, `--rerun` mandatory) applies as always.

**Sanity check on the test itself (recommended, not optional):** before trusting a green result, temporarily verify the new test actually fails against pre-Block-2 code (e.g., by checking it against a stash/worktree of the pre-fix `CameraScreen.kt`) — a new test that is green against both the buggy and fixed production code is not proof of anything. This check does not need to be repeated after Block 2 and Block 3 are both permanently landed; it is a one-time sanity step during initial implementation of this block.

### Completion criteria
- The new test class compiles and all its tests pass genuinely on both API 29 and S23.
- The test was confirmed (at least once, during initial implementation) to fail against pre-Block-2 `CameraScreen.kt` and pass against post-Block-2 `CameraScreen.kt`.
- `git status` shows exactly one new file relative to the end of Block 2.

### Rollback boundary
Delete `app/src/androidTest/java/com/isardomains/sameview/ui/camera/CameraScreenConstrainedViewportTest.kt` (new file — rollback is deletion, not `git checkout`).

### Progress record
- Date: 2026-07-14
- Implemented by: Claude (Sonnet 5), STEP 3 implementation session
- Files changed:
  - `app/src/androidTest/java/com/isardomains/sameview/ui/camera/CameraScreenConstrainedViewportTest.kt` — new file, 5 tests.
  - `CameraScreen.kt` was **not** modified — the assertion-4 behavioral drag proof succeeded using existing production-observable state (`marker_edit_border`'s existing tag for coordinate derivation, `viewModel.uiState.value.overlayOffsetY` for outcome observation); the documented test-observability fallback (new `testTag` on `ReferenceImageOverlay`) was never needed.
- Harness: `mountCameraScreenForViewportTest()` — reuses the exact pattern from `CameraGuideTipIntegrationTest.mountCameraScreenForGuideTipTest()` (not modified, read-only reference): `ActivityScenario<ComponentActivity>`, `createEmptyComposeRule()`, `GrantPermissionRule.grant(Manifest.permission.CAMERA)`, directly-constructed `CameraViewModel(context, SettingsRepository(settingsPrefs))` with an isolated per-test DataStore file, real `CameraScreen(viewModel = viewModel)`, `SameViewTheme`, the real `portrait_tall.jpg` asset copied to a per-test temp file. `waitUntil` used for `referenceImageMetadata != null` and for `viewportWidth > 0 && viewportHeight > 0` (no `Thread.sleep`). No `GuideTipController` instantiated; no guide-tip UI touched.
- Tests added (5):
  1. `markerEditBorder_usesConstrainedViewport` — `MarkerEditBorder`'s rendered width/height match `uiState.viewportWidth/Height` (COMPARE_WITH_PREVIEW fill case, unambiguous).
  2. `markerEditBorder_nonZeroVerticalOffset_usesConstrainedHeight` — differential vertical-shift check (SHOW_FULL_IMAGE, letterboxed so movement isn't clamped away) against `offsetDelta * uiState.viewportHeight`.
  3. `referenceMarkerOverlay_projectsMarkerAtBorderCenter` — a marker at normalized image-center is hit-tested (via the existing `marker_drag_loupe` tag appearing on a short drag) at a coordinate derived purely from `marker_edit_border`'s own rendered center, proving `ReferenceMarkerOverlay` and `MarkerEditBorder` share the same effective viewport, without recomputing `normalizedToScreen`/`computeVisibleImageRect`.
  4. `overlayDrag_convertsPixelDistanceUsingConstrainedHeight` — a real `performTouchInput` drag (slop-crossing move + a second, separately-dispatched move of exactly `dragDistancePx`) on the shared viewport region; resulting `uiState.value.overlayOffsetY` delta compared against `dragDistancePx / uiState.viewportHeight`.
  5. `markerEditBorder_rejectsIdealizedAspectRatioHeight` — asserts the border height matches the true `uiState.viewportHeight` and explicitly does **not** match `uiState.viewportWidth * 16/9`; guarded by `org.junit.Assume.assumeTrue` on the live-state precondition that the two genuinely differ on this device (see "Remaining issues" for why this is a skip, not a failure, on S23).
- Commands run:
  - `./gradlew compileDebugAndroidTestKotlin` — BUILD SUCCESSFUL (twice: once on first implementation, once after the two design fixes below).
  - `./gradlew pixel2Api29DebugAndroidTest --rerun "...CameraScreenConstrainedViewportTest"` — **first attempt** (before fixing assertion 4's gesture design): genuine (`Starting 5 tests on pixel2Api29` / `Finished 5 tests on pixel2Api29`), `tests="5" failures="1"` — `overlayDrag_convertsPixelDistanceUsingConstrainedHeight` failed with an exact `0.0` delta (test-gesture-design flaw, not production wiring — see Change Log entry). Fixed (added a slop-crossing move before the measured move) and re-verified in isolation: genuine, 1/1 PASS. **Full-class re-run**: genuine (`Starting 5 tests on pixel2Api29` / `Finished 5 tests on pixel2Api29`), `tests="5" failures="0" errors="0" skipped="0"`.
  - `./gradlew connectedDebugAndroidTest --rerun "...CameraScreenConstrainedViewportTest"` (S23) — genuine (`Starting 5 tests on SM-S911B - 16` / `Finished ... on SM-S911B - 16`), first attempt: `tests="5" failures="1" skipped="0"` — `markerEditBorder_rejectsIdealizedAspectRatioHeight` failed its own precondition check (idealized height == actual height, 1920.0 == 1920.0, on this device the viewport genuinely isn't height-constrained) — an assertion-design issue (hard `assertTrue` used for an environment-dependent precondition instead of `Assume`), not a production bug. Fixed by switching that one precondition check from `assertTrue` to `org.junit.Assume.assumeTrue`. Re-verified: genuine, `tests="5" failures="0" errors="0" skipped="1"` (the one precondition-guarded test correctly skipped on S23; the other 4 passed).
  - Pre-Block-2 sanity check: `CameraScreen.kt` backed up to session scratchpad, then `git stash push -- CameraScreen.kt` (verified `git diff --stat` empty afterward, i.e. exactly `HEAD`/pre-Block-2). Ran `pixel2Api29DebugAndroidTest --rerun "...CameraScreenConstrainedViewportTest"` twice against this baseline: both genuine (`Starting 5 tests on pixel2Api29` / `Finished 5 tests on pixel2Api29`), both `tests="5" failures="4" errors="0" skipped="0"` — `markerEditBorder_usesConstrainedViewport`, `markerEditBorder_nonZeroVerticalOffset_usesConstrainedHeight`, `overlayDrag_convertsPixelDistanceUsingConstrainedHeight`, and `markerEditBorder_rejectsIdealizedAspectRatioHeight` all failed against real, device-specific mismatched values (e.g. border height 1731.0 vs. expected constrained value 1678.0 in that pre-fix run); `referenceMarkerOverlay_projectsMarkerAtBorderCenter` passed (expected — both composables shared the identical wrong idealized viewport pre-fix, so mutual alignment still held). Restored via `git stash pop`; `diff` against the pre-stash backup confirmed byte-identical restoration; `compileDebugKotlin` re-confirmed BUILD SUCCESSFUL. `git stash list` empty. Both scratchpad backup files deleted after use — no backup files, patches, or worktrees left behind.
  - `./gradlew pixel2Api29DebugAndroidTest --rerun "...CameraScreenConstrainedViewportTest"` (final, post-restoration) — genuine (`Starting 5 tests on pixel2Api29` / `Finished 5 tests on pixel2Api29`), `tests="5" failures="0" errors="0" skipped="0"`.
- Test counts:
  - API 29 (final, post-restoration): tests="5" failures="0" errors="0" skipped="0"
  - S23 (final): tests="5" failures="0" errors="0" skipped="1" (one device-geometry-dependent precondition correctly skipped, not failed)
  - Pre-Block-2 baseline (API 29, ×2 genuine runs): tests="5" failures="4" errors="0" skipped="0" each time
- Result: All mandatory Block 3 verification passed genuinely on both devices after two legitimate in-scope test-design fixes (both to the new test file only, never to production code, never weakening any production-correctness assertion). The new test class was confirmed to fail 4/5 against pre-Block-2 `CameraScreen.kt` and pass 5/5 (4 pass + 1 correct skip on S23) against the current Block 2 code. `CameraScreen.kt` required no changes — the documented `ReferenceImageOverlay` test-tag fallback was not needed.
- Remaining issues: none for Block 3. Two implementation-time design corrections, both within Block 3's own approved scope (the new test file only), are recorded here rather than treated as deviations requiring a separate Amendment: (1) `overlayDrag_convertsPixelDistanceUsingConstrainedHeight`'s original single `down→moveTo→up` gesture left nothing for `ReferenceMarkerOverlay`'s pan-forwarding loop to observe, since its gesture classifier consumes the first, slop-crossing move purely for drag/tap/long-press classification — fixed by adding a slop-crossing move followed by a second, separately-dispatched move of the measured distance. (2) `markerEditBorder_rejectsIdealizedAspectRatioHeight`'s device-geometry precondition (idealized 16:9 height must meaningfully exceed the actual constrained height for this check to be meaningful) does not hold on the S23 (a genuinely ~16:9-ish device with no material system-bar-driven mismatch at this viewport) — fixed by using `org.junit.Assume.assumeTrue` for that specific precondition instead of a hard assertion, so the test is correctly reported as skipped (not failed, not falsely passed) on a device where the scenario it targets doesn't apply; the unconditional border-geometry assertions in `markerEditBorder_usesConstrainedViewport` still cover S23 fully.
- User approval for next block: Pending — do not start Block 4 without explicit user approval.

---

## Block 4 — Full Regression Verification & Plan Closure

### Status
Complete

### Goal
Holistic, cross-block regression sign-off confirming the whole camera-viewport fix is complete and has not disturbed anything outside its intended scope. This block makes **no code changes** — it is verification-only.

### Files
None (verification-only block; this plan file's Progress/Change Log sections are updated per "Progress Update Rules," which is not a scope change).

### Preconditions
- Blocks 1, 2, and 3 all marked Complete with passing Progress Records.

### Exact implementation instructions
Run the full verification matrix below in order, stopping at the first failure. Do not proceed to a broader command if a narrower one in the same device/class scope has not already passed. No source change is made in this block; if a failure occurs, return to the relevant earlier block (do not patch ad hoc inside Block 4).

### Explicitly untouched
Every file in the repository — this block is read/execute-only.

### Risks
- Broader regression surfaces (full `connectedDebugAndroidTest`, `assembleRelease`) are expensive and, per prior investigation history, may surface *pre-existing, unrelated* flakiness (e.g. the documented Samsung IS_PENDING/media-scanner flakiness in `MediaStoreWriterGpsTest`, unrelated to this plan). Such pre-existing flakiness is not a signal of a regression from this plan's blocks — do not attempt to fix it here; note it and move on.

### Fail-fast verification
See the full Verification Matrix below. This block executes every row marked "Mandatory."

### Completion criteria
- Every "Mandatory" row in the Verification Matrix has a genuine, evidenced pass.
- No test that was green at the end of Block 3 is now red.
- No known-red test remains red anywhere in this plan's scope.

### Rollback boundary
Not applicable (no changes made in this block).

### Progress record
- Date: 2026-07-14
- Implemented by: Claude (Sonnet 5), STEP 3 implementation session
- Files changed: None. Verification-only block — confirmed via `git status --porcelain`/`git diff --stat` before and after this block, both identical to the end-of-Block-3 state.
- Commands run, in exact mandatory order (every instrumentation command used `--rerun`; every managed-device run showed genuine `Starting`/`Finished` evidence):
  1. `./gradlew compileDebugKotlin` — BUILD SUCCESSFUL
  2. `./gradlew compileDebugAndroidTestKotlin` — BUILD SUCCESSFUL
  3. `./gradlew pixel2Api29DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceMarkersOverlayUITest"` — genuine (`Starting 21 tests on pixel2Api29` / `Finished 21 tests on pixel2Api29`), `tests="21" failures="0" errors="0" skipped="0"`
  4. `./gradlew pixel2Api29DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.CameraScreenConstrainedViewportTest"` — genuine (`Starting 5 tests on pixel2Api29` / `Finished 5 tests on pixel2Api29`), `tests="5" failures="0" errors="0" skipped="0"`
  5. `./gradlew connectedDebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceMarkersOverlayUITest"` (S23) — genuine (`Starting 21 tests on SM-S911B - 16` / `Finished 21 tests on SM-S911B - 16`), `tests="21" failures="0" errors="0" skipped="0"`
  6. `./gradlew connectedDebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.CameraScreenConstrainedViewportTest"` (S23) — genuine (`Starting 5 tests on SM-S911B - 16` / `Finished 5 tests on SM-S911B - 16`), `tests="5" failures="0" errors="0" skipped="1"` — skip is `markerEditBorder_rejectsIdealizedAspectRatioHeight`, the same test and same device-geometry precondition reason established in Block 3 (S23's viewport isn't height-constrained; idealized 16:9 height coincides with actual height on this device)
  7. `./gradlew assembleDebug` — BUILD SUCCESSFUL
  8. `./gradlew testDebugUnitTest` — BUILD SUCCESSFUL, 825 tests, 0 failures, 0 skipped (aggregated from `app/build/test-results/testDebugUnitTest/*.xml`)
  - Post-verification repository review: `git status --porcelain`/`git diff --stat` re-run, identical to pre-Block-4 state; `git stash list` empty. Full diffs reviewed for `ReferenceMarkersOverlayUITest.kt` (confirmed confined to imports + 4 new constants + `setBorderContent`'s signature/body, no test body/assertion/tolerance/name/count changed), `CameraScreen.kt` (confirmed confined to the one shared `hasConstrainedViewport`/`constrainedOverlayModifier` declaration + the three call-site guards/modifiers, no internal overlay/marker/border logic or unrelated formatting changed), and `CameraScreenConstrainedViewportTest.kt` (confirmed exactly 5 `@Test` methods + local harness helpers only, zero `GuideTipController` import/usage, no unrelated test coverage).
- Test counts (see above, per command).
- Result: All 8 mandatory commands passed genuinely, in order, with no failures at any step. All instrumentation runs were genuine (no cached/UP-TO-DATE results accepted). The API-29 production-wiring test passed 5/5. No unexpected S23 skip occurred (the one skip exactly matches the single justified geometry-precondition skip already established in Block 3). No source file changed during Block 4. The final diff matches the approved scope exactly. No unresolved viewport issue remains within this plan's scope.
- Remaining issues: none within this plan's scope. The API-29 REFERENCE Guide-Tip Completion Race remains open as a separate, out-of-scope issue (see Amendment 2026-07-14 (Part 2)) — not touched, not re-run, not analyzed further in this block.
- User approval for next block: N/A — this is the final block. Plan is closed pending user review of this final report.

---

# Verification Matrix

| Command | Device | Block | Purpose | Mandatory/Optional | Expected result | Genuine execution evidence required |
|---|---|---|---|---|---|---|
| `./gradlew compileDebugAndroidTestKotlin` | n/a (compile only) | 1 | Catch syntax/type errors in harness change | Mandatory | BUILD SUCCESSFUL | Build log |
| `pixel2Api29DebugAndroidTest --rerun` — `border_framesVisibleImageRect_whenLetterboxed` | API 29 managed | 1 | Confirm previously-failing test now green | Mandatory | 1/1 pass | `Starting 1 tests on pixel2Api29` / `Finished 1 tests on pixel2Api29` |
| `pixel2Api29DebugAndroidTest --rerun` — `border_topAndBottom_correctForLandscapeImageWithNonZeroOffsetY` | API 29 managed | 1 | Confirm previously-failing test now green | Mandatory | 1/1 pass | `Starting`/`Finished` lines |
| `pixel2Api29DebugAndroidTest --rerun` — `border_matchesViewport_whenImageFillsViewport` | API 29 managed | 1 | Confirm previously-passing test stays green (not passing "by accident" anymore) | Mandatory | 1/1 pass | `Starting`/`Finished` lines |
| `pixel2Api29DebugAndroidTest --rerun` — full `ReferenceMarkersOverlayUITest` | API 29 managed | 1 | Full-class regression | Mandatory | 21/21 pass | `Starting 21 tests`/`Finished 21 tests` |
| `connectedDebugAndroidTest --rerun` — full `ReferenceMarkersOverlayUITest` | S23 | 1 | Cross-device confirmation | Mandatory | 21/21 pass | Genuine run log (S23 has no cache/UP-TO-DATE ambiguity risk but `--rerun` still required for certainty) |
| `compileDebugKotlin` | n/a (compile only) | 2 | Catch production compile errors | Mandatory | BUILD SUCCESSFUL | Build log |
| `assembleDebug` | n/a (build only) | 2 | Full debug build sanity | Mandatory | BUILD SUCCESSFUL | Build log |
| Diff review of `CameraScreen.kt` | n/a (static review) | 2 | Confirm change confined to one shared derived value + three call-site guards/modifiers; `aspectRatio` sizing absent from exactly those three sites | Mandatory | Confirmed | Diff inspection (see Progress record) |
| `connectedDebugAndroidTest --rerun` — `ReferenceMarkersOverlayUITest` | S23 | 2 | Confirm Block 2 didn't change Block 1's outcome | Mandatory | 21/21 pass (unchanged from Block 1) | Genuine run log |
| `connectedDebugAndroidTest --rerun` — `CameraGuideTipIntegrationTest` | S23 | 2 | Informative only (not mandatory) — real-CameraScreen-mounting test, confirms end-to-end behavior on a device where the unrelated guide-tip race does not manifest | Optional / informative | all pass | Genuine run log |
| ~~`pixel2Api29DebugAndroidTest --rerun` — `CameraGuideTipIntegrationTest`~~ | API 29 managed | 2 | **Removed as an invalid gate 2026-07-14 (Part 2)** — proven, via genuine baseline A/B comparison, to fail identically with or without Block 2's change present (pre-Block-2: 2/2 failures; post-Block-2: 3/3 failures), due to a pre-existing, causally unrelated race in `CameraScreen.kt`'s guide-tip orchestration. See Amendment. | **Not a gate** | — | Historical result retained in Progress record for reference only |
| `compileDebugAndroidTestKotlin` | n/a | 3 | Catch new-file compile errors | Mandatory | BUILD SUCCESSFUL | Build log |
| `pixel2Api29DebugAndroidTest --rerun` — `CameraScreenConstrainedViewportTest` | API 29 managed | 3 | Authoritative production-wiring proof | Mandatory | all pass | `Starting`/`Finished` lines |
| `connectedDebugAndroidTest --rerun` — `CameraScreenConstrainedViewportTest` | S23 | 3 | Cross-device authoritative proof | Mandatory | all pass | Genuine run log |
| `testDebugUnitTest` | n/a (JVM) | 4 | Broad unrelated-unit-test regression | Mandatory | BUILD SUCCESSFUL | Build log |
| `assembleDebug` | n/a | 4 | Final debug build sanity | Mandatory | BUILD SUCCESSFUL | Build log |
| `connectedDebugAndroidTest` (full, unfiltered) | S23 | 4 | Broad connected regression | **Not authorized without separate approval** | — | — |
| `pixel2Api29DebugAndroidTest` (full, unfiltered) | API 29 managed | 4 | Broad managed-device regression | **Not authorized without separate approval** | — | — |
| `assembleRelease` | n/a | 4 | Release build sanity | **Not authorized without separate approval** | — | — |
| `bundleRelease` | n/a | 4 | Release bundle | **Not authorized without separate approval** | — | — |

Every managed-device (`pixel2Api29DebugAndroidTest`) invocation in this matrix must be run with `--rerun` and must show the literal `Starting N tests on pixel2Api29` / `Finished N tests on pixel2Api29` lines in its output. A result without these lines is a cached/UP-TO-DATE artifact, not evidence, and must be discarded and re-run.

---

# Resume Instructions

A future Claude session picking up this plan (with no prior chat history) must:

1. Read this entire file.
2. Read the current state of: `CameraScreen.kt` (specifically lines 663-690, 780-849, 1111-1156, 2595-2653), `ReferenceMarkersOverlayUITest.kt` (specifically `setBorderContent` and the five `border_*` tests), and `CameraGuideTipIntegrationTest.kt` (specifically `mountCameraScreenForGuideTipTest`), plus `CameraScreenConstrainedViewportTest.kt` if it already exists.
3. Run `git status --porcelain` and `git diff --stat`, and compare against the "Current Repository State" section above plus the "Progress record" of every block marked Complete. If they disagree, stop and investigate before touching anything.
4. Read the "Current Progress" section below.
5. Identify the first block, in order (1 → 2 → 3 → 4), whose Status is not "Complete."
6. Implement only that block's "Exact implementation instructions" — after explicit user approval to proceed with that specific block.
7. Run only that block's "Fail-fast verification" commands, in order, stopping at the first failure.
8. If any command fails, stop immediately — do not attempt to fix forward into a different block's scope, and do not weaken the fix to force a pass.
9. Update only that block's Status and Progress record, plus "Current Progress" and "Change Log" (see "Progress Update Rules" below) — do not rewrite any other section.
10. Stop and wait for explicit user approval before starting the next block.

---

# Current Progress

- [x] Block 1 — Test Harness Correction (`setBorderContent`) — Status: Complete (2026-07-13)
- [x] Block 2 — Production Constrained-Viewport Wiring Fix — Status: Complete (2026-07-14, Part 2) — invalid API-29 `CameraGuideTipIntegrationTest` gate corrected/removed (proven pre-existing, unrelated race); all corrected mandatory verification passed genuinely; see Amendment
- [x] Block 3 — CameraScreen Production-Wiring Integration Test — Status: Complete (2026-07-14) — 5 new tests, 5/5 pass on API 29, 4/5 pass + 1 correct skip on S23, confirmed 4/5 fail against pre-Block-2 baseline; CameraScreen.kt not modified
- [x] Block 4 — Full Regression Verification & Plan Closure — Status: Complete (2026-07-14) — all 8 mandatory commands passed genuinely on both devices; no source file changed; final diff matches approved scope; plan closed

For each block, when work begins, fill in (in that block's own Progress record, not here):
status / date / changed files / commands / results / unresolved issues / next approval.

---

# Progress Update Rules

After completing (or attempting) any block, update **only**:

- That block's `## Status` line.
- That block's `## Progress record` fields.
- The `# Current Progress` checklist entry for that block.
- The `# Change Log` (append-only — add a new entry, never edit a prior one).

Do **not** rewrite any other section — not the Problem Statement, Confirmed Root Cause, Proven Diagnostics, Settled Findings, Approved Scope, Non-Goals, Implementation Strategy, or any other block's instructions, even if implementation reveals a nuance. If a nuance or correction is genuinely needed, add it via an **Amendment** subsection immediately below "# Change Log" rather than editing the original text in place.

**If new evidence invalidates any part of this plan** (e.g., a block's instructions turn out to be technically wrong once attempted, or a file outside the approved scope turns out to be genuinely required):

1. Add an `## Amendment` subsection under "# Change Log" describing the new evidence.
2. Cite exactly what was found (file, line, command output) — not a vague restatement.
3. Identify which block(s) are affected.
4. Stop implementation of the affected block.
5. Request fresh scope/approach approval from the user before continuing — do not silently improvise a fix.

---

# Change Log

### 2026-07-13 — Plan created
- Plan created from direct source inspection (not solely from chat summary): `CLAUDE_PROJECT_INSTRUCTION.md`, `ALIGNMENT_POINTS_V1.md`, `CAMERA_WORKFLOW_UX_V1.md`, `IMPLEMENTATION_NOTES.md`, `REFERENCE_MARKER_DRAG_LOUPE_V1_IMPLEMENTATION_PLAN.md`, `CameraScreen.kt`, `ReferenceMarkerOverlay.kt`, `CameraViewModel.kt`, `ReferenceRenderer.kt`, `ReferenceMarkersOverlayUITest.kt`, `CameraGuideTipIntegrationTest.kt`, current `git status`/`git diff`, and existing `docs/implementation_plans/` conventions.
- Block structure (4 blocks) determined by source analysis: harness correction, production wiring fix, new authoritative integration test, full regression closure — chosen as the smallest set of narrowly-scoped, independently reversible, independently resumable units; no block was split or merged merely to hit a target count.
- No implementation performed. No tests executed. No production or test files modified.

### 2026-07-13 — Block 1 implemented and verified
- Repository state re-confirmed against "Current Repository State" before any edit: `git status --porcelain` matched exactly (four pre-existing approved changes, untracked `-Pandroid`, plus this plan file itself) — no discrepancy.
- `setBorderContent` in `ReferenceMarkersOverlayUITest.kt` changed per Block 1's instructions: the independent `aspectRatio(9f/16f)`/`16f/9f` wrapper replaced with an explicit `Modifier.size(width = viewportWidthDp, height = viewportHeightDp)`, backed by four new fixed, device-independent private constants (`BORDER_TEST_VIEWPORT_WIDTH_PORTRAIT_DP = 540.dp`, `BORDER_TEST_VIEWPORT_HEIGHT_PORTRAIT_DP = 800.dp`, `BORDER_TEST_VIEWPORT_WIDTH_LANDSCAPE_DP = 760.dp`, `BORDER_TEST_VIEWPORT_HEIGHT_LANDSCAPE_DP = 420.dp`) and two new orientation-aware defaulted parameters. `aspectRatio`/`fillMaxWidth`/`fillMaxHeight` imports removed (confirmed unused elsewhere in the file via grep before removal); `androidx.compose.foundation.layout.size`, `androidx.compose.ui.unit.Dp`, `androidx.compose.ui.unit.dp` added.
- All Block 1 mandatory verification passed genuinely (`--rerun`, `Starting`/`Finished` lines confirmed) on both API 29 (pixel2Api29 managed device) and S23 (SM-S911B, Android 16): the two previously-failing tests now pass, the previously-passing test remains green, and the full 21-test class passes 21/21 on both devices. Full detail in Block 1's Progress record.
- Block 1 Status set to Complete. Blocks 2, 3, and 4 not started — no production code, no other test file, touched.

### 2026-07-13 — Block 2 attempted; verification failed; not marked Complete
- Repository state re-confirmed before any edit: `git status --porcelain` and `git diff --stat` matched the plan plus completed Block 1 exactly — no discrepancy.
- `CameraScreen.kt` changed per Block 2's instructions exactly: one `hasConstrainedViewport`/`constrainedOverlayModifier` derived-value declaration added immediately before the three call sites; `ReferenceImageOverlay`'s and `ReferenceMarkerOverlay`'s guards extended with `&& hasConstrainedViewport`; `MarkerEditBorder`'s call site newly wrapped in `if (hasConstrainedViewport) { ... }`; all three `modifier = if (!isLandscape) { Modifier.fillMaxWidth().aspectRatio(9f/16f)... } else { Modifier.fillMaxHeight().aspectRatio(16f/9f)... }` expressions replaced with `modifier = constrainedOverlayModifier!!`. No other line changed; no import added (`androidx.compose.foundation.layout.size` was already imported); `aspectRatio`/`fillMaxWidth`/`fillMaxHeight` imports left in place (confirmed still used 15 times elsewhere in the file).
- `compileDebugKotlin` and `assembleDebug` passed. `CameraGuideTipIntegrationTest` and `ReferenceMarkersOverlayUITest` both passed genuinely on S23 (8/8 and 21/21 respectively).
- The mandatory `pixel2Api29DebugAndroidTest --rerun` run of `CameraGuideTipIntegrationTest` failed genuinely on retry (first attempt was a discarded APK-install infrastructure failure, `tests="0"`, no `Starting`/`Finished` lines): `tests="8" failures="1"`, `referenceTipVisible_referenceImageSelected_completesTipAndDoesNotReappear` failed at `assertTrue(GuideTipId.REFERENCE in seenTipIds)` (`CameraGuideTipIntegrationTest.kt:267`).
- Per instruction, did not attempt to fix this by modifying `CameraScreen.kt` further, `CameraGuideTipIntegrationTest.kt`, or `CameraViewModel.kt`. Added the Amendment below instead and stopped without marking Block 2 Complete and without starting Block 3 or Block 4.

## Amendment — 2026-07-13 (Block 2 verification failure — not caused by Block 2's diff)

**New evidence:** genuine API-29 managed-device execution (`Starting 8 tests on pixel2Api29` / `Finished 8 tests on pixel2Api29`, second attempt after a discarded APK-install infra failure) of `CameraGuideTipIntegrationTest` produced `tests="8" failures="1" errors="0" skipped="0"`, with `referenceTipVisible_referenceImageSelected_completesTipAndDoesNotReappear` failing at `assertTrue(GuideTipId.REFERENCE in seenTipIds)` (`CameraGuideTipIntegrationTest.kt:267`). The same test, same code, passed 8/8 genuinely on S23 (SM-S911B).

**Root-cause tracing (read-only investigation, no files modified beyond Block 2's approved diff):**
- `GuideTipController.clearActiveTipWithoutMarkingSeen()` (`GuideTipController.kt:70-74`) is fully synchronous: `_activeTipId.value = null`, no repository write.
- `GuideTipController.completeTip()` (`GuideTipController.kt:80-86`) is a `suspend fun`: it calls `repository.markTipSeen(tipId)` (async DataStore I/O) before clearing `_activeTipId`.
- In `CameraScreen.kt`, two independent, pre-existing effects both react to `referenceUri` transitioning to non-null:
  - `LaunchedEffect(referenceUri)` (lines 453-461, **not part of Block 2's diff**): launches `guideTipScope.launch { guideTipController?.completeTip(GuideTipId.REFERENCE) }` — an extra coroutine dispatch plus an async DataStore write.
  - `LaunchedEffect(guideTipController, cameraEligibleTipIds, cameraTipBlocked, activeGuideTip?.id)` (lines 479-495, **not part of Block 2's diff**): `cameraEligibleTipIds` requires `referenceUri == null` (line 471), so the moment `referenceUri` becomes non-null this effect's key changes and it synchronously calls `controller.clearActiveTipWithoutMarkingSeen(currentTip.id)`.
- These two effects are in a structural race: whichever executes first determines whether `GuideTipId.REFERENCE` is actually marked seen in the repository before the active tip is cleared. This race is entirely contained within code Block 2 never touched — `hasConstrainedViewport`, `constrainedOverlayModifier`, and the three overlay call sites have no bearing on `referenceUri`, `cameraEligibleTipIds`, `cameraTipBlocked`, `activeGuideTip`, `completeTip`, or `clearActiveTipWithoutMarkingSeen`.
- Consistent with this investigation's own established premise (documented throughout this plan and its predecessor investigation) that API-29 and S23 exhibit different timing characteristics, this pre-existing race manifested on API-29 in this run and did not manifest on S23.

**Affected block:** Block 2 (verification only — the production code change itself is not implicated by this evidence). Blocks 1, 3, and 4 are unaffected; Block 1 remains Complete and green.

**Not done, per instruction:** no attempt was made to fix the race (would require modifying `CameraScreen.kt`'s guide-tip effect ordering/synchronization — a production composable-body change beyond Block 2's approved scope — or modifying `CameraGuideTipIntegrationTest.kt`'s timing assumptions, explicitly forbidden). The Block 2 `CameraScreen.kt` change remains uncommitted in the working tree, not reverted, pending user decision.

**Request:** user approval needed on how to proceed — options include (a) treat this as pre-existing, out-of-scope flakiness unrelated to this plan and re-run to confirm, (b) scope a separate fix for the guide-tip race (outside this plan's approved file boundary as currently written), or (c) something else the user specifies. Implementation is stopped pending this decision; do not resume Block 2 verification or start Block 3/4 without fresh direction.

## Amendment — 2026-07-14 (Block 2 verification failure — proven pre-existing via baseline comparison; race confirmed, not merely theorized)

**Analysis-only session.** No production code, test code, or plan sections other than this Amendment and the entries below were modified. `CameraScreen.kt` was temporarily reverted to the pre-Block-2 baseline via `git stash push -- CameraScreen.kt` (after backing up the Block 2 version to the session scratchpad), tested, then restored via `git stash pop`; restore was verified byte-identical to the pre-stash backup via `diff`, and `compileDebugKotlin` re-confirmed BUILD SUCCESSFUL after restore. `git status --porcelain` before and after this session matched exactly (no file outside this analysis touched).

**1. Exact failure evidence:** the failing assertion is `assertTrue(GuideTipId.REFERENCE in seenTipIds)` at `CameraGuideTipIntegrationTest.kt:267`. The prior `guide_tip_card` assertions (lines 255, 264) and the `waitUntil(timeoutMillis=10_000){ activeTipId.value == null }` (lines 260-262) all pass — the wait resolves quickly (test total time 4-13s across runs, never near the 10s timeout). The failure occurs strictly *after* tip completion/clearing has already resolved, not during reference loading. No viewport-dependent composable (`ReferenceImageOverlay`/`ReferenceMarkerOverlay`/`MarkerEditBorder`) is read anywhere in the failing assertion's data path — it reads only `harness.repository.observeSeenTipIds()`.

**2. Logical proof of mechanism (not inference from the test name):** `GuideTipController.completeTip()` (`GuideTipController.kt:80-86`) writes `repository.markTipSeen(tipId)` **before** nulling `_activeTipId`. Since every failing run observed `activeTipId == null` **and** `REFERENCE ∉ seenTipIds` simultaneously, `completeTip()` cannot be what nulled the tip in these runs — only `clearActiveTipWithoutMarkingSeen()` (`GuideTipController.kt:70-74`, which never touches the repository) could have. This is a logical necessity from the code's own sequencing, not a guess.

**3. Reproduction (post-Block-2, current code):** 3/3 genuine, isolated single-method `--rerun` executions of `CameraGuideTipIntegrationTest#referenceTipVisible_referenceImageSelected_completesTipAndDoesNotReappear` on pixel2Api29 — all 3 failed identically at the same assertion (`Starting 1 tests on pixel2Api29` / `Finished 1 tests on pixel2Api29` confirmed genuine on each). Stopped at 3 (of the allowed 5) per "stop early if deterministic" — 100% failure rate established.

**4. Baseline comparison (pre-Block-2 code, proven via git stash, not merely argued):** `CameraScreen.kt` was reverted to exactly `HEAD` (confirmed via `git diff --stat` showing zero output) and the same isolated method was run genuinely 2/2 times — **both failed identically** (`Starting 1 tests on pixel2Api29` / `Finished 1 tests on pixel2Api29`, `tests="1" failures="1"`, same `AssertionError`). Failure rate on the pre-Block-2 baseline (2/2 = 100%) matches the post-Block-2 rate (3/3 = 100%) exactly. `CameraScreen.kt` was then restored via `git stash pop` and verified byte-identical to the Block 2 version via `diff` before continuing.

**5. Race hypothesis status:** upgraded from "strongly evidenced" to **directly observed and proven** — the race is logically demonstrated by section 2 above, and its independence from Block 2 is now proven empirically (not merely argued from diff inspection) by section 4's baseline reproduction. The race lives entirely in `LaunchedEffect(referenceUri)` (`CameraScreen.kt:453-461`) racing against `LaunchedEffect(guideTipController, cameraEligibleTipIds, cameraTipBlocked, activeGuideTip?.id)` (`CameraScreen.kt:479-495`) — both present, unchanged, and racing identically in both the pre- and post-Block-2 versions of the file.

**Decision:** **B — Block 2 is correct; the failure is a proven pre-existing, unrelated race, requiring a separate task.** Rejected: A (revert — no evidence Block 2 causes or worsens this), C (test defect — the test's expectation is a reasonable assertion on production orchestration logic; the defect is in that logic's race, not the test), D (insufficient evidence — the baseline comparison directly resolved the open question).

**Plan consequence:**
- Block 2 **remains Blocked** (not marked Complete) — its literal completion criteria ("`CameraGuideTipIntegrationTest` passes in full, genuinely, on both API 29 and S23") is not met as written, even though the cause is proven unrelated to Block 2's own diff.
- Block 2's `CameraScreen.kt` change is **not reverted** — retained in the working tree, confirmed intact and byte-identical post-analysis.
- A **separate new issue/task must be opened** for the `CameraScreen.kt` guide-tip orchestration race (`LaunchedEffect(referenceUri)` vs. the `cameraEligibleTipIds`-keyed effect) — this is outside the approved file/scope boundary of this plan as currently written and requires its own scoping and approval before any fix is attempted.
- **Block 3 and Block 4 remain prohibited** until Block 2's status is resolved (either by amending its completion criteria to explicitly exclude this known, proven-unrelated race, or by fixing the race under a separate approved task and re-verifying).
- Awaiting user decision on how Block 2's Blocked status should ultimately be resolved.

## Amendment — 2026-07-14 (Part 2): Block 2 gate corrected; Block 2 marked Complete

**Scope of this amendment:** planning/scope-correction only. No production code, test code, or the Block 2 `CameraScreen.kt` implementation itself was modified in this session — only this plan file (Block 2's "Fail-fast verification," "Completion criteria," "Status," Progress record addendum, Verification Matrix rows, Current Progress, and this Amendment).

**Basis:** the 2026-07-14 Amendment immediately above already proved, via genuine API-29 baseline A/B comparison, that `CameraGuideTipIntegrationTest#referenceTipVisible_referenceImageSelected_completesTipAndDoesNotReappear`'s failure reproduces identically (100% rate) on both the current Block 2 `CameraScreen.kt` (3/3) and the exact pre-Block-2 baseline (2/2), and that the responsible code (`LaunchedEffect(referenceUri)` and the `cameraEligibleTipIds`-keyed effect, plus `GuideTipController.completeTip`/`clearActiveTipWithoutMarkingSeen`) does not appear anywhere in Block 2's diff. This amendment acts on that proof to correct Block 2's own verification gate rather than leaving Block 2 indefinitely Blocked by a gate now known to be invalid for this specific fix.

**1. Invalid gate removed.** `pixel2Api29DebugAndroidTest --rerun "...CameraGuideTipIntegrationTest"` is removed from Block 2's mandatory "Fail-fast verification" and "Completion criteria." It was never a valid regression gate for the constrained-viewport change: the assertion it fails on reads only `GuideTipController`/repository state, with no dependency on `ReferenceImageOverlay`, `ReferenceMarkerOverlay`, `MarkerEditBorder`, `hasConstrainedViewport`, or `constrainedOverlayModifier`. No historical evidence was deleted — the original Block 2 Progress record entries (including the exact failing run's evidence) remain verbatim above; a dated "Update — 2026-07-14 (Part 2)" note was appended beneath them.

**2. Corrected Block 2 verification gate (all already satisfied by genuine, already-collected evidence — no new test added):**
- `compileDebugKotlin` — PASSED (genuine, `BUILD SUCCESSFUL`).
- `assembleDebug` — PASSED (genuine, `BUILD SUCCESSFUL`).
- Diff review of `CameraScreen.kt` — PASSED: confirmed exactly one shared `hasConstrainedViewport`/`constrainedOverlayModifier` declaration; all three call sites (`ReferenceImageOverlay`, `ReferenceMarkerOverlay`, `MarkerEditBorder`) consume it identically via `modifier = constrainedOverlayModifier!!`; `aspectRatio(9f/16f)`/`16f/9f` sizing is absent from exactly those three call sites (confirmed via grep: 15 remaining usages of `aspectRatio`/`fillMaxWidth`/`fillMaxHeight` elsewhere in the file, none at the three call sites).
- `ReferenceMarkersOverlayUITest` full class on S23 — PASSED, 21/21 genuine (the existing test suite most directly coupled, by contract, to the geometry Block 2 changes — its API-29 coverage was already established in Block 1 and is not re-required here, since this test's `setBorderContent` harness constructs its own independent viewport with zero reference to `CameraScreen.kt` and is structurally incapable of observing any change to that file — proven repeatedly earlier in this investigation).
- `CameraGuideTipIntegrationTest` on S23 — PASSED, 8/8 genuine, retained as **informative, non-mandatory** evidence only: it confirms real `CameraScreen()` composition with a loaded reference image works end-to-end on a device where the unrelated guide-tip race does not manifest. It is not required for Block 2's completion, and no filtered/partial re-run of it was invented for this purpose (no new test was added, per instruction).

No additional command was found to be necessary: every piece of verification causally connected to Block 2's actual diff (compile, build, exact-diff-scope, and the one test suite whose assertions are genuinely coupled to the changed geometry) already has genuine, passing evidence on record from the original Block 2 attempt. Re-running any of it would not add new information.

**3. Decision: Block 2 status.** **A — Block 2 can now be marked Complete.** All corrected mandatory criteria are satisfied by already-collected genuine evidence; no additional diagnostic command is required (Decision B is not chosen — there is nothing left that would add information); Block 2 does not need to remain Blocked for any other viewport-related reason (Decision C is not chosen — no other viewport-related gate is unmet). Block 2's `## Status` is updated to **Complete** above.

**4. Separate Issue Record**

**Title:** API-29 REFERENCE Guide-Tip Completion Race

- **Exact failing test:** `CameraGuideTipIntegrationTest#referenceTipVisible_referenceImageSelected_completesTipAndDoesNotReappear` (`app/src/androidTest/java/com/isardomains/sameview/ui/camera/CameraGuideTipIntegrationTest.kt:267`), assertion `assertTrue(GuideTipId.REFERENCE in seenTipIds)`.
- **Baseline reproduction counts:** post-Block-2 `CameraScreen.kt`: 3/3 genuine, isolated single-method API-29 runs failed identically. Pre-Block-2 `CameraScreen.kt` (exact `HEAD` baseline, verified via `git diff --stat` showing zero output before testing and `diff` against a backup showing byte-identical restore afterward): 2/2 genuine runs failed identically. Same test passes 8/8 genuinely on S23 (SM-S911B, Android 16) with both code versions.
- **Suspected effect race:** `LaunchedEffect(referenceUri)` (`CameraScreen.kt:453-461`) asynchronously calls `GuideTipController.completeTip(GuideTipId.REFERENCE)` (a `suspend fun` that writes `repository.markTipSeen` **before** nulling `_activeTipId`) via a separately-launched `guideTipScope.launch{}` coroutine, racing against `LaunchedEffect(guideTipController, cameraEligibleTipIds, cameraTipBlocked, activeGuideTip?.id)` (`CameraScreen.kt:479-495`), which synchronously calls `GuideTipController.clearActiveTipWithoutMarkingSeen()` (no repository write) the instant `cameraEligibleTipIds` becomes empty (which happens on the same `referenceUri`-becomes-non-null transition, per the `referenceUri == null` condition at line 471). Proven directly (not inferred from the test name): `completeTip`'s own sequential ordering means it cannot be what nulled `_activeTipId` in the observed failing state (`activeTipId == null` and `REFERENCE ∉ seenTipIds` simultaneously) — only `clearActiveTipWithoutMarkingSeen` could have.
- **Confirmation that it is outside this viewport plan:** none of the responsible code (`LaunchedEffect(referenceUri)`, `cameraEligibleTipIds`, `cameraTipBlocked`, `activeGuideTip`, `GuideTipController.completeTip`, `GuideTipController.clearActiveTipWithoutMarkingSeen`) appears anywhere in Block 2's diff (`hasConstrainedViewport`, `constrainedOverlayModifier`, and the three call-site guards/modifiers). Confirmed empirically via genuine baseline A/B comparison, not merely by diff inspection.
- **Confirmation that it requires its own analysis → scope → implementation cycle:** yes. This is a production correctness issue in `CameraScreen.kt`'s guide-tip orchestration (not a test defect), living in code outside this plan's "Approved Scope" (§Approved Scope lists only `CameraScreen.kt`'s constrained-viewport call sites as in-scope for Block 2, not the guide-tip effects). Fixing it requires its own root-cause-confirmed design (e.g., how `completeTip` and `clearActiveTipWithoutMarkingSeen` should be ordered or made atomic with respect to the same eligibility transition), its own approved file/scope boundary, and its own implementation + verification cycle. It is **not added to any current block** in this plan.

**5. Explicit clarifications:**
- `CameraScreen.kt` (Block 2's actual production code) was **not modified** in this session.
- No test file was modified in this session.
- The guide-tip race was **not fixed** in this session — only documented as a separate issue.
- Block 3 was **not started** in this session. Marking Block 2 Complete removes Block 3's stated precondition ("Block 2 must be complete"), but per the plan's own Resume Instructions, starting Block 3 still requires explicit user approval before implementation begins — this amendment does not grant that approval on its own.

### 2026-07-14 — Block 3 implemented and verified; CameraScreen.kt not touched

- Repository state re-confirmed before any edit: `git status --porcelain` and `git diff --stat` matched the plan plus completed Blocks 1 and 2 exactly — no discrepancy. Confirmed Block 1 Complete, Block 2 Complete, the Guide-Tip race documented as a separate out-of-scope issue (not touched), and no unexpected repository changes.
- New file `app/src/androidTest/java/com/isardomains/sameview/ui/camera/CameraScreenConstrainedViewportTest.kt` added, reusing `CameraGuideTipIntegrationTest`'s harness pattern (read-only reference, not modified) with a directly-constructed `CameraViewModel`, isolated per-test `SettingsRepository` DataStore, real `CameraScreen(viewModel = viewModel)`, and the real `portrait_tall.jpg` asset. 5 tests cover: `MarkerEditBorder` matching `uiState.viewportWidth/Height`; a non-zero vertical offset shifting the border by `offsetDelta * uiState.viewportHeight`; `ReferenceMarkerOverlay` and `MarkerEditBorder` mutual alignment via the existing `marker_drag_loupe` tag; a real `performTouchInput` drag converting pixel distance via `uiState.viewportHeight`; and explicit rejection of the idealized `viewportWidth * 16/9` height. No test hardcodes 1080×1731/1920, re-derives `computeVisibleImageRect`/`normalizedToScreen`, or uses `ReferenceMarkersOverlayUITest`'s isolated harness.
- `CameraScreen.kt` required **no change** — the plan's documented `ReferenceImageOverlay` test-tag fallback for assertion 4 was not needed; the real drag path was proven observable using only `viewModel.uiState.value.overlayOffsetY` and the already-existing `marker_edit_border` tag for coordinate derivation.
- Two implementation-time test-design issues were found and fixed, both confined to the new test file (no production file touched, no other test modified, no assertion weakened to force a pass): (1) the drag test's original single-jump gesture left nothing for `ReferenceMarkerOverlay`'s pan-forwarding loop to observe, since its `awaitEachGesture` classifier consumes the first slop-crossing move purely for gesture-type classification — fixed by adding a slop-crossing move followed by a second, separately measured move; (2) the idealized-height-rejection test's device-geometry precondition doesn't hold on the S23 (a genuinely ~16:9 device with no material system-bar mismatch at this viewport) — fixed by using `org.junit.Assume.assumeTrue` for that one precondition instead of a hard assertion, so it is correctly skipped (not failed) on S23 rather than reporting a false production failure.
- All mandatory verification passed genuinely: `compileDebugAndroidTestKotlin` (BUILD SUCCESSFUL); API 29 full class 5/5 (`tests="5" failures="0" errors="0" skipped="0"`, `Starting`/`Finished` lines confirmed, `--rerun`); S23 full class 4/5 pass + 1 correct skip (`tests="5" failures="0" errors="0" skipped="1"`, genuine); pre-Block-2 baseline sanity check via a backed-up, reversible `git stash` (verified `git diff --stat` empty = exact `HEAD` before testing) — 4/5 tests failed genuinely, twice, against real mismatched device values, with the 5th (mutual-alignment) test passing as expected since both composables shared the same wrong idealized viewport pre-fix; `CameraScreen.kt` restored via `git stash pop` and verified byte-identical to the pre-stash backup via `diff`; `compileDebugKotlin` re-confirmed BUILD SUCCESSFUL after restore; `git stash list` empty; both scratchpad backup files deleted. Final post-restoration API-29 re-run: genuine, 5/5 pass.
- Block 3 Status set to Complete. Block 4 not started — no production code, no other test file, no Guide-Tip race work, touched. `git status` confirms the only new/changed entries beyond the pre-Block-3 baseline are the new test file and this plan file.

### 2026-07-14 — Block 4 verification passed; plan closed

- Repository state re-confirmed before running anything: `git status --porcelain` and `git diff --stat` matched the plan plus completed Blocks 1, 2, and 3 exactly — no discrepancy. Confirmed Block 1/2/3 Complete, the Block 2 `CameraScreen.kt` implementation intact, `ReferenceMarkersOverlayUITest.kt` containing only the verified Block 1 change, `CameraScreenConstrainedViewportTest.kt` containing the verified Block 3 tests, the API-29 REFERENCE Guide-Tip race still documented as a separate out-of-scope issue (not touched), and `git stash list` empty (no leftover stash/worktree/backup artifacts from prior blocks' reversible sanity checks).
- All 8 mandatory verification commands run in the exact required order, each genuine, none cached: `compileDebugKotlin`, `compileDebugAndroidTestKotlin`, `ReferenceMarkersOverlayUITest` on API 29 (21/21), `CameraScreenConstrainedViewportTest` on API 29 (5/5), `ReferenceMarkersOverlayUITest` on S23 (21/21), `CameraScreenConstrainedViewportTest` on S23 (4/5 pass + 1 justified geometry-precondition skip, matching Block 3 exactly), `assembleDebug`, `testDebugUnitTest` (825 tests, 0 failures). Full detail in this block's Progress record.
- Post-verification repository review confirmed the final diffs for `ReferenceMarkersOverlayUITest.kt`, `CameraScreen.kt`, and `CameraScreenConstrainedViewportTest.kt` each match their respective block's approved scope exactly, with no unrelated changes, no formatting/refactoring drift, and no silent rewriting of any prior block's historical record or Amendment. The four previously-approved unrelated changes (`MediaStoreWriterGpsTest.kt`, `SettingsScreenTest.kt`, `ShareComparisonScreenTest.kt`, `ui/compare/CompareScreen.kt`) remain untouched (diff stat identical throughout). The untracked `-Pandroid` file was left alone.
- No source file was modified during Block 4 — verification-only, as designed.
- Block 4 Status set to Complete. `# Current Progress` updated to reflect all four blocks Complete.

## Amendment — 2026-07-14 (Part 3): Plan closure — final Document Status

**Scope:** this is a closure note, not a correction of prior findings. Per "Progress Update Rules," direct edits are authorized only for a block's own Status line, its Progress record, the Current Progress checklist, and the Change Log — the `# Document Status` section's "Implementation status" field is not among those explicitly listed, so it is not rewritten in place. This Amendment records the final status instead.

**Final status:** with Blocks 1, 2, 3, and 4 all genuinely verified Complete (see each block's Progress record and this Change Log), the plan's overall **Implementation status is Complete** as of 2026-07-14. The shared constrained viewport fix (Design B) is fully implemented in `CameraScreen.kt`, proven via the isolated component-test harness (Block 1) and the authoritative real-`CameraScreen()` integration tests (Block 3), and holistically regression-verified (Block 4) with no unrelated behavior changed.

**Open item carried forward, explicitly not part of this plan:** the API-29 REFERENCE Guide-Tip Completion Race (see Amendment 2026-07-14 (Part 2), "Separate Issue Record") remains unresolved and requires its own future analysis → scope → implementation cycle. It does not block this plan's closure, since it is proven causally unrelated to this plan's diff.
