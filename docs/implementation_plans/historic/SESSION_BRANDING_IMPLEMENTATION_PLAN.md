# SESSION_BRANDING_IMPLEMENTATION_PLAN.md

## 1. Document Status

This document is the **authoritative implementation plan** for the Session Branding feature.

Specification: `SESSION_BRANDING_V1.md`

This plan defines:
- Block structure and sequencing
- Per-block affected files, risks, and test requirements
- Complete test plan

This plan does NOT redefine product or UX decisions.
All product decisions are in `SESSION_BRANDING_V1.md`.

---

## 2. Overview

The Session Branding feature is implemented in 8 blocks. Each block is independently
verifiable (builds pass, tests pass) before the next block begins.

| Block | Name | Description |
|---|---|---|
| 0 | Metadata v6 Foundation | Data model, schema v6, scanner update |
| 1 | Image Assets + Normalizer | VectorDrawables, BrandingNormalizer, built-in symbol rendering |
| 2 | Global Branding Repository | GlobalBrandingRepository, global PNG read/write |
| 3 | Session Branding Storage | SessionStorage extensions, auto-copy on creation |
| 4 | Global Settings UI | Branding section in SettingsScreen, BrandingPickerSheet |
| 5 | Edit Session Branding Card | Branding card in EditSessionScreen |
| 6 | Share Image Integration | ShareComparisonScreen toggle, renderer extension |
| 7 | Tests + Verification | Full test suite, regression guard, device smoke test |

Blocks 0–3 are purely data/storage and have no visible UI. Blocks 4–6 build the UI on
top of the verified data layer.

---

## 3. Block 0 — Metadata v6 Foundation

### 3.1 Scope

Introduce schema version 6. Extend the data model and session reading/writing to support
the `branding` block and `files.brandingHandle`. No branding logic yet — only the
schema plumbing.

### 3.2 New Files

| File | Purpose |
|---|---|
| `SessionBranding.kt` | Data class: `handleFile: String`, `type: String`, `builtinId: String?`, `updatedAtMs: Long` |
| `SessionBrandingMeta.kt` | Lightweight global branding metadata companion (type + builtinId stored alongside `filesDir/branding/handle.png`) |

### 3.3 Modified Files

| File | Change |
|---|---|
| `SessionStorage.kt` | Write `"version": 6` for all new sessions; write `branding` block and `files.brandingHandle` when branding PNG is provided at creation; read `branding` block from existing sessions |
| `SessionScanner.kt` | Add `6` to `SUPPORTED_VERSIONS`; add `files.brandingHandle` file-existence check for v6 sessions (when present); expose `branding: SessionBranding?` on `ScannedSession` |
| `ScannedSession.kt` | Add `branding: SessionBranding? = null` field |

### 3.4 Metadata-Driven Backup (no change needed)

`SessionBackupExporter` already reads all `files.*` block entries. Once `files.brandingHandle`
is written by `SessionStorage`, the backup exporter automatically includes `branding-handle.png`
in backups. **No change to `SessionBackupExporter`.**

### 3.5 SessionScanner SUPPORTED_VERSIONS

```kotlin
val SUPPORTED_VERSIONS = setOf(2, 3, 4, 5, 6)
```

### 3.6 Risks

| Risk | Mitigation |
|---|---|
| Scanner breaking on v6 sessions before Block 0 is complete | Block 0 must be fully tested before any v6 sessions can be created. No v6 session creation until Block 3. |
| v5 sessions affected by scanner change | v5 validation path unchanged; new v6 checks are version-gated. |
| `ScannedSession` field addition breaking call sites | New field has `= null` default. All existing call sites compile without change. |

### 3.7 Tests

**Unit tests — new:**
- `SessionStorageMetadataV6Test`:
  - Write v6 session without branding → `version = 6`, no `branding` block, no `files.brandingHandle`
  - Write v6 session with branding → `version = 6`, `branding` block present, `files.brandingHandle` present
  - Round-trip `branding.type = "image"` with null `builtinId`
  - Round-trip `branding.type = "builtin"` with `builtinId = "fire"`
  - Read v5 session → `ScannedSession.branding == null`
  - Read v6 session with branding → `ScannedSession.branding != null`, fields correct
  - Unknown `branding.type` value → silently accepted, no crash

**Instrumentation tests — new:**
- `SessionScannerV6Test`:
  - v6 session with `files.brandingHandle` and file present → accepted
  - v6 session with `files.brandingHandle` but file missing → rejected
  - v6 session without `files.brandingHandle` → accepted
  - v5 session → accepted (existing test updated to verify `branding == null`)

**Regression guard:**
- All existing `SessionScannerTest` tests must remain green
- All existing `SessionStorageMetadataTest` tests must remain green
- `assembleDebug` and `assembleRelease` must succeed

---

## 4. Block 1 — Image Assets + BrandingNormalizer

### 4.1 Scope

Create the 6 VectorDrawable assets for built-in symbols. Implement `BrandingNormalizer`
for own-image normalization. Implement `BuiltinSymbolRenderer` for VectorDrawable → PNG.

### 4.2 New Files

| File | Purpose |
|---|---|
| `res/drawable/ic_branding_heart.xml` | Heart VectorDrawable |
| `res/drawable/ic_branding_star.xml` | Star VectorDrawable |
| `res/drawable/ic_branding_camera.xml` | Camera VectorDrawable |
| `res/drawable/ic_branding_home.xml` | Home VectorDrawable |
| `res/drawable/ic_branding_pin.xml` | Map pin VectorDrawable |
| `res/drawable/ic_branding_fire.xml` | Flame VectorDrawable |
| `BrandingNormalizer.kt` | Normalizes any Bitmap to 512×512 RGBA PNG ByteArray |
| `BuiltinBrandingSymbol.kt` | Enum: HEART, STAR, CAMERA, HOME, PIN, FIRE with drawable resource IDs and `id` string property matching `SESSION_BRANDING_V1.md §6.1` |
| `BuiltinSymbolRenderer.kt` | Renders a `BuiltinBrandingSymbol` VectorDrawable to a 512×512 RGBA PNG via `BrandingNormalizer` |

### 4.3 VectorDrawable Design Guidelines

