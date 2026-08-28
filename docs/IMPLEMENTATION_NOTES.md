# IMPLEMENTATION_NOTES.md

## Purpose
This file supplements `CLAUDE_PROJECT_INSTRUCTION.md`.

It documents the **current implementation state**, **recent decisions**, and **release-relevant notes**.

It must stay short, precise, and aligned with the actual codebase.

If there is any conflict, `CLAUDE_PROJECT_INSTRUCTION.md` remains the source of truth.

---

## Current Project Status

Project name:
- SameView Android app

Technical baseline:
- Kotlin
- Jetpack Compose
- Material 3
- MVVM + Hilt
- CameraX preview
- minSdk 29 / targetSdk 36 / compileSdk 36

Permissions:
- CAMERA
- ACCESS_FINE_LOCATION (GPS Recreation System; foreground-only; lazy request in Settings)
- ACCESS_MEDIA_LOCATION (companion permission; allows Photo Picker to return unredacted GPS EXIF)
- No INTERNET permission
- No READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE permission

Current release state:
- Closed-testing-ready based on the current code and documented verification
- No known critical blocker for Closed Testing / Play upload
- Release build hardening is active: R8, resource shrinking, backup/session exclusions, and debug-log gating
- A signed release APK has been installed and used successfully on a real device

### Android 16 / API 36 migration

- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 29` unchanged
- deprecated `announceForAccessibility()` usage for the two Camera warning bubbles was replaced by Compose `LiveRegionMode.Polite`
- real-device TalkBack verification passed, including repeat announcement
- real-device Android 16 verification passed for:
  - Camera predictive-back cancel/complete
  - marker-edit back
  - Compare normal/fullscreen back behavior
  - Compare Library selection-mode back
  - Edit Session dirty-state guard
  - Create Video render-state back guard
  - Settings permission-dialog back behavior
  - Walkthrough
  - system-bar/inset smoke checks
- automated verification:
  - 828/828 unit tests
  - 930/930 instrumentation tests on API 35
  - 930/930 instrumentation tests on API 36
  - standalone `pixel2Api36` managed device
  - existing API 29/33/35 managed devices unchanged
- build/release verification:
  - debug build passed
  - release APK build passed
  - release AAB build passed
  - R8/resource shrinking passed
  - local release artifacts remain unsigned
  - no dependency modernization was required

---

## Implemented Features

### Camera
- CameraX Preview working reliably
- Back camera only
- Lifecycle-safe preview handling
- Locally bound CameraX use cases are released when the Camera composition is disposed; async provider binding is guarded against late bind after dispose
- Capture and save via MediaStore
- Capture callbacks are protected with ViewModel-issued capture tokens so stale CameraX success/error callbacks after rotation or navigation are ignored before starting the save pipeline
- Stale capture success callbacks recycle the delivered bitmap and do not emit save, compare, navigation, or snackbar side effects
- Capture flash and haptic feedback on capture trigger
- Save failures show user-facing feedback and keep the app usable
- Session-save failures (MediaStore save succeeded, but `SessionStorage.saveSession` returns null) surface a dedicated `capture_saved_compare_failed` Snackbar; `compareInput` remains null and auto-open compare is not triggered; the generic `capture_saved` Snackbar is suppressed in this case
- Camera start/bind failures use a dedicated camera-start error path and invalidate `imageCaptureState`
- CameraScreen bottom workflow is stable: `Reference` / `Capture` / `Compare`
- Compare is always visible and never dynamically switches to Shots/History
- Disabled Compare taps show short workflow hints (~2000 ms)
- Top-right CameraScreen navigation contains persistent History and Overflow actions
- History opens the internal Compare Library even when it is empty
- Overflow contains Settings and About entries

### Settings
- SettingsScreen is implemented and reachable from the CameraScreen top-right Overflow menu
- Settings are persisted locally via DataStore Preferences in `sameview_settings`
- Implemented settings:
  - Grid Type: Off / Rule of Thirds / Quarters
  - Keep screen awake
  - `library_filter`: `"all"` / `"favorites"` (default `"all"`) — added 2026-06-20
  - `library_sort_order`: `"newest_first"` / `"oldest_first"` (default `"newest_first"`) — added 2026-06-20
  - Reset overlay after capture
  - Auto-open compare after capture
  - Recreation Guidance (GPS; boolean; default OFF; lazy permission request)
- Grid Type updates CameraScreen grid rendering through `CameraViewModel` state
- Keep screen awake is applied only while `CameraScreen` is visible and is cleared on dispose
- Reset overlay after capture removes the reference image entirely after a successful capture:
  - `referenceImageUri`, `referenceImageMetadata`, `referenceImageHasViewportMismatch`,
    `referenceImageDisplayMode`, `overlayOffsetX`, `overlayOffsetY`, `overlayScale`,
    `isOverlayNearlyInvisible`, `displayModeChangedByUser`
- `overlayAlpha` and `compareInput` are preserved; compare session remains accessible after reset
- Auto-open compare after capture emits a one-shot navigation event only after successful capture with valid session data
- Auto-open compare does not replay on rotation or recomposition

### Permissions
- Full permission flow implemented:
  - initial request
  - rationale
  - permanent denial -> app settings
- Camera permission is rechecked on `ON_RESUME` after returning from Android Settings
- Returning from Settings does not trigger an automatic permission re-request
- Permanently denied state updates without requiring an app restart

### Location Permission (GPS Recreation)
- ACCESS_FINE_LOCATION permission flow implemented
- Lazy request: permission is never requested on app start; request is triggered only when the user first enables "Recreation guidance" in Settings
- Pre-rationale dialog shown before the system permission dialog
- On permanent denial: toggle reverts to OFF; inline hint in Settings explains that location access is required
- ACCESS_MEDIA_LOCATION declared in manifest as companion permission; enables Photo Picker to return unredacted GPS EXIF for HEIC and JPEG reference images
- `setRequireOriginal()` is called only when the URI authority is `media` (Photo Picker); SAF/DocumentProvider URIs are opened directly to avoid SecurityException

### GPS Recreation Guidance

Full specification: `GPS_RECREATION_SYSTEM_V1.md`

Implemented (Blocks 1–8, plus Live direction arrow):

- **Reference GPS EXIF extraction** — passive read from the reference image EXIF via `ReferenceImageMetadataReader`; no permission required; missing GPS is normal and not an error condition
- **Recreation Guidance setting** — boolean DataStore setting (default OFF); parent toggle controlling all GPS behavior, GPS EXIF writing, and metadata.json location fields. A dependent sub-setting **Live direction arrow** controls bearing arrow display; see Live direction arrow entries below.
- **LocationProvider** — `LocationManager`-based; `GPS_PROVIDER` primary, `NETWORK_PROVIDER` fallback; 8–10 s update interval; foreground-only; no `BACKGROUND_LOCATION`
- **GPS activation conditions** — GPS updates are requested only when all four conditions are simultaneously true: Recreation Guidance ON, location permission granted, reference image has GPS EXIF, CameraScreen is active and in the foreground
- **GpsGuidanceState / GuidanceComputer** — sealed interface (`Hidden`, `Neutral`, `Informative`) with proximity color model (Green/Orange/Red/Neutral), Haversine distance, and geographic bearing computation; bearing suppressed below ~15–20 m; hysteresis prevents color flickering; small distance/bearing changes below threshold are filtered. The North-up bearing model has been removed. `bearingDegrees` in `GpsGuidanceState.Informative` carries device-relative bearing when Live direction arrow is ON and the sensor is active; null when Live direction arrow is OFF or distance is below the suppression threshold.
- **GpsGuidanceChip** — Composable on CameraScreen; distance label, proximity color accent, optional device-relative bearing arrow (Canvas, visible when `bearingDegrees != null`); `AnimatedVisibility` fade transitions; does not overlap Top-Left Hint Zone; `Hidden` state renders no element. When Live direction arrow is OFF, chip shows distance and proximity color only (distance-only default). No N-label; no static North-up arrow.
- **Live direction arrow setting** — boolean DataStore setting (`"live_direction_arrow"`, default OFF); sub-toggle under Recreation Guidance; enabled only when Recreation Guidance is ON; stored value preserved when Recreation Guidance is toggled off; does not influence GPS capture, EXIF writing, or `metadata.json` contents.
- **CompassProvider** — `TYPE_ROTATION_VECTOR` wrapper (internal `SensorEventListener`, callback-based `Float` azimuth delivery); handles rotation matrix extraction, display-rotation remapping via `SensorManager.remapCoordinateSystem()`, and azimuth normalization to 0–360°; exception-safe; foreground-only; sensor active only when all five conditions are simultaneously true: CameraScreen active, Recreation Guidance ON, Live direction arrow ON, reference image has GPS EXIF, location permission granted.
- **DirectionArrowCalculator** — pure `object`; `computeDisplayBearing(geoBearing, azimuth)` = `(geoBearing - azimuth + 360°) % 360°`; isolated from GuidanceComputer.
- **Smart SAF Fallback** — one-shot dialog offered only when Recreation Guidance is ON and the selected reference image has no readable GPS EXIF; `SAF/OpenDocument` is the fallback path; `onReferenceImageSelectedViaSaf()` never re-triggers the dialog; dialog is not shown on picker cancellation
- **metadata.json schema v3** — schema version updated to 3; `captureLocation` and `referenceLocation` are optional top-level fields; v2 sessions remain fully readable; `SessionScanner` accepts versions 2 and 3
- **GPS capture freeze** — at shutter trigger the current GPS fix is frozen into an immutable `GpsSnapshot`; `gpsSnapshot` is null when Recreation Guidance is OFF or no location fix is available
- **GPS EXIF in MediaStore/gallery image** — written via `GpsExifWriter.writeGpsToUri()` when `gpsSnapshot != null`; fail-soft, never invalidates the saved image
- **GPS EXIF in `capture.jpg`** — written via `GpsExifWriter.writeGpsToFile()` when `gpsSnapshot != null`; fail-soft
- **GPSDateStamp EXIF tag** — written when `fixTimestampMs != null`; UTC date in `YYYY:MM:DD` format (Block 8)
- **GPSTimeStamp EXIF tag** — written when `fixTimestampMs != null`; UTC time as `HH/1,MM/1,SS/1` rational format (Block 8)
- **GPSProcessingMethod EXIF tag** — written as `GPS` when provider is `"gps"`, as `NETWORK` when provider is `"network"`; omitted otherwise (Block 8)
- **`reference.jpg` never receives GPS** — by design; it is a rendered geometry product with no location semantics
- **`reference-original.jpg` GPS preservation** — when Recreation Guidance ON and the reference image has GPS EXIF, those coordinates are re-written from `ReferenceImageMetadata` via `GpsExifWriter`; fail-soft; no GPS is added when guidance is OFF or the source has no GPS
- **`captureLocation` in `metadata.json`** — top-level optional field; written when `gpsSnapshot != null`; fields: `latitude`, `longitude`, optional `altitude`, optional `accuracyMeters`, optional `provider`, optional `fixTimestampMs`
- **`referenceLocation` in `metadata.json`** — top-level optional field; written when Recreation Guidance ON and reference has GPS EXIF; fields: `latitude`, `longitude`, optional `altitude`, `source: "exif"`
- **Recreation Guidance OFF** — `gpsSnapshot` is always null; no GPS EXIF written to any file; no `captureLocation` or `referenceLocation` in `metadata.json`

GPS is architecturally separate from the Compare rendering pipeline. `GpsSnapshot` is not a rendering input. `ReferenceRenderer.render()` receives no GPS data. See `GPS_RECREATION_SYSTEM_V1.md` sections 2 and 6 for the full constraint set.

---

### Reference Image
- Android Photo Picker integration
- Single image selection
- Picker cancellation does NOT remove existing overlay
- Invalid or unreadable reference images show a Snackbar

### Overlay Rendering
- Overlay displayed above preview using AsyncImage
- Opacity adjustable via slider (0.1-0.9)
- Overlay drag implemented
- Overlay pinch scaling implemented
- Reset restores the default alignment state
- Overlay delete is supported with confirmation and undo flow
- Overlay state is stored in ViewModel and survives normal rotation within the active session
- Live overlay coverage warning shown in top-left hint slot when less than 20 % of the overlay is visible within the viewport; soft warning only, capture always allowed

### Grid
- Grid overlay implemented as a preview-only Canvas layer
- Grid is not written into captured images
- Supported grid types:
  - Off
  - Rule of Thirds
  - Quarters

### Compare
- `CompareScreen` is implemented as a separate fullscreen slider-based comparison screen
- Tap-based fullscreen viewing mode is implemented
- Back exits fullscreen before leaving the screen
- Compare shows a metadata header above the slider: title, location (`📍 displayName · city, country`), or `Created <date>` fallback; hidden in fullscreen (spec: §42)
- Compare image load failures show a fallback UI and allow back navigation
- **Favourite star (2026-06-20):** TopAppBar action (first action before Create Video); visible when `sessionId != null`; outline star = not favourited; filled star with `SameViewStarFavorited` amber tint = favourited; toggles via `CameraViewModel.toggleFavorite()`; state derived from `CameraViewModel.savedSessions` — no local state in CompareScreen
- **CompareScreen TopAppBar — planned restructuring (spec 2026-06-21, not yet implemented):** The dedicated Create Video icon will be replaced by an Export icon (see `SHARE_COMPARISON_IMAGE_V1.md §6` and `COMPARE_FLOW_V1.md §43`). The Export icon opens a dropdown with "Share image" → `ShareComparisonScreen` and "Share video" → `CreateVideoScreen`. This restructuring is part of the Share Comparison Image implementation scope, not yet implemented.

Active compare session lifecycle — fully implemented:

- `compareInput` persists across lifecycle transitions, camera rebinds, capture errors, and CompareScreen navigation
- `compareInput` is cleared by: reference removed, reference replaced, new successful capture, or session deleted
- `deleteSessions` atomically clears `compareInput` when the active session's ID is in the deleted set
- Optional auto-open compare after capture is implemented as a one-shot `UiEvent.NavigateToCompare` and does not change `compareInput` lifecycle rules
- Full spec: COMPARE_FLOW_V1.md sections 14, 38 and 39

### Compare Library
- `CompareLibraryScreen` is implemented as a focused internal session overview
- Sessions are shown in a grid
- Long-press multi-select delete is implemented with confirmation
- Multi-select mode includes Select All / Deselect All toggle and a Backup icon
- Backup exports all selected sessions as a single ZIP file via SAF `ACTION_CREATE_DOCUMENT`
- A `LinearProgressIndicator` is shown below the TopAppBar while backup is running; backup and delete icons disabled during this time
- After successful backup: multi-select mode exits automatically, selection is cleared
- After failed backup: multi-select mode remains active, selection is preserved (user can retry)
- Titles and user-authored location are displayed on tiles when present (see tile display logic below)
- Tile text area always reserves height for two lines — grid height is stable regardless of content
- Tile display priority: (A) title + location → title / location, no date; (B) title only → title / date; (C) location only → location / date; (D) neither → date only
- Location is formatted using the §32 priority order: displayName · city, country → displayName · city → displayName · country → displayName → city, country → city → country
- **Favorites (2026-06-20):** Favourite star visible on each tile in normal mode (TopStart, 48 dp touch target, icon anchored to corner); hidden in multi-select mode; toggled via `CameraViewModel.toggleFavorite()` with Write-First and targeted in-memory update; `ScannedSession.isFavorite: Boolean = false` added; `SessionStorage.updateFavorite()` added
- **Filter / Sort (2026-06-20):** Overflow menu (⋮) in normal-mode TopAppBar; Filter: All comparisons / Favorites only; Sort: Newest first / Oldest first; persisted via `SettingsRepository`/DataStore keys `library_filter` and `library_sort_order`; filter then sort pipeline derived in-memory via `remember`; Favorites-specific empty state when filter = Favorites and no favorites present; Select All operates on the **filtered list** only (see PD-08 note above)
- This is not a general gallery or MediaStore browser

### Share Comparison Image

Full specification: `SHARE_COMPARISON_IMAGE_V1.md`
Implementation plan: `implementation_plans/historic/SHARE_COMPARISON_IMAGE_IMPLEMENTATION_PLAN.md`

**Status: Specification and implementation plan complete (2026-06-21). Not yet implemented.**

Block A completed (2026-06-21): scope addendum written in `CLAUDE_PROJECT_INSTRUCTION.md` — Share Comparison Image and Create Video declared in scope as session post-processing export features; capture pipeline restrictions clarified; out-of-scope boundaries (cloud, social media integrations, server) preserved.

Slider handle product decision resolved (2026-06-21): Slider export style includes the SameView handle (see `implementation_plans/historic/SHARE_COMPARISON_IMAGE_IMPLEMENTATION_PLAN.md §4`).

Block 1 completed (2026-06-21): CompareScreen TopAppBar restructured — dedicated Create Video icon replaced by Export icon (`Icons.Outlined.Share`); Export dropdown with "Share image" (item 1) and "Share video" (item 2); test tags `compare_screen_export_button`, `compare_screen_export_share_item`, `compare_screen_export_create_video_item`; 5 existing Create Video tests migrated to Export flow; `onShareComparisonImage` no-op placeholder wired in MainActivity; `testDebugUnitTest` PASSED; `assembleDebug` BUILD SUCCESSFUL.

Block 2 completed (2026-06-21): ShareImageRenderer core implemented — package `com.isardomains.sameview.image`; new classes: `ShareComparisonStyle`, `ShareQuality`, `ShareCaptionData`, `ShareRenderConfig` (+ `computeCanvasDimensions`, `buildDisplayName`), `ShareMediaStoreWriter`, `CaptionRenderer`, `SliderRenderStrategy` (50/50 + gradient divider + SameViewAccent handle + white arrows), `SideBySideRenderStrategy`, `ShareImageRenderer` (orchestrator, IS_PENDING lifecycle, no EXIF written); 15/15 unit tests PASSED (`ShareRenderConfigTest`); `testDebugUnitTest` BUILD SUCCESSFUL; `assembleDebug` BUILD SUCCESSFUL; instrumentation tests (`ShareImageRendererInstrumentedTest`) require real device — not yet executed.

Block 3 completed (2026-06-21): `ShareComparisonViewModel`, `ShareComparisonPreview`, `ShareComparisonScreen`, `MainActivity` route + navigation wired.

Block 4 completed (2026-06-21) — **Share Comparison Image fully implemented and verified**:

- `testDebugUnitTest` — BUILD SUCCESSFUL; 611/611 PASSED; ShareRenderConfigTest 15/15, ShareComparisonViewModelTest 20/20, all prior tests remain green
- `connectedDebugAndroidTest` — BUILD SUCCESSFUL; 594/594 PASSED on SM-S911B (Android 16); ShareImageRendererInstrumentedTest 6/6, ShareComparisonScreenTest 7/7, all prior tests remain green
- `assembleDebug` — BUILD SUCCESSFUL
- `assembleRelease` — BUILD SUCCESSFUL
- JPEG export confirmed in `Pictures/SameView` on device; MediaStore DISPLAY_NAME format verified; no GPS EXIF; app launches without crash
- Manual smoke tests Smoke-01, 02, 09, 10, 18 deferred — require physical screen interaction before production release

---

### Video Export

Full specification: `VIDEO_EXPORT_V1.md`
Implementation plan: `implementation_plans/historic/VIDEO_EXPORT_IMPLEMENTATION_PLAN.md`

MP4 export infrastructure implemented and verified (Blocks 1–2 Completed). Create Video flow implemented and verified (Blocks 3+4 Completed). High Quality export implemented and verified (Block 5 Completed). Branding endcard implemented and verified (Block 6 Completed). Block 7 Final Verification completed — manual device smoke test passed on SM-S911B (2026-06-10). Block 9 Flash Mode completed (2026-06-17). All Video Export blocks complete:

- **Renderer Core (Block 1)** — `VideoRenderConfig`, `VideoMode`, `VideoExportFormat`, `VideoQuality` enums; `VideoFrameRenderer` interface; `CompareSliderRenderEngine` (cubic smoothstep, gradient soft-transition divider + 1 px core line, fill semantics); `BeforeAfterRenderEngine` (linear crossfade, fit semantics); canvas setup and bitmap lifecycle; `computeCanvasDimensions` with even-dimension enforcement
- **Encoding Pipeline (Block 2)** — `VideoEncoder` (MediaCodec H.264/AVC, ByteBuffer input, ARGB→YUV420 conversion, NV12/I420 auto-detect via `MediaCodecList`); `MediaStoreVideoWriter` (IS_PENDING lifecycle, `Movies/SameView`, cleanup on failure); `VideoExportPipeline` (orchestrates decode → render → encode → commit; coroutine-cancellation-safe cleanup via `NonCancellable`)
- Output: MP4, H.264, 30 FPS, 7 Mbps, `Movies/SameView`, no audio track
- **Create Video Flow (Block 3+4)** — feature is now fully reachable from the app via CompareScreen
- CompareScreen TopAppBar contains Create Video action with Slideshow icon; TopAppBar structure: Back | Create Video | Delete Session | Overflow
- `CreateVideoScreen` contains three states: Configuring, Rendering, Preview
- Configuring-State: mode selection (Compare Slider / Before & After), format (Original / Portrait / Landscape), duration, quality, branding toggle, Create Video CTA; UI aligned to Settings language using SettingsCard / SettingsSwitchRow / SameViewSegmentControl from `SettingsComponents.kt`
- Rendering-State: CircularProgressIndicator, LinearProgressIndicator, frame progress text; Back opens Cancel Export Dialog
- Preview-State: ExoPlayer/Media3 auto-play, loop, muted; Share as primary action; Done as secondary action; Delete Video as destructive text action with Confirmation Dialog
- Share uses Android Share Sheet via `Intent.ACTION_SEND` with MediaStore-URI; opens only on explicit tap
- Delete Video deletes the MP4 from MediaStore after explicit confirmation; returns to Configuring on success
- Done / Back from Preview closes the screen; video remains saved; returns to CompareScreen
- `brandingEnabled` persists via DataStore `sameview_settings`; Default = true; `BrandingEndcardRenderer` renders a 1.5 s endcard (45 frames: 6 fade-in + 33 static + 6 fade-out) when enabled
- **High Quality + Device Limit Fallback (Block 5)** — High Quality export path is fully wired
- `VideoEncoder` supports H.265/HEVC via new `codecMimeType` parameter; `findHevcEncoder()` and `isResolutionSupported()` added as static helpers
- `VideoExportPipeline.resolveEncoderParams()` selects codec and canvas dimensions before MediaStore insert: HEVC preferred for HIGH_QUALITY (silent AVC fallback if no ByteBuffer-capable HEVC encoder found); resolution checked via `VideoCapabilities.isSizeSupported()`; falls back to Standard 1080p if device cannot handle 4K
- Bitrate: STANDARD_1080P = 7 Mbps (unchanged); HIGH_QUALITY = 20 Mbps
- User-visible Snackbar `create_video_quality_fallback_notice` emitted only when resolution is capped (not on HEVC→AVC codec switch)
- T-U-20 grün; T-I-01/T-I-02 PASSED (`VideoExportPipelineStandardTest`); T-I-03 PASSED (`VideoExportPipelineTest`) on SM-S911B (Android 16)

---

### Session Backup Export

Full specification: `SESSION_BACKUP_EXPORT_V1.md`

- User-initiated, local, full-fidelity backup of one or more compare sessions as a ZIP file
- Written to user-chosen storage via Android Storage Access Framework (`ACTION_CREATE_DOCUMENT`)
- No additional permissions required; no FileProvider required
- No network calls; any cloud destination is handled by the OS SAF provider outside the app process

Entry points:
- `CompareScreen` overflow menu → "Backup Session" (single session; requires `sessionId != null`)
- `CompareLibraryScreen` multi-select action bar → Backup icon (one or more sessions)

ZIP format:
- One subdirectory per session, named by session ID
- File set is metadata-driven: all filenames declared in `files.*` block plus `metadata.json` — v4 sessions export 4 files; v5 sessions export 6 files including `capture-original.jpg` and `reference-source-original.[ext]`
- Files written byte-for-byte without modification (no re-encoding, no EXIF stripping, no GPS stripping)

Operation locks:
- Backup blocked while deletion in progress; deletion blocked while backup in progress
- SAF picker returning null URI → no-op, no snackbar, no state change
- All-or-nothing: any failure aborts the entire backup and attempts best-effort cleanup of the partial file

Feedback:
- Success: "Session backed up" (1 session) or "N sessions backed up" (N ≥ 2)
- Failure: "Backup failed" snackbar; never silent

---

### Session Storage
- Successful captures with an active reference can create an internal session under `filesDir/sessions/<sessionId>/`
- **Session Originals (2026-06-22):** schema is now **v5**. Each new session contains six files: `capture.jpg`, `capture-original.jpg`, `reference.jpg`, `reference-original.jpg`, `reference-source-original.[ext]`, `metadata.json`. Full specification: `SESSION_ORIGINALS_V1.md`.
  - `capture-original.jpg` is a byte-for-byte copy of the committed MediaStore file (quality 95, all EXIF intact)
  - `reference-source-original.[ext]` is a byte-for-byte copy of the reference source URI; extension derived from MIME type (`image/jpeg` → `.jpg`, `image/heic`/`image/heif` → `.heic`, `null`/unknown → `.bin`)
  - Compare uses only `capture.jpg` + `reference.jpg` — originals are provenance files for future HQ export
  - `metadata.json` v5 writes `reference.sourceUri` (correcting the v2–v4 misnomer `reference.sourceDisplayName`); writes `reference.sourceMimeType` when non-null
  - `files.*` block contains all six filenames including the format-specific `referenceSourceOriginal` name
- `SessionScanner` accepts versions {2, 3, 4, 5}; v5 additionally validates that the files referenced in `files.captureOriginal` and `files.referenceSourceOriginal` exist on disk; v2/v3/v4 sessions without original files remain fully valid
- `SessionBackupExporter` is metadata-driven: reads the `files.*` block from each session's `metadata.json` to determine which files to export; handles v4 (4 files) and v5 (6 files) uniformly; variable-extension originals are handled automatically
- `EditSessionViewModel` reads `reference.sourceUri` first (v5), falls back to `reference.sourceDisplayName` (v2–v4)
- **Session Originals Privacy (2026-07-XX):** optional setting `strip_originals_metadata` (DataStore key, default `false`). Full specification: `SESSION_ORIGINALS_PRIVACY_V1.md`.
  - Privacy OFF (default): `capture-original.jpg` and `reference-source-original.*` are byte-for-byte copies; all EXIF/GPS/metadata intact; maximum restore fidelity
  - Privacy ON: session originals are metadata-stripped before writing to the session directory; MediaStore gallery photo is never modified; Compare (`capture.jpg` + `reference.jpg`) and `OriginalReferenceBadge` (`reference-original.jpg`) are unaffected
  - Capture path (Privacy ON): byte-level JPEG segment strip (APP1/XMP/IPTC removed, ICC kept); fallback to decode→orient→JPEG-95 re-encode for non-trivial EXIF orientation or malformed JPEG; GPS, EXIF DateTimeOriginal, Make/Model, MakerNotes removed; full resolution preserved
  - Reference source path (Privacy ON): format-specific decode→re-encode cycle — JPEG/HEIC/HEIF/WebP → JPEG 95; PNG → PNG (lossless); GIF/BMP → JPEG 95; AVIF API 31+ → JPEG 95, API 29–30 → `not_possible`; unknown MIME → `not_possible` (stored as-is, session still saved)
  - `metadata.json` `originals` block (only when Privacy ON): `privacyMode: true`, `capturePreservation`, `referenceSourcePreservation`, optional `referenceSourceStoredMimeType` (when output format differs from source)
  - `CameraViewModel` reads `stripOriginalsMetadata` from `SettingsRepository`, freezes the value at capture time (before IO), passes `stripMetadata` to `SessionStorage.saveSession()`
  - MIME inference fallback via `inferMimeTypeFromUri()` handles `file://` URIs where `ContentResolver.getType()` returns null (uses file extension)
