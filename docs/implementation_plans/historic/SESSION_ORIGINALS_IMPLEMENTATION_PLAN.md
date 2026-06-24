# SESSION_ORIGINALS_IMPLEMENTATION_PLAN.md

## Purpose

This document is the block-by-block implementation plan for the session originals feature.

Specification: `SESSION_ORIGINALS_V1.md`

This plan does NOT modify compare rendering, CompareScreen, VideoExport, ShareComparisonImage, CameraScreen, or any UI component.

Implementation is **blocked** until this plan and `SESSION_ORIGINALS_V1.md` have received explicit user approval.

---

## Prerequisites

Before implementation begins, confirm:

- [ ] `SESSION_ORIGINALS_V1.md` is approved
- [ ] `SESSION_METADATA_V1.md` v5 section is finalized
- [ ] `COMPARE_SESSION_RENDERING_V1.md` has been updated (v5 file structure)
- [ ] `SESSION_BACKUP_EXPORT_V1.md` has been updated (metadata-driven export, 6-file structure)
- [ ] All existing tests are green before any change begins

---

## Block A — Schema Version and Constants

### Files

- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionScanner.kt`

### Changes

**SessionStorage.kt:**

- `METADATA_VERSION` constant: change from `4` to `5`
- Add constants:
  - `FILE_CAPTURE_ORIGINAL = "capture-original.jpg"`
  - `FILE_REFERENCE_SOURCE_ORIGINAL_BASE = "reference-source-original"`
- `writeMetadata()`: add `files.captureOriginal`, `files.referenceSourceOriginal`, `reference.sourceUri` (replacing `reference.sourceDisplayName`), `reference.sourceMimeType`

**SessionScanner.kt:**

- `SUPPORTED_VERSIONS`: change from `setOf(2, 3, 4)` to `setOf(2, 3, 4, 5)`
- `validateUnsafe()`: for `version == 5`, additionally read `files.captureOriginal` and `files.referenceSourceOriginal` from the `files` block and verify the corresponding files exist on disk

### Validation

Block A is complete when:

- `testDebugUnitTest` BUILD SUCCESSFUL
- `assembleDebug` BUILD SUCCESSFUL
- No unit test failures

---

## Block B — SessionStorage: New Write Methods

### Files

- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt`

### Changes

**New private helper: `resolveExtensionForMimeType(mimeType: String?): String`**

Maps MIME type to file extension per `SESSION_ORIGINALS_V1.md §6.2`:

- `"image/jpeg"` → `".jpg"`
- `"image/heic"` → `".heic"`
- `"image/heif"` → `".heic"` (normalize to .heic)
- `"image/png"` → `".png"`
- `"image/webp"` → `".webp"`
- `"image/gif"` → `".gif"`
- `"image/avif"` → `".avif"`
- `"image/bmp"` → `".bmp"`
- `null`, empty, or unrecognized → `".bin"` (unknown format; content cannot be assumed to be JPEG)

**New private helper: `resolveSourceUri(context: Context, uri: Uri): Uri`**

Applies `MediaStore.setRequireOriginal()` for media-authority URIs; catches and falls back to the plain URI for all exceptions. Same resolution logic as `ReferenceImageMetadataReader.resolveSourceUri()`. Consider extracting to a shared utility if both implementations would be identical.

**New private method: `writeCaptureOriginal(context: Context, captureMediaStoreUri: Uri, sessionDir: File): String`**

- Resolves source URI (always the committed MediaStore URI — `setRequireOriginal()` NOT applied here; the MediaStore file is already our own committed entry)
- Opens `ContentResolver.openInputStream(captureMediaStoreUri)`
- Copies bytes in 8 KB chunks to `File(sessionDir, FILE_CAPTURE_ORIGINAL)`
- Returns `FILE_CAPTURE_ORIGINAL` (`"capture-original.jpg"`)
- Throws `IOException` if stream cannot be opened or copy fails

**New private method: `writeReferenceSourceOriginal(context: Context, referenceUri: Uri, sessionDir: File): String`**

- Resolves source URI using `resolveSourceUri()` (applies `setRequireOriginal()` for `media` authority)
- Reads MIME type via `context.contentResolver.getType(resolvedUri)` (or falls back to plain `referenceUri` if null)
- Determines extension via `resolveExtensionForMimeType(mimeType)`
- Constructs filename: `"$FILE_REFERENCE_SOURCE_ORIGINAL_BASE$extension"`
- Opens `ContentResolver.openInputStream(resolvedUri)`
- Copies bytes in 8 KB chunks to `File(sessionDir, filename)`
- Returns the constructed filename (e.g., `"reference-source-original.heic"`)
- Throws `IOException` if stream cannot be opened or copy fails

**Updated `saveSession()` internal overload — new write order:**

