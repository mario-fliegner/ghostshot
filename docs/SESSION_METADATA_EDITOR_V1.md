# SESSION_METADATA_EDITOR_V1.md

## 1. Document Status

This document is the **authoritative UX and feature specification** for the Session Metadata Editor in SameView.

It is written for:
- AI coding systems
- Implementation sessions
- Design review
- Regression-safe follow-up work

If an implementation proposal conflicts with this document, this document wins unless the user makes an explicit product decision to override it.

This document defines:
- Entry point and screen type
- V1 field scope and UX behavior
- Save and navigation contracts
- Validation rules
- Data persistence rules
- Scanner and state requirements
- CompareScreen update requirements

This document does NOT define:
- Metadata storage implementation (defined in `SESSION_METADATA_V1.md`)
- Storage write functions (defined in `SESSION_METADATA_V4_IMPLEMENTATION_PLAN.md`)
- Compare rendering (defined in `COMPARE_SESSION_RENDERING_V1.md`)
- Compare flow and navigation contracts (defined in `COMPARE_FLOW_V1.md`)
- GPS recreation system (defined in `GPS_RECREATION_SYSTEM_V1.md`)

**Revision note:** Initial version. Covers Title, Reference Date, and Location fields only.

---

## 2. Purpose

The Session Metadata Editor allows users to add and correct contextual information about a saved compare session: what it is called, when the reference photo was taken, and where the session took place.

The editor surfaces the user-editable fields introduced in the v4 metadata schema (`content.title`, `reference.date`, `location.*`) in a dedicated, focused editing screen.

The editor does not touch rendering contract fields, system identity fields, GPS coordinates, or any field that is immutable after session creation.

---

## 3. Product Philosophy

### Editor is a Service to the Session Record

A session is a long-lived record of a photographic recreation event. The editor exists to let the user enrich that record with knowledge the system cannot derive automatically — a corrected year, a place name, a meaningful title.

The editor must feel like filling in a form at a desk, not using a control panel in a cockpit.

### Calm and Readable

The editor is a form screen. It must be calm, well-spaced, and easy to read. There is no visual complexity from camera controls, overlays, or live UI. The user can take their time.

### Explicit User Commitment

The user makes deliberate edits and explicitly saves them. There is no silent auto-save on field blur or navigation. The user is always in control of when changes are written.

### Non-Destructive Defaults

Opening the editor never modifies the session. Changes take effect only on explicit Save. Cancelling always leaves the session unchanged.

### Strict V1 Scope

The editor presents exactly the fields defined in V1. It does not present future fields or experimental toggles. It does not offer features that depend on network access, GPS, or extended permissions.

---

## 4. Entry Point

### Overflow Menu in CompareScreen

The editor is entered via the overflow menu (⋮) in `CompareScreen`.

**V1 menu item label:** "Edit Session"

This menu item replaces the existing "Edit Title" entry. The label change from "Edit Title" to "Edit Session" reflects that the editor now covers more than just the title.

**Availability:** The "Edit Session" menu item is shown only when `CompareScreen` has a valid session context (`sessionId != null`). A `CompareScreen` opened without session context (transient compare viewer) must not show "Edit Session".

**Existing overflow menu state before this change:**

```
Edit Title
Remove Title  (only when a title is present)
Backup Session
```

**Updated overflow menu after this change:**

```
Edit Session
Backup Session
```

"Remove Title" is removed from the overflow menu. Title removal is handled inside the editor (clearing the title field and saving).

### No Other Entry Points in V1

The editor has no entry points from:
- `CompareLibraryScreen`
- `CameraScreen`
- Any other screen

Additional entry points are a future extension.

---

## 5. Screen Type and Navigation

### Screen Type

The editor opens as an **opaque fullscreen form screen**.

It is NOT:
- a dialog
- a bottom sheet
- a transparent overlay on top of `CompareScreen`
- a semi-transparent overlay with a blurred background
- an in-place expansion of the overflow menu

The editor is a fully independent screen destination in the navigation graph, visually distinct from `CompareScreen`.

### Background

The screen uses the app's standard opaque surface background. The `CompareScreen` underneath is not visible. The editor background must not scroll when the form content scrolls.

### Form Content

The form content may be scrollable when the screen is too short to display all fields without scrolling (e.g., small screen, landscape mode). The background does not scroll with the content.

### Top App Bar