- Each drawable: `viewportWidth = 24`, `viewportHeight = 24` (standard Material grid)
- Stroke-based design preferred for clarity at small sizes
- Single path or minimal paths; no gradients
- Designed to be recognizable within a 54 dp (branding handle) circle at 72% ratio = ~39 dp

### 4.4 BrandingNormalizer Specification

```kotlin
object BrandingNormalizer {
    // Returns a 512×512 RGBA PNG as ByteArray.
    // Input bitmap is fit-scaled (aspect preserved) and centered on a transparent canvas.
    // EXIF orientation must be pre-applied before calling this function.
    fun normalize(sourceBitmap: Bitmap): ByteArray
}
```

Implementation notes:
- Create `Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)` (transparent by default)
- Compute fit-scale: `scale = min(512f / sourceWidth, 512f / sourceHeight)`
- Compute centered offset: `left = (512 - scaledWidth) / 2`, `top = (512 - scaledHeight) / 2`
- Draw scaled source bitmap onto canvas using `Canvas.drawBitmap()`
- Compress to PNG: `bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)`

### 4.5 URI Decoding for Photo Picker

When the user selects a photo via Photo Picker:
1. Open InputStream from `ContentResolver.openInputStream(uri)`
2. Decode with EXIF orientation: use `ExifInterface` to read orientation, apply rotation matrix
3. Pass decoded `Bitmap` to `BrandingNormalizer.normalize()`
4. HEIC (API 28+): use `ImageDecoder.createSource(contentResolver, uri).decodeBitmap { ... }`

### 4.6 Risks

| Risk | Mitigation |
|---|---|
| HEIC decode failure on API 29 | Try `ImageDecoder` first, fallback to `BitmapFactory`; on all failure: error snackbar |
| Very large bitmap causing OOM | Sample down before full decode: use `BitmapFactory.Options.inSampleSize` to pre-scale to ≤1024 px before normalization |
| VectorDrawable rendering at 512px returns blank | Test each symbol in instrumentation; verify non-null, non-zero-pixel output |

### 4.7 Tests

**Unit tests — new (`BrandingNormalizerTest`):**
- JPEG-decoded Bitmap 800×600 → 512×512 RGBA PNG output
- PNG with alpha → alpha channel preserved (pixel test: corner pixels transparent)
- Landscape 800×200 → content centered vertically (top/bottom padding transparent)
- Portrait 200×800 → content centered horizontally (left/right padding transparent)
- Square 512×512 → pixel-for-pixel center region
- Small 32×32 → upscaled to fit in center
- Large 4096×4096 → downscaled to fill 512×512

**Instrumentation tests — new (`BuiltinSymbolRendererTest`):**
- Each of 6 symbols renders to non-empty ByteArray
- Each rendered PNG is 512×512 RGBA
- No pixel test for exact appearance (VectorDrawable rendering is platform-dependent)

**Regression guard:** `testDebugUnitTest` fully green; `assembleDebug` successful.

---

## 5. Block 2 — GlobalBrandingRepository

### 5.1 Scope

Implement `GlobalBrandingRepository` for reading and writing the global branding file
at `filesDir/branding/handle.png`. Also stores global branding metadata (type, builtinId)
alongside the PNG as a small JSON companion file.

### 5.2 New Files

| File | Purpose |
|---|---|
| `GlobalBrandingRepository.kt` | Read/write `filesDir/branding/handle.png` and companion metadata |

### 5.3 GlobalBrandingRepository Interface

```kotlin
@Singleton
class GlobalBrandingRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    val brandingDir: File = File(context.filesDir, "branding")

    fun hasBranding(): Boolean
    fun getBrandingFile(): File?         // null when hasBranding() == false
    fun getBrandingMeta(): SessionBrandingMeta?

    suspend fun setBranding(
        normalizedPng: ByteArray,
        type: String,
        builtinId: String?
    )

    suspend fun removeBranding()
}
```

### 5.4 Companion Metadata File

Alongside `filesDir/branding/handle.png`, a `filesDir/branding/handle-meta.json` stores:
```json
{ "type": "builtin", "builtinId": "fire" }
```

This allows `SessionStorage` to copy the correct `type` and `builtinId` into new session
metadata when auto-copying global branding.

### 5.5 Atomicity

When writing global branding:
1. Write PNG to `filesDir/branding/handle-new.png`
2. Write meta to `filesDir/branding/handle-meta-new.json`
3. On success: rename both to final names (best-effort atomic via `File.renameTo()`)
4. On failure: clean up temp files

### 5.6 Risks

| Risk | Mitigation |
|---|---|
| `branding/` directory not created | Create on first write with `mkdirs()` |
| Concurrent read during write | No concurrent session creation during settings change; low risk in practice |

### 5.7 Tests

**Unit tests — new (`GlobalBrandingRepositoryTest`):**
- `hasBranding()` false when no file
- `setBranding()` creates `handle.png` and `handle-meta.json`
- `getBrandingFile()` returns the file after `setBranding()`
- `getBrandingMeta()` returns correct type and builtinId
- `removeBranding()` deletes both files
- `hasBranding()` false after `removeBranding()`

---

## 6. Block 3 — Session Branding Storage

### 6.1 Scope

Add branding auto-copy to `SessionStorage.saveSession()`. Add `updateSessionBranding()`,
`removeSessionBranding()`, and `copyGlobalBrandingToSession()` to `SessionStorage`.

### 6.2 Modified Files

| File | Change |
|---|---|
| `SessionStorage.kt` | Auto-copy global branding in `saveSession()`; add three new `fun` entries |
| `CameraViewModel.kt` | Pass `GlobalBrandingRepository` to session save; read `hasBranding()` to decide auto-copy |

### 6.3 saveSession() Extension

`saveSession()` receives an additional optional parameter:

```kotlin
suspend fun saveSession(
    ...,
    globalBrandingRepository: GlobalBrandingRepository? = null
): SavedSessionRef?
```

If `globalBrandingRepository != null` and `globalBrandingRepository.hasBranding()`:
1. Copy `globalBrandingRepository.getBrandingFile()` to `<sessionDir>/branding-handle.png`
2. Read meta from `globalBrandingRepository.getBrandingMeta()`
3. Include `files.brandingHandle` and `branding` block in `metadata.json`

