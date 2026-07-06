# GUIDE_TIPS_IMPLEMENTATION_PLAN

Spec: `docs/GUIDE_TIPS_UX_V1.md`  
Base: current implementation as of commit `24ba386`  
Tests baseline: 805/805 instrumented tests passing as of 2026-07-01

---

## 1. Current vs. Target Summary

| Area | Current | Target |
|---|---|---|
| Tip count | 7 | 5 |
| Tip IDs | REFERENCE, ALIGN, COMPARE, HISTORY, EXPORT, MARKER, GPS | REFERENCE, SHARE, EDIT_SESSION, OPEN_COMPARISON, MULTI_SELECT |
| Card color | `surfaceVariant` → grey `0xFF666666` | `SameViewAppSurface` → `0xFF17202F` |
| Card max width | `min(320.dp, container - 32.dp)` | `min(280.dp, container - 32.dp)` |
| Pointer | 8 dp circle inside card column | 14×8 dp triangle outside card edge, directional |
| Animation | `AnimatedVisibility(visible = true)` — never triggers | `AnimatedVisibility` keyed on `activeGuideTip != null`, 200ms/150ms fade |
| Completion model | Dismissed by button tap | Completed by feature event; Dismiss persists nothing |
| Button labels | "Got it" / "Learn more" | "Dismiss" / "Learn more" |
| Landscape placement | Compact: ABOVE/BELOW only | Compact landscape: all four sides |
| Exclusion zones | None | Capture button; compare viewport |
| Library scope | Not implemented | New: LIBRARY scope, GuideTipHost in CompareLibraryScreen |
| completeTip() function | Does not exist | New function in GuideTipController |
| Stable grid anchor | Does not exist | New phantom anchor in CompareLibraryScreen |

---

## 2. What Is Reused Without Change

The following components require **no change** in this implementation:

| Component | File | Reason |
|---|---|---|
| `GuideTipAnchor` data class | `guide/GuideTipAnchor.kt` | Model is correct; key enum will change separately |
| `guideTipAnchor()` Modifier extension | `guide/GuideTipAnchor.kt` | Implementation is correct |
| `GuideRepository` | `guide/GuideRepository.kt` | DataStore, keys, and operations are unchanged |
| `GuideTipScope` (after adding LIBRARY) | `guide/GuideTipScope.kt` | Enum pattern unchanged |
| `GuideTipController.evaluate()` | `guide/GuideTipController.kt` | Core eligibility logic unchanged; new function added alongside |
| `GuideTipController.dismissActiveTip()` | `guide/GuideTipController.kt` | Dismiss path unchanged |
| `GuideTipController.clearActiveTipWithoutMarkingSeen()` | `guide/GuideTipController.kt` | Path unchanged |
| `GuideTipController.onUserAction()` | `guide/GuideTipController.kt` | Anti-spam logic unchanged |
| `calculateGuideTipPlacement()` (mostly) | `guide/GuideTipPlacement.kt` | Pure function; exclusion zone and landscape changes are additive |
| `FirstRunWalkthroughGateState` enum | `guide/GuideRoutes.kt` | Unchanged |
| `WalkthroughEntryMode` enum | `guide/GuideRoutes.kt` | Unchanged |
| Guide screen reset flow | `guide/GuideScreen.kt` | Unchanged |
| Guide topic navigation | `guide/GuideRoutes.kt` | Topic IDs unchanged for relevant tips |

---

## 3. Reusable Test Infrastructure

| Test file | Reuse / change needed |
|---|---|
| `GuideRepositoryTest.kt` | Reuse; add 2 new test cases (§11 §12 from spec §26.1) |
| `GuideTipControllerTest.kt` | Partial reuse; anti-spam, dismiss, evaluate tests structurally unchanged — update tip IDs, add completeTip and prerequisite tests |
| `GuideTipHostTest.kt` | Partial reuse; keep placement geometry and render tests; replace Got it tag references with Dismiss tag |
| `CameraGuideTipIntegrationTest.kt` | Keep only `referenceTip_anchorsToReferenceButton` test; delete compareTip, markerTip, gpsTip tests |
| `CompareGuideTipIntegrationTest.kt` | All 3 existing tests are for EXPORT and must be deleted; replace with SHARE and EDIT_SESSION tests |
| `LibraryGuideTipIntegrationTest.kt` | Does not exist; must be created |

---

## 4. Implementation Blocks

Work is divided into eight sequential blocks. Each block must leave all tests passing before the next block begins.

---

### Block A — Tip Model Cleanup

**Goal:** Update tip identity files. Remove removed IDs. Add new IDs. Update anchor keys. Update scopes.

**Files:**