```
1. captureOriginalFilename = writeCaptureOriginal(...)
2. referenceSourceOriginalFilename = writeReferenceSourceOriginal(...)
3. writeCapture(...)
4. writeReferenceOriginalAndReference(...)
5. writeMetadata(..., captureOriginalFilename, referenceSourceOriginalFilename, mimeType)
```

`writeMetadata()` receives the two new filenames and the MIME type so it can write the correct `files.*` entries.

### Validation

Block B is complete when:

- `testDebugUnitTest` BUILD SUCCESSFUL (existing tests must remain green)
- `assembleDebug` BUILD SUCCESSFUL

Note: full byte-integrity verification requires instrumentation tests (Block E).

---

## Block C — SessionScanner: v5 Validation

### Files

- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionScanner.kt`

### Changes

`validateUnsafe()` extension for v5:

After reading `files.reference` and `files.capture` (existing logic), for `version == 5`:

1. Read `filesObj.getString("captureOriginal")` → `captureOriginalFile`; return null if missing or empty
2. Validate `isSafeFilename(captureOriginalFile)` → return null if unsafe
3. Verify `File(sessionDir, captureOriginalFile).exists()` → return null if missing
4. Read `filesObj.getString("referenceSourceOriginal")` → `referenceSourceOriginalFile`; return null if missing or empty
5. Validate `isSafeFilename(referenceSourceOriginalFile)` → return null if unsafe
6. Verify `File(sessionDir, referenceSourceOriginalFile).exists()` → return null if missing

For versions 2, 3, 4: no change. The new checks are gated strictly on `version == 5`.

`ScannedSession` data class: no changes required. The original filenames are referenced in `metadata.json`'s `files.*` block; the scanner does not need to surface them as scan result fields.

### Validation

Block C is complete when:

- `testDebugUnitTest` BUILD SUCCESSFUL
- `SessionScannerTest` passes (see Block E)
- `assembleDebug` BUILD SUCCESSFUL

---

## Block D — SessionBackupExporter: Metadata-Driven Export

### Files

- `app/src/main/java/com/isardomains/sameview/storage/SessionBackupExporter.kt`

### Changes

Replace the hardcoded `REQUIRED_FILES` list with a **metadata-driven file discovery** approach.

**New private method: `collectSessionFiles(sessionDir: File): List<String>`**

1. Read and parse `metadata.json` from `sessionDir`
2. Read the `"files"` JSONObject
3. Collect all values (filenames) from the `"files"` object
4. Return the collected filenames plus `"metadata.json"` — these are all files to export

On parse failure or missing `"files"` block: throw `IOException` — export fails per the all-or-nothing rule.

**Updated pre-validation in `exportSessions()`:**

Replace the hardcoded `REQUIRED_FILES` iteration with per-session `collectSessionFiles()` calls. Validate that each collected file exists before writing anything.

**Updated export loop:**

Use the per-session file list from `collectSessionFiles()` instead of `REQUIRED_FILES`.

**Why metadata-driven:**

- Handles variable filename extension for `reference-source-original.<ext>`
- Handles v2–v4 sessions (fewer files) and v5 sessions (more files) with the same code
- Future new files added to `files.*` are automatically included in backups
- No hardcoded filenames that could diverge from actual session content

**Backward compatibility:**

- v2/v3/v4 sessions have `files.capture`, `files.reference`, `files.referenceOriginal` → 4 files exported (3 from `files.*` + metadata.json)
- v5 sessions have 5 entries in `files.*` → 6 files exported

### Note on `reference-original.jpg`

The current hardcoded list includes `"reference-original.jpg"` even though `files.referenceOriginal` is not currently validated by `SessionScanner`. With the metadata-driven approach, `reference-original.jpg` is included because it is declared in `files.referenceOriginal`. This is correct behavior.

### Validation

Block D is complete when:

- `testDebugUnitTest` BUILD SUCCESSFUL (existing backup tests must pass)
- `SessionBackupExporterTest` passes (see Block E)
- `assembleDebug` BUILD SUCCESSFUL

---

## Block E — Tests

### Unit Tests

**`SessionStorageTest.kt`** (new or extended):

| # | Test |
|---|---|
| E-U-01 | `saveSession()` creates a v5 session directory containing all 6 expected files |
| E-U-02 | `capture-original.jpg` has the same byte content as the source URI stream |
| E-U-03 | `reference-source-original.jpg` filename is derived correctly for `image/jpeg` MIME type |
| E-U-04 | `reference-source-original.heic` filename is derived correctly for `image/heic` MIME type |
| E-U-05 | `reference-source-original.heic` filename is derived correctly for `image/heif` MIME type (normalizes to `.heic`) |
| E-U-06 | Unknown MIME type produces `reference-source-original.bin` (fallback) |
| E-U-07 | Null MIME type produces `reference-source-original.bin` (fallback) |
| E-U-08 | `metadata.json` contains `files.captureOriginal = "capture-original.jpg"` |
| E-U-09 | `metadata.json` contains `files.referenceSourceOriginal` matching the constructed filename |
| E-U-10 | `metadata.json` contains `reference.sourceUri` (not `reference.sourceDisplayName`) |
| E-U-11 | `metadata.json` contains `reference.sourceMimeType` |
| E-U-12 | `metadata.json` version is 5 |

**`SessionScannerTest.kt`** (extended):

| # | Test |
|---|---|
| E-U-13 | v5 session with all 6 files → valid `ScannedSession` |
| E-U-14 | v5 session missing `capture-original.jpg` → null (invalid) |
| E-U-15 | v5 session missing `reference-source-original.heic` → null (invalid) |
| E-U-16 | v5 session with `files.captureOriginal` referencing a file that does not exist → null |
| E-U-17 | v5 session with `files.referenceSourceOriginal` referencing a file that does not exist → null |
| E-U-18 | v4 session without `files.captureOriginal` → valid `ScannedSession` (backward compat) |
| E-U-19 | v3 session without new files → valid `ScannedSession` (backward compat) |
| E-U-20 | v2 session without new files → valid `ScannedSession` (backward compat) |

**`SessionBackupExporterTest.kt`** (extended):

| # | Test |
|---|---|
| E-U-21 | v5 session backup: ZIP contains all 6 required files |
| E-U-22 | v4 session backup: ZIP contains original 4 files (backward compat) |
| E-U-23 | Metadata-driven: `collectSessionFiles()` returns filenames matching `files.*` values plus `metadata.json` |
| E-U-24 | Missing file declared in `files.*` → pre-validation fails → BackupResult.Failure |

**`resolveExtensionForMimeTypeTest.kt`** (new — JVM unit test):

Cover all MIME type mappings from `SESSION_ORIGINALS_V1.md §6.2`.

### Instrumentation Tests

**`SessionStorageOriginalsTest.kt`** (new — `androidTest/`):

| # | Test |
|---|---|
| E-I-01 | Full v5 session save on device: all 6 files present in session directory |
| E-I-02 | `capture-original.jpg`: byte-for-byte identical to the MediaStore file |
| E-I-03 | `reference-source-original.<ext>`: byte-for-byte identical to the source URI stream |
| E-I-04 | HEIC reference source: extension is `.heic`, file is valid HEIC |
| E-I-05 | v4 session (simulated): scan succeeds without new files |
| E-I-06 | v5 session with missing `capture-original.jpg`: scan returns null |

### Regression Guard

The following must remain green after all blocks are complete:

- All existing `SessionScannerTest` tests
- All existing `SessionStorageMetadataTest` tests
- All existing `SessionBackupExporterTest` tests
- All existing `CompareScreenTest` tests
- All existing `CompareLibraryScreenTest` tests
- `testDebugUnitTest` full suite
- `connectedDebugAndroidTest` full suite

---

## Block F — EditSessionViewModel Compatibility

### Files

- `app/src/main/java/com/isardomains/sameview/ui/compare/EditSessionViewModel.kt`

### Changes

`EditSessionViewModel` reads `reference.sourceDisplayName` from `metadata.json` to display the reference file name in `EditSessionScreen`.

With v5, the field is renamed to `reference.sourceUri`.

**`metadataReader` (injectable lambda) must be updated to read:**

```
referenceSourceDisplayName = json.optJSONObject("reference")
    ?.let { ref ->
        ref.optString("sourceUri").ifEmpty { null }
            ?: ref.optString("sourceDisplayName").ifEmpty { null }
    } ?: ""