- `metadata.json` schema is **v4** through 2026-06-21 (bumped from v3 in Block A, 2026-06-09): includes `capture.timestampMs` as the canonical capture timestamp inside the `capture` block; `session.createdAtMs` is preserved for backward compatibility and carries the same value
- `metadata.json` contains an `additional` block at session creation with fixed defaults: `isFavorite: false`, `visibility: "private"`, `source: "sameview"` (Block B, 2026-06-09); UI and update endpoint implemented 2026-06-20 via `SessionStorage.updateFavorite()` and `CameraViewModel.toggleFavorite()`
- `content` block is absent at session creation when no title is present (Block C, 2026-06-09); fixes §12.1 violation where `description: null` and `tags: []` were pre-populated; `updateTitle()` handles absent `content` block correctly via `optJSONObject("content") ?: JSONObject()`
- `reference.date` EXIF auto-population implemented (Block D, 2026-06-09): at session creation, `ReferenceImageMetadataReader` reads EXIF `DateTimeOriginal` and parses it to `"YYYY-MM-DD"`; plausibility filter rejects years < 1826 or > current year; non-lenient `Calendar` rejects invalid month/day values (e.g. month=99, Feb 31); when present, `reference.date`, `reference.dateSource = "exif"`, and `reference.userEdited = false` are written into the `reference` block; when absent or implausible, the three fields are omitted entirely; `ReferenceImageMetadata.exifDateTimeOriginal` is the carrier (trailing default `null`, no call-site changes required)
- `SessionStorage.updateReferenceDate()` implemented (Block E, 2026-06-09): storage-side write function for manual `reference.date` changes; signature `(sessionsRoot, sessionId, date: String?): Boolean`; `date != null` (valid) → writes `reference.date`, `reference.dateSource = "manual"`, `reference.userEdited = true`; `date == null` (remove) → removes `reference.date` and `reference.dateSource`, keeps `reference.userEdited = true`; invalid non-null date returns `false` without modifying anything; `isValidReferenceDate()` validates exact ISO 8601 precision levels ("YYYY", "YYYY-MM", "YYYY-MM-DD") with plausibility filter and non-lenient Calendar check; path traversal protection identical to `updateTitle()`; no UI, no scanner changes
- `SessionStorage.updateLocation()` implemented (Block F, 2026-06-09): storage-side write function for manual user location metadata; signature `(sessionsRoot, sessionId, displayName: String?, city: String?, country: String?): Boolean`; each string normalized via `trim().ifEmpty{null}`; when at least one field non-null → sets `location.displayName`/`city`/`country` (individually) and `location.userEdited = true`; when all fields null after normalization → removes entire `location` block; blank strings never stored; `captureLocation` and `referenceLocation` never modified; path traversal protection identical to `updateTitle()`; no format validation (plain text fields); no UI, no scanner changes
- `metadata.json` includes schema version, timestamp, file names, MediaStore URI, picker URI, optional title, optional GPS location fields (`captureLocation`, `referenceLocation`), and optional reference date fields (`reference.date`, `reference.dateSource`, `reference.userEdited`)
- Missing, corrupt, or incomplete session metadata is ignored during scanning
- Session writes are best-effort and do not invalidate the main MediaStore save; if `SessionStorage.saveSession` returns null after a successful MediaStore save, the user receives `capture_saved_compare_failed` instead of the generic `capture_saved`
- Session deletion only removes internal session folders
- Session operations accept only direct child session IDs; nested or traversal-like IDs are rejected with controlled failure
- User-authored text metadata (title, description, location fields) is sanitized on save via `MetadataTextSanitizer`: single-line fields (title, display name, city, country) do not persist pasted line breaks (replaced with space); description keeps line breaks; zero-width and Bidi override characters are removed; international characters, emojis, and normal punctuation are preserved; no length limits are introduced

### Shot Titles
- Optional session title stored in `metadata.json`
- Missing title field is valid
- Titles are trimmed; blank titles are stored as absent
- CompareScreen allows editing and removing a title
- CompareLibrary displays titles above timestamps when present
- Title-save failures were previously handled via a dedicated Snackbar (`compare_screen_title_save_failed`) in `CameraViewModel.updateSessionTitle()`; this path was removed as dead code (2026-07-09) — title editing now goes through the Edit Session flow (`SessionStorage.updateContent()`, `edit_session_save_failed`)

### Layout
- Fullscreen `Box` root
- Camera preview uses `fillMaxSize()`
- UI is layered above the preview and does not resize it
- Portrait controls are stacked above the bottom bar
- Landscape controls are aligned to the preview center, independent of the navigation bar
- Landscape bottom row is `Reference` / `Capture` / `Compare`
- Landscape opacity slider is centered above the button row
- Top-right History and Overflow actions stay visible in portrait and landscape
- Portrait uses the classic top-right horizontal History/Overflow layout
- Landscape uses adaptive side-rail History/Overflow placement based on navigation bar insets
- Landscape History/Overflow actions are vertically aligned and positioned in the upper side-rail area
- Landscape Overflow popup opens toward the free side area instead of overlapping the rail
- Overlay action menu stays visible and inside root bounds

---

## Compare Approach

The authoritative rendering architecture is `COMPARE_SESSION_RENDERING_V1.md`.

Session files per capture:

- `capture.jpg` — unmodified camera output
- `reference.jpg` — rendered deterministically via `ReferenceRenderer.render()` with frozen overlay geometry (overlayScale, overlayOffsetX, overlayOffsetY, referenceImageDisplayMode, viewport)
- `reference-original.jpg` — EXIF-oriented original reference, not used in normal compare rendering

`CompareScreen` renders only `capture.jpg` + `reference.jpg` — no geometry reconstruction at compare time.

The saved MediaStore capture is never composited with the overlay.

---

## Storage / Privacy / Release Hardening

- Manifest declares CAMERA only
- The app has no INTERNET permission
- No analytics, telemetry, tracking, upload, or network feature is implemented
- Android Photo Picker is used for reference image selection
- Captures are saved through MediaStore under `Pictures/SameView`
- Internal compare sessions are stored under `filesDir/sessions/`
- `backup_rules.xml` excludes `sessions/` from Auto Backup
- `data_extraction_rules.xml` excludes `sessions/` from cloud backup and device transfer
- `SessionDeleter` validates target paths against path traversal
- `SessionStorage.updateTitle` validates target paths against path traversal
- Invalid session IDs are rejected before IO/path traversal behavior and return controlled failure (`false`)
- Debug/session logs are guarded by `BuildConfig.DEBUG`
- R8 minify and resource shrinking are enabled for release
- Build and release artifacts are ignored by `.gitignore`

---

## Known Release-Relevant Residual Risks

