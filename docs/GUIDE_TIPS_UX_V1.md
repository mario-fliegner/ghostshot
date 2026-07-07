# GUIDE_TIPS_UX_V1

## 1. Document Status

This document is the **authoritative specification** for the SameView contextual guide tip system.

It supersedes the tip definitions and visual design embedded in the earlier implementation derived from `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md §10–15`. The architecture, persistence, and Guide/Walkthrough systems defined in `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md` remain valid except where this document explicitly overrides them.

If this document conflicts with `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md` on any tip-specific behavior, this document wins.

---

## 2. Intent

Guide tips are **contextual discovery hints**.

They are not:

- walkthrough pages
- onboarding screens
- feature tours
- forced navigation flows

A tip appears when the feature it describes is immediately actionable in the current screen. A tip is **completed** when the user meaningfully discovers or uses the feature. Dismissing a tip without using the feature does not count as completion.

Tips must not teach the full feature. They must announce that the feature exists and give the user one clear action.

---

## 3. Non-Goals

This feature must not introduce:

- new Android permissions
- analytics, tracking, telemetry, or network behavior
- cloud behavior
- camera capture behavior changes
- compare rendering changes
- session metadata changes
- export behavior changes
- marker behavior changes
- GPS behavior changes
- Settings toggles for tips
- web content, WebView, or FAQ pages

---

## 4. Tip Set

The following five tips form the complete tip set for V1.

| # | ID | Screen scope | Anchor target | Show trigger | Prerequisite | Completion event | Learn more |
|---|---|---|---|---|---|---|---|
| 1 | `REFERENCE` | Camera | Reference button | CameraScreen entered after walkthrough; no reference loaded | None | Reference image successfully selected | Yes |
| 2 | `SHARE` | Compare | Share button | First meaningful compare-slider interaction; ~1 s deferred | None | Share menu opened | Yes |
| 3 | `EDIT_SESSION` | Compare | Overflow menu button | `SHARE` tip completed; Edit Session screen never opened | `SHARE` completed | Edit Session screen opened | Yes |
| 4 | `OPEN_COMPARISON` | Library | Stable grid-area anchor | ≥ 1 comparison exists; comparison tile never tapped; user opens library screen | None | First comparison tile tapped in the Comparisons screen | No |
| 5 | `MULTI_SELECT` | Library | Stable grid-area anchor | Compare screen already opened; multi-select never used | OPEN_COMPARISON completed | Multi-select activated | No |

Priority within each scope (lower number shows first when multiple are simultaneously eligible):

| Scope | Priority order |
|---|---|
| Camera | REFERENCE (1) |
| Compare | SHARE (1), EDIT_SESSION (2) |
| Library | OPEN_COMPARISON (1), MULTI_SELECT (2) |

Only one tip is visible at a time across all scopes.

---

## 5. Removed Tips

The following tips from the prior implementation are **permanently removed**.

| Removed ID | Previous anchor | Removal rationale |
|---|---|---|
| `ALIGN` | Opacity slider | Users who loaded a reference image do not need to be told to align — the overlay is visually obvious. The trigger was too early and the anchor (opacity slider) was semantically wrong for alignment guidance. |
| `MARKER` | Marker row in reference menu | The marker feature is sufficiently discoverable inside the reference menu where it lives. A tip appearing during marker menu interaction created timing conflicts with the menu itself. |
| `GPS` | GPS guidance chip | The GPS chip is self-explanatory when visible. The tip duplicated information the chip itself communicates. GPS tip eligibility required complex simultaneous conditions. |
| `COMPARE` | Compare button | The Compare button is a primary bottom-bar action. A tip on a primary visible action adds noise without value. Users who have a capture can see the Compare button is enabled. |
| `HISTORY` | History icon | The history icon uses a standard history/library icon. The copy ("Favorites") was misaligned with the anchor target (history library access). The library is always visible in the top bar and does not require tip discovery. |
| `EXPORT` | Export icon | Replaced by `SHARE`. The EXPORT tip was triggered on first CompareScreen open rather than after first slider engagement, making it appear before the user had meaningfully used the comparison. |

---

## 6. Completion Model

### 6.1 Completed vs. Dismissed

**Completed** means the user has meaningfully discovered or used the feature, or has explicitly dismissed the tip card. Completion is permanent. Completed tips never appear again unless the user explicitly resets tip state from the Guide screen.

**Dismissed** means the user manually closed the tip card via the Dismiss action, without performing the feature action. **Dismissal is completion** — tapping Dismiss marks the tip as permanently seen, identically to Learn more. A dismissed tip does **not** re-appear under any circumstance short of an explicit tip-state reset from the Guide screen.

This is distinct from navigating away from a screen while a tip is visible without tapping either action (§6.5), which does **not** complete the tip.

### 6.2 Completion Events Per Tip

| Tip | Completion event | Where to call markTipCompleted() |
|---|---|---|
| `REFERENCE` | `CameraViewModel.onReferenceImageSelected()` succeeds | `CameraViewModel` |
| `SHARE` | Share menu dropdown opens in `CompareScreen` | `CompareScreen` (on `showExportMenu = true`) |
| `EDIT_SESSION` | Edit Session route is pushed in `MainActivity` | `MainActivity` navigation event handler |
| `OPEN_COMPARISON` | First comparison tile tapped in `CompareLibraryScreen` (before navigation) | `CompareLibraryScreen` tile tap handler |
| `MULTI_SELECT` | Multi-select mode is activated in `CompareLibraryScreen` | `CompareLibraryScreen` long-press handler |

