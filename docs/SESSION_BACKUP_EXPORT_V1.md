# SESSION_BACKUP_EXPORT_V1.md

## 1. Document Status

This document is the **authoritative specification** for the session backup export feature in SameView.

It is written for:
- AI coding systems
- Implementation sessions
- Analysis sessions
- Regression-safe follow-up work

If an implementation proposal conflicts with this document, this document wins.

This specification covers export only. Import is not in scope for V1 and is not specified here. Every structural decision is made with future import compatibility in mind, so that a future import feature can be implemented cleanly without retroactively changing the export format.

---

## 2. Feature Purpose

The session backup export feature allows users to create a complete, portable backup of one or more compare sessions as a ZIP file written to a user-chosen location on the device.

### What this feature IS

- A user-initiated, local, full-fidelity backup of internal compare sessions
- Triggered from CompareScreen (single session) or Compare Library (one or more selected sessions)
- Output: a standard ZIP file containing all session files without modification
- Written to user-chosen storage via the Android Storage Access Framework
- Offline-first: the app makes no network calls; any cloud destination is the user's own choice via the OS SAF picker

### What this feature IS NOT

- Not a cloud sync feature
- Not a share flow (sharing via the Android Share Sheet is a separate, not-yet-planned feature)
- Not an export of comparison images or composite outputs
- Not a Drive, OneDrive, or any specific cloud service integration
- Not a session import feature (import is not in V1)
- Not a PDF or video export
- Not a second output file created during camera capture

The backup feature is a session management tool. It operates on stored sessions and does not interact with the camera capture pipeline.

---

## 3. Product Philosophy

### Offline-First, User-Initiated

Backup is always triggered by an explicit user action. The app writes no data to any destination without user initiation. The app makes no network calls during backup. If the user selects a cloud-connected SAF provider (e.g., Google Drive) in the OS picker, the resulting upload is handled by the OS-level SAF provider in a separate process outside the app's control and permission scope.

### Full Fidelity

A backup contains the complete session: all four files, all metadata, including GPS data. No fields are stripped, no files are omitted, no images are re-encoded or resized, no metadata is modified. A backup is a reliable, bit-accurate copy of the original session data.

### Future-Import-Compatible by Design

Although import is not implemented in this version, every structural decision ensures that a future import can be implemented cleanly using only the ZIP structure and `metadata.json`. No additional manifest or index file is required.

### KISS

The format is as simple as possible. No backup-level manifest file (`backup.json` or similar) is introduced. A future importer iterates subdirectories in the ZIP and validates each one independently.

---

## 4. ZIP Export Format

### 4.1 Container Format

The backup is a standard ZIP file using `java.util.zip.ZipOutputStream` with MIME type `application/zip`. DEFLATED compression is used for all entries. For JPEG files, deflate provides negligible size reduction but a consistent compression strategy simplifies the implementation.

### 4.2 Session Directory Structure Inside the ZIP

Each session in the ZIP is represented as a subdirectory named after the session ID. The file set depends on the session schema version.

**Schema version 6 session (with branding):**

```text
<sessionId>/
├── capture.jpg
├── capture-original.jpg
├── reference.jpg
├── reference-original.jpg
├── reference-source-original.[ext]
├── branding-handle.png           (optional — present only when session has branding)
└── metadata.json
```

**Schema version 5 session:**

```
<sessionId>/
├── capture.jpg
├── capture-original.jpg
├── reference.jpg
├── reference-original.jpg
├── reference-source-original.[ext]
└── metadata.json
```

**Schema version 2–4 session:**

```
<sessionId>/
├── capture.jpg
├── reference.jpg
├── reference-original.jpg
└── metadata.json
```

`[ext]` is the format-specific extension stored in `files.referenceSourceOriginal` in `metadata.json` (e.g. `.jpg`, `.heic`).

The session directory name **must** equal the session ID as stored in `metadata.json` under `session.id`. A future importer uses this equality as a consistency check.

### 4.3 Single-Session ZIP

A backup of exactly one v5 session contains one subdirectory:

```
SameView_2026-05-15_14-30-00.zip
└── 2026-05-15_14-30-00/
    ├── capture.jpg
    ├── capture-original.jpg
    ├── reference.jpg
    ├── reference-original.jpg
    ├── reference-source-original.heic
    └── metadata.json
```

