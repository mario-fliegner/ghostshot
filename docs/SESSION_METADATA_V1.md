# SESSION_METADATA_V1.md

## 1. Document Status

This document is the **authoritative specification** for the session metadata model in SameView.

It is written for:

- AI coding systems
- Implementation sessions
- Analysis sessions
- Regression-safe follow-up work

If an implementation proposal conflicts with this document, this document wins.

This document defines the metadata model, product boundaries, persistence rules, and schema evolution strategy.

It does not define:

- Compare UI behavior
- Slider labels or animations
- CompareScreen layout
- GPS guidance behavior (defined in `GPS_RECREATION_SYSTEM_V1.md`)
- Session backup mechanics (defined in `SESSION_BACKUP_EXPORT_V1.md`)
- Rendering pipeline (defined in `COMPARE_SESSION_RENDERING_V1.md`)

Cross-references from other documents that describe `metadata.json` structure point here for metadata semantics.

**Revision note:** Updated to incorporate product decisions on capture date permanence, reference date granularity, extended metadata blocks (content, reference, location, additional), location as user metadata, favorites, visibility, provenance, and privacy design direction for future uploads.

---

## 2. Purpose

SameView stores each compare session as a set of files under `filesDir/sessions/<sessionId>/`. The session contains image files and a `metadata.json` file.

`metadata.json` has three distinct roles:

**Role 1 — Rendering contract:**
Stores the overlay geometry, viewport, and display mode frozen at capture time. This data makes compare rendering deterministic without re-computing from live state. Defined authoritatively by `COMPARE_SESSION_RENDERING_V1.md`.

**Role 2 — System identity and provenance:**
Stores the stable identity of the session (session ID, capture timestamp) and device-local provenance references (MediaStore URI, reference picker URI). These fields are set by the system at creation and are immutable.

**Role 3 — User content and context:**
Stores user-authored content, temporal context, location context, and session-level flags. This data enriches the session with human-readable, user-editable, and UI-displayable information beyond rendering geometry.

This document governs Roles 2 and 3. Rendering contract data is owned by `COMPARE_SESSION_RENDERING_V1.md` and must not be redefined here.

---

## 3. Product Philosophy

### Sessions are Long-Lived Records

A compare session is not a temporary workspace. It is a record of a photographic recreation event — a moment in time where a user aligned a camera to a reference photo and captured the result.

Sessions may be created years before they are reviewed, exported, shared, or uploaded. Metadata must remain stable, parseable, and meaningful long after the session was created.

### Capture Date is a Technical Fact

The capture timestamp is not user content. It is a system-recorded technical fact: the moment the shutter was pressed and the session was created.

The capture timestamp must:

- Be stored independently of EXIF so it remains available even if EXIF data is later stripped
- Be immutable after session save — the user cannot edit it
- Remain available across: Compare UI, Website Viewer, Video Export, session sorting, timeline features, and future upload flows

The capture timestamp is the bedrock of session identity in time. It must not be confused with, derived from, or overwritten by any other date value.

### Reference Date is User Knowledge, Not File Metadata

A reference photo may carry no EXIF, incorrect EXIF, or EXIF that reflects a scan date rather than the original capture date. The user often knows when the reference photo was taken from personal memory, family records, or other context — not from the file's technical metadata.

`referenceDate` represents user knowledge. EXIF data may suggest an initial value, but the user's judgment is always authoritative.

### Location is User Context, Not GPS Data

A user may know that a session was taken at "Marienplatz" or "Golden Gate Bridge" without GPS coordinates. Conversely, GPS coordinates may be present without the user knowing the place name.

User-entered location metadata (display name, city, country) is independent of GPS coordinates. The two systems coexist in the same session but are never automatically linked. Reverse geocoding — deriving place names from GPS coordinates — is forbidden by `GPS_RECREATION_SYSTEM_V1.md` §12 and is not assumed in this model.

### Metadata is User-Owned, Not System-Computed

User content fields (title, description, tags, reference date, location) are owned by the user. The user may author, correct, or remove them at any time. System-computed values may suggest initial values but user choices always take precedence.

### EXIF is a Hint, Not an Authority

EXIF data from the reference image is a technical file property that may be absent, incorrect, or misleading. EXIF may serve as a convenient default for reference date population but does not define the session's reference date.

### Backward Compatibility is Non-Negotiable

Sessions may be exported, archived, and re-imported years later. The schema must evolve in a way that keeps old sessions readable by new software and new sessions tolerated by old software wherever possible. Breaking schema changes require a version increment and an explicit compatibility decision.

### Portable by Default

All metadata fields stored in `metadata.json` must be interpretable without access to the originating device. Fields that reference device-local state (URIs, internal paths) are included for diagnostic value but must never be required for session usability.

---

## 4. Metadata Ownership Rules

### Category 1 — Rendering Contract (Immutable After Save)

These fields are set by the system at capture time and must never be modified after session save. They are governed by `COMPARE_SESSION_RENDERING_V1.md`.

```text
overlayScale
overlayOffsetX
overlayOffsetY
referenceImageDisplayMode
viewportWidth
viewportHeight
referenceFile
referenceOriginalFile
captureFile
```

### Category 2 — System Identity (Immutable After Save)

These fields are set by the system at session creation and must never be modified after save. They represent objective facts about the capture event.

