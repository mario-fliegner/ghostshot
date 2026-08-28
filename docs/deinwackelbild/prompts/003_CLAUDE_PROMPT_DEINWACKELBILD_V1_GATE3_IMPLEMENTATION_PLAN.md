# CLAUDE PROMPT — DEINWACKELBILD V1 — GATE 3: IMPLEMENTATION PLAN ONLY

## Role

You are working in the existing SameView Android repository.

Gate 1 — repository/spec consistency review — is complete.
Gate 2 — product-spec correction + network-governance addendum — is complete.

This Gate 3 authorizes **creation of the implementation plan only**.

Do **not** implement production code.
Do **not** modify AndroidManifest.
Do **not** add `INTERNET`.
Do **not** add dependencies.
Do **not** add API credentials.
Do **not** create or modify tests.
Do **not** contact DeinWackelbild.de.
Do **not** run the real partner API.
Do **not** change product decisions.

Your only implementation artifact in this gate is:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

The plan must be derived from the **actual current repository state**, not from assumptions.

---

# 1. Authoritative Inputs

Read fully before planning:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`

Also inspect all current authoritative documents relevant to the implementation, including at minimum:

- `docs/IMPLEMENTATION_NOTES.md`
- `docs/COMPARE_FLOW_V1.md`
- `docs/COMPARE_SESSION_RENDERING_V1.md`
- `docs/SHARE_COMPARISON_IMAGE_V1.md`
- `docs/SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md`
- `docs/SESSION_ORIGINALS_V1.md`
- `docs/SESSION_ORIGINALS_PRIVACY_V1.md`
- `docs/SESSION_METADATA_V1.md`
- `docs/RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `docs/SETTINGS_UX_V1.md`
- `docs/RELEASE_HARDENING_AUDIT_V2.md`
- any other current specification that directly governs files/classes touched by the planned implementation.

If duplicate or historical files exist, identify which one is current and authoritative before relying on it.

Do not treat historical implementation plans as current authority.

---

# 2. Repository Baseline

Before planning:

1. report current branch;
2. report current HEAD;
3. report `git status --short`;
4. identify any unrelated pre-existing modifications/untracked files;
5. confirm current Android/Gradle baseline;
6. inspect the actual current implementations of the relevant screens, renderers, session/original readers, resources, build files, and manifest.

The implementation plan must match the current code exactly.

---

# 3. Product Contract — Must Not Change

The plan must preserve the approved V1 behavior from:

`docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`

Do not optimize away or reinterpret any of these:

## Entry / navigation

- entry only from an opened saved CompareScreen;
- existing Share icon/menu;
- existing item order preserved;
- divider before `Wackelbild erstellen`;
- dedicated full SameView screen;
- Back navigation;
- no CameraScreen or Library entry.

## Preview

- local preview uses `reference.jpg` + `capture.jpg`;
- Reference initially visible;
- tilt switches directly between Reference/Capture;
- no fade;
- no animation;
- no sound;
- no haptics;
- horizontal swipe toggles regardless of direction;
- swipe remains available with or without sensor;
- sensor/swipe must not fight each other;
- preview remains interactive during preparation/upload.

## Date overlay

- only optional overlay is `Datum anzeigen`;
- default OFF;
- disabled when no usable Reference date exists;
- missing Reference date never blocks ordering;
- no metadata editing from this screen;
- bottom-right only;
- white text;
- dark SameView CI background;
- rounded corners;
- no shadow;
- no outline;
- runtime preview overlay only;
- final print overlay only in newly created temporary transfer JPEGs;
- never mutate session/original files.

## Ordering / network

- opening screen is fully local;
- first network activity only after `Bestelle dein Wackelbild` and any required fallback confirmation;
- spinner + `Wackelbild wird vorbereitet …`;
- no fake progress;
- Back during active transfer prompts for cancellation;
- no WorkManager/foreground-service persistent uploader;
- no persistent handoff state;
- exact API-returned `checkout_url`;
- Android Custom Tab;
- no order-status inference;
- user may start another handoff later;
- no prices/product options in SameView.