No critical blockers are currently documented.

Accepted residual risks:
- Very large reference images can still increase memory pressure during session creation; failures are caught and the main MediaStore save remains the source of truth
- Overlay and library thumbnail image-load failures may produce limited visual feedback, but do not block navigation or core capture
- Delete failures are handled by rescanning state, but currently do not show a dedicated user-facing error
- DataStore read/write failures currently fall back to default settings behavior and do not show dedicated user-facing feedback

These are robustness/polish items, not known Closed Testing blockers.

---

### Session Metadata Editor

Full specification: `SESSION_METADATA_EDITOR_V1.md`
Implementation plan: `implementation_plans/historic/SESSION_METADATA_EDITOR_IMPLEMENTATION_PLAN.md`

Block A completed (2026-06-09):

- **Edit Session entry point** — `CompareScreen` overflow menu "Edit Title" + "Remove Title" items replaced with single "Edit Session" item; `onSaveTitle` parameter removed; `onEditSession: (() -> Unit)?` parameter added; title `AlertDialog` and all associated local state removed; `sessionTitle` prop is now read-only display (no local mutation)
- **`EditSessionScreen` navigation shell** — new fullscreen opaque composable in `com.isardomains.sameview.ui.compare`; `TopAppBar` with back icon, "Edit session" title, disabled Save button; scrollable empty body; no ViewModel, no state, no save logic
- **Navigation route** — `ROUTE_EDIT_SESSION_WITH_ARGS` added to `MainActivity`; `editSessionRoute()` helper function encodes `sessionId` via `Uri.encode()`; back navigation via `navController.popBackStack()`
- **Test suite updated** — 7 title-dialog tests removed; 5 tests renamed/updated; 1 new test `moreMenu_editSessionItem_invokesCallback` added; `setCompareContent()` helper updated to `onEditSession`

Latest verified test state (Session Metadata Editor Block A — Completed 2026-06-09):

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `CompareScreenTest` — 79/79 PASSED on SM-S911B (Android 16), BUILD SUCCESSFUL in 2m 47s

No open Session Metadata Editor Block A tasks remain.

Block B completed (2026-06-09):

- **`EditSessionViewModel`** — new `@HiltViewModel` in `com.isardomains.sameview.ui.compare`; `sessionId` extracted from `SavedStateHandle["sessionId"]`; on `init`, reads `metadata.json` from `filesDir/sessions/<sessionId>/` on the IO dispatcher and populates five `StateFlow`s: `titleField`, `referenceDateField`, `locationDisplayNameField`, `locationCityField`, `locationCountryField`; `isLoading: StateFlow<Boolean>` starts `true`, guaranteed `false` in `finally` block; best-effort load — any exception leaves all fields at empty string without crashing
- **Injectable lambda** — `internal var metadataReader: (sessionsRoot: File, sessionId: String) -> InitialSessionFields` replaceable in tests; default reads and parses the actual file; `internal var ioDispatcher` replaceable for test dispatcher injection
- **`EditSessionScreen`** — `viewModel: EditSessionViewModel = hiltViewModel()` default parameter added; `MainActivity.kt` unchanged (no extra wiring needed; identical pattern to `CreateVideoScreen`)
- **`EditSessionViewModelTest`** — 6 unit tests using `StandardTestDispatcher` so the `init` coroutine is queued but not started during construction; `ioDispatcher` and `metadataReader` overridden after construction before `advanceUntilIdle()`; tests cover: title load, referenceDate load, all three location fields load, all-fields-empty on IOException, all-fields-empty when blocks absent, `isLoading` true→false transition

Latest verified test state (Session Metadata Editor Block B — Completed 2026-06-09):

- `testDebugUnitTest` — BUILD SUCCESSFUL, 394/394 unit tests passed, 0 failures
- `EditSessionViewModelTest` — 6/6 PASSED

No open Session Metadata Editor Block B tasks remain.

Block C completed (2026-06-09):

- **Title field** — `OutlinedTextField` added to `EditSessionScreen`; `titleField` collected via `collectAsStateWithLifecycle()`; `onValueChange` wired to new `onTitleChanged(value: String)` function in `EditSessionViewModel`; `singleLine = true`, `ImeAction.Done` + `clearFocus()`; pre-populated from Block B initial load; Save button remains disabled; string resource `edit_session_field_title` added
- **`EditSessionViewModelTest`** — `onTitleChanged_updatesState` added; 7/7 tests pass

Latest verified test state (Session Metadata Editor Block C — Completed 2026-06-09):

- `testDebugUnitTest` — BUILD SUCCESSFUL, 395/395 unit tests passed, 0 failures
- `EditSessionViewModelTest` — 7/7 PASSED
- `assembleDebug` — BUILD SUCCESSFUL

No open Session Metadata Editor Block C tasks remain.

Block D completed (2026-06-09):

- **Shared validation** — `SessionStorage.isValidReferenceDate()` changed from `private` to `internal` (1-word change); single source of truth for date format validation; no duplication
- **Reference date field** — `OutlinedTextField` added to `EditSessionScreen`; `referenceDate` and `referenceError` collected via `collectAsStateWithLifecycle()`; placeholder hint, `isError`, conditional `supportingText` for error display; `ImeAction.Done` + `clearFocus()`
- **Title field IME** — changed from `ImeAction.Done` to `ImeAction.Next` with `moveFocus(FocusDirection.Down)` to chain keyboard navigation to Reference Date
- **`EditSessionViewModel`** — `internal val _referenceDateError: MutableStateFlow<String?>` (null initially; accessible in tests); `referenceDateError: StateFlow<String?>`; `onReferenceDateChanged()` (updates field, clears error); `internal fun isValidReferenceDateInput()` (empty/blank → true; non-empty → delegates to `SessionStorage.isValidReferenceDate(trimmed)`); error state is present but stays null in Block D (no save trigger yet)
- **`EditSessionViewModelTest`** — 12 new tests: `onReferenceDateChanged_clearsPreviousError` + 11 validation cases; 19/19 pass

Latest verified test state (Session Metadata Editor Block D — Completed 2026-06-09):

- `testDebugUnitTest` — BUILD SUCCESSFUL, 407/407 unit tests passed, 0 failures
- `EditSessionViewModelTest` — 19/19 PASSED
- `assembleDebug` — BUILD SUCCESSFUL

No open Session Metadata Editor Block D tasks remain.

Block E completed (2026-06-09):

- **Location fields** — three `OutlinedTextField`s added to `EditSessionScreen`: Location (display name), City, Country; each wired to its own `StateFlow` via `collectAsStateWithLifecycle()` and to `onLocationDisplayNameChanged()`, `onLocationCityChanged()`, `onLocationCountryChanged()` in `EditSessionViewModel`; all three handler functions set their respective `MutableStateFlow` directly without normalization or coroutine
- **Reference Date IME** — `ImeAction` changed from `Done` to `Next` + `moveFocus(FocusDirection.Down)` to chain keyboard focus into the location fields
- **IME chain** — Title → Reference Date → Location → City → Country (Done + clearFocus); Country is the final field and clears focus on Done
- **No GPS coupling** — location fields are architecturally isolated; `captureLocation`/`referenceLocation` are not touched; no reverse geocoding
- **Save remains disabled** — `TextButton(enabled = false)` unchanged; no `onSave` logic in Block E
- **`EditSessionViewModelTest`** — three new tests added: `onLocationDisplayNameChanged_updatesState`, `onLocationCityChanged_updatesState`, `onLocationCountryChanged_updatesState`

Latest verified test state (Session Metadata Editor Block E — Completed 2026-06-09):

- `testDebugUnitTest` — BUILD SUCCESSFUL, 22/22 `EditSessionViewModelTest` PASSED, 0 failures
- `assembleDebug` — BUILD SUCCESSFUL

No open Session Metadata Editor Block E tasks remain.

Block F completed (2026-06-09):

- **`EditSessionEvent`** — `sealed interface EditSessionEvent` with `data object SaveComplete` and `data object SaveFailed`; declared at package level in `EditSessionViewModel.kt`; exposed as `SharedFlow<EditSessionEvent>` from `events` property
- **`isDirty` and `isSaving`** — both `StateFlow<Boolean>` added to `EditSessionViewModel`; `isDirty` computed via `updateIsDirty()` called from every `onXxxChanged()` handler; `isSaving` set true at start of `onSave()` coroutine, guaranteed false in `finally`; Save button `enabled = isDirty && !isSaving`
- **`normalizeField()`** — `private fun normalizeField(s: String): String? = s.trim().ifEmpty { null }`; blank input always becomes null at save time; display values are never modified
- **`onSave()`** — validates reference date first (sets `referenceDateError`, returns without writing on failure); then `_isSaving.value = true`; writes changed field groups in order: title → referenceDate → location (all-or-nothing per group, not per field); on any storage write returning `false`, emits `SaveFailed` and returns; on all writes succeeding (or no writes needed), updates `initial*` vars, calls `updateIsDirty()` (resets `isDirty` to false), emits `SaveComplete`
- **Injectable storage lambdas** — `internal var sessionTitleUpdater`, `sessionReferenceDateUpdater`, `sessionLocationUpdater` (all replaceable in tests); defaults delegate to `SessionStorage.updateTitle/updateReferenceDate/updateLocation` respectively
- **`EditSessionScreen`** — `viewModel: EditSessionViewModel` is now a **required parameter** (no `= hiltViewModel()` default); `isDirty` and `isSaving` collected via `collectAsStateWithLifecycle()`; Save button wired to `viewModel::onSave` and `enabled = isDirty && !isSaving`
- **`MainActivity`** — `ROUTE_EDIT_SESSION_WITH_ARGS` composable fully wired: creates `editSessionViewModel = hiltViewModel()` and `cameraViewModel = hiltViewModel(cameraEntry)`; `LaunchedEffect` collects `editSessionViewModel.events`; `SaveComplete` → `cameraViewModel.refreshSavedSessions()` + `navController.popBackStack()`; `SaveFailed` → `snackbarHostState.showSnackbar(saveFailedMessage)`; `SnackbarHost` rendered at bottom of screen
- **`strings.xml`** — `edit_session_save_failed` → `"Couldn't save changes"` added
- **`EditSessionViewModelTest`** — 24 new tests added (isDirty tracking, onSave paths, event emission, storage call order, isSaving state); `createViewModel` helper updated with `titleUpdater`, `referenceDateUpdater`, `locationUpdater` parameters (all with defaults); `reader` moved to last position so existing trailing-lambda calls continue to work

Latest verified test state (Session Metadata Editor Block F — Completed 2026-06-09):

- `testDebugUnitTest` — BUILD SUCCESSFUL, 46/46 `EditSessionViewModelTest` PASSED (22 existing + 24 new)
- `assembleDebug` — BUILD SUCCESSFUL

No open Session Metadata Editor Block F tasks remain.

Block G completed (2026-06-09):

- **Back handling** — `EditSessionScreen` now intercepts both system back and the TopAppBar back icon. `BackHandler(enabled = isSaving || isDirty)` is active whenever either state is true.
- **Dirty back → Discard dialog** — when `isSaving == false && isDirty == true`, pressing back shows a Material 3 `AlertDialog`: title "Discard changes?", body "Your changes have not been saved.", confirm "Discard" (navigates back via `onBack()`), dismiss "Keep editing" (closes dialog).
- **Saving back → Saving-in-progress dialog** — when `isSaving == true`, pressing back shows an information dialog: title "Saving changes", body "Please wait until saving is finished.", confirm "OK" (closes dialog). Navigation is blocked; the save continues running.
- **Clean back** — when both `isDirty == false` and `isSaving == false`, back navigates immediately via `onBack()` with no dialog.
- **SaveComplete path unchanged** — `isDirty == false` is guaranteed before `SaveComplete` is emitted, so the `BackHandler` is disabled at navigation time; no dialog risk.
- **No ViewModel changes** — `isDirty` and `isSaving` were already implemented in Block F. Block G is purely a screen-level change.
- **7 new string resources** — 4 discard dialog strings + 3 saving dialog strings added to `strings.xml`.

Latest verified test state (Session Metadata Editor Block G — Completed 2026-06-09):

- `testDebugUnitTest` — BUILD SUCCESSFUL, all 46 `EditSessionViewModelTest` PASSED
- `assembleDebug` — BUILD SUCCESSFUL

No open Session Metadata Editor Block G tasks remain.

Block UX completed (2026-06-09) — Session Metadata Editor UX Correction (Pre-Block-H):

- **`SessionStorage.updateContent()`** — new atomic write function `(sessionsRoot, sessionId, title: String?, description: String?): Boolean`; trims both fields, blank → null; reads or creates `content` JSONObject; sets/removes title and description individually; **always writes back** `json.put("content", content)` — never removes the block even when both fields are null; path traversal protection identical to `updateTitle()`; returns false on missing metadata.json, invalid sessionId, IO or security errors; `updateTitle()` is preserved and unchanged
- **`InitialSessionFields`** extended — `description: String = ""`, `captureTimestampMs: Long = 0L`, `referenceSourceDisplayName: String = ""` added with **default values** so all existing positional test call sites compile without changes
- **`EditSessionViewModel`** extended — `descriptionField: StateFlow<String>` + `onDescriptionChanged()`; `captureTimestampMs: StateFlow<Long>` (read-only, from `capture.timestampMs`); `referenceSourceDisplayName: StateFlow<String>` (read-only, from `reference.sourceDisplayName`); `sessionTitleUpdater` lambda **replaced** by `sessionContentUpdater: (File, String, String?, String?) -> Boolean` (defaults to `SessionStorage.updateContent`); `updateIsDirty()` extended to include description; `onSave()` uses `sessionContentUpdater` atomically for title + description; `initialDescription` reset after successful save; `metadataReader` reads all new fields
- **`EditSessionScreen`** — fully rebuilt: `TopAppBar` subtitle column ("Update information about this comparison") in `SameViewSettingsSecondaryText`; Save button moved to `Scaffold.bottomBar` as `Button` with `imePadding()` + `navigationBarsPadding()`, `enabled = isDirty && !isSaving`; 3 `SettingsCard` groups: **Session** (title + description minLines=3), **Reference Photo** (thumbnail 64dp + filename/session date labels + reference date field with DatePicker), **Location** (place name + city + country); `Column(Arrangement.spacedBy(14.dp))` layout; `referenceImageUri` via `remember(viewModel.sessionId)`, `referenceFilename` via `remember(referenceSourceDisplayName)`, `captureDate` via `remember(captureTimestampMs, locale)` using `DateFormat.MEDIUM`; `DatePickerDialog` triggered by calendar `IconButton` trailing on reference date field; all Block G dialogs (discard, saving-in-progress) preserved; location display-name field now uses `edit_session_field_place_name` label
- **`strings.xml`** — `edit_session_screen_title` value updated to "Edit Session" (capital S); 17 new strings added: `edit_session_subtitle`, `edit_session_save_changes`, `edit_session_card_session`, `edit_session_card_reference_photo`, `edit_session_card_location`, `edit_session_field_description`, `edit_session_placeholder_title`, `edit_session_placeholder_description`, `edit_session_placeholder_reference_date`, `edit_session_reference_date_help`, `edit_session_label_filename`, `edit_session_label_session_date`, `edit_session_pick_date_content_description`, `edit_session_field_place_name`, `edit_session_placeholder_place_name`, `edit_session_placeholder_city`, `edit_session_placeholder_country`
- **`EditSessionViewModelTest`** — `createViewModel()` helper: `titleUpdater` param replaced by `contentUpdater: (File, String, String?, String?) -> Boolean`; 7 tests migrated (`onSave_withValidTitle_callsTitleUpdater` → `onSave_withChangedTitle_callsContentUpdater`, etc.); 4 new description tests added; 50/50 pass
- **`SessionStorageMetadataTest`** — `createSessionWithContentFields()` helper added; 6 new `updateContent_*` tests added (require instrumented device run)
- **Stable contracts unchanged** — `updateTitle()`, `updateReferenceDate()`, `updateLocation()` in `SessionStorage`; `EditSessionEvent` sealed interface; `ROUTE_EDIT_SESSION_WITH_ARGS` in `MainActivity`; `BackHandler` / discard / saving-in-progress dialog logic in `EditSessionScreen`

Latest verified test state (Session Metadata Editor Block UX — Completed 2026-06-09):

- `testDebugUnitTest` — BUILD SUCCESSFUL, 50/50 `EditSessionViewModelTest` PASSED (46 migrated + 4 new description tests)
- `assembleDebug` — BUILD SUCCESSFUL
- `SessionStorageMetadataTest.updateContent_*` — 6 tests added; require instrumented device run (not yet executed on device)

No open Session Metadata Editor Block UX tasks remain.

Block UX6 completed (2026-06-09) — Session Metadata Editor Final UX Cleanup Before Block H:

- **Session Card auf einzeilige Metadaten-Darstellung reduziert** — zwei-zeilige Darstellung (Label "Session date" + Wert) ersetzt durch einzelne Textzeile `"Captured on Jun 9, 2026 19:22"` mit formatierbarem String `edit_session_captured_on` = `"Captured on %s"`; ein `Text`-Composable statt zwei; `SameViewSettingsSecondaryText`/`bodySmall` Stil.
- **Reference-Date-Hilfetext aus TextField ausgelagert** — `supportingText`-Parameter aus `OutlinedTextField` entfernt; Help-Text und Error-Text werden jetzt als freie `Text`-Composables unterhalb der Thumbnail/Field-Row gerendert, über volle Card-Breite; `isError` verbleibt am TextField (rote Outline bleibt aktiv); Error-Text in `MaterialTheme.colorScheme.error`; 4dp-Spacer zwischen Row und Hilfetext.
- **Current Photo Card visuell an Reference Photo Card angeglichen** — plain `Column` ersetzt durch `Box` mit `Modifier.border(1.dp, outline, extraSmall)`, `heightIn(min = 56.dp)`, `padding(16/8dp)` und `Modifier.weight(1f)`; Label + Wert im Box-Inneren; keine Fokus-, Cursor- oder Klick-Affordance.

Latest verified test state (Session Metadata Editor Block UX6 — Completed 2026-06-09):

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL

No open Session Metadata Editor Block UX6 tasks remain.

---

Block UX7 completed (2026-06-10) — Session Metadata Editor Final Visual Alignment Fix:

- **Session Card Header vereinfacht** — Card-Titel "Session" entfernt; `captureDateWithTime` wird jetzt direkt als Card-Header übergeben (`SettingsCard(title = ...)`), formatiert als `"Created %s"` / `"Erstellt am %s"` mit neuem formatierbarem String `edit_session_created`; wenn kein Timestamp vorhanden, `title = null` (Card ohne Titel). Bisheriges Body-`Text`-Composable mit `edit_session_captured_on` und `Spacer(8.dp)` entfernt. Typografie, Farbe und Abstände identisch mit altem "Session"-Header (SettingsCard-interne Darstellung unverändert).
- **Current Photo Card Display-Box vertikal zentriert** — `Row(verticalAlignment = Alignment.Top)` → `Row(verticalAlignment = Alignment.CenterVertically)`; die Display-Box und das Thumbnail werden jetzt auf der gemeinsamen Mittelachse ausgerichtet. Größe, Breite und Inhalt der Box unverändert.

Latest verified test state (Session Metadata Editor Block UX7 — Completed 2026-06-10):

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL

No open Session Metadata Editor Block UX7 tasks remain.

---

Block H completed (2026-06-10) — Session Metadata Editor Instrumentation/UI Tests:

- **`EditSessionScreenTest`** — new instrumentation test class in `com.isardomains.sameview.ui.compare`; 22 tests across five groups: screen structure (8 tests: root present, back/save buttons present, all six field test tags present, created header when timestamp available, absence of standalone "Session" card title, reference/current/location cards present, captured-on label present), field pre-population (3 tests: title, reference date, and location fields), Save button state (4 tests: disabled initially; enabled after title, description, reference date, or location change), back/discard dialog navigation (4 tests: immediate back with no changes invokes callback, back with changes shows discard dialog, confirm navigates back, cancel keeps editor open), reference date validation UI (2 tests: invalid date shows error text, valid date shows no error text)
- **Test infrastructure** — `setEditSessionContent()` launches `ComponentActivity` via `ActivityScenario`, wires `EditSessionViewModel` with a real `SavedStateHandle`, and awaits `isLoading == false` before proceeding; `createSession()` writes a v4-compatible `metadata.json` plus reference and capture JPEG stubs into `filesDir/sessions/<sessionId>/`; `wakeTestDevice()` fires `KEYCODE_WAKEUP` to prevent display-off timing failures; `@After tearDown()` closes the scenario and deletes temp session directories

Latest verified test state (Session Metadata Editor Block H — Completed 2026-06-10):

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `EditSessionScreenTest` — 22/22 PASSED on SM-S911B (Android 16)
- `MediaStoreWriterGpsTest` (isolated) — 3/3 PASSED on SM-S911B (Android 16)
- `connectedDebugAndroidTest` full suite (482 tests) — 481/482 across two consecutive runs; one consistent failure in `MediaStoreWriterGpsTest.save_noGps_whenGpsSnapshotNull`; root cause: known Samsung IS_PENDING/media-scanner timing race (see Video Export Block 7 full-suite note for prior manifestation as `save_hasGpsTags_whenGpsSnapshotPresent`); both affected methods pass 3/3 in isolation; no functional coupling between Block H and `MediaStoreWriter` or `GpsExifWriter`; pre-existing device-state flake, full suite not claimed as fully green

No open Session Metadata Editor Block H tasks remain.

---

**Favourite Star in EditSessionScreen (Block F.3 + bugfix — 2026-06-20):**

- `EditSessionScreen` TopAppBar receives a Favourite star action (first action; consistent with CompareScreen)
- `EditSessionViewModel` exposes `isFavorite: StateFlow<Boolean>` loaded from `metadata.json`; `toggleFavorite()` performs an optimistic flip followed by `SessionStorage.updateFavorite()` on IO dispatcher
- Toggle does NOT affect `isDirty`; Save button and Discard dialog are unaffected
- `EditSessionEvent.FavoriteToggleComplete` emitted on success; `MainActivity` handles it by calling `cameraViewModel.refreshSavedSessions()` (no navigation) — this fixes the bug where `CompareScreen` showed a stale star after Favourite-only Back from EditSession
- `EditSessionViewModelTest` extended with `favoriteUpdater` injectable lambda and 2 new tests
- `EditSessionScreenTest` extended with 6 new `favoriteButton_*` tests; 28/28 PASSED

---

Block UX5 completed (2026-06-09) — Session Metadata Editor Pre-Block-H UX Fix:

- **Session date priorisiert** — in der Session Card steht "Session date" jetzt als erstes Element, vor Title und Description; die Hauptinformation der Session ist damit sofort sichtbar.
- **Session date Zeitgenauigkeit unified** — Session Card verwendet nun `captureDateWithTime` (Datum + Uhrzeit, z. B. "Jun 9, 2026 19:22") statt `captureDate` (nur Datum); konsistent mit CompareScreen und Current photo Card. `captureDate`-Derivation und unbenutzter `locale`-Import entfernt.
- **Reference-Date-Hilfetext korrigiert** — `edit_session_reference_date_help` zeigt jetzt die tatsächlich akzeptierten ISO-8601-Eingabeformate (`2008`, `2008-06`, `2008-06-15`); vorheriger Text zeigte "June 2008" / "June 15, 2008", was die Validierung ablehnt. Deutsche Übersetzung entsprechend angepasst.

Latest verified test state (Session Metadata Editor Block UX5 — Completed 2026-06-09):

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL

No open Session Metadata Editor Block UX5 tasks remain.

---

Block UX4 completed (2026-06-09) — Session Metadata Editor Reference Card Layout Refinement:

- **Reference photo card layout unified** — card body rebuilt from vertical (thumbnail → spacer → full-width field) to horizontal Row: thumbnail (80 dp) on the left, `OutlinedTextField` with `Modifier.weight(1f)` on the right. Mirrors the Current photo card structure. `supportingText` (help text / error) remains attached to the field and renders below it. All DatePicker logic, validation, strings, and dirty-state tracking unchanged.

Latest verified test state (Session Metadata Editor Block UX4 — Completed 2026-06-09):

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL

No open Session Metadata Editor Block UX4 tasks remain.

---

Block UX3 completed (2026-06-09) — Session Metadata Editor UX Polish:

- **"Session date" terminology unified** — the label in the Current photo card changed from "Captured on" (`edit_session_label_captured_on`) to "Session date" (`edit_session_label_session_date`), matching the Session card and the app's consistent terminology.
- **Thumbnails enlarged** — both the Reference photo and Current photo thumbnails increased from 64 dp to 80 dp; shape, crop logic, and layout unchanged.
- **Reference date help text improved** — `edit_session_reference_date_help` rewritten from technical ISO format examples to user-friendly natural-language examples ("Reference photo date. Examples: 2008, June 2008, or June 15, 2008."); German translation updated accordingly; storage formats and validation unchanged.

Latest verified test state (Session Metadata Editor Block UX3 — Completed 2026-06-09):

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL

No open Session Metadata Editor Block UX3 tasks remain.

---

Block UX2 completed (2026-06-09) — Session Metadata Editor UX Refinement V2:

- **Sentence case unified** — all visible text in the Session Metadata Editor now uses sentence case consistently: "Edit session", "Save changes", "Reference photo", "Session date", "Current photo". No Title Case labels remain.
- **Placeholders corrected** — all generic placeholder text replaced with concrete examples: Title → "e.g. Summer vacation in Italy", Description → "Add notes about this moment", Place name → "e.g. Marienplatz", City → "e.g. Munich", Country → "e.g. Germany".
- **Session date moved** — "Session date" display (read-only, from `captureTimestampMs`) moved from the Reference photo card into the Session card; shown below the Description field when a capture timestamp is available.
- **Filename removed** — filename label and value removed from the Reference photo card; `edit_session_label_filename` string resource removed; `referenceFilename` derivation and `referenceSourceDisplayName` collection removed from `EditSessionScreen`.
- **Current photo card added** — new fourth card between "Reference photo" and "Location"; shows only the capture thumbnail (`capture.jpg`); no labels, no metadata, no actions.
- **Reference photo card reduced** — now contains only: reference thumbnail, reference date field, DatePicker icon, and help text. Filename and session date are gone.
- **Card order** — Session → Reference photo → Current photo → Location.

Latest verified test state (Session Metadata Editor Block UX2 — Completed 2026-06-09):

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL

No open Session Metadata Editor Block UX2 tasks remain.

---

### Compare Slider Date Labels and Handle Refresh

**Status:** Completed  
**Specification:** `COMPARE_FLOW_V1.md §41`  
**Implementation date:** 2026-06-10

This block implements the handle redesign and temporal date labels specified in `COMPARE_FLOW_V1.md §41`. It was enabled by the Session Metadata v4 work (Blocks A–F), which established reliable availability of `reference.date` and `capture.timestampMs` in `metadata.json`.

**Step 1 — Data-layer prerequisites (commit e15bdb3):**

- `SavedSessionRef` — `referenceDate: String?` added; `saveSession()` derives it from `reference.date`
- `ScannedSession` — `referenceDate: String?` added; `validateUnsafe()` reads `json.optJSONObject("reference")?.optString("date")`
- `CompareInput` — `referenceDate: String?` added; populated from `SavedSessionRef` in `onCaptureSaved()`
- `MainActivity` — route, both `compareRoute()` overloads, and the `CompareScreen` call extended with `referenceDate`
- `CompareScreen` — new `referenceDate: String?` parameter, threaded to `CompareSliderViewport`
- `SessionScannerTest` — coverage for `referenceDate` propagation added

**Step 2 — UI/UX layer (commit 3fb489d):**

- `CompareLabelLogic.kt` (new) — pure function `computeCompareLabels` with 5-level priority chain; locale-aware `SimpleDateFormat`; no Compose/Android Context dependency; `CompareLabelPair` data class
- `CompareDivider` handle redesigned — 40dp (was 32dp) filled circle in `SameViewAccent` blue with white `KeyboardArrowLeft`/`KeyboardArrowRight` icons
- Moving text labels left/right of handle; per-label edge-hiding via `rememberTextMeasurer()`; text shadow for contrast
- Accessibility: `compare_slider_labels_content_description` format string always includes both label values, even when edge-hidden
- Reference and Current image-overlay badges removed from `CompareSliderViewport`; `OriginalReferenceBadge` retained unchanged
- `CompareLabelLogicTest.kt` (new) — 16 unit tests covering all 5 levels, precision boundaries, and German locale
- `CompareScreenTest.kt` — 8 affected tests updated; 7 new UI tests added (86 total)
- `strings.xml` (EN + DE) — `compare_label_past`, `compare_label_present`, `compare_label_current`, `compare_slider_labels_content_description` added

Full UX spec, five-level label logic, edge behavior, fullscreen behavior, accessibility, and i18n: `COMPARE_FLOW_V1.md §41`.

Latest verified test state (Compare Slider Date Labels and Handle Refresh — Completed 2026-06-10):

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `CompareLabelLogicTest` — 16/16 PASSED (JVM unit tests)
- `CompareScreenTest` — 86/86 PASSED on SM-S911B (Android 16)
- `MediaStoreWriterGpsTest` (isolated) — 3/3 PASSED on SM-S911B (Android 16)
- Manual smoke test — completed successfully
- `connectedDebugAndroidTest` full suite — 1 consistent failure in `MediaStoreWriterGpsTest.save_hasGpsTags_whenGpsSnapshotPresent`; pre-existing Samsung IS_PENDING/media-scanner timing race; passes 3/3 in isolation; not caused by this block; same category as Block H flakiness; full suite not claimed as fully green

Spec correction (2026-06-10): Level 3 condition relaxed — day precision now always produces `d MMM` labels when year and month match, including same-day (`10 Jun ↔ 10 Jun`). Level 4 no longer covers the day-precision same-date case. `CompareLabelLogicTest.level3_sameYear_sameMonth_sameDays_dayPrecision` updated accordingly; full unit test suite green.

No open Compare Slider Date Labels and Handle Refresh tasks remain.

---

## Open Future UX Investigations

This section tracks UX questions that are deliberately left open — not committed features, but unresolved questions that must be investigated before they can become product decisions.

### Reference Date Override Transparency

**Question:** Should users be able to see whether a session's reference date was automatically detected from EXIF metadata or manually entered?

**Context:** `reference.dateSource` (`"exif"` or `"manual"`) and `reference.userEdited` are persisted in `metadata.json` at creation and update time. The Session Metadata Editor V1 does not expose this distinction in the UI — all reference dates appear identically regardless of origin. A user who corrects an EXIF-derived date has no confirmation that their edit superseded the automatic value.

**Not a V1 requirement. No implementation. No `reference.originalDate` field.** No UI change until this investigation produces a product decision.

**Cross-reference:** `SESSION_METADATA_EDITOR_V1.md §19` already lists "Visual indicator showing whether the current date was set from EXIF or manually" and "Read-only original EXIF date hint with Reset action" as explicitly deferred future extensions. The investigation point here is whether either of these additions would meaningfully help users, or whether the `dateSource`/`userEdited` distinction is too technical to warrant surface-level exposure in V1 UX.

---

## Localization

### English Sentence Case Rule

User-facing English strings (labels, screen titles, buttons, section headings) use **Sentence case**: first word capitalised, all others lowercase unless they are proper nouns or product names.

Exceptions: **SameView**, **Compare Slider**, **Before & After**, **#MadeWithSameView**.

This rule applies to all new strings added to `values/strings.xml`. It does not affect non-visible strings (content descriptions, format strings, test tags, error keys).

### German Tone Rule

German localization uses informal user address consistently.

- Use: `du`, `dir`, `dich`, `dein`, `deine`, `deiner`, `deinem`
- Do not use: `Sie`, `Ihnen`, `Ihr`, `Ihre`, `Ihren`, `Ihrem`
- Imperative forms must be informal: `Öffne`, `Wähle`, `Tippe`, `Aktiviere`
- Never use formal imperative: `Öffnen Sie`, `Wählen Sie`, `Tippen Sie`

This rule applies to all user-facing German strings in `values-de/strings.xml`.
Non-user-facing strings (content descriptions, test tags) are not affected.

---

## Practical Working Rules

### Scope discipline
- Only implement the requested feature
- No speculative future features

### Change discipline
- Keep changes minimal
- No refactoring outside scope

### UI discipline
- No layout that resizes preview
- Use central color definitions
- Keep camera controls practical and uncluttered

### State
- ViewModel is source of truth for active camera-session state
- No persistence of active overlay/camera state across full app restarts

### Testability
- Keep logic testable
- Do not introduce complex UI logic in Composables

---

## Current Verification Notes

Existing tests cover the critical release paths around:
- ViewModel overlay/reference state
- capture lock and capture error handling
- snackbar replay protection
- session scanner/storage/deleter behavior
- title metadata read/write behavior
- title-update exception boundary (ViewModel-level `catch(Exception)`, failure Snackbar path verified via `updateSessionTitle_updaterThrows_emitsSnackbarFailure`)
- session-save failure feedback path: `capture_saved_compare_failed` Snackbar surfaced when MediaStore save succeeds but `SessionStorage.saveSession` returns null (`onCaptureSaved_withSnapshotButNoSessionRef_emitsCaptureCompareFailedSnackbar`, `onCaptureSaved_withSnapshotButNoSessionRef_setsCaptureSuccessHadReferenceTrue`)
- compare navigation
- compare slider and fullscreen behavior
- compare library grid, navigation, selection, delete, and title display
- Camera top-right History/Overflow navigation
- stable Compare button semantics and disabled Compare hints
- Camera controls, landscape alignment, grid, and capture feedback
- Overlay coverage computation and live visibility warning state
- Settings persistence and settings-driven workflow behavior
- GPS activation lifecycle (all four conditions, start/stop, duplicate-start prevention)
- GPS guidance state computation (proximity colors, hysteresis, bearing suppression, distance thresholds)
- GPS snapshot freeze at capture time (null when guidance OFF, null when no fix, correct values when fix present, immutability after later location updates)
- GPS EXIF writing (DMS rational format, altitude, GPSDateStamp, GPSTimeStamp, GPSProcessingMethod, orientation invariant)
- GPS EXIF in MediaStore image and capture.jpg (instrumentation: MediaStoreWriterGpsTest, SessionStorageGpsTest)
- GPS metadata in metadata.json (captureLocation, referenceLocation presence/absence)
- reference.jpg never receives GPS; reference-original.jpg preserves GPS when present
- session backup export: ZIP structure, byte integrity, operation locks, SAF null-URI no-op handling
- CompareScreen overflow: Backup Session visibility (sessionId != null), disabled state during backup
- CompareLibrary backup: icon presence, disabled states (empty selection, isBackupInProgress, isDeletionInProgress)
- Select All sets selectedSessionIds to the **current filtered view** (not all sessions unconditionally); this supersedes the prior "complete scanned session list" behavior — see `FAVORITES_AND_LIBRARY_FILTERS_V1.md PD-08`