| File | Change |
|---|---|
| `guide/GuideTipId.kt` | Remove ALIGN, COMPARE, HISTORY, EXPORT, MARKER, GPS. Add SHARE, EDIT_SESSION, OPEN_COMPARISON, MULTI_SELECT. Keep REFERENCE unchanged. |
| `guide/GuideTipAnchorKey.kt` | Remove ALIGN_CONTROLS, COMPARE_ACTION, HISTORY_ACTION, EXPORT_ACTION, MARKER_ACTION, GPS_CHIP. Add SHARE_ACTION, OVERFLOW_ACTION, LIBRARY_GRID_AREA. Keep REFERENCE_BUTTON unchanged. |
| `guide/GuideTipScope.kt` | Add LIBRARY value. |
| `guide/GuideTipRegistry.kt` | Remove 6 tip registrations. Add SHARE, EDIT_SESSION, OPEN_COMPARISON, MULTI_SELECT registrations. Update REFERENCE (no anchor key change needed). |
| `res/values/strings.xml` | Remove 6 old tip string pairs. Add 5 new tip string pairs. Rename `guide_tip_got_it` → `guide_tip_dismiss`. |
| `res/values-de/strings.xml` | Same changes as en strings.xml with German translations. |

**GuideTipRegistry additions:**

```
REFERENCE:
  anchorKey = REFERENCE_BUTTON
  scope = CAMERA
  priority = 1
  learnMoreTopicId = GuideTopicId.REFERENCE_PHOTOS

SHARE:
  anchorKey = SHARE_ACTION
  scope = COMPARE
  priority = 1
  learnMoreTopicId = GuideTopicId.SHARE_COMPARISON_IMAGE

EDIT_SESSION:
  anchorKey = OVERFLOW_ACTION
  scope = COMPARE
  priority = 2
  prerequisiteTipId = GuideTipId.SHARE
  learnMoreTopicId = GuideTopicId.GETTING_STARTED

OPEN_COMPARISON:
  anchorKey = LIBRARY_GRID_AREA
  scope = LIBRARY
  priority = 1
  learnMoreTopicId = null

MULTI_SELECT:
  anchorKey = LIBRARY_GRID_AREA
  scope = LIBRARY
  priority = 2
  prerequisiteTipId = GuideTipId.OPEN_COMPARISON
  learnMoreTopicId = null
```

**Prerequisite enforcement:** `GuideTipController.evaluate()` must check `prerequisiteTipId != null && !seenIds.contains(tip.prerequisiteTipId.storedValue)` and skip ineligible tips. This logic may already exist for HISTORY's prerequisite of SHARE. Verify and extend.

**Tests after Block A:** Existing unit tests for controller and registry will fail on compile because tip IDs referenced in test files no longer exist. Update test references to new IDs. All 5 new unit test cases for prerequisites (spec §26.1 rows 8–9) can be written now.

---

### Block B — Card Visual Redesign

**Goal:** Fix card color, max width, pointer, animation in `GuideTipHost`.

**Files:**

| File | Change |
|---|---|
| `guide/GuideTipHost.kt` | All visual changes detailed below |

**Changes in `GuideTipHost.kt`:**

1. **Card color fix:**
   ```kotlin
   // Remove:
   CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
   // Replace with:
   CardDefaults.cardColors(containerColor = SameViewAppSurface)
   ```
   Import `SameViewAppSurface` from `ui.theme.Color`.

2. **Max card width fix:**
   ```kotlin
   // Remove:
   val maxCardWidth = min(320.dp, containerWidth - 32.dp)
   // Replace with:
   val maxCardWidth = min(280.dp, containerWidth - 32.dp)
   ```

3. **Pointer replacement:** Remove the existing 8 dp circle `GuideTipPointer` composable. Add a new `GuideTipPointer` composable drawn with Canvas:
   - Parameters: `side: PlacementSide`, `color: Color = SameViewAppSurface`
   - Draws a filled triangle (base 14 dp, height 8 dp) pointing in the direction of the anchor.
   - For ABOVE placement: pointer points downward (card is above anchor, pointer extends toward anchor below).
   - For BELOW placement: pointer points upward.
   - For START placement: pointer points rightward.
   - For END placement: pointer points leftward.
   - Size of the composable: `width = 14.dp, height = 8.dp` for ABOVE/BELOW; `width = 8.dp, height = 14.dp` for START/END.
   - The pointer is placed as a sibling of the Card in the SubcomposeLayout, positioned at the card's anchor-facing edge, centered on that edge.

4. **Animation fix:** Find `AnimatedVisibility(visible = true, ...)` and replace with the caller-controlled pattern. `GuideTipHost` itself does not own the AnimatedVisibility — instead, the call sites in each screen wrap `GuideTipHost` in:
   ```kotlin
   AnimatedVisibility(
       visible = activeGuideTip != null,
       enter = fadeIn(animationSpec = tween(200, easing = FastOutLinearInEasing)),
       exit = fadeOut(animationSpec = tween(150, easing = LinearOutSlowInEasing))
   )
   ```
   Remove the `AnimatedVisibility` currently inside `GuideTipHost`.

5. **Button label update:** Replace `stringResource(R.string.guide_tip_got_it)` with `stringResource(R.string.guide_tip_dismiss)`. Update test tags from `"guide_tip_got_it"` to `"guide_tip_dismiss"` in both the composable and all test files.

6. **Body text max lines:** Ensure `maxLines = 2, overflow = TextOverflow.Ellipsis` is applied to the body text element.

