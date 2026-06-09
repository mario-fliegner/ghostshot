# SESSION_METADATA_V4_IMPLEMENTATION_PLAN.md

## 1. Document Status

### 1.1 Purpose

This document is the **working implementation plan** for the metadata.json v4 migration in SameView.

It is written for:
- AI coding systems
- Implementation sessions
- Code review and regression-safe follow-up work

It supplements `SESSION_METADATA_V1.md` without replacing it. Where this document conflicts with `SESSION_METADATA_V1.md`, this document reflects the **actual code state** and the decisions made during the v4 analysis session (2026-06-09). Discrepancies between `SESSION_METADATA_V1.md` and the code are listed explicitly in Section 4.

### 1.2 Authoritative Sources

| Document | Role |
|---|---|
| `SESSION_METADATA_V1.md` | Schema definition, field semantics, product philosophy |
| `SESSION_BACKUP_EXPORT_V1.md` | Accurate description of current metadata.json structure; backup export contract |
| `COMPARE_SESSION_RENDERING_V1.md` | Rendering contract fields — immutable after save |
| `GPS_RECREATION_SYSTEM_V1.md` | captureLocation / referenceLocation semantics; reverse geocoding prohibition |
| `VIDEO_EXPORT_V1.md` | VideoExportPipeline reads viewport.width/height from metadata.json |
| `IMPLEMENTATION_NOTES.md` | Current verified implementation state |
| `CLAUDE_PROJECT_INSTRUCTION.md` | Architecture constraints, change discipline |

### 1.3 Scope of This Document

This document covers:
- The actual current metadata.json structure as written by `SessionStorage.kt`
- The delta from current state to v4 target
- Implementation blocks with file-level scope, risks, and test requirements
- Progress tracking

This document does NOT cover:
- Compare UI layout or slider behavior
- GPS guidance system implementation
- Video rendering pipeline
- Session backup ZIP format

---

## 2. Fixed Product Decisions

The following decisions are final. They must not be re-evaluated or re-discussed during implementation.

### 2.1 Nested Block Structure

The v4 metadata.json uses a nested block structure. This decision is closed.

```json
{
  "version": 4,
  "session": { },
  "files": { },
  "viewport": { },
  "overlay": { },
  "captureLocation": { },
  "referenceLocation": { },
  "capture": { },
  "reference": { },
  "content": { },
  "location": { },
  "additional": { }
}
```

Flat vs. nested is not a discussion item. The code already uses nested blocks for most fields. v4 completes this structure.

### 2.2 capture.timestampMs is the Canonical Capture Timestamp

`capture.timestampMs` is the single authoritative capture timestamp in v4. It is:
- Set by the system at session creation from the device clock
- Immutable after save
- Independent of EXIF data in any image file
- Not user-editable under any circumstance

The existing field `session.createdAtMs` remains in the schema for backward compatibility. Both fields carry the same value. `capture.timestampMs` is canonical for all new consumers.

### 2.3 reference.date is User Knowledge, Not EXIF Truth

`reference.date` represents what the user knows about when the reference photo was taken. EXIF `DateTimeOriginal` may suggest an initial value, but the user's judgment is always authoritative.

- EXIF auto-population sets `reference.dateSource = "exif"` and `reference.userEdited = false`
- Manual user input sets `reference.dateSource = "manual"` and `reference.userEdited = true`
- A `"manual"` source must never automatically revert to `"exif"`

### 2.4 Year-Only Date is Explicitly Valid

The minimum valid `reference.date` is a four-digit year string (`"YYYY"`). Supported precision levels:

- `"2008"` — year only — **valid minimum**
- `"2008-06"` — year and month
- `"2008-06-15"` — full date

### 2.5 GPS and User Location are Independent Systems

`captureLocation` and `referenceLocation` are GPS measurements governed by `GPS_RECREATION_SYSTEM_V1.md`. They remain top-level fields.

`location.displayName`, `location.city`, `location.country` are user-authored free text. These two systems never automatically interact. Reverse geocoding is forbidden by `GPS_RECREATION_SYSTEM_V1.md` §12.

