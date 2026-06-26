# SESSION_BRANDING_V2_UX_REWORK.md

## Document Status

**Status:** Final — implementation-ready. Architecture Corrected Edition.

This document is the **authoritative UX specification** for Session Branding V2 in SameView.

It supersedes all branding-related UX content in the following documents. It does not supersede their technical sections:

| Superseded | Sections |
|---|---|
| `SESSION_BRANDING_V1.md` | §11, §12, §13.1–§13.3 |
| `SESSION_METADATA_EDITOR_V1.md` | §21 |
| `SHARE_COMPARISON_IMAGE_V1.md` | FD-18, §15.3 layout |
| `SETTINGS_UX_V1.md` | §11 |

The following are **unchanged and remain authoritative:**
- All storage architecture (V1 §1–§10, §14–§20), with the exception that session-level branding files are no longer created (§3.4 remains valid for existing sessions)
- Metadata schema v6 (existing sessions remain valid; no new session branding files are written)
- Backup integration
- Rendering pipeline
- Repository architecture

---

## Architecture Summary — Corrected Edition

The original V2 design distributed branding management across three screens (Settings, Edit Session, Share Comparison). Real-world testing revealed this created confusion: users had to understand two separate ownership levels (global vs. session) before they could reliably brand a comparison export.

**The corrected architecture has two ownership levels, not three:**

| Surface | Owns |
|---|---|
| Settings | Global default branding |
| Share Comparison | Session branding — reads, writes, and manages `branding-handle.png` |
| Edit Session | **Nothing branding-related** |

The persistence model is unchanged: session branding is stored in `branding-handle.png` inside the session folder. Sessions remain 100% self-contained and reproducible. Backups include the branding asset automatically via the existing metadata-driven file list.

**What changes is UI ownership only:** branding management moves from Edit Session to Share Comparison. The global default branding serves as the starting point when a session has no branding yet. The user's choice — whether to keep the global default, select a different logo, or remove branding — is written to the session folder immediately, exactly as the V1 Edit Session behavior did.

---

## 1. Terminology

All user-facing text uses "logo." The word "branding" does not appear in any string visible to the user. It may remain in internal code identifiers.

### Master replacement table

| V1 user-facing string | V2 user-facing string |
|---|---|
| "Branding" (card/section title) | "Logo" |
| "Default branding for new sessions" | "Your logo" |
| "Choose image" | "Choose photo" |
| "Choose symbol" | "Use a symbol" |
| "Remove" / "Remove branding" | "Remove logo" |
| "Copy from default branding" | *(removed — concept no longer exists)* |
| "Change branding" | *(removed — replaced by symmetric actions)* |
| "Use branding" | "Show logo" |
| "No branding for this session." | "No logo for this comparison." |
| "Add branding in Edit session." | *(removed — user adds directly in Share Comparison)* |
| "Only applied in slider style." | *(removed — section is absent in side-by-side)* |

### String resource keys — new

| Key | Value |
|---|---|
| `settings_logo_section_title` | "Your logo" |
| `settings_logo_description` | "Shown in the slider handle when sharing comparison images. Copied into new comparisons automatically." |
| `settings_logo_none` | "No logo set" |
| `settings_logo_current` | "Your current logo" |
| `settings_logo_choose_photo` | "Choose photo" |
| `settings_logo_use_symbol` | "Use a symbol" |
| `settings_logo_remove` | "Remove logo" |
| `settings_logo_load_error` | "Couldn't load logo" |
| `share_comparison_logo_card_title` | "Logo on handle" |
| `share_comparison_logo_none` | "No logo for this comparison." |
| `share_comparison_logo_show` | "Show logo" |
| `share_comparison_logo_choose_photo` | "Choose photo" |
| `share_comparison_logo_use_symbol` | "Use a symbol" |
| `share_comparison_logo_remove` | "Remove logo" |
| `share_comparison_logo_error` | "Couldn't set logo" |
| `branding_symbol_picker_title` | "Choose a symbol" |