Latest verified test state (GPS Recreation Blocks 1–8 complete):

- `testDebugUnitTest` — PASSED
- `MediaStoreWriterGpsTest` — 3/3 PASSED on SM-S911B
- `SessionStorageGpsTest` — 24/24 PASSED on SM-S911B
- `ReferenceImageMetadataReaderTest` — PASSED on SM-S911B
- `connectedDebugAndroidTest` full suite — PASSED on SM-S911B
- Release build (`assembleRelease`) — BUILD SUCCESSFUL
- Real-device validation — completed on SM-S911B: GPS EXIF in gallery image verified, capture.jpg EXIF verified, reference.jpg confirmed GPS-free, metadata.json captureLocation/referenceLocation verified, Recreation Guidance ON/OFF behavior verified

No open GPS implementation tasks remain.

Latest verified test state (Session Backup Export complete):

- `testDebugUnitTest` (SessionBackupExporterTest, CameraViewModelTest backup extensions) — PASSED
- `SessionBackupExporterInstrumentedTest` — compilation verified on SM-S911B
- `CompareScreenTest` and `CompareLibraryScreenTest` backup extensions — compilation verified
- Manual device smoke test — completed on SM-S911B: single-session backup, multi-session backup, Select All + Backup, SAF cancel no-op, success snackbar, ZIP structure verified

No open Session Backup Export implementation tasks remain.

Latest verified test state (Session Originals — Blocks A–F complete, 2026-06-22):

- `testDebugUnitTest` — BUILD SUCCESSFUL (642 tests, 0 failed); includes 14 `ResolveExtensionForMimeTypeTest`, 3 `EditSessionViewModelTest` Block-F tests, updated `SessionBackupExporterTest` (v5/metadata-driven)
- `assembleDebug` — BUILD SUCCESSFUL
- `assembleRelease` — BUILD SUCCESSFUL
- `connectedDebugAndroidTest` full suite — **610/610 PASSED** on SM-S911B (Android 16); includes `SessionStorageMetadataTest` (byte-identity, sourceMimeType, sourceUri), `SessionScannerTest` (v5 validation, backward compat), `SessionBackupExporterInstrumentedTest` (v4/v5 ZIP structure)

No open Session Originals tasks remain.

Latest verified test state (Session Originals Privacy — Blocks A–F complete, 2026-07-XX):

- `testDebugUnitTest` — BUILD SUCCESSFUL; includes `SessionStoragePrivacyJpegTest` (JPEG byte-level stripper, 9 tests), `ResolveExtensionForMimeTypeTest` (14 MIME-mapping tests), `CameraViewModelTest` Block-D/E tests (`stripOriginalsMetadata` flow, freeze, propagation)
- `assembleDebug` — BUILD SUCCESSFUL
- `assembleRelease` — BUILD SUCCESSFUL
- `connectedDebugAndroidTest` full suite — **631/631 PASSED** on SM-S911B (Android 16); includes `SessionStorageMetadataTest` (Privacy ON/OFF, JPEG/PNG/HEIC/unknown MIME stripping, GPS removal, capture-original byte-identity), `ResolveSourceUriTest` (Picker URI regression guard), `SessionScannerTest`, `SessionBackupExporterInstrumentedTest`
- HEIC real-device test with `privacy/reference_source_original_heic_test.heic` (2.67 MB) — `ImageDecoder` decode + JPEG-95 re-encode verified; no GPS/EXIF/Make in output; `originals.referenceSourceStoredMimeType = "image/jpeg"` confirmed

No open Session Originals Privacy tasks remain.

Latest verified test state (Video Export Blocks 1–2 complete):

- `testDebugUnitTest` — PASSED (T-U-01–T-U-14 grün)
- `VideoExportPipelineTest` (T-I-01) — PASSED on SM-S911B (Android 16)
- `connectedDebugAndroidTest` full suite — 329/329 PASSED on SM-S911B (Android 16)
- MP4 playback on SM-S911B: verified
- No known regressions

No open Video Export Blocks 1–2 implementation tasks remain.

Latest verified test state (Video Export Block 3+4 — Completed 2026-06-10):

- `testDebugUnitTest` — PASSED
- `CompareScreenTest` — 82/82 PASSED (prior to Session Metadata Editor Block A)
- `VideoExportPipelineTest` — 2/2 PASSED
- `assembleRelease` — BUILD SUCCESSFUL
- `ReferenceImageMetadataReaderTest` — 2 Failures; pre-existing, not caused by Block 3+4
- Manual device flow — **Completed on SM-S911B (2026-06-10)**

Manual device verification completed (SM-S911B, Android 16, 2026-06-10):

- [x] Configuring-State fully operable
- [x] Rendering-State and progress display
- [x] Cancel Export Dialog (Back from Rendering)
- [x] Preview Playback (auto-play, loop, muted)
- [x] Share Sheet (opens on tap; cancel is not an error)
- [x] Delete Video Confirmation + Delete
- [x] Done / Back from Preview
- [x] Portrait rendering and preview
- [x] Landscape rendering and preview
- [x] Gallery / Movies / SameView visibility check after export

Latest verified test state (Video Export Block 5 — Completed 2026-06-04):

- `testDebugUnitTest` — PASSED
- `VideoExportPipelineStandardTest` (T-I-01, T-I-02) — PASSED on SM-S911B (Android 16)
- `VideoExportPipelineTest` (T-I-03) — PASSED on SM-S911B (Android 16)
- `assembleDebug` — BUILD SUCCESSFUL
- `assembleRelease` — BUILD SUCCESSFUL
- High Quality export (HEVC preferred / AVC fallback): verified on SM-S911B
- Resolution 3840×2160 or 1920×1080 fallback: accepted by test; confirmed valid MP4 committed to MediaStore

Test class structure note: T-I-01 and T-I-02 were moved from `VideoExportPipelineTest.kt` to `VideoExportPipelineStandardTest.kt` to resolve an ART class-loading issue (ClassNotFoundException caused by coroutine lambda classes from multiple test methods sharing a DEX shard). Both files are in `com.isardomains.sameview.video`; both are black-box instrumentation tests with no reference to production internals.

No open Video Export Block 5 implementation tasks remain.

Latest verified test state (Video Export Block 6 — Completed 2026-06-04):

- `testDebugUnitTest` — PASSED (387 tests)
- T-U-09 (branding ON: totalFrameCount = animationFrameCount + 45) — PASSED (3 presets)
- T-U-10 (branding OFF: totalFrameCount = animationFrameCount) — PASSED (3 presets)
- `assembleDebug` — BUILD SUCCESSFUL
- `assembleRelease` — BUILD SUCCESSFUL
- T-I-02 (branding ON + duration check) — PASSED on SM-S911B (Android 16)

Manual device verification — Completed:

- [x] Video with brandingEnabled = true: endcard appears after main animation
- [x] Video with brandingEnabled = false: no endcard
- [x] Fade-in (200 ms) and fade-out (200 ms) visible
- [x] Logo visible and correctly scaled (Portrait + Landscape + Original)
- [x] "#MadeWithSameView" dominant; "Made with ❤️" smaller; heart = red
- [x] Background color #0D1424 correct
- [x] Total video duration correct (animation + 1.5 s endcard)

No open Block 6 tasks remain.

Latest verified test state (Video Export Block 7 — Targeted Verification Completed 2026-06-04):

- `testDebugUnitTest` — PASSED
- `assembleDebug` — BUILD SUCCESSFUL
- `assembleRelease` — BUILD SUCCESSFUL
- T-I-01 (`VideoExportPipelineStandardTest`) — PASSED on SM-S911B (Android 16)
- T-I-02 (`VideoExportPipelineStandardTest`) — PASSED on SM-S911B (Android 16)
- T-I-03 (`VideoExportPipelineTest`) — PASSED on SM-S911B (Android 16)
- T-I-04 (`VideoExportPipelineTest`) — PASSED on SM-S911B (Android 16)
- `ReferenceImageMetadataReaderTest` — 19/19 PASSED on SM-S911B (Android 16) — after test infrastructure fix
- `connectedDebugAndroidTest` full suite (407 tests) — run twice on SM-S911B (Android 16); no Video Export failures; no `ReferenceImageMetadataReaderTest` failures
- Manual smoke test — **Completed on SM-S911B (2026-06-10)**

No remaining Video Export blocker. Block 7 fully complete.

No open Video Export Block 7 tasks remain.

---

### Animated Mode Preview (2026-06-12)

Post-Block-7 addition. Implements the animated mode preview inside the Configuring state of `CreateVideoScreen`.

**Neue Datei:** `VideoModePreview.kt` in `com.isardomains.sameview.ui.video`

- Composable `VideoModePreview(mode, sessionDir)` — 16:9 Frame, max 200 dp Höhe, horizontal zentriert via `BoxWithConstraints`
- Lädt `reference.jpg` / `capture.jpg` aus `sessionDir` via Coil `AsyncImage`
- **Compare Slider:** Hold-Reference → Sweep links→rechts (Cubic Smoothstep) → Hold-Capture → Pause → Restart; 4 s Loop via `InfiniteTransition`
- **Before & After:** Hold-Reference → Crossfade 500 ms → Hold-Capture → Pause → Restart; 4 s Loop via `InfiniteTransition`
- Moduswechsel: 175 ms `Crossfade`; neue Animation startet sofort
- Reduce Motion (`ANIMATOR_DURATION_SCALE == 0`): keine `InfiniteTransition`; Slider-Modus statisch mit 50 %-Split + Divider-Linie; B&A-Modus statisch beide Bilder bei 0.5 Alpha überlagert
- Accessibility: `Modifier.clearAndSetSemantics {}` — nur Preview-Frame ausgeblendet, nicht Card oder Segment-Control

**Geändert:** `CreateVideoScreen.kt`

- `sessionDir` lokal berechnet: `File(context.filesDir, "sessions/${viewModel.sessionId}")`; kein neues ViewModel-Feld
- `HorizontalDivider` + `VideoModePreview` in Video-Type-`SettingsCard` nach Segment-Control eingefügt
- Neuer Parameter `sessionDir: File` in `ConfiguringContent`
- Neuer Import: `HorizontalDivider`, `java.io.File`

**Nicht geändert:** `CreateVideoViewModel`, Export-Pipeline, `CompareScreen`, `MainActivity`, `SettingsRepository`, `strings.xml`, Gradle, Manifest, Navigation, Tests

**Test-Status:** `testDebugUnitTest` + `assembleDebug` + `assembleRelease` — siehe aktuellen Build-Status

**Manuelle Verifikation offen:** CreateVideoScreen Portrait, CreateVideoScreen Landscape, Compare Slider Preview, Before & After Preview, Reduce Motion ON, Tablet/großes Layout (falls nicht verfügbar)

T-I-04 (`t_i_04_deleteVideo_removesEntryFromMediaStore`) added to `VideoExportPipelineTest.kt` in Block 7. No production code changes.

Test infrastructure fix (Block 7): `PhotoPickerMimicContentProvider` and `SafMimicContentProvider` moved from `app/src/androidTest/` to `app/src/debug/` so their classes live in the app APK's classloader. Root cause: the classes landed in DEX shard 11 of the test APK; `Application.getClassLoader()` in the app process cannot reach secondary DEX shards at ContentProvider instantiation time. Moving to `src/debug/` places them in the app APK's own primary DEX. Additionally, `require_original=1` is now pre-embedded in `PhotoPickerMimicContentProvider.uriFor()` because `MediaStore.setRequireOriginal()` on Android 16 rejects non-MediaStore authorities (throws `IllegalArgumentException`), which was silently caught by `resolveSourceUri()` and prevented the original file from being served. No changes to production code or test logic.

Full suite status: the full `connectedDebugAndroidTest` (407 tests) ran twice on SM-S911B (Android 16). Each run produced one different flaky failure:

- Run 1: `AboutScreenTest.aboutContent_showsCoreV2Information` — Compose Activity timing race ("No compose hierarchies found")
- Run 2: `MediaStoreWriterGpsTest.save_hasGpsTags_whenGpsSnapshotPresent` — transient MediaStore `.pending` ENOENT

Both tests pass cleanly in isolation (3/3 each). These are pre-existing device-state flaky failures unrelated to Video Export. The full suite is **not claimed as fully green**. This flakiness is tracked outside Video Export scope.

For the next Closed Testing upload, re-run the following verifications after any code change:

- unit tests
- connected instrumentation tests
- release build/sign/install smoke test on a real device

---

### Block 9 — Flash Video Mode + Branding Timing Model (2026-06-17)

**Neue Dateien:**

| Datei | Inhalt |
|---|---|
| `video/FlashRenderEngine.kt` | `FlashRenderEngine : VideoFrameRenderer`; Phase 1 (45 Frames Hold, Reference+Overlay), Phase 2 (Hard-Cut-Wechsel Reference/Capture); Fill-Semantik; companion object mit `FLASH_HOLD_FRAMES=45`, `cycleCount()`, `showCaptureAt()` |
| `video/FlashRenderEngineTest.kt` | T-F-U-01–T-F-U-09; 21 Unit-Tests für Frame-Count, Phase-Grenzen, Zyklus-Logik, Endframe=Capture |
| `androidTest/video/VideoExportPipelineFlashTest.kt` | T-F-I-01, T-F-I-02; End-to-End-Tests (Flash MP4 gültig; Dateiname endet auf `_flash.mp4`; Duration-Check mit neuem Branding-Modell) |

**Geänderte Produktionsdateien:**

| Datei | Änderung |
|---|---|
| `video/VideoRenderConfig.kt` | `VideoMode.FLASH` ergänzt; `animationFrameCount` gibt jetzt immer `durationMs × 30 / 1000` zurück (Branding-Timing-Modell-Änderung) |
| `video/VideoExportPipeline.kt` | `createRenderer()` + `buildDisplayName()` + `computeHoldFrameCount()` um `FLASH`-Zweig erweitert; Flash-HoldFrameCount = `FlashRenderEngine.FLASH_HOLD_FRAMES` (45, fest) |
| `ui/video/CreateVideoScreen.kt` | 3. Modus (`VideoMode.FLASH`) im Segment-Control; `create_video_branding_hint`-Text unter Branding-Toggle (dauerhaft sichtbar, `bodySmall`, `SameViewSettingsSecondaryText`) |
| `ui/video/VideoModePreview.kt` | `FLASH`-Zweig mit `FlashStatic` (beide Bilder 0.5 Alpha) und `FlashAnimated` (Hard-Cut via `flashShowCaptureFromProgress`) |
| `ui/video/VideoLoadingPreview.kt` | `FLASH`-Zweig mit `FlashLoadingStatic` und `FlashLoadingAnimated` + `FlashLoadingFrame` (ContentScale.Crop) |
| `values/strings.xml` | `create_video_mode_compare_slider` → "Compare"; neu: `create_video_mode_flash` = "Flash"; `create_video_branding_hint` = "Adds 1.5 seconds to your video" |
| `values-de/strings.xml` | `create_video_mode_compare_slider` → "Compare"; neu: `create_video_mode_flash` = "Flash"; `create_video_branding_hint` = "Fügt deinem Video 1,5 Sekunden hinzu" |

**Geänderte Testdateien (Wertanpassungen durch Branding-Timing-Modell):**

| Datei | Änderung |
|---|---|
| `VideoRenderConfigTest.kt` | T-U-09: Assertions 75+45=120, 135+45=180, 195+45=240 → 120+45=165, 180+45=225, 240+45=285 |
| `CompareSliderRenderEngineTest.kt` | T-U-01 branding ON: `is75`, `is135`, `is195` → `is120`, `is180`, `is240` |
| `BeforeAfterRenderEngineTest.kt` | T-U-05 branding ON: identische Änderungen |
| `VideoExportPipelineStandardTest.kt` | T-I-02: Duration-Assertion `≈durationMs` → `≈durationMs + BRANDING_DURATION_MS` (3500 ms) |

**Branding-Timing-Modell — Zusammenfassung der Änderung:**

Vorher: `animationFrameCount = (selectedDuration − brandingDuration) wenn branding ON`. Gesamt-Video = immer = selectedDuration.

Neu: `animationFrameCount = selectedDuration` (immer). Endcard wird additiv angehängt. Gesamt-Video = selectedDuration + 1.5 s (wenn branding ON). Die gewählte Dauer beschreibt immer die eigentliche Animationsdauer — unabhängig vom Branding-Toggle. Gilt für alle drei Modi gleichmäßig.

**Teststatus (2026-06-17):**

| Test | Status |
|---|---|
| `testDebugUnitTest` (gesamt) | PASSED |
| `FlashRenderEngineTest` (21 Tests) | PASSED |
| `CompareSliderRenderEngineTest` | PASSED |
| `BeforeAfterRenderEngineTest` | PASSED |
| `VideoRenderConfigTest` | PASSED |
| `assembleDebug` | BUILD SUCCESSFUL |
| `assembleRelease` | BUILD SUCCESSFUL |

