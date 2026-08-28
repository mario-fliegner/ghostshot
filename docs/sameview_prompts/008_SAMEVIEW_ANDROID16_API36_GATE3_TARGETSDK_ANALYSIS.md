# Gate 3 — Analysis Only: `targetSdk 35 → 36` Runtime Behavior Impact

## Objective

Analyze the impact of raising SameView from:

- `compileSdk = 36`
- `targetSdk = 35`

to:

- `compileSdk = 36`
- `targetSdk = 36`

This task is **analysis only**.

Do **not** modify any file.
Do **not** change `targetSdk`.
Do **not** implement compatibility fixes.
Do **not** add managed devices.
Do **not** update dependencies.
Do **not** update documentation.
Do **not** commit, push, or merge.

The goal is to determine whether SameView can safely activate Android 16 / API 36 target-SDK behavior with a one-line `targetSdk` change, or whether one or more compatibility fixes must be prepared first.

---

## Repository

Repository:

`C:\data\work\privat\git-repos\sameview`

Required branch:

`upgrade/android-16-api-36`

Before analysis:

1. Confirm the active branch is exactly `upgrade/android-16-api-36`.
2. Confirm the working tree is clean.
3. Confirm current HEAD.
4. Confirm:
   - `compileSdk = 36`
   - `targetSdk = 35`
   - `minSdk = 29`
5. Confirm the API-36 accessibility deprecation fix from Gate 2C is already committed.
6. If the working tree is not clean or the branch/baseline differs unexpectedly, STOP and report the exact state.
7. Do not stash, reset, discard, modify, or commit anything.

---

## Source-of-Truth Review — Mandatory

Before analyzing target-SDK behavior, read the relevant project specifications.

At minimum:

- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`
- `CAMERA_WORKFLOW_UX_V1.md`
- `COMPARE_FLOW_V1.md`
- `COMPARE_SESSION_RENDERING_V1.md`
- `RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `SETTINGS_UX_V1.md`
- `GPS_RECREATION_SYSTEM_V1.md`
- `SESSION_METADATA_V1.md`
- `SESSION_ORIGINALS_V1.md`
- `SESSION_ORIGINALS_PRIVACY_V1.md`
- `SESSION_BACKUP_EXPORT_V1.md`
- `SHARE_COMPARISON_IMAGE_V1.md`
- `SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md`
- `VIDEO_EXPORT_V1.md`
- `FIRST_RUN_WALKTHROUGH_GUIDE_V1.md`
- `GUIDE_TIPS_UX_V1.md`
- `RELEASE_HARDENING_AUDIT_V2.md`

Also inspect any newer spec or addendum directly relevant to:
- Hosted Comparison
- app networking boundary
- permissions
- navigation/back handling
- responsive layouts
- accessibility
- release behavior

If code and docs conflict:
- docs win unless explicitly superseded
- report the inconsistency
- do not silently choose the code behavior

---

## Current Verified Baseline

The following is already verified and should be treated as the starting point:

- `compileSdk = 36`
- `targetSdk = 35`
- `minSdk = 29`
- 828/828 unit tests green
- debug build green
- lint green with 0 errors
- API-36 `announceForAccessibility()` deprecations have been migrated to Compose `LiveRegionMode.Polite`
- targeted camera overlay instrumentation tests green
- real-device TalkBack verification passed for both affected warning bubbles, including rapid repeat activation
- current branch is dedicated to the Android 16 / API 36 migration

Do not reopen already-closed Gate 2 work unless it is directly relevant evidence for a targetSdk-36 behavior change.

---

## Core Question

Answer this question precisely:

> If `targetSdk` is changed from 35 to 36 today, which Android 16 target-SDK-gated behavior changes will actually affect SameView, and what must be changed or tested before that target-SDK bump can be considered safe?

Do not provide a generic Android 16 changelog.

Every finding must be tied to actual SameView code, manifest, tests, or documented behavior.

---

# Required Analysis Areas

## 1. Edge-to-Edge, System Bars, Insets, and Window Behavior

Audit all relevant code for:

- `enableEdgeToEdge()`
- `WindowCompat`
- status bar handling
- navigation bar handling
- `systemBarsPadding()`
- `navigationBarsPadding()`
- `WindowInsets`
- fullscreen content
- dialogs / modal sheets
- landscape controls
- cutouts / notches
- gesture navigation areas
- keyboard/IME interactions where relevant

Inspect at minimum:

- `MainActivity`
- `CameraScreen`
- `CompareScreen`
- `CompareLibraryScreen`
- `SettingsScreen`
- `EditSessionScreen`
- `CreateVideoScreen`
- `ShareComparisonScreen`
- Guide / Walkthrough screens

Determine whether targetSdk 36 introduces any behavior change SameView is not already handling.

### Critical invariants

Do not propose a redesign.

Preserve:
- CameraScreen control placement contracts
- Compare fullscreen behavior
- responsive-layout contracts
- existing system-bar/inset semantics
- no new navigation structure

For every affected screen classify:

- safe as-is
- verification required
- code change required
- unknown / targeted experiment required

---

## 2. Predictive Back / Back Navigation / Gesture Behavior