### 2.6 No Migration of Existing Dev Sessions

The app is not published. Dev sessions may be deleted. No migration path is needed for v2/v3 sessions on developer devices.

New v4 readers must still read v2 and v3 sessions correctly, because the scanner must remain backward-compatible. But no conversion, no backfill, no data rescue tools are required or planned.

### 2.7 User Content Must Never Influence Compare Rendering

`reference.date`, `content.description`, `content.tags`, `location.*`, `additional.*` must never affect `ReferenceRenderer.render()`, `ContentScale`, overlay geometry, viewport, or divider position.

---

## 3. Actual Current Code State

### 3.1 What SessionStorage.writeMetadata() Actually Writes (Version 3)

The current `metadata.json` produced by `SessionStorage.kt` is already a **nested block structure**, not the flat structure described in `SESSION_METADATA_V1.md §5.2`. The actual schema as written by the code:

```
version                              Integer    3 (currently)

session.id                           String     Session directory name
session.createdAtMs                  Long       Capture time — current canonical timestamp

files.capture                        String     "capture.jpg"
files.reference                      String     "reference.jpg"
files.referenceOriginal              String     "reference-original.jpg"

content.description                  null       PRE-POPULATED — violates §12.1
content.tags                         []         PRE-POPULATED — violates §12.1

capture.mediaStoreUri                String     MediaStore URI (device-local)

captureLocation                      Object?    Top-level GPS fix (optional)
referenceLocation                    Object?    Top-level GPS from reference EXIF (optional)

reference.sourceDisplayName          String     Reference image source URI (device-local)
reference.originalWidth              Int        Raw image width before EXIF rotation
reference.originalHeight             Int        Raw image height before EXIF rotation
reference.orientedWidth              Int        Width after EXIF orientation applied
reference.orientedHeight             Int        Height after EXIF orientation applied
reference.exifOrientation            Int?       EXIF orientation constant or null

viewport.width                       Int        Viewport width in pixels at capture
viewport.height                      Int        Viewport height in pixels at capture
viewport.orientation                 String     "PORTRAIT" or "LANDSCAPE"

overlay.scale                        Float      Overlay scale frozen at capture
overlay.offsetX                      Float      Overlay X offset frozen at capture
overlay.offsetY                      Float      Overlay Y offset frozen at capture
overlay.displayMode                  String     Reference image display mode enum

rendering.referenceBackgroundColor   String     "#17202F"
rendering.referenceJpegQuality       Int        90
```

### 3.2 Already v4-Aligned (No Change Required)

| Field / Block | Status |
|---|---|
| `session.id` | Nested — correct |
| `files.*` | Nested — correct |
| `viewport.*` | Nested — correct |
| `overlay.*` | Nested — correct |
| `rendering.*` | Present — not in spec but harmless |
| `capture.mediaStoreUri` | Matches v4 spec |
| `captureLocation` | Top-level — correct per spec |
| `referenceLocation` | Top-level — correct per spec |
| `reference.sourceDisplayName` | Name differs from spec (`sourceUri`) but is authoritative per `SESSION_BACKUP_EXPORT_V1.md §5.2` — no rename |
| `location.*` absent at creation | §12.1-compliant |
| `content.title` only via updateTitle() | Correct — user-authored only |

### 3.3 What the VideoExportPipeline Reads

`VideoExportPipeline.readViewport()` reads `json.optJSONObject("viewport")` → `width` / `height`. This path is stable across the v4 migration. No change required. No regression risk.

### 3.4 What SessionScanner Currently Reads

| Field read | Path |
|---|---|
| `version` | `json.getInt("version")` |
| `timestamp` | `sessionObj.getLong("createdAtMs")` where `sessionObj = json.getJSONObject("session")` |
| `referenceFile` | `filesObj.getString("reference")` |
| `captureFile` | `filesObj.getString("capture")` |
| `title` | `json.optJSONObject("content")?.optString("title", "")` |

`SUPPORTED_VERSIONS = setOf(2, 3)` — v4 currently rejected.