**Tests after Block B:** `GuideTipHostTest.kt` render tests should pass. Update test tag references from `"guide_tip_got_it"` to `"guide_tip_dismiss"`. Add card-color and max-width tests (spec §26.2 rows 13–14). The pointer test tag must be present and the pointer test (row 15) must pass.

---

### Block C — Placement Algorithm Update

**Goal:** Fix Compact landscape candidate sides. Add exclusion zone support.

**Files:**

| File | Change |
|---|---|
| `guide/GuideTipPlacement.kt` | Two independent changes |

**Change 1 — Landscape candidate sides:**

Update `candidateSides()`:

```kotlin
internal fun candidateSides(
    windowWidthSizeClass: WindowWidthSizeClass,
    isLandscape: Boolean
): List<PlacementSide> = when {
    windowWidthSizeClass == WindowWidthSizeClass.Compact && !isLandscape ->
        listOf(PlacementSide.ABOVE, PlacementSide.BELOW)
    windowWidthSizeClass == WindowWidthSizeClass.Compact && isLandscape ->
        listOf(PlacementSide.ABOVE, PlacementSide.BELOW, PlacementSide.START, PlacementSide.END)
    else ->
        listOf(PlacementSide.END, PlacementSide.START, PlacementSide.ABOVE, PlacementSide.BELOW)
}
```

Update `GuideTipPlacementInput` to include `isLandscape: Boolean`.

**Change 2 — Exclusion zones:**

Add `exclusionZones: List<Rect> = emptyList()` to `GuideTipPlacementInput`.

In the candidate evaluation loop in `calculateGuideTipPlacement()`, add after the existing anchor-overlap check:

```kotlin
val overlapsExclusion = input.exclusionZones.any { zone -> cardRect.overlapsRect(zone) }
if (overlapsExclusion) continue
```

`overlapsRect()` is the existing extension function in `GuideTipPlacement.kt`.

**Tests after Block C:** Add 5 new unit tests for placement (spec §26.1 rows 1–5): Capture button exclusion, compare viewport exclusion, Compact landscape candidates, 280 dp max width in 360 dp container, deferred-when-no-safe-side.

---

### Block D — Camera Screen Integration Update

**Goal:** Simplify Camera integration to only REFERENCE tip. Remove ALIGN, COMPARE, HISTORY, MARKER, GPS eligibility and anchor bindings. Add AnimatedVisibility wrapper. Wire Reference completion event.

**Files:**

| File | Change |
|---|---|
| `ui/camera/CameraScreen.kt` | Multiple changes below |
| `ui/camera/CameraViewModel.kt` | Add completeTip call on reference success |

**Changes in `CameraScreen.kt`:**

1. **Remove eligibility state for removed tips:**
   Remove from `cameraEligibleTipIds` computation:
   - `isAlignTipIdleReady` condition → remove entirely
   - COMPARE eligibility condition → remove
   - HISTORY eligibility condition → remove
   - `isMarkerTipEligible` condition → remove
   - `gpsGuidanceState` GPS condition → remove

   Final:
   ```kotlin
   val cameraEligibleTipIds: Set<GuideTipId> = buildSet {
       if (referenceUri == null) add(GuideTipId.REFERENCE)
   }.filter { tipId ->
       val tip = GuideTipRegistry.tipFor(tipId)
       tip == null || guideTipAnchors.containsKey(tip.anchorKey)
   }.toSet()
   ```

2. **Remove anchor bindings for removed tips:**
   Remove `Modifier.guideTipAnchor(GuideTipAnchorKey.ALIGN_CONTROLS, ...)` applications.
   Remove `Modifier.guideTipAnchor(GuideTipAnchorKey.COMPARE_ACTION, ...)` applications.
   Remove `Modifier.guideTipAnchor(GuideTipAnchorKey.HISTORY_ACTION, ...)` application.
   Remove `Modifier.guideTipAnchor(GuideTipAnchorKey.MARKER_ACTION, ...)` applications.
   Remove `Modifier.guideTipAnchor(GuideTipAnchorKey.GPS_CHIP, ...)` application.
   Keep `Modifier.guideTipAnchor(GuideTipAnchorKey.REFERENCE_BUTTON, ...)` on both portrait and landscape reference button positions.

3. **Capture button exclusion zone:** Add `onGloballyPositioned` to the Capture button slot to report its bounds into a `captureButtonBounds: Rect?` state variable. Pass this as `exclusionZones = listOfNotNull(captureButtonBounds)` to `GuideTipHost`.

4. **AnimatedVisibility wrapper:** Wrap the `GuideTipHost` call with:
   ```kotlin
   AnimatedVisibility(
       visible = activeCameraTip != null,
       enter = fadeIn(animationSpec = tween(200, easing = FastOutLinearInEasing)),
       exit = fadeOut(animationSpec = tween(150, easing = LinearOutSlowInEasing))
   )
   ```