Completion is recorded by calling `GuideRepository.markTipSeen(tipId)`. `GuideTipController.dismissActiveTip()` calls `markTipSeen(tipId)` unconditionally, for **both** `GuideTipDismissReason.GOT_IT` (Dismiss) and `GuideTipDismissReason.LEARN_MORE` (Learn more) — the `reason` argument no longer changes whether persistence happens, only which button the user tapped (kept for callers/analytics). All other completions call `markTipSeen()` directly from the feature event site, then call `guideTipController.clearActiveTipWithoutMarkingSeen()` if a tip is currently active, to dismiss the visual without double-persisting.

### 6.3 Manual Dismiss

Each tip card provides a **Dismiss** action (label: "Dismiss"). Tapping Dismiss:

- Hides the tip immediately with fade-out animation.
- **Marks the tip as completed** via `dismissActiveTip(GuideTipDismissReason.GOT_IT)`, which calls `GuideRepository.markTipSeen(tipId)` — identically to Learn more.
- Sets `waitingForUserActionAfterDismissal = true` in `GuideTipController`. Since the dismissed tip is now permanently completed, this flag no longer serves to delay that same tip's reappearance (it will never reappear). It still prevents a **different** eligible tip from immediately taking its place; the next tip only becomes eligible to show after the user performs a normal UI action (see §8.4).
- The tip never appears again, in this or any future session, unless the user explicitly resets tip state from the Guide screen.

The `dismissActiveTip(GuideTipDismissReason.GOT_IT)` path in `GuideTipController` is used for Dismiss.

### 6.4 Learn More

Tips that have `learnMore = true` show a **Learn more** action (label: "Learn more"). Tapping Learn more:

- Navigates to the corresponding Guide topic.
- Marks the tip as completed via `dismissActiveTip(GuideTipDismissReason.LEARN_MORE)`, which calls `GuideRepository.markTipSeen(tipId)`.
- The tip does not appear again.

Tips with `learnMore = false` (Open Comparison, Multi Select) show only the Dismiss action — which is now their only path to completion besides the feature event itself.

### 6.5 Navigate Away Without Completing

When the user leaves a screen while a tip is visible:

- The tip is cleared without completing (`clearActiveTipWithoutMarkingSeen()`).
- The tip is eligible to re-appear on next entry under its normal trigger conditions.
- The manual dismiss anti-spam flag is **not** set — navigating away is not a dismissal.

---

## 7. Tip Trigger Definitions

### 7.1 Reference Tip

**Trigger conditions (all must be true):**

- CameraScreen is active and resumed.
- No reference image is loaded (`referenceUri == null`).
- Walkthrough has been completed (`walkthrough_completed == true`).
- `REFERENCE` tip is not yet completed.
- No transient UI is blocking (see §8).

**Screen entry delay:** 800 ms after CameraScreen enters the resumed state before evaluating this tip for the first time in a session.

**Anchor:** `REFERENCE_BUTTON` — the Reference button in the camera bottom bar.

**Clears when:** A reference image is loaded (tip completes) or any blocking condition becomes true.

### 7.2 Share Tip

**Trigger conditions (all must be true):**

- CompareScreen is active and in a valid session (`sessionId != null`).
- A meaningful compare-slider interaction has been detected (see §7.2.1).
- 1 second has elapsed since the interaction was first detected.
- `SHARE` tip is not yet completed.
- No transient UI is blocking (see §8).

**§7.2.1 — Meaningful slider interaction definition:** A horizontal drag on the compare slider that exceeds 8 dp from its starting position and lasts at least 100 ms. Accidental touches (short taps with negligible horizontal movement) do not qualify. The interaction detector is a one-shot: once a meaningful interaction is detected in a session, the 1 s timer starts and does not restart on subsequent interactions.

**Screen entry delay:** The interaction-based 1 s delay serves as the screen entry delay. The tip never appears before the first slider interaction.

**Anchor:** `SHARE_ACTION` — the Export/Share button in CompareScreen TopAppBar (`compare_screen_export_button`).

**Clears when:** Share menu opens (tip completes), tip is dismissed, or any blocking condition becomes true.

### 7.3 Edit Session Tip

**Trigger conditions (all must be true):**

- CompareScreen is active and in a valid session (`sessionId != null`).
- `SHARE` tip is completed (persisted in `seen_tip_ids`).
- `EDIT_SESSION` tip is not yet completed.
- No transient UI is blocking (see §8).

**Screen entry delay:** 1 200 ms after CompareScreen enters active state with the prerequisite satisfied. This delay ensures the tip does not interrupt users who are still interacting with the share flow that triggered prerequisite completion.

**Anchor:** `OVERFLOW_ACTION` — the overflow button (⋮) in CompareScreen TopAppBar.

**Clears when:** Edit Session route is pushed (tip completes), tip is dismissed, or any blocking condition becomes true.

### 7.4 Open Comparison Tip

**Trigger conditions (all must be true):**

- CompareLibraryScreen is active and resumed.
- At least one comparison exists in the session list.
- `OPEN_COMPARISON` tip is not yet completed.
- No transient UI is blocking (see §8).

**Screen entry delay:** 600 ms after CompareLibraryScreen enters the resumed state.

**Anchor:** `LIBRARY_GRID_AREA` — stable grid-area phantom anchor (see §13.4).

**Clears when:** First comparison tile is tapped (tip completes, before navigation), tip is dismissed, or any blocking condition becomes true.

### 7.5 Multi Select Tip

**Trigger conditions (all must be true):**

- CompareLibraryScreen is active and resumed.
- `OPEN_COMPARISON` tip is completed.
- `MULTI_SELECT` tip is not yet completed.
- No transient UI is blocking (see §8).

**Screen entry delay:** 600 ms after CompareLibraryScreen enters the resumed state with both conditions satisfied.

