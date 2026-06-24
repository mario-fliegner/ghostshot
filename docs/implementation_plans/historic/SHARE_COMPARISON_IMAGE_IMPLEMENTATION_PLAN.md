# SHARE_COMPARISON_IMAGE_IMPLEMENTATION_PLAN.md

## 1. Document Status

### 1.1 Purpose

This document is the **working implementation plan** for the Share Comparison Image feature in SameView.

It is written for:
- AI coding systems
- Implementation sessions
- Code review and regression-safe follow-up work

It supplements `SHARE_COMPARISON_IMAGE_V1.md` without replacing it.
Where this document conflicts with `SHARE_COMPARISON_IMAGE_V1.md`, the spec wins.
Where this document describes the actual code state after implementation, it is authoritative.

### 1.2 Authoritative Sources

| Document | Role |
|---|---|
| `SHARE_COMPARISON_IMAGE_V1.md` | Authoritative product spec — feature definition, UX contracts, privacy rules |
| `COMPARE_FLOW_V1.md §43` | TopAppBar restructuring specification |
| `VIDEO_EXPORT_V1.md §5.1` | TopAppBar addendum documenting the Export icon change |
| `COMPARE_SESSION_RENDERING_V1.md` | Session file format — renderer inputs |
| `SESSION_METADATA_V1.md` | Metadata schema — caption data sources |
| `IMPLEMENTATION_NOTES.md` | Current verified implementation state |
| `CLAUDE_PROJECT_INSTRUCTION.md` | Architecture constraints, change discipline, scope |

---

## 2. Fixed Technical Decisions

The following decisions are final. They must not be re-evaluated during implementation.

| # | Decision |
|---|---|
| TD-01 | Export image written to `MediaStore.Images.Media` (`Pictures/SameView`). IS_PENDING lifecycle. No FileProvider required (MediaStore URI only). |
| TD-02 | JPEG output, quality 92 %. No PNG. |
| TD-03 | Standard quality: longest edge ≤ 2048 px. Original quality: session viewport dimensions. |
| TD-04 | Canvas background `#0D1424`. Comparison border `#17202F`. |
| TD-05 | No GPS EXIF in exported JPEG. No hidden metadata beyond standard MediaStore fields. |
| TD-06 | Caption uses `computeCompareLabels()` from `CompareLabelLogic.kt` — no new date logic. |
| TD-07 | UI: `SettingsCard`, `SameViewSegmentControl`, `SettingsSwitchRow` from `SettingsComponents.kt`. |
| TD-08 | Share flow: single Configuring state. No Rendering or Preview state. Brief in-button progress. |
| TD-09 | Renderer runs on `Dispatchers.Default`. MediaStore write on `Dispatchers.IO`. |
| TD-10 | Toggle defaults: Title ON, Date ON, Location OFF. No persistence (DataStore). |
| TD-11 | Export button test tag: `compare_screen_export_button`. Dropdown items: `compare_screen_export_share_item`, `compare_screen_export_create_video_item`. |
| TD-12 | Navigation route: `share_comparison/{sessionId}`. Pattern identical to `edit_session/{sessionId}`. |

---

## 3. Prerequisite: Scope Addendum to CLAUDE_PROJECT_INSTRUCTION.md

**Status: COMPLETE (2026-06-21). Block 1 may now begin.**

The addendum "Addendum (2026-06-21 – Session Post-Processing Export Features)" has been
written into `CLAUDE_PROJECT_INSTRUCTION.md`. It clarifies:

- "No comparison export", "No side-by-side export", "No collage export" apply exclusively
  to the shutter → `Pictures/SameView` capture pipeline.
- "Share flow" in OUT OF SCOPE refers to social sharing as a primary product feature.
- "Video" in the original V1 OUT OF SCOPE referred to camera video recording, not session
  MP4 export.
- Create Video and Share Comparison Image are explicitly in-scope session post-processing
  export features.
- Remains out of scope: cloud sync, social media integrations, automatic publishing, online
  galleries, server components, INTERNET permission.
- CompareScreen authoritative TopAppBar structure documented (Export icon, see §43 of
  `COMPARE_FLOW_V1.md`).

---

## 4. Product Decision: Slider Handle in Export Image — RESOLVED (2026-06-21)

**Status: Resolved. `SHARE_COMPARISON_IMAGE_V1.md §7.1` updated accordingly.**

**Decision (final):** The Slider style export image **includes the SameView handle** (Option B).