## Print / privacy

- best suitable real print quality;
- exact visible crop/content/alignment parity with persisted `reference.jpg` / `capture.jpg`;
- two independent temporary JPEGs;
- identical pixel dimensions;
- no artificial upscaling to approach API limits;
- API limits are upper bounds only;
- fallback to comparison images if HQ originals cannot be used, with approved warning;
- all transfer JPEGs metadata-clean;
- no GPS/EXIF/device/session metadata;
- no `external_reference`;
- no session/comparison ID transfer;
- no sensor-data transfer;
- all temp transfer files cleaned up;
- no MediaStore/gallery/session persistence.

If planning reveals that any of these are impossible with current persisted data, stop and report the conflict instead of silently changing the contract.

---

# 4. Goal of This Gate

Create a complete, implementation-ready plan that answers:

1. What exact files must be created or modified?
2. What exact classes/functions/state models are required?
3. What can be reused unchanged?
4. What shared code should be extracted only if absolutely necessary?
5. What network/client approach is smallest and safest for this repository?
6. How will the two temporary print JPEGs be generated?
7. How will crop parity be preserved?
8. How will metadata-clean output be guaranteed?
9. How will sensor + swipe interaction work?
10. How will lifecycle/cancellation be handled?
11. How will the API key be provisioned without entering source control?
12. What manifest/build/release changes are required?
13. What tests and real-device checks are required?
14. What documentation and Play/privacy follow-up must occur before release?
15. In what minimal sequence should implementation be performed to reduce regression risk?

The plan must be detailed enough that future implementation prompts can execute one block at a time without re-architecting the feature.

---

# 5. Required Repository Analysis

## 5.1 CompareScreen entry point

Inspect the exact current Share/Export dropdown implementation.

Plan:

- exact callback/interface change required;
- exact route/navigation plumbing;
- divider insertion;
- third menu item;
- test updates;
- preservation of existing Share Image / Create Video behavior.

Do not refactor the whole top bar.

## 5.2 New Wackelbild destination

Determine:

- exact package;
- route naming;
- nav-argument model;
- screen composable;
- ViewModel;
- state model;
- Back behavior;
- Custom Tab return behavior;
- responsive structure;
- scroll behavior;
- accessibility semantics.

Prefer the existing ShareComparison/CreateVideo navigation patterns.

Avoid a new navigation architecture.

## 5.3 Tilt provider and lifecycle

Inspect the actual `CompassProvider` and its lifecycle integration.

Plan the smallest new tilt implementation.

The plan must specify:

- exact sensor API to use and why;
- whether to reuse or parallel `CompassProvider`;
- how display rotation is handled;
- how the neutral position is captured;
- how thresholds/hysteresis are represented;
- how swipe override and later sensor resumption work;
- lifecycle registration/unregistration;
- behavior when the sensor is unavailable;
- how no-runtime-permission behavior is verified.

Do not choose sensor thresholds as final numbers unless repository/device evidence supports them.

Mark them for real-device tuning.

## 5.4 Swipe + scroll interaction

Gate 1 found no existing axis-arbitration precedent.

Do not hand-wave this.

Plan exactly how the layout avoids or handles gesture conflict.

Prefer the smallest solution consistent with current SameView patterns.

If the preview can be kept outside the vertically scrollable content to avoid gesture arbitration, document the exact screen structure and why it still satisfies responsive requirements.

If axis arbitration is still needed on Compact-height layouts, say so explicitly.

## 5.5 Date state / formatting

Inspect actual metadata parsing and date formatting.

Plan:

- how to detect Reference date availability;
- how to preserve year-only/month-year/full-date precision;
- how Capture date is obtained;
- locale-aware formatting;
- where date state lives;
- why it remains temporary/non-persistent;
- disabled-state UI/supporting text;
- how Custom Tab return keeps toggle value for the same screen visit;
- how process recreation resets it.

