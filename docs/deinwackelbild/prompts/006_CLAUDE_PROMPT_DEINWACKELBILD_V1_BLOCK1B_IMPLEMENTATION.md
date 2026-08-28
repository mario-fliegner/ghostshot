# CLAUDE PROMPT — DEINWACKELBILD V1 — BLOCK 1B: IMPLEMENTATION

## Role

You are working in the existing SameView Android repository.

Block 1A analysis/scope has been reviewed and approved with two explicit scope corrections:

1. **Do NOT add `isWackelbildAvailable`.**
2. In `docs/COMPARE_FLOW_V1.md`, do **not** change the existing stale `Share video` wording in this block. Only document the new divider + Wackelbild menu entry.

This prompt authorizes implementation of **Block 1 only**.

Do not begin Block 2.
Do not add navigation.
Do not add a Wackelbild screen.
Do not add network, manifest, Gradle, sensor, image, API, or Custom Tab code.

Implement exactly the approved Block 1 scope and nothing else.

---

# 1. Authoritative Inputs

Read before changing anything:

- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/COMPARE_FLOW_V1.md`

Also inspect the current versions of:

- `app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt`
- `app/src/androidTest/java/com/isardomains/sameview/ui/compare/CompareScreenTest.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de/strings.xml`

If any repository state differs materially from the approved Block 1A analysis, STOP and report the difference before editing.

---

# 2. Repository Baseline

Before modification, report privately to yourself / include in final report:

- branch
- HEAD
- `git status --short`

Do not touch unrelated pre-existing changes or untracked files.

---

# 3. Exact Authorized File Scope

You may modify exactly these five files:

1. `app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt`
2. `app/src/main/res/values/strings.xml`
3. `app/src/main/res/values-de/strings.xml`
4. `app/src/androidTest/java/com/isardomains/sameview/ui/compare/CompareScreenTest.kt`
5. `docs/COMPARE_FLOW_V1.md`

No other file may be modified.

If implementation requires any sixth file, STOP and report why. Do not expand scope.

---

# 4. Production Change — `CompareScreen.kt`

Implement only the following additive changes:

## 4.1 New callback

Add exactly one new optional callback parameter to `CompareScreen`:

`onCreateWackelbild: (() -> Unit)? = null`

Place it with the existing export-related callback parameters, immediately after the current Share Image parameters and before `referenceDate`, preserving the current parameter grouping.

Do **not** add:

- `isWackelbildAvailable`
- any availability flag
- any feature flag
- any session file check
- any connectivity check

The existing `sessionId != null` gate is the only visibility gate for this menu item in Block 1.

## 4.2 Divider

Inside the existing Export `DropdownMenu`, after the existing `Create video` item and before the new Wackelbild item, add:

`HorizontalDivider()`

Add the required Material3 import only.

Do not change the existing Share Image or Create Video menu items.

## 4.3 New menu item

Add a third `DropdownMenuItem` after the divider.

Requirements:

- text resource: `R.string.export_menu_create_wackelbild`
- enabled: always `true`
- click behavior:
  1. `showExportMenu = false`
  2. `onCreateWackelbild?.invoke()`
- test tag:
  `compare_screen_export_wackelbild_item`

Do not add icons, badges, price text, partner name, or extra styling.

Do not alter any other top-bar/menu behavior.

---

# 5. String Resources

Modify only the two existing string resource files.

## English

In:

`app/src/main/res/values/strings.xml`

add:

`export_menu_create_wackelbild = "Create lenticular print"`

Place it adjacent to the existing export menu strings.

## German

In:

`app/src/main/res/values-de/strings.xml`

add:

`export_menu_create_wackelbild = "Wackelbild erstellen"`

Place it adjacent to the existing export menu strings.

Do not change any existing string values in this block.

---

# 6. Instrumentation Tests — `CompareScreenTest.kt`

Add only additive tests for the new menu action.

Do not rewrite or weaken any existing test.

The test coverage for Block 1 must prove:

1. With a non-null valid `sessionId`, opening the Export menu shows:
   - existing Share Image item,
   - existing Create Video item,
   - new Wackelbild item.
2. The Wackelbild item appears after the existing Create Video item and after the divider.
3. Tapping the new item invokes `onCreateWackelbild` exactly once.
4. Tapping the new item does not invoke:
   - `onCreateVideo`
   - `onShareComparisonImage`
5. The menu closes after tapping the Wackelbild item, following the existing close-before-invoke convention.
6. With `sessionId == null`, the Export button remains absent and the Wackelbild item is therefore also absent.

Do **not** add enabled/disabled tests for `isWackelbildAvailable`, because that parameter must not exist.

Prefer the existing Compose-test patterns in `CompareScreenTest.kt`.

Do not introduce a new testing library.

If divider ordering cannot be robustly asserted via a stable semantics/test API without brittle child-index assumptions, prefer proving menu item order through the stable semantics tree structure already available in the test file rather than adding production-only tags to the divider. Do not add a divider test tag unless existing project conventions clearly require it.

---

# 7. Documentation — `docs/COMPARE_FLOW_V1.md`

Update only the relevant Export-dropdown section to document:

- existing first item remains unchanged;
- existing second item remains unchanged in the document text for this block;
- divider added after the second item;
- third item: `Wackelbild erstellen`.

Important scope rule:

Do **not** correct the pre-existing stale `Share video` wording in this block, even if the actual UI currently says `Create video`.

That stale wording is outside this implementation fix.

Do not modify unrelated sections.

---

# 8. Files Explicitly Forbidden

Do not modify:

- `app/src/main/java/com/isardomains/sameview/MainActivity.kt`
- any navigation route/constants
- any new `WackelbildScreen.kt`
- any new `WackelbildViewModel.kt`
- AndroidManifest
- Gradle files
- ProGuard
- network/API code
- OkHttp
- `androidx.browser`
- Custom Tabs
- sensor code
- image renderer code
- date overlay code
- temp-file code
- `docs/IMPLEMENTATION_NOTES.md`
- `docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`
- `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
- `docs/CLAUDE_PROJECT_INSTRUCTION.md`
- any unrelated documentation.

