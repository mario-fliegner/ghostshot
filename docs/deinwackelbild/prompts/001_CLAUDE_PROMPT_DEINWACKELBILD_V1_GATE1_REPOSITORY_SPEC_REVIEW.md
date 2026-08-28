# CLAUDE PROMPT — DEINWACKELBILD V1 — GATE 1: REPOSITORY & SPEC CONSISTENCY REVIEW

## Role

You are working in the existing SameView Android repository.

This is an **ANALYSIS-ONLY gate** for the planned DeinWackelbild.de integration.

Do **not** implement anything.
Do **not** modify production code.
Do **not** create an implementation plan yet.
Do **not** silently rewrite the proposed feature specification.

Your job in this gate is to determine whether the proposed product/UX specification is consistent with:

1. the current repository,
2. the current authoritative SameView MD specifications,
3. the actual existing image/session/rendering architecture,
4. the current privacy/offline/network/release contracts,
5. the supplied DeinWackelbild pilot API contract as represented in the feature specification.

The result of this gate is a factual review report and, only where justified by evidence, a proposed list of specification corrections/clarifications.

---

## Repository Access

You have access to the SameView Android repository and must inspect the actual repository state.

Do not reason from assumptions where repository evidence is available.

Before drawing conclusions, inspect:

- the current branch and HEAD,
- `git status`,
- relevant source files,
- relevant tests,
- relevant Gradle/build configuration,
- relevant AndroidManifest files,
- relevant authoritative documentation under `/docs`.

Report the branch, HEAD commit, and working-tree state at the start of your report.

Do not modify unrelated existing working-tree changes.

---

## Primary Input Specification

The new proposed feature specification is:

`docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`

Read the **entire file** before beginning the review.

This file contains the product/UX decisions already made for V1.

Treat those decisions as the **desired feature behavior**, but not yet as an authoritative repository Source of Truth until this consistency review is complete.

Do not simplify it into a different feature.

Do not replace product decisions with implementation preferences.

---

## Existing Source-of-Truth Rule

Before assessing feasibility or consistency, inspect all existing SameView specifications that may govern the affected behavior.

At minimum, locate and read the current authoritative versions of relevant documents, including where applicable:

- `CLAUDE_PROJECT_INSTRUCTION.md`
- `IMPLEMENTATION_NOTES.md`
- `CAMERA_WORKFLOW_UX_V1.md`
- `COMPARE_FLOW_V1.md`
- `COMPARE_SESSION_RENDERING_V1.md`
- `SETTINGS_UX_V1.md`
- `SESSION_ORIGINALS_V1.md`
- `SESSION_ORIGINALS_PRIVACY_V1.md`
- `SHARE_COMPARISON_IMAGE_V1.md`
- `SHARE_COMPARISON_IMAGE_HQ_ORIGINAL_V1.md`
- `SESSION_METADATA_V1.md`
- `RESPONSIVE_LAYOUT_SYSTEM_V1.md`
- `DE_LOCALIZATION_UX_REWORK_V1.md`
- `RELEASE_HARDENING_AUDIT_V2.md`
- any other current document that actually governs CompareScreen sharing, image rendering, originals, privacy, networking, lifecycle, accessibility, localization, or release behavior.

Do not assume filenames are exact if the repository uses a slightly different current name/version. Find the authoritative current files.

If duplicate or historical versions exist, determine which document is current before relying on it.

If an existing authoritative MD conflicts with the proposed DeinWackelbild behavior:

- identify the conflict explicitly,
- cite both sides,
- state which existing document currently governs the repository,
- state whether the new feature specification would require an explicit documentation update,
- do not silently resolve the contradiction.

Historical documents must not be rewritten merely to erase historical context.

---

# Review Objectives

## Objective 1 — Validate the Proposed UX Against Current SameView UI Patterns

Inspect the actual CompareScreen and current Share Image / Share Video flows.

Verify:

- where the existing Share icon/menu is implemented;
- current Share menu item order and visual pattern;
- whether a divider and third action can be added without changing unrelated behavior;
- how Share Image and Share Video navigate to their dedicated screens;
- top-app-bar/back behavior;
- screen-state ownership;
- scroll/responsive patterns;
- how Compose state is currently handled;
- whether returning from an external Custom Tab can naturally restore the proposed screen state;
- whether the proposed dedicated Wackelbild screen fits current navigation architecture without redesign.

