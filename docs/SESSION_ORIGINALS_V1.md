# SESSION_ORIGINALS_V1.md

## 1. Document Status

This document is the **authoritative specification** for the session originals feature in SameView.

It governs the two new session files introduced in schema version 5:

- `capture-original.jpg`
- `reference-source-original.<ext>`

It is written for:

- AI coding systems
- Implementation sessions
- Analysis sessions
- Regression-safe follow-up work

If an implementation proposal conflicts with this document, this document wins.

Cross-references:

- Rendering contract: `COMPARE_SESSION_RENDERING_V1.md`
- Metadata schema: `SESSION_METADATA_V1.md`
- Backup export: `SESSION_BACKUP_EXPORT_V1.md`
- Implementation: `implementation_plans/historic/SESSION_ORIGINALS_IMPLEMENTATION_PLAN.md`

---

## 2. Purpose

A SameView session must be fully portable and self-contained.

Before this feature, re-use of session data at original quality depended on:

- The MediaStore entry of the captured photo (device-local, can be deleted)
- The original reference image picker URI (ephemeral, cannot be resolved on another device)

With this feature, every new session stores byte-for-byte copies of both source files at creation time, inside the session directory. The session then contains all data needed for:

- Original-quality export and print
- Re-rendering at higher quality
- Import and restore on a different device
- Future workflows that require the unmodified source material

---

## 3. Product Decisions

### 3.1 No Setting

There is no opt-in, opt-out, or global preference for original file storage.

All new sessions always save both original files.

There is no "Store originals" toggle.

### 3.2 Compare Is Unchanged

`CompareScreen` continues to render exclusively from:

- `capture.jpg`
- `reference.jpg`

The original files must never influence compare rendering, display mode, slider behavior, or any existing session UI.

### 3.3 No Partial Sessions

If either original file cannot be written, the session is treated as failed:

- The session directory is deleted
- The session does not appear in the library
- The MediaStore photo already written to `Pictures/SameView` remains intact

### 3.4 Backward Compatibility

Sessions at schema versions 2, 3, and 4 remain fully valid.

The absence of original files in older sessions is not an error.

A future high-quality export or re-render feature must gracefully degrade for sessions without originals (v2–v4) rather than crashing or blocking.

---

## 4. Complete Session File Structure (v5)

A schema version 5 session contains exactly these files:

```
<sessionId>/
├── capture.jpg
├── capture-original.jpg
├── reference.jpg
├── reference-original.jpg
├── reference-source-original.<ext>
└── metadata.json
```

All six files are required for a v5 session to be considered valid.

---

## 5. File Definitions

### 5.1 capture.jpg — Unchanged

The JPEG compare image written from the in-memory bitmap at capture time.

- Quality: 90
- Re-encoded from the in-memory bitmap (not a copy of the MediaStore file)
- May include GPS EXIF when Recreation Guidance is active
- Used exclusively for compare rendering in `CompareScreen`

### 5.2 capture-original.jpg — New

A **byte-for-byte copy** of the file that was committed to MediaStore as `Pictures/SameView/SameView_*.jpg`.

- Not re-encoded
- Not re-compressed
- Not re-rendered
- Not re-sampled
- Written by copying the raw bytes from the MediaStore URI via `ContentResolver.openInputStream(captureMediaStoreUri)`
- Quality is inherited from `MediaStoreWriter` (currently quality 95)
- Includes all EXIF tags written by `MediaStoreWriter`, including GPS EXIF when present
- This file is the authoritative high-quality capture source for future export and print workflows

**Privacy mode exception:** When the `strip_originals_metadata` setting is ON, `capture-original.jpg` is written at full resolution and JPEG quality 95 but without EXIF, GPS, camera, or device metadata. See `SESSION_ORIGINALS_PRIVACY_V1.md` for the complete specification.

**The MediaStore URI (`capture.mediaStoreUri`) remains in metadata.json for diagnostic provenance. It must never be used for file resolution by any future import or restore feature — `capture-original.jpg` is the authoritative source.**

