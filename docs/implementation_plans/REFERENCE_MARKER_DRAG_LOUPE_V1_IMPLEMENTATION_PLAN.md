# Reference Marker Drag Loupe — Implementation Plan

**Spec:** `REFERENCE_MARKER_DRAG_LOUPE_V1.md` Rev 2 (2026-06-30)

**Status:** Pre-implementation — not started.

---

## 1. Feature Summary

### Problem

When a user drags a reference marker, their finger covers the exact image pixel they are trying to place. For precision alignment on small targets (roof edges, window corners, mountain peaks) the user cannot confirm whether the marker is on the correct pixel.

### Loupe behavior

While a marker drag is in progress in Marker Edit Mode, a circular magnified window (120 dp diameter) appears above the finger. It displays a 2× magnified crop of the reference image bitmap centered on the true marker normalized coordinate. A small marker indicator is drawn at the loupe center. The loupe disappears immediately on finger lift. There is no animation.

Magnification is 2× relative to the current overlay display scale, clamped to an effective scale range of [1.0, 6.0] image pixels per screen pixel.

The loupe position defaults to above the marker, clamped to remain fully within the edit-mode viewport, with a 88 dp reservation at the bottom for the Done button area. If above-position would overlap the marker, the loupe moves below.

### Non-goals

The loupe is strictly transient UI:

- Never persisted; never in `ReferenceMarkersState`, `ReferenceMarker`, `metadata.json`, or any export
- No effect on session files, capture output, compare rendering, video export, or backup
- Not visible in CompareScreen
- Does not appear during long-press-create or long-press-delete warning states
- Does not appear outside Marker Edit Mode
- Does not intercept pointer events or modify marker coordinates

---

## 2. Files Expected to Be Touched

### Production files