No DataStore.

No session metadata changes.

## 5.6 Date badge rendering

Plan one reusable rendering model for both:

1. live Compose preview overlay;
2. bitmap/JPEG rendering into temporary print files.

The plan must identify:

- current SameView color token;
- white text;
- typography basis;
- proportional sizing model;
- bottom-right margin model;
- rounded-rectangle background;
- no shadow/no outline;
- how preview and print rendering stay visually consistent.

No canonical bitmap-badge corner-radius token exists.

The plan must either:

- choose a concrete value based on the nearest existing SameView visual precedent and justify it; or
- explicitly mark the value as a small design constant to be fixed during the implementation block with visual validation.

Do not invent a user setting.

---

# 6. Print/HQ Pipeline Plan — Critical

This is the most important technical section.

Inspect the actual existing HQ Share Image code and plan the smallest safe reuse.

The plan must trace the exact current functions/classes used for:

- Reference HQ reconstruction;
- Capture HQ reconstruction;
- viewport/crop geometry;
- no-upscale behavior;
- fallback;
- bitmap lifetime/recycling;
- JPEG encoding;
- metadata-clean output.

Then define the new two-file pipeline.

## 6.1 Required output

Two temporary JPEGs:

- image one;
- image two.

Both must:

- be same dimensions;
- same orientation/aspect ratio;
- exact visual crop/alignment parity;
- metadata-clean;
- optional date overlay included;
- comply with DeinWackelbild limits.

## 6.2 Reuse strategy

The plan must clearly classify each candidate existing component as:

- reuse unchanged;
- reuse via new shared helper;
- duplicate narrowly to avoid regression;
- must remain untouched.

Do not refactor shared image code merely for cleanliness.

If extraction from `ShareImageRenderer` risks changing existing Share Image behavior, prefer a narrow additive path unless there is strong evidence a shared helper is safer.

Explicitly discuss regression risk.

## 6.3 Common output dimensions

Define the algorithm precisely.

Requirements:

- determine a single common target width/height;
- bounded by actual genuine source resolution;
- preserve Comparison aspect ratio;
- never upscale weaker source;
- never target API maximums as a goal;
- satisfy <=16,000px side and <=80MP;
- produce files <=20MiB each.

Plan how the byte-size constraint is handled:

- dimension reduction;
- JPEG quality reduction;
- retry loop/step-down strategy;
- minimum acceptable quality if a floor is needed.

Do not leave "compress until small enough" vague.

If exact quality-step constants are product-independent implementation details, propose them in the plan and explain the rationale.

## 6.4 Exact crop parity

Document precisely why the Reference reconstruction and Capture reconstruction preserve the same visible content as `reference.jpg`/`capture.jpg`.

Reference side and Capture side must be explained separately.

If Capture crop parity relies on center-crop assumptions from the existing HQ Share pipeline, cite the current code path and note any edge case requiring tests.

## 6.5 Fallback

Plan exact detection and user-state transition for:

- HQ originals usable;
- HQ original reconstruction failed but `reference.jpg`/`capture.jpg` fallback usable;
- neither valid.

The fallback warning must happen only after the order CTA, before transfer.

The fallback still creates new temporary JPEGs.

No persisted file mutation.

---

# 7. Temporary File Strategy

Plan:

- exact directory (`cacheDir` or other evidence-backed location);
- file naming;
- whether a per-operation subdirectory is useful;
- ownership/lifetime;
- cleanup on success;
- cleanup on cancel;
- cleanup on final error;
- cleanup on screen disposal;
- cleanup on process-loss leftovers;
- whether startup/next-screen-entry cleanup is needed.

Do not place transfer files in:

- MediaStore;
- session directories;
- backup-managed persistent files.