Manuelle Geräteverifikation (Flash-Export, Branding-Timing, UI-Labels) offen — auf SM-S911B oder gleichwertigem Gerät auszuführen.

---

### Block 9b — Video Type Selector UX Polish (2026-06-17)

**Geänderte Dateien:**

| Datei | Änderung |
|---|---|
| `ui/settings/SettingsComponents.kt` | `SameViewSegmentItem`: neues optionales Feld `icon: ImageVector? = null` (nach `testTag`, Default null — alle bestehenden Aufrufer unverändert); `SameViewSegmentControl`: Box-Inhalt verzweigt — wenn `icon != null`: Column mit Icon 24dp (dekorativ, `contentDescription = null`, `tint` = Segment-Farbe) + Spacer 4dp + Text labelLarge zentriert; wenn `icon == null`: bestehendes Text-only-Verhalten exakt unverändert |
| `ui/video/CreateVideoScreen.kt` | `modeItems`: Icons zu allen drei Modi ergänzt (`CompareArrows`, `Timeline`, `FlashOn`); Branding-Hint-`Text` entfernt |
| `values/strings.xml` | `create_video_branding_hint` Key entfernt |
| `values-de/strings.xml` | `create_video_branding_hint` Key entfernt |

**Neue Imports in SettingsComponents.kt:**
`size`, `Icon` (Material 3), `ImageVector`, `TextAlign`

**Neue Imports in CreateVideoScreen.kt:**
`Icons.AutoMirrored.Filled.CompareArrows`, `Icons.Filled.Timeline`, `Icons.Filled.FlashOn`

**Icons (alle aus `material-icons-extended`, bereits Abhängigkeit):**
- Compare → `Icons.AutoMirrored.Filled.CompareArrows`
- Before & After → `Icons.Filled.Timeline`
- Flash → `Icons.Filled.FlashOn`

**Bekannte Einschränkung — Label-Umbruch:**

Mit `labelLarge` (14sp) und 1/3-Breite (≈91dp Textbereich auf 360dp Gerät) bricht "Before & After" weiterhin auf 2 Zeilen um. Das ist durch die Produkt-Entscheidung bewusst akzeptiert: die Schriftgröße wurde NICHT reduziert, um den Umbruch zu vermeiden. Der Icon als visuelles Anker-Element aller drei Segmente schafft Gleichgewicht, auch wenn "Before & After" mehr Texthöhe belegt als "Compare" und "Flash". Manuelle Verifikation bei Font Scale 1.3× und 1.5× erforderlich.

**Teststatus (2026-06-17):**

| Test | Status |
|---|---|
| `testDebugUnitTest` | PASSED |
| `assembleDebug` | BUILD SUCCESSFUL |
| `assembleRelease` | BUILD SUCCESSFUL |

Manuelle Verifikation offen: Portrait (360dp), Landscape, Font Scale 1.3×, Font Scale 1.5×.

---

### Responsive Layout System — Block 2: CompareLibrary Grid Scaling (2026-06-18)

Full specification: `RESPONSIVE_LAYOUT_SYSTEM_V1.md`

`RESPONSIVE_LAYOUT_SYSTEM_V1.md` is the authoritative specification for the responsive layout system of the SameView Android app. Block 1 of that document is the spec itself. Block 2 is the first implementation block and is now complete.

**Implemented:**

- `CompareLibraryScreen` uses `WindowWidthSizeClass` to determine grid column count:
  - Compact → 2 columns (previous fixed behavior, preserved as default)
  - Medium → 3 columns
  - Expanded → 4 columns
- `calculateWindowSizeClass(this)` is computed once in `MainActivity` inside the `setContent` composable scope
- `windowWidthSizeClass` is passed exclusively to `CompareLibraryScreen`; no other screen is affected
- `material3-window-size-class` artifact added as a BOM-managed dependency (alias `androidx-compose-material3-windowsizeclass`; alias `…-window-size-class` is a Gradle reserved name and was rejected)
- `@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)` applied to `MainActivity.onCreate`, `CompareLibraryScreen`, and `CompareLibraryScreenTest`

**Not changed:** CameraScreen, CompareScreen, EditSessionScreen, CreateVideoScreen, SettingsScreen, AboutScreen, CameraViewModel, SessionScanner, SessionStorage, Backup/Delete logic, Navigation graph structure, Manifest, Permissions, Strings, metadata.json, Video Export pipeline.

**Verified test state (Block 2 — Completed 2026-06-18):**

| Test | Status |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | BUILD SUCCESSFUL |
| `CompareLibraryScreenTest` (40 tests) | 40/40 PASSED on SM-S911B (Android 16) |
| `connectedDebugAndroidTest` (511 tests) | 511/511 PASSED on SM-S911B (Android 16) |

No open Block 2 tasks remain.

---

### Responsive Layout System — Block 3A: Max-Width Constraints (2026-06-18)

Full specification: `RESPONSIVE_LAYOUT_SYSTEM_V1.md` (see addendum for Block 3A/3B split)

**Implemented:**

- `SettingsScreen` and `CreateVideoScreen` (Configuring state only) receive a centered max-width container on `WindowWidthSizeClass.Expanded`:
  - Max content width: **680 dp**, centered horizontally
  - Compact and Medium: current behavior unchanged
- `windowWidthSizeClass` is passed from `MainActivity` to both screens; the value is already computed by `calculateWindowSizeClass(this)` since Block 2
- `AboutScreen`: no change; already has `widthIn(max=520.dp)` and is fully responsive per `ABOUT_SCREEN.md §10`
- `EditSessionScreen`: intentionally deferred to **Block 3B** due to `Scaffold.bottomBar` Save button requiring separate `navigationBarsPadding()` / `imePadding()` handling

**Not changed:** CameraScreen, CompareScreen, CompareLibraryScreen, EditSessionScreen, Video Export Pipeline, `SettingsComponents.kt`, `SettingsViewModel`, `SettingsRepository`, DataStore, Permission flows, Session Storage, Backup/Delete logic, Manifest, Permissions, Strings, metadata.json, rendering pipeline.

**Verified test state (Block 3A — Completed 2026-06-18):**

| Test | Status |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | BUILD SUCCESSFUL |
| `SettingsScreenTest` (20 tests) | 20/20 PASSED on SM-S911B (Android 16) |
| `connectedDebugAndroidTest` (511 tests) | 511/511 PASSED on SM-S911B (Android 16) |

No open Block 3A tasks remain.

---

### Responsive Layout System — Block 3B: EditSessionScreen Max-Width (2026-06-18)

Full specification: `RESPONSIVE_LAYOUT_SYSTEM_V1.md` (see addendum A4)

**Implemented:**

- `EditSessionScreen` receives a centered max-width container on `WindowWidthSizeClass.Expanded`:
  - Max content width: **680 dp**, centered horizontally
  - Compact and Medium: current behavior unchanged
- Save button (in `Scaffold.bottomBar`) is visually constrained to the same 680 dp width as the form content on Expanded
- `navigationBarsPadding()` and `imePadding()` remain on the outermost `fillMaxWidth()` bottomBar container; only the visual inner container is constrained — Scaffold bottomBar height measurement and keyboard-above behavior are unaffected

**Not changed:** `EditSessionViewModel`, `SessionStorage`, `SessionScanner`, `metadata.json`, Save/Discard/Saving-in-progress dialog logic, reference date validation, field pre-population, `BackHandler`, image thumbnail display, navigation contracts, Manifest, Permissions, Strings.

**Verified test state (Block 3B — Completed 2026-06-18):**

| Test | Status |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | BUILD SUCCESSFUL |
| `EditSessionScreenTest` (22 tests) | 22/22 PASSED on SM-S911B (Android 16) |
| `connectedDebugAndroidTest` (511 tests) | 511/511 PASSED on SM-S911B (Android 16) |

No open Block 3B tasks remain.

---

### Responsive Layout System — Block 4: CompareScreen Max-Width (2026-06-18)

Full specification: `RESPONSIVE_LAYOUT_SYSTEM_V1.md` (see addendum A5)

**Implemented:**

- `CompareScreen` receives a centered 900 dp max-width container on `WindowWidthSizeClass.Expanded`:
  - A `Box(fillMaxWidth, weight(1f), contentAlignment=TopCenter)` replaces the direct Column children; inside it, a `Column(widthIn(max=900.dp), fillMaxHeight)` encloses both `CompareMetadataHeader` and the compare viewport (portrait and landscape branches)
  - On Compact and Medium: current behavior unchanged — inner `Column` uses `fillMaxWidth()`
- `TopAppBar` (custom `Row`) remains full-width outside the container; it is a direct Column child before the wrapper `Box`
- Fullscreen mode: `TopAppBar` and `CompareMetadataHeader` are hidden as before; the 900 dp container remains active for the compare viewport
- `windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact` parameter added to `CompareScreen`; default `Compact` preserves all existing test call sites without change
- `windowWidthSizeClass = windowSizeClass.widthSizeClass` wired in `MainActivity` at the `CompareScreen` call site
- `@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)` added to `CompareScreen` and `CompareScreenTest`

**Not changed:** Compare rendering, `ContentScale`, `computeFitBounds`, slider, divider, handle, labels, edge-hiding logic, `isLandscape` branching, `CompareMetadataHeader` internal structure, §42 maxLines / smart-reduction, `PortraitLocationRow` / `BoxWithConstraints` text-measurement, session data, navigation, `CameraViewModel`, `SessionStorage`, `SessionScanner`, Manifest, Permissions, Strings, Gradle.

**Verified test state (Block 4 — Completed 2026-06-18):**

| Test | Status |
| --- | --- |
| `assembleDebug` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | BUILD SUCCESSFUL |
| `connectedDebugAndroidTest` (full suite) | PASSED on SM-S911B (Android 16) |

No open Block 4 tasks remain.

---

### Responsive Layout System — Block 5: CreateVideoScreen Preview State (2026-06-18)

Full specification: `RESPONSIVE_LAYOUT_SYSTEM_V1.md` (see addendum A6); `VIDEO_EXPORT_V1.md §7.5` (see Expanded layout note)

**Implemented:**

- `PreviewContent` in `CreateVideoScreen` receives a centered 800 dp max-width container on `WindowWidthSizeClass.Expanded`:
  - A `Box(fillMaxWidth, weight(1f), contentAlignment=TopCenter)` wraps an inner `Column(widthIn(max=800.dp), fillMaxHeight)` that encloses both the `AndroidView`(PlayerView) and the actions `Column` (Share / Done / Delete Video)
  - On Compact and Medium: current behavior unchanged — inner `Column` uses `fillMaxWidth()`
- `PlayerView` and all three action buttons share the same 800 dp container — no separate widths
- `TopAppBar` remains full-width (Material3 standard behavior, outside the container)
- `navigationBarsPadding()` on the actions `Column` continues to work correctly inside the constrained container
- `windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact` parameter added to `PreviewContent`; forwarded from `CreateVideoScreen` call site
- `import androidx.compose.foundation.layout.fillMaxHeight` added (only new import)

**Not changed:** ExoPlayer creation, `PlayerView` configuration, playback, `repeatMode`, `volume`, `DisposableEffect` (player release), Share intent, Done navigation, Delete dialog and delete logic, `CreateVideoViewModel`, state machine, `RenderingContent`, `ConfiguringContent`, `VideoLoadingPreview`, `VideoModePreview`, export pipeline, navigation, strings, Manifest, Permissions, Gradle.

**Verified test state (Block 5 — Completed 2026-06-18):**

| Test | Status |
| --- | --- |
| `assembleDebug` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | BUILD SUCCESSFUL |
| `connectedDebugAndroidTest` (full suite) | One unrelated `CameraControlsOverlayTest` failure: `No compose hierarchies found in the app` — pre-existing Compose hierarchy flake; isolated rerun PASSED; not a Block 5 regression |

No open Block 5 tasks remain.

---

### Block 9d — Video Mode Label Rename + Icon Revert (2026-06-17)

**Geänderte Dateien:**

| Datei | Änderung |
|---|---|
| `values/strings.xml` | `create_video_mode_compare_slider`: "Compare" → "Slider"; `create_video_mode_before_after`: "Before &amp; After" → "Fade" |
| `values-de/strings.xml` | identische Änderungen; "Flash" in beiden Sprachen unverändert |
| `ui/video/CreateVideoScreen.kt` | `modeItems` zurück auf einfache `SameViewSegmentItem(label)`; Icon-Imports (`CompareArrows`, `Timeline`, `FlashOn`) entfernt |
| `ui/settings/SettingsComponents.kt` | icon != null Zweig: `labelMedium` → `labelLarge`; alle Segment-Controls wieder typografisch einheitlich |
| `docs/VIDEO_EXPORT_V1.md` | §7.3 Wizard Layout; §24.0 Mode-Label-Ausnahmen; §24.1 String-Werte aktualisiert |

**Neue UI-Terminologie:**

| Modus | Bisheriger Label | Neuer Label | Interner Enum |
|---|---|---|---|
| Compare Slider | Compare | **Slider** | `VideoMode.COMPARE_SLIDER` (unverändert) |
| Before & After | Before & After | **Fade** | `VideoMode.BEFORE_AFTER` (unverändert) |
| Flash | Flash | **Flash** | `VideoMode.FLASH` (unverändert) |

**Teststatus (2026-06-17):**

| Test | Status |
|---|---|
| `testDebugUnitTest` | PASSED |
| `assembleDebug` | BUILD SUCCESSFUL |
| `assembleRelease` | BUILD SUCCESSFUL |

---

### Block 9c — Branding Default OFF + labelMedium Icon-Modus (2026-06-17)

**Geänderte Dateien:**

| Datei | Änderung |
|---|---|
| `ui/settings/SettingsRepository.kt` | `prefs[Keys.BRANDING_ENABLED] ?: true` → `?: false`; Kommentar aktualisiert |
| `ui/settings/SettingsComponents.kt` | Im `icon != null`-Zweig: `labelLarge` → `labelMedium` (12sp); `icon == null`-Zweig unverändert (labelLarge) |
| `docs/VIDEO_EXPORT_V1.md` | FD-15: "enabled by default" → "disabled by default"; §13.3 Rationale aktualisiert |
| `docs/implementation_plans/historic/VIDEO_EXPORT_IMPLEMENTATION_PLAN.md` | Block 9c in Progress Tracking ergänzt |

**Branding Default OFF:** Unter dem neuen Branding-Timing-Modell (additive Endcard) würde Default-ON dazu führen, dass Erstnutzer ein Video erhalten, das länger ist als die gewählte Dauer. Default-OFF entspricht der Nutzererwartung.

**labelMedium (12sp) für Icon-Segmente:** Löst den "Before & After"-Umbruch bei Standard-Scale (1.0×). Nur im `icon != null`-Zweig; Format/Duration/Quality-Segmente (ohne Icon) weiterhin `labelLarge` (14sp).

**Teststatus (2026-06-17):**

| Test | Status |
|---|---|
| `testDebugUnitTest` | PASSED |
| `assembleDebug` | BUILD SUCCESSFUL |
| `assembleRelease` | BUILD SUCCESSFUL |
---

### Session Branding V1 — Blocks 0–4 (2026-07-XX)

Full specification: `SESSION_BRANDING_V1.md`
Implementation plan: `implementation_plans/historic/SESSION_BRANDING_IMPLEMENTATION_PLAN.md`

**Block 0 — Metadata v6 Foundation:** `SessionBranding`, `SessionBrandingMeta` data classes; `METADATA_VERSION` bumped to 6; `SUPPORTED_VERSIONS` extended to include 6; v6 scanner validation for `files.brandingHandle`; `ScannedSession.branding: SessionBranding?`. 684/684 unit tests, 647/647 instrumentation tests.

**Block 1 — Image Assets + Normalizer:** 6 custom VectorDrawables; `BrandingNormalizer` (Bitmap → 512×512 RGBA PNG, metadata-clean by construction); `BuiltinBrandingSymbol` enum; `BuiltinSymbolRenderer`. 10/10 + 9/9 instrumentation tests.

**Block 2 — GlobalBrandingRepository:** File-based global branding at `filesDir/branding/handle.png` + `handle-meta.json`. `hasBranding()` requires both files present and meta parseable. Atomicity via `Files.move(REPLACE_EXISTING)`.

**Architecture note (Block 2→4 change):** `GlobalBrandingRepository.setBranding()` and `removeBranding()` are suspend but perform **no internal IO dispatching**. The caller is responsible for dispatching to an IO context. Changed in Block 4 for JVM testability. Threading contract documented in `GlobalBrandingRepository` KDoc.

**Block 3 — Session Branding Storage:** `SessionStorage` extended with `updateSessionBranding()`, `removeSessionBranding()`, `copyGlobalBrandingToSession()`; `saveSession()` auto-copies global branding on session creation (fail-soft). 20/20 instrumentation tests.

**Block 4 — Global Settings Branding UI:** `SettingsModule` provides `GlobalBrandingRepository` as Hilt singleton; `SettingsViewModel` injected with repository + branding functions; `SettingsScreen` shows "Default branding for new sessions" card. `BuiltinSymbolPickerDialog` is `internal` for reuse. 689/689 unit tests, 35/35 `SettingsScreenTest`.

