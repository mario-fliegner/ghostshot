# DeinWackelbild V1 — Implementation Plan

# 1. Document Status

This is the repository-derived implementation plan for the DeinWackelbild.de integration, created after:

- **Gate 1** — repository/spec consistency review (`docs/deinwackelbild/prompts/001_...GATE1...md`) — result: READY WITH SPEC CORRECTIONS.
- **Gate 2** — spec correction + network-governance addendum — applied corrections to `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md` and added the DeinWackelbild network exception to `docs/CLAUDE_PROJECT_INSTRUCTION.md`.

This plan is derived from direct inspection of the current repository state (source files, build files, manifest, tests) as of the HEAD commit recorded in §2. It is a planning artifact only. No production code, tests, manifest, Gradle, or dependency was changed in this gate. `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md` remains the authoritative product/UX contract; this plan implements it and does not reinterpret it. No conflict requiring a return to product decision was found during planning.

---

# 2. Repository Baseline

- **Branch:** `main`
- **HEAD:** `2572f89f3f887cb4337866ea073279f67143933b` (2026-08-27) — unchanged since Gate 1/Gate 2.
- **Working tree at plan-creation time:** `docs/CLAUDE_PROJECT_INSTRUCTION.md` modified (Gate 2 addendum, present in the working tree, not yet committed to git); `docs/deinwackelbild/` and `docs/sameview_prompts/` untracked (the latter pre-existing and unrelated, not touched).
- **Android/Gradle baseline** (verified by direct read of `app/build.gradle.kts` and `gradle/libs.versions.toml`): `compileSdk = 36`, `minSdk = 29`, `targetSdk = 36`, AGP `9.1.1`, Kotlin `2.2.10`, KSP `2.3.6`, Hilt `2.59`, Compose BOM `2026.02.01`, CameraX `1.4.1`, Navigation Compose `2.8.7`, Coil `2.7.0`, `androidx.exifinterface` `1.3.7`, Media3 `1.5.1`. `buildFeatures { compose = true; buildConfig = true }`. Only a `release` buildType is customized (`isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-rules.pro` + default optimize file). `proguard-rules.pro` currently contains only `-keepattributes SourceFile,LineNumberTable` / `-renamesourcefileattribute SourceFile` — no existing keep rules to account for.
- **Manifest baseline** (`app/src/main/AndroidManifest.xml`): permissions are `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_MEDIA_LOCATION`. No `INTERNET`. No `networkSecurityConfig`, no `usesCleartextTraffic`. `<queries>` contains only a `mailto:` `SENDTO` intent filter. Single activity `.MainActivity`.
- **Relevant screens/renderers inspected directly for this plan:**
  - `app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt` — exact current TopAppBar Row (lines 323-479) and Export `DropdownMenu` (lines 381-423).
  - `app/src/main/java/com/isardomains/sameview/MainActivity.kt` — route constants (lines 71-84), `createVideoRoute`/`shareComparisonRoute` helpers (lines 599-606), `composable(ROUTE_SHARE_COMPARISON_WITH_ARGS)` block (~544-568).
  - `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModel.kt` — full file read; confirms `SavedStateHandle["sessionId"]` pattern, plain-`MutableStateFlow` state (not `SavedStateHandle`-backed for configurable fields), injectable-lambda testability convention used throughout the codebase, `computeCompareLabels`/`CountryCatalog.resolveDisplayName` locale patterns, direct `metadata.json` JSON parsing via `org.json.JSONObject`.
  - `app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoScreen.kt` — full Rendering-state Back-intercept + confirm/cancel `AlertDialog` pattern (lines 112-159, 181-190).
  - `app/src/main/java/com/isardomains/sameview/image/ShareImageRenderer.kt` and `ShareRenderConfig.kt` — full files read; exact HQ decode/crop/dimension logic traced (see §9).
  - `app/src/main/java/com/isardomains/sameview/ui/camera/ReferenceRenderer.kt` and `CompassProvider.kt` — full files read.
  - `app/proguard-rules.pro` — full file read (near-empty, no relevant existing rules).

No unrelated pre-existing modification was found. The plan matches the exact current code, not an assumed or historical version.

---

# 3. Authoritative Specifications

| Document | Role for this plan |
|---|---|
| `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md` (post-Gate-2) | Product/UX/privacy/technical contract — governs all behavior in this plan |
| `docs/CLAUDE_PROJECT_INSTRUCTION.md` (post-Gate-2) | Master governance; DeinWackelbild network exception now recorded |
| `docs/IMPLEMENTATION_NOTES.md` | Current implementation-state ledger; will receive new entries (§28) |
| `docs/COMPARE_FLOW_V1.md` §43 | Current Export-dropdown contract; extended, not replaced (§28) |
| `docs/COMPARE_SESSION_RENDERING_V1.md` | Confirms `reference.jpg`/`capture.jpg` as deterministic, immutable, frozen — governs the crop-parity requirement |
| `docs/SHARE_COMPARISON_IMAGE_V1.md` + `docs/SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md` | Authoritative source of the existing HQ reconstruction algorithm this plan reuses (§9) |
| `docs/SESSION_ORIGINALS_V1.md` | Defines which original files exist per schema version (v5/v6 vs v2-v4) |
| `docs/SESSION_ORIGINALS_PRIVACY_V1.md` | Confirms the metadata-stripping mechanism (decode/re-encode with no EXIF writer) |
| `docs/SESSION_METADATA_V1.md` | Authoritative `reference.date` precision model (year / year-month / full date) |
| `docs/RESPONSIVE_LAYOUT_SYSTEM_V1.md` | Compact/Medium/Expanded rules; `ShareComparisonScreen`/`CreateVideoScreen` as the template this plan follows |
| `docs/SETTINGS_UX_V1.md` | Confirms `SettingsCard`/`SettingsSwitchRow` component reuse for the date toggle row |
| `docs/RELEASE_HARDENING_AUDIT_V2.md` | Current release/privacy state; will require a follow-up audit note (§27) |

No duplicate/historical document was mistaken for current authority. `docs/RELEASE_HARDENING_AUDIT_V1.md` and `docs/implementation_plans/historic/*` were not treated as authoritative.

---

# 4. Existing Architecture Findings

Summarized findings that ground every later decision (full detail inline in the relevant sections below):

- **Navigation:** single-Activity, flat Navigation Compose graph in `MainActivity.kt`; new destinations follow a fixed pattern: route constant + `{sessionId}` arg + `Uri.encode`-based route-builder + `composable(...)` block calling `hiltViewModel()` with `sessionId` auto-populated from `SavedStateHandle`.
- **Export dropdown:** `CompareScreen.kt:381-423`, a `Box { IconButton { DropdownMenu { DropdownMenuItem × 2 } } }`, gated on `sessionId != null`. No divider exists yet, but `CompareLibraryScreen.kt:455` has a directly reusable `HorizontalDivider()`-inside-`DropdownMenu` precedent.
- **Screen shell:** `ShareComparisonScreen.kt`/`CreateVideoScreen.kt` both use `Scaffold` + `TopAppBar` + Back, `Column().verticalScroll(rememberScrollState())`, `widthIn(max = 680.dp)` on Expanded.
- **Sensor:** `CompassProvider.kt` — `TYPE_ROTATION_VECTOR`, no permission, exact display-rotation remap table, lifecycle-gated via `DisposableEffect`+`LifecycleEventObserver` calling into ViewModel `updateXActivation()` methods.
- **HQ pipeline:** `ShareImageRenderer`/`ShareRenderConfig` already independently reconstruct a Reference bitmap (`renderHqReference` → `ReferenceRenderer.render()` at HQ dims) and a Capture bitmap (`decodeHqCapture`/`prepareHqCaptureForSbs` → `ImageDecoder` downsample-only) at matching dimensions, then always composite them into one canvas. The two-bitmap-before-compositing structure is the reusable asset.
- **Metadata reading:** direct `org.json.JSONObject` parsing of `metadata.json`, no ORM/parser library. `reference.date` at `json.optJSONObject("reference")?.optString("date")`, `capture.timestampMs` at `json.optJSONObject("capture")?.optLong("timestampMs")`, viewport at `json.optJSONObject("viewport")`.
- **Testability convention:** every ViewModel in this codebase exposes `internal var xRunner: suspend (...) -> Y = { ... real impl ... }` lambdas so unit tests substitute fakes without a real filesystem/network/bitmap stack. This plan follows the same convention for the new network client.
- **No existing precedent for:** HTTP networking, `androidx.browser`, build-time secrets, gesture axis-arbitration, or a bitmap-rendered rounded-rect badge.

---

# 5. Target Architecture

New package roots (all under `app/src/main/java/com/isardomains/sameview/`):

- `ui/wackelbild/` — screen, ViewModel, tilt provider, temp-file manager, date-badge Compose overlay.
- `image/wackelbild/` — two-file HQ/fallback print renderer, dimension resolver, bitmap-side date-badge renderer.
- `net/deinwackelbild/` — API client, DTOs, state machine, locale mapper.

This mirrors the existing package structure (`ui/compare/`, `ui/video/`, `image/`) rather than inventing a new top-level layering. No existing package is renamed or restructured.

---

# 6. Navigation and Screen Architecture

## 6.1 CompareScreen entry point

**Exact change to `CompareScreen.kt`:**
- Add two new parameters to the `CompareScreen` composable signature, following the existing `onShareComparisonImage`/`isShareComparisonAvailable` pair exactly: `onCreateWackelbild: (() -> Unit)? = null`, `isWackelbildAvailable: Boolean = true` (spec §5: item is visible for every regular saved Comparison, never hidden by connectivity/date/original availability — so this flag exists only for the `sessionId != null` gate, not for feature-availability logic; default `true`).
- Inside the existing `if (sessionId != null) { Box { IconButton { DropdownMenu { ... } } } }` block (lines 381-423), insert a `HorizontalDivider()` (import `androidx.compose.material3.HorizontalDivider`, not currently imported in this file) after the "Create video" `DropdownMenuItem` and a third `DropdownMenuItem`:
  ```kotlin
  HorizontalDivider()
  // 3. Wackelbild erstellen
  DropdownMenuItem(
      text = { Text(stringResource(R.string.export_menu_create_wackelbild)) },
      enabled = isWackelbildAvailable,
      onClick = {
          showExportMenu = false
          onCreateWackelbild?.invoke()
      },
      modifier = Modifier.testTag("compare_screen_export_wackelbild_item")
  )
  ```
- No other line in the Export dropdown, the rest of the top bar, or any other CompareScreen behavior is touched. This is strictly additive within the existing `if (sessionId != null)` block.

**Existing Share Image / Create Video behavior preservation:** unaffected — both existing `DropdownMenuItem`s and their `enabled`/`onClick`/testTag wiring are untouched; only new content is appended after them.

**Test updates required:** `CompareScreenTest.kt` (androidTest) gains new tests (menu item presence, divider presence via semantics tree child count or testTag ordering, enabled/click-callback wiring) mirroring the existing `compare_screen_export_share_item`/`compare_screen_export_create_video_item` test patterns. No existing test is modified — this is additive coverage only, per the existing test-suite discipline documented in `COMPARE_FLOW_V1.md §18` (no silently rewriting existing tests to hide regressions).

## 6.2 New Wackelbild destination

- **Package:** `ui/wackelbild/`
- **Route:** `ROUTE_WACKELBILD = "wackelbild"`, `ARG_WACKELBILD_SESSION_ID = "sessionId"`, `ROUTE_WACKELBILD_WITH_ARGS = "$ROUTE_WACKELBILD/{$ARG_WACKELBILD_SESSION_ID}"` — exact naming-convention clone of `ROUTE_SHARE_COMPARISON`.
- **Route builder:** `private fun wackelbildRoute(sessionId: String): String = "$ROUTE_WACKELBILD/${Uri.encode(sessionId)}"` in `MainActivity.kt`, placed next to `shareComparisonRoute`.
- **MainActivity wiring:** in the `CompareScreen(...)` call site, add `onCreateWackelbild = if (sessionId != null) { { navController.navigate(wackelbildRoute(sessionId)) } } else null` next to the existing `onShareComparisonImage` line; add a new `composable(route = ROUTE_WACKELBILD_WITH_ARGS, arguments = listOf(navArgument(ARG_WACKELBILD_SESSION_ID) { type = NavType.StringType }))` block, modeled exactly on the `ROUTE_SHARE_COMPARISON_WITH_ARGS` block.
- **Screen composable:** `WackelbildScreen.kt` — `Scaffold` + `TopAppBar` (title `R.string.wackelbild_screen_title` = "Wackelbild erstellen") + Back `IconButton`, identical shell shape to `ShareComparisonScreen`/`CreateVideoScreen`.
- **ViewModel:** `WackelbildViewModel.kt`, `@HiltViewModel`, `sessionId` from `SavedStateHandle["sessionId"]` — screen/UI-facing state summarized in §6.4 below; the order/network operation state machine is defined fully in §14.3-§14.5.
- **Back behavior:** normal `onBack()` before an active operation; during the busy phases (§14.3), `BackHandler(enabled = ...)` intercepts and shows the confirm/cancel dialog, cloned from `CreateVideoScreen.kt:112-159` (`showCancelDialog` + `AlertDialog` with new `wackelbild_cancel_transfer_*` string keys, §17/§18).
- **Custom Tab return behavior:** no deep link, no `onNewIntent` handling needed — the Custom Tab is a separate Activity Task on top of SameView's Task; returning is a normal `onResume` of the still-alive `WackelbildScreen`/`WackelbildViewModel`. **Generic `ON_RESUME` alone is not treated as a Custom Tab return** — see §14.3's `CustomTabAwaitState` distinction and §19's corrected lifecycle rules. The screen's `ON_RESUME` observer only performs the reset-to-Reference/re-enable-toggle/re-show-CTA behavior when the ViewModel reports `CustomTabAwaitState.LAUNCHED_AWAITING_RETURN` (set immediately before the actual Custom Tab launch, §14.4/§16); an ordinary Home-button-then-reopen resume during preparation/upload leaves the active operation and all screen state completely untouched. No `rememberSaveable` is used for this distinction — the awaiting-return marker lives in the ViewModel alongside the rest of the ephemeral operation state (§14.3), consistent with the rest of this plan's non-persisted state model.
- **Accessibility semantics:** Back button carries an explicit `contentDescription` string resource (following `ShareComparisonScreen`'s pattern rather than `CreateVideoScreen`'s `null`-description icons, since the Wackelbild preview needs its own distinct accessible label separate from the screen title — see §20 for the full accessibility treatment).

## 6.3 Swipe + scroll interaction — resolved without new axis-arbitration code