Ensure there is no accidental backup exposure.

---

# 8. Metadata-Clean Guarantee

Plan exactly how the final transfer JPEGs are guaranteed metadata-clean.

Do not rely on vague assumptions.

State:

- whether new `Bitmap.compress()` output alone is sufficient in this code path;
- whether any EXIF writer must never be invoked;
- whether source EXIF orientation has already been applied to pixels;
- how tests will verify absence of GPS/EXIF.

Reference existing Share Image metadata-clean tests/patterns where available.

---

# 9. Network Client Decision

No HTTP client exists today.

You must choose the smallest appropriate client strategy for this repository and justify it.

Compare at least:

- platform `HttpURLConnection`;
- OkHttp;
- Retrofit only if genuinely justified.

The feature needs:

- JSON POST;
- custom headers;
- multipart upload;
- cancellation;
- timeouts;
- retry/backoff;
- response parsing;
- upload of files up to ~20MiB;
- testability.

Do not add Retrofit automatically just because it is common.

The implementation plan must pick one approach and state:

- new dependency/dependencies;
- size/complexity implications;
- cancellation behavior;
- testability;
- why it is the minimal appropriate choice.

---

# 10. API Client / State Machine

Plan exact classes/data models for:

- create request;
- create response;
- upload response;
- REST error envelope;
- handoff operation state;
- idempotency key;
- retry policy;
- slot tracking;
- cancellation.

Map all important API outcomes:

- 400
- 401
- 403
- 409
- 410
- 413
- 415
- 422
- 429
- 5xx
- timeout
- connection failure
- cancellation.

The plan must distinguish:

- automatic retry;
- restart with new handoff;
- re-prepare image;
- user-visible retry;
- permanent failure.

Do not expose HTTP/API terminology in UI.

---

# 11. API Key Provisioning

There is no current secret-provisioning precedent.

The implementation plan must choose a concrete, repository-appropriate mechanism.

Requirements:

- key never committed to VCS;
- key not placed in URLs;
- key not logged;
- debug/release handling defined;
- local developer setup defined;
- CI/release build setup defined;
- behavior when key is missing defined;
- tests do not require a real production key.

Acknowledge explicitly:

- a key embedded in the released APK/AAB is extractable;
- the protection goal is limited scope + no accidental publication + rotation;
- obfuscation is not equivalent to secrecy.

If the plan proposes `local.properties`, Gradle properties, environment variables, BuildConfig, generated resources, or another method, explain the complete flow.

Do not create the key or any credential in this gate.

---

# 12. Manifest / Build / Dependency Plan

Plan exact expected changes to:

- `AndroidManifest.xml`;
- `app/build.gradle.kts`;
- `gradle/libs.versions.toml`;
- ProGuard/R8 rules if needed;
- Custom Tabs dependency;
- HTTP client dependency;
- build config for partner key.

Explicitly flag:

- `INTERNET` as a release-critical capability change;
- manifest-level scope;
- release build validation required.

Do not add the permission in this gate.

---

# 13. Custom Tab Plan

No current `androidx.browser` dependency exists.

Plan:

- dependency;
- URL launch helper/location;
- lifecycle behavior;
- launch failure handling;
- keeping current `checkout_url` in-memory;
- retry-open without re-upload;
- return-to-screen behavior;
- no deep link;
- no order callback.

Use exact API-returned URL.

No manual URL reconstruction.

---

# 14. ViewModel / State Model

Define an explicit Wackelbild ViewModel state machine.

At minimum model:

- initial local preview state;
- current visible image;
- sensor available/unavailable;
- date-toggle enabled/disabled/value;
- idle;
- preparing HQ;
- fallback confirmation needed;
- preparing fallback;
- creating handoff;
- uploading;
- ready-to-open;
- open-failed-with-checkout-url;
- retryable error;
- permanent error;
- cancelled.

Avoid exposing backend technical phases to UI if product UX uses one spinner state.

