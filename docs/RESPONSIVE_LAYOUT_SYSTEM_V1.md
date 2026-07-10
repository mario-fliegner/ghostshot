# RESPONSIVE_LAYOUT_SYSTEM_V1.md

## 1. Document Status

This document is the **authoritative specification** for the responsive layout system of the SameView Android app.

It defines:
- the primary layout-class system (Window Size Classes)
- breakpoint values
- global layout rules
- per-screen behavior matrix
- explicit boundary conditions and conflicts with existing specs
- the recommended implementation roadmap

This document does NOT define:
- compare rendering (defined in `COMPARE_SESSION_RENDERING_V1.md`)
- compare flow and navigation contracts (defined in `COMPARE_FLOW_V1.md`)
- camera workflow UX rules (defined in `CAMERA_WORKFLOW_UX_V1.md`)
- session metadata storage (defined in `SESSION_METADATA_V1.md`)
- video export behavior (defined in `VIDEO_EXPORT_V1.md`)
- settings categories or settings behavior (defined in `SETTINGS_UX_V1.md`)
- about screen structure (defined in `ABOUT_SCREEN.md`)

If this document conflicts with any screen-specific specification listed above,
**the screen-specific specification wins** for that screen,
unless this document explicitly records a superseding product decision for responsive behavior.

---

## 2. Purpose

SameView was initially designed primarily for a standard smartphone in portrait mode.
As test results have surfaced layout issues on wider displays, a systematic approach is needed.

The goal of this document is to define a **single, long-term layout system** that:
- covers phones (portrait and landscape), foldables, tablets, and desktop/ChromeOS windows
- is based on available window width, not on device type
- aligns with modern Android best practices (Material 3, Jetpack Compose, Window Size Classes)
- can be implemented incrementally without breaking existing behavior or existing specifications
- is maintainable and unambiguous for AI-assisted implementation sessions

---

## 3. Core Decision

### 3.1 Primary Layout System: Window Size Classes

SameView uses **Window Size Classes** as the primary structural layout signal.

The three classes are:

| Class | Width | Typical contexts |
|---|---|---|
| Compact | < 600 dp | Phone portrait; narrow foldable closed |
| Medium | 600 – 839 dp | Phone landscape; foldable open; small tablet |
| Expanded | ≥ 840 dp | Large tablet; large foldable open; ChromeOS window; desktop mode |

These classes are derived from the **available window width** at runtime, via `calculateWindowSizeClass()` in `MainActivity`. They update automatically on configuration changes (rotation, fold/unfold, window resize).

### 3.2 No Device-Type Logic

There is no distinction between "phone" and "tablet" in layout decisions.

Layout behavior is determined exclusively by the current `WindowWidthSizeClass`. Build-time or runtime device detection by screen size, density, or model name is forbidden for layout purposes.

### 3.3 Orientation as a Consequence

Orientation (portrait / landscape) is not a direct layout trigger.

- A phone in landscape produces a Medium-width window (or Compact, on very narrow phones).
- A tablet in portrait produces a Medium or Expanded window depending on the tablet size.
- Layout adapts to the resulting width class, not to whether the device is "in portrait."

Exception: some screens (notably CameraScreen) have height-sensitive behavior. See §4.3.

### 3.4 Existing Landscape Logic Remains Valid

CameraScreen has explicit landscape-specific control placement defined in `CAMERA_WORKFLOW_UX_V1.md`.
`CompareScreen` has explicit portrait and landscape metadata header behavior defined in `COMPARE_FLOW_V1.md §42`.

These existing adaptations remain valid and are not replaced by this document.
This document adds Expanded-class behavior on top of what already exists for Compact and Medium.

---

## 4. Breakpoints

### 4.1 Width Breakpoints

| Class | Condition | dp Range |
|---|---|---|
| Compact | `WindowWidthSizeClass.Compact` | 0 – 599 dp |
| Medium | `WindowWidthSizeClass.Medium` | 600 – 839 dp |
| Expanded | `WindowWidthSizeClass.Expanded` | ≥ 840 dp |

These values match the Material 3 / Jetpack Compose `WindowSizeClass` specification exactly and must not be modified.

### 4.2 Height Breakpoints (Optional, CameraScreen Only)

A height breakpoint exists as an optional companion signal for the CameraScreen, where vertical space directly constrains the camera preview and the reachability of controls.

| Name | Condition | Typical context |
|---|---|---|
| CompactHeight | Window height < 480 dp | Phone landscape on short displays |
| StandardHeight | Window height ≥ 480 dp | All other cases |

**This height breakpoint is defined here for documentation completeness only.**

It is NOT implemented in any screen as of this document version.

When CameraScreen Block 6 is undertaken, `CompactHeight` may be used in combination with `WindowWidthSizeClass` to fine-tune control placement in phone landscape. That decision requires an explicit amendment to `CAMERA_WORKFLOW_UX_V1.md` before implementation.

### 4.3 What WindowSizeClass Does NOT Cover

Foldable posture (flat / half-open / closed) is available through `WindowInfoTracker` (Jetpack Window Manager).
This API is not required for SameView.

Posture information becomes relevant only if a future screen needs to be "fold-aware" (e.g., placing content above the hinge). No current SameView screen requires this. Posture support is out of scope for all blocks defined in this document.

---

## 5. Global Layout Rules

These rules apply to all screens unless a screen-specific section in this document explicitly states an exception.

### 5.1 No Unbounded Content Width on Expanded

On Expanded displays, content must not stretch to fill the full available width.

Free-form content (forms, settings lists, informational cards, video players) must be bounded by a **max-width constraint** and centered horizontally when the available width exceeds the constraint.

Recommended max content width: **680 dp** for form and settings screens.

This value is a default recommendation. Individual screens may use a different value if their content type demands it (e.g., image comparison viewports in CompareScreen may benefit from a wider constraint of up to 900 dp; see §7.2).

### 5.2 Grid Screens Scale Column Count with Width

Screens that use a grid of tiles adapt their column count to the available window width.

Default column count table:

| Class | Column Count |
|---|---|
| Compact | 2 |
| Medium | 3 |
| Expanded | 4 |

Currently only `CompareLibraryScreen` is a grid screen. If future screens introduce grids, they follow the same defaults unless a screen-specific section overrides them.

### 5.3 No Side-Panel Layouts

Side-panel layouts — list + detail pane, content + navigation rail, or any comparable multi-pane structure — are not part of the SameView responsive architecture.

SameView screens are focused, single-purpose screens. The app's "calm and minimal" product philosophy is better served by centered, max-width-bounded single-column layouts than by multi-pane dashboards.

Side-panels are not planned and not implicitly scheduled. If a future screen were ever to require a side-panel evaluation, that would require a separate analysis, an explicit product decision, and an amendment to this document. Side-panel adoption is not a natural consequence of implementing this responsive system.