Failure to copy global branding is non-fatal: session is saved without branding.
Log warning in debug builds.

### 6.4 New Storage Functions

```kotlin
fun updateSessionBranding(
    sessionsRoot: File,
    sessionId: String,
    brandingPng: ByteArray,
    type: String,
    builtinId: String?
): Boolean

fun removeSessionBranding(
    sessionsRoot: File,
    sessionId: String
): Boolean

fun copyGlobalBrandingToSession(
    sessionsRoot: File,
    sessionId: String,
    globalBrandingFile: File,
    globalBrandingMeta: SessionBrandingMeta?
): Boolean
```

All three functions:
- Validate `sessionId` against path traversal (same pattern as `updateTitle()`)
- Return `false` on invalid input, missing metadata.json, or IO error
- Preserve all existing `metadata.json` fields (atomic read-modify-write)

### 6.5 Risks

| Risk | Mitigation |
|---|---|
| Session creation blocks on large branding PNG copy | Copy is a small 512×512 PNG (~100 KB); negligible IO. Uses IO dispatcher. |
| Global branding file deleted between `hasBranding()` check and copy | Catch `IOException`, treat as no-branding (fail-soft) |

### 6.6 Tests

**Instrumentation tests — new (`SessionStorageBrandingTest`):**
- `saveSession()` with global branding present → `branding-handle.png` in session folder
- `saveSession()` with no global branding → no `branding-handle.png`
- `updateSessionBranding()` → file written, metadata updated, `updatedAtMs` set
- `removeSessionBranding()` → file deleted, `branding` block removed, `files.brandingHandle` removed
- `copyGlobalBrandingToSession()` → file copied, metadata consistent
- Invalid sessionId → returns false, no IO
- Missing `metadata.json` → returns false
- `updateSessionBranding()` existing branding → file overwritten atomically

---

## 7. Block 4 — Global Settings Branding Section

### 7.1 Scope

Add the "Default branding for new sessions" section to `SettingsScreen`. Implement
`BrandingPickerSheet` as a shared composable for image + symbol selection.

### 7.2 New Files

| File | Purpose |
|---|---|
| `BrandingPickerSheet.kt` | Bottom sheet with Image / Symbol tabs for branding selection |
| `BrandingPreviewCircle.kt` | 64 dp circle composable showing current branding |

### 7.3 Modified Files

| File | Change |
|---|---|
| `SettingsScreen.kt` | Add "Default branding for new sessions" section at bottom |
| `SettingsViewModel.kt` | Add branding state: `globalBrandingBitmap: StateFlow<Bitmap?>`, `setGlobalBranding()`, `removeGlobalBranding()` |
| `strings.xml` + `values-de/strings.xml` | New string resources (see §7.6) |

### 7.4 BrandingPickerSheet

```
BrandingPickerSheet(
    onImageSelected: (Uri) -> Unit,
    onSymbolSelected: (BuiltinBrandingSymbol) -> Unit,
    onDismiss: () -> Unit
)
```

Contains two tabs: "Image" and "Symbol".

**Image tab:**
- Single row with "Choose from gallery" action
- Tapping launches `PickVisualMedia(ImageOnly)` activity result contract
- On selection: calls `onImageSelected(uri)` and dismisses sheet
- On cancellation: dismisses sheet silently

**Symbol tab:**
- Grid of 6 built-in symbol buttons (2 × 3 grid, 48 dp each)
- Each symbol shows: `BrandingPreviewCircle` (48 dp, plain circle for the preview grid)
- Tapping calls `onSymbolSelected(symbol)` and dismisses sheet

### 7.5 Settings Section UX

```
Default branding for new sessions
──────────────────────────────────
[BrandingPreviewCircle — 64dp]

  [Choose image]         (always visible)
  [Choose symbol]        (always visible)
  [Remove]               (visible only when hasBranding == true)

"Automatically added to new sessions. Existing sessions are not changed."
```

### 7.6 New String Resources

| Key | EN | DE |
|---|---|---|
| `settings_branding_section_title` | "Default branding for new sessions" | "Standard-Branding für neue Sitzungen" |
| `settings_branding_no_branding` | "No branding set" | "Kein Branding gesetzt" |
| `settings_branding_choose_image` | "Choose image" | "Bild auswählen" |
| `settings_branding_choose_symbol` | "Choose symbol" | "Symbol auswählen" |
| `settings_branding_remove` | "Remove branding" | "Branding entfernen" |
| `settings_branding_info` | "Automatically added to new sessions. Existing sessions are not changed." | "Wird automatisch neuen Sitzungen hinzugefügt. Bestehende Sitzungen werden nicht geändert." |
| `branding_picker_tab_image` | "Image" | "Bild" |
| `branding_picker_tab_symbol` | "Symbol" | "Symbol" |
| `branding_picker_image_choose` | "Choose from gallery" | "Aus Galerie auswählen" |
| `branding_load_error` | "Couldn't load image" | "Bild konnte nicht geladen werden" |

### 7.7 Risks

| Risk | Mitigation |
|---|---|
| Photo Picker launched from BottomSheet causes activity result issues | Use `rememberLauncherForActivityResult` in the parent Composable (SettingsScreen), pass launcher into BrandingPickerSheet |
| Large bitmap normalization blocks UI | Run normalization on IO dispatcher in ViewModel; show loading indicator |

### 7.8 Tests

**Unit tests — new (`SettingsViewModelBrandingTest`):**
- `setGlobalBranding()` calls `GlobalBrandingRepository.setBranding()`
- `removeGlobalBranding()` calls `GlobalBrandingRepository.removeBranding()`
- `globalBrandingBitmap` state reflects current global branding
- Normalization failure → error event emitted, no branding change

**UI tests — new (`SettingsScreenBrandingTest`):**
- Branding section visible in Settings
- "Remove" button not visible when no branding set
- "Remove" button visible when branding is set
- Info text present

---

## 8. Block 5 — Edit Session Branding Card

### 8.1 Scope

Add a Branding card to `EditSessionScreen`. The card shows the current session branding
and allows changing, removing, or copying from global branding.

### 8.2 Modified Files

| File | Change |
|---|---|
| `EditSessionScreen.kt` | Add Branding card between Current photo card and Location card |
| `EditSessionViewModel.kt` | Add `sessionBranding: StateFlow<SessionBranding?>`, `setBranding()`, `removeBranding()`, `copyFromGlobalBranding()` |
| `SESSION_METADATA_EDITOR_V1.md` | Add §21 documenting the Branding card |
| `strings.xml` + `values-de/strings.xml` | New string resources (see §8.5) |

### 8.3 EditSessionViewModel Branding Methods

```kotlin
val sessionBranding: StateFlow<SessionBranding?>   // from metadata.json branding block
val sessionBrandingBitmap: StateFlow<Bitmap?>       // loaded from branding-handle.png

fun setBranding(normalizedPng: ByteArray, type: String, builtinId: String?)
fun removeBranding()
fun copyFromGlobalBranding()   // available only when sessionBranding == null && global exists
```

Branding operations:
- Execute on IO dispatcher
- Write immediately (do not wait for Save button)
- Do NOT affect `isDirty` state

### 8.4 Card Layout

**When session has branding:**
```
Branding
[BrandingPreviewCircle 64dp]
[Change branding]   [Remove branding]
```

**When session has no branding:**
```
Branding
"No branding for this session."
[Choose image]   [Choose symbol]
[Copy from default branding]    ← only when global branding exists
```

Both "Change branding" and "Choose image / Choose symbol" open `BrandingPickerSheet`.

### 8.5 New String Resources

| Key | EN | DE |
|---|---|---|
| `edit_session_card_branding` | "Branding" | "Branding" |
| `edit_session_branding_none` | "No branding for this session." | "Kein Branding für diese Sitzung." |
| `edit_session_branding_change` | "Change branding" | "Branding ändern" |
| `edit_session_branding_remove` | "Remove branding" | "Branding entfernen" |
| `edit_session_branding_copy_global` | "Copy from default branding" | "Standard-Branding kopieren" |

### 8.6 Dirty State / Discard Interaction

Branding changes in Edit Session:
- Written immediately, independent of Save/Discard
- `isDirty` is NOT affected
- The Discard dialog ("Discard changes?") does NOT revert branding changes

This matches the Favourite star behavior (§20 in `SESSION_METADATA_EDITOR_V1.md`).

### 8.7 Risks

| Risk | Mitigation |
|---|---|
| User discards form changes but branding was already changed | Intentional: branding is always immediate. Documented in §8.6. |
| Session branding bitmap load fails (file missing) | Show "No branding" state; log warning; do not crash |

### 8.8 Tests

**Unit tests — new (`EditSessionViewModelBrandingTest`):**
- `sessionBranding` loaded from metadata on screen open
- `setBranding()` calls `SessionStorage.updateSessionBranding()` immediately
- `removeBranding()` calls `SessionStorage.removeSessionBranding()` immediately
- `copyFromGlobalBranding()` calls `SessionStorage.copyGlobalBrandingToSession()`
- Branding operations do NOT set `isDirty`
- Branding operations do NOT affect save path

**UI tests — new (`EditSessionScreenBrandingTest`):**
- Branding card visible in Edit Session
- When no branding: "No branding for this session" visible
- "Copy from default branding" visible only when global branding exists AND session has no branding
- When branding set: preview circle visible; "Change branding" and "Remove branding" buttons visible

---

## 9. Block 6 — Share Image Branding Integration

### 9.1 Scope

Extend `ShareComparisonScreen` with a "Use branding" toggle. Extend `SliderRenderStrategy`
and `ShareComparisonPreview` to render the branding handle when the toggle is ON.

### 9.2 Modified Files

| File | Change |
|---|---|
| `ShareComparisonViewModel.kt` | Add `sessionBrandingFile: File?`, `useBranding: MutableStateFlow<Boolean>`, `toggleUseBranding()` |
| `ShareComparisonScreen.kt` | Add "Use branding" toggle row with info text |
| `ShareComparisonPreview.kt` | Extend `SliderPreviewContent` to render branding handle when `useBranding == true` |
| `SliderRenderStrategy.kt` | Extend to render branding handle in export when branding present |
| `strings.xml` + `values-de/strings.xml` | New string resources (see §9.5) |

### 9.3 ShareComparisonViewModel Branding State

```kotlin
val sessionBrandingFile: File?   // set at init from session directory; null if no branding-handle.png

val hasBranding: Boolean = sessionBrandingFile != null

// Default: ON when branding present, OFF when absent (but UI shows toggle in both cases)
val useBranding: MutableStateFlow<Boolean> = MutableStateFlow(hasBranding)

fun toggleUseBranding() {
    useBranding.value = !useBranding.value
}
```

`useBranding` is NOT persisted per FD-15 (`SHARE_COMPARISON_IMAGE_V1.md`).

### 9.4 Toggle UX in ShareComparisonScreen

```
Use branding      [Toggle]
"Add branding in Edit session."   ← visible only when !hasBranding
```

Toggle is:
- Enabled and defaults to ON when `hasBranding == true`
- Visible but DISABLED and defaults to OFF when `hasBranding == false`
- Info text shown only when disabled

### 9.5 SliderRenderStrategy Extension

`SliderRenderStrategy` receives an additional render parameter:

```kotlin
data class BrandingRenderInput(
    val brandingFile: File,
    val enabled: Boolean
)
```

**Rendering when `BrandingRenderInput.enabled == true` and file exists:**
1. Load `brandingFile` as `Bitmap`
2. Compute branding handle size: `standardHandleSize × 1.5`
3. Draw outer ring: `SameViewAccent`, same arc geometry as standard handle
4. Draw inner circle: `#F5F7FA`
5. Draw logo: scale to 72% of circle diameter (Fit), center in circle
6. No arrows drawn

**Rendering when `enabled == false` or `brandingFile` missing:**
- Draw standard SameView handle (unchanged existing code)
- If `brandingFile` missing: log warning; fall back to standard handle silently

**Preview consistency:** `ShareComparisonPreview` uses the same rendering logic path.
Where the existing code draws arrows, a branding mode branch draws the logo. Both
paths must produce visually equivalent results.