5. **Pass isLandscape to GuideTipHost:** `GuideTipHost` passes it to the placement input. Check if `isLandscape` is already available in `CameraScreen`; it should be (used for landscape layout). Pass it through.

6. **Screen entry delay:** A `LaunchedEffect(Unit)` or `LaunchedEffect(walkthroughCompleted)` that sets `isCameraReady = true` after 800 ms before making the tip eligible. Add `isCameraReady` boolean state. Include it in the eligibility filter: `&& isCameraReady`.

**Changes in `CameraViewModel.kt`:**

In `onReferenceImageSelected()` success path:
```kotlin
viewModelScope.launch {
    guideTipController?.completeTip(GuideTipId.REFERENCE)
}
```

**Tests after Block D:**
- `CameraGuideTipIntegrationTest.kt`: Keep and update `referenceTip_anchorsToReferenceButton`. Delete `compareTip_anchorsToCompareButtonWhenCompareInputExists`, `markerTip_defersUntilMarkerActionIsVisible`, `gpsTip_anchorsToGpsChipWhenGuidanceIsVisible`. Add:
  - `referenceTip_notVisible_whileCaptureInProgress`
  - `referenceTip_notVisible_whileReferencePickerActive`
  - `captureButton_reachable_whileReferenceTipVisible`
- All 805 existing tests must still pass after removing anchor bindings and eligibility state.

---

### Block E — Compare Screen Integration Update **[DONE]**

**Goal:** Replace EXPORT tip with SHARE tip. Add EDIT_SESSION tip. Wire slider interaction detection. Wire completion events. Add AnimatedVisibility wrapper.

**Files:**

| File | Change |
|---|---|
| `ui/compare/CompareScreen.kt` | Multiple changes below |
| `MainActivity.kt` | Add EDIT_SESSION completion event |

**Changes in `CompareScreen.kt`:**

1. **Remove EXPORT anchor binding:** Remove `Modifier.guideTipAnchor(GuideTipAnchorKey.EXPORT_ACTION, ...)` from the Export icon button.

2. **Add SHARE anchor binding:** Add `Modifier.guideTipAnchor(GuideTipAnchorKey.SHARE_ACTION, onGuideTipAnchor)` to the Export/Share icon button (same button, new anchor key).

3. **Add OVERFLOW anchor binding:** Add `Modifier.guideTipAnchor(GuideTipAnchorKey.OVERFLOW_ACTION, onGuideTipAnchor)` to the overflow (⋮) menu button.

4. **Slider interaction detection:** Add a `LaunchedEffect` or drag gesture observer that:
   - Tracks horizontal drag start position and time on the compare slider.
   - Sets `sliderInteractionDetected = true` when horizontal delta > 8 dp and duration > 100 ms.
   - After setting, starts a coroutine: `delay(1_000); isSliderInteractionReady = true`.
   - `isSliderInteractionReady` is a `remember { mutableStateOf(false) }` local state.

5. **Eligibility derivation:**
   ```kotlin
   val shareTipCompleted by guideRepository
       .observeTipSeen(GuideTipId.SHARE)
       .collectAsState(initial = false)

   val compareEligibleTipIds: Set<GuideTipId> = buildSet {
       if (sessionId != null && isSliderInteractionReady) {
           if (!shareTipCompleted) add(GuideTipId.SHARE)
       }
       if (sessionId != null && shareTipCompleted) {
           add(GuideTipId.EDIT_SESSION)
       }
   }.filter { tipId ->
       val tip = GuideTipRegistry.tipFor(tipId)
       tip == null || guideTipAnchors.containsKey(tip.anchorKey)
   }.toSet()
   ```

6. **SHARE completion event:** In the handler that sets `showExportMenu = true`:
   ```kotlin
   if (!shareTipCompleted) {
       viewModelScope.launch { guideTipController?.completeTip(GuideTipId.SHARE) }
   }
   ```

7. **Compare viewport exclusion zone:** Add `onGloballyPositioned` to the compare slider composable to report its bounds into `compareViewportBounds: Rect?`. Pass `exclusionZones = listOfNotNull(compareViewportBounds)` to `GuideTipHost`.

8. **AnimatedVisibility wrapper:** Wrap `GuideTipHost` with 200ms/150ms fade animation keyed on `activeCompareTip != null`.

9. **Screen entry delay for EDIT_SESSION:** The 1 200 ms delay before EDIT_SESSION appears is enforced in `GuideTipController.evaluate()` or as a local `LaunchedEffect` delay in the screen. Simpler approach: add a `LaunchedEffect(shareTipCompleted)` that sets `editSessionTipDelayReady = true` after `delay(1_200)` when `shareTipCompleted` becomes true. Include `&& editSessionTipDelayReady` in the eligibility condition for EDIT_SESSION.

**Changes in `MainActivity.kt`:**

In the navigation listener / route change handler for the Edit Session route:
```kotlin
if (route == GuideRoutes.ROUTE_EDIT_SESSION) {
    lifecycleScope.launch { guideTipController.completeTip(GuideTipId.EDIT_SESSION) }
}
```

The exact route name for Edit Session must be confirmed in `GuideRoutes.kt`.

