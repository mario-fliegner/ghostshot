# VIDEO_EXPORT_V1.md

## 1. Document Status

This document is the **authoritative specification** for the video export feature in SameView.

It is written for:
- AI coding systems
- Implementation sessions
- Analysis sessions
- Regression-safe follow-up work

If an implementation proposal conflicts with this document, this document wins.

This specification covers the creation and export of a video file from an existing compare session. It does not cover video import, session import, or any other media management operation.

---

## 2. Feature Purpose

### What this feature IS

- A user-initiated export of a compare session as an MP4 video file
- Triggered from `CompareScreen` via a dedicated `Create Video` action in the top app bar
- Output: a standard MP4 file without an audio track, written to `Movies/SameView` via MediaStore
- Fully offline: no network calls; the app makes no uploads
- Two video modes in V1: **Compare Slider** and **Before & After**

### What this feature IS NOT

- Not a screen recording or UI capture
- Not a GIF or animated image export
- Not a slideshow or collage export
- Not a session import or backup feature
- Not a social media integration (no TikTok/Instagram/WhatsApp buttons)
- Not a video editing tool
- Not a second output file from the camera capture pipeline

The video export feature is a session post-processing tool. It operates on stored session files and does not interact with the camera capture pipeline.

---

## 3. Product Philosophy

SameView is a precision recreation camera. Its core value is deterministic, detail-accurate comparison. The video export feature must reflect this identity:

- The video shows exactly what the session contains — no reinterpretation, no reframe, no hidden crop
- Quality is not sacrificed for file size. Detail-rich motifs (architecture, landscapes, buildings) deserve high-fidelity output.
- No social platform dictates the export format inside the app. The Android Share Sheet is the platform selector.
- Videos are fully offline-native. The app makes no network calls.
- The post-render experience is unhurried: the user previews the result, then decides whether to share or discard.

---

## 4. Fixed Product Decisions

These decisions are final and must not be re-evaluated during implementation.

| # | Decision |
|---|---|
| FD-01 | Video is saved to `MediaStore.Video.Media`, `RELATIVE_PATH = Movies/SameView` |
| FD-02 | Renderer is fully UI-independent: no CompareScreen capture, no display size, no status bar, no Notch |
| FD-03 | Renderer input: `reference.jpg` and `capture.jpg` from the compare session; `metadata.json` optional |
| FD-04 | Two video modes in V1: **Compare Slider** and **Before & After** |
| FD-05 | No audio track. MP4 without any audio stream. No music, no sound effects, no empty audio track. |
| FD-06 | No platform picker in the app. No TikTok, Instagram, WhatsApp, or YouTube buttons. |
| FD-07 | Android Share Sheet opens only on explicit user tap on `[Share]`. Never opens automatically. |
| FD-08 | Frame rate: **30 FPS** for all modes, presets, quality levels, and export formats. No FPS option in wizard. |
| FD-09 | Session title and metadata are not included in the video in V1. V1 exclusion; superseded by §31 (Show Title and Date overlay) and §32 (Show Location overlay), both implemented in Block 8. |
| FD-10 | Videos are fully independent after export. No export history, no session-video link, no re-share from app. |
| FD-11 | No FileProvider required. MediaStore-URI is used directly for sharing. |
| FD-12 | No new Manifest permissions required for this feature. |
| FD-13 | Free areas in the video frame are filled with the app surface color `#17202F`. No blurred background in V1. |
| FD-14 | No crop. No automatic reframe. No content modification of session images. |
| FD-15 | Branding endcard is **enabled by default** (Default = ON). The last-used branding setting persists across video exports via DataStore. |
| FD-16 | Endcard content: "SameView" and "#MadeWithSameView". No "Created with SameView" wording. |

---

## 5. Entry Point and Availability

### 5.1 TopAppBar Structure

The `CompareScreen` top app bar is restructured as part of this feature's scope:

```
← Back  |  [Create Video icon]  |  [Delete Session icon]  |  ⋮
```

This is the product-intended structure documented in `SESSION_BACKUP_EXPORT_V1.md` §7.3 and `CLAUDE_PROJECT_INSTRUCTION.md` Addendum 2026-06-01. The restructuring must not be pre-implemented with placeholders or disabled icons — it happens in full as part of the Create Video implementation scope.

The overflow menu continues to contain:
- Edit Title
- Remove Title (visible only when a title is present)
- Backup Session

Delete Session remains a dedicated top app bar icon, unchanged.

### 5.2 Availability Rule

`Create Video` is available only for valid compare sessions where both `reference.jpg` and `capture.jpg` exist on the filesystem.

When the session's image files are missing, `Create Video` must not be offered as an executable action. The exact visual treatment of the unavailable state (disabled icon, hidden icon) is determined during implementation planning, but the product rule is: no executable video creation without valid session files.

When both files exist, tapping `Create Video` navigates to `CreateVideoScreen`.

### 5.3 Session Context Requirement

`Create Video` requires a valid `sessionId`. If `CompareScreen` is opened without session context, `Create Video` is not shown.

---

## 6. Video Modes V1

### 6.1 Compare Slider

The Compare Slider mode shows the core value of SameView: exact alignment between reference and capture. The divider automatically animates from one side to the other, revealing the reference on one side and the capture on the other — identical in principle to the interactive `CompareScreen` slider, but animated.

- Reference image revealed on the left side of the divider
- Capture image revealed on the right side of the divider
- Divider moves forward (left to right) then backward (right to left)
- No handle element on the divider (line only)

Full animation specification in Section 14.

### 6.2 Before & After

The Before & After mode shows both images completely and sequentially. The user sees the reference (Before) in full, then the capture (After) in full, with a crossfade transition between them.

- Both images shown at full scale (ContentScale.Fit — no crop)
- Reference (Before) shown first
- Capture (After) shown second
- Crossfade transition between the two

Full animation specification in Section 15.

---

## 7. Wizard UX

### 7.1 Screen Type

`CreateVideoScreen` is a separate fullscreen screen with its own Navigation Compose route (`createVideoRoute`). It is not a bottom sheet, not a dialog, and not a modal overlay on `CompareScreen`.

### 7.2 States

`CreateVideoScreen` has three distinct states managed by `CreateVideoViewModel`:

```
Configuring  →  [Create Video tap]  →  Rendering  →  [Encoding complete]  →  Preview
```

Back navigation from `Configuring`: closes `CreateVideoScreen`, returns to `CompareScreen`.
Back navigation from `Rendering`: confirmation dialog or direct cancel (implementation detail).
Back navigation from `Preview`: equivalent to `[Done]` — screen closes, video remains saved.

### 7.3 Configuring State Layout

```
TopAppBar:  ← Back   "Create Video"

[ Compare Slider ]  [ Before & After ]   ← Mode selection (segmented or card-based)
─────────────────────────────────────────  ← internal divider (same card)
[ Animated mode preview — 16:9, max 200 dp height ]

Format:
  [ Original ]  [ Portrait 9:16 ]  [ Landscape 16:9 ]

Duration:
  [ 4s ]  [ 6s ]  [ 8s ]

Quality:
  [ Standard ]  [ High Quality ]
  (note under High Quality: "Creates larger files and takes longer")

Extras:
  [ ] Show title and date        ← default OFF; local export state; see §31
      My grandparents · 2008 → 2026   ← dynamic preview of actual video content

  [ ] Show location              ← default OFF; local export state; see §32
      Munich, Germany            ← dynamic preview of city/country

  [ ] Add #MadeWithSameView card   ← Branding toggle (DataStore-persisted; see Section 13 for default)

[ Create Video ]   ← primary CTA, bottom
```

The Extras section groups all optional video additions (§30). The dynamic preview line below "Show title and date" is always visible regardless of toggle state and shows exactly the text that will appear in the exported video.

### 7.4 Mode Preview

The animated mode preview is a Compose-only, looping animation placed inside the Video Type settings card, directly below the mode segment control and separated by an internal divider.

**Purpose:** Visual selection aid only. It is not an exact rendering of the exported video.

**Technical implementation:**
- Pure Compose animation via `InfiniteTransition`. No `ExoPlayer`, no MP4, no export pipeline.
- Loads `reference.jpg` and `capture.jpg` from the compare session directory via Coil.
- Frame: 16:9 aspect ratio, `#17202F` background. Height = `min(containerWidth × 9/16, 200 dp)`. Horizontally centred; in portrait it fills the full card width.
- Mode switch: ~175 ms crossfade via `Crossfade`; new animation starts immediately.
- Decorative — excluded from the accessibility tree via `clearAndSetSemantics {}`.
- No label, no pause/tap interaction.

**Compare Slider animation (4 s loop):**
- 0–15 %: Hold Reference (slider = 0)
- 15–60 %: Sweep left → right with cubic smoothstep easing (slider 0 → 1)
- 60–100 %: Hold Capture (slider = 1) + brief pause, then restart

