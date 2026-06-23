# SESSION_BRANDING_V1.md

## 1. Document Status

This document is the **authoritative specification** for the Session Branding feature in SameView.

It is written for:
- AI coding systems
- Implementation sessions
- Analysis sessions
- Regression-safe follow-up work

If an implementation proposal conflicts with this document, this document wins.

This specification covers:
- The session branding model and data architecture
- Global branding as a default template for new sessions
- Built-in symbol assets and image normalization
- Branding handle visual design
- Metadata schema v6
- Edit Session branding card
- Global Settings branding section
- Share Comparison Image integration
- Backup / Restore / Website reproducibility
- Video export future compatibility

This document does NOT cover:
- Live Compare Screen behavior (unchanged — see §14)
- Video export implementation (future only — see §16)
- Full implementation sequencing (see `SESSION_BRANDING_IMPLEMENTATION_PLAN.md`)

---

## 2. Feature Purpose

### What this feature IS

- A way for users to set a custom branding logo or built-in symbol in the slider handle
  of a Share Comparison Image export
- A per-session asset (`branding-handle.png`) stored in the session folder
- A global default branding that is automatically copied to new sessions
- An Edit Session card for per-session branding management
- A "Use branding" toggle in the Share Comparison Image screen

### What this feature IS NOT

- Not a watermark or marketing overlay on comparison content
- Not a Live Compare Screen visual change (CompareScreen handle is unchanged)
- Not a video export feature in V1 (architecture only prepared — see §16)
- Not a cloud branding service or remote asset
- Not a paid feature gate
- Not a global real-time override of session branding

---

## 3. Product Model

### 3.1 Session Branding is the Truth

The branding for a session is defined by the file `branding-handle.png` inside the session
folder. This file is the single source of truth for all exports from that session.

- If the file exists: the session has branding.
- If the file does not exist: the session has no branding.

No runtime lookup of global settings is performed when rendering a session export.
Session branding is fully self-contained and independent of the device's current settings.

### 3.2 Global Branding is a Template

Global branding (`filesDir/branding/handle.png`) is a device-local default that is
automatically copied into new sessions at creation time.

- Global branding exists → new session receives a copy of it as `branding-handle.png`
- Global branding not set → new session is created without branding
- Changing global branding after a session is created has **no effect** on that session
- Deleting global branding after a session is created has **no effect** on that session

Global branding is never the live fallback for a session. It is a convenience feature
for users who want every new session to start with their branding pre-applied.

### 3.3 Hierarchy

```
Global Branding (default template for new sessions)
    ↓ copied at session creation
Session Branding (branding-handle.png in session folder)
    ↓ used at export time
Share Comparison Image export
```

There is no runtime inheritance. Once created, each session is branding-independent.

### 3.4 Remove Branding Behavior

Removing session branding means permanently removing it from the session.

- `branding-handle.png` is deleted from the session folder
- The `branding` block is removed from `metadata.json`
- `files.brandingHandle` is removed from `metadata.json`
- There is NO automatic fallback to global branding after removal
- To re-add branding: user must explicitly use "Change branding" or "Copy from default
  branding" in Edit Session

---

## 4. Session Branding File

### 4.1 Filename

```
branding-handle.png
```

This filename is fixed and non-configurable. The `metadata.json` `branding.handleFile`
and `files.brandingHandle` fields always contain `"branding-handle.png"`.

### 4.2 Location

```
filesDir/sessions/<sessionId>/branding-handle.png
```

### 4.3 Format

- PNG with RGBA (alpha channel required)
- Dimensions: 512 × 512 pixels
- Created by the normalization pipeline (see §7)
- All branding assets — own images and built-in symbols alike — are normalized to this
  format before being stored in the session

### 4.4 Atomicity

When updating session branding:

1. New `branding-handle.png` is written to a temp file in the session directory
2. On success: temp file is renamed to `branding-handle.png` (atomic replace)
3. On failure: temp file is removed; existing `branding-handle.png` is preserved
4. `metadata.json` is updated only after the file write succeeds

---

## 5. Global Branding Storage

### 5.1 Location

```
filesDir/branding/handle.png
```

The `branding/` subdirectory is created on first use. No DataStore key is required.
File existence is the setting: if the file exists, global branding is set.

### 5.2 Format

Same as session branding: 512 × 512 RGBA PNG.

Only the normalized PNG is stored. The original source image (from Photo Picker) is
not retained after normalization. The URI is used for a single read-and-normalize
operation and is then released.

### 5.3 Auto Backup

The `filesDir/branding/` directory follows the app's existing Auto Backup exclusion
rules. Global branding is a device-local setting and is excluded from Android Auto
Backup (consistent with `filesDir/sessions/` exclusion in `backup_rules.xml` and
`data_extraction_rules.xml`).

### 5.4 Behavior When Global Branding is Changed

Changing global branding:
- Creates or replaces `filesDir/branding/handle.png`
- Has NO effect on any existing session
- Only affects new sessions created after the change

