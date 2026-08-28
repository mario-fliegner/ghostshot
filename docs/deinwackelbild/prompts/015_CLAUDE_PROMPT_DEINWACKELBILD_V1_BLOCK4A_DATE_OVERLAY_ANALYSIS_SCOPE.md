# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 4A: DATE OVERLAY — ANALYSIS & SCOPE CONFIRMATION ONLY

## Role

You are working in the existing SameView Android repository.

DeinWackelbild V1 Blocks 1–3 are complete and committed.

This prompt covers **Implementation Block 4 only** and follows the SameView strict workflow:

1. analysis only;
2. scope confirmation;
3. STOP and wait for explicit approval;
4. no implementation yet.

Do not write code.
Do not modify files.
Do not commit.
Do not begin Block 5.

---

# 1. Block 4 Objective

Add the optional Date overlay behavior to the existing Wackelbild preview.

Approved V1 behavior for this block:

- add a `Datum anzeigen` toggle;
- default = OFF;
- the toggle is available only if a usable Reference date exists;
- missing Reference date does **not** block the Wackelbild feature;
- when enabled:
  - Reference image shows the Reference date;
  - Capture image shows the Capture date;
- date appears bottom-right;
- white text;
- dark SameView CI background;
- rounded corners;
- no shadow;
- no outline;
- no drag/reposition/resize/editing;
- no title;
- no location;
- no branding;
- date display updates live as the visible image switches;
- preview overlay is runtime-only and must not modify any persisted image;
- date format follows current app locale/language;
- Reference date precision must be preserved exactly;
- no metadata editing from this screen.

Block 4 is **preview/UI only**.

Do **not** render the date into transfer JPEGs yet. That belongs to the later print/HQ block.

---

# 2. Authoritative Inputs

Read fully before analysis:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

Also inspect relevant authoritative docs:

- `docs/SESSION_METADATA_V1.md`
- `docs/DE_LOCALIZATION_UX_REWORK_V1.md`
- `docs/RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `docs/COMPARE_SESSION_RENDERING_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Inspect current implementation of:

- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt`
- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`
- current metadata parsing used by Compare/Edit/Share flows
- current date formatting helpers
- current Reference date precision handling
- current Capture timestamp/date handling
- current SameView color/typography/corner-radius tokens
- current Wackelbild tests.

If repository reality differs materially from the approved product spec or implementation plan, report the conflict and stop rather than silently changing behavior.

---

# 3. Repository Baseline

Before analysis, report:

- current branch;
- current HEAD;
- `git status --short`;
- any unrelated untracked/modified files.

Do not touch unrelated state.

---

# 4. Product Constraints That Apply Now

Preserve exactly:

- toggle label: `Datum anzeigen`
- default OFF
- temporary state only
- no DataStore
- no session metadata write
- no persisted image mutation
- no metadata editor link/button
- Reference date required to enable the toggle
- missing Reference date only disables the toggle
- ordering/navigation/preview still otherwise usable
- Reference date must preserve known precision:
  - year only
  - year + month
  - full date
- Capture date is the actual Capture date
- formatting follows current app locale
- Reference preview shows Reference date
- Capture preview shows Capture date
- bottom-right only
- same relative position for both images
- white text
- dark SameView CI background
- rounded corners
- no shadow
- no outline
- no user-configurable style
- no drag/edit/resize
- no title/location/branding overlay
- no transfer-image rendering yet

Do not alter these decisions.

---

# 5. Required Repository Analysis

## 5.1 Current metadata model

Confirm exactly how current sessions represent:

- Reference date
- Reference date source
- Reference date precision, if stored explicitly or inferable
- Capture timestamp/date
- session metadata file version relevant to current release

Do not assume full-day precision if the repository stores partial dates.

Identify the exact data structures/functions already used by:

- Compare labels
- Edit Session metadata
- Share Image metadata/date display

## 5.2 Reference-date precision

This is critical.

Determine exactly how the code distinguishes:

- `YYYY`
- `YYYY-MM`
- `YYYY-MM-DD`

If current storage only uses a string field, confirm the exact inference rules.

Do not invent missing month/day values.

Plan the smallest reusable representation for Block 4.

If an existing domain/helper already represents date precision, reuse it.

## 5.3 Capture date

Confirm where the Capture date comes from.

Determine whether Block 4 should derive it from:

- `capture.timestampMs`
- another metadata field
- filesystem timestamp only if current authoritative metadata contract says so

Use the current metadata contract, not assumptions.

## 5.4 Locale-aware formatting

Find the current SameView date formatting helpers.

Determine:

- whether locale comes from `Locale.getDefault()`, Compose configuration, or app-specific locale handling
- current German formatting
- current English formatting
- how year-only and month-year are formatted

Do not add a new date-formatting framework if current helpers already exist.

## 5.5 ViewModel state

Determine the smallest new Block-4 state.

Likely needs:

- `dateOverlayEnabled`
- `isDateOverlayAvailable`
- formatted Reference date
- formatted Capture date

But confirm against current architecture.

State must be temporary and not SavedStateHandle-persisted.

Do not add order/network state.

## 5.6 Metadata loading

Inspect whether `WackelbildViewModel` currently reads no metadata.

Plan the smallest safe metadata load required for date-only state.

Do not parse unrelated title/location data.

Do not widen scope into a generic session metadata repository unless one already exists and is clearly reusable.

If a narrow read of `metadata.json` is required, identify exactly which fields are read.

## 5.7 Toggle placement

Confirm where in current `WackelbildScreen.kt` the toggle should go.

Approved layout:

- directly below the preview
- simple SameView-style row
- toggle on the right
- no card
- no separate Options section
- interaction hint follows below

Check how the current Block-3 preview/hint layout is structured and identify the minimal rearrangement.