**Before & After animation (4 s loop):**
- 0–15 %: Hold Reference (alpha ref = 1, alpha cap = 0)
- 15–27.5 %: Crossfade 500 ms (linear)
- 27.5–100 %: Hold Capture (alpha ref = 0, alpha cap = 1) + brief pause, then restart

**Reduce Motion (Animator Duration Scale == 0):**
- No `InfiniteTransition`.
- Compare Slider: static 50 % split with visible divider line.
- Before & After: both images at 0.5 alpha overlaid.

### 7.4 Rendering State Layout

```
TopAppBar: "Creating video…"

CircularProgressIndicator
LinearProgressIndicator (frame-based, 0.0..1.0)
"Rendering frame X of Y"
```

No other user interaction during rendering except optional cancel.

### 7.5 Preview State Layout

```
TopAppBar:  ← Back   "Video Created"

[ Video player — auto-play, loop, muted ]

[ Share ]                  ← primary action
[ Done ]                   ← secondary action
[ Delete Video ]           ← destructive text action (confirmation dialog required)
```

`[Share]`: opens Android Share Sheet with MediaStore-URI.
`[Delete Video]`: shows a confirmation dialog before deleting. On confirmation: deletes video from MediaStore, returns to `Configuring` state.
`[Done]` / Back: video remains saved, `CreateVideoScreen` closes. Back behaves identically to Done.

### 7.6 Wizard State Persistence

Wizard configuration state (mode, format, duration, quality, branding toggle) may survive normal rotation within the active wizard session. It must not persist across app restarts.

---

## 8. Export Format

### 8.1 Options

Three export format options are offered in the Wizard:

| Option | Canvas Aspect Ratio | Description |
|---|---|---|
| **Original** *(Default)* | Session viewport ratio | Canvas dimensions derived from session `viewportWidth` / `viewportHeight` |
| **Portrait 9:16** | 9:16 | Standard portrait for Reels, Stories, Shorts |
| **Landscape 16:9** | 16:9 | Standard landscape for YouTube, desktop |

### 8.2 No-Crop Rule

The export format selection **does not crop** the session images. It does not reframe, pan, or zoom. The content of `reference.jpg` and `capture.jpg` is always fully visible.

When the session's native aspect ratio does not match the chosen canvas ratio, the images are scaled proportionally (ContentScale.Fit) to fit within the canvas. Free areas are filled with `#17202F`. This is padding, not crop.

**Example — Landscape session in Portrait 9:16 canvas:**
- Canvas: 1080 × 1920 px (at Standard quality)
- Session images are scaled to fit the 1080px width
- Resulting image height is less than 1920px
- Empty space at top and bottom: `#17202F`

**Example — Portrait session in Landscape 16:9 canvas:**
- Canvas: 1920 × 1080 px (at Standard quality)
- Session images are scaled to fit the 1080px height
- Resulting image width is less than 1920px
- Empty space at left and right: `#17202F`

### 8.3 Canvas Dimensions by Format and Quality

| Format | Standard 1080p | High Quality |
|---|---|---|
| Original | Longest edge = 1920px; aspect ratio from session | Longest edge = min(session max dimension, 3840px); aspect ratio from session |
| Portrait 9:16 | 1080 × 1920 px | 2160 × 3840 px (capped by device codec limit) |
| Landscape 16:9 | 1920 × 1080 px | 3840 × 2160 px (capped by device codec limit) |

For **Original** format, canvas dimensions are computed from the session's `viewportWidth` and `viewportHeight` stored in `metadata.json`. If `metadata.json` is unavailable or the viewport fields are missing, canvas dimensions are derived from the actual pixel dimensions of `capture.jpg`.

### 8.4 Always Even Dimensions

Both canvas width and height must be even numbers (required by most H.264/H.265 encoders). If a computed dimension is odd, it is rounded down to the nearest even number.

---

## 9. Duration Presets

Three presets, no free input:

| Label | Duration | Default |
|---|---|---|
| Short | 4 s | |
| Medium | 6 s | ✓ |
| Long | 8 s | |

Both video modes use the same presets. The animation timing is internally scaled to the total duration. The endcard (if branding enabled) is subtracted from the animation duration before scaling — see Sections 13, 14, and 15 for timing details.

---

## 10. Resolution and Quality

### 10.1 Standard 1080p

- Canvas dimensions: as defined in Section 8.3
- Video codec: H.264 / AVC
- Video bitrate: 6–8 Mbps
- Encoder: `MediaFormat.MIMETYPE_VIDEO_AVC`
- Typical file size at 6s: 5–10 MB

### 10.2 High Quality

- Canvas dimensions: as defined in Section 8.3 (up to 4K)
- Video codec: H.265 / HEVC if hardware encoder available; fallback to H.264 if not
- Video bitrate: 15–25 Mbps
- Encoder: `MediaFormat.MIMETYPE_VIDEO_HEVC` (preferred), `MediaFormat.MIMETYPE_VIDEO_AVC` (fallback)
- Typical file size at 6s: 15–30 MB
- Warning shown in Wizard: "Creates larger files and takes longer"

### 10.3 Device Codec Limit Handling

Before starting the encoder, the implementation checks whether the target resolution is supported by the available hardware encoder using `MediaCodecList` and `MediaCodecInfo.VideoCapabilities`.

If the target resolution exceeds device capabilities:
- Silent fallback to Standard 1080p dimensions
- User-visible informational Snackbar: string key `create_video_quality_fallback_notice`
- No crash, no silent quality degradation without notification

### 10.4 IS_PENDING Lifecycle

On API 29+, MediaStore entries are initially inserted with `IS_PENDING = 1`. The implementation must:
1. Insert the video entry with `IS_PENDING = 1` before encoding begins
2. Write encoded frames to the MediaStore-provided `FileDescriptor`
3. After encoding completes successfully, update the entry to `IS_PENDING = 0`

Only after `IS_PENDING = 0` is the video accessible to other apps (Share Sheet, Gallery). If encoding fails, the pending entry must be deleted from MediaStore via `contentResolver.delete(videoUri, null, null)`.

---

## 11. Frame Rate

V1 uses **30 FPS** for all configurations.

This applies to:
- All video modes (Compare Slider, Before & After)
- All duration presets (4s, 6s, 8s)
- All quality levels (Standard, High Quality)
- All export formats (Original, Portrait, Landscape)

No FPS option is offered in the Wizard. 24 FPS and 60 FPS are not V1 options.

At 30 FPS:
- 4s = 120 frames
- 6s = 180 frames
- 8s = 240 frames
- Endcard at 1.5s = 45 frames (if branding enabled)

---

## 12. Audio

The exported MP4 contains **no audio track**.

- No music
- No sound effects
- No empty/silent audio stream
- No `MediaFormat` audio track is added to the encoder

This is intentional. Users who want background music add it in TikTok, Instagram Reels, YouTube Shorts, or any video editor of their choice. Including an empty audio track would interfere with that workflow.

---

## 13. Branding

### 13.1 Endcard Format

When branding is enabled, a 1.5-second endcard (45 frames at 30 FPS) is appended after the main animation:

```
Background: #0D1424

        [SameView Logo]

        Made with ❤️

    #MadeWithSameView
```

- Background: `#0D1424` (deep dark, distinct from the `#17202F` app surface used for animation frames)
- **Logo**: SameView app icon (`ic_launcher_foreground`), centered horizontally, approximately 22 % of `min(canvasWidth, canvasHeight)`
- **"Made with ❤️"**: smaller supporting line, white, centered below the logo; ❤️ renders red via the system emoji font
- **"#MadeWithSameView"**: visually dominant, white, bold, centered below "Made with ❤️"; the primary focal point
- No "Created with SameView" wording
- Animation: 200 ms fade-in (6 frames) → 1.1 s static (33 frames) → 200 ms fade-out (6 frames)

### 13.2 Wizard Toggle

The Wizard offers a toggle: **"Add #MadeWithSameView card"**

When enabled: 1.5s endcard is appended. Animation duration = total duration − 1.5s.
When disabled: no endcard frames. Animation duration = total duration.

### 13.3 Branding Default

**Default = ON.**

Rationale:

- SameView is a new product and benefits from organic discovery through shared videos.
- The branding appears exclusively as a post-animation endcard — it does not overlay, watermark, or interfere with the actual comparison content.
- Users can disable branding at any time; the setting persists (see 13.5).
- The endcard uses the established community hashtag `#MadeWithSameView`.

### 13.4 Endcard Visual Design

The finalized endcard design (approved in Block 6):

```text
        [SameView Logo]

        Made with ❤️

    #MadeWithSameView
```