### 4.4 Multi-Session ZIP

A backup of two or more sessions contains one subdirectory per session:

```
SameView_Backup_2026-06-01_10-00-00.zip
├── 2026-05-15_14-30-00/
│   ├── capture.jpg
│   ├── capture-original.jpg
│   ├── reference.jpg
│   ├── reference-original.jpg
│   ├── reference-source-original.heic
│   └── metadata.json
└── 2026-05-16_09-15-00/
    ├── capture.jpg
    ├── reference.jpg
    ├── reference-original.jpg
    └── metadata.json
```

Sessions may be at different schema versions. The example above shows one v5 session (with originals) and one older session (without). The exporter handles both via metadata-driven file discovery (§6.3a).

Session order within the ZIP is unspecified. A future importer must not rely on ZIP entry order.

### 4.5 File Content Rules

**Strict no-modification rule.** All files are written into the ZIP exactly as they exist on disk:

- `capture.jpg`: byte-for-byte copy. No re-encoding, no quality change, no resize, no EXIF modification.
- `capture-original.jpg`: byte-for-byte copy.
- `reference.jpg`: byte-for-byte copy.
- `reference-original.jpg`: byte-for-byte copy.
- `reference-source-original.[ext]`: byte-for-byte copy. The extension matches the original source format.
- `metadata.json`: written as-is from disk.

No transformation of any kind is applied during export.

### 4.6 Filename Convention

| Case | Suggested ZIP filename |
|---|---|
| 1 session (from CompareScreen or Library with 1 item selected) | `SameView_<sessionId>.zip` |
| N ≥ 2 sessions (Library with N items selected) | `SameView_Backup_<timestamp>.zip` |

`<sessionId>` is the session directory name, formatted as `YYYY-MM-DD_HH-mm-ss`.

`<timestamp>` is the export initiation time, formatted as `YYYY-MM-DD_HH-mm-ss` in local time.

These filenames are passed as `initialFileName` to `ACTION_CREATE_DOCUMENT`. The user may change the filename in the SAF picker. The app does not enforce or validate the final filename chosen by the user.

---

## 5. metadata.json in the Export

### 5.1 Full Export — No Stripping

The complete `metadata.json` is written into the ZIP without modification. No fields are removed, no values are changed. This includes all blocks present in the session:

**v2–v4 sessions:** schema version, session block, files block (`files.capture`, `files.reference`, `files.referenceOriginal`), capture block, reference block (with `reference.sourceDisplayName` in v2–v4), viewport, overlay, rendering, optional GPS fields, optional content/location/additional blocks.

**v5 sessions (additional):** `files.captureOriginal`, `files.referenceSourceOriginal`, `reference.sourceUri` (replaces `reference.sourceDisplayName`), `reference.sourceMimeType`.

All fields are written as-is from disk. No field stripping, no GPS removal, no URI redaction.

### 5.2 Device-Local Fields

Two fields in `metadata.json` contain URIs specific to the device on which the session was created. These fields are included in the export because removing them would violate full-fidelity backup principles. They may have diagnostic or future-reference value.

| Field path | Version | Type | Device-local — not portable |
| --- | --- | --- | --- |
| `capture.mediaStoreUri` | v2–v5 | String (URI) | Points to the captured photo in `Pictures/SameView/` on the creating device. Meaningless on another device or after gallery cleanup. |
| `reference.sourceDisplayName` | v2–v4 | String (URI) | **Misnomer.** Contains a full content URI, not a display name. Points to the reference image source on the creating device. Not resolvable on another device. |
| `reference.sourceUri` | v5+ | String (URI) | Correct field name for the reference source URI. Same semantics as `reference.sourceDisplayName`. Device-local, non-portable. |

**Note on the `sourceDisplayName` misnomer:** In v2–v4, this field stores a full content URI under an incorrect name. In v5, the field is written as `reference.sourceUri`. Both fields refer to the same data; the rename is a correction only.

**Contract for future importers:** These fields must be treated as informational metadata only. An importer must not attempt to resolve these URIs on the importing device. Missing or unresolvable values for these fields must not cause import failure. A v5 session is fully importable from the files in the ZIP alone, without any URI access.

### 5.3 GPS Data

`captureLocation` and `referenceLocation` are optional fields present only when Recreation Guidance was active and GPS data was available at session creation time. These fields are exported as-is; no GPS stripping is performed.