## 5.8 Disabled-state supporting text

When no usable Reference date exists:

- toggle disabled
- show:
  - German: `Referenzdatum hinzufügen, um das Datum anzuzeigen.`
  - English equivalent from the implementation plan / localization recommendation

Analyze the current Compose pattern for disabled toggle + supporting text.

Do not add navigation to metadata editing.

## 5.9 Date badge visual tokens

Reconfirm current actual tokens/patterns for:

- dark SameView surface color
- white text
- typography
- padding
- rounded corner precedent

The approved spec already identified `SameViewAppSurface` / `#17202F` as the dark surface token.

No canonical bitmap badge corner radius exists.

For Block 4 preview-only rendering, choose the smallest consistent Compose radius/padding based on current UI precedent.

Do not invent a user setting.

## 5.10 Badge geometry / proportionality

The preview date badge should represent the same relative positioning concept later used by print rendering.

For Block 4, determine:

- bottom-right margin
- internal horizontal/vertical padding
- font sizing behavior
- whether the badge scales with preview size or uses fixed dp/sp at screen preview level

Do not design HQ bitmap scaling yet, but keep the preview geometry suitable for later WYSIWYG mapping.

If exact proportional print mapping belongs to Block 5, say so explicitly.

## 5.11 Image switching

Confirm the overlay updates immediately with current `visibleImage`.

No image-state label should be added.

The date badge itself is the only visible per-image metadata.

## 5.12 Accessibility

Analyze:

- toggle semantics
- disabled-state semantics
- badge content description / whether it should merge with preview semantics
- no additional visible controls

Keep this minimal.

---

# 6. Strict Block 4 Scope

Likely candidates:

- `WackelbildScreen.kt`
- `WackelbildViewModel.kt`
- possibly a small date formatter/helper if no existing reusable helper exists
- English/German strings
- Wackelbild screen tests
- ViewModel/date-format unit tests
- `docs/IMPLEMENTATION_NOTES.md`

Do not assume exact files blindly.

Confirm exact files from repository evidence.

---

# 7. Forbidden in Block 4

Do not include:

- HQ print rendering
- date rendering into bitmap/JPEG
- `ShareImageRenderer` changes
- `CaptionRenderer` changes unless repository evidence shows a tiny existing helper can be reused read-only
- temp files
- network/API
- OkHttp
- partner key
- AndroidManifest
- Gradle
- `INTERNET`
- Custom Tabs
- order CTA
- transfer disclosure
- loading/upload state
- fallback-quality warning
- release/privacy changes
- sensor-threshold changes
- swipe logic changes
- navigation changes
- metadata editing
- title/location overlays
- branding overlays

If any forbidden item appears necessary, stop and report why.

---

# 8. Missing Reference Date Behavior

This must be explicit.

If Reference date is unavailable/unusable:

- `Datum anzeigen` is disabled
- supporting text is shown
- preview otherwise works normally
- tilt/swipe still works
- no error dialog
- no metadata editor link
- no fallback screen
- no blocking behavior

Confirm how this maps to current UI/state architecture.

---

# 9. Capture Date Edge Cases

Analyze what happens if:

- Reference date exists
- Capture timestamp/date is unexpectedly missing or invalid

The spec assumes the Capture date exists for current sessions.

Determine whether current metadata contract guarantees this.

If guaranteed, document that.
If not, define the minimal defensive UI behavior without inventing values.

Do not silently substitute current device date.

---

# 10. Regression-Safety Review

Explicitly assess risk to:

- Block-3 tilt/swipe behavior
- sensor lifecycle
- preview image loading/fallback
- portrait/landscape aspect ratio
- accessibility action
- scroll layout
- existing tests

Block 4 must not alter sensor thresholds or swipe arbitration.

---

# 11. STEP 2 — Required Scope Confirmation Output

Return exactly these sections:

## 1. Repository Baseline

- branch
- HEAD
- working-tree state

## 2. Current Implementation Evidence

List:

- Wackelbild screen structure
- current ViewModel state
- metadata/date model
- Reference precision representation
- Capture date source
- locale/date formatter precedent
- design-token precedent
- current tests

Use repository-relative paths and line ranges where practical.

## 3. Root Cause / Required Change

Explain why Block 4 requires the identified changes and why nothing broader is needed.

## 4. Files Proposed for Modification

Table:

| File | Modify / Create | Exact change | Why required |
|---|---|---|---|

List **ALL** files intended for Block 4.

No hidden files.

## 5. Files Explicitly NOT Touched

Confirm at minimum:

- `MainActivity.kt`
- `CompareScreen.kt`
- sensor files
- HQ/image renderer files
- Gradle
- AndroidManifest
- network/API files
- DeinWackelbild spec/plan unless a real conflict is found

## 6. Exact Implementation Plan for Block 4

Describe precise edits, but **do not provide code**.

Include:

- metadata/date loading
- precision handling
- locale formatting
- toggle state/default
- disabled behavior
- badge styling/position
- live Reference/Capture date switching
- accessibility
- tests
- documentation update

## 7. Risks

List only real Block-4 risks and mitigations.

## 8. Verification Planned After Implementation

State exact commands/tests to run after approval.

At minimum assess:

- `./gradlew testDebugUnitTest`
- relevant Wackelbild instrumentation tests
- Managed Device task
- `./gradlew assembleDebug`

State whether real-device validation is required for Block 4 and, if so, what visual checks should be done.

## 9. Scope Confirmation

End with exactly:

**BLOCK 4 SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

Then STOP.

---

# Final Rule

This prompt is ANALYSIS + SCOPE CONFIRMATION only.

No code.
No file modifications.
No HQ.
No network.
No Block 5.
No unrelated cleanup.

Wait for explicit approval.