- **Background**: `#0D1424`
- **Logo**: `ic_launcher_foreground`, pre-scaled to approximately 22 % of `min(canvasWidth, canvasHeight)`, centered
- **"Made with ❤️"**: text size approximately 3.3 % of `min(canvasWidth, canvasHeight)`; white; ❤️ red via system emoji font; centered below logo with a gap of approximately 5 % of the base dimension
- **"#MadeWithSameView"**: text size approximately 6.5 % of `min(canvasWidth, canvasHeight)`, bold; white; centered below "Made with ❤️" with a gap of approximately 3 % of the base dimension; visually dominant
- **Vertical layout**: all elements centered as a group within the canvas
- **Fade**: alpha applied uniformly to all elements per frame (logo, both text lines rendered with the same alpha)

### 13.5 Branding Persistence

The branding toggle state persists across video exports using the existing app DataStore (`sameview_settings`).

Behavior:

- User disables branding → next export opens with branding OFF
- User enables branding → next export opens with branding ON
- First-ever export: branding ON (see 13.3)

The persisted value is a simple boolean preference. Its key follows the project's existing DataStore naming convention. No separate settings screen entry is needed — persistence is managed transparently through the Wizard toggle.

### 13.6 No Permanent Watermark

No branding element is ever rendered onto individual video frames during the animation. Branding is exclusively an appended endcard segment.

Note: The title and date overlay (§31) and the Show Location overlay (§32) are not branding elements. They are user-authored session context rendered during the initial Hold phase only. §13.6 applies exclusively to branding elements (`#MadeWithSameView`, the SameView logo, and related endcard content).

---

## 14. Animation Specification — Compare Slider

### 14.1 Timing Model

Let `T` = animation duration in seconds (= total video duration − endcard duration).

Endcard duration = 1.5s if branding enabled, 0s if disabled.

Single-pass reveal: the video tells "Before → After" and ends on the result.

| Phase | Start (% of T) | Duration (% of T) | Slider position |
|---|---|---|---|
| Hold Reference | 0% | 15% | 0.0 (fully reference) |
| Sweep | 15% | 45% | cubic smoothstep: 0.0 → 1.0 |
| Hold Capture | 60% | 40% | 1.0 (fully capture) |

**Worked example — 6s, branding OFF:**
T = 6.0s

| Phase | Start | Duration | End |
|---|---|---|---|
| Hold Reference | 0.00s | 0.90s | 0.90s |
| Sweep | 0.90s | 2.70s | 3.60s |
| Hold Capture | 3.60s | 2.40s | 6.00s |

**Worked example — 6s, branding ON:**
T = 4.5s (animation) + 1.5s (endcard) = 6.0s

| Phase | Start | Duration | End |
|---|---|---|---|
| Hold Reference | 0.00s | 0.675s | 0.675s |
| Sweep | 0.675s | 2.025s | 2.700s |
| Hold Capture | 2.700s | 1.800s | 4.500s |
| Endcard | 4.500s | 1.500s | 6.000s |

### 14.2 Easing Function

The ease-in-out function for slider movement is cubic smoothstep (Hermite interpolation):

```
f(t) = 3t² − 2t³
```

where `t` is the normalized progress within the sweep phase (0.0..1.0) and `f(t)` is the normalized slider position (0.0..1.0).

Properties: f(0) = 0, f(1) = 1, f′(0) = 0, f′(1) = 0. First and second derivatives are zero at both endpoints, producing a smooth and symmetric ease-in/ease-out acceleration profile.

### 14.3 Frame Computation

For frame index `i` (0-based, total frames = total duration in seconds × 30):
1. Compute normalized position within animation: `t = i / animationFrameCount`
2. Determine phase: Hold Reference (t < 0.15), Sweep (0.15 ≤ t < 0.60), Hold Capture (t ≥ 0.60)
3. Compute slider position `p ∈ [0.0, 1.0]` using §14.2
4. Render frame at slider position `p` (see Section 17)

### 14.4 Slider Position Semantics

- `p = 0.0`: reference image fully visible, capture image fully hidden
- `p = 1.0`: capture image fully visible, reference image fully hidden
- `p = 0.5`: equal split (same as initial state in interactive CompareScreen)

---

## 15. Animation Specification — Before & After

### 15.1 Timing Model

Let `T` = animation duration in seconds (= total video duration − endcard duration).
Crossfade duration `T_cf` = 0.5s (absolute, not proportional to T).
Hold duration per image = `(T − T_cf) / 2`

| Phase | Start | Duration | Alpha (reference) | Alpha (capture) |
|---|---|---|---|---|
| Hold Before | 0 | `(T − 0.5) / 2` | 1.0 | 0.0 |
| Crossfade | `(T − 0.5) / 2` | 0.5s | 1.0 → 0.0 (linear) | 0.0 → 1.0 (linear) |
| Hold After | `(T − 0.5) / 2 + 0.5` | `(T − 0.5) / 2` | 0.0 | 1.0 |

**Worked example — 6s, branding OFF:**
T = 6.0s, Hold = (6.0 − 0.5) / 2 = 2.75s

| Phase | Start | Duration | End |
|---|---|---|---|
| Hold Before | 0.00s | 2.75s | 2.75s |
| Crossfade | 2.75s | 0.50s | 3.25s |
| Hold After | 3.25s | 2.75s | 6.00s |

**Worked example — 6s, branding ON:**
T = 4.5s, Hold = (4.5 − 0.5) / 2 = 2.00s

| Phase | Start | Duration | End |
|---|---|---|---|
| Hold Before | 0.00s | 2.00s | 2.00s |
| Crossfade | 2.00s | 0.50s | 2.50s |
| Hold After | 2.50s | 2.00s | 4.50s |
| Endcard | 4.50s | 1.50s | 6.00s |

**All preset timings with branding ON/OFF:**

| Preset | T_anim | Hold | Crossfade | Hold | Endcard |
|---|---|---|---|---|---|
| 4s, branding OFF | 4.0s | 1.75s | 0.5s | 1.75s | — |
| 4s, branding ON | 2.5s | 1.00s | 0.5s | 1.00s | 1.5s |
| 6s, branding OFF | 6.0s | 2.75s | 0.5s | 2.75s | — |
| 6s, branding ON | 4.5s | 2.00s | 0.5s | 2.00s | 1.5s |
| 8s, branding OFF | 8.0s | 3.75s | 0.5s | 3.75s | — |
| 8s, branding ON | 6.5s | 3.00s | 0.5s | 3.00s | 1.5s |

### 15.2 Crossfade Computation

During the crossfade phase, progress `cf ∈ [0.0, 1.0]` is linear:

```
alpha_reference = 1.0 - cf
alpha_capture   = cf
```

Frame rendering: draw `#17202F` background, draw reference at `alpha_reference`, draw capture at `alpha_capture`.

### 15.3 ContentScale

Both images use **Fit** semantics (preserve aspect ratio, fully visible, no crop). This is the primary semantic difference from the Compare Slider mode, which uses Fill semantics for both images.

---

## 16. Divider Line Specification

This section applies exclusively to the Compare Slider mode.

### 16.1 No Handle

The divider line has **no handle element** (no circle, no grip, no UI widget). The line only.

Rationale: The handle communicates "you can drag" to an interactive user. In a video, this affordance is false and visually distracting.

### 16.2 Divider Rendering

The divider is rendered as a gradient soft-transition zone with a 1 px white core line.

**Gradient soft-transition zone**

A `LinearGradient` alpha mask is applied to the capture layer via `PorterDuff.Mode.DST_IN`, creating a soft-feathered right edge on the capture image centered on the divider position. The reference image is visible beneath wherever the capture alpha fades.

| Parameter | Value |
|---|---|
| Half-width base | 12 px at 1080p canvas height |
| Half-width formula | `(12 × canvasHeight / 1080).coerceAtLeast(4)` |
| Gradient direction | left (capture opaque) → right (capture transparent) |
| Gradient extent | `[sliderX − halfWidth, sliderX + halfWidth]`, clamped to canvas bounds |

The gradient zone scales proportionally with canvas height, producing consistent visual weight at all export resolutions (1080p, 4K Portrait, 4K Landscape).

**Core line**

A 1 px white stroke is drawn at the exact divider position (`sliderX`) for the full canvas height. This line serves as an orientation anchor in subtle comparisons where image differences are small. It is intentionally thin and fixed at 1 px regardless of resolution — its purpose is perceptual anchoring, not visual weight.

**Implementation**

Rendering sequence per sweep frame:
1. Fill canvas with `#17202F`
2. Draw reference bitmap full canvas (fill semantics, base layer)
3. `canvas.saveLayer(RectF(0, 0, sliderX + halfWidth, canvasHeight), null)`
4. Draw capture bitmap full canvas (fill semantics, within layer)
5. Apply `DST_IN` mask: `LinearGradient` from `Color.BLACK` to `Color.TRANSPARENT` over `[gradientLeft, gradientRight]`
6. `canvas.restore()` (composites masked capture over reference)
7. Draw 1 px white line at `sliderX`