```text
sessionId           Session directory name and stable identity key
capture.timestampMs Capture time in milliseconds since Unix epoch — not user-editable
captureLocation     GPS fix frozen at capture time (governed by GPS_RECREATION_SYSTEM_V1.md)
referenceLocation   GPS EXIF from reference image (governed by GPS_RECREATION_SYSTEM_V1.md)
```

`capture.timestampMs` is the canonical capture timestamp. It must be stored explicitly in the session, independent of EXIF data in any image file. It is not the same as `referenceDate`. It is not user-editable under any circumstance.

### Category 3 — Device-Local Provenance (Immutable, Non-Portable)

These fields are set at session creation and are immutable. They reference device-local state and must not be used for file resolution on any other device.

```text
capture.mediaStoreUri     MediaStore URI of the saved capture photo
reference.sourceUri       Source URI of the reference image at time of selection
```

These fields are included in backups for diagnostic value but must be treated as informational by importers. See `SESSION_BACKUP_EXPORT_V1.md` §5.2.

### Category 4 — User Content (Mutable After Save)

These fields may be created, updated, or removed after session save via explicit user action. The compare rendering pipeline must never depend on any of these fields. Their presence or absence must not affect `CompareScreen` image rendering.

```text
content.title           Optional user-authored session title
content.description     Optional user-authored narrative
content.tags            Optional keyword list

reference.date          Optional date when the reference photo was taken
reference.dateSource    Provenance of reference.date ("exif" or "manual")
reference.userEdited    Boolean flag: true when any reference metadata field was user-edited

location.displayName    Optional user-entered place name
location.city           Optional user-entered city
location.country        Optional user-entered country
location.userEdited     Boolean flag: true when any location field was user-entered

additional.isFavorite   Boolean; user marks session as a favorite
additional.visibility   Enum: "private" | "unlisted" | "public"
additional.source       Enum: "sameview" | "website" | "import" | "api"
```

---

## 5. Current Metadata Model (Schema v3)

### 5.1 Schema Version

The current production schema is version 3. `SessionScanner.SUPPORTED_VERSIONS` accepts versions 2 and 3. Version 2 sessions remain fully readable; GPS and title fields are absent and treated as optional.

### 5.2 Current Flat Structure

The current `metadata.json` is a flat JSON object. There is no nesting or block structure in the implemented schema.

```text
version                     Integer    Schema version (3)
sessionTimestampMs          Long       Capture time, milliseconds since Unix epoch
sessionId                   String     Session directory name, format YYYY-MM-DD_HH-mm-ss
referenceFile               String     Filename of the rendered compare reference image
referenceOriginalFile       String     Filename of the EXIF-oriented original reference
captureFile                 String     Filename of the capture image
overlayScale                Float      Overlay scale frozen at capture
overlayOffsetX              Float      Overlay X offset frozen at capture
overlayOffsetY              Float      Overlay Y offset frozen at capture
referenceImageDisplayMode   String     Display mode enum frozen at capture
viewportWidth               Integer    Viewport width in pixels at capture
viewportHeight              Integer    Viewport height in pixels at capture
captureMediaStoreUri        String?    MediaStore URI of the saved capture photo (device-local)
referencePickerUri          String?    Source URI of the reference image (device-local)
title                       String?    Optional user-authored session title
captureLocation             Object?    Optional GPS fix at capture time (see GPS_RECREATION_SYSTEM_V1.md §5)
referenceLocation           Object?    Optional GPS EXIF from reference image (see GPS_RECREATION_SYSTEM_V1.md §5)
```

### 5.3 Schema Version History

| Version | Added Fields | Notes |
| --- | --- | --- |
| 1 | Initial schema | Geometry and file references only |
| 2 | `sessionTimestampMs`, MediaStore URI, reference URI | Session identity and device-local provenance |
| 3 | `captureLocation`, `referenceLocation`, `title` | GPS and user title support |

---

## 6. Target Metadata Model (v4)

### 6.1 Design Direction: Nested Blocks

The v4 schema introduces nested blocks for new fields. Existing flat fields from v3 are preserved unchanged at the top level for backward compatibility. Migrating existing flat fields into nested blocks is a separate future decision (see Section 15.4).

The nested block structure is the intended long-term direction. New fields introduced in v4 and beyond are added within their respective blocks, not as flat top-level fields.

### 6.2 v4 Block Structure