---

## 4. Conflicts Between SESSION_METADATA_V1.md and Current Code

### K-01: §5.2 Describes v3 as Flat — Code is Already Nested

`SESSION_METADATA_V1.md §5.2` lists `sessionTimestampMs`, `sessionId`, `overlayScale`, `viewportWidth` etc. as top-level flat fields. These field names do not exist anywhere in the code. The actual fields are `session.createdAtMs`, `overlay.scale`, `viewport.width` — all nested.

**Impact on migration:** The fallback strategy in §6.4 ("v4 readers fall back to flat `sessionTimestampMs`") is inapplicable — `sessionTimestampMs` as a flat field never existed. The real fallback must go from `capture.timestampMs` → `session.createdAtMs`.

### K-02: §6.2 Says Rendering Fields Stay Flat in v4 — Already Nested in Code

Spec §6.2 states: `"--- Rendering contract (flat, unchanged from v3) ---"` with fields like `overlayScale`, `viewportWidth`. The code has always used `overlay.scale`, `viewport.width` (nested). No migration is needed — the fields are already in their final location.

### K-03: reference.sourceUri (Spec) vs. reference.sourceDisplayName (Code)

`SESSION_METADATA_V1.md §6.2` defines `reference.sourceUri`. Code uses `reference.sourceDisplayName`. `SESSION_BACKUP_EXPORT_V1.md §5.2` explicitly uses `reference.sourceDisplayName` and the future importer algorithm in §11.3 references it by this name.

**Decision:** `reference.sourceDisplayName` is the authoritative field name. The spec §6.2 has a documentation error. No rename is performed.

### K-04: §12.1 Prohibits Pre-Population of content.description and content.tags

`SESSION_METADATA_V1.md §12.1`: `content.description` and `content.tags` must be absent at creation. Current `SessionStorage.writeMetadata()` writes `"description": null` and `"tags": []` unconditionally.

**Impact:** This is an active spec violation. Addressed in Block C.

### K-05: capture.timestampMs Missing — session.createdAtMs is Current Carrier

Spec defines `capture.timestampMs` as the canonical capture timestamp. Code carries this value in `session.createdAtMs`. The `capture.timestampMs` field in the `capture` block does not exist yet.

**Impact:** SessionScanner reads from `session.createdAtMs`. After Block A, it will read from `capture.timestampMs` with fallback to `session.createdAtMs` for v2/v3 sessions.

### K-06: SUPPORTED_VERSIONS = {2, 3} — v4 Actively Rejected

`SessionScannerTest.v4_isRejected()` at line 445 confirms: v4 sessions are currently ignored by the scanner. Must be inverted in Block A.

### K-07: additional Block Completely Absent

`additional.isFavorite`, `additional.visibility`, `additional.source` are not written at session creation. The entire block is missing from `writeMetadata()`. Addressed in Block B.

### K-08: SessionStorageMetadataTest Asserts version = 3

`metadataFile_containsVersion3()` at line 148. Must be updated to version = 4 in Block A.

### K-09: SESSION_BACKUP_EXPORT_V1.md §5.1 and SESSION_METADATA_V1.md §5.2 Disagree

`SESSION_BACKUP_EXPORT_V1.md §5.1` accurately describes the actual nested structure (session, files, capture, reference, viewport, overlay, rendering blocks). `SESSION_METADATA_V1.md §5.2` describes a flat structure that never existed in the code.

**Consequence:** `SESSION_BACKUP_EXPORT_V1.md §5.1` is the accurate mirror of the actual code state. When the two documents conflict on structure, the code and the backup spec win.

---

## 5. Target Architecture v4

### 5.1 Complete v4 Schema