During Hold Reference (sliderPos = 0.0) and Hold Capture (sliderPos = 1.0), no layer compositing occurs — only the respective bitmap is drawn.

The implementation must not use `Paint.setShadowLayer()` — this requires `LAYER_TYPE_SOFTWARE` and degrades per-frame Bitmap rendering performance.

---

## 17. Canvas Rendering Rules

These rules apply to both video modes for all frames.

### 17.1 Canvas Setup

Each frame is rendered into a reusable `Bitmap` of canvas dimensions (see Section 8.3). The bitmap is created once before the frame loop and reused to avoid per-frame allocation.

### 17.2 Session Image Preparation

Before the frame loop:
1. Decode `reference.jpg` and `capture.jpg` from the session directory
2. Scale each to fit the canvas (ContentScale.Fit for Before & After; fill to canvas dimensions for Compare Slider where both images must cover the full canvas independently)
3. Store the scaled bitmaps in memory for the duration of the render

For Compare Slider, each image must individually fill the entire canvas (i.e., the largest dimension of the image fills the corresponding canvas dimension). This ensures the split at any slider position shows a full-coverage image on each side.

For Before & After, each image is fit within the canvas (ContentScale.Fit), centered, with `#17202F` padding applied to free areas.

### 17.3 Background Color

Every animation frame begins with a full-canvas fill of `#17202F`. This establishes:
- Free area color for format padding (Section 8.2)
- Background behind Fit-scaled Before & After images

Endcard frames use `#0D1424` instead (see Section 17.6).

### 17.4 Compare Slider Frame Rendering

For slider position `p ∈ [0.0, 1.0]`:

1. Fill canvas with `#17202F`
2. Draw reference bitmap full canvas (fill semantics, base layer)
3. Composite capture over reference using gradient soft-transition (see Section 16.2 for full rendering sequence)
4. Draw 1 px white core line at `p × canvasWidth` when `0 < p < 1`

### 17.5 Before & After Frame Rendering

For alphas `alpha_reference` and `alpha_capture`:

1. Fill canvas with `#17202F`
2. Draw reference bitmap at `alpha_reference` opacity (centered, Fit-scaled)
3. Draw capture bitmap at `alpha_capture` opacity (centered, Fit-scaled)

### 17.6 Endcard Frame Rendering (if branding enabled)

Total: 45 frames (1.5 s at 30 FPS).

Per-frame alpha based on endcard frame index `e` (0-based):

- Frames 0–5 (200 ms fade-in): `alpha = e / 5`
- Frames 6–38 (1.1 s static): `alpha = 1.0`
- Frames 39–44 (200 ms fade-out): `alpha = 1.0 − (e − 38) / 6`

For each endcard frame:

1. Fill canvas with `#0D1424`
2. Draw SameView logo (`ic_launcher_foreground`) centered, pre-scaled, at computed alpha
3. Draw "Made with ❤️" centered below logo at computed alpha (❤️ renders red via system emoji font)
4. Draw "#MadeWithSameView" centered below "Made with ❤️", bold, at computed alpha

### 17.7 Memory Management

- Session bitmaps decoded once, held in memory for the render duration, recycled after render completes
- Frame bitmap reused across all frames
- No `Bitmap.createBitmap()` calls inside the frame loop
- All bitmaps are recycled in a `try/finally` block

---

## 18. Storage and MediaStore Contract

### 18.1 MediaStore Insertion

Video is written to `MediaStore.Video.Media` with:

```
RELATIVE_PATH = Movies/SameView
DISPLAY_NAME  = SameView_<sessionId>_<mode>.mp4
MIME_TYPE     = video/mp4
IS_PENDING    = 1
```

Where `<mode>` is `compare_slider` or `before_after`.

### 18.2 IS_PENDING Lifecycle

1. Insert entry with `IS_PENDING = 1` before encoding starts
2. Open `FileDescriptor` from the inserted URI via `contentResolver.openFileDescriptor(uri, "w")`
3. Pass `FileDescriptor` to `MediaMuxer` for writing
4. After encoding completes successfully: update `IS_PENDING = 0`
5. On encoding failure: `contentResolver.delete(uri, null, null)` (best-effort)

Only after `IS_PENDING = 0` is the video visible in the Gallery and accessible to the Share Sheet.

### 18.3 No FileProvider Required

The video resides in `Movies/SameView` under the system MediaStore. Its URI is a `content://` URI issued by `com.android.providers.media.MediaProvider`. This URI is directly usable in `Intent.ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION`.

No `FileProvider` configuration is required for this feature. `RELEASE_HARDENING_AUDIT_V1.md` Block D (Finding S-02) does not block this feature. Block D remains open for a future "share session ZIP via Android Share Sheet" feature only.

### 18.4 Video Deletion

The video is owned by the app (inserted by the app). Deletion uses:
```kotlin
contentResolver.delete(videoUri, null, null)
```
No additional permissions are required on API 29+. Failure is reported via Snackbar; the app remains fully usable.

---

## 19. Post-Render UX

### 19.1 Full Flow

```
CreateVideoScreen (Configuring)
    User taps [Create Video]
    ↓
CreateVideoScreen (Rendering)
    Progress indicator visible
    ↓ encoding complete
CreateVideoScreen (Preview)
    Video plays automatically (loop, muted)
    ↓ User taps [Share]
    Android Share Sheet opens
    ↓ User taps [Delete]
    Video deleted from MediaStore → returns to Configuring state
    ↓ User taps [Done] or Back
    Video remains saved → CreateVideoScreen closes → returns to CompareScreen
```

### 19.2 Share Intent

```kotlin
Intent(Intent.ACTION_SEND).apply {
    type = "video/mp4"
    putExtra(Intent.EXTRA_STREAM, videoMediaStoreUri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
```

The Share Sheet opens only on explicit `[Share]` tap. Canceling the Share Sheet is not an error condition; the video remains saved and the app state is unchanged.

### 19.3 Delete from Preview

`[Delete Video]` shows a confirmation dialog before deleting. On confirmation: deletes the video from MediaStore. On success: transitions to `Configuring` state. On failure: shows error Snackbar (`create_video_delete_failed`); video state and Preview state remain unchanged.

### 19.4 Done / Back from Preview

Both `[Done]` and the Back gesture from the Preview state close `CreateVideoScreen` and return to `CompareScreen`. The video is not deleted.

---

## 20. Renderer Architecture

### 20.1 Overview

```
VideoExportPipeline
├── VideoRenderConfig        (data class; all render parameters)
├── VideoFrameRenderer       (interface; renders a single frame)
│   ├── CompareSliderRenderEngine   (VideoFrameRenderer implementation)
│   └── BeforeAfterRenderEngine     (VideoFrameRenderer implementation)
├── BrandingEndcardRenderer  (standalone; renders endcard frames)
├── VideoEncoder             (MediaCodec wrapper; accepts Bitmaps)
└── MediaStoreVideoWriter    (MediaStore insertion, IS_PENDING lifecycle)
```

`VideoExportPipeline` orchestrates all components. It is the single entry point for the `CreateVideoViewModel`.

### 20.2 VideoRenderConfig

```kotlin
data class VideoRenderConfig(
    val mode: VideoMode,
    val exportFormat: VideoExportFormat,
    val durationMs: Int,                    // 4000 | 6000 | 8000
    val quality: VideoQuality,
    val brandingEnabled: Boolean,
    val frameRateFps: Int = 30,             // fixed in V1
    val sessionId: String,
    val sessionDir: File
)

enum class VideoMode { COMPARE_SLIDER, BEFORE_AND_AFTER }
enum class VideoExportFormat { ORIGINAL, PORTRAIT_9_16, LANDSCAPE_16_9 }
enum class VideoQuality { STANDARD_1080P, HIGH_QUALITY }
```

### 20.3 VideoFrameRenderer Interface

```kotlin
interface VideoFrameRenderer {
    /** Total animation frames (excluding endcard frames). */
    fun animationFrameCount(config: VideoRenderConfig): Int

    /**
     * Renders animation frame [frameIndex] into [target].
     * [target] is reused across frames — do not store a reference.
     * [reference] and [capture] are pre-scaled for the canvas.
     */
    fun renderFrame(
        frameIndex: Int,
        config: VideoRenderConfig,
        reference: Bitmap,
        capture: Bitmap,
        target: Bitmap
    )
}
```

### 20.4 Extensibility

V1 has exactly two `VideoFrameRenderer` implementations. Adding a V2 mode requires:
1. A new enum value in `VideoMode`
2. A new `VideoFrameRenderer` implementation class
3. A `when` branch in `VideoExportPipeline`

