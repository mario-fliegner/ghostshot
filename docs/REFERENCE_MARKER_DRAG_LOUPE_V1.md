# Reference Marker Drag Loupe — REFERENCE_MARKER_DRAG_LOUPE_V1.md

## 1. Document Status

This document is the **authoritative UX and technical specification** for the Reference Marker drag-loupe feature.

It supplements `ALIGNMENT_POINTS_V1.md` without modifying it.

When this document conflicts with `ALIGNMENT_POINTS_V1.md`, **this document governs** for loupe-specific behavior. For all other marker behavior not addressed here, `ALIGNMENT_POINTS_V1.md` remains authoritative.

**Revision:** 2 (2026-06-30) — Contradiction fix in §4/§10 (drag state threading and performance boundary); §7/OQ-2 clarified (doneButtonAreaHeight default and UI risk).

---

## 2. Feature Overview

### Problem

When a user drags a reference marker, their finger covers the exact image area around the marker position. For precise placement on small alignment targets (roof edges, window corners, sign tops, mountain peaks) this creates a confidence problem: the user cannot clearly see whether the marker is on the correct pixel.

### Solution

Display a temporary **drag loupe** while a marker is being dragged. The loupe shows a magnified crop of the reference image centered on the true marker position, lifted above the finger in a circular overlay.

The user sees the hidden image detail while the finger drag remains the direct control. No interruption to the drag interaction. No change to the stored coordinate.

### Non-Goals

The loupe is strictly transient UI. It:

- is never saved
- never enters session metadata
- never enters `metadata.json`, `reference.jpg`, `capture.jpg`, or any export format
- has no effect on capture output
- has no effect on compare rendering
- has no effect on the share image pipeline
- has no effect on video export
- has no effect on session backup export
- is not visible in CompareScreen
- does not appear during long-press-create
- does not appear during long-press-delete warning state
- does not appear when not dragging
- does not appear outside Marker Edit Mode

---

## 3. Ownership

The loupe is a **stateless display element** driven by the active drag gesture. It has no persistent state, no ViewModel state, no stored coordinates, and no interaction with session ownership.

When the drag gesture ends (finger lift), the loupe disappears immediately. No cleanup needed.

The loupe does not constitute an entry in the `ReferenceMarkersState` data class. It is not part of `ReferenceMarker`, `markersExist`, `markersVisible`, or `isEditModeActive`.

---

## 4. Interaction Model

### When the loupe is visible

The loupe is visible **only** while all of the following are true simultaneously:

1. Marker Edit Mode is active (`isEditModeActive = true`)
2. A marker drag is in progress (finger is down and moving, gesture classified as drag by the existing `pointerInput` handler)
3. A valid decoded reference bitmap is available

The loupe disappears **immediately** when the drag ends (finger lift). No fade-out.

### What the loupe controls

The loupe is display-only. It has no touch targets. It does not intercept any pointer events. It does not affect marker coordinates.

The **stored marker coordinate** (`normalizedX`, `normalizedY`) is always the true finger/drag position converted via `screenToNormalized`. The loupe does not modify this path.

### Drag state threading

The existing drag gesture handler in `ReferenceMarkerOverlay` currently calls `onMoveMarker` on each pointer event. The loupe needs to read current drag state to determine visibility and crop center.

Two local state values are used:

- `isDragging: Boolean` — true while a marker drag gesture is active
- `draggingMarkerNormalizedPos: Pair<Float, Float>?` — the most recent normalized position during drag; null when not dragging

These are Compose Snapshot state values (`mutableStateOf`) scoped **locally inside `ReferenceMarkerOverlay`**. They are set by the drag branch in `pointerInput` at the same time as `onMoveMarker` is called, and cleared on finger lift.

Using Snapshot state here means the loupe `Canvas` within the overlay will invalidate and redraw when these values change. This is acceptable because:

- Recomposition scope is limited to the loupe drawing layer inside `ReferenceMarkerOverlay`
- No parent composable (CameraScreen or higher) recomposes per pointer event
- No ViewModel state is updated for loupe display purposes

See §10 for the explicit boundary of what is and is not allowed per frame.

No ViewModel changes are required.

---

## 5. Loupe Content

### What the loupe shows

- A magnified circular crop of the **reference image bitmap** centered on the current marker position
- A **marker indicator** centered within the loupe circle (see §9 for visual design)

