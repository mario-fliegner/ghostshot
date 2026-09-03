# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 9B: PARTNER-KEY SCOPE CORRECTION — ANALYSIS + FINAL SCOPE CONFIRMATION

## Purpose

Block 9A analysis is largely accepted, but two scope corrections are required before implementation:

1. Do **not** create a new `PARTNER_KEY_SETUP.md`.
2. Do **not** modify or overwrite the user's real `local.properties` for synthetic verification.

This gate is **ANALYSIS + SCOPE CORRECTION ONLY**.

Do not modify files.
Do not implement code.
Do not add `INTERNET`.
Do not use or reveal the real pilot key.
Do not make any network request.
Do not begin Block 10.

The goal is to reduce Block 9 to the smallest safe implementation scope while preserving the accepted debug/release credential policy from Block 9A.

---

# 1. Mandatory source review

Re-read:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Inspect current:

- `app/build.gradle.kts`
- `.gitignore`
- current handling of `local.properties` **without printing or modifying its contents**
- `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/OkHttpDeinWackelbildApiClient.kt`
- `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`
- `app/src/test/java/com/isardomains/sameview/net/deinwackelbild/OkHttpDeinWackelbildApiClientTest.kt`

Report:

- branch
- HEAD
- `git status --short`

Expected:

- Block 8 committed
- no Block-9 production code yet
- prompt archive may be untracked.

If materially different, STOP.

---

# 2. Accepted Block-9A decisions to preserve

Unless repository evidence directly disproves them, preserve:

## Debug / non-release policy

Resolution order:

1. `local.properties` key
2. environment variable
3. blank string

## Release policy

Resolution order:

1. environment variable only
2. blank string

**Release must never consult `local.properties`.**

This is the critical safety rule preventing a developer's local pilot/test key from entering a release APK/AAB.

## Missing-key behavior

`OkHttpDeinWackelbildApiClient.createHandoff()` should reject blank partner key locally before request construction and return:

`INTEGRATION_UNAVAILABLE`

No network call.

## Production ViewModel wiring

Replace Block 8's inert:

`partnerKey = ""`

with:

`BuildConfig.DEINWACKELBILD_PARTNER_KEY`

or the exact equivalent consistent with package/imports.

## BuildConfig string escaping

Use a small local Gradle escaping helper for:
- backslash
- double quote
- newline
- carriage return

No new dependency.

---

# 3. Correction A — NO new PARTNER_KEY_SETUP.md

Reject the Block-9A proposal to create:

`docs/deinwackelbild/PARTNER_KEY_SETUP.md`

Reason:

- one property does not justify a third source of truth;
- the existing implementation plan already has the authoritative partner-key provisioning section;
- `IMPLEMENTATION_NOTES.md` already acts as implementation ledger;
- an additional setup document would create needless duplication and maintenance risk.

Therefore the implementation documentation scope must be limited to:

- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Do not create any new documentation file.

---

# 4. Correction B — NEVER modify the user's real local.properties for tests

The implementation/verification must not:

- edit `local.properties`
- overwrite `local.properties`
- temporarily replace it
- append a synthetic test key
- rename it
- back it up and restore it
- print its contents
- grep its contents for the real key.

Treat it as user-owned local configuration.

Verification must instead rely on:

- static inspection of Gradle build-type resolution logic;
- synthetic environment variables;
- unit tests for runtime blank-key behavior;
- build success for debug/release;
- safe generated-BuildConfig inspection that does not reveal any real secret.

If synthetic local-property precedence cannot be proven without touching the real file, do **not** attempt it. The code structure itself plus release-only env resolution is sufficient to prove the release leak path is closed.

---

# 5. Final intended Gradle policy

Reconfirm the smallest implementation structure in `app/build.gradle.kts`.

Expected conceptual behavior:

```text
read local.properties once

debug/non-release BuildConfig value:
    local property
    else env var
    else ""

release BuildConfig value:
    env var only
    else ""
```

Release expression must not reference the local property variable at all.

This should be achieved using the existing:
- `defaultConfig`
- `buildTypes.release`

without adding flavors.

Confirm whether defaultConfig applies to debug and release first and release overrides it; if yes, explicitly document that the release override is authoritative and safe.

Do not add a custom plugin or new Gradle file.

---

# 6. Release build behavior

Final policy must answer exactly:

### Case 1
Developer has only a pilot/test key in `local.properties`, no env var, runs:

`./gradlew assembleRelease`

Expected:

- release BuildConfig key = blank
- pilot key is not embedded.

### Case 2
Developer/CI has a production key in environment variable.

Expected:

- release BuildConfig key = env value
- local property is irrelevant.

### Case 3
No release env key.

Expected:

- release build succeeds
- BuildConfig key = blank
- integration fails locally with `INTEGRATION_UNAVAILABLE` only when operation starts
- no startup crash.

Preserve this exact policy unless the current source of truth explicitly requires release-build failure instead.

---

# 7. Missing-key guard location

Reconfirm:

`OkHttpDeinWackelbildApiClient.createHandoff()`

is the narrowest correct boundary.

Required behavior:

- `partnerKey.isBlank()`
- return `Failure(INTEGRATION_UNAVAILABLE)`
- before `Request.Builder()`
- before `callFactory.newCall(...)`
- no socket/request attempt.