**Tests after Block E:**
All 3 existing `CompareGuideTipIntegrationTest.kt` tests are for EXPORT / `GuideTipId.EXPORT` and must be deleted. Replace with:
- `shareTip_appearsAfterSliderInteraction`
- `shareTip_completesOnExportMenuOpen`
- `editSessionTip_notEligibleWithoutShareCompleted`
- `editSessionTip_anchorsToOverflowButton`

**Block E completion note (2026-07-06):**

All Block E goals achieved:

- Compare scope wired in `CompareScreen.kt` (`GuideTipScope.COMPARE`)
- SHARE Tip: anchors to `SHARE_ACTION`; completes when the export dropdown opens
- EDIT_SESSION Tip: anchors to `OVERFLOW_ACTION`; completes via `addOnDestinationChangedListener` in `MainActivity` on navigation to Edit Session
- SHARE prerequisite enforced: `EDIT_SESSION` does not appear until `SHARE` is marked seen
- Integration tests: 4 new tests in `CompareGuideTipIntegrationTest.kt` replace the 3 deleted EXPORT tests; all pass

Flaky test fix: A race in `CompareGuideTipIntegrationTest` was stabilized by signal-relative synchronization — `guide_tip_host` is awaited before asserting on `guide_tip_card`, making waits host-readiness-relative instead of wallclock-relative. No production code was changed.

Latest verified test state:
- `connectedDebugAndroidTest` — 861/861 PASSED
- `testDebugUnitTest` — PASSED
- `assembleDebug` — BUILD SUCCESSFUL

---

### Block F — Library Screen Integration (New)

**Goal:** Add guide tip support to `CompareLibraryScreen`. New screen scope. New stable phantom anchor. New tip host. Wire completion events.

**Files:**

| File | Change |
|---|---|
| `ui/library/CompareLibraryScreen.kt` | Multiple additions below |

This is the most additive block and has no deletions.

**Changes in `CompareLibraryScreen.kt`:**

1. **Add `guideTipController` parameter:**
   ```kotlin
   @Composable
   fun CompareLibraryScreen(
       ...
       guideTipController: GuideTipController? = null,
       onGuideTipLearnMore: (GuideTipId, GuideTopicId) -> Unit = { _, _ -> },
       ...
   )
   ```

2. **Guide state collection:**
   ```kotlin
   val openComparisonTipCompleted by guideRepository
       .observeTipSeen(GuideTipId.OPEN_COMPARISON)
       .collectAsState(initial = false)

   val multiSelectTipCompleted by guideRepository
       .observeTipSeen(GuideTipId.MULTI_SELECT)
       .collectAsState(initial = false)
   ```
   `guideRepository` is injected via the ViewModel that backs this screen, or passed explicitly if the screen already has access to a Guide-aware ViewModel.

3. **Eligible tip IDs:**
   ```kotlin
   val isTipDelayReady by remember { mutableStateOf(false) }
   LaunchedEffect(Unit) {
       delay(600)
       isTipDelayReady = true
   }

   val libraryEligibleTipIds: Set<GuideTipId> = buildSet {
       if (sessions.isNotEmpty() && !openComparisonTipCompleted && isTipDelayReady) {
           add(GuideTipId.OPEN_COMPARISON)
       }
       if (openComparisonTipCompleted && !multiSelectTipCompleted && isTipDelayReady) {
           add(GuideTipId.MULTI_SELECT)
       }
   }.filter { tipId ->
       val tip = GuideTipRegistry.tipFor(tipId)
       tip == null || libraryTipAnchors.containsKey(tip.anchorKey)
   }.toSet()
   ```

4. **Blocked condition:**
   ```kotlin
   val isGridScrollInProgress by remember {
       derivedStateOf { lazyGridState.isScrollInProgress }
   }
   val libraryTipBlocked = isMultiSelectActive ||
       showDeleteDialog ||
       isBackupInProgress ||
       isGridScrollInProgress
   ```

   `lazyGridState` is the `LazyGridState` passed to `LazyVerticalGrid`. `isScrollInProgress` covers both pointer-driven drag and momentum fling. Using `derivedStateOf` prevents the blocked-condition recomposition from firing on every scroll frame.

5. **Evaluate LaunchedEffect:**
   ```kotlin
   LaunchedEffect(libraryEligibleTipIds, libraryTipBlocked) {
       if (libraryTipBlocked) {
           guideTipController?.clearActiveTipWithoutMarkingSeen()
       } else {
           guideTipController?.evaluate(
               scope = GuideTipScope.LIBRARY,
               eligibleTipIds = libraryEligibleTipIds
           )
       }
   }
   ```

6. **Anchor state:**
   ```kotlin
   var libraryTipAnchors by remember { mutableStateOf<Map<GuideTipAnchorKey, GuideTipAnchor>>(emptyMap()) }
   val onLibraryTipAnchor: (GuideTipAnchor) -> Unit = { anchor ->
       libraryTipAnchors = libraryTipAnchors + (anchor.key to anchor)
   }
   ```