**Anchor:** `LIBRARY_GRID_AREA` — same stable grid-area anchor as Open Comparison.

**Clears when:** Multi-select mode is activated (tip completes), tip is dismissed, or any blocking condition becomes true.

---

## 8. Blocked Conditions

A tip must never appear while any of the following conditions is true in its screen scope.

### 8.1 Camera Scope Blocked Conditions

- Reference image picker is pending (`isReferencePickerActive == true`)
- Capture is in progress (`isCaptureInProgress == true`)
- GPS SAF fallback dialog is showing
- A snackbar is pending or currently showing
- Overlay gesture is active (drag or pinch in progress)
- Marker edit mode is active
- Camera permission flow is active (any permission state other than granted)

### 8.2 Compare Scope Blocked Conditions

- Fullscreen mode is active (`isFullscreen == true`)
- Export dropdown menu is open (`showExportMenu == true`)
- Overflow menu is open (`showMoreMenu == true`)
- Delete dialog is showing (`showDeleteDialog == true`)
- Backup is in progress (`isBackupInProgress == true`)
- Edit Session route is being loaded

### 8.3 Library Scope Blocked Conditions

- Multi-select mode is active
- Delete confirmation dialog is showing
- Backup is in progress
- The comparison grid is actively scrolling (pointer-driven drag gesture in progress)
- The comparison grid is actively flinging (momentum scroll animation in progress)

Tips must appear only after scrolling has fully settled. A tip that appears during a fling or drag is intrusive and visually unstable. `LazyGridState.isScrollInProgress` covers both drag and fling and is the correct signal.

### 8.4 Global Rules

- Only one tip may be visible at any time.
- After any dismissal or completion event, a **different** eligible tip must not immediately take the dismissed/completed tip's place until the user performs a normal UI action (`onUserAction()` is called in `GuideTipController`). Since dismissal is now itself completion (§6.1, §6.3), this rule only governs the transition to the *next* tip — the dismissed/completed tip itself never reappears regardless of this flag.
- Tips must not appear during any active permission dialog.

---

## 9. Hard Placement Rules

These rules override all other placement considerations.

**Rule P-1:** A tip must **never** visually cover the Capture button. The Capture button bounds are provided as an exclusion zone to the placement algorithm.

**Rule P-2:** A tip must **never** visually cover the compare slider handle or the active compare viewport region.

**Rule P-3:** A tip must **never** appear immediately on screen entry. Each tip has a defined screen entry delay (§7.x).

**Rule P-4:** If no safe placement can be found that satisfies all constraints, the tip is **deferred** — it is not shown. Deferred behavior is correct; a badly placed tip is worse than no tip.

---

## 10. Card Visual Specification

### 10.1 Surface and Color

| Element | Token | Value |
|---|---|---|
| Card surface | `SameViewAppSurface` | `0xFF17202F` |
| Title text | `onSurface` → White | `0xFFFFFFFF` |
| Body text | `SameViewTextSecondary` | `0xFFC7CCD6` |
| Action label text (Learn more) | `SameViewAccent` | `0xFF4F8CFF` |
| Action label text (Dismiss) | `SameViewAccent` (same as Learn more) | `0xFF4F8CFF` |
| Pointer fill | `SameViewAccent` | `0xFF4F8CFF` (solid accent fill, no card-color match) |
| Card border | `SameViewAccent` | `0xFF4F8CFF`, 0.5 dp stroke |

The card must use `containerColor = SameViewAppSurface` explicitly. It must **not** use `MaterialTheme.colorScheme.surfaceVariant`, which resolves to the slider-track grey `0xFF666666` in the current theme.

### 10.2 Dimensions and Shape

| Property | Value |
|---|---|
| Max width (ceiling, not a fixed width) | 260 dp |
| Internal padding (top/start/end) | 12 dp |
| Internal padding (bottom) | 0 dp |
| Vertical spacing: title → body | 6 dp |
| Vertical spacing: body → actions | 4 dp |
| Card shape | `RoundedCornerShape(12.dp)` |
| Elevation | 6 dp |
| Border | `BorderStroke(0.5.dp, SameViewAccent)` |
| Min width | Wrap content (do not fill below 200 dp) |

The card is **content-based**, not `fillMaxWidth()`: neither the `Card` nor the action `Row` apply `fillMaxWidth()`. The card measures to the width of its widest line (title, wrapped body, or the action row) and only grows up to the `maxCardWidth` ceiling — `min(260.dp, containerWidth - 32.dp)` — if the content needs it. Short copy (e.g. the REFERENCE tip) renders a visibly narrower card; longer copy that already needs to wrap still reaches the same 260 dp ceiling as before, so no tip's text is at greater truncation risk than previously.

Internal bottom padding is `0.dp` because the action label touch-target boxes (see §10.4) already provide their own ~15 dp of vertical space below the visible label text — adding further declared bottom padding on top of that would recreate the previous bottom-heavy imbalance. Top padding stays at 12 dp since the title has no equivalent built-in inset above it.

### 10.3 Typography

| Element | Type role | Weight |
|---|---|---|
| Title | `MaterialTheme.typography.titleSmall` | `FontWeight.SemiBold` |
| Body | `MaterialTheme.typography.bodySmall` | Normal |
| Learn more button | `MaterialTheme.typography.labelMedium` | Normal |
| Dismiss button | `MaterialTheme.typography.labelMedium` | Normal |

Body text must use `maxLines = 2` with `overflow = TextOverflow.Ellipsis`. Copy must be written to fit within two lines at the default system font scale on a 260 dp card.

### 10.4 Actions Layout

Actions row is positioned at the bottom of the card content column.

