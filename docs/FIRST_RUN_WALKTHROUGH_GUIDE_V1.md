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
- Markers
- GPS guidance
- Compare
- Share comparison image
- Create video
- Favorites
- Backups

Each topic opens a dedicated detail screen.

Guide main screen structure:

- The topic list uses existing SameView visual language.
- Each topic row contains a Material icon, title, one short description line, and chevron.
- Example topic copy pattern: `Reference photos` / `Choose and align photos.`
- Topic rows remain visually distinct from the separated bottom actions.

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

### 8.2 Guide Bottom Actions

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

Illustration intent: comparison result. The park scene showing the winter tree on the left ("Then") and the summer tree on the right ("Now"), divided by a comparison slider positioned at approximately 75% from the left edge. "Then" (left) fills approximately 75% of the illustration; "Now" (right) fills approximately 25%. "Then" and "Now" labels visible inside the illustration.

### Page 2 — Align the overlay

Body copy:

Go back to the original spot. Drag and scale the ghost photo over the live camera view until it lines up.

Two sentences are used on this page only. The first reinforces the location requirement introduced on page 1. The second describes the alignment action. This is the most conceptually complex step in the workflow and warrants two sentences.

Illustration intent: live camera view of the summer park scene. Ghost overlay of the winter tree visible at approximately 50% opacity, deliberately misaligned — shifted visibly off-position relative to the camera feed. Corner handles visible on the overlay to communicate that it can be dragged and scaled. Alignment is clearly incomplete.

This illustration must not appear as two separate photos, vertically stacked photos, or a split-screen editor. It must read as one screen, one camera feed, one translucent layer floating on top.

### Page 3 — Take the shot

Body copy:

Once the overlay lines up, tap the shutter from the same spot.

"From the same spot" briefly reinforces the location requirement without re-explaining it. Page 2 has already established the concept.

Illustration intent: same park camera scene. Alignment is complete. The ghost overlay is at approximately 15% opacity — nearly invisible, settled. The shutter button dominates the lower third of the illustration and is the primary visual focus of this page. A press state is shown on the shutter button. This page must read visually as the "press something" page, clearly distinct from page 2's "move something" illustration. The camera feed and nearly-invisible overlay are background context; the shutter button is foreground content.

### Page 4 — See what changed

Body copy:

Drag the slider to reveal then and now.

"Then and now" in the body copy deliberately echoes page 1's title. The walkthrough opens and closes on the same idea, giving the four-page sequence a sense of arrival.

Illustration intent: comparison result, same park scene. Slider positioned at approximately 22% from the left edge — heavily offset from page 1's 75% position. "Then" (left) fills approximately 22% of the illustration; "Now" (right) fills approximately 78%. "Then" and "Now" labels visible. The slider handle is prominent and clearly off-center toward the left. Despite sharing the same UI pattern as page 1, this illustration must read visually distinct at a glance due to the difference in slider position: page 1 emphasises "Then", page 4 emphasises "Now". The two pages are intentional visual mirrors: both expose approximately 22–25% of the minority side, and both lean strongly and unambiguously toward their respective dominant state.

Walkthrough visual direction:

- Use simplified SameView-themed mockups built from Compose UI elements.
- Do not use screenshots, stock illustrations, external artwork, or PNG-based walkthrough illustrations.
- Visuals communicate the workflow conceptually and do not reproduce exact production UI screens.
- Page 2 must clearly communicate a reference overlay on top of a live camera view, including overlay positioning and scaling.
- Page 2 must not appear as two separate photos, vertically stacked photos, or a before/after comparison.
- Page 3 must be visually distinct from page 2 despite sharing the same camera scene. The shutter button must be the dominant element.

Illustration system — approved theme:

A single park scene with one tree is used across all four walkthrough pages. All illustrations depict the same location from the same angle.

Then state:

- Winter
- Bare trunk and branches visible
- Muted, warm-toned cast

Now state:

- Late summer
- Same trunk, same position
- Full leafy canopy
- Cooler, greener cast

Stable alignment anchors — unchanged between Then and Now:

- Trunk position
- Path edge
- Ground line

These anchors are visible in both states and serve as the visual reference points for overlay alignment in the page 2 illustration. The crown is what changes; the trunk and path do not.

Rationale for the park scene theme:

- Time passage is immediately legible without labels. A bare tree and a full-canopy tree read as different seasons or years without any explanation.
- Simpler to render as Compose shapes than architecture: trunk silhouette, crown silhouette, ground line, path edge.
- Works correctly for all four illustration types: comparison slider (pages 1 and 4), alignment overlay (page 2), and capture moment (page 3).
- Culturally neutral. Not tied to any specific architectural style or geography.
- Memorable. Before/after illustrations typically use buildings; a park scene with a tree is distinctive and avoids a visual category that is overused in this genre.

Visual story narrative:

The four illustrations tell a single coherent story. The user sees the destination first, learns the process in two steps, and returns to the destination.

| Page | Illustration role | Key visual element |
| --- | --- | --- |
| 1 | Outcome — show the reward first | Slider at ~75% from left (Then ~75%, Now ~25%) |
| 2 | Process — alignment step | Ghost overlay misaligned, corner handles visible |
| 3 | Process — capture moment | Overlay near-invisible, shutter button dominant |
| 4 | Outcome revisited — reward arrived | Slider at ~22% toward Now |

The slider positions on pages 1 and 4 are intentionally different. Page 1 shows more "Then" (the past, where the workflow begins). Page 4 shows more "Now" (the result the workflow produces). Despite using the same UI, the two illustrations must be distinguishable at a glance.

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

1. Image slot — the mockup illustration. The slot height is determined by the image aspect ratio and does not change between pages.
2. Text slot — the page title and body copy. This is the only slot that absorbs variation in content length. The slot boundaries are fixed. The title and body copy are top-aligned within the slot. Remaining space accumulates below the body text, between the body and the progress dots slot. Short body text produces more space below it; longer body text reduces that space. The slot itself does not grow or shrink between pages. Layout stability takes precedence over equalized copy lengths across pages.
3. Progress dots slot — the row of progress indicator dots. The slot position is fixed directly below the text slot on every page.
4. Navigation button slot — the row of navigation buttons. The slot position is fixed directly below the progress dots slot on every page.

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

The walkthrough uses simplified SameView-themed mockups built from Compose UI elements. It must not use screenshots, stock illustrations, external artwork, or PNG-based walkthrough illustrations.

Assets must be local app assets. No network images.

## 19. Responsive Layout

Guide, Walkthrough, and Contextual Tips must support:

- phone portrait
- phone landscape
- expanded layouts
- tablets

Large screens should meaningfully use available space while keeping content readable and visually grouped. Large screens must not simply stretch phone layouts across the full available width.

Guide main screen:

- Phone portrait: one-column topic layout.
- Phone landscape: one-column topic layout; keep reading flow simple and do not switch to a cramped landscape grid.
- Tablet portrait and tablet landscape: two-column topic layout.
- Topic cards/rows remain visually grouped and do not stretch a single topic row across the full tablet width.

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