| File | Reason | Estimated scope |
|---|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/camera/ReferenceMarkerOverlay.kt` | All loupe logic lives here: loupe constants, bitmap loading `LaunchedEffect`, drag state tracking (`isDragging`, `draggingMarkerNormalizedPos`), loupe position computation, loupe Canvas rendering, loupe image crop geometry | Large — single-file, self-contained additions |
| `app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt` | (1) Add `referenceUri = referenceUri` parameter at the `ReferenceMarkerOverlay` call site (line ~680). (2) Update `MarkerEditBorder` composable and its call site to accept image metadata, `displayMode`, `overlayScale`, `overlayOffsetX/Y`, and viewport size; compute the visible image rect via the helper (§2.5) and draw the border at that rect clipped to the viewport instead of the hardcoded `9f/16f` aspect ratio. (3) Empty-state hint placement update: if centered via the overlay composable, pass the computed visible image rect bounds. | Small–Medium — `referenceUri` arg (minimal) + `MarkerEditBorder` geometry (small) |

### Test files

| File | Reason | Estimated scope |
|---|---|---|
| `app/src/androidTest/java/com/isardomains/sameview/ui/camera/ReferenceMarkersOverlayUITest.kt` | Add 9 mandatory loupe instrumentation tests; move gesture-timing-brittle tests to manual validation | Medium — 9 new test methods |

### Files that must NOT be touched

| File | Reason |
|---|---|
| `ReferenceMarkersState.kt` | Spec §3 explicitly: loupe has no entry in the `ReferenceMarkersState` data class, not part of `ReferenceMarker`, `markersExist`, `markersVisible`, or `isEditModeActive` |
| `CameraViewModel.kt` / any ViewModel | Spec §3/§4/§10: no ViewModel state updates for loupe display; forbidden during drag |
| `ReferenceMarkerDefaults` (within `ReferenceMarkerOverlay.kt`) | Spec §9: loupe-internal marker indicator uses loupe-specific constants that must NOT be added to `ReferenceMarkerDefaults`, which remains the single source of truth for the full-size on-screen marker only |
| `ReferenceMarkersControlsTest.kt` | Tests overlay controls (Done button, menu items) — no loupe interaction |
| `ReferenceMarkersViewModelTest.kt` | ViewModel-layer tests — loupe has no ViewModel state |
| `ReferenceMarkerOverlayTest.kt` | JVM unit tests for `normalizedToScreen`/`screenToNormalized` pure functions — these are not changed |
| Any session storage, metadata, or rendering files | Spec §2/§13: loupe has no effect on any output pipeline |
| Any export, compare, or video pipeline files | Same reason |

**Coordinate functions are immutable:** `normalizedToScreen()` and `screenToNormalized()` in `ReferenceMarkerOverlay.kt` must not be modified for any loupe, border, or hint change. The visible image rect (§2.5) is computed from the same input parameters these functions use, but as a separate new computation added alongside — not a refactoring of the existing path.

---

## 2.5 Visible Image Rect Helper Strategy

A single helper computation — `computeVisibleImageRect` — is required by three components that must now align to the same boundary:

1. **`MarkerEditBorder`** (in `CameraScreen.kt`) — to position and size the blue border
2. **Empty-state hint** (in `ReferenceMarkerOverlay.kt`) — to center within the image rect rather than the full viewport
3. **Loupe position clamping** (in `ReferenceMarkerOverlay.kt`) — to clamp the loupe to the image rect instead of the full viewport

**Do NOT implement this helper yet.** This section documents the required strategy only.

When implemented, the helper must:

- **Accept:** `viewportWidth: Float`, `viewportHeight: Float`, `imageWidth: Float`, `imageHeight: Float`, `displayMode: ReferenceImageDisplayMode`, `overlayOffsetX: Float`, `overlayOffsetY: Float`, `overlayScale: Float`
- **Compute:** the transformed visible image rectangle in viewport-local pixels, then intersect with `[0, 0, viewportWidth, viewportHeight]`
- **Use:** the same `baseScale` formula already present in `normalizedToScreen`:
  - `COMPARE_WITH_PREVIEW`: `baseScale = max(vW/iW, vH/iH)`
  - `SHOW_FULL_IMAGE`: `baseScale = min(vW/iW, vH/iH)`
- **Derive image corners:**
  - `imageLeft = vW/2 − (iW * baseScale * overlayScale)/2 + translationX`
  - `imageTop  = vH/2 − (iH * baseScale * overlayScale)/2 + translationY`
  - `imageRight  = imageLeft + iW * baseScale * overlayScale`
  - `imageBottom = imageTop  + iH * baseScale * overlayScale`
  - (translationX/Y clamped per display mode, matching `normalizedToScreen`)
- **Clamp to viewport:** intersect with `[0, 0, vW, vH]` so the result never exceeds the viewport
- **Return:** `left`, `top`, `right`, `bottom` in viewport pixels

The helper can be a `private fun` in `ReferenceMarkerOverlay.kt` for the loupe and hint path. For `MarkerEditBorder` in `CameraScreen.kt`, either pass the precomputed rect in as a parameter, or duplicate the short computation inline — both are acceptable since the formula is a single arithmetic expression, not business logic.

**Do NOT change `normalizedToScreen` or `screenToNormalized`.**

---

## 3. Release-Unit Breakdown

Each unit is independently deployable and testable. Units 1→4 are strictly ordered (each depends on the previous). Unit 5 runs alongside Unit 4 or after it.

---

### Release Unit 1 — Bitmap Loading Infrastructure

**Scope:** Add `referenceUri: Uri?` as a new parameter to `ReferenceMarkerOverlay`. Add local Compose state `var loupe­Bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }`. Add a `LaunchedEffect(referenceUri)` that loads the bitmap on the IO dispatcher with `inSampleSize` downsampling targeting 1024px on the longest side using `BitmapFactory.Options`. Recycle and clear on `referenceUri` change. Add a second `LaunchedEffect(isEditModeActive)` that recycles and nulls the bitmap when Edit Mode exits (OQ-3 resolution). Add `referenceUri = referenceUri` at the `ReferenceMarkerOverlay` call site in `CameraScreen.kt`.

**Key constraints:**
- Bitmap decode is forbidden during drag (spec §10). Loading happens in `LaunchedEffect`, which runs outside the `pointerInput` coroutine.
- No new Bitmap allocation during drag.
- If bitmap is null when drag starts, the loupe renders background-only (graceful degradation — spec §10).
- The `LaunchedEffect(referenceUri)` is already guarded: `ReferenceMarkerOverlay` is only composed when `referenceUri != null` (CameraScreen line 677).

**Files:** `ReferenceMarkerOverlay.kt`, `CameraScreen.kt`

**Risks:**
- Incorrect `inSampleSize` computation → bitmap too large or too small. Mitigate: clamp `inSampleSize` to a power of 2 ≥ 1.
- `SecurityException` accessing the URI after the user navigates away. Mitigate: wrap decode in `try/catch`, leave bitmap null on failure.
- Bitmap not recycled if Edit Mode is entered and exited rapidly. Mitigate: `LaunchedEffect` cleanup block runs before re-launch; include explicit `bitmap.recycle()` in the cleanup block of the `LaunchedEffect(isEditModeActive)`.

**Tests (Unit 1):**
- No targeted instrumentation tests for this unit — internal bitmap state is an implementation detail not tested directly.
- Regression: existing edit mode entry/exit behavior in `ReferenceMarkersOverlayUITest` unchanged.
- Manual: open Edit Mode with a real reference image; confirm loupe shows image content on the first drag (not background-only); see §6.

---

### Release Unit 2 — Drag State Tracking

**Scope:** Add two local Snapshot state variables inside `ReferenceMarkerOverlay`:

```kotlin
var isDragging by remember { mutableStateOf(false) }
var draggingMarkerNormalizedPos by remember { mutableStateOf<Pair<Float, Float>?>(null) }
```

In the existing `pointerInput` handler, in the marker-drag branch (`nearestMarker != null` inside the `"dragged"` case):
- Set `isDragging = true` immediately when drag branch is entered (before the drag loop).
- On each pointer event where `onMoveMarkerState.value(...)` is called, also set `draggingMarkerNormalizedPos = Pair(clamped.first, clamped.second)`.
- When the drag loop exits (finger lift), set `isDragging = false` and `draggingMarkerNormalizedPos = null`.

These writes trigger recomposition scoped to `ReferenceMarkerOverlay` — not CameraScreen-wide (spec §4/§10 boundary is maintained because these are Snapshot state values local to the overlay composable).

**Files:** `ReferenceMarkerOverlay.kt` only.

**Risks:**
- **Marker drag regression** (highest risk in this unit): The existing `onMoveMarkerState.value(...)` call path is unchanged. The new state writes are additive. Verify via gesture tests that `onMoveMarker` still fires correctly with the correct coordinates.
- Drag state not cleared if pointer is lost mid-drag (e.g., system gesture interruption). Mitigate: the drag loop's `break@loop` conditions (no pointer found, or pointer not pressed) will exit cleanly, and the `isDragging = false` assignment placed after the loop will execute.
- The `draggingMarkerNormalizedPos` update is only written when `normalized != null` (inside the `if (normalized != null)` guard at line ~378). If `normalized` is null (pointer outside bounds), the last valid position is held until lift — this is acceptable loupe behavior during out-of-bounds drag.

**Tests (Unit 2):**
- Targeted: `loupe_appearsWhileDraggingMarker` — simulate marker drag, assert `marker_drag_loupe` node is displayed.
- Targeted: `loupe_disappears_afterDragEnds` — simulate lift after drag, assert loupe node does not exist.
- Targeted: `loupe_notVisible_whenNotDragging` — no drag active, assert loupe node does not exist.
- Regression: existing marker drag tests in `ReferenceMarkersOverlayUITest` must all still pass (verified by full connected test run).

---

### Release Unit 3 — Loupe Position and Container Rendering

**Scope:** Add loupe-specific private constants at the top of `ReferenceMarkerOverlay.kt` (inside the file, NOT inside `ReferenceMarkerDefaults`):

```kotlin
private val LOUPE_DIAMETER_DP = 120.dp
private val LOUPE_BORDER_STROKE_DP = 1.5.dp
private val LOUPE_FINGER_OFFSET_DP = 16.dp
private val LOUPE_DONE_AREA_HEIGHT_DP = 88.dp
private val LOUPE_INDICATOR_RING_DP = 16.dp
private val LOUPE_INDICATOR_STROKE_DP = 1.5.dp
private val LOUPE_INDICATOR_DOT_DP = 3.dp
```

Add a loupe rendering layer inside the existing `Box` in `ReferenceMarkerOverlay`, positioned after the marker `Canvas` and before the empty-state hint `Box`. This ordering ensures the loupe is above markers and below the empty-state hint.

The layer is a `Box` with `testTag("marker_drag_loupe")`, visible only when `isDragging && draggingMarkerNormalizedPos != null`. Inside it, a `Canvas` draws:

1. **Position computation** (per spec §7):
   - Convert `draggingMarkerNormalizedPos` to screen coordinates via `normalizedToScreen(...)`.
   - Default loupe center: `loupeCenterX_default = markerScreenX`, `loupeCenterY_default = markerScreenY - (loupeDiameterPx / 2) - offsetBelowFingerPx`.
   - Compute visible image rect via the §2.5 helper: `imageLeft`, `imageTop`, `imageRight`, `imageBottom` (already clipped to viewport).
   - Clamp bounds (spec §7, updated): primary bounds use the image rect; fall back per-axis to viewport when image rect is smaller than loupe diameter. `clampBoundsBottom` always subtracts `doneButtonAreaPx` from the effective bottom (image rect bottom or viewport bottom).
   - Final: `loupeCenterX = clamp(default, clampLeft + loupeRadiusPx, clampRight + loupeRadiusPx)`, `loupeCenterY = clamp(default, clampTop + loupeRadiusPx, clampBottom + loupeRadiusPx)`.
   - Fallback-below check: if `loupeCenterY + loupeDiameterPx/2 > markerScreenY - minGap`, compute `loupeCenterY_below = markerScreenY + (loupeDiameterPx / 2) + offsetBelowFingerPx` clamped to bounds. Use above-position if below also fails.

2. **Canvas draw sequence** in Unit 3 (image content deferred to Unit 4):
   - Pre-fill circle with `SameViewOverlayScrim` (`0xB3000000`).
   - Draw drop shadow via `BlurMaskFilter` (6.dp blur, black 50% alpha) on border ring.
   - Draw white border ring (1.5.dp stroke, white at 90% alpha).
   - Draw loupe marker indicator at geometric loupe center: ring (16.dp diameter, 1.5.dp stroke, white) and center dot (3.dp diameter, `SameViewAccent`).

**Files:** `ReferenceMarkerOverlay.kt` and `CameraScreen.kt`.

`ReferenceMarkerOverlay.kt`: loupe constants, helper, drag state, loupe container and Canvas.
`CameraScreen.kt`: `MarkerEditBorder` geometry update; pass image rect info for hint if needed.

**Risks:**
- **Clamping edge cases**: Incorrect clamp math at image rect edges and corners. Mitigate: extract `computeVisibleImageRect` as an `internal fun` and unit-test it in `ReferenceMarkerOverlayTest.kt` with known viewport/image/scale combinations before wiring to Compose.
- **Image rect smaller than loupe**: If the visible image area is very small (extreme zoom-out), the loupe may need to fall back to the full viewport. Mitigate: per-axis fallback logic (§2.5). Verify with manual validation case "overlay scaled very small".
- **Fallback-below overlap check**: Use `loupeDiameterPx / 2` as minGap — if loupe bottom > `markerScreenY - loupeDiameterPx/2`, shift below. Fallback-below still uses image rect clamp bounds.
- **Done button area height**: 88.dp is the authoritative V1 default (spec §7/OQ-2). Always measured from viewport bottom, not image rect bottom. Manual validation required on large-font devices.
- **Canvas clipping**: The existing `Box` has `Modifier.clipToBounds()`. The loupe Canvas is inside the same Box, so it is naturally clipped. The clamping logic must keep the loupe within bounds to avoid clipping artifacts.
- **MarkerEditBorder regression**: Changing from fixed aspect ratio to computed image rect must not change the border on COMPARE_WITH_PREVIEW at overlayScale=1.0 (image fills viewport → rect = viewport). Verify with existing `editModeBorder_*` tests.

**Tests (Unit 3):**
- Targeted: `loupe_notVisible_outsideEditMode` — Edit Mode off, no loupe node.
- Targeted: `loupe_clamped_nearTopEdge` — drag near top → loupe within visible image rect top bound.
- Targeted: `loupe_clamped_nearBottomEdge` — drag near bottom → loupe above Done button area and within image rect bottom when possible.
- Targeted: `loupe_clamped_nearLeftEdge` — drag near left → loupe within image rect left bound when possible.
- Targeted: `loupe_clamped_nearRightEdge` — drag near right → loupe within image rect right bound when possible.
- Targeted: `loupe_clamped_imageSmallerThanLoupe` — SHOW_FULL_IMAGE with extreme zoom-out (image rect smaller than loupe diameter) → loupe falls back to viewport clamping; assert loupe remains inside viewport.
- Targeted: `editBorder_framesImageRect_whenLetterboxed` — SHOW_FULL_IMAGE with non-16:9 square image → assert border `boundsInRoot` matches image display rect, not full viewport rect.
- Targeted: `editBorder_framesViewport_whenImageFillsViewport` — COMPARE_WITH_PREVIEW at overlayScale=1.0 → border bounds match viewport (regression: no visual change expected).
- Targeted: `emptyHint_centeredInImageRect_whenImageSmallerThanViewport` — SHOW_FULL_IMAGE with square image → hint node is positioned within image rect bounds, not full viewport.
- Regression: existing `emptyStateHint_*` and `editModeBorder_*` tests must pass (update assertions where border semantics changed).

---

### Release Unit 4 — Loupe Image Content Rendering

**Scope:** Inside the loupe Canvas (added in Unit 3), before drawing the border and marker indicator, add the reference image crop draw call:

1. **Effective scale** (spec §6):
   - Compute `baseScale` inline using the same formula as `normalizedToScreen` (`max` for COMPARE_WITH_PREVIEW, `min` for SHOW_FULL_IMAGE, applied to `viewportWidth/imageWidth` and `viewportHeight/imageHeight`).
   - `effectiveScale = (baseScale * overlayScale * 2.0f).coerceIn(1.0f, 6.0f)`.

2. **Crop geometry** (spec §5):
   - `cropHalfPx = (loupeDiameterPx / 2f) / effectiveScale`
   - `cropLeft = markerNormX * imageWidthPx - cropHalfPx`
   - `cropTop = markerNormY * imageHeightPx - cropHalfPx`
   - `cropRight = cropLeft + 2f * cropHalfPx`
   - `cropBottom = cropTop + 2f * cropHalfPx`
   - `srcRect`: clamped to `[0, imageWidthPx] × [0, imageHeightPx]` — computes the visible image sub-region (spec §8).
   - `dstRect`: the corresponding sub-region of the loupe circle in canvas coordinates. When the crop exceeds image bounds, only the in-bounds portion maps to a sub-region of the loupe; the out-of-bounds area has already been filled with `SameViewOverlayScrim` (from Unit 3).

3. **Draw call** (spec §10):
   - `canvas.nativeCanvas.drawBitmap(loupeBitmap, srcRect, dstRect, null)` — no allocation per frame.
   - This runs only when `loupeBitmap != null`. When null, background-only fallback is already in place from Unit 3.

**Implementation note on `srcRect`/`dstRect` mapping for out-of-bounds fill:**

The src/dst mapping for partial image content:
- `srcRect = RectF(max(0, cropLeft), max(0, cropTop), min(imageWidthPx, cropRight), min(imageHeightPx, cropBottom))`
- Compute the fraction of the crop covered by the image on each side, and use it to derive `dstRect` as the corresponding fraction of the loupe circle.

**Files:** `ReferenceMarkerOverlay.kt` only.

**Risks:**
- **Crop geometry at low/high effectiveScale**: At `effectiveScale = 1.0` (clamped up from very low overlay zoom), `cropHalfPx` will be large (up to `loupeDiameterPx/2` image pixels). The image content will span many image pixels in the loupe. At `effectiveScale = 6.0` (clamped down), `cropHalfPx` will be small. Both extremes must produce a valid (non-zero, non-negative) `srcRect` and `dstRect`.
- **`imageWidthPx`/`imageHeightPx`**: These are the oriented bitmap dimensions, which may differ from the metadata dimensions (rotation applied by BitmapFactory). Confirm: `BitmapFactory` with no `inSampleSize` respects EXIF orientation? On API 29+, `ImageDecoder` does; `BitmapFactory` does NOT auto-rotate. Use `BitmapFactory` with explicit orientation handling OR use `ExifInterface` after decode to get oriented dimensions. **Decision**: the `metadata.orientedWidth`/`metadata.orientedHeight` are already orientation-corrected. The downsampled loupe bitmap may not be auto-rotated by BitmapFactory. Use `ReferenceImageMetadata.orientedWidth * sampleFraction` as the effective width for crop geometry, OR apply the EXIF rotation to the decoded bitmap before caching. Recommend: decode with `BitmapFactory`, then apply EXIF rotation matrix via `Matrix` if orientation != normal, creating an oriented cached bitmap. This is the only allocation, done once at load time.
- **Float precision**: Crop coordinates as `android.graphics.Rect` (integer) vs. `android.graphics.RectF` (float). The native `drawBitmap(Bitmap, Rect, Rect, Paint)` uses integer rects. Use `RectF` form: `canvas.nativeCanvas.drawBitmap(bitmap, srcRectF, dstRectF, paint)`.

**Tests (Unit 4):**
- Targeted: `loupe_doesNotModifyMarkerCoordinate` — drag marker to position P, verify `onMoveMarker` is called with normalized position P, not loupe center position.
- Manual: loupe shows reference image content; content is centered on marker position; image-edge dark fill behavior; see §6.
- Regression: `ReferenceMarkerOverlayTest.kt` JVM tests for `normalizedToScreen`/`screenToNormalized` must still pass (those functions are not modified).

---

### Release Unit 5 — Instrumentation Tests

**Scope:** Add 9 mandatory loupe instrumentation tests to `ReferenceMarkersOverlayUITest.kt`. A new `setLoupeOverlayContent()` helper sets up `ReferenceMarkerOverlay` with a marker in a draggable position and Edit Mode active.

**Mandatory automated tests (9):**

Appearance (4):
- `loupe_appearsWhileDraggingMarker` — simulate marker drag → assert `marker_drag_loupe` node displayed
- `loupe_notVisible_whenNotDragging` — no drag → assert loupe node does not exist
- `loupe_notVisible_outsideEditMode` — Edit Mode off → loupe node does not exist
- `loupe_disappears_afterDragEnds` — drag then lift → loupe node disappears

Position/clamping (4):
- `loupe_clamped_nearTopEdge` — marker near top → loupe top edge ≥ viewport top
- `loupe_clamped_nearBottomEdge` — marker near bottom → loupe bottom edge ≤ viewport height − 88dp
- `loupe_clamped_nearLeftEdge` — marker near left → loupe left edge ≥ 0
- `loupe_clamped_nearRightEdge` — marker near right → loupe right edge ≤ viewport width

Coordinate integrity (1):
- `loupe_doesNotModifyMarkerCoordinate` — drag to P, verify `onMoveMarker` called with P

**Optional / secondary automated test (not blocking in V1):**
- `loupe_notVisible_markerDrag_editModeOff` — drag without Edit Mode → no loupe (stable; not gesture-timing brittle; can be promoted to mandatory in a future pass)

**Manual / not automated in V1 (gesture-timing brittle or pixel-level):**
- `loupe_notVisible_duringLongPressCreate` — long-press on empty area → no loupe; brittle because long-press detection relies on the `withTimeoutOrNull` timer, making reliable automation in Compose tests fragile
- `loupe_notVisible_duringDeleteWarning` — long-press on marker → no loupe during warning; same reason
- `loupe_content_centeredOnMarkerPosition` — pixel/content verification of loupe crop; not automated in V1

**Infrastructure:**
- Clamping position tests use `onNodeWithTag("marker_drag_loupe").fetchSemanticsNode().boundsInRoot` to verify bounds.
- Gesture simulation uses `composeRule.onNodeWithTag(...).performTouchInput { ... }` with `down()` / `moveTo()` / `up()` to simulate marker drag.

**Files:** `ReferenceMarkersOverlayUITest.kt`

**Risks:**
- Touch input simulation in Compose test may not perfectly trigger the `awaitEachGesture` / `withTimeoutOrNull` path. Mitigate: use explicit timing (`advanceTimeBy`) if the long-press timeout interferes with drag classification.
- Clamping bound assertions require measuring the loupe node position in the test screen coordinate space. Mitigate: use `fetchSemanticsNode().boundsInRoot` and assert `.top >= 0`, `.left >= 0`, etc.

---

## 4. Risk Analysis

### Gesture risks

**Marker drag regression**
The existing `pointerInput` marker drag loop (the `nearestMarker != null` branch inside `"dragged"`) is modified only by appending `isDragging = true` before the loop and `isDragging = false; draggingMarkerNormalizedPos = null` after the loop, plus `draggingMarkerNormalizedPos = Pair(...)` inside the loop alongside the existing `onMoveMarkerState.value(...)` call. The `onMoveMarker` call path and coordinate computation are unchanged. Risk: LOW.

**Long-press regression**
The long-press branch (`gestureResult == null`) is entirely untouched. The loupe is driven only by `isDragging`, which is set exclusively in the drag branch. Risk: VERY LOW.

**Overlay drag regression**
The overlay pan/zoom branch (no `nearestMarker`) is entirely untouched. No loupe state is set in that branch. Risk: NONE.

### Performance risks

**Recomposition scope**
`isDragging` and `draggingMarkerNormalizedPos` are `remember { mutableStateOf(...) }` values local to `ReferenceMarkerOverlay`. Compose invalidates only the composables that read them. The loupe Canvas reads both; the marker Canvas reads neither. No parent composable reads them. CameraScreen-wide recomposition per pointer event is therefore not triggered. **Verify**: after implementation, check that the `CameraScreen` composition is not marked dirty on drag events. Risk: MEDIUM during implementation — requires careful verification.

**Bitmap memory**
One 1024px downsampled ARGB_8888 bitmap ≈ 4 MB peak. Recycled on Edit Mode exit (OQ-3) and URI change. Risk: LOW. If the reference image is very large (e.g., 20 MP), `inSampleSize` must bring it to ≤ 1024px max dimension; verify that `inJustDecodeBounds` is used to read dimensions before allocating.

**Decode timing**
Bitmap is loaded proactively when Edit Mode is entered (OQ-1). The `LaunchedEffect(referenceUri)` starts when the composable enters composition (which happens when Edit Mode is active and `referenceUri != null`). If the user immediately drags on first entering Edit Mode, the bitmap may not be ready; the loupe will show background-only (graceful fallback). Risk: LOW — acceptable per spec §10.

**Per-frame draw**
`canvas.nativeCanvas.drawBitmap(bitmap, srcRectF, dstRectF, paint)` — no allocation per frame. The RectF objects should be reused (not allocated per draw call). Mitigate: declare `srcRectF` and `dstRectF` outside the draw call (e.g., as `remember`-ed instances). Risk: LOW after mitigation.

### Layout risks

**Viewport edges**
The clamping formula uses `viewportWidth` and `viewportHeight` from `viewportSizeState.value` (already tracked via `Modifier.onSizeChanged`). The loupe diameter is a fixed dp value converted to px by `LocalDensity`. The viewport size in px is known. The clamping math is straightforward but must be verified at all four edges and all four corners. Risk: MEDIUM — manual validation checklist required.

**Done button area (88.dp)**
The 88.dp bottom reservation is the V1 authoritative default (spec §7/OQ-2). On devices with large accessibility font scales or unusual bottom insets, 88.dp may be insufficient, causing the loupe to visually overlap the Done button on bottom-edge drags. This is a known accepted risk per OQ-2. Flag for manual validation. Risk: MEDIUM (device-specific).

**Landscape**
The clamping logic uses the same formula with the same viewport dimensions. In landscape, `viewportWidth > viewportHeight`. No separate path needed (spec §7). Risk: LOW — manual validation covers landscape.

### Lifecycle risks

**Reference replace/remove**
When the user replaces or removes the reference image: `referenceUri` changes → `LaunchedEffect(referenceUri)` cleanup runs → bitmap is recycled and nulled. The overlay composable is conditionally rendered only when `referenceUri != null`; when it leaves composition, Compose disposes all effects. Risk: LOW.

**Edit mode exit**
`LaunchedEffect(isEditModeActive)` recycles bitmap when `isEditModeActive` becomes false. This is the OQ-3 resolution. If the user re-enters Edit Mode, `LaunchedEffect(referenceUri)` re-runs and reloads the bitmap. Risk: LOW.

**Rotation during drag**
`isDragging` and `draggingMarkerNormalizedPos` are `remember` (not `rememberSaveable`). They reset to `false`/`null` on rotation. A drag in progress at the moment of rotation will leave the loupe invisible after recomposition. This is correct: rotation invalidates the current drag state. Risk: VERY LOW.

---

## 5. Test Strategy

**Fail-fast order**: JVM unit tests → assembleDebug → focused instrumentation tests for each unit → full connected suite.

### Release Unit 1 (Bitmap loading)
- No targeted instrumentation tests — internal bitmap state is an implementation detail.
- Regression: all existing `ReferenceMarkersOverlayUITest` tests must still pass after the new parameter is added.
- Manual: open Edit Mode with a real reference image; confirm loupe shows image content on first drag; no crash; no persistent blank-loupe after bitmap has had time to load.

### Release Unit 2 (Drag state tracking)
- Targeted: `loupe_appearsWhileDraggingMarker`, `loupe_disappears_afterDragEnds`, `loupe_notVisible_whenNotDragging`.
- Regression: all existing marker drag gesture tests in `ReferenceMarkersOverlayUITest`; verify `onMoveMarker` callback semantics unchanged.

### Release Unit 3 (Position and container)
- Targeted: `loupe_notVisible_outsideEditMode`, all four clamping tests.
- Regression: `emptyStateHint_*` tests, `editModeBorder_*` tests.
- Manual: loupe visible above finger in center of viewport; loupe clamps correctly at all four viewport edges and corners; loupe shifts below marker when dragging near top edge.

### Release Unit 4 (Image content)
- Targeted: `loupe_doesNotModifyMarkerCoordinate`.
- Manual: loupe shows reference image content centered on marker position; image-edge dark fill behavior; see §6.
- Regression: `ReferenceMarkerOverlayTest.kt` JVM tests for pure coordinate functions must still pass.

### Release Unit 5 (9 mandatory tests)
- 9 mandatory tests listed in §3 Unit 5.
- Optional: `loupe_notVisible_markerDrag_editModeOff` (add if time permits; not blocking).
- Manual: long-press-create and long-press-delete warning behaviors; loupe content quality; see §6.

---

## 6. Manual Validation Checklist

### Portrait

**Center:**
- [ ] Loupe appears above finger during marker drag at center of viewport
- [ ] Loupe shows reference image content — crop is clearly visible, not background-only
- [ ] Crop appears centered on true marker position (center of loupe matches marker pixel)
- [ ] Loupe disappears immediately on finger lift

**All four viewport edges:**
- [ ] Top edge: loupe clamps to below viewport top; fallback-below triggered if marker near top
- [ ] Bottom edge: loupe stays above Done button area (88 dp clearance)
- [ ] Left edge: loupe clamps to right of viewport left
- [ ] Right edge: loupe clamps to left of viewport right

**All four viewport corners:**
- [ ] Top-left: loupe clamps in both axes
- [ ] Top-right: loupe clamps in both axes
- [ ] Bottom-left: loupe stays within bottom clearance and left bound
- [ ] Bottom-right: loupe stays within bottom clearance and right bound

### Landscape

**Center:**
- [ ] Loupe appears above finger; done button area reservation applies along height axis
- [ ] Image content visible in loupe

**All four edges:**
- [ ] Top edge: clamping correct
- [ ] Bottom edge: 88 dp clearance maintained in landscape layout
- [ ] Left edge: clamping correct in wider viewport
- [ ] Right edge: clamping correct

**All four corners:**
- [ ] Top-left, Top-right, Bottom-left, Bottom-right: all clamped correctly

### Zoomed reference image

**Low zoom (overlayScale ≈ 0.1):**
- [ ] `effectiveScale` clamped to 1.0; loupe shows large region of image
- [ ] Content is recognizable; no display artifacts

**Normal zoom (overlayScale ≈ 1.0):**
- [ ] `effectiveScale` ≈ 3–6 depending on baseScale; tight crop visible
- [ ] Marker indicator visible at loupe center

**High zoom (overlayScale very high):**
- [ ] `effectiveScale` clamped to 6.0; tight crop, few image pixels shown
- [ ] No crash; loupe still renders correctly

### Visible image rect — border and hint

**Landscape image in portrait viewport (SHOW_FULL_IMAGE):**

- [ ] Blue border frames the image area only — does not extend into top/bottom letterbox strips
- [ ] Empty-state hint (if no markers) is centered within the image area, not the full viewport
- [ ] Loupe clamps within the image rect; does not drift into the empty letterbox strips

**Portrait image in landscape viewport (SHOW_FULL_IMAGE):**

- [ ] Blue border frames the image area only — does not extend into left/right pillarbox strips
- [ ] Loupe clamps within the image rect

**Square image in portrait viewport (SHOW_FULL_IMAGE):**

- [ ] Blue border frames the square image area; letterbox strips above/below are outside the border
- [ ] Loupe clamped to image rect on top/bottom edges; no loupe intrusion into letterbox

**Overlay scaled smaller than viewport (overlayScale < 1.0, either display mode):**

- [ ] Blue border shrinks with the image — consistently smaller than the viewport
- [ ] Loupe clamped to the smaller image rect; stays inside the border

**Overlay panned partially outside viewport:**

- [ ] Blue border clips to the viewport edge (intersection of image rect and viewport)
- [ ] Loupe clamped to the clipped intersection; does not extend beyond viewport

**Image rect smaller than loupe diameter (extreme zoom-out):**

- [ ] Loupe falls back to viewport clamping; no crash; loupe remains inside viewport
- [ ] Loupe stays as close as possible to the image rect center

**COMPARE_WITH_PREVIEW at overlayScale = 1.0 (regression):**

- [ ] Blue border matches the full viewport — visually unchanged from previous behavior
- [ ] Loupe clamping behaves identically to the viewport-only path (image rect = viewport)

### Image-edge fill behavior

*(Marker position relative to the reference image bounds, not the viewport.)*

- [ ] Marker near left image edge: right portion of loupe shows image content; left portion shows dark (`SameViewOverlayScrim`) fill
- [ ] Marker near right image edge: left portion shows image; right shows dark fill
- [ ] Marker near top image edge: bottom portion shows image; top shows dark fill
- [ ] Marker near bottom image edge: top portion shows image; bottom shows dark fill
- [ ] Marker at image corner (e.g. top-left): one quadrant shows image; other three show dark fill
- [ ] No crop-shift artifact as marker crosses the image edge during drag

### Gesture safety (manual — not automated in V1)

- [ ] Long-press on empty area (long-press-create): no loupe appears at any point during or after the long-press; marker is created normally
- [ ] Long-press on existing marker (long-press-delete warning): no loupe appears while the marker shows the red warning ring; marker is deleted normally on lift
- [ ] Tap on marker: no loupe
- [ ] Tap on empty area: no loupe
- [ ] Overlay drag (1-finger on free area): no loupe
- [ ] Overlay pinch-scale (2-finger): no loupe

### Marker lifecycle

**Remove reference:**
- [ ] Edit Mode exits; loupe disappears; no crash on re-entry

**Replace reference:**
- [ ] Markers cleared; loupe works on new image; no stale content from old image

**Rotation:**
- [ ] Drag in progress at moment of rotation: loupe disappears after rotation; new drag works correctly

### Performance

**First drag after entering Edit Mode:**
- [ ] If bitmap not yet loaded: loupe shows background-only (graceful fallback); no crash; subsequent drags show image once loaded

**Rapid drag:**
- [ ] No jank during rapid finger movement; loupe follows drag smoothly

**Repeated drags:**
- [ ] Multiple drag/lift cycles: loupe appears and disappears correctly each time; no state leak

### Done button area

**Large font accessibility scale:**
- [ ] On a device with large font scale, verify loupe does not visually overlap Done button on bottom-edge drags (88 dp reservation may be tight — document result)

---

## 7. Documentation Impact

**`ALIGNMENT_POINTS_V1.md`:**
No amendment required. Spec §13 explicitly confirms no conflicts. The loupe is a new sub-feature not addressed by that document.

**`CAMERA_WORKFLOW_UX_V1.md`:**
No amendment required. Spec §13 explicitly confirms no conflicts. The loupe respects the Done button slot boundary defined in §3 of that document.

**`IMPLEMENTATION_NOTES.md`:**
Should receive a completion entry after implementation is verified. The entry should document:
- Which files were changed
- Test state at completion (unit + instrumentation)
- Manual validation result
- Known residual risk (88 dp clearance on large-font devices)

No other specification documents require amendment.

---

## 8. Test Commands

Run in this order (fail-fast):

```
# Step 1 — JVM unit tests (fast gate; catches compile errors and pure-function regressions)
./gradlew testDebugUnitTest

