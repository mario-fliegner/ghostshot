# FIRST_RUN_WALKTHROUGH_GUIDE_V1

## 1. Document Status

This document is the V1 specification for three separate SameView discovery systems:

- Guide
- First-run walkthrough
- Contextual tips

This is a specification only. It does not implement the feature.

## 2. Analysis Gate Result

No blocking architectural or UX issue was found.

The concept is sound if the three systems remain separate and if tip state is centralized outside `CameraViewModel`, `CompareScreen`, Settings, and session storage.

### 2.1 Source-of-truth review

Reviewed documents:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/IMPLEMENTATION_NOTES.md`
- `docs/CAMERA_WORKFLOW_UX_V1.md`
- `docs/SETTINGS_UX_V1.md`
- `docs/ABOUT_SCREEN.md`
- `docs/RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `docs/COMPARE_FLOW_V1.md`
- `docs/GPS_RECREATION_SYSTEM_V1.md`

Reviewed implementation areas:

- `MainActivity` Navigation Compose graph
- `CameraScreen` permission flow, top actions, bottom workflow, reference menu, markers, GPS chip
- `CameraViewModel` capture/reference/compare state and one-shot events
- `CompareScreen` top app bar, Export dropdown, fullscreen behavior
- `CompareLibraryScreen` route integration
- `SettingsRepository` DataStore usage
- existing test packages for camera, compare, settings, about, markers, and GPS

### 2.2 Documentation and implementation differences

The following differences are not blockers but must be acknowledged by implementation:

- `CAMERA_WORKFLOW_UX_V1.md`, `SETTINGS_UX_V1.md`, and `ABOUT_SCREEN.md` describe CameraScreen overflow entries as `Settings` and `About`. Adding `Guide` is a compatible app-level extension because those specs already allow future Help-like entries and keep Settings/About out of the bottom workflow.
- `IMPLEMENTATION_NOTES.md` contains one stale note saying the CompareScreen Export restructuring was not yet implemented. The current code and later notes show it is implemented: `CompareScreen` has an Export icon with `Share image` and `Share video`.
- `GPS_RECREATION_SYSTEM_V1.md` says Block 8 was open in one relationship table, while `IMPLEMENTATION_NOTES.md` documents the GPS system and additional EXIF tags as implemented. This does not affect Guide/Walkthrough/Tip architecture, but GPS topic copy must describe current behavior from the implementation and latest notes.
- `SETTINGS_UX_V1.md` reserves `Hide Reference Peek Hint`, but this V1 must not add a tips toggle or onboarding toggle. The reserved setting is not a mandate and should remain unimplemented.

## 3. Product Intent

The feature improves discoverability without changing the capture, compare, export, marker, GPS, storage, or permission behavior of SameView.

The three systems have different jobs:

- Guide: permanent, user-initiated help and visual reference.
- First-run walkthrough: first-run orientation before first normal CameraScreen use, with user-initiated replay available from Guide.
- Contextual tips: short feature-existence hints anchored to live UI.

They must not collapse into one shared UI pattern.

## 4. Non-Goals

This feature must not introduce:

- new Android permissions
- analytics, tracking, telemetry, or network behavior
- cloud behavior
- camera capture behavior changes
- compare rendering changes
- session metadata changes
- export behavior changes
- marker behavior changes
- GPS behavior changes
- Settings toggles for tips or onboarding
- web content, WebView, FAQ pages, or long manuals

## 5. Navigation

SameView remains a single-activity Navigation Compose app with a flat navigation graph.

New destinations:

- `Guide`
- `GuideDetail`
- `Walkthrough`

CameraScreen overflow menu order:

1. Settings
2. Guide
3. About

`Guide` is an app-level destination. It opens as a dedicated fullscreen screen through normal Navigation Compose routing.

`Guide` must not be:

- a dialog
- a bottom sheet
- an overlay
- a Settings subsection
- embedded in CameraScreen

The `Walkthrough` destination serves two entry models:

- first-run gate entry when `walkthrough_completed == false`
- user-initiated replay entry from Guide

The Guide replay entry is not a state reset. It must not make the walkthrough appear automatically on the next app start. Replay completion or skip returns to Guide.

## 6. Package Architecture

Preferred package:

`com.isardomains.sameview.guide`

Everything below belongs inside the guide package unless otherwise stated:

- `GuideTopicId`
- `GuideTipId`
- `GuideTopic`
- `GuideTip`
- topic registry
- tip registry
- `GuideRepository`
- `GuideTipController`
- `GuideTipHost`
- `GuideScreen`
- `GuideDetailScreen`
- `WalkthroughScreen`
- walkthrough page definitions
- Guide/tip navigation contracts

Outside the guide package:

- `MainActivity` owns route registration and high-level navigation callbacks.
- CameraScreen exposes small integration signals and anchor bounds only.
- CompareScreen exposes small integration signals and anchor bounds only.
- CompareLibraryScreen, marker UI, and GPS UI expose only the minimum signal needed to make a tip eligible.
- Settings remains unchanged except for any shared DataStore module decision described in this spec.

The guide package must not depend on CameraX, image capture internals, session writing, compare rendering, or GPS calculation internals.

## 7. Persistence

Persistence must be centralized.

Recommended model:

- Create a dedicated DataStore Preferences file, for example `sameview_guide`.
- `GuideRepository` is the only public persistence API for Guide/Walkthrough/Tips state.
- Store walkthrough completion separately from seen tips.
- Store seen tips as an enum-backed set encoded centrally, not as one public repository method per tip.