### Symbol display names

| Symbol ID | Display name |
|---|---|
| `heart` | Heart |
| `star` | Star |
| `camera` | Camera |
| `home` | Home |
| `pin` | Pin |
| `fire` | Fire |

### Strings deprecated by V2

The following V1 keys must be removed after V2 is fully implemented:

`settings_branding_section_title`, `settings_branding_description`, `settings_branding_choose_image`, `settings_branding_choose_symbol`, `settings_branding_remove`, `settings_branding_load_error`, `edit_session_card_branding`, `edit_session_branding_none`, `edit_session_branding_change`, `edit_session_branding_remove`, `edit_session_branding_copy_global`, `edit_session_branding_error`, `share_comparison_branding_label`, `share_comparison_branding_hint_edit_session`, `share_comparison_branding_hint_slider_only`.

The following previously-added V2 keys are also removed under the corrected architecture:

`edit_session_card_logo`, `edit_session_logo_description`, `edit_session_logo_none`, `edit_session_logo_type_photo`, `edit_session_logo_type_symbol`, `edit_session_logo_use_default`, `edit_session_logo_choose_photo`, `edit_session_logo_use_symbol`, `edit_session_logo_remove`, `edit_session_logo_error`, `share_comparison_logo_hint`.

All corresponding `values-de` keys are also deprecated.

---

## 2. Settings Screen UX

### Responsibility

Settings manages the **global default logo** only. The global logo is automatically used as the starting export logo whenever the user opens Share Comparison. It has no effect on exports that are already in progress or have been completed.

### Section title

```
Your logo
```

### Layout — Empty state (no logo set)

```
┌──────────────────────────────────────────────────┐
│  Your logo                                       │
│                                                  │
│  Shown in the slider handle when sharing         │
│  comparison images. Copied into new              │
│  comparisons automatically.                      │
│                                                  │
│  ┌──────────┐                                    │
│  │  [icon]  │  No logo set                       │
│  └──────────┘                                    │
│                                                  │
│  [ Choose photo ]  [ Use a symbol ]              │
│                                                  │
└──────────────────────────────────────────────────┘
```

### Layout — Populated state (logo set)

```
┌──────────────────────────────────────────────────┐
│  Your logo                                       │
│                                                  │
│  Shown in the slider handle when sharing         │
│  comparison images. Copied into new              │
│  comparisons automatically.                      │
│                                                  │
│  ┌──────────┐                                    │
│  │  [logo]  │  Your current logo                 │
│  └──────────┘                                    │
│                                                  │
│  [ Choose photo ]  [ Use a symbol ]              │
│                                                  │
│  [ Remove logo ]                                 │
│                                                  │
└──────────────────────────────────────────────────┘
```

### Element specifications

**Description text**
Always visible in both states. Positioned immediately below the card title. Style: `bodySmall` / `SameViewSettingsSecondaryText`. Padding: 4 dp horizontal.
String: `settings_logo_description`. Note: the description wording reflects that the global default is copied into a session when Share Comparison first opens for a session without branding — the same self-containment model as V1.

**Placeholder circle (empty state)**
- Size: 64 dp
- Border ring: 2 dp, `SameViewAccent`, `CircleShape`
- Fill: `#F5F7FA`
- Interior: a centered image-add icon, 24 dp, `SameViewSettingsSecondaryText` color
- Purpose: establishes the visual form factor of the logo before any logo is set

**Preview circle (populated state)**
- `BrandingPreviewCircle` composable at 64 dp
- Renders the actual logo as it will appear in exports
- No change from V1

**State label (beside circle)**
Both circles are accompanied by a short label to their right:
- Empty: `settings_logo_none` — "No logo set"
- Populated: `settings_logo_current` — "Your current logo"
- Style: `bodySmall` / `SameViewSettingsSecondaryText`

**"Choose photo" and "Use a symbol" buttons**
- `TextButton`, side by side in a `Row` with equal `weight(1f)`
- Always visible, regardless of whether a logo is currently set
- When no logo: adds a new logo; When logo exists: replaces it
- String keys: `settings_logo_choose_photo`, `settings_logo_use_symbol`

