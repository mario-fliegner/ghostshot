# SESSION_ORIGINALS_PRIVACY_V1.md

## 1. Document Status

This document is the **authoritative specification** for the Session Originals Privacy feature in SameView.

It governs the optional removal of EXIF, GPS, camera, and device metadata from session original files (`capture-original.jpg` and `reference-source-original.<ext>`) before they are written to the session directory.

Written for:
- AI coding systems
- Implementation sessions
- Analysis sessions
- Regression-safe follow-up work

If an implementation proposal conflicts with this document, this document wins.

Cross-references:
- `SESSION_ORIGINALS_V1.md` — base specification for session original files
- `SESSION_METADATA_V1.md` — metadata schema (v5 `originals` block additions)
- `SETTINGS_UX_V1.md` — Privacy settings category
- `implementation_plans/historic/SESSION_ORIGINALS_PRIVACY_IMPLEMENTATION_PLAN.md` — implementation blocks
- `SESSION_BACKUP_EXPORT_V1.md` — backup behavior

---

## 2. Purpose

When the Session Originals Privacy setting is enabled, SameView stores session original files at full visual resolution but removes EXIF, GPS, camera, and device metadata before writing them to the session directory.

**Privacy has priority over byte-exact originality in this mode.** The user has made an explicit choice to trade metadata fidelity for metadata privacy.

---

## 3. Product Decisions

The following are final product decisions. They must not be revisited during implementation.

### 3.1 Global Setting

The feature is controlled by a single global boolean setting in the app's DataStore preferences.

DataStore key: `strip_originals_metadata`
Default: `false` (OFF)

### 3.2 Default OFF

The default is OFF. Existing behavior (byte-for-byte copies) is unchanged for users who do not enable the setting.

### 3.3 Scope

The setting applies exclusively to session original files stored in `filesDir/sessions/`:

- `capture-original.jpg`
- `reference-source-original.<ext>`

It does NOT apply to:
- `capture.jpg` (compare file)
- `reference.jpg` (rendered compare reference)
- `reference-original.jpg` (EXIF-oriented rendering intermediate)
- `metadata.json`
- The MediaStore photo in `Pictures/SameView` — **this is never modified by this setting**

### 3.4 No Retroactive Processing

The setting applies only to new sessions created after the setting is turned ON. Existing sessions are never retroactively modified.

### 3.5 JPEG Conversions Accepted

When necessary to reliably remove metadata, SameView may convert the original file to JPEG format. This is explicitly acceptable. Full visual resolution must be preserved.

### 3.6 Conversion Quality

All JPEG conversions and re-encodings under privacy mode use **quality 95**.

Rationale: JPEG 95 is perceptually indistinguishable from JPEG 100 for photographic content, produces significantly smaller files, and is consistent with the quality used by `MediaStoreWriter` for the capture pipeline. JPEG 100 is wasteful (3–5× larger than 95) with no perceptible quality benefit. Under privacy priority, the one-time controlled quality step at 95% is the right balance.

---

## 4. Setting Behavior

### 4.1 When OFF (default)

Identical to the behavior defined in `SESSION_ORIGINALS_V1.md §5.2` and `§5.5`:

- `capture-original.jpg` = byte-for-byte copy of the committed MediaStore file
- `reference-source-original.<ext>` = byte-for-byte copy of the reference source URI stream

All metadata (GPS, EXIF, MakerNotes, XMP) is preserved in these files.

### 4.2 When ON

**`capture-original.jpg`:**
- Source: the committed MediaStore JPEG
- EXIF orientation is read first and applied to the pixel data if non-trivial
- All metadata is removed (see §5)
- Re-encoded as JPEG 95 without any EXIF, GPS, or device metadata
- Filename: `capture-original.jpg` (unchanged)
- Visual quality: full resolution; controlled quality-95 compression (same quality as MediaStoreWriter source)

**`reference-source-original.<ext>`:**
- Source: the reference image picker URI
- Always decoded via `ImageDecoder` and re-encoded as JPEG 95 (see §6 Format Matrix)
- No byte-level stripping is attempted for reference sources — always decode and re-encode
- PNG sources are re-encoded as PNG (lossless); all other decodeable formats become JPEG 95
- Filename extension changes to `.jpg` for non-PNG sources (e.g. `.heic` → `.jpg`)
- The actual stored filename is always written to `files.referenceSourceOriginal` in metadata.json
- Visual quality: full resolution; PNG sources lossless; all other sources JPEG 95

**MediaStore photo (`Pictures/SameView`):**
- Never modified by this setting, regardless of ON or OFF

**Compare behavior:**
- Unaffected. CompareScreen uses only `capture.jpg` and `reference.jpg`, neither of which is touched by this feature.