Do not:
- validate prefix
- validate key length
- log the key
- throw at startup.

---

# 8. Final file scope

The corrected implementation should require exactly these six files:

1. `app/build.gradle.kts`
2. `app/src/main/java/com/isardomains/sameview/net/deinwackelbild/OkHttpDeinWackelbildApiClient.kt`
3. `app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt`
4. `app/src/test/java/com/isardomains/sameview/net/deinwackelbild/OkHttpDeinWackelbildApiClientTest.kt`
5. `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
6. `docs/IMPLEMENTATION_NOTES.md`

Reinspect the repo and confirm no seventh file is actually required.

Do not add:
- new setup docs
- example properties files
- CI workflow
- manifest
- strings
- ViewModel test unless genuinely required by compile/test breakage.

If a seventh file is required, mark Block 9B blocked and explain why.

---

# 9. Tests — corrected scope

Required test changes:

## `OkHttpDeinWackelbildApiClientTest.kt`

Add:

- blank partner key → `INTEGRATION_UNAVAILABLE`
- zero `Call.Factory` invocation / zero request creation path
- existing fake nonblank-key tests remain green
- existing no-key-in-URL tests remain green.

Do not duplicate existing coverage unnecessarily.

## BuildConfig wiring

No new unit test file required unless repository structure genuinely needs one.

Verification may rely on:
- compile/build success;
- static Gradle logic inspection;
- safe synthetic env build.

Do not add a test solely to read and print BuildConfig values.

---

# 10. Safe synthetic release verification

Use only synthetic environment values, never the real key.

Allowed:

```text
DEINWACKELBILD_PARTNER_KEY=synthetic_release_value
```

for a controlled release build.

Never print actual generated credential values from the user's normal environment.

If verifying whether the release field is blank/nonblank:
- compare against known synthetic value;
- or verify generated source structure without emitting secret text.

Do not echo environment variables.

Do not dump generated BuildConfig in a way that could expose a real key when the synthetic override is absent.

---

# 11. Documentation correction

Update the implementation plan minimally:

## Partner-key provisioning section

Replace/supersede the old flat:

`local.properties → env → blank`

for all variants.

Document:

### Debug/non-release
`local.properties → env → blank`

### Release
`env only → blank`

Explicitly state:
- release never consumes local.properties;
- pilot/test key may live in local.properties for local debug/pilot use only;
- production release key must be supplied externally via environment variable;
- blank release credential leaves integration unavailable rather than breaking startup.

## Open items

If §30 still says the debug/release split is unresolved, close that item.

Retain:
- exact CI mechanism as external/unresolved if no CI exists.

## IMPLEMENTATION_NOTES

Later Block-9 implementation entry only.

No real key.

No new doc file.

---

# 12. BuildConfig escaping

Reconfirm the proposed helper is adequate.

At minimum escape in this order:

1. `\` → `\\`
2. `"` → `\"`
3. newline → `\n`
4. carriage return → `\r`

Confirm this produces a valid quoted `buildConfigField("String", ...)` value.

If the project already has a safer helper or Gradle API for Java-string quoting, prefer it.

Do not add a dependency.

---

# 13. Security checks

The implementation must preserve:

- no real key in git diff
- no real key in test source
- no real key in docs
- no real key in URLs
- no real key in errors
- no logging
- no INTERNET permission yet.

Do not use broad repository grep commands that could print credential values from ignored local files.

If searching tracked files for accidental credentials, restrict to tracked files and report only presence/absence — never matching content.

---

# 14. Verification commands for later implementation

Plan:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
git diff --check
git status --short
```

Plus one safe synthetic release-env build if useful.

No:
- connected Android test
- physical-device test
- live API request
- modification of local.properties.

---

# 15. Required final output

Return exactly:

## 1. Repository Baseline

## 2. Accepted Block-9A Decisions

## 3. Correction A — Documentation Scope

Confirm no `PARTNER_KEY_SETUP.md`.

## 4. Correction B — local.properties Safety

Confirm it will never be modified or printed.

## 5. Final Provisioning Policy

Table:

| Build type | Source precedence | Missing key result |

## 6. Release Safety

Explicitly answer all three cases from §6.

## 7. Missing-Key Guard

Exact location and behavior.

## 8. BuildConfig Escaping

Exact approach.

## 9. Final Files Proposed

Table:

| File | Modify/Create | Exact change | Why |

Must contain exactly six files unless blocked.

## 10. Tests

Exact test changes.

## 11. Documentation Impact

Exact plan sections and notes.

## 12. Verification Plan

Exact commands and synthetic-check constraints.

## 13. Security Confirmation

Confirm no secret exposure path introduced.

## 14. Remaining Open Decisions

If none:

`None`

Otherwise list only real external release-process items that do not block implementation.

## 15. Gate Result

If fully corrected:

**BLOCK 9B CORRECTED SCOPE READY — WAITING FOR EXPLICIT APPROVAL**

If a seventh file or user decision is required:

**BLOCK 9B BLOCKED — USER DECISION REQUIRED**

Then STOP.

---

# Final constraints

Analysis only.

Correct exactly the Block-9A scope:

- no new setup document;
- never touch real local.properties;
- keep debug local-key convenience;
- structurally exclude local.properties from release;
- release env only;
- blank key fails locally at Create;
- no INTERNET yet.
