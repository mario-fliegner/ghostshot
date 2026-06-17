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
- minSdk 29 / targetSdk 35

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

Implemented (Blocks 1–8):

- **Reference GPS EXIF extraction** — passive read from the reference image EXIF via `ReferenceImageMetadataReader`; no permission required; missing GPS is normal and not an error condition
- **Recreation Guidance setting** — single boolean DataStore setting (default OFF); controls all GPS behavior; no sub-settings
- **LocationProvider** — `LocationManager`-based; `GPS_PROVIDER` primary, `NETWORK_PROVIDER` fallback; 8–10 s update interval; foreground-only; no `BACKGROUND_LOCATION`
- **GPS activation conditions** — GPS updates are requested only when all four conditions are simultaneously true: Recreation Guidance ON, location permission granted, reference image has GPS EXIF, CameraScreen is active and in the foreground
- **GpsGuidanceState / GuidanceComputer** — sealed interface (`Hidden`, `Neutral`, `Informative`) with proximity color model (Green/Orange/Red/Neutral), Haversine distance, static North-up bearing; bearing suppressed below ~15–20 m; hysteresis prevents color flickering; small distance/bearing changes below threshold are filtered
- **GpsGuidanceChip** — Composable on CameraScreen; bearing arrow (Canvas), distance label, proximity color accent, "N" label; `AnimatedVisibility` fade transitions; does not overlap Top-Left Hint Zone; `Hidden` state renders no element
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
- Compare supports timestamp, delete, and optional shot title display
- Compare image load failures show a fallback UI and allow back navigation

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
- Multi-select mode remains active after backup; selection is preserved
- Titles are displayed when present
- This is not a general gallery or MediaStore browser

### Video Export

Full specification: `VIDEO_EXPORT_V1.md`
Implementation plan: `VIDEO_EXPORT_IMPLEMENTATION_PLAN.md`

MP4 export infrastructure implemented and verified (Blocks 1–2 Completed). Create Video flow implemented and verified (Blocks 3+4 Completed). High Quality export implemented and verified (Block 5 Completed). Branding endcard implemented and verified (Block 6 Completed). Block 7 Final Verification completed — manual device smoke test passed on SM-S911B (2026-06-10). All Video Export blocks complete:

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
- Each subdirectory contains all four session files: `capture.jpg`, `reference.jpg`, `reference-original.jpg`, `metadata.json`
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
- Each session stores `capture.jpg`, `reference.jpg`, and `metadata.json`
- `metadata.json` schema is **v4** (bumped from v3 in Block A, 2026-06-09): includes `capture.timestampMs` as the canonical capture timestamp inside the `capture` block; `session.createdAtMs` is preserved for backward compatibility and carries the same value
- `SessionScanner` accepts versions {2, 3, 4}; reads `capture.timestampMs` as primary timestamp source, falls back to `session.createdAtMs` for v2/v3 sessions that have no `capture` block
- `metadata.json` contains an `additional` block at session creation with fixed defaults: `isFavorite: false`, `visibility: "private"`, `source: "sameview"` (Block B, 2026-06-09); no UI or update endpoint yet
- `content` block is absent at session creation when no title is present (Block C, 2026-06-09); fixes §12.1 violation where `description: null` and `tags: []` were pre-populated; `updateTitle()` handles absent `content` block correctly via `optJSONObject("content") ?: JSONObject()`
- `reference.date` EXIF auto-population implemented (Block D, 2026-06-09): at session creation, `ReferenceImageMetadataReader` reads EXIF `DateTimeOriginal` and parses it to `"YYYY-MM-DD"`; plausibility filter rejects years < 1826 or > current year; non-lenient `Calendar` rejects invalid month/day values (e.g. month=99, Feb 31); when present, `reference.date`, `reference.dateSource = "exif"`, and `reference.userEdited = false` are written into the `reference` block; when absent or implausible, the three fields are omitted entirely; `ReferenceImageMetadata.exifDateTimeOriginal` is the carrier (trailing default `null`, no call-site changes required)
- `SessionStorage.updateReferenceDate()` implemented (Block E, 2026-06-09): storage-side write function for manual `reference.date` changes; signature `(sessionsRoot, sessionId, date: String?): Boolean`; `date != null` (valid) → writes `reference.date`, `reference.dateSource = "manual"`, `reference.userEdited = true`; `date == null` (remove) → removes `reference.date` and `reference.dateSource`, keeps `reference.userEdited = true`; invalid non-null date returns `false` without modifying anything; `isValidReferenceDate()` validates exact ISO 8601 precision levels ("YYYY", "YYYY-MM", "YYYY-MM-DD") with plausibility filter and non-lenient Calendar check; path traversal protection identical to `updateTitle()`; no UI, no scanner changes
- `SessionStorage.updateLocation()` implemented (Block F, 2026-06-09): storage-side write function for manual user location metadata; signature `(sessionsRoot, sessionId, displayName: String?, city: String?, country: String?): Boolean`; each string normalized via `trim().ifEmpty{null}`; when at least one field non-null → sets `location.displayName`/`city`/`country` (individually) and `location.userEdited = true`; when all fields null after normalization → removes entire `location` block; blank strings never stored; `captureLocation` and `referenceLocation` never modified; path traversal protection identical to `updateTitle()`; no format validation (plain text fields); no UI, no scanner changes
- `metadata.json` includes schema version, timestamp, file names, MediaStore URI, picker URI, optional title, optional GPS location fields (`captureLocation`, `referenceLocation`), and optional reference date fields (`reference.date`, `reference.dateSource`, `reference.userEdited`)
- Missing, corrupt, or incomplete session metadata is ignored during scanning
- Session writes are best-effort and do not invalidate the main MediaStore save; if `SessionStorage.saveSession` returns null after a successful MediaStore save, the user receives `capture_saved_compare_failed` instead of the generic `capture_saved`
- Session deletion only removes internal session folders
- Session operations accept only direct child session IDs; nested or traversal-like IDs are rejected with controlled failure

### Shot Titles
- Optional session title stored in `metadata.json`
- Missing title field is valid
- Titles are trimmed; blank titles are stored as absent
- CompareScreen allows editing and removing a title
- CompareLibrary displays titles above timestamps when present
- Title-save failures, including unexpected exceptions from the title updater, are handled via the existing failure Snackbar (`compare_screen_title_save_failed`); the ViewModel launch does not surface uncaught exceptions

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
Implementation plan: `SESSION_METADATA_EDITOR_IMPLEMENTATION_PLAN.md`

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
- **Placeholders corrected** — all generic placeholder text replaced with concrete examples: Title → "e.g. Summer vacation in Italy", Description → "Add notes about this comparison", Place name → "e.g. Marienplatz", City → "e.g. Munich", Country → "e.g. Germany".
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
- Select All sets selectedSessionIds to the complete session list; Deselect All clears it

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