**"Remove logo" button**
- `TextButton`, full width
- Visible **only** when a logo is currently set
- On tap: logo deleted; preview circle transitions to placeholder
- No confirmation dialog
- String: `settings_logo_remove`

### Interaction rules

- All changes are immediate writes. No Save button, no confirmation.
- State transitions are immediate: the circle updates the moment the selection completes.
- On normalization failure: show snackbar `settings_logo_load_error`. No state change.

---

## 3. Edit Session Screen UX

### Logo responsibility

**Edit Session has no logo management responsibility in this architecture.**

The Logo card has been removed from Edit Session. Edit Session manages only:
- Session title and description
- Reference photo date
- Location fields

The card order is: Session → Reference photo → Current photo → Location.

No branding UI, no branding workflow, no branding-related actions exist in Edit Session.

### Rationale for removal

Under the original V2 design, session-specific branding required users to understand a two-level ownership model (global default vs. session override). This created confusion in testing: users were not sure whether changing branding in Edit Session would affect other sessions, and they could not predict what would happen when they opened Share Comparison after setting branding in Edit Session.

The corrected architecture eliminates this ambiguity. Logo management sits in exactly two places: Settings (global default) and Share Comparison (session logo).

---

## 4. Share Comparison Screen UX

### Responsibility

Share Comparison controls both **export behavior** and **session branding** for the current session. It reads and writes the session's `branding-handle.png` directly — the same file the export renderer uses. It does not modify the global default branding in Settings.

### Session branding and reproducibility

Session branding is stored in `branding-handle.png` inside the session folder. This is the same V1 persistence model. Sessions are 100% self-contained: the branding image is included in backups, survives device migration, and is independent of the current state of global Settings.

Changing or removing the global default in Settings after a session has been branded has no effect on that session.

### Logo initialization on screen open

When Share Comparison opens for a session:

1. **Session already has branding** (`branding-handle.png` exists): the session branding is loaded and shown. `hasBranding = true`. The global default has no effect.

2. **Session has no branding AND global branding exists**: the global branding is copied into the session folder as `branding-handle.png` immediately. After the copy, the session has branding. `hasBranding = true`. The user sees the logo in the card and may keep it, replace it, or remove it.

3. **Session has no branding AND no global branding**: the card shows the empty state. `hasBranding = false`. The user may add a logo using the action buttons.

The global branding is **only consulted at initialization** when the session has no branding. It is never consulted again during the same or future Share Comparison sessions for that session. The global default is never modified by any action in Share Comparison.

### Logo write behavior

All logo actions in Share Comparison write to the session folder immediately — there is no Save button and no confirmation. This is the same immediate-write pattern the V1 branding feature used in Edit Session, relocated to Share Comparison.

### Position in screen

A dedicated "Logo on handle" card, positioned **between the Style card and the Information card**.

Full card order:
1. Style card (Slider / Side-by-side + preview)
2. **Logo on handle** ← Slider only
3. Information card (Title / Date / Location)
4. Quality card (Standard / Original)
5. Share button

### Visibility

The Logo on handle card is rendered **only when the Slider style is selected**.

When Side-by-side is selected: the card is absent. No placeholder. No disabled state. No message. Absent.

When the user switches from Slider to Side-by-side, the card disappears. When they switch back to Slider, the card reappears with its previous state.

### Card title

```
Logo on handle
```

String: `share_comparison_logo_card_title`.

### Layout — Empty state (session has no branding, no global branding exists)

```
┌──────────────────────────────────────────────────┐
│  Logo on handle                                  │
│                                                  │
│  ┌──────────┐                                    │
│  │  [icon]  │  No logo for this comparison.      │
│  └──────────┘                                    │
│                                                  │
│  [ Choose photo ]  [ Use a symbol ]              │
│                                                  │
└──────────────────────────────────────────────────┘
```