GPS coordinates are also embedded as EXIF tags in `capture.jpg` (when Recreation Guidance was active) and in `reference-original.jpg` (when the original reference image had GPS EXIF). These files are copied without modification, preserving all EXIF tags.

No GPS-specific warning dialog is displayed before export. The backup is a trusted, complete copy of the session. GPS data is part of the session by definition.

### 5.4 Schema Version Contract

`metadata.json` contains a `version` integer field (currently `5` for new sessions). This field is the authoritative schema version for future import compatibility.

**Rule for the development team:** The `version` field must be incremented whenever a breaking change is introduced to the `metadata.json` schema. Minor additions (new optional fields) do not require a version increment but must be additive and non-breaking. `SessionScanner.SUPPORTED_VERSIONS` must be kept up to date with the accepted version set.

### 5.5 Forward Compatibility Rules for Future Importers

A future import implementation must follow these rules:

1. **Directory-based session discovery**: each top-level subdirectory of the ZIP that contains a parseable `metadata.json` with a `version` field is a candidate session. No ZIP-level index file is required.
2. **Unknown field tolerance**: unknown fields anywhere in `metadata.json` — at any nesting level — must be silently ignored. This is the forward compatibility guarantee for future schema evolution.
3. **Device-local field treatment**: `capture.mediaStoreUri`, `reference.sourceDisplayName` (v2–v4), and `reference.sourceUri` (v5) must not be used for file resolution on the importing device. All session files are resolved exclusively from the `files.*` block and the ZIP contents.
4. **Version validation**: an importer must accept versions 2, 3, 4, and 5. An importer encountering an unknown version must reject that session with an explicit, user-visible error, not a silent skip.
5. **Optional field tolerance**: absent optional fields (`captureLocation`, `referenceLocation`, `content.title`) must not cause import failure.

---

## 6. Export Mechanism

### 6.1 Android Storage Access Framework — ACTION_CREATE_DOCUMENT

The backup export uses `Intent.ACTION_CREATE_DOCUMENT` with MIME type `application/zip`.

This mechanism:
- Requires no permissions beyond those already declared in the manifest
- Does not require FileProvider — see 6.2
- Is supported on all Android versions from API 21; this app targets minSdk 29
- Gives the user full control over the export destination (local downloads, SD card, or cloud-connected SAF providers via the OS picker)
- Does not constitute a network call by the app — any cloud upload is handled by the OS SAF provider in a separate process

### 6.2 No FileProvider Dependency

The backup export reads session files from internal app storage (`filesDir/sessions/`) and streams them directly to the SAF-provided `OutputStream`. No session file URI is passed to any external app component. No `content://` URI wrapping of internal files is required for this feature.

FileProvider preparation (RELEASE_HARDENING_AUDIT Block D, finding S-02) is **not a prerequisite** for the backup export feature. It would become a prerequisite only if a future "Share ZIP via Android Share Sheet" feature is added.

### 6.3 Streaming Contract

The implementation must stream session file content directly to the SAF-provided `OutputStream`. No intermediate temporary file is required or permitted.

**Metadata-driven file discovery (replaces hardcoded file list):**

Before writing each session, the exporter reads `metadata.json` from the session directory and collects all filenames from the `files.*` block. This produces the per-session file list. `metadata.json` itself is always included. This approach handles version-specific file sets (v2–v4: 3+1 files; v5: 5+1 files) and variable extensions (e.g. `reference-source-original.heic`) without hardcoded filenames.

Required approach:

1. Pre-validation: for each session, parse `metadata.json`, collect filenames from `files.*`, verify each file exists on disk. Abort before writing if any file is missing.
2. `contentResolver.openOutputStream(destinationUri)` provides the target output stream
3. Wrap in `BufferedOutputStream` for write performance
4. Wrap in `ZipOutputStream`
5. For each session, for each file in the metadata-driven file list: add a `ZipEntry` with the correct entry path (`<sessionId>/filename`), open the source file with a `FileInputStream`, copy in fixed-size chunks to the ZipOutputStream entry, close the entry
6. Close `ZipOutputStream` after all entries are written (writes the ZIP end-of-central-directory record)

Chunk-based file copying (not full-file in-memory loads) is required to avoid OOM on large session files, especially v5 sessions containing full-resolution original files.