The editor has a top app bar containing:
- Back navigation icon (left side)
- Screen title: "Edit Session" with subtitle (using string resources)
- Favorite star action (right side, icon button) — see §20
- Save action: located in the **bottom bar** (`Scaffold.bottomBar`), not in the TopAppBar

Note: The original V1 specification described Save as a TopAppBar action. The
implemented architecture places Save in `Scaffold.bottomBar` to accommodate IME
(keyboard) interactions and Expanded-width layout constraints. The TopAppBar `actions`
slot is used exclusively for the Favorite star (§20).

The Save button in the bottom bar is always visible. It must never be hidden or placed
in an overflow menu. It is enabled only when `isDirty == true && isSaving == false`.

### Navigation Flow

```
CompareScreen  →  (⋮ → Edit Session)  →  EditSessionScreen  →  (Save)  →  CompareScreen
CompareScreen  →  (⋮ → Edit Session)  →  EditSessionScreen  →  (Back, no changes)  →  CompareScreen
CompareScreen  →  (⋮ → Edit Session)  →  EditSessionScreen  →  (Back, unsaved changes)  →  Confirm Dialog  →  CompareScreen
```

### Return Destination

After Save or confirmed Cancel: the user returns to `CompareScreen`. The `CompareScreen` must reflect the updated metadata immediately upon return (see Section 15).

---

## 6. V1 Field Scope

The editor presents exactly six editable fields in V1:

| Field | Label (intent) | Storage Path | Nullable |
|---|---|---|---|
| Title | "Title" | `content.title` | Yes |
| Description | "Description" | `content.description` | Yes |
| Reference date | "Reference date" | `reference.date` | Yes |
| Location display name | "Place name" | `location.displayName` | Yes |
| City | "City" | `location.city` | Yes |
| Country | "Country" | `location.country` | Yes |

All six fields are optional. No field is required for a valid session save.

### Card Structure

The form is organized into four `SettingsCard` sections:

- **Session card**: Header shows `"Created <capture date>"` (read-only, derived from `capture.timestampMs`). Editable fields: Title, Description.
- **Reference photo card**: Thumbnail (80 dp, left side). Editable field: Reference date (with DatePicker companion). Help text and validation error displayed below the field at full card width.
- **Current photo card**: Thumbnail (80 dp, left side). Read-only display box (right side) showing the label "Captured on" and the formatted capture date. Not editable.
- **Location card**: Editable fields: Place name, City, Country.

### Field Order

Fields are presented in card order: Session → Reference photo → Current photo → Location. Within each card, fields follow the order listed in the table above. Field order must not be randomized or dynamically reordered.

### Current Photo Card

The Current photo card is a read-only contextual display. It shows the user the capture date of the current photo without providing any editable controls.

- **Thumbnail**: the session's `capture.jpg`, rendered at 80 dp, clipped to a rounded rectangle.
- **Display box**: a non-interactive outlined box (matching `OutlinedTextField` visual weight) containing:
  - Label: "Captured on" (`edit_session_label_captured_on`) in `labelSmall` / `SameViewSettingsSecondaryText`
  - Value: formatted capture date and time (derived from `capture.timestampMs`) in `bodyMedium` / `SameViewSettingsLabelText`
- The display box has no focus state, cursor, click handler, or edit affordance.
- The display box is absent from dirty tracking and the save workflow.

### Pre-Population at Open

When the editor opens, each editable field is pre-populated from the current session metadata:

- `content.title` → pre-fill Title field; empty when absent
- `content.description` → pre-fill Description field; empty when absent
- `reference.date` → pre-fill Reference date field; empty when absent
- `location.displayName` → pre-fill Place name field; empty when absent
- `location.city` → pre-fill City field; empty when absent
- `location.country` → pre-fill Country field; empty when absent
- `capture.timestampMs` → shown read-only in Current photo card; not pre-filled into any editable field

The pre-populated values are the current saved state. They represent the last persisted values, not live ViewModel state.

---

## 7. Explicit Non-Goals

The following are explicitly out of scope for V1 and must not be implemented as part of any task that references this document:

**Fields not in V1 form:**
- Tags (`content.tags`)
- Favorite toggle as a form field — `isFavorite` is surfaced as a TopAppBar action (§20),
  not as an editable form field
- Visibility selector (`additional.visibility`)
- GPS coordinates (any field from `captureLocation` or `referenceLocation`)

**Features not in V1:**
- GPS-based location auto-fill
- Reverse geocoding
- Location search or suggestions
- Upload or sharing from the editor
- Import of metadata from external sources
- Export of metadata from the editor
- Batch editing of multiple sessions