Required persistent state:

- `walkthrough_completed: Boolean`, default `false`
- `seen_tip_ids: Set<GuideTipId>`, default empty

Required repository operations:

- observe whether the walkthrough is complete
- mark walkthrough complete
- observe seen tip state
- mark a tip seen
- reset contextual tip state

Reset and replay behavior:

- `Show tips again` resets contextual tip seen-state only.
- `Show tips again` must not reset walkthrough completion.
- `Show tips again` must not reset permissions, settings, sessions, exports, marker state, GPS state, or app data.
- `Show walkthrough again` opens the walkthrough immediately from Guide.
- `Show walkthrough again` must not reset walkthrough completion.
- `Show walkthrough again` must not affect contextual tip seen-state.
- `Show walkthrough again` must not reset permissions, settings, sessions, exports, marker state, GPS state, or app data.

Gate behavior:

- The first-run walkthrough gate is controlled only by `walkthrough_completed == false`.
- Guide replay is a user-initiated route and does not change the gate state.

DataStore safety:

- Missing keys are valid defaults.
- Unknown stored tip ids must be ignored.
- New tips must default to unseen.
- Removing an obsolete tip must not crash parsing.

## 8. Guide

Guide is a permanent discovery area.

Main topics:

- Getting started
- Reference photos
- GPS guidance
- Export

Each topic opens a dedicated detail screen. Reference photos and Export are consolidated topics; each covers multiple related sub-features within the standard detail screen format (see §8.2 and §8.3).

The following topics from the prior structure are no longer standalone Guide topics:

| Removed topic | Disposition |
| --- | --- |
| `Reference photos` | Merged into `Reference photos` (the consolidated topic; see note below) |
| `Markers` | Merged into `Reference photos` (the consolidated topic; see note below) |
| `Share comparison image` | Merged into `Export` |
| `Create video` | Merged into `Export` |
| `Backups` | Merged into `Export`, as `Session backups` |
| `Favorites` | Removed entirely; not merged into any topic. The Favorites feature itself (star toggle, Library filter) is unchanged — it is self-explanatory and does not require dedicated Guide content. |
| `Compare` | Removed entirely; not merged into any topic. Compare remains discoverable through the always-visible primary Compare bottom-bar button (`CAMERA_WORKFLOW_UX_V1.md` §5) and CompareScreen's own self-explanatory slider/fullscreen UI. No tip has ever targeted this topic (`GUIDE_TIPS_UX_V1.md` §23.2). |

Note: the pre-consolidation standalone topic named `Reference photos` (image selection only) is distinct from the current consolidated topic of the same name, which also covers overlay alignment and reference markers (§8.2). The consolidated topic was briefly planned under the working name `Reference photos & alignment`; the shipped Guide topic name is `Reference photos`.

This consolidation changes only the Guide topic registry contents and Guide detail-page copy. It does not change Guide navigation, persistence, or package architecture (§5–§7).

Guide main screen structure:

- Getting started is not a normal feature topic. It is the Guide's orientation and walkthrough-replay entry point (§8.1) and receives moderate visual emphasis to reflect that distinct role, rendered as the first element on the Guide main screen, above the remaining topic list.
- Getting started reuses the existing hero-card pattern already established by `AboutScreen` (`ABOUT_SCREEN.md` §10: "hero card aligned with Settings card language"). It does not introduce a new or custom card component.
- The remaining three topics — Reference photos, GPS guidance, Export — are standard topic rows, structurally and visually identical to one another. No topic other than Getting started receives elevated visual weight.
- Standard topic rows reuse the `SettingsCard` row family already used by `SettingsScreen`, `EditSessionScreen`, and `CreateVideoScreen` (Configuring state). Guide does not introduce a new or custom card component for standard rows.
- Each standard topic row contains a Material icon, title, one short description line, and chevron.
- Example topic copy pattern: `Reference photos` / `Choose, align, and mark your reference photo.`
- Spacing between topic rows should be generous enough that the 4-topic list does not read as a truncated version of the former 9-topic list. Exact spacing values are an implementation-planning decision, not fixed by this specification.
- Topic rows (Getting started and standard) remain visually distinct from the separated bottom actions (§8.4).

Guide detail screen structure:

- title
- short introduction
- screenshot
- 1-2 short sentences
- screenshot
- 1-2 short sentences
- additional screenshot plus 1-2 short sentences if needed

Guide detail screens use real screenshots, not walkthrough mockups.

Guide detail screens must not become:

- documentation dumps
- FAQ pages
- long manuals
- web content
- side-panel layouts
- wide documentation-style two-column layouts

Content must remain concise and visual. Guide detail pages are reading/reference content, not workflow presentations.

### 8.1 Getting Started
Getting Started should reuse or closely mirror first-run walkthrough content.

Avoid duplicate maintenance by defining shared page/topic content data where practical. The walkthrough may use the same core copy and visual assets with different navigation chrome.

### 8.2 Reference Photos

Reference photos consolidates three sub-features into one detail screen, using the screenshot-plus-short-sentence block format defined above (one screenshot and 1-2 sentences per sub-feature, in order):

- Reference image selection
- Overlay alignment
- Reference markers