**`reference-original.jpg` and `OriginalReferenceBadge`:**
- Unaffected. The badge uses `reference-original.jpg`, which is the EXIF-oriented JPEG rendering intermediate, not the source original.

---

## 5. Metadata Removal Scope

### 5.1 Always Removed

The following metadata is always removed when setting is ON:

| Metadata | Location | Reason |
|---|---|---|
| GPS coordinates (all GPS tags) | EXIF APP1 | Precise location, high privacy risk |
| DateTimeOriginal | EXIF APP1 | Exact capture timestamp |
| DateTime | EXIF APP1 | File modification time |
| DateTimeDigitized | EXIF APP1 | Digitization time |
| Make | EXIF APP1 | Camera manufacturer |
| Model | EXIF APP1 | Camera model |
| Software | EXIF APP1 | Camera firmware / processing software |
| LensMake, LensModel | EXIF APP1 | Lens identification |
| CameraSerialNumber | EXIF APP1 | Device serial number |
| BodySerialNumber | EXIF APP1 | Device serial number |
| MakerNote | EXIF APP1 | Manufacturer-proprietary block, may contain serial numbers, shooting mode, face detection data |
| Artist, Copyright, Owner | EXIF APP1 | Potentially personal |
| EXIF thumbnail | EXIF APP1 | Downsampled copy of image, may contain GPS if present in parent EXIF |
| XMP data | APP1 (XMP namespace) | Can contain GPS, face recognition tags, Adobe Lightroom edit history, ratings |
| IPTC data | APP13 | Can contain caption, byline, location, copyright |

### 5.2 Preserved

| Metadata | Location | Reason |
|---|---|---|
| ICC color profile | APP2 (JPEG) / iCCP chunk (PNG) | Colorimetric data, no personal content, required for accurate color reproduction |
| JFIF header | APP0 (JPEG) | Version information only, no personal content |
| Image dimensions (IHDR) | PNG | Structural, required for correct display |
| Image data | All formats | Pixels are always fully preserved |

### 5.3 Orientation Handling

EXIF orientation is **not preserved as a tag** — it is applied to the pixel data and then discarded. The two original files use different strategies.

**`capture-original.jpg` (MediaStore JPEG source):**

- Orientation is read from EXIF before processing
- Trivial orientation (NORMAL / UNDEFINED): byte-level JPEG segment stripping; no re-encoding; zero quality loss
- Non-trivial orientation: decode to Bitmap, apply rotation via `Matrix`, re-encode at JPEG 95 without orientation tag
- Fallback if byte-level strip encounters a malformed segment: decode+rotate+re-encode at JPEG 95
- Note: MediaStore captures are typically NORMAL orientation because `CameraViewModel` already rotates the bitmap before saving

**`reference-source-original` (user-selected reference source):**

- Always decoded via `ImageDecoder.decodeBitmap()`, which applies EXIF orientation automatically
- No separate orientation check or `Matrix` rotation required
- Output re-encoded to JPEG 95 (or PNG for PNG sources) without any orientation tag
- Byte-level stripping is never attempted for reference sources

After processing, both files display correctly without any orientation tag.

### 5.4 What SameView Can and Cannot Guarantee

**Can guarantee:**
- GPS coordinates removed from stored original files
- EXIF DateTimeOriginal removed from stored original files
- Camera make and model removed from stored original files
- MakerNotes removed
- XMP data removed
- EXIF thumbnail removed
- Full visual resolution preserved in all cases

**Cannot guarantee:**
- Steganographic data embedded in pixel values (outside scope)
- Perfect losslessness when JPEG → re-encode or HEIC → JPEG is performed
- Metadata removal from formats that cannot be decoded (`.bin`, AVIF on API 29–30) — these receive `preservation: "not_possible"`
- Removal of ICC profile (deliberately preserved, contains no personal data)

---

## 6. Format Matrix