- Learn more and Dismiss are **not** `TextButton`s. Each is a custom `Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = ...))` with `contentAlignment = Alignment.CenterStart`, containing a plain `Text`. This avoids Material3's `TextButton` internals (`ButtonDefaults.MinHeight = 40.dp` plus the enforced 48 dp minimum interactive size stacking on top), which previously made it impossible to control the vertical gap below the action row precisely — no `contentPadding` value could compensate for it.
- The 48 dp `heightIn(min = ...)` on each action box **is** the touch target — same minimum size Material3 enforces automatically on `TextButton`, just explicit and directly controlled instead of implicit.
- Both actions are grouped together and aligned to the **start** of the row (`Arrangement.spacedBy(24.dp, alignment = Alignment.Start)`), matching the body text's left edge — not spread across the full card width and not right-aligned. Learn more (when present) sits immediately before Dismiss with a 24 dp gap between them, so Dismiss sits further right without being squeezed against Learn more or crowding the card's right edge. Neither the row nor the card use `fillMaxWidth()` (see §10.2), so this row no longer forces the card to its max width.
- When `learnMore = false`: only Dismiss renders, still start-aligned.
- No divider between actions and body text. The 4 dp vertical spacer between body and actions provides visual separation.
- Learn more and Dismiss both render their label in `SameViewAccent` — no special secondary/grey treatment for Dismiss.
- Label text starts flush with the box's left edge (`contentAlignment = Alignment.CenterStart`, no internal content padding), so the visible label aligns exactly with Title/Body's left edge.

### 10.5 No Dialog Appearance Rules

The tip card must not appear dialog-like. Prohibited:

- Full-screen overlay or dim behind the card.
- Spotlight or punch-through revealing the anchor.
- Modal blocking behavior.
- Card wider than 260 dp.
- Card taller than necessary to contain two body lines and actions.

---

## 11. Pointer Specification

The pointer is a filled **equilateral triangle** drawn solid in `SameViewAccent` (`0xFF4F8CFF`) — no border, outline, or stroke. The pointer is the primary visual connector between the card and the anchor, so it uses the brand accent color directly rather than matching the card surface color. It is drawn as a Canvas element positioned outside the card's rounded-corner boundary, visually extending from the card edge toward the anchor.

### 11.1 Dimensions

| Property | Value |
|---|---|
| Triangle base (ABOVE/BELOW) | 20 dp |
| Triangle height (ABOVE/BELOW) | 12 dp |
| Triangle base (START/END) | 20 dp |
| Triangle height (START/END) | 12 dp |
| Gap between pointer tip and anchor bounds | 4 dp (this is `gapPx` in placement) |

For START/END, the triangle's dimensions are transposed: the 20 dp "base" spans vertically along the card's side edge and the 12 dp "height" is the horizontal reach toward the anchor.

### 11.2 Position Per Placement Side

| Placement side | Pointer position |
|---|---|
| ABOVE (card above anchor) | Triangle extends downward from card bottom edge, horizontally positioned to track the anchor's horizontal center |
| BELOW (card below anchor) | Triangle extends upward from card top edge, horizontally positioned to track the anchor's horizontal center |
| START (card to the left of anchor) | Triangle extends rightward from card right edge, centered vertically on the card |
| END (card to the right of anchor) | Triangle extends leftward from card left edge, centered vertically on the card |

For ABOVE/BELOW, the pointer tracks the anchor's horizontal center along the card edge, clamped to the card's safe inner span (inset from each rounded corner) so the triangle never runs into a rounded corner. This keeps the pointer visually aimed at the anchor even when the card itself is horizontally clamped away from the anchor's center (see §14.4). For START/END, the pointer remains centered on the vertical edge of the card and does not track the anchor's vertical position — this is unchanged and still applies.

### 11.3 Implementation Approach

The pointer is drawn as a separate Canvas element placed in the `SubcomposeLayout` output after the card content, using a second `subcompose` slot for the pointer. The card's `RoundedCornerShape(12.dp)` is applied uniformly to all corners. The pointer is a distinct drawn triangle adjacent to the card, not a clipped notch in the card shape.

---

## 12. Animation Specification

### 12.1 Fade In

Duration: **200 ms**
Easing: `FastOutLinearInEasing`
Trigger: `activeGuideTip` transitions from `null` to a non-null tip value in the screen's composition.

`AnimatedVisibility` wraps the `GuideTipHost` call site in each screen, keyed on `activeGuideTip != null`.

```
AnimatedVisibility(
    visible = activeGuideTip != null,
    enter = fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)),
    exit = fadeOut(animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing))
)
```

### 12.2 Fade Out

Duration: **150 ms**
Easing: `LinearOutSlowInEasing`
Trigger: `activeGuideTip` transitions from a non-null tip to `null`.

Fade out fires immediately when any of the following events occurs:

- User taps Dismiss.
- User taps Learn more.
- Tip completes via feature event.
- A blocking condition becomes true.
- The user navigates away from the screen.

### 12.3 No Other Animations

Tips must not use:

- pulse, bounce, scale, or slide animations
- coach-mark spotlight animations
- any animation other than fade in and fade out

### 12.4 Tip Substitution

If a new tip becomes eligible while an existing tip is fading out, the new tip appears only after the fade-out animation completes. Tips must not overlap during transitions.

---

## 13. Anchor Definitions

### 13.1 REFERENCE_BUTTON

**Screen:** Camera  
**Target UI element:** Reference button in the camera bottom bar (both portrait and landscape positions).  
**Binding:** `Modifier.guideTipAnchor(GuideTipAnchorKey.REFERENCE_BUTTON, onGuideTipAnchor)` applied to the `reference_action_slot` Box.  
**Valid when:** Element is in composition and has non-zero bounds.