Specifically assess the proposed flow:

`CompareScreen → Share menu → Wackelbild erstellen → dedicated screen → local preview → Bestelle dein Wackelbild → Custom Tab`

Do not propose UI redesign unless the current architecture makes the approved flow impossible or materially unsafe.

---

## Objective 2 — Validate the Tilt / Swipe Preview

Inspect current dependencies and architecture relevant to Android sensors and Compose lifecycle.

Determine:

- whether a suitable device sensor can be used without additional runtime permission;
- the smallest appropriate sensor API for the proposed left/right tilt interaction;
- how it can be lifecycle-bound to the visible screen;
- whether current project architecture already contains any sensor abstraction that should be reused;
- how display rotation affects sensor axes;
- whether the approved relative-neutral-position model is technically sound;
- whether hysteresis can stabilize switching without introducing a visible transition;
- how horizontal swipe toggle can coexist with vertical scrolling;
- how swipe priority can avoid immediate overwrite by an unchanged sensor state;
- what accessibility semantics/actions are appropriate for a non-tilt alternative.

The approved behavior must remain:

- direct image A/B switching;
- no fade;
- no animation;
- no sound;
- no haptic feedback;
- Reference initially visible;
- horizontal swipe toggles regardless of direction;
- sensor and swipe must not fight each other;
- preview remains interactive during preparation/upload.

Flag real-device tuning requirements explicitly.

Do not invent exact tilt thresholds during this gate.

---

## Objective 3 — Validate Preview Image Sources

Confirm from repository evidence what `reference.jpg` and `capture.jpg` actually represent.

Determine whether they are appropriate for:

- fast local Wackelbild preview;
- exact representation of the stored Comparison crop/alignment;
- Portrait/Landscape display;
- runtime date-overlay preview.

Verify whether using them directly for the local preview is consistent with existing rendering contracts.

Identify any mismatch between the terminology used in the proposed specification and the actual repository/session model.

---

## Objective 4 — Validate the HQ Print Reconstruction Requirement

This is one of the most important parts of the review.

Inspect the actual existing HQ / Original-quality Share Image implementation and all relevant specifications/tests.

Determine exactly:

- which persisted original files exist for current public-release sessions;
- how Reference originals are stored;
- how Capture originals are stored;
- which transformation/crop/alignment information is persisted;
- how the current HQ Share Image path reconstructs a high-resolution image;
- whether both Reference and Capture can be reconstructed independently in higher quality;
- whether the proposed requirement can be satisfied:

> The temporary print pair must reproduce exactly the same visible crop/content/alignment as persisted `reference.jpg` and `capture.jpg`, while using the highest genuinely available source quality.

Do not merely say "reuse HQ export".

Trace the actual rendering pipeline and identify whether it supports **both** images and strict crop parity.

Explicitly distinguish:

1. what is already implemented and reusable;
2. what is technically feasible but would require new logic;
3. what cannot currently be reconstructed from persisted data;
4. any case where the proposed specification assumes data that is not actually stored.

Also validate the proposed fallback from persisted `reference.jpg` + `capture.jpg`.

Hard rule: no proposed solution may mutate existing session files or originals.

---

## Objective 5 — Validate Date Overlay Feasibility and Existing Design Tokens

Inspect the current metadata/date model.

Determine:

- how Reference date precision is represented;
- whether year-only / month+year / full-date precision is available exactly as assumed;
- how Capture date is represented;
- current locale-aware date formatting helpers, if any;
- whether the proposed date rendering can preserve actual known precision without inventing values.

Inspect existing SameView UI/design definitions and identify the actual reusable values/patterns for:

- dark SameView CI background color,
- white foreground text,
- typography,
- corner radius,
- padding/spacing.

Do not invent new CI values.

Assess how the same date overlay can be:

1. drawn live over the local preview without modifying the preview files;
2. rendered proportionally into temporary HQ/fallback JPEGs;
3. kept visually consistent across Preview and Print output.

Approved UX remains:

- toggle `Datum anzeigen`;
- default OFF;
- bottom-right only;
- white text;
- dark SameView CI background;
- rounded corners;
- no shadow;
- no outline;
- no drag/edit/resize;
- no title/location overlay;
- disabled if no usable Reference date;
- missing Reference date must not block ordering.

---

## Objective 6 — Validate Temporary Transfer Image Generation

Inspect existing image-export utilities and temporary-file patterns.

