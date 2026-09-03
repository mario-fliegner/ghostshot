# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 10: NETWORK ACTIVATION / MANIFEST / CUSTOM TAB INFRASTRUCTURE — IMPLEMENTATION APPROVAL

## Authorization

Block 10B final scope is approved.

Implement **exactly** the confirmed Block-10 scope.

This is the first manifest/network-capability change for the DeinWackelbild integration.

Do not begin Block 11.
Do not wire the UI.
Do not call `startOperation()`.
Do not launch the Custom Tab from any current screen.
Do not make any real API request.
Do not use or expose the real partner key.

The objective is limited to:

- add `INTERNET`
- add `androidx.browser:browser:1.10.0`
- add the thin, unwired `WackelbildCustomTabLauncher`
- correct the now-stale INTERNET claims in `IMPLEMENTATION_NOTES.md`
- update the plan's browser version from 1.9.0 → 1.10.0

Nothing more.

---

# 1. Mandatory baseline check

Before editing, read:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Then report:

- branch
- HEAD
- `git status --short`

Expected:

- Block 9 committed
- no Block-10 implementation yet
- prompt archive may be untracked.

If materially different, STOP and report.

Also reconfirm before editing:

- no `startOperation()` caller exists in `app/src/main`
- no `confirmFallbackAndContinue()` caller exists in `app/src/main`
- no `WackelbildCustomTabLauncher` already exists.

---

# 2. Exactly authorized files

Modify/create exactly these six files:

1. `app/src/main/AndroidManifest.xml`
2. `gradle/libs.versions.toml`
3. `app/build.gradle.kts`
4. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildCustomTabLauncher.kt`
5. `docs/IMPLEMENTATION_NOTES.md`
6. `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

No seventh file is authorized.

If an additional file appears necessary, STOP and report before changing it.

`app/proguard-rules.pro` is explicitly **not** pre-authorized.

If `assembleRelease` or `bundleRelease` proves that a keep rule is genuinely required, STOP and report the exact shrinker failure before touching ProGuard.

---

# 3. Manifest change

In:

`app/src/main/AndroidManifest.xml`

add exactly:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Place it with the existing `<uses-permission>` declarations, before the `<uses-feature>` block, preserving the file's established ordering/style.

Do not add or modify any other permission.

Do not add:

- `ACCESS_NETWORK_STATE`
- foreground-service permission
- storage/media permission
- package visibility `<queries>`
- deep link/app link
- exported component
- network security config
- `usesCleartextTraffic`
- service/provider/activity.

`ACCESS_NETWORK_STATE` already exists transitively in merged manifests via Media3 and is pre-existing, not a Block-10 addition.

---

# 4. Network security

Do not add:

- `networkSecurityConfig`
- `android:usesCleartextTraffic="false"`

SameView minSdk 29 already has the platform default that disallows cleartext traffic, and DeinWackelbild URLs are HTTPS-only by contract and existing client validation.

No redundant security XML.

---

# 5. Browser dependency

In:

`gradle/libs.versions.toml`

add exactly the stable dependency:

`androidx.browser:browser:1.10.0`

using the repository's current version-catalog naming/style.

Expected conceptually:

```toml
androidxBrowser = "1.10.0"
```

and:

```toml
androidx-browser = { module = "androidx.browser:browser", version.ref = "androidxBrowser" }
```

Use actual repo naming convention if slightly different.

In:

`app/build.gradle.kts`

add exactly:

```kotlin
implementation(libs.androidx.browser)
```

at the appropriate dependency-group location.

Do not add:

- other browser artifacts
- WebKit
- Retrofit
- logging
- additional AndroidX browser helpers.

---

# 6. Custom Tab launcher

Create:

`app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildCustomTabLauncher.kt`

Implement exactly this narrow contract:

```kotlin
class WackelbildCustomTabLauncher {
    fun launch(context: Context, url: String): Boolean =
        try {
            CustomTabsIntent.Builder()
                .build()
                .launchUrl(context, Uri.parse(url))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
}
```

Exact formatting/import layout may follow project style.

Requirements:

- accepts plain `Context`
- accepts raw `String`
- parses with `Uri.parse`
- launches exact provided URL
- returns `true` if launch invocation succeeds
- catches **only** `ActivityNotFoundException`
- returns `false` for that case
- no broad `Exception` catch
- no logging
- no WebView fallback
- no URL reconstruction
- no query/fragment modification
- no host rewrite
- no state
- no analytics
- no persistence.

