# SESSION_ORIGINALS_PRIVACY_IMPLEMENTATION_PLAN.md

## Purpose

Block-by-block implementation plan for the Session Originals Privacy feature.

Specification: `SESSION_ORIGINALS_PRIVACY_V1.md`

This plan does NOT modify compare rendering, CompareScreen, VideoExport, ShareComparisonImage, CameraScreen overlay, GPS Guidance behavior, or any UI component beyond SettingsScreen.

Implementation is **blocked** until `SESSION_ORIGINALS_PRIVACY_V1.md` has received explicit user approval.

---

## Prerequisites

Before implementation begins, confirm:

- [ ] `SESSION_ORIGINALS_PRIVACY_V1.md` is approved
- [ ] JPEG quality value (95) is confirmed as final
- [ ] All existing tests are green before any change begins

---

## Block A — Setting + DataStore + Settings UI

### Files

- `app/src/main/java/com/isardomains/sameview/ui/settings/SettingsRepository.kt`
- `app/src/main/java/com/isardomains/sameview/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/ui/settings/SettingsScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de/strings.xml`

### Changes

**`SettingsRepository.kt`:**
- Add DataStore key: `private val STRIP_ORIGINALS_METADATA_KEY = booleanPreferencesKey("strip_originals_metadata")`
- Add `val stripOriginalsMetadata: Flow<Boolean>` (default: `false`)
- Add `suspend fun setStripOriginalsMetadata(enabled: Boolean)`

**`SettingsViewModel.kt`:**
- Add `val stripOriginalsMetadata: StateFlow<Boolean>` derived from repository flow
- Add `fun setStripOriginalsMetadata(enabled: Boolean)` that calls repository

**`SettingsScreen.kt`:**
- Add new "Privacy" / "Datenschutz" category section after GPS Guidance
- Add `SettingsSwitchRow` for the new setting
- Collect `stripOriginalsMetadata` from ViewModel
- No dependency on other settings (standalone toggle)

**`strings.xml` / `strings-de.xml`:**

Required string resource keys:

| Key | EN Value | DE Value |
|---|---|---|
| `settings_privacy_section` | Privacy | Datenschutz |
| `settings_strip_originals_metadata_title` | Strip metadata from stored originals | Metadaten aus Originalen entfernen |
| `settings_strip_originals_metadata_subtitle` | Stores full-resolution session originals without EXIF, GPS, or camera metadata. Gallery photos are not affected. | Speichert Session-Originale in voller Auflösung ohne EXIF-, GPS- oder Kamerainformationen. Galeriefotos bleiben unverändert. |

### Validation

Block A is complete when:

- `testDebugUnitTest` BUILD SUCCESSFUL
- `assembleDebug` BUILD SUCCESSFUL
- Setting visible and toggleable in SettingsScreen on device
- Setting persists across app restarts

---

## Block B — JPEG Metadata Stripping Engine

### Files

- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt`

### Changes

**New internal function: `stripJpegMetadata(source: File, dest: File): Boolean`**

Implements byte-level JPEG segment manipulation:

1. Open `source` as `FileInputStream`, wrap as `DataInputStream`
2. Verify SOI marker (`0xFF 0xD8`)
3. Iterate JPEG markers:
   - `APP0` (JFIF, `0xFF 0xE0`): **keep** (JFIF header; version info only)
   - `APP1` with EXIF marker (`0xFF 0xE1` + `"Exif\0\0"`): **skip** (EXIF data)
   - `APP1` with XMP marker (`0xFF 0xE1` + `"http://ns.adobe.com/"`): **skip** (XMP data)
   - `APP2` with ICC marker (`0xFF 0xE2` + `"ICC_PROFILE\0"`): **keep** (color profile)
   - `APP13` (`0xFF 0xED`): **skip** (Photoshop / IPTC data)
   - Any other `APP1`–`APP15` marker: **skip** (conservative approach)
   - All other markers (SOF, DHT, DQT, SOS, image data, EOI): **copy** verbatim
4. Write result to `dest`
5. Return `true` on success; `false` / throw on malformed JPEG or IO error

**New internal function: `readJpegOrientation(uri: Uri, contentResolver: ContentResolver): Int`**

Returns the EXIF orientation tag value from a JPEG URI. Returns `ExifInterface.ORIENTATION_UNDEFINED` on failure or absence.

**Updated `writeCaptureOriginalPrivate(context, captureMediaStoreUri, sessionDir): String`**

When privacy mode is ON:

```
1. Copy bytes to a temp file in sessionDir (temp_capture_orig.jpg)
2. Read EXIF orientation from temp file
3. If orientation is trivial (NORMAL or UNDEFINED):
   a. stripJpegMetadata(temp_capture_orig.jpg, capture-original.jpg)
   b. Delete temp file