### What the loupe does not show

- Camera preview
- Captured images
- Overlay transparency layer
- Grid
- Edit-mode viewport border
- GPS chip, controls, Done button, snackbars, or any other CameraScreen UI
- Any export-rendered or session-rendered image

### Bitmap source

The loupe renders from a decoded `android.graphics.Bitmap` of the reference image. This bitmap is distinct from the Coil-managed texture used by `AsyncImage` for the overlay display.

The bitmap must be:

- loaded asynchronously on a background coroutine (IO dispatcher)
- cached in a `remember { mutableStateOf<Bitmap?>(null) }` local to the loupe rendering path
- invalidated and reloaded only when the reference URI changes
- downsampled to a maximum dimension of 1024px on the longest side — sufficient for the maximum effective magnification at any realistic loupe size

If the bitmap is not yet available when dragging begins, the loupe circle renders with the background color only (dark fill, no image content) until the bitmap is ready. This is a graceful degradation, not an error state.

### Crop geometry

The loupe crop is computed in reference image pixel space:

```
effectiveScale = clamp(baseScale × overlayScale × 2.0, minScale=1.0, maxScale=6.0)

cropHalfPx = (loupeDiameterPx / 2) / effectiveScale

cropLeft_px   = markerNormX × imageWidthPx  − cropHalfPx
cropTop_px    = markerNormY × imageHeightPx − cropHalfPx
cropRight_px  = cropLeft_px + 2 × cropHalfPx
cropBottom_px = cropTop_px  + 2 × cropHalfPx
```

Where:
- `baseScale` is the fit/fill scale of the reference image in the current display mode and viewport (same value computed by `normalizedToScreen`)
- `overlayScale` is the current user-applied overlay scale
- `imageWidthPx`, `imageHeightPx` are the oriented bitmap dimensions
- `loupeDiameterPx` is the loupe diameter in physical pixels

These crop coordinates are passed directly to `Canvas.drawBitmap(bitmap, srcRect, dstRect, paint)` or equivalent. No intermediate bitmap is created during drag.

---

## 6. Magnification Model

### Base model

The loupe shows the reference image content at **2× the current screen-space overlay magnification**. This means a user who has zoomed out sees proportionally more context; a user who has zoomed in sees proportionally less but at higher fidelity.

The factor 2.0 is fixed. No user control over loupe zoom in V1.

### Effective magnification

```
effectiveScale = clamp(baseScale × overlayScale × 2.0, 1.0, 6.0)
```

- `1.0` minimum: at very low overlay zoom, image content in the loupe is never displayed at less than 1 screen pixel per image pixel. Below this threshold, the loupe would show image content at worse resolution than the un-magnified overlay, which would defeat its purpose.
- `6.0` maximum: at very high overlay zoom, the loupe is capped to prevent showing so few image pixels that individual pixel blocks dominate and no alignment context remains. Six image pixels per screen pixel provides strong magnification without pixel-washing.

### Behavior at extremes

| Overlay zoom state | `baseScale × overlayScale` | `effectiveScale` | Loupe appearance |
|---|---|---|---|
| Very zoomed out (overlayScale = 0.1) | e.g. 0.3 | 1.0 (clamped up) | Shows large region of image at 1:1 pixel ratio |
| Normal reference alignment (overlayScale ≈ 1.0) | e.g. 3.0 | 6.0 (clamped) | Shows tight crop at max allowed detail |
| Slightly zoomed (overlayScale ≈ 0.5) | e.g. 1.5 | 3.0 | Shows medium crop at 3× image pixels per screen pixel |
| Maximum zoom (overlayScale very high) | e.g. 8.0 | 6.0 (clamped) | Shows minimum crop at max allowed detail |

---

## 7. Loupe Position and Clamping

### Default position

The loupe is positioned **above the marker's current screen position**, offset by:

```
loupeCenterY_default = markerScreenY − (loupeDiameter / 2) − offsetBelowFinger
```

Where `offsetBelowFinger = 16.dp` — a small gap between finger touch target and loupe bottom edge.

Horizontally, the loupe center aligns with the marker screen X position:

```
loupeCenterX_default = markerScreenX
```

### Clamping to viewport