No formatting cleanup.
No refactoring.
No renames.
No code movement.

---

# 9. Regression Safety

Existing behavior must remain unchanged:

- Share Image item wording/state/callback.
- Create Video item wording/state/callback.
- Export button visibility.
- sessionId gating.
- menu open/close behavior.
- CompareScreen state.
- top-bar layout outside the new third item.
- all existing tests.

The only new production behavior is:

`Share/Export menu → divider → Wackelbild erstellen → optional callback`

No navigation occurs yet because `MainActivity` remains unchanged in Block 1.

---

# 10. Verification

After implementation, run:

1. `./gradlew testDebugUnitTest`
2. the narrowest relevant CompareScreen instrumentation test command available in this repository;
3. if a narrow class/method run is not practical, run the relevant configured Managed Device task covering `CompareScreenTest`;
4. `./gradlew assembleDebug`
5. `git diff --check`
6. `git status --short`

Do not suppress failing tests.

Do not modify existing tests to make failures disappear.

If any existing test fails, investigate the Block 1 change only.

Real-device validation is **not required** for Block 1.

Also inspect the final diff and confirm exactly the five authorized files changed.

---

# 11. Required Final Report

Return exactly these sections:

## 1. Repository Baseline

- branch
- HEAD
- initial working-tree state

## 2. Files Modified

List exactly the files modified.

State explicitly whether any unauthorized file changed.

## 3. Implementation Summary

Describe:

- callback added;
- divider placement;
- menu item behavior;
- string resources;
- test additions;
- documentation update.

Explicitly confirm:

- no `isWackelbildAvailable` was added;
- `MainActivity.kt` was not touched;
- no navigation was added;
- no stale `Share video` wording was corrected in `COMPARE_FLOW_V1.md`.

## 4. Regression Safety

Confirm existing Share Image / Create Video behavior was not changed.

## 5. Tests / Verification

Report exact commands and results:

- unit tests
- instrumentation / Managed Device test
- assembleDebug
- `git diff --check`
- final `git status --short`

State any command not run and why.

## 6. Diff Scope

Confirm exactly the authorized five files are in the diff and no unrelated edits exist.

## 7. Remaining Work

State only:

- Block 2 will add the real Wackelbild destination and navigation wiring.
- No Block 2 work was performed here.

## 8. Gate Result

Choose exactly one:

- **BLOCK 1 COMPLETE — READY FOR REVIEW**
- **BLOCK 1 INCOMPLETE — USER DECISION REQUIRED**

Do not begin Block 2 automatically.

---

# Final Rule

Implement exactly Block 1.

Five files maximum.

No navigation.
No screen.
No network.
No manifest.
No Gradle.
No sensor/image logic.
No unrelated documentation cleanup.

If anything requires scope expansion, stop and report it instead of implementing it.