**No toggle in the empty state.** There is nothing to show or hide.

**Placeholder circle:** 64 dp, same geometry as Settings.

**State text:** `share_comparison_logo_none` — "No logo for this comparison."

**Action buttons:** "Choose photo" and "Use a symbol" are visible in the empty state. Both write to the session folder immediately.

### Layout — Populated state, toggle ON

```
┌──────────────────────────────────────────────────┐
│  Logo on handle                                  │
│                                                  │
│  ┌──────────┐  [switch ●]  Show logo            │
│  │  [logo]  │                                    │
│  └──────────┘                                    │
│                                                  │
│  [ Choose photo ]  [ Use a symbol ]              │
│                                                  │
│  [ Remove logo ]                                 │
│                                                  │
└──────────────────────────────────────────────────┘
```

### Layout — Populated state, toggle OFF

```
┌──────────────────────────────────────────────────┐
│  Logo on handle                                  │
│                                                  │
│  ┌──────────┐  [switch ○]  Show logo            │
│  │  [logo,  │                                    │
│  │  dimmed] │                                    │
│  └──────────┘                                    │
│                                                  │
│  [ Choose photo ]  [ Use a symbol ]              │
│                                                  │
│  [ Remove logo ]                                 │
│                                                  │
└──────────────────────────────────────────────────┘
```

### Element specifications — populated state

**Preview circle**
- `BrandingPreviewCircle` at 64 dp when toggle is ON
- When toggle is OFF: same composable, `alpha = 0.4f`

**"Show logo" toggle**
- `SettingsSwitchRow`, label: `share_comparison_logo_show` — "Show logo"
- Positioned in a `Row` with the preview circle: circle left (64 dp), toggle row right (`weight(1f)`)
- On change: updates `useBranding` in `ShareComparisonViewModel`; live preview in Style card updates immediately
- String: `share_comparison_logo_show`

**"Choose photo" button**
- `TextButton`, always visible in both empty and populated states
- In populated state: replaces the session branding without requiring a remove step
- On tap: opens Photo Picker. On successful selection: image normalized and written atomically to `<sessionDir>/branding-handle.png`; circle updates immediately
- String: `share_comparison_logo_choose_photo`
- **Never modifies global branding.**

**"Use a symbol" button**
- `TextButton`, always visible in both empty and populated states
- On tap: opens `BrandingSymbolPickerSheet`. On selection: VectorDrawable rendered and written atomically to `<sessionDir>/branding-handle.png`; circle updates immediately
- String: `share_comparison_logo_use_symbol`
- **Never modifies global branding.**

Both buttons displayed side by side in a `Row` with equal `weight(1f)`.

**"Remove logo" button**
- `TextButton`, full width
- Visible **only** when session branding is set (`hasBranding == true`)
- On tap: `branding-handle.png` deleted from session folder; metadata updated; card transitions to empty state; toggle disappears
- No confirmation dialog
- String: `share_comparison_logo_remove`

**Error handling**
- On normalization or write failure: show snackbar `share_comparison_logo_error`. No state change. Existing branding (if any) is preserved.

### Toggle behavior

- **Default ON** when session branding is set (`hasBranding == true`)
- **Default OFF** when session has no branding
- **Not persisted.** Resets to the default when the screen re-opens
- When ON and style is Slider: `SliderRenderStrategy` renders the branded handle using the session `branding-handle.png`
- When OFF: `SliderRenderStrategy` renders the standard SameView handle
- State is preserved in `ShareComparisonViewModel` memory when the user switches between Slider and Side-by-side within the same screen session

---

## 5. Symbol Picker Specification

### Component name

`BrandingSymbolPickerSheet`

### Type

`ModalBottomSheet` (Material 3).

### Layout