```

This reads `sourceUri` first (v5), falls back to `sourceDisplayName` (v2–v4), and returns empty string if neither is present.

No other changes to `EditSessionViewModel` or `EditSessionScreen`.

### Validation

Block F is complete when:

- `EditSessionViewModelTest` — all existing tests pass
- `EditSessionScreenTest` — all existing tests pass
- `assembleDebug` BUILD SUCCESSFUL

---

## Implementation Order and Dependencies

```
Block A → Block B → Block C → Block D → Block E → Block F
```

- Block A (constants) must precede Block B (write logic uses new constants)
- Block B (write logic) must precede Block C (scanner validates what write produces)
- Block D (backup exporter) can be implemented in parallel with Block C
- Block E (tests) is written incrementally alongside each block
- Block F (EditSessionViewModel compat) can be implemented after Block A (when v5 is defined)

---

## Explicitly Out of Scope

- CompareScreen — no changes
- CompareLibraryScreen — no changes
- ReferenceRenderer — no changes
- VideoExportPipeline — no changes
- ShareImageRenderer — no changes
- CameraViewModel — no changes (context and captureMediaStoreUri are already passed to SessionStorage)
- MediaStoreWriter — no changes
- Any UI component — no changes
- backup_rules.xml — no changes (sessions/ already excluded)
- data_extraction_rules.xml — no changes (sessions/ already excluded)
- Any high-quality export, print, or re-render feature — not in scope for this plan