**Storage not in V1:**
- No new metadata storage functions are introduced by this editor beyond what Blocks E and F already provide (`updateReferenceDate()`, `updateLocation()`, and the existing `updateTitle()`)
- No new fields in `metadata.json`
- No new scanner fields exposed to `ScannedSession` beyond what is needed to pre-populate the editor

**Scanner not in V1:**
- The editor does not require a new top-level scanner or session-list re-scan after save
- `SessionScanner` field exposure changes are limited to the minimum needed to load the editor's initial state

---

## 8. Save and Back Behavior

### Explicit Save

The editor uses an **explicit Save model**. No field is saved on blur, on focus change, or on navigation. All changes are written atomically when the user taps Save.

### Save Button Behavior

- The Save button is **enabled** only when `isDirty == true` and `isSaving == false`
- The Save button is **disabled** when no field has been changed from its pre-populated initial value, or while a save operation is in progress
- Tapping Save validates all fields (see Section 11)
- If validation passes: write all changed fields, then navigate back to `CompareScreen`
- If validation fails: show inline field errors; remain on the editor; do not write anything

### Dirty State Tracking

The editor tracks whether any editable field value differs from the pre-populated initial state (after blank normalization). This is the "dirty" state.

Dirty state controls two behaviors:
- **Save button enabled state**: Save is disabled when the editor is not dirty
- **Back navigation dialog**: shows the unsaved-changes confirmation dialog when the editor is dirty

### Back with No Unsaved Changes

If the user presses Back (system back or top-bar back icon) and no field has been changed: navigate back to `CompareScreen` immediately without a confirmation dialog.

### Back with Unsaved Changes

If the user presses Back (system back or top-bar back icon) and at least one field has been changed: show a confirmation dialog.

**Confirmation dialog intent:**

- Title: "Discard changes?"
- Body: "Your changes have not been saved."
- Confirm action: "Discard" (navigates back to `CompareScreen` without saving)
- Cancel action: "Keep editing" (dismisses the dialog; remains on the editor)

The exact string resource keys follow the app's existing i18n conventions.

### After Successful Save

After a successful Save:
- Navigate back to `CompareScreen`
- `CompareScreen` displays the updated session metadata (see Section 15)
- No success snackbar is shown on the editor; the immediate visual update in `CompareScreen` is the implicit confirmation

### After Save Failure

If the storage write fails for any reason:
- Remain on the editor
- Show a snackbar or inline error (see Section 12)
- Do not navigate away
- Do not leave a partially written session

---

## 9. Reference Date UX

### Input Model

Reference date is a **free text field** with format validation. A Material 3 DatePicker is available as a companion entry method via a calendar icon button.

The free text field is the primary input path because year-only (`"2008"`) and year-month (`"2008-06"`) inputs must be supported and cannot be expressed through a day-precision date picker. The DatePicker provides a convenient alternative for full-date (`YYYY-MM-DD`) selection only.

### Supported Formats

The user may enter any of the following:

| Input | Meaning | Example |
|---|---|---|
| `YYYY` | Year only | `2008` |
| `YYYY-MM` | Year and month | `2008-06` |
| `YYYY-MM-DD` | Full date | `2008-06-15` |

No other formats are accepted. The validation rules for these formats are defined in Section 11.

### Input Hint / Placeholder

The field displays a placeholder hint when empty (e.g., `"e.g. 2008 or 2008-06"`) indicating that partial dates are valid. The exact placeholder text uses a string resource.

### Removing the Reference Date

The user may clear the Reference date field entirely. A cleared (empty) field means: remove `reference.date` from the session.

When Save is tapped with an empty Reference date field:
- `reference.date` is removed from `metadata.json`
- `reference.dateSource` is removed from `metadata.json`
- `reference.userEdited` is set to `true` (the user made a deliberate choice to remove the date)

Clearing the date is a valid, intentional user action and requires no confirmation.

### EXIF Pre-Population Behavior

If the session has a reference date that was auto-populated from EXIF (`reference.dateSource = "exif"`), the editor pre-fills the field with the stored EXIF-derived date string. The user may overwrite it, keep it, or delete it.

If the user overwrites or keeps and saves an EXIF-derived date, the storage layer updates `reference.dateSource` to `"manual"` and `reference.userEdited` to `true` (handled by `updateReferenceDate()`).