```
┌──────────────────────────────────────────────────┐
│               ─────                              │
│                                                  │
│  Choose a symbol                                 │
│                                                  │
│  ┌────────┐   ┌────────┐   ┌────────┐           │
│  │[handle]│   │[handle]│   │[handle]│           │
│  │preview │   │preview │   │preview │           │
│  │ Heart  │   │  Star  │   │ Camera │           │
│  └────────┘   └────────┘   └────────┘           │
│                                                  │
│  ┌────────┐   ┌────────┐   ┌────────┐           │
│  │[handle]│   │[handle]│   │[handle]│           │
│  │preview │   │preview │   │preview │           │
│  │  Home  │   │  Pin   │   │  Fire  │           │
│  └────────┘   └────────┘   └────────┘           │
│                                                  │
│  [ Cancel ]                                      │
└──────────────────────────────────────────────────┘
```

### Symbol cell specification

Each cell renders the symbol exactly as it will appear in the exported handle:

- **Preview circle:** 56 dp
- **Ring:** 2 dp, `SameViewAccent`, two arcs with 12° gaps at top and bottom (matches `BrandingHandleRenderer` geometry)
- **Fill:** `#F5F7FA`
- **Symbol icon:** centered, scaled to 72% of circle diameter, rendered at the VectorDrawable's native fill color (no `colorFilter` override). The VectorDrawable fill color (`#17202F`) provides correct contrast against `#F5F7FA` and matches the actual export appearance (spec rule P-03).
- **Name label:** `labelSmall` / `SameViewSettingsLabelText`, sentence case, below the circle

Grid: `LazyVerticalGrid`, `GridCells.Fixed(3)`, `horizontalArrangement = Arrangement.spacedBy(8.dp)`, `verticalArrangement = Arrangement.spacedBy(8.dp)`.

### Interaction

- Tapping a cell: selection is made → sheet dismisses → session branding written to `branding-handle.png` → caller UI updates
- Tapping "Cancel": sheet dismisses → no selection → no change to current logo
- Drag down to dismiss: equivalent to Cancel

### Callers

`BrandingSymbolPickerSheet` is called from:
- `SettingsScreen` (global logo management)
- `ShareComparisonScreen` (session logo management)

Edit Session is **not** a caller. The callback signature is unchanged: `onSymbolSelected: (BuiltinBrandingSymbol) -> Unit`.

### Symbol color correctness (implementation note)

The `colorFilter = ColorFilter.tint(SameViewSettingsLabelText)` previously applied in the picker was incorrect. `SameViewSettingsLabelText` is `Color(0xFFFFFFFF)` (pure white) — designed for dark background text, not for icons on the light `#F5F7FA` circle. This override made symbols nearly invisible (contrast ~1.07:1) while the actual export rendered the VectorDrawable's native `#17202F` (contrast ~14.6:1), violating rule P-03. The `colorFilter` must not be applied. Symbol icons render at their native VectorDrawable fill color.

---

## 6. Visual Hierarchy

### Settings — Your Logo

| Tier | Element | Rationale |
|---|---|---|
| 1 | Description text | Feature is unfamiliar to new users; context before interaction |
| 2 | Circle (placeholder or preview) | Visual anchor establishing form factor |
| 3 | State label beside circle | Confirms current state |
| 4 | "Choose photo" + "Use a symbol" | Equal-weight primary actions |
| 5 | "Remove logo" | Secondary destructive action, separated below |

### Edit Session

No branding elements. Not applicable.

### Share Comparison — Logo on Handle

| Tier | Element | Rationale |
|---|---|---|
| 1 | Circle (placeholder or dimmed preview) | Immediate visual state |
| 2 | "Show logo" toggle (populated) | Primary export-behavior control for this render |
| 3 | "Choose photo" + "Use a symbol" | Add or replace session branding |
| 4 | "Remove logo" (populated) | Remove session branding entirely |
| — | Placeholder + state text (empty) | Informational only until action taken |

---

## 7. User Journeys

### Journey A — New user sets a global logo and shares a comparison