This uses the detail screen's existing block allowance (introduction plus up to three screenshot/text blocks) exactly as specified — one block per sub-feature. It does not require a new content format or a sectioned/anchored layout.

### 8.3 Export

Export consolidates three sub-features into one detail screen, using the same block format:

- Share comparison image
- Create video
- Session backups

Share comparison image content should lead the detail screen (appear first, immediately after the introduction). It is the only Export sub-feature reachable via a Guide Tip `Learn more` action (`SHARE` tip; see `GUIDE_TIPS_UX_V1.md` §23.2). Leading with it keeps that navigation contextually relevant without introducing in-page anchors or deep-linking.

### 8.4 Guide Bottom Actions

Guide main screen contains two visually separated bottom actions:

1. `Show tips again`
2. `Show walkthrough again`

These are not Guide topics.

They must be visually separated from the normal Guide topic list and must not look like normal Guide topics.

#### Show Tips Again

Behavior:

- opens a confirmation dialog
- confirm resets contextual tip seen-state
- cancel leaves state unchanged

It must not reset the first-run walkthrough completion state.

It must not affect permissions, settings, sessions, exports, marker state, GPS state, or app data.

#### Show Walkthrough Again

Behavior:

- opens the walkthrough immediately from Guide
- does not reset walkthrough completion state
- does not cause the walkthrough to appear again automatically on next app start
- does not affect contextual tip seen-state
- does not affect permissions, settings, sessions, exports, marker state, GPS state, or app data
- returns to Guide after the walkthrough is completed or skipped

## 9. First-Run Walkthrough

The walkthrough is separate from Guide and contextual tips.

The first-run gate is shown after camera permission flow and before first normal CameraScreen usage when `walkthrough_completed == false`.

The walkthrough can also be replayed from Guide through `Show walkthrough again`. Replay is user-initiated and is not controlled by resetting `walkthrough_completed`.

The walkthrough must be:

- fullscreen
- opaque
- a dedicated screen
- navigated through normal app navigation or an equivalent top-level gate

The walkthrough must not:

- overlay the live camera preview
- use transparent camera backgrounds
- use floating modal cards above CameraScreen
- request permissions
- change camera/session state

Walkthrough pages:

| Page | Title | Navigation |
| --- | --- | --- |
| 1 | Then and now | Skip + Next |
| 2 | Align the overlay | Skip + Back + Next |
| 3 | Take the shot | Skip + Back + Next |
| 4 | See what changed | Back + Start |

### Page 1 — Then and now

Purpose: Establish what SameView does and why the user would want it before any workflow step is introduced. This page leads with the outcome — the payoff — rather than the first task in the workflow. It communicates the photo source, the location requirement, and the change-over-time concept in order, so that later pages can build on established context rather than introduce ideas from nothing.

Body copy:

Pick an old photo.
Go back to where it was taken.
See how it's changed.

The three-fragment structure is intentional. Each fragment conveys one concept: the user provides an existing photo from their collection; they must physically return to where the original photo was taken; the result is a comparison that shows change over time. The location requirement is introduced here so that users encounter it once before page 2 asks them to act on it.

Illustration intent (as implemented): a full-bleed photorealistic alpine valley landscape (mountains, lake, meadow, path, pine trees), partially desaturated on the left side of the frame and transitioning to full color toward the right. A bordered "reference photo" card is overlaid, showing a tighter crop of the same landscape, with a curved directional arrow pointing from the card to a specific tree in the background — communicating "find this spot in the real world."

### Page 2 — Align the overlay

Body copy:

Go back to the original spot. Drag and scale the ghost photo over the live camera view until it lines up.

Two sentences are used on this page only. The first reinforces the location requirement introduced on page 1. The second describes the alignment action. This is the most conceptually complex step in the workflow and warrants two sentences.

Illustration intent (as implemented): the same alpine valley landscape, in full color, with a bordered "reference photo" card overlaid centrally. A four-directional move icon and a corner resize icon are shown on the card, communicating that it can be dragged and scaled to align with the surroundings.

This illustration intentionally uses a framed reference-photo card layered over the landscape rather than a single blended, translucent camera-feed overlay. The framed-card treatment is the approved presentation: it must not be flattened into two unrelated, disconnected photos (e.g. side by side or unrelated crops) — the card and the background must read as one connected "photo held up to the view" composition, not as two arbitrary images.

### Page 3 — Take the shot

Body copy:

Once the overlay lines up, tap the shutter from the same spot.

"From the same spot" briefly reinforces the location requirement without re-explaining it. Page 2 has already established the concept.

Illustration intent (as implemented): the same alpine valley landscape and reference-photo card, with a large filled white circular disc placed at the bottom of the frame, dominating the lower portion of the illustration. This page must read visually as the "press something" page, clearly distinct from page 2's "move something" illustration.

### Page 4 — See what changed

Body copy:

Drag the slider to reveal then and now.

"Then and now" in the body copy deliberately echoes page 1's title. The walkthrough opens and closes on the same idea, giving the four-page sequence a sense of arrival.

Illustration intent (as implemented): the same alpine valley landscape, split by a vertical divide between a desaturated left half and a full-color right half, with a circular "‹ ›" slider-handle icon centered on the divide — reading immediately as a before/after comparison slider. The divide position is visually close to the center of the frame; this page uses a distinct visual pattern from page 1 (slider-divide vs. reference-photo-card-with-arrow) rather than a mirrored variant of the same illustration.

Walkthrough visual direction:

- Use a single shared photorealistic landscape illustration, rendered as static local WEBP image assets and reused across all four walkthrough pages from a consistent vantage point.
- Page-specific UI iconography (reference-photo frame, move/resize handles, capture disc, before/after slider handle) is composited directly into each WEBP asset, not rendered as separate Compose overlays.
- Assets must remain local app assets (`drawable-nodpi`, WEBP). No network images.
- Visuals communicate the workflow conceptually and do not reproduce exact production UI screens.
- Page 2 must clearly communicate that a reference photo is being positioned against the surroundings, including move/resize affordances on the reference-photo card.
- Page 3 must be visually distinct from page 2 despite sharing the same landscape. The capture disc must be the dominant element.

Illustration system — current implementation:

A single alpine valley scene (mountains, lake, meadow, winding path, pine trees) is used across all four walkthrough pages, viewed from a consistent, similar vantage point.

Recurring elements across pages 1–3:

- Full-bleed background landscape (page 1: partially desaturated on the left side of the frame, transitioning to full color toward the right; pages 2–3: fully saturated).
- A bordered "reference photo" card overlaid on the landscape, representing the photo the user is trying to recreate.

Page-specific iconography baked into the artwork:

- Page 1: a curved directional arrow from the reference-photo card to a specific tree in the background, implying "find this spot in the real world."
- Page 2: a four-directional move icon and a corner resize icon overlaid on the reference-photo card, implying "drag and resize this overlay."
- Page 3: a large white circular disc dominating the lower portion of the frame, implying a capture action.
- Page 4: a vertical divide between a desaturated left half and a full-color right half, with a circular "‹ ›" slider-handle icon at the divide, implying a before/after comparison.

Visual story narrative:

| Page | Illustration role | Key visual element (as implemented) |
| --- | --- | --- |
| 1 | Match the reference photo to the real location | Reference-photo card + directional arrow to a landscape tree |
| 2 | Align/adjust the overlay | Reference-photo card + move/resize icons |
| 3 | Capture the shot | Dominant white capture disc at the bottom of the frame |
| 4 | Reveal the comparison | Desaturated/full-color split with a centered "‹ ›" slider icon |

Pages 1 and 4 intentionally use distinct visual patterns rather than a mirrored slider treatment: page 1 uses the reference-photo-card-with-arrow motif, page 4 uses the before/after slider-divide motif. Both are valid, approved presentations of their respective page purpose.

Walkthrough branding:

- The SameView wordmark occupies a reserved layout slot at the top of the walkthrough screen.
- Pages 1 and 2 use full visual illustrations. On these pages the wordmark is intentionally not rendered, but its layout slot remains allocated with a constant height. The reserved space must be identical on every page so that the image slot position is unaffected by wordmark visibility.
- Pages 3 and 4 render the SameView wordmark text within the reserved slot.
- No separate logo treatment is required.
- Use existing SameView colors and visual language.

Progress indicator:

- Use progress dots only, for example `● ○ ○ ○`.
- Progress dots are not clickable. They are visual position indicators only.
- Do not use step counters, numeric counters, or percentage indicators.

Navigation buttons:

- Page 1: `Skip` (low emphasis, left) and `Next` (high emphasis, right).
- Page 2: `Skip` (low emphasis, left), `Back` (medium emphasis, center), and `Next` (high emphasis, right).
- Page 3: `Skip` (low emphasis, left), `Back` (medium emphasis, center), and `Next` (high emphasis, right).
- Page 4: `Back` (medium emphasis, left) and `Start` (high emphasis, right).
- `Skip` is available on pages 1–3 only. There is no `Skip` on page 4.
- `Start` replaces `Next` on the final page. `Start` is not a skip action; it completes the walkthrough and proceeds to the camera.
- Swipe left and right is enabled across all pages.
- Use existing SameView button components, styling, typography, shapes, and color system.
- Do not introduce walkthrough-specific button styling.

Rationale for the three-button model on pages 2 and 3:

`Skip` and `Back` serve different functions and must both be available on intermediate pages. `Skip` exits the walkthrough entirely. `Back` returns to the previous page. Removing `Skip` from intermediate pages forces a user who decides to exit mid-walkthrough to navigate backward to find an exit, which adds friction for users who have already committed time to earlier pages. Both actions must remain available simultaneously.

Future UX evaluation — page 1 title:

The approved title is `Then and now`. An alternative — `Same place. Different time.` — was evaluated during the UX analysis and set aside in favour of the current title. This is not an open blocker and must not delay implementation. It may be re-evaluated in a future UX review if there is evidence that the current title underperforms.

Responsive behavior:

- Phone portrait: single-column layout.
- Phone landscape: two-column layout, mockup left, content right, overall composition centered.
- Tablet portrait: two-column layout, mockup left, content right, overall composition centered.
- Tablet landscape: two-column layout, mockup left, content right, overall composition centered.
- Larger mockup and additional whitespace are allowed on tablet/expanded layouts.
- Title, text, and button sizes remain broadly consistent with phone sizing.
- Tablet layouts must not stretch across the full width.
- Tablet layouts must not appear as a narrow phone column.

Walkthrough layout model:

The walkthrough screen is divided into four fixed layout slots. Slot positions must not shift when the user navigates between pages.

Portrait (single-column) slot order:

0. Leading slack slot — a spacer above the image slot, weighted equally with the text slot below. This centers the image slot and text slot together as one group between the top safe area and the progress dots slot. The spacer is weight-based, not content-based: its size depends only on the fixed heights of the other slots, never on per-page title or body length, so the image slot position stays identical across all four pages.
1. Image slot — the mockup illustration. The slot height is determined by the image aspect ratio and does not change between pages.
2. Text slot — the page title and body copy. This is the only slot that absorbs variation in content length. The slot boundaries are fixed. The title and body copy are top-aligned within the slot. Remaining space accumulates below the body text, between the body and the progress dots slot. Short body text produces more space below it; longer body text reduces that space. The slot itself does not grow or shrink between pages. Layout stability takes precedence over equalized copy lengths across pages.
3. Progress dots slot — the row of progress indicator dots. The slot position is fixed directly below the text slot on every page.
4. Navigation button slot — the row of navigation buttons. The slot position is fixed directly below the progress dots slot on every page.

The leading slack slot and the text slot's remaining space are not bundled with the progress dots slot or navigation button slot into a single centered block — the dots and button slots keep their own fixed position below, unaffected by how the leading slack slot is sized. This satisfies the forbidden-pattern rule below while still visually centering the image and text as a group.

Landscape (two-column) arrangement:

The left column contains the image slot. The right column contains the wordmark slot, text slot, progress dots slot, and navigation button slot. In landscape the wordmark slot is placed at the top of the right column rather than above the two-column row. This placement allows the row to span the full safe-area height without a top offset, so the composed two-column group is vertically centered within the safe area. The wordmark slot height (28dp) and content rules are identical to portrait: pages 1 and 2 leave the slot empty; pages 3 and 4 render the SameView wordmark text within it.

Stability requirements:

- The image slot position must be identical across all four pages.
- The progress dots slot position must be identical across all four pages.
- The navigation button slot position must be identical across all four pages.
- All variation in title and body text length between pages must be absorbed inside the text slot only.
- The text slot must be sized to accommodate the tallest title and body combination present in the walkthrough content.
- Layout stability takes precedence over equal text line counts across pages. Pages are not required to contain the same amount of text.

Forbidden implementation patterns:

- Fake line breaks, empty lines, or placeholder strings used to equalise page heights are forbidden.
- Layout strategies that redistribute gaps between slots in response to content height changes are forbidden. These strategies move every slot position when content height changes and do not satisfy the stability requirements.
- Centering a variable-height block that bundles the text slot together with the dots slot and button slot is forbidden. This pattern causes the image and fixed-position slots to shift whenever text height changes.
- Showing or hiding the wordmark slot without reserving constant layout space for it is forbidden. The wordmark slot must occupy the same vertical extent on every page regardless of whether the wordmark text is rendered on that page.

Completion rules:

- In first-run gate entry, `Skip` marks walkthrough complete and returns to normal CameraScreen.
- In first-run gate entry, `Start` marks walkthrough complete and returns to normal CameraScreen.
- In replay entry, `Skip` and `Start` do not reset or modify walkthrough completion.
- In replay entry, `Skip` and `Start` do not affect contextual tip seen-state.
- Replay completion or skip returns to Guide.
- Completion persists across app restarts.
- The walkthrough is not shown automatically again after completion.
- Rotation or recomposition must not reopen the walkthrough after completion.
- Process recreation must not reopen the walkthrough after completion has persisted.
- `Show walkthrough again` remains available as a user-initiated replay route from Guide.

Camera permission and first-run gate behavior:

- Camera permission remains owned by the current CameraScreen permission flow.
- No walkthrough logic runs during the permission request, rationale state, permanent denial state, or permission re-check state.
- First-run walkthrough appears only after permission is granted.
- The walkthrough must not appear above Android permission dialogs.
- Permanent denial and rationale states are unchanged.
- The walkthrough completion state is loading-aware; loading must not be treated as incomplete.
- While the first-run gate state is loading after permission is granted, normal camera content must not render.
- While the first-run gate state is loading after permission is granted, the live camera preview must not bind or appear.
- While the first-run gate state is loading after permission is granted, the app shows only a neutral SameView surface.

## 10. Contextual Tips
Contextual tips are separate from Guide and walkthrough.

Purpose:

- feature discoverability only
- explain that a feature exists
- provide one concise sentence

They must not:

- teach the full feature
- duplicate Guide detail content
- become a forced tour
- block normal workflow
- obscure the target feature

Actions:

- `Learn more`
- `Got it`

Behavior:

- `Learn more` is visible in V1.
- `Learn more` opens the corresponding Guide topic and marks the tip seen.
- `Got it` dismisses the tip and marks it seen.
- Tap-outside dismissal is not used.
- Auto-dismiss timers are not used.
- No X button is required in V1.

## 11. Tip Visual Design
Approved direction:

- Tips use a small SameView-style card.
- Tip contents are title, one short sentence, `Learn more`, and `Got it`.
- Tip cards use existing SameView surfaces, colors, typography, and spacing principles.
- Tips do not introduce a separate onboarding design language.
- Tips use a small pointer/anchor indicator.
- Tips animate with fade in and fade out only.
- Tips must not use pulse, bounce, or coach-mark animation.

The target UI remains visible.

Tips must not use:

- full-screen dimming
- full-screen coach-mark systems
- forced click-through tours
- feature-obscuring overlays

Responsive requirements:

- Phone portrait: place the tip above or below the target, depending on safe space.
- Phone landscape: place the tip beside the target when practical; otherwise place it above or below.
- Tablet/expanded layouts: keep the tip close to the target and prefer side placement when it keeps the target visible and visually connected.
- Tips must not be placed far away from their target just because more space is available.
- If no safe connected placement exists, the tip is deferred rather than shown poorly.

Accessibility:
- Tips must be reachable by TalkBack.
- Focus order enters the tip after the anchor context without trapping focus.
- Buttons have explicit labels.
- Anchors and tips must not create duplicate or misleading content descriptions.

## 12. Tip Controller and Anti-Spam Rules

`GuideTipController` owns eligibility, priority, timing, and seen-state checks.

Anti-spam rules:

- never show multiple tips simultaneously
- do not chain tips immediately after one another
- do not interrupt capture
- do not interrupt exports
- do not interrupt rendering
- do not interrupt picker flows
- do not interrupt permission flows
- do not interrupt dialogs
- do not interrupt active overlay or marker gestures
- do not show tips when CameraScreen is not resumed
- do not show tips while CompareScreen is in fullscreen mode
- defer tips while snackbar/error feedback is active where overlap would occur

Recommended cooldown:

- after any tip dismissal or Learn More navigation, do not show another tip until the user returns to a stable screen state and at least one normal user action has occurred.

## 13. Tip Definitions

Use registry-based, data-driven definitions.

`GuideTip` should include:

- `id: GuideTipId`
- title string resource
- body string resource
- target anchor key
- optional `guideTopicId`
- priority
- screen/scope

`GuideTopic` should include:

- `id: GuideTopicId`
- title string resource
- summary string resource
- detail content definition
- optional visual asset references

Adding a new topic or tip should require:

- one enum/id addition
- one registry entry
- string resources in English and German
- integration signal only if a new screen state is required

It should not require new repository APIs or persistence keys per tip.

## 14. Initial Tip Trigger Validation

### Reference tip

Preferred trigger is valid with one adjustment:

- Show immediately after walkthrough completion or first normal CameraScreen entry after walkthrough completion.
- Anchor to the Reference button.
- Do not show during permission, picker, snackbar, or dialog states.

### Align tip

Preferred trigger is valid:

- after successful reference load
- after approximately 2-3 seconds of inactivity
- no drag gesture
- no pinch gesture
- no active alignment interaction
- no marker edit mode

Implementation must treat overlay drag/pinch and camera zoom gestures as blockers.

### Capture tip

No Capture tip in V1. This is correct. Capture is already the primary visual action.

### Compare tip

Preferred trigger is valid:

- after first successful capture with valid compare input
- anchor to Compare
- do not show if auto-open compare immediately navigates to CompareScreen
- if `Reset overlay after capture` clears the reference, show only if the Compare action remains a valid active session entry; otherwise defer to History tip.

### History/Library tip

Preferred trigger is valid:

- only after at least one stored comparison exists
- only after returning to CameraScreen
- anchor to History

Because History is always visible, this tip should be lower priority than Reference/Align/Compare.

### Export tip

Preferred trigger is valid:

- on first CompareScreen open with a session that has export available
- anchor to the Export icon
- do not show while fullscreen, backup/export in progress, delete dialog open, overflow menu open, or export dropdown open

### Marker tip

Preferred trigger is valid:

- only when marker-related UI is first opened in the Reference menu
- anchor to the marker menu item or marker edit affordance that is currently visible
- do not show during marker drag or while the loupe is active

### GPS tip

Preferred trigger needs precision:

- show only when GPS functionality becomes visible or newly relevant, not merely when the setting exists.
- Valid eligibility examples: Recreation Guidance is ON, location permission is granted, a reference image has GPS EXIF, and the GPS guidance chip is visible or waiting for a fix.
- Anchor to the GPS guidance chip.
- Do not show in Settings; Settings continues to use normal supporting text.

## 15. Integration Surface

Other screens should integrate through a small stable surface:

- provide anchor bounds or tagged anchor metadata
- provide high-level eligibility signals
- invoke tip dismiss/Learn More callbacks from the host

Do not scatter tip logic through screen code.

CameraScreen should not know whether a specific tip has been seen. It may report state such as:

- reference loaded
- successful capture happened
- compare input exists
- marker menu open
- marker edit mode active
- active gesture/interaction state
- snackbar/dialog/picker/capture active

CompareScreen should not know whether the export tip has been seen. It may report state such as:

- session id exists
- export available
- export menu closed
- fullscreen false
- no modal dialog active

## 16. Settings Behavior

Settings must not use contextual tip popups.

Settings continues using:

- normal setting descriptions
- normal supporting text
- standard dialogs for permission rationale

Do not add:

- tips enabled toggle
- onboarding toggle
- contextual tip setting

`Show tips again` and `Show walkthrough again` belong in Guide, not Settings.

## 17. Copy and Internationalization

All visible text must use string resources.