The implementation plan must distinguish internal operation state from user-visible state.

Explain:

- cancellation;
- one-shot events;
- Custom Tab launch event;
- return behavior;
- process recreation behavior.

Do not persist handoff state.

---

# 15. UI Layout Plan

Define the expected screen structure by width class.

At minimum:

## Compact

- TopAppBar;
- moderate preview;
- date toggle row;
- interaction hint;
- transfer disclosure;
- CTA/loading/error area;
- vertical scrolling only where necessary.

## Medium / Expanded

Use current SameView max-width rules.

Do not make preview fill the entire available display merely because more space exists.

Ensure:

- Portrait Comparison stays visually Portrait;
- Landscape Comparison stays visually Landscape;
- no additional crop;
- date overlay remains WYSIWYG.

List exact existing layout constants/components likely reusable.

---

# 16. Localization Plan

Plan all new string resources.

At minimum include DE + EN.

Use approved German copy from the feature spec.

For English, propose final values for each new user-facing string.

The implementation plan may recommend:

- "Create lenticular print"
- "Order your lenticular print"

or another wording, but must make one concrete recommendation for implementation.

Also plan:

- API locale mapping;
- current DE/EN app locales;
- fallback to `de-DE` if external service lacks current language;
- external locale matrix still requiring Olaf confirmation.

---

# 17. Error Copy Plan

List the exact planned string resources/user states for:

- no internet;
- generic transfer failure;
- partner unavailable;
- HQ fallback warning;
- permanent image/preparation failure;
- transfer cancellation dialog;
- Custom Tab open failure.

Keep wording consistent with approved German product spec.

Do not invent extra dialogs.

---

# 18. Testing Strategy

The plan must define tests before implementation starts.

## Unit tests

At minimum:

- tilt threshold/hysteresis;
- relative neutral position;
- display rotation mapping;
- swipe/sensor arbitration;
- date precision formatting;
- date availability;
- date badge geometry;
- common output dimension algorithm;
- no-upscale;
- API limit handling;
- JPEG-size reduction strategy;
- exact Reference crop parity;
- exact Capture crop parity;
- HQ fallback;
- metadata-clean output;
- API DTO parsing;
- idempotency;
- retry/backoff;
- status/error mapping;
- cancellation state machine;
- partner-key missing behavior.

## UI/instrumentation tests

At minimum:

- Share menu item and divider;
- navigation to Wackelbild screen;
- Back;
- date-toggle default and disabled state;
- preview initial Reference state;
- swipe toggling;
- loading state;
- Back cancellation dialog;
- fallback warning;
- retryable/permanent errors;
- Custom Tab launch intent/behavior as testable without real partner service;
- Custom Tab open failure fallback;
- accessibility semantics;
- Compact/Medium/Expanded layout.

## Real-device tests

Must explicitly include:

- tilt thresholds/hysteresis;
- portrait device orientation;
- landscape device orientation;
- sensor unavailable behavior if feasible;
- swipe/scroll feel;
- background/resume;
- Custom Tab launch/return;
- slow network behavior;
- cancellation during upload.

---

# 19. DeinWackelbild Pilot Validation

The implementation plan must include a final manual pilot phase against the actual DeinWackelbild test/production pilot endpoint.

Do not execute it now.

Include:

1. Landscape pair.
2. Portrait pair.
3. Retry after connection interruption.
4. Over-limit source handling.
5. JPEG validation.
6. Expired handoff.
7. Internal `sameview` partner attribution.
8. Customer mail/invoice token leakage check.
9. Complete checkout without SameView return flow.

Additionally validate SameView-specific behavior:

- crop parity;
- date overlay;
- metadata stripping;
- fallback;
- Custom Tab return;
- re-ordering.

State which checks require Olaf/DeinWackelbild cooperation.

---

# 20. Release / Privacy / Compliance Plan