The loupe must remain fully inside the editable overlay viewport at all times. The viewport boundary for clamping purposes is the same bounding box used by `ReferenceMarkerOverlay` (the viewport area passed via `Modifier.onSizeChanged`).

An additional bottom margin is subtracted to avoid overlapping the Done button bar area:

```
clampBoundsTop    = 0
clampBoundsLeft   = 0
clampBoundsRight  = viewportWidth − loupeDiameter
clampBoundsBottom = viewportHeight − loupeDiameter − doneButtonAreaHeight
```

Where `doneButtonAreaHeight` is the effective height reservation for the center bottom bar (Done button area). The V1 default is **88.dp** — a conservative estimate covering the Done button height plus bottom system insets on typical devices. If the measured height of the bottom action area is already available at the `ReferenceMarkerOverlay` call site without layout restructuring, that measured value may be used instead. No layout restructuring is required solely to obtain this measurement; 88.dp is the authoritative fallback. See OQ-2.

The final loupe center is:

```
loupeCenterX = clamp(loupeCenterX_default, loupeDiameter/2, viewportWidth − loupeDiameter/2)
loupeCenterY = clamp(loupeCenterY_default, loupeDiameter/2, viewportHeight − loupeDiameter/2 − doneButtonAreaHeight)
```

### Fallback when no space above

If the clamped Y position causes the loupe to overlap the marker (loupe bottom > markerScreenY − minGap), shift the loupe below the marker:

```
loupeCenterY_below = markerScreenY + (loupeDiameter / 2) + offsetBelowFinger
loupeCenterY_below_clamped = clamp(loupeCenterY_below, loupeDiameter/2, clampBoundsBottom + loupeDiameter/2)
```

If neither above nor below provides a non-overlapping position (marker is in the very center of a tiny viewport), use the above position. The loupe may partially overlap the marker in this extreme case.

### Landscape

The clamping logic is identical in landscape. The viewport dimensions are simply different (wider, shorter). The Done button occupies the same center slot; `doneButtonAreaHeight` applies along the height axis as in portrait. No separate landscape path is needed.

### Relation between loupe position and loupe content

Loupe position clamping does **not** affect the crop content. The crop is always centered on the true marker normalized coordinates, regardless of where the loupe visual container is placed on screen. The marker indicator inside the loupe is always drawn at the center of the loupe circle, representing the exact marker position.

---

## 8. Image-Edge Behavior

### Chosen approach: allow crop to extend outside image bounds; fill with background

When the marker is near an image edge, the crop region (computed in §5) will extend beyond the image pixel boundaries. The loupe renders:

- Image content for the portion of the crop inside the image bounds
- The loupe background color (`SameViewOverlayScrim`) for out-of-bounds areas

The marker indicator remains centered in the loupe in all cases.

**Rationale:** This is simpler to implement correctly than adjusting the crop origin, produces no crop-shift artifact when the marker crosses an edge during drag, and clearly communicates "this is the edge of the reference image" through the dark letterbox fill. The user still sees the image content up to the edge and can confirm the marker is at the correct boundary.

### Edge cases by marker position

| Marker position | Behavior |
|---|---|
| Near left edge | Left portion of loupe shows background; right shows image |
| Near right edge | Right portion shows background; left shows image |
| Near top edge | Top portion shows background; bottom shows image |
| Near bottom edge | Bottom shows background; top shows image |
| Corner (e.g. top-left) | Two quadrants show background; image in bottom-right quadrant |
| Fully inside image | Full loupe shows image content |

### Implementation note

Use `Canvas.drawBitmap(bitmap, srcRect, dstRect, paint)` where `srcRect` is clamped to `[0, imageWidth] × [0, imageHeight]` and `dstRect` is the corresponding sub-region of the loupe circle. The out-of-bounds area is pre-filled with the background color.

---

## 9. Visual Design

### Loupe container

| Property | Value |
|---|---|
| Shape | Circle |
| Diameter | 120.dp |
| Clip | `Modifier.clip(CircleShape)` |
| Border | 1.5.dp stroke, `Color.White` at 90% alpha |
| Background | `SameViewOverlayScrim` (`0xB3000000`) |
| Drop shadow | 6.dp blur radius, black at 50% alpha — applied via `BlurMaskFilter` on the border ring |
| Elevation equivalent | None (shadow via Canvas, not Compose `shadow()`) |