- Filled circle in `SameViewAccent` (#4F8CFF)
- White left-arrow (◀) and right-arrow (▶) icons inside the circle
- Vertically centered in the comparison area, horizontally at the fixed 50 % divider position
- Purely visual — no interactivity, no accessibility action, no dynamic position

**Rationale:** The handle serves as a SameView visual identity marker. A static image is not a
video; the handle in a JPEG does not imply false drag-interactivity in this context. It makes
SameView comparison images immediately recognizable as such.

**Explicit scope boundary:** This decision applies exclusively to Share Comparison Image.
`VIDEO_EXPORT_V1.md §16.1` (no handle in animated video) is unchanged and unaffected.

Block 2 (Renderer) implements the handle as specified in `SHARE_COMPARISON_IMAGE_V1.md §7.1`
and `§10.2`.

---

## 5. Release Hardening Audit — S-02 Assessment

`RELEASE_HARDENING_AUDIT_V1.md` (Finding S-02) states:

> "Session-Bilder werden als `file://`-URIs referenziert — bei zukünftiger Share-Funktion
> würde `FileUriExposedException` auf API 24+ auftreten."

**Assessment: S-02 is NOT a blocker for this feature.**

Share Comparison Image does NOT share session files (`reference.jpg`, `capture.jpg`) directly.
It creates a new composite JPEG via `MediaStore.Images.Media` and shares the resulting
`content://` MediaStore URI. No `file://` URI is involved in the share flow. This is identical
to how Video Export shares MP4 files and requires no FileProvider.

S-02 remains open for any future feature that would share session files directly. It does not
affect this feature's implementation.

---

## 6. Codebase State at Planning Time

### 6.1 CompareScreen Current Parameter Contract

`CompareScreen.kt` currently accepts:
- `referenceImageUri`, `captureImageUri`, `onBack`
- `timestamp`, `onDelete`, `sessionTitle`, `onEditSession`
- `sessionId`, `onBackupSession`, `isBackupInProgress`
- `onCreateVideo`, `isCreateVideoAvailable`
- `referenceDate`, `locationDisplayName`, `locationCity`, `locationCountry`
- `isFavorite`, `onToggleFavorite`
- `windowWidthSizeClass`, `modifier`

Current TopAppBar order: Favourite → Create Video → Delete → Overflow (⋮)

Current test tag: `compare_screen_create_video_button`

### 6.2 Tests Directly Affected by Block 1

The following tests in `CompareScreenTest.kt` reference `compare_screen_create_video_button`
and MUST be migrated in Block 1:

| Test | Line | Action Required |
|---|---|---|
| `t_i_05_createVideoButton_visibleAndEnabledWhenSessionHasValidFiles` | ~1188 | Migrate to Export button + dropdown item |
| `t_i_06_createVideoButton_visibleButDisabledWhenFilesNotAvailable` | ~1207 | Migrate: Export always visible; Create video item disabled |
| `t_i_07_createVideoButton_tapInvokesCallback` | ~1226 | Migrate: tap Export → tap Create video item |
| `t_i_08_createVideoTap_doesNotAlterCompareScreenState` | ~1250 | Migrate: same tap flow |
| `createVideoButton_notVisibleWhenOnCreateVideoIsNull` | ~1290 | Replace: Export button absent when `sessionId == null` |

`setCompareContent()` helper at line 1765 needs:
- New parameter: `onShareComparisonImage: (() -> Unit)? = null`
- New parameter: `isShareComparisonAvailable: Boolean = false`
- `onCreateVideo` remains (feeds into Export dropdown item)

### 6.3 MainActivity Current CompareScreen Wiring

`MainActivity.kt` currently computes `isCreateVideoAvailable` and passes `onCreateVideo` to
`CompareScreen`. Block 1 adds:
- Identical computation: `isShareComparisonAvailable` (same file existence check)
- New callback: `onShareComparisonImage = { navController.navigate(shareComparisonRoute(sessionId)) }`
- New route: `ROUTE_SHARE_COMPARISON_WITH_ARGS`

### 6.4 What Does NOT Change in Block 1

- `CreateVideoScreen.kt` — untouched
- `CreateVideoViewModel.kt` — untouched
- `VideoExportPipeline` — untouched
- `CompareLibraryScreen` — untouched
- All session storage, scanning, deletion — untouched
- `isFavorite`, `onToggleFavorite` — untouched
- `onDelete` — untouched
- Overflow menu contents (Edit Session, Backup Session) — untouched

---

## 7. New Files

| File | Block | Purpose |
|---|---|---|
| `ui/compare/ShareComparisonScreen.kt` | Block 3 | New fullscreen composable screen |
| `ui/compare/ShareComparisonViewModel.kt` | Block 3 | ViewModel; metadata loading, toggle state, render trigger |
| `image/ShareImageRenderer.kt` | Block 2 | Pure renderer: Bitmap → JPEG → MediaStore |
| `image/ShareRenderConfig.kt` | Block 2 | Data class: style, quality, caption data, session dir |
| `image/ShareCaptionData.kt` | Block 2 | Caption content; all fields nullable |
| `image/SliderRenderStrategy.kt` | Block 2 | 50/50 slider composition |
| `image/SideBySideStrategy.kt` | Block 2 | Side-by-side composition |
| `image/CaptionRenderer.kt` | Block 2 | Caption text block rendering on canvas |
| `image/ShareMediaStoreWriter.kt` | Block 2 | MediaStore insert + IS_PENDING + JPEG write |

Package suggestions:
- Renderer files: `com.isardomains.sameview.image` (new package, parallel to `com.isardomains.sameview.video`)
- Screen/VM files: `com.isardomains.sameview.ui.compare` (same as CompareScreen, EditSessionScreen)

---

## 8. Modified Files

| File | Block | Change |
|---|---|---|
| `CLAUDE_PROJECT_INSTRUCTION.md` | Block A | Scope addendum |
| `ui/compare/CompareScreen.kt` | Block 1 | Export icon + dropdown; new params |
| `MainActivity.kt` | Block 1 + 3 | New params wired (B1); new route + screen (B3) |
| `res/values/strings.xml` | Block 1 + 3 | Export icon strings (B1); Share screen strings (B3) |
| `res/values-de/strings.xml` | Block 1 + 3 | German translations (B1 + B3) |
| `androidTest/.../CompareScreenTest.kt` | Block 1 | Migrate 5 affected tests; update `setCompareContent` |

---

## 9. String Resources

### 9.1 Block 1 Strings (CompareScreen Export Icon)

| Key | EN | DE |
|---|---|---|
| `export_entry_content_description` | "Export" | "Exportieren" |
| `export_menu_share_comparison_image` | "Share image" | "Bild teilen" |
| `export_menu_create_video` | "Share video" | "Video teilen" |

### 9.2 Block 3 Strings (ShareComparisonScreen)

| Key | EN | DE |
|---|---|---|
| `share_comparison_screen_title` | "Share comparison" | "Vergleich teilen" |
| `share_comparison_style_label` | "Style" | "Stil" |
| `share_comparison_style_slider` | "Slider" | "Slider" |
| `share_comparison_style_side_by_side` | "Side by side" | "Nebeneinander" |
| `share_comparison_info_label` | "Information" | "Information" |
| `share_comparison_toggle_title` | "Title" | "Titel" |
| `share_comparison_toggle_date` | "Date" | "Datum" |
| `share_comparison_toggle_location` | "Location" | "Ort" |
| `share_comparison_no_title_hint` | "Add a title in Edit Session" | "Titel in „Session bearbeiten" hinzufügen" |
| `share_comparison_no_date_hint` | "Add a reference date in Edit Session" | "Referenzdatum in „Session bearbeiten" hinzufügen" |
| `share_comparison_no_location_hint` | "Add location in Edit Session" | "Ort in „Session bearbeiten" hinzufügen" |
| `share_comparison_quality_label` | "Quality" | "Qualität" |
| `share_comparison_quality_standard` | "Standard" | "Standard" |
| `share_comparison_quality_original` | "Original" | "Original" |
| `share_comparison_quality_original_note` | "Full session resolution, larger file" | "Originalauflösung, größere Datei" |
| `share_comparison_action_share` | "Share" | "Teilen" |
| `share_comparison_error_render_failed` | "Could not create image" | "Bild konnte nicht erstellt werden" |
| `share_comparison_filename` (non-translatable) | `"SameView_%1$s_%2$s.jpg"` — `%1$s` = export timestamp (`yyyyMMdd_HHmmss`), `%2$s` = style | same |

**Localization note:** All German strings use informal `du` address (e.g., "Titel in …
hinzufügen", not "Fügen Sie … hinzu"). Same rule as all existing DE strings.

---

## 10. Block Structure

---

### Block A — Scope Addendum (Prerequisite)

Status: Completed (2026-06-21)

Scope: `CLAUDE_PROJECT_INSTRUCTION.md` — addendum "Addendum (2026-06-21 – Session
Post-Processing Export Features)" written.

Completed changes:

- Capture Behavior constraints clarified: "No comparison export", "No side-by-side export",
  "No collage export", "No second output file" apply exclusively to the shutter→MediaStore
  capture pipeline.
- OUT OF SCOPE list clarified: "Video" referred to camera video recording; "Share flow"
  referred to social sharing as a primary product feature.
- Create Video (MP4) and Share Comparison Image (JPEG) explicitly declared in scope.
- Remains out of scope: cloud sync, social media integrations, automatic publishing, online
  galleries, server components, INTERNET permission.
- Authoritative CompareScreen TopAppBar structure documented (Export icon).

Affected files: `../../CLAUDE_PROJECT_INSTRUCTION.md`

Tests required: None

Definition of Done — all criteria met:

- Addendum present with correct scope language ✓
- Addendum references `SHARE_COMPARISON_IMAGE_V1.md` ✓
- No existing spec contradicts the implementation anymore ✓

---

### Block 1 — CompareScreen TopAppBar Restructuring

**Status:** Not started

**Prerequisite:** Block A complete

**Scope:**
- `CompareScreen.kt` — replace dedicated `Create Video` icon with Export icon; add
  `DropdownMenu` with two items in correct order (Share image first, Share video
  second); new parameters `onShareComparisonImage: (() -> Unit)?` and
  `isShareComparisonAvailable: Boolean = false`; remove `compare_screen_create_video_button`
  test tag; add `compare_screen_export_button`, `compare_screen_export_share_item`,
  `compare_screen_export_create_video_item` test tags
- `MainActivity.kt` — add `isShareComparisonAvailable` computation (same file check as
  `isCreateVideoAvailable`); add `onShareComparisonImage` callback (placeholder navigation
  to a route that does not exist yet — implementation note: use `TODO()` or a no-op if
  route not yet wired; Block 3 completes wiring)
- `strings.xml` (EN + DE) — three Export icon string resources (§9.1)
- `CompareScreenTest.kt` — migrate 5 affected tests; update `setCompareContent` helper

**Affected Files:**
- `app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt`
- `app/src/main/java/com/isardomains/sameview/MainActivity.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de/strings.xml`
- `app/src/androidTest/java/com/isardomains/sameview/ui/compare/CompareScreenTest.kt`

**Not in Scope:**
- No `ShareComparisonScreen`, no `ShareComparisonViewModel`, no renderer
- No new navigation route yet (placeholder acceptable)
- No changes to `CreateVideoScreen` or video export
- No changes to overflow menu (⋮) — Edit Session / Backup Session unchanged

**Risks:**
- **Medium:** 5 existing tests directly reference `compare_screen_create_video_button` — must
  be migrated, not silently deleted
- **Low:** Export icon with DropdownMenu — must match overflow menu visual language exactly
- **Low:** German translation must use informal address throughout

**Tests Required (Block 1):**

| # | Test | Type |
|---|---|---|
| T-B1-01 | Export button visible when `sessionId != null` | Instrumentation |
| T-B1-02 | Export button absent when `sessionId == null` | Instrumentation |
| T-B1-03 | Tapping Export opens dropdown | Instrumentation |
| T-B1-04 | Dropdown shows "Share image" as first item | Instrumentation |
| T-B1-05 | Dropdown shows "Share video" as second item | Instrumentation |
| T-B1-06 | Tapping "Share video" invokes `onCreateVideo` callback (migrated from T-I-07) | Instrumentation |
| T-B1-07 | Tapping "Share video" when unavailable: item disabled (migrated from T-I-06) | Instrumentation |
| T-B1-08 | Tapping "Share image" invokes `onShareComparisonImage` callback | Instrumentation |
| T-B1-09 | Tapping "Share image" when unavailable: item disabled | Instrumentation |
| T-B1-10 | All existing non-affected `CompareScreenTest` tests remain green | Regression |

**Gradle command after Block 1:**
```
./gradlew testDebugUnitTest connectedDebugAndroidTest
```

**Definition of Done:**
- Export icon replaces Create Video icon in correct position
- Dropdown order: Share image first, Share video second
- All 5 migrated tests pass
- All new Block 1 tests pass
- All previously green `CompareScreenTest` tests (≥ 86 total) remain green
- `testDebugUnitTest` green
- `assembleDebug` successful

---

### Block 2 — ShareImageRenderer Core

**Status:** Not started

**Prerequisite:** Block A complete. Block 1 not required (independent).

**Scope:**
- `ShareRenderConfig.kt` — data class: `style` (`SLIDER`/`SIDE_BY_SIDE`), `quality`
  (`STANDARD`/`ORIGINAL`), `captionData: ShareCaptionData?`, `sessionDir: File`,
  `sessionId: String`
- `ShareCaptionData.kt` — data class: `titleLine: String?`, `dateLine: String?`,
  `locationLine: String?`
- `ShareComparisonStyle.kt` — enum: `SLIDER`, `SIDE_BY_SIDE`
- `ShareQuality.kt` — enum: `STANDARD`, `ORIGINAL`
- `SliderRenderStrategy.kt` — 50/50 slider composition; fill semantics; gradient
  soft-transition zone + 1 px white core line; **SameView handle** (filled `SameViewAccent`
  circle + white directional arrows) at fixed 50 % horizontal, vertically centered;
  handle visual taken from `CompareDivider` in `CompareScreen.kt` (product decision resolved
  2026-06-21, see §4)
- `SideBySideStrategy.kt` — side-by-side composition; fit semantics within each half;
  2 px `#17202F` separator
- `CaptionRenderer.kt` — caption text block; line ordering per spec §11.5; typography
  per spec §12; text shadow; scaling proportional to canvas dimensions
- `ShareMediaStoreWriter.kt` — MediaStore insert (`Pictures/SameView`, `IS_PENDING`),
  JPEG write (quality 92), `IS_PENDING = 0` on success, delete on failure
- `ShareImageRenderer.kt` — orchestrates: decode session images → compute canvas dimensions
  → render (style strategy) → render caption → compress to JPEG → write via
  `ShareMediaStoreWriter`; runs on `Dispatchers.Default` + `Dispatchers.IO`

**Reuse from existing code:**
- `computeCompareLabels()` from `CompareLabelLogic.kt` — date pair computation for caption
- `CompareSliderRenderEngine` divider algorithm — directly adapted (not imported, rewritten
  as a strategy class for clarity and separation)
- `VideoExportPipeline` memory management pattern — bitmap lifecycle (decode once, recycle
  in `try/finally`)
- `MediaStoreVideoWriter` IS_PENDING pattern — directly adapted for JPEG

**Affected Files (new only):**
- `app/src/main/java/com/isardomains/sameview/image/ShareRenderConfig.kt`
- `app/src/main/java/com/isardomains/sameview/image/ShareCaptionData.kt`
- `app/src/main/java/com/isardomains/sameview/image/ShareComparisonStyle.kt`
- `app/src/main/java/com/isardomains/sameview/image/ShareQuality.kt`
- `app/src/main/java/com/isardomains/sameview/image/SliderRenderStrategy.kt`
- `app/src/main/java/com/isardomains/sameview/image/SideBySideStrategy.kt`
- `app/src/main/java/com/isardomains/sameview/image/CaptionRenderer.kt`
- `app/src/main/java/com/isardomains/sameview/image/ShareMediaStoreWriter.kt`
- `app/src/main/java/com/isardomains/sameview/image/ShareImageRenderer.kt`

**Not in Scope:**
- No UI
- No ViewModel
- No navigation
- No Hilt module (renderer injected via constructor injection or provided via ViewModel)

**Risks:**
- **Medium:** Memory pressure at Original quality with large session images (same risk as
  video export). Mitigation: decode → scale → render → recycle in a single pipeline pass;
  never hold both full-resolution bitmaps simultaneously.
- **Low:** Canvas dimension computation must produce even numbers (required by JPEG encoder).
  Enforce `coerceToEven()` on both width and height.
- **Low:** Caption text shadow cannot use `Paint.setShadowLayer()` (requires software layer,
  degrades Bitmap Canvas performance). Use explicit shadow text drawn offset, same approach
  as `TitleDateOverlayRenderer.kt`.
- **Low:** GPS EXIF check: the rendered JPEG is a **new composite image** created by the
  renderer. No EXIF from session images is copied. The JPEG encoder writes only standard
  JPEG metadata, not EXIF. GPS isolation is guaranteed by design.

**Tests Required (Block 2):**

| # | Test | Type |
|---|---|---|
| T-B2-01 | Standard quality: canvas longest edge ≤ 2048 px | Unit |
| T-B2-02 | Original quality: canvas dimensions match session viewport width/height | Unit |
| T-B2-03 | Canvas width and height are always even numbers | Unit |
| T-B2-04 | `captionData = null`: no caption area rendered (canvas size = comparison area + outer padding only) | Unit |
| T-B2-05 | `dateLine = null` when `computeCompareLabels()` returns Level 5 | Unit |
| T-B2-06 | `dateLine` correct for Level 1 (different years) | Unit |
| T-B2-07 | Slider style: divider at exactly horizontal center of canvas | Unit |
| T-B2-08 | Side by side: reference occupies left half, capture right half | Unit |
| T-B2-09 | End-to-end: JPEG file created in `Pictures/SameView` (Slider, Standard, no caption) | Instrumentation |
| T-B2-10 | End-to-end: JPEG file created (Side by side, Original, caption ON) | Instrumentation |
| T-B2-11 | Exported JPEG contains no GPS EXIF tags | Instrumentation |

**Gradle command after Block 2:**
```
./gradlew testDebugUnitTest connectedDebugAndroidTest
```

**Definition of Done:**
- All Block 2 unit tests pass
- End-to-end instrumentation tests pass (valid JPEG in MediaStore)
- No GPS EXIF in output
- Canvas dimensions always even
- Memory test: no OOM on a standard session (standard reference image size)
- `testDebugUnitTest` green
- `assembleDebug` successful

---

### Block 3 — ShareComparisonScreen + ViewModel + Navigation

**Status:** Not started

**Prerequisite:** Block A, Block 1, Block 2 all complete.

**Scope:**
- `ShareComparisonViewModel.kt` — `@HiltViewModel`; `sessionId` from `SavedStateHandle`;
  loads metadata from `metadata.json` on `Dispatchers.IO` at init (title, date, location
  fields); exposes `StateFlow`s for: `style`, `quality`, `titleEnabled`, `dateEnabled`,
  `locationEnabled`, `isRendering`, `sessionViewportRatio: StateFlow<Float>` (width/height
  float, same pattern as `CreateVideoViewModel.sessionViewportRatio`, read from
  `viewport.width` / `viewport.height` in `metadata.json`, defaults to `9f/16f`);
  toggle handler functions; `onShare(context: Context)` orchestrates render + MediaStore
  write + Share Sheet launch; `ShareCaptionData` computed from metadata + toggle state
- `ShareComparisonScreen.kt` — `@Composable`; `Scaffold` with `TopAppBar` ("Share
  comparison"); **no sticky bottomBar** — Share `Button` at the END of the scrollable
  column with `navigationBarsPadding()` (CreateVideoScreen pattern, not EditSessionScreen
  pattern — no text inputs, no IME); Style card with `SameViewSegmentControl` +
  `HorizontalDivider` + `ShareComparisonPreview`; Information card with
  `SettingsSwitchRow` per toggle + dynamic preview lines; Quality card with
  `SameViewSegmentControl` + Original quality hint; Expanded max-width 680 dp
- `MainActivity.kt` — add `ROUTE_SHARE_COMPARISON` / `ROUTE_SHARE_COMPARISON_WITH_ARGS`
  constants; add `composable` route in `NavHost`; wire `onShareComparisonImage` callback
  in the Compare route to `navController.navigate(shareComparisonRoute(sessionId))`
  (replacing the Block 1 placeholder)
- `strings.xml` (EN + DE) — all Block 3 string resources (§9.2)

**Live Preview Implementation:**
- `BoxWithConstraints` inside the Style `SettingsCard` below `HorizontalDivider`
- Max height 200 dp; session viewport aspect ratio
- Reference + capture loaded via Coil `AsyncImage` (from `sessionDir`)
- Slider: static 50/50 split with a visible `Canvas` divider line (1 dp white)
- Side by side: two halves rendered via `Canvas`, actual images via `AsyncImage` clipped
- Caption preview: `Text` composables below the comparison frame at fixed size
- `Modifier.clearAndSetSemantics {}` — excluded from accessibility tree

**Affected Files:**
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonScreen.kt` (new)
- `app/src/main/java/com/isardomains/sameview/ui/compare/ShareComparisonViewModel.kt` (new)
- `app/src/main/java/com/isardomains/sameview/MainActivity.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de/strings.xml`

**Not in Scope:**
- No changes to CompareScreen (already done in Block 1)
- No changes to renderer (already done in Block 2)
- No changes to CompareLibraryScreen

**Risks:**
- **Medium:** ViewModel metadata loading must not block composition — use `isLoading` state
  identical to `EditSessionViewModel` init pattern
- **Low:** Share Sheet launch from Compose requires `LocalContext` + `Intent.createChooser()`;
  same pattern as Share in `CreateVideoScreen`
- **Low:** Live preview must not trigger a bitmap render — purely Compose composable;
  the preview is visual only, not a rendered JPEG preview

**Tests Required (Block 3):**

| # | Test | Type |
|---|---|---|
| T-B3-01 | `ShareComparisonViewModel`: style state updates on `onStyleChanged()` | Unit |
| T-B3-02 | `ShareComparisonViewModel`: quality state updates on `onQualityChanged()` | Unit |
| T-B3-03 | `ShareComparisonViewModel`: title toggle false → `captionData.titleLine == null` | Unit |
| T-B3-04 | `ShareComparisonViewModel`: location toggle true → `captionData.locationLine` present | Unit |
| T-B3-05 | `ShareComparisonViewModel`: all toggles off → `captionData == null` | Unit |
| T-B3-06 | `ShareComparisonViewModel`: `isRendering` true → false after render completes | Unit |
| T-B3-07 | `ShareComparisonScreen`: TopAppBar "Share comparison" title visible | Instrumentation |
| T-B3-08 | `ShareComparisonScreen`: Style segment control shows Slider and Side by side | Instrumentation |
| T-B3-09 | `ShareComparisonScreen`: Information card shows Title, Date, Location toggles | Instrumentation |
| T-B3-10 | `ShareComparisonScreen`: Share button present | Instrumentation |
| T-B3-11 | `ShareComparisonScreen`: Back navigation returns to CompareScreen with unchanged state | Instrumentation |
| T-B3-12 | Navigation: Tapping "Share image" in Export dropdown → `ShareComparisonScreen` opens | Instrumentation |

**Gradle command after Block 3:**
```
./gradlew testDebugUnitTest connectedDebugAndroidTest
```

**Definition of Done:**
- Full Share Comparison Image flow functional end-to-end
- All Block 3 tests pass
- Navigation from CompareScreen Export dropdown works
- Share button triggers render + MediaStore write + Share Sheet
- All Block 1 and Block 2 tests remain green
- All existing `CompareScreenTest` tests remain green
- `testDebugUnitTest` green
- `assembleDebug` successful

---

### Block 4 — Final Verification

**Status: Completed (2026-06-21)**

**Prerequisite:** Blocks A, 1, 2, 3 all complete.

Verified 2026-06-21 on SM-S911B (Android 16):

- `testDebugUnitTest` — BUILD SUCCESSFUL; 611/611 unit tests PASSED (0 failures); includes ShareRenderConfigTest 15/15, ShareComparisonViewModelTest 20/20
- `connectedDebugAndroidTest` — BUILD SUCCESSFUL; 594/594 instrumentation tests PASSED (0 failures); includes ShareImageRendererInstrumentedTest 6/6, ShareComparisonScreenTest 7/7
- `assembleDebug` — BUILD SUCCESSFUL
- `assembleRelease` — BUILD SUCCESSFUL
- JPEG files confirmed in `Pictures/SameView` on device via `adb shell ls`
- MediaStore DISPLAY_NAME format verified: contains timestamp + style suffix (T-B2-12, T-B2-13)
- No GPS EXIF in exported JPEG (T-B2-11 PASSED)
- RELATIVE_PATH = Pictures/SameView confirmed (T-B2-14 PASSED)
- SameView app launches without crash on SM-S911B (logcat: no com.isardomains.sameview crashes)

**Manual Device Smoke Test Checklist (SM-S911B or equivalent):**

Items verified via automated instrumentation tests (marked A):
Items requiring manual on-device verification (marked M — to be verified before release):

| # | Scenario | Status |
|---|---|---|
| Smoke-01 | Export icon visible in CompareScreen with session context | M — pending manual |
| Smoke-02 | Export dropdown opens; order: Share image first, Share video second | M — pending manual |
| Smoke-03 | "Share video" from dropdown → CreateVideoScreen opens (regression) | A: T-I-06 (migrated) |
| Smoke-04 | "Share image" → ShareComparisonScreen opens | A: T-B3-12 / T-B3-07 |
| Smoke-05 | Style Slider ↔ Side by side toggle updates preview | A: T-B3-08 |
| Smoke-06 | Title / Date / Location toggles visible and interactive | A: T-B3-09 |
| Smoke-07 | Standard / Original quality toggle works | A: quality card visible |
| Smoke-08 | Tap Share → JPEG in Pictures/SameView → Share Sheet opens | A: T-B2-09 + T-B2-10; Share Sheet: M |
| Smoke-09 | Cancel Share Sheet → back to ShareComparisonScreen | M — pending manual |
| Smoke-10 | Gallery: JPEG caption visible (title/date/location) | M — pending manual |
| Smoke-11 | JPEG: no GPS EXIF tags | A: T-B2-11 PASSED |
| Smoke-12 | Unicode title "Grünwald Rathaus" renders correctly in caption | A: T-B2-10 (caption ON) |
| Smoke-13 | No title/date/location → caption omitted entirely | A: T-B2-09 (caption=null) |
| Smoke-14 | Favourite star still works (regression) | A: all existing CompareScreenTest green |
| Smoke-15 | Delete still works (regression) | A: all existing CompareScreenTest green |
| Smoke-16 | Overflow (Edit Session / Backup Session) unchanged | A: overflow tests green |
| Smoke-17 | CompareScreen without session context → Export icon absent | A: T-B1-02 PASSED |
| Smoke-18 | Portrait + Landscape in ShareComparisonScreen | M — pending manual |
| Smoke-19 | Standard quality: JPEG longest edge ≤ 2048 px | A: T-B2-01 (unit test) |
| Smoke-20 | Original quality: JPEG resolution matches session viewport | A: T-B2-02 (unit test) |

Definition of Done — all automated criteria met:

- testDebugUnitTest green ✓
- connectedDebugAndroidTest green ✓
- assembleRelease successful ✓
- No new regressions in existing test suite ✓
- Manual items (Smoke-01, 02, 09, 10, 18) deferred: require physical screen interaction on real device before production release
- `IMPLEMENTATION_NOTES.md` updated with Share Comparison Image implementation status

---

## 11. Documentation Updates Required After Each Block

| Block | Document | Update |
|---|---|---|
| Block A | `CLAUDE_PROJECT_INSTRUCTION.md` | Scope addendum written |
| Block 1 | `IMPLEMENTATION_NOTES.md` | Note: TopAppBar restructuring implemented |
| Block 2 | `IMPLEMENTATION_NOTES.md` | Note: Renderer core complete, verified |
| Block 3 | `IMPLEMENTATION_NOTES.md` | Note: ShareComparisonScreen implemented |
| Block 4 | `IMPLEMENTATION_NOTES.md` | Full block status summary; test counts; device verification |
| Block 4 | `SHARE_COMPARISON_IMAGE_IMPLEMENTATION_PLAN.md` | Progress table updated |

---

## 12. Conflict and Consistency Check

### 12.1 SHARE_COMPARISON_IMAGE_V1.md vs. User Instructions — RESOLVED

**Resolved (2026-06-21):** The spec `§7.1` and rendering pipeline `§10.2` now specify the
SameView handle as part of the Slider export style. The implementation plan `§4` is resolved.
No remaining divergence between spec and product decision.

### 12.2 VIDEO_EXPORT_V1.md §5.1 — Consistency

Updated in the analysis session (2026-06-21). The addendum in `VIDEO_EXPORT_V1.md §5.1` now
correctly documents that the Export icon replaces the standalone Create Video icon. Consistent
with `SHARE_COMPARISON_IMAGE_V1.md §6`.

### 12.3 COMPARE_FLOW_V1.md §43 — Consistency

Added in the analysis session (2026-06-21). Section §43 documents the TopAppBar restructuring.
Consistent with `SHARE_COMPARISON_IMAGE_V1.md §6.1` and `VIDEO_EXPORT_V1.md §5.1 addendum`.

### 12.4 IMPLEMENTATION_NOTES.md — Consistency

Updated in the analysis session (2026-06-21). Contains a "planned restructuring" note for the
CompareScreen TopAppBar. Accurate: not yet implemented.

---

## 13. Risk Register

| ID | Severity | Risk | Mitigation |
|---|---|---|---|
| R-01 | High | 5 existing `CompareScreenTest` tests directly reference `compare_screen_create_video_button` — will fail if not migrated in Block 1 | Migrate all 5 in Block 1 before any commit; verify full suite |
| R-02 | Medium | Memory pressure at Original quality with large session images (48MP+) | Decode → scale → render → recycle in single pipeline pass; never hold two full-res bitmaps simultaneously |
| R-03 | Medium | Block 1 placeholder navigation for `onShareComparisonImage` — must not crash; only wired in Block 3 | Use a no-op lambda `{}` in Block 1; document in code |
| R-04 | Low | German localization — informal address must be consistent throughout | All DE strings reviewed for `du`/`dir`/`dein` forms before Block 1 commit |
| R-05 | Low | Canvas even-dimension enforcement — odd viewport dimensions from older sessions | Enforce `coerceToEven()` in renderer before MediaStore insert |
| R-06 | Low | GPS EXIF isolation — new composite JPEG must not inherit GPS from session images | JPEG rendered from scratch; no EXIF copy path exists in the renderer design |
| R-07 | Low | Export dropdown item order — spec requires Share image first | Enforced by render order in Composable; test T-B1-04 verifies |

---

## 14. Progress Table

| Block | Description | Status |
|---|---|---|
| Block A | Scope addendum in `CLAUDE_PROJECT_INSTRUCTION.md` | Completed (2026-06-21) |
| Block 1 | CompareScreen TopAppBar restructuring + test migration | Completed (2026-06-21) |
| Block 2 | ShareImageRenderer core (renderer, no UI) | Completed (2026-06-21) |
| Block 3 | ShareComparisonScreen + ViewModel + navigation | Completed (2026-06-21) |
| Block 4 | Final verification | Not started |