### 9.6 New String Resources

| Key | EN | DE |
|---|---|---|
| `share_image_use_branding` | "Use branding" | "Branding verwenden" |
| `share_image_branding_add_hint` | "Add branding in Edit session." | "Branding in Sitzung bearbeiten hinzufügen." |

### 9.7 Side by Side

The branding toggle has no effect on Side by side style rendering. When Side by side
is selected, `BrandingRenderInput` is not passed to `SideBySideRenderStrategy`.

### 9.8 Risks

| Risk | Mitigation |
|---|---|
| Loading branding bitmap on main thread during export causes ANR | Load on IO dispatcher before rendering pass; pass decoded Bitmap into render |
| Preview bitmap loading causes jank | Load asynchronously via Coil or `LaunchedEffect`; show preview placeholder during load |
| Branding handle too large / too small at different canvas sizes | Size is proportional (1.5× standard), bounded by the same proportional formula — verified in instrumentation tests |

### 9.9 Tests

**Unit tests — new (`ShareComparisonViewModelBrandingTest`):**
- `useBranding` defaults to `true` when `sessionBrandingFile != null`
- `useBranding` defaults to `false` when `sessionBrandingFile == null`
- `toggleUseBranding()` toggles value
- `useBranding` is NOT persisted (not in DataStore or disk)

**Instrumentation tests — new (`ShareImageRendererBrandingTest`):**
- With branding, `useBranding = true` → rendered pixel area of handle differs from standard (non-trivial change)
- With branding, `useBranding = false` → standard handle rendered
- No branding → standard handle regardless of toggle
- Missing branding file → standard handle (fallback, no crash)
- Side by side → no handle rendered regardless of toggle

**UI tests — new (`ShareComparisonScreenBrandingTest`):**
- Toggle visible in Share Comparison Screen
- Toggle disabled when no session branding
- Info text "Add branding in Edit session." visible when toggle disabled
- Info text absent when toggle enabled

---

## 10. Block 7 — Tests + Verification

### 10.1 Scope

Full test suite run, regression guard, device smoke test.

### 10.2 Unit Test Suite

Run `testDebugUnitTest`. All tests must pass:

| Test class | Expected |
|---|---|
| `SessionStorageMetadataV6Test` | All new tests green |
| `SessionScannerV6Test` | All new tests green |
| `BrandingNormalizerTest` | All new tests green |
| `BuiltinSymbolRendererTest` | All new tests green |
| `GlobalBrandingRepositoryTest` | All new tests green |
| `SessionStorageBrandingTest` | All new tests green (instrumented) |
| `SettingsViewModelBrandingTest` | All new tests green |
| `EditSessionViewModelBrandingTest` | All new tests green |
| `ShareComparisonViewModelBrandingTest` | All new tests green |
| All prior unit tests | Remain green (regression guard) |

### 10.3 Instrumentation Test Suite

Run `connectedDebugAndroidTest`. Must pass on SM-S911B (Android 16) or equivalent:

| Test class | Expected |
|---|---|
| `SessionStorageBrandingTest` | All green |
| `SessionScannerV6Test` | All green |
| `ShareImageRendererBrandingTest` | All green |
| `SettingsScreenBrandingTest` | All green |
| `EditSessionScreenBrandingTest` | All green |
| `ShareComparisonScreenBrandingTest` | All green |
| All prior instrumentation tests | Remain green (regression guard) |

### 10.4 Build Verification

- `assembleDebug` → BUILD SUCCESSFUL
- `assembleRelease` → BUILD SUCCESSFUL

### 10.5 Device Smoke Test

Manual verification on a real device (SM-S911B or equivalent):

| # | Scenario | Expected |
|---|---|---|
| SM-01 | Open Settings → Branding section visible | Section present |
| SM-02 | Choose image → normalize → global branding set | Preview circle shows logo |
| SM-03 | Remove global branding | Preview shows "No branding" |
| SM-04 | Choose symbol (fire) → global branding set | Preview shows fire symbol |
| SM-05 | Create new session with global branding set | `branding-handle.png` in session folder |
| SM-06 | Create new session without global branding | No `branding-handle.png` in session folder |
| SM-07 | Edit Session → Branding card visible | Card present |
| SM-08 | No session branding → "Copy from default branding" visible (when global set) | Button visible |
| SM-09 | Copy from default branding → session branding set | Preview shows logo |
| SM-10 | Change session branding → different logo | Preview updated |
| SM-11 | Remove session branding | Card shows "No branding" |
| SM-12 | Share Comparison → Slider → Toggle visible | Toggle present |
| SM-13 | Toggle ON + branding set → branding handle in preview | Logo in handle circle |
| SM-14 | Toggle OFF → standard SameView handle in preview | Arrows in handle circle |
| SM-15 | Toggle disabled when no session branding | Toggle greyed out, info text visible |
| SM-16 | Export with branding ON → saved JPEG has branding handle | Logo visible in exported file |
| SM-17 | Export with branding OFF → saved JPEG has standard handle | Arrows visible in exported file |
| SM-18 | Backup session with branding → ZIP contains `branding-handle.png` | File in ZIP |
| SM-19 | CompareScreen handle unchanged | White circle + blue arrows, no logo |

---

## 11. Block 8 — Documentation Updates

### 11.1 Files to Update

| File | Change |
|---|---|
| `SESSION_METADATA_V1.md` | Add §6.7 documenting v6 schema changes (`files.brandingHandle`, `branding` block); update schema version history table; update `SessionScanner.SUPPORTED_VERSIONS` entry |
| `SESSION_BACKUP_EXPORT_V1.md` | Update §4.2 session directory structure for v6 (add `branding-handle.png` to example); note that backup exporter handles v6 automatically |
| `SESSION_METADATA_EDITOR_V1.md` | Add §21 documenting the Branding card: card position, immediate-write behavior, interaction with isDirty/Discard |
| `SETTINGS_UX_V1.md` | Add new "Branding" section (Category 6) documenting the default branding setting |
| `SHARE_COMPARISON_IMAGE_V1.md` | Extend FD-17 to cover branding handle variant; add new FD-18 for "Use branding" toggle |
| `VIDEO_EXPORT_V1.md` | Add §17 Future Compatibility: note about TODO comment in `CompareSliderRenderEngine` |
| `CLAUDE_PROJECT_INSTRUCTION.md` | Update Session Storage addendum: schema is now v6; reference `SESSION_BRANDING_V1.md` |
| `IMPLEMENTATION_NOTES.md` | Add "Session Branding" section after Session Originals Privacy; record verified test state for each block |