The shadow provides contrast against both light and dark reference image content without adding visual mass.

### Marker indicator inside loupe

A smaller version of the standard `ReferenceMarkerDefaults` marker, always drawn at the geometric center of the loupe circle:

| Property | Value |
|---|---|
| Ring diameter | 16.dp |
| Ring stroke width | 1.5.dp |
| Ring color | `Color.White` |
| Center dot diameter | 3.dp |
| Center dot color | `SameViewAccent` (`0xFF4F8CFF`) |
| Drop shadow | None (would clutter small loupe content) |

These loupe-specific sizes are constants local to the loupe rendering code. They are **not added to `ReferenceMarkerDefaults`**, which remains the single source of truth for the full-size on-screen marker only.

### Animation

**No entrance or exit animation.** The loupe appears and disappears instantly with the drag state. An animation would delay the precision feedback the loupe exists to provide and add visual noise inconsistent with SameView's calm UX philosophy.

### What is NOT used

- No crosshairs
- No coordinate labels or numeric readouts
- No zoom level indicator
- No connection line from loupe to marker
- No magnifier glass chrome or handle graphic
- No colored tint on the loupe image content

---

## 10. Performance Constraints

### During drag (per-frame path)

The following operations are **forbidden** during the drag gesture (in the `pointerInput` coroutine or during per-frame Canvas drawing):

- Bitmap decoding (no `BitmapFactory.decodeStream`, no `ImageDecoder`)
- File I/O of any kind
- Session file reads
- ViewModel coroutine launches for loupe display
- Memory allocation of new Bitmap objects

The performance boundary is:

| Operation | Verdict |
| --- | --- |
| CameraScreen-wide recomposition per pointer event | **Forbidden** |
| ViewModel state update for loupe display | **Forbidden** |
| Bitmap decode or new Bitmap allocation during drag | **Forbidden** |
| Snapshot state update scoped to `ReferenceMarkerOverlay` | **Allowed** — invalidates only the loupe Canvas within the overlay |
| `Canvas.drawBitmap` from cached bitmap per frame | **Allowed** — no allocation |

Per-frame work must be limited to:

1. Writing the new normalized position to the local Snapshot state (`draggingMarkerNormalizedPos`)
2. The Canvas draw sequence: background fill → `drawBitmap(cachedBitmap, srcRect, dstRect, paint)` → marker indicator circles

The loupe content draw call is:

```
canvas.drawBitmap(cachedBitmap, srcRect, dstRect, paint)
```

This operates on already-decoded pixels in memory and allocates no new objects per frame.

### Bitmap cache lifecycle

The cached loupe bitmap:

- is loaded **once** when the reference URI is first set and Marker Edit Mode is entered
- is held in a `remember { mutableStateOf<Bitmap?>(null) }` or equivalent composable-local state
- is invalidated and reloaded only when the reference URI changes
- is **not** recycled during a drag or between drags
- may be recycled when the reference image is removed (`Remove` or `Replace`) to free memory

Loading is done on the IO dispatcher via a `LaunchedEffect(referenceUri)` block. The loupe bitmap load should not block or delay the marker overlay appearance.

### Bitmap sizing

The loupe bitmap is downsampled at decode time. Target size:

```
maxLoupeBitmapDimension = 1024px (longest side)
```

At the maximum effective scale of 6.0 and a 120dp loupe at 3× density (360px), the loupe shows at most 60px of image content per diameter. A 1024px bitmap can cover this with substantial margin. Full-resolution decoding is not required and would waste memory.

Use `BitmapFactory.Options.inSampleSize` at decode time. The sample size should be computed based on reference image dimensions relative to the 1024px target.

### Graceful unavailability

If the cached bitmap is null (not yet loaded, or load failed):

- The loupe circle renders with background color only
- The marker indicator inside the loupe is still drawn
- No crash, no error toast, no skip of the loupe composable

---

## 11. Gesture Safety

The loupe is a purely passive rendering consumer. It must not alter any of the following:

| Gesture / behavior | Status | Note |
|---|---|---|
| Marker drag | **Unchanged** | Loupe reads drag state; does not intercept pointer events |
| Long-press add | **Unchanged** | Loupe does not appear during long-press-create |
| Long-press delete (warning) | **Unchanged** | Loupe does not appear during delete warning state |
| Overlay drag (1-finger, free area) | **Unchanged** | Not affected |
| Overlay pinch-scale (2-finger) | **Unchanged** | Not affected |
| Edit Mode lifecycle | **Unchanged** | Loupe appears/disappears based on drag state only |
| Capture | **Unchanged** | Capture is disabled during Edit Mode (ALIGNMENT_POINTS_V1.md §6.7) |
| Camera Zoom Mode | **Unchanged** | Disabled during Edit Mode (ALIGNMENT_POINTS_V1.md §6.9) |
| Back gesture | **Unchanged** | Exits Edit Mode as per ALIGNMENT_POINTS_V1.md §6.8 |

The loupe composable is layered **above** the Canvas marker layer and **below** the empty-state hint, ensuring it is visually clear but does not participate in gesture routing.

The loupe has no test tag needed for production but should be wrapped in a `Box` with `testTag("marker_drag_loupe")` for instrumentation test access.

---

## 12. Expected Tests

All tests are instrumentation tests, consistent with the existing marker test suite.

### Appearance

| Test | Assertion |
|---|---|
| `loupe_appearsWhileDraggingMarker` | Drag a marker → loupe node with tag `marker_drag_loupe` is displayed |
| `loupe_notVisible_whenNotDragging` | No active drag → loupe node does not exist |
| `loupe_notVisible_outsideEditMode` | Edit Mode not active → loupe node does not exist even if marker exists |
| `loupe_disappears_afterDragEnds` | Lift finger after drag → loupe node does not exist |

### Position / clamping

| Test | Assertion |
|---|---|
| `loupe_clamped_nearTopEdge` | Drag marker to top edge → loupe remains within viewport top bound |
| `loupe_clamped_nearBottomEdge` | Drag marker to bottom edge → loupe remains above Done button area |
| `loupe_clamped_nearLeftEdge` | Drag marker to left edge → loupe remains within viewport left bound |
| `loupe_clamped_nearRightEdge` | Drag marker to right edge → loupe remains within viewport right bound |

### Marker coordinate integrity

| Test | Assertion |
|---|---|
| `loupe_doesNotModifyMarkerCoordinate` | Drag marker to position P → loupe visible → on lift, `onMoveMarker` was called with position P, not the loupe position |

### No loupe in wrong states

| Test | Assertion |
|---|---|
| `loupe_notVisible_duringLongPressCreate` | Long-press on empty area → loupe does not appear |
| `loupe_notVisible_duringDeleteWarning` | Long-press on marker → loupe does not appear during warning state |
| `loupe_notVisible_markerDrag_editModeOff` | Drag without Edit Mode active → loupe does not appear |

### Content mapping (if bitmap access is feasible in test)

| Test | Note |
|---|---|
| `loupe_content_centeredOnMarkerPosition` | Loupe center corresponds to marker normalized coordinates (visual test only; not automated in V1) |

Content mapping tests are marked as **manual verification only** in V1. Automated pixel-level loupe content verification is out of scope for the initial test suite.

---

## 13. Source Document Notes

### ALIGNMENT_POINTS_V1.md

No conflicts. The loupe is a new sub-feature that `ALIGNMENT_POINTS_V1.md` does not address. The spec's instruction that "Kein Code darf Marker-Farben, -Größen oder -Touch-Radien inline hardcoden" applies only to the production marker visual (`ReferenceMarkerDefaults`). The loupe-internal marker indicator uses loupe-specific constants that are not production marker sizes and must not be added to `ReferenceMarkerDefaults`.

`ALIGNMENT_POINTS_V1.md` §6.4 states markers are created by long-press, not drag. The loupe correctly appears only on drag of an **existing** marker, not during long-press creation.

`ALIGNMENT_POINTS_V1.md` §11 (Explicit Non-Goals) does not exclude a drag loupe. The non-goals listed are: persistence, export, annotations, AI, numbered markers, capture in edit mode, etc. A transient drag-only display element does not conflict with any of them.

### CAMERA_WORKFLOW_UX_V1.md

No conflicts. The clamping boundary for loupe position respects the Done button area described in §3 (Bottom Bar exception for Marker Edit Mode). The loupe never displaces the Done button, Reference button, or Compare button.