**Block 5 — Edit Session Branding Card:** `EditSessionViewModel` extended with `GlobalBrandingRepository` injection, `hasBranding`/`hasGlobalBranding` StateFlows, `brandingError` SharedFlow (separate from `events` to avoid `MainActivity` interference), and four immediate-write branding functions: `onImageUriSelectedForBranding()`, `onSetSessionBrandingFromSymbol()`, `onRemoveSessionBranding()`, `onCopyFromGlobalBranding()`. Branding changes do NOT set `isDirty` and are NOT reverted by "Discard changes". New Branding card added between Current photo card and Location card in `EditSessionScreen`. Productional rule enforced: no Global-Branding fallback after removal. 696/696 unit tests, 36/36 `EditSessionScreenTest` on SM-S911B (Android 16).

**Block 6 — Share Comparison Image Integration:** New `BrandingHandleRenderer` (`internal object`, `branding` package, no Compose dependencies, reusable for future Video Export). `ShareRenderConfig` extended with `useBranding: Boolean = false`. `SliderRenderStrategy` receives `brandingBitmap: Bitmap? = null`; standard handle extracted to `drawStandardHandle()`; branding path calls `BrandingHandleRenderer.draw()` at 1.5× standard diameter. `ShareImageRenderer` decodes `branding-handle.png` from `sessionDir` (never from global branding). `ShareComparisonViewModel` gains `hasBranding`/`useBranding` StateFlows, injectable `brandingFileChecker`, `onToggleUseBranding()`. "Use branding" toggle placed in Style card between segment control and preview (Option B: toggle always active; Side-by-side shows informational note). `ShareComparisonPreview` renders branding handle via `AsyncImage` + Compose DrawScope (matching export semantics). 709/709 unit tests, 12/12 `ShareComparisonScreenTest`, 11/11 `ShareImageRendererInstrumentedTest` (includes I-12 pixel-difference test) on SM-S911B.

**Block 7 — Final Verification:** `assembleRelease` BUILD SUCCESSFUL. Full `connectedDebugAndroidTest` 711/711 PASSED on SM-S911B (Android 16) — 0 failures, 0 regressions. TODO comment `// TODO VIDEO_BRANDING: Check sessionDir for branding-handle.png...` added to `CompareSliderRenderEngine.kt` per `SESSION_BRANDING_V1.md §16`. I-12 pixel-difference test implemented and verified.

**Block 8 — Documentation Updates:** `SESSION_METADATA_V1.md` §5.3 + §6.5 + §6.7 updated to v6. `SESSION_BACKUP_EXPORT_V1.md` §4.2 v6 structure added. `SESSION_METADATA_EDITOR_V1.md` §21 Branding card added. `SETTINGS_UX_V1.md` §5 + §11 updated. `SHARE_COMPARISON_IMAGE_V1.md` FD-17 extended + FD-18 added. `VIDEO_EXPORT_V1.md` §33 Future Compatibility added. `CLAUDE_PROJECT_INSTRUCTION.md` Session Storage section updated to schema v6 and `SUPPORTED_VERSIONS` {2,3,4,5,6}. `IMPLEMENTATION_NOTES.md` Blocks 5–8 documented.

---

### Session Branding V2 — UX Rework, Blocks 1–5 (2026-07-XX)

Full UX specification: `SESSION_BRANDING_V2_UX_REWORK.md` (approved, final)
Implementation plan: `implementation_plans/historic/SESSION_BRANDING_V2_IMPLEMENTATION_PLAN.md` (approved, final)

Technical backend (storage, metadata v6, backup, normalization, rendering) is unchanged from V1. All V2 changes are UI-layer only.

**Block 1 — Settings layout and wording:** "Default branding for new sessions" card replaced by "Your logo" card. New section title, always-visible description, placeholder circle in empty state, renamed action labels ("Choose photo", "Use a symbol", "Remove logo"). `settings_logo_*` string keys added; deprecated `settings_branding_*` keys retained for Block 5 cleanup. 46/46 `SettingsScreenTest` PASSED on SM-S911B.