7. **Stable phantom anchor:** Placed immediately before `LazyVerticalGrid` in the content column:
   ```kotlin
   val density = LocalDensity.current
   Box(
       modifier = Modifier
           .fillMaxWidth()
           .height(0.dp)
           .onGloballyPositioned { coordinates ->
               val rootBounds = coordinates.boundsInRoot()
               with(density) {
                   onLibraryTipAnchor(
                       GuideTipAnchor(
                           key = GuideTipAnchorKey.LIBRARY_GRID_AREA,
                           bounds = Rect(
                               left = rootBounds.left,
                               top = rootBounds.top,
                               right = rootBounds.left + 120.dp.toPx(),
                               bottom = rootBounds.top + 80.dp.toPx()
                           )
                       )
                   )
               }
           }
   )
   ```

8. **Completion events:**
   - Tile tap handler (existing `onOpenSession` call site): before navigating, call `viewModelScope.launch { guideTipController?.completeTip(GuideTipId.OPEN_COMPARISON) }`.
   - Long-press handler (existing `onLongPress` or multi-select activation): call `viewModelScope.launch { guideTipController?.completeTip(GuideTipId.MULTI_SELECT) }`.

9. **GuideTipHost placement:**
   ```kotlin
   val activeLibraryTip by guideTipController?.activeTipId
       ?.map { id -> id?.let { GuideTipRegistry.tipFor(it) } }
       ?.collectAsState(initial = null)
       ?: remember { mutableStateOf(null) }

   Box(modifier = Modifier.fillMaxSize()) {
       // ... existing screen content ...

       AnimatedVisibility(
           visible = activeLibraryTip != null,
           enter = fadeIn(animationSpec = tween(200, easing = FastOutLinearInEasing)),
           exit = fadeOut(animationSpec = tween(150, easing = LinearOutSlowInEasing))
       ) {
           GuideTipHost(
               activeTip = activeLibraryTip,
               anchors = libraryTipAnchors.values.toList(),
               windowWidthSizeClass = windowWidthSizeClass,
               isLandscape = isLandscape,
               exclusionZones = emptyList(),
               onGotIt = { guideTipController?.dismissActiveTip(GuideTipDismissReason.GOT_IT) },
               onLearnMore = { tipId, topicId -> onGuideTipLearnMore(tipId, topicId) },
               modifier = Modifier.fillMaxSize()
           )
       }
   }
   ```

10. **Wire in MainActivity / navigation:** Pass `guideTipController` down to `CompareLibraryScreen` from the parent composable that already has access to it (likely the same entry point as `CameraScreen` and `CompareScreen`).

**Tests after Block F:**
Create `LibraryGuideTipIntegrationTest.kt` with 7 tests (spec §26.2):
- `openComparisonTip_showsWhenSessionExists`
- `openComparisonTip_notVisible_inMultiSelectMode`
- `openComparisonTip_notVisible_whileGridScrolling`
- `openComparisonTip_appearsAfterScrollSettles`
- `multiSelectTip_notEligible_withoutOpenComparisonCompleted`
- `multiSelectTip_showsAfterOpenComparisonCompleted`
- Stable anchor: `libraryGridAreaAnchor_alwaysReportedRegardlessOfScrollState`

---

### Block G — GuideTipController Extension

**Goal:** Add `completeTip()` function.

**Files:**

| File | Change |
|---|---|
| `guide/GuideTipController.kt` | Add one function |

**New function:**
```kotlin
suspend fun completeTip(tipId: GuideTipId) {
    repository.markTipSeen(tipId)
    if (_activeTipId.value == tipId) {
        _activeTipId.value = null
    }
    waitingForUserActionAfterDismissal = true
}
```

This function is called from feature event sites outside the tip card. It is a public `suspend fun` on the singleton. Callers wrap it in `viewModelScope.launch { ... }` or `lifecycleScope.launch { ... }`.

**Tests after Block G:**
Add to `GuideTipControllerTest.kt`:
- `completeTip_marksSeenInRepository`
- `completeTip_clearsActiveTipIfActive`
- `completeTip_doesNotClearActiveTipIfDifferentTipIsActive`
- `completeTip_setsWaitingForUserAction`

---

### Block H — Final Verification and Test Baseline

**Goal:** Confirm all tests pass. Verify no regressions in non-tip feature areas.

**Checklist:**

- [ ] `GuideTipControllerTest.kt` — all tests pass including new prerequisite and completeTip tests
- [ ] `GuideRepositoryTest.kt` — all tests pass including new reset and unknown-ID tests
- [ ] `GuideTipPlacementTest.kt` — all placement tests pass including exclusion zone and landscape tests
- [ ] `GuideTipHostTest.kt` — all render tests pass with correct color, width, pointer, and Dismiss label
- [ ] `CameraGuideTipIntegrationTest.kt` — only Reference tests remain; COMPARE/MARKER/GPS tests removed
- [ ] `CompareGuideTipIntegrationTest.kt` — EXPORT tests removed; SHARE and EDIT_SESSION tests pass
- [ ] `LibraryGuideTipIntegrationTest.kt` — all 7 new tests pass
- [ ] Total instrumented test count ≥ 805 (net new tests should exceed removed tests)
- [ ] No compile errors in removed anchor key or tip ID references anywhere in non-guide code
- [ ] `adb shell am instrument` run on device passes all tests

