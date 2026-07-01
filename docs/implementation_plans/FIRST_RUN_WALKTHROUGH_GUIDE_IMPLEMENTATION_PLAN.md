# FIRST_RUN_WALKTHROUGH_GUIDE_IMPLEMENTATION_PLAN

## 1. Document Status

This is an implementation plan only.

No code was changed. No tests were created or edited. No existing specifications were modified.

Source specification: `docs/FIRST_RUN_WALKTHROUGH_GUIDE_V1.md`.

## 2. Source-of-Truth Review

Reviewed source documents:

- `docs/FIRST_RUN_WALKTHROUGH_GUIDE_V1.md`
- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/IMPLEMENTATION_NOTES.md`
- `docs/CAMERA_WORKFLOW_UX_V1.md`
- `docs/SETTINGS_UX_V1.md`
- `docs/ABOUT_SCREEN.md`
- `docs/RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `docs/COMPARE_FLOW_V1.md`
- `docs/ALIGNMENT_POINTS_V1.md`
- `docs/GPS_RECREATION_SYSTEM_V1.md`

Reviewed code areas:

- `app/src/main/java/com/isardomains/sameview/MainActivity.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/CameraViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/GpsGuidanceChip.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/ReferenceMarkerOverlay.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/CompareLibraryScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/settings/SettingsRepository.kt`
- `app/src/main/java/com/isardomains/sameview/ui/settings/SettingsModule.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de/strings.xml`
- existing camera, compare, settings, about, marker, GPS, and repository tests

Findings:

- The app already uses a flat single-Activity Navigation Compose graph in `MainActivity`.
- CameraScreen owns camera permission flow locally. MainActivity owns first-run walkthrough navigation decisions after CameraScreen reports that permission is granted.
- CameraScreen top-right actions currently expose History and Overflow; Overflow currently contains Settings/About in both portrait and landscape paths.
- Adding Guide to Overflow is additive and does not change the stable bottom workflow: Reference / Capture / Compare.
- Settings is not an appropriate home for tip or onboarding toggles; Guide owns Show tips again and Show walkthrough again.
- About is a good pattern for a dedicated fullscreen app-level destination.
- Responsive docs favor centered max-width single-column app-level screens on Expanded, with no side panels.
- CompareScreen exposes the Export icon and local state for fullscreen, export dropdown, overflow menu, and delete dialog. These are necessary for Export tip suppression.
- Marker behavior is governed by `ALIGNMENT_POINTS_V1.md`; marker tips must respect Reference menu state, marker edit mode, marker drag, and loupe visibility.
- GPS guidance is CameraScreen-only and permission/settings controlled; GPS tips must not appear in Settings or trigger permission behavior.
- Existing test patterns already cover DataStore with `PreferenceDataStoreFactory`, Compose `testTag`, Navigation stubs, and `WindowWidthSizeClass`.

Non-blocking differences:

- Older docs mention only Settings/About in Camera overflow; the approved spec explicitly adds Guide and remains compatible with prior Help-like future entries.
- Some implementation notes are historical around Compare export status; current code has the Export dropdown.
- GPS status text differs slightly between older docs and current implementation notes; Guide copy should follow current behavior.
- `ALIGNMENT_POINTS_V1.md` includes German prose but remains authoritative for marker behavior; new visible text still requires English and German string resources.

No blocking conflict was found. Proceed with implementation planning.

## 3. Implementation Principles

Preserve:

- camera lifecycle behavior and CameraX binding
- camera permission flow, rationale, permanent denial, and return-from-settings handling
- Photo Picker and SAF fallback behavior
- overlay drag, pinch, and camera zoom gestures
- capture state, capture tokens, save callbacks, and snackbar behavior
- CompareScreen navigation, fullscreen, export, backup, delete, and rendering behavior
- Navigation Compose contracts and existing routes
- DataStore safety and defaults
- full English/German i18n
- accessibility focus, labels, and TalkBack traversal
- responsive behavior in portrait, landscape, and Expanded/tablet layouts

Implementation rules:

- Keep the feature self-contained under `com.isardomains.sameview.guide` where possible.
- Store Guide/Walkthrough/Tip state in a dedicated guide DataStore, not Settings DataStore.
- CameraScreen and CompareScreen expose signals and anchors only; they do not own seen-state logic.
- MainActivity owns first-run walkthrough navigation decisions.
- CameraScreen continues to own the complete camera permission flow, including initial request, rationale UI, permanent denial UI, and permission re-check on resume.
- CameraScreen exposes a first-run gate signal only after reaching a granted-permission state.
- CameraViewModel must not own walkthrough state or walkthrough navigation.
- GuideRepository remains the source of truth for walkthrough completion state.
- Do not add Settings toggles for onboarding or tips.
- Do not add permissions, analytics, network, cloud behavior, or session metadata.
- Do not implement open UX details without approval.

Responsive rules:

- Guide, Walkthrough, and Contextual Tips must support phone portrait, phone landscape, Expanded layouts, and tablets.
- Large screens should meaningfully use available space while keeping content readable and visually grouped.
- Large screens must not simply stretch phone layouts across the full available width.

## 4. Proposed Package and Module Structure
Preferred package:

`com.isardomains.sameview.guide`

Inside this package:

- `GuideTopicId.kt`: enum or sealed ids for Guide topics.
- `GuideTipId.kt`: enum or sealed ids for contextual tips.
- `GuideTopic.kt`: topic model with id, title string, summary string, and detail content definition.
- `GuideTip.kt`: tip model with id, title/body strings, anchor key, priority, screen scope, and optional Guide topic id.
- `GuideTopicRegistry.kt`: data-driven topic list.
- `GuideTipRegistry.kt`: data-driven tip list.
- `GuideRepository.kt`: only persistence API for walkthrough completion and seen tips.
- `GuideModule.kt`: Hilt module for dedicated DataStore, recommended file `sameview_guide`.
- `GuideRoutes.kt`: route constants/builders for Guide, Guide detail, and Walkthrough entry modes.
- `GuideViewModel.kt`: Guide reset actions and UI events if needed.
- `GuideScreen.kt`: topic list plus separated bottom actions.
- `GuideDetailScreen.kt`: concise visual topic details.
- `WalkthroughScreen.kt`: fullscreen first-run/replay walkthrough UI.
- `WalkthroughContent.kt`: shared Getting Started/walkthrough content definitions.
- `GuideTipController.kt`: eligibility, priority, anti-spam, seen-state, and cooldown logic.
- `GuideTipHost.kt`: anchored callout rendering and actions.
- `GuideTipAnchor.kt`: anchor ids and bounds model.
- `GuideTipSignals.kt`: screen-state signals consumed by the controller.

Outside this package:

- `MainActivity.kt`: add routes, navigation callbacks, first-run gate state observation, and first-run walkthrough navigation decisions.
- `CameraScreen.kt`: add `onOpenGuide`, add Guide overflow item, expose Camera tip signals/anchors, and expose the first-run gate signal only after camera permission is granted.
- `CompareScreen.kt`: expose Export tip signals/anchor and suppression states.
- `strings.xml` and `strings-de.xml`: all visible text and accessibility labels.

Optional outside changes:

- `CameraViewModel.kt` only if a non-walkthrough high-level signal cannot safely be derived in `CameraScreen`; do not store guide seen-state, walkthrough completion, or walkthrough navigation there.
- `CompareLibraryScreen.kt` only if History-tip return eligibility cannot be derived from CameraScreen saved sessions.
- `app/src/main/res/drawable/guide_*.xml` only if approved local visuals are required.

Extensibility:

- New topics require one id, one registry entry, strings, optional local assets, and focused tests.
- New tips require one id, one registry entry, strings, an existing or new signal, and no new persistence key per tip.

## 5. Implementation Blocks

### Block A: Architecture and Persistence Foundation

Create the guide package, ids, models, registries, `GuideRepository`, dedicated DataStore, and unit tests.

Rules:

- Persist `walkthrough_completed: Boolean`, default false.
- Persist `seen_tip_ids`, default empty.
- Unknown stored tip ids are ignored.
- Show tips again clears only seen tips.
- Show walkthrough again is route navigation only and writes no state.
- Do not add `resetWalkthroughCompletion()` in V1.

Tests:

- defaults
- mark walkthrough complete
- mark tip seen
- unknown tip ids ignored
- reset tips does not reset walkthrough
- replay path does not write repository state

### Block B: Guide Screens

Add Guide and GuideDetail routes/screens, Guide overflow entry, Show tips again, Show walkthrough again, confirmation dialog, strings, and navigation tests.

Rules:

- Overflow order is Settings / Guide / About in portrait and landscape.
- Guide is fullscreen and app-level.
- Bottom actions are visually separated from topics and are not topic registry rows.
- Show tips again requires confirmation.
- Show walkthrough again opens Walkthrough immediately and does not reset completion.

Approved Guide main screen structure:

- Guide topic list uses existing SameView visual language.
- Each topic row contains a Material icon, title, one short description line, and chevron.
- Example topic copy pattern: `Reference photos` / `Choose and align photos.`
- Topic rows remain visually distinct from the separated bottom actions.

Approved Guide detail screen structure:

- Detail screens use real screenshots, not walkthrough mockups.
- Detail screens follow this reading/reference structure: title, short introduction, screenshot, 1-2 short sentences, screenshot, 1-2 short sentences, and additional screenshot/text sections if needed.
- Guide detail pages remain reading/reference content.

Approved Guide main screen responsive behavior:

- Phone portrait uses a one-column topic layout.
- Phone landscape keeps a one-column topic layout with a simple reading flow and does not switch to a cramped landscape grid.
- Tablet portrait, tablet landscape, and Expanded layouts use a two-column topic layout.
- Topic cards/rows remain visually grouped and do not stretch a single topic row across the full tablet width.
- Large layouts use available space meaningfully while preserving SameView's calm UI.

Approved Guide detail screen responsive behavior:

- Detail screens keep the same vertical content structure across phone and tablet: title, short introduction, screenshot, 1-2 short sentences, screenshot, 1-2 short sentences, and an additional screenshot plus 1-2 short sentences if needed.
- Phone layouts use a single-column vertical reading flow.
- Tablet and Expanded layouts keep the same vertical content structure with larger max-width and more spacing allowed.
- Detail screens do not use a separate two-column documentation layout, side-panel layout, or wide manual-style layout.
- Guide detail pages are reading/reference content, not workflow presentations.

Tests:

- Guide opens from overflow
- topic list renders
- topic detail navigation and back navigation
- Show tips again confirmation/reset
- Show walkthrough again route
- bottom actions are not normal topics
- Guide main screen uses one-column topic layout on phone portrait and phone landscape
- Guide main screen uses two-column grouped topic layout on tablet/Expanded
- Guide detail screen uses vertical reading flow on phone and tablet/Expanded, with no side-panel or wide manual-style layout

### Block C: First-Run Walkthrough
Add Walkthrough route/gate, `WalkthroughScreen`, shared content, completion persistence, replay behavior, approved Compose mockup visuals, approved responsive layout, strings, and tests.

Approved content:

| Page | Title | Text | Illustration intent |
| --- | --- | --- | --- |
| 1 | Choose a photo | Pick a photo<br>to recreate. | Reference photo selection. |
| 2 | Align the overlay | Move and scale the overlay<br>to match the original photo. | Transparent overlay positioning and scaling. |
| 3 | Capture | Take a new photo from<br>the same position. | Capture after alignment. |
| 4 | Compare | Compare the original<br>and your new photo. | SameView comparison slider. |

Page 2 mockup requirements:

- clearly communicates overlay, positioning, and scaling
- communicates a reference overlay on top of a live camera view
- must not appear as two separate photos, vertically stacked photos, or a before/after comparison

Approved visual direction:

- use simplified SameView-themed mockups built from Compose UI elements
- do not use screenshots, stock illustrations, external artwork, or PNG-based walkthrough illustrations
- visuals communicate the workflow conceptually and do not reproduce exact production UI screens

Approved branding:

- display `SameView` at the top of the walkthrough
- do not require a separate logo treatment
- use existing SameView colors and visual language

Approved responsive layout:

- Compact phone portrait: single-column, vertically centered composition: SameView, mockup, title, description, progress dots, navigation buttons.
- Medium phone landscape: two-column centered composition: SameView, mockup left, content right, progress dots, navigation buttons.
- Expanded tablet portrait/landscape: two-column centered composition: SameView, larger mockup allowed, mockup left, content right, progress dots, navigation buttons.
- Expanded must use available space meaningfully and must not render as a narrow phone-sized column centered on large tablets.
- Expanded/tablet layout must not stretch across the full tablet width.
- Title, description, and button sizes remain broadly consistent with phone layout; increased whitespace is allowed.

Approved progress indicator:

- use progress dots only, for example `● ○ ○ ○`
- do not use step counters, numeric counters, or percentage indicators

Approved navigation buttons:

- Pages 1-3: `Skip` and `Next`
- Page 4: `Back` and `Start`
- use existing SameView button components, styling, typography, shapes, and color system
- do not introduce walkthrough-specific button styling

Approved replay behavior:

- Guide -> Show walkthrough again -> Walkthrough -> Finish or Skip -> return to Guide
- replay does not reset walkthrough completion or contextual tip state

Rules:

- First-run gate appears only after camera permission is granted and only when `walkthrough_completed == false`.
- Walkthrough never overlays permission UI or the live camera preview.
- First-run Skip/Start mark completion true.
- Replay Skip/Start return to Guide and do not reset completion or tip state.

Approved first-run gate architecture:

- MainActivity owns first-run walkthrough navigation decisions.
- CameraScreen continues to own the complete camera permission flow.
- CameraViewModel must not become responsible for walkthrough state or walkthrough navigation.
- GuideRepository remains the source of truth for `walkthrough_completed`.
- Permission ownership stays in CameraScreen because CameraScreen already owns the initial request, rationale UI, permanent denial UI, and permission re-check on resume.
- Walkthrough navigation ownership stays in MainActivity because MainActivity already owns Navigation Compose routing.

Approved first-run flow:

1. App launches normally at the Camera route.
2. CameraScreen handles permission flow exactly as today.
3. No walkthrough logic runs during permission request, rationale state, permanent denial state, or permission re-check state.
4. Only after CameraScreen reaches a granted-permission state, CameraScreen exposes a first-run gate signal to MainActivity.
5. MainActivity evaluates `walkthrough_completed` through GuideRepository.
6. If `walkthrough_completed == false`, MainActivity navigates to `Walkthrough(entry=first_run)`.
7. If `walkthrough_completed == true`, normal CameraScreen content renders.

Approved loading behavior:

- Walkthrough completion state is loading-aware.
- Do not assume `false` before DataStore loading completes.
- While gate state is loading, CameraScreen must not render normal camera content.
- While gate state is loading, CameraScreen must not bind CameraX preview.
- While gate state is loading, CameraScreen renders a neutral SameView surface only.
- This prevents first-start camera preview flicker, CameraX startup immediately before walkthrough navigation, and incorrect walkthrough navigation caused by DataStore loading latency.

Approved first-run completion behavior:

- `Walkthrough(entry=first_run)` Skip marks `walkthrough_completed = true` and returns to CameraScreen.
- `Walkthrough(entry=first_run)` Start marks `walkthrough_completed = true` and returns to CameraScreen.
- After completion, walkthrough must never auto-open again.
- Rotation must not reopen walkthrough.
- Process recreation must not reopen walkthrough after completion persists.

Approved replay flow:

- Guide -> Show walkthrough again -> `Walkthrough(entry=replay)` -> Skip or Start -> return to Guide.
- Replay does not reset `walkthrough_completed`.
- Replay does not modify seen tips.
- Replay does not clear Guide state.

Rejected alternatives:

- CameraScreen-owned walkthrough gate is rejected because onboarding state would couple to the camera workflow and navigation policy would leak into CameraScreen.
- Separate splash/start destination is rejected because permission state currently belongs to CameraScreen and a splash-like gate would duplicate permission state handling.
- Pure MainActivity gate without a CameraScreen permission signal is rejected because MainActivity cannot safely distinguish the permission dialog, rationale UI, permanent denial UI, and granted-state timing.
- Navigation side-effect after rendering camera content is rejected because it causes camera preview flicker, unnecessary CameraX startup, and violates "before first normal CameraScreen use".

Tests:

- first-run visibility after permission
- MainActivity owns first-run walkthrough navigation decisions
- CameraScreen emits the first-run gate signal only after permission is granted
- no walkthrough logic during permission request, rationale, permanent denial, or permission re-check state
- gate state loading renders only neutral SameView surface
- gate state loading does not bind CameraX preview
- no display over permission/rationale/permanent denial
- all four pages render with approved titles, descriptions, mockups, and progress dots
- Next advances pages 1-3
- Back returns correctly on page 4
- Skip completes/exits correctly
- Start completes correctly
- not shown again after completion
- rotation/recomposition does not reopen first-run walkthrough after completion
- process recreation does not reopen first-run walkthrough after completion persistence loads
- replay launches from Guide, returns to Guide, and does not reset completion or tips
- Compact, Medium, and Expanded layouts match approved structure
- tablet layout does not render as a narrow phone-width column
- tablet layout does not stretch across the full available width
- mockups render without screenshot, PNG, external artwork, or stock-asset dependencies
- TalkBack traversal, focus order, button labels, and progress indicator accessibility

