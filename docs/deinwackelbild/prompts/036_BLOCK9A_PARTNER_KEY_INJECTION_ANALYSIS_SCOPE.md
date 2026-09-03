# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 9A: PARTNER-KEY INJECTION — ANALYSIS + SCOPE CONFIRMATION

## Purpose
Block 8 is committed. Analyze Block 9 only: securely provide the DeinWackelbild partner key to the existing API client.

**ANALYSIS ONLY. NO CODE OR FILE MODIFICATION.**

Do not add INTERNET, make a real network request, expose/search/print the real pilot key, or begin Block 10/UI/Custom Tabs.

## 1. Mandatory source review
Read fully:
- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/IMPLEMENTATION_NOTES.md`

Inspect:
- `app/build.gradle.kts`
- relevant root Gradle files
- `.gitignore`
- local.properties handling **without printing its contents**
- `WackelbildViewModel.kt`
- `OkHttpDeinWackelbildApiClient.kt`
- `DeinWackelbildApiClient.kt`
- relevant Block-7/8 tests
- existing repository patterns for `buildConfigField`, `BuildConfig.`, `Properties()`, `System.getenv`, CI/release secret injection and build-type configuration.

Report branch, HEAD and `git status --short`. Expected: Block 8 committed, no Block-9 implementation. If materially different, STOP.

## 2. Security invariant
A real pilot/test key exists outside the repository. Do not inspect, reproduce, grep for, echo or log it.

Guarantee:
- no real key in tracked source/docs/tests/prompt archives;
- no key in URLs/logs/exceptions/error objects/reports;
- tests use clearly fake credentials only.

## 3. Verify the planned mechanism
The plan previously proposed `local.properties` → BuildConfig, with environment-variable fallback, using a candidate property `DEINWACKELBILD_PARTNER_KEY`.

Verify against the actual repository:
- whether BuildConfig generation is enabled or must be enabled;
- whether `local.properties` is ignored;
- whether CI already exists;
- whether any example config file is actually necessary;
- where local setup should be documented.

Do not blindly implement old pseudocode.

## 4. Critical release-policy question
The supplied credential is explicitly a **test/pilot** key. Accidental inclusion in the production Play release is unacceptable.

Compare and choose one exact minimal safe policy:

A. one generic BuildConfig field for all variants;
B. build-type-specific sources;
C. generic field with an explicit release safety gate;
or another smaller repository-consistent solution.

You MUST explicitly answer:
1. Developer has pilot key in `local.properties` and runs `assembleRelease`: what gets embedded?
2. CI provides a production key: what wins?
3. No production/release key exists: does release build blank/disabled, or fail?

Do not invent needless flavors. But do not allow a local pilot key to leak into release.

If this genuinely requires a user/release-policy decision, mark the gate BLOCKED rather than guessing.

## 5. Secret precedence
Define exact precedence separately where necessary for debug and release.

A local developer pilot key must never silently override a CI/release production credential.

## 6. Missing-key behavior
Block 8 currently has an inert `partnerKey = ""` production placeholder.

Determine the narrowest correct place to reject a blank/missing key:
- API client,
- ViewModel wiring,
- or an existing boundary.

Requirements:
- no startup crash;
- no request with blank credential;
- local deterministic failure when operation starts;
- map to existing `INTEGRATION_UNAVAILABLE` behavior where appropriate;
- no raw technical user message;
- no prefix-based validation unless contract requires it.

Do not create a new abstraction just for this.

## 7. BuildConfig escaping
If using `buildConfigField("String", ...)`, analyze safe Java/Kotlin string-literal escaping. Do not rely blindly on `"\"$value\""` if quotes/backslashes/newlines could break generated source. Use the smallest correct method available without a new dependency.

## 8. local.properties
Confirm it is git-ignored without displaying its contents. Do not modify it in this gate.

Decide whether a tracked example file is necessary. Prefer existing documentation if sufficient. Any example must contain no real or realistic credential.

## 9. CI/release
Inspect actual CI files if present. If absent, do not invent GitHub Actions. Environment-variable support may be documented as an external release-process input.

## 10. Production wiring
Identify the exact Block-8 `partnerKey = ""` construction path and the minimal future replacement, likely BuildConfig-backed.

Do not alter retry/orchestration/API contract/endpoints/timeouts/UI.

## 11. INTERNET boundary
Block 9 must leave `AndroidManifest.xml` unchanged and still without INTERNET. No live API test or socket-based instrumentation.

## 12. Tests
Plan exact coverage, reusing existing tests where possible:
- missing/blank key → `INTEGRATION_UNAVAILABLE`, zero network call;
- fake nonblank key only in Create header;
- key absent from URL;
- uploads do not carry partner key;
- debug BuildConfig wiring compiles;
- release cannot consume a local pilot key accidentally;
- missing release credential behavior is deterministic;
- unit tests require no real key.

Use synthetic fake values only for precedence/build tests.

## 13. Documentation
Identify exact implementation-time updates:
- `DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` partner-key provisioning / Block 9 / §30 open items as applicable;
- `docs/IMPLEMENTATION_NOTES.md` Block 9 entry.

No credential values in docs.

## 14. Proposed file scope
List ALL files required and why. Expected candidates may include:
- `app/build.gradle.kts`
- `WackelbildViewModel.kt`
- relevant existing unit test
- `OkHttpDeinWackelbildApiClient.kt` + test only if blank-key rejection belongs there
- the two docs.

Minimize. No manifest. Justify any additional file.

## 15. Verification plan
For implementation plan at minimum:
```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
git diff --check
git status --short
```
Define safe synthetic-value checks for debug/release source selection without ever printing a real key.

## 16. Required final output
Return exactly:

### 1. Repository Baseline
### 2. Source-of-Truth Findings
### 3. Current Credential Wiring
### 4. Chosen Block-9 Provisioning Design
### 5. Debug / Pilot / Release Policy
### 6. Secret Precedence
### 7. Missing-Key Behavior
### 8. BuildConfig Escaping
### 9. Security Properties
### 10. Files Proposed for Modification / Creation
Table: File | Modify/Create | Exact change | Why
### 11. Tests to Add / Update
### 12. Release Safety
Answer the three questions from §4 explicitly.
### 13. Documentation Impact
### 14. Commands to Run After Implementation
### 15. Risks
### 16. Remaining Open Decisions
If none: `None`
### 17. Gate Result
If resolved:
**BLOCK 9A SCOPE READY — WAITING FOR EXPLICIT APPROVAL**
If release-policy input is needed:
**BLOCK 9A BLOCKED — USER DECISION REQUIRED**

Then STOP.

## Final constraint
Analyze only. Do not implement. The primary safety requirement is: the pilot/test credential may be used deliberately for development/pilot testing later, but there must be no path that accidentally ships it in a production release.