4. If orientation is non-trivial:
   a. Decode temp file to Bitmap
   b. Apply rotation via Matrix
   c. Compress to capture-original.jpg at JPEG 95, no ExifInterface write
   d. Delete temp file
5. Return "capture-original.jpg"
```

**Updated `writeReferenceSourceOriginalPrivate(context, referenceUri, sessionDir): ReferenceSourceOriginalResult`**

When privacy mode is ON: delegates to format-specific handlers (Block C).

**Updated `saveSession()` internal overload:**

Reads `stripOriginalsMetadata` from DataStore at session save time (passed as parameter from `CameraViewModel`, which already reads settings). Selects either the existing byte-copy path or the new privacy path based on the setting value.

### Notes

- The JPEG byte-level stripper is self-contained with no external dependencies
- `BitmapFactory.decodeFile()` + `Bitmap.compress(JPEG, 95)` is the fallback path for non-trivial orientation — always available on minSdk 29
- The temp-file pattern for capture-original ensures that if stripping fails, the session save catches the exception and cleans up via `deleteRecursively()`

### Validation

Block B is complete when:

- `testDebugUnitTest` BUILD SUCCESSFUL
- `assembleDebug` BUILD SUCCESSFUL
- Existing `SessionStorageMetadataTest` remains green

---

## Block C — Format Conversion for Non-JPEG Sources

### Files

- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt`

### Changes

**New internal function: `processReferenceSourceForPrivacy(context, sourceUri, mimeType, sessionDir): ReferenceSourceOriginalResult`**

Implements the format-specific logic from `SESSION_ORIGINALS_PRIVACY_V1.md §6`:

```
when (mimeType?.lowercase()) {
  "image/jpeg" → stripJpegReferenceSource(context, sourceUri, sessionDir)
  "image/heic", "image/heif" → decodeAndReencodeAsJpeg(context, sourceUri, sessionDir, "image/heic")
  "image/png" → stripPngMetadata(context, sourceUri, sessionDir)
  "image/webp" → decodeAndReencodeAsJpeg(context, sourceUri, sessionDir, mimeType)
  "image/gif" → decodeAndReencodeAsJpeg(context, sourceUri, sessionDir, mimeType)
  "image/avif" → avifWithFallback(context, sourceUri, sessionDir, mimeType)
  "image/bmp" → decodeAndReencodeAsJpeg(context, sourceUri, sessionDir, mimeType)
  else → copyAsIsNotPossible(context, sourceUri, sessionDir)
}
```

**`stripJpegReferenceSource(context, sourceUri, sessionDir)`:**
- Similar to capture-original JPEG stripping
- Read EXIF orientation via `resolveSourceUri()` (existing helper)
- Byte-level strip if trivial orientation; decode+rotate+re-encode 95% otherwise
- Returns `ReferenceSourceOriginalResult(filename="reference-source-original.jpg", mimeType=originalMime, storedMimeType="image/jpeg", preservation="metadata_stripped")`

**`decodeAndReencodeAsJpeg(context, sourceUri, sessionDir, sourceMimeType)`:**
- `BitmapFactory.decodeStream(contentResolver.openInputStream(resolvedUri))`
- Orientation applied automatically for HEIC by decoder, or via ExifInterface for others
- `bitmap.compress(Bitmap.CompressFormat.JPEG, 95, FileOutputStream(destFile))`
- Returns `ReferenceSourceOriginalResult(filename="reference-source-original.jpg", mimeType=sourceMimeType, storedMimeType="image/jpeg", preservation="metadata_stripped")`

**`stripPngMetadata(context, sourceUri, sessionDir)`:**
- Read PNG bytes; iterate chunks
- Remove: tEXt, iTXt, zTXt, eXIf chunks (contain text metadata and EXIF)
- Keep: IHDR, IDAT, PLTE, iCCP, gAMA, sRGB, cHRM, pHYs, tRNS, bKGD, IEND
- Write clean PNG to `reference-source-original.png`
- Returns `ReferenceSourceOriginalResult(filename="reference-source-original.png", mimeType="image/png", storedMimeType="image/png", preservation="metadata_stripped")`