### 11.2 IMPLEMENTATION_NOTES.md Entry (template)

```markdown
### Session Branding

Full specification: `SESSION_BRANDING_V1.md`
Implementation plan: `SESSION_BRANDING_IMPLEMENTATION_PLAN.md`

Status: [Block X completed YYYY-MM-DD]

Block 0 completed (YYYY-MM-DD): ...
Block 1 completed (YYYY-MM-DD): ...
...
```

---

## 12. Complete Test Plan

### 12.1 Unit Tests

| # | Class | Test |
|---|---|---|
| U-01 | `BrandingNormalizerTest` | JPEG Bitmap → 512×512 RGBA PNG |
| U-02 | `BrandingNormalizerTest` | PNG with alpha → alpha preserved |
| U-03 | `BrandingNormalizerTest` | Landscape 800×200 → top/bottom transparent padding |
| U-04 | `BrandingNormalizerTest` | Portrait 200×800 → left/right transparent padding |
| U-05 | `BrandingNormalizerTest` | Square 512×512 → unchanged center |
| U-06 | `BrandingNormalizerTest` | Small 32×32 → upscaled to fit |
| U-07 | `BrandingNormalizerTest` | Large 4096×4096 → downscaled |
| U-08 | `SessionStorageMetadataV6Test` | Write v6 without branding → no `branding` block |
| U-09 | `SessionStorageMetadataV6Test` | Write v6 with branding → `branding` and `files.brandingHandle` present |
| U-10 | `SessionStorageMetadataV6Test` | Round-trip `type = "image"` |
| U-11 | `SessionStorageMetadataV6Test` | Round-trip `type = "builtin"`, `builtinId = "fire"` |
| U-12 | `SessionStorageMetadataV6Test` | Unknown `branding.type` silently accepted |
| U-13 | `SessionStorageMetadataV6Test` | v5 session parsed without error; `branding == null` |
| U-14 | `GlobalBrandingRepositoryTest` | `hasBranding()` false when no file |
| U-15 | `GlobalBrandingRepositoryTest` | `setBranding()` creates `handle.png` and meta |
| U-16 | `GlobalBrandingRepositoryTest` | `removeBranding()` deletes both files |
| U-17 | `GlobalBrandingRepositoryTest` | `getBrandingMeta()` correct after `setBranding()` |
| U-18 | `SettingsViewModelBrandingTest` | `setGlobalBranding()` delegates to repository |
| U-19 | `SettingsViewModelBrandingTest` | `removeGlobalBranding()` delegates to repository |
| U-20 | `EditSessionViewModelBrandingTest` | `sessionBranding` loaded at open |
| U-21 | `EditSessionViewModelBrandingTest` | `setBranding()` writes immediately, no `isDirty` change |
| U-22 | `EditSessionViewModelBrandingTest` | `removeBranding()` writes immediately, no `isDirty` change |
| U-23 | `EditSessionViewModelBrandingTest` | Branding changes do not affect Save button state |
| U-24 | `ShareComparisonViewModelBrandingTest` | `useBranding` default ON when branding present |
| U-25 | `ShareComparisonViewModelBrandingTest` | `useBranding` default OFF when no branding |
| U-26 | `ShareComparisonViewModelBrandingTest` | `toggleUseBranding()` flips value |

### 12.2 Instrumentation Tests

| # | Class | Test |
|---|---|---|
| I-01 | `BuiltinSymbolRendererTest` | Each of 6 symbols renders to non-empty 512×512 RGBA PNG |
| I-02 | `SessionScannerV6Test` | v6 session with branding file → accepted |
| I-03 | `SessionScannerV6Test` | v6 session with `files.brandingHandle` but file missing → rejected |
| I-04 | `SessionScannerV6Test` | v6 session without branding → accepted |
| I-05 | `SessionScannerV6Test` | v5 session → accepted, `branding == null` |
| I-06 | `SessionStorageBrandingTest` | `saveSession()` with global branding → file copied to session |
| I-07 | `SessionStorageBrandingTest` | `saveSession()` without global branding → no branding file |
| I-08 | `SessionStorageBrandingTest` | `updateSessionBranding()` → file written, metadata updated |
| I-09 | `SessionStorageBrandingTest` | `removeSessionBranding()` → file deleted, metadata cleaned |
| I-10 | `SessionStorageBrandingTest` | `copyGlobalBrandingToSession()` → correct file + metadata |
| I-11 | `SessionStorageBrandingTest` | Invalid sessionId → returns false, no IO |
| I-12 | `ShareImageRendererBrandingTest` | Branding ON → rendered output differs from standard handle |
| I-13 | `ShareImageRendererBrandingTest` | Branding OFF → standard handle rendered |
| I-14 | `ShareImageRendererBrandingTest` | No branding → standard handle |
| I-15 | `ShareImageRendererBrandingTest` | Missing branding file → standard handle fallback |
| I-16 | `ShareImageRendererBrandingTest` | Side by side → no handle, branding has no effect |
| I-17 | `SettingsScreenBrandingTest` | Branding section visible |
| I-18 | `SettingsScreenBrandingTest` | "Remove" absent when no branding |
| I-19 | `SettingsScreenBrandingTest` | "Remove" present when branding set |
| I-20 | `EditSessionScreenBrandingTest` | Branding card visible |
| I-21 | `EditSessionScreenBrandingTest` | No session branding → "No branding" text visible |
| I-22 | `EditSessionScreenBrandingTest` | "Copy from default branding" visible only when applicable |
| I-23 | `EditSessionScreenBrandingTest` | Session branding present → preview and "Change / Remove" visible |
| I-24 | `ShareComparisonScreenBrandingTest` | Toggle visible in Share Comparison Screen |
| I-25 | `ShareComparisonScreenBrandingTest` | Toggle disabled when no session branding |
| I-26 | `ShareComparisonScreenBrandingTest` | Info text visible when toggle disabled |