**Block 2 — Symbol BottomSheet migration:** `BuiltinSymbolPickerDialog` (AlertDialog) replaced by `BrandingSymbolPickerSheet` (ModalBottomSheet, package `ui.branding`). Symbol cells render 56 dp handle-style previews (SameViewAccent ring, #F5F7FA fill, symbol at 72%) — full-ring style consistent with `BrandingPreviewCircle`. Both callers (`SettingsScreen`, `EditSessionScreen`) migrated. `performScrollTo()` removed from sheet tests (ModalBottomSheet has no standard scroll parent). 6/6 `BrandingSymbolPickerSheetTest`, 46/46 `SettingsScreenTest`, 40/40 `EditSessionScreenTest` PASSED.

**Block 3 — Edit Session logo card redesign:** V1 asymmetric Branding card replaced by V2 symmetric Logo card. "Change branding" (image-only) removed. "Choose photo" and "Use a symbol" always visible in both empty and populated states — direct replacement without remove-first. "Copy from default branding" renamed "Use your default logo". Card title: "Logo". Always-visible description. Placeholder circle in empty state. Type label "Symbol: [Name]" for builtin; no label for photo logos (product decision). `EditSessionViewModel` extended with `sessionLogoType: StateFlow<String?>` and `sessionLogoBuiltinId: StateFlow<String?>`, updated after all four write paths. Fix: `sessionLogoBuiltinId` required local val capture before null-check in Compose lambda (Kotlin smart-cast limitation on StateFlow delegates). 54/54 `EditSessionScreenTest` PASSED; all unit tests green.

**Block 4 — Share Comparison logo card extraction:** V1 "Use branding" toggle inside Style card replaced by V2 "Logo on handle" `SettingsCard` between Style and Information cards. Card absent when Side-by-side selected (card removed entirely, no disabled state, no disclaimer). Toggle label renamed "Show logo". Empty state: placeholder circle + informational text + "Add one in Edit session." — no editing actions. Populated state: `BrandingPreviewCircle` at 40% alpha when toggle OFF. `sessionBrandingFile` derived in Screen from `sessionDir` (no ViewModel change). `ShareComparisonScreenStub` in tests fully replaced to match V2 structure. Two missing imports (`Alignment`, `Spacer`) fixed after initial compile. 16/16 `ShareComparisonScreenTest` PASSED.

**Block 5 — String cleanup and BuiltinSymbolPickerDialog removal:** `BuiltinSymbolPickerDialog` composable (56 lines) removed from `SettingsScreen.kt`. 11 now-unused imports removed (kept `AlertDialog` — still used by location permission rationale dialog). 16 deprecated string keys removed from `strings.xml` and `strings-de.xml` (7 `settings_branding_*` including `settings_branding_builtin_symbols_title`, 3 `share_comparison_branding_*`, 6 `edit_session_branding_*`). Test `logoCard_noSliderOnlyHint_visible()` updated to use hardcoded literal `"Only applied to slider style"` before string key removal. `assembleDebug` BUILD SUCCESSFUL — compile-time verification of zero remaining deprecated key references. Full `connectedDebugAndroidTest` 751/751 PASSED on SM-S911B (Android 16).

**V2 Final State — Test counts post-V2:** `testDebugUnitTest` all green. `connectedDebugAndroidTest` 751/751 PASSED. Net new instrumentation tests: +16 `BrandingSymbolPickerSheetTest` (new class), +38 `EditSessionScreenTest` (14 added + block updates), +4 `ShareComparisonScreenTest` (net new logo tests).

---

### Session Branding V2 — Logo Card UX Refinement (2026-06-26)

Three approved UX refinements applied to the Logo card in `ShareComparisonScreen`. No changes to persistence, export renderer, or SettingsScreen.

**Issue 1 — "Use default logo" meaningful visibility:**

`_isUsingGlobalDefault: MutableStateFlow<Boolean>` added to `ShareComparisonViewModel`. Tracks whether the current session logo originated from the global default and has not been replaced by a user action. The button is now visible only when `hasGlobalBranding && !isUsingGlobalDefault`.

Derivation analysis: pure metadata comparison works reliably for builtin symbols (`type = "builtin"` + `builtinId` match). Photo logos (`type = "image"`) have no stored content hash — metadata comparison is not sufficient. A dedicated StateFlow is the correct approach: it tracks operational intent rather than trying to infer it from file content. Metadata comparison is used as a best-effort initialiser when the session already has branding on screen open (covers the common builtin case when re-opening a previously branded session).

State transitions:

- `true`: auto-copy fires at init; `onUseDefaultLogo()` succeeds; session builtin meta matches global builtin meta at init.
- `false`: `onImageUriSelectedForBranding()` succeeds; `onSetSessionBrandingFromSymbol()` succeeds; `onRemoveSessionBranding()` succeeds; session meta is photo or mismatches global at init.

`ShareMetadataSnapshot` extended with `brandingMeta: SessionBrandingMeta? = null`. `readMetadata()` reads `metadata.json → branding` block to populate it. No new storage writes.

**Issue 2 — Logo replacement must not modify Show logo toggle:**

"Choose photo", "Use a symbol", and "Use default logo" now only set `_useBranding = true` when adding the **first** logo (transitioning from no-logo to has-logo state). When replacing an existing logo, the toggle is left unchanged. `wasEmpty = _previewBrandingBitmap.value == null` check applied before setting the new bitmap in all three write paths.

**Issue 4 — Preview row spacing:**

`Spacer(modifier = Modifier.width(16.dp))` added between the preview circle Box and the Show logo switch row Box in the populated Zone 1 of `ShareComparisonScreen`. Matches the empty-state spacer at 16 dp to account for the heavier visual weight of the switch widget.

**Files changed:** `ShareComparisonViewModel.kt`, `ShareComparisonScreen.kt`

**Tests — new unit tests in `ShareComparisonViewModelTest` (+19):** `isUsingGlobalDefault_false_initially`, `isUsingGlobalDefault_true_afterAutoCopy`, `isUsingGlobalDefault_true_whenExistingBuiltinMatchesGlobal`, `isUsingGlobalDefault_false_whenExistingBuiltinDiffersFromGlobal`, `isUsingGlobalDefault_false_afterPhotoSelected`, `isUsingGlobalDefault_false_afterSymbolSelected`, `isUsingGlobalDefault_true_afterUseDefault`, `isUsingGlobalDefault_false_afterRemove`, `choosePhoto_setsUseBrandingTrue_whenFirstLogoAdded`, `choosePhoto_doesNotModifyUseBranding_whenAlreadyEnabled`, `chooseSymbol_setsUseBrandingTrue_whenFirstLogoAdded`, `chooseSymbol_doesNotModifyUseBranding_whenAlreadyEnabled`, `useDefaultLogo_setsUseBrandingTrue_whenFirstLogoAdded`, `useDefaultLogo_doesNotModifyUseBranding_whenAlreadyEnabled`, `removeLogoAvailable_whenShowLogoOff`, plus 4 existing `brandingVersion` and `sessionBrandingChanged` tests that continue to cover the write paths (no changes needed).

**Tests — new instrumentation tests in `ShareComparisonScreenTest` (+8):** `useDefaultLogo_hidden_whenAlreadyUsingDefault`, `useDefaultLogo_visible_whenGlobalExistsAndSessionDiffers`, `useDefaultLogo_absent_inEmptyState_whenAlreadyUsingDefault`, `removeLogoStillAvailable_whenShowLogoOff`, `useDefaultLogo_hiddenImmediately_afterUseDefault`, `useDefaultLogo_visible_afterChoosingPhoto`, `useDefaultLogo_visible_afterChoosingSymbol`, `useDefaultLogo_visibility_updatesWithoutScreenReopen`.

**Existing tests updated:** `ShareComparisonScreenStub` — added `isUsingGlobalDefault: Boolean = false` parameter; button visibility changed from `hasGlobalBranding` to `hasGlobalBranding && !isUsingGlobalDefault`; 16 dp spacer added to populated Zone 1. `launch()` helper extended with `isUsingGlobalDefault: Boolean = false`. Pre-existing tests `logoCard_emptyState_useDefaultLogo_visible_whenGlobalExists` and `logoCard_populated_useDefaultLogo_visible_whenGlobalExists` continue to pass because `isUsingGlobalDefault` defaults to `false`.

**Test results (2026-06-26):**

- `testDebugUnitTest` — BUILD SUCCESSFUL; 740/740 PASSED (was 669; +71 net new `ShareComparisonViewModelTest`)
- `ShareComparisonScreenTest` (`connectedDebugAndroidTest`) — **36/36 PASSED** on SM-S911B (Android 16); was 16+4=20 after V2 Blocks 4–5; +16 net new tests in this session

**Issue 3 (no change):** `onRemoveSessionBranding()` behavior unchanged. "Remove logo" is always available when `hasBranding == true`, regardless of `useBranding`. Confirmed by `removeLogoAvailable_whenShowLogoOff` regression guard. Manual device verification still required for spacing (Issue 4) and for the live button visibility update (Issue 1 in the production ViewModel flow).

---

### Reference Marker Drag Loupe — V1 (2026-06-30)

Full specification: `REFERENCE_MARKER_DRAG_LOUPE_V1.md` (Rev 2)
Implementation plan: `implementation_plans/REFERENCE_MARKER_DRAG_LOUPE_V1_IMPLEMENTATION_PLAN.md`

**Files changed:**

- `app/src/main/java/com/isardomains/sameview/ui/camera/ReferenceMarkerOverlay.kt` — loupe constants, bitmap loading/recycling via `LaunchedEffect`, drag state pre-initialization, loupe Box with Canvas crop rendering
- `app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt` — added `referenceUri = referenceUri` at `ReferenceMarkerOverlay` call site
- `app/src/androidTest/java/com/isardomains/sameview/ui/camera/ReferenceMarkersOverlayUITest.kt` — 9 new loupe tests + `setLoupeOverlayContent()` helper

**Key implementation decisions:**

- Loupe local state only: `isDragging`, `draggingMarkerNormalizedPos`, `loupeBitmap` are Compose snapshot state scoped to `ReferenceMarkerOverlay` — no ViewModel state, no persistence, no session/export impact
- `draggingMarkerNormalizedPos` is pre-initialized to `Pair(nearestMarker.normalizedX, nearestMarker.normalizedY)` before the inner drag loop starts, so the loupe appears immediately when the drag is classified; the gesture classification loop consumes the slop-qualifying move event and the inner loop starts with no queued events
- Bitmap loaded proactively on `LaunchedEffect(referenceUri)` on IO dispatcher using `inSampleSize` doubling to ≤ 1024 px max dimension; EXIF orientation applied once via `android.graphics.Matrix`; bitmap recycled on `LaunchedEffect(isEditModeActive)` when edit mode exits (OQ-3)
- `android.graphics.Canvas.drawBitmap(Bitmap, Rect, RectF, Paint?)` — integer `Rect` for src (no `(Bitmap, RectF, RectF, Paint?)` overload exists in Android Canvas)
- Loupe clamped inside viewport: default above marker (`markerScreen.y - radius - fingerOffset`), clamped to `[radius .. vH - radius - loupeDoneAreaPx]` × `[radius .. vW - radius]`; fallback below marker if above still overlaps
- 88 dp Done-area clearance matches spec §7; residual risk: unusual system bar insets on some devices could reduce the effective safe zone below this value

**Test implementation notes:**

- `performTouchInput { down(Offset(x,y)); moveTo(Offset(x,y)) }` then `waitForIdle()` classifies the drag; a second `performTouchInput { moveTo(Offset(x,y)) }` is needed to drive the inner drag loop (used in `loupe_doesNotModifyMarkerCoordinate`)
- Clamping tests compute marker screen position as `vH/2 + min(vW,vH) * (normY - 0.5)` — not `vH * normY` — because `SHOW_FULL_IMAGE` with a square image centers the image vertically in a tall viewport; using `vH * normY` places the DOWN event ~600 px away from the actual marker and `findNearestMarker` returns null
- Move deltas of 50 px reliably exceed the device's `viewConfiguration.touchSlop` (~8 dp × 3.5 density = 28 px on SM-S911B)

**Latest verified test state (2026-06-30):**

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL
- `ReferenceMarkersOverlayUITest` focused run — **13/13 PASSED** on SM-S911B (Android 16): 4 pre-existing + 9 new loupe tests
- `connectedDebugAndroidTest` full suite — **BUILD SUCCESSFUL** (one run); one flaky failure in `SettingsScreenTest.brandingCard_removeLogo_visibleWhenBrandingSet` observed on second run — pre-existing flaky test unrelated to this feature (see prior notes on Samsung IS_PENDING/branding test flakiness)
- `assembleRelease` — BUILD SUCCESSFUL

**Manual validation still required:** visual loupe appearance, crop geometry at various zoom levels, edge-clamping on a physical device, Done-area clearance on real navigation bar insets.

---

### Reference Markers — Visible Image Rect Boundary Fix (2026-07-01)

Full specification: `ALIGNMENT_POINTS_V1.md` (Rev 9), `REFERENCE_MARKER_DRAG_LOUPE_V1.md` (Rev 3)
Implementation plan: `implementation_plans/REFERENCE_MARKER_DRAG_LOUPE_V1_IMPLEMENTATION_PLAN.md` (Rev 2)

**Problem:** Three UI elements incorrectly used the full viewport as their boundary reference. The edit-mode border, empty-state hint, and drag loupe clamping all followed the viewport, not the visible reference-image rectangle. In SHOW_FULL_IMAGE mode with mismatched aspect ratios (letterboxing/pillarboxing), these elements extended into the empty letterbox zones.

**Fix:** All three elements now follow the **transformed visible reference-image rectangle** — the image rect after applying `displayMode`, `overlayScale`, `overlayOffsetX/Y`, image dimensions, and viewport dimensions, then clipped to the viewport.

**Files changed:**

- `app/src/main/java/com/isardomains/sameview/ui/camera/ReferenceMarkerOverlay.kt` — added `VisibleImageRect` data class and `computeVisibleImageRect()` helper; updated loupe clamping to image-rect-based (with per-axis viewport fallback when image rect < loupe diameter); updated empty-state hint from `fillMaxSize().Center` to `absoluteOffset` + `size` within image rect
- `app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt` — replaced old `MarkerEditBorder` (fixed aspect ratio border) with new implementation using `onSizeChanged` + `computeVisibleImageRect` + `absoluteOffset`; added `import androidx.compose.foundation.layout.absoluteOffset`
- `app/src/androidTest/java/com/isardomains/sameview/ui/camera/ReferenceMarkersOverlayUITest.kt` — updated `setBorderContent`, `setOverlayContent`, `setLoupeOverlayContent` helpers with optional metadata/displayMode/offset/scale params; removed `isLandscape` param; added 5 new tests

**Key implementation decisions:**

- `computeVisibleImageRect()` uses identical `baseScale` formula as `normalizedToScreen()`: `max(vW/iW, vH/iH)` for COMPARE_WITH_PREVIEW, `min(vW/iW, vH/iH)` for SHOW_FULL_IMAGE — single source of truth
- Per-axis viewport fallback for loupe clamping: when `imageRectW < loupeDiameterPx` on an axis, fall back to viewport bounds on that axis (handles extreme zoom-out edge case)
- Done-area reservation remains viewport-bottom-relative: `minOf(clampBottom, vH - loupeDoneAreaPx)` — not image-rect-bottom-relative
- `MarkerEditBorder` uses `onSizeChanged` + `absoluteOffset` pattern (not Canvas) so `boundsInRoot` in tests reflects the actual image rect position for bound assertions
- Empty-state hint has a fallback to `fillMaxSize` center when `metadata == null` or viewport not yet measured

**5 new tests added:**

1. `border_framesVisibleImageRect_whenLetterboxed` — wide 2:1 image in SHOW_FULL_IMAGE; asserts border top > 0 and bottom < viewport height
2. `border_matchesViewport_whenImageFillsViewport` — square image in COMPARE_WITH_PREVIEW; asserts border equals viewport bounds (±2 px)
3. `hint_centeredInsideVisibleImageRect` — wide 2:1 image in SHOW_FULL_IMAGE; asserts hint text stays within computed image rect top/bottom
4. `loupe_clampedToVisibleImageRect` — 1000×1000 square image in SHOW_FULL_IMAGE portrait (letterboxed); drag near image top downward; asserts loupe top ≥ imageRectTop (not 0)
5. `loupe_fallsBackToViewport_whenImageRectSmallerThanLoupe` — overlayScale=0.05 (image rect ~54 px, far smaller than loupe diameter ~360 px); asserts loupe stays within viewport

**Latest verified test state (2026-07-01):**

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL
- `ReferenceMarkersOverlayUITest` focused run — **18/18 PASSED** on SM-S911B (Android 16): 13 pre-existing + 5 new boundary-fix tests
- `connectedDebugAndroidTest` full suite — **802/802 PASSED** on SM-S911B (Android 16)
- `assembleRelease` — BUILD SUCCESSFUL

**Manual validation still required:** visual border alignment in SHOW_FULL_IMAGE letterboxed mode, empty-state hint position with mismatched aspect ratios, loupe clamping in pillarboxed layout on a physical device.

---

### Reference Markers — MarkerEditBorder Viewport-Coordinate Fix (2026-07-01)

**Problem:** `MarkerEditBorder` call site in `CameraScreen.kt` passed only `Modifier.align(Alignment.Center)` — no size constraint. Inside `MarkerEditBorder` the outer Box does `.fillMaxSize()`, so `onSizeChanged` measured the full CameraScreen inner container (not the constrained 9:16/16:9 marker viewport). Since `overlayOffsetX/Y` are normalized to the constrained viewport, `computeVisibleImageRect` received the wrong `viewportWidth/Height`, causing the translation (`translationX = overlayOffsetX * viewportWidth`) to use the wrong scale factor. The border was positioned and sized correctly only when both offsets were zero.

**Fix (single call site, `CameraScreen.kt`):** Changed the `MarkerEditBorder` call site modifier from `Modifier.align(Alignment.Center)` to the same orientation-conditional modifier used by `ReferenceMarkerOverlay`:

- Portrait: `Modifier.fillMaxWidth().aspectRatio(9f / 16f).align(Alignment.Center)`
- Landscape: `Modifier.fillMaxHeight().aspectRatio(16f / 9f).align(Alignment.Center)`

`computeVisibleImageRect`, `normalizedToScreen()`, and `screenToNormalized()` are unchanged.

**Files changed:**

- `app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt` — call site modifier for `MarkerEditBorder` (Layer 2.7) now matches `ReferenceMarkerOverlay` viewport constraint
- `app/src/androidTest/java/com/isardomains/sameview/ui/camera/ReferenceMarkersOverlayUITest.kt` — `setBorderContent` helper now wraps `MarkerEditBorder` in the same constrained Box (tagged `test_viewport`); updated `border_framesVisibleImageRect_whenLetterboxed` (non-zero `overlayOffsetY`, tight position assertion using constrained viewport height); updated `border_matchesViewport_whenImageFillsViewport` (compares against `test_viewport` bounds, not root); added 3 new regression tests

**3 new tests:**

1. `border_neverExceedsViewport_whenImageLargerThanViewport` — large image in COMPARE_WITH_PREVIEW; border must not exceed constrained viewport bounds
2. `border_topAndBottom_correctForLandscapeImageWithNonZeroOffsetY` — 16:9 image in SHOW_FULL_IMAGE portrait with `overlayOffsetY = -0.1`; border top/bottom must match position computed from constrained viewport height
3. `border_recomputesCorrectly_inLandscapeViewport` — 16:9 image in COMPARE_WITH_PREVIEW in landscape 16:9 viewport; border must fill the landscape viewport end-to-end

**Latest verified test state (2026-07-01):**

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL
- `ReferenceMarkersOverlayUITest` focused run — **21/21 PASSED** on SM-S911B (Android 16): 18 prior tests + 3 new viewport-coordinate-fix tests
- `connectedDebugAndroidTest` full suite — **805/805 PASSED** on SM-S911B (Android 16)
- `assembleRelease` — BUILD SUCCESSFUL

**Manual validation still required:** border alignment with non-zero overlay offset on a physical device (three screenshot cases: letterboxed portrait, pillarboxed landscape, COMPARE_WITH_PREVIEW with panned image).

---

### Guide Tips System — Blocks A–H complete (2026-07-06)

Full specification: `GUIDE_TIPS_UX_V1.md`
Implementation plan: `implementation_plans/GUIDE_TIPS_IMPLEMENTATION_PLAN.md`

**Completed blocks:** A (Tip Model Cleanup), B (Card Visual Redesign), C (Placement Algorithm Update), D (Camera Screen Integration), E (Compare Screen Integration), F (Library Screen Integration), G (GuideTipController Extension), H (Final Verification)

**Active tips:**

| Tip ID | Scope | Completion Event |
|---|---|---|
| REFERENCE | CAMERA | Successful reference image import (`CameraViewModel.onReferenceImageSelected`) |
| SHARE | COMPARE | Export dropdown opened (`showExportMenu = true`) |
| EDIT_SESSION | COMPARE | Navigation to Edit Session screen (`addOnDestinationChangedListener` in `MainActivity`) |
| OPEN_COMPARISON | LIBRARY | First session tile tap in `CompareLibraryScreen` (before navigation) |
| MULTI_SELECT | LIBRARY | Multi-select activated via long-press in `CompareLibraryScreen` |

**Prerequisite enforcement:** EDIT_SESSION does not appear until SHARE is marked seen. MULTI_SELECT does not appear until OPEN_COMPARISON is marked seen.

**Test stability note:** A race in `CompareGuideTipIntegrationTest` was stabilized by signal-relative synchronization — `guide_tip_host` is awaited before asserting on `guide_tip_card`. No production code was changed.

**Static verification (Block H — 2026-07-06):**

- Deleted Tip IDs (ALIGN, COMPARE, HISTORY, EXPORT, MARKER, GPS): 0 references in source — clean
- Deleted Anchor Keys (ALIGN_CONTROLS, COMPARE_ACTION, HISTORY_ACTION, EXPORT_ACTION, MARKER_ACTION, GPS_CHIP): 0 references in source — clean
- `CameraGuideTipIntegrationTest.kt`: 4 tests, all Reference-scoped; no COMPARE/MARKER/GPS tests — clean
- `LibraryGuideTipIntegrationTest.kt`: 7 tests confirmed in source
- `guide_tip_got_it`: present in `strings.xml`/`strings-de.xml` as a dead resource (0 code references, no compile impact); pending one-line cleanup in a future maintenance pass

**Latest verified test state (Block H closure — 2026-07-06):**

| Test | Status |
|---|---|
| `LibraryGuideTipIntegrationTest` | 7/7 PASSED |
| `CompareLibraryScreenTest` | 74/74 PASSED |
| `CompareGuideTipIntegrationTest` | 4/4 PASSED |
| `testDebugUnitTest` | PASSED |
| `connectedDebugAndroidTest` | 868/868 PASSED |
| `assembleDebug` | BUILD SUCCESSFUL |

No open Guide Tips implementation tasks remain.

---

### DeinWackelbild Integration — Block 1 & Block 2 complete (2026-08-28)

Full specification: `deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
Implementation plan: `deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

**Block 1 completed** — Compare menu entry only: `CompareScreen`'s Export dropdown gained a divider and a third item ("Wackelbild erstellen" / `export_menu_create_wackelbild`, test tag `compare_screen_export_wackelbild_item`), wired to a new optional `onCreateWackelbild` callback parameter. No navigation destination existed yet at this point.

**Block 2 completed** — real destination and navigation wiring:

- `MainActivity.kt`: `ROUTE_WACKELBILD`/`ARG_WACKELBILD_SESSION_ID`/`ROUTE_WACKELBILD_WITH_ARGS` constants and `wackelbildRoute(sessionId)` builder, modeled exactly on the existing `shareComparisonRoute` pattern; `onCreateWackelbild` now navigates to `wackelbild/{sessionId}`.
- New `WackelbildScreen.kt`/`WackelbildViewModel.kt` in package `com.isardomains.sameview.ui.wackelbild`. The Hilt-backed `WackelbildScreen` is a thin wrapper delegating to an `internal` stateless `WackelbildScreenContent(referenceFile: File, onBack, windowWidthSizeClass)`, so instrumentation tests exercise the exact production UI without needing a Hilt-provided `SavedStateHandle`.
- `WackelbildViewModel` resolves only `sessionId` (from `SavedStateHandle`) and `referenceFile = File(context.filesDir, "sessions/$sessionId/reference.jpg")` — no `metadata.json` parsing, no persisted/derived aspect-ratio state.
- The local preview is sized from the **intrinsic dimensions of the successfully decoded `reference.jpg`** (via Coil's `AsyncImagePainter.state`), not from session metadata and not from a hardcoded fallback ratio; capped at a moderate maximum height so the preview never fills the whole screen, using the same behavioral (not code-shared) pattern as `ShareComparisonPreview`'s aspect-ratio-driven, height-capped sizing.
- `ContentScale.Fit` is used throughout — no additional crop is ever introduced beyond what is already baked into the persisted `reference.jpg`.
- A single local fallback state (`wackelbild_preview_fallback`) covers both a missing `reference.jpg` and a present-but-undecodable one, driven uniformly by Coil's `AsyncImagePainter.State.Error` (plus a defensive check that a `Success` state's intrinsic dimensions are actually positive). The fallback keeps the normal TopAppBar/Back available; it performs no file repair and no network request.
- Standard `Scaffold`/`TopAppBar`/Back shell, cloned from `ShareComparisonScreen`; `widthIn(max = 680.dp)` on Expanded, matching the existing responsive pattern.

**Not yet implemented (later blocks):** tilt/swipe interaction, date overlay, HQ print reconstruction, network/API handoff, Custom Tab, temp-file lifecycle. Opening the Wackelbild screen in Block 2 performs no network request — no networking dependency exists in the app yet.

**Verification:**

- `testDebugUnitTest` — BUILD SUCCESSFUL
- `assembleDebug` — BUILD SUCCESSFUL
- `pixel2Api29` Managed Device, `WackelbildScreenTest` (new, 12 tests) — see final Block 2 report for pass/fail detail
- No unrelated existing test was modified

**Block 3 completed** — local tilt/swipe interaction:

- `WackelbildViewModel` now also resolves `captureFile = File(context.filesDir, "sessions/$sessionId/capture.jpg")` and owns a `visibleImage: StateFlow<WackelbildImageSide>` (`REFERENCE`/`CAPTURE`, always starting at `REFERENCE`). The existing single local fallback (`wackelbild_preview_fallback`) now covers a missing/undecodable `reference.jpg` **or** `capture.jpg` uniformly — no degraded one-image mode.
- New `TiltProvider.kt`, a narrow duplicate of `CompassProvider` (`Sensor.TYPE_ROTATION_VECTOR`, no runtime permission, identical display-rotation remap table), reading `orientationAngles[2]` (roll) instead of `orientationAngles[0]` (azimuth). `CompassProvider.kt` is unchanged.
- New `TiltHysteresisStateMachine.kt` — a pure, Android-independent state machine (`NEUTRAL`/`TOWARD_REFERENCE`/`TOWARD_CAPTURE`) with an angle-wrap-safe delta, a switch threshold, and a re-arm band, so ordinary hand jitter never causes repeated switching. Placeholder tuning constants (`THRESHOLD_DEGREES = 9f`, `REARM_DEGREES = 6f`) are explicitly marked `// TODO(real-device tuning)` and are not final.
- Neutral posture is calibrated by `WackelbildViewModel` (not `TiltProvider`) from the first sensor reading after `onScreenActive()`; it is cleared on `onScreenInactive()`/`onScreenLeft()` and recalibrated on every subsequent resume — always relative to current device posture, never persisted.
- Swipe/sensor arbitration follows the exact required rule: a manual swipe (or the accessibility toggle) sets the image immediately and arms `swipeOverrideActive`; the hysteresis machine keeps observing underneath but its output is ignored until an explicit `NEUTRAL` transition is observed (`neutralObservedSinceOverride`), and only the *next* threshold-crossing transition after that clears the override and hands control back to the sensor — `SWIPE -> neutral/re-arm observed -> new threshold crossing -> sensor resumes`. An unchanged tilt reading at swipe time can never undo the manual choice.
- Horizontal swipe is implemented in `WackelbildScreen.kt` on the preview region only, via `detectDragGestures` accumulating per-axis distance and only consuming/firing once a gesture is clearly horizontal past a small threshold; a clearly vertical or ambiguous gesture never toggles.
- The preview is now structurally outside the screen's vertical-scroll container (only the interaction-hint area below it scrolls), resolving swipe/scroll contention without any new axis-arbitration code, per the implementation plan's `ShareComparisonScreen`+`CompareScreen`-composition approach.
- Sensor-unavailable UX: `wackelbild_hint_tilt_title` ("Handy leicht neigen" / "Tilt your phone") when a sensor exists, `wackelbild_hint_swipe_title` ("Über das Bild wischen" / "Swipe over the image") otherwise, same `wackelbild_hint_subtitle` either way. No hardware/error message is ever shown.
- Accessibility: the preview region exposes a custom accessibility action (`wackelbild_accessibility_toggle_action`, reusing the existing `compare_label_reference`/`compare_label_capture` strings for the current-image announcement) so a screen-reader user can switch images without tilting or swiping. No new visible control was added.
- Lifecycle observation (`ON_RESUME`/`ON_PAUSE`/disposal) is owned entirely by the `WackelbildScreen`/`WackelbildScreenContent` composable layer via `DisposableEffect`+`LifecycleEventObserver`, calling `viewModel.onScreenActive()`/`onScreenInactive()`/`onScreenLeft()`. `WackelbildViewModel` has no dependency on `Lifecycle`/`LifecycleOwner`/`LifecycleEventObserver`.
- No `WackelbildPreview.kt` file was created — the preview composable lives inside `WackelbildScreen.kt` as originally scoped.
- No runtime permission was added; no date/HQ/network/order work was performed.

**Not yet implemented (later blocks):** date overlay, HQ print reconstruction, network/API handoff, Custom Tab, temp-file lifecycle.

**Verification:**

- `testDebugUnitTest` — new `TiltProviderTest`, `TiltHysteresisStateMachineTest`, `WackelbildViewModelTest` plus the full existing suite
- `compileDebugAndroidTestKotlin` — compiles against the extended `WackelbildScreenTest`
- `pixel2Api29` Managed Device, extended `WackelbildScreenTest`
- `assembleDebug`
- No unrelated existing test was modified; no unauthorized file was touched

**Real-device validation still required:** tilt "feel" (threshold/re-arm constants), left/right intuitiveness after real device rotation, and swipe/sensor arbitration timing have not been validated on physical hardware in this block — the placeholder constants above are not final.

Block 4 (date overlay) has not been started.