Required resource files:

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de/strings.xml`

Coverage required for:

- Guide screen title
- Guide topic titles and summaries
- Guide detail copy
- Show tips again action and confirmation dialog
- Show walkthrough again action
- Walkthrough page titles and body copy
- Walkthrough buttons
- Tip titles and body copy
- Got it
- Learn more
- accessibility content descriptions
- navigation/back content descriptions where needed

English copy:

- sentence case
- concise

German copy:

- fully supported at launch
- no English-only fallback strings for this feature

Tip copy rules:

- title
- one sentence
- if more explanation is needed, use Guide

## 18. Visual Content

Guide detail pages use real screenshots and concise supporting text.

The walkthrough uses a shared photorealistic landscape illustration (local WEBP assets under `drawable-nodpi`) reused across all four pages, with page-specific UI iconography composited into the artwork. See §9 "Illustration system — current implementation" for details. It must not use literal screenshots of SameView production UI within the illustration artwork.

Assets must be local app assets. No network images.

## 19. Responsive Layout

Guide, Walkthrough, and Contextual Tips must support:

- phone portrait
- phone landscape
- expanded layouts
- tablets

Large screens should meaningfully use available space while keeping content readable and visually grouped. Large screens must not simply stretch phone layouts across the full available width.

Guide main screen:

Guide main screen responsive behavior follows `WindowWidthSizeClass` exclusively (Compact / Medium / Expanded), per `RESPONSIVE_LAYOUT_SYSTEM_V1.md` §3.2 and §5.5. See `RESPONSIVE_LAYOUT_SYSTEM_V1.md` §7.10 for the full specification. Summary:

- Compact: single-column topic layout (Getting started hero card, then the three standard topic rows), full available width.
- Medium: single-column topic layout, full available width; no grid.
- Expanded: single-column topic layout, bounded by a centered max-width container (recommended: 680 dp, matching `SettingsScreen`, `EditSessionScreen`, and `CreateVideoScreen` Configuring state). Guide main screen does not use a multi-column topic grid at any width class.
- Topic rows fill the width of their containing column — the full screen width on Compact/Medium, the centered max-width container on Expanded — and never stretch to the full screen width when the max-width container is active.

Guide detail screens:

- Phone: single-column vertical reading flow.
- Tablet/expanded layouts: same vertical reading structure, with larger max width and more spacing allowed.
- No side-panel layout.
- No documentation-style two-column layout.

Walkthrough:

- Phone portrait: single-column layout.
- Phone landscape: two-column layout with mockup left and content right.
- Tablet portrait and tablet landscape: two-column layout with mockup left and content right.
- Overall composition remains centered.
- Tablet layouts must not stretch across the full width or collapse to a narrow phone column.

Contextual tip placement:

- Phone portrait: above or below target.
- Phone landscape: beside target when safe, otherwise above or below.
- Tablet/expanded layouts: keep visually close to the target and use side placement when practical.
- If safe placement does not exist, defer the tip.
- Do not show disconnected tips.
- The target UI element must remain visible while the tip is shown.

CameraScreen itself remains governed by `CAMERA_WORKFLOW_UX_V1.md`; this feature must not perform a broad CameraScreen responsive redesign.

## 20. Risk Review
CameraScreen lifecycle:

- Tips must not start CameraX, stop CameraX, rebind use cases, or alter keep-screen-awake behavior.
- First-run gate loading must not render normal camera content or bind the live camera preview before walkthrough routing is resolved.

Permission flow:

- Walkthrough appears after camera permission is granted.
- Walkthrough logic does not run during permission request, rationale, permanent denial, or permission re-check states.
- Tips never interrupt permission dialogs or rationale screens.

Photo Picker flow:

- Reference and SAF pickers are not interrupted.
- Reference tip must not show while a picker is pending.

Overlay gestures:

- Align tip requires inactivity and no drag/pinch.
- Active overlay gestures dismiss/defer tips.

Capture state:

- Tips must not appear during `isCaptureInProgress`.
- Tips must not alter capture tokens, stale callback handling, or save pipeline behavior.

CompareScreen navigation:

- Learn More navigation from CompareScreen must be explicit and must not mutate compare input.
- Back from Guide returns through normal Navigation Compose behavior.

DataStore defaults:

- missing keys default to walkthrough incomplete and no tips seen.
- loading state must not be treated as walkthrough incomplete.
- parser must ignore unknown tip ids.

DataStore migration risks:

- dedicated guide DataStore avoids coupling to Settings keys and reduces migration risk.
- no existing settings migrations are required.

Responsive callout positioning:

- anchor-based placement is required.
- fallback is defer, not overlap.

Accessibility focus order:

- tips must not trap TalkBack.
- Learn More must announce navigation intent clearly.

## 21. Testing Strategy

Likely new test classes:

- `GuideRepositoryTest`
- `GuideTipControllerTest`
- `GuideScreenTest`
- `GuideNavigationTest`
- `WalkthroughScreenTest`
- `GuideTipHostTest`
- `CameraGuideTipIntegrationTest`
- `CompareGuideTipIntegrationTest`

Persistence tests:

- walkthrough completion persists
- seen tip state persists
- unknown stored tip ids are ignored
- Show tips again resets only contextual tip state
- walkthrough completion is not reset by Show tips again
- Show walkthrough again does not reset or modify walkthrough completion
- Show walkthrough again does not reset or modify contextual tip seen-state

Guide tests:

- Guide opens from CameraScreen overflow
- topic list renders
- each topic opens detail
- back returns to Guide
- Guide back returns to CameraScreen
- Show tips again confirmation dialog works
- Show walkthrough again opens the walkthrough from Guide
- Guide bottom actions are visually separated from the normal topic list and are not rendered as normal topics

Walkthrough tests:

- first-run gate is shown after camera permission and before normal CameraScreen use when `walkthrough_completed == false`
- first-run gate is not shown automatically when `walkthrough_completed == true`
- first-run Skip completes and does not show automatically again
- first-run Start completes and does not show automatically again
- Guide replay Skip does not reset walkthrough completion or tip state
- Guide replay Start does not reset walkthrough completion or tip state
- replay returns to Guide
- page 1 shows Skip and Next; no Back is present
- page 2 shows Skip, Back, and Next
- page 3 shows Skip, Back, and Next
- page 4 shows Back and Start; no Skip and no Next are present
- Skip on pages 1, 2, and 3 exits the walkthrough
- Back on page 2 returns to page 1
- Back on page 3 returns to page 2
- Back on page 4 returns to page 3
- Next on page 1 advances to page 2
- Next on page 2 advances to page 3
- Next on page 3 advances to page 4
- Start on page 4 completes the walkthrough
- swipe left advances to the next page
- swipe right returns to the previous page
- swipe does not navigate past page 4 or before page 1
- replay entry uses the same page navigation model as first-run entry
- progress dots advance correctly during button navigation
- progress dots advance correctly during swipe navigation
- progress dots are not interactive
- no walkthrough over permission/rationale/permanent denial states
- no normal camera content or live preview while first-run gate state is loading
- rotation/recomposition does not reopen walkthrough after completion
- process recreation does not reopen walkthrough after completion persists

Tips tests:

- trigger conditions for each initial tip
- dismissal marks seen
- seen tips do not show again
- Learn More is visible, marks seen, and opens Guide topic
- Got it marks seen and dismisses
- approved SameView-style card, pointer, and fade in/out behavior render correctly
- no multiple tips simultaneously
- no immediate chaining
- no tap-outside dismissal
- no auto-dismiss timer

Camera integration tests:

- Reference tip anchors to Reference
- Align tip appears only after reference load and inactivity
- Compare tip appears after first successful capture when not auto-opening
- History tip appears only after stored comparison exists and CameraScreen resumes
- tips do not appear during capture, picker, dialogs, or gestures

Compare integration tests:

- Export tip appears on first eligible CompareScreen open
- Export tip does not appear in fullscreen
- Export tip does not appear while export dropdown or dialogs are open

Marker integration tests:

- Marker tip appears only when marker UI is first opened
- no marker tip during marker drag/loupe

GPS integration tests:

- GPS tip appears only when GPS chip is visible or relevant
- no GPS tip in Settings
- no GPS permission behavior changes

Accessibility tests:

- TalkBack traversal reaches tip content and actions
- focus order remains coherent
- button labels and content descriptions exist

Responsive tests:

- portrait callout placement
- landscape side-rail callout placement
- Guide main phone/tablet column behavior
- Guide detail vertical reading flow on phone and tablet
- Walkthrough phone portrait single-column behavior
- Walkthrough landscape/tablet two-column behavior
- tablet layouts do not stretch full width or collapse to narrow phone columns
- tablet callout fallback/defer behavior when anchor space is insufficient

Regression tests:

- camera lifecycle unchanged
- permission flow unchanged
- compare flow unchanged
- navigation graph remains stable
- DataStore errors fall back safely

## 22. Verification Requirements

Before release:

```text
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
./gradlew assembleRelease
```

Manual verification:

- first install with camera permission grant
- first install with permission denial and later grant
- walkthrough skip/start
- first-run walkthrough does not reappear automatically after completion
- first-run gate loading shows neutral SameView surface without normal camera preview
- rotation and process recreation do not reopen walkthrough after completion
- Guide topic navigation
- Show tips again reset
- Show walkthrough again replay from Guide returns to Guide
- Show walkthrough again does not reset tips or make walkthrough appear on next app start
- Reference, Align, Compare, History, Export, Marker, GPS tips
- portrait phone
- landscape phone
- expanded/tablet layout
- TalkBack traversal

## 23. Implementation Blocks

### Block A: Architecture and persistence foundation

- add guide package
- add ids, registries, repository, DataStore
- add tip controller state machine
- add unit tests

### Block B: Guide screens

- add Guide and GuideDetail routes
- add CameraScreen overflow entry
- add topic list and detail content
- add Show tips again confirmation
- add Show walkthrough again replay entry
- add navigation tests

### Block C: First-run walkthrough

- add fullscreen walkthrough route/gate
- support user-initiated replay from Guide without resetting walkthrough completion
- add shared Getting Started content
- persist completion
- add walkthrough tests

### Block D: Contextual tip infrastructure

- add GuideTipHost
- add anchor model
- add placement/defer rules
- add Got it and Learn More behavior
- add host/controller tests

### Block E: Initial tip integrations

- integrate Reference, Align, Compare, History, Export, Marker, and GPS tips
- keep screen integration signal-based
- add focused integration tests

### Block F: Verification and polish

- run required Gradle verification
- complete responsive and accessibility testing
- perform manual smoke validation
- update implementation notes only if implementation is later approved

## 24. Final Recommendation

Proceed with implementation planning after review.

The approved direction should use a self-contained `guide` package, dedicated guide persistence, registry-based Guide/topic/tip definitions, and small signal-based integration points from CameraScreen and CompareScreen. This keeps onboarding discoverability out of the camera and compare state machines while preserving SameView's calm, camera-first workflow.

## 25. Revision Log

- 2026-07-09 — Walkthrough visual spec aligned with implemented WEBP artwork.