### 12.3 Backup Tests

| # | Test |
|---|---|
| B-01 | v6 session with branding → `branding-handle.png` present in backup ZIP |
| B-02 | v6 session without branding → no `branding-handle.png` in ZIP |
| B-03 | v5 session backup → unchanged (3-file or 5-file session as before) |
| B-04 | Backup ZIP integrity: `branding-handle.png` byte-identical to source |

### 12.4 Metadata Tests

| # | Test |
|---|---|
| M-01 | v6 metadata round-trip: all `branding` fields survive write-read cycle |
| M-02 | `branding.updatedAtMs` set to current time on `updateSessionBranding()` |
| M-03 | `branding` block fully absent after `removeSessionBranding()` |
| M-04 | `files.brandingHandle` fully absent after `removeSessionBranding()` |
| M-05 | Unknown top-level `branding.*` fields silently ignored on read |
| M-06 | `metadata.json` `branding` block contains no source URI field after `updateSessionBranding()` |
| M-07 | `metadata.json` `branding` block contains no EXIF-derived fields (no camera, date, GPS) |

### 12.5 Normalization Tests

| # | Test |
|---|---|
| N-01 | JPEG input → 512×512 RGBA PNG output |
| N-02 | PNG with alpha → alpha preserved |
| N-03 | Landscape logo → transparent padding top/bottom |
| N-04 | Portrait logo → transparent padding left/right |
| N-05 | EXIF rotation → auto-corrected before scaling |
| N-06 | HEIC input → decoded and normalized (API 29+) |
| N-07 | Very small image (32×32) → upscaled without crash |
| N-08 | Normalization failure → error returned, no crash |
| N-09 | JPEG with GPS EXIF → normalized PNG: `TAG_GPS_LATITUDE` absent (via `ExifInterface`) |
| N-10 | JPEG with camera Make/Model → normalized PNG: Make/Model absent (via `ExifInterface`) |
| N-11 | Built-in symbol PNG → no EXIF tags present in output |

### 12.6 Share Image Toggle Tests

| # | Test |
|---|---|
| T-01 | Toggle visible in Slider style Share Comparison Screen |
| T-02 | Toggle enabled when session has branding |
| T-03 | Toggle disabled when session has no branding |
| T-04 | Info text "Add branding in Edit session." present when disabled |
| T-05 | Toggle ON → branding handle in preview |
| T-06 | Toggle OFF → standard SameView handle in preview |
| T-07 | Toggle state not persisted across screen re-opens |
| T-08 | Side by side style → toggle present, no branding rendered |

### 12.7 Compare Screen Regression Guard

| # | Test |
|---|---|
| R-01 | `CompareScreen` handle visual unchanged (white circle, blue arrows) |
| R-02 | `CompareScreen` slider gesture behavior unchanged |
| R-03 | `CompareScreen` label rendering unchanged |
| R-04 | `CompareScreen` fullscreen mode unchanged |
| R-05 | `CompareLibraryScreen` tile display unchanged |
| R-06 | Session backup export unchanged (existing instrumented tests green) |
| R-07 | Session delete behavior unchanged |
| R-08 | Video export unchanged |
| R-09 | All existing unit and instrumentation tests pass without modification |

---

## 13. Implementation Discipline Rules

### 13.1 Feature Boundary Rules

1. No branding logic in `CompareScreen`, `CompareSliderViewport`, or `CompareDivider`
2. No branding logic in `CompareSliderRenderEngine` — only a TODO comment (§16 of spec)
3. `SessionBackupExporter` is not modified — branding is included automatically
4. No `material-icons-extended` dependency introduced
5. Photo Picker URI is never persisted — decode, normalize, release
6. All new user-facing strings use string resources; no hardcoded visible text
7. Global branding storage is file-based; no new DataStore key for branding
8. All branding IO runs on the IO dispatcher; no branding IO on the main thread
9. Each block must pass `assembleDebug` and `testDebugUnitTest` before the next begins
10. `assembleRelease` must succeed after Block 7

### 13.2 Privacy and Safety Rules

1. Branding images are always metadata-clean: the normalized PNG must contain no EXIF,
   GPS, XMP, IPTC, Make/Model, or MakerNotes — enforced by the decode→Bitmap→PNG pipeline
2. The original source image URI is never stored on disk or in `metadata.json`
3. The source image filename is never stored in `metadata.json`
4. The branding metadata-clean rule applies unconditionally — it is not controlled by
   `strip_originals_metadata` and cannot be disabled

### 13.3 Unrelated-Change Prohibition

Each block defines a specific scope. The following are forbidden in every block unless
explicitly listed as part of that block's scope:

- Modifying unrelated Composables, ViewModels, or storage classes
- Reformatting or reorganizing existing files
- Refactoring existing logic outside the feature boundary
- Adding imports, dependencies, or string resources not needed by the block
- Changing the behavior of `SessionScanner`, `SessionBackupExporter`, `SessionDeleter`,
  `CompareScreen`, `CreateVideoScreen`, or `BrandingEndcardRenderer` beyond what is
  explicitly required for the block

When a block's change requires touching a shared file (e.g., `SessionStorage.kt`,
`strings.xml`), the change must be as small and localized as possible.

---

## 14. New Files Summary

| File | Package / Location | Block |
|---|---|---|
| `SessionBranding.kt` | `ui/camera` | 0 |
| `SessionBrandingMeta.kt` | `ui/camera` | 0 |
| `ic_branding_heart.xml` | `res/drawable` | 1 |
| `ic_branding_star.xml` | `res/drawable` | 1 |
| `ic_branding_camera.xml` | `res/drawable` | 1 |
| `ic_branding_home.xml` | `res/drawable` | 1 |
| `ic_branding_pin.xml` | `res/drawable` | 1 |
| `ic_branding_fire.xml` | `res/drawable` | 1 |
| `BuiltinBrandingSymbol.kt` | `ui/branding` | 1 |
| `BrandingNormalizer.kt` | `ui/branding` | 1 |
| `BuiltinSymbolRenderer.kt` | `ui/branding` | 1 |
| `GlobalBrandingRepository.kt` | `ui/branding` | 2 |
| `BrandingPickerSheet.kt` | `ui/branding` | 4 |
| `BrandingPreviewCircle.kt` | `ui/branding` | 4 |