### 5.3 reference.jpg — Unchanged

The deterministically rendered compare reference image. See `COMPARE_SESSION_RENDERING_V1.md` for the authoritative definition.

### 5.4 reference-original.jpg — Unchanged

The EXIF-oriented reference image, re-encoded as JPEG at quality 90. See `COMPARE_SESSION_RENDERING_V1.md` for the authoritative definition.

### 5.5 reference-source-original.\<ext\> — New

A **byte-for-byte copy** of the original reference source file as it was read from the picker or SAF URI at session creation time.

- Not re-encoded
- Not re-compressed
- Not EXIF-oriented
- Not JPEG-normalized
- Not decoded at all
- Written by copying the raw bytes from the reference image URI via `ContentResolver.openInputStream`
- The file extension reflects the actual source format (see §6)
- Includes all original EXIF tags, including GPS if present in the source
- This file is the authoritative original-quality reference source for future re-render and export workflows

**The reference source URI (`reference.sourceUri`) remains in metadata.json for diagnostic provenance. It must never be used for file resolution by any future import or restore feature — `reference-source-original.<ext>` is the authoritative source.**

**Privacy mode exception:** When the `strip_originals_metadata` setting is ON, `reference-source-original.<ext>` is written at full resolution without EXIF, GPS, camera, or device metadata. The file extension may change (e.g., `.heic` → `.jpg`) when format conversion is required to remove metadata reliably. See `SESSION_ORIGINALS_PRIVACY_V1.md` for the complete specification and format matrix.

### 5.6 metadata.json — Updated (v5)

Stores schema version, session identity, file references, capture geometry, GPS, and user content. See `SESSION_METADATA_V1.md` for the complete v5 schema definition.

Key v5 additions relevant to this feature:

- `files.captureOriginal` — filename of the capture original file (always `"capture-original.jpg"`)
- `files.referenceSourceOriginal` — filename of the reference source original file (e.g., `"reference-source-original.heic"`)
- `reference.sourceUri` — replaces `reference.sourceDisplayName`; stores the reference picker URI as a device-local provenance string
- `reference.sourceMimeType` — the MIME type of the reference source at picker time (e.g., `"image/heic"`)

---

## 6. Extension Policy for reference-source-original

The file extension of `reference-source-original` is determined at session creation time from the MIME type of the reference source URI.

### 6.1 URI Resolution for MIME Type

The same URI resolution logic used in `ReferenceImageMetadataReader` applies:

- For URIs with authority `media` (Photo Picker / MediaStore): apply `MediaStore.setRequireOriginal()` to obtain the original-format MIME type. Catch `UnsupportedOperationException`, `SecurityException`, `IllegalArgumentException` and fall back to the plain URI.
- For SAF URIs and other providers: use the URI directly.

The MIME type is read via `ContentResolver.getType(resolvedUri)`.

### 6.2 MIME Type to Extension Mapping

| MIME Type | Extension | Notes |
|---|---|---|
| `image/jpeg` | `.jpg` | Standard JPEG |
| `image/heic` | `.heic` | Apple High Efficiency Image |
| `image/heif` | `.heic` | Normalize: HEIF and HEIC use the same container |
| `image/png` | `.png` | PNG |
| `image/webp` | `.webp` | WebP |
| `image/gif` | `.gif` | Animated GIF (unusual reference source) |
| `image/avif` | `.avif` | AV1 Image Format |
| `image/bmp` | `.bmp` | Bitmap |
| `null` / unrecognized | `.bin` | Safe fallback; unknown format, content cannot be assumed to be JPEG |

### 6.3 Byte-Copy Uses the Resolved URI

The byte-copy of the reference source is performed using the same resolved URI (with `setRequireOriginal()` for media authority). This ensures the bytes copied match the format reported by the MIME type.

### 6.4 Filename

The filename is constructed as `reference-source-original` + the resolved extension, e.g.:

- `reference-source-original.jpg`
- `reference-source-original.heic`
- `reference-source-original.png`
- `reference-source-original.bin` (null or unrecognized MIME type)