### 13.2 SHARE_ACTION

**Screen:** Compare  
**Target UI element:** Export/Share icon button in CompareScreen TopAppBar (`compare_screen_export_button`).  
**Binding:** `Modifier.guideTipAnchor(GuideTipAnchorKey.SHARE_ACTION, onGuideTipAnchor)` applied to the Export icon button.  
**Valid when:** Element is in composition and has non-zero bounds.

### 13.3 OVERFLOW_ACTION

**Screen:** Compare  
**Target UI element:** Overflow (⋮) menu button in CompareScreen TopAppBar.  
**Binding:** `Modifier.guideTipAnchor(GuideTipAnchorKey.OVERFLOW_ACTION, onGuideTipAnchor)` applied to the overflow icon button.  
**Valid when:** Element is in composition and has non-zero bounds.

### 13.4 LIBRARY_GRID_AREA

**Screen:** Library (CompareLibraryScreen)  
**Target UI element:** A stable phantom anchor placed at the fixed top-left entry point of the comparison grid area — immediately before the `LazyVerticalGrid` composable, not on any individual tile.  
**Why stable anchor:** Comparison tiles are in a lazy grid and scroll out of composition. Anchoring to the first tile would cause the anchor bounds to become zero when the tile scrolls off screen, making the placement algorithm defer the tip mid-session. The grid-area anchor is always in the composition tree regardless of scroll state.  
**Binding:** `Modifier.onGloballyPositioned { ... }` applied to a zero-size `Box` placed immediately before the `LazyVerticalGrid` in the screen's content column, at the same horizontal and vertical alignment as where the first tile would appear.  
**Bounds:** The anchor bounds report the position of the top-left grid entry point with a nominal size sufficient for placement (e.g., `width = 120 dp, height = 80 dp`) hardcoded to approximate the first visible tile dimensions.

Implementation note: use `onGloballyPositioned` with `boundsInRoot()` on a composable that is always rendered (not lazy), positioned at the grid start coordinates.

---

## 14. Placement Algorithm

### 14.1 Candidate Sides

For each `WindowWidthSizeClass`:

| Class | Portrait candidate order | Landscape candidate order |
|---|---|---|
| Compact | ABOVE, BELOW | ABOVE, BELOW, START, END |
| Medium | END, START, ABOVE, BELOW | ABOVE, BELOW, START, END |
| Expanded | END, START, ABOVE, BELOW | ABOVE, BELOW, START, END |

Landscape candidate order depends on `isLandscape` alone, not on `WindowWidthSizeClass` — **any** landscape orientation prefers ABOVE/BELOW first, falling back to START/END. This is because phones commonly cross from Compact into Medium width class once rotated to landscape (their portrait height becomes the landscape width), and the Medium/Expanded side-placement order is intended for wide, tablet-style layouts, not a rotated phone. Keying the branch on width class alone previously caused a landscape phone measuring as Medium to fall through to side placement instead of the intended ABOVE/BELOW-first order. Portrait candidate order still depends on `WindowWidthSizeClass` as before (Compact: ABOVE/BELOW only; Medium/Expanded: side placement first) — only the landscape column changed.

Implementation: pass an `isLandscape: Boolean` parameter to `candidateSides()` in addition to `WindowWidthSizeClass`. When `isLandscape`, include all four sides in ABOVE/BELOW/START/END order, regardless of width class.

### 14.2 Margins

| Parameter | Value |
|---|---|
| Screen edge margin | 16 dp |
| Gap between card and anchor | 8 dp |

### 14.3 Exclusion Zones

The placement input must include a list of exclusion rects that the placed card must not overlap. Current required exclusion zones:

| Zone | Source | Scope |
|---|---|---|
| Capture button bounds | `Modifier.onGloballyPositioned` on Capture button slot | Camera |
| Compare slider handle and viewport | Entire compare viewport bounds | Compare |

The placement algorithm checks `cardRect.overlapsRect(exclusionZone)` for each exclusion zone in addition to the existing anchor overlap check. If any overlap is found, the candidate is rejected and the next side is tried. If all sides fail, the result is `Deferred`.

### 14.4 Horizontal Clamping (ABOVE/BELOW)

The card X position is clamped to `[marginPx, containerWidth - marginPx - cardWidth]`. The card is initially centered on the anchor's horizontal center, then clamped.

### 14.5 Vertical Clamping (START/END)

The card Y position is clamped to `[marginPx, containerHeight - marginPx - cardHeight]`. The card is initially centered on the anchor's vertical center, then clamped.

### 14.6 Deferred Behavior

When placement is deferred, the tip is not rendered. The tip remains eligible and will be re-evaluated on the next `LaunchedEffect` trigger. The deferral must not be treated as a dismissal or completion.

---

## 15. Screen Integration Surface

Each screen that hosts tips must provide:

- An `onGuideTipAnchor` callback passed down to all relevant composables.
- A `GuideTipHost` placed as the topmost layer in the screen's content `Box`.
- `AnimatedVisibility` wrapping `GuideTipHost` keyed on `activeGuideTip != null`.
- An `eligibleTipIds` set derived from live screen state.
- A `tipBlockedCondition` boolean derived from live screen state.
- A `LaunchedEffect` that evaluates and clears the active tip when state changes.

### 15.1 Camera Screen

**GuideTipHost placement:** Last layer in the camera fullscreen `Box`, after all overlays and the snackbar layer.

**Eligibility signals:**

```
val cameraEligibleTipIds = buildSet {
    if (referenceUri == null) add(GuideTipId.REFERENCE)
}.filter { tipId ->
    val tip = GuideTipRegistry.tipFor(tipId)
    tip == null || guideTipAnchors.containsKey(tip.anchorKey)
}.toSet()
```