**`avifWithFallback(context, sourceUri, sessionDir, mimeType)`:**
- Check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` (API 31)
- If API 31+: decode via `ImageDecoder.decodeBitmap()`, re-encode as JPEG 95
- If API 29–30: copy bytes as-is, return `preservation="not_possible"`

**`copyAsIsNotPossible(context, sourceUri, sessionDir)`:**
- Copy bytes as-is (fallback to OFF behavior for this file)
- Returns `ReferenceSourceOriginalResult(filename="reference-source-original.bin", mimeType=null, storedMimeType=null, preservation="not_possible")`

### Update to `ReferenceSourceOriginalResult`

Add `storedMimeType: String?` and `preservation: String` fields:

```kotlin
internal data class ReferenceSourceOriginalResult(
    val filename: String,
    val mimeType: String?,
    val storedMimeType: String? = null,
    val preservation: String = "byte_copy"
)
```

Existing call sites in the OFF path use defaults: `storedMimeType = null`, `preservation = "byte_copy"`. No call-site changes required for existing code.

### Validation

Block C is complete when:

- `testDebugUnitTest` BUILD SUCCESSFUL
- `assembleDebug` BUILD SUCCESSFUL
- Existing tests remain green

---

## Block D — `metadata.json` `originals` Block

### Files

- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt`

### Changes

**Updated `writeMetadata()` signature:**

Add parameter: `originalsPrivacyMode: Boolean`, `capturePreservation: String`, `referenceSourceResult: ReferenceSourceOriginalResult`.

**Updated `writeMetadata()` body:**

When `originalsPrivacyMode == true`, add the `originals` block:

```json
"originals": {
  "privacyMode": true,
  "capturePreservation": "<capturePreservation>",
  "referenceSourcePreservation": "<referenceSourceResult.preservation>",
  "referenceSourceStoredMimeType": "<referenceSourceResult.storedMimeType>"
}
```

`referenceSourceStoredMimeType` is only included when `storedMimeType != null && storedMimeType != mimeType` (i.e., only when the stored format differs from the source format — e.g., HEIC → JPEG).

When `originalsPrivacyMode == false`: `originals` block is absent. No change to current behavior.

### Validation

Block D is complete when:

- `testDebugUnitTest` BUILD SUCCESSFUL
- `assembleDebug` BUILD SUCCESSFUL
- metadata.json contains `originals` block when setting is ON
- metadata.json has no `originals` block when setting is OFF

---

## Block E — CameraViewModel Integration

### Files

- `app/src/main/java/com/isardomains/sameview/ui/camera/CameraViewModel.kt`

### Changes

**Read setting at capture time:**

In `onPhotoCaptured()`, before launching the IO coroutine, read `stripOriginalsMetadata` from `settingsRepository`. Pass the boolean to `SessionStorage.saveSession()` as a new parameter: `stripOriginalsMetadata: Boolean`.

The setting value is frozen at capture time (same pattern as GPS snapshot freeze) to avoid race conditions if the user toggles the setting during a long session save.

**Updated `saveSession()` public signature:**

```kotlin
fun saveSession(
    context: Context,
    capturedBitmap: Bitmap,
    snapshot: CaptureSessionSnapshot,
    captureMediaStoreUri: Uri,
    stripMetadata: Boolean = false
)
```

Default `false` ensures all existing call sites (including tests) are unaffected without change.

### Validation

Block E is complete when:

- `testDebugUnitTest` BUILD SUCCESSFUL
- `assembleDebug` BUILD SUCCESSFUL
- Real-device test: toggle setting ON, create session, verify `capture-original.jpg` has no GPS EXIF

---

## Block F — Tests

### JVM Unit Tests

**`PrivacyJpegStripperTest.kt`** (new, `app/src/test/.../camera/`):

| # | Test |
|---|---|
| F-U-01 | `stripJpegMetadata()` removes EXIF APP1 segment |
| F-U-02 | `stripJpegMetadata()` removes XMP APP1 segment |
| F-U-03 | `stripJpegMetadata()` removes IPTC APP13 segment |
| F-U-04 | `stripJpegMetadata()` preserves image pixel data (output is valid JPEG) |
| F-U-05 | `stripJpegMetadata()` preserves ICC profile APP2 if present |
| F-U-06 | `stripJpegMetadata()` on JPEG with no metadata: output is valid JPEG |
| F-U-07 | `processReferenceSourceForPrivacy()` with `image/jpeg` → returns `.jpg`, preservation `"metadata_stripped"` |
| F-U-08 | `processReferenceSourceForPrivacy()` with `image/heic` → returns `.jpg`, storedMimeType `"image/jpeg"` |
| F-U-09 | `processReferenceSourceForPrivacy()` with `null` MIME → returns `.bin`, preservation `"not_possible"` |
| F-U-10 | `processReferenceSourceForPrivacy()` with `image/png` → returns `.png`, preservation `"metadata_stripped"` |

**`SessionStoragePrivacyTest.kt`** (new, JVM unit test):