### 6.4 Background Execution

ZIP creation and streaming must run on an IO dispatcher (background coroutine). The UI must not be blocked during export. The ViewModel manages the export state and emits results via the existing snackbar event system.

### 6.5 UI Responsibility for SAF Launcher

The SAF file picker is launched via `ActivityResultContracts.CreateDocument` registered in the Composable. The ViewModel is not responsible for launching the SAF picker directly. The interaction model is:

1. User taps a backup entry point in the UI
2. Composable launches the SAF picker with the suggested filename
3. User selects a destination; picker closes
4. Composable passes the returned destination URI to the ViewModel
5. ViewModel performs the backup on an IO dispatcher

If the user cancels the SAF picker (the launcher returns a null URI), no backup operation starts and no feedback is shown. This is not an error condition.

---

## 7. Entry Points

### 7.1 CompareScreen — Overflow Menu

"Backup Session" is added to the existing overflow menu (⋮) in CompareScreen.

**Complete overflow menu structure:**
```
⋮
├── Edit Title
├── Remove Title        (visible only when a title is present)
└── Backup Session
```

Delete Session remains a dedicated icon in the top app bar. It is not in the overflow menu.

**Precondition:** A valid session context (`sessionId`) must be present. If CompareScreen is opened without a session context (transient compare immediately after capture, before session data is available), "Backup Session" must not appear in the overflow menu.

**Trigger:** Tapping "Backup Session" closes the overflow menu and launches the SAF picker with suggested filename `SameView_<sessionId>.zip`.

### 7.2 Current CompareScreen Top App Bar

The current top app bar structure is:

```
← Back  |  [Delete Session icon]  |  ⋮ (Overflow)
```

This structure is **unchanged by this specification**.

### 7.3 Planned Future CompareScreen Top App Bar (Not In This Scope)

The following is the product-intended target structure, to be implemented as part of the Create Video feature scope:

```
← Back  |  [Create Video icon]  |  [Delete Session icon]  |  ⋮ (Overflow)
```

Product intent:
- **Delete Session**: primary action when the compare result is unsatisfactory
- **Create Video**: primary action when the compare result is successful
- **Overflow**: secondary session management actions (Edit Title, Remove Title, Backup Session)

This structure is documented here as the target state and must not be pre-implemented with placeholders or disabled icons. The top app bar restructuring happens in the Create Video scope.

### 7.4 Compare Library — Multi-Select Action Bar

Backup is available in Compare Library through the existing multi-select mode.

**Complete multi-select action bar structure:**
```
[N selected]  ·  [Select All / Deselect All]  [Backup icon]  [Delete icon]
```

- **Select All / Deselect All**: see Section 8
- **Backup icon**: exports all currently selected sessions as a single ZIP; see Section 9
- **Delete icon**: existing behavior, unchanged; requires a confirmation dialog; destructive

Backup does not require a confirmation dialog before the SAF picker opens. Delete continues to require its confirmation dialog.

---

## 8. Select All / Deselect All in Compare Library

### 8.1 Rationale

The primary use case for multi-session backup is a complete backup of all sessions before a device change. Without Select All, a user with many sessions must tap each tile individually after entering multi-select mode. Select All eliminates this friction and makes the complete-backup workflow practical.

### 8.2 Behavior

| Current selection state | User action | Result |
|---|---|---|
| No items selected | Tap Select All | All sessions selected |
| Some (not all) items selected | Tap Select All | All sessions selected |
| All items selected | Tap Deselect All | No sessions selected |

The toggle reflects the current state: shows "Select All" when not all items are selected, "Deselect All" when all are selected.

### 8.3 Scope of Selection

Select All selects all sessions in the full scanned session list — not just the tiles currently visible in the scroll viewport. The selection is applied to the complete in-memory session list.

### 8.4 Multi-Select Mode Remains Active After Backup

After a successful or failed backup, multi-select mode remains active and the selection is preserved. The user may then choose to delete the exported sessions or exit multi-select mode manually. Multi-select mode is not automatically exited by the backup operation.

---

## 9. UX Flow

### 9.1 From CompareScreen (Single Session)

```
User opens ⋮ → taps "Backup Session"
→ Overflow menu closes
→ SAF file picker opens
  Suggested filename: SameView_<sessionId>.zip
→ User selects destination and confirms
→ Loading state begins (Backup entry point disabled)
→ ZIP is created and streamed to destination
→ Loading state ends
→ Snackbar: "Session backed up"
```