**Blocked condition:**

```
val cameraTipBlocked = isReferencePickerActive ||
    uiState.isCaptureInProgress ||
    showGpsFallbackDialog ||
    pendingSnackbarEvent != null ||
    snackbarHostState.currentSnackbarData != null ||
    isOverlayInteractionActive ||
    uiState.referenceMarkersState.isEditModeActive
```

**Completion event:** `onReferenceImageSelected()` success in `CameraViewModel` calls `guideTipController?.let { viewModelScope.launch { it.completeTip(GuideTipId.REFERENCE) } }`. The `completeTip()` function marks the tip seen and clears the active tip.

**Exclusion zone:** Pass Capture button bounds to `GuideTipHost`.

### 15.2 Compare Screen

**GuideTipHost placement:** Last layer in the Compare screen composition, inside the main `Box`.

**Eligibility signals:**

```
val compareEligibleTipIds = buildSet {
    if (sessionId != null) {
        if (!shareTipCompleted) add(GuideTipId.SHARE)
        if (shareTipCompleted) add(GuideTipId.EDIT_SESSION)
    }
}.filter { tipId ->
    val tip = GuideTipRegistry.tipFor(tipId)
    tip == null || guideTipAnchors.containsKey(tip.anchorKey)
}.toSet()
```

`shareTipCompleted` is observed from `GuideRepository.observeTipSeen(GuideTipId.SHARE)` collected as state.

**Blocked condition:**

```
val compareTipBlocked = isFullscreen ||
    showExportMenu ||
    showMoreMenu ||
    showDeleteDialog ||
    isBackupInProgress
```

**Completion events:**

- SHARE: `showExportMenu = true` → call `guideTipController?.completeTip(GuideTipId.SHARE)`.
- EDIT_SESSION: Navigation event in `MainActivity` for Edit Session route → call `guideTipController?.completeTip(GuideTipId.EDIT_SESSION)`.

**Slider interaction detection:** A `LaunchedEffect` or gesture observer monitors the compare slider drag state. When a drag with horizontal delta > 8 dp and duration > 100 ms is detected, set `sliderInteractionDetected = true`. After a 1000 ms delay, add `GuideTipId.SHARE` to `compareEligibleTipIds` (by making `isSliderInteractionReady = true`).

**Exclusion zone:** Pass the compare viewport bounds to `GuideTipHost`.

### 15.3 Library Screen (CompareLibraryScreen)

**GuideTipHost placement:** New top-layer in CompareLibraryScreen, after the session grid.

**Eligibility signals:**

```
val libraryEligibleTipIds = buildSet {
    if (sessions.isNotEmpty() && !openComparisonTipCompleted) {
        add(GuideTipId.OPEN_COMPARISON)
    }
    if (openComparisonTipCompleted && !multiSelectTipCompleted) {
        add(GuideTipId.MULTI_SELECT)
    }
}.filter { tipId ->
    val tip = GuideTipRegistry.tipFor(tipId)
    tip == null || guideTipAnchors.containsKey(tip.anchorKey)
}.toSet()
```

`openComparisonTipCompleted` and `multiSelectTipCompleted` are observed from `GuideRepository`.

**Blocked condition:**

```
val isGridScrollInProgress = lazyGridState.isScrollInProgress
val libraryTipBlocked = isMultiSelectActive ||
    showDeleteDialog ||
    isBackupInProgress ||
    isGridScrollInProgress
```

`lazyGridState` is the `LazyGridState` instance used by the comparison grid. `isScrollInProgress` is `true` during both pointer-driven drag and momentum fling. Wrap in `derivedStateOf` to avoid unnecessary recompositions during scroll frames.

**Completion events:**

- OPEN_COMPARISON: Tile tap handler in CompareLibraryScreen → call `guideTipController?.completeTip(GuideTipId.OPEN_COMPARISON)` immediately on tap, before navigating to `CompareScreen`. The completion event is the tap itself; whether the user proceeds to view the comparison is irrelevant.
- MULTI_SELECT: Long-press handler → call `guideTipController?.completeTip(GuideTipId.MULTI_SELECT)`.

**Stable grid anchor:** A zero-size phantom `Box` with `onGloballyPositioned` and a synthetic bounds reporter placed at the logical position of the first grid tile:

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(0.dp)
        .onGloballyPositioned { coordinates ->
            val rootBounds = coordinates.boundsInRoot()
            onGuideTipAnchor(
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
)
```

This phantom anchor is placed immediately before `LazyVerticalGrid` in the screen content column and is always in the composition tree.

---

## 16. GuideTipController Extension

`GuideTipController` gains a `completeTip(tipId: GuideTipId)` suspend function:

```kotlin
suspend fun completeTip(tipId: GuideTipId) {
    repository.markTipSeen(tipId)
    if (_activeTipId.value == tipId) {
        _activeTipId.value = null
    }
    waitingForUserActionAfterDismissal = true
}
```

This function is called from feature event sites and from Learn more's `dismissActiveTip(LEARN_MORE)` path (which already calls `markTipSeen`). It is distinct from `dismissActiveTip()`, which is only called from tip card button actions. The new function does not set `waitingForUserActionAfterDismissal = false` — the controller waits for the next `onUserAction()` call before showing another tip.

---

## 17. Touch Behavior

### 17.1 No Touch Blocking Outside Visible Card

The `GuideTipHost` layout must not intercept touches outside the visible card bounds. The `SubcomposeLayout` with `fillMaxSize()` must not draw a background (no `Modifier.background()`). Touch events pass through the host to underlying layers everywhere except within the placed card's bounds.

### 17.2 Capture Button Always Reachable

The placement algorithm's exclusion zone (§14.3) ensures the card is never placed over the Capture button. The combination of the exclusion zone check and deferral-on-failure guarantees the Capture button is always tappable while a tip is visible.

### 17.3 Card Touch Behavior

Within the card's visible bounds, touches are intercepted by the card's background and action buttons. This is standard Card behavior and is correct.

---

## 18. GuideTipId Changes

New enum values replacing the prior set:

| New ID | storedValue |
|---|---|
| `REFERENCE` | `"reference"` (unchanged) |
| `SHARE` | `"share"` |
| `EDIT_SESSION` | `"edit_session"` |
| `OPEN_COMPARISON` | `"open_comparison"` |
| `MULTI_SELECT` | `"multi_select"` |

Removed IDs:

| Removed ID | Prior storedValue |
|---|---|
| `ALIGN` | `"align"` |
| `COMPARE` | `"compare"` |
| `HISTORY` | `"history"` |
| `EXPORT` | `"export"` |
| `MARKER` | `"marker"` |
| `GPS` | `"gps"` |

DataStore safety: any stored value not in the current `GuideTipId.values()` is silently ignored by `fromStoredValue()`. No migration is required.

---

## 19. GuideTipAnchorKey Changes

New anchor key set:

| New key | Description |
|---|---|
| `REFERENCE_BUTTON` | Reference button (unchanged) |
| `SHARE_ACTION` | Export/Share button in CompareScreen |
| `OVERFLOW_ACTION` | Overflow ⋮ button in CompareScreen |
| `LIBRARY_GRID_AREA` | Stable phantom anchor in CompareLibraryScreen |

Removed anchor keys:

- `ALIGN_CONTROLS`
- `COMPARE_ACTION`
- `HISTORY_ACTION`
- `EXPORT_ACTION`
- `MARKER_ACTION`
- `GPS_CHIP`

---

## 20. GuideTipScope Changes

New scope set:

| Scope | Hosts tip IDs |
|---|---|
| `CAMERA` | `REFERENCE` |
| `COMPARE` | `SHARE`, `EDIT_SESSION` |
| `LIBRARY` | `OPEN_COMPARISON`, `MULTI_SELECT` |

`LIBRARY` is a new scope value. It must be added to the `GuideTipScope` enum.

---

## 21. Persistence

### 21.1 DataStore File

Dedicated DataStore file: `sameview_guide` (unchanged).

### 21.2 Keys

| Key | Type | Description |
|---|---|---|
| `walkthrough_completed` | Boolean | Whether first-run walkthrough has been completed |
| `seen_tip_ids` | StringSet | Completed tip IDs (storedValues) |

No new DataStore keys are introduced. The existing `seen_tip_ids` key stores completed tip IDs, which now includes tips the user dismissed via the Dismiss action — Dismiss and Learn more both persist to this key identically (§6.1, §6.3). The only way a tip is *not* persisted is navigating away from the screen without tapping either action (§6.5).

### 21.3 Reset

`GuideRepository.resetContextualTips()` removes `seen_tip_ids`. Walkthrough completion is unaffected.

---

## 22. Copy Specification

### 22.1 English Strings

| Key | Value |
|---|---|
| `guide_tip_reference_title` | `"Reference photos"` |
| `guide_tip_reference_body` | `"Choose an earlier photo to line up the same view."` |
| `guide_tip_share_title` | `"Share your comparison"` |
| `guide_tip_share_body` | `"Export a slider image or video from your saved comparison."` |
| `guide_tip_edit_session_title` | `"Edit session details"` |
| `guide_tip_edit_session_body` | `"Add a title, date, or location to this comparison."` |
| `guide_tip_open_comparison_title` | `"Open a comparison"` |
| `guide_tip_open_comparison_body` | `"Tap any comparison to review it side by side."` |
| `guide_tip_multi_select_title` | `"Select multiple"` |
| `guide_tip_multi_select_body` | `"Long-press to select comparisons for export or deletion."` |
| `guide_tip_learn_more` | `"Learn more"` (unchanged) |
| `guide_tip_dismiss` | `"Dismiss"` |

Remove: `guide_tip_got_it`, `guide_tip_align_title`, `guide_tip_align_body`, `guide_tip_marker_title`, `guide_tip_marker_body`, `guide_tip_gps_title`, `guide_tip_gps_body`, `guide_tip_compare_title`, `guide_tip_compare_body`, `guide_tip_history_title`, `guide_tip_history_body`, `guide_tip_export_title`, `guide_tip_export_body`.

### 22.2 German Strings

All visible strings require a German translation in `values-de/strings.xml`. The same sentence-case rule applies. German tone: informal (`du`, `dir`). No English-only fallback allowed.

---

## 23. Guide Screen Integration

### 23.1 Reset Guide Tips

"Reset guide tips" remains in the Guide screen as a bottom action, separate from the topic list. The Guide screen is the only location for this action. It is not added to SettingsScreen.

Behavior:
- Tapping "Reset guide tips" shows a confirmation dialog.
- Confirming calls `GuideRepository.resetContextualTips()`, which removes `seen_tip_ids`.
- Walkthrough completion state is unaffected.
- Confirmed reset does not navigate away from the Guide screen.

### 23.2 Guide Topics for Learn More Navigation

The following Guide topics must exist for Learn more navigation:

| Tip | Guide topic |
|---|---|
| `REFERENCE` | `GuideTopicId.REFERENCE_PHOTOS` |
| `SHARE` | `GuideTopicId.SHARE_COMPARISON_IMAGE` |
| `EDIT_SESSION` | `GuideTopicId.GETTING_STARTED` (or a dedicated `EDIT_SESSION` topic if created) |

`OPEN_COMPARISON` and `MULTI_SELECT` have no Learn more action and do not require a topic mapping.

---

## 24. Responsive Behavior

Tip placement adapts to `WindowWidthSizeClass` as described in §14.1.

On all size classes:

- Max card width: 280 dp (never stretches).
- Tips must not appear far from their anchor (Portrait: Compact prefers ABOVE/BELOW, Medium/Expanded prefers side placement; any landscape orientation prefers ABOVE/BELOW first, regardless of width class — see §14.1).
- Deferred tips are suppressed silently and re-evaluated on next eligible state change.

Tip placement behavior must not conflict with the overlay, control, or navigation areas defined in `RESPONSIVE_LAYOUT_SYSTEM_V1.md` for each screen.

---

## 25. Accessibility

- Tips must be reachable by TalkBack.
- Focus traversal must enter the tip after the anchor context without trapping focus.
- All buttons must have explicit content descriptions or text labels.
- The tip card must be announced as a hint or popup region (use `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`).
- Learn more must announce navigation intent: "Opens Reference photos in Guide."
- Dismiss must be labeled "Dismiss" with no additional annotation needed.

---

## 26. Test Requirements

### 26.1 Unit Tests

| Test | Class |
|---|---|
| Placement: card does not overlap Capture button bounds | `GuideTipPlacementTest` |
| Placement: card does not overlap compare viewport bounds | `GuideTipPlacementTest` |
| Placement: Compact + landscape adds START/END candidates | `GuideTipPlacementTest` |
| Placement: card max width ≤ 280 dp in 360 dp container | `GuideTipPlacementTest` |
| Placement: Deferred when no safe side exists | `GuideTipPlacementTest` |
| Controller: completeTip marks seen and clears active | `GuideTipControllerTest` |
| Controller: dismissed tip (GOT_IT) is marked seen, identically to Learn more | `GuideTipControllerTest` |
| Controller: EDIT_SESSION not eligible without SHARE completed | `GuideTipControllerTest` |
| Controller: MULTI_SELECT not eligible without OPEN_COMPARISON completed | `GuideTipControllerTest` |
| Repository: resetContextualTips removes seen_tip_ids | `GuideRepositoryTest` |
| Repository: walkthrough_completed unaffected by reset | `GuideRepositoryTest` |
| Repository: unknown stored IDs are ignored | `GuideRepositoryTest` |

### 26.2 Instrumented Tests

| Test | Class |
|---|---|
| Reference tip anchor to Reference button | `CameraGuideTipIntegrationTest` |
| Reference tip not visible while capture in progress | `CameraGuideTipIntegrationTest` |
| Reference tip not visible while reference picker active | `CameraGuideTipIntegrationTest` |
| Capture button reachable while Reference tip is visible | `CameraGuideTipIntegrationTest` |
| Share tip anchor to Export button | `CompareGuideTipIntegrationTest` |
| Share tip completes on export menu open | `CompareGuideTipIntegrationTest` |
| Edit Session tip not eligible without Share completed | `CompareGuideTipIntegrationTest` |
| Edit Session tip anchor to overflow button | `CompareGuideTipIntegrationTest` |
| Open Comparison tip shows in Library when session exists | `LibraryGuideTipIntegrationTest` |
| Open Comparison tip does not show in multi-select mode | `LibraryGuideTipIntegrationTest` |
| Library tip does not appear while grid is scrolling | `LibraryGuideTipIntegrationTest` |
| Library tip appears after grid scroll settles | `LibraryGuideTipIntegrationTest` |
| Multi Select tip not eligible without Open Comparison completed | `LibraryGuideTipIntegrationTest` |
| Card renders with SameViewAppSurface color | `GuideTipHostTest` |
| Card max width ≤ 280 dp at runtime | `GuideTipHostTest` |
| Pointer is displayed and directional (test tag present) | `GuideTipHostTest` |
| Fade in animation: tip visible after 200 ms | `GuideTipHostTest` |
| Dismiss marks tip seen in repository (same as Learn more) | `GuideTipHostTest` |
| Learn more marks tip seen and navigates | `GuideTipHostTest` |

---

## 27. Migration from Prior Implementation

The following table summarizes what changes relative to the prior tip implementation:

| Area | Prior state | New state |
|---|---|---|
| Tip count | 7 | 5 |
| Card color | `surfaceVariant` (0xFF666666) | `SameViewAppSurface` (0xFF17202F) |
| Card max width | 320 dp | 280 dp |
| Pointer | 8 dp circle inside card | 14×8 dp triangle outside card edge |
| Animation | AnimatedVisibility(visible=true) — no actual animation | AnimatedVisibility keyed on activeGuideTip != null — real 200ms/150ms fade |
| Completion model | Dismissed by Got it / Learn more | Completed by feature event; Dismiss exists separately |
| Button label | "Got it" | "Dismiss" |
| Completion persistence | On button tap | On feature usage event |
| ALIGN tip | Active | Removed |
| MARKER tip | Active | Removed |
| GPS tip | Active | Removed |
| COMPARE tip | Active | Removed |
| HISTORY tip | Active | Removed |
| EXPORT tip | Active | Replaced by SHARE |
| Library scope | Not supported | New: LIBRARY scope with OPEN_COMPARISON and MULTI_SELECT |
| Landscape placement | Compact: ABOVE/BELOW only | Compact landscape: all four sides |
| Exclusion zones | None | Capture button; compare viewport |
| "Reset guide tips" location | Guide screen ("Show tips again") | Guide screen (unchanged) |