Determine the smallest technically sound approach for creating two temporary JPEGs that:

- have identical pixel dimensions;
- have identical orientation/aspect ratio;
- preserve exact Comparison crop/alignment;
- use the highest common genuine source resolution;
- do not upscale the weaker source merely to match the stronger source;
- comply with the current pilot limits:
  - JPEG only,
  - <= 20 MiB each,
  - <= 16,000 px per side,
  - <= 80 MP;
- contain the optional date overlay if enabled;
- are correctly oriented at pixel level;
- do not rely on EXIF orientation.

Do not design a complete implementation yet.

The goal is to determine whether the specification is feasible and identify the concrete existing components that could support it.

If the phrase "highest common genuine source resolution" is ambiguous in relation to the existing renderer, explain exactly what needs to be clarified before implementation.

---

## Objective 7 — Validate Metadata Stripping / Privacy

Inspect existing metadata/privacy behavior and relevant documentation.

Determine whether SameView already has a proven mechanism for generating metadata-clean JPEG output.

The approved Wackelbild privacy requirement is strict:

The two files sent to DeinWackelbild.de must contain no unnecessary metadata, including:

- GPS,
- EXIF,
- camera/device metadata,
- EXIF timestamps,
- SameView session metadata,
- title,
- location,
- description,
- internal IDs,
- source/MediaStore URIs.

The optional visible date is image pixels only.

Confirm whether creating newly encoded JPEGs naturally satisfies this in the existing pipeline or whether explicit metadata handling is needed.

Also verify that no existing original/session file would be modified.

---

## Objective 8 — Validate Temporary File Lifecycle

Inspect current cache/temp/export patterns.

Determine:

- appropriate existing cache/temp location;
- cleanup behavior after success;
- cleanup after user cancellation;
- cleanup after final error;
- process-death/crash cleanup possibilities;
- whether Android cache lifecycle is sufficient or whether SameView already has a stronger cleanup pattern.

The feature must not:

- save transfer JPEGs to MediaStore;
- add them to the session;
- include them in session backup;
- persist them as print artifacts.

Do not add persistent handoff state merely to solve cleanup.

---

## Objective 9 — Validate Network Architecture Impact

This is release-critical.

Inspect:

- current AndroidManifest permissions;
- current release manifest;
- current network dependencies;
- Gradle dependencies;
- any Network Security Config;
- release hardening documentation;
- privacy/offline statements;
- Google Play/Data Safety assumptions documented in the repository.

Determine exactly what adding this explicit user-initiated network feature would change.

In particular verify whether the current release has `INTERNET` permission.

If adding the integration requires `android.permission.INTERNET`, identify this explicitly as a release/privacy behavior change.

Do not downplay this.

Assess:

- whether `INTERNET` would apply globally at manifest level even though the feature is purpose-limited;
- whether any current "offline-only", "no upload", or equivalent project claims become false or require qualification;
- whether current Play Data Safety declarations may require re-evaluation;
- whether Privacy Policy / in-app wording may require updates.

Do not make legal conclusions that repository evidence cannot support.

Separate:

- technical facts,
- documentation conflicts,
- compliance items requiring explicit review.

---

## Objective 10 — Validate API Client / Dependency Impact

Inspect current dependencies before proposing any networking approach.

Determine:

- whether a suitable HTTP client already exists;
- whether Android/platform APIs could satisfy the narrow integration without a new large dependency;
- whether Custom Tabs support/dependency already exists;
- whether adding a networking or browser dependency would affect app size/build/release configuration.

Do not choose an architecture just because it is popular.

The later implementation plan must prefer the smallest targeted solution consistent with the repository.

For this gate, report feasible options and existing project constraints only.

---

## Objective 11 — Validate Partner-Key Handling

Inspect the current build/release setup.

The pilot API requires a SameView partner key for handoff creation.

The external API specification explicitly acknowledges that a key embedded in a released Android application is extractable.

Determine:

- what build configuration mechanisms currently exist;
- whether secrets are already handled in a standard repository pattern;
- whether release-specific BuildConfig values are currently enabled/appropriate;
- what would and would not keep the key out of public source control;
- whether the proposed integration would accidentally expose the key in logs, URLs, crash output, tests, or generated artifacts.

Do not claim that an Android-embedded key can be made secret.

The security objective is limited exposure, scope, rotation, and no accidental publication.

Do not implement secret handling during this gate.