The actual filename is stored in `files.referenceSourceOriginal` in `metadata.json`.

---

## 7. Save Order

The file write order inside `SessionStorage.saveSession()` for v5 sessions is:

```
Step 1: capture-original.jpg   — byte-copy from captureMediaStoreUri (fast, IO-only)
Step 2: reference-source-original.<ext>  — byte-copy from referenceImageUri (fast, IO-only)
Step 3: capture.jpg            — JPEG compress from in-memory bitmap
Step 4: reference-original.jpg — decode, orient, re-encode (memory-intensive)
Step 5: reference.jpg          — render from oriented bitmap (memory-intensive)
Step 6: metadata.json          — LAST (atomicity anchor)
```

### 7.1 Rationale

Byte-copies (steps 1 and 2) are performed first because:

- They are fast IO operations with no bitmap decoding or rendering
- They require no additional memory beyond a small copy buffer
- Capturing them early avoids any dependency on long-lived bitmap memory during the expensive rendering steps
- If an `OutOfMemoryError` occurs during rendering (steps 4–5), the originals were already secured; the catch block will delete the entire session directory regardless

### 7.2 URI Validity at Save Time

At the time `SessionStorage.saveSession()` is called:

- `captureMediaStoreUri` is valid: `MediaStoreWriter.save()` has already cleared `IS_PENDING=0` before returning, fully committing the file.
- `snapshot.referenceImageUri` is valid: the reference was selected in the same foreground app session and the ContentResolver grant is still active.

---

## 8. Atomicity

### 8.1 Selected Strategy: Write-Metadata-Last

The v5 implementation uses the same atomicity strategy as v2–v4: **write `metadata.json` last**.

`SessionScanner` requires a valid, parseable `metadata.json` to recognize a session. Any session directory without `metadata.json` is invisible to the scanner. Any session directory with a corrupt or truncated `metadata.json` (from an in-progress write or crash) is treated as absent by the scanner.

### 8.2 Cleanup on Failure

Any exception or `OutOfMemoryError` in any step of `saveSession()` triggers `sessionDir.deleteRecursively()`. This removes all partially written files, including originals written in steps 1 and 2. No partial session remains.

### 8.3 Why No Temp Directory

A temp-directory pattern (write to `<id>_tmp`, rename to `<id>` on success) was evaluated and rejected:

1. `metadata.json` written last already achieves the critical invariant: the scanner cannot see an incomplete session.
2. Orphaned temp directories from crashes need explicit cleanup logic, adding complexity.
3. The cleanup risk (corrupt metadata.json visible to scanner) is mitigated by `JSONException` catching in `SessionScanner.validateUnsafe()`.
4. The atomicity window (crash after successful `metadata.json` write) already produces a valid session — all files were written before `metadata.json`.

The write-metadata-last approach is sufficient for the v5 file set.

### 8.4 Scanner Validation for v5

For v5 sessions, `SessionScanner` must validate that the files referenced in `files.captureOriginal` and `files.referenceSourceOriginal` exist on disk, in addition to the existing `files.reference` and `files.capture` checks. If either original file is missing from a v5 session, the session is treated as corrupt and skipped.

For v2–v4 sessions, these checks do not apply.

---

## 9. Backward Compatibility

| Schema Version | capture-original.jpg | reference-source-original.\<ext\> | Valid? |
|---|---|---|---|
| 2 | Absent | Absent | Yes |
| 3 | Absent | Absent | Yes |
| 4 | Absent | Absent | Yes |
| 5 | Required | Required | Yes |

Sessions at versions 2–4 without original files are fully valid. Their absence is not an error in any context: scanner, compare rendering, backup export, or future export features.

Future export or re-render features that use original files must check for the presence of `files.captureOriginal` and `files.referenceSourceOriginal` in `metadata.json` before attempting to use them, and must offer a graceful degradation path for sessions that lack them.

---

## 10. What the Original Files Must NOT Be Used For

- `capture-original.jpg` and `reference-source-original.<ext>` must never be loaded by `CompareScreen`
- They must never affect compare rendering, `ContentScale`, viewport geometry, or divider position
- They must never be used as the `captureImageUri` or `referenceImageUri` passed to `CompareInput`
- `SessionScanner` must not surface them as compare images
- They must not be rendered into any video frame or share image unless a future high-quality export feature explicitly opts in
- The rendering contract defined in `COMPARE_SESSION_RENDERING_V1.md` is not modified by this feature

---

## 11. Storage and Privacy

### 11.1 Storage Location

Both new files are written to the session directory under `filesDir/sessions/<sessionId>/`. This is app-internal private storage.

### 11.2 Auto Backup and Data Extraction

The existing `backup_rules.xml` and `data_extraction_rules.xml` already exclude `sessions/` from Android Auto Backup and cloud device transfer. The new files are in the same directory and are excluded automatically. No changes to backup/extraction rules are required.

### 11.3 Storage Size Impact

Sessions will be significantly larger. Typical increase per session:

- `capture-original.jpg`: 3–8 MB (quality-95 JPEG, similar to MediaStore photo)
- `reference-source-original.<ext>`: 2–15 MB depending on format (HEIC may be smaller than JPEG for same resolution)
- Estimated session size increase: from ~4 MB to ~10–25 MB per session

This is inherent to the feature goal of storing full-quality originals. No mitigation is applied.

### 11.4 EXIF and GPS in Original Files

- `capture-original.jpg` contains all EXIF tags written by `MediaStoreWriter`, including GPS coordinates when Recreation Guidance was active. This mirrors what is already in the MediaStore photo.
- `reference-source-original.<ext>` contains all original EXIF tags from the source file, including GPS if present. GPS preservation was already implemented for `reference-original.jpg`; the source original goes further by preserving all original bytes without any re-encoding.

### 11.5 Session Backup ZIP

The session backup ZIP (defined in `SESSION_BACKUP_EXPORT_V1.md`) includes all original files byte-for-byte. A backup of a v5 session therefore includes full-resolution original images with all EXIF data. This is consistent with the full-fidelity backup principle.

### 11.6 No New Permissions

No new permissions are required. Both source URIs (`captureMediaStoreUri` and `referenceImageUri`) are accessible to the app at session creation time via existing ContentResolver grants.

---

## 12. Future Use Cases Enabled by This Feature

These are documented intent, not current implementation requirements:

- **Original-quality export**: Use `capture-original.jpg` as the capture input for a future HQ image export
- **Print workflow**: Use both originals at full resolution for high-DPI print output
- **Re-render**: Use `reference-source-original.<ext>` to re-render `reference.jpg` with different viewport or overlay geometry without needing the picker URI
- **Import/Restore**: Import a backup ZIP on a different device; all session data is self-contained without access to MediaStore or picker URI
- **Future website viewer**: Upload originals for a high-quality web comparison experience

---

## 13. Relationships to Other Specifications

| Specification | Relationship |
|---|---|
| `COMPARE_SESSION_RENDERING_V1.md` | Authoritative for the rendering contract. `capture-original.jpg` and `reference-source-original.<ext>` are added to the session file structure. The compare rendering pipeline is unchanged. |
| `SESSION_METADATA_V1.md` | Defines the v5 schema fields that reference the new files. `files.captureOriginal`, `files.referenceSourceOriginal`, `reference.sourceUri`, and `reference.sourceMimeType` are introduced in v5. |
| `SESSION_BACKUP_EXPORT_V1.md` | Backup includes all session files declared in `files.*`. V5 sessions include 2 additional files. The exporter uses a metadata-driven file list. |
| `implementation_plans/historic/SESSION_ORIGINALS_IMPLEMENTATION_PLAN.md` | Defines the implementation blocks for this feature. |
| `CLAUDE_PROJECT_INSTRUCTION.md` | "Session Storage Contents" section is updated to list the v5 file set. |
| `IMPLEMENTATION_NOTES.md` | Must be updated after implementation is verified on device. |
