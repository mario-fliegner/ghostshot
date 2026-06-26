# SHARE_COMPARISON_IMAGE_V1.md

## 1. Document Status

This document is the **authoritative specification** for the Share Comparison Image feature in SameView.

It is written for:
- AI coding systems
- Implementation sessions
- Analysis sessions
- Regression-safe follow-up work

If an implementation proposal conflicts with this document, this document wins.

This specification covers the creation and sharing of a single JPEG image from an existing compare
session. It does not cover video creation, session backup, camera capture behavior, or any
other export format.

---

## 2. Feature Purpose

### What this feature IS

- A user-initiated export of a compare session as a JPEG image
- Triggered from `CompareScreen` via a new **Export** icon in the top app bar
- The Export icon opens a dropdown menu with two items: **Share image** and **Share video**
- Output: a single JPEG file written to `Pictures/SameView` via MediaStore
- Fully offline: no network calls; the app makes no uploads
- Two visual styles: **Slider** (50/50) and **Side by side**
- Optional caption area with user-authored metadata below the comparison

### What this feature IS NOT

- Not a screen recording or UI screenshot
- Not a GIF, animation, or video export
- Not a session backup (no ZIP, no raw session files)
- Not a social media integration (no TikTok, Instagram, WhatsApp buttons)
- Not a general image editor or collage tool
- Not a second output file from the camera capture pipeline
- Not an export with GPS coordinates

---

## 3. Product Philosophy

SameView is a precision recreation camera. The Share Comparison Image feature must reflect this
identity:

- The exported image shows exactly what the session contains — no reinterpretation, no hidden crop
- The comparison must remain the dominant visual element; caption is supplementary context
- Dark, calm aesthetics consistent with the app's design language (`#0D1424` background)
- No watermarks, no app branding overlay on the comparison content
- No social media template look
- The Android Share Sheet is the platform selector; the app does not pick the destination

The exported image must feel like a dignified, timeless visual record of the recreation event —
not a social media marketing template.

---

## 4. Scope Addendum to CLAUDE_PROJECT_INSTRUCTION.md

**IMPORTANT:** Before any implementation begins, a scope addendum must be added to
`CLAUDE_PROJECT_INSTRUCTION.md`. This is required because the original V1 instruction lists
the following items as out of scope:

```
- No comparison export
- No collage export
- No side-by-side export
- Share flow (out of scope)
```

The addendum must clarify (analogous to the 2026-06-01 Session Backup Export addendum):

- The "No comparison export", "No collage export", and "No side-by-side export" restrictions apply
  exclusively to the **camera capture pipeline** — the path from shutter press to the saved photo in
  `Pictures/SameView`. They do not apply to user-initiated session export features.
- "Share flow" in the OUT OF SCOPE section refers to social sharing as a **primary product feature**.
  The Export menu and Android Share Sheet integration introduced by this feature are not a primary
  social sharing product; they are secondary export tools operating on stored session files.
- This feature is a **session post-processing tool** that extends the product scope per this
  addendum, not a modification to the capture pipeline.

This addendum must be written and reviewed before implementation begins. The addendum must also
reference this document.

---

## 5. Fixed Product Decisions

These decisions are final and must not be re-evaluated during implementation.

| # | Decision |
|---|---|
| FD-01 | Exported image is saved to `MediaStore.Images.Media`, `RELATIVE_PATH = Pictures/SameView` |
| FD-02 | Renderer is fully UI-independent: no CompareScreen capture, no display size, no status bar |
| FD-03 | Renderer input: `reference.jpg` and `capture.jpg` from the compare session; `metadata.json` for caption |
| FD-04 | Two styles: **Slider** (50/50) and **Side by side** |
| FD-05 | No watermark, no app branding overlay on the comparison content. The SameView slider handle in the Slider style is a visual identity element, not a watermark — it is part of the comparison presentation, not overlaid marketing content. |
| FD-17 | Slider style includes the SameView handle at the fixed 50/50 divider position. **Standard handle** (default): white filled circle + `SameViewAccent` directional arrows + white outer ring with top/bottom gaps. **Branding handle** (when session has branding and "Use branding" toggle is ON): white outer ring + white circle (identical visual language to the standard handle) + session `branding-handle.png` logo centered at 72% of circle diameter replacing the arrows; handle is 1.5× the standard handle diameter. Handle is purely visual; no interactivity; no dynamic position; no accessibility action. Video Export (VIDEO_EXPORT_V1.md §16.1) is explicitly unaffected. |
| FD-18 | **V2 UX REWORK APPLIED (2026-07-XX). See `SESSION_BRANDING_V2_UX_REWORK.md §4` for the authoritative V2 specification.** V2 implemented state: A dedicated "Comparison logo" `SettingsCard` (title: `share_comparison_logo_card_title`) is placed between the Style card and the Information card. The card is rendered only when Slider style is selected; it is completely absent when Side-by-side is selected. Three zones: (1) preview circle + Show logo toggle (populated) or placeholder + state text (empty); (2) `OutlinedButton` pair for "Choose photo" / "Use a symbol" + optional "Use default logo" TextButton; (3) "Remove logo" TextButton with error color (populated only). Settings card uses the same zone pattern, card title "Default logo" (`settings_logo_section_title`). V1 original: toggle inside Style card, always visible. **UX refinement applied (2026-06-26):** (a) "Use default logo" is visible only when a global default exists AND the current session is not already using that default — the button is hidden when pressing it would perform no meaningful change. (b) "Choose photo", "Use a symbol", and "Use default logo" do not modify the Show logo toggle; they only replace the stored comparison logo. Show logo defaults ON when the first logo is added; subsequent replacements leave the toggle unchanged. (c) 16 dp spacer added between the preview circle and the Show logo row. |
| FD-06 | No GPS coordinates in the exported JPEG (no EXIF GPS tags) |
| FD-07 | No platform picker in the app. Android Share Sheet opens only on explicit user tap on `[Share]` |
| FD-08 | Output format: JPEG at 92% quality for both Standard and Original quality tiers |
| FD-09 | Exported image is saved permanently to `Pictures/SameView`; it remains after Share Sheet interaction |
| FD-10 | Images are fully independent after export: no export history, no session-image link, no re-share from app |
| FD-11 | No FileProvider required: MediaStore URI is used directly for sharing |
| FD-12 | No new Manifest permissions required for this feature |
| FD-13 | Canvas background color: `#0D1424` (SameViewAppBackground). Comparison border color: `#17202F` (SameViewAppSurface) |
| FD-14 | No crop, no automatic reframe, no content modification of session images |
| FD-15 | Caption toggles (Title, Date, Location) are NOT persisted. They reset to defaults when the screen re-opens |
| FD-16 | Default style: Slider. Default quality: Standard. Default toggles: Title ON, Date ON, Location OFF |

---

## 6. Entry Point and CompareScreen Integration

### 6.1 TopAppBar Restructuring

The `CompareScreen` top app bar is updated as part of this feature's scope.

**Previous structure:**
```
← Back  |  [Favourite]  |  [Create Video]  |  [Delete Session]  |  ⋮
```

**New structure:**
```
← Back  |  [Favourite]  |  [Export]  |  [Delete Session]  |  ⋮
```

The Create Video icon is removed from the top app bar. It is replaced by a new **Export** icon.