```json
{
  "version": 4,
  "session": {
    "id": "2026-06-08_14-30-00",
    "createdAtMs": 1749386200000
  },
  "files": {
    "capture": "capture.jpg",
    "reference": "reference.jpg",
    "referenceOriginal": "reference-original.jpg"
  },
  "viewport": {
    "width": 1080,
    "height": 1920,
    "orientation": "PORTRAIT"
  },
  "overlay": {
    "scale": 1.0,
    "offsetX": 0.0,
    "offsetY": 0.0,
    "displayMode": "COMPARE_WITH_PREVIEW"
  },
  "rendering": {
    "referenceBackgroundColor": "#17202F",
    "referenceJpegQuality": 90
  },
  "captureLocation": {
    "latitude": 47.421,
    "longitude": 10.985,
    "altitude": 2962.0,
    "accuracyMeters": 8.5,
    "provider": "gps",
    "fixTimestampMs": 1749386195000
  },
  "referenceLocation": {
    "latitude": 47.420,
    "longitude": 10.984,
    "altitude": 2960.0,
    "source": "exif"
  },
  "capture": {
    "timestampMs": 1749386200000,
    "mediaStoreUri": "content://media/external/images/media/1234"
  },
  "reference": {
    "sourceDisplayName": "content://com.google.android.apps.photos.../...",
    "originalWidth": 3024,
    "originalHeight": 4032,
    "orientedWidth": 3024,
    "orientedHeight": 4032,
    "exifOrientation": 1,
    "date": "2008-06",
    "dateSource": "exif",
    "userEdited": false
  },
  "content": {
    "title": "Zugspitze 2026"
  },
  "location": {
    "displayName": "Zugspitze Summit",
    "city": "Garmisch-Partenkirchen",
    "country": "Deutschland",
    "userEdited": true
  },
  "additional": {
    "isFavorite": false,
    "visibility": "private",
    "source": "sameview"
  }
}
```

Notes:
- `content` block is absent at creation unless a title is already present (§12.1)
- `location` block is absent at creation (§12.1)
- `reference.date`, `reference.dateSource`, `reference.userEdited` present only when EXIF DateTimeOriginal was successfully read

### 5.2 Block Responsibilities

| Block | Responsible for |
|---|---|
| `session` | Session identity and creation timestamp (immutable) |
| `files` | Filename contract for all four session files (immutable) |
| `viewport` | Frozen viewport geometry used for reference.jpg rendering (immutable) |
| `overlay` | Frozen overlay transform used for reference.jpg rendering (immutable) |
| `rendering` | Rendering parameters used for reference.jpg (immutable) |
| `captureLocation` | GPS fix at capture time (immutable, top-level) |
| `referenceLocation` | GPS coordinates from reference image EXIF (immutable, top-level) |
| `capture` | Canonical capture timestamp and MediaStore URI (immutable after save) |
| `reference` | Reference image metadata and user-authoritative date (date fields mutable) |
| `content` | User-authored session context — title, description, tags (mutable) |
| `location` | User-authored location context — display name, city, country (mutable) |
| `additional` | Session-level flags and provenance — isFavorite, visibility, source (mutable except source) |

### 5.3 Immutability Rules

After session save, the following blocks and fields must never be modified:
- `session.*` (entire block)
- `files.*` (entire block)
- `viewport.*` (entire block)
- `overlay.*` (entire block)
- `rendering.*` (entire block)
- `captureLocation` (entire object)
- `referenceLocation` (entire object)
- `capture.timestampMs`
- `capture.mediaStoreUri`
- `reference.sourceDisplayName`
- `reference.originalWidth`, `reference.originalHeight`, `reference.orientedWidth`, `reference.orientedHeight`, `reference.exifOrientation`
- `additional.source`

The following may be modified after session save via explicit user action:
- `content.title`, `content.description`, `content.tags`
- `reference.date`, `reference.dateSource`, `reference.userEdited`
- `location.*`
- `additional.isFavorite`, `additional.visibility`

---

## 6. Implementation Blocks

---

### Block A — capture.timestampMs + METADATA_VERSION 4 + SUPPORTED_VERSIONS

**Status:** Completed (2026-06-09)

**Goal:**
Add `capture.timestampMs` as the canonical capture timestamp in v4 sessions. Bump `METADATA_VERSION` to 4. Update `SessionScanner` to accept v4 sessions and read `capture.timestampMs` as the primary timestamp source with fallback to `session.createdAtMs` for v2/v3 sessions.