**Narrow exception (2026-07-10):** `CreateVideoScreen`'s Finished Preview state uses a side-by-side Row (video player left, Share/Done/Delete Video actions column right) exclusively on `WindowWidthSizeClass.Medium` with short locally available height. This exception applies only to this one screen, this one state, and this one width/height combination — it does not extend to any other screen, state, or width class, and it does not constitute a general side-panel allowance. See §7.7 for the exact condition and geometry, and Addendum §A11 for the product decision record.

### 5.4 Navigation Structure Does Not Change with Width

SameView uses a single-activity, Navigation Compose architecture with a flat navigation graph.

Window Size Class changes do not trigger navigation structure changes:
- No bottom navigation bar replaces the current overflow menu structure
- No navigation rail is added
- No navigation drawer is added

The top-right History and Overflow actions defined in `CAMERA_WORKFLOW_UX_V1.md` remain the sole navigation entry points on CameraScreen at all width classes.

Any future navigation restructuring for larger screens is a separate product decision outside this document's scope.

### 5.5 Compose `WindowSizeClass` Is the Only Layout Signal

Layout decisions based on the window size must use `WindowWidthSizeClass` (or `WindowHeightSizeClass` for the CameraScreen height exception in §4.2) exclusively.

The following are forbidden as layout signals:
- `LocalConfiguration.current.screenWidthDp` used as a raw threshold
- `LocalConfiguration.current.orientation` used as a layout switch
- `BoxWithConstraints` used to replicate Window Size Class logic inline
- Device-model or screen-density checks

`BoxWithConstraints` is permitted for local layout adjustments within a screen (e.g., measuring available width for label visibility in the compare slider), but must not be used as a replacement for Window Size Class decisions.

### 5.6 CameraScreen Is Governed by CAMERA_WORKFLOW_UX_V1

The CameraScreen is the most risk-sensitive screen in SameView.
CameraX lifecycle, overlay geometry, and control placement are tightly specified in `CAMERA_WORKFLOW_UX_V1.md`.

This document adds no new constraints to CameraScreen.
All responsive changes to CameraScreen belong exclusively in Block 6 (§10.6).
Block 6 requires an explicit amendment to `CAMERA_WORKFLOW_UX_V1.md` before any implementation begins.

---

## 6. Per-Screen Matrix

The following table summarizes responsive behavior for each screen.
Detailed per-screen sections follow.

| Screen | Compact | Medium | Expanded | Wide Layout | Max-Width Sufficient | Block |
|---|---|---|---|---|---|---|
| CameraScreen | Current (CAMERA_WORKFLOW_UX_V1) | Current (landscape rules) | Deferred | No | No | Block 6 (High Risk) |
| CompareScreen | Current | Current (§42 landscape rules) | Viewport max-width constraint | No | Yes | Block 4 |
| CompareLibrary | 2 columns | 3 columns | 4 columns | Grid scaling only | No (column count changes) | Block 2 |
| EditSessionScreen | Current scrollable form | Current | Max-width bounded form | No | Yes | Block 3 |
| CreateVideoScreen (Configuring) | Current | Current | Max-width bounded | No | Yes | Block 3 |
| CreateVideoScreen (Rendering) | Current | Current | Current (indicators scale fine) | No | No change needed | — |
| CreateVideoScreen (Preview) | Current | Current | Centered player, max-width | No | Yes | Block 5 |
| SettingsScreen | Current | Current | Max-width bounded | No | Yes | Block 3 |
| AboutScreen | Current | Current | Max-width bounded (already specified) | No | Yes | Block 3 |
| Guide main screen | Current (single-column) | Current (single-column) | Max-width bounded, single-column (no topic grid) | No | Yes | New (see §7.10) |

---

## 7. Per-Screen Specification

### 7.1 CameraScreen

**Governing spec:** `CAMERA_WORKFLOW_UX_V1.md` (primary authority)

**Compact behavior:**
Portrait controls (stacked, above bottom bar). Existing behavior. No change.

**Medium behavior:**
Landscape-mode control placement as defined in `CAMERA_WORKFLOW_UX_V1.md`: side-rail History/Overflow, centered bottom row (Reference / Capture / Compare), centered opacity slider. Existing behavior. No change.

**Expanded behavior:**
Not defined. Deferred to Block 6.

**Wide Layout allowed:**
No. Any responsive change to CameraScreen requires a dedicated product decision and an amendment to `CAMERA_WORKFLOW_UX_V1.md`.

**Max-Width sufficient:**
No. The camera preview fills the available area by design; control placement on wide tablets requires independent analysis.

**Risk level:**
High. CameraX lifecycle, overlay geometry (overlayScale, offsets, viewport dimensions), capture token architecture, and the documented landscape invariants in `CAMERA_WORKFLOW_UX_V1.md` all make this screen high-risk for responsive changes.

**Action:**
No change until Block 6. Block 6 is a standalone, isolated work block.

---

### 7.2 CompareScreen

**Governing spec:** `COMPARE_FLOW_V1.md` (primary authority)

**Compact behavior:**
Current vertical layout: TopAppBar → MetadataHeader → Slider Viewport. `ContentScale.Fit` in normal mode, `ContentScale.Crop` in fullscreen mode. Existing behavior. No change.

**Medium behavior:**
Landscape metadata behavior as defined in `COMPARE_FLOW_V1.md §42` (amended 2026-06-19, see §A7): session metadata is integrated inline into the TopAppBar center slot; no separate `CompareMetadataHeader` is rendered below the TopAppBar. When user-authored metadata is present, title (maxLines=1) and/or location are shown. When absent, "Compare" + "Created `<date>`" are shown as a two-line fallback. The compare viewport has 8 dp bottom padding in normal mode. The viewport is wider than before due to the reclaimed header height (~85 dp more for 16:9 sessions). Portrait sessions in landscape remain geometrically narrow under `ContentScale.Fit` — this is intentionally accepted.

**Expanded behavior:**
CompareScreen is and remains a focused, single-viewport compare screen. The vertical layout structure is preserved. No side-panel, no two-column layout, no dashboard structure. This is the current product decision for CompareScreen responsive behavior.

The slider viewport receives a max-width constraint (recommended: ≤ 900 dp, centered horizontally).
The MetadataHeader receives the same max-width constraint and horizontal centering.
The TopAppBar remains full-width (standard Material 3 behavior).

Rationale: `ContentScale.Fit` naturally letterboxes portrait images within a wide viewport, producing correct but visually sparse results. A max-width constraint focuses the comparison in the visual center and reduces the perception of empty margin space on tablets. This is the intended responsive architecture for CompareScreen — not a temporary solution. Any future structural change (side-panel, two-column) would require an explicit product decision and amendments to both this document and `COMPARE_FLOW_V1.md`.