No registry system, no ServiceLoader, no DI module for renderers. The `when` branch is the extension point.

### 20.5 Pipeline Threading

- All render and encoding operations on `Dispatchers.Default` (CPU-bound)
- MediaStore writes on `Dispatchers.IO`
- Progress updates delivered via `StateFlow<Float>` (0.0..1.0) from `VideoExportPipeline` to `CreateVideoViewModel`
- `CreateVideoViewModel` holds a `Job?` reference for cancellation support

---

## 21. Error Handling

### 21.1 Missing Session Files at Wizard Entry

If `reference.jpg` or `capture.jpg` is missing when the user attempts to open the Wizard (taps `Create Video`), the action must not proceed. The `Create Video` icon must not be offered as an executable action when session files are missing (see Section 5.2).

### 21.2 Image Decode Failure

If a session image exists on disk but cannot be decoded (corrupt file), the error is detected during render preparation in `VideoExportPipeline`. Behavior:
- Render does not start
- Transition to `Configuring` state
- Error Snackbar: `create_video_error_render_failed`

### 21.3 Encoding Failure

If encoding fails mid-render:
- Cancel in-progress `MediaCodec` session
- Attempt `contentResolver.delete(videoUri, null, null)` to remove the incomplete entry (best-effort; failure is acceptable)
- Transition to `Configuring` state
- Error Snackbar: `create_video_error_render_failed`

### 21.4 Device Codec Limit

If the target resolution is unsupported by the hardware encoder:
- Silent fallback to Standard 1080p dimensions
- Informational Snackbar: `create_video_quality_fallback_notice`
- Encoding proceeds at 1080p

### 21.5 MediaStore Insertion Failure

If `contentResolver.insert()` returns null or throws:
- Encoding does not start
- Transition to `Configuring` state
- Error Snackbar: `create_video_error_render_failed`

### 21.6 Video Delete Failure

If `contentResolver.delete()` fails during Preview `[Delete]`:
- Snackbar: `create_video_delete_failed`
- Preview state remains unchanged
- App remains fully usable

### 21.7 No Silent Failure

Every error that prevents video creation or video deletion must produce a user-visible Snackbar. Silent failure is not permitted.

---

## 22. Permissions

This feature requires **no new Manifest permissions**.

| Operation | Permission Required |
|---|---|
| Write MP4 to `Movies/SameView` | None. App-owned MediaStore entry on API 29+. |
| Delete own MP4 from MediaStore | None. App is owner of the entry. |
| Share MP4 via Share Sheet | None. `FLAG_GRANT_READ_URI_PERMISSION` + MediaStore-URI. |
| Read session files from `filesDir` | None. Internal app storage. |

`READ_MEDIA_VIDEO`, `WRITE_EXTERNAL_STORAGE`, and `READ_EXTERNAL_STORAGE` must not be added.

---

## 23. Privacy and Play Store

### 23.1 No Network Calls

The app makes no network calls during video export. The `INTERNET` permission is not declared and is not used.

### 23.2 No GPS in Video

MP4 files produced by this feature contain no GPS metadata. `MediaFormat.KEY_LOCATION` is not set. Session images may contain GPS EXIF, but the rendered video frames carry no location data. This is intentional.

### 23.3 Data Safety Form

No new Play Store Data Safety entry is required. Video export is:
- User-initiated
- Fully local
- No GPS in output
- Sharing is user-controlled via standard Android Share Sheet

Existing declarations (Camera, optional precise location, no sharing, no tracking) fully cover this feature.

### 23.4 Play Store Compliance

No new compliance requirements are introduced by this feature.

---

## 24. i18n Contract

All user-facing text must use string resources. No hardcoded visible strings.

### 24.1 Required String Resource Keys

**Wizard — Configuring State:**

| Key | Usage |
|---|---|
| `create_video_screen_title` | Top app bar title: "Create Video" |
| `create_video_mode_compare_slider` | Mode option: "Compare Slider" |
| `create_video_mode_before_after` | Mode option: "Before & After" |
| `create_video_format_label` | Section label: "Format" |
| `create_video_format_original` | Format option: "Original" |
| `create_video_format_portrait` | Format option: "Portrait 9:16" |
| `create_video_format_landscape` | Format option: "Landscape 16:9" |
| `create_video_duration_label` | Section label: "Duration" |
| `create_video_duration_short` | Duration option: "4s" |
| `create_video_duration_medium` | Duration option: "6s" |
| `create_video_duration_long` | Duration option: "8s" |
| `create_video_quality_label` | Section label: "Quality" |
| `create_video_quality_standard` | Quality option: "Standard" |
| `create_video_quality_high` | Quality option: "High Quality" |
| `create_video_quality_high_note` | Quality sub-label: "Creates larger files and takes longer" |
| `create_video_branding_label` | Toggle label: "Add #MadeWithSameView card" |
| `create_video_action_create` | Primary CTA button: "Create Video" |

**Wizard — Rendering State:**

| Key | Usage |
|---|---|
| `create_video_rendering_title` | Top app bar title: "Creating video…" |
| `create_video_rendering_progress` | Progress label: "Rendering frame %1$d of %2$d" |

**Wizard — Preview State:**

| Key | Usage |
|---|---|
| `create_video_preview_title` | Top app bar title: "Video Created" |
| `create_video_action_share` | Action button: "Share" |
| `create_video_action_delete` | Action button: "Delete" |
| `create_video_action_done` | Action button: "Done" |

**Top App Bar — CompareScreen:**

| Key | Usage |
|---|---|
| `create_video_entry_content_description` | Icon button content description: "Create video" |

**Error / Info Snackbars:**

| Key | Usage |
|---|---|
| `create_video_error_render_failed` | Snackbar: "Could not create video" |
| `create_video_quality_fallback_notice` | Snackbar: "High quality not supported on this device" |
| `create_video_delete_failed` | Snackbar: "Could not delete video" |

**Filename (not translatable):**

| Key | Usage |
|---|---|
| `create_video_filename` | MediaStore display name: `"SameView_%1$s_%2$s.mp4"` (sessionId, mode) |

Key names follow the project's existing naming convention. No second naming system is introduced.

---

## 25. Testing Contract

### 25.1 Required Unit Tests

| # | Test |
|---|---|
| T-U-01 | `CompareSliderRenderEngine.animationFrameCount` returns correct count for all 3 presets × branding ON/OFF |
| T-U-02 | Frame 0 (Compare Slider): slider position is 0.0 (fully reference-side) |
| T-U-03 | First frame of Hold Capture phase (Compare Slider, t = 0.60): slider position is 1.0 |
| T-U-04 | Last animation frame (Compare Slider): slider position is 1.0 (Hold Capture; no reversal) |
| T-U-05 | `BeforeAfterRenderEngine.animationFrameCount` returns correct count for all 3 presets × branding ON/OFF |
| T-U-06 | Frame 0 (Before & After): alpha_reference = 1.0, alpha_capture = 0.0 |
| T-U-07 | Frame in crossfade midpoint (Before & After): alpha_reference ≈ 0.5, alpha_capture ≈ 0.5 |
| T-U-08 | Last animation frame (Before & After): alpha_reference = 0.0, alpha_capture = 1.0 |
| T-U-09 | Branding ON: total frame count = animation frames + 30 |
| T-U-10 | Branding OFF: total frame count = animation frames |
| T-U-11 | `VideoRenderConfig` with High Quality + Original format: canvas dimensions derived from session viewport |
| T-U-12 | `VideoRenderConfig` with Standard + Portrait 9:16: canvas = 1080 × 1920 |
| T-U-13 | `VideoRenderConfig` with Standard + Landscape 16:9: canvas = 1920 × 1080 |
| T-U-14 | Canvas width and height are always even numbers |
| T-U-15 | `CreateVideoViewModel`: `CreateVideoState` transitions from `Configuring` to `Rendering` on create tap |
| T-U-16 | `CreateVideoViewModel`: transitions to `Preview` with MediaStore URI after successful encode |
| T-U-17 | `CreateVideoViewModel`: transitions to `Configuring` with error Snackbar on encode failure |
| T-U-18 | `CreateVideoViewModel`: delete from Preview transitions to `Configuring` on success |
| T-U-19 | `CreateVideoViewModel`: delete failure emits `create_video_delete_failed` Snackbar; Preview state unchanged |
| T-U-20 | `CreateVideoViewModel`: quality fallback emits `create_video_quality_fallback_notice` Snackbar |

### 25.2 Required Instrumentation Tests