### 9.2 From Compare Library (Multi-Select)

```
User enters multi-select mode (long press on a tile)
→ Selects one or more sessions, or taps Select All
→ Taps Backup icon in action bar
→ SAF file picker opens
  1 session selected:   SameView_<sessionId>.zip
  N ≥ 2 sessions:       SameView_Backup_<timestamp>.zip
→ User selects destination and confirms
→ Loading state begins (Backup icon disabled)
→ ZIP is created and streamed to destination
→ Loading state ends
→ Snackbar: "Session backed up" (1) or "N sessions backed up" (N ≥ 2)
→ Multi-select mode remains active
```

### 9.3 SAF Picker Cancelled

If the user cancels the SAF picker (returns without selecting a destination):
- No backup operation starts
- No snackbar is shown
- No state is changed
- This is not an error condition

### 9.4 No Confirmation Dialog

There is no additional confirmation dialog before the SAF picker opens. The SAF picker itself is the confirmation step: the user actively selects a destination before any write occurs. Canceling the picker cancels the entire operation without side effects.

### 9.5 Loading State

During export:
- The entry point that triggered the backup is disabled (prevents re-entry and double-export)
- A loading indicator is shown
- For single-session export: typically 1–3 seconds
- For multi-session export: longer; a loading indicator is mandatory throughout the operation

The exact visual form of the loading indicator is an implementation detail, consistent with existing loading patterns in the app.

### 9.6 Success Feedback

| Case | Snackbar text |
|---|---|
| 1 session backed up | "Session backed up" |
| N sessions backed up | "N sessions backed up" |

No file path is shown. No "Open" action button is attached. The snackbar follows the app's existing short-lived snackbar pattern.

---

## 10. Error Handling and Fail-Safe Contract

### 10.1 All-or-Nothing Rule

If any error occurs during export — whether reading a session file, writing to the SAF OutputStream, or any other IO or unexpected failure — the entire backup is aborted. A partial ZIP is never delivered to the user.

This rule applies equally to:
- Single-session export
- Multi-session export (an error in any session aborts the entire ZIP)

A silent partial backup is worse than no backup. The user must be able to trust that a "Session backed up" snackbar means the backup is complete.

### 10.2 Cleanup on Failure

When the backup fails after partial writing has begun:
1. `contentResolver.delete(destinationUri, null, null)` is called to remove the incomplete file
2. This cleanup is best-effort and may silently fail on some SAF providers
3. The cleanup result does not affect the error feedback shown to the user

### 10.3 Error Feedback

A dedicated error snackbar is shown on any backup failure. The message must be short and clear. Technical error details must not be shown to the user. The error message uses a string resource (key: `session_backup_error`).

### 10.4 No Silent Failure

A backup failure must always result in user-facing feedback. Silent failure (no snackbar, no state change) is not permitted.

### 10.5 Missing Session Files at Export Time

If a session's files are missing or incomplete at export time (e.g., the session was deleted by a concurrent operation, or the session directory is corrupted), this is treated as an IO error and the entire backup fails per 10.1. The app must not crash or produce a malformed ZIP entry.

---

## 11. Future Import Compatibility

This section documents what a future import feature must implement to correctly process backups produced by this export specification. It is documentation for the future, not a current implementation requirement.

### 11.1 Session Discovery Algorithm

A future importer reads the ZIP and identifies top-level subdirectories. For each subdirectory:

1. Check for a `metadata.json` file inside the subdirectory
2. Parse `metadata.json` as JSON; skip on parse failure
3. Read the `version` field; reject if version is unknown
4. Validate required fields (`session.id`, `session.createdAtMs`, `files.capture`, `files.reference`)
5. Verify that `session.id` matches the subdirectory name; skip on mismatch
6. Collect all filenames from the `files.*` block of `metadata.json`
7. Verify that each collected file exists in the ZIP subdirectory
8. For version 5: additionally verify `files.captureOriginal` and `files.referenceSourceOriginal` are present in `files.*` and exist in the ZIP
9. Accept as a valid importable session

No ZIP-level index or manifest file is involved.

### 11.2 Required Files per Importable Session

**Version 2–4 sessions:**