The editor does not distinguish EXIF-derived from manually-entered dates in the UI in V1. There is no "(from EXIF)" label or indicator in V1.

### Manual Entry Always Wins

Once the user saves a manual value for Reference date, the session's `reference.dateSource` becomes `"manual"` permanently. Future auto-population from EXIF on re-read must never overwrite a manually saved date (enforced at the storage layer per `SESSION_METADATA_V1.md §7.4`).

### DatePicker Companion

A calendar icon button (`Icons.Default.DateRange`) appears as the trailing icon of the Reference date `OutlinedTextField`. Tapping it opens a Material 3 `DatePickerDialog`.

When the user confirms a date in the picker:
- The selected date is converted to `YYYY-MM-DD` format (UTC calendar, zero-padded month and day)
- The Reference date text field is updated with the formatted date string
- Any existing validation error is cleared
- Dirty state is updated accordingly

The DatePicker is day-precision only. Year-only and year-month inputs must be entered via the text field directly. The DatePicker does not replace the text field; it is a convenience shortcut for full-date entry.

---

## 10. Location UX

### Input Model

Location fields are **free text fields** with no format constraints or controlled vocabulary.

The user types whatever they know. The app stores exactly what they type (after trimming leading/trailing whitespace).

### Three Independent Fields

Location Display Name, City, and Country are three independent text fields. The user may fill any combination: all three, just one, or none.

Filling one field does not require filling the others. No field is required by the presence of another field.

### Blank/Empty Handling

A field left blank or containing only whitespace is treated as absent. Blank values are never stored.

At Save time, each location field is sanitized:
- Trimmed (leading/trailing whitespace removed)
- Pasted line breaks replaced with a single space (single-line fields)
- Tabs replaced with a single space
- Zero-width characters (U+200B, U+200C, U+200D, U+FEFF, U+2060) removed
- Bidi / Directional Override characters (U+202A–U+202E, U+2066–U+2069) removed
- Result trimmed again; a result that is empty is treated as null (absent)
- International characters, emojis, and normal punctuation are preserved unchanged
- No length limits are applied

### Removing Location Data

The user may clear one or more location fields. At Save:
- If all three location fields are empty after trimming: the entire `location` block is removed from `metadata.json` (handled by `updateLocation()`)
- If at least one location field is non-empty: only the non-empty fields are written; the others are absent

Location removal requires no confirmation.

### No GPS Coupling

Location fields have no relationship to GPS coordinates:
- `location.displayName`, `location.city`, `location.country` are never auto-populated from GPS coordinates
- No reverse geocoding is performed
- Changing location fields does not affect `captureLocation` or `referenceLocation` in `metadata.json`

This is a hard rule defined by `GPS_RECREATION_SYSTEM_V1.md §12` and `SESSION_METADATA_V1.md §9.2`.

### Display of Existing Location Data

When a session already has location data, the editor pre-fills the corresponding fields from the stored values. The user sees what was previously saved and can edit or clear it.

---

## 11. Validation Rules

Validation runs when the user taps Save. Inline errors are shown for failing fields. Fields pass/fail independently; multiple fields may fail simultaneously.

### Title

- No format validation
- A blank/whitespace-only title is treated as absent (cleared title)
- No maximum length is enforced by validation in V1 (practical limit is a UX concern for the field width)
- Title never causes a save failure; blank means "remove title"

### Reference Date

The Reference date field is validated if and only if it is non-empty after trimming.

An empty Reference date is always valid (means "remove date").

A non-empty Reference date must match exactly one of these patterns:

| Pattern | Rule |
|---|---|
| `YYYY` | Four decimal digits. Year must be ≥ 1826 and ≤ current year. |
| `YYYY-MM` | `YYYY` as above, then `-`, then two decimal digits for month. Month must be 01–12. |
| `YYYY-MM-DD` | `YYYY-MM` as above, then `-`, then two decimal digits for day. Day must be valid for the given year and month (non-lenient calendar check). |

Any other input format causes a validation error on the Reference date field.

**Validation error message intent:** "Enter a year (e.g. 2008), year-month (e.g. 2008-06), or full date (e.g. 2008-06-15)."

### Location Fields

- No format validation
- No maximum length enforced in V1
- Blank/whitespace is treated as absent, not as an error
- Location fields never cause a save failure

### Summary

| Field | Can cause save failure | Blank behavior |
|---|---|---|
| Title | No | Treated as absent (remove) |
| Reference date | Yes, if non-empty and invalid format | Treated as absent (remove) |
| Location display name | No | Treated as absent |
| City | No | Treated as absent |
| Country | No | Treated as absent |