## 15. Modified Files Summary

| File | Blocks |
|---|---|
| `SessionStorage.kt` | 0, 3 |
| `SessionScanner.kt` | 0 |
| `ScannedSession.kt` | 0 |
| `CameraViewModel.kt` | 3 |
| `SettingsScreen.kt` | 4 |
| `SettingsViewModel.kt` | 4 |
| `EditSessionScreen.kt` | 5 |
| `EditSessionViewModel.kt` | 5 |
| `ShareComparisonViewModel.kt` | 6 |
| `ShareComparisonScreen.kt` | 6 |
| `ShareComparisonPreview.kt` | 6 |
| `SliderRenderStrategy.kt` | 6 |
| `CompareSliderRenderEngine.kt` | 6 (TODO comment only) |
| `strings.xml` (en + de) | 4, 5, 6 |
| Various doc `.md` files | 8 |

---

## 16. Architecture and Code Quality Rules

### 16.1 Package Structure

All new branding classes live under a dedicated package:

```text
com.isardomains.sameview.branding
    BrandingNormalizer          Pure object — no Android context; Bitmap in, ByteArray out
    BuiltinBrandingSymbol       Enum of 6 symbols with drawable resource IDs and string IDs
    BuiltinSymbolRenderer       Context-aware renderer: symbol → normalized PNG ByteArray
    GlobalBrandingRepository    @Singleton; reads/writes filesDir/branding/
    SessionBranding             Data class; mirrors the metadata.json branding block
    SessionBrandingMeta         Lightweight companion for type + builtinId storage
    BrandingHandleRenderer      Stateless drawing helper for the handle circle in Canvas/DrawScope
```

`BrandingPickerSheet` and `BrandingPreviewCircle` live under the existing UI package:

```text
com.isardomains.sameview.ui.branding
    BrandingPickerSheet         Bottom sheet Composable — shared by Settings and Edit Session
    BrandingPreviewCircle       Composable — 64 dp preview circle with SameViewAccent ring
```

`SessionBranding` and `SessionBrandingMeta` may alternatively live under
`com.isardomains.sameview.ui.camera` if that matches the existing data class pattern
used by `ScannedSession` and related types. Decision: follow the existing convention.

### 16.2 Responsibility Boundaries

| Layer | Allowed | Forbidden |
|---|---|---|
| `BrandingNormalizer` | Pure Bitmap transformation, PNG encoding | Android Context, file IO, UI state |
| `GlobalBrandingRepository` | File IO for global PNG and meta JSON | Composable state, ViewModel logic |
| `SessionStorage` extensions | Atomic file write + metadata update | UI state, Context beyond path resolution |
| `BrandingHandleRenderer` | DrawScope / Canvas drawing | File IO, ViewModel access |
| `BrandingPickerSheet` | UI only — open picker, show grid | File IO, normalization |
| `SettingsViewModel` / `EditSessionViewModel` | Orchestrate IO via repository + storage | Direct file access, Bitmap work on main thread |

### 16.3 No God Classes

- `SessionStorage.kt` receives minimal additions (three new functions + auto-copy hook).
  It must not absorb normalization logic, global branding management, or UI state.
- `SettingsScreen.kt` receives a new card section only. No normalization or storage
  logic moves into the Composable.
- `ShareComparisonScreen.kt` receives a new toggle row only. Rendering logic stays in
  `SliderRenderStrategy` and `BrandingHandleRenderer`.

### 16.4 Reuse Existing Infrastructure

Before creating new helpers, check for existing equivalents:

| Need | Existing candidate |
|---|---|
| EXIF orientation reading | `ReferenceImageMetadataReader` — check if `ExifInterface` usage is extractable |
| Session path safety checks | `SessionStorage.isSafeFilename()` / path-traversal pattern — reuse exactly |
| Metadata JSON read-modify-write | Existing `updateTitle()` / `updateFavorite()` pattern — follow same structure |
| SettingsCard / SettingsSwitchRow | `SettingsComponents.kt` — use existing components for branding section |
| Photo Picker launcher | `PickVisualMedia` contract already used for reference image — follow same pattern |
| IO dispatcher injection | Existing `ioDispatcher` injectable pattern in `EditSessionViewModel` — reuse |
| Coroutine scope in ViewModel | `viewModelScope.launch(ioDispatcher)` — existing pattern |

Do not duplicate. If an existing helper almost fits, extend it minimally or create a
small new focused function rather than copy-pasting.

### 16.5 Coroutine and Threading Rules

- All file read/write operations run on the IO dispatcher
- `BrandingNormalizer.normalize()` (CPU-bound Bitmap work) runs on the Default dispatcher
- No `Dispatchers.Main` blocking on file or bitmap operations
- `GlobalBrandingRepository` functions are `suspend` and dispatch internally
- ViewModel functions that trigger IO use `viewModelScope.launch(ioDispatcher) { ... }`

### 16.6 No Hidden Test Hooks in Production Code

- No `@VisibleForTesting` on fields or functions that are not logically internal
- Injectable lambdas follow the existing pattern in `EditSessionViewModel` (already used
  for `sessionTitleUpdater`, `sessionLocationUpdater`, etc.) — this is the approved
  pattern for testability; do not introduce new patterns
- `internal` visibility used only where the existing codebase already uses it

### 16.7 Modular Reusability

The branding asset layer (`BrandingNormalizer`, `GlobalBrandingRepository`,
`SessionBranding`, `BrandingHandleRenderer`) must be designed so that:

- Share Image rendering (Block 6) uses `BrandingHandleRenderer` without copying code
- Future Video rendering can use the same `BrandingHandleRenderer` without changes to it
- Future Website/backup consumers can read `branding-handle.png` directly — no app code needed

`BrandingHandleRenderer` must not depend on `ShareComparisonViewModel` or any
screen-specific state. It receives only drawing primitives (DrawScope/Canvas,
circle bounds, logo Bitmap) and produces drawing commands.