**Scope:**
- `SessionStorage.kt` — `METADATA_VERSION = 4`; add `timestampMs` to the `capture` block in `writeMetadata()`; value equals `sessionTimestampMs` (same instant); `session.createdAtMs` remains unchanged for backward compatibility
- `SessionScanner.kt` — add `4` to `SUPPORTED_VERSIONS`; timestamp read logic: attempt `capture.timestampMs` from `capture` block first; fall back to `session.createdAtMs` if `capture` block absent or `capture.timestampMs` missing/zero; `capture` block is not required (v2/v3 sessions have no `capture` block)
- `SessionScannerTest.kt` — invert `v4_isRejected()` to `v4_isAccepted()`; add tests for timestamp primary path and fallback path
- `SessionStorageMetadataTest.kt` — update `metadataFile_containsVersion3()` to assert version 4; add test for `capture.timestampMs` presence and correctness

**Affected Files:**
- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionScanner.kt`
- `app/src/androidTest/java/com/isardomains/sameview/storage/SessionScannerTest.kt`
- `app/src/androidTest/java/com/isardomains/sameview/storage/SessionStorageMetadataTest.kt`

**Not in Scope:**
- No flat `sessionTimestampMs` field (never existed in the code — not introduced)
- No migration of `session.createdAtMs` in existing sessions
- No changes to `ScannedSession` data class
- No UI changes
- No VideoExportPipeline changes (reads `viewport.*`, not `capture.timestampMs`)
- No SessionBackupExporter changes (copies metadata.json byte-for-byte)

**Risks:**
- **Medium:** `v4_isRejected()` is an explicitly opposing test case — must be actively inverted, not simply removed
- **Low:** Fallback priority must be implemented correctly; wrong order breaks library timestamp sort; mitigated by test coverage
- **Low:** `metadataFile_containsVersion3()` fails at version = 4 — must be updated

**Required Tests:**
- `v4_isAccepted` — scanner returns session for version 4
- `v4_sessionWithCaptureTsMs_timestampReadCorrectly` — scanner reads `capture.timestampMs` in v4
- `v3_sessionWithoutCaptureBlock_fallsBackToSessionCreatedAtMs` — fallback path for v3
- `metadataFile_containsVersion4` (replaces `containsVersion3`)
- `metadataFile_capture_containsTimestampMs_greaterThanZero`
- `metadataFile_capture_timestampMs_equalsSessionCreatedAtMs` — both fields carry same value

**Definition of Done:**
- `METADATA_VERSION = 4`
- All new sessions written with `capture.timestampMs` in `capture` block
- Scanner accepts versions {2, 3, 4}
- Scanner reads `capture.timestampMs` for v4, `session.createdAtMs` for v2/v3
- All listed tests pass
- Full `testDebugUnitTest` suite green
- Full `connectedDebugAndroidTest` suite green
- `assembleRelease` build successful

**Real-Device Validation Required:** No — pure file-based logic, fully covered by instrumentation tests.

**Test Results (2026-06-09):**
- `v4_isAccepted` — implemented (inverted from `v4_isRejected`)
- `v4_sessionWithCaptureTsMs_timestampReadCorrectly` — implemented
- `v3_sessionWithoutCaptureBlock_fallsBackToSessionCreatedAtMs` — implemented
- `metadataFile_containsVersion4` — implemented (replaces `metadataFile_containsVersion3`)
- `metadataFile_capture_containsTimestampMs_greaterThanZero` — implemented
- `metadataFile_capture_timestampMs_equalsSessionCreatedAtMs` — implemented
- `connectedDebugAndroidTest` — pending device run
- `assembleRelease` — pending

---

### Block B — additional Block at Session Creation

**Status:** Not Started

**Goal:**
Write `additional: {isFavorite: false, visibility: "private", source: "sameview"}` at session creation for every new v4 session.

**Scope:**
- `SessionStorage.kt` — add `additional` block to `writeMetadata()`
- `SessionStorageMetadataTest.kt` — add tests for all three additional fields

**Affected Files:**
- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt`
- `app/src/androidTest/java/com/isardomains/sameview/storage/SessionStorageMetadataTest.kt`