The plan must include a dedicated release-readiness block after implementation, not buried in notes.

Include:

- Privacy Policy update/review;
- Google Play Data Safety review/update;
- `RELEASE_HARDENING_AUDIT_V2.md` or successor audit update;
- `IMPLEMENTATION_NOTES.md`;
- re-check of any "offline/no INTERNET/no uploads" wording;
- partner/commission disclosure review;
- manifest review;
- release artifact inspection;
- API-key exposure review;
- HTTPS-only behavior;
- no cleartext traffic.

Do not make legal conclusions.

Mark legal/compliance items as review requirements.

---

# 21. Documentation Changes Planned

The implementation plan must list every document expected to change and at which implementation block.

At minimum assess:

- `docs/IMPLEMENTATION_NOTES.md`
- `docs/COMPARE_FLOW_V1.md`
- `docs/RELEASE_HARDENING_AUDIT_V2.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md` only if implementation discovers a real contract issue
- any network/privacy wording in `docs/CLAUDE_PROJECT_INSTRUCTION.md` only if required by actual implementation behavior.

Do not rewrite historical documents.

---

# 22. Implementation Block Design

The plan must break implementation into small, reviewable blocks.

Prefer one concern per block.

The exact block sequence must minimize risk.

A good plan will likely separate concerns roughly like:

1. navigation/menu shell;
2. local preview screen shell;
3. tilt/swipe interaction;
4. date overlay preview;
5. two-file print renderer;
6. temp-file cleanup;
7. network client + DTOs;
8. handoff state machine;
9. API key/build config;
10. manifest/dependencies/Custom Tabs;
11. end-to-end UI wiring;
12. error/cancellation;
13. tests;
14. release/privacy/documentation hardening;
15. real-device/pilot validation.

Do not copy this blindly.

Inspect dependencies between blocks and propose the safest actual order.

Every block in the plan must include:

- objective;
- exact files created/modified;
- exact functions/classes involved;
- implementation detail;
- regression risks;
- test commands;
- manual validation;
- stop/gate criteria.

No block may contain unrelated cleanup.

---

# 23. File Scope Table

The implementation plan must contain a single consolidated table:

| File | Create / Modify | Block(s) | Exact responsibility | Risk |
|---|---|---|---|---|

Use full repository-relative paths.

Do not invent filenames if repository evidence does not justify them.

For new files, choose final proposed paths.

Mark high-risk files explicitly, especially:

- CompareScreen;
- MainActivity/navigation;
- shared image renderer;
- AndroidManifest;
- Gradle;
- release/proguard config.

---

# 24. Reuse vs. New-Code Matrix

Include:

| Existing component | Reuse unchanged | Extend | Extract shared helper | Do not touch | Reason |
|---|---|---|---|---|---|

At minimum assess:

- `ShareImageRenderer`
- `ReferenceRenderer`
- current HQ helper logic
- `CaptionRenderer`
- `CompassProvider`
- `ShareComparisonScreen`
- `CreateVideoScreen`
- current temp/cache helpers if any
- current localization/date helpers.

This matrix must make regression boundaries explicit.

---

# 25. Risk Register

Include a concise but concrete risk register.

At minimum assess:

- `INTERNET` capability/release impact;
- partner key extractability;
- first network client in app;
- upload cancellation;
- process/background lifecycle;
- HQ memory/OOM risk;
- JPEG size limit enforcement;
- crop parity;
- sensor jitter;
- gesture conflict;
- temporary-file leakage;
- metadata leakage;
- Custom Tab failure;
- API drift;
- external locale support;
- Play/privacy disclosure.

For each risk:

- severity;
- mitigation;
- verification.

---

# 26. Required Gradle / Test Commands

The plan must state the intended verification commands and when they are run.

Assess repository-specific variants, but include as relevant:

- `./gradlew clean`
- `./gradlew testDebugUnitTest`
- appropriate Managed Device tasks
- `./gradlew connectedDebugAndroidTest`
- `./gradlew lintDebug`
- `./gradlew assembleDebug`
- `./gradlew assembleRelease`
- `./gradlew bundleRelease`

Do not suppress failures.

Do not introduce a lint baseline.

Do not disable tests.

The final plan must distinguish:

- per-block fast checks;
- end-of-feature full checks;
- release checks;
- real-device checks.

---

# 27. No Implementation in This Gate

You may inspect and analyze.

You may create/update only:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

Do not change:

- Kotlin/Java;
- XML resources;
- manifest;
- Gradle;
- ProGuard;
- tests;
- any other documentation.

If the implementation plan reveals that the feature spec itself must change before planning can continue, stop and report the conflict instead of changing the spec.

---

# 28. Verification for This Gate

After creating the plan, run:

1. `git diff --check`
2. `git status --short`
3. inspect the full new plan
4. verify no production/test/build file changed
5. verify no implementation code was created
6. verify no real API call occurred

No Gradle build is required for plan creation unless needed to verify a factual repository claim. If you do run any Gradle command, report exactly why and the result.

---

# 29. Required Structure of `DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

The plan file must use exactly these top-level sections:

# 1. Document Status
# 2. Repository Baseline
# 3. Authoritative Specifications
# 4. Existing Architecture Findings
# 5. Target Architecture
# 6. Navigation and Screen Architecture
# 7. Tilt / Swipe Architecture
# 8. Date Overlay Architecture
# 9. HQ Print Image Architecture
# 10. Common Resolution / API Limit Strategy
# 11. Temporary File Architecture
# 12. Metadata / Privacy Architecture
# 13. Network Client Decision
# 14. DeinWackelbild API Client and State Machine
# 15. Partner-Key Provisioning
# 16. Custom Tab Integration
# 17. Localization
# 18. Error / Retry / Cancellation Model
# 19. Lifecycle / Background Behavior
# 20. Accessibility / Responsive Layout
# 21. File Scope
# 22. Reuse vs. New-Code Matrix
# 23. Implementation Blocks
# 24. Test Strategy
# 25. Real-Device Validation
# 26. DeinWackelbild Pilot Acceptance
# 27. Release / Privacy / Play Compliance
# 28. Documentation Updates
# 29. Risk Register
# 30. Open External Dependencies
# 31. Final Implementation Sequence
# 32. Definition of Done

Do not omit any section.

---

# 30. Required Final Response

After creating the implementation-plan file, respond with exactly:

## 1. Repository Baseline

- branch
- HEAD
- initial status

## 2. File Created

- exact path
- line count
- confirm no other file changed

## 3. Key Architecture Decisions in the Plan

Summarize:

- chosen HTTP client;
- chosen partner-key provisioning approach;
- chosen tilt sensor approach;
- chosen HQ two-file reuse strategy;
- chosen temp-file strategy;
- chosen common-resolution / <=20MiB strategy;
- Custom Tab approach.

## 4. Implementation Blocks

List block names in order only.

## 5. Highest Risks

Top 5 only.

## 6. Verification

- `git diff --check`
- final `git status --short`
- Gradle/tests run or not run
- confirm no API calls
- confirm no production code changes

## 7. Open External Items

Only items that genuinely require Olaf / legal / Play Console / release environment input.

## 8. Gate Result

Choose exactly one:

- **GATE 3 COMPLETE — IMPLEMENTATION PLAN READY FOR REVIEW**
- **GATE 3 BLOCKED — PRODUCT/SPEC DECISION REQUIRED**

No implementation is authorized by completion of this gate.

---

# Final Rule

This is a planning gate.

One artifact only:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

No code.
No manifest.
No Gradle changes.
No permissions.
No dependencies.
No tests modified.
No API traffic.
No unrelated cleanup.

If anything is unclear, inspect repository evidence first; if still unclear, record it in the plan instead of inventing behavior.