```text
version                     Integer    Schema version (4)

--- Rendering contract (flat, unchanged from v3) ---
sessionId                   String     Session directory name
referenceFile               String     Rendered compare reference image filename
referenceOriginalFile       String     EXIF-oriented original reference filename
captureFile                 String     Capture image filename
overlayScale                Float      Overlay scale frozen at capture
overlayOffsetX              Float      Overlay X offset frozen at capture
overlayOffsetY              Float      Overlay Y offset frozen at capture
referenceImageDisplayMode   String     Display mode enum frozen at capture
viewportWidth               Integer    Viewport width at capture
viewportHeight              Integer    Viewport height at capture

--- GPS (flat, unchanged from v3, governed by GPS_RECREATION_SYSTEM_V1.md) ---
captureLocation             Object?    GPS fix at capture time
referenceLocation           Object?    GPS EXIF from reference image

--- capture block (new in v4) ---
capture.timestampMs         Long       Capture time, ms since epoch (canonical; replaces flat sessionTimestampMs)
capture.mediaStoreUri       String?    MediaStore URI of capture photo (device-local, non-portable)

--- reference block (new in v4) ---
reference.sourceUri         String?    Source URI of reference image at selection time (device-local)
reference.date              String?    ISO 8601 date when the reference photo was taken (user-authoritative)
reference.dateSource        String?    "exif" | "manual"; absent when reference.date is absent
reference.userEdited        Boolean?   true when the user has explicitly set or confirmed any reference metadata field

--- content block (new in v4) ---
content.title               String?    User-authored session title
content.description         String?    User-authored narrative
content.tags                Array?     List of user-authored keyword strings

--- location block (new in v4) ---
location.displayName        String?    User-entered place name (e.g., "Zugspitze Summit")
location.city               String?    User-entered city (e.g., "Garmisch-Partenkirchen")
location.country            String?    User-entered country (e.g., "Deutschland")
location.userEdited         Boolean?   true when the user has explicitly entered any location field

--- additional block (new in v4) ---
additional.isFavorite       Boolean    true when the user has marked this session as a favorite; default false
additional.visibility       String     "private" | "unlisted" | "public"; default "private"
additional.source           String     "sameview" | "website" | "import" | "api"; default "sameview"
```

### 6.3 v4 Example

```json
{
  "version": 4,
  "sessionId": "2026-06-08_14-30-00",

  "referenceFile": "reference.jpg",
  "referenceOriginalFile": "reference-original.jpg",
  "captureFile": "capture.jpg",
  "overlayScale": 1.0,
  "overlayOffsetX": 0.0,
  "overlayOffsetY": 0.0,
  "referenceImageDisplayMode": "COMPARE_WITH_PREVIEW",
  "viewportWidth": 1080,
  "viewportHeight": 1920,

  "captureLocation": {
    "latitude": 47.421,
    "longitude": 10.985,
    "altitude": 2962.0,
    "accuracyMeters": 8.5,
    "provider": "gps",
    "fixTimestampMs": 1748000000000
  },

  "capture": {
    "timestampMs": 1748000012000,
    "mediaStoreUri": "content://media/external/images/media/1234"
  },

  "reference": {
    "sourceUri": "content://com.google.android.apps.photos.../...",
    "date": "2008-06",
    "dateSource": "exif",
    "userEdited": false
  },

  "content": {
    "title": "Zugspitze 2026",
    "description": "Same vantage point as the 2008 summer hike. North face visible.",
    "tags": ["nature", "mountain", "annual"]
  },

  "location": {
    "displayName": "Zugspitze Summit",
    "city": "Garmisch-Partenkirchen",
    "country": "Deutschland",
    "userEdited": true
  },

  "additional": {
    "isFavorite": true,
    "visibility": "private",
    "source": "sameview"
  }
}
```

### 6.4 Relationship Between v3 Flat Fields and v4 Blocks

The flat field `sessionTimestampMs` in v3 is superseded by `capture.timestampMs` in v4.

During the transition:

- v4 writers write `capture.timestampMs` as the canonical capture timestamp
- v4 writers may also write the legacy flat `sessionTimestampMs` for backward compatibility with v3 readers, or omit it once backward compatibility is no longer required
- v4 readers use `capture.timestampMs` as authoritative; if absent (reading a v3 session), fall back to flat `sessionTimestampMs`

Similarly, `captureMediaStoreUri` and `referencePickerUri` are superseded by `capture.mediaStoreUri` and `reference.sourceUri` in v4.

The flat `title` field is superseded by `content.title` in v4.

The decision of when to stop writing legacy flat fields is an implementation decision, not a schema decision. The schema version governs which readers must support which fields.

### 6.5 Schema Version Implication

v4 introduces new nested blocks. This is a forward-compatible additive change for readers that follow the unknown-field-tolerance rule (Section 15.5). Schema version must be incremented to 4 to signal the new structure.

`SessionScanner.SUPPORTED_VERSIONS` must be updated to accept version 4. Versions 2 and 3 must continue to be accepted.

---

## 7. Reference Date Rules

### 7.1 Core Distinction: EXIF Date vs. Reference Date

EXIF `DateTimeOriginal` is a technical property of the reference image file. It records when the camera created the image according to the camera's clock. It may be:

- Absent (file has no EXIF, EXIF was stripped, image is a screenshot or scan)
- Incorrect (camera clock misconfigured, battery replacement reset the date)
- Misleading (scan date instead of original photo date, heavily edited image)
- Timezone-naive (standard EXIF carries no UTC offset)

`reference.date` in `metadata.json` is a user-facing semantic field. It answers: *"When was this reference photo taken?"* This question is in the domain of the user's knowledge, not the camera file's metadata.

These are two distinct concepts. EXIF `DateTimeOriginal` may inform `reference.date` but does not define it.

### 7.2 Auto-Population from EXIF

When a session is created and a reference image with a readable EXIF `DateTimeOriginal` is present, the system may auto-populate `reference.date` and set `reference.dateSource` to `"exif"`.

Rules for auto-population:

- Auto-population is best-effort and must never block or delay session save
- If EXIF reading fails for any reason, `reference.date` is simply absent — this is not an error
- `DateTimeOriginal` is the only EXIF tag used for auto-population; `DateTime` and `DateTimeDigitized` are not used
- Auto-population must not occur for implausible values (year before 1826 or after the current year)
- `reference.userEdited` is set to `false` when the date is auto-populated from EXIF