Removing global branding:
- Deletes `filesDir/branding/handle.png`
- Has NO effect on any existing session
- New sessions will no longer receive branding auto-copy

---

## 6. Built-in Symbols

### 6.1 Symbol Set (V1)

V1 includes exactly **6 built-in symbols**:

| Symbol ID | Visual Description |
|---|---|
| `heart` | Heart shape |
| `star` | Five-point star |
| `camera` | Camera silhouette |
| `home` | House / home |
| `pin` | Map location pin |
| `fire` | Flame |

No additional symbols are introduced in V1. The set is complete as defined here.

### 6.2 Technical Implementation

Built-in symbols are implemented as custom `VectorDrawable` XML files:

```
res/drawable/ic_branding_heart.xml
res/drawable/ic_branding_star.xml
res/drawable/ic_branding_camera.xml
res/drawable/ic_branding_home.xml
res/drawable/ic_branding_pin.xml
res/drawable/ic_branding_fire.xml
```

Custom VectorDrawables are used instead of `material-icons-extended` to:
- Avoid the ~10 MB `material-icons-extended` dependency
- Allow design-optimized symbols (thicker strokes, better circle-fit visibility)
- Remain independent of Material Icon rename/deprecation cycles

### 6.3 Built-in Symbol to PNG Conversion

When the user selects a built-in symbol:

1. The VectorDrawable is rendered to a Bitmap at 512 × 512 pixels
2. The Bitmap is centered on a 512 × 512 transparent RGBA canvas (Fit semantics)
3. The result is saved as `branding-handle.png`

The PNG file is the truth. The built-in symbol's origin is **documented** in
`metadata.json` (`branding.type = "builtin"`, `branding.builtinId = "heart"`) but
is never required for rendering. Old sessions remain valid regardless of whether the
app still ships that symbol ID.

### 6.4 No Large Icon Library

No `material-icons-extended` or similar large icon dependency is introduced for this
feature. The 6 custom VectorDrawables are the only new drawable assets.

---

## 7. Image Selection and Normalization

### 7.1 Photo Picker

Own images are selected via the Android Photo Picker:
`ActivityResultContracts.PickVisualMedia(PickVisualMedia.ImageOnly)`.

- No `READ_MEDIA_IMAGES` or `READ_EXTERNAL_STORAGE` permission is required
- The returned URI is valid only for the duration of the selection callback
- The image is immediately decoded and normalized; the URI is not persisted

**SAF / ACTION_OPEN_DOCUMENT is not used** for branding image selection. The Photo
Picker provides sufficient access without additional permissions.

### 7.2 Supported Input Formats

| Format | Notes |
|---|---|
| JPEG | Full support |
| PNG | Full support including alpha channel |
| HEIC / HEIF | Decoded via `ImageDecoder` (API 28+; minSdk = 29) |
| WebP | Full support |
| GIF | First frame only; acceptable for branding logos |

Other formats: `BitmapFactory.decodeStream()` is attempted as a fallback. On failure:
show error snackbar, no branding change.

### 7.3 Normalization Rules

All selected images — own images and built-in symbols alike — are normalized to:

| Property | Value |
|---|---|
| Format | PNG |
| Dimensions | 512 × 512 pixels |
| Color mode | ARGB_8888 (alpha channel always present) |
| Scaling | Fit (preserve aspect ratio; no crop) |
| Background | Transparent (alpha = 0) |
| Orientation | Auto-corrected for EXIF orientation before scaling |

**Fit semantics:** the source image is scaled to fit entirely within 512 × 512 while
preserving its aspect ratio. Remaining area is transparent. A landscape logo (800 × 200)
becomes a 512 × 128 image centered on a 512 × 512 transparent canvas.