**Not in Scope:**
- No UI for isFavorite or visibility
- No `updateAdditional()` write endpoint
- No scanner changes (scanner does not expose additional fields yet)
- No CompareScreen or CompareLibrary changes

**Risks:**
- Very low — purely additive fields with no existing consumers.

**Required Tests:**
- `metadataFile_additional_isFavorite_isFalse`
- `metadataFile_additional_visibility_isPrivate`
- `metadataFile_additional_source_isSameview`

**Definition of Done:**
- All new sessions contain `additional` block with correct defaults
- All listed tests pass
- Full test suite green

**Real-Device Validation Required:** No.

---

### Block C — content Block Cleanup (Fix §12.1 Violation)

**Status:** Not Started

**Goal:**
Remove `"description": null` and `"tags": []` from the `content` block at session creation. The `content` block must be absent at creation unless a title is already present. This fixes the active `SESSION_METADATA_V1.md §12.1` violation.

**Scope:**
- `SessionStorage.kt` — `writeMetadata()`: remove pre-populated `content.description = null` and `content.tags = []`; `content` block is only written if there is actual content at creation time (currently no title at creation either, so the block is fully absent)
- `SessionStorageMetadataTest.kt` — add test asserting `content` block absent at creation; existing `updateTitle` tests unaffected (they operate on pre-existing sessions)

**Affected Files:**
- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt`
- `app/src/androidTest/java/com/isardomains/sameview/storage/SessionStorageMetadataTest.kt`

**Not in Scope:**
- No changes to `updateTitle()` — already null-safe (`optJSONObject("content") ?: JSONObject()`)
- No migration of existing sessions that have `description: null` and `tags: []`
- No scanner changes — scanner already handles absent `content` block gracefully

**Risks:**
- Low — no existing test asserts the presence of `description: null` or `tags: []`
- Verify: `updateTitle()` handles absent `content` block correctly — it does, already

**Required Tests:**
- `writtenJson_contentBlock_isAbsent_atCreationWithoutTitle`

**Definition of Done:**
- No `content` block written at session creation (without title)
- `description` and `tags` not pre-populated
- `updateTitle()` continues to work correctly
- All listed tests pass
- Full test suite green

**Real-Device Validation Required:** No.

---

### Block D — reference.date EXIF Auto-Population

**Status:** Not Started

**Goal:**
At session creation, read EXIF `DateTimeOriginal` from the reference image. If present and plausible, write `reference.date` (ISO 8601), `reference.dateSource = "exif"`, and `reference.userEdited = false` into the `reference` block.

Plausibility filter: reject values where year < 1826 or year > current year.

Only `DateTimeOriginal` is used for auto-population. `DateTime` and `DateTimeDigitized` are not used (per `SESSION_METADATA_V1.md §7.2`).

**Scope:**
- `ReferenceImageMetadataReader.kt` — read `ExifInterface.TAG_DATETIME_ORIGINAL`; parse to date string; store in `ReferenceImageMetadata`
- `CameraViewModel.kt` — extend `ReferenceImageMetadata` data class with `exifDateTimeOriginal: String?` (nullable, absent when EXIF missing or implausible)
- `SessionStorage.kt` — `writeMetadata()`: write `reference.date`, `reference.dateSource`, `reference.userEdited` when `exifDateTimeOriginal != null`
- `SessionStorageMetadataTest.kt` — tests for date fields; update snapshot builders
- `ReferenceImageMetadataReaderTest.kt` — test for `DateTimeOriginal` reading

**Affected Files:**
- `app/src/main/java/com/isardomains/sameview/ui/camera/ReferenceImageMetadataReader.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/CameraViewModel.kt` (ReferenceImageMetadata data class)
- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt`
- `app/src/androidTest/java/com/isardomains/sameview/storage/SessionStorageMetadataTest.kt`
- `app/src/androidTest/java/com/isardomains/sameview/ui/camera/ReferenceImageMetadataReaderTest.kt`
- All test files that construct `ReferenceImageMetadata` directly (must add new optional field)