1. User opens Settings.
2. User scrolls to the "Your logo" section. Reads: "Shown in the slider handle when sharing comparison images. Copied into new comparisons automatically."
3. User taps "Use a symbol." `BrandingSymbolPickerSheet` opens. User sees six symbols as handle previews — they see exactly how each will look.
4. User selects Star. Sheet dismisses. Preview circle appears showing Star.
5. User closes Settings and opens Share Comparison for a session that has no existing branding.
6. Share Comparison copies the global Star symbol into the session folder as `branding-handle.png`.
7. "Logo on handle" card is visible (Slider selected). Preview circle shows the Star symbol. Toggle is ON.
8. Live preview in the Style card shows the branded handle.
9. User taps Share. Export contains the Star symbol in the handle.
10. If the user later changes or removes the global logo in Settings, this session keeps its Star — the session is self-contained.

---

### Journey B — User overrides the session logo from Share Comparison

1. User has a global Star symbol set in Settings.
2. User opens Share Comparison for a specific session that has no existing branding.
3. Share Comparison copies the global Star into the session as the starting logo.
4. "Logo on handle" card shows Star symbol. Toggle is ON.
5. User wants to use their company photo for this session.
6. User taps "Choose photo" in the Logo card.
7. Photo Picker opens. User selects their company logo.
8. Photo Picker closes. Company logo is normalized and written to `branding-handle.png` in the session folder immediately. Preview circle updates.
9. User taps Share. This export uses the company photo logo.
10. The next time Share Comparison is opened for this session, the company photo is already in the session — it is shown as the current logo. The override is persisted.
11. Settings still shows the Star symbol. Unchanged. Other sessions are unaffected.

---

### Journey C — User adds a logo directly in Share Comparison (no global logo set)

1. User has no global logo set in Settings.
2. User opens Share Comparison for a session.
3. "Logo on handle" card shows empty state: placeholder circle, "No logo for this comparison.", "Choose photo" and "Use a symbol" buttons. No toggle.
4. User taps "Use a symbol." `BrandingSymbolPickerSheet` opens.
5. User selects Camera. Symbol is rendered and written to the session's `branding-handle.png` immediately.
6. Card transitions to populated state: Camera preview circle, "Show logo" toggle (ON).
7. User taps Share. Export contains the Camera symbol in the handle.
8. Session now has a Camera logo stored. Future exports for this session start with Camera as the current logo.
9. Global Settings still shows "No logo set." Unchanged.

---

### Journey D — User removes session branding and shares without a logo

1. User opens Share Comparison for a session that already has branding (e.g., a Star symbol from a previous open).
2. "Logo on handle" card shows the Star. Toggle is ON.
3. User taps "Remove logo." `branding-handle.png` is deleted from the session folder immediately.
4. Card transitions to empty state: placeholder circle, "No logo for this comparison." Action buttons appear.
5. User taps Share. Export is created without any logo — standard SameView handle is rendered.
6. Global Settings is unchanged. The global Star symbol is not affected.
7. Next time Share Comparison is opened for this session, it starts in empty state (no session branding). If a global logo exists, it is re-copied as the new starting default.

---

### Journey E — User shares a Side-by-side export

1. User opens Share Comparison. Logo card is visible under Slider.
2. User switches to Side-by-side. The Logo card disappears entirely. No toggle. No placeholder. No message. The live preview shows two plain images.
3. User taps Share. Export is created without any logo — branding never applies to Side-by-side.
4. User switches back to Slider. Logo card reappears with the same state as before the switch.

---

## 8. Responsibility Boundaries

### Settings

**Owns:** Global default logo.

**Permitted actions:**
- Add global logo (Choose photo / Use a symbol)
- Replace global logo (Choose photo / Use a symbol)
- Remove global logo (Remove logo)

**Does not permit:**
- Any action affecting any export configuration
- Any action on any specific session

**Effect timing:** The global logo is consulted at Share Comparison screen open time only when the session has no branding. It is copied into the session at that point. Changes to Settings after the copy have no effect on that session.

---

### Edit Session

**Owns:** Session title, reference date, location.

**No branding actions.** Branding is not in scope for Edit Session.

---

### Share Comparison