### COMPARE_SESSION_RENDERING_V1.md

No conflicts. The loupe is transient UI. It is explicitly excluded from the rendering pipeline. No amendment to that document is required.

### RESPONSIVE_LAYOUT_SYSTEM_V1.md

No conflicts. The loupe clamping logic uses runtime viewport dimensions, which naturally reflect both portrait and landscape viewports. No orientation-specific loupe path is needed.

---

## 14. Open Questions

### OQ-1: Loupe bitmap load timing

**Question:** Should the loupe bitmap be loaded proactively when Edit Mode is entered, or lazily when the first drag begins?

**Default position for implementation:** Load proactively when Edit Mode is entered. This avoids a blank loupe on the first drag (which could feel like a bug) and the bitmap is cheap to load at 1024px.

**Risk if lazy:** User starts a drag before bitmap is loaded → loupe appears with background only → may look broken. Proactive loading avoids this at the cost of loading the bitmap even if the user never drags.

### OQ-2: Done button area height for bottom clamping

**Question:** Can the exact height of the Done button bar area be passed into `ReferenceMarkerOverlay` without restructuring the existing layout?

**Default for implementation:** Use **88.dp** as the V1 conservative estimate. This covers the Done button height plus bottom system insets on typical devices without requiring layout changes.

If the Done button composable or its parent slot already reports a measured height accessible at the `ReferenceMarkerOverlay` call site (e.g. via `onSizeChanged` on an existing container), that measured value may be used directly. No new layout wrappers or restructuring required.

**UI risk:** If 88.dp is insufficient on a specific device (large-font accessibility scale, unusual bottom inset), the loupe may visually overlap the Done button on bottom-edge drags. Flag as a manual verification item in the implementation plan.

### OQ-3: Loupe bitmap recycling on Edit Mode exit

**Question:** Should the loupe bitmap be explicitly recycled when Edit Mode ends (to release memory)?

**Default position:** Yes. When Edit Mode exits and the reference URI has not changed, the cached bitmap should be set to null and `bitmap.recycle()` called. It can be re-decoded on next Edit Mode entry. This trades a small decode cost for memory efficiency, appropriate since the marker bitmap (1024px downsampled) is a non-trivial allocation.

---

## 15. Changelog

### Revision 2 — 2026-06-30

**Contradiction fix — §4 and §10:**

§4 "Drag state threading" clarified: `isDragging` and `draggingMarkerNormalizedPos` are Compose Snapshot state (`mutableStateOf`) scoped locally inside `ReferenceMarkerOverlay`. Recomposition triggered by these values is acceptable and bounded to the loupe Canvas within the overlay. The constraint in §10 is not "zero Compose recomposition anywhere" — it is specifically: no CameraScreen-wide recomposition per pointer event, and no ViewModel update for loupe display.

§10 "During drag (per-frame path)" updated: removed the blanket "Compose recomposition triggered by drag position" forbidden item. Replaced with an explicit verdict table distinguishing forbidden operations (ViewModel updates, CameraScreen-wide recomposition, bitmap decode/allocation) from allowed operations (Snapshot state updates scoped to the overlay, per-frame `drawBitmap` from cache).

**§7 and OQ-2 — doneButtonAreaHeight clarified:**

88.dp is the V1 authoritative fallback, not a required hard-coded constant. Measured height may be used if already available at the call site without layout restructuring. OQ-2 updated to name the UI risk (overlap on bottom-edge drags at large-font scale) and flag it for manual verification in the implementation plan.

---

### Revision 1 — 2026-06-30

Initial specification. All ten product decision areas specified:

1. Ownership and non-goals — loupe is transient, never persisted
2. Interaction model — display-only, drag-only, pointer-transparent
3. Loupe content — reference image crop + centered marker indicator
4. Magnification model — 2× relative, clamped [1×, 6×] effective scale
5. Position and clamping — above marker default, clamped to viewport, avoids Done button area
6. Image-edge behavior — allow crop to extend outside bounds, fill with background
7. Visual design — 120dp circle, white border, `SameViewOverlayScrim` background, no animation
8. Performance constraints — cached 1024px bitmap, no decode per frame, graceful fallback
9. Gesture safety — no pointer event interception, all existing gestures unchanged
10. Tests — 12 instrumentation tests specified; content mapping deferred to manual V1