**Not in Scope:**
- No UI for reference.date display
- No `updateReferenceDate()` write endpoint (that is Block E)
- No `DateTime` or `DateTimeDigitized` EXIF tags
- No timezone conversion (EXIF dates are timezone-naive; stored as date only)

**Risks:**
- **Medium:** `ReferenceImageMetadata` constructor appears in multiple test files — all must be updated for the new field
- **Medium:** EXIF date parsing requires handling the `YYYY:MM:DD HH:MM:SS` format and extracting only the date portion
- **Low:** Plausibility filter must be correctly implemented (year boundaries)
- Auto-population must never block or delay session save (best-effort per §7.2)

**Required Tests:**
- `reference_date_isPopulated_whenExifDateTimeOriginalPresent`
- `reference_dateSource_isExif_whenAutoPopulated`
- `reference_userEdited_isFalse_whenAutoPopulated`
- `reference_date_isAbsent_whenNoExifDateTimeOriginal`
- `reference_date_isAbsent_whenExifYearBelow1826`
- `reference_date_isAbsent_whenExifYearAfterCurrentYear`
- `referenceMetadataReader_readsDateTimeOriginal_whenPresent`

**Definition of Done:**
- Sessions with EXIF `DateTimeOriginal` contain `reference.date`, `reference.dateSource = "exif"`, `reference.userEdited = false`
- Sessions without EXIF date have no `reference.date`, `reference.dateSource`, or `reference.userEdited` fields
- Plausibility filter correctly rejects out-of-range years
- All `ReferenceImageMetadata` constructor call sites updated
- All listed tests pass
- Full test suite green

**Real-Device Validation Required:** Optional — EXIF reading behavior on device should be spot-checked if available, but not a blocker.

---

### Block E — reference.date Manual Edit via updateReferenceDate()

**Status:** Not Started

**Goal:**
Implement `SessionStorage.updateReferenceDate()` to allow users to manually set, update, or remove `reference.date`. When the user sets the date manually, `reference.dateSource` is set to `"manual"` and `reference.userEdited` is set to `true`. A `"manual"` source must never revert to `"exif"`.

**Scope:**
- `SessionStorage.kt` — add `updateReferenceDate(sessionsRoot, sessionId, date: String?, dateSource: String)` method; path traversal validation identical to `updateTitle()`; write `reference.date`, `reference.dateSource`, `reference.userEdited`; when date is removed, remove `reference.date` and `reference.dateSource` but `reference.userEdited` may remain `true`
- `SessionStorageMetadataTest.kt` — tests analogous to updateTitle tests