**Transparent background:** the circle background (#F5F7FA) is rendered at export time,
not baked into the PNG. The PNG always has a transparent background, making it flexible
for any future use (different circle backgrounds, dark mode, video).

### 7.4 Normalization Failure

If normalization fails for any reason:
- Show a short error snackbar ("Couldn't load image")
- Do not change the existing branding (global or session)
- Log the failure in debug builds

### 7.5 Branding Privacy — Mandatory Metadata-Clean Requirement

**Branding assets must always be metadata-clean. This rule is unconditional and
independent of the `strip_originals_metadata` privacy setting.**

A user must not accidentally introduce EXIF data, GPS coordinates, camera information,
device identifiers, or any other metadata into a session, backup, or export through
a branding image.

**The original source image is never stored.** Only the normalized PNG is saved.
This applies both to own images and to built-in symbols.

**The normalized `branding-handle.png` must contain:**

- Raw pixel data only (Bitmap → PNG encode)
- No EXIF segment (no APP1)
- No XMP segment (no APP1 UUID / `http://ns.adobe.com/`)
- No IPTC/IIM data (no APP13)
- No Camera Make / Model
- No GPS coordinates
- No MakerNotes
- No ICC profile beyond what the PNG encoder includes by default (acceptable)

**The normalization pipeline guarantees this automatically:**
Decoding the source to a `Bitmap` and then compressing that Bitmap with
`Bitmap.compress(CompressFormat.PNG, ...)` strips all metadata. No explicit
EXIF-stripping step is needed because the decode→encode round trip discards all
non-pixel data. This is the same privacy guarantee as other decode→re-encode paths
in the codebase (`SESSION_ORIGINALS_PRIVACY_V1.md`).

**What `metadata.json` may store for branding:**

- `branding.handleFile` — filename of the PNG
- `branding.type` — `"image"` or `"builtin"`
- `branding.builtinId` — built-in symbol ID when applicable
- `branding.updatedAtMs` — timestamp of last branding update

**What `metadata.json` must NOT store for branding:**

- The original source URI
- The original filename
- Any EXIF field from the source image (date, camera, GPS, etc.)
- Any provenance linking the branding PNG to its source file

**Built-in symbols:** VectorDrawable → Bitmap render produces a clean Bitmap with no
metadata by definition. The same `Bitmap.compress(PNG)` output path applies.

**This rule is not a setting.** There is no user-facing toggle to skip metadata
cleaning for branding images. Every branding asset written to disk is metadata-clean.

---

## 8. Branding Handle Design

### 8.1 Principles

The branding handle design is deterministic. No automatic color analysis, no adaptive
backgrounds, no dynamic ring color. The visual appearance is fully defined by this spec
and must not vary based on the content of the logo.

### 8.2 Standard Handle (no branding)

The standard SameView handle (as specified in `SHARE_COMPARISON_IMAGE_V1.md` FD-17
and the `CompareScreen` implementation) is unchanged:

- Outer ring: white, two arcs with 12° gaps at top/bottom
- Inner circle: white (`#FFFFFF`)
- Content: `SameViewAccent` left/right arrows

### 8.3 Branding Handle (branding active)

When session branding is active and the "Use branding" toggle is ON:

**Outer ring:**
- Color: `SameViewAccent` (same blue as the standard handle arrows)
- Two arcs with 12° gaps at top/bottom (same geometry as standard handle)
- Thickness: same as standard handle (2 dp at preview scale, proportional at export scale)

**Inner circle:**
- Color: `#F5F7FA` (off-white, not pure white)
- Same shadow as standard handle

**Logo:**
- Source: `branding-handle.png` from session folder
- Rendered at **72%** of the branding circle diameter
- Fit semantics: logo scaled to fit in 72% area, aspect ratio preserved
- Centered in the circle
- Logo is NOT clipped to a circle — rectangular/irregular logo shapes are preserved
- No additional background behind the logo (circle provides #F5F7FA background)

### 8.4 Handle Sizing

**Standard handle size:**
```
handleSize = min(comparisonWidth × 15%, comparisonHeight × 20%, 36dp_equivalent)
```

**Branding handle size:**
The branding handle is 1.5× the standard handle size:
```
brandingHandleSize = standardHandleSize × 1.5
```

In the Compose preview (`ShareComparisonPreview.kt`):
```kotlin
val standardHandleSize = minOf(compW.value * 0.15f, compH.value * 0.20f, 36f).dp
val brandingHandleSize = minOf(compW.value * 0.225f, compH.value * 0.30f, 54f).dp
```

In the export renderer (`SliderRenderStrategy`), the same proportional formula is used
in pixel space. Preview and export must compute equivalent sizes from the same formula.

### 8.5 Dark / Light Logo Handling

The `#F5F7FA` circle background ensures logos are visible regardless of logo color. No
auto-adaptation is performed. The `SameViewAccent` outer ring provides visual separation
from the comparison content in all configurations.

A user who selects a white or near-white logo may experience low contrast between the
logo and the `#F5F7FA` background. This is the user's choice; V1 does not attempt to
detect or prevent this.

### 8.6 Preview = Export

The `ShareComparisonPreview` composable and the `SliderRenderStrategy` export renderer
must produce visually identical results for the branding handle. Both must:
- Use the same handle size formula (preview in dp, export in scaled pixels)
- Apply the same ring geometry (SameViewAccent color, same arc angles and gaps)
- Render the logo at the same 72% ratio inside the circle
- Use the same #F5F7FA circle background color

---

## 9. Metadata Schema v6

### 9.1 Version Bump Rationale

Schema version 6 is introduced with the Session Branding feature. All new sessions
written after the v6 implementation is deployed will carry `"version": 6`, regardless
of whether they have branding.

`SessionScanner.SUPPORTED_VERSIONS` must be updated to accept `{2, 3, 4, 5, 6}`.

### 9.2 Changes from v5

**`files` block — one new optional field:**

```text
files.brandingHandle   String   Filename of the branding handle PNG (always "branding-handle.png")
                                Optional — absent when no session branding
```

**New `branding` block (optional):**

```text
branding.handleFile    String   Filename of the branding handle PNG ("branding-handle.png")
                                Must match files.brandingHandle when both present
branding.type          String   "image" | "builtin"
                                "image": user-provided own image
                                "builtin": generated from a built-in symbol
branding.builtinId     String?  Symbol ID (e.g. "heart", "star") — only present when type = "builtin"
                                null / absent when type = "image"
branding.updatedAtMs   Long     Timestamp of last branding update (ms since Unix epoch)
```

All other v5 fields are unchanged.

### 9.3 files Block (v6 full)

```text
files.capture                 String    "capture.jpg"
files.captureOriginal         String    "capture-original.jpg"
files.reference               String    "reference.jpg"
files.referenceOriginal       String    "reference-original.jpg"
files.referenceSourceOriginal String    "reference-source-original.[ext]"
files.brandingHandle          String?   "branding-handle.png" (OPTIONAL — new in v6)
```

### 9.4 branding Block (v6)

```text
branding.handleFile    String    "branding-handle.png"
branding.type          String    "image" | "builtin"
branding.builtinId     String?   Built-in symbol ID; null or absent when type = "image"
branding.updatedAtMs   Long      Last branding update time in ms since epoch
```

The `branding` block is optional. It is absent when the session has no branding.
When `branding` is absent, `files.brandingHandle` must also be absent, and vice versa.

### 9.5 Full v6 Example (with branding)

```json
{
  "version": 6,
  "session": { "id": "2026-07-15_14-30-00", "createdAtMs": 1752924600000 },
  "files": {
    "capture": "capture.jpg",
    "captureOriginal": "capture-original.jpg",
    "reference": "reference.jpg",
    "referenceOriginal": "reference-original.jpg",
    "referenceSourceOriginal": "reference-source-original.jpg",
    "brandingHandle": "branding-handle.png"
  },
  "capture": {
    "timestampMs": 1752924600000,
    "mediaStoreUri": "content://media/external/images/media/5678"
  },
  "reference": {
    "sourceUri": "content://com.google.android.apps.photos.../...",
    "sourceMimeType": "image/jpeg",
    "date": "2015-08",
    "dateSource": "exif",
    "userEdited": false
  },
  "viewport": { "width": 1080, "height": 1920, "orientation": "PORTRAIT" },
  "overlay": { "scale": 1.0, "offsetX": 0.0, "offsetY": 0.0, "displayMode": "COMPARE_WITH_PREVIEW" },
  "rendering": { "referenceBackgroundColor": "#17202F", "referenceJpegQuality": 90 },
  "additional": { "isFavorite": false, "visibility": "private", "source": "sameview" },
  "branding": {
    "handleFile": "branding-handle.png",
    "type": "image",
    "builtinId": null,
    "updatedAtMs": 1752924600000
  }
}
```

### 9.6 Full v6 Example (with built-in symbol)

```json
{
  "version": 6,
  "files": {
    "capture": "capture.jpg",
    "captureOriginal": "capture-original.jpg",
    "reference": "reference.jpg",
    "referenceOriginal": "reference-original.jpg",
    "referenceSourceOriginal": "reference-source-original.jpg",
    "brandingHandle": "branding-handle.png"
  },
  "branding": {
    "handleFile": "branding-handle.png",
    "type": "builtin",
    "builtinId": "fire",
    "updatedAtMs": 1752924600000
  }
}
```

### 9.7 Full v6 Example (no branding)

```json
{
  "version": 6,
  "files": {
    "capture": "capture.jpg",
    "captureOriginal": "capture-original.jpg",
    "reference": "reference.jpg",
    "referenceOriginal": "reference-original.jpg",
    "referenceSourceOriginal": "reference-source-original.jpg"
  }
}
```

No `branding` block, no `files.brandingHandle`. This is a fully valid v6 session.

### 9.8 Scanner Validation (v6)

`SessionScanner` must accept version 6. For v6 sessions:

- All v5 validation rules apply unchanged
- If `files.brandingHandle` is present:
  - Value must be non-empty
  - Value must pass `isSafeFilename()`
  - `File(sessionDir, files.brandingHandle).exists()` must be true
  - On failure: session is rejected (same strictness as `captureOriginal` in v5)
- If `branding` block is present but `files.brandingHandle` is absent:
  - Inconsistency → treat session as having no branding; do NOT reject the session
  - Log warning in debug builds
- If `files.brandingHandle` is present but `branding` block is absent:
  - Inconsistency → treat session as having no branding; do NOT reject the session
  - Log warning in debug builds
- `branding.type` unknown values: silently accepted (forward compatibility)
- `branding.builtinId` unknown values: silently accepted

### 9.9 Backward Compatibility

Sessions at versions 2–5 are unchanged and remain fully valid. The scanner and
backup exporter handle v5 and v6 uniformly via the metadata-driven `files.*` approach.

The backup exporter (`SessionBackupExporter`) reads the `files.*` block from each
session's `metadata.json` to build the per-session file list. Since `files.brandingHandle`
is in the `files.*` block, `branding-handle.png` is automatically included in backups
for v6 sessions that have branding. No changes to `SessionBackupExporter` are required.

---

## 10. Session Lifecycle

### 10.1 New Session Creation

At session creation (`SessionStorage.saveSession()`):

1. Check if `filesDir/branding/handle.png` exists
2. If exists:
   - Copy it to `<sessionDir>/branding-handle.png`
   - Write `files.brandingHandle = "branding-handle.png"` in `metadata.json`
   - Write `branding.handleFile`, `branding.type`, `branding.builtinId`,
     `branding.updatedAtMs` in `metadata.json` (derive from global branding metadata)
3. If not exists:
   - No branding file is created
   - `files.brandingHandle` is absent from `metadata.json`
   - `branding` block is absent from `metadata.json`

The global branding copy happens as part of the session save pipeline. A failure to copy
global branding is **non-fatal**: the session is saved without branding rather than the
save failing entirely. This matches the fail-soft pattern of other session creation steps.

### 10.2 Edit Session Branding

The user may add, change, or remove branding at any time via Edit Session.

**Add / Change branding:**
1. User selects own image or built-in symbol via `BrandingPickerSheet`
2. Image is normalized to 512 × 512 RGBA PNG
3. PNG is written atomically to `<sessionDir>/branding-handle.png`
4. `metadata.json` is updated with `files.brandingHandle` and `branding` block
5. `branding.updatedAtMs` is set to current time

**Remove branding:**
1. `<sessionDir>/branding-handle.png` is deleted
2. `metadata.json` is updated: `files.brandingHandle` removed, `branding` block removed

### 10.3 "Copy from Default Branding"

This action is available in Edit Session under the following conditions:
- Session currently has **no** branding (`branding-handle.png` absent)
- AND global branding file exists (`filesDir/branding/handle.png` exists)

The action copies `filesDir/branding/handle.png` to `<sessionDir>/branding-handle.png`
and updates `metadata.json` accordingly (same as adding branding).

This action is **not visible** when:
- The session already has branding (user should use "Change branding" instead)
- OR global branding is not set

### 10.4 Missing Branding File at Export Time

If `metadata.json` references `files.brandingHandle` but the file does not exist at
export time:
- Graceful fallback: render the standard SameView handle instead
- Log warning in debug builds
- No crash, no error shown to user (silent fallback only)

---

## 11. Global Settings — Branding Section

### 11.1 Screen Placement

A new **"Branding"** section is added to the Settings screen, below the Privacy section.

Section heading: "Default branding for new sessions" (sentence case per project rules)

### 11.2 Section Contents

```
Default branding for new sessions
──────────────────────────────────
[Preview circle — 64dp, shows current branding or "No branding" placeholder]

  [Choose image]         → opens BrandingPickerSheet (image tab)
  [Choose symbol]        → opens BrandingPickerSheet (symbol tab)
  [Remove]               → only visible when global branding is set

Info text: "Automatically added to new sessions. Existing sessions are not changed."
```

### 11.3 Preview Circle

A 64 dp circle previews the current branding:
- If global branding is set: renders the `handle.png` logo on `#F5F7FA` circle
  with `SameViewAccent` outer ring (matches the export appearance)
- If not set: shows a placeholder with "No branding" text or an empty circle

### 11.4 BrandingPickerSheet (shared component)

`BrandingPickerSheet` is a bottom sheet composable used in both Global Settings and
Edit Session. It has two tabs:

**Tab 1: Image**
- Tap to open Photo Picker
- Selected image is normalized and saved

**Tab 2: Symbol**
- Grid of 6 built-in symbols (2 columns, 3 rows or similar layout)
- Each symbol shown in a small circle preview (48 dp)
- Tap to select, normalize, and save

The sheet's save destination is determined by the caller:
- Global Settings → `filesDir/branding/handle.png`
- Edit Session → `<sessionDir>/branding-handle.png`

---

## 12. Edit Session — Branding Card

### 12.1 Card Position

A new **Branding card** is added to `EditSessionScreen`, between the Current photo card
and the Location card.

Card order: Session → Reference photo → Current photo → **Branding** → Location

### 12.2 Card Contents

**When session has branding:**
```
Branding
──────────────────────────────────
[Preview circle — 64dp]

  [Change]               → opens BrandingPickerSheet
  [Remove]               → removes session branding
```

**When session has no branding:**
```
Branding
──────────────────────────────────
No branding set for this session.

  [Choose image]         → opens BrandingPickerSheet (image tab)
  [Choose symbol]        → opens BrandingPickerSheet (symbol tab)
  [Copy from default branding]  → visible only when global branding exists
```

### 12.3 Branding Card and Dirty State

Changes to session branding in the Edit Session screen:
- Are written **immediately** when the user confirms the selection in `BrandingPickerSheet`
  (not waiting for the Save button)
- Do NOT affect `isDirty` or the Save button state
- Are NOT reverted by "Discard changes" for other form fields

This matches the behavior of the Favorite star (§20 in `SESSION_METADATA_EDITOR_V1.md`):
some actions take effect immediately, independent of the form save flow.

Rationale: branding is a file operation (copy/delete PNG file + metadata update). It is
conceptually separate from text metadata editing. Immediate write eliminates the risk of
partial state.

### 12.4 Storage Functions

New storage functions added to `SessionStorage`:

```kotlin
fun updateSessionBranding(
    sessionsRoot: File,
    sessionId: String,
    brandingPng: ByteArray,     // normalized 512x512 RGBA PNG
    type: String,               // "image" | "builtin"
    builtinId: String?          // null when type = "image"
): Boolean

fun removeSessionBranding(
    sessionsRoot: File,
    sessionId: String
): Boolean

fun copyGlobalBrandingToSession(
    sessionsRoot: File,
    sessionId: String,
    globalBrandingFile: File,   // filesDir/branding/handle.png
    globalBrandingMeta: SessionBrandingMeta?  // type + builtinId from global metadata file
): Boolean
```

All three functions follow the existing path traversal protection pattern.

---

## 13. Share Comparison Image Integration

### 13.1 Use Branding Toggle

A "Use branding" toggle is added to `ShareComparisonScreen`.

**Toggle visibility and state:**

| Session branding | Toggle state | Behavior |
|---|---|---|
| Present | Enabled, default ON | Handle shows branding |
| Absent | Visible but DISABLED | Handle shows standard SameView handle |

The toggle is **always visible** in the Share Comparison Image screen.
When disabled (no session branding), an info text is shown below the toggle:
"Add branding in Edit session."

**Note:** The toggle follows `FD-15` from `SHARE_COMPARISON_IMAGE_V1.md` — it is NOT
persisted. It resets to the default (ON when branding present) each time the screen opens.

### 13.2 Toggle Placement

The "Use branding" toggle is placed in the style/options section of `ShareComparisonScreen`,
below the style selection (Slider / Side by side).

### 13.3 Side by Side Style

The branding toggle applies only to the **Slider** style. Side by side has no slider
handle and therefore shows no branding, regardless of the toggle state.

When Side by side is selected, the "Use branding" toggle is present but visually
indicates it applies to the Slider style (e.g., with a note: "Applies to Slider style").

### 13.4 Share Comparison Screen State

`ShareComparisonViewModel` receives:
- `sessionBrandingFile: File?` — the session's `branding-handle.png` if it exists, else null
- `useBranding: StateFlow<Boolean>` — toggle state (default: true when branding present)

### 13.5 Export Rendering

`SliderRenderStrategy` is extended to accept branding input:

**When `useBranding == true` and `brandingFile != null`:**
1. Load `branding-handle.png` as `Bitmap`
2. Draw branding handle (§8.3) at the 50/50 divider position
3. Replace standard SameView arrows with branding logo

**When `useBranding == false` or `brandingFile == null`:**
- Draw standard SameView handle (unchanged from current implementation)

**When `brandingFile` is referenced but file missing from disk:**
- Graceful fallback to standard SameView handle (silent)
- Log warning in debug builds

### 13.6 Preview Consistency

`ShareComparisonPreview` must render the branding handle using the same visual logic as
`SliderRenderStrategy`. The preview must not diverge from the export result.

`ShareComparisonPreview` receives the same `useBranding` and `brandingFile` state from
the ViewModel.

---

## 14. Live Compare Screen (Explicitly Not Affected)

**The `CompareScreen` compare slider handle is unchanged by this feature.**

- `CompareScreen` is a working tool, not an export or branding surface
- The handle in `CompareScreen` continues to show the standard white circle with
  `SameViewAccent` arrows
- No branding file is loaded in `CompareScreen`
- No `branding-handle.png` is read in the compare rendering path
- `CompareSliderViewport`, `CompareDivider`, and all related constants are unchanged

This boundary is intentional and must not be removed without an explicit product decision.

---

## 15. Backup / Restore / Website Reproducibility

### 15.1 Session Self-Containment

A session with branding must be fully reproducible from its folder alone. All export
features (Share Image, future Video) must be able to reproduce the branding from the
session folder without any reference to global settings or device state.

This is guaranteed by:
- `branding-handle.png` residing in the session folder
- `files.brandingHandle` in `metadata.json` registering the file for backup discovery
- No runtime dependency on `filesDir/branding/handle.png` for existing sessions

### 15.2 Backup Export

The `SessionBackupExporter` is metadata-driven: it reads all filenames from the `files.*`
block in `metadata.json`. Since `files.brandingHandle` is in that block (for sessions that
have branding), `branding-handle.png` is automatically included in the backup ZIP.

**No changes to `SessionBackupExporter` are required.**

A v6 session backup ZIP for a session with branding:

```
SameView_2026-07-15_14-30-00.zip
└── 2026-07-15_14-30-00/
    ├── capture.jpg
    ├── capture-original.jpg
    ├── reference.jpg
    ├── reference-original.jpg
    ├── reference-source-original.jpg
    ├── branding-handle.png           ← automatically included
    └── metadata.json
```

### 15.3 Restore

When a session ZIP is restored:
- `branding-handle.png` is extracted to the session folder along with all other session files
- The restored session is immediately ready for export with the original branding
- No re-normalization or global branding lookup is needed

### 15.4 Website Import (Future)

A future website importer reading a session ZIP:
- Finds `branding-handle.png` referenced in `files.brandingHandle`
- Reads `branding.type` and `branding.builtinId` for display/documentation purposes
- Uses `branding-handle.png` directly for the share/export UI
- Does not need the originating device, app version, or global settings

The `branding.builtinId` field tells the website "this logo was derived from the 'fire'
symbol" — useful for UI display but not required for rendering.

### 15.5 Import Compatibility Note

A future session importer reading a v6 session:
- `files.brandingHandle` present and file exists → import session with branding
- `files.brandingHandle` present but file missing → reject session (missing required file)
- `files.brandingHandle` absent → import session without branding (valid)

This follows the same rule as `files.captureOriginal` in v5.

---

## 16. Video Export — Future Compatibility

**Session branding is NOT implemented for video export in V1.**

The architecture is prepared so that future video branding requires no structural changes:

1. `branding-handle.png` already exists in the session folder
2. `CompareSliderRenderEngine` has access to the session directory
3. In a future version, the engine can check for `branding-handle.png` and use it in
   the video slider handle

In `CompareSliderRenderEngine.kt`, the existing handle-drawing code will receive a
comment:
```kotlin
// TODO VIDEO_BRANDING: Check sessionDir for branding-handle.png and render it here
// instead of the standard arrows. See SESSION_BRANDING_V1.md §16.
```

No production code changes to `CompareSliderRenderEngine` are made in V1.

**Video endcard branding** (`BrandingEndcardRenderer`) is a separate surface and is
not affected by this feature. The existing `BRANDING_ENABLED` DataStore key for the
video endcard is unrelated to session branding.

---

## 17. Relationship to Other Specifications

| Specification | Relationship |
|---|---|
| `SHARE_COMPARISON_IMAGE_V1.md` | Extends FD-17 (handle design). "Use branding" toggle added to ShareComparisonScreen. `SliderRenderStrategy` extended for branding handle rendering. |
| `SESSION_METADATA_V1.md` | Introduces schema v6. The `branding` block and `files.brandingHandle` follow the existing block structure and ownership rules (Category 4 — User Content). |
| `SESSION_BACKUP_EXPORT_V1.md` | Branding file included automatically via metadata-driven discovery. §4.2 session file structure updated for v6. |
| `SESSION_METADATA_EDITOR_V1.md` | New Branding card added to `EditSessionScreen` (§12). Branding changes are immediate, outside the Save/Discard flow. |
| `SETTINGS_UX_V1.md` | New "Branding" section added (§11). BrandingPickerSheet is a new shared component. |
| `VIDEO_EXPORT_V1.md` | Not affected in V1. Future compatibility hook documented in `CompareSliderRenderEngine`. |
| `COMPARE_SESSION_RENDERING_V1.md` | Not affected. Branding is a post-processing export concern only. |
| `COMPARE_FLOW_V1.md` | Not affected. `CompareScreen` handle is unchanged. |
| `CLAUDE_PROJECT_INSTRUCTION.md` | Session Storage section must be updated to reference v6 schema and branding file. |
| `IMPLEMENTATION_NOTES.md` | Must be updated after each implementation block is completed and verified. |

---

## 18. Testing Contract

### 18.1 Unit Tests

**Metadata v6 serialization:**
- v6 session with branding: `files.brandingHandle` and `branding` block round-trip correctly
- v6 session without branding: `files.brandingHandle` and `branding` block absent
- `branding.type = "builtin"`, `builtinId = "fire"` serialized and deserialized correctly
- `branding.type = "image"`, `builtinId = null` serialized correctly
- Unknown `branding.type` values silently accepted on read
- v5 session (no branding block): parsed without error; session treated as no-branding

**BrandingNormalizer:**
- JPEG input → 512 × 512 RGBA PNG output
- PNG with alpha → alpha preserved
- Landscape image (800 × 200) → 512 × 128 centered on 512 × 512 transparent canvas
- Portrait image (200 × 800) → 128 × 512 centered on 512 × 512 transparent canvas
- Square image (512 × 512) → no change to dimensions
- Very small image (32 × 32) → upscaled to fit 512 × 512
- Very large image (4000 × 4000) → downscaled to fit 512 × 512
- EXIF orientation applied before scaling

**BrandingNormalizer — metadata-clean:**

- JPEG with EXIF (Make, Model, GPS, DateTimeOriginal) → normalized PNG contains no EXIF
  (verify via `ExifInterface` on the output: GPS tags absent, Make/Model absent)
- JPEG with GPS lat/lon → normalized PNG: `TAG_GPS_LATITUDE` absent, `TAG_GPS_LONGITUDE` absent
- PNG with XMP metadata → normalized PNG produces a clean pixel-only PNG (no text chunks
  that carry XMP; verifiable by scanning raw PNG bytes for `iTXt`/`tEXt` XMP markers)
- Built-in symbol output is metadata-clean by construction (VectorDrawable → Bitmap → PNG)

**BuiltinSymbolRenderer:**
- Each of the 6 symbols renders to a 512 × 512 RGBA PNG without error
- Alpha channel present in output
- Output PNG contains no EXIF or metadata chunks

**GlobalBrandingRepository:**
- No global branding → `handle.png` does not exist → `hasBranding()` returns false
- Set branding → `handle.png` exists, matches normalized input
- Remove branding → `handle.png` deleted

**SessionStorage branding functions:**
- `updateSessionBranding()` → `branding-handle.png` written atomically, metadata updated
- `removeSessionBranding()` → file deleted, metadata cleaned
- `copyGlobalBrandingToSession()` → global PNG copied to session folder, metadata written
- Path traversal protection: invalid sessionId returns false without IO
- Global branding absent during session creation → no branding file, no error

### 18.2 Instrumentation Tests

**Session creation with global branding:**
- Create global `filesDir/branding/handle.png`
- Save a session → `branding-handle.png` appears in session folder
- `metadata.json` contains `files.brandingHandle` and `branding` block
- Global branding change after session creation → session branding unchanged

**Session creation without global branding:**
- No global `filesDir/branding/handle.png`
- Save a session → no `branding-handle.png` in session folder
- `metadata.json` has no `branding` block, no `files.brandingHandle`

**Scanner v6:**
- v6 session with `branding-handle.png` present → accepted
- v6 session with `files.brandingHandle` but file missing → rejected
- v6 session with no branding → accepted
- v5 session → accepted (no branding)

**Backup:**
- v6 session with branding → `branding-handle.png` present in backup ZIP
- v6 session without branding → no `branding-handle.png` in backup ZIP
- v5 session backup unchanged
- Backup ZIP contains only the normalized PNG, never the original source image
  (original source image was never stored; this is structurally guaranteed)

**Metadata-clean (instrumentation — requires real Bitmap decode):**

- Own image selected from Photo Picker with GPS EXIF →
  resulting `branding-handle.png` has no GPS tags (verified via `ExifInterface`)
- Own image with camera Make/Model →
  resulting PNG has no Make/Model (verified via `ExifInterface`)
- Built-in symbol selected →
  resulting PNG has no EXIF tags
- After `updateSessionBranding()`: `metadata.json` contains no source URI,
  no original filename, no EXIF-derived fields in the `branding` block

### 18.3 Share Image Renderer Tests (Instrumentation)

**Toggle behavior:**
- Session branding present, toggle ON → branding handle rendered
- Session branding present, toggle OFF → standard SameView handle rendered
- Session branding absent → toggle disabled → standard SameView handle rendered
- Branding file missing on disk → fallback to standard SameView handle

**Rendering correctness:**
- Branding handle: circle color is `#F5F7FA` (not pure white)
- Branding handle: outer ring is `SameViewAccent`
- Logo centered, scaled to 72% of circle diameter
- Branding handle is 1.5× standard handle size
- Preview composable and export renderer produce equivalent visual results

**Side by side style:**
- Branding has no effect on Side by side rendering

### 18.4 Regression Guard

The following must remain entirely unaffected after this feature is implemented:

**CompareScreen (live handle):**

- `CompareScreen` handle appearance is pixel-identical to pre-branding state
  (white circle + SameViewAccent arrows; branding code path never reached)
- `CompareScreen` slider behavior, gesture handling, label rendering unchanged
- `CompareScreen` fullscreen mode unchanged

**Share Comparison Image (branding OFF path):**

- When branding toggle is OFF, the exported JPEG must be pixel-identical to an
  export from a session without any branding (standard SameView handle)
- No performance regression in the export path when branding is absent

**Session Originals Privacy:**

- `strip_originals_metadata` behavior is unchanged
- The `originals` block in `metadata.json` is written/read identically to before
- No interaction between branding normalization and the originals privacy pipeline

**Video Export:**

- `CompareSliderRenderEngine` output is unchanged (only a TODO comment is added)
- `BrandingEndcardRenderer` is unchanged
- `CreateVideoScreen` and all video export flows are unchanged

**Backup / Scanner:**

- `SessionBackupExporter` behavior for v5 sessions is unchanged
- `SessionScanner` behavior for v2–v5 sessions is unchanged
- All existing `SessionScannerTest` and `SessionBackupExporterInstrumentedTest` pass

**All existing tests:**

- `testDebugUnitTest` fully green after each block
- `connectedDebugAndroidTest` fully green after Block 7

---

## 19. Explicit Non-Goals (V1)

- Live Compare Screen branding (CompareScreen handle is unchanged)
- Video export branding (future only)
- Multiple branding assets per session
- Branding opacity / transparency control
- Round-cropped logo display
- Dark mode adaptive branding
- Animated branding / GIF support
- Sync of branding across devices
- Remote branding asset hosting
- Per-export branding override (the toggle is per-share-session, not per-export)

---

## 20. Open Questions (Pre-Implementation Clarification Needed)

None. All design decisions documented in this specification are final per the product
decisions communicated before this spec was written.