**Wide Layout allowed:**
No. `COMPARE_FLOW_V1.md §12` explicitly forbids "changing into a totally different compare UI in landscape" and "switching compare mode based on device rotation." A side-panel or two-column compare layout would constitute such a change and is therefore forbidden without an explicit product decision and an amendment to `COMPARE_FLOW_V1.md`.

**Max-Width sufficient:**
Yes. The slider comparison mechanic is horizontal and benefits from available width. Constraining the viewport max-width is the intended responsive architecture for CompareScreen. It delivers a polished tablet appearance without structural change. Any future departure from this approach requires an explicit product decision and an amendment to both this document and `COMPARE_FLOW_V1.md`.

**Conflict check:**
`COMPARE_FLOW_V1.md §42` defines specific portrait and landscape row content and maxLines values. This spec does not alter row content or maxLines — it only adds a horizontal max-width container around the existing MetadataHeader and slider viewport. No conflict.

**Fullscreen mode:**
In fullscreen mode, the MetadataHeader and TopAppBar are hidden per `COMPARE_FLOW_V1.md §11A`. The slider viewport max-width constraint, if applied, must remain active in fullscreen mode. The slider and comparison mechanic are unaffected.

**Action:**
Block 4. Low risk. No changes to compare rendering, ContentScale logic, slider behavior, or session data.

---

### 7.3 CompareLibraryScreen

**Compact behavior:**
2-column grid. Existing behavior. No change.

**Medium behavior:**
3-column grid. New behavior (Block 2).

**Expanded behavior:**
4-column grid. New behavior (Block 2).

**Wide Layout allowed:**
Grid column scaling only. No list-detail pane, no side navigation.

**Max-Width sufficient:**
No. Grid column count must change, which requires layout logic.

**Risk level:**
Very low. Column count is isolated to the `LazyVerticalGrid` composable. No rendering, no navigation, no storage, no ViewModel changes.

**Action:**
Block 2. The column count is determined by `WindowWidthSizeClass` passed from `MainActivity`.

---

### 7.4 EditSessionScreen (Session Metadata Editor)

**Governing spec:** `SESSION_METADATA_EDITOR_V1.md`

**Compact behavior:**
Current vertical scrollable form with SettingsCard groups (Session, Reference Photo, Current Photo, Location). Existing behavior. No change.

**Medium behavior:**
Current vertical scrollable form. Adequate at 600–839 dp widths. No change.

**Expanded behavior:**
The form content column is bounded by a max-width constraint (recommended: 680 dp) and centered horizontally.

The existing SettingsCard structure, field ordering, IME chain (Title → Reference Date → Location → City → Country), Save button in `bottomBar`, and back/discard/saving-in-progress dialogs are unchanged.

The scrollable form remains a single column. A two-column form layout is not part of the current responsive architecture for this screen.

**Wide Layout allowed:**
No. Single-column form with max-width constraint. This is the current product decision for this screen.

**Max-Width sufficient:**
Yes. `SESSION_METADATA_EDITOR_V1.md §3` describes the editor as a "form screen" that must feel like "filling in a form at a desk." Max-width centering directly supports this philosophy.

**Conflict check:**
`SESSION_METADATA_EDITOR_V1.md` does not define any specific width behavior. The max-width constraint is additive and does not conflict.

**Action:**
Block 3. Shared with other form screens.

---

### 7.5 CreateVideoScreen — Configuring State

**Governing spec:** `VIDEO_EXPORT_V1.md`

**Compact behavior:**
Current scrollable settings form (mode selector, format, duration, quality, branding toggle). Existing behavior. No change.

**Medium behavior:**
Current. No change.

**Expanded behavior:**
The Configuring state content column is bounded by a max-width constraint (recommended: 680 dp) and centered horizontally.

The `SameViewSegmentControl`, `SettingsCard` groups, and Create Video CTA remain structurally unchanged.

**Wide Layout allowed:**
No. Max-width bounded single-column layout only.

**Max-Width sufficient:**
Yes.

**Action:**
Block 3. Shared with other form screens.

---

### 7.6 CreateVideoScreen — Rendering State

**Compact / Medium / Expanded behavior:**
The `CircularProgressIndicator`, `LinearProgressIndicator`, and frame progress text are centered in the available space at all width classes. This is already the expected behavior in the current implementation. No responsive change needed.

**Action:**
No action required.

---

### 7.7 CreateVideoScreen — Preview State

**Governing spec:** `VIDEO_EXPORT_V1.md`

**Compact behavior:**
A format-correct player card (not a full-bleed player) is centered — horizontally and vertically — within the area remaining after the Share / Done / Delete Video actions have already claimed their true natural height. The card's maximum height follows a 90%-of-available-height visual cap applied to that already-reduced remaining area (a different calibration than the Rendering-state loading card's 62% cap on the full content area — see `VIDEO_EXPORT_V1.md §7.5`); at short available heights, only the card shrinks — Actions are never resized or crowded out, since they are measured and reserved first, independently of the card. See Addendum §A9 and §A10.

**Medium behavior — sufficient height:**
Same as Compact — full available width, format-correct centered card, Actions always fully visible. See Addendum §A9 and §A10.

**Medium behavior — short height:**
When the locally available height (read via `BoxWithConstraints`, not `LocalConfiguration.orientation` and not device detection) is below **420 dp**, the vertical stack is replaced by a side-by-side Row. The player uses its actual, aspect-ratio-derived width — not `weight(1f)` — so the player and the Actions column form one compact shared content group instead of two independently-sized zones; the shared group as a whole is centered (horizontally and vertically) in the available area, with Actions remaining to the right of the player. The player area still spans the full Row height (so the card can be vertically centered within it), and the Actions column remains fixed at **220 dp** wide, natural height, vertically centered in the Row via `Alignment.CenterVertically`. Actions keep their existing order (Share, Done, Delete Video), callbacks, and semantics — only the surrounding column width changes from full-screen to 220 dp. The player card computation is unchanged (90%-of-available-height cap, §7.5), just applied to the Row's height instead of the stack's remainder. This is a narrowly-scoped exception to §5.3 — see that section and Addendum §A11 (original Row implementation) and §A12 (compact shared-group geometry correction).

The 420 dp threshold and 220 dp Actions-column width are specific to this screen and state; they do not reuse or alter §4.2's `CompactHeight` breakpoint (which remains reserved for a future, separately-amended `CameraScreen` decision) and do not change §4.1's width breakpoints.

**Expanded behavior:**
Same card sizing and centering rule as Compact/Medium, additionally bounded by the existing 800 dp max-width container (Addendum §A6). This avoids both full-width letterboxing bars on wide tablets and a top-anchored, unbalanced appearance for narrower (e.g. portrait-format) exported videos. No new tablet-specific structure and no two-column layout are introduced. The Medium short-height Row does not apply to Expanded.

**Wide Layout allowed:**
No, with one narrow, named exception: the Medium short-height Row described above. This does not constitute a general side-by-side layout allowance for this screen or any other — see §5.3.

**Max-Width sufficient:**
Yes, for Compact, Medium with sufficient height, and Expanded. Not sufficient on its own for Medium with short height, which is why the Row exception exists.

**Action:**
Block 5 (Addendum §A6), the card sizing/centering refinement in Addendum §A9 (corrected in §A10), and the Medium short-height Row in Addendum §A11 (geometry corrected in §A12). `RenderingContent` and `ConfiguringContent` are unaffected by any of these.

---

### 7.8 SettingsScreen

**Governing spec:** `SETTINGS_UX_V1.md`

**Compact behavior:**
Current vertical scrollable settings list with SettingsCard sections. Existing behavior. No change.

**Medium behavior:**
Current. No change.

**Expanded behavior:**
The settings content column is bounded by a max-width constraint (recommended: 680 dp) and centered horizontally.

The SettingsCard structure, section order, and all setting behaviors remain unchanged.

`SETTINGS_UX_V1.md §10` already states the "overflow menu background color should visually match the bottom workflow controls" and emphasizes visual calm — a max-width constraint is consistent with this philosophy.

**Wide Layout allowed:**
No.

**Max-Width sufficient:**
Yes.

**Action:**
Block 3.

---

### 7.9 AboutScreen

**Governing spec:** `ABOUT_SCREEN.md` (document title: ABOUT_SCREEN_V2)

**Compact behavior:**
Current layout: TopAppBar, hero card, footer card. Existing behavior. No change.

**Medium behavior:**
Current. No change.

**Expanded behavior:**
`ABOUT_SCREEN.md §10` already explicitly states:
> "centered, max-width bounded content; hero card aligned with Settings card language; separate calm footer card"

The max-width constraint for Expanded is **already specified in the governing spec**.

This document confirms alignment and does not add new requirements.

**Wide Layout allowed:**
No.

**Max-Width sufficient:**
Yes.

**Conflict check:**
None. `ABOUT_SCREEN.md §10` and this document are fully aligned.

**Action:**
Block 3. The max-width implementation for AboutScreen should verify it matches the existing spec.

---

### 7.10 Guide Main Screen

**Governing spec:** `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md` §8, §19 (primary authority for Guide-specific content and structure)

**Compact behavior:**
Single-column topic list: one Getting-started hero card (reusing the existing `AboutScreen` hero-card pattern, `ABOUT_SCREEN.md` §10) followed by three standard `SettingsCard`-family topic rows (Reference photos, GPS guidance, Export). Full available width. No custom card components are introduced.

**Medium behavior:**
Same single-column topic list as Compact. Full available width. No grid.

**Expanded behavior:**
The topic list column is bounded by a max-width constraint (recommended: 680 dp, matching `SettingsScreen` §7.8, `EditSessionScreen` §7.4, and `CreateVideoScreen` Configuring state §7.5) and centered horizontally. Guide main screen does not use a multi-column topic grid at any width class — this supersedes the two-column tablet topic layout previously described in `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md` §19.

**Wide Layout allowed:**
No. Single-column, max-width-bounded layout only, consistent with §5.3 (no side-panel layouts) and the other SettingsCard-family screens.

**Max-Width sufficient:**
Yes. Guide main screen is a list-of-topics screen structurally equivalent to Settings, not a grid screen — §5.2 grid-scaling rules apply only to `CompareLibraryScreen`.

**Conflict check:**
`FIRST_RUN_WALKTHROUGH_GUIDE_V1.md` §19 previously specified phone/tablet-orientation terminology and a two-column tablet topic grid for the Guide main screen. Both are superseded by this section: Guide main screen responsive behavior now follows `WindowWidthSizeClass` exclusively, per §3.2 and §5.5 of this document. `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md` §19 has been updated to match (companion change, same date).

**Action:**
New screen addition to this document (see Addendum below). Not yet scheduled in the §10 roadmap. Recommend grouping with Block 3 (max-width form/settings screens) given identical max-width treatment and shared SettingsCard-family styling; final scheduling is an implementation-planning decision.

---

## 8. Conflict Register

This section lists all known potential conflicts between this document and existing screen specifications, and their resolution.

### 8.1 COMPARE_FLOW_V1.md §12 — Orientation Contract

**Potential conflict:**
§12 states: "Changing into a totally different compare UI in landscape" is forbidden. "Introducing separate landscape-only compare logic unless strictly needed by layout" is forbidden.

**Resolution:**
This document proposes only a viewport max-width constraint on Expanded. This is a bounded container change, not a different compare UI, not a new compare mode, and not landscape-only logic. The slider, rendering, ContentScale, metadata header rows, and navigation contracts are unchanged.

**Status:** No conflict. The max-width constraint is additive and does not trigger any forbidden behavior.

---

### 8.2 COMPARE_FLOW_V1.md §42 — Metadata Header Portrait/Landscape Layout

**Potential conflict:**
§42 defines specific layout rules for the metadata header:
- Portrait: two rows maximum, title maxLines=2, location with smart reduction
- Landscape: two rows maximum, title maxLines=1, same smart reduction

These rules are defined per orientation, not per window size class.

**Resolution:**
This document does not alter §42's row content, maxLines, or smart-reduction logic. The max-width constraint proposed for Expanded is applied to the containing column of the metadata header. The header's internal structure (rows, maxLines, text content) remains exactly as §42 defines.

**Note for Block 4 implementation:**
When implementing the Expanded max-width for CompareScreen, the implementer must verify that the max-width container wraps both the MetadataHeader and the slider viewport without altering the MetadataHeader's internal layout or the existing landscape adjustments.

**Status:** No conflict. Block 4 must preserve §42 internal layout.

---

### 8.3 CAMERA_WORKFLOW_UX_V1.md — CameraScreen Layout Rules

**Potential conflict:**
`CAMERA_WORKFLOW_UX_V1.md` defines explicit invariants for CameraScreen:
- Capture always centered at bottom
- Overlay and Compare symmetrically spaced around Capture
- Side-rail History/Overflow placement for landscape
- Overlay action menu visibility rules

A responsive system could inadvertently introduce behavior that breaks these invariants.

**Resolution:**
This document explicitly defers all CameraScreen responsive work to Block 6. No responsive changes are made to CameraScreen in Blocks 1–5. Block 6 requires a dedicated amendment to `CAMERA_WORKFLOW_UX_V1.md` before implementation begins.

**Status:** No conflict. Deferred by design.

---

### 8.4 ABOUT_SCREEN.md §10 — Max-Width Already Specified

**Potential conflict:**
`ABOUT_SCREEN.md §10` already specifies "centered, max-width bounded content." If this document added different constraints, a conflict would arise.

**Resolution:**
This document affirms the existing spec. No new constraint is introduced. The max-width implementation in Block 3 must align with `ABOUT_SCREEN.md §10`.

**Status:** No conflict.

---

### 8.5 VIDEO_EXPORT_V1.md — CreateVideoScreen Preview State

**Potential conflict:**
`VIDEO_EXPORT_V1.md` defines the Preview state (ExoPlayer, auto-play, loop, Share / Done / Delete Video actions). If max-width constraints alter the action button placement in ways that conflict with the spec, a conflict would arise.

**Resolution:**
Block 5 is a refinement block, not a behavioral change block. The Share/Done/Delete Video actions remain functionally identical. Their visual grouping may be constrained to a max-width container, but their behavior, order, and tap semantics are unchanged.

If Block 5 determines that the spec requires an amendment, the amendment must be explicit and recorded in `VIDEO_EXPORT_V1.md` before implementation.

**Status:** No current conflict. Block 5 may produce a minor amendment to `VIDEO_EXPORT_V1.md` for the visual layout of the Preview state on Expanded.

---

## 9. Release and Play Store Safety

Responsive layout changes are visually-focused and do not touch any subsystems with privacy, permission, or Play compliance implications.

### 9.1 Permissions

No new Android permissions are required or introduced by any block in this roadmap.

The responsive system is a pure UI/layout concern. It does not access location, camera, storage, or any other permission-gated system.

### 9.2 Network and Privacy

No network calls are introduced.
No session data is transmitted.
No analytics or telemetry is added.
No user data is collected, modified, or exposed differently based on screen size.

### 9.3 Session and Rendering Data

Responsive layout changes do not affect:
- `metadata.json` schema or content
- `capture.jpg`, `reference.jpg`, or `reference-original.jpg` file contents
- `CaptureSessionSnapshot` geometry
- `ReferenceRenderer.render()` behavior
- Compare rendering pipeline
- Video export pipeline

The responsive system operates entirely at the UI layer. Session files, rendering contracts, and storage architecture are unchanged.

### 9.4 CameraScreen Safety Boundary

CameraX lifecycle, the camera preview, and the capture pipeline are not touched until Block 6.
Block 6 is explicitly deferred and requires a separate risk assessment before any work begins.

Any implementation work in Blocks 1–5 that touches files in the CameraScreen composition tree or CameraViewModel is a scope violation.

### 9.5 Existing Test Coverage

Blocks 2–5 must not break any currently green tests.

Each block must:
- leave existing instrumentation and unit tests passing
- add new tests for any introduced layout branch (e.g., column count changes in CompareLibrary based on size class)

The MediaStoreWriterGpsTest pre-existing flakiness (Samsung IS_PENDING timing race, documented in `IMPLEMENTATION_NOTES.md`) is not affected by responsive layout changes. It must not be used as a false regression signal.

---

## 10. Implementation Roadmap

The blocks below define the recommended order for implementing the responsive layout system.
Each block is independent: a later block does not depend on an earlier block being complete,
unless explicitly stated.

### Block 1 — Responsive Layout System Spec

**What:** This document (`RESPONSIVE_LAYOUT_SYSTEM_V1.md`).

**Why:** All subsequent blocks depend on agreed breakpoints, rules, and screen assignments. This document must exist and be reviewed before implementation begins.

**Risk:** None. Documentation only.

**Prerequisite for:** All blocks.

**Status:** This document is Block 1.

---

### Block 2 — CompareLibrary Grid Scaling

**What:**
`CompareLibraryScreen` changes from a fixed 2-column grid to a window-size-class-driven column count: Compact=2, Medium=3, Expanded=4.

**Why:** The highest visual improvement per implementation effort. A 2-column grid on a 12" tablet in landscape produces very large, sparse tiles. 3–4 columns dramatically improves information density without any UX or behavior change.

**Scope:**
- `CompareLibraryScreen.kt`: change `LazyVerticalGrid` fixed column count to use `WindowWidthSizeClass`
- `MainActivity.kt` or the screen call site: pass `windowSizeClass` (or `widthSizeClass`) to `CompareLibraryScreen`
- Add test coverage for column count by size class

**Scope exclusions:**
- No changes to tile layout, session scanning, delete behavior, backup flow, or multi-select mode
- No changes to navigation

**Risk:** Very low. Isolated to the grid composable.

**Prerequisite:** Block 1.

---

### Block 3 — Max-Width Constraints for Form and Settings Screens

**What:**
The following screens receive a max-width content column (680 dp, centered) on Expanded:
- `EditSessionScreen`
- `SettingsScreen`
- `AboutScreen`
- `CreateVideoScreen` Configuring state

**Why:** All four screens share the same SettingsCard-based layout language and the same "calm form" philosophy. A single shared max-width pattern can be applied to all four in one block. The improvement is immediately visible on tablets.

**Scope:**
- Each screen composable: wrap existing column content in a centered max-width container when `WindowWidthSizeClass.Expanded`
- Pass `windowSizeClass` from `MainActivity` to each affected screen
- No logic, ViewModel, navigation, or storage changes

**Scope exclusions:**
- No changes to field validation, save behavior, or storage in EditSessionScreen
- No changes to settings persistence
- No changes to video export logic
- No changes to About content

**Risk:** Low. Pure layout change. No rendering, no data, no navigation.

**Note for EditSessionScreen:** The Save button in `Scaffold.bottomBar` must remain correctly aligned when content is max-width constrained. Block 3 must verify that `navigationBarsPadding()` and `imePadding()` on the Save button continue to work correctly.

---

### Block 4 — CompareScreen Viewport Refinement

**What:**
On Expanded, the `CompareScreen` slider viewport and MetadataHeader are bounded by a max-width constraint (recommended: ≤ 900 dp) and centered horizontally.

**Why:** On tablets, the comparison viewport spans the full screen width. Portrait-aspect images appear with very large side margins under `ContentScale.Fit`. Centering the viewport focuses the comparison in the middle of the display and improves the perceived quality of the screen.

**Scope:**
- `CompareScreen.kt`: add max-width container around slider viewport and MetadataHeader for Expanded
- Pass `windowSizeClass` from `MainActivity` to `CompareScreen`
- The MetadataHeader's internal structure (§42 row logic, maxLines, smart reduction) is unchanged
- Fullscreen mode: max-width constraint remains active in fullscreen

**Scope exclusions:**
- No changes to compare rendering, ContentScale, or divider logic
- No changes to session data, metadata, or navigation
- No side-panel layout
- No changes to `COMPARE_FLOW_V1.md §42` row content or maxLines

**Risk:** Low. The max-width constraint is a wrapping container; compare rendering is unaffected.

**Prerequisite:** Block 1. Independent of Blocks 2 and 3.

---

### Block 5 — CreateVideoScreen Preview State Refinement

**What:**
On Expanded, the CreateVideoScreen Preview state (ExoPlayer + Share / Done / Delete Video actions) is bounded by a max-width constraint and centered horizontally.

**Why:** On wide tablets, an ExoPlayer container at full screen width produces large letterbox bars for non-widescreen video formats. Share/Done/Delete actions spread across the full width and lose visual grouping.

**Scope:**
- `CreateVideoScreen.kt` Preview state composable: add max-width container around the player and action buttons for Expanded
- The exact max-width value must be determined in Block 5, considering that SameView supports Original, Portrait, and Landscape video formats

**Scope exclusions:**
- No changes to ExoPlayer initialization, playback behavior, or media handling
- No changes to Share intent, Delete Video confirmation, or Done navigation
- No changes to video export pipeline

**Risk:** Low. Pure layout wrapping.

**Prerequisite:** Block 1. Independent of Blocks 2–4.

**Note:** If Block 5 requires a visual layout amendment to `VIDEO_EXPORT_V1.md` for the Preview state on Expanded, that amendment must be written and reviewed before implementation begins.

---

### Block 6 — CameraScreen Responsive Pass

**What:**
A dedicated, isolated responsive analysis and implementation for CameraScreen.

This block is high-risk and deferred. It requires:
1. A separate pre-implementation analysis of CameraX behavior on wide displays
2. Product decisions for control placement on Expanded (tablet landscape)
3. An explicit amendment to `CAMERA_WORKFLOW_UX_V1.md` before any code changes

**Why deferred:** CameraX lifecycle, `CaptureSessionSnapshot` geometry, overlay transform state, and the existing landscape invariants documented in `CAMERA_WORKFLOW_UX_V1.md` make the CameraScreen uniquely sensitive. Responsive changes to other screens carry near-zero risk to the camera pipeline. Changes to CameraScreen carry meaningful risk to capture reliability, overlay behavior, and navigation correctness.

**Scope (to be defined in Block 6 planning):**
- Control placement on Expanded (capture button reach on wide tablets)
- Side-rail behavior on Expanded
- Optional `CompactHeight` consideration for phone landscape (§4.2)
- Real-device validation on at least one tablet and one foldable

**Prerequisite:** All of Blocks 1–5 complete. Dedicated CameraScreen risk analysis. Amendment to `CAMERA_WORKFLOW_UX_V1.md`.

**Risk:** High.

---

## 11. Authority and Governance

### 11.1 This Document vs. Screen Specifications

This document defines the **layout system and per-screen behavior matrix**.

Screen-specific implementation details, constraints, and forbidden behaviors remain governed by their respective specifications (`CAMERA_WORKFLOW_UX_V1.md`, `COMPARE_FLOW_V1.md`, etc.).

When a block requires layout behavior that conflicts with a screen-specific specification, the conflict must be resolved by amending the screen-specific specification first, with an explicit product decision. Silent overrides are forbidden.

### 11.2 Amending This Document