**Affected Files:**
- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt`
- `app/src/androidTest/java/com/isardomains/sameview/storage/SessionStorageMetadataTest.kt`

**Not in Scope:**
- No UI (no date picker dialog in this block)
- No scanner changes to expose `reference.date` in `ScannedSession` (added when UI needs it)
- No automatic EXIF re-read after manual edit

**Risks:**
- Low — analogous to `updateTitle()` which is fully tested and stable.

**Required Tests:**
- `updateReferenceDate_writesDate_andDateSource_andUserEdited`
- `updateReferenceDate_setsManualSource`
- `updateReferenceDate_doesNotRevertToExif`
- `updateReferenceDate_removesDate_removesDateSource`
- `updateReferenceDate_preservesAllOtherFields`
- `updateReferenceDate_pathTraversal_returnsFalse`
- `updateReferenceDate_absolutePath_returnsFalse`

**Definition of Done:**
- `updateReferenceDate()` correctly writes and removes reference date fields
- Path traversal protection in place
- All listed tests pass
- Full test suite green

**Real-Device Validation Required:** No.

---

### Block F — location Block via updateLocation()

**Status:** Not Started

**Goal:**
Implement `SessionStorage.updateLocation()` to set, update, or remove `location.displayName`, `location.city`, `location.country`, and `location.userEdited`. Location block is absent at creation (§12.1). When all location fields are cleared, the entire `location` block is removed from `metadata.json`.

**Scope:**
- `SessionStorage.kt` — add `updateLocation(sessionsRoot, sessionId, displayName: String?, city: String?, country: String?)` method; path traversal validation; `location.userEdited = true` when any field is set; remove entire `location` block when all fields are cleared
- `SessionStorageMetadataTest.kt` — tests analogous to updateTitle

**Affected Files:**
- `app/src/main/java/com/isardomains/sameview/ui/camera/SessionStorage.kt`
- `app/src/androidTest/java/com/isardomains/sameview/storage/SessionStorageMetadataTest.kt`

**Not in Scope:**
- No UI
- No reverse geocoding (forbidden by `GPS_RECREATION_SYSTEM_V1.md §12`)
- No auto-populate from GPS coordinates
- No scanner changes

**Risks:**
- Very low — purely additive.

**Required Tests:**
- `updateLocation_writesFields_andSetsUserEdited`
- `updateLocation_removesBlock_whenAllFieldsCleared`
- `updateLocation_preservesAllOtherFields`
- `updateLocation_pathTraversal_returnsFalse`

**Definition of Done:**
- `updateLocation()` correctly writes and removes location fields
- Entire block removed when all fields absent
- Path traversal protection in place
- All listed tests pass
- Full test suite green

**Real-Device Validation Required:** No.

---

## 7. Progress Table

| Block | Description | Status |
|---|---|---|
| Block A | capture.timestampMs + METADATA_VERSION 4 + SUPPORTED_VERSIONS | Completed (2026-06-09) |
| Block B | additional block at session creation | Not Started |
| Block C | content block cleanup (fix §12.1 violation) | Not Started |
| Block D | reference.date EXIF auto-population | Not Started |
| Block E | reference.date manual edit via updateReferenceDate() | Not Started |
| Block F | location block via updateLocation() | Not Started |

---

## 8. Recommended Next Step

**Block A is the correct first implementation step.**

Rationale:
- `capture.timestampMs` is the most architecturally significant new field in v4 — it establishes the canonical capture timestamp that Compare Library, Video Export, and future upload flows depend on
- The version bump from 3 to 4 is a prerequisite for all subsequent blocks running under a well-defined schema context
- The scanner fallback (`capture.timestampMs` → `session.createdAtMs`) is the key backward-compatibility mechanism and must be the first thing implemented and tested
- Scope is precisely bounded — four files, no UI, no rendering changes
- All existing features remain fully functional (VideoExportPipeline, SessionBackupExporter, CompareScreen, CompareLibrary)

**Proceed to Block A implementation after this document is approved.**

---

## 9. Open Questions

### OQ-1 — reference.userEdited Redundancy (from SESSION_METADATA_V1.md OQ-18.1)

`reference.userEdited` and `reference.dateSource = "manual"` partially overlap. When `dateSource` is `"manual"`, `userEdited` is always `true`. The distinction is whether `userEdited` is intended to cover edits to other reference fields beyond just the date.

**Pending decision before Block E implementation.** For Block D (EXIF auto-population), `reference.userEdited = false` is set without ambiguity.

### OQ-2 — Scanner Exposure of New v4 Fields

When UI features for `reference.date`, `location.*`, or `additional.*` are implemented, `ScannedSession` will need to be extended to carry these fields. The exact set of fields to expose depends on which UI features are prioritized. This is not a blocker for Blocks A–F as specified.

### OQ-3 — reference.date Granularity in EXIF Auto-Population

EXIF `DateTimeOriginal` carries full date and time (`YYYY:MM:DD HH:MM:SS`). For auto-population, the date should be stored at `"YYYY-MM-DD"` precision (full date, as the EXIF provides it). Year-only is valid for manual entry but auto-population from EXIF naturally yields full date precision. This is consistent with `SESSION_METADATA_V1.md §7.2` — no open issue, documented here for implementer clarity.

---

*Document created 2026-06-09. Based on metadata.json v4 analysis session results.*