| # | Test |
|---|---|
| T-I-01 | End-to-end: Compare Slider, 4s, Standard, Original format, branding OFF → valid MP4 created in `Movies/SameView` |
| T-I-02 | End-to-end: Before & After, 6s, Standard, Portrait 9:16, branding ON → valid MP4, duration ≈ 6s |
| T-I-03 | High Quality: output video has expected resolution (within codec-reported max) |
| T-I-04 | Delete from Preview: `contentResolver.query` confirms entry no longer exists in MediaStore |
| T-I-05 | `CompareScreen` shows `Create Video` icon in top app bar when session has valid files |
| T-I-06 | `CompareScreen` `Create Video` icon is not executable when session files are missing |
| T-I-07 | Tapping `Create Video` navigates to `CreateVideoScreen` |
| T-I-08 | Back from `CreateVideoScreen` returns to `CompareScreen` with unchanged state |

### 25.3 Regression Guard

The implementation must not break:
- All existing `CompareScreenTest` tests (slider, fullscreen, delete, title, navigation, backup)
- All existing `CompareLibraryScreenTest` tests
- All existing `CameraViewModelTest` tests
- All existing `SessionBackupExporterTest` tests
- All existing snackbar replay protection tests
- All previously green unit and instrumentation tests

---

## 26. Implementation Discipline

The following are **strictly forbidden** in any implementation block for this feature:

- Dummy Toast ("Video creation coming soon")
- Disabled `Create Video` icon as a pre-implementation placeholder
- Empty `CreateVideoScreen` route that does nothing
- Partially functional Wizard state without completing the full state machine
- `Create Video` icon that navigates to a screen that shows only a loading indicator permanently
- Half-rendered video output that is not a valid MP4
- Any UI element that is visible to the user but not fully functional

Every implementation block must deliver a stable, testable, fully functional outcome. If a block ends before Preview is implemented, the feature must not be reachable from the UI at all (no entry point) until Block 4 is complete.

---

## 27. Relationships to Other Specifications

| Specification | Relationship |
|---|---|
| `COMPARE_SESSION_RENDERING_V1.md` | Defines `reference.jpg` and `capture.jpg` as the authoritative, deterministically rendered session files. Video export uses these files as input without modification. |
| `COMPARE_FLOW_V1.md` | Defines `CompareScreen`. This spec adds the `Create Video` icon to the top app bar and introduces `CreateVideoScreen` as a new navigation destination. No change to compare mechanics, slider, or session state. `CompareLabelLogic.computeCompareLabels()` defined in §41.4 is reused by §31 for the date pair overlay. |
| `SESSION_BACKUP_EXPORT_V1.md` | Defines the planned future top app bar structure (`← Back | [Create Video] | [Delete Session] | ⋮`). This structure is implemented as part of this spec's scope. Overflow menu entries (Edit Title, Remove Title, Backup Session) are unchanged. |
| `SESSION_METADATA_V4_IMPLEMENTATION_PLAN.md` | Defines the v4 metadata schema and all write functions. §31 reads `content.title`, `reference.date`, and `capture.timestampMs` from `metadata.json` at video creation time. §32 reads `location.city` and `location.country`. No new metadata fields are introduced by this spec. |
| `SESSION_METADATA_EDITOR_V1.md` | Users edit `content.title` and `reference.date` via Edit Session (reflected in "Show title and date" preview in `CreateVideoScreen`). Users edit `location.city` and `location.country` via Edit Session (reflected in "Show location" preview). |
| `CLAUDE_PROJECT_INSTRUCTION.md` | The "Storage: MediaStore ONLY" and "No video support" constraints in the original V1 instruction refer to the camera capture pipeline and camera preview. Video export is a session post-processing feature that explicitly extends the product scope per the 2026-06-01 addendum. "Share flow" in OUT OF SCOPE refers to social sharing as a primary feature, not to the optional Share Sheet access from the video preview. |
| `RELEASE_HARDENING_AUDIT_V1.md` | Finding S-02 (FileProvider) is NOT a prerequisite for this feature. MediaStore-URI sharing does not require FileProvider. Block D remains open for future ZIP-sharing only. |
| `CAMERA_WORKFLOW_UX_V1.md` | Not affected. Video export is not a camera workflow feature. |
| `SETTINGS_UX_V1.md` | Not affected. Video export has no settings-screen entries. |
| `APP_NAMING_DECISION.md` | The community hashtag `#MadeWithSameView` established here informs the branding endcard content (Section 13). |

---

## 28. Out of Scope

The following are explicitly excluded from V1 and must not be pre-implemented:

- Audio track of any kind
- Session title and location in video without overlay — V1 exclusion; superseded by §31 (Show title and date) and §32 (Show location), both implemented in Block 8
- Blurred background padding (V2 candidate)
- Ken-Burns / pan / zoom effects
- Auto-crop to social media format
- Export history or session-video linking
- Re-share or re-delete of previously created videos from within the app
- GIF export
- Session import
- Cloud sync
- Platform-specific share integrations (TikTok, Instagram, WhatsApp, YouTube)
- Video trimming
- Frame rate options (24 FPS, 60 FPS)
- Bitrate controls
- Custom aspect ratios beyond the three defined options
- Multiple simultaneous video exports
- Background video export (app must remain in foreground during rendering)
- Video export from Compare Library (entry point is CompareScreen only)

---

## 29. Implementation Blocks

Each block delivers a stable, testable, fully functional state. No dummy UI, no placeholders.

---

### Block 1 — Renderer Core

**Scope:**
- `VideoRenderConfig` data class, `VideoMode`, `VideoExportFormat`, `VideoQuality` enums
- `VideoFrameRenderer` interface
- `CompareSliderRenderEngine` — full implementation with correct timing (Section 14), easing, frame computation
- `BeforeAfterRenderEngine` — full implementation with correct timing (Section 15), crossfade
- Canvas rendering logic (Section 17) in both engines
- Divider line rendering (Section 16) in `CompareSliderRenderEngine`
- Unit tests T-U-01 through T-U-14

**Touches:** New files only. No existing code modified.
**Result:** Both renderers are unit-tested, timing-correct, and produce accurate Bitmap output. No video file, no encoder, no UI.

---

### Block 2 — VideoEncoder + MediaStoreVideoWriter

**Scope:**
- `VideoEncoder` — MediaCodec wrapper; accepts Bitmap frames at 30 FPS; writes encoded video
- `MediaStoreVideoWriter` — handles MediaStore insertion, `IS_PENDING` lifecycle, deletion on failure
- `VideoExportPipeline` — orchestrates Renderer + `BrandingEndcardRenderer` + Encoder + Writer; exposes `ProgressCallback`
- `BrandingEndcardRenderer` — renders static endcard frames (30 frames) when branding enabled
- Instrumentation test T-I-01: valid MP4 created in `Movies/SameView`

**Touches:** New files only. No existing code modified.
**Result:** The complete render pipeline produces a valid, playable MP4 in `Movies/SameView`. Verified by instrumentation test.

---

### Block 3 — CreateVideoScreen + ViewModel (Configuring → Rendering)

**Scope:**
- `CreateVideoViewModel` — full state machine (`Configuring`, `Rendering`, `Preview`), progress `StateFlow`, error events
- `CreateVideoScreen` Composable — fully functional Configuring state (mode selection, format selection, duration presets, quality selection, branding toggle, Create Video button); functional Rendering state (progress display)
- Navigation route `createVideoRoute` in `AppNavGraph` / `MainActivity`
- `CompareScreen` top app bar restructured to `← Back | [Create Video] | [Delete Session] | ⋮`
- Availability check: `Create Video` icon state based on session file existence
- `strings.xml` — all new i18n keys for Configuring and Rendering states
- Unit tests T-U-15, T-U-16, T-U-17
- Instrumentation tests T-I-05, T-I-06, T-I-07, T-I-08
- All existing `CompareScreenTest` tests must remain green

**Touches:** `CompareScreen.kt`, `MainActivity.kt` / `AppNavGraph`, `strings.xml` (additions), new files.
**Result:** User can open the Wizard from CompareScreen, configure a video, and create it. The MP4 is saved to `Movies/SameView`. Preview state is not yet implemented — but the entry point in CompareScreen is fully functional (not a placeholder).

---

### Block 4 — Preview State + Share + Delete

**Scope:**
- Preview state in `CreateVideoScreen` — Media3 / ExoPlayer-based video playback (auto-play, loop, muted)
- `[Share]` — `Intent.ACTION_SEND` with MediaStore-URI and `FLAG_GRANT_READ_URI_PERMISSION`
- `[Delete]` — `contentResolver.delete`, returns to Configuring state, Snackbar on failure
- `[Done]` / Back — closes screen, video remains saved
- `strings.xml` — remaining i18n keys (Preview state labels, error strings)
- Unit tests T-U-18, T-U-19
- Instrumentation tests T-I-02, T-I-04