Do **not** add HTTPS validation here; checkout URL is already validated upstream.

Do **not** wire this class anywhere yet.

There must be zero references to this launcher from:

- `WackelbildScreen.kt`
- `WackelbildViewModel.kt`
- `MainActivity.kt`
- navigation
- any production call site.

Block 11 owns the first actual call.

---

# 7. No launcher test file

Do not create a dedicated Block-10 launcher test.

This decision is locked.

Reason:
- no Robolectric in repo
- real Android launcher behavior is device/browser dependent
- Block 11 will test launch success/failure deterministically through the fake launcher seam.

Do not add an instrumentation test that depends on whether the emulator/device has a browser.

---

# 8. Implementation Notes update

Modify:

`docs/IMPLEMENTATION_NOTES.md`

Correct exactly the stale current statements that say:

- `No INTERNET permission`
- `The app has no INTERNET permission`

They become false once Block 10 lands.

Replace them with accurate wording that reflects:

- `INTERNET` is now declared solely for the approved DeinWackelbild/Hosted Comparison network exception as governed by project instructions
- no DeinWackelbild network call is currently user-triggerable yet because Block 11 UI wiring is still absent
- no background networking/analytics/tracking has been added.

Also append one concise Block-10 implementation-history entry, matching existing Block 7/8/9 entry style:

- INTERNET permission added
- androidx.browser 1.10.0 added
- Custom Tab launcher infrastructure added but not wired
- no live request
- no UI activation
- release/privacy hardening still pending later blocks.

Do not rewrite unrelated historical entries.

---

# 9. Implementation plan version sync

Modify:

`docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`

Only update the relevant Browser/Custom-Tab version reference:

- `androidx.browser 1.9.0` → `1.10.0`

Preserve all other Block-10/11 architecture and sequencing.

If the plan contains multiple factual references to the exact locked browser version, update only those necessary for internal consistency.

Do not rewrite unrelated sections.

---

# 10. Explicitly forbidden changes

Do not modify:

- `app/proguard-rules.pro`
- `WackelbildScreen.kt`
- `WackelbildViewModel.kt`
- `WackelbildHandoffOrchestrator.kt`
- `WackelbildPrintRenderer.kt`
- `WackelbildTempFileManager.kt`
- strings
- `MainActivity.kt`
- navigation
- API client
- DTOs
- BuildConfig partner-key wiring
- camera/session files
- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/RELEASE_HARDENING_AUDIT_V2.md`
- Privacy Policy files
- Play Console config.

Do not add:

- UI CTA
- consent dialog
- spinner
- fallback dialog
- Back-confirmation
- `startOperation()` caller
- `confirmFallbackAndContinue()` caller
- Custom Tab launch caller
- real pilot request.

---

# 11. Pre/post network-reachability check

Before and after implementation, verify in `app/src/main`:

- `startOperation()` still has no caller
- `confirmFallbackAndContinue()` still has no caller
- `WackelbildCustomTabLauncher.launch(...)` has no caller

The app may now have socket capability, but the user-facing flow must remain inert.

If any current call path is found, STOP and report.

---

# 12. Merged-manifest verification

After implementation, run:

```bash
./gradlew :app:processDebugMainManifest
./gradlew :app:processReleaseMainManifest
```

Inspect exactly:

### Debug merged manifest

`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`

Blame report:

`app/build/intermediates/manifest_merge_blame_file/debug/processDebugMainManifest/manifest-merger-blame-debug-report.txt`

### Release merged manifest

`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`

Blame report:

`app/build/intermediates/manifest_merge_blame_file/release/processReleaseMainManifest/manifest-merger-blame-release-report.txt`

Report:

- `INTERNET` exists in both merged manifests
- its attribution is the app manifest
- pre-existing `ACCESS_NETWORK_STATE` remains attributed to Media3, not browser/app
- whether `androidx.browser:browser:1.10.0` contributes:
  - permission
  - activity
  - service
  - provider
  - exported component
  - `<queries>`
- whether any cleartext/security attribute changed.

Do not print secret BuildConfig values during manifest inspection.

---

# 13. Automated verification

Run all of:

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease
./gradlew :app:processDebugMainManifest
./gradlew :app:processReleaseMainManifest
git diff --check
git status --short
```