---

## 12. Error Handling

### Validation Errors

When Reference date validation fails:
- The Save action does not write anything
- An inline error message is shown below the Reference date field
- The user remains on the editor
- All other fields retain their current entered values

### Storage Write Errors

If the underlying storage write fails after successful validation (e.g., IO error, disk full, concurrent session deletion):
- Remain on the editor
- Show a snackbar with a brief error message (e.g., "Couldn't save changes")
- Do not navigate away
- Do not leave `metadata.json` in a partially written state (atomicity enforced at the storage layer per `SESSION_METADATA_V1.md §12.4`)

### Session No Longer Exists

If the session is deleted while the editor is open (e.g., deleted from another entry point in a future multi-window scenario, or background process):
- On Save: the storage write returns failure
- Treat as a storage write error (show error snackbar, remain on editor)
- Do not crash

### Missing Session Context

The editor must never be opened without a valid `sessionId`. The entry point (CompareScreen overflow menu) must only show "Edit Session" when `sessionId != null`. If somehow the editor is invoked without a `sessionId`, it must navigate back immediately without attempting any read or write.

---

## 13. Data Persistence Rules

### Write Functions

The editor uses exactly the following existing storage functions:

| Changed field(s) | Storage function |
|---|---|
| `content.title` and/or `content.description` | `SessionStorage.updateContent()` |
| `reference.date` | `SessionStorage.updateReferenceDate()` |
| `location.*` | `SessionStorage.updateLocation()` |

No new storage functions are introduced for V1.

### Write Strategy

On Save, the editor writes each changed group independently in sequence:
1. If Title or Description changed: call `updateContent(title, description)`
2. If Reference date changed: call `updateReferenceDate()`
3. If any location field changed: call `updateLocation()` (with all three location values as a unit)

"Changed" means the current field value differs from the pre-populated initial value at editor open (after blank normalization). Title and Description are treated as a unit: if either changes, `updateContent()` is called with both current values.

If a field group was not changed, its write function is not called.

If any write call returns failure, the Save is treated as a storage write error (see Section 12). Writes that already succeeded before the failure are not rolled back (partial update is a known edge case; the write functions each preserve all other fields per `SESSION_METADATA_V1.md §12.2`).

### Immutable Fields

The editor must never call any write function that could modify:
- Rendering contract fields (`overlay.*`, `viewport.*`, `files.*`, `rendering.*`)
- System identity fields (`session.*`, `capture.timestampMs`, `capture.mediaStoreUri`)
- GPS fields (`captureLocation`, `referenceLocation`)
- `additional.source`

These fields are immutable after session creation and must not be touched by the editor under any circumstance.

### Blank / Absent Normalization

- Blank Title and blank Description → `updateContent(null, null)` (removes both)
- Blank Title only → `updateContent(null, description)` (removes title, keeps description)
- Blank Description only → `updateContent(title, null)` (keeps title, removes description)
- Blank Reference date → `updateReferenceDate(null)` (removes date)
- All blank location fields → `updateLocation(null, null, null)` (removes location block)

The storage functions handle the actual JSON mutation; the editor's job is to pass the correct null/non-null values.

---

## 14. Scanner / State Requirements

### Data Needed at Editor Open

The editor must read the current values of the following fields before displaying the form:

- `content.title`
- `reference.date`
- `location.displayName`
- `location.city`
- `location.country`

These values are needed to pre-populate the form fields and to establish the initial state for dirty tracking.

### How to Obtain the Initial State

The editor may obtain the initial state by:

**Option A:** Reading `metadata.json` directly at editor open time (simplest; avoids requiring `ScannedSession` to carry new fields).

**Option B:** Extending `ScannedSession` to carry the new fields, so the editor can receive the initial state from the ViewModel without a separate file read.

The choice between Option A and Option B is an implementation decision. Option A is simpler and avoids scanner changes. Option B is more consistent with the ViewModel-as-source-of-truth pattern.

Either option is acceptable in V1. The implementation decision must be explicit and consistent.

### Scanner Changes (if Option B is chosen)

If `ScannedSession` is extended to carry the new fields:
- `title: String?` — already present in `ScannedSession` (existing field, keep as-is)
- `referenceDate: String?` — new field; may be null; absence is not an error
- `locationDisplayName: String?` — new field; may be null
- `locationCity: String?` — new field; may be null
- `locationCountry: String?` — new field; may be null