---

## Objective 12 — Validate API Contract Assumptions

Review the API baseline embedded in `DEINWACKELBILD_INTEGRATION_V1.md`.

Check the SameView-side assumptions for internal consistency:

- create handoff;
- partner key only on create;
- stable Idempotency-Key;
- upload URL supplied by server;
- handoff token used for uploads;
- slots `one` and `two`;
- retry/idempotency behavior;
- 24-hour expiration;
- ready state + `checkout_url`;
- exact URL opened without reconstruction;
- no V1 callback/order status;
- `external_reference` deliberately omitted;
- `partner=sameview`;
- locale mapping/fallback.

Identify questions that still require Olaf / DeinWackelbild to confirm before implementation.

Do not access or test the production API unless explicitly authorized by the user.

Do not send any images, requests, credentials, or test traffic to DeinWackelbild.de in this gate.

---

## Objective 13 — Validate Error Mapping

Compare the supplied API HTTP/error model with the approved SameView UX error model.

Determine whether each relevant technical condition can be safely mapped into:

- automatic retry while showing normal loading;
- temporary/retryable user error;
- quality fallback;
- permanent image/preparation error;
- partner/integration unavailable;
- Custom Tab open failure.

Identify any API state for which the approved UX is ambiguous.

Do not expose technical HTTP/API wording to the proposed UI.

---

## Objective 14 — Validate Background / Lifecycle Behavior

Inspect current coroutine/ViewModel/navigation/lifecycle patterns.

Assess whether the approved V1 behavior is technically consistent:

- no WorkManager;
- no foreground-service upload;
- no persistent handoff recovery;
- no later notification;
- no later automatic browser opening;
- handoff data held only for the active operation;
- short backgrounding may continue only while the active in-memory operation naturally survives;
- process loss means the user starts again later.

Identify any lifecycle hazard, especially:

- navigation away while upload is active;
- cancellation propagation;
- Custom Tab launch after screen disappearance;
- configuration changes;
- app backgrounding;
- process recreation.

Do not redesign the flow unless necessary.

---

## Objective 15 — Validate Localization

Inspect current localization resources and localization specifications.

Determine:

- exact resource pattern for DE/EN;
- current app-locale handling;
- date-formatting behavior;
- how the current app language can be mapped to the API locale.

The proposed specification intentionally leaves final English product terminology open.

Report a recommendation for the natural English equivalent of:

- `Wackelbild erstellen`
- `Bestelle dein Wackelbild`

but mark it as a copy/localization recommendation, not an implementation change.

Do not change localization files in this gate.

Also identify whether the DeinWackelbild locale matrix must be confirmed externally.

---

## Objective 16 — Validate Accessibility and Responsive Behavior

Inspect existing accessibility and responsive-layout tests/patterns.

Determine:

- how the new preview should expose image-switch semantics;
- whether the date toggle's disabled/supporting state fits current Compose patterns;
- whether the screen can remain usable with font scaling;
- whether Portrait/Landscape device orientation is supported by the current navigation/layout architecture;
- whether a scroll container is likely required on compact devices;
- whether horizontal preview gestures can coexist with vertical scrolling.

Do not create a new responsive-layout system.

---

## Objective 17 — Identify Documentation Impact

Produce a concrete documentation impact matrix.

For every current authoritative document affected by this feature, classify it as:

- **No change required**
- **Must be updated if feature is implemented**
- **Potential update — depends on implementation/compliance decision**
- **Historical document — do not rewrite**

Pay special attention to documents describing:

- offline behavior,
- network behavior,
- privacy,
- originals,
- external sharing,
- release hardening,
- Google Play assumptions,
- session file contracts.

Do not modify those files during this gate.

---

## Objective 18 — Identify Test / Verification Impact

Do not run destructive or unnecessary test suites merely for this analysis.

Inspect existing tests and identify the verification that implementation would require.

At minimum consider:

- unit tests for tilt state logic;
- swipe/sensor arbitration;
- date formatting/precision;
- date-overlay rendering geometry;
- HQ crop parity;
- common output dimensions;
- fallback behavior;
- metadata-clean JPEG generation;
- API request/idempotency/error mapping;
- cancellation;
- lifecycle;
- navigation;
- accessibility;
- responsive UI;
- build/release manifest changes.

Also identify expected Gradle verification commands for the later implementation, including which of these are relevant:

- `./gradlew clean`
- `./gradlew testDebugUnitTest`
- `./gradlew connectedDebugAndroidTest`
- existing Managed Device tasks
- `./gradlew assembleDebug`
- `./gradlew assembleRelease`
- `./gradlew bundleRelease`
- lint tasks used by this repository.

State which validations require a real Android device, especially sensor behavior and Custom Tab behavior.

---

# Hard Scope Rules

During this gate:

## You MAY

- read files;
- search the repository;
- inspect Git history if needed to understand current contracts;
- inspect tests;
- inspect Gradle configuration;
- inspect manifests;
- inspect documentation;
- run read-only/non-mutating diagnostic commands where useful;
- run narrowly relevant existing tests only if necessary to verify an uncertain factual claim.

## You MUST NOT

- modify production code;
- modify tests;
- modify Gradle files;
- modify manifests;
- add dependencies;
- add permissions;
- add network code;
- contact DeinWackelbild.de;
- use real API credentials;
- create test handoffs;
- upload images;
- modify existing authoritative specifications;
- create `DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`;
- refactor anything;
- clean up unrelated code;
- rename files/classes/variables;
- change formatting;
- commit;
- push.

The only deliverable is the review report in your response.

Do not create repository files in this gate unless the user separately authorizes that after reviewing your report.

---

# Evidence Requirements

Every important conclusion must be tied to concrete repository evidence.

Use:

- full repository-relative file paths;
- class/function/resource names;
- relevant line ranges where practical;
- specification section names/headings;
- actual manifest/dependency facts.

Do not use vague claims such as:

- "should be possible",
- "probably reusable",
- "likely already supported"

without showing what repository evidence supports the statement.

For important feasibility questions, classify confidence as:

- **Confirmed**
- **Confirmed with required new implementation**
- **Unclear — needs repository clarification**
- **External confirmation required**
- **Conflict**

---

# Required Output Format

Return one structured report with exactly these major sections.

## 1. Executive Result

Give one of:

- **READY FOR SPEC FINALIZATION**
- **READY WITH SPEC CORRECTIONS**
- **BLOCKED — PRODUCT/SPEC DECISION REQUIRED**
- **BLOCKED — TECHNICAL/EXTERNAL INFORMATION REQUIRED**

Then summarize why in no more than 10 bullets.

Do not call the feature "ready for implementation"; this gate precedes the implementation plan.

---

## 2. Repository Baseline

Report:

- branch;
- HEAD;
- working-tree status;
- relevant Android/Gradle baseline;
- whether any unrelated pre-existing modifications were present.

---

## 3. Authoritative Documents Reviewed

Table:

| Document | Authority / relevance | Key constraint for this feature |
|---|---|---|

Include all relevant current documents actually reviewed.

Call out duplicates/historical versions where relevant.

---

## 4. Proposed Spec vs. Existing Specs — Conflict Matrix

Table:

| Proposed section / behavior | Existing source of truth | Status | Evidence | Required action |
|---|---|---|---|---|

Status must be one of:

- Compatible
- Needs clarification
- Conflict
- Requires existing-doc update if implemented

Do not hide conflicts in prose.

---

## 5. UX / Navigation Feasibility

Cover:

- Compare Share menu;
- dedicated screen;
- navigation/back behavior;
- Custom Tab return;
- temporary screen state;
- responsive/scroll behavior.

For each important item, state evidence and feasibility classification.

---

## 6. Tilt / Swipe / Accessibility Feasibility

Cover:

- sensor choice/options;
- permissions;
- lifecycle;
- display rotation;
- relative neutral position;
- hysteresis;
- swipe toggle;
- swipe/sensor arbitration;
- vertical scroll coexistence;
- accessibility fallback;
- real-device validation needs.

No exact tuning constants yet.

---

## 7. Image Pipeline Findings

This section must be detailed.

Cover separately:

### 7.1 Preview sources

### 7.2 Persisted originals

### 7.3 Existing HQ renderer

### 7.4 Reference HQ reconstruction

### 7.5 Capture HQ reconstruction

### 7.6 Exact crop/alignment parity

### 7.7 Common output dimensions / no-upscale rule

### 7.8 Fallback feasibility

For each subsection distinguish existing implementation from required new implementation.

---

## 8. Date Overlay Findings

Cover:

- date precision model;
- locale formatting;
- missing Reference date;
- preview overlay;
- HQ/fallback render overlay;
- actual SameView CI tokens/patterns found;
- WYSIWYG feasibility.