**Touches:** `CreateVideoScreen.kt`, `CreateVideoViewModel.kt`, `strings.xml` (additions).
**Result:** Complete post-render UX. User can preview, share, delete, or keep the video.

---

### Block 5 — High Quality + Device Limit Fallback

**Scope:**
- High Quality option wired through `VideoRenderConfig` and `VideoEncoder`
- HEVC encoder availability check via `MediaCodecList`
- Resolution limit check + fallback to Standard 1080p
- `create_video_quality_fallback_notice` Snackbar
- Unit test T-U-20
- Instrumentation test T-I-03

**Touches:** `VideoEncoder.kt`, `VideoRenderConfig.kt`, `CreateVideoViewModel.kt`.
**Result:** Both quality tiers are fully functional. Device limits are handled gracefully.

---

### Block 6 — Branding Endcard

**Scope:**
- Branding toggle wired through to `VideoRenderConfig`
- `BrandingEndcardRenderer` active when `brandingEnabled = true`
- Correct timing: animation duration = total − 1.0s when branding enabled
- Unit tests T-U-09, T-U-10
- Instrumentation test T-I-02 (covers branding ON scenario)

**Touches:** `BrandingEndcardRenderer.kt`, `VideoExportPipeline.kt`, `CreateVideoScreen.kt` (toggle wiring).
**Result:** Branding endcard fully implemented and toggleable.

---

### Block 7 — Final Verification

**Scope:**
- Full `testDebugUnitTest` suite green
- Full `connectedDebugAndroidTest` suite green
- Manual device smoke test: Compare Slider 6s Standard Original branding OFF, Before & After 8s High Quality Portrait branding ON, Share, Delete, Done flows
- Release build smoke test (`assembleRelease`)
- Regression verification: all CompareScreen, CompareLibrary, CameraViewModel, SessionBackupExporter tests remain green

**Touches:** Any minor corrections from integration issues in previous blocks.
**Result:** Feature is release-ready. No regressions.

---

## 30. Video Extras Section (V2+)

### 30.1 Purpose

The Extras section groups all optional additions to the exported video. These elements are independent of the core animation and do not affect rendering parameters, ContentScale, or canvas dimensions.

### 30.2 Wizard Layout

In the Configuring state, the Extras section appears between the Quality card and the Create Video button. It is implemented as a `SettingsCard` with the title "Extras":

```
Extras

  [ ] Show title and date
      My grandparents · 2008 → 2026

  [ ] Show location
      Munich, Germany

  [ ] Add #MadeWithSameView card
```

No structural changes are needed to accommodate future additions — new items are appended within the existing card.

### 30.3 Item Order

Within the Extras section, items appear in this fixed order:

1. Show title and date
2. Show location
3. Add #MadeWithSameView card

Rationale: user-authored session content precedes app branding. This order is permanent and must not be dynamically reordered.

### 30.4 Branding Toggle Migration

The existing "Add #MadeWithSameView card" toggle moves from its current standalone position into the Extras section. Its behavior, persistence, default state, and DataStore key are unchanged (§13).

---

## 31. Show Title and Date Overlay

### 31.1 Purpose

When enabled, a brief text overlay appears during the initial Hold phase of the animation, identifying the session with user-authored context (title and/or date pair). The overlay disappears before the main animation begins. The comparison remains the central element of the video.

This overlay is not a branding element and does not conflict with §13.6.

### 31.2 Toggle Label

**"Show title and date"**

### 31.3 Dynamic Preview Line

A preview line appears directly below the toggle, always visible regardless of whether the toggle is enabled or disabled. It shows exactly the text that will appear in the exported video.

**Priority logic for the preview and the video content are identical:**

| Available data | Preview + video content |
|---|---|
| Title + date pair (Levels 1–4) | `My grandparents · 2008 → 2026` |
| Date pair only (Levels 1–4, no title) | `2008 → 2026` |
| Title only (no `reference.date`) | `My grandparents` |
| Neither title nor date | Toggle disabled — see §31.4 |

The date pair is computed using the same 5-level priority chain as `CompareLabelLogic.computeCompareLabels()` (defined in `COMPARE_FLOW_V1.md §41.4`), using `reference.date` and `capture.timestampMs`.

**Level 5 exclusion:** When `reference.date` is absent, `computeCompareLabels()` returns Level 5 labels ("Reference" / "Current"). These are role descriptors, not temporal context. Level 5 results in no date line in the overlay. Only the title is shown (if present).

**Separator:** When both title and date pair are present, they are combined with a middle dot (·): `Title · DatePair`. The middle dot is reserved for this separator; the arrow (→) is reserved for the date pair itself.

The preview line is always visible. It does not disappear when the toggle is switched off. Its content reflects the session's current metadata state.

**Long title handling:** The preview line is truncated to a single line with ellipsis if the title exceeds the available width. The same truncation applies in the video.

### 31.4 Disabled State

When neither `content.title` nor a computable date pair (Levels 1–4) is available:

- Toggle remains **visible** (discoverability: the user must be able to discover the feature exists)
- Toggle is **disabled** (grayed out, not tappable)
- Preview line shows: **"Add a title or date in Edit Session"**

The toggle is never hidden regardless of metadata state.

### 31.5 State

The "Show title and date" toggle is **not** persisted. It is local export state within the active `CreateVideoScreen` wizard session.

**Default: OFF.**

The toggle resets to OFF when `CreateVideoScreen` is re-opened. This matches the behavior of other export parameters (mode, format, duration, quality) and reflects that the decision to include personal session context is specific to each individual export. No DataStore key is added for this toggle.

### 31.6 Video Overlay Behavior

**Trigger:** Toggle enabled AND at least one of (title, date pair at Levels 1–4) is available.

**Phase:**
- Compare Slider mode: Hold Reference phase (0–15 % of animation duration `T`)
- Before & After mode: Hold Before phase (initial hold before the crossfade)

**Appear:** The overlay appears at full opacity from frame 0 of the Hold phase. No fade-in is applied.

**Fade-out:** Begins at 80 % of the Hold phase frame count. Completes at the last frame of the Hold phase. By the first Sweep frame (Compare Slider) or first Crossfade frame (Before & After), the overlay alpha is 0.

**Visibility outside the Hold phase:** The overlay is not rendered on any frame outside the initial Hold phase. It does not reappear during the Sweep, Hold Capture, Crossfade, Hold After, or endcard phases.

**Short hold phase (4s + branding ON):** The Hold Reference phase at 4s with branding ON is approximately 0.375s (≈ 11 frames). The overlay is visible at full opacity from frame 0, then fades out in the final frames. This is the minimum viable display duration. The video loops in the Gallery, providing additional viewing opportunities.

### 31.7 Content Hierarchy and Line Layout

Maximum 3 lines:

| Line | Content | Condition |
|---|---|---|
| Line 1 | Session title (`content.title`) | Non-empty after trim |
| Line 2 | Date pair (Levels 1–4 of `computeCompareLabels()`) | `reference.date` is available |

Line 1 precedes Line 2. When only one element is available, it occupies Line 1 only.

**Examples:**

```
My grandparents
2008 → 2026
```

```
2008 → 2026
```

```
My grandparents
```

When all three elements are present:

```
My grandparents
2008 → 2026
Munich, Germany
```

### 31.8 Position in the Canvas

**Bottom-left, left-aligned.**

Padding from canvas edge:
- Left: approximately 4 % of canvas width
- Bottom: approximately 4 % of canvas height

Both paddings scale proportionally with canvas resolution (Standard 1080p, High Quality 4K).

**Known behavior — Before & After mode with format mismatch:** When a portrait session image is exported in the Landscape 16:9 canvas format with ContentScale.Fit, the image occupies a centered column with `#17202F` side margins. The overlay in this case appears over the left side margin rather than over image content. This is accepted behavior: the dark background provides strong contrast for white text. This does not affect Compare Slider mode, which always uses fill semantics.

### 31.9 Typography and Visual Design

**Date pair (Line 2 when title is present; Line 1 when no title):**
- Weight: Bold
- Size: approximately 4.5 % of `min(canvasWidth, canvasHeight)` in pixels

**Title (Line 1 when present):**
- Weight: Regular
- Size: approximately 3.5 % of `min(canvasWidth, canvasHeight)` in pixels

**Location (Line 3, when present — see §32):**
- Weight: Regular
- Size: approximately 3.5 % of `min(canvasWidth, canvasHeight)` in pixels