| Source Format | Privacy ON: Approach | Output Format | Extension Changes | Quality Impact | minSdk 29 Support | Notes |
|---|---|---|---|---|---|---|
| `image/jpeg` | **capture-original**: byte-level JPEG segment strip (APP1/APP13/XMP removed, ICC kept); fallback to decode→JPEG95 if strip fails or orientation non-trivial. **reference-source**: `ImageDecoder` decode → JPEG 95 (no byte-level strip) | JPEG | None (stays `.jpg`) | capture: none (byte-level) or minimal (95% fallback). reference: minimal (95% re-encode) | ✓ | Byte-level strip only for capture-original; reference-source always re-encoded |
| `image/heic` | `ImageDecoder.decodeBitmap()` → orientation applied automatically → compress JPEG 95 | JPEG | `.heic` → `.jpg` | Controlled, single-step | ✓ (`ImageDecoder` supports HEIC from API 28+) | `BitmapFactory` does NOT apply EXIF orientation for HEIC; `ImageDecoder` is required |
| `image/heif` | Same as HEIC via `ImageDecoder` | JPEG | `.heif` → `.jpg` | Controlled, single-step | ✓ | Normalize to same path as HEIC |
| `image/png` | Decode (BitmapFactory) → PNG re-encode (lossless). Removes all metadata. No chunk parsing. | PNG | None (stays `.png`) | None (lossless) | ✓ | BitmapFactory+compress(PNG) is simpler and more reliable than chunk-level iteration |
| `image/webp` | `ImageDecoder.decodeBitmap()` → orientation applied automatically → compress JPEG 95 | JPEG | `.webp` → `.jpg` | Controlled, single-step | ✓ (`ImageDecoder` API 28+) | `ImageDecoder` handles both lossy and lossless WebP with correct orientation |
| `image/gif` | Decode first frame (BitmapFactory) → compress JPEG 95 | JPEG | `.gif` → `.jpg` | Lossy; animation lost | ✓ | GIF as reference source is extremely rare; animation loss acceptable |
| `image/avif` | API 31+: `ImageDecoder.decodeBitmap()` → JPEG 95. API 29–30: `preservation: "not_possible"` → stored as-is | JPEG (API 31+) / unchanged (API 29–30) | `.avif` → `.jpg` (API 31+) | Controlled (API 31+) | Partial | `BitmapFactory` does NOT support AVIF at any API level. `ImageDecoder` required; AVIF decode only available from API 31 |
| `image/bmp` | Decode (BitmapFactory) → compress JPEG 95 | JPEG | `.bmp` → `.jpg` | Controlled, single-step | ✓ | BMP has minimal metadata; conversion for consistency |
| `null` / `.bin` / unrecognized | Cannot decode → stored as-is | Unchanged | None | None | ✓ | `preservation: "not_possible"` written to metadata |

---

## 7. Processing Pipeline (When ON)

### 7.1 `capture-original.jpg`

```
1. Open captureMediaStoreUri via ContentResolver.openInputStream()
2. Read EXIF orientation tag via ExifInterface
3. If orientation is trivial (NORMAL / UNDEFINED):
   a. Parse JPEG byte stream
   b. Remove APP1 (EXIF), APP1 (XMP), APP13 (IPTC) segments
   c. Keep APP0 (JFIF), APP2 (ICC), all image data segments
   d. Write result to capture-original.jpg
   → Zero quality loss
4. If orientation is non-trivial:
   a. Decode to Bitmap via BitmapFactory
   b. Apply rotation via Matrix
   c. Compress to capture-original.jpg at JPEG 95, no ExifInterface write
   → Controlled quality-95 compression
5. Return filename: "capture-original.jpg"
```

### 7.2 `reference-source-original.<ext>`

```
1. Resolve source URI (resolveSourceUri() — unchanged)
2. Read MIME type via ContentResolver.getType()
3. Dispatch by decodability:
   a. PNG source (image/png):
      - Decode via BitmapFactory.decodeStream()
      - Compress as PNG (lossless) → reference-source-original.png
      - preservation = "metadata_stripped", storedMimeType = "image/png"
   b. All other decodeable formats (JPEG, HEIC, HEIF, WebP, GIF, BMP):
      - Decode via ImageDecoder.decodeBitmap() [orientation applied automatically]
        For formats ImageDecoder does not support (BMP, GIF): use BitmapFactory.decodeStream()
      - Compress to JPEG 95 → reference-source-original.jpg
      - preservation = "metadata_stripped", storedMimeType = "image/jpeg"
   c. AVIF on API 29–30:
      - ImageDecoder does not support AVIF before API 31
      - Copy bytes as-is
      - preservation = "not_possible", filename = reference-source-original.bin
   d. .bin / unrecognized / decode failure:
      - Copy bytes as-is
      - preservation = "not_possible", filename = reference-source-original.bin
4. Return ReferenceSourceOriginalResult(filename, mimeType, storedMimeType, preservation)
```

No byte-level stripping is attempted for `reference-source-original`. All decodeable sources go through a full decode → re-encode cycle, which eliminates all metadata completely and reliably.

### 7.3 Atomicity

The privacy processing happens within the existing `saveSession()` try/catch block. Any failure in privacy processing propagates as an Exception, causing `sessionDir.deleteRecursively()` — no partial session remains. This is identical to the existing atomicity contract.

---

## 8. Schema Changes (Additive v5)

### 8.1 Schema Version

No version bump required. The new `originals` block is additive and optional. Existing v5 sessions without it are fully valid and treated as `privacyMode: false`.

`SessionScanner.SUPPORTED_VERSIONS` and `SessionBackupExporter` are unaffected.

### 8.2 `originals` Block

The `originals` block is written to `metadata.json` **only when `privacyMode: true`**. It is absent for sessions created with the setting OFF.