---

## 5. Files Requiring Changes

Complete list of files that change in any block:

| File | Block(s) |
|---|---|
| `guide/GuideTipId.kt` | A |
| `guide/GuideTipAnchorKey.kt` | A |
| `guide/GuideTipScope.kt` | A |
| `guide/GuideTipRegistry.kt` | A |
| `guide/GuideTipController.kt` | G |
| `guide/GuideTipHost.kt` | B |
| `guide/GuideTipPlacement.kt` | C |
| `ui/camera/CameraScreen.kt` | D |
| `ui/camera/CameraViewModel.kt` | D |
| `ui/compare/CompareScreen.kt` | E |
| `ui/library/CompareLibraryScreen.kt` | F |
| `MainActivity.kt` | E |
| `res/values/strings.xml` | A |
| `res/values-de/strings.xml` | A |

**Test files:**

| File | Block(s) | Action |
|---|---|---|
| `GuideTipControllerTest.kt` | A, G | Update existing, add new |
| `GuideRepositoryTest.kt` | A | Add 2 new tests |
| `GuideTipPlacementTest.kt` | C | Add 5 new tests |
| `GuideTipHostTest.kt` | B | Update test tag references, add 3 new tests |
| `CameraGuideTipIntegrationTest.kt` | D | Delete 3 tests, keep/update 1, add 3 new |
| `CompareGuideTipIntegrationTest.kt` | E | Delete all 3, add 4 new |
| `LibraryGuideTipIntegrationTest.kt` | F | Create new file, 5 tests |

---

## 6. Migration and Removal Work

### 6.1 Anchor Key Removal

Every `Modifier.guideTipAnchor(GuideTipAnchorKey.XXX, ...)` call in `CameraScreen.kt` and `CompareScreen.kt` for the following removed keys must be deleted:

- `ALIGN_CONTROLS` — 2 call sites in `CameraScreen.kt` (portrait and landscape `AlignControls`)
- `COMPARE_ACTION` — 2 call sites in `CameraScreen.kt`
- `HISTORY_ACTION` — 1 call site in `CameraScreen.kt`
- `MARKER_ACTION` — 3 call sites in `CameraScreen.kt` (reference menu, collapsed marker, expanded marker)
- `GPS_CHIP` — 1 call site in `CameraScreen.kt`
- `EXPORT_ACTION` — 1 call site in `CompareScreen.kt`

After removal, verify that no `GuideTipAnchorKey` enum value remains unreferenced in source (except LIBRARY_GRID_AREA, which is referenced in `CompareLibraryScreen.kt` via the phantom anchor).

### 6.2 State Variable Removal in CameraScreen

Remove state variables that exist solely to support removed tips:

- `isAlignTipIdleReady: Boolean` (drives ALIGN tip eligibility via idle timer)
- `isMarkerTipEligible: Boolean` (drives MARKER tip eligibility)
- Any `LaunchedEffect` that sets the above

The GPS guidance chip state (`gpsGuidanceState`) must **not** be removed — it drives the GPS chip UI, not only the tip.

The overlay interaction tracking state (`isOverlayInteractionActive`, `overlayInteractionGeneration`) must **not** be removed if it is also used for the camera tip blocked condition. The blocked condition retains it.

### 6.3 String Key Removal

Remove from `res/values/strings.xml` and `res/values-de/strings.xml`:

- `guide_tip_got_it`
- `guide_tip_align_title`
- `guide_tip_align_body`
- `guide_tip_marker_title`
- `guide_tip_marker_body`
- `guide_tip_gps_title`
- `guide_tip_gps_body`
- `guide_tip_compare_title`
- `guide_tip_compare_body`
- `guide_tip_history_title`
- `guide_tip_history_body`
- `guide_tip_export_title`
- `guide_tip_export_body`

Check with a full project build that no resource reference to any of these keys remains after removal (the build will fail with `unresolved reference` if any resource is still referenced in code).

### 6.4 Test Cleanup

Delete individual test functions (not whole files unless all tests in the file are being removed):

- `CameraGuideTipIntegrationTest.kt`: delete `compareTip_anchorsToCompareButtonWhenCompareInputExists`, `markerTip_defersUntilMarkerActionIsVisible`, `gpsTip_anchorsToGpsChipWhenGuidanceIsVisible`
- `CompareGuideTipIntegrationTest.kt`: delete `exportTip_appearsForSavedSessionContext`, `exportTip_gotItMarksTipSeen`, `exportTip_learnMoreOpensShareGuideTopicAndMarksSeen`

---

## 7. Regression Risk Analysis

### 7.1 Low Risk