Gate 1 confirmed no axis-arbitration precedent exists in this codebase (`CompareScreen`'s slider `detectDragGestures` unconditionally consumes every pointer event and its viewport is not nested in a scroll container).

**Resolution — screen structure, not gesture code:**

```
Scaffold(topBar = { TopAppBar(...) }) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
        // Fixed-height, NOT inside verticalScroll — mirrors CompareScreen's own
        // weight(1f) viewport, which likewise sits outside any scroll container.
        WackelbildPreview(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = previewMaxHeight)
        )
        // Everything below IS inside verticalScroll — mirrors ShareComparisonScreen.
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            DateToggleRow(...)
            InteractionHintText(...)
            TransferDisclosureText(...)
            CtaAndStateArea(...)
        }
    }
}
```

The preview `Box` owns 100% of the horizontal-drag gesture region and is never a descendant of a `verticalScroll` container, so there is no pointer-event contention to arbitrate — vertical scroll only ever starts from a touch that begins below the preview. This is a composition of two already-proven patterns (`CompareScreen`'s non-scrolled viewport + `ShareComparisonScreen`'s scrolled form), not new interaction-arbitration logic.

**Compact-height layouts (landscape phone, short window):** if `WackelbildPreview` plus the fixed TopAppBar leaves too little vertical room for the below-preview content to be usable without scrolling far, the existing `Column().verticalScroll()` below the preview already handles this — the user scrolls the toggle/hint/disclosure/CTA area while the preview itself never needs to move or shrink below a usable size. `previewMaxHeight` is capped (e.g., 45% of available height on Compact, matching `CreateVideoScreen`'s general moderation of preview size vs. controls) so the CTA is reachable without excessive scrolling on any tested device class. **No axis arbitration is needed on any layout, including Compact-height, because the preview is structurally never inside the scrollable region.**

## 6.4 UI Layout Plan (by width class)

**Compact:**
- `TopAppBar` (title "Wackelbild erstellen", Back).
- `WackelbildPreview` — fixed-height (not maximized; spec §7 "comfortable surrounding space," "not expanded to the maximum possible screen size"), outside the scroll container (§6.3).
- Below, inside `verticalScroll`: `SettingsSwitchRow` for "Datum anzeigen" (reusing the existing `SettingsCard`/`SettingsSwitchRow` components per `SETTINGS_UX_V1.md`, not wrapped in its own card per spec §9.9's "do not wrap it in a special Options card"), interaction hint text (tilt or swipe copy per §7.5), the external-transfer disclosure sentence (plain `Text`), and the CTA/loading/error area (`Button` → spinner+text → error copy, state-driven from `isBusy`/`userVisibleError`, §14.3).

**Medium / Expanded:**
- Same structure, `widthIn(max = 680.dp)` applied to the scrollable content column (matching `ShareComparisonScreen`/`CreateVideoScreen`'s existing constant exactly — **no new max-width value is introduced**).
- `WackelbildPreview` is NOT stretched to fill the additional available width/height merely because more space exists (spec §44) — its own max-size constraint is independent of and smaller than the 680dp form-width constraint, consistent with spec §7's "comfortable surrounding space" for every width class.
- Portrait Comparison stays visually Portrait / Landscape Comparison stays visually Landscape — enforced structurally since `WackelbildPreview` renders `reference.jpg`/`capture.jpg` with `ContentScale.Fit` inside a `Box` sized from the session's own viewport aspect ratio (same `sessionViewportRatio`-driven sizing pattern already used by `ShareComparisonViewModel`/`CreateVideoViewModel`), never re-cropped.
- Date overlay WYSIWYG: guaranteed by §8.3's shared-geometry design, independent of width class.

**Reusable existing components/constants identified:** `Scaffold`+`TopAppBar` shell pattern, `Column().verticalScroll(rememberScrollState())`, `widthIn(max = 680.dp)` literal, `SettingsCard`/`SettingsSwitchRow` (`SettingsComponents.kt`), `AlertDialog` cancel-confirmation pattern (`CreateVideoScreen.kt`), `sessionViewportRatio`-driven preview sizing pattern (`ShareComparisonViewModel`/`CreateVideoViewModel`), `SameViewAppSurface` color token, `TextMeasurer` usage pattern (`CompareScreen.kt`).

---

# 7. Tilt / Swipe Architecture

## 7.1 Sensor choice

**Decision: `Sensor.TYPE_ROTATION_VECTOR`**, read via a new `TiltProvider` class parallel to (not a subclass of, and not touching) `CompassProvider`.

**Why:** `CompassProvider.kt` already proves this exact sensor works for exactly this class of interaction (device-relative orientation, no permission, foreground-lifecycle-bound, `SensorManager.remapCoordinateSystem` handles display rotation) in this codebase. `TYPE_ACCELEROMETER` was considered and rejected: it would require hand-rolling gravity-vector low-pass filtering and axis interpretation from scratch, duplicating work `TYPE_ROTATION_VECTOR` + `SensorManager.getOrientation` already solves, for no accuracy or battery benefit at `SENSOR_DELAY_UI` rate. No runtime permission is required for either sensor type (motion/composite sensors are not dangerous permissions on Android) — confirmed by the absence of any permission check in `CompassProvider` and the absence of any motion-sensor entry in the manifest.

## 7.2 `TiltProvider` design

`app/src/main/java/com/isardomains/sameview/ui/wackelbild/TiltProvider.kt`:

```kotlin
open class TiltProvider internal constructor(private val sensorManager: SensorManager?) {
    constructor(context: Context) : this(context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)

    open fun isAvailable(): Boolean =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

    open fun startUpdates(displayRotationProvider: () -> Int, onRollChanged: (Float) -> Unit) { ... }
    open fun stopUpdates() { ... }
}
```

Internally, the `SensorEventListener` body is the same rotation-matrix + `remapCoordinateSystem` sequence as `CompassProvider.kt:30-66`, with one difference: after `SensorManager.getOrientation(adjustedMatrix, orientationAngles)`, this class reads `orientationAngles[2]` (roll, the left/right tilt axis) instead of `orientationAngles[0]` (azimuth, compass heading) — the only functional delta from `CompassProvider`. This is written as a **narrow duplicate**, not a shared base class or shared listener extraction: the two providers serve different products (GPS Recreation Guidance vs. DeinWackelbild) with independent lifecycles and independent futures; sharing a base class would couple their evolution for a ~40-line class with one line of difference in what's read from an already-computed array. `CompassProvider.kt` is not modified.

## 7.3 Neutral position, thresholds, hysteresis, swipe/sensor arbitration

- **Neutral position:** captured as `neutralRoll = firstRollReadingAfterActivation` inside `WackelbildViewModel`, not inside `TiltProvider` itself (keeps the provider a pure sensor wrapper, consistent with `CompassProvider`'s separation of raw-sensor delivery from `CameraViewModel`'s guidance logic). Every subsequent reading is compared as `delta = currentRoll - neutralRoll` (angle-wrapped).
- **Direct switch rule:** `delta > +THRESHOLD_DEGREES` → show Capture; `delta < -THRESHOLD_DEGREES` → show Reference; direction sign convention validated against display rotation the same way `CompassProvider` already validates azimuth (both go through the same remap table, so "left tilt" stays intuitive across rotation exactly as spec §8.3 requires).
- **Hysteresis:** switching back requires crossing a *smaller* re-arm band around neutral (e.g., must return within `±REARM_DEGREES` of neutral before the opposite threshold can fire again) — a standard two-band hysteresis state machine (`TiltHysteresisState: NEUTRAL | TOWARD_CAPTURE | TOWARD_REFERENCE`), implemented as a small pure class `TiltHysteresisStateMachine` for unit testability, separate from `TiltProvider` (which stays a raw sensor wrapper) and separate from the ViewModel (which owns *which image is visible*, not the hysteresis math).
- **Exact degree constants (`THRESHOLD_DEGREES`, `REARM_DEGREES`) are explicitly NOT finalized in this plan.** Per the spec's own §8.3 ("Exact thresholds are an implementation/tuning detail and require real-device validation"), the plan proposes placeholder constants (`THRESHOLD_DEGREES = 12f`, `REARM_DEGREES = 6f`) purely so the state machine is testable with concrete numbers from Block 3 onward, explicitly marked `// TODO(real-device tuning): placeholder, see §25 real-device validation` and revisited before release.
- **Swipe/sensor arbitration (§8.5 of the spec):** the ViewModel holds `lastInputSource: InputSource { SENSOR, SWIPE }` and `swipeOverrideActive: Boolean`. On a manual swipe, `swipeOverrideActive = true` and the displayed image is set directly; the tilt-hysteresis state machine keeps running (so it doesn't miss real device movement) but its output is **ignored** by the ViewModel while `swipeOverrideActive` is true. `swipeOverrideActive` is cleared the next time the hysteresis state machine reports a **state transition** relative to its state *at the moment the override began* (i.e., "a new sufficiently clear tilt movement," per spec §8.5) — not merely a new reading. This is genuinely new state-machine logic (Gate 1 confirmed no precedent exists) but is fully unit-testable in isolation from Compose/sensors since it operates purely on a stream of hysteresis-state values and swipe events.

## 7.4 Lifecycle registration

Cloned pattern from `CameraViewModel`'s `updateSensorActivation()`/`onCameraScreenActive()`/`onCameraScreenInactive()`: `WackelbildScreen` wires a `DisposableEffect(lifecycleOwner)` + `LifecycleEventObserver` on `ON_RESUME`/`ON_PAUSE` calling `viewModel.onScreenActive()`/`viewModel.onScreenInactive()`, which start/stop `TiltProvider` (gated only on `tiltProvider.isAvailable()` — no permission gate needed, unlike the GPS case's multi-condition gate). A `DisposableEffect(Unit) { onDispose { viewModel.onScreenLeft() } }` performs full release when the screen leaves composition (matches spec §8.8's "fully release when leaving the screen").

## 7.5 Sensor-unavailable behavior

`WackelbildViewModel.isSensorAvailable: StateFlow<Boolean>` is set once from `tiltProvider.isAvailable()` at init. The screen switches the hint text between `R.string.wackelbild_hint_tilt_title` ("Handy leicht neigen") and `R.string.wackelbild_hint_swipe_title` ("Über das Bild wischen") based on this flag, per spec §8.6. No hardware/error message is ever shown (spec requirement, directly enforced by this being a plain boolean UI branch, not an error state).

## 7.6 No-runtime-permission verification

Verified by direct manifest inspection (§2) — no motion-sensor permission exists in the app today and none is added by this feature; `TiltProvider.isAvailable()`/`startUpdates()` never call any permission-check API, mirroring `CompassProvider`'s existing behavior exactly. A unit test (`TiltProviderTest`) asserts `startUpdates()` calls `SensorManager.registerListener` directly with no intervening permission check, using a mocked `SensorManager` (`mockito-kotlin`, already a project test dependency).

---

# 8. Date Overlay Architecture

## 8.1 Availability, precision, formatting

- **Detection:** `WackelbildViewModel` reads `metadata.json` via the same direct `JSONObject` pattern as `ShareComparisonViewModel.readMetadata()` (`json.optJSONObject("reference")?.optString("date")`), reusing the string-length precision rule already established in `docs/SESSION_METADATA_V1.md §7.3` and mirrored in `app/src/main/java/com/isardomains/sameview/ui/compare/CompareLabelLogic.kt` (confirmed present in this repo at that exact path): length 4 → year, length 7 → year-month, length 10 → full date. No new precision-parsing logic is invented; this plan reuses `CompareLabelLogic`'s existing precision-detection helpers directly where their signatures allow, and otherwise duplicates the same trivial length-based `when` (a 3-line pattern, not worth extracting) inside a new small pure function `DateBadgeFormatter.formatReferenceDate(rawDate: String, locale: Locale): String` and `DateBadgeFormatter.formatCaptureDate(timestampMs: Long, locale: Locale): String`, both locale-aware via `java.text.DateFormat`/`SimpleDateFormat` consistent with the `Locale.getDefault()`-injected-as-parameter convention already used in `ShareComparisonViewModel.computeDateLine`/`CreateVideoViewModel`.
- **Capture date:** `capture.timestampMs`, read identically to `ShareComparisonViewModel.readMetadata()` (`json.optJSONObject("capture")?.optLong("timestampMs", 0L)`).
- **Unknown components are never invented:** the formatter only ever renders the precision actually present in the stored string (spec §9.5) — this falls directly out of using the string length to select the format pattern, with no fallback that guesses a missing month/day.

## 8.2 State ownership and lifecycle

- `WackelbildViewModel` owns `dateOverlayEnabled: MutableStateFlow<Boolean>(false)` (default OFF, spec §9.1) and `isReferenceDateUsable: StateFlow<Boolean>` (derived from metadata load).
- **No DataStore. No session metadata write.** The toggle is pure in-memory ViewModel state, exactly like `ShareComparisonViewModel`'s `_titleDateEnabled`/`_locationEnabled` (plain `MutableStateFlow`, not `SavedStateHandle`-backed) — this is the established precedent for "configurable-but-non-persistent screen option" in this codebase.
- **Disabled-state UI:** `SettingsSwitchRow(enabled = isReferenceDateUsable, supportingText = if (!isReferenceDateUsable) stringResource(R.string.wackelbild_date_unavailable_hint) else null, ...)` — direct reuse of the existing `SettingsSwitchRow` component and its established enabled/supporting-text pattern (per `SETTINGS_UX_V1.md`).
- **Custom Tab return (same screen visit):** because `dateOverlayEnabled` lives in a plain `MutableStateFlow` in a ViewModel that survives the Custom Tab Activity's foreground time (process stays alive — Custom Tab launches as a new Task, does not finish SameView's Activity), the value is naturally retained without any special-case code, matching spec §9.2's "if the user returns from the Custom Tab to the still-existing screen, the toggle retains its current value."
- **Process recreation:** since the field is not `SavedStateHandle`-backed, a process-death recreation resets it to `false` — matching spec §9.2's "a new visit to the Wackelbild screen starts with the toggle OFF" (process recreation is product-indistinguishable from "a new visit" per spec §24).
- **Ordering-state freeze (§9.10):** `WackelbildViewModel` snapshots the current `dateOverlayEnabled` value into the operation's immutable order object the instant `onOrderPressed()` is called; the live `dateOverlayEnabled` StateFlow is separately set to a disabled-for-editing UI state (`isDateToggleEditable: StateFlow<Boolean>`) during the busy phases (§14.3), re-enabled on final error or Custom Tab return.

## 8.3 Date badge rendering — one shared geometry model, two renderers

Two renderers are required (live Compose overlay vs. bitmap/JPEG output) but must be visually consistent (WYSIWYG per spec §9.3). Rather than one renderer trying to serve both (Compose and `android.graphics.Canvas` are different drawing APIs), the plan defines **one shared pure-geometry module** consumed by both:

**`DateBadgeGeometry.kt`** (`ui/wackelbild/`, no Android drawing APIs, pure math — testable as plain JVM unit tests):
```kotlin
data class DateBadgeLayout(val boxLeft: Float, val boxTop: Float, val boxRight: Float, val boxBottom: Float,
                            val textBaselineX: Float, val textBaselineY: Float, val cornerRadius: Float,
                            val textSizePx: Float)

fun computeDateBadgeLayout(canvasW: Float, canvasH: Float, text: String, measureTextWidth: (String, Float) -> Float): DateBadgeLayout
```
- **Position:** bottom-right, per spec §9.6. Margin fractions of `min(canvasW, canvasH)` — the same proportional-sizing family already used by `CaptionRenderer`/`ShareRenderConfig` (`DATE_SIZE_FRACTION` etc.), so behavior at arbitrary preview vs. HQ-print resolutions stays consistent by construction.
- **Text size:** proportional fraction of `min(canvasW, canvasH)`, same family as `CaptionRenderer`'s `dateSize = baseDim * 0.045f` (existing token, directly reused as the starting fraction since the visual weight target — a bold, legible date label — is the same use case).
- **Corner radius — concrete decision for this plan (per Gate 2's explicit deferral to the implementation-plan/design-resolution step, which is this section):** `cornerRadius = boxHeight * 0.25f` (proportional to the badge's own height, not to canvas size). Rationale: the nearest existing same-screen-family constant is `CompareViewportCornerRadius = 8.dp` (`CompareScreen.kt:154`), but that value is tuned for a large viewport-frame radius at a fixed dp scale, not for a small proportionally-scaled badge that must render correctly at both a ~400dp preview and a multi-thousand-pixel print bitmap — a height-relative fraction (a well-established "rounded rectangle, not a stadium/pill" ratio at 20-30% of box height) generalizes correctly across both contexts the way `CaptionRenderer`'s fraction-based sizing already does for text. **This is a proposed default, not a final value** — it must be visually validated on a real device during Block 4 (§25) and is trivially tunable since it is a single named constant in `DateBadgeGeometry.kt`.
- **Styling per spec §9.7 (unchanged, explicitly reused tokens):** background `SameViewAppSurface` = `0xFF17202F` (existing Compose color token, `ui/theme/Color.kt:9`); text `white`; no shadow; no outline; internal padding proportional to text size (same padding-relative-to-text convention as `CaptionRenderer`).

**`DateBadgeOverlay.kt`** (`ui/wackelbild/`, Compose) — draws the live preview overlay using `Modifier.drawWithContent` / `Canvas` in Compose, calling `computeDateBadgeLayout()` with the Compose `TextMeasurer` (already imported/used elsewhere in this codebase, e.g. `CompareScreen.kt:104-106`) as `measureTextWidth`, then drawing a `RoundedCornerShape`-equivalent rect (`drawRoundRect`) filled with `SameViewAppSurface`, and the text in white via `drawText`.

**`DateBadgeRenderer.kt`** (`image/wackelbild/`, `android.graphics.Canvas`-based) — draws into the temporary print JPEGs. Calls the same `computeDateBadgeLayout()` with an `android.graphics.Paint.measureText`-based `measureTextWidth` lambda, then `canvas.drawRoundRect(...)` (a genuinely new call in this renderer family — Gate 1 confirmed no existing bitmap-renderer code in this repo currently draws a rounded rect; `Canvas.drawRoundRect` is a standard, low-risk platform API) filled with `Paint().apply { color = 0xFF17202F.toInt() }`, then the text via `Paint(ANTI_ALIAS_FLAG)` with `color = Color.WHITE`, `setShadowLayer` **not** called (spec: no shadow — this is the one deliberate divergence from `CaptionRenderer.makePaint()`, which does use a shadow; `CaptionRenderer.kt` is not modified).

Both renderers consume the identical `DateBadgeLayout` math, so preview and print stay visually consistent (spec §9.3/§20) by construction rather than by parallel-maintained constants.

## 8.4 No mutation of session/original files

Both renderers only ever draw onto freshly-allocated bitmaps (the live Compose preview surface, or the new temporary print bitmaps created in §9) — neither ever opens `reference.jpg`/`capture.jpg`/any `*-original.*` file in a writable mode. This is enforced structurally: `DateBadgeRenderer.kt` takes a `Bitmap` parameter and a `Canvas` already bound to that bitmap; it has no file-path parameter and therefore cannot write to a session file even by mistake.

---

# 9. HQ Print Image Architecture

This is the most consequential section. All claims below are grounded in the exact `ShareImageRenderer.kt`/`ShareRenderConfig.kt` source read in full for this plan (§2).

## 9.1 Traced existing functions (exact current code)

| Concern | Existing function | File:lines | Visibility |
|---|---|---|---|
| HQ capture source detection | `hasHqCaptureSource(sessionDir)`, `resolveHqCaptureFile(sessionDir)` | `ShareImageRenderer.kt:172-190` | `internal fun` on class instance |
| Capture HQ decode (Slider-shape, full comp-area) | `decodeHqCapture(file, targetW, targetH)` | `ShareImageRenderer.kt:203-216` | `private fun` |
| Capture HQ decode (slot-shape, center-crop fill) | `prepareHqCaptureForSbs(file, slotW, slotH)` | `ShareImageRenderer.kt:235-278` | `private fun` |
| Reference HQ re-render | `renderHqReference(sessionDir, compW, compH, overlayParams)` | `ShareImageRenderer.kt:293-316` | `private fun` |
| Reference HQ fallback | `decodeReferenceFallback(sessionDir)` | `ShareImageRenderer.kt:319-321` | `private fun` |
| Viewport read | `readSessionViewport(sessionDir)` | `ShareImageRenderer.kt:329-344` | `internal fun` on class instance |
| Overlay params read | `readOverlayParams(sessionDir)` | `ShareRenderConfig.kt:121-137` | top-level `internal fun` |
| EXIF-oriented dims read | `readExifOrientedDimensions(file)` | `ShareRenderConfig.kt:91-108` | top-level `internal fun` |
| Fill-crop draw | `drawBitmapFill(canvas, bitmap, rectF)` | `SliderRenderStrategy.kt:184-196` | `internal fun` |
| No-upscale HQ dimension math | inline in `computeCanvasDimensions` | `ShareRenderConfig.kt:177-187` | top-level `internal fun` (whole function is Share-Image-specific, not reused as-is — see §10) |
| Underlying HQ reconstruction primitive | `ReferenceRenderer.render(...)` | `ReferenceRenderer.kt` (whole file) | `object`, public |

**Confirmed by direct code reading:** both `refBitmap` and `capBitmap` (`ShareImageRenderer.kt:74-114`) are prepared as **two fully independent `Bitmap` objects at identical target dimensions** before being handed to `SliderRenderStrategy`/`SideBySideRenderStrategy` for compositing, and both are `recycle()`d in a `finally` block (lines 135-139) immediately after compositing. No code path anywhere writes either bitmap out standalone — this is the one missing piece this plan must add.

## 9.2 Reuse strategy — visibility widening, not extraction, not duplication

**Decision:** widen exactly four `private fun` methods in `ShareImageRenderer.kt` to `internal fun`: `decodeHqCapture`, `prepareHqCaptureForSbs`, `renderHqReference`, `decodeReferenceFallback`. **No other change to `ShareImageRenderer.kt`** — no logic, no call sites, no signatures beyond the visibility keyword are touched.

**Why this over the alternatives, explicitly:**
- **Reuse unchanged (impossible as-is):** these four methods are `private`, so a new class cannot call them without a visibility change of some kind.
- **Extract to a shared helper class:** rejected. Pulling these methods out of `ShareImageRenderer` into a new shared class would touch the Share Image feature's own call sites too (`ShareImageRenderer.kt:79-114` would need to call the extracted class instead of its own methods), which is exactly the kind of "refactor shared image code merely for cleanliness" this plan should avoid, and it multiplies the surface area that could regress the existing, already-shipped, tested Share Image feature.
- **Duplicate narrowly (copy the ~120 lines of bitmap/crop-geometry logic into the new class):** rejected as the primary choice. This is real, non-trivial geometry code (`prepareHqCaptureForSbs`'s fill-scale math in particular) that could silently drift from the original if either copy is later bugfixed without updating the other — a correctness risk with no offsetting benefit, since the alternative (visibility widening) carries near-zero risk of its own.
- **Visibility widening (chosen):** a four-keyword change (`private` → `internal`) with **zero logic change and zero existing call-site change**. It is same-module (`app`), so `com.isardomains.sameview.image.wackelbild.WackelbildPrintRenderer` can call `ShareImageRenderer().decodeHqCapture(...)` etc. directly as an instance method, exactly as the Wackelbild renderer needs. Every existing Share Image unit test (`ShareRenderConfigTest`, 15/15 per `IMPLEMENTATION_NOTES.md`) and instrumentation test (`ShareImageRendererInstrumentedTest`, `ShareComparisonScreenTest`) continues to pass unmodified, because the code paths they exercise are byte-identical — only their *reachability from outside the class* changes, which no existing test asserts against (visibility is not itself behavior).

This is flagged explicitly as touching `ShareImageRenderer.kt` — a high-risk file per §21 — but the change itself is classified **low risk** given the above.

`readOverlayParams`, `readExifOrientedDimensions` (`ShareRenderConfig.kt`), `resolveHqCaptureFile`, `hasHqCaptureSource`, `readSessionViewport` (`ShareImageRenderer.kt`) are already `internal` — **reused unchanged, no edit required.**

`ReferenceRenderer.render(...)` is already public — **reused unchanged, no edit required.**

## 9.3 New two-file pipeline — `WackelbildPrintRenderer`

`app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildPrintRenderer.kt`:

```kotlin
class WackelbildPrintRenderer(private val shareRenderer: ShareImageRenderer = ShareImageRenderer()) {
    suspend fun renderPrintPair(
        sessionDir: File,
        dims: WackelbildTargetDimensions,   // from WackelbildDimensionResolver, §10
        dateOverlay: WackelbildDateOverlay?  // null = no overlay
    ): WackelbildPrintPair   // { referenceFile: File, captureFile: File } — two independent temp JPEGs
}
```

**Reference side (independent of Capture):** calls `shareRenderer.readOverlayParams(sessionDir)`; if non-null, calls the now-`internal` `shareRenderer.renderHqReference(sessionDir, dims.width, dims.height, overlayParams)` (identical function, identical math, identical `ReferenceRenderer.render()` call underneath) — this reproduces `reference.jpg`'s exact visible composition at the new target resolution because, per `SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md §5.3` (verified during Gate 1), overlay offsets are stored as normalized viewport fractions, so uniform scaling of the target width/height preserves the same relative source-pixel mapping regardless of resolution. `dims` here is the pair-level target computed by `WackelbildDimensionResolver` (§10), which explicitly caps this side's own scale so the reference source is never magnified beyond its own genuine pixel density (§10.2). If `overlayParams` is null or `renderHqReference` throws, falls back to decoding `reference.jpg` directly (same fallback shape as the existing pipeline) — this is the HQ-unavailable branch of §9.5 below, not the full-fallback branch.

**Capture side (independent of Reference):** if a HQ capture source is available (`shareRenderer.hasHqCaptureSource(sessionDir)`), calls the now-`internal` `shareRenderer.decodeHqCapture(captureOriginalFile, dims.width, dims.height)` — the exact same center-crop-free, EXIF-oriented, downsample-only decode used by Share Image's Slider style. Falls back to decoding `capture.jpg` on any failure.

**Why crop parity holds — Reference and Capture explained separately, both deterministic (Correction B: no runtime-test-driven algorithm choice):**

- **Reference:** identical algorithm to the already-shipped, already-tested HQ Share Image reference reconstruction (`renderHqReference`/`ReferenceRenderer.render()`), called with a different target resolution. The mathematical proof already exists and was independently verified in Gate 1 (`SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md §5.3`); this plan does not re-derive it, only re-invokes the same code.

- **Capture — re-inspected from the actual capture pipeline, not assumed:**
  1. `SessionStorage.saveSession()` (called from `CameraViewModel.onPhotoCaptured`, `CameraViewModel.kt:876-883`) writes `capture.jpg` (`writeCapture()`, `SessionStorage.kt:635-643`) from the **exact same in-memory `Bitmap`** (`corrected`, the post-rotation-correction capture) that is also passed to `MediaStoreWriter.save()` (`CameraViewModel.kt:862`) — whose committed MediaStore file is later byte-copied into `capture-original.jpg` (`writeCaptureOriginal()`, `SessionStorage.kt:1139-1152`). `capture.jpg` and `capture-original.jpg` are therefore two different JPEG-quality encodings (90 vs. 95, `SessionStorage.kt:80-81`) of the **identical pixel content and identical native aspect ratio** — no crop happens between them anywhere in `SessionStorage`.
  2. That same `corrected` bitmap is itself already cropped by CameraX, before this app ever sees it, to a **fixed** aspect ratio: `CameraScreen.kt:718-721` builds `ImageCapture.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9)`, and `CameraScreen.kt:759-766` binds both `Preview` and `imageCapture` into one `UseCaseGroup` with a shared `ViewPort.Builder(Rational(16,9) or Rational(9,16), rotation)` — CameraX's documented mechanism for guaranteeing every use case in the group is cropped to the same visible field of view as the live preview.
  3. The stored `metadata.json` viewport (`snapshot.viewportWidth`/`viewportHeight`, frozen from `currentState.viewportWidth`/`viewportHeight`) is *itself* deliberately computed to match that exact same ratio: `CameraScreen.kt:702-703` sets `effectiveHeight = (w * 9f / 16f).toInt()` for portrait (with the equivalent 16:9 computation for landscape) before calling `onReferenceViewportChanged(...)`.
  4. Therefore `capture.jpg`/`capture-original.jpg`'s native pixel aspect ratio is **guaranteed equal** to the stored `viewport.width : viewport.height` ratio for every session produced by the current capture pipeline — not by luck, but because both are deliberately derived from the same fixed 16:9/9:16 family. This is also the only architecture under which the already-shipped, heavily-tested Compare feature's own "what you saw is what you get" guarantee (`COMPARE_SESSION_RENDERING_V1.md`'s Core Principle) can hold at all: if the ratios ever diverged, `reference.jpg` (rendered at exactly `viewportWidth × viewportHeight`) and `capture.jpg` (CameraX's native ratio) would already visibly mismatch under `ContentScale.Fit` in today's shipped `CompareScreen` — since that has not been an observed defect, this is strong corroborating evidence, not merely a theoretical argument.
  5. Given this guarantee, `decodeHqCapture`'s direct `ImageDecoder.setTargetSize(dims.width, dims.height)` downsample (where `dims` shares the exact viewport aspect ratio by construction, §10.2) is the **correct, deterministic** algorithm — a pure proportional resize, never a stretch, because the source and target ratios are architecturally guaranteed equal. `prepareHqCaptureForSbs`'s fill-crop logic is not needed here and is not adapted for this feature: that logic exists specifically to solve the Side-by-side style's *different* problem (a target ratio that is deliberately *not* the viewport ratio, because each SxS slot is only half-width) — a problem this feature does not have, since Wackelbild's target is always the full viewport ratio, exactly like the Slider style `decodeHqCapture` already serves correctly today.

  **Defensive guard (not a runtime algorithm choice — a fail-safe, since no session from the current pipeline can trigger it):** before decoding, `WackelbildPrintRenderer` compares `readExifOrientedDimensions(captureOriginalFile)`'s aspect ratio against the target `dims` aspect ratio (both already known before the decode call). If they match within a small floating-point/rounding tolerance (the expected, guaranteed case), it proceeds with `decodeHqCapture` as above. If they do **not** match — which would only be possible for a session this feature was never designed against (e.g., a hypothetically corrupted or externally-tampered file, or a future capture-pipeline change that breaks the ViewPort/viewport-measurement guarantee) — `WackelbildPrintRenderer` does not attempt a crop or a stretch at all; it treats this exactly as an HQ-reconstruction failure and routes into the already-specified §9.5 case 2 fallback (the approved "Originalqualität nicht verfügbar" warning, falling back to `capture.jpg` directly). This removes the previous "use `decodeHqCapture()` unless a future test proves it wrong" framing entirely: the algorithm is chosen deterministically from repository evidence, and the only thing a mismatch can ever do is trigger the already-approved fallback UX, never silently produce wrong crop content.

## 9.4 Bitmap lifetime / recycling

Same discipline as `ShareImageRenderer.kt:74-139`: both HQ bitmaps are held in `try { ... } finally { bitmap?.recycle() }` blocks. Unlike Share Image's compositing pipeline, this pipeline's pair-level size algorithm (§10.3) genuinely requires both decoded bitmaps to be held **simultaneously** at each resolution attempt — the pair-level ≤20 MiB decision cannot be made from one side alone. Peak memory is nonetheless kept bounded: only one bitmap-pair generation is ever alive at a time (both bitmaps for the current dimension attempt are recycled together before any dimension step-down produces the next pair, §10.3), and each candidate JPEG is encoded straight to a temporary file rather than an in-memory buffer (§10.3), avoiding the additional overhead a duplicated encoded-byte buffer would add on top of the two live bitmaps. This pipeline never needs Share Image's additional shared output canvas allocation, so its peak is expected to stay below the existing Slider-HQ ~105MB figure documented in `SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md §5.6`, even while holding both bitmaps at once.

## 9.5 Fallback

Three-state detection, matching spec §19 exactly:

1. **HQ originals usable:** `resolveHqCaptureFile(sessionDir) != null` AND `readOverlayParams(sessionDir) != null` AND `readExifOrientedDimensions()` succeeds for both `capture-original.jpg` and `reference-original.jpg` (both needed by `WackelbildDimensionResolver`, §10.2) AND both HQ decode calls succeed AND the pair-level size algorithm (§10.3) produces a compliant pair within its bound → proceed with HQ pair, no warning.
2. **HQ reconstruction failed but `reference.jpg`/`capture.jpg` usable:** any of — an HQ decode call fails, `WackelbildDimensionResolver` throws (degenerate genuine-resolution floor, §10.2), the capture-side defensive aspect-ratio guard detects a mismatch (§9.3), or the pair-level size algorithm exhausts its bound (§10.3) — while `BitmapFactory.decodeFile(reference.jpg)`/`decodeFile(capture.jpg)` both still succeed → this is the state that triggers spec §19's "Originalqualität nicht verfügbar" warning. **Detected once, eagerly, when the user presses "Bestelle dein Wackelbild"** (not merely when the screen opens — spec §19: "Do not show this warning merely when opening the Wackelbild screen"), by attempting the HQ path first and catching the failure before showing the warning dialog, exactly per spec §19's ordering.
3. **Neither valid:** even `reference.jpg`/`capture.jpg` fail to decode → permanent preparation failure state (§18.3 of spec, "Wackelbild kann nicht erstellt werden"), no retry offered.

The fallback branch still creates **new** temporary JPEGs from `reference.jpg`/`capture.jpg` (never reuses/copies the persisted files directly, and never touches them) — same "always re-encode via `Bitmap.compress`, never byte-copy" discipline as §12.

---

# 10. Common Resolution / API Limit Strategy

## 10.1 Why `computeCanvasDimensions` is not reused directly

`ShareRenderConfig.computeCanvasDimensions` (traced in full, §9.1) bakes in Share-Image-specific concerns not applicable here: caption-area height participation, Side-by-side compH halving, and a hard `MAX_HQ_LONGEST_EDGE = 3840` cap tuned for a shareable social image, not a print product. Reusing it as-is would silently impose the wrong resolution ceiling (per Gate 2's spec correction: "API limits are upper safety constraints, not target resolutions" — and 3840 is not even the DeinWackelbild API's limit, it's an unrelated feature's limit). A new, small, purpose-built function is required.

## 10.2 `WackelbildDimensionResolver` (Correction A — both sources)

`app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildDimensionResolver.kt`:

```kotlin
data class WackelbildTargetDimensions(val width: Int, val height: Int)

object WackelbildDimensionResolver {
    fun resolve(
        viewportW: Int, viewportH: Int,
        captureOriginalDims: Pair<Int, Int>,        // capture-original.jpg, EXIF-oriented pixel dims
        referenceOriginalDims: Pair<Int, Int>,       // reference-original.jpg, EXIF-oriented pixel dims
        overlayScale: Float,
        displayMode: ReferenceImageDisplayMode,
        maxSidePx: Int = 16_000,
        maxMegapixels: Long = 80_000_000L
    ): WackelbildTargetDimensions   // throws WackelbildHqUnusableException on the degenerate floor below
}
```

This function is only ever called once both HQ prerequisites are confirmed present (§9.5 case 1's gating) — the fallback path (§9.5 case 2/3) never calls it, since `reference.jpg`/`capture.jpg` are already exactly at viewport resolution with nothing left to resolve.

**Algorithm — the weaker of *both* sources determines the ceiling, not capture alone:**

```
// Capture side: capture-original.jpg's native ratio is architecturally guaranteed equal to the
// viewport ratio (proven in §9.3 Correction B from the CameraX ViewPort/UseCaseGroup wiring), so
// this is a straightforward "how much bigger is the real capture than the viewport" factor.
captureScale = min(captureOriginalDims.first / viewportW, captureOriginalDims.second / viewportH)

// Reference side: derived from ReferenceRenderer's OWN compositing math (ReferenceRenderer.kt),
// not from reference-original.jpg's raw pixel dimensions alone. fillOrFitScale mirrors exactly the
// scale ReferenceRenderer applies to the source bitmap when rendering at the ORIGINAL (k=1) viewport
// size — this is the "actually visible source area" the correction requires, expressed as a density:
//   COMPARE_WITH_PREVIEW -> fillOrFitScale = max(viewportW/refW, viewportH/refH)   [[Crop/Fill semantics]]
//   SHOW_FULL_IMAGE      -> fillOrFitScale = min(viewportW/refW, viewportH/refH)   [[Fit semantics]]
effectiveScale = fillOrFitScale * overlayScale   // source-pixel-to-output-pixel density at k=1
referenceScale = 1f / effectiveScale
// NOT coerced to >= 1: if the ORIGINAL k=1 render already required upscaling the reference source
// (effectiveScale > 1 — e.g. a low-resolution reference stretched to fill the screen), the genuinely
// available resolution is smaller than the original viewport, and this plan allows that explicitly
// rather than upscaling further (per Correction A's explicit requirement).

apiSideScale = maxSidePx / max(viewportW, viewportH)
apiMpScale   = sqrt(maxMegapixels / (viewportW.toLong() * viewportH.toLong()))

commonScale  = minOf(captureScale, referenceScale, apiSideScale, apiMpScale)
// No .coerceAtLeast(1f) anywhere in this formula — the weaker of the two real sources genuinely
// determines the ceiling, and API maxima are upper bounds only (Gate 2's spec correction).

// Sanity floor only — not a target, not an upscale. If commonScale would produce a degenerate,
// unusably tiny image, this is treated as HQ-unusable and routed into the §9.5 case 2 fallback
// rather than emitting a near-unusable print image:
if (viewportW * commonScale < MIN_OUTPUT_SIDE_PX || viewportH * commonScale < MIN_OUTPUT_SIDE_PX) {
    throw WackelbildHqUnusableException()   // caught by the pipeline; routes to §9.5 fallback
}

width  = makeEven(round(viewportW * commonScale))
height = makeEven(round(viewportH * commonScale))
```

`captureScale`'s two per-axis ratios collapse to (approximately) the same value precisely because the capture side's aspect-ratio-equals-viewport guarantee (§9.3) holds — this is now an explicitly justified fact, not an assumption, unlike the original draft's `scaleByWidth`/`scaleByHeight` pair.

`makeEven` is a 1-line duplicate of the existing private top-level `makeEven` in `ShareRenderConfig.kt` — deliberately **duplicated trivially** rather than widened to `internal`, since it is a single, self-contained, unlikely-to-change one-liner where duplication carries no realistic drift risk, unlike the ~120-line HQ decode functions in §9.2.

## 10.3 Pair-level ≤20 MiB enforcement — print-quality-first, bounded algorithm (Correction E)

Both output files must independently satisfy ≤20 MiB, but the two are always resized **together**, never independently — they must always end at identical pixel dimensions (already guaranteed by §10.2's single `WackelbildTargetDimensions`; this section only adds the size-driven downgrade path on top of it).

```
qualityLadder = listOf(92, 85)          // two high-quality steps only — see rationale below
maxDimensionSteps = 3                    // see rationale below
dimensionStepFactor = 0.85f

var currentDims = resolvedDims            // from §10.2
for (dimStep in 0..maxDimensionSteps) {
    val refBitmap = renderReferenceAt(currentDims)   // §9.3 reference side
    val capBitmap = decodeCaptureAt(currentDims)      // §9.3 capture side
    try {
        for (quality in qualityLadder) {
            val refTemp = encodeToTempFile(refBitmap, quality)   // Bitmap.compress() straight to a
            val capTemp = encodeToTempFile(capBitmap, quality)   // File via FileOutputStream — no
                                                                   // in-memory ByteArray buffer
            if (refTemp.length() <= TWENTY_MIB && capTemp.length() <= TWENTY_MIB) {
                return WackelbildPrintPair(refTemp, capTemp)      // success — both files already on disk
            }
            refTemp.delete(); capTemp.delete()                    // this attempt's files did not qualify
        }
    } finally {
        refBitmap.recycle(); capBitmap.recycle()
    }
    // Both quality steps exceeded 20 MiB at this resolution — shrink BOTH dimensions together and retry.
    currentDims = WackelbildTargetDimensions(
        makeEven(round(currentDims.width * dimensionStepFactor)),
        makeEven(round(currentDims.height * dimensionStepFactor))
    )
}
// Bounded — give up after 2 quality steps x 4 dimension levels (original + 3 reductions).
// This is a preparation failure (§9.5 case 3 / spec §18.3), not an infinite loop.
throw WackelbildPreparationException("Cannot produce a compliant pair within size limits")
```

**Why pair-level, not per-image:** a dimension reduction is only ever meaningful if applied to both images identically — reducing just the oversized one would violate the identical-pixel-dimensions requirement (spec §18/§48-5) and would itself require re-deriving `WackelbildTargetDimensions` from scratch. The loop above always re-renders/re-decodes **both** bitmaps together at each dimension level, and only advances past the quality ladder for the whole pair, never for one side alone.

**Why print quality is prioritized over dimension:** a physical print product should sacrifice excess pixel dimensions before visible JPEG compression artifacts — the ladder therefore tries only two genuinely high-quality settings (`92`, the existing repo convention already used by `ShareImageRenderer.JPEG_QUALITY`, and `85`, still a visually near-lossless setting for print output) before ever reducing dimensions, rather than descending toward a visibly-degraded quality floor. Given Gate 2's already-established finding that real camera-resolution sources are typically far below the 16,000px/80MP ceiling, hitting the dimension-reduction branch at all is expected to be rare — the two-step quality ladder exists mainly to absorb the residual gap for the occasional large source, not as the primary size-control lever.

**Why the bound (2 × 4 = 8 pair-level attempts) is safe:** `dimensionStepFactor = 0.85f` applied up to 3 times shrinks the linear dimension to `0.85³ ≈ 0.614` (about 61% of the original side length, ≈38% of the original pixel area) by the final attempt — a substantial reduction relative to any file that still doesn't fit within 20 MiB after that is legitimately treated as a preparation failure rather than continuing indefinitely. 8 total attempts (at most) keeps the operation's worst-case latency bounded and predictable, consistent with the single, non-percentage "Wackelbild wird vorbereitet …" spinner (spec §12) not needing to communicate an open-ended process.

**Memory behavior:** each candidate JPEG is encoded directly to a temporary `File` via `Bitmap.compress()` writing into a `FileOutputStream` (the same temp-file target the pipeline already needs for the eventual output, §11) and checked via `File.length()` — no intermediate in-memory `ByteArray` of the full encoded JPEG is ever held for a size check. The only unavoidable in-memory cost is the two decoded `Bitmap` objects themselves (both are needed simultaneously to determine whether the *pair* fits, which is inherent to a pair-level size decision, not an avoidable inefficiency, §9.4); these are recycled together before any dimension step-down, keeping peak memory bounded to one bitmap-pair generation at a time.

**Both output files always end at identical `width × height`** (spec §18/§48-5) — enforced structurally since both bitmaps are always decoded/redecoded from the same `WackelbildTargetDimensions` value at every step, and every dimension step-down applies to both simultaneously, never independently.

---

# 11. Temporary File Architecture

- **Directory:** `context.cacheDir/wackelbild/<operationId>/` where `operationId` is a fresh UUID per handoff attempt (not the session ID — avoids any accidental cross-referencing of session identifiers in a temp-file name, consistent with spec §27's data-minimization intent even though this is purely local). `cacheDir` is chosen over `filesDir` because: it is app-private, OS-reclaimable, and — critically — **not** subject to the existing `sessions/`/`branding/`-specific entries in `backup_rules.xml`/`data_extraction_rules.xml` (per `RELEASE_HARDENING_AUDIT_V2.md`, those exclusions target `filesDir` subpaths); `cacheDir` content is excluded from Auto Backup by Android platform default, requiring no new backup-rules entry at all.
- **File naming:** `image_one.jpg`, `image_two.jpg` inside the per-operation subdirectory — deliberately generic (not "reference"/"capture") since the API's own slot naming (`one`/`two`) is the contract surface, and generic names avoid leaking session semantics into a filename that could theoretically appear in a stack trace or file-listing log.
- **Ownership/lifetime:** owned exclusively by `WackelbildTempFileManager` (`ui/wackelbild/`), injected into `WackelbildViewModel` via the same constructor-injectable-lambda-or-class convention used elsewhere.
- **Cleanup on success:** immediately after the API confirms both uploads accepted (spec: temp files "must be deleted after successful upload" — not after the Custom Tab closes, since the images have already served their purpose once uploaded).
- **Cleanup on cancel:** inside the Back-confirmation cancel path (§18), synchronously before returning to `CompareScreen`.
- **Cleanup on final error:** inside a `finally`/`NonCancellable`-wrapped block around the whole prepare→upload sequence, mirroring the coroutine-cancellation-safe cleanup pattern already documented for `VideoExportPipeline` in `IMPLEMENTATION_NOTES.md` ("orchestrates decode → render → encode → commit; coroutine-cancellation-safe cleanup via `NonCancellable`").
- **Cleanup on screen disposal:** `WackelbildViewModel.onCleared()` best-effort deletes the current operation's subdirectory if one exists (mirrors `CompassProvider.stopUpdates()` being called from `CameraViewModel.onCleared()` as a teardown safety net).
- **Process-loss leftovers:** `WackelbildTempFileManager` performs a **sweep-on-screen-entry**: at `WackelbildViewModel.init`, delete every existing subdirectory under `cacheDir/wackelbild/` — i.e., simply wipe the whole `cacheDir/wackelbild/` directory at the start of every screen visit, before creating the new operation's subdirectory. This is safe because temp files are never meant to survive a screen visit anyway (spec §22), and unconditionally clearing stale leftovers at the next natural entry point avoids needing any process-death-specific detection logic.
- **Never placed in:** MediaStore, `filesDir/sessions/`, or any path referenced by `backup_rules.xml`/`data_extraction_rules.xml` — verified by construction (only `cacheDir` is ever touched by this feature's file-writing code).

---

# 12. Metadata / Privacy Architecture

- **`Bitmap.compress()` output alone is sufficient** — confirmed by direct code reading of `ShareImageRenderer.kt`/`ShareMediaStoreWriter.kt`: `Bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)` writes pixel data only; no EXIF block is ever present in its output. `ExifInterface` **is** imported in `ShareRenderConfig.kt`, but only for **read-only** dimension/orientation queries (`readExifOrientedDimensions`) — it is never used to write or copy EXIF into an output file anywhere in the existing pipeline, and `WackelbildPrintRenderer`/`DateBadgeRenderer` follow the identical rule: `ExifInterface` is never instantiated in write mode anywhere in the new code, by construction (no such call is written).
- **Source EXIF orientation is already applied to pixels before the plan's code ever sees the bitmap:** `capture-original.jpg` is decoded via `ImageDecoder`, which applies EXIF orientation automatically during decode (confirmed comment in `ShareImageRenderer.kt:196`: "EXIF orientation is applied automatically by ImageDecoder"); `reference-original.jpg` is decoded via `BitmapFactory.decodeFile` and then passed through `ReferenceRenderer.render()`, which itself performs no EXIF read — `reference-original.jpg` is documented (`COMPARE_SESSION_RENDERING_V1.md`) as already stored pre-EXIF-oriented at session-save time. Both output bitmaps are therefore already pixel-correct with no EXIF orientation tag needed or written on output — satisfying spec §17's "correctly pixel-oriented without relying on EXIF orientation" for the transfer JPEGs.
- **Test verification of GPS/EXIF absence:** reuses the exact existing test pattern from `ShareImageRendererInstrumentedTest` (which, per Gate 1's research, already asserts no EXIF GPS tags on Share Image output) — a new `WackelbildPrintRendererInstrumentedTest` opens the produced `image_one.jpg`/`image_two.jpg` with `androidx.exifinterface.media.ExifInterface` (already a project dependency) and asserts `getAttribute(TAG_GPS_LATITUDE) == null` etc. for the full metadata list in spec §21 (GPS, camera/device tags, DateTimeOriginal, MakerNote, etc.).
- **No source/session file is ever opened in a writable mode** by any new class in this plan — enforced by construction: `WackelbildPrintRenderer`/`DateBadgeRenderer` only ever open session files via read-only `BitmapFactory.decodeFile`/`ImageDecoder.createSource`, and only ever write to files under `cacheDir/wackelbild/`.

---

# 13. Network Client Decision

**Decision: OkHttp (`com.squareup.okhttp3:okhttp`), no Retrofit.**

| Requirement | `HttpURLConnection` | OkHttp | Retrofit |
|---|---|---|---|
| JSON POST | Manual stream handling | `RequestBody`/`Response.body` | Same as OkHttp + annotation layer |
| Custom headers | Manual, verbose | `Request.Builder().header(...)` | Same as OkHttp |
| Multipart upload (~20MiB) | Must hand-roll multipart boundary encoding | `MultipartBody.Builder` — built-in, streaming | Same as OkHttp (Retrofit multipart is just OkHttp underneath) |
| Cancellation | Awkward (`disconnect()` from another thread, racy) | `Call.cancel()` — clean, coroutine-friendly via `suspendCancellableCoroutine` + `invokeOnCancellation` | Same as OkHttp |
| Timeouts | Manual per-connection setters | `OkHttpClient.Builder` connect/read/write timeouts | Same as OkHttp |
| Retry/backoff | Fully manual | `Interceptor`-based, straightforward | Same as OkHttp |
| Testability | Hard to fake without a real socket | Easily wrapped behind an injectable interface (this codebase's established convention, §13.1) | Requires interface+annotation generation, plus a fake `Retrofit`/`Call.Factory` — more moving parts for no benefit here |
| Fixed, tiny endpoint set (4 operations: create, upload×2, no polling endpoint needed for V1 per spec §12) | — | Sufficient on its own | Retrofit's declarative-interface value proposition (many endpoints, shared conventions) does not pay for itself at this scale |

**Chosen: OkHttp.** `HttpURLConnection` is rejected because hand-rolling multipart encoding and cancellation-safe streaming for a ~20MiB upload is exactly the kind of error-prone code this plan should not reinvent when a well-tested, small (~1-2MB AAR), single-purpose library exists. Retrofit is rejected as unjustified overhead for four fixed operations with no shared declarative-interface benefit.

## 13.1 Testability without a real HTTP stack

Following this codebase's established convention (every ViewModel exposes injectable `internal var xRunner: suspend (...) -> Y` lambdas), `DeinWackelbildApiClient` is wrapped behind a small interface consumed by the ViewModel/state machine:

```kotlin
interface DeinWackelbildApiClient {
    suspend fun createHandoff(request: CreateHandoffRequest, idempotencyKey: String): DeinWackelbildResult<CreateHandoffResponse>
    suspend fun uploadImage(handoffToken: String, slot: Slot, file: File): DeinWackelbildResult<UploadResponse>
}
class OkHttpDeinWackelbildApiClient(private val client: OkHttpClient, private val baseUrl: String, private val partnerKey: String) : DeinWackelbildApiClient
```

Unit tests inject a fake `DeinWackelbildApiClient` implementation directly — **no MockWebServer dependency is added**, since the interface boundary already gives full test control without needing a real (even fake) socket, consistent with how `ShareComparisonViewModel` tests fake `shareRunner`/`hqSourceChecker` rather than standing up real MediaStore/filesystem infrastructure.

## 13.2 New dependency footprint

`libs.versions.toml`: add `okhttp = "4.12.0"` (current stable OkHttp 4.x at plan-authoring time — implementer should re-check for a newer stable patch/minor release at actual dependency-addition time per normal review, not pinned as unreviewable in this plan) and `okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }` in `[libraries]`. Single new `implementation(libs.okhttp)` line in `app/build.gradle.kts`. OkHttp ships its own R8/consumer ProGuard rules inside its AAR — no manual `proguard-rules.pro` entries are expected to be required (confirmed empirically during Block 10's `assembleRelease` check, §24).

## 13.3 Timeout configuration (Correction D)

Per the supplied DeinWackelbild V1 API contract (§50), upload connection/write timeout must be **at least 60 seconds** (a ~20 MiB file over a mobile connection can legitimately take that long) — this is a fixed contract requirement, not an open question. `OkHttpClient.Builder()` is configured with `connectTimeout` and `writeTimeout` of at least 60s for upload calls (the create-handoff call, being a small JSON POST, can use a shorter timeout, e.g. 30s connect/read, without contradicting the contract). Exact values beyond this documented minimum (e.g., whether to extend write timeout further for slow mobile networks) remain a plan-level decision, not an external unknown.

---

# 14. DeinWackelbild API Client and State Machine

## 14.1 DTOs (`net/deinwackelbild/DeinWackelbildDtos.kt`)

```kotlin
data class CreateHandoffRequest(val partner: String = "sameview", val locale: String)
data class CreateHandoffResponse(val handoffId: String, val handoffToken: String, val uploadUrl: String)
data class UploadResponse(val slot: String, val status: String, val checkoutUrl: String? = null)
data class DeinWackelbildErrorEnvelope(val code: String?, val message: String?)   // parsed only for logging classification, never shown to user verbatim
sealed class DeinWackelbildResult<out T> {
    data class Success<T>(val value: T) : DeinWackelbildResult<T>()
    data class Failure(val classification: DeinWackelbildErrorClassification, val httpStatus: Int?) : DeinWackelbildResult<Nothing>()
}
enum class DeinWackelbildErrorClassification {
    RETRYABLE_NETWORK, RETRYABLE_SERVER, RATE_LIMITED,
    INVALID_REQUEST, EXPIRED_HANDOFF, INTEGRATION_UNAVAILABLE,
    FILE_TOO_LARGE, INVALID_IMAGE, DIMENSION_MISMATCH, PERMANENT_LOCAL
}
```

JSON parsing uses `org.json.JSONObject` directly (this codebase's established convention — no `kotlinx.serialization`/`Moshi`/`Gson` exists anywhere in the repo, confirmed in Gate 1's dependency research; introducing one would be an unjustified new dependency for four small, fixed response shapes that are trivially hand-parsed the same way `metadata.json` already is throughout this codebase).

## 14.2 Handoff operation state machine

`net/deinwackelbild/DeinWackelbildHandoffStateMachine.kt` — a pure class (no Android framework dependency, fully unit-testable) owning:

```kotlin
data class HandoffState(
    val idempotencyKey: String,          // stable for the life of one user operation (spec §26)
    val handoffId: String? = null,
    val handoffToken: String? = null,
    val uploadUrl: String? = null,
    val slotOneUploaded: Boolean = false,
    val slotTwoUploaded: Boolean = false,
    val checkoutUrl: String? = null
)
```

Drives create → upload-one → upload-two → (checkout URL available once both slots report ready, per spec §26 — this plan assumes, pending Olaf/API confirmation per §30, that `checkoutUrl` becomes non-null on the second successful upload's response; if the installed pilot instead requires a separate polling call, this state machine's ready-detection step is the sole place that changes). Idempotency key is generated once per **user-visible operation** (fresh UUID at the moment "Bestelle dein Wackelbild" is pressed) and reused across automatic retries of that same operation; a new explicit press after a completed/abandoned flow generates a new key (spec §15/§26).

## 14.3 ViewModel-level operation state

`WackelbildViewModel` distinguishes internal operation phase from the small user-visible state the product UX actually shows (one spinner state, not backend phases — spec §12):

```kotlin
internal sealed class WackelbildOperationPhase {
    object Idle : WackelbildOperationPhase()
    object PreparingHq : WackelbildOperationPhase()
    data class FallbackConfirmationNeeded(val pair: WackelbildPrintPair) : WackelbildOperationPhase()
    object PreparingFallback : WackelbildOperationPhase()
    object CreatingHandoff : WackelbildOperationPhase()
    data class UploadingSlot(val slot: Slot) : WackelbildOperationPhase()
    data class ReadyToOpen(val checkoutUrl: String) : WackelbildOperationPhase()
    data class OpenFailedWithCheckoutUrl(val checkoutUrl: String) : WackelbildOperationPhase()
    data class RetryableError(val classification: DeinWackelbildErrorClassification) : WackelbildOperationPhase()
    object PermanentError : WackelbildOperationPhase()
    object Cancelled : WackelbildOperationPhase()
}

// User-visible collapse:
val isBusy: StateFlow<Boolean>            // true for PreparingHq/PreparingFallback/CreatingHandoff/UploadingSlot*
val showFallbackWarning: StateFlow<WackelbildPrintPair?>   // non-null only for FallbackConfirmationNeeded
val userVisibleError: StateFlow<WackelbildUserError?>      // maps RetryableError/PermanentError → the small approved copy set (§18)
```

`isBusy` is the only signal `WackelbildScreen` reads to decide "show spinner + Wackelbild wird vorbereitet …" — it does not branch UI on `PreparingHq` vs. `CreatingHandoff` vs. `UploadingSlot(ONE)` vs. `UploadingSlot(TWO)` (spec §12: no "image 1 of 2", no phase text). The full sealed class exists purely for internal correctness/testability (each phase transition is independently unit-testable) and cancellation targeting.

### Foreground / Custom Tab return distinction (Correction C)

Generic `ON_RESUME` (e.g., returning from Home, another app, or the notification shade) must not be confused with returning specifically from a launched Custom Tab. The ViewModel adds an explicit ephemeral marker:

```kotlin
internal enum class CustomTabAwaitState { NOT_LAUNCHED, LAUNCHED_AWAITING_RETURN }
```

Required behavior, all backed by this single piece of state (no persistent storage, no new phase needed in `WackelbildOperationPhase`):

- **Normal background/resume during `PreparingHq`/`PreparingFallback`/`CreatingHandoff`/`UploadingSlot`:** `CustomTabAwaitState` remains `NOT_LAUNCHED`. `ON_RESUME` does nothing to the operation phase, the visible image, or the date-toggle editability — the active operation continues exactly as before backgrounding (§19).
- **Upload finishes while SameView is backgrounded:** the phase transitions to `ReadyToOpen(checkoutUrl)` as normal, but the `LaunchCustomTab` event is only *collected* once the screen is actually resumed in the foreground — the ViewModel does not launch anything itself; launching is a screen-side effect (§14.4/§16) that can only fire while the screen is composed and visible. The `checkoutUrl` sits in the `ReadyToOpen` phase's ephemeral state in the meantime — no separate storage needed.
- **Actual Custom Tab launch:** immediately before invoking `WackelbildCustomTabLauncher.launch()`, the ViewModel sets `CustomTabAwaitState = LAUNCHED_AWAITING_RETURN`.
- **Actual resume after a Custom Tab launch:** the `ON_RESUME` observer checks `CustomTabAwaitState`; only when it reads `LAUNCHED_AWAITING_RETURN` does the approved return-reset run (Reference visible, date toggle editable again with its retained value, normal CTA shown again, no order-status inference — spec §14) — and `CustomTabAwaitState` is immediately reset to `NOT_LAUNCHED` afterward so a *second* unrelated resume later in the same screen visit does not repeat the reset.

## 14.4 Cancellation and one-shot events

A `CancellationToken`-style `Job` held by the ViewModel for the active prepare/upload coroutine; the Back-confirmation dialog's "Abbrechen" action calls `job.cancel()`, which — via the `finally`/`NonCancellable`-wrapped cleanup (§11) — always runs temp-file deletion regardless of which phase was interrupted.

One-shot events (`Channel`-based, same convention as `ShareComparisonEvent`/`CreateVideoEvent`): `LaunchCustomTab(url)` — emitted once the handoff reaches `ready` with a non-null `checkoutUrl` **and** the screen is actually resumed in the foreground (a `Channel` naturally buffers the event if the screen is backgrounded when `ready` is reached, so the event is safely delivered exactly once the screen next resumes, never launching a browser while SameView itself is still backgrounded — see the Foreground/Custom-Tab-return distinction above and §16 for the full launch mechanics).

## 14.5 Process recreation behavior

None of `WackelbildOperationPhase`, `dateOverlayEnabled`, or the tilt/swipe display state survive process death (all plain `MutableStateFlow`/in-memory, no `SavedStateHandle` beyond `sessionId`) — matching spec §24/§25's explicit "do not persist handoff state" / "the user may explicitly start a new handoff later."

## 14.6 Error/outcome mapping

| HTTP/condition | Classification | ViewModel-visible outcome |
|---|---|---|
| `400` | `INVALID_REQUEST` | Permanent local error — do not retry unchanged (internal bug signal; user sees generic preparation-failure copy since this should not occur if local preparation is correct) |
| `401` | `INTEGRATION_UNAVAILABLE` | "DeinWackelbild.de ist derzeit nicht verfügbar…" — non-retryable for this attempt, feature stays enabled long-term |
| `403` | `EXPIRED_HANDOFF` | Start a new handoff automatically as part of the retry flow (new Idempotency-Key) |
| `409` | (handled inline, not a top-level failure) | Re-upload the missing slot only |
| `410` | `EXPIRED_HANDOFF` | New handoff, new Idempotency-Key |
| `413` | `FILE_TOO_LARGE` | Local re-prepare via §10.3's dimension/quality step-down (should not occur given local pre-check, but handled defensively) |
| `415` | `INVALID_IMAGE` | Local re-prepare (should not occur — output is always a fresh `Bitmap.compress()` JPEG) |
| `422` | `DIMENSION_MISMATCH` | Local re-prepare (should not occur — both images always share `WackelbildTargetDimensions`) |
| `429` | `RATE_LIMITED` | Automatic retry with backoff, same spinner |
| `5xx` | `RETRYABLE_SERVER` | Automatic retry with backoff, same spinner |
| Timeout / connect failure | `RETRYABLE_NETWORK` | "Keine Internetverbindung" / "Übertragung nicht möglich" — user-actionable retry |
| Coroutine cancellation | (not a `DeinWackelbildResult` — propagates as `CancellationException`) | Cancellation cleanup path (§11), no error shown |

Automatic-retry classes (`RETRYABLE_NETWORK`, `RETRYABLE_SERVER`, `RATE_LIMITED`) retry silently under the same "Wackelbild wird vorbereitet …" spinner (spec §30.1). Per the supplied DeinWackelbild V1 API contract (§50), create/upload retries on network failure are **up to three attempts with increasing delay**, and the same Idempotency-Key is reused across Create retries belonging to one user operation (§14.2) — these two facts are fixed by the contract, not open questions (Correction D). Only the exact backoff intervals between those three attempts are left as an implementation-tunable detail (placeholder: 1s/2s/4s increasing delay, marked `// TODO(confirm against installed pilot if it imposes additional constraints beyond the supplied contract)`) — see §30.

No HTTP/API terminology (status codes, "handoff", "token", "Idempotency-Key") ever reaches a string resource shown to the user — verified by construction, since the ViewModel only ever maps `DeinWackelbildErrorClassification` (an internal enum) to the small set of already-approved German string resources from spec §45.

---

# 15. Partner-Key Provisioning

No existing repository precedent exists (confirmed absence of any secrets-gradle-plugin, `buildConfigField`, or `local.properties`-reading code — Gate 1 finding, re-confirmed by direct `app/build.gradle.kts` read in §2, which contains zero `buildConfigField` calls today).

**Chosen mechanism: `local.properties` (already git-ignored, confirmed at `.gitignore:10`) → `buildConfigField`, with a CI/release environment-variable fallback.**

`app/build.gradle.kts` (planned addition, not made in this gate):
```kotlin
val partnerKeyProps = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) load(localPropsFile.inputStream())
}
val deinWackelbildPartnerKey: String =
    (partnerKeyProps.getProperty("DEINWACKELBILD_PARTNER_KEY")
        ?: System.getenv("DEINWACKELBILD_PARTNER_KEY")
        ?: "")

android {
    defaultConfig {
        buildConfigField("String", "DEINWACKELBILD_PARTNER_KEY", "\"$deinWackelbildPartnerKey\"")
    }
}
```

**Complete flow:**
- **Local developer setup:** developer adds `DEINWACKELBILD_PARTNER_KEY=<pilot key>` to their own `local.properties` (never committed — already git-ignored for the entire file). The expected key name and setup steps are documented in a new tracked file, `docs/deinwackelbild/PARTNER_KEY_SETUP.md` (created in Block 9, alongside the `buildConfigField` wiring), which contains only the key **name** and instructions, never a real value. This is the single deterministic location for this documentation (Correction H) — not `local.properties.example` (a loose, un-conventional file for this repository) and not a new top-level `README.md` (none currently exists; creating one would be unrelated scope expansion for this feature).
- **CI/release build setup:** release builds read `DEINWACKELBILD_PARTNER_KEY` from a CI-injected environment variable (exact CI mechanism — GitHub Actions secret, or whatever this repo's actual release pipeline uses — is an **open item requiring the release-environment owner**, §30, since no CI configuration file was found/inspected as part of this plan's repository scope).
- **Debug vs. release handling:** the same `buildConfigField` mechanism serves both build types from the same source-resolution chain (`local.properties` first, env var fallback) — no separate debug-only or release-only key is planned unless the actual pilot/production key pair requires it (open item, §30, since only one pilot key was supplied per the spec's §50 baseline).
- **Behavior when the key is missing/blank:** `BuildConfig.DEINWACKELBILD_PARTNER_KEY.isBlank()` is checked once at `DeinWackelbildApiClient` construction (or lazily before the first `createHandoff` call); if blank, the create-handoff call fails **locally**, before any network request, surfacing the same `INTEGRATION_UNAVAILABLE` user-facing state as a real `401` — this is a deliberate, testable, non-crashing behavior for a missing-key build (e.g., a contributor's local build without the pilot key configured).
- **Key never placed in a URL:** always sent as the request header `X-DWB-Partner-Key` (Correction D — the documented DeinWackelbild V1 API contract header, §50, not an open question; the alternative `Authorization: Bearer` form is not used unless Olaf later supplies a newer API contract) on the create-handoff call only, never on upload calls (per spec §26's "partner key only on create" and the supplied API's own contract), never appended to any query string.
- **Key never logged — no logging interceptor at all (Correction H):** no `HttpLoggingInterceptor` dependency is added; this plan does not use OkHttp's logging-interceptor artifact in any build type. Any feature-specific debug logging (gated by the existing `BuildConfig.DEBUG` convention, used ~35 times elsewhere in this codebase) is manual and minimal — at most the `DeinWackelbildErrorClassification` enum value and the HTTP status code for a failed call — and **never** logs request/response headers or bodies. This structurally excludes the partner-key header and any request/response payload content from ever reaching Logcat, without relying on interceptor log-level configuration to enforce it.
- **Tests never require a real production key:** unit tests inject the fake `DeinWackelbildApiClient` from §13.1, which never touches `BuildConfig` or `OkHttpClient` at all; a real key is needed only for the final manual pilot-acceptance pass (§25/§26), never for `testDebugUnitTest`/`connectedDebugAndroidTest`.

**What release verification can actually check (Correction F):** the real partner key is not committed to VCS; not present in any tracked source file; not placed in Android resources unless strictly required by the chosen mechanism (it is not — `buildConfigField` generates it into `BuildConfig`, a build-generated class, never into `res/values`); not placed in the manifest; not placed in any URL; not logged (above — no logging interceptor at all); not included in crash/telemetry output (this app has none — `CLAUDE_PROJECT_INSTRUCTION.md`'s no-analytics/no-tracking rule is unaffected by this feature); and not unnecessarily duplicated across generated artifacts or config beyond the single `BuildConfig` field.

**What release verification cannot and does not claim to check:** whether the key is actually secret. It is explicitly acknowledged that a key embedded in a released APK/AAB's compiled `BuildConfig` is extractable by anyone with the artifact (static analysis of the DEX/resources trivially recovers a `BuildConfig` string constant) — no artifact inspection, however careful, can prove or produce secrecy, because the key is present in the shipped binary by design (it must be, to make the API call at runtime). R8/obfuscation is explicitly not a secrecy control here: R8 minification is enabled for `release` for general size/performance reasons already, and while it may rename classes, it does not meaningfully hide a string constant referenced at a call site. Security for this key rests entirely on server-side properties outside this repository's control: narrow API scope (create-handoff only), rate limiting, and the ability to rotate the key if it is ever found to be misused — never on client-side secrecy. **No key or credential of any kind was created, generated, or stored during this gate.**

---

# 16. Custom Tab Integration

- **Dependency:** `androidx.browser:browser`, planned version `1.9.0` (current stable at plan-authoring time; re-verify at actual dependency-addition time). New `libs.versions.toml` entry + `implementation(libs.androidx.browser)` in `app/build.gradle.kts`.
- **Launch helper:** `ui/wackelbild/WackelbildCustomTabLauncher.kt` — a thin wrapper: `fun launch(context: Context, url: String): Boolean` calling `CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))` inside a `try { ...; true } catch (_: ActivityNotFoundException) { false }`. Kept as a small injectable class (not inlined into the ViewModel) so tests can substitute a fake launcher without needing a real Custom Tab provider on the test device/emulator.
- **Lifecycle:** the launch is a one-shot `LaunchedEffect`/Compose side-effect fired from the `LaunchCustomTab` event (§14.4), not a persistent state — the event is only ever collected while the screen is composed and foregrounded, and (per §14.3) the ViewModel only emits it once the operation is both `ready` and the screen is actually in the foreground, so the Custom Tab is never launched while SameView itself is backgrounded.
- **Immediately before the actual launch,** the ViewModel sets `CustomTabAwaitState = LAUNCHED_AWAITING_RETURN` (§14.3) so the subsequent `ON_RESUME` is correctly recognized as a Custom Tab return rather than an unrelated foreground resume.
- **Launch failure handling:** if `launch()` returns `false`, the ViewModel transitions to `OpenFailedWithCheckoutUrl` (§14.3) retaining the already-received `checkoutUrl` string in memory and resetting `CustomTabAwaitState` to `NOT_LAUNCHED` (the launch never actually left the app, so there is no pending return to await); the screen shows "DeinWackelbild.de konnte nicht geöffnet werden." with a "DeinWackelbild.de öffnen" retry action that calls `launch()` again with the same stored URL, re-arming `LAUNCHED_AWAITING_RETURN` on the retry attempt — **no re-upload, no new handoff** (spec §31).
- **Return-to-screen behavior:** no `onNewIntent`/deep-link handling is added anywhere (spec §14: "V1 has no return callback"). Returning is simply the SameView Activity regaining foreground via the normal Android back-stack/Task-switch mechanism; `WackelbildScreen`'s `ON_RESUME` observer performs the reset-to-Reference/re-enable-toggle/re-show-CTA behavior **only when `CustomTabAwaitState == LAUNCHED_AWAITING_RETURN`** (§6.2/§14.3) — an unrelated resume (Home button, notification shade, another app) during an active or idle operation leaves the screen state untouched.
- **Exact URL usage:** `checkoutUrl` from `UploadResponse`/handoff-ready response is passed to `launch()` verbatim — no string concatenation, no query-param addition, no host/path substitution anywhere in this plan's code (spec §14: "must not construct or modify the checkout URL itself").

---

# 17. Localization

## 17.1 New string resources (DE + EN, both `values/strings.xml` and `values-de/strings.xml`)

| Key | German (approved, spec §45) | Proposed English |
|---|---|---|
| `export_menu_create_wackelbild` | Wackelbild erstellen | Create lenticular print |
| `wackelbild_screen_title` | Wackelbild erstellen | Create lenticular print |
| `wackelbild_date_toggle_label` | Datum anzeigen | Show date |
| `wackelbild_date_unavailable_hint` | Referenzdatum hinzufügen, um das Datum anzuzeigen. | Add a reference date to show it here. |
| `wackelbild_hint_tilt_title` | Handy leicht neigen | Tilt your phone |
| `wackelbild_hint_supporting` | Sieh dir dein Wackelbild an. | See your lenticular print in action. |
| `wackelbild_hint_swipe_title` | Über das Bild wischen | Swipe over the image |
| `wackelbild_transfer_disclosure` | Deine beiden Bilder werden zur Gestaltung an DeinWackelbild.de übertragen. Die Bestellung schließt du dort ab. | Your two images are sent to DeinWackelbild.de to create your print. You complete the order there. |
| `wackelbild_cta_order` | Bestelle dein Wackelbild | Order your lenticular print |
| `wackelbild_loading_preparing` | Wackelbild wird vorbereitet … | Preparing your lenticular print … |
| `wackelbild_cancel_transfer_title` | Übertragung abbrechen? | Cancel transfer? |
| `wackelbild_cancel_transfer_message` | Die Bilder werden gerade an DeinWackelbild.de übertragen. | Your images are currently being sent to DeinWackelbild.de. |
| `wackelbild_cancel_transfer_continue` | Weiter übertragen | Keep transferring |
| `wackelbild_cancel_transfer_stop` | Abbrechen | Cancel |
| `wackelbild_error_no_internet` | Keine Internetverbindung | No internet connection |
| `wackelbild_error_transfer_failed` | Übertragung nicht möglich | Transfer not possible |
| `wackelbild_error_retry` | Erneut versuchen | Try again |
| `wackelbild_error_preparation_failed` | Wackelbild kann nicht erstellt werden | This lenticular print can't be created |
| `wackelbild_error_integration_unavailable` | DeinWackelbild.de ist derzeit nicht verfügbar. Bitte versuche es später erneut. | DeinWackelbild.de is currently unavailable. Please try again later. |
| `wackelbild_custom_tab_open_failed` | DeinWackelbild.de konnte nicht geöffnet werden. | DeinWackelbild.de couldn't be opened. |
| `wackelbild_custom_tab_open_retry` | DeinWackelbild.de öffnen | Open DeinWackelbild.de |
| `wackelbild_quality_fallback_title` | Originalqualität nicht verfügbar | Original quality not available |
| `wackelbild_quality_fallback_message` | (final localized meaning per spec §19, exact copy pending localization review) | (same, English) |
| `wackelbild_quality_fallback_cancel` | Abbrechen | Cancel |
| `wackelbild_quality_fallback_continue` | Trotzdem fortfahren | Continue anyway |

**Concrete recommendation for the physical-product term:** **"lenticular print"**, used consistently across all English strings above (rejecting a literal "wobble picture" translation as unnatural product terminology in English, per spec §46's own guidance to prefer natural meaning over mechanical translation). This is a copy recommendation only, not a locked decision — spec §46 keeps the German wording as the currently approved source of intent, and this plan does not change that.

## 17.2 Locale mapping

`net/deinwackelbild/WackelbildLocaleMapper.kt` — a pure function, no existing utility to reuse (Gate 1 confirmed none exists):

```kotlin
fun mapAppLocaleToDeinWackelbild(appLocale: Locale, supportedLocales: Set<String>): String {
    val candidate = "${appLocale.language}-${appLocale.country}"
    return if (candidate in supportedLocales) candidate else "de-DE"
}
```

Given the app currently supports exactly two locales (`values`/default = English, `values-de` = German — confirmed directory structure), the practical mapping is trivially `de → "de-DE"`, everything else → app's own English default mapped to whatever DeinWackelbild's confirmed English locale code is (pending §30), falling back to `"de-DE"` per spec §29 if unsupported. `supportedLocales` is passed in (not hardcoded) so the eventual real matrix (§30) is a data update, not a code change.

---

# 18. Error / Retry / Cancellation Model

String resources for each state are listed in §17.1 above (no additional dialogs beyond the ones already named in spec §45/§30). Mapping of technical conditions → these exact strings is fully specified in §14.6.

**Cancellation state machine:** `WackelbildOperationPhase` (§14.3) transitions to `Cancelled` only via the explicit Back-confirmation "Abbrechen" action (spec §13, dialog `wackelbild_cancel_transfer_*`, cloned from `CreateVideoScreen.kt:112-159`) — never automatically, never as a side effect of any other error path, matching spec §13's requirement that cancellation is always a distinct, deliberate user action separate from failure handling.

**Accessibility semantics for error/retry:** the CTA/error area uses standard `Button`/`Text` composables with default Material3 semantics (button role, text content automatically exposed) — no custom `semantics {}` block is needed there, unlike the image-switch control (§7/§20), which does need one since it is a custom gesture region with no built-in semantic role.

---

# 19. Lifecycle / Background Behavior

- **No WorkManager, no foreground service** anywhere in this plan — the entire prepare→upload sequence runs in a single `viewModelScope.launch { }` coroutine, cancelled and cleaned up exactly as described in §11/§14.4.
- **Background/Custom Tab state distinction (Correction C):** a short app backgrounding (Home button, notification shade, switching to another app) while an operation is `PreparingHq`/`PreparingFallback`/`CreatingHandoff`/`UploadingSlot` never resets the screen, never re-enables the date toggle, and never simulates a Custom Tab return — see §14.3's `CustomTabAwaitState` distinction for the exact mechanism. If the operation reaches `ReadyToOpen` while the app is backgrounded, the Custom Tab is not launched from the background; it launches exactly once, the next time the screen is actually resumed in the foreground (§14.4/§16).
- **Navigation away while upload is active:** if the user presses system Back and confirms cancellation (§18), the coroutine is cancelled and cleanup runs; if the user instead backgrounds the whole app (Home button, not Back), the coroutine keeps running as long as the process naturally stays alive (spec §24: "a short temporary app backgrounding does not need to be artificially cancelled if the current in-memory operation naturally remains alive") — no explicit background-detection code is added, since `viewModelScope` coroutines are unaffected by Activity backgrounding by default; this is existing platform behavior, not new code.
- **Process loss:** if the process is killed while an operation is active, on relaunch `WackelbildViewModel` is freshly constructed with no memory of the prior operation (§14.5) — the server-side handoff, if one was created, is left to expire under DeinWackelbild's own 24-hour retention (spec §36), with no SameView-side reconstruction attempt, exactly as specified.
- **Configuration changes (rotation):** `WackelbildViewModel` survives rotation by default (standard `ViewModel` lifecycle scoping to the `NavBackStackEntry`) — no special handling needed; `WackelbildPreview`'s own transient Compose-local state (if any beyond the ViewModel-owned "current visible image") is expected to be minimal enough not to require `rememberSaveable`, but this is confirmed during Block 2/3 implementation, not asserted here as already proven.
- **Custom Tab launch after screen disappearance:** cannot occur, since the Custom Tab launch is a synchronous one-shot event fired only while the screen is composed and observing its ViewModel's event channel; if the screen were somehow gone by the time the event fires (not expected given `viewModelScope` is tied to the same lifecycle), the event is simply never collected — no crash, no stale launch.

---

# 20. Accessibility / Responsive Layout

- **Image-switch semantics:** `WackelbildPreview`'s root `Box` gets `Modifier.semantics { contentDescription = <"Reference image visible" / "Capture image visible", localized>; onClick { toggleImage(); true } }` — exposing both the current state and a manual activation action for screen-reader users who cannot tilt or swipe reliably (spec §8.7's accessibility requirement, satisfied without a separate visible mode/setting).
- **Date-toggle disabled/supporting state:** direct reuse of `SettingsSwitchRow`'s existing enabled/supportingText semantics (already accessibility-correct per its existing usage elsewhere in the app).
- **Font scaling:** all text uses `sp`-based Compose text styles (via `MaterialTheme.typography`/string resources), never fixed `dp`-sized text — same discipline as every other existing screen; no new font-scaling risk introduced.
- **Portrait/Landscape device orientation:** supported without a forced orientation lock (spec §6: "no custom orientation lock"), consistent with the app-wide `android:configChanges` handling already declared in the manifest (§2) — no manifest change needed for this screen specifically.
- **Compact-height scroll:** addressed structurally in §6.3 — no separate accessibility concern beyond standard scrollable-content semantics, which Compose provides automatically for `Modifier.verticalScroll`.
- **Responsive layout:** fully covered in §6.4; uses only the existing `WindowWidthSizeClass` mechanism (`RESPONSIVE_LAYOUT_SYSTEM_V1.md`), no new breakpoint or layout system.

---

# 21. File Scope

| File | Create / Modify | Block(s) | Exact responsibility | Risk |
|---|---|---|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt` | Modify | 1 | Add divider + 3rd Export-menu item, new optional params (callback wired to a real destination starting Block 2) | **High** — heavily tested existing screen; change is additive-only within one existing `if` block |
| `app/src/main/java/com/isardomains/sameview/MainActivity.kt` | Modify | 2 | New route constants, route builder, `composable()` block, param wiring | **High** — central navigation graph; change is additive, modeled exactly on `ROUTE_SHARE_COMPARISON` |
| `app/src/main/res/values/strings.xml`, `values-de/strings.xml` | Modify | 1, 4, 5, 7 | New string resources (§17.1) | Low |
| `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt` | Create | 2 | Screen shell, layout, Back handling | Medium |
| `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt` | Create | 2, 3, 4, 8, 9 | Full state model (§14.3-§14.5) | Medium — grows across blocks |
| `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildPreview.kt` | Create | 2, 3, 4 | Preview composable, tilt/swipe gesture region, image-switch semantics | Medium |
| `app/src/main/java/com/isardomains/sameview/ui/wackelbild/TiltProvider.kt` | Create | 3 | Sensor wrapper (§7.2) | Low — narrow, isolated, modeled on proven `CompassProvider` |
| `app/src/main/java/com/isardomains/sameview/ui/wackelbild/TiltHysteresisStateMachine.kt` | Create | 3 | Pure hysteresis/arbitration logic (§7.3) | Medium — genuinely new logic, no precedent |
| `app/src/main/java/com/isardomains/sameview/ui/wackelbild/DateBadgeGeometry.kt` | Create | 4 | Shared pure geometry math (§8.3) | Low |
| `app/src/main/java/com/isardomains/sameview/ui/wackelbild/DateBadgeOverlay.kt` | Create | 4 | Compose live-preview overlay | Low |
| `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildTempFileManager.kt` | Create | 5, 6 | `cacheDir` lifecycle (§11) | Medium |
| `app/src/main/java/com/isardomains/sameview/image/ShareImageRenderer.kt` | Modify | 5 | Widen 4 `private`→`internal` methods (§9.2) — **no logic change** | **High file, Low change-risk** |
| `app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildDimensionResolver.kt` | Create | 5 | Common-resolution algorithm considering both sources + pair-level size loop (§10) | Medium |
| `app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildPrintRenderer.kt` | Create | 5 | Two-file HQ/fallback pipeline (§9.3) | **High** — core correctness of the feature |
| `app/src/main/java/com/isardomains/sameview/image/wackelbild/DateBadgeRenderer.kt` | Create | 5 | Bitmap-side date badge (§8.3) | Medium |
| `gradle/libs.versions.toml` | Modify | 7, 10 | Add OkHttp + `androidx.browser` versions/coordinates | Medium — first new runtime deps in the app |
| `app/build.gradle.kts` | Modify | 7, 9, 10 | Add dependencies; `buildConfigField` for partner key | **High** — build/release-critical file |
| `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/*.kt` (DTOs, client, state machine, locale mapper) | Create | 7, 8 | API integration (§13, §14, §17.2) | **High** — first network code in this app |
| `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildCustomTabLauncher.kt` | Create | 10 | Custom Tab launch/failure (§16) | Medium |
| `local.properties` (developer-local, not committed) | N/A — never a repo file change | 9 | Developer key entry (§15) | N/A |
| `docs/deinwackelbild/PARTNER_KEY_SETUP.md` | Create | 9 | Deterministic key-name/setup documentation (§15 Correction H) | Low |
| `app/src/main/AndroidManifest.xml` | Modify | 10 | Add `INTERNET` permission | **High** — release/Play/privacy-critical; gated on Gate 2's governance addendum, already satisfied |
| `app/proguard-rules.pro` | Modify only if Block 10's `assembleRelease` check surfaces a need | 10 | Potential OkHttp/androidx.browser keep rules | Low (OkHttp ships its own consumer rules; expected no-op) |
| Test files mirroring every new class above | Create | 13 (bulk), plus incrementally per-block | Unit + instrumentation coverage (§24) | Low individually |
| `docs/IMPLEMENTATION_NOTES.md` | Modify | throughout | Status entries per completed block | Low |
| `docs/COMPARE_FLOW_V1.md` | Modify | 1 | §43 dropdown update | Low |
| `docs/RELEASE_HARDENING_AUDIT_V2.md` | Modify | 14 | Release-readiness addendum | Low |

---

# 22. Reuse vs. New-Code Matrix

| Existing component | Reuse unchanged | Extend | Extract shared helper | Do not touch | Reason |
|---|---|---|---|---|---|
| `ShareImageRenderer` (`resolveHqCaptureFile`, `hasHqCaptureSource`, `readSessionViewport`) | ✅ | | | | Already `internal`, directly callable |
| `ShareImageRenderer` (`decodeHqCapture`, `prepareHqCaptureForSbs`, `renderHqReference`, `decodeReferenceFallback`) | | ✅ (visibility only) | | | §9.2 — zero logic change, near-zero regression risk |
| `ShareRenderConfig` (`readOverlayParams`, `readExifOrientedDimensions`) | ✅ | | | | Already `internal`, directly callable |
| `ShareRenderConfig` (`computeCanvasDimensions`, `MAX_HQ_LONGEST_EDGE`, caption-height logic) | | | | ✅ | Share-Image-specific concerns (caption, SbS halving, wrong cap for this feature) — new `WackelbildDimensionResolver` instead |
| `ReferenceRenderer.render()` | ✅ | | | | Already public, exact function DeinWackelbild's crop-parity relies on |
| `CaptionRenderer` | | | | ✅ | Different visual composition (shadow, no box, vs. spec's boxed-no-shadow badge) — new `DateBadgeRenderer` instead, sharing only color/proportional-sizing *conventions*, not code |
| `CompassProvider` | | | | ✅ | Different product concern (compass heading vs. tilt roll); new parallel `TiltProvider` narrowly duplicates the ~15-line remap pattern, reading a different array index |
| `ShareComparisonScreen` / `CreateVideoScreen` | | | | ✅ (as templates, not code-shared) | Structural pattern is cloned (Scaffold/TopAppBar/scroll/680dp), not imported as shared composables — each screen in this codebase is independently self-contained by existing convention |
| Temp/cache helpers | N/A (new) | | | | Confirmed no reusable precedent exists (Gate 1) — `WackelbildTempFileManager` is new |
| `ShareComparisonViewModel`/`CreateVideoViewModel` locale/date helpers (`computeDateLine`, `Locale.getDefault()`-injection pattern) | | ✅ (pattern reused, new small functions written) | | | Convention reused; underlying private functions are not directly callable across ViewModels, so equivalent small functions are written following the same pattern |
| `CompareLabelLogic.computeCompareLabels` date-precision helpers | ✅ (where directly applicable) | | | | Confirmed present at `ui/compare/CompareLabelLogic.kt`; precision-detection logic reused where signatures allow |

---

# 23. Implementation Blocks

Ordered to minimize regression risk: pure-UI/navigation shell first (fully testable, zero new dependencies, zero network/permission surface), then local-only image/sensor work (still zero network/permission surface), then network/build/manifest work last (the only genuinely release-risky changes), then hardening/tests/docs, then external validation.

## Block 1 — Compare menu entry only (Correction G)
- **Objective:** modify only the existing `CompareScreen` entry point — add the new callback parameter, divider, Wackelbild menu item, and click wiring. No navigation destination exists yet; the caller may pass a no-op/null callback at this call site to keep compilation green, but no temporary screen, fake route, feature flag, or disposable stub architecture is introduced anywhere.
- **Files:** `CompareScreen.kt`, `strings.xml`/`values-de/strings.xml` (menu-item string only).
- **Functions/classes:** divider + third `DropdownMenuItem` addition, `onCreateWackelbild`/`isWackelbildAvailable` parameters (§6.1).
- **Regression risk:** Low-Medium (touches one high-risk file, but purely additively).
- **Test commands:** `testDebugUnitTest`, `assembleDebug`; new `CompareScreenTest` cases (menu item present, divider present, enabled/click-callback wiring) — additive only.
- **Manual validation:** tap through Share menu on a debug build, confirm item order and divider match spec §5 (the item is present but not yet functionally wired until Block 2).
- **Stop/gate criteria:** existing Share Image / Create Video tests remain green; no existing test modified; no `MainActivity.kt` change in this block.

## Block 2 — Real destination + navigation (Correction G)
- **Objective:** create the real `WackelbildScreen`/`WackelbildViewModel` destination and wire actual navigation from the callback `CompareScreen` gained in Block 1. `WackelbildScreen` renders `reference.jpg` initially, supports Back navigation, and follows the responsive shell (§6.4) — no tilt/swipe, no date overlay, no network yet.
- **Files:** `WackelbildScreen.kt`, `WackelbildViewModel.kt` (initial subset), `MainActivity.kt` (route constants, route builder, `composable()` block, `onCreateWackelbild` callback wiring to the real navigation call), `strings.xml` (screen title, disclosure text).
- **Functions/classes:** `ROUTE_WACKELBILD*` constants + `wackelbildRoute()` (§6.2).
- **Regression risk:** Low-Medium (new files plus one additive `MainActivity.kt` change, modeled exactly on `ROUTE_SHARE_COMPARISON`).
- **Test commands:** `testDebugUnitTest`, `connectedDebugAndroidTest` (new instrumentation tests for navigation/Back/responsive, plus a `CompareScreenTest` update confirming the Block-1 menu item now actually navigates), `assembleDebug`.
- **Manual validation:** open screen from menu, confirm no network request occurs (spec §6/§23 — verifiable via Android Studio's Network Profiler showing zero traffic).
- **Stop/gate criteria:** screen opens fully offline, Back works, layout matches Compact/Medium/Expanded rules; no temporary/stub architecture remains anywhere in the codebase.

## Block 3 — Tilt/swipe interaction
- **Objective:** `TiltProvider`, `TiltHysteresisStateMachine`, swipe gesture, arbitration, sensor-unavailable fallback hint.
- **Files:** `TiltProvider.kt`, `TiltHysteresisStateMachine.kt`, `WackelbildPreview.kt` (gesture region), `WackelbildViewModel.kt` (extended).
- **Regression risk:** Medium (genuinely new arbitration logic, no precedent).
- **Test commands:** `testDebugUnitTest` (hysteresis/arbitration pure-logic tests), `connectedDebugAndroidTest` (swipe instrumentation).
- **Manual validation:** real-device tilt/swipe feel (placeholder thresholds, §7.3) — flagged for revisit in §25.
- **Stop/gate criteria:** no runtime permission requested; swipe works with sensor disabled (emulator without rotation-vector sensor, or a test double forcing `isAvailable() == false`).

## Block 4 — Date overlay preview
- **Objective:** `DateBadgeGeometry`, `DateBadgeOverlay` (Compose), date-toggle row, availability/precision logic, corner-radius visual validation.
- **Files:** `DateBadgeGeometry.kt`, `DateBadgeOverlay.kt`, `WackelbildViewModel.kt` (extended).
- **Regression risk:** Low.
- **Test commands:** `testDebugUnitTest` (geometry/formatting), `connectedDebugAndroidTest` (toggle default/disabled-state tests).
- **Manual validation:** visually confirm the §8.3 proposed corner-radius constant on a real device; adjust the single named constant if needed.
- **Stop/gate criteria:** toggle defaults OFF; disabled when no usable Reference date; missing date never blocks the (still network-inert) CTA.

## Block 5 — Two-file print renderer
- **Objective:** `ShareImageRenderer` visibility widening, `WackelbildDimensionResolver`, `WackelbildPrintRenderer`, `DateBadgeRenderer` (bitmap side), fallback detection, `WackelbildTempFileManager` (creation side).
- **Files:** per §21.
- **Regression risk:** High (core correctness) but isolated — no existing Share Image call site changes.
- **Test commands:** `testDebugUnitTest` (dimension algorithm, crop-parity unit tests with synthetic bitmaps), `connectedDebugAndroidTest` (metadata-clean assertions, real-decode crop-parity tests against real session fixtures), full existing `ShareRenderConfigTest`/`ShareImageRendererInstrumentedTest` suites re-run to confirm zero regression from the visibility change.
- **Manual validation:** visually compare a rendered Wackelbild pair against the same session's `reference.jpg`/`capture.jpg` for crop/alignment identity.
- **Stop/gate criteria:** T-9.3-A edge case (§9.3) explicitly checked against real session data; both existing Share Image test suites 100% green, unmodified.

## Block 6 — Temp-file cleanup
- **Objective:** complete `WackelbildTempFileManager` (all cleanup paths), cancellation wiring in the ViewModel.
- **Files:** `WackelbildTempFileManager.kt`, `WackelbildViewModel.kt`.
- **Regression risk:** Low-Medium.
- **Test commands:** `testDebugUnitTest`, `connectedDebugAndroidTest` (leftover-file assertions after each terminal path).
- **Manual validation:** force-kill the app mid-"preparation" (still network-inert at this point) and confirm sweep-on-next-entry clears leftovers.
- **Stop/gate criteria:** zero leftover files after success/cancel/error in every tested path.

## Block 7 — Network client + DTOs
- **Objective:** OkHttp dependency, `DeinWackelbildApiClient` interface + `OkHttpDeinWackelbildApiClient` implementation, DTOs, error-classification mapping. **No manifest change yet** — this block is buildable/testable entirely against the fake client; the real `OkHttpDeinWackelbildApiClient` is wired but never actually reachable until `INTERNET` exists (Block 10), so this block cannot make a real network call even if invoked.
- **Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts` (OkHttp only), `net/deinwackelbild/*.kt`.
- **Regression risk:** Medium (first dependency addition, but additive).
- **Test commands:** `testDebugUnitTest` (DTO parsing, error-classification mapping against synthetic responses), `assembleDebug`.
- **Manual validation:** none yet (no real network reachable without `INTERNET`).
- **Stop/gate criteria:** builds cleanly with the new dependency; no existing test affected.

## Block 8 — Handoff state machine
- **Objective:** `DeinWackelbildHandoffStateMachine`, idempotency-key lifecycle, retry/backoff logic, `WackelbildOperationPhase` wiring into `WackelbildViewModel`.
- **Files:** `net/deinwackelbild/DeinWackelbildHandoffStateMachine.kt`, `WackelbildViewModel.kt` (extended).
- **Regression risk:** Medium.
- **Test commands:** `testDebugUnitTest` (full state-machine transition coverage against the fake `DeinWackelbildApiClient`, including every §14.6 error branch).
- **Manual validation:** none yet.
- **Stop/gate criteria:** every §14.6 row has a corresponding passing unit test.

## Block 9 — API key / build config
- **Objective:** `local.properties`/env-var → `buildConfigField` wiring per §15. **No real key is added in this block or any other block of this plan** — the mechanism is built and tested against a blank/missing key (§15's "missing key" behavior).
- **Files:** `app/build.gradle.kts`.
- **Regression risk:** Low (additive `buildConfigField`).
- **Test commands:** `assembleDebug`, `assembleRelease` with a blank key present, confirming the graceful `INTEGRATION_UNAVAILABLE` local-failure path (§15) rather than a crash.
- **Manual validation:** confirm `BuildConfig.DEINWACKELBILD_PARTNER_KEY` is blank in a clean checkout without `local.properties` configured.
- **Stop/gate criteria:** app builds and runs with no key configured; feature fails gracefully, not crashingly.

## Block 10 — Manifest/dependencies/Custom Tabs
- **Objective:** add `INTERNET` permission (governance precondition already satisfied, Gate 2), `androidx.browser` dependency, `WackelbildCustomTabLauncher`.
- **Files:** `AndroidManifest.xml`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `WackelbildCustomTabLauncher.kt`, `app/proguard-rules.pro` (only if needed, per §13.2).
- **Regression risk:** High (release-critical, first-ever `INTERNET` permission in this app).
- **Test commands:** `assembleDebug`, `assembleRelease`, `bundleRelease`, full `testDebugUnitTest`/`connectedDebugAndroidTest` re-run to confirm the permission addition alone changes nothing else observable.
- **Manual validation:** confirm the Play Store manifest diff shows exactly one new permission; confirm no other app behavior implicitly changed.
- **Stop/gate criteria:** release build succeeds; manifest diff is exactly the planned one line plus whatever `androidx.browser`'s own manifest merges in (to be inspected, not assumed empty).

## Block 11 — End-to-end UI wiring
- **Objective:** connect all prior blocks into the live user-facing flow: real order button, spinner, real Custom Tab launch, fallback-warning dialog, Back-confirmation during active transfer.
- **Files:** `WackelbildScreen.kt`, `WackelbildViewModel.kt` (final wiring).
- **Regression risk:** Medium (integration point for everything above).
- **Test commands:** `connectedDebugAndroidTest` (full instrumentation flow, still against the fake API client for automated CI-safe testing — no real network traffic in CI).
- **Manual validation:** first end-to-end manual run against the actual pilot endpoint (requires the real key, developer-local only) — this is the first point in the whole plan where a real network call is even possible.
- **Stop/gate criteria:** a full manual happy-path order completes and opens a real Custom Tab.

## Block 12 — Error/cancellation polish
- **Objective:** verify and, if needed, complete every §14.6/§18 error path against the real API's actual observed behavior (not just the fake client's synthetic responses).
- **Files:** touch-ups across `WackelbildViewModel.kt`, `net/deinwackelbild/*`.
- **Regression risk:** Low-Medium.
- **Test commands:** full unit + instrumentation suite.
- **Manual validation:** simulated no-network, airplane-mode-mid-upload, forced-slow-network (Android Studio network throttling) checks.
- **Stop/gate criteria:** every spec §30 error state reproducible and correctly mapped.

## Block 13 — Tests (consolidation pass)
- **Objective:** close any coverage gaps identified across Blocks 1-12; no new product behavior.
- **Files:** test files only.
- **Regression risk:** Low.
- **Test commands:** full suite (§24).
- **Stop/gate criteria:** §24's full test list satisfied.

## Block 14 — Release/privacy/documentation hardening
- **Objective:** execute §27/§28 in full.
- **Files:** `docs/IMPLEMENTATION_NOTES.md`, `docs/COMPARE_FLOW_V1.md`, `docs/RELEASE_HARDENING_AUDIT_V2.md` (or successor).
- **Regression risk:** N/A (docs only).
- **Stop/gate criteria:** every §27 checklist item explicitly addressed or explicitly assigned to an external owner.

## Block 15 — Real-device / pilot validation
- **Objective:** execute §25 (real-device list) and §26 (DeinWackelbild pilot acceptance checklist) in full, with real credentials, against the real pilot endpoint.
- **Regression risk:** N/A (validation only).
- **Stop/gate criteria:** every item in §25/§26 checked off or explicitly deferred with owner and reason.

Dependencies between blocks are respected throughout: no block that requires `INTERNET`/a real dependency runs before Block 10; every earlier block is independently testable against fakes; Block 5's `ShareImageRenderer` visibility change is isolated to its own block with a full existing-suite re-run as its gate criterion specifically because it is the one change touching already-shipped code.

---

# 24. Test Strategy

## 24.1 Unit tests (JVM, `testDebugUnitTest`)

- Tilt threshold/hysteresis transitions (`TiltHysteresisStateMachineTest`) — every state-transition edge, including the placeholder-constant boundary values (§7.3).
- Relative neutral-position capture and angle-wrap delta math.
- Display-rotation mapping for `TiltProvider` (mirrors the existing `remapCoordinateSystem` table, tested with a mocked `SensorManager`, §7.6).
- Swipe/sensor arbitration (`swipeOverrideActive` set/clear transitions, §7.3).
- Date-precision formatting (`DateBadgeFormatterTest` — year/year-month/full-date, locale variation).
- Date availability detection (usable vs. unusable Reference date, including absent/malformed `metadata.json`).
- Date-badge geometry (`DateBadgeGeometryTest` — bottom-right positioning, proportional sizing, corner-radius constant, text-fit at multiple canvas sizes).
- Common-output-dimension algorithm (`WackelbildDimensionResolverTest` — capture is the weaker source, reference visible-source-area is the weaker source, API side cap is the limiting factor, API megapixel cap is the limiting factor, no source is ever upscaled, output legitimately smaller than viewport when required to honor no-upscale, even-dimension enforcement, degenerate-floor routing into fallback).
- Pair-level JPEG-size reduction strategy (`§10.3`'s bounded quality/dimension-step loop — one file exceeding 20 MiB triggers a pair-level dimension reduction applied to both images identically, encoding restarts at high quality after each pair downscale, the algorithm is bounded at 8 attempts, no large in-memory encoded buffers remain after an attempt, tested with synthetic oversized inputs asserting the exact step sequence and the bounded-failure exit).
- Exact Reference crop parity (pixel/geometry comparison against `reference.jpg` at multiple target resolutions).
- Exact Capture crop parity (native `capture-original.jpg` aspect ratio equals viewport ratio — the guaranteed/expected case; the defensive-guard fail-safe path when a synthetic input's ratio is forced to differ, routing correctly into the §9.5 fallback rather than stretching, §9.3).
- HQ fallback (all three §9.5 branches, each independently triggerable via injected fakes).
- Metadata-clean output (structural assertion — no `ExifInterface` write call is reachable from the render path; complemented by the instrumentation-level EXIF-content assertion below).
- API DTO parsing (`DeinWackelbildDtosTest` — valid and malformed JSON for every response shape).
- Partner-header contract (`DeinWackelbildApiClientTest` — Create request carries the `X-DWB-Partner-Key` header; upload requests carry no partner-key header at all; §15/§30).
- Idempotency-key lifecycle (stable across retries of one operation, fresh on a new explicit order action).
- Retry/backoff behavior (`DeinWackelbildHandoffStateMachineTest` — up to three attempts per the supplied API contract, backoff timing, against the fake `DeinWackelbildApiClient`).
- Status/error mapping (every row of §14.6, one test each).
- Cancellation state machine (`WackelbildViewModelTest` — `Job.cancel()` at every phase, cleanup always runs).
- Partner-key missing behavior (`DeinWackelbildApiClientTest` — blank key → local `INTEGRATION_UNAVAILABLE` failure, never a real request attempt).
- Security-checklist assertions (§15/§24.3): request construction never places the key in a URL (asserted directly on the constructed `Request` object); no header/body logging code path exists at all (verified by the absence of any logging-interceptor dependency); test builds succeed with a blank/fake key and make no real API traffic.

## 24.2 UI/instrumentation tests (`connectedDebugAndroidTest`)

- Share menu item and divider presence/order (`CompareScreenTest`, additive).
- Navigation to the Wackelbild screen and Back.
- Date-toggle default (OFF) and disabled state (no usable Reference date).
- Preview initial Reference-visible state.
- Swipe toggling (with and without a mocked sensor).
- Loading state (spinner + single copy string, no phase text, no percentage).
- Home/background during active upload does not reset the screen, does not re-enable the date toggle, and does not simulate a Custom Tab return (`CustomTabAwaitState` remains `NOT_LAUNCHED`, §14.3).
- Resume from an unrelated app (not a Custom Tab) does not trigger the Custom-Tab-return reset.
- Upload completing while the app is backgrounded does not launch a Custom Tab from the background; the `LaunchCustomTab` event is only collected once the screen resumes in the foreground.
- Foreground resume after `ReadyToOpen` launches the Custom Tab exactly once, not repeatedly on subsequent resumes.
- Actual Custom Tab return (`CustomTabAwaitState == LAUNCHED_AWAITING_RETURN`) triggers the reset-to-Reference behavior exactly once, and resets the marker so a later unrelated resume in the same screen visit does not repeat it.
- Date-toggle value is retained across an actual Custom Tab return, and remains frozen (non-editable) throughout any busy phase.
- Back-cancellation dialog (shown only during busy phases, both actions wired).
- Fallback-quality warning dialog (shown only after CTA press, not on screen open).
- Retryable and permanent error states (each renders the correct approved copy, no technical terminology).
- Custom Tab launch intent construction, testable without a real partner service by asserting the `Intent`/URL passed to the launcher, not by actually completing a checkout.
- Custom Tab open failure fallback (forced `ActivityNotFoundException` via a fake launcher).
- Accessibility semantics (image-switch content description/action, date-toggle supporting text).
- Compact/Medium/Expanded layout (preview sizing, 680dp form-width constraint, scroll behavior).

## 24.3 Gradle commands and cadence

**Per-block fast checks:** `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` after every block in §23.

**End-of-feature full checks** (after Block 12, before Block 13/14): `./gradlew clean`, `./gradlew testDebugUnitTest`, `./gradlew connectedDebugAndroidTest` (or the relevant `pixel2Api*` Gradle Managed Device task(s) already configured in `app/build.gradle.kts`, per this repo's existing convention of using Managed Devices for instrumentation coverage across API 29/33/35/36), `./gradlew lintDebug`.

**Release checks** (Block 10 onward): `./gradlew assembleRelease`, `./gradlew bundleRelease`, plus a manual release-artifact review scoped to what is actually enforceable (§15's "what release verification can actually check" list — VCS/tracked-source absence, no manifest placement, no URL placement, no logging, no unnecessary resource/config duplication) rather than a claim that the key is undiscoverable in the compiled artifact (it is not, and this plan does not claim otherwise), and confirmation that R8/resource-shrinking still succeeds with the two new dependencies.

No lint baseline is introduced, no test is disabled, and no failure is suppressed at any step.

---

# 25. Real-Device Validation

The following cannot be verified by Gradle/CI and must be checked on physical hardware before release:

- Tilt thresholds/hysteresis final tuning (§7.3's placeholder constants) — adjust `THRESHOLD_DEGREES`/`REARM_DEGREES` for a natural, non-jittery, non-laggy feel.
- Portrait device orientation — preview, gestures, and date badge all remain correct.
- Landscape device orientation — same, plus Compact-height scroll behavior (§6.3).
- Sensor-unavailable behavior on a real device lacking `TYPE_ROTATION_VECTOR` if such a device is available in the test matrix; otherwise validated via the forced-`isAvailable()==false` test double from Block 3.
- Swipe/scroll coexistence feel — confirm the structural resolution in §6.3 feels natural, not just structurally non-conflicting.
- Background/resume — send the app to background mid-upload (Home button, not Back) and confirm the operation continues untouched, the screen does not reset, and the Custom Tab launches automatically the moment the app is foregrounded again once ready (not before).
- Resume from an unrelated app during an active or idle operation — confirm this never triggers the Custom-Tab-return reset (§14.3's `CustomTabAwaitState` distinction).
- Custom Tab launch/return — confirm the actual installed browser/Custom-Tab-provider on a real device launches and returns correctly, and that `WackelbildScreen`'s reset-to-Reference/re-enable-toggle behavior fires exactly once on the genuine return, not on a later unrelated resume.
- Slow network behavior — throttled network (Android Studio profiler or a real poor-connectivity environment) exercises the retry/backoff and "Übertragung nicht möglich" paths realistically.
- Cancellation during upload — confirm mid-upload Back-cancel actually stops the upload (not just the local coroutine) and cleans up temp files, on a real device with real latency.
- Date-badge corner-radius visual sign-off (§8.3) on at least one real device at both preview and full print-resolution scale.

---

# 26. DeinWackelbild Pilot Acceptance

Executed only in Block 15, against the real installed pilot endpoint, with real credentials. Not executed during this planning gate.

**Checks required by the supplied partner API (spec §51), each mapped to what this plan must demonstrate:**

1. Landscape image pair correctly prefilled — validates `WackelbildDimensionResolver`'s aspect-ratio preservation for landscape sessions.
2. Portrait image pair selects Portrait correctly — same, for portrait sessions.
3. Retry after a simulated connection interruption does not create a duplicate — validates the idempotency-key reuse in §14.2/§14.6.
4. Over-limit source files handled correctly — validates §10.3's size-enforcement loop against the real API's actual `413` behavior.
5. Invalid/non-JPEG files rejected correctly — not expected to occur (output is always a fresh valid `Bitmap.compress()` JPEG), but the `415` mapping (§14.6) must still be confirmed reachable and correctly handled if the real API ever returns it.
6. Expired handoffs cannot be reused — validates the `410`/`403` → new-handoff mapping (§14.6).
7. Test order carries the internal partner identifier `sameview` — validates `CreateHandoffRequest.partner` (§14.1).
8. Customer email and invoice expose neither partner nor handoff tokens — external to this app's code; SameView's own responsibility is limited to never sending anything beyond what §14.1's minimal request already sends (spec §27).
9. Checkout completes without a SameView return flow — validates §16's "no deep link, no order callback" design.

**Additional SameView-specific validation for this block:** crop parity (visual comparison against `reference.jpg`/`capture.jpg`), date overlay (WYSIWYG preview-vs-print), metadata stripping (real uploaded-file inspection if the pilot process allows it), fallback behavior (forced on a legacy v2-v4 test session), Custom Tab return (§16), re-ordering (a second explicit CTA press after a completed flow creates a genuinely new handoff, spec §15).

**Requires Olaf/DeinWackelbild cooperation:** checks 3, 4, 6, 7, 8, 9 above (anything requiring server-side behavior confirmation, order-system inspection, or coordinated test scenarios) — these cannot be executed unilaterally from the SameView side. Checks 1, 2, 5 and the SameView-specific validations can be largely self-verified from the SameView side against the real endpoint, with Olaf's confirmation only needed if an unexpected response shape is encountered.

---

# 27. Release / Privacy / Play Compliance

Dedicated release-readiness checklist, not buried in implementation notes:

- **Privacy Policy update/review** — required; this repository cannot author or confirm the final policy wording (external, non-technical).
- **Google Play Data Safety review/update** — required; the existing `RELEASE_HARDENING_AUDIT_V2.md §04` already documents this form as open independent of this feature (Gate 1 finding) — this feature adds a new data category (uploaded images) that must be reflected there before release.
- **`RELEASE_HARDENING_AUDIT_V2.md` (or a successor audit) update** — required; specifically the "kein INTERNET-Permission" positive claim (Executive Summary point 15) needs correction once `INTERNET` is added in Block 10.
- **`IMPLEMENTATION_NOTES.md`** — "The app has no INTERNET permission" line needs correction (§28).
- **Re-check of "offline/no INTERNET/no uploads" wording** — `docs/CLAUDE_PROJECT_INSTRUCTION.md`'s PRIVACY/PLAY COMPLIANCE section already anticipates and permits this exception (Gate 2); no further edit to that document is expected unless implementation reveals a real deviation from the approved behavior.
- **Partner/commission disclosure review** — required, explicitly left as a compliance-review item by the spec itself (§28) and not resolved here.
- **Manifest review** — the single `INTERNET` addition, scoped exactly as planned in §21, with no other permission/manifest change.
- **Release artifact inspection** — the enforceable partner-key exposure checks from §15/§24.3 (VCS/tracked-source absence, no manifest/URL/log placement — explicitly **not** a claim that the compiled key is undiscoverable, which it is not), HTTPS-only behavior confirmation (base URL is `https://...` per spec §50; no HTTP fallback is planned anywhere in `DeinWackelbildApiClient`), no cleartext traffic (no `usesCleartextTraffic="true"`/network security config permitting cleartext is planned — the app's current absence of any network security config is fine since the OS default already disallows cleartext on API 28+, which is below this app's `minSdk 29`).

No legal conclusion is asserted anywhere in this plan; every item above is marked as a review requirement for the appropriate external owner (product/legal/Play Console access).

---

# 28. Documentation Updates

| Document | Change | At which block |
|---|---|---|
| `docs/IMPLEMENTATION_NOTES.md` | New "DeinWackelbild" status entries per completed block (following this file's existing per-feature entry convention, e.g. the "Share Comparison Image" section's block-by-block status log); correction of the "no INTERNET permission" line once Block 10 lands | Throughout, finalized at Block 14 |
| `docs/COMPARE_FLOW_V1.md` | §43 Export-dropdown structure updated to list the third item + divider | Block 1 |
| `docs/RELEASE_HARDENING_AUDIT_V2.md` | New addendum/finding entry documenting the INTERNET-permission change and its justification, consistent with this document's existing addendum style | Block 14 |
| `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md` | **No change planned.** Only touched if implementation discovers a real, unavoidable contract conflict (none found during planning) | N/A unless triggered |
| `docs/CLAUDE_PROJECT_INSTRUCTION.md` | **No further change planned** beyond Gate 2's addendum, unless implementation reveals actual behavior diverging from what that addendum already approved | N/A unless triggered |

No historical document is rewritten.

---

# 29. Risk Register

| Risk | Severity | Mitigation | Verification |
|---|---|---|---|
| `INTERNET` capability/release impact | High | Governance addendum already in place (Gate 2); permission added only in Block 10, immediately followed by a full release-build check | `assembleRelease`/`bundleRelease` pass; manual review that no other code path gained implicit new network capability |
| Partner key extractability | Medium — **accepted limitation, not something release-artifact scanning can eliminate** (§15 Correction F) | Never committed, never in a URL, never logged, no logging interceptor at all, narrow create-only scope; rotation and rate-limiting are server-side responsibilities | Manual review against the enforceable checklist in §15/§24.3 during Block 14 — explicitly not a secrecy proof |
| First network client in this app | Medium | Small, well-known library (OkHttp); fully interface-wrapped for testability (§13.1); no MockWebServer/real-socket dependency in unit tests | Unit tests against fake client; manual pilot pass (§26) for the real integration |
| Upload cancellation correctness | Medium | Single coroutine, standard `Job.cancel()` + `finally`/`NonCancellable` cleanup, same pattern as existing `VideoExportPipeline` | `WackelbildViewModelTest` cancellation-state tests; real-device cancellation-during-upload test (§25) |
| Process/background lifecycle | Low-Medium | No WorkManager/service; explicit "no reconstruction after process loss" behavior matches spec exactly | Real-device background/resume test (§25) |
| Background/Custom-Tab state confusion (Correction C) | Medium | Explicit `CustomTabAwaitState` marker (§14.3) distinguishes an ordinary foreground resume from an actual Custom Tab return; Custom Tab only launches while foregrounded, never from background | `WackelbildViewModelTest` lifecycle-distinction tests (§24.2); real-device background/resume and unrelated-app-resume tests (§25) |
| HQ memory/OOM risk | Medium | Both bitmaps genuinely held simultaneously per pair-level size attempt (§9.4/§10.3, unavoidable for a pair-level decision), but only one attempt generation alive at a time, encoded straight to temp files (no duplicated in-memory buffers) | Manual large-image real-device test |
| Reference-source true-resolution calculation (Correction A) | Medium | `WackelbildDimensionResolver` derives the reference side's genuine max scale from `ReferenceRenderer`'s own fill/fit + overlay-scale math (§10.2), not from `reference-original.jpg`'s raw pixel dimensions alone; output may legitimately be smaller than the viewport when the reference source is the weaker side | `WackelbildDimensionResolverTest` — weaker-reference-source cases, no-upscale assertion, sub-viewport-output-allowed assertion |
| JPEG size-limit enforcement (Correction E) | Medium | Bounded, pair-level, print-quality-first algorithm (§10.3): 2 high-quality steps × 4 dimension levels = 8 attempts max, both images always resized together, encoded to temp files (no large in-memory buffers) | `WackelbildDimensionResolverTest`/pair-level size-enforcement unit tests with synthetic oversized inputs |
| Crop parity (Correction B) | High | Reference side reuses proven, already-tested code unchanged; Capture side is now a deterministic algorithm backed by direct repository evidence (CameraX `ViewPort`/`UseCaseGroup` wiring + matching viewport-measurement code, §9.3) with a defensive fail-safe into the already-approved fallback UX for the (architecturally unreachable) mismatch case — no runtime-test-driven algorithm choice remains | Pixel-comparison unit/instrumentation tests against `reference.jpg`/`capture.jpg`; defensive-guard unit test with a synthetic ratio-mismatch input |
| Sensor jitter / false switches | Medium | Hysteresis state machine (§7.3), thresholds explicitly marked as placeholders pending real-device tuning | Real-device tilt test pass (§25) |
| Gesture conflict (swipe vs. scroll) | Medium (resolved structurally) | Preview kept outside the scroll container (§6.3) — no runtime arbitration needed | Manual scroll/swipe coexistence check on Compact and Compact-height devices (§25) |
| Temporary-file leakage | Low | `cacheDir`-only, sweep-on-entry, cleanup on every terminal state (§11) | Instrumentation test asserting no leftover files after each terminal path |
| Metadata leakage | Medium | Structural guarantee (no `ExifInterface` write call exists in the new code) + explicit instrumentation assertion, mirroring the existing Share Image metadata test pattern | `WackelbildPrintRendererInstrumentedTest` |
| Custom Tab failure | Low | Explicit `OpenFailedWithCheckoutUrl` state, no re-upload, retry-open only (§16) | Unit test on the failure branch; real-device Custom-Tab-provider-absent scenario if feasible |
| API drift (installed pilot vs. supplied contract) | Medium (external) | State machine isolates the assumption points (§14.2's ready-detection note); the header/retry/timeout contract facts (§30) are fixed by the supplied spec and only need drift-checking, not re-derivation; manual pilot validation required before release (§26) | Manual pilot acceptance checklist (§26) |
| External locale support | Low | `WackelbildLocaleMapper` takes the supported set as data, not hardcoded logic (§17.2) | Confirmed against real matrix before release (§30) |
| Play/privacy disclosure | Medium (external, non-technical) | Explicitly flagged for release-block review (§27), not silently assumed complete | Manual Play Console / Privacy Policy review, outside this repository's scope |

---

# 30. Open External Dependencies

Fixed by the supplied DeinWackelbild V1 API contract (§50) and **not** open questions (Correction D) — restated here only to flag possible drift between the supplied written contract and the actual installed pilot, not to re-derive them: the `X-DWB-Partner-Key` header name (§15), the up-to-three-retries-with-increasing-delay policy (§14.6), the ≥60s upload timeout minimum (§13.3), and the same-Idempotency-Key-on-Create-retry rule (§14.2).

Items that genuinely cannot be resolved from repository evidence and require external input beyond the supplied written API contract:

- Whether the actual installed pilot endpoint's behavior matches the supplied contract exactly, especially whether `checkout_url` becomes available on the second upload's response or requires a separate poll call (§14.2's ready-detection assumption) — the supplied contract does not explicitly state this either way.
- Exact backoff intervals between the three documented retry attempts, if Olaf's installed implementation imposes constraints beyond the contract's "increasing delay" (§14.6 placeholder: 1s/2s/4s).
- DeinWackelbild-supported locale matrix (§17.2).
- Final English product wording sign-off (§17.1's recommendation is a proposal, not a locked decision).
- CI/release-pipeline mechanism for injecting `DEINWACKELBILD_PARTNER_KEY` in release builds (§15) — no CI configuration file was found/inspected as part of this repository-scoped plan.
- Whether a separate debug vs. release/production key pair is needed (§15).
- Privacy Policy / Google Play Data Safety / partner-commission-disclosure content (§27) — legal/compliance, not technical.
- Real-device tilt threshold/hysteresis final tuning values (§7.3).
- Real-device badge corner-radius final visual sign-off (§8.3's proposed `boxHeight × 0.25` default).

---

# 31. Final Implementation Sequence

Block 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12 → 13 → 14 → 15, exactly as ordered and justified in §23. This sequence was chosen specifically (not copied from a template) because:

- Blocks 1-6 are entirely local, offline, permission-free, and independently testable — the vast majority of the feature's genuine complexity (gesture arbitration, crop-parity, dimension/size algorithms) is de-risked before a single byte of network/manifest/dependency risk is introduced.
- Blocks 7-9 introduce network *code* and *build config* while remaining **unreachable** (no `INTERNET` permission yet), so they can be fully unit-tested against fakes with zero risk of an accidental real network call during development.
- Block 10 is the single, isolated, easily-reviewed point where the release-critical manifest/dependency change lands — deliberately made as small and late as possible.
- Blocks 11-12 are where real end-to-end behavior first becomes possible, after every component has already been independently proven.
- Blocks 13-15 close out testing, compliance, and external validation without touching product code further.

---

# 32. Definition of Done

The feature is implementation-complete and release-ready only when **all** of the following hold:

- Every UX acceptance criterion in `DEINWACKELBILD_INTEGRATION_V1.md §47` (UX), `§48` (Image/Privacy), and `§49` (Lifecycle/Security) is verifiably true, not merely asserted.
- Every row of §14.6/§18's error/retry mapping is exercised by at least one automated test and, where feasible, one manual real-device/real-API check.
- `testDebugUnitTest` and the full `connectedDebugAndroidTest` (or equivalent Managed Device) suite are green, including every pre-existing test unmodified and passing.
- `assembleRelease`/`bundleRelease` succeed with R8/resource-shrinking active and no suppressed warnings introduced for this feature's code.
- The manifest diff is exactly the one planned `INTERNET` permission (plus whatever `androidx.browser` merges in, inspected and accepted, not assumed).
- The partner key satisfies every enforceable check in §15 (not committed, not in tracked source, not in the manifest, not in a URL, not logged) — understood explicitly as a check of these specific properties, not a claim that the compiled key is secret or undiscoverable.
- No transfer JPEG ever produced by a test or manual run contains GPS/EXIF/device/session metadata, verified by instrumentation test.
- No persisted session/original file was ever modified by any test or manual run.
- Every item in §24.3/§27's checklists is either checked off or explicitly and visibly deferred to a named external owner — none silently skipped.
- §25/§26's full real-device and DeinWackelbild pilot acceptance checklists are complete, or explicitly and visibly deferred with owner and reason.
- `docs/IMPLEMENTATION_NOTES.md`, `docs/COMPARE_FLOW_V1.md`, and `docs/RELEASE_HARDENING_AUDIT_V2.md` (or its successor) reflect the shipped feature accurately.

No implementation begins until this plan is reviewed and explicitly approved.