The overflow menu (⋮) contents are **unchanged**:
- Edit Session
- Backup Session

### 6.2 Export Dropdown Menu

Tapping the Export icon opens a `DropdownMenu` with two items:

```text
───────────────────────────────
  Share image   →  navigates to ShareComparisonScreen
  Share video   →  navigates to CreateVideoScreen (existing behavior)
───────────────────────────────
```

**Design:** The Export dropdown uses the same `DropdownMenu` / `DropdownMenuItem` pattern as the
existing overflow menu (⋮). No new menu component is introduced.

**Icon:** `Icons.Outlined.Share` (or equivalent export/share icon from Material Icons). Must have
content description from string resource `export_entry_content_description`.

**Placement:** The Export icon occupies exactly the same position in the top app bar as the former
Create Video icon. The favourite star, Export icon, Delete icon, and overflow menu remain in that
order.

### 6.3 Availability

The Export icon is shown only when `sessionId != null` (session context is present).

**"Share image"** item in the dropdown is executable only when both `reference.jpg` and
`capture.jpg` exist on the filesystem (same availability rule as the existing Share Video
availability check: `isCreateVideoAvailable`). When session files are missing, the item must not
be offered as an executable action.

**"Share video"** item in the dropdown follows its existing availability rule unchanged.

### 6.4 Session Context Requirement

`ShareComparisonScreen` requires a valid `sessionId`. Without session context, the Export icon is
not shown.

### 6.5 No Impact on Compare Mechanics

This feature does not change:

- Compare rendering, slider behavior, or image display in `CompareScreen`
- Session storage, session scanning, or session deletion logic
- Navigation contracts from `CameraScreen` to `CompareScreen` or from Library to `CompareScreen`
- `compareInput` lifecycle or `savedSessions` state
- The overflow menu contents (⋮)
- The Delete Session icon behavior

---

## 7. Export Styles

### 7.1 Slider (50/50)

The Slider style presents the comparison as it appears in `CompareScreen` at the 50/50 position,
including the SameView slider handle. The handle serves as a visual identity marker that makes
SameView comparison images immediately recognizable.

- Reference image on the left half (0 % to 50 % of comparison width)
- Capture image on the right half (50 % to 100 % of comparison width)
- Centered vertical divider at exactly 50 % of the comparison width
- Divider: gradient soft-transition zone + 1 px white core line (identical to
  `CompareSliderRenderEngine` divider specification at `sliderPos = 0.5`)