**Color:** White (#FFFFFF)

**Shadow:** Black at approximately 75 % opacity, 1 px offset, blur radius approximately 3–5 px at 1080p (scales proportionally). Rendered via Bitmap Canvas text shadow (not View `setShadowLayer()` — the restriction in §16.2 applies only to the divider rendering path, not to text rendering on a Bitmap Canvas).

**No background plate, no chip, no badge, no outline, no blur, no Material component.** The design is intentionally minimal and timeless — not Social Media template–like.

**Line spacing:** approximately 20 % of the text size.

### 31.10 Data Sources

| Overlay element | Source | Notes |
|---|---|---|
| Title (Line 1) | `content.title` from `metadata.json` | Trimmed; blank treated as absent; long titles truncated with ellipsis |
| Date pair (Line 2) | `reference.date` + `capture.timestampMs` via `computeCompareLabels()` | Levels 1–4 only; Level 5 ("Reference/Current") → no date line |

No other metadata fields are read for the title/date overlay. `reference.dateSource`, `reference.userEdited`, `location.displayName`, `location.userEdited`, `additional.*`, EXIF data, and GPS coordinates are not accessed by the overlay rendering path. `location.city` and `location.country` are read exclusively by §32 (Show Location overlay).

### 31.11 VideoRenderConfig Extension

`VideoRenderConfig` is extended with an optional `VideoOverlay` parameter:

```kotlin
data class VideoOverlay(
    val title: String?,        // null if no title to show
    val dateLine: String?,     // null if no date pair (Level 5 or absent); e.g. "2008 → 2026"
    val locationLine: String?  // null if no location data; e.g. "Munich, Germany"
)

// Added to VideoRenderConfig:
val overlay: VideoOverlay? = null  // null = no overlay rendered
```

`CreateVideoViewModel` computes `VideoOverlay` from the session's `metadata.json` before constructing `VideoRenderConfig`. The renderers receive pre-computed display strings; no metadata parsing occurs inside `VideoFrameRenderer` or `VideoExportPipeline`.

Individual fields within `VideoOverlay` may be null independently: `title` is null when "Show title and date" is disabled or no title is available; `dateLine` is null when "Show title and date" is disabled or no date pair exists (Level 5 or absent); `locationLine` is null when "Show location" is disabled or no city/country data is available. When all three fields are null, `overlay` itself may be set to null as an optimization.

When `overlay` is null: no overlay frames are rendered. No change to frame count, timing, or canvas dimensions.

### 31.12 Interaction with Show Location and Branding Endcard

The title/date overlay, the Show Location overlay, and the branding endcard are temporally independent from each other:

- Title/date and Location overlays: during the initial Hold phase (animation start)
- Branding endcard: appended after animation (animation end)

All three toggles may be enabled simultaneously. Enabling one does not affect the behavior of the others. Neither overlay is rendered on endcard frames.

### 31.13 New i18n Keys

All user-facing text uses string resources. New keys required for this feature:

| Key | Usage |
|---|---|
| `create_video_extras_section_title` | SettingsCard title: "Extras" |
| `create_video_overlay_title_date_label` | Toggle label: "Show title and date" |
| `create_video_overlay_no_data_hint` | Disabled state preview: "Add a title or date in Edit Session" |

---

## 32. Show Location Overlay

### 32.1 Purpose

When enabled, a location line appears as the third line of the overlay during the initial Hold phase, showing the user-authored location context (city and/or country). The location line appears below the title and date pair and disappears before the main animation begins. The comparison remains the central element of the video.

"Show location" is a **separate, independent toggle** from "Show title and date". Location is never automatically included when "Show title and date" is enabled. The user must make an explicit, separate choice to include location.

This overlay is not a branding element and does not conflict with §13.6.

### 32.2 Toggle Label

**"Show location"**

### 32.3 Dynamic Preview Line

A preview line appears directly below the toggle, always visible regardless of whether the toggle is enabled or disabled. It shows exactly the location text that will appear in the exported video.

**Data source:** `location.city` and `location.country` from `metadata.json`.

**`location.displayName` must never appear in the preview or in the video**, regardless of availability. Rationale: `location.displayName` is a free-text field that may contain private information — street addresses, property names, private place identifiers. Restricting the video to city and country level limits exposure to non-identifying geographic context, consistent with the GPS exclusion policy in §23.2.

**Priority logic for the preview and the video content are identical:**

| Available data | Preview + video content |
|---|---|
| City + Country | `Munich, Germany` |
| City only | `Munich` |
| Country only | `Germany` |
| Neither city nor country | Toggle disabled — see §32.4 |

### 32.4 Disabled State

When neither `location.city` nor `location.country` is available:

- Toggle remains **visible** (discoverability: the user must be able to discover the feature exists)
- Toggle is **disabled** (grayed out, not tappable)
- Preview line shows: **"Add a city or country in Edit Session"**

The toggle is never hidden regardless of metadata state.

### 32.5 State

The "Show location" toggle is **not** persisted. It is local export state within the active `CreateVideoScreen` wizard session.

**Default: OFF.**

The toggle resets to OFF when `CreateVideoScreen` is re-opened. This matches the behavior of all other export-specific parameters. No DataStore key is added for this toggle.

### 32.6 Video Overlay Behavior

**Trigger:** Toggle enabled AND at least one of (`location.city`, `location.country`) is available.

**Phase:**
- Compare Slider mode: Hold Reference phase (0–15 % of animation duration `T`)
- Before & After mode: Hold Before phase (initial hold before the crossfade)

**Appear:** The overlay appears at full opacity from frame 0 of the Hold phase. No fade-in is applied. This is identical to §31.6.

**Fade-out:** Begins at 80 % of the Hold phase frame count. Completes at the last frame of the Hold phase. By the first Sweep frame (Compare Slider) or first Crossfade frame (Before & After), the overlay alpha is 0.

**Visibility outside the Hold phase:** The location line is not rendered on any frame outside the initial Hold phase. It does not reappear during the Sweep, Hold Capture, Crossfade, Hold After, or endcard phases.

The title/date lines (§31) and the location line (§32) share the same Hold-phase fade-out timing — they form a single unified overlay block rendered together.

### 32.7 Content Position

Location appears as Line 3, rendered below the date pair (Line 2) and title (Line 1). Line ordering adapts when earlier elements are absent:

| Present elements | Line 1 | Line 2 | Line 3 |
|---|---|---|---|
| Title + date + location | Title | Date pair | Location |
| Title + location (no date) | Title | Location | — |
| Date + location (no title) | Date pair | Location | — |
| Location only | Location | — | — |

### 32.8 Position in the Canvas

Identical to §31.8: bottom-left, left-aligned. Padding approximately 4 % of canvas width from the left edge, approximately 4 % of canvas height from the bottom edge. All overlay lines are rendered as a left-aligned group within that anchored position.

**Known behavior — Before & After mode with format mismatch:** Identical to §31.8 — overlay appears over the left side margin in landscape canvas with portrait session. Dark background provides strong contrast. Accepted behavior.

### 32.9 Typography and Visual Design

Identical to the title line (§31.9):
- Weight: Regular
- Size: approximately 3.5 % of `min(canvasWidth, canvasHeight)` in pixels
- Color: White (#FFFFFF)
- Shadow: Black at approximately 75 % opacity, 1 px offset, blur radius approximately 3–5 px at 1080p (scales proportionally). Rendered via Bitmap Canvas text shadow.

No background plate, no chip, no badge, no outline, no blur, no Material component.

Line spacing between all overlay lines: approximately 20 % of the text size.

### 32.10 Data Source

| Overlay element | Source | Notes |
|---|---|---|
| Location (Line 3) | `location.city` + `location.country` from `metadata.json` | Format: "City, Country" / "City" / "Country"; `location.displayName` is never used |

`reference.dateSource`, `reference.userEdited`, `location.displayName`, `location.userEdited`, `additional.*`, EXIF data, and GPS coordinates are not accessed by the location overlay rendering path.

### 32.11 VideoRenderConfig

`locationLine` is a field of the `VideoOverlay` data class (see §31.11). `CreateVideoViewModel` computes the location line from `metadata.json` — joining `location.city` and `location.country` with a comma separator — before constructing `VideoRenderConfig`. When `locationLine` is null (toggle disabled or no city/country data): the location line is not rendered and no existing frame is affected.

### 32.12 Interaction with Title/Date Overlay and Branding Endcard

- "Show title and date" and "Show location" are independent toggles — enabling one does not affect the other.
- Both overlays are rendered during the same Hold phase as a single unified text block.
- The branding endcard is temporally independent — appended after the animation, not during the Hold phase.
- Location is never rendered on endcard frames.

### 32.13 New i18n Keys

All user-facing text uses string resources. New keys required for this feature:

| Key | Usage |
|---|---|
| `create_video_overlay_location_label` | Toggle label: "Show location" |
| `create_video_overlay_location_no_data_hint` | Disabled state preview: "Add a city or country in Edit Session" |

### 32.14 Out of Scope

`location.displayName` in any form, reverse geocoding, GPS-based auto-fill, location search, or any network operation. These are forbidden by `GPS_RECREATION_SYSTEM_V1.md §12`.
