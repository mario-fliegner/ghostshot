# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 10A: NETWORK ACTIVATION / MANIFEST / CUSTOM-TAB INFRASTRUCTURE — ANALYSIS + SCOPE CONFIRMATION ONLY

## Purpose

Blocks 1–9 of the DeinWackelbild integration are implemented and committed.

Block 10 is the first release-critical block that may change the app's Android network capability surface.

This gate is **ANALYSIS ONLY**.

Do not modify files.
Do not implement code.
Do not add `INTERNET` yet.
Do not make any real API request.
Do not launch the pilot flow.
Do not use or expose the real partner key.
Do not begin Block 11 UI wiring.

The goal is to establish the exact Block-10 scope from the CURRENT repository and CURRENT authoritative docs before the first manifest/network-capability change lands.

---

# 1. Mandatory source-of-truth review

Read fully:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`
- `docs/RELEASE_HARDENING_AUDIT_V2.md`

Also inspect:

- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/proguard-rules.pro`
- `WackelbildViewModel.kt`
- `WackelbildHandoffOrchestrator.kt`
- `OkHttpDeinWackelbildApiClient.kt`
- any existing `WackelbildCustomTabLauncher.kt` or browser-launch abstraction if one already exists
- relevant tests
- current merged-manifest/build setup if discoverable without building.

Do not rely on the original 2026-08-28 plan blindly. Blocks 1–9 have already corrected multiple plan assumptions.

Report:

- branch
- HEAD
- `git status --short`

Expected:
- Block 9 committed
- no Block-10 implementation yet
- only prompt archives may be untracked.

If materially different, STOP.

---

# 2. Governance precondition

Confirm that `docs/CLAUDE_PROJECT_INSTRUCTION.md` already contains the approved DeinWackelbild network exception created in Gate 2.

This must explicitly authorize:
- `INTERNET` for the narrow DeinWackelbild handoff
- only user-initiated transfer
- no analytics/telemetry/tracking
- no background upload
- no unrelated network expansion.

If that addendum is missing, contradictory, or no longer matches implementation, Block 10 is BLOCKED.

Do not silently add the permission against governance.

---

# 3. Determine the TRUE current Block-10 contract

Read the current implementation-plan Block 10 section exactly.

Do not infer Block 10 merely from previous conversation summaries.

Determine whether Block 10 currently owns some or all of:

- `android.permission.INTERNET`
- `androidx.browser` dependency
- a thin `WackelbildCustomTabLauncher`
- ProGuard/R8 changes only if release build proves required
- dependency/merged-manifest inspection
- no UI wiring yet.

Then explicitly separate what belongs to Block 10 from Block 11.

The current plan evidence suggests Block 11 is the end-to-end UI wiring block that owns:
- real order button
- spinner
- consent/fallback dialogs
- Back confirmation
- actual Custom Tab launch from UI
- first real pilot happy path.

Verify that from the current source-of-truth.

Do not pull Block-11 behavior forward.

---

# 4. INTERNET permission — exact manifest change

Inspect `app/src/main/AndroidManifest.xml`.

Determine the exact one-line permission addition:

`<uses-permission android:name="android.permission.INTERNET" />`

Confirm:
- placement consistent with existing `<uses-permission>` ordering
- no runtime permission flow is needed (`INTERNET` is normal permission)
- no user prompt/dialog is introduced
- no other manifest permission should change.

Do not add:
- `ACCESS_NETWORK_STATE` unless already present/transitive and explicitly required
- foreground service permissions
- storage/media permissions
- browser package queries unless required by Custom Tabs APIs
- deep links/app links
- exported components.

If anything besides INTERNET is required, explain exact evidence.

---

# 5. Network security / cleartext

The app currently has:
- minSdk 29
- no `networkSecurityConfig`
- no `usesCleartextTraffic` override.

The DeinWackelbild API base URL and upload/checkout URLs are HTTPS-only by contract/client validation.

Analyze whether Block 10 needs:
- no additional config (preferred if platform defaults are sufficient)
- `android:usesCleartextTraffic="false"`
- a custom `networkSecurityConfig`.

Do not add redundant security XML purely for appearance.

Use current Android platform behavior and repository policy.

If the existing manifest has no cleartext override and minSdk 29 already defaults cleartext to false, say whether leaving it untouched is the minimal correct approach.

---

# 6. `androidx.browser` dependency

Read the current Block-10 plan and current dependency catalog.

Determine whether Block 10 is supposed to add `androidx.browser` now even though UI launch occurs in Block 11.

If yes:
- identify exact stable artifact/version already locked by current plan, if any;
- if plan does not lock version, inspect repository conventions and choose only during this analysis if Block 10 is the designated dependency decision point;
- explain why dependency belongs here rather than Block 11.

If no:
- do not add it.

Do not assume the original plan's version is still current without verifying current plan text.

No web search is needed unless the project docs leave the required browser version truly unspecified and external recency is necessary. If version remains open after repo inspection, report it rather than guessing.

---

# 7. Custom Tab launcher boundary

If current Block 10 owns a thin launcher class, derive the exact minimal contract.

Likely requirements from the integration spec:

- receive exact API-returned `checkoutUrl`
- never modify it
- open via Android Custom Tab
- if no Custom Tabs/browser handler exists, return/throw a typed launch failure for Block 11 to map
- no URL reconstruction
- no deep link
- no order-status tracking
- no persistent browser state.

Determine whether launcher should:
- accept `Context`
- accept `Uri` or raw String
- validate HTTPS itself or rely on Block-7 parser guarantees
- return a Boolean/result
- be injectable/fakeable in Block 11 tests.

Choose the smallest interface.

Do not add UI or call it from ViewModel/Screen in Block 10.

If launcher belongs to Block 11 per current plan, do not create it now.

---

# 8. Browser availability / fallback behavior

The feature contract says Custom Tab / browser flow is external.

Determine what Block 10 infrastructure should do when no browser handler exists.

Do not add user-facing strings now.

Potential raw launcher outcomes may be:
- launched
- unavailable
- activity-not-found/failure.

Do not implement fallback to arbitrary WebView.

Do not add an embedded browser.

Do not alter the checkout URL.

---

# 9. Current real-network reachability after adding INTERNET

This is critical.

After Block 9:
- the production ViewModel is already wired to `BuildConfig.DEINWACKELBILD_PARTNER_KEY`
- Block 8 already has real orchestration
- Block 7 already has real OkHttp client.

Therefore, adding INTERNET changes the capability surface materially.

Analyze whether **any current user-accessible code path can already invoke `startOperation()` before Block 11**.

Inspect:
- `WackelbildScreen.kt`
- all callers of `startOperation()`
- all callers of `confirmFallbackAndContinue()`
- any hidden test/debug button
- lifecycle/init calls.

This must be proven.

If current UI does NOT call `startOperation()`, then adding INTERNET remains capability-only and no actual upload happens until Block 11.

If current UI DOES call it, Block 10 would immediately activate real network behavior and must be treated differently.

Do not guess.

This is a mandatory finding.

---

# 10. Consent / privacy boundary

The external contract requires explicit user consent before first API request.

Confirm whether Block 10 adding INTERNET alone can occur safely while:
- no UI starts the operation
- consent dialog remains Block 11.

If any current path can start network before consent UI exists, Block 10 is BLOCKED.

No user image may be transmitted before explicit consent.

---

# 11. Partner key / release-build implications

Block 9 policy:

- debug/non-release: local.properties → env → blank
- release: env only → blank.

Analyze Block 10 behavior in these cases:

### Debug with local pilot key
After INTERNET is added, network becomes technically possible — but only if a caller invokes the operation.

### Release with no env key
BuildConfig key blank → `createHandoff()` locally returns `INTEGRATION_UNAVAILABLE`; no request.

### Release with env key
Real network capability exists.

Confirm that no release artifact should be considered ready for Play simply because it builds.

Block 10 must trigger release/privacy follow-up work later.

---

# 12. Documentation that becomes factually stale immediately in Block 10

This is important.

The current plan explicitly says once INTERNET lands:

- `docs/IMPLEMENTATION_NOTES.md` has a stale "no INTERNET permission" claim that must be corrected in Block 10.
- `docs/RELEASE_HARDENING_AUDIT_V2.md` has a stale "kein INTERNET-Permission" positive claim, but the plan may defer the formal audit addendum to Block 14.
- `docs/CLAUDE_PROJECT_INSTRUCTION.md` already anticipates the exception and should not need another change.
- Privacy Policy and Play Data Safety require external review before release.

Determine exactly which docs MUST be changed in Block 10 versus which are deliberately deferred to Block 14.

Do not leave a known source-of-truth contradiction if the plan says it must be corrected now.

Do not prematurely rewrite the release audit if its planned update is explicitly Block 14, but identify the now-stale statement and its deferred owner.

---

# 13. Play Data Safety / Privacy Policy release gate

No legal drafting in Block 10 unless explicitly authorized.

But the analysis must clearly state:

- uploaded images become a real network data flow once the user-facing operation is activated
- Google Play Data Safety must be reviewed/updated before shipping the feature
- Privacy Policy must disclose the DeinWackelbild transfer before shipping
- partner/commission disclosure remains an explicit compliance review item
- Block 10 itself is not sufficient for Play release readiness.

Determine whether Block 10 implementation should be allowed into `main` while those external compliance items remain open, provided the UI path is not yet activated.

Distinguish:
- code integration/commit safety
- Play Store release safety.

---

# 14. Manifest merge inspection

After future implementation, the manifest must be inspected, not assumed.

Plan exact checks:

- merged debug manifest
- merged release manifest
- permissions introduced by app + dependencies
- exported components
- activities/providers/services added transitively
- queries entries
- cleartext/security attributes.

Expected requirement:
- planned app manifest diff is exactly one new INTERNET line
- any `androidx.browser` merged manifest contribution must be explicitly inspected and reported.

Determine exact Gradle tasks/files to use for merged manifest verification in this project.

Do not rely only on source manifest diff.

---

# 15. R8 / release build

Because Block 10 is release-critical, require:

- `assembleDebug`
- `assembleRelease`
- `bundleRelease`
- full unit tests
- manifest inspection
- potentially lint if current release-hardening practice requires it.

Inspect whether OkHttp/browser need explicit ProGuard rules.

Current plan expects:
- OkHttp consumer rules sufficient
- browser likely no extra rules
- `app/proguard-rules.pro` only touched if release build proves necessity.

Preserve that evidence-driven rule.

Do not add speculative keep rules.

---

# 16. Automated tests

Determine whether Block 10 needs code tests at all if it only:
- adds INTERNET
- adds browser dependency/launcher infrastructure.

If a launcher is created:
- plan JVM/instrumented tests appropriate to its Android nature
- no actual browser/network launch in automated tests
- use fake context/launcher seam if feasible.

If Block 10 is manifest/dependency-only:
- no fake test file should be invented just to increase coverage.

No real server test in Block 10 unless current authoritative plan explicitly says otherwise.

---

# 17. Manual validation after Block 10

Define exactly what the user can/should manually verify after implementation.

Likely:
- app launches normally
- existing offline features still work
- Wackelbild preview/tilt/date still work
- no network operation is user-triggerable yet if Block 11 owns CTA
- no browser opens
- no consent path accidentally bypassed.

Do NOT tell the user to perform a real pilot upload yet unless Block 10's current plan explicitly activates the UI path.

The current implementation plan appears to place first real happy-path pilot run in Block 11. Verify this.

---

# 18. Block 10 file scope

After full analysis, enumerate exact files.

Potential candidates from the plan:

- `app/src/main/AndroidManifest.xml`
- `gradle/libs.versions.toml` (only if browser dependency is added)
- `app/build.gradle.kts` (only if browser dependency is added)
- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildCustomTabLauncher.kt` (only if Block 10 owns it)
- exact test file if launcher requires it
- `docs/IMPLEMENTATION_NOTES.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` only if actual current plan needs a factual correction
- `app/proguard-rules.pro` only conditionally if release build proves required — not pre-authorized without evidence.

Do not assume all candidates are needed.

List exact paths and responsibilities.

---

# 19. Explicit files that should normally remain untouched

Unless current source-of-truth proves otherwise, Block 10 should not modify:

- `WackelbildScreen.kt`
- `WackelbildViewModel.kt`
- `WackelbildHandoffOrchestrator.kt`
- renderer/temp manager
- strings
- MainActivity/navigation
- API client
- BuildConfig key injection
- camera/session code
- Privacy Policy website files (not in this repo)
- Play Console configuration.

Any exception requires explicit justification.

---

# 20. Verification commands for future implementation

At minimum assess:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease
git diff --check
git status --short
```

Add exact merged-manifest inspection command(s).

Assess whether:
- `lintDebug`
- `lintRelease`
- `compileDebugAndroidTestKotlin`
- Managed Device/instrumentation

are warranted by the exact final Block-10 scope.

Because INTERNET/manifest/release behavior is a high-risk area, err toward explicit verification, but do not run unrelated expensive tests without reason.

---

# 21. Security checks

Future implementation report must verify:

- no real partner key printed
- no key added to source/docs/manifest
- no key in URL
- no cleartext HTTP fallback
- no analytics/telemetry
- no background uploader/service
- no new unrelated permission
- no unexpected exported component
- no operation can start before explicit user consent.

---

# 22. Required final response

Return exactly:

## 1. Repository Baseline

## 2. Source-of-Truth Verification

Include the exact current Block-10 definition from the plan.

## 3. Governance Gate

Confirm whether the approved network exception is present and sufficient.

## 4. Current Network Reachability

Prove whether any current UI/caller can invoke the real operation before Block 11.

## 5. Final Block-10 Boundary

Separate:
- implement in Block 10
- defer to Block 11+
- defer to Block 14/compliance.

## 6. Manifest Change

Exact permission and exact non-changes.

## 7. Network Security

State whether any networkSecurityConfig / usesCleartextTraffic change is needed.

## 8. Browser Dependency / Custom Tab Infrastructure

State exactly:
- whether androidx.browser belongs in Block 10
- exact version if locked
- whether launcher class belongs in Block 10
- raw launcher behavior.

## 9. Consent Safety

Prove no API request can occur before explicit consent.

## 10. Release / Partner-Key Behavior

Debug pilot / release blank / release env cases.

## 11. Documentation Impact

Exact docs changed now vs deferred.

## 12. Play/Privacy Status

Clearly distinguish code-merge safety from Play-release readiness.

## 13. Files Proposed for Modification / Creation

Table:

| File | Modify/Create | Exact change | Why |

Enumerate every file.

## 14. Files Explicitly Not Touched

## 15. Tests / Verification

Exact automated checks and merged-manifest inspection.

## 16. Manual Validation

Exact device checks after implementation; state whether real pilot upload is still deferred.

## 17. Risks / Blockers

Only genuine Block-10 issues.

## 18. Remaining Open Decisions

If none:

`None`

## 19. Gate Result

If Block 10 is fully safe/specified:

**BLOCK 10A SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

If governance/consent/release behavior is unsafe or ambiguous:

**BLOCK 10A BLOCKED — USER DECISION REQUIRED**

Then STOP.

---

# Final constraints

Analysis only.

This is the first manifest/network-capability block and must be treated as release-critical.

Do not equate "INTERNET permission added" with "feature ready to release."

Do not activate the user-facing upload flow early.

Do not make a real request.

Do not expose the real key.

The goal is one isolated, reviewable Block-10 change that preserves consent, privacy, release stability and the exact Block-11 boundary.