### Block D: Contextual Tip Infrastructure

Add `GuideTipController`, `GuideTipHost`, anchors, signals, placement/defer logic, Got it, Learn more support, and tests.

Rules:

- One visible tip maximum.
- No tap-outside dismissal.
- No auto-dismiss timer.
- Learn more is visible in V1.
- Tips use `Learn more` and `Got it` actions.
- Learn more opens the matching Guide topic and marks the tip seen.
- Got it marks the tip seen and dismisses it.
- Tips use a small SameView-style card with title, one short sentence, Learn more, and Got it.
- Tip cards use existing SameView surfaces, colors, typography, and spacing principles.
- Tips do not introduce a separate onboarding design language.
- Tips use a small pointer/anchor indicator.
- Tips animate with fade in and fade out only; do not use pulse, bounce, or coach-mark animation.
- Unsafe or missing anchors defer.
- Tips defer during capture, permission, picker, dialogs, active gestures, rendering/export progress, and fullscreen Compare.
- Tips stay visually connected to their target UI element and the target remains visible while the tip is shown.
- Phone portrait places tips above or below the target depending on safe space.
- Phone landscape places tips beside the target when practical, otherwise above or below.
- Tablet/Expanded layouts keep tips close to the target, prefer side placement when it keeps the target visible and visually connected, and never place tips far from the target just because more space is available.
- If no safe connected placement exists, defer the tip instead of showing a badly placed or disconnected tip.

Tests:

- priority and anti-spam
- Got it marks seen
- Learn more marks seen and navigates
- no multiple tips
- no chaining
- missing/unsafe anchors defer
- phone portrait placement above/below target
- phone landscape side placement when safe, otherwise above/below
- tablet/Expanded placement remains close to target
- target remains visible while tip is shown

### Block E: Initial Contextual Tip Integrations
Integrate and test:

- Reference tip: after walkthrough completion or first normal CameraScreen entry, anchored to Reference.
- Align tip: after reference load plus 2-3 seconds inactivity, no gestures or marker edit.
- Compare tip: after first successful capture with valid compare input, unless auto-open navigates away.
- History tip: after at least one stored session exists and CameraScreen resumes.
- Export tip: first eligible CompareScreen open, anchored to Export.
- Marker tip: when marker UI is first opened, not during drag/loupe.
- GPS tip: only when GPS chip is visible/relevant, never in Settings.

Tests:

- focused camera, compare, marker, GPS, and responsive placement tests
- blockers for capture, picker, dialogs, permission, gestures, fullscreen, menus, and loupe

### Block F: Final Verification and Documentation Update

Run full verification, complete manual smoke validation, and update `docs/IMPLEMENTATION_NOTES.md` only after successful implementation.

Required commands:

```text
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
./gradlew assembleRelease
```

## 6. Remaining Open Decisions

All product and UX decisions are approved and implementation-ready, including Guide, Walkthrough, Contextual Tip, responsive, replay, visual, content, progress, navigation, Learn more, pointer, animation, and first-run gate architecture decisions.

Only these engineering conventions remain unresolved:

1. String key convention

Recommended:

- `guide_`
- `walkthrough_`
- `guide_tip_`

2. Test split

Recommended unit tests:

- repository logic
- controller logic
- persistence logic

Recommended instrumentation tests:

- Compose UI
- navigation
- walkthrough
- guide screens
- contextual tips
- responsive behavior

## 7. File Impact EstimateExpected files, not a final exact list.

Block A create:

- `app/src/main/java/com/isardomains/sameview/guide/GuideTopicId.kt`
- `app/src/main/java/com/isardomains/sameview/guide/GuideTipId.kt`
- `app/src/main/java/com/isardomains/sameview/guide/GuideTopic.kt`
- `app/src/main/java/com/isardomains/sameview/guide/GuideTip.kt`
- `app/src/main/java/com/isardomains/sameview/guide/GuideTopicRegistry.kt`
- `app/src/main/java/com/isardomains/sameview/guide/GuideTipRegistry.kt`
- `app/src/main/java/com/isardomains/sameview/guide/GuideRepository.kt`
- `app/src/main/java/com/isardomains/sameview/guide/GuideModule.kt`
- `app/src/test/java/com/isardomains/sameview/guide/GuideRepositoryTest.kt`