# Step 2 — Build verification (catches import and resource errors)
./gradlew assembleDebug

# Step 3 — Focused loupe test class (after Unit 5)
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceMarkersOverlayUITest

# Step 4 — Full connected suite regression check
./gradlew connectedDebugAndroidTest

# Step 5 — Release build verification
./gradlew assembleRelease
```

**Notes:**
- Steps 1 and 2 must be run after each release unit before proceeding to the next.
- Step 3 may be run after Release Unit 2 (even before all tests are written) to verify drag state tests pass.
- The known Samsung IS_PENDING/media-scanner flakiness in `MediaStoreWriterGpsTest` is pre-existing and does not signal a loupe regression. Pass 3/3 in isolation before accepting the full suite result.

---

## 9. Open Implementation Decisions

Only items genuinely unresolved after the spec.

### OD-1: Bitmap EXIF orientation handling

**Description:**
`BitmapFactory.decodeStream` does not auto-rotate based on EXIF orientation (unlike `ImageDecoder`). If the reference image has EXIF rotation (e.g., shot in portrait on a phone), the decoded bitmap may be in the wrong orientation. The crop geometry uses `metadata.orientedWidth`/`metadata.orientedHeight` (already orientation-corrected in the metadata reader). If the loupe bitmap is not rotated to match, the crop will draw the wrong image region.

**Recommendation:**
After decoding with `BitmapFactory`, read `ExifInterface` from the URI input stream, extract the EXIF orientation tag, and apply the corresponding rotation matrix via `android.graphics.Matrix` + `Bitmap.createBitmap(...)`. Cache the result as the oriented bitmap. This allocation is done once at load time (not per frame). The oriented bitmap dimensions then match `metadata.orientedWidth`/`metadata.orientedHeight`.

**Impact:** ~10 additional lines in the `LaunchedEffect` bitmap loading block. No per-frame impact.

---

### OD-2: Whether to extract `computeBaseScale` as an internal helper

**Description:**
The loupe crop geometry (Unit 4) needs `baseScale` — the same value computed inside `normalizedToScreen`. Currently `normalizedToScreen` computes it locally and does not return it.

**Recommendation:**
Compute `baseScale` inline in the loupe rendering code using the same one-liner formula already present in `normalizedToScreen`. This is acceptable duplication (a single arithmetic expression, not business logic). Do not refactor `normalizedToScreen` — it is a stable, tested function.

**Impact:** Two lines added in the loupe rendering block. Zero risk to existing code.

---

### OD-3: Placement of `testTag("marker_drag_loupe")`

**Description:**
Spec §11 requires `testTag("marker_drag_loupe")` for instrumentation test access. The loupe rendering is done via Canvas draw calls. A `Canvas` composable does not itself carry semantics. The `Box` wrapper containing the Canvas should carry the test tag.

**Recommendation:**
Apply `Modifier.testTag("marker_drag_loupe")` to the outermost `Box` of the loupe layer (the one conditionally shown when `isDragging`). The Canvas is nested inside. This makes the node findable by `onNodeWithTag("marker_drag_loupe")` in tests.

**Impact:** One modifier line on the loupe Box.

---

### OD-4: `RectF` reuse for per-frame crop draw

**Description:**
`srcRectF` and `dstRectF` are computed per drag frame inside the loupe Canvas. Allocating them inside the Canvas draw lambda creates garbage per frame.

**Recommendation:**
Declare `val srcRectF = remember { android.graphics.RectF() }` and `val dstRectF = remember { android.graphics.RectF() }` outside the Canvas lambda, inside `ReferenceMarkerOverlay`. Set their values inside the Canvas draw lambda with `.set(left, top, right, bottom)`. This eliminates per-frame allocation.

**Impact:** Two `remember` declarations added. Minor.

---

## 10. Revision History

### Revision 2 — 2026-07-01

**Visible image rect rule applied throughout (alignment with ALIGNMENT_POINTS_V1.md Rev 9 and REFERENCE_MARKER_DRAG_LOUPE_V1.md Rev 3):**

- §2 Production files: `CameraScreen.kt` scope expanded — `MarkerEditBorder` geometry change and empty-state hint placement added alongside the existing `referenceUri` argument change.
- §2 "Files that must NOT be touched": added explicit note that `normalizedToScreen()` and `screenToNormalized()` are immutable.
- §2.5 added: Visible Image Rect Helper Strategy. Documents the `computeVisibleImageRect` helper required by border, hint, and loupe clamping. Not yet implemented.
- Release Unit 3: clamp bounds updated to use image rect (with per-axis viewport fallback); files expanded to include `CameraScreen.kt`; risks section updated; tests expanded with 4 new targeted tests for border, hint, and image-smaller-than-loupe scenarios.
- §6 Manual Validation Checklist: new section "Visible image rect — border and hint" added with 8 scenarios covering letterboxed/pillarboxed images, overlay scaling, overlay panning, extreme zoom-out fallback, and COMPARE_WITH_PREVIEW regression.