- **Handle: rendered at the divider center, vertically centered in the comparison area**
  - White filled circle with `SameViewAccent` (#4F8CFF) left-arrow (◀) and right-arrow (▶) icons
  - White outer ring (two arcs with top/bottom gaps where the divider line flows through)
  - Matches the CompareScreen handle visual exactly; scaled proportionally to canvas resolution
  - Purely visual: no interactivity, no accessibility action, no dynamic position
  - Position is always the fixed 50 % horizontal center; the current slider position in
    `CompareScreen` is never used and never influences the handle position
- No text labels in the comparison area
- Fill semantics: each image covers the full comparison area; proportional centered crop at
  aspect ratio mismatch (same as `CompareSliderRenderEngine`)

**Explicit distinction from Video Export:** `VIDEO_EXPORT_V1.md §16.1` explicitly specifies no
handle in the animated Compare Slider video mode, because a drag-affordance widget in a video
would communicate false interactivity. This rule applies to video only. For Share Comparison
Image, the handle is a deliberate product decision: a static image benefits from the SameView
visual identity marker, and no false interactivity is implied by a visible handle in a JPEG.

### 7.2 Side by side

The Side by side style presents both images simultaneously at full width.

- Reference on the left half (0 % to 50 % of canvas comparison area)
- Capture on the right half (50 % to 100 % of canvas comparison area)
- Thin vertical separator between the halves: 2 px line in `#17202F`
- Fit semantics within each half: each image is scaled to fit its half independently,
  preserving aspect ratio. Empty areas within each half are filled with `#17202F`
- No text labels in the comparison area

### 7.3 No Further Styles

Only two styles are defined. No additional styles, modes, or hybrid options are in scope for V1.

---

## 8. Quality Tiers

### 8.1 Standard

- Canvas longest edge: max 2048 px, preserving session viewport aspect ratio
- JPEG compression quality: 92%
- Caption area included
- Typical file size: 200–800 KB depending on image content

### 8.2 Original

- Canvas dimensions derived from session `viewport.width` / `viewport.height` stored in
  `metadata.json`. If `metadata.json` is unavailable or viewport fields are missing, pixel
  dimensions are derived from `capture.jpg`
- No upscaling: if session viewport is smaller than 2048 px on the longest edge, Original
  produces the same canvas as Standard
- JPEG compression quality: 92%
- Caption area included
- Typical file size: 1–5 MB depending on session resolution
- **Wizard hint (shown when Original is selected):** string resource
  `share_comparison_quality_original_note` = "Full session resolution, larger file"

### 8.3 Canvas Dimensions

Both quality tiers use even-number canvas dimensions (rounded down to nearest even if odd).
The aspect ratio follows the session viewport, not the image content.

### 8.4 JPEG Format Rationale

JPEG is the correct format for this feature:
- Compatible with all Share targets (Instagram, WhatsApp, Messages, email, AirDrop)
- File sizes appropriate for sharing (vs PNG which would be 5–10× larger)
- Session images are already JPEG; rendering into a new JPEG at 92% quality does not produce
  perceptible artifact amplification on photographic content
- The dark caption area renders cleanly at 92% JPEG quality

PNG is explicitly **not** used for this feature.

---

## 9. Canvas Layout

### 9.1 Full Canvas Structure

The exported image is a single JPEG canvas with the following layout (top to bottom):

```
┌──────────────────────────────────────┐  ← Canvas fill: #0D1424
│                                      │
│  ┌────────────────────────────────┐  │  ← Comparison border: 1 px #17202F,
│  │                                │  │    corner radius: equivalent of
│  │       Comparison area          │  │    MaterialTheme.shapes.medium in pixels
│  │  (Slider 50/50 or Side by side)│  │
│  │                                │  │
│  └────────────────────────────────┘  │
│                                      │
│  [Caption area — only when           │  ← Always on #0D1424 background
│   at least one metadata line active] │
│                                      │
└──────────────────────────────────────┘
```

**Canvas background:** `#0D1424` (SameViewAppBackground). This matches the branding endcard
background in `VIDEO_EXPORT_V1.md §13.4` — a deliberate product decision to unify the visual
identity of SameView exports.

**Comparison border:** 1 px rendered border in `#17202F` (SameViewAppSurface). Subtle, not
decorative. Rounded corners at render-resolution equivalent of `MaterialTheme.shapes.medium`.

**No shadow. No Material card elevation. No additional decorative elements.**

### 9.2 Padding Rules

All padding values scale proportionally with canvas resolution.

**Canvas outer padding (all sides):** approximately 4 % of the shortest canvas dimension.

**Between comparison area and caption:** equal to the outer canvas padding (`outerPad`). This
ensures the gap above the caption is visually consistent between Slider and Side by side exports.
Using a fraction of the comparison height (`compH × 4 %`) would produce a 2× larger gap for
portrait Slider exports (where compH is full image height) than for Side by side (where compH is
halved), making Slider captions appear further from the image than intended.

**Caption internal padding (from canvas edge):**
- Left: approximately 4 % of canvas width
- Bottom: approximately 4 % of canvas height

### 9.3 Caption Area Presence and Dynamic Height

The caption area is rendered **only** when at least one caption line produces visible content
after applying toggle state and metadata availability. When no caption content is active, the
canvas below the comparison area is only the outer padding — no footer area is reserved.

**Caption height is dynamic — it grows with the number of visible lines:**

- 0 visible lines → no caption area; canvas below comparison = outer padding only
- 1 visible line → canvas accommodates exactly one text line + gap + outer padding
- 2 visible lines → canvas accommodates exactly two text lines + spacing + gap + outer padding
- 3 visible lines → canvas accommodates all three lines

No fixed reservation for three lines is made when fewer lines are active. The canvas height
for a session with only a date label is significantly smaller than one with title + date + location.

This behaviour applies to both the exported JPEG and the preview in `ShareComparisonScreen`.
The caption height calculation in `computeCanvasDimensions()` mirrors the `CaptionRenderer`
rendering logic precisely, ensuring canvas size matches the actual rendered text block.

---

## 10. Comparison Area Rendering

### 10.1 Session Image Preparation

Before rendering:
1. Decode `reference.jpg` and `capture.jpg` from the session directory
2. Scale each to the comparison area dimensions according to the style's content scale rule
3. Store scaled bitmaps for rendering; recycle after rendering completes

### 10.2 Slider (50/50) Rendering

For the Slider style (fixed at `sliderPos = 0.5`):

1. Fill canvas with `#0D1424`
2. Fill comparison area with `#17202F` (background for empty areas)
3. Draw reference bitmap: fill semantics (covers full comparison area, centered crop at mismatch)
4. Composite capture over reference using gradient soft-transition zone:
   - Identical algorithm to `CompareSliderRenderEngine` at `sliderPos = 0.5`
   - Half-width formula: `(12 × comparisonHeight / 1080).coerceAtLeast(4)` px
   - Gradient: `Color.BLACK` → `Color.TRANSPARENT` over `[sliderX − halfWidth, sliderX + halfWidth]`
5. Draw 1 px white core line at `sliderX = comparisonWidth / 2`
6. Draw SameView handle at the divider center:
   - Position: `x = sliderX`, `y = comparisonHeight / 2` (vertically centered in comparison area)
   - White outer ring (two arcs, 12° gaps at top/bottom); ring thickness and gap scale proportionally
   - White filled circle; radius scales proportionally with canvas resolution
   - `SameViewAccent` (#4F8CFF) left-arrow (◀) and right-arrow (▶) inside the circle; same
     geometry as `CompareScreen` handle arrows
   - Handle is drawn on top of all comparison content and the divider line
7. Draw comparison border
8. Draw caption (if active)

Reference in both halves is visible due to gradient; divider is hard-centered; handle is
at the fixed horizontal midpoint, vertically centered.

### 10.3 Side by Side Rendering

**Comparison area height is style-specific.** For Side by side, `compH` is set to
`makeEven(sliderCompH / 2)` before canvas allocation. This is necessary because each image
occupies only half the comparison width (`halfWidth = compW / 2`). With Fit semantics, the
natural visible height in each half is:

```text
visibleH = halfWidth / ratio = (compW / 2) / (compW / compH) = compH / 2
```

Using the full Slider `compH` for Side by side would produce a comparison area twice as tall
as the images it contains, leaving 50% of each half as empty dark space above and below the
images. Setting `compH = compHBase / 2` ensures the images fill their slots exactly.

For the Side by side style:

1. Fill canvas with `#0D1424`
2. Compute `halfWidth = comparisonWidth / 2`
3. Fill comparison area with `#17202F`
4. Draw reference bitmap fit-scaled into left half `[0, halfWidth]` (centered within half,
   Fit semantics, empty areas `#17202F`)
5. Draw capture bitmap fit-scaled into right half `[halfWidth, comparisonWidth]` (same Fit logic)
6. Draw 2 px vertical line at `x = halfWidth` in `#17202F` (separator, not prominent)
7. Draw comparison border
8. Draw caption (if active)

### 10.4 Memory Management

- Session bitmaps decoded once, held for the render duration, recycled after rendering completes
  in a `try/finally` block
- Canvas bitmap allocated once, not inside any loop
- No `Bitmap.createBitmap()` calls during rendering passes

---

## 11. Caption Logic

### 11.1 Purpose

The caption area below the comparison provides temporal and geographic context for the recreation
event. It is supplementary to the comparison, not the primary content.

The caption uses the same data sources, the same priority logic, and the same formatting as
`VIDEO_EXPORT_V1.md §31` (Show title and date) and §32 (Show location). This ensures that what
users see in the video export matches what they see in the image export.

### 11.2 Data Sources

| Caption element | Source | Notes |
|---|---|---|
| Title | `content.title` from `metadata.json` | Trimmed; blank = absent |
| Date pair | `reference.date` + `capture.timestampMs` via `computeCompareLabels()` | Levels 1–4 only; Level 5 = no date |
| Location | `location.displayName`, `location.city`, `location.country` | Priority logic per §11.4 |

No other metadata fields are used for the caption. GPS coordinates, `reference.dateSource`,
`reference.userEdited`, `additional.*`, EXIF data, and `content.description` are not accessed
by the caption rendering path.

### 11.3 Date Pair Logic

The date pair uses `CompareLabelLogic.computeCompareLabels()` (Levels 1–4 only):

| Level | Condition | Display |
|---|---|---|
| 1 | Different years | `2008 → 2026` |
| 2 | Same year, different months | `Mar 2026 → Oct 2026` |
| 3 | Same year, same month | `12 Jun → 28 Jun` |
| 4 | Reference date present but indistinguishable | `Past → Present` |
| 5 | No `reference.date` | **No date line rendered** |

The separator between left and right label in the caption is `→` (U+2192).

Date formatting (Levels 2 and 3) uses Android locale-aware date formatters. No hardcoded date
format strings.

### 11.4 Location Format

Priority logic (identical to `VIDEO_EXPORT_V1.md §32.3` and `COMPARE_FLOW_V1.md §42`):

| Available fields | Displayed text |
|---|---|
| displayName + city + country | `Am Schwarzsee · Kitzbühel, Österreich` |
| displayName + city | `Am Schwarzsee · Kitzbühel` |
| displayName + country | `Am Schwarzsee · Österreich` |
| displayName only | `Am Schwarzsee` |
| city + country | `Kitzbühel, Österreich` |
| city only | `Kitzbühel` |
| country only | `Österreich` |
| none | (no location line) |

Separator between `displayName` and city/country: middle dot `·` (U+00B7).

### 11.5 Line Ordering

| Active content | Line 1 | Line 2 | Line 3 |
|---|---|---|---|
| Title + Date + Location | Title | Date pair | Location |
| Title + Date | Title | Date pair | — |
| Title + Location | Title | Location | — |
| Date + Location | Date pair | Location | — |
| Title only | Title | — | — |
| Date only | Date pair | — | — |
| Location only | Location | — | — |
| None active | (caption area omitted) | — | — |

Maximum 3 lines. Longer titles are truncated with ellipsis to fit a single line at render scale.

---

## 12. Caption Typography

All measurements scale proportionally with canvas resolution.

**Date pair:**
- Weight: Bold
- Size: approximately 4.5 % of `min(canvasWidth, canvasHeight)` in pixels
- Color: White (#FFFFFF)

**Title:**
- Weight: Regular
- Size: approximately 3.5 % of `min(canvasWidth, canvasHeight)` in pixels
- Color: White (#FFFFFF)

**Location:**
- Weight: Regular
- Size: approximately 3.5 % of `min(canvasWidth, canvasHeight)` in pixels
- Color: White (#FFFFFF)

**Text shadow:** Black at approximately 75 % opacity, 1 px offset, blur radius approximately 3–5 px
at 1080p canvas height, scales proportionally. Rendered via Bitmap Canvas text shadow (same
approach as `TitleDateOverlayRenderer`). Not via `Paint.setShadowLayer()` for performance reasons.

**Line spacing:** baseline-to-baseline distance = `maxOf(currentLine.textSize, adjacentLine.textSize) × 1.20`.

**No background plate, no chip, no badge, no outline, no blur, no Material component in the
caption area.**

---

## 13. Information Toggles

Three independent optional toggles control what metadata appears in the caption area.

| Toggle | Label | Default | Persisted |
|---|---|---|---|
| Title | "Title" | ON | No |
| Date | "Date" | ON | No |
| Location | "Location" | OFF | No |

Toggles are **local export state** within the active `ShareComparisonScreen` session. They reset to
defaults when the screen is re-opened (same behavior as "Show title and date" and "Show location"
toggles in `CreateVideoScreen`). No DataStore key is added for any toggle.

### 13.1 Disabled Toggle States

A toggle is **disabled** (visible, grayed out, not tappable) when no content is available for it:

| Toggle | Disabled when |
|---|---|
| Title | `content.title` is absent or blank |
| Date | `computeCompareLabels()` returns Level 5 (no `reference.date`) |
| Location | All of `location.displayName`, `location.city`, `location.country` are absent |

When a toggle is disabled, a hint line appears below it (same pattern as `CreateVideoScreen`
disabled Extras toggles):

| Toggle | Disabled hint |
|---|---|
| Title | `share_comparison_no_title_hint`: "Add a title in Edit Session" |
| Date | `share_comparison_no_date_hint`: "Add a reference date in Edit Session" |
| Location | `share_comparison_no_location_hint`: "Add location in Edit Session" |

**Disabled toggles remain visible.** Discoverability requires that the user can discover these
features exist even when metadata is missing.

### 13.2 Dynamic Preview Lines

A preview line appears directly below each toggle label (regardless of toggle state), showing
exactly the text that will appear in the exported image.

| Toggle | Preview line content |
|---|---|
| Title | The title text, or the disabled hint if absent |
| Date | The computed date pair (e.g., "2008 → 2026"), or the disabled hint |
| Location | The location string (e.g., "Grünwald"), or the disabled hint |

The preview line always uses `SameViewSettingsSecondaryText` style. It is never hidden.

---

## 14. Live Preview in Screen

### 14.1 Purpose

The `ShareComparisonScreen` contains a live preview showing the full export canvas layout. The user
immediately sees:
- Slider vs Side by side comparison appearance
- Caption area with current metadata and toggle state
- Canvas background and overall image proportions
- Real session images (not placeholders)

### 14.2 Technical Implementation

The live preview is a **Compose-only visual simulation** of the export canvas. It is not a bitmap
render of the actual export output. It uses `AsyncImage` (Coil) for the session images and a
`Canvas` composable to simulate the comparison composition.

The preview is **ratio-proportional**: its height is derived from the actual session viewport
aspect ratio, so portrait sessions appear tall, landscape sessions flat, and square sessions
square — accurately reflecting the exported JPEG's proportions.

The preview fills the available card width (`compW = availableW`). **Comparison height is
style-dependent** — Slider and Side by side have different natural heights because they give
each image a different effective width:

```text
Slider:       compH = min(availableW / ratio,  availableW × 1.5,  500 dp)
Side by side: compH = min((availableW / 2) / ratio,  500 dp)
totalH        = min(compH + captionOverhead + outerPad×2,  550 dp)
```

**Why different heights:**

- Slider gives each image the **full** preview width; height follows `availableW / ratio`.
- Side by side gives each image **half** the preview width. With `ContentScale.Fit`, the natural
  image height in each half is `(availableW / 2) / ratio`. Using the Slider height for Side by
  side would leave 50% of the comparison area as empty dark space.

The preview container changes height when the user switches between Slider and Side by side.
This is correct and expected: the two styles have inherently different proportions.

**Session format behaviour at 330 dp card width (halfW = 165 dp):**

| Format | ratio | Slider compH | Side by side compH | SbS letterboxing |
| --- | --- | --- | --- | --- |
| 9:16 portrait | 0.5625 | 495 dp (1.5× cap) | **293 dp** (exact) | None |
| 3:4 portrait | 0.75 | 440 dp (exact) | **220 dp** (exact) | None |
| 1:1 square | 1.0 | 330 dp (exact) | **165 dp** (exact) | None |
| 4:3 landscape | 1.333 | 248 dp (exact) | **124 dp** (exact) | None |
| 16:9 landscape | 1.778 | 187 dp (exact) | **93 dp** (exact) | None |

Side by side `compH` is always exactly half the Slider `compH` (before caps). With `ContentScale.Fit`,
both images fill their respective half-width slots completely with no letterboxing.

The screen is scrollable; portrait Slider previews push the Quality card and Share button below
the initial fold. This is expected and consistent with `CreateVideoScreen` scroll behaviour.
Side by side previews are significantly more compact and often fit without scrolling.

**Outer canvas padding:** A uniform 4 dp inset surrounds the comparison area on all four sides
within the preview canvas. This makes the dark `#0D1424` canvas visible around the comparison
image, mirrors the outer padding of the actual export canvas, and ensures the comparison border
(`#17202F`) is visually legible against the dark background. The padding is constant regardless
of caption state — the preview always looks like a framed export object.

**Slider preview:** Static 50/50 split with a visible centered vertical divider line and the
SameView handle (filled `SameViewAccent` circle with directional arrows) at the divider
midpoint. Images use `ContentScale.Crop` — consistent with the export's Fill semantics. No
animation.

**Side by side preview:** Two equal halves separated by a 1 dp line. Images use
`ContentScale.Fit` — both reference and capture are always fully visible within their respective
halves. Letterboxing may appear when the image aspect ratio does not match the half-slot
proportions; this is correct and expected behaviour, as it preserves the complete image rather
than cropping. The dark `#0D1424` canvas background fills any letterbox areas. `ContentScale.Fit`
is the defining requirement for Side by side: both images must be shown in their entirety, which
is the visual distinction from the Slider style.

**Caption preview:** Caption lines rendered below the comparison area in the preview, matching
the font weight and hierarchy (bold date pair, regular title/location), but at a fixed readable
scale relative to the preview container rather than proportional to export resolution.

**Reduce Motion:** Always static (comparison images are static; no animation exists for this feature).

### 14.3 Style Switch

When the user switches between Slider and Side by side, the preview updates immediately. No
transition animation is required.

### 14.4 Loading State

While session images are loading in the preview, show the canvas background (`#0D1424`) and a
subtle loading placeholder. The Share button may remain available; rendering uses the actual
decoded bitmaps from the session directory, not the Coil-cached preview images.

---

## 15. ShareComparisonScreen UX

### 15.1 Screen Type

`ShareComparisonScreen` is a **separate fullscreen screen** with its own Navigation Compose route
(`shareComparisonRoute`). It is not a bottom sheet, not a dialog, and not a modal overlay.

### 15.2 State

`ShareComparisonScreen` has a single meaningful state: **Configuring**. No separate Rendering or
Preview states are needed. Image creation is fast enough (typically < 500 ms at Standard quality)
that a brief progress state on the Share button suffices.

```
Configuring  →  [Share tap]  →  brief in-button progress  →  Share Sheet opens
              ↓ on error                                     ↓ after Share Sheet
              Error Snackbar                                 Returns to Configuring
```

Back navigation from Configuring closes `ShareComparisonScreen` and returns to `CompareScreen`.

### 15.3 Configuring State Layout

> **V2 UX REWORK APPLIED (2026-07-XX).** A dedicated "Logo on handle" card was added
> between the Style card and the Information card. See `SESSION_BRANDING_V2_UX_REWORK.md §4`.

**V2 implemented layout:**

```
TopAppBar:  ← Back   "Share comparison"

┌─────────────────────────────────────────────┐
│ Style                                       │  ← SettingsCard
│ [ Slider ]  [ Side by side ]                │  ← SameViewSegmentControl
│ ─────────────────────────────────────────── │  ← HorizontalDivider (within card)
│ [ Live preview — session aspect ratio ]     │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐  ← SettingsCard; Slider only, absent in SbS
│ Logo on handle                              │
│ [placeholder/preview circle]  No logo …    │  ← empty state
│   or [circle]  [○] Show logo               │  ← populated state (toggle)
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ Information                                 │  ← SettingsCard
│ [x] Title                                   │  ← SettingsSwitchRow
│     My grandparents                         │  ← preview line
│ [x] Date                                    │  ← SettingsSwitchRow
│     2008 → 2026                             │  ← preview line
│ [ ] Location                                │  ← SettingsSwitchRow
│     Munich, Germany                         │  ← preview line
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ Quality                                     │  ← SettingsCard
│ [ Standard ]  [ Original ]                  │  ← SameViewSegmentControl
└─────────────────────────────────────────────┘

[ Share ]                                        ← full-width Button; navigationBarsPadding()
```

**V1 original layout (superseded — no separate Logo card, toggle was inside Style card):**

```
TopAppBar:  ← Back   "Share comparison"

┌─────────────────────────────────────────────┐
│ Style                                       │  ← SettingsCard
│ [ Slider ]  [ Side by side ]                │  ← SameViewSegmentControl
│ ─────────────────────────────────────────── │  ← HorizontalDivider (within card)
│ [ Live preview — session aspect ratio,      │
│   max 200 dp height                       ] │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ Information                                 │  ← SettingsCard
│ [x] Title                                   │  ← SettingsSwitchRow
│     My grandparents                         │  ← preview line
│ [x] Date                                    │  ← SettingsSwitchRow
│     2008 → 2026                             │  ← preview line
│ [ ] Location                                │  ← SettingsSwitchRow
│     Munich, Germany                         │  ← preview line
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ Quality                                     │  ← SettingsCard
│ [ Standard ]  [ Original ]                  │  ← SameViewSegmentControl
└─────────────────────────────────────────────┘

[ Share ]                                        ← full-width Button at the end of the
                                                   scrollable column (not a sticky bottomBar);
                                                   navigationBarsPadding(); enabled when
                                                   session files exist
```

**Share button placement rationale:** `ShareComparisonScreen` has no text input fields and therefore
does not need `imePadding()`. The CTA at the end of the scroll column matches `CreateVideoScreen`
exactly (not `EditSessionScreen`, which uses a sticky bottomBar only because its Save button must
stay above the keyboard). With only three compact cards (Style, Information, Quality), the total
scroll height is small enough that the Share button is reachable with minimal scrolling on all
compact devices.

### 15.4 Wizard State Persistence

Style, quality, and toggle state may survive normal rotation within the active screen session.
They must not persist across app restarts. No DataStore keys are introduced.

---

## 16. Share Flow

### 16.1 Full Flow

```
ShareComparisonScreen (Configuring)
    User taps [Share]
    ↓
Brief progress (button shows loading state; screen remains interactive)
    ↓ image rendered and written to MediaStore
Share Sheet opens (Intent.ACTION_SEND with MediaStore URI)
    ↓ user shares or dismisses Share Sheet
ShareComparisonScreen (Configuring, state unchanged)
```

The Share Sheet opens only on explicit `[Share]` tap. Never opens automatically.
Canceling the Share Sheet is not an error condition. The image remains saved in `Pictures/SameView`.

### 16.2 Share Intent

```kotlin
Intent(Intent.ACTION_SEND).apply {
    type = "image/jpeg"
    putExtra(Intent.EXTRA_STREAM, imageMediaStoreUri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
```

### 16.3 No Post-Share Management UI

Unlike `CreateVideoScreen` (which has a Preview state with Delete Video), `ShareComparisonScreen`
does not offer an in-app mechanism to delete the created image. The image is saved to
`Pictures/SameView` and managed by the system gallery or file manager. This matches user
expectations for image exports and avoids UI complexity.

---

## 17. Storage and MediaStore Contract

### 17.1 MediaStore Insertion

Image is written to `MediaStore.Images.Media` with:

```text
RELATIVE_PATH = Pictures/SameView
DISPLAY_NAME  = SameView_<exportTimestamp>_<style>.jpg
MIME_TYPE     = image/jpeg
IS_PENDING    = 1
```

Where:

- `<exportTimestamp>` is the current wall-clock time at export start, formatted as `yyyyMMdd_HHmmss` (e.g., `20240615_143022`). This is the export time, not the session capture time.
- `<style>` is `slider` or `sidebyside`.

Example: `SameView_20240615_143022_slider.jpg`

The export timestamp guarantees a unique filename per export even when the same session is exported multiple times with identical settings. The session capture time (`sessionId`) is not used in the filename.

### 17.2 IS_PENDING Lifecycle

1. Insert entry with `IS_PENDING = 1` before rendering begins
2. Open `FileDescriptor` from the inserted URI
3. Compress rendered Bitmap to JPEG (quality 92) and write to FileDescriptor
4. After write completes successfully: update `IS_PENDING = 0`
5. On failure: `contentResolver.delete(uri, null, null)` (best-effort cleanup)

Only after `IS_PENDING = 0` is the image accessible to Share Sheet and Gallery.

### 17.3 No FileProvider Required

The image resides in `Pictures/SameView` under the system MediaStore. Its URI is a `content://`
URI issued by `com.android.providers.media.MediaProvider`. This URI is directly usable in
`Intent.ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION`.

### 17.4 Permanent Storage

Images are saved permanently. The user manages them through the system Gallery or file manager.
The SameView app does not track, reference, or offer in-app deletion of previously created
comparison images. This matches the `VIDEO_EXPORT_V1.md` approach (FD-10).

---

## 18. Privacy

### 18.1 No GPS Coordinates

GPS coordinates are explicitly **NOT** written into the exported JPEG.

- No GPS EXIF tags are written to the output image
- The rendering pipeline creates a new composite bitmap from session images; no EXIF from
  `reference.jpg` or `capture.jpg` is copied to the output
- `Bitmap.compress()` does not write any EXIF block — the output JPEG contains no EXIF
  by construction
- `ExifInterface` is **never called** on the output JPEG. No EXIF write of any kind is
  performed after compression. No EXIF injection step exists in the rendering pipeline.
- No XMP sidecar, no IPTC metadata, no ICC profile with location data is written
- Only the caption text (user-authored `content.title`, `reference.date`, `location.*`) is
  included as visible pixel content in the image

This matches `VIDEO_EXPORT_V1.md §23.2` (no GPS in exported video).

**Rationale:**
- GPS coordinates are precise location data with significant privacy implications
- Users sharing an image to social media do not expect their GPS coordinates to travel with it
- User-authored location text fields represent conscious, curated geographic context
- This policy is consistent across all SameView export features

### 18.2 User-Authored Location Text Only

The only location information in the exported image is visible caption text chosen by the user
through the Location toggle. No hidden metadata, no EXIF location, no XMP geolocation data is
added to the output.

### 18.3 Play Store Compliance

No new Data Safety entries required:

- User-initiated, fully local export
- No GPS data in output
- Sharing is user-controlled via standard Android Share Sheet
- No analytics, tracking, or telemetry
- No network calls

Existing Data Safety declarations fully cover this feature.

---

## 19. Renderer Architecture

### 19.1 Overview

```
ShareComparisonViewModel
└── ShareImageRenderer
    ├── ShareRenderConfig    (parameters for rendering)
    ├── SliderRenderStrategy (50/50 Slider composition)
    ├── SideBySideStrategy   (Side by side composition)
    ├── CaptionRenderer      (caption text onto canvas)
    └── ShareMediaStoreWriter (MediaStore write + IS_PENDING lifecycle)
```

`ShareImageRenderer` is the single entry point for `ShareComparisonViewModel`. It orchestrates
decode → render → encode → write.

### 19.2 ShareRenderConfig

```kotlin
data class ShareRenderConfig(
    val style: ShareComparisonStyle,
    val quality: ShareQuality,
    val captionData: ShareCaptionData?,   // null = no caption area
    val sessionDir: File,
    val sessionId: String
)

data class ShareCaptionData(
    val titleLine: String?,       // null when toggle off or content.title absent
    val dateLine: String?,        // null when toggle off or Level 5
    val locationLine: String?     // null when toggle off or no location fields
)

enum class ShareComparisonStyle { SLIDER, SIDE_BY_SIDE }
enum class ShareQuality { STANDARD, ORIGINAL }
```

When all three fields in `ShareCaptionData` are null, pass `captionData = null` to skip caption
area rendering entirely.

### 19.3 Threading

- Bitmap rendering on `Dispatchers.Default` (CPU-bound)
- MediaStore write on `Dispatchers.IO`
- Progress: `isRendering: StateFlow<Boolean>` exposed from `ShareComparisonViewModel`

### 19.4 ViewModel Contract

```kotlin
class ShareComparisonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val shareImageRenderer: ShareImageRenderer
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""

    val style: StateFlow<ShareComparisonStyle>          // default SLIDER
    val quality: StateFlow<ShareQuality>                // default STANDARD
    val titleEnabled: StateFlow<Boolean>                // default true
    val dateEnabled: StateFlow<Boolean>                 // default true
    val locationEnabled: StateFlow<Boolean>             // default false
    val isRendering: StateFlow<Boolean>

    fun onStyleChanged(style: ShareComparisonStyle)
    fun onQualityChanged(quality: ShareQuality)
    fun onTitleToggled(enabled: Boolean)
    fun onDateToggled(enabled: Boolean)
    fun onLocationToggled(enabled: Boolean)
    fun onShare(context: Context)                       // triggers render + Share Sheet
}
```

`ShareComparisonViewModel` reads session metadata from `metadata.json` at initialization to
populate preview lines and determine toggle availability. Reading uses `Dispatchers.IO`.

---

## 20. Navigation

### 20.1 Route

```kotlin
const val ROUTE_SHARE_COMPARISON = "share_comparison/{sessionId}"
```

Navigation from `CompareScreen` passes `sessionId` via `Uri.encode()` (identical to the
`editSessionRoute()` pattern).

### 20.2 Back Navigation

Back from `ShareComparisonScreen` returns to `CompareScreen`. No intermediate navigation.

---

## 21. UI Consistency

### 21.1 Screen Design Language

`ShareComparisonScreen` must be visually identical to `CreateVideoScreen` in:

| Element | Rule |
|---|---|
| TopAppBar | Same `TopAppBar` with Back icon and screen title |
| Card layout | `SettingsCard` for every section (Style, Information, Quality) |
| Segment controls | `SameViewSegmentControl` for Style and Quality selection |
| Toggle rows | `SettingsSwitchRow` for Title, Date, Location |
| Preview placement | Inside the Style card, below the segment control, separated by `HorizontalDivider` |
| Preview hint text | `SameViewSettingsSecondaryText` for dynamic preview lines |
| Primary CTA | Full-width `Button` in `Scaffold.bottomBar` |
| Bottom padding | `navigationBarsPadding()` on the bottom bar |
| Colors | `SameViewAppBackground`, `SameViewAppSurface`, `SameViewAccent` — no new color tokens |
| Typography | Existing Material 3 typescale — no new type tokens |

### 21.2 Export Dropdown Menu

The Export dropdown uses `DropdownMenu` + `DropdownMenuItem` exactly as in the existing
CompareScreen overflow menu. No new menu component is introduced. No new visual style.

### 21.3 No New Design Language

`ShareComparisonScreen` must not introduce:
- New card shapes
- New color tokens
- New typography tokens
- Custom animations
- Social media template aesthetics
- Material cards with elevation or shadows
- Floating action buttons

---

## 22. Responsive Layout

### 22.1 Governing spec

`RESPONSIVE_LAYOUT_SYSTEM_V1.md` applies. `ShareComparisonScreen` is a form+preview screen,
following the same responsive rules as `EditSessionScreen` and `SettingsScreen` (Block 3).

### 22.2 Compact (< 600 dp)

Single-column scrollable layout. Full-width cards and buttons. Current default behavior.

### 22.3 Medium (600–839 dp)

Current behavior unchanged. No structural change. Full-width cards remain adequate at
600–839 dp.

### 22.4 Expanded (≥ 840 dp)

Content column bounded by max-width **680 dp**, centered horizontally.

The live preview inside the Style card may appear larger at 680 dp width — max height remains
capped at 200 dp regardless of width.

The `Scaffold.bottomBar` Share button follows the same pattern as `EditSessionScreen` Block 3B:
the visual button is constrained to 680 dp width; `navigationBarsPadding()` remains on the
outermost container.

---

## 23. Accessibility

### 23.1 Toggle Accessibility

Each toggle row uses the existing `SettingsSwitchRow` semantics (Role.Switch). Disabled toggles
communicate their disabled state via `Switch(enabled = false)`.

Disabled hint text is a regular `Text` composable below the toggle — readable by screen readers.

### 23.2 Export Icon

The Export icon has a content description: string resource `export_entry_content_description`.

The dropdown menu items have visible text labels — no additional content descriptions required.

### 23.3 Share Button

The Share button is a standard Material 3 `Button`. When `isRendering = true`, the button
should show a progress indicator and communicate a loading state via `semantics { }` if needed
(implementation decision: at minimum disable the button during rendering).

### 23.4 Preview

The live preview is excluded from the accessibility tree via
`Modifier.clearAndSetSemantics {}`. It is decorative only — the actual metadata and options are
conveyed by the card content and toggle rows.

---

## 24. i18n Contract

All user-facing text must use string resources. No hardcoded visible strings.

Capitalization rule: Sentence case (first word capitalised, remaining lowercase unless proper noun
or product name). This matches all existing SameView string resources.

### 24.1 Required String Resource Keys

**CompareScreen — TopAppBar:**

| Key | Usage |
|---|---|
| `export_entry_content_description` | Export icon content description: "Export" |
| `export_menu_share_comparison_image` | Dropdown item: "Share image" |
| `export_menu_create_video` | Dropdown item: "Share video" |

**ShareComparisonScreen — General:**

| Key | Usage |
|---|---|
| `share_comparison_screen_title` | TopAppBar: "Share comparison" |

**ShareComparisonScreen — Style card:**

| Key | Usage |
|---|---|
| `share_comparison_style_label` | SettingsCard title: "Style" |
| `share_comparison_style_slider` | Segment: "Slider" |
| `share_comparison_style_side_by_side` | Segment: "Side by side" |

**ShareComparisonScreen — Information card:**

| Key | Usage |
|---|---|
| `share_comparison_info_label` | SettingsCard title: "Information" |
| `share_comparison_toggle_title` | Toggle label: "Title" |
| `share_comparison_toggle_date` | Toggle label: "Date" |
| `share_comparison_toggle_location` | Toggle label: "Location" |
| `share_comparison_no_title_hint` | Disabled title hint: "Add a title in Edit Session" |
| `share_comparison_no_date_hint` | Disabled date hint: "Add a reference date in Edit Session" |
| `share_comparison_no_location_hint` | Disabled location hint: "Add location in Edit Session" |

**ShareComparisonScreen — Quality card:**

| Key | Usage |
|---|---|
| `share_comparison_quality_label` | SettingsCard title: "Quality" |
| `share_comparison_quality_standard` | Segment: "Standard" |
| `share_comparison_quality_original` | Segment: "Original" |
| `share_comparison_quality_original_note` | Hint shown when Original is selected: "Full session resolution, larger file" |

**ShareComparisonScreen — CTA:**

| Key | Usage |
|---|---|
| `share_comparison_action_share` | CTA button: "Share" |

**ShareComparisonScreen — Error / Snackbars:**

| Key | Usage |
|---|---|
| `share_comparison_error_render_failed` | Snackbar: "Could not create image" |

**Filename (not translatable):** `share_comparison_filename` = `"SameView_%1$s_%2$s.jpg"` where `%1$s` is the export timestamp (`yyyyMMdd_HHmmss`) and `%2$s` is the style (`slider` or `sidebyside`).

Key names follow the existing project naming convention. No second naming system is introduced.

---

## 25. Error Handling

| Scenario | Behavior |
|---|---|
| Session files missing at screen entry | Share button disabled; no snackbar |
| Image decode failure during render | Error Snackbar `share_comparison_error_render_failed`; no Share Sheet |
| MediaStore insert failure | Error Snackbar same key; screen remains usable in Configuring state |
| IS_PENDING update failure | Best-effort `contentResolver.delete()`; Error Snackbar |
| Share Sheet cancel | Not an error; image saved; return to Configuring |

No silent failure. Every error that prevents image creation must produce a user-visible Snackbar.

---

## 26. Permissions

No new Manifest permissions required.

| Operation | Permission |
|---|---|
| Write JPEG to `Pictures/SameView` | None. App-owned MediaStore entry on API 29+. |
| Share image via Share Sheet | None. `FLAG_GRANT_READ_URI_PERMISSION` + MediaStore URI. |
| Read session files from `filesDir` | None. Internal app storage. |

`READ_MEDIA_IMAGES`, `WRITE_EXTERNAL_STORAGE`, and `READ_EXTERNAL_STORAGE` must not be added.

---

## 27. Filename and Unicode Safety

The filename is based on the **export timestamp**, not on session titles, location names, or
any other user-authored content. This is a deliberate product decision:

- Session titles can be absent, long, or changed after export
- Location names can contain arbitrary Unicode including characters problematic for some
  filesystems (e.g., `/`, `:`, `*` in Windows-mounted paths)
- An export timestamp is always present, always short, and always ASCII-safe
- Using the export timestamp guarantees uniqueness: repeated exports of the same session
  produce different filenames without MediaStore deduplication

**Pattern:** `SameView_<exportTimestamp>_slider.jpg` or `SameView_<exportTimestamp>_sidebyside.jpg`

Where `<exportTimestamp>` is formatted as `yyyyMMdd_HHmmss` (e.g., `20240615_143022`).

Unicode session titles and location names appear **only in the visible caption area** of the
exported image — never in the filename. This guarantees that:

- Filenames are always filesystem-safe on all Android API levels and mounted storage
- Unicode in user content is fully preserved in the rendered caption
- Examples like `München`, `Łódź`, `東京駅`, `Кремль` render correctly in the caption
  without any sanitization
- No Unicode normalization, romanization, or stripping occurs anywhere in the export pipeline

`DISPLAY_NAME` in MediaStore supports Unicode natively and would technically accept Unicode
filenames, but the export-timestamp approach makes this irrelevant and avoids an entire class
of potential edge cases.

---

## 28. Reusable Components

### 28.1 No Changes Required

| Component | File | Reuse |
|---|---|---|
| `computeCompareLabels()` | `CompareLabelLogic.kt` | Date pair in caption |
| `SettingsCard` | `SettingsComponents.kt` | All option cards |
| `SameViewSegmentControl` | `SettingsComponents.kt` | Style and Quality selection |
| `SettingsSwitchRow` | `SettingsComponents.kt` | Title, Date, Location toggles |
| `SameViewAppBackground` (#0D1424) | `Color.kt` | Canvas background |
| `SameViewAppSurface` (#17202F) | `Color.kt` | Comparison border, SbS separator |
| `MetadataTextSanitizer` | existing | Not directly needed at render time |

### 28.2 Used as Architectural Templates (No Direct Code Reuse)

| Template | File | Used for |
|---|---|---|
| `VideoModePreview.kt` | ui/video | Live preview implementation |
| `TitleDateOverlayRenderer.kt` | video | Caption bitmap rendering |
| `MediaStoreVideoWriter.kt` | video | MediaStore write + IS_PENDING pattern |
| `CompareSliderRenderEngine.kt` | video | Slider 50/50 divider rendering (gradient zone + core line); handle visual taken from `CompareDivider` in `CompareScreen.kt` |

### 28.3 Minimal CompareScreen Changes

`CompareScreen.kt` requires:
- Replace `onCreateVideo` icon with a new Export icon
- Add Export `DropdownMenu` with two items
- New parameter: `onShareComparisonImage: (() -> Unit)?`
- New parameter: `isShareComparisonAvailable: Boolean = false`
- Remove `onCreateVideo` and `isCreateVideoAvailable` from direct icon; move them into the dropdown

No changes to compare mechanics, slider, or session state.

---

## 29. Testing Contract

### 29.1 Required Unit Tests

| # | Test |
|---|---|
| T-U-01 | `ShareRenderConfig` with Standard quality: canvas longest edge = 2048 px |
| T-U-02 | `ShareRenderConfig` with Original quality: canvas dimensions from session viewport |
| T-U-03 | Canvas width and height are always even numbers |
| T-U-04 | `computeCompareLabels()` Level 5: date line is null in `ShareCaptionData` |
| T-U-05 | `computeCompareLabels()` Level 1: date line is year pair e.g. "2008 → 2026" |
| T-U-06 | `ShareComparisonViewModel`: style toggle emits correct `ShareComparisonStyle` |
| T-U-07 | `ShareComparisonViewModel`: quality toggle emits correct `ShareQuality` |
| T-U-08 | `ShareComparisonViewModel`: title toggle false → `captionData.titleLine` is null |
| T-U-09 | `ShareComparisonViewModel`: location toggle true → `captionData.locationLine` present when available |
| T-U-10 | `ShareComparisonViewModel`: all toggles off + no content → `captionData` is null |
| T-U-11 | `ShareComparisonViewModel`: `isRendering` transitions true → false after render |

### 29.2 Required Instrumentation Tests

| # | Test |
|---|---|
| T-I-01 | End-to-end: Slider style, Standard quality, caption OFF → valid JPEG created in `Pictures/SameView` |
| T-I-02 | End-to-end: Side by side, Original quality, caption ON → valid JPEG, correct dimensions |
| T-I-03 | `CompareScreen` shows Export icon in top app bar when session has valid files |
| T-I-04 | `CompareScreen` Export dropdown shows "Share image" and "Share video" |
| T-I-05 | Tapping "Share image" navigates to `ShareComparisonScreen` |
| T-I-06 | Tapping "Share video" navigates to `CreateVideoScreen` (regression: existing behavior unchanged) |
| T-I-07 | Back from `ShareComparisonScreen` returns to `CompareScreen` with unchanged compare state |

### 29.3 Regression Guard

The implementation must not break:

- All existing `CompareScreenTest` tests (slider, fullscreen, delete, title, navigation, backup, favourites)
- All existing `CompareLibraryScreenTest` tests
- All existing `CameraViewModelTest` tests
- All existing `CreateVideoScreen` tests
- All existing `SessionBackupExporter` tests
- All previously green unit and instrumentation tests

---

## 30. Implementation Blocks

Each block delivers a stable, testable, fully functional state.

---

### Block 1 — Renderer Core

**Scope:**
- `ShareRenderConfig` data class, `ShareComparisonStyle` and `ShareQuality` enums
- `ShareCaptionData` data class
- `SliderRenderStrategy` — renders 50/50 Slider composition into a `Bitmap`
- `SideBySideStrategy` — renders Side by side composition into a `Bitmap`
- `CaptionRenderer` — renders caption text block onto a `Bitmap` canvas
- `ShareMediaStoreWriter` — MediaStore insert + IS_PENDING lifecycle + JPEG write
- `ShareImageRenderer` — orchestrates the above
- Unit tests T-U-01 through T-U-03

**Touches:** New files only. No existing code modified.

**Result:** Renderer produces valid JPEG bytes. Covered by unit tests. No UI, no ViewModel,
no navigation.

---

### Block 2 — CompareScreen TopAppBar Integration

**Scope:**
- `CompareScreen.kt`: remove dedicated Create Video icon; add Export icon with `DropdownMenu`;
  new parameters `onShareComparisonImage`, `isShareComparisonAvailable`
- `MainActivity.kt`: wire new parameters from `CameraViewModel` state
- `strings.xml` (EN + DE): `export_entry_content_description`,
  `export_menu_share_comparison_image`, `export_menu_create_video`
- All existing `CompareScreenTest` tests must remain green
- Instrumentation tests T-I-03, T-I-04, T-I-05, T-I-06

**Touches:** `CompareScreen.kt`, `MainActivity.kt`, `strings.xml`

**Result:** Export icon visible and functional. Both dropdown items navigate correctly.

---

### Block 3 — ShareComparisonScreen + ViewModel

**Scope:**
- `ShareComparisonViewModel` — metadata loading, toggle state, render trigger
- `ShareComparisonScreen` — full Configuring state UI (Style card with preview, Information
  card, Quality card, Share CTA)
- Navigation route `shareComparisonRoute` in `MainActivity`
- `strings.xml` (EN + DE): all new i18n keys for `ShareComparisonScreen`
- Unit tests T-U-04 through T-U-11
- Instrumentation tests T-I-01, T-I-02, T-I-07

**Touches:** New files + `MainActivity.kt` + `strings.xml` additions.

**Result:** Complete, fully functional share comparison flow. User can configure and share
a comparison image. All tests green.

---

## 31. Out of Scope

The following are explicitly excluded from V1 and must not be pre-implemented:

- GPS coordinates in exported image (forbidden by FD-06)
- App branding or watermark on the comparison area
- Share-from-Compare-Library entry point (CompareScreen only)
- Custom divider position for Slider style (always 50/50)
- Animated or GIF export
- Preview state after image creation (no "Image created" state)
- In-app deletion of previously created comparison images
- Re-share of previously created images from within the app
- PNG export format
- Additional export styles beyond Slider and Side by side
- Third-party platform integrations in export flow
- Caption font or color customization
- Metadata fields in the JPEG EXIF (only visible caption text; no hidden EXIF metadata beyond
  standard MediaStore fields)
- Date and time in the caption (date pair only, no time component)
- Session ID, app version, or any technical metadata in the caption

---

## 32. Relationships to Other Specifications

| Specification | Relationship |
|---|---|
| `COMPARE_SESSION_RENDERING_V1.md` | Defines `reference.jpg` and `capture.jpg` as renderer input. No modification to session files. |
| `COMPARE_FLOW_V1.md` | `CompareScreen` TopAppBar is modified. `computeCompareLabels()` reused. §5 out-of-scope items superseded by scope addendum in `CLAUDE_PROJECT_INSTRUCTION.md`. |
| `VIDEO_EXPORT_V1.md` | Caption logic (§31, §32), location priority logic, and MediaStore IS_PENDING pattern are directly reused. The Export dropdown replaces the standalone Create Video icon. |
| `SESSION_METADATA_V1.md` | Caption reads `content.title`, `reference.date`, `capture.timestampMs`, `location.*`. No new metadata fields. |
| `implementation_plans/historic/SESSION_METADATA_V4_IMPLEMENTATION_PLAN.md` | All required metadata fields (`content.title`, `reference.date`, `capture.timestampMs`, `location.*`) are available from the v4 schema. |
| `CLAUDE_PROJECT_INSTRUCTION.md` | Requires scope addendum (§4 of this document) before implementation. |
| `RESPONSIVE_LAYOUT_SYSTEM_V1.md` | `ShareComparisonScreen` is a form+preview screen; Expanded max-width 680 dp applies (Block 3 pattern). |
| `SESSION_BACKUP_EXPORT_V1.md` | Not affected. Backup remains in overflow menu (⋮). |
| `SETTINGS_UX_V1.md` | Not affected. No new Settings entries. |
| `CAMERA_WORKFLOW_UX_V1.md` | Not affected. This is a session post-processing feature. |
| `IMPLEMENTATION_NOTES.md` | Must be updated after each implementation block is verified. |