This is a high-priority area.

Audit:

- all `BackHandler` usages
- Navigation Compose back stack behavior
- `popBackStack()`
- fullscreen Compare back handling
- selection-mode exit behavior
- Edit Session unsaved-change handling
- Create Video rendering/back confirmation behavior
- Walkthrough / Guide navigation
- dialogs and modal sheets
- activity-level back behavior
- manifest flags relevant to predictive back

Determine:

1. Whether targeting API 36 changes predictive-back behavior for this app.
2. Whether SameView currently opts in, opts out, or relies on defaults.
3. Whether existing `BackHandler` logic remains semantically correct.
4. Whether any screen requires predictive-back-specific code or API migration.
5. Whether a manifest change such as `android:enableOnBackInvokedCallback` is relevant, obsolete, harmful, or unnecessary.
6. Whether animation behavior may change even if navigation result remains correct.

Do not implement anything.

### Required screen-by-screen table

At minimum include:
- CameraScreen
- CompareScreen
- CompareLibraryScreen
- EditSessionScreen
- CreateVideoScreen
- ShareComparisonScreen
- SettingsScreen
- Guide/Walkthrough

For each:
- current back contract
- target-36 risk
- required action/test

---

## 3. Camera Lifecycle / Activity Recreation / Rotation

Camera lifecycle is release-critical.

Audit:

- CameraX provider binding
- lifecycle ownership
- bind/unbind paths
- async bind cancellation/guards
- Preview
- ImageCapture
- rotation
- `defaultDisplay.rotation` or related display APIs
- Activity recreation on rotation
- pause/resume/background/foreground
- keep-screen-on behavior
- permission-return flows
- disposal behavior

Determine whether any targetSdk-36 behavior change affects:
- Activity recreation timing
- camera availability
- lifecycle callbacks
- rotation/display APIs
- foreground restrictions

Do not refactor camera code.

If no code change is required, define the exact real-device tests needed to prove it.

---

## 4. Permissions and Privacy

Audit all current permissions and runtime flows:

- CAMERA
- ACCESS_FINE_LOCATION
- ACCESS_COARSE_LOCATION
- ACCESS_MEDIA_LOCATION
- INTERNET if currently introduced by the separately approved Hosted Comparison capability

Confirm:

- no new dangerous permission becomes necessary
- no background location is needed
- no media/storage permission becomes necessary
- Photo Picker behavior remains correct
- precise/approximate location behavior remains correct
- permission-denial flows remain correct
- returning from Settings remains correct

Also assess whether targetSdk 36 changes:
- permission dialogs
- one-time permissions
- partial/approximate permission behavior
- media access behavior
- location behavior

Distinguish:
- Android-16 OS behavior for all apps
- targetSdk-36-gated behavior

Do not conflate them.

---

## 5. MediaStore / Photo Picker / SAF / File Access

Audit:

- capture writes to `Pictures/SameView`
- video writes
- share-image writes
- session originals
- EXIF updates
- `IS_PENDING`
- `RELATIVE_PATH`
- Photo Picker
- SAF fallback
- `MediaStore.setRequireOriginal()`
- content URI lifetime assumptions
- backup export via SAF
- MIME/format handling

Confirm the previously implemented Android 16 Photo Picker / `setRequireOriginal()` mitigation remains correct under targetSdk 36.

Determine if targetSdk 36 introduces any new storage access or URI restrictions that affect SameView.

---

## 6. GPS / Sensors / Foreground Restrictions

Audit:

- `LocationManager`
- `GPS_PROVIDER`
- `NETWORK_PROVIDER`
- sensor listeners
- Compass / bearing logic
- lifecycle activation/deactivation
- location permission checks
- session GPS freeze/write behavior

Confirm:
- GPS remains foreground-only
- no background-location requirement is introduced
- no targetSdk-36 restriction breaks current behavior
- GPS remains isolated from compare rendering and overlay geometry

If verification is needed, define exact real-device scenarios.

---

## 7. Compose / Material / Window Size Class Runtime Behavior

The current project uses Compose Material 3 and the experimental Material 3 Window Size Class API.

Audit whether targetSdk 36 changes runtime behavior relevant to:

- edge-to-edge
- insets
- window metrics
- density / font scaling
- accessibility semantics
- gesture navigation
- size-class calculation
- dialogs/sheets

Do not recommend dependency upgrades unless targetSdk 36 actually requires them.

---

## 8. App Compatibility with Existing Sessions / Upgrades

Assess whether changing only `targetSdk` can affect existing installed users with:

- existing DataStore settings
- existing sessions v2–v6
- GPS metadata
- session originals
- favorites
- branding
- backups
- Hosted Comparison identifiers
- existing MediaStore files

Confirm whether:
- no schema migration is needed
- no storage migration is needed
- no permission reset is expected
- no user data loss is expected

If any upgrade/install behavior may change, flag it explicitly.

---

## 9. Accessibility

Gate 2C fixed the API-36 announcement deprecations.

Audit whether targetSdk 36 introduces any additional accessibility behavior change that affects SameView.