---

## 9. Privacy / Metadata / Temporary Files

Cover:

- metadata stripping;
- persisted originals remain untouched;
- temp file strategy;
- cleanup;
- backup/MediaStore exclusion;
- sensor privacy;
- any privacy-spec conflict.

---

## 10. Network / Release / Play Impact

This is mandatory and must be explicit.

Cover:

- current INTERNET permission state;
- required permission changes;
- current networking dependencies;
- offline-only assumptions;
- Privacy Policy impact;
- Play Data Safety impact;
- release-hardening impact;
- whether any current claims become inaccurate.

Separate confirmed technical facts from legal/compliance review items.

---

## 11. API / Security Review

Cover:

- API contract consistency;
- Idempotency;
- token lifecycle;
- `external_reference` omission;
- locale;
- partner key handling;
- logging exposure;
- extractability of Android-embedded key;
- questions requiring Olaf confirmation.

Do not contact the API.

---

## 12. Error / Retry / Lifecycle Mapping

Table:

| Technical condition | Proposed user behavior | Feasible? | Notes / gap |
|---|---|---|---|

Include at least the supplied `400`, `401`, `403`, `409`, `410`, `413`, `415`, `422`, `429`, `5xx`, no-network, local preparation failure, fallback, cancellation, and Custom Tab open failure.

---

## 13. Localization Review

Cover:

- DE/EN resource architecture;
- locale mapping;
- date localization;
- recommended natural English product wording;
- external locale matrix still needing confirmation.

---

## 14. Documentation Impact Matrix

Table:

| Document | Impact classification | Exact reason | Update required before release? |
|---|---|---|---|

Do not modify the documents.

---

## 15. Expected File Scope for Later Implementation

This is **not** permission to implement.

List the files that, based on current evidence, would likely need:

- modification,
- creation.

Use full repository-relative paths.

Group them by:

- UI/navigation,
- image/rendering,
- network/API,
- resources/localization,
- build/manifest,
- tests,
- documentation.

If exact filenames cannot yet be known, say so rather than inventing them.

Explicitly identify any high-risk file.

---

## 16. Verification Plan for Later Implementation

List:

- unit tests;
- instrumentation/UI tests;
- build/lint commands;
- release build checks;
- real-device checks;
- manual DeinWackelbild pilot acceptance checks.

State which commands you actually ran during this analysis and their results.

State which commands were **not** run.

---

## 17. Required Corrections to `DEINWACKELBILD_INTEGRATION_V1.md`

Split into:

### 17.1 Required corrections

Only factual/contract changes necessary because the proposed specification conflicts with repository reality or another authoritative source.

For each:

- current section;
- problem;
- repository/spec evidence;
- exact recommended replacement meaning.

### 17.2 Recommended clarifications

Non-blocking wording improvements that would make the specification less ambiguous.

### 17.3 No-change product decisions

Explicitly list important product decisions that repository analysis does **not** justify changing.

This prevents implementation convenience from eroding approved UX.

---

## 18. External Questions Before Implementation Plan

List only questions that genuinely require Olaf / DeinWackelbild / legal-compliance confirmation.

For each question explain:

- why repository evidence cannot answer it;
- whether it blocks spec finalization;
- whether it blocks only implementation;
- whether it blocks only release.

Do not manufacture questions merely to be cautious.

---

## 19. Final Gate Recommendation

Choose exactly one:

- **Approve spec as-is for finalization**
- **Apply listed spec corrections, then finalize**
- **Return to product decision before finalization**
- **Obtain external information before finalization**

Then state the exact next step.

The next step must **not** be implementation.

If the spec can be finalized after this review, the following separate gate will be creation of:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

No implementation begins until that plan is reviewed and explicitly approved.

---

# Critical Reminders

- One problem: DeinWackelbild V1 specification validation.
- Analysis only.
- No code.
- No implementation plan yet.
- No unrelated improvements.
- Existing working behavior must remain untouched.
- Existing authoritative MDs outrank current code unless the user explicitly overrides them.
- Do not silently change approved UX because another implementation would be easier.
- Do not guess about the HQ image pipeline: trace it.
- Do not guess about network/privacy impact: inspect manifests/docs.
- Do not claim an embedded Android API key is secret.
- Do not contact DeinWackelbild.de during this gate.
- Do not alter any existing SameView file.