This document may be amended in future blocks when:
- A new screen is added to SameView that requires responsive behavior
- A per-screen responsive decision is explicitly changed via a product decision (e.g., a screen's Expanded layout is fundamentally redesigned)
- Block 6 produces CameraScreen-specific responsive rules

Amendments must be recorded in a dated addendum section at the end of this document, following the addendum pattern established in `COMPARE_FLOW_V1.md` and `CLAUDE_PROJECT_INSTRUCTION.md`.

### 11.3 AI Implementation Rule

AI coding systems working from this document must:
- Implement only the block explicitly requested
- Not implement future blocks or other screens' responsive behavior as a side effect
- Not introduce device-type detection logic
- Verify that existing tests remain green after each block
- Consult the screen-specific specification before implementing any screen's responsive behavior
- Record any required spec amendment before, not after, implementation

---

## Addendum (2026-06-18) — Block 3 Refined Scope

### A1. Block 3 Split into 3A and 3B

The original §10.3 description treats Block 3 as a single block covering four screens. Based on pre-implementation analysis, Block 3 has been split:

**Block 3A** (completed 2026-06-18):

- `SettingsScreen`: max-width 680 dp, centered, Expanded only. Implemented.
- `CreateVideoScreen` Configuring state: max-width 680 dp, centered, Expanded only. Implemented.

**Block 3B** (not yet started):

- `EditSessionScreen`: max-width 680 dp, centered, Expanded only.
- Deferred from Block 3A because `EditSessionScreen` has a `Scaffold.bottomBar` Save button with `navigationBarsPadding()` and `imePadding()` that requires separate handling and verification. This is an isolated complexity that does not affect the simpler screens in Block 3A.

The per-screen sections §7.4, §7.5, and §7.8 are unchanged in their original content. Their "Action: Block 3" entries now map to Block 3B (§7.4) and Block 3A (§7.5, §7.8) respectively.

### A2. AboutScreen Removed from Block 3

`AboutScreen` (§7.9) has been removed from the Block 3 implementation scope.

The `AboutScreenContent` composable already contains `widthIn(max=520.dp)` applied unconditionally to the inner content column, combined with `horizontalAlignment = Alignment.CenterHorizontally` on the outer column. This satisfies the "centered, max-width bounded content" requirement stated in `ABOUT_SCREEN.md §10` and confirmed by §7.9 of this document.

No `WindowWidthSizeClass` parameter is required for `AboutScreen`. No further action is needed.

### A3. Max-Width Decision: Expanded Only

Following analysis of the Medium width range (600–839 dp), the 680 dp max-width constraint is applied **on `WindowWidthSizeClass.Expanded` only** for all Block 3 screens.

Rationale: §7.8 and §7.5 explicitly specify "Medium | Current. No change." The Medium range (600–839 dp) includes phones in landscape and small foldables where a full-width settings or wizard screen is acceptable. The max-width constraint produces a meaningful, visible improvement only on Expanded (≥ 840 dp). No spec change is required; this addendum records the confirmed alignment with the existing per-screen matrix.

---

### A4. Block 3B Completed (2026-06-18)

`EditSessionScreen` (§7.4) has been implemented as Block 3B.

- Max content width: **680 dp**, centered horizontally, on `WindowWidthSizeClass.Expanded` only
- Compact and Medium: current behavior unchanged
- The `Scaffold.bottomBar` Save button is visually constrained to the same 680 dp width as the form content on Expanded. `navigationBarsPadding()` and `imePadding()` remain on the outermost `fillMaxWidth()` container; Scaffold's bottomBar height measurement and IME-above-keyboard behavior are unaffected.

**Block 3 completion status:**

| Block | Screen(s) | Status |
| --- | --- | --- |
| Block 3A | SettingsScreen, CreateVideoScreen (Configuring) | Completed 2026-06-18 |
| Block 3B | EditSessionScreen | Completed 2026-06-18 |
| AboutScreen | No action — already responsive (widthIn 520 dp) | — |

All Block 3 work is complete. No open Block 3 tasks remain.

---

### A5. Block 4 Completed (2026-06-18)

`CompareScreen` (§7.2) has been implemented as Block 4.

- Max content width: **900 dp**, centered horizontally, on `WindowWidthSizeClass.Expanded` only
- Compact and Medium: current behavior unchanged
- A single `Box`/`Column` wrapper encloses the compare viewport (and `CompareMetadataHeader` in portrait); the `TopAppBar` remains full-width outside the container
- Fullscreen mode: `TopAppBar` and `CompareMetadataHeader` are hidden as before; the 900 dp container remains active for the compare viewport
- No changes to compare rendering, slider, divider, handle, labels, edge-hiding, `computeFitBounds`, or session data
- Note: landscape metadata placement was subsequently updated per §A7; the landscape `CompareMetadataHeader` branch described here is no longer rendered

**Responsive layout block completion status:**

| Block | Screen(s) | Status |
| --- | --- | --- |
| Block 2 | CompareLibraryScreen | Completed 2026-06-18 |
| Block 3A | SettingsScreen, CreateVideoScreen (Configuring) | Completed 2026-06-18 |
| Block 3B | EditSessionScreen | Completed 2026-06-18 |
| Block 4 | CompareScreen | Completed 2026-06-18 |
| Block 5 | CreateVideoScreen (Preview) | Completed 2026-06-18 |
| Block 6 | CameraScreen | Deferred — high risk |

---

### A6. Block 5 Completed (2026-06-18)

`CreateVideoScreen` Preview State (§7.7) has been implemented as Block 5.

- Max content width: **800 dp**, centered horizontally, on `WindowWidthSizeClass.Expanded` only
- Compact and Medium: current behavior unchanged
- A single `Box`/`Column` wrapper encloses both the `PlayerView` and the actions group (Share / Done / Delete Video); they share the same 800 dp column
- `TopAppBar` remains full-width outside the container
- No changes to ExoPlayer configuration, playback, Share intent, Delete dialog, Done navigation, `RenderingContent`, `ConfiguringContent`, or export pipeline
- Isolated verification passed after one unrelated full-suite `CameraControlsOverlayTest` Compose hierarchy flake (pre-existing; passes in isolation)

---

### A7. CompareScreen Landscape Metadata and Viewport Refinements (2026-06-19)

`CompareScreen` §7.2 Medium behavior reflects amendments to `COMPARE_FLOW_V1.md §42` (2026-06-19):

**Metadata placement:**
In landscape mode, session metadata is no longer rendered as a separate `CompareMetadataHeader` component below the TopAppBar. Instead, it is integrated inline into the TopAppBar center slot via `Modifier.weight(1f)`. This reclaims the vertical space previously consumed by the header (~48 dp), increasing the compare viewport width for 16:9 sessions by approximately 85 dp.

When user-authored metadata (title and/or location) is present, it is shown in the TopAppBar center slot. When no user-authored metadata is present, the center slot shows two lines: (1) the screen title "Compare" (`titleLarge`, primary), and (2) "Created `<date>`" (`bodySmall`, secondary) when a timestamp is available. This matches the created-date fallback rule applied in portrait mode, while keeping the screen title visible and prominent.

**Viewport bottom padding:**
In landscape normal mode, the compare viewport has 8 dp bottom padding (`padding(bottom = if (isFullscreen) 0.dp else 8.dp)`), preventing the viewport from touching the screen edge. In fullscreen this padding is 0 dp and the viewport uses the maximum available screen space.

**Portrait sessions in landscape — accepted geometry:**
When the reference image has a portrait aspect ratio and the device is in landscape orientation, `ContentScale.Fit` produces a narrow vertical viewport. The remaining horizontal space shows the app background color. This geometry is intentionally accepted. No zoom, crop, or alternative compare mode compensates for it. Any future change requires an explicit product decision.

Portrait mode and fullscreen mode are otherwise unchanged. The §7.2 statement "Existing behavior. No change." for Medium landscape behavior at the time of Block 4 completion no longer applies. All compare rendering, ContentScale logic, slider behavior, and session data are unchanged.

---

### A8. Guide Main Screen Added (2026-07-07)

Per §11.2, this addendum records that a new screen has been added to this document's scope.

`Guide main screen` (`FIRST_RUN_WALKTHROUGH_GUIDE_V1.md` §8) is added to the per-screen matrix (§6) and per-screen specification (§7.10). This follows the Guide information architecture consolidation from 9 topics to 5 topics, which removed the rationale for the two-column tablet topic grid previously described in `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md` §19.

Guide main screen responsive behavior now follows `WindowWidthSizeClass` exclusively, consistent with §3.2 and §5.5. The corresponding phone/tablet-orientation language in `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md` §19 has been updated to match (companion change, same date).

This addendum does not record implementation. Guide main screen responsive behavior is specified but not yet scheduled or implemented — see §7.10.

**Out of scope for this addendum:** `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md` §19's "Guide detail screens" and "Walkthrough" subsections still use phone/tablet-orientation terminology and are not addressed here. Guide detail screens and Walkthrough were not part of the Guide Main Screen review that produced this amendment. A future amendment would be required to bring those subsections under this document's `WindowWidthSizeClass` model.

---

### A9. CreateVideoScreen Preview State — Format-Correct Centered Card (2026-07-10)

Following feedback that the finished-video preview appeared visually unlike the Rendering-state loading preview (full-bleed player starting directly under the TopAppBar, versus a smaller, centered card during rendering), `CreateVideoScreen` Preview State (§7.7) received a card-sizing and centering refinement on top of the Block 5 800 dp max-width constraint (§A6). A prior attempt at this same goal (a fixed `PREVIEW_ACTIONS_RESERVED_HEIGHT` estimate of the Actions block's height) was found to be unreliable on real devices and was fully rolled back before this refinement was designed; this addendum supersedes that rolled-back attempt, not §A6.

- The player area remains a `weight(1f)` region of the existing `Column`, measured *after* the Share/Done/Delete Actions column (an unchanged, non-weighted sibling) has already claimed its true natural height — Actions are never estimated, and can never be crowded out, because their reservation is completely independent of anything that happens inside the player area.
- Within that already-safe player area, the player is no longer stretched to fill it. A `BoxWithConstraints` reads the true (already-safe) remaining width/height, and a format-correct card is sized against it using the same 62%-of-available-height visual cap principle as `RenderingContent`'s loading card (§7.6) — a visual size limit for centering, not a stand-in for a measured Actions height.
- The card's aspect ratio is the exported MP4's real aspect ratio, read via `Player.Listener.onVideoSizeChanged` on the existing `ExoPlayer` instance — not from the wizard's selected export format or session viewport ratio, and without extending `CreateVideoState.Preview` or touching `CreateVideoViewModel`. A neutral `4f / 3f` fallback is used until the real size is known; a single layout reflow is accepted once it is.
- `PlayerView.resizeMode` is set explicitly to `AspectRatioFrameLayout.RESIZE_MODE_FIT`, so no future default-behavior change can silently introduce cropping.
- No fixed top spacer or device-specific offset is used anywhere in this refinement — the visible gap above the card is a consequence of the card's height cap and its centering within the already-safe player area.
- Applies uniformly to Compact, Medium, and Expanded; the existing 800 dp Expanded width cap (§A6) is unchanged and composes with this refinement.
- No changes to the Configuring state, the Rendering state, `CreateVideoViewModel`, the export pipeline, encoder, or MediaStore behavior.

**Status:** Implemented 2026-07-10.

---

### A10. Finished-Preview Card Height Cap Corrected to 90% (2026-07-10)

Real-device validation of §A9 showed that applying the Rendering-state's 62% cap again to the already-reduced Finished-Preview player area made the final preview substantially smaller than the Rendering preview — the same proportional cap was being applied twice to two different-sized bases (§A9's player area is already the remainder *after* Actions reservation, not the full content area §7.6's 62% assumes).

- Finished Preview's card height cap changed from 62% to **90%** of its own safe, already actions-reduced weighted player area.
- Actions reservation, the `weight(1f)` mechanism, and all other responsive safety guarantees described in §A9 remain unchanged.
- No other layout change: no new spacer, no new percentage elsewhere, no change to `RenderingContent`, `ConfiguringContent`, the ViewModel, the state machine, or the export pipeline.

**Status:** Implemented 2026-07-10.

---

### A11. Finished-Preview Medium Short-Height Row Layout (2026-07-10)

Real-device validation (Samsung Galaxy S23, landscape) showed that even after §A10's 90% correction, the vertical-stack layout could not be fixed by calibration alone on `WindowWidthSizeClass.Medium` windows with short available height: the Actions column has a fixed, orientation-independent natural height, so on a short window it consumes a disproportionate share of a much smaller total, leaving only a sliver for the player — while the window's ample width went unused. Root-cause analysis proved this mathematically (any percentage cap on `totalHeight − fixedActionsHeight` degrades toward zero as `totalHeight` shrinks toward `fixedActionsHeight`) and concluded no calibration change could resolve it; a structural change was required.

- On `WindowWidthSizeClass.Medium` with locally available height below **420 dp** (read via `BoxWithConstraints`, not orientation or device detection), `PreviewContent` uses a `Row` instead of the vertical `Column` stack: player area left (`weight(1f)`, full Row height), Actions column right (fixed **220 dp** wide, natural height, `Alignment.CenterVertically`).
- Compact, Medium with sufficient height, and Expanded are unaffected — they keep the exact vertical-stack code path described in §A9/§A10, unchanged.
- The player card computation (90% height cap, real aspect ratio, `RESIZE_MODE_FIT`, `Player.Listener`) is unchanged — only the reference area it operates on changes for this one branch (Row height instead of Column remainder).
- Actions keep their existing order, callbacks, test tags, and confirmation-dialog behavior; only the surrounding column's width changes from full-screen to 220 dp (chosen so the longest existing button labels in English and German — "Delete video" / "Video löschen" — render on one line without wrapping or ellipsizing).
- This is a narrowly-scoped exception to §5.3 ("No Side-Panel Layouts"), limited to `CreateVideoScreen`'s Finished Preview state, Medium width, and short height only. It does not generalize to other screens, states, or width classes, and does not alter §4.1's width breakpoints or §4.2's `CompactHeight` (which remains reserved for a separate, future `CameraScreen` decision).
- No changes to `RenderingContent`, `ConfiguringContent`, `CreateVideoViewModel`, the state machine, navigation, the export pipeline, `Player.Listener` behavior, Autoplay/Loop/Mute, or `RESIZE_MODE_FIT`.

**Status:** Implemented 2026-07-10.

---

### A12. Finished-Preview Medium Short-Height Row Geometry Corrected (2026-07-10)

Real-device smoke testing of §A11 showed that although the player was no longer undersized, the Row read as two visually disconnected islands: player left, Actions right, with a large dead area between them. Root-cause analysis found the cause was not the Row structure itself but its internal geometry — the player area's `weight(1f)` claimed the entire remaining Row width regardless of the card's actual (much smaller) rendered size, and the card was centered within that artificially inflated zone rather than relative to Actions. This addendum corrects the geometry; it does not revise §A11's decision to use a Row, and does not change the 420 dp threshold or the 220 dp Actions-column width.

- The player area's `weight(1f)` is replaced by an explicit `widthIn(max = availablePlayerWidth)`, where `availablePlayerWidth = (outerMaxWidth − PreviewActionsColumnWidth).coerceAtLeast(0.dp)` — a precise, non-estimated subtraction of the Actions column's own known fixed width (220 dp already includes its internal padding and `navigationBarsPadding()`, since those apply *inside* its fixed outer width), not a guess about anything variable. The player area keeps `fillMaxHeight()`, so the card computation (90% height cap, §7.5) is unaffected and continues to operate on the full Row height.
- The `Row` no longer `fillMaxSize()`s; without `weight(1f)` forcing it to fill all available width, it wraps to its true content width (card width + Actions width), so player and Actions form one compact group instead of two independently-positioned zones.
- The outer `BoxWithConstraints` around the Medium/Compact/Expanded branch decision changes `contentAlignment` from `TopCenter` to `Center`, so the now content-sized Row is centered as a whole in the available area. This is shared with the vertical-stack branch (Compact, Medium with sufficient height, Expanded), but has no effect there — that branch's `Column` always `fillMaxHeight()`s and matches the full available width, making either alignment value equivalent; confirmed by the unchanged Compact/Expanded tests remaining green.
- Actions-column width (220 dp), threshold (420 dp), button order, styles, callbacks, test tags, and touch targets are unchanged.
- No changes to `RenderingContent`, `ConfiguringContent`, `CreateVideoViewModel`, the state machine, navigation, the export pipeline, `Player.Listener`, Autoplay/Loop/Mute, `RESIZE_MODE_FIT`, or `videoAspectRatio` determination.

**Status:** Implemented 2026-07-10.