### 7.3 Minimum Precision — Year Only is Valid

Historical photographs and family archive images frequently have date information at year precision only, or at month+year precision. An exact day is often unknown.

**Year-only precision is explicitly valid and fully supported.**

The minimum valid `reference.date` value is a four-digit year string (`"YYYY"`). A date entry UI must offer year-only entry as a valid option. No UI must force the user to enter a month or day to save a reference date.

Supported precision levels:

- `"2008"` — year precision only — **valid minimum**
- `"2008-06"` — year and month
- `"2008-06-15"` — full date

Partial strings without a year are not valid (e.g., `"06"` alone is not acceptable).

### 7.4 Manual Override

The user may explicitly set or correct `reference.date` at any time after session creation.

When the user sets the date manually:

- `reference.date` is updated to the user's value
- `reference.dateSource` is set to `"manual"`
- `reference.userEdited` is set to `true`
- This transition is permanent: a `"manual"` source must never automatically revert to `"exif"`

When the user removes the date:

- `reference.date` is removed from `metadata.json`
- `reference.dateSource` is removed from `metadata.json`
- `reference.userEdited` may remain `true` if other reference metadata fields were previously user-edited

### 7.5 Priority Rule

When reading `reference.date` for any purpose:

1. If `reference.date` is present in `metadata.json` → use it, regardless of source
2. If `reference.date` is absent → no confirmed reference date is available

The stored value is always authoritative. Live EXIF re-reading must never override a stored `reference.date`. Future sessions created from the same reference image must not inherit the date from a prior session.

### 7.6 Granularity for Display

Consumer code (UI, export, viewer) must degrade gracefully based on the precision of the stored value:

- `"2008-06-15"` → may display as "Jun 15, 2008" or "Jun 2008" depending on context
- `"2008-06"` → may display as "Jun 2008"
- `"2008"` → may display as "2008"

The Compare UI use case (displaying temporal labels adjacent to the slider handle) requires at minimum year precision. Month+year is the preferred display granularity for that context. Full date precision is for detail views.

### 7.7 What Reference Date is NOT

- Not the capture date (that is `capture.timestampMs`)
- Not derived from GPS `fixTimestampMs`
- Not the date the session was created
- Not a rendering input
- Not a sorting or session-identity field
- Not a required field for session validity

---

## 8. Capture Date Rules

### 8.1 The Capture Date is a System Fact

`capture.timestampMs` records the moment the capture was saved and the session was created. It is a technical fact, not user content.

It is set exactly once: at session creation, from the device clock at the time the save pipeline completes. It is never derived from EXIF data in the captured image.

### 8.2 Immutability

`capture.timestampMs` must never be modified after session save. There is no user-facing edit UI for the capture date. No background process, migration, import, or synchronization operation may alter it.

This immutability is what makes `capture.timestampMs` reliable for all downstream uses.

### 8.3 Independence from EXIF

`capture.timestampMs` is stored explicitly in `metadata.json` so it remains available even if:

- EXIF data is stripped from `capture.jpg` by an external tool
- The MediaStore entry is deleted
- The session is restored on a different device

The capture date must not be reconstructed from EXIF at read time. The stored value in `metadata.json` is the canonical source.

### 8.4 Intended Uses

`capture.timestampMs` is the authoritative timestamp for:

| Use Case | Notes |
| --- | --- |
| Compare UI — temporal label | Displayed adjacent to the slider handle as the "Present" anchor |
| Compare Library — session sorting | Primary sort key for chronological session ordering |
| Compare Library — display timestamp | Formatted date below each session tile |
| Video Export | Temporal context for the exported video |
| Future Website Viewer | Session date in the viewer |
| Future upload flows | Temporal metadata in the upload payload |
| Future timeline features | Anchoring sessions in a time-based view |

### 8.5 What Capture Date is NOT

- Not the GPS fix timestamp (`captureLocation.fixTimestampMs`)
- Not the EXIF `DateTimeOriginal` from `capture.jpg` (though they will usually match closely)
- Not editable by the user
- Not a fallback for `reference.date`

---

## 9. Location Metadata Rules

### 9.1 Location is Pure User Metadata

`location.displayName`, `location.city`, and `location.country` are user-authored free-text fields. They represent the user's knowledge of where a session's photos were taken.

These fields are entirely independent of GPS coordinates. No automatic relationship between GPS coordinates and location text fields exists or is implied.

### 9.2 Explicit Separation from GPS

GPS coordinates (`captureLocation`, `referenceLocation`) are a technical measurement system governed by `GPS_RECREATION_SYSTEM_V1.md`. They express where the device was at the moment of capture in geodetic coordinates.

Location text fields express where the user understands the subject matter to be located, in human language. These two representations coexist in the same session without coupling.

**Reverse geocoding — deriving location text from GPS coordinates — is explicitly forbidden** by `GPS_RECREATION_SYSTEM_V1.md` §12. Location text fields must never be auto-populated from GPS coordinates.

A session may have:

- GPS coordinates but no location text (user did not enter location)
- Location text but no GPS coordinates (reference image had no GPS EXIF, or Recreation Guidance was OFF)
- Both
- Neither

All four states are valid and must be handled gracefully.

### 9.3 Field Definitions

**`location.displayName`**