Pay attention to:
- semantics live regions
- content descriptions
- focus
- TalkBack
- edge-to-edge + accessibility focus bounds
- predictive back announcements/animations

Do not reopen unrelated historical accessibility findings unless targetSdk 36 makes them relevant.

---

## 10. Build / Lint / Runtime Flags

Inspect whether targetSdk 36 causes:
- new manifest merge behavior
- new lint checks
- new build warnings
- new runtime compatibility changes
- new platform-enforced restrictions

Identify any relevant Android compatibility-framework changes that can be toggled before the actual targetSdk bump.

If useful, recommend **specific** compatibility-framework experiments that allow target-36 behavior to be tested while `targetSdk` is still 35.

Do not enable them automatically.

---

## 11. Test Coverage Gap Analysis

Inspect current test coverage and classify what must run immediately after `targetSdk = 36`.

At minimum assess:

- unit tests
- Compose UI tests
- instrumentation tests
- Gradle Managed Devices
- real-device tests
- API 29 backward-compatibility coverage
- API 35 coverage
- missing API 36 coverage

### Required decision: API 36 Managed Device

Gate 1/2 intentionally deferred adding `pixel2Api36`.

Determine now whether it should be added:
- before `targetSdk = 36`
- in the same implementation gate
- immediately after the one-line targetSdk bump
- or not at all

Choose one and justify it.

Do not add it in this analysis prompt.

---

# Android Studio Manual Analysis

The user uses Android Studio.

Define what the user should inspect **before implementation** in:

## SDK Upgrade Assistant
`Tools → SDK Upgrade Assistant`

Tell the user:
- which API 36 behavior-change categories are worth screenshotting/reporting
- what not to Apply yet
- what would be useful evidence to compare with this Gate 3 report

## App Inspection / Build tools
If Android Studio offers a compatibility or target API warning directly in Gradle/build files, explain what is relevant.

Do not ask the user to perform broad manual app testing yet unless a compatibility-framework experiment is specifically justified.

---

# Required Classification

For every target-36-relevant finding, use exactly one of:

- **NO CHANGE REQUIRED**
- **VERIFICATION REQUIRED**
- **CHANGE REQUIRED BEFORE TARGET 36**
- **CHANGE REQUIRED AFTER TARGET 36 IF REPRODUCED**
- **UNKNOWN — TARGETED EXPERIMENT REQUIRED**

Do not use vague wording such as “probably fine” without assigning a classification.

---

# Required Migration Decision

At the end, determine which of these is correct:

### Path A — Direct targetSdk bump is safe
The next implementation can be exactly:

`targetSdk = 35 → 36`

with no preparatory production-code change.

### Path B — Preparatory compatibility fix required
One specific target-36 issue must be fixed before changing `targetSdk`.

### Path C — Targeted experiment required first
A compatibility-framework or emulator/real-device experiment must resolve an unknown before touching `targetSdk`.

Choose exactly one.

---

# Required Final Report Structure

Return exactly these sections.

## 1. Branch / Baseline State
- branch
- HEAD
- working tree
- compileSdk
- targetSdk
- minSdk
- Gate 2C commit status

## 2. Source-of-Truth Review
- docs reviewed
- binding constraints
- stale/conflicting statements relevant to targetSdk 36

## 3. Android 16 Target-36 Compatibility Matrix

Table:

| Area | Classification | SameView evidence | Required action |
|---|---|---|---|

## 4. Predictive Back Analysis

Screen-by-screen table:

| Screen | Current back contract | Target-36 risk | Action/test |
|---|---|---|---|

## 5. Edge-to-Edge / Insets Analysis
- app-level
- CameraScreen
- CompareScreen
- remaining screens
- exact risks/tests

## 6. Camera Lifecycle Analysis
- target-36 impact
- required code change or verification
- exact real-device scenarios

## 7. Permissions / Storage / GPS Analysis
Separate subsections:
- permissions
- MediaStore/Photo Picker/SAF
- GPS/sensors
- privacy/offline boundaries

## 8. Existing-User Upgrade Impact
- settings
- sessions
- MediaStore
- permissions
- data/schema compatibility

## 9. Test Gap Analysis
- current automated coverage
- required API 36 coverage
- managed-device decision
- real-device requirements

## 10. Android Studio Checks Before Implementation
Short concrete checklist.

## 11. Required Pre-Target Fixes
List only fixes that genuinely must happen before `targetSdk = 36`.
If none, say:
`None.`

## 12. Recommended Next Migration Path
Choose exactly:
- **PATH A — DIRECT TARGETSDK BUMP**
- **PATH B — PREPARATORY FIX REQUIRED**
- **PATH C — TARGETED EXPERIMENT REQUIRED**

Explain why.

## 13. Gate 3 Verdict

Choose one:

- **READY FOR TARGETSDK SCOPE CONFIRMATION**
- **BLOCKED**

If ready:
- state the exact next step only
- do not implement it

Then STOP.

---

# Final Safety Rules

- Analysis only.
- No tracked file changes.
- No targetSdk change.
- No source changes.
- No test changes.
- No managed-device additions.
- No dependency upgrades.
- No docs updates.
- No warning fixes.
- No commit.
- No push.
- Stop after the report.