```json
"originals": {
  "privacyMode": true,
  "capturePreservation": "metadata_stripped" | "not_possible",
  "referenceSourcePreservation": "metadata_stripped" | "not_possible",
  "referenceSourceStoredMimeType": "image/jpeg"
}
```

**Fields:**

| Field | Type | When present | Meaning |
|---|---|---|---|
| `privacyMode` | Boolean | Always, when block is written | `true` — confirms this session was created with privacy mode ON |
| `capturePreservation` | String | Always, when block is written | `"metadata_stripped"` = stripping succeeded; `"not_possible"` = source could not be processed (should not occur for JPEG captures) |
| `referenceSourcePreservation` | String | Always, when block is written | `"metadata_stripped"` = stripping succeeded; `"not_possible"` = format could not be decoded |
| `referenceSourceStoredMimeType` | String? | Only when stored MIME differs from source MIME | The MIME type of the actually stored file (e.g. `"image/jpeg"` when source was `"image/heic"`) |

**Absence semantics:**
- `originals` block absent → `privacyMode: false`; both files are byte-for-byte copies
- `originals.referenceSourceStoredMimeType` absent → stored MIME is identical to `reference.sourceMimeType`

**Reading:**
- Future import/restore code: if `originals` block is absent, assume byte-copy preservation
- If `originals.referenceSourceStoredMimeType` is present, it supersedes `reference.sourceMimeType` for resolving the stored file format

---

## 9. Settings UX

### 9.1 Category

New settings category 5: **Privacy** (EN) / **Datenschutz** (DE)

Placed after GPS Guidance in the settings screen. No sub-toggles in V1.

### 9.2 Setting

| | EN | DE |
|---|---|---|
| **Category header** | Privacy | Datenschutz |
| **Setting title** | Strip metadata from stored originals | Metadaten aus Originalen entfernen |
| **Setting subtitle** | Stores full-resolution session originals without EXIF, GPS, or camera metadata. Gallery photos are not affected. | Speichert Session-Originale in voller Auflösung ohne EXIF-, GPS- oder Kamerainformationen. Galeriefotos bleiben unverändert. |

DataStore key: `strip_originals_metadata`
Type: Boolean
Default: `false`

### 9.3 UX Rules

- The setting takes effect for new sessions from the moment it is toggled ON; no retroactive processing
- No confirmation dialog required when toggling — the subtitle is the disclosure
- No dependency on other settings (independent of GPS Guidance, Branding, etc.)

---

## 10. Backup Behavior

Session backup (`SESSION_BACKUP_EXPORT_V1.md`) exports all files declared in `files.*` byte-for-byte, as-is from disk.

When privacy mode is ON, the backed-up `capture-original.jpg` and `reference-source-original.<ext>` are the stripped/converted versions — **this is correct and intentional**. The backup faithfully reflects what the session contains. A user who enables privacy mode has chosen to store stripped originals; their backup must contain the same.

`metadata.json` includes the `originals` block when privacy mode was ON, allowing importers to understand the preservation mode.

---

## 11. Scope Boundaries

### 11.1 Not Affected by This Feature

- `capture.jpg` — compare file; untouched
- `reference.jpg` — rendered compare reference; untouched
- `reference-original.jpg` — EXIF-oriented rendering intermediate; untouched (already a re-encoded JPEG without full source EXIF)
- `metadata.json` — session metadata, including `captureLocation` and `referenceLocation` GPS fields; these are stored from in-memory GPS data at session creation, not from file EXIF, and are not removed by this setting
- `capture.timestampMs` — stored in metadata.json; not removed
- `reference.date` / `reference.dateSource` — populated from EXIF at session creation and stored in metadata.json; not removed
- MediaStore photo in `Pictures/SameView` — **never touched by this setting**
- CompareScreen rendering
- OriginalReferenceBadge (uses `reference-original.jpg`)
- Video Export (uses `capture.jpg` + `reference.jpg`)
- Share Comparison Image (uses `capture.jpg` + `reference.jpg`)

---

## 12. Relationships to Other Specifications

| Specification | Relationship |
|---|---|
| `SESSION_ORIGINALS_V1.md` | Base spec. The byte-for-byte copy definitions in §5.2 and §5.5 apply when privacy mode is OFF. When ON, this document governs. |
| `SESSION_METADATA_V1.md` | The new `originals` block is an additive extension to the v5 schema. |
| `SETTINGS_UX_V1.md` | New Privacy category (category 5) added. Setting defined per §9. |
| `SESSION_BACKUP_EXPORT_V1.md` | No change to backup logic. Backup exports what is on disk. |
| `implementation_plans/historic/SESSION_ORIGINALS_PRIVACY_IMPLEMENTATION_PLAN.md` | Block-by-block implementation guide. |