`connectedDebugAndroidTest` is explicitly required by the current Block-10 plan to prove the permission/dependency addition did not change observable existing behavior.

Do not suppress failures.

Do not disable tests.

Do not add baseline/suppressions.

If `connectedDebugAndroidTest` cannot run because no device is attached, use the project's already-configured Managed Device fallback only if that is consistent with current repo conventions, and report exactly what ran.

If a test fails:
- investigate exact cause
- do not alter unrelated code
- do not weaken tests
- stop if scope expansion is required.

---

# 14. Release / R8 verification

`assembleRelease` and `bundleRelease` must pass.

Do not touch ProGuard preemptively.

If R8/shrinker fails due specifically to `androidx.browser` or existing OkHttp interaction:

STOP.

Report:
- exact failure
- exact missing class/rule evidence
- why `app/proguard-rules.pro` would be required.

Do not implement that change without separate approval.

---

# 15. Manual validation status

Do not perform a real pilot upload.

Do not make a live network request.

The implementation report must state that real-device/manual validation after Block 10 is limited to:

- app starts normally
- no runtime INTERNET permission dialog
- existing offline features remain functional
- Wackelbild preview/tilt/date still work
- no CTA exists
- no network call starts
- no Custom Tab opens.

The first real DeinWackelbild pilot upload remains Block 11.

---

# 16. Release/compliance boundary

Block 10 may be committed to `main` if all tests/builds/merged-manifest checks pass.

But explicitly state:

**Block 10 is not Play-release-ready.**

Still pending before shipping the activated feature:

- Google Play Data Safety review/update
- Privacy Policy disclosure
- partner/commission disclosure review
- formal `RELEASE_HARDENING_AUDIT_V2.md` correction in Block 14.

Do not modify those now.

---

# 17. Required final report

Return exactly:

## 1. Repository Baseline

- branch
- HEAD
- initial status

## 2. Files Modified / Created

List exact files.

Confirm exactly six authorized files changed.

## 3. Manifest Change

Report:
- exact INTERNET line
- no other source-manifest changes.

## 4. Browser Dependency

Report:
- `androidx.browser:browser:1.10.0`
- version catalog entry
- app dependency line
- no extra dependency.

## 5. Custom Tab Launcher

Report:
- exact contract
- no production caller
- no launcher test by design.

## 6. Network Reachability

Confirm:
- no `startOperation()` caller
- no `confirmFallbackAndContinue()` caller
- no launcher caller
- no real request possible from UI yet.

## 7. Merged Manifest Verification

Report debug/release:
- INTERNET attribution
- ACCESS_NETWORK_STATE attribution
- any browser manifest contribution
- exported components
- queries
- cleartext/security attributes.

## 8. Documentation

Report:
- stale INTERNET statements corrected
- Block 10 history entry
- plan version 1.10.0 sync.

## 9. Tests / Build Verification

Report exact result for:
- testDebugUnitTest
- connectedDebugAndroidTest or exact managed-device fallback
- assembleDebug
- assembleRelease
- bundleRelease
- both manifest tasks
- diff check
- status.

## 10. R8 / ProGuard

Confirm no ProGuard change was needed, or STOP/report if evidence proved otherwise.

## 11. Security / Privacy

Confirm:
- no real key printed
- no key in source/docs/manifest
- no logging
- no cleartext fallback
- no analytics/telemetry
- no background uploader
- no unrelated permission.

## 12. Block Boundary Confirmation

Confirm no:
- UI wiring
- CTA
- consent
- spinner
- fallback dialog
- Back-confirmation
- real Custom Tab launch
- live network call.

## 13. Manual Validation Status

State what was and was not manually validated.

## 14. Diff Scope

Confirm no unauthorized edits.

## 15. Remaining Work

State:
- Block 11 UI wiring + first real pilot request
- later error polish if scheduled
- Block 14 release/privacy hardening/compliance.

## 16. Release Safety

State explicitly:
- safe to commit to main if all checks pass
- not Play-release-ready.

## 17. Gate Result

If successful:

**BLOCK 10 IMPLEMENTED — READY FOR REVIEW**

If blocked:

**BLOCK 10 BLOCKED — USER DECISION REQUIRED**

Then STOP.

---

# Final instruction

Implement exactly Block 10.

One manifest capability.
One browser dependency.
One thin unwired launcher.
Two doc corrections.

No UI.
No live request.
No secret exposure.
No speculative ProGuard change.
No Block 11.