**Owns:** Session branding (`branding-handle.png` in the session folder); whether the logo is applied to the current export.

**Permitted actions:**
- Add session branding (Choose photo / Use a symbol)
- Replace session branding (Choose photo / Use a symbol — symmetric, no intermediate removal)
- Remove session branding (Remove logo)
- Copy global default into session as starting logo (automatic, on screen open when session has no branding)
- Toggle logo on/off for the current export render (Show logo toggle)

**Does not permit:**
- Modifying the global default in Settings
- Any action affecting other sessions

**Effect timing:** Logo writes (`branding-handle.png`) are immediate. The toggle state is not persisted (resets to default on screen re-open).

---

### Boundary enforcement

| Action | Settings | Edit Session | Share Comparison |
|---|---|---|---|
| Add / replace session branding | ✗ | ✗ | ✓ |
| Remove session branding | ✗ | ✗ | ✓ |
| Set global default | ✓ | ✗ | ✗ |
| Copy global → session (auto-init) | ✗ | ✗ | ✓ |
| Control export toggle | ✗ | ✗ | ✓ |
| Persist branding to session file | ✗ | ✗ | ✓ |
| Affect other sessions | ✗ | ✗ | ✗ |
| Modify global branding file | ✓ | ✗ | ✗ |

---

## 9. Behavioral Rules

### Visibility rules

**V-01.** The Logo on handle card in Share Comparison is rendered if and only if the current style is `ShareComparisonStyle.SLIDER`. It is absent for `SIDE_BY_SIDE`. No disabled state, no warning, no placeholder.

**V-02.** "Remove logo" in Settings is visible if and only if a global logo is currently set.

**V-03.** "Remove logo" in Share Comparison is visible if and only if session branding is currently set (`hasBranding == true`).

**V-04.** "Use your default logo" does not exist anywhere in the UI. The concept has been removed.

**V-05.** "Choose photo" and "Use a symbol" are visible at all times in both Settings and Share Comparison, regardless of whether a logo is currently set.

**V-06.** The "Show logo" toggle in Share Comparison is visible if and only if `hasBranding == true`. It does not appear in the empty state.

**V-07.** Edit Session has no branding-related UI elements of any kind.

### State rules

**S-01.** The preview circle (`BrandingPreviewCircle`) is shown when session branding is set (`branding-handle.png` exists). The placeholder circle is shown when no session branding is set. There is no state where neither circle is shown.

**S-02.** When the "Show logo" toggle is ON, the preview circle in Share Comparison renders at full opacity. When OFF, it renders at 40% opacity (`alpha = 0.4f`).

**S-03.** The `useBranding` state in `ShareComparisonViewModel` defaults to `true` when session branding is set (`hasBranding == true`), `false` when it is not.

**S-04.** The `useBranding` state is preserved in `ShareComparisonViewModel` memory when the user switches between Slider and Side-by-side styles within the same Share Comparison session. It is not persisted across screen re-opens.

**S-05.** When Share Comparison opens for a session with no branding and a global default exists, the global branding is copied into the session folder as `branding-handle.png` before the screen becomes interactive. After the copy, `hasBranding == true`. The session is self-contained from that point forward.

### Write rules

**W-01.** All logo writes in Settings are immediate. No Save button, no confirmation.

**W-02.** All branding writes in Share Comparison are immediate and write to `filesDir/sessions/<id>/branding-handle.png`. No writes ever occur to `filesDir/branding/handle.png` (the global default). The toggle state (`useBranding`) is the only Share Comparison state that is not persisted to disk.

**W-03.** "Choose photo" and "Use a symbol" in Settings and Share Comparison act as add or replace operations. No intermediate removal is required.

**W-04.** The "Remove logo" action in Settings and Share Comparison requires no confirmation dialog.

**W-05.** On any logo normalization failure: show a snackbar (`settings_logo_load_error` or `share_comparison_logo_error`). Do not change the current logo state. No partial update.

### Replacement rules (Share Comparison)