| Area | Rationale |
|---|---|
| GuideTipHost visual changes | Isolated composable with no side effects. Visual changes do not affect business logic. Risk: test tags changed (guide_tip_got_it → guide_tip_dismiss) — breaks tests if not updated. Mitigation: update test tags in Block B. |
| Tip set reduction | Data-driven changes in `GuideTipRegistry`. No logic depends on specific enum values except the test cases that are being updated. |
| Camera eligibility simplification | Removing state variables and eligibility conditions reduces code. Less code = lower risk. Risk: accidental removal of state used outside tips. Mitigation: check each removed variable's usage before deleting (Block D). |
| GuideRepository | No changes to persistence layer. DataStore file name, key names, and read/write functions unchanged. |

### 7.2 Medium Risk

| Area | Rationale |
|---|---|
| CompareScreen slider interaction detection | New gesture observation on the compare slider could interact with existing drag handling. Risk: interference with existing slider events or accidental double-detection. Mitigation: use a one-shot flag (`sliderInteractionDetected`) that stops observing after first qualifying drag. Test with both portrait and landscape slider. |
| CompareLibraryScreen GuideTipHost injection | Adding a new composition layer over the library grid could affect touch passthrough to the grid tiles. Risk: tap/long-press events intercepted by the GuideTipHost container. Mitigation: confirm `GuideTipHost` uses `SubcomposeLayout` without a background Modifier; touch passthrough is correct only if no blocking Modifier exists. |
| CompareLibraryScreen scroll state detection | `LazyGridState.isScrollInProgress` is a Compose state read inside `derivedStateOf`. If not wrapped correctly it recomposes the blocked condition on every scroll frame. Risk: excessive recompositions causing visible jank. Mitigation: always derive `isGridScrollInProgress` inside `derivedStateOf { lazyGridState.isScrollInProgress }` so Compose batches state reads during scroll. |
| EDIT_SESSION completion via MainActivity navigation | Navigation events in MainActivity must fire before or at the same time as the route becoming the active destination. Risk: completion fires late, after the screen has already presented, causing a brief tip flash on Edit Session screen. Mitigation: call `completeTip()` from the `addOnDestinationChangedListener` before the new destination's composition starts. |

### 7.3 Negligible Risk

| Area | Rationale |
|---|---|
| Placement algorithm landscape fix | Additive change; existing ABOVE/BELOW paths are unchanged for portrait. New START/END paths follow the same pattern as Medium/Expanded. |
| AnimatedVisibility fix | Current `visible = true` means the `exit` animation never fires (nothing to exit). Fixing this to `visible = activeGuideTip != null` enables the exit animation for the first time. Risk: visual change only, no behavior regression. |
| Card color fix | Visual-only change. `SameViewAppSurface` is already used for many surfaces in the app. |
| German string translations | No code impact. Only affects string values in `values-de/strings.xml`. |

---

## 8. Block Execution Order

Blocks are executed sequentially. Each block must leave all tests passing before the next block begins.

```
A → B → C → G → D → E → F → H
```

| Step | Block | Depends on |
| --- | --- | --- |
| 1 | A — Tip Model Cleanup | — |
| 2 | B — Card Visual Redesign | A |
| 3 | C — Placement Algorithm Update | B |
| 4 | G — GuideTipController Extension | C |
| 5 | D — Camera Screen Integration Update | G |
| 6 | E — Compare Screen Integration Update | D |
| 7 | F — Library Screen Integration | E |
| 8 | H — Final Verification | F |

---

## 9. Definition of Done

- [ ] `GUIDE_TIPS_UX_V1.md` has been reviewed and approved before Block A begins.
- [ ] All removed tip IDs produce no compile references after Block A.
- [ ] All new string keys have both English and German translations.
- [ ] Card surface color is `SameViewAppSurface` (`0xFF17202F`) — verified in `GuideTipHostTest`.
- [ ] Max card width ≤ 280 dp at runtime — verified in `GuideTipHostTest`.
- [ ] Pointer is a directional triangle, not a circle — verified in `GuideTipHostTest` by pointer test tag.
- [ ] Fade in/out animations are observable (duration > 0 ms) — verified with `MainTestClock`.
- [ ] Dismiss button does not persist tip seen — verified in controller test.
- [ ] Feature event (reference select, export menu, library tile tap, long-press) marks tip seen — verified in integration tests.
- [ ] EDIT_SESSION tip does not appear without SHARE completed — verified in controller test and integration test.
- [ ] MULTI_SELECT tip does not appear without OPEN_COMPARISON completed — verified in controller test.
- [ ] Capture button remains tappable while REFERENCE tip is visible — verified in camera integration test.
- [ ] Library grid stable anchor is always present regardless of scroll state — verified in library integration test.
- [ ] Library tips do not appear while the comparison grid is actively scrolling or flinging — verified in library integration test.
- [ ] Total instrumented test count ≥ 805.
- [ ] No remaining references to removed tip IDs (`ALIGN`, `COMPARE`, `HISTORY`, `EXPORT`, `MARKER`, `GPS`) in source or test files.
- [ ] No remaining references to removed anchor keys in source or test files.
- [ ] `guide_tip_got_it` string key does not exist in any strings.xml file.