New `ScannedSession` fields must have null defaults so that existing session scan results (v2/v3 sessions without these fields) remain valid without changes to call sites.

`SessionScanner` reads these fields from the `reference` and `location` blocks; absent blocks and absent fields return null silently (forward and backward compatible per `SESSION_METADATA_V1.md §12.3`).

### After Save: ViewModel State Refresh

After a successful Save, the in-memory session data visible to `CompareScreen` must reflect the updated values. See Section 15 for the required CompareScreen update behavior.

The editor must not rely on a full `SessionScanner` re-scan of all sessions to update a single session's display state. The update should be targeted.

---

## 15. CompareScreen Update Requirements

### What CompareScreen Displays from Metadata

`CompareScreen` displays a metadata header above the compare slider (see `COMPARE_FLOW_V1.md §42`):
- `content.title` — shown as the primary line when present
- `location.displayName`, `location.city`, `location.country` — shown as a compact location line when present
- `Created <date>` fallback — shown when no user-authored metadata is present and a timestamp is available

The header is hidden in fullscreen mode.

### Title Update on Return

When the user saves a changed title in the editor and returns to `CompareScreen`:
- The updated title must be visible immediately in `CompareScreen` without requiring manual refresh or re-navigation

The implementation must ensure the title displayed in `CompareScreen` is sourced from the updated session state, not a stale cached value.

### Overflow Menu Update

The overflow menu in `CompareScreen` no longer contains "Edit Title" or "Remove Title". After this change:
- "Edit Session" replaces "Edit Title"
- "Remove Title" is removed from the overflow menu entirely (title removal is now done inside the editor)

The overflow menu after this change:

```
Edit Session
Backup Session
```

This is a permanent change to the overflow menu structure. The "Remove Title" shortcut is not preserved as a compatibility measure.

### No Additional CompareScreen Rendering Changes

No changes to the compare slider, image rendering, session timestamp display, delete button, or fullscreen behavior are introduced by this feature.

---

## 16. Accessibility

### Minimum Requirements

- All form fields must have semantic content descriptions or labels readable by TalkBack
- The Save action in the top app bar must have a content description
- The Back navigation icon must have a content description
- Inline validation error messages must be associated with their field semantically (so screen readers announce the error in context)
- The confirmation dialog (unsaved changes) must be announced correctly by the accessibility system

### Input Fields

Standard Material 3 `OutlinedTextField` or `TextField` components are preferred. These provide built-in accessibility semantics for labels, hints, and error states.

### No Custom Accessibility Work Required for V1

V1 does not require custom semantics trees or non-standard accessibility annotations beyond what Material 3 components provide by default, provided that all fields have correctly set labels and the error states are wired to the `isError` parameter.

---

## 17. i18n

### General Rule

All user-facing text must use string resources. No hardcoded user-visible strings are permitted.

### Required String Resource Entries (intent, not final key names)

| Intent | Key pattern (follow existing conventions) |
|---|---|
| Screen title "Edit Session" | `edit_session_title` |
| Overflow menu item "Edit Session" | `compare_menu_edit_session` |
| Save action label | `edit_session_save` |
| Save action content description | `edit_session_save_content_description` |
| Section header "Session" (if used) | `edit_session_section_session` |
| Section header "Reference photo" (if used) | `edit_session_section_reference_photo` |
| Section header "Location" (if used) | `edit_session_section_location` |
| Field label "Title" | `edit_session_field_title` |
| Field label "Reference date" | `edit_session_field_reference_date` |
| Field label "Location" | `edit_session_field_location_display_name` |
| Field label "City" | `edit_session_field_city` |
| Field label "Country" | `edit_session_field_country` |
| Reference date placeholder hint | `edit_session_reference_date_hint` |
| Reference date validation error | `edit_session_reference_date_error` |
| Discard dialog title "Discard changes?" | `edit_session_discard_dialog_title` |
| Discard dialog body | `edit_session_discard_dialog_body` |
| Discard dialog confirm "Discard" | `edit_session_discard_confirm` |
| Discard dialog cancel "Keep editing" | `edit_session_discard_cancel` |
| Save error snackbar | `edit_session_save_failed` |

Follow the app's existing string resource naming conventions for the final key names. The patterns above are intent-level guidance.

### Language Coverage

All string resources must be added to `strings.xml` (default language). Any existing language variants must also receive translations if the project already maintains them.