**R-01.** None → Photo: tap "Choose photo" → photo normalized and written atomically to `branding-handle.png`.
**R-02.** None → Symbol: tap "Use a symbol" → symbol rendered and written atomically to `branding-handle.png`.
**R-03.** Photo → Photo: tap "Choose photo" → new photo replaces existing `branding-handle.png`.
**R-04.** Photo → Symbol: tap "Use a symbol" → symbol replaces photo in `branding-handle.png`. No remove step required.
**R-05.** Symbol → Photo: tap "Choose photo" → photo replaces symbol in `branding-handle.png`. No remove step required.
**R-06.** Symbol → Symbol: tap "Use a symbol" → new symbol replaces existing `branding-handle.png`.
**R-07.** Any → None: tap "Remove logo" → `branding-handle.png` deleted from session folder; metadata updated.

All replacement directions are available from a single consistently-visible set of actions.

### Slider-only rules

**L-01.** Logo rendering is Slider-only. The logo never appears in Side-by-side exports regardless of session branding state.

**L-02.** Logo controls are Slider-only in Share Comparison. The Logo on handle card is absent when Side-by-side is selected.

**L-03.** No message, warning, or hint about Slider-only behavior is shown anywhere in the UI.

### Preview rules

**P-01.** In the Share Comparison Style card live preview, the branded handle is rendered when `style == SLIDER && useBranding == true && hasBranding == true`. In all other cases, the standard SameView handle is rendered.

**P-02.** The `BrandingPreviewCircle` must render identically in Settings and Share Comparison — same size (64 dp), same ring color, same fill color, same logo rendering.

**P-03.** Symbol cells in `BrandingSymbolPickerSheet` must render each symbol using the same visual specification as the actual branding handle: `SameViewAccent` ring, `#F5F7FA` fill, symbol at 72% of circle diameter, symbol at native VectorDrawable fill color. Preview = export result. No `colorFilter` override.

**P-04.** The placeholder circle must match the `BrandingPreviewCircle` geometry in Settings and Share Comparison: same 2 dp `SameViewAccent` border, same `#F5F7FA` fill, same `CircleShape`.

---

## 10. Implementation Blocks

Superseded. See `docs/implementation_plans/historic/SESSION_BRANDING_V2_IMPLEMENTATION_PLAN.md` for the corrected implementation plan.

---

## 11. Spec Impact

| Document | Section | What becomes invalid | Reason |
|---|---|---|---|
| `SESSION_BRANDING_V1.md` | §11 | Card title, description text, action labels, empty state spec | Superseded by §2 of this document |
| `SESSION_BRANDING_V1.md` | §12 | Entire Edit Session branding card | Edit Session no longer has branding — §3 of this document |
| `SESSION_BRANDING_V1.md` | §13.1–13.3 | Toggle placement, toggle behavior, side-by-side behavior | Superseded by §4 of this document |
| `SESSION_METADATA_EDITOR_V1.md` | §21 | Entire branding card spec | Edit Session no longer has branding |
| `SHARE_COMPARISON_IMAGE_V1.md` | FD-18 | Toggle-only card replaced by full management card | Superseded by §4 of this document |
| `SHARE_COMPARISON_IMAGE_V1.md` | §15.3 | Screen layout (Logo card contents) | Superseded by §4 of this document |
| `SETTINGS_UX_V1.md` | §11.1 | Action labels, description text | Superseded by §2 of this document |

**Documents with no changes required:**
`SESSION_BACKUP_EXPORT_V1.md`, `COMPARE_FLOW_V1.md`, `RESPONSIVE_LAYOUT_SYSTEM_V1.md`, `SESSION_ORIGINALS_V1.md`, `SESSION_ORIGINALS_PRIVACY_V1.md`, `GPS_RECREATION_SYSTEM_V1.md`, `VIDEO_EXPORT_V1.md`. All technical sections of `SESSION_BRANDING_V1.md` (§1–§10, §14–§20) remain valid. Metadata schema v6 is unchanged; existing sessions with `branding-handle.png` remain valid and scannable.
