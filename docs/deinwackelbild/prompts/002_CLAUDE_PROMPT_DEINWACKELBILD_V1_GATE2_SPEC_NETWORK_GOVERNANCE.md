# CLAUDE PROMPT — DEINWACKELBILD V1 — GATE 2: SPEC CORRECTION & NETWORK-GOVERNANCE ADDENDUM

## Role

You are working in the existing SameView Android repository.

Gate 1 — repository/spec consistency review — has been completed.

The accepted Gate 1 conclusion is:

**READY WITH SPEC CORRECTIONS**

This Gate 2 is a **documentation-only implementation step**.

You are authorized to modify exactly the two documentation files listed below, and nothing else.

Do not modify Android/Kotlin code.
Do not modify tests.
Do not modify Gradle.
Do not modify AndroidManifest.
Do not add `INTERNET`.
Do not add dependencies.
Do not create the DeinWackelbild implementation plan yet.

The purpose of this gate is only to:

1. apply the repository-validated corrections/clarifications to the DeinWackelbild V1 product specification; and
2. explicitly authorize DeinWackelbild as a second narrow SameView network exception in the master project instructions, following the existing Hosted Comparison governance precedent.

After these two documentation changes, stop and report exactly what changed.

---

# 1. Repository Baseline

Before changing anything:

1. report current branch;
2. report current HEAD;
3. run/check `git status`;
4. inspect the current versions of both target files;
5. verify that no unrelated file needs modification.

If unrelated working-tree changes exist, do not alter them.

Expected feature spec:

`docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`

Master project instructions:

`CLAUDE_PROJECT_INSTRUCTION.md`

If the actual authoritative master-instruction path differs, stop and report it rather than guessing.

---

# 2. Source Material

Read in full before editing:

- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `CLAUDE_PROJECT_INSTRUCTION.md`

Also use the already completed Gate 1 findings as the basis for these changes.

Where useful for preserving the exact governance structure, inspect the existing **Hosted Comparison network/manifest addendum** inside `CLAUDE_PROJECT_INSTRUCTION.md`.

Do not reopen the entire product design.

Do not reinterpret already-approved decisions.

---

# 3. Authorized Files

Exactly these two files may be modified:

1. `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
2. `CLAUDE_PROJECT_INSTRUCTION.md`

No third file may be changed.

If you discover that a third file would need modification to complete this gate, STOP and report why. Do not modify it.

---

# 4. Change A — DeinWackelbild Spec: Required Governance Correction

Update `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md` so it explicitly records the repository governance precondition identified in Gate 1.

The meaning must be:

- DeinWackelbild is an explicitly user-initiated online feature.
- Its implementation requires Android `INTERNET` permission.
- SameView's current master manifest/network policy requires such online capabilities to be explicitly approved in `CLAUDE_PROJECT_INSTRUCTION.md`.
- Therefore implementation of DeinWackelbild is gated on the companion master-instruction addendum being present.
- The network exception is narrow and purpose-limited to the approved DeinWackelbild handoff/order flow.
- This does not authorize unrelated network calls, telemetry, analytics, tracking, background networking, or expansion of other SameView features.
- The permission itself remains an app-level Android capability even though SameView code must use it only for explicitly approved online features.

Place this clarification in the most appropriate existing section(s), preferably §23 / §41 / §42 / §52 as appropriate.

Do not duplicate the same paragraph unnecessarily across several sections.

The spec should remain internally coherent.

---

# 5. Change B — DeinWackelbild Spec: HQ Clarification

Apply the Gate 1 clarification to the HQ wording.

The specification must make clear that:

- "highest suitable common print resolution" means the highest **genuinely supported common resolution** based on the real persisted source images;
- the DeinWackelbild limits such as 16,000 px / 80 MP / 20 MiB are upper safety/API constraints, **not target resolutions**;
- SameView must not upscale source imagery simply to approach those API limits;
- the existing Share Image HQ reconstruction logic is a validated architectural building block for crop/alignment parity;
- however, the Wackelbild pipeline has a different output requirement: two independent JPEGs rather than one composited Share Image;
- therefore this is new integration work and not merely calling the existing Share Image export unchanged.

Do not introduce implementation-specific class names into the product contract unless they already belong there.

Do not alter the approved "exact same visible crop/content/alignment" requirement.

---

# 6. Change C — DeinWackelbild Spec: Date Badge Token Clarification

Gate 1 confirmed that:

- `SameViewAppSurface = 0xFF17202F` is an existing canonical dark SameView surface token;
- white text is established;
- no single canonical bitmap/date-badge corner-radius token currently exists.

Update the relevant date-overlay wording so the specification does **not falsely imply** that a canonical corner radius can simply be derived from an existing token.

Preserve the approved visual decision:

- dark SameView CI background;
- white text;
- rounded corners;
- no shadow;
- no outline.

The exact corner-radius value remains an implementation/design detail to be chosen consistently with existing SameView visual language during the implementation-plan/design-resolution step.

Do not change the approved appearance.

Do not add a user setting for it.

---

# 7. Change D — DeinWackelbild Spec: Existing Share-Menu Wording

Gate 1 identified a trivial wording verification item around the current second Export menu action ("Share video" vs. resource naming such as `export_menu_create_video`).

Inspect the actual current localized resource values and current UI behavior.

Then ensure the conceptual menu shown in the DeinWackelbild specification reflects the **actual visible current menu wording** rather than an internal resource key or stale prose.

This is documentation alignment only.

Do not modify the existing menu resources or UI.

Do not rename the existing Share Image / Share Video feature.

---

# 8. Change E — Master Project Instructions: DeinWackelbild Network Exception

Update `CLAUDE_PROJECT_INSTRUCTION.md`.

This is the critical governance change.

Use the existing Hosted Comparison online/network addendum as the structural precedent.

Add a **new, separate DeinWackelbild-specific addendum/exception**.

Do not rewrite the Hosted Comparison exception into a generic "network allowed" rule.

Do not weaken the default offline/privacy posture.

The required meaning is:

## 8.1 Explicitly approved online feature

DeinWackelbild V1 is an explicitly approved SameView online feature.

Its network purpose is narrowly limited to the user-initiated transfer of the two prepared Comparison images to DeinWackelbild.de and the retrieval/opening of the resulting checkout/configurator URL.

## 8.2 Explicit user action

No DeinWackelbild network request may occur merely because:

- SameView starts;
- CompareScreen opens;
- the Share menu opens;
- the Wackelbild screen opens;
- the user previews via tilt/swipe;
- the user toggles the date option.

Network activity begins only after the explicit approved order action and any required fallback confirmation.

## 8.3 INTERNET permission governance

The Android `INTERNET` permission is permitted for the approved SameView online features.

At this point, the master instructions must make clear that the explicitly approved online exceptions include:

- Hosted Comparison;
- DeinWackelbild V1.

Do not phrase this as unrestricted network permission.

Preserve the rule that any future unrelated online feature requires its own explicit approval.

## 8.4 Privacy restrictions

The DeinWackelbild exception must not authorize:

- analytics;
- telemetry;
- tracking;
- advertising SDKs;
- unrelated uploads;
- unrelated API calls;
- automatic background uploads;
- user profiling;
- persistent order tracking.

Only the purpose-limited handoff defined in:

`docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`

is approved.

## 8.5 Data minimization

The addendum should capture at master-policy level that:

- only temporary prepared transfer images and the minimum technical handoff fields may be sent;
- no SameView session/comparison identifier is sent as `external_reference`;
- no GPS/EXIF/device/session metadata is intentionally included in transfer JPEGs;
- existing persisted session/original files are never modified by the transfer;
- motion/sensor data is not sent.

Do not duplicate the entire feature specification into the master instructions. Keep the master addendum concise and point to the feature specification for detailed behavior.

## 8.6 No background-network expansion

The approval does not authorize a persistent WorkManager/foreground-service order uploader or automatic resume after process loss.

Any such future behavior would require a separate explicit decision.

## 8.7 Release/compliance consequence

State that adding/shipping this capability requires the relevant privacy, Play Data Safety, and release-hardening documentation to be re-reviewed before release.

Do not claim those external compliance items are already complete.

---

# 9. Do Not Change These Product Decisions

This gate must not alter any of the following:

- entry only from an opened saved Comparison;
- existing Share menu as the entry point;
- dedicated Wackelbild screen;
- Reference initially visible;
- direct A/B switch;
- no fade;
- no transition animation;
- no haptic feedback;
- no sound;
- tilt + always-available swipe;
- no sensor permission;
- date toggle default OFF;
- date only;
- no title overlay;
- no location overlay;
- no SameView branding on print;
- date bottom-right;
- white text;
- dark SameView CI background;
- rounded corners;
- no shadow;
- no outline;
- missing Reference date disables only the date toggle;
- missing Reference date never blocks ordering;
- no metadata editing from the Wackelbild screen;
- no prices;
- no product sizes/options in SameView;
- exact approved German transfer sentence;
- CTA `Bestelle dein Wackelbild`;
- spinner + `Wackelbild wird vorbereitet …`;
- preview remains interactive while preparing/uploading;
- no percentage/progress bar;
- exact crop/alignment parity with `reference.jpg` / `capture.jpg`;
- originals used only as quality sources;
- fallback warning only when HQ originals cannot be used;
- fallback still creates new temporary transfer files;
- never modify `reference.jpg`;
- never modify `capture.jpg`;
- never modify any persisted original;
- metadata-clean transfer JPEGs;
- no `external_reference`;
- no persistent handoff state;
- no background uploader;
- Android Custom Tab;
- no order-status inference;
- user may order again;
- no telemetry/analytics/tracking.

If you believe any of these must change because of a newly discovered hard conflict, STOP and report it instead of editing around it.

---

# 10. Documentation Authority After This Gate

After the edits, the intended authority relationship is:

- `CLAUDE_PROJECT_INSTRUCTION.md`
  - remains the master project governance/source-of-truth document;
  - explicitly permits the narrow DeinWackelbild network exception.

- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
  - becomes the repository-validated V1 product/UX/privacy/technical-contract specification for this feature.

The future:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

does not yet exist and must **not** be created in this gate.

---

# 11. Minimal Change Discipline

Changes must be surgical.

Do not:

- rewrite entire documents;
- reformat unrelated sections;
- reorder unrelated content;
- clean up wording outside this scope;
- update historical documents;
- update `IMPLEMENTATION_NOTES.md`;
- update `RELEASE_HARDENING_AUDIT_V2.md`;
- update `COMPARE_FLOW_V1.md`;
- modify Android code;
- modify resources;
- modify tests;
- modify Gradle;
- modify manifest;
- add dependencies.

Those later documentation/code impacts belong to subsequent gates.

---

# 12. Verification

Because this gate is documentation-only:

- no Android build is required;
- no Gradle test suite is required;
- no device validation is required.

However, you must verify:

1. `git diff --check`
2. `git diff -- docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md CLAUDE_PROJECT_INSTRUCTION.md`
3. `git status --short`

Confirm:

- exactly the two authorized files were modified;
- no unrelated content changed;
- the feature spec remains internally coherent;
- the master instructions still preserve the default offline/privacy posture while adding exactly the new approved exception;
- no implementation-plan file was created;
- no production/test/build file changed.

If `git diff --check` reports a problem caused by your edits, fix it properly.

Do not suppress anything.

---

# 13. Required Final Report

Return exactly these sections:

## 1. Repository Baseline

- branch
- HEAD
- initial working-tree state

## 2. Files Modified

List exactly the modified files.

If anything other than the two authorized files changed, state that clearly as a failure.

## 3. `DEINWACKELBILD_INTEGRATION_V1.md` Changes

For each change:

- section affected;
- previous meaning;
- new meaning;
- why Gate 1 required/recommended it.

Explicitly cover:

- network-governance precondition;
- HQ/source-resolution clarification;
- date-badge token clarification;
- actual existing Share-menu wording verification.

## 4. `CLAUDE_PROJECT_INSTRUCTION.md` Addendum

Summarize exactly:

- where the new addendum was inserted;
- how DeinWackelbild is authorized;
- what network behavior is allowed;
- what remains forbidden;
- how the existing Hosted Comparison exception was preserved.

## 5. Product Decisions Preserved

Confirm that none of the protected decisions in §9 of this prompt were changed.

Call out explicitly:

- no persisted image mutation;
- no background uploader;
- no telemetry;
- no price/product UI;
- no order-status tracking.

## 6. Verification

Report:

- `git diff --check`
- final `git status --short`
- exact files in the diff
- whether any Gradle/tests/builds were run
- whether any production code changed

## 7. Remaining Open Items

List only items that still genuinely remain open after this documentation gate, such as:

- exact date-badge corner radius;
- exact English product wording;
- external DeinWackelbild locale matrix;
- exact API behavior validation;
- partner-key provisioning;
- networking-client choice;
- Privacy Policy / Play Data Safety / release review.

Do not turn these into implementation decisions in this gate.

## 8. Gate Result

Choose exactly one:

- **GATE 2 COMPLETE — READY FOR IMPLEMENTATION-PLAN GATE**
- **GATE 2 INCOMPLETE — USER DECISION REQUIRED**

If complete, state that the next separate step is creation of:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

and that no implementation is authorized yet.

---

# Final Rule

This gate changes documentation only.

Exactly two files.

No implementation.

No implementation plan.

No hidden cleanup.

No unrelated edits.

If anything is unclear, preserve existing content and stop rather than expanding scope.