---

## 18. Testing Expectations

This section identifies what must be verified when the editor is implemented. It does not prescribe test file names or test frameworks.

### Unit Tests

**ViewModel:**
- Pre-population of all six editable fields from session metadata at open
- `capture.timestampMs` loaded as read-only; not included in dirty tracking
- Dirty state is false when no editable fields have changed; true when any field changed (including Description)
- Dirty state resets correctly after Save
- Save button is disabled when not dirty; enabled when dirty and not saving
- Save action calls the correct storage functions with the correct arguments
- Save with blank title and blank description calls `updateContent(null, null)`
- Save with blank reference date calls `updateReferenceDate(null)`
- Save with all blank location fields calls `updateLocation(null, null, null)`
- Save with non-empty location fields calls `updateContent()` / `updateLocation()` with trimmed values
- Save with unchanged fields does not call the corresponding write function
- Storage write failure on any field results in save error emission (no navigation)

**Reference date validation:**
- `"2008"` is valid
- `"2008-06"` is valid
- `"2008-06-15"` is valid
- `""` (empty) is valid (means remove)
- `"08"` is invalid
- `"2008-13"` is invalid (month out of range)
- `"2008-02-31"` is invalid (invalid calendar day)
- `"1825"` is invalid (before 1826)
- Year after current year is invalid
- `"2008/06/15"` is invalid (wrong separator)
- `"2008-6"` is invalid (month not zero-padded)

### Instrumentation / UI Tests

**Editor open and close:**
- Tapping "Edit Session" in CompareScreen overflow opens the editor
- Back with no changes navigates back to CompareScreen without a dialog
- Back with changes shows the discard confirmation dialog
- Confirming discard navigates back to CompareScreen
- Cancelling discard returns to the editor

**Save button state:**
- Save button is disabled when no fields have been changed from their pre-populated values
- Save button becomes enabled after any field is changed

**Save behavior:**
- Filling Title and tapping Save: CompareScreen shows updated title
- Clearing Title and tapping Save: CompareScreen title is removed
- Filling Description and tapping Save: description persisted
- Filling Reference date via text field with valid value and tapping Save: save succeeds
- Selecting a date via the DatePicker companion: Reference date field is updated with `YYYY-MM-DD` value; editor becomes dirty
- Filling Reference date with invalid value and tapping Save: inline error shown, no navigation
- Clearing all location fields and tapping Save: location block removed (no crash)

**Overflow menu:**
- "Edit Session" appears in overflow menu when session context is present
- "Edit Title" and "Remove Title" no longer appear in overflow menu

### Regression Guards

The following must remain unaffected:
- `CompareScreen` rendering, slider behavior, and fullscreen mode
- `CompareLibraryScreen` tile rendering, navigation, and multi-select
- Session backup export behavior
- Session delete behavior
- Camera capture flow and `compareInput` lifecycle
- All existing unit and instrumentation tests must remain green

---

## 19. Future Extensions

The following are explicitly deferred and must not be implemented as part of V1:

### Additional Fields

- Tags (`content.tags`) — requires tag input UI (chip input or tag list)
- Favorite toggle as form field — decided: NOT a form field; implemented as TopAppBar
  action per §20 and `FAVORITES_AND_LIBRARY_FILTERS_V1.md §18.3`
- Visibility selector (`additional.visibility`) — requires selector and upload-flow integration

### Editor Entry Points

- Entry from `CompareLibraryScreen` tile long-press action
- Entry from a session detail screen (if introduced in a future version)
- Inline editing directly in `CompareScreen` (not planned)

### Reference Date UX

- Visual indicator showing whether the current date was set from EXIF or manually
- Read-only original EXIF date hint with Reset action (requires `reference.originalDate` storage field)

### Location UX

- Location search field with reverse geocoding (requires network access and explicit exception to `GPS_RECREATION_SYSTEM_V1.md §12`)
- "Populate from GPS" button that reads `captureLocation` lat/lon and performs reverse geocoding (out of scope until GPS_RECREATION_SYSTEM_V1.md is updated)
- Location format validation or normalization

### Scanner Extensions

- Exposing `reference.date` and `location.*` in `ScannedSession` for use in Compare Library sorting, filtering, or tile display

### Compare Library Integration

- Filtering sessions by location or date
- Sorting by `reference.date`
- Displaying location or reference date on library tiles

---

## 20. Favorite Star — TopAppBar Action