The specific place name as the user knows it.

- Examples: "Marienplatz", "Golden Gate Bridge", "Eiffelturm", "Oak tree in the garden"
- Free text; no controlled vocabulary
- Optional

**`location.city`**

The city or municipality as the user knows it.

- Examples: "München", "San Francisco", "Paris"
- Free text; no controlled vocabulary or ISO code requirement
- Optional; may be absent even when `displayName` is present

**`location.country`**

The country as the user knows it.

- Examples: "Deutschland", "USA", "France"
- Free text; no ISO 3166 requirement for v4
- Optional; may be absent even when `city` is present

**`location.userEdited`**

Boolean flag. Set to `true` when any location field has been explicitly entered by the user. Absent or `false` when no location data has been entered.

### 9.4 Editing Rules

- All location fields may be added, edited, or removed individually after session creation
- Removing a location field does not require confirmation
- Setting any location field sets `location.userEdited` to `true`
- Clearing all location fields removes the entire `location` block; `location.userEdited` is removed with it
- Location fields have no fixed maximum length in the schema; practical limits are a UX concern

### 9.5 Display Rules

- Location text is displayed as user-entered
- No formatting, normalization, or translation is applied by the app beyond minimal sanitization on save
- On save, location fields are trimmed; zero-width and Bidi override characters are removed; pasted line breaks are replaced with a space; normal international characters, emojis, and punctuation are preserved unchanged; no length limits are applied
- Locale of the viewer (website, export) does not alter stored location text

### 9.6 What Location is NOT

- Not GPS coordinates
- Not auto-populated from GPS
- Not validated against any geographic database
- Not a required field
- Not a rendering input
- Not a session-identity field

---

## 10. Additional Metadata Fields

### 10.1 `additional.isFavorite`

**Purpose:** Allows the user to mark a session as a favorite for future filtering, showcase use, and selective export.

**Type:** Boolean
**Default:** `false`
**Mutable:** Yes — user may toggle at any time after session creation

**Intended future uses:**

- Favorites filter in Compare Library
- "Best of" or showcase selection for website upload
- Pre-selection for video export compilations
- Quick-access view

**Rules:**

- `isFavorite` defaults to `false` for all new sessions; it is never pre-set by the system
- Setting `isFavorite` to `false` is not the same as removing the field; the field may be present with value `false`
- `isFavorite` does not affect compare rendering, navigation, or delete behavior

### 10.2 `additional.visibility`

**Purpose:** Expresses the intended audience for this session in a future upload or sharing context.

**Type:** String enum
**Default:** `"private"`
**Mutable:** Yes — user may change at any time

**Allowed values:**

| Value | Meaning |
| --- | --- |
| `"private"` | Session is local only; not published or shared |
| `"unlisted"` | Session is reachable by direct link if published; not publicly listed |
| `"public"` | Session is publicly visible; for future showcase or community features |

**Current behavior:**
`visibility` is currently metadata only. No upload feature exists. The field is stored and persisted but has no runtime effect in the current app version.

**Future use:**
When a website upload feature is implemented, `visibility` expresses the default privacy intent for that session at upload time. The upload flow must allow the user to confirm or change the visibility before upload proceeds.

**Rules:**

- Default is always `"private"` for all new sessions
- The app must never automatically change `visibility` to `"public"` without explicit user action
- `visibility` does not affect local session behavior, compare rendering, or delete behavior

### 10.3 `additional.source`

**Purpose:** Records the origin of the session for future provenance tracking and import attribution.

**Type:** String enum
**Default:** `"sameview"`

**Allowed values:**

| Value | Meaning |
| --- | --- |
| `"sameview"` | Created by the SameView Android app through the normal capture flow |
| `"website"` | Created or originated via the SameView website |
| `"import"` | Created through a session import operation |
| `"api"` | Created via a future SameView API |

**Rules:**

- All sessions created by the current app set `source` to `"sameview"` automatically at creation
- `source` is set by the system at creation and must not be user-editable
- `source` is immutable after session creation
- A future import feature must set `source` to `"import"` for imported sessions

**Note:** `additional.source` (session origin) is distinct from `reference.dateSource` (how the reference date was established). These are independent fields.

---

## 11. Story / Description Rules

### 11.1 Purpose

`content.description` allows the user to attach narrative context to a session. Examples:

- "Location: Zugspitze, same vantage point as the 2008 summer hike"
- "Reference from Dad's photo album — approximately 1975, location unknown"
- "Annual documentation of the oak tree in the garden"

### 11.2 Content Constraints

- Plain text only; no HTML, no Markdown for v4
- Sanitized and trimmed on save: zero-width and Bidi override characters are removed; tabs are replaced with a space; blank result is treated as absent; no length limits are applied
- Line breaks in `content.description` are preserved (multi-line field); line breaks in `content.title` are replaced with a space (single-line field)
- International characters, emojis, and normal punctuation are preserved unchanged

### 11.3 Tags — `content.tags`

`content.tags` is an optional array of user-authored keyword strings attached to a session.

- Type: JSON array of strings
- Each tag: trimmed plain text, no markup
- Empty array and absent array are equivalent; store as absent when empty
- No enforced tag count or tag length limit in the schema; limits are a UX decision
- Tags are case-sensitive in storage; display normalization is a UX decision

Intended future uses: filtering in Compare Library, thematic grouping, website tag browsing.

### 11.4 Editing Rules

- Description and tags may be added, edited, or removed at any time after session creation
- Removal does not require confirmation
- Changes must update immediately in the session's `metadata.json`
- These fields are purely optional; their absence must not affect any other session behavior

### 11.5 What Description is NOT

- Not a search index
- Not a structured data field
- Not a rendering input
- Not a required field

---

## 12. Persistence Rules

### 12.1 Write Contract — At Session Creation

At session creation, the system writes to `metadata.json`:

- All rendering contract fields (atomically, as part of session save)
- `capture.timestampMs` (immutable capture timestamp)
- `capture.mediaStoreUri` (device-local, informational)
- `reference.sourceUri` (device-local, informational)
- `reference.date` and `reference.dateSource` if EXIF auto-population is successful
- `additional.source` set to `"sameview"`
- `additional.visibility` set to `"private"`
- `additional.isFavorite` set to `false`
- `content.title` if provided at capture time (currently no UI for title at capture)

The following fields are absent at creation and must not be pre-populated with empty values:

- `content.description`
- `content.tags`
- `location.*` (all location fields)

### 12.2 Write Contract — After Session Creation (User Edits)

Field updates after creation must:

- Preserve all existing fields; partial overwrites that lose other fields are forbidden
- Not modify any Category 1 (Rendering Contract) or Category 2 (System Identity) fields
- Surface a user-facing error on write failure; silent data loss is not acceptable

### 12.3 Read Contract

All user content fields are optional. Readers must:

- Return null / absent for missing fields without error
- Silently ignore unknown fields (forward compatibility guarantee)
- Accept all supported schema versions

### 12.4 Atomicity

`metadata.json` updates must not leave the file in a partially written state. An update failure must not corrupt existing field values.

### 12.5 Session ID as Stable Identity

The session ID (`YYYY-MM-DD_HH-mm-ss`) is the stable identity of a session. It must not be changed after creation. It is used as the subdirectory name, as `sessionId` in `metadata.json`, and as the ZIP subdirectory name in backups. These three uses must remain consistent.

---

## 13. Backup / Export Rules

### 13.1 Full Fidelity — No Stripping

Session backup (defined by `SESSION_BACKUP_EXPORT_V1.md`) copies `metadata.json` byte-for-byte without modification. All user content fields and all new v4 fields are included in every backup automatically. No special handling is required for new fields.

### 13.2 Import Compatibility

A future importer reading a backup ZIP must:

- Accept all new v4 fields when present
- Not fail when any optional field is absent
- Not re-derive `reference.date` from EXIF during import; the stored value is authoritative
- Not re-derive `capture.timestampMs` from image EXIF during import; the stored value is authoritative
- Treat `reference.dateSource` as informational
- Treat `capture.mediaStoreUri` and `reference.sourceUri` as device-local and non-resolvable on the importing device

A backup from an older app version will have new fields absent. An importer must handle this gracefully.

### 13.3 Future Website Viewer Compatibility

A website viewer reading session ZIP files must be able to display `reference.date`, `capture.timestampMs`, `content.title`, `content.description`, `content.tags`, `location.*`, and `additional.*` without device access.

All new fields are self-contained values requiring no device-local resolution.

`reference.date` and `capture.timestampMs` must be formatted using the locale of the viewer, not the originating device. ISO 8601 storage format is locale-neutral.

---

## 14. Privacy Design Direction (Future Upload)

This section documents the intended design direction for a future website upload feature. It is not a current implementation requirement. No upload feature exists in the current app version.

### 14.1 GPS Stripping as an Optional Upload Step

When a future upload feature is implemented, the user must be offered the option to strip GPS coordinate data before upload.

GPS stripping, if performed, removes:

- `captureLocation` (GPS coordinates and fix metadata)
- `referenceLocation` (GPS coordinates from reference EXIF)
- GPS EXIF tags from `capture.jpg` (within the upload payload)
- GPS EXIF tags from `reference-original.jpg` (within the upload payload)

GPS stripping must not remove or alter:

- `capture.timestampMs` — temporal context must be preserved
- `reference.date` — temporal context must be preserved
- `content.title`, `content.description`, `content.tags` — user content preserved
- `location.displayName`, `location.city`, `location.country` — user-authored location context preserved
- All rendering contract fields

### 14.2 Principle: Temporal Context Survives GPS Removal

Removing GPS coordinates must not result in the loss of temporal information. A session that had GPS coordinates stripped must still carry:

- When the capture happened (`capture.timestampMs`)
- When the reference photo was taken (`reference.date`, if established)
- Where the user says it was taken (`location.*`, if entered)

The user's chronological and geographic narrative context must remain intact after GPS stripping.

### 14.3 Local Backup is Always Full-Fidelity

The session backup export feature (`SESSION_BACKUP_EXPORT_V1.md`) always writes a full-fidelity backup with no stripping. GPS stripping is only relevant to the future upload flow, not to local backup. These two operations must not share code paths that could accidentally strip data from local backups.

### 14.4 Visibility Controls Upload Access

When a session is uploaded, its `additional.visibility` value expresses the intended access level. The upload flow must display the current `visibility` value and allow the user to confirm or change it before upload proceeds. The app must never silently publish a session with `visibility: "private"`.

---

## 15. Future Compatibility Rules

### 15.1 Adding New Optional Fields

New optional user content fields may be added within existing blocks without a schema version increment, provided they are:

- Not required for session validity
- Silently ignored by older parsers
- Documented in this specification or a linked extension document

Recommended practice: increment schema version when introducing new optional fields, even when not architecturally required, to provide a clear signal to future tooling.

### 15.2 Adding New Required Fields

A new required field is a breaking schema change. It requires:

- A schema version increment
- An explicit product decision documented before implementation
- A migration path for existing sessions that lack the field

### 15.3 Renaming or Removing Fields

Field renaming and removal are breaking changes. They require a schema version increment and an explicit product decision. The previous field name must be supported for reading at least one schema version after removal.

### 15.4 Migration of Flat v3 Fields to Nested Blocks

The flat v3 fields (`sessionTimestampMs`, `captureMediaStoreUri`, `referencePickerUri`, `title`) are superseded by their nested counterparts in v4 (`capture.timestampMs`, `capture.mediaStoreUri`, `reference.sourceUri`, `content.title`).

The decision of when to stop writing and reading the legacy flat fields requires an explicit implementation decision. Until that decision is made:

- v4 writers must write new nested block fields as canonical
- v4 readers must fall back to flat v3 fields when nested counterparts are absent (reading a v3 session)
- Both representations may coexist in the same session during the transition period

This migration does not require a schema version increment beyond v4, as both forms are additive from a reader perspective.

### 15.5 The Forward Compatibility Guarantee

Every parser in the codebase that reads `metadata.json` must follow this rule:

> Unknown fields at any nesting level must be silently ignored.

This rule is the foundation of forward compatibility. Any parser that throws on unknown fields violates this guarantee.

### 15.6 `additional.visibility` Enum Extensibility

Future values for `additional.visibility` beyond `"private"`, `"unlisted"`, and `"public"` must be treated as `"private"` by any parser that does not recognize them. Parsers must not throw on unknown enum values for this field.

### 15.7 `additional.source` Enum Extensibility

Future values for `additional.source` beyond the four defined values must be silently accepted by parsers. Unknown source values are treated as informational and must not affect session loading or behavior.

---

## 16. Explicit Non-Goals

The following are explicitly out of scope for this specification and must not be implemented as part of any feature that references this document:

**Not metadata fields:**

- Raw EXIF data stored in `metadata.json` — EXIF lives in `reference-original.jpg`
- Camera model, lens, or shooting settings from the capture
- Device model or app version at time of capture
- Edit history or change log for user content fields
- GPS-derived place names (reverse geocoding is forbidden by `GPS_RECREATION_SYSTEM_V1.md` §12)

**Not session metadata features:**

- Multi-user collaboration or shared editing
- Session linking or relationship tracking between sessions
- AI-generated summaries or captions
- Automatic date correction based on GPS timestamp
- Automatic location population from GPS coordinates

**Not persistence behaviors:**

- Cloud sync of metadata fields
- Automatic metadata migration between devices
- Server-side metadata storage
- Metadata encryption at rest

**Not rendering behaviors:**

- `reference.date` must never influence `ReferenceRenderer.render()`
- `content.description` must never influence compare rendering
- User content fields must never affect `ContentScale`, viewport, overlay geometry, or divider position
- `additional.visibility` must not affect local session behavior

---

## 17. Testing Considerations

This section identifies what must be verified when the new metadata fields are implemented. It does not prescribe test file names or test frameworks.

### 17.1 Parser Tests

**Capture block:**

- `SessionScanner` correctly reads `capture.timestampMs` from a v4 session
- `SessionScanner` falls back to flat `sessionTimestampMs` when reading a v3 session (no `capture` block)
- `capture.timestampMs` is never null for any valid session (v2, v3, or v4)

**Reference block:**

- All three ISO 8601 precision formats parse correctly (`"YYYY"`, `"YYYY-MM"`, `"YYYY-MM-DD"`)
- `reference.date` is absent for v3 sessions — not an error
- `reference.dateSource` is absent when `reference.date` is absent
- `reference.userEdited` defaults to `false` when absent

**Content block:**

- `content.title`, `content.description`, `content.tags` are all absent for v3 sessions — not an error
- Empty tags array and absent tags are treated equivalently
- Unknown fields in the content block are ignored

**Location block:**

- All location fields are absent for v3 sessions — not an error
- Partial location (e.g., only `displayName` present, no `city` or `country`) is valid
- `location.userEdited` is correctly read

**Additional block:**

- `additional.isFavorite` defaults to `false` when absent
- `additional.visibility` defaults to `"private"` when absent
- Unknown `additional.visibility` values are treated as `"private"`
- Unknown `additional.source` values are silently accepted
- Unknown fields in the additional block are ignored

**Forward compatibility:**

- Parsing a v4 session with an additional unknown top-level field does not throw
- Parsing a v4 session with an additional unknown field inside any block does not throw

### 17.2 Write / Update Tests

- Writing `capture.timestampMs` does not alter rendering contract fields
- Updating `content.description` does not alter `reference.date` or `capture.timestampMs`
- Removing `reference.date` also removes `reference.dateSource`; `reference.userEdited` may remain
- Toggling `additional.isFavorite` does not alter any other field
- A failed write does not leave `metadata.json` in a partially written state

### 17.3 Priority and Source Tests

- A session with `reference.dateSource: "exif"` can transition to `"manual"` via user edit
- A session with `reference.dateSource: "manual"` is not overwritten by any subsequent EXIF read
- `capture.timestampMs` is never overwritten after session creation

### 17.4 Backup / Export Tests

- A session backup ZIP contains `metadata.json` with all v4 fields when set
- A session backup ZIP contains `metadata.json` with no v4 block fields when reading a v3 session
- A restored v3 session is fully usable without any v4 block fields

### 17.5 Regression Guard

The introduction of new metadata fields must not break:

- `CompareScreen` rendering behavior
- `CompareLibraryScreen` rendering and navigation
- Backup export behavior
- Session delete behavior
- All existing unit and instrumentation tests must remain green

---

## 18. Open Questions

### RESOLVED — Closed Questions

**OQ-14.1 — Flat vs. Nested Schema**
*Resolution (Decision 3):* Nested blocks is the v4 direction. New fields introduced in v4 are placed in named blocks (`capture`, `reference`, `content`, `location`, `additional`). Existing flat v3 fields are preserved for backward compatibility. The migration strategy for legacy flat fields to their nested counterparts is a separate implementation decision but does not block v4.

**OQ-14.2 — Minimum Precision for Manual Reference Date**
*Resolution (Decision 2):* Year-only (`"YYYY"`) is explicitly the valid minimum. The entry UI must support year-only input. Month and day are optional enhancements.

**OQ-14.4 — Tags Field**
*Resolution (Decision 3):* Tags are in scope for v4 as `content.tags`. They are stored as a JSON array of strings. No pre-implementation as empty arrays is allowed; absent when no tags are set.

---

### OPEN — Pending Decisions

**OQ-18.1 — `reference.userEdited` Redundancy (Priority: Medium)**

`reference.userEdited` (boolean) and `reference.dateSource` (`"exif"` / `"manual"`) partially overlap in meaning. When `dateSource` is `"manual"`, `userEdited` is always `true`. When `dateSource` is `"exif"`, `userEdited` is `false` unless other reference fields were edited.

**Decision needed:** Is `reference.userEdited` intended as a broader flag covering all user edits to any reference field, making it distinct from `dateSource`? Or is it redundant and should be removed in favor of deriving the boolean from `dateSource == "manual"`?

#### OQ-18.2 — GPS Block Migration (Priority: Low)

`captureLocation` and `referenceLocation` are currently flat top-level fields (v3). The nested block direction suggests they could eventually move into a `gps` block. However, they are listed at the top level in `SESSION_BACKUP_EXPORT_V1.md` §5.1.

**Decision needed:** Should GPS fields move to a nested `gps` block in a future version, or remain flat permanently for maximum backward compatibility?

#### OQ-18.3 — Tags Count and Length Limits (Priority: Low)

The schema imposes no maximum count or maximum character length for individual tags. UI input controls must set practical limits.

**Decision needed:** What is the maximum number of tags per session? What is the maximum character length per tag, as enforced by the input UI? The schema need not encode these limits.

#### OQ-18.4 — Description Maximum Length (Priority: Low)

The schema imposes no maximum length for `content.description`. A UI input control must set a practical limit.

**Decision needed:** What is the maximum character count for the description field as enforced by the edit UI?

#### OQ-18.5 — Reference Date Plausibility Validation (Priority: Low)

When auto-populating `reference.date` from EXIF, implausible values should be rejected.

Proposed rule: reject values where year is before 1826 (earliest photograph) or after the current year.

**Decision needed:** Is this rule correct and sufficient?

#### OQ-18.6 — Future GPS-to-Location Link (Priority: Low — Future)

In a future version, the user could be offered the option to populate `location.city` and `location.country` from GPS coordinates via reverse geocoding. This would require an explicit exception to the geocoding prohibition in `GPS_RECREATION_SYSTEM_V1.md` §12 and would require network access that the app currently does not have.

**Decision needed (when relevant):** Should reverse geocoding ever be introduced as an opt-in feature? If so, must `GPS_RECREATION_SYSTEM_V1.md` be updated before any implementation begins.

---

## 19. Relationship to Other Specifications

| Specification | Relationship |
| --- | --- |
| `COMPARE_SESSION_RENDERING_V1.md` | Owns the rendering contract fields in `metadata.json`. Fields defined there are immutable after save. This document governs user content and system identity fields only. |
| `GPS_RECREATION_SYSTEM_V1.md` | Defines `captureLocation` and `referenceLocation` in `metadata.json`. GPS fields are Category 2 (System Identity) fields, not user content. Reverse geocoding from GPS coordinates to location text is forbidden by §12 of that document. |
| `SESSION_BACKUP_EXPORT_V1.md` | Defines backup export behavior. All new v4 fields are included in backups by the full-fidelity rule. The block structure described in §5.1 of that document is the v4 direction formalized in this specification. |
| `COMPARE_FLOW_V1.md` | Defines CompareScreen and Compare Library behavior. User content fields may be displayed in these screens but must not affect compare mechanics, slider behavior, or navigation contracts. |
| `CLAUDE_PROJECT_INSTRUCTION.md` | Governs overall architecture and change rules. New metadata fields follow the same MVVM + Hilt + Compose patterns. |
| `IMPLEMENTATION_NOTES.md` | Tracks implemented state. Must be updated when v4 schema fields are implemented and verified. |