| # | Test |
|---|---|
| F-U-11 | `saveSession()` with `stripMetadata=false`: `originals` block absent in metadata.json |
| F-U-12 | `saveSession()` with `stripMetadata=true`, JPEG source: `originals.privacyMode = true` in metadata.json |
| F-U-13 | `saveSession()` with `stripMetadata=true`, HEIC source: `files.referenceSourceOriginal` ends in `.jpg` |
| F-U-14 | `saveSession()` with `stripMetadata=true`, HEIC source: `originals.referenceSourceStoredMimeType = "image/jpeg"` |
| F-U-15 | `saveSession()` with `stripMetadata=true`, `.bin` source: `originals.referenceSourcePreservation = "not_possible"` |

### Instrumentation Tests

**`SessionStoragePrivacyInstrumentedTest.kt`** (new, `androidTest/`):

| # | Test |
|---|---|
| F-I-01 | Privacy ON + JPEG source: `capture-original.jpg` has no GPS EXIF tags |
| F-I-02 | Privacy ON + JPEG source: `capture-original.jpg` has no DateTimeOriginal |
| F-I-03 | Privacy ON + JPEG source: `capture-original.jpg` has no Make/Model |
| F-I-04 | Privacy ON + JPEG source: `capture-original.jpg` resolution unchanged |
| F-I-05 | Privacy ON + JPEG source: `reference-source-original.jpg` has no GPS EXIF |
| F-I-06 | Privacy OFF: `capture-original.jpg` retains all bytes from source (byte-for-byte identity) |
| F-I-07 | Privacy OFF: `reference-source-original.jpg` retains all bytes from source |
| F-I-08 | `originals` block in metadata.json present when ON, absent when OFF |
| F-I-09 | `metadataReader` in EditSessionViewModel reads existing sessions without `originals` block without error |

### Regression Guard

All existing tests must remain green:

- `SessionStorageMetadataTest` — all tests
- `SessionStorageGpsTest` — GPS data in `metadata.json` unaffected by privacy mode
- `SessionStorageReferenceOrientationTest` — `reference-original.jpg` unaffected
- `SessionBackupExporterTest` — backup exports stripped files correctly
- `SessionScannerTest` — v5 scanner tests unaffected
- `CompareScreenTest` — compare rendering unaffected

---

## Block G — SettingsTest Extension

**`SettingsViewModelTest.kt`** (existing, extend):

| # | Test |
|---|---|
| G-U-01 | Default value of `stripOriginalsMetadata` is `false` |
| G-U-02 | `setStripOriginalsMetadata(true)` persists across ViewModel recreations |
| G-U-03 | `setStripOriginalsMetadata(false)` reverts to OFF |

**`SettingsScreenTest.kt`** (existing, extend if applicable):

| # | Test |
|---|---|
| G-I-01 | Privacy category visible in SettingsScreen |
| G-I-02 | Toggle changes state and persists |

---

## Block H — Verification

**Smoke Tests (real device):**

| # | Smoke Test |
|---|---|
| H-S-01 | Toggle ON, take photo with GPS, select JPEG reference, create session → `capture-original.jpg` has no GPS EXIF |
| H-S-02 | Toggle ON, select HEIC reference (Samsung photo) → `reference-source-original.jpg` exists (not `.heic`), no GPS |
| H-S-03 | Toggle ON, create session → `metadata.json` contains `originals.privacyMode: true` |
| H-S-04 | Toggle OFF, take photo → `capture-original.jpg` has GPS EXIF (byte-for-byte copy intact) |
| H-S-05 | Toggle ON, create session, export backup → ZIP contains stripped files |
| H-S-06 | CompareScreen works correctly on a privacy-mode session (slider, badge, etc.) |
| H-S-07 | `OriginalReferenceBadge` still appears on a privacy-mode session (`reference-original.jpg` is unaffected) |

---

## Implementation Order

```
Block A → Block B → Block C → Block D → Block E → Block F → Block G → Block H
```

- Block A (setting) can precede Block B/C/D; the setting is read but has no effect until Block E
- Block B (JPEG stripping) is the most complex single block; implement and verify independently
- Block C (format conversion) builds on Block B's pattern
- Block D (metadata) requires Block C's `ReferenceSourceOriginalResult` extension
- Block E (CameraViewModel) wires everything together
- Blocks F/G/H are test and verification layers

---

## Explicitly Out of Scope

- Retroactive processing of existing sessions
- Modification of the MediaStore capture photo
- Modification of `capture.jpg`, `reference.jpg`, `reference-original.jpg`
- Changes to CompareScreen, compare rendering, slider behavior
- Changes to VideoExport or ShareComparisonImage pipelines
- GPS stripping from `metadata.json` fields (captureLocation, referenceLocation) — these remain
- Changes to OriginalReferenceBadge behavior
- Third-party image processing libraries (all processing uses Android SDK only)