This section documents the decided extension to the Session Metadata Editor, added per
`FAVORITES_AND_LIBRARY_FILTERS_V1.md §18.3`. It supersedes the V1 Non-Goals entry
for "Favorite toggle" (§7), which listed it as a form field. The feature is implemented
as a TopAppBar action instead.

### 20.1 Purpose

`EditSessionScreen` is the third Favorites entry point in SameView, alongside
`CompareScreen` (TopAppBar star) and `CompareLibraryScreen` (tile star). The star in
EditSession allows the user to see and change the Favorite status while editing session
metadata, without leaving the screen.

### 20.2 TopAppBar Position

The Favorite star is added to the `actions` slot of the `EditSessionScreen` TopAppBar.
The `actions` slot was previously empty. No existing TopAppBar element is moved or
removed.

Updated TopAppBar structure:
```
← Back  |  Edit session / Update information…  |  [★ Favorite]
```

The star is the only element in the `actions` slot.

### 20.3 Icon Visual States

Identical to `FAVORITES_AND_LIBRARY_FILTERS_V1.md §6.2`:
- Not favorited: outline star, standard `onSurface` tint
- Favorited: filled star, `SameViewStarFavorited` amber/yellow tint

### 20.4 Toggle Behavior

Tapping the star:
1. Calls `SessionStorage.updateFavorite()` immediately (via injectable `sessionFavoriteUpdater`
   lambda in `EditSessionViewModel`)
2. On success: `isFavorite` StateFlow in `EditSessionViewModel` updates; icon changes
3. On failure: error Snackbar via the existing `events: SharedFlow<EditSessionEvent>`
   channel; icon reverts

The star toggle:
- Does NOT affect `isDirty`
- Does NOT require the Save button to persist
- Does NOT interact with form validation
- Is NOT reverted when the user confirms "Discard changes" for form fields

### 20.5 Dirty / Discard Interaction

The Discard dialog ("Your changes have not been saved") and the Discard action apply
exclusively to form fields tracked by `isDirty`. The favorite status, having been
written to disk immediately, is permanent from the moment the star is tapped.

A user who taps the star and then discards form changes leaves the session with:
- Favorite status: updated (persisted)
- Title, description, reference date, location: unchanged (discarded)

This is the intended behavior. It matches how the star works in CompareScreen and
CompareLibraryScreen — the star always takes effect immediately.

### 20.6 Availability

The star is always visible in EditSessionScreen (a valid `sessionId` is always present
when the screen is open). No conditional visibility rule is needed.

### 20.7 Accessibility

- Content description: reuses `compare_screen_favorite_mark` / `compare_screen_favorite_remove`
  (no new string resources required)
- The star is a standard `IconButton` in the TopAppBar; TalkBack navigates to it
  naturally in the action area

### 20.8 ViewModel Requirements

`EditSessionViewModel` is extended with:
- `isFavorite: StateFlow<Boolean>` — initialized from `additional.isFavorite` in `metadata.json`
  during the existing `init`-phase metadata read (via `InitialSessionFields`)
- `toggleFavorite()` function — writes via `sessionFavoriteUpdater(sessionsRoot, sessionId, !isFavorite.value)`
  on the IO dispatcher; updates `isFavorite` StateFlow on success; emits a failure event
  on error
- `sessionFavoriteUpdater: (File, String, Boolean) -> Boolean` — injectable lambda
  (default: `SessionStorage.updateFavorite`), following the same pattern as
  `sessionLocationUpdater` and `sessionReferenceDateUpdater`

`isDirty` is NOT modified by `toggleFavorite()`. The `onSave()` path is NOT modified.

### 20.9 Testing Requirements

- `favoriteButton_isVisible` — star is visible in the EditSession TopAppBar
- `favoriteButton_showsOutlineIcon_whenNotFavorited` — `isFavorite = false` → outline star
- `favoriteButton_showsFilledIcon_whenFavorited` — `isFavorite = true` → filled star
- `favoriteButton_tap_togglesImmediately` — tap star → `toggleFavorite()` called;
  no Save required
- `favoriteButton_doesNotAffectDirtyState` — tap star → `isDirty` unchanged
- `favoriteButton_doesNotAffectSaveButton` — Save button enabled/disabled state
  unaffected by star toggle

### 20.10 Implementation Reference

Implementation block: `Block F.3` in
`FAVORITES_AND_LIBRARY_FILTERS_V1_IMPLEMENTATION_PLAN.md`