| File | Required | Notes |
|---|---|---|
| `<sessionId>/capture.jpg` | Yes | Capture image |
| `<sessionId>/reference.jpg` | Yes | Rendered compare reference |
| `<sessionId>/reference-original.jpg` | Yes | EXIF-oriented original reference |
| `<sessionId>/metadata.json` | Yes | Session metadata |

**Version 5 sessions (additional):**

| File | Required | Notes |
|---|---|---|
| `<sessionId>/capture-original.jpg` | Yes | Byte-copy of MediaStore capture |
| `<sessionId>/reference-source-original.[ext]` | Yes | Byte-copy of reference source; filename from `files.referenceSourceOriginal` |

A session with any missing required file must not be imported.

### 11.3 Device-Local Field Handling

An importer must explicitly ignore:

- `capture.mediaStoreUri` — do not attempt to resolve this URI on the importing device
- `reference.sourceDisplayName` (v2–v4) — do not attempt to resolve this URI on the importing device
- `reference.sourceUri` (v5) — do not attempt to resolve this URI on the importing device

The absence of valid URIs for these fields must not cause import failure. A session is fully importable from the files in the ZIP alone. The `files.*` block in `metadata.json` is the authoritative file manifest.

### 11.4 Session ID Constraint

The session ID in a ZIP (`metadata.json`'s `session.id`) has the format `YYYY-MM-DD_HH-mm-ss`. Collision handling (two sessions with the same ID in the same ZIP) is an importer concern. A well-formed backup produced by this spec cannot contain duplicate session IDs.

---

## 12. Privacy

### 12.1 No Network Calls

The app makes no network calls during backup export. The INTERNET permission is not declared in the manifest and is not used. SAF destination handling and any subsequent cloud uploads are entirely outside the app's process and permission scope.

### 12.2 GPS Data Included Without Warning

GPS coordinates embedded in `metadata.json` and EXIF tags are included in the export as part of the full-fidelity backup. No GPS-specific warning is displayed. This is consistent with the app's existing behavior of storing GPS data in session files silently when Recreation Guidance is active.

Users who export sessions to shared or cloud-accessible destinations are responsible for the privacy implications of the exported data. The app does not make this choice on behalf of the user.

### 12.3 Play Store Data Safety

The backup export is a user-initiated operation that writes data to user-chosen local device storage. Under Google's Data Safety definitions, this is classified as data not shared with third parties. The existing GPS/Location (precise location, optional, not shared, not used for tracking) and Camera declarations in the Data Safety form cover this feature. No additional Data Safety form entry is required solely for the backup export feature.

---

## 13. Planned Future Top App Bar Structure

Documented in Section 7.3. Not in scope for this specification.

---

## 14. Testing Contract

### 14.1 Required Unit Tests

| # | Test |
|---|---|
| 1 | Single-session ZIP contains exactly one subdirectory with the correct session ID |
| 2 | Multi-session ZIP contains the correct number of subdirectories, one per selected session |
| 3 | Each subdirectory in the ZIP contains all files declared in `files.*` plus `metadata.json` (version-aware: 4 files for v2–v4, 6 files for v5) |
| 4 | Files in the ZIP are byte-identical copies of the source files |
| 5 | Single-session suggested filename matches `SameView_<sessionId>.zip` |
| 6 | Multi-session suggested filename matches `SameView_Backup_<timestamp>.zip` |
| 7 | `session.id` in `metadata.json` matches the ZIP subdirectory name |
| 8 | IO error during session file read triggers backup abort and best-effort cleanup |
| 9 | No partial ZIP is written on failure (output stream must receive a delete call) |
| 10 | SAF picker returning null URI starts no backup operation and emits no events |
| 11 | ViewModel correctly exposes loading state as true during export, false after |
| 12 | ViewModel emits correct success snackbar event for single session |
| 13 | ViewModel emits correct success snackbar event for N sessions (count is correct) |
| 14 | ViewModel emits error snackbar event on export failure |
| 15 | Select All sets selectedSessionIds to the complete session list |
| 16 | Deselect All clears selectedSessionIds |

### 14.2 Required Instrumentation Tests

| # | Test |
|---|---|
| 1 | Single-session backup: write ZIP to a test file, open and verify ZIP structure and file presence |
| 2 | Multi-session backup: write ZIP containing N sessions, verify all N session subdirectories present |
| 3 | ZIP file content integrity: source files and ZIP-extracted files are byte-identical |

### 14.3 Regression Guard

The implementation must not break:
- Existing CompareScreen tests (delete, title edit, slider, fullscreen, navigation)
- Existing Compare Library tests (multi-select delete, navigation, title display, grid rendering)
- No snackbar replay regression from backup events
- No compare navigation regression
- All previously green unit and instrumentation tests remain green

---

## 15. i18n Contract

All user-facing text must use string resources. No hardcoded visible strings.

Required minimum string resource keys:

| Key | Usage |
|---|---|
| `compare_screen_overflow_backup_session` | Overflow menu item label |
| `compare_library_action_backup` | Multi-select action bar backup icon content description |
| `compare_library_action_select_all` | Select All toggle label / content description |
| `compare_library_action_deselect_all` | Deselect All toggle label / content description |
| `session_backup_success_single` | Snackbar: "Session backed up" |
| `session_backup_success_multi` | Snackbar: "%d sessions backed up" (with integer placeholder) |
| `session_backup_error` | Snackbar: "Backup failed" |
| `session_backup_filename_single` | Suggested ZIP filename: "SameView_%s.zip" (with sessionId) |
| `session_backup_filename_multi` | Suggested ZIP filename: "SameView_Backup_%s.zip" (with timestamp) |

The exact key names follow the project's existing naming conventions. Do not introduce a second naming system.

---

## 16. Relationships to Other Specifications

| Specification | Relationship |
|---|---|
| `COMPARE_SESSION_RENDERING_V1.md` | Defines the session file format and content. The backup exports these exact files unchanged. `reference-original.jpg` was explicitly designed for "export workflows." |
| `COMPARE_FLOW_V1.md` | Defines CompareScreen and Compare Library. This spec adds to the overflow menu and the multi-select action bar. No change to compare mechanics, navigation contracts, or session storage behavior. |
| `GPS_RECREATION_SYSTEM_V1.md` | GPS data in `metadata.json` and EXIF is included in the export unchanged. Section 13 of that document names "export workflows that embed captureLocation GPS data" as a planned future target — this specification is its implementation. |
| `CLAUDE_PROJECT_INSTRUCTION.md` | "Storage: MediaStore ONLY" and "No comparison export" / "No second output file" in CAPTURE BEHAVIOR apply exclusively to the camera capture pipeline. Backup export uses SAF `ACTION_CREATE_DOCUMENT` and is a session management operation separate from capture. "Share flow" out-of-scope applies to social sharing; backup to local device storage is not a share flow. |
| `RELEASE_HARDENING_AUDIT_V1.md` | Block D (FileProvider) is not a prerequisite for this feature. See that document for the clarification note. |
| `CAMERA_WORKFLOW_UX_V1.md` | Not affected. Backup is not a camera workflow feature. |
| `SETTINGS_UX_V1.md` | Not affected. Backup has no settings. |

---

## 17. Explicitly Out of Scope

- Session import (not in V1)
- Cloud sync of any kind
- Drive, OneDrive, or specific cloud service integration
- Share via Android Share Sheet (requires FileProvider; separate future feature)
- Create Video feature (separate future scope; defines future top app bar structure)
- PDF or image composite export
- GPS field stripping during export
- Selective field removal from `metadata.json`
- Backup scheduling or automatic backup triggers
- Backup verification or ZIP integrity checking
- "Backup All" as a dedicated UI entry point outside multi-select (Select All in multi-select mode serves this purpose)
- Per-session encryption
- Backup progress percentage display (loading indicator is sufficient for V1)

---

## 18. Implementation Discipline Rules

1. New backup logic is isolated in a new dedicated class (e.g., `SessionBackupExporter`) — it does not modify `SessionStorage`, `SessionScanner`, or `SessionDeleter`
2. No changes to the compare rendering pipeline
3. No changes to the GPS architecture
4. No changes to the MediaStore capture pipeline
5. No changes to `compareInput` lifecycle, `savedSessions` state, or any camera state
6. Backup failure must leave the app in a fully stable, usable state
7. All new user-facing text must use string resources
8. All new logic must be covered by unit tests per Section 14
9. Existing tests must pass without modification
10. Do not pre-implement Create Video placeholder icons or disabled states