Block B create/modify:

- `app/src/main/java/com/isardomains/sameview/guide/GuideRoutes.kt`
- `app/src/main/java/com/isardomains/sameview/guide/GuideViewModel.kt`
- `app/src/main/java/com/isardomains/sameview/guide/GuideScreen.kt`
- `app/src/main/java/com/isardomains/sameview/guide/GuideDetailScreen.kt`
- `app/src/androidTest/java/com/isardomains/sameview/guide/GuideScreenTest.kt`
- `app/src/androidTest/java/com/isardomains/sameview/guide/GuideNavigationTest.kt`
- `app/src/main/java/com/isardomains/sameview/MainActivity.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de/strings.xml`
- `app/src/androidTest/java/com/isardomains/sameview/ui/camera/CameraTopRightNavigationTest.kt`

Block C create/modify:

- `app/src/main/java/com/isardomains/sameview/guide/WalkthroughScreen.kt`
- `app/src/main/java/com/isardomains/sameview/guide/WalkthroughContent.kt`
- `app/src/androidTest/java/com/isardomains/sameview/guide/WalkthroughScreenTest.kt`
- `app/src/main/java/com/isardomains/sameview/MainActivity.kt`
- `app/src/main/java/com/isardomains/sameview/guide/GuideRoutes.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de/strings.xml`

Block D create/modify:

- `app/src/main/java/com/isardomains/sameview/guide/GuideTipController.kt`
- `app/src/main/java/com/isardomains/sameview/guide/GuideTipHost.kt`
- `app/src/main/java/com/isardomains/sameview/guide/GuideTipAnchor.kt`
- `app/src/main/java/com/isardomains/sameview/guide/GuideTipSignals.kt`
- `app/src/test/java/com/isardomains/sameview/guide/GuideTipControllerTest.kt`
- `app/src/androidTest/java/com/isardomains/sameview/guide/GuideTipHostTest.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de/strings.xml`

Block E create/modify:

- `app/src/androidTest/java/com/isardomains/sameview/guide/CameraGuideTipIntegrationTest.kt`
- `app/src/androidTest/java/com/isardomains/sameview/guide/CompareGuideTipIntegrationTest.kt`
- `app/src/androidTest/java/com/isardomains/sameview/guide/MarkerGuideTipIntegrationTest.kt` if needed
- `app/src/androidTest/java/com/isardomains/sameview/guide/GpsGuideTipIntegrationTest.kt` if needed
- `app/src/main/java/com/isardomains/sameview/MainActivity.kt`
- `app/src/main/java/com/isardomains/sameview/ui/camera/CameraScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de/strings.xml`

Block F modify after successful implementation only:

- `docs/IMPLEMENTATION_NOTES.md`

## 8. Test Strategy

Persistence:

- walkthrough completion persistence
- tip seen persistence
- unknown tip id handling
- Show tips again resets seen tips only
- Show tips again does not reset walkthrough completion
- Show walkthrough again does not write/reset completion
- Show walkthrough again does not reset tip state

Guide:

- Guide opens from overflow
- topic list renders
- topic detail navigation
- back navigation
- Show tips again confirmation
- Show walkthrough again route
- bottom actions are separate from normal topics
- Guide main screen uses one-column topic layout on phone portrait
- Guide main screen uses one-column topic layout on phone landscape
- Guide main screen uses two-column topic layout on Expanded/tablet
- Guide main screen keeps topic cards/rows visually grouped without stretching a single row across full tablet width
- Guide detail screen uses phone vertical reading flow
- Guide detail screen uses tablet vertical reading flow with larger max-width and spacing
- Guide detail screen has no side-panel, two-column documentation, or wide manual-style layout

Walkthrough:

- first-run visibility after permission
- MainActivity owns first-run navigation decision after CameraScreen permission-granted signal
- CameraScreen permission request/rationale/permanent denial/re-check states do not run walkthrough logic
- loading walkthrough completion state renders neutral SameView surface only
- loading walkthrough completion state does not bind CameraX preview
- first-run walkthrough navigation is one-shot and does not loop on recomposition
- skip completion
- start completion
- not shown again after completion
- rotation does not reopen walkthrough after completion
- process recreation does not reopen walkthrough after completion persists
- no walkthrough over permission/rationale/permanent denial
- all four pages render correctly
- correct titles displayed: Choose a photo, Align the overlay, Capture, Compare
- correct descriptions displayed on all four pages
- progress dots render and update correctly on page changes
- Compose mockups render for all pages
- Page 2 mockup clearly communicates overlay positioning/scaling over a live camera view, not two separate photos or a before/after comparison
- no screenshot dependencies
- no external artwork dependencies
- no PNG-based walkthrough illustration dependencies
- Next advances correctly on pages 1-3
- Back returns correctly on page 4
- Skip completes/exits correctly
- Start completes correctly
- replay from Guide launches walkthrough
- replay returns to Guide
- replay from Guide does not reset completion state
- replay from Guide does not reset tips
- Compact layout matches approved single-column structure
- Medium layout matches approved two-column structure
- Expanded layout matches approved two-column structure
- tablet layout does not render as a narrow phone-width column
- tablet layout does not stretch across the full available width
- TalkBack traversal
- focus order
- button labels
- progress indicator accessibility

Tips:

- trigger eligibility
- Got it marks seen
- Learn more is visible, marks seen, and navigates
- Got it marks seen and dismisses
- tip card uses approved SameView-style visual direction
- small pointer/anchor indicator renders while target remains visible
- fade in/out only; no pulse, bounce, or coach-mark animation
- no tap-outside dismissal
- no auto-dismiss
- no multiple tips simultaneously
- no chaining
- unsafe/missing anchors defer
- phone portrait places tips above/below target depending on safe space
- phone landscape places tips beside target when safe, otherwise above/below
- tablet/Expanded placement remains close to the target
- unsafe placement defers the tip
- target remains visible while tip is shown

Camera integration:

- Reference tip
- Align tip after idle reference load
- Compare tip after first capture
- History tip after stored session exists
- no tips during capture, picker, dialog, permission, active gesture, marker edit, marker drag, or loupe

Compare integration:

- Export tip on eligible CompareScreen
- no Export tip in fullscreen
- no Export tip while export menu, overflow menu, delete dialog, or backup/export progress is active

Marker integration:

- Marker tip only when marker UI opens
- no Marker tip during drag/loupe
- marker state unchanged by tips

GPS integration:

- GPS tip only when GPS chip is relevant
- no GPS tip in Settings
- no GPS permission behavior changes

Accessibility:

- TalkBack traversal
- focus order
- button labels
- content descriptions

Responsive:

- Guide main phone portrait one-column layout
- Guide main phone landscape one-column layout, not a cramped landscape grid
- Guide main Expanded/tablet two-column topic layout with grouped rows/cards
- Guide detail phone vertical reading flow
- Guide detail tablet vertical reading flow with max-width and spacing
- Guide detail has no side-panel or wide manual-style layout
- Walkthrough Compact single-column layout
- Walkthrough Medium two-column layout
- Walkthrough Expanded/tablet two-column layout
- Walkthrough tablet layout does not stretch full width or collapse to a narrow phone column
- Contextual tip portrait placement above/below target
- Contextual tip landscape side placement when safe
- Contextual tip tablet placement remains close to target
- unsafe contextual tip placement defers
- target remains visible while contextual tip is shown

Regression:
- camera lifecycle unaffected
- CameraViewModel does not own walkthrough state or navigation
- no CameraX startup before first-run walkthrough navigation when gate state is loading or incomplete
- no first-start camera preview flicker before walkthrough
- permission flow unaffected
- Photo Picker unaffected
- compare flow unaffected
- navigation unaffected
- settings DataStore unaffected
- guide DataStore safety

## 9. Verification Commands

Expected after implementation:

```text
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
./gradlew assembleRelease
```

Manual/real-device validation required:

- fresh install with permission grant
- fresh install shows no normal camera preview before first-run walkthrough when walkthrough is incomplete
- gate loading state shows only a neutral SameView surface and does not bind CameraX preview
- permission denial/rationale/permanent denial and later grant
- first-run walkthrough Skip/Start marks completion and returns to CameraScreen
- rotation/recomposition after first-run completion does not reopen walkthrough
- process recreation after persisted completion does not reopen walkthrough
- walkthrough replay from Guide returns to Guide
- all four walkthrough pages show approved title and description
- progress dots update correctly
- navigation buttons match approved layout: pages 1-3 Skip/Next, page 4 Back/Start
- Guide main screen is one-column on phone portrait
- Guide main screen remains one-column on phone landscape and does not switch to a cramped grid
- Guide main screen is two-column on Expanded/tablet with grouped topic rows/cards that do not stretch full width
- Guide detail screen keeps vertical reading flow on phone
- Guide detail screen keeps vertical reading flow on tablet/Expanded with larger max-width and spacing
- Guide detail screen has no side-panel, two-column documentation, or wide manual-style layout
- Compact phone portrait walkthrough layout is single-column and vertically centered
- Medium phone landscape walkthrough layout is two-column with mockup left/content right
- Expanded/tablet walkthrough layout is two-column and not a narrow phone-width column
- Expanded/tablet walkthrough layout does not stretch across the full tablet width
- Compose mockups render with no screenshots, external artwork, or PNG walkthrough illustrations
- Show tips again reset
- Reference, Align, Compare, History, Export, Marker, and GPS tips
- contextual tips place above/below targets on phone portrait when safe
- contextual tips prefer side placement on phone landscape when safe
- contextual tips remain close to targets on tablet/Expanded
- unsafe contextual tip placement defers the tip
- contextual tip target remains visible while the tip is shown
- marker drag/loupe suppression
- Compare fullscreen/export menu suppression
- GPS chip relevance and no Settings tip
- TalkBack traversal, focus order, button labels, and progress indicator accessibility

## 10. Risk Register

| Risk | Mitigation |
| --- | --- |
| CameraScreen lifecycle | Keep Guide and Walkthrough as routes; suppress normal CameraScreen content and CameraX binding while first-run gate state is loading or incomplete. |
| Permission flow ordering | CameraScreen keeps full permission ownership; expose first-run gate signal only after granted state; test request, rationale, permanent denial, resume re-check, and later grant. |
| DataStore defaults/loading | Safe defaults, catch read failures, ignore unknown ids, and never treat loading as incomplete before DataStore emits. |
| Walkthrough gating | MainActivity owns first-run navigation; GuideRepository owns completion state; use loading-aware state; separate first-run entry from replay entry; test replay non-reset. |
| Navigation replay from Guide | Encode `entry=first_run` vs `entry=replay` explicitly; first-run returns to CameraScreen after marking complete, replay returns to Guide without writing completion or tip state. |
| Navigation/recomposition loops | Trigger first-run navigation as a one-shot route-aware side effect from MainActivity after the CameraScreen granted signal and loaded completion state. |
| App start flicker | Render only a neutral SameView surface while gate state is loading or first-run walkthrough navigation is pending. |
| Process recreation | Persist completion before returning from first-run walkthrough; restored app state must not auto-open after completion loads true. |
| Anchored callout placement | Use anchor bounds, safe insets, blocked regions, responsive side/above/below placement, and defer fallback when no safe connected placement exists. |
| Overlay gestures | Track interaction blockers and require idle time before Align tip. |
| Capture state | Suppress tips while capture/save feedback is active. |
| Compare fullscreen/export state | Expose menu/dialog/fullscreen/progress signals and suppress Export tip. |
| Marker drag/loupe interaction | Suppress Marker tip while edit interactions or loupe are active. |
| GPS chip relevance | Show only when chip is visible/relevant; never in Settings or permission flow. |
| Large-screen over-stretching | Use max widths/grouped layouts so phone layouts do not stretch across full tablet width. |
| Guide detail layout drift | Keep vertical reading/reference structure on tablet; do not add side panels or wide manual-style layouts. |
| Disconnected contextual tips | Keep tips close to targets, preserve target visibility, and defer when no safe connected placement exists. |
| Accessibility focus | Add semantics tests and manual TalkBack validation. |
| i18n completeness | Add English and German strings in the same blocks; review before release. |
| Test flakiness | Keep timing logic unit-testable and use deterministic fake signals. |
| Documentation drift | Update `IMPLEMENTATION_NOTES.md` only after successful implementation. |

## 11. Final Recommendation

Implementation should proceed. No blocking conflict was found between the approved spec, reviewed source-of-truth documents, and current codebase.

Implement Block A first. It establishes the self-contained guide package, central persistence, registries, and tests before any CameraScreen or CompareScreen integration exists.

All product and UX decisions are approved and implementation-ready. Before code starts, resolve or accept the remaining engineering conventions in Section 6.

After those are resolved or accepted, implement sequentially: Block A, Block B, Block C, Block D, Block E, Block F.