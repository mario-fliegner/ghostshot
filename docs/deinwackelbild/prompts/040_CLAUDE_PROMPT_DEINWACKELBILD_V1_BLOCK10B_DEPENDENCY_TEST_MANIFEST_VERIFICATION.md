# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 10B: DEPENDENCY / TEST / MERGED-MANIFEST VERIFICATION — ANALYSIS + FINAL SCOPE CONFIRMATION

## Purpose

Block 10A is accepted.

Before implementing Block 10, close the three remaining implementation-time decisions so the actual implementation prompt contains no discretionary choices:

1. exact `androidx.browser` version;
2. exact merged-manifest verification path/tasks for the current AGP setup;
3. whether `WackelbildCustomTabLauncher` gets a dedicated Block-10 test now or is covered only later via Block-11 fake-seam tests.

This gate is **ANALYSIS ONLY**.

Do not modify files.
Do not implement code.
Do not add INTERNET.
Do not add dependencies.
Do not make any network request.
Do not use the real pilot key.
Do not begin Block 11.

---

# 1. Mandatory source review

Re-read:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Inspect current:

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/proguard-rules.pro`
- any existing browser/custom-tab dependency or launcher abstraction
- current AGP/Kotlin/plugin versions
- existing `androidTest` conventions and use of `ApplicationProvider`, `ActivityScenario`, Robolectric, or similar
- current managed-device/instrumentation setup.

Report:

- branch
- HEAD
- `git status --short`

Expected:
- Block 9 committed
- no Block-10 implementation yet.

If materially different, STOP.

---

# 2. Accepted Block-10A decisions to preserve

Preserve unless current repo evidence directly disproves:

- Block 10 owns:
  - INTERNET permission
  - `androidx.browser`
  - `WackelbildCustomTabLauncher`
  - `IMPLEMENTATION_NOTES.md` stale INTERNET-claim correction
  - ProGuard only if release build proves necessary
- Block 11 owns:
  - CTA
  - consent dialog
  - spinner
  - startOperation wiring
  - Custom Tab launch call
  - fallback dialog
  - Back confirmation
  - first real pilot network call
- no current `startOperation()` caller exists in main source
- Block 10 remains capability-only
- no networkSecurityConfig needed
- no usesCleartextTraffic override needed
- no ACCESS_NETWORK_STATE
- no deep link/app link
- no browser WebView fallback
- no release/privacy compliance signoff in Block 10.

Do not reopen these unless a direct contradiction appears.

---

# 3. Resolve exact `androidx.browser` version

The current implementation plan names:

`androidx.browser:browser:1.9.0`

and explicitly says re-verify at actual dependency-addition time.

Do this now.

Use repository-compatible evidence first.

If the exact currently stable version cannot be proven from repository files alone, perform a targeted authoritative web check against AndroidX/Google Maven documentation.

Do not use blog posts or random mirrors.

Report:
- current stable `androidx.browser` version
- whether 1.9.0 is still current/stable
- minSdk compatibility with SameView minSdk 29
- whether any API used by the planned launcher changed.

Lock one exact version for implementation.

Do not leave this open.

---

# 4. Resolve exact merged-manifest verification

Determine the actual current AGP version from the repository.

Then establish the exact reliable verification path for both variants.

Do not assume the intermediate path from memory.

Use one or more of:

- known AGP task outputs
- `./gradlew :app:processDebugMainManifest`
- `./gradlew :app:processReleaseMainManifest`
- `./gradlew :app:processDebugManifest`
- `./gradlew :app:processReleaseManifest`
- Gradle task introspection if needed
- build/intermediates directory inspection.

This analysis gate may run non-mutating Gradle inspection/tasks if necessary.

Do not edit files.

At the end, lock:

## Debug merged manifest
- exact Gradle task
- exact output path or deterministic discovery command

## Release merged manifest
- exact Gradle task
- exact output path or deterministic discovery command

The implementation prompt must be able to say exactly how to inspect:
- permissions
- exported components
- providers/services/activities
- `<queries>`
- cleartext/security attributes.

No guesswork.

---

# 5. Resolve launcher-test decision

Inspect repository test architecture.

Question:

Should Block 10 create a dedicated test for:

`WackelbildCustomTabLauncher`

now?

Evaluate these options:

## A. No dedicated Block-10 launcher test
Justification would be:
- launcher is one trivial Android wrapper
- no Robolectric in repo
- a meaningful test would need instrumentation
- Block 11 already tests launch success/failure through a fake launcher seam
- adding an instrumented test now gives little value.

## B. Dedicated instrumented test now
Justification would require:
- existing project convention supports it cleanly
- test can deterministically validate `ActivityNotFoundException`/Boolean behavior without relying on whatever browser happens to be installed
- no flaky external dependency.

Do not choose B if the test would simply launch a real browser or depend on device image state.

Lock one exact decision.

If no test now, explicitly state that this is intentional and where the behavior is verified later.

---

# 6. Launcher class contract — final lock

Assuming launcher remains in Block 10, lock exact contract.

Expected:

```kotlin
class WackelbildCustomTabLauncher {
    fun launch(context: Context, url: String): Boolean
}
```

Behavior:
- parse provided URL into `Uri`
- build `CustomTabsIntent`
- `launchUrl(context, uri)`
- return true on successful invocation
- catch only `ActivityNotFoundException`
- return false
- do not catch broad `Exception`
- do not alter/rebuild/append to URL
- no WebView fallback
- no logging
- no persistent state.

Determine whether HTTPS validation belongs here.

Given Block-7 parser already guarantees checkoutUrl is valid HTTPS and Block 8 only exposes that validated value, avoid duplicate validation unless required for launcher safety.

Lock exact behavior.

---

# 7. Browser dependency manifest impact

Using the chosen `androidx.browser` version, determine whether its AAR contributes any manifest entries relevant to:

- permissions
- activities
- services
- providers
- exported components
- queries.

Do not assume none.

If repository/local Gradle resolution can inspect the AAR manifest after dependency add only, then make this an explicit implementation-time verification criterion rather than guessing now.

But lock what must be checked.

---

# 8. ProGuard/R8 expectation

Confirm:
- no preemptive change to `app/proguard-rules.pro`
- only modify if `assembleRelease`/`bundleRelease` fails due to proven shrinker issue.

Because implementation prompt file scope must be exact, decide how to handle this:

Preferred:
- do **not** pre-authorize `proguard-rules.pro`
- if release build fails specifically due to browser/R8, implementation must STOP and request scope expansion.

This is stricter and consistent with the project workflow.

Lock this policy.

---

# 9. Exact final Block-10 file scope

After resolving version/test decisions, produce exact required files.

Expected likely set if no dedicated test:

1. `app/src/main/AndroidManifest.xml`
2. `gradle/libs.versions.toml`
3. `app/build.gradle.kts`
4. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildCustomTabLauncher.kt`
5. `docs/IMPLEMENTATION_NOTES.md`

If launcher test is required, add exactly one identified test file.

Do not include:
- proguard-rules unless pre-authorized by actual evidence
- WackelbildScreen
- ViewModel
- HandoffOrchestrator
- strings
- MainActivity
- API client
- plan doc unless version choice deviates from current plan and must be synced.

If chosen browser version differs from 1.9.0, then `DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` must be added to scope to keep source-of-truth consistent. State this explicitly.

---

# 10. Documentation scope

Reconfirm exact stale claims in `docs/IMPLEMENTATION_NOTES.md`.

List exact sections/lines conceptually that must change:
- permission list
- storage/privacy/release-hardening statement
- Block 10 history entry.

Do not touch `RELEASE_HARDENING_AUDIT_V2.md` now; Block 14 owns it.

Do not touch `CLAUDE_PROJECT_INSTRUCTION.md`.

If browser version differs from plan:
- minimally update only the version reference in `DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`.

---

# 11. Exact verification commands

Lock exact post-implementation command set.

At minimum:

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease
git diff --check
git status --short
```

Then exact merged-manifest commands from §4.

Assess whether `compileDebugAndroidTestKotlin` is needed separately if `connectedDebugAndroidTest` already compiles/runs instrumentation.

No real network test.

No browser launch test on physical hardware yet unless launcher-specific manual smoke check is explicitly valuable and non-network.

---

# 12. Manual validation after implementation

Lock exactly:

- app starts
- no runtime INTERNET permission dialog
- existing camera/compare/share/video/GPS still work
- Wackelbild preview/tilt/date still work
- no order CTA yet
- no network request starts
- no Custom Tab opens from UI
- no real pilot upload.

If a launcher class is not wired, there is nothing meaningful to tap-test for it yet.

---

# 13. Release-safety gate

Explicitly state:

Block 10 may be committed to `main` if:
- all builds/tests pass
- merged manifests are clean
- no unexpected permission/exported component appears
- UI still cannot start operation.

But Block 10 is NOT Play-release-ready.

Play release remains blocked until later compliance/release-hardening work is complete.

---

# 14. Required final output

Return exactly:

## 1. Repository Baseline

## 2. Accepted Block-10A Decisions

## 3. Browser Version Decision

State exact version and evidence.

## 4. Merged-Manifest Verification

State exact debug/release tasks and output paths/discovery commands.

## 5. Launcher Test Decision

One exact decision, no options left.

## 6. Final Launcher Contract

Exact class/method behavior.

## 7. ProGuard Policy

State no preemptive change; stop if R8 proves need.

## 8. Final Files Proposed

Table:

| File | Modify/Create | Exact change | Why |

Must be exact.

## 9. Documentation Impact

## 10. Verification Commands

## 11. Manual Validation

## 12. Release Safety

## 13. Remaining Open Decisions

If none:

`None`

## 14. Gate Result

If fully resolved:

**BLOCK 10B FINAL SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

Otherwise:

**BLOCK 10B BLOCKED — USER DECISION REQUIRED**

Then STOP.

---

# Final constraints

Analysis only.

Close all implementation-time choices.

No code.
No manifest edit.
No dependency edit.
No INTERNET yet.
No real key.
No real network.
No Block 11 UI.

The result must be a fully deterministic Block-10 implementation scope with no discretionary decisions left.
