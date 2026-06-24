# SESSION_BRANDING_V2_UX_REWORK.md

## Document Status

**Status:** Final — implementation-ready.

This document is the **authoritative UX specification** for Session Branding V2 in SameView.

It supersedes all branding-related UX content in the following documents. It does not supersede their technical sections:

| Superseded | Sections |
|---|---|
| `SESSION_BRANDING_V1.md` | §11, §12, §13.1–§13.3 |
| `SESSION_METADATA_EDITOR_V1.md` | §21 |
| `SHARE_COMPARISON_IMAGE_V1.md` | FD-18, §15.3 layout |
| `SETTINGS_UX_V1.md` | §11 |

The following are **unchanged and remain authoritative:**
- All storage architecture (V1 §1–§10, §14–§20)
- Metadata schema v6
- Backup integration
- Rendering pipeline
- Repository architecture

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
| "Copy from default branding" | "Use your default logo" |
| "Change branding" | *(removed — replaced by symmetric actions)* |
| "Use branding" | "Show logo" |
| "No branding for this session." | "No logo for this comparison." |
| "Add branding in Edit session." | "Add one in Edit session." |
| "Only applied in slider style." | *(removed — section is absent in side-by-side)* |

### String resource keys — new

| Key | Value |
|---|---|
| `settings_logo_section_title` | "Your logo" |
| `settings_logo_description` | "Shown in the slider handle when sharing comparison images. Copied to new sessions automatically." |
| `settings_logo_none` | "No logo set" |
| `settings_logo_current` | "Your current logo" |
| `settings_logo_choose_photo` | "Choose photo" |
| `settings_logo_use_symbol` | "Use a symbol" |
| `settings_logo_remove` | "Remove logo" |
| `settings_logo_load_error` | "Couldn't load logo" |
| `edit_session_card_logo` | "Logo" |
| `edit_session_logo_description` | "Appears in the slider handle when sharing this comparison." |
| `edit_session_logo_none` | "No logo for this comparison." |
| `edit_session_logo_type_photo` | "Photo" |
| `edit_session_logo_type_symbol` | "Symbol: %s" |
| `edit_session_logo_use_default` | "Use your default logo" |
| `edit_session_logo_choose_photo` | "Choose photo" |
| `edit_session_logo_use_symbol` | "Use a symbol" |
| `edit_session_logo_remove` | "Remove logo" |
| `edit_session_logo_error` | "Couldn't set logo" |
| `share_comparison_logo_card_title` | "Logo on handle" |
| `share_comparison_logo_none` | "No logo for this comparison." |
| `share_comparison_logo_hint` | "Add one in Edit session." |
| `share_comparison_logo_show` | "Show logo" |
| `branding_symbol_picker_title` | "Choose a symbol" |

### Symbol display names (for type indicator in Edit Session)

| Symbol ID | Display name |
|---|---|
| `heart` | Heart |
| `star` | Star |
| `camera` | Camera |
| `home` | Home |
| `pin` | Pin |
| `fire` | Fire |

### Strings deprecated by V2

The following V1 keys must be removed after V2 is implemented:

`settings_branding_section_title`, `settings_branding_description`, `settings_branding_choose_image`, `settings_branding_choose_symbol`, `settings_branding_remove`, `settings_branding_load_error`, `edit_session_card_branding`, `edit_session_branding_none`, `edit_session_branding_change`, `edit_session_branding_remove`, `edit_session_branding_copy_global`, `edit_session_branding_error`, `share_comparison_branding_label`, `share_comparison_branding_hint_edit_session`, `share_comparison_branding_hint_slider_only`.

All corresponding `values-de` keys are also deprecated.

---

## 2. Settings Screen UX

### Responsibility

Settings manages the **global default logo only**. The global logo is automatically copied into new sessions at creation time. It has no effect on sessions that already exist.

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
│  comparison images. Copied to new sessions       │
│  automatically.                                  │
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
│  comparison images. Copied to new sessions       │
│  automatically.                                  │
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
String: `settings_logo_description`.

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

**"Choose photo" button**
- `TextButton`
- Always visible, regardless of whether a logo is currently set
- When no logo: adds a new logo
- When logo exists: replaces it
- On tap: opens Photo Picker. On successful selection: logo normalized and saved; placeholder transitions to preview circle immediately
- String: `settings_logo_choose_photo`

**"Use a symbol" button**
- `TextButton`
- Always visible, regardless of whether a logo is currently set
- When no logo: adds a new logo
- When logo exists: replaces it
- On tap: opens `BrandingSymbolPickerSheet`. On selection: logo saved; placeholder transitions to preview circle immediately
- String: `settings_logo_use_symbol`

Both buttons displayed side by side in a `Row` with equal `weight(1f)`.

**"Remove logo" button**
- `TextButton`, full width
- Visible **only** when a logo is currently set
- On tap: logo deleted; preview circle transitions to placeholder; "Remove logo" button disappears
- No confirmation dialog
- String: `settings_logo_remove`

### Interaction rules

- All changes are immediate writes. No Save button, no confirmation.
- "Choose photo" and "Use a symbol" function identically in empty and populated states — they add or replace.
- State transitions are immediate: the circle updates the moment the selection completes.
- On normalization failure: show snackbar `settings_logo_load_error`. No state change.

---

## 3. Edit Session Screen UX

### Responsibility

Edit Session manages the **session-specific logo** for one comparison. Changes affect only this session. They do not affect the global default and do not affect any other session.

### Card position

Last card in the form. After the Location card.

Order: Session → Reference photo → Current photo → Location → **Logo**

This position is unchanged from V1.

### Card title

```
Logo
```

String: `edit_session_card_logo`.

### Supporting description

Always visible in both states, immediately below the card title:

```
Appears in the slider handle when sharing this comparison.
```

Style: `bodySmall` / `SameViewSettingsSecondaryText`. String: `edit_session_logo_description`.

### Layout — Empty state, no global logo

```
┌──────────────────────────────────────────────────┐
│  Logo                                            │
│                                                  │
│  Appears in the slider handle when sharing       │
│  this comparison.                                │
│                                                  │
│  ┌──────────┐                                    │
│  │  [icon]  │  No logo for this comparison.      │
│  └──────────┘                                    │
│                                                  │
│  [ Choose photo ]  [ Use a symbol ]              │
│                                                  │
└──────────────────────────────────────────────────┘
```

### Layout — Empty state, global logo exists

```
┌──────────────────────────────────────────────────┐
│  Logo                                            │
│                                                  │
│  Appears in the slider handle when sharing       │
│  this comparison.                                │
│                                                  │
│  ┌──────────┐                                    │
│  │  [icon]  │  No logo for this comparison.      │
│  └──────────┘                                    │
│                                                  │
│  [ Use your default logo ]                       │
│                                                  │
│  [ Choose photo ]  [ Use a symbol ]              │
│                                                  │
└──────────────────────────────────────────────────┘
```

### Layout — Populated state

```
┌──────────────────────────────────────────────────┐
│  Logo                                            │
│                                                  │
│  Appears in the slider handle when sharing       │
│  this comparison.                                │
│                                                  │
│  ┌──────────┐                                    │
│  │  [logo]  │  Photo                             │
│  └──────────┘  (or: Symbol: Heart)               │
│                                                  │
│  [ Choose photo ]  [ Use a symbol ]              │
│                                                  │
│  [ Remove logo ]                                 │
│                                                  │
└──────────────────────────────────────────────────┘
```

### Element specifications

**Placeholder circle**
Identical to Settings placeholder circle: 64 dp, 2 dp `SameViewAccent` border, `#F5F7FA` fill, 24 dp image-add icon in `SameViewSettingsSecondaryText`.

**Preview circle**
`BrandingPreviewCircle` at 64 dp. Same as Settings.

**State label (beside circle)**
- Empty: `edit_session_logo_none` — "No logo for this comparison."
- Populated (photo logo): `edit_session_logo_type_photo` — "Photo"
- Populated (symbol logo): `edit_session_logo_type_symbol` — "Symbol: [Name]" where [Name] is the symbol's display name (Heart, Star, Camera, Home, Pin, Fire), derived from `branding.builtinId` in session metadata
- Style: `bodySmall` / `SameViewSettingsSecondaryText`

**"Use your default logo" button**
- `TextButton`, full width
- Visibility condition: `!hasBranding && hasGlobalBranding`
- Positioned between the circle row and the "Choose photo" / "Use a symbol" row
- On tap: calls `copyGlobalBrandingToSession()`. Logo applied immediately; placeholder transitions to preview circle; type indicator updates; "Use your default logo" disappears; "Remove logo" appears
- String: `edit_session_logo_use_default`

**"Choose photo" and "Use a symbol" buttons**
- `TextButton`, side by side, equal weight
- **Always visible in both empty and populated states**
- In populated state: act as direct replacement — the current logo is replaced without a remove step
- "Choose photo": opens Photo Picker. String: `edit_session_logo_choose_photo`
- "Use a symbol": opens `BrandingSymbolPickerSheet`. String: `edit_session_logo_use_symbol`

**"Remove logo" button**
- `TextButton`, full width
- Visible **only** when a logo is set
- On tap: logo deleted immediately; card transitions to empty state
- No confirmation dialog
- String: `edit_session_logo_remove`

### Critical behavioral rules

- Logo changes write **immediately**. They are outside the form's Save/Discard flow.
- `isDirty` is **not** modified by logo changes.
- The Save button state is **not** modified by logo changes.
- Discarding form field changes does **not** revert logo changes.
- This behavior matches the Favorites star (§20, `SESSION_METADATA_EDITOR_V1.md`).

### The symmetric action fix

V1 populated state: "Change branding" (image only) + "Remove branding". Switching from photo to symbol required: Remove → then Use a symbol.

V2 populated state: "Choose photo" + "Use a symbol" both visible. Direct replacement. No intermediate removal required. This applies to all four switching directions: photo→photo, photo→symbol, symbol→photo, symbol→symbol.

---

## 4. Share Comparison Screen UX

### Responsibility

Share Comparison controls **export behavior only**: whether the session logo is applied to the current export instance. It does not add, change, or remove logos. Logo management belongs to Settings and Edit Session.

### Position in screen

A dedicated "Logo on handle" card, positioned **between the Style card and the Information card**.

Full card order:
1. Style card (Slider / Side-by-side + preview)
2. **Logo on handle** ← V2 addition (Slider only)
3. Information card (Title / Date / Location)
4. Quality card (Standard / Original)
5. Share button

### Visibility

The Logo on handle card is rendered **only when the Slider style is selected**.

When Side-by-side is selected: the card is absent. No placeholder. No disabled state. No message. Absent.

When the user switches from Slider to Side-by-side, the card disappears. When they switch back to Slider, the card reappears with its previous toggle state.

### Card title

```
Logo on handle
```

String: `share_comparison_logo_card_title`.

### Layout — Empty state (session has no logo)

```
┌──────────────────────────────────────────────────┐
│  Logo on handle                                  │
│                                                  │
│  ┌──────────┐                                    │
│  │  [icon]  │  No logo for this comparison.      │
│  └──────────┘  Add one in Edit session.          │
│                                                  │
└──────────────────────────────────────────────────┘
```

**No action buttons in the empty state.** The card is informational. Logo management is not Share Comparison's responsibility.

**Placeholder circle:** 64 dp, same geometry as Settings and Edit Session.

**State text:**
- Line 1: `share_comparison_logo_none` — "No logo for this comparison."
- Line 2: `share_comparison_logo_hint` — "Add one in Edit session."
- Both lines: `bodySmall` / `SameViewSettingsSecondaryText`

### Layout — Populated state, toggle ON

```
┌──────────────────────────────────────────────────┐
│  Logo on handle                                  │
│                                                  │
│  ┌──────────┐  [switch ●]  Show logo            │
│  │  [logo]  │                                    │
│  └──────────┘                                    │
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
└──────────────────────────────────────────────────┘
```

### Element specifications — populated state

**Preview circle**
- `BrandingPreviewCircle` at 64 dp when toggle is ON
- When toggle is OFF: same composable, `alpha = 0.4f`. Communicates that the logo exists but is not applied to this export.

**"Show logo" toggle**
- `SettingsSwitchRow`, label: `share_comparison_logo_show` — "Show logo"
- Positioned in a `Row` with the preview circle: circle left (64 dp), toggle row right (`weight(1f)`)
- On change: updates `useBranding` in `ShareComparisonViewModel`; live preview in Style card updates immediately
- String: `share_comparison_logo_show`

**No change or remove actions.** The only interactive element in the populated Logo card is the toggle.

### Toggle behavior

- **Default ON** when session has a logo (`sessionBrandingFile != null`)
- **Default OFF** when session has no logo
- **Not persisted.** Resets to default each time the screen opens
- When ON and style is Slider: `SliderRenderStrategy` renders the branded handle
- When OFF: `SliderRenderStrategy` renders the standard SameView handle
- State is preserved in `ShareComparisonViewModel` memory when the user switches between Slider and Side-by-side. On return to Slider, the toggle shows the same state it held before the style switch.

---

## 5. Symbol Picker Specification

### Component name

`BrandingSymbolPickerSheet`

### Type

`ModalBottomSheet` (Material 3). Replaces `BuiltinSymbolPickerDialog` (AlertDialog).

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
- **Symbol icon:** centered, scaled to 72% of circle diameter, `SameViewSettingsLabelText` color
- **Name label:** `labelSmall` / `SameViewSettingsLabelText`, sentence case, below the circle

Grid: `LazyVerticalGrid`, `GridCells.Fixed(3)`, `horizontalArrangement = Arrangement.spacedBy(8.dp)`, `verticalArrangement = Arrangement.spacedBy(8.dp)`.

### Interaction

- Tapping a cell: selection is made → sheet dismisses → logo written to destination → caller UI updates
- Tapping "Cancel": sheet dismisses → no selection → no change to current logo state
- Drag down to dismiss: equivalent to Cancel — no selection, no change

### Callers

`BrandingSymbolPickerSheet` is called identically from:
- `SettingsScreen` (global logo management)
- `EditSessionScreen` (session logo management)

Callback signature: `onSymbolSelected: (BuiltinBrandingSymbol) -> Unit`

### Symbols fixed in V2

Six symbols only: heart, star, camera, home, pin, fire. No color customization. No additional symbols. The six custom VectorDrawable assets from V1 are unchanged.

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

### Edit Session — Logo Card

| Tier | Element | Rationale |
|---|---|---|
| 1 | Circle (placeholder or preview) | User has context from prior screens; visual first |
| 2 | Type label beside circle | Confirms type of current logo |
| 3 | "Use your default logo" (conditional) | Fastest path when global default exists |
| 4 | "Choose photo" + "Use a symbol" | Equal-weight add/replace actions |
| 5 | "Remove logo" | Secondary destructive action |

### Share Comparison — Logo on Handle

| Tier | Element | Rationale |
|---|---|---|
| 1 | Circle (placeholder or dimmed preview) | Immediate visual state |
| 2 | "Show logo" toggle (populated) | The only interactive element in this card |
| — | State message + hint (empty) | Informational only |

### Consistency references

| Feature | Primary control | Pattern |
|---|---|---|
| Video branding endcard | `SettingsSwitchRow` in CreateVideoScreen | Export option in export screen |
| Caption toggles | `SettingsSwitchRow` in ShareComparisonScreen | Export option in export screen |
| Favorites | Star icon, immediate write | Co-located with session, visual-first |
| GPS Live direction arrow | Disabled `SettingsSwitchRow` when parent OFF | Dependent control correctly disabled |
| Grid type | `SameViewSegmentControl` in Settings | Persistent camera preference in Settings |
| **Logo** | **Circle + `TextButton` actions** | **Visual-first, export option in export screen** |

The logo circle serves the same role the Favorites star serves: it is a recognizable visual element that immediately communicates state before the user reads any label.

---

## 7. User Journeys

### Journey A — New user creates a default logo

1. User opens Settings via the CameraScreen overflow menu.
2. User scrolls to the "Your logo" section.
3. User sees the placeholder circle and reads: "Shown in the slider handle when sharing comparison images. Copied to new sessions automatically."
4. User understands the feature from the description alone — no prior knowledge required.
5. User taps "Use a symbol."
6. `BrandingSymbolPickerSheet` opens. User sees six symbols rendered as handle previews — they see exactly how each will look in the export.
7. User selects Star.
8. Sheet dismisses. Preview circle appears in the Settings card showing the Star symbol.
9. User closes Settings and captures a new comparison.
10. User opens Share Comparison for that session.
11. "Logo on handle" card is visible (Slider is selected). Preview circle shows the Star. Toggle is ON.
12. Live preview in the Style card shows the branded handle.
13. User taps Share. Export contains the Star symbol in the handle.

---

### Journey B — User overrides logo for one session

1. User has a default Star symbol set in Settings.
2. User opens a specific session and accesses Edit Session.
3. Logo card shows the Star symbol preview and type label "Symbol: Star."
4. User wants to use their company photo logo for this specific session.
5. User taps "Choose photo."
6. Photo Picker opens. User selects their company logo.
7. Photo Picker closes. Preview circle updates immediately — company logo visible. Type label changes to "Photo."
8. User taps Save for the form fields. Logo change was already written — unaffected by the Save operation.
9. When this session is shared, it uses the company photo logo.
10. All other sessions continue to use the Star symbol (their default).
11. Settings still shows the Star — unchanged.

---

### Journey C — User shares a branded comparison

1. User opens Share Comparison for a session that has a logo.
2. Slider is selected by default. The "Logo on handle" card is visible below the Style card.
3. Card shows the preview circle and the "Show logo" toggle in the ON state.
4. The live preview in the Style card shows the branded handle at the center of the comparison.
5. User switches to Side-by-side to check. The "Logo on handle" card disappears entirely. Live preview shows two plain images. No toggle. No message.
6. User switches back to Slider. The "Logo on handle" card reappears with the toggle ON, exactly as before.
7. User taps Share. The export is created with the logo in the handle.

---

### Journey D — User removes the logo from a session

1. User opens Edit Session for a session that has a logo.
2. Logo card shows preview circle, type label "Photo."
3. User decides this session should not have a logo.
4. User taps "Remove logo."
5. Logo is deleted immediately. Card transitions to empty state: placeholder circle, "No logo for this comparison."
6. If global branding exists, "Use your default logo" now appears.
7. Other sessions are unchanged. Settings global logo is unchanged.
8. User opens Share Comparison for this session.
9. "Logo on handle" card shows empty state: placeholder circle, "No logo for this comparison. Add one in Edit session."
10. "Show logo" toggle does not appear — there is no logo to toggle.

---

## 8. Responsibility Boundaries

These boundaries define which surface owns which action. They must not overlap.

### Settings

**Owns:** Global default logo.

**Permitted actions:**
- Add global logo (Choose photo / Use a symbol)
- Replace global logo (Choose photo / Use a symbol)
- Remove global logo (Remove logo)

**Does not permit:**
- Any action affecting an existing session's logo
- Any export behavior control

**Effect timing:** The global logo is copied into sessions at **creation time only**. Changes to Settings have no effect on sessions that already exist.

---

### Edit Session

**Owns:** The logo assigned to one specific session.

**Permitted actions:**
- Add session logo (Choose photo / Use a symbol)
- Replace session logo (Choose photo / Use a symbol — symmetric, no intermediate removal)
- Apply global default to this session (Use your default logo)
- Remove session logo (Remove logo)

**Does not permit:**
- Any action affecting the global default in Settings
- Any action affecting any other session
- Any export behavior control

**Effect timing:** Immediate write. Outside the form's Save/Discard flow.

---

### Share Comparison

**Owns:** Whether the session logo is applied to the current export instance.

**Permitted actions:**
- Toggle logo on/off for the current export (Show logo toggle)

**Does not permit:**
- Adding, replacing, or removing logos
- Modifying the global default
- Any action affecting session metadata

**Effect timing:** The toggle state is not persisted. Defaults reset when the screen opens.

---

### Boundary enforcement

| Action | Settings | Edit Session | Share Comparison |
|---|---|---|---|
| Add / replace logo | ✓ | ✓ | ✗ |
| Remove logo | ✓ | ✓ | ✗ |
| Apply default to session | ✗ | ✓ | ✗ |
| Set global default | ✓ | ✗ | ✗ |
| Control export toggle | ✗ | ✗ | ✓ |
| Affect other sessions | ✗ | ✗ | ✗ |
| Affect existing sessions | ✗ | ✗ | ✗ |

---

## 9. Behavioral Rules

### Visibility rules

**V-01.** The Logo on handle card in Share Comparison is rendered if and only if the current style is `ShareComparisonStyle.SLIDER`. It is absent for `SIDE_BY_SIDE`. No disabled state, no warning, no placeholder.

**V-02.** "Remove logo" in Settings is visible if and only if a global logo is currently set (`hasBranding == true`).

**V-03.** "Remove logo" in Edit Session is visible if and only if the session has a logo (`hasBranding == true`).

**V-04.** "Use your default logo" in Edit Session is visible if and only if the session has no logo (`hasBranding == false`) AND the global logo exists (`hasGlobalBranding == true`).

**V-05.** "Choose photo" and "Use a symbol" are visible at all times in both Settings and Edit Session, regardless of whether a logo is currently set.

**V-06.** The "Show logo" toggle in Share Comparison is visible if and only if `hasBranding == true` (session has a logo). It does not appear in the empty state.

### State rules

**S-01.** The preview circle (`BrandingPreviewCircle`) is shown when a logo is set. The placeholder circle is shown when no logo is set. There is no state where neither circle is shown.

**S-02.** When the "Show logo" toggle is ON, the preview circle in Share Comparison renders at full opacity. When OFF, it renders at 40% opacity (`alpha = 0.4f`).

**S-03.** The `useBranding` state in `ShareComparisonViewModel` defaults to `true` when `sessionBrandingFile != null`, `false` when `sessionBrandingFile == null`.

**S-04.** The `useBranding` state is preserved in `ShareComparisonViewModel` memory when the user switches between Slider and Side-by-side styles within the same Share Comparison session. On returning to Slider, the toggle shows the same state it held before the style switch.

### Write rules

**W-01.** All logo writes in Settings are immediate. No Save button, no confirmation.

**W-02.** All logo writes in Edit Session are immediate. They are outside the form's Save/Discard flow. `isDirty` is not affected.

**W-03.** "Choose photo" and "Use a symbol" in Settings and Edit Session act as add or replace operations. The caller does not need to remove an existing logo before calling either action. If a logo exists, it is replaced atomically.

**W-04.** The "Remove logo" action in both Settings and Edit Session requires no confirmation dialog. It is immediate.

**W-05.** On any logo write failure: show a snackbar (`settings_logo_load_error` or `edit_session_logo_error`). Do not change the current logo state. No partial update.

### Replacement rules

**R-01.** Photo → Photo: tap "Choose photo" → new photo replaces existing.
**R-02.** Photo → Symbol: tap "Use a symbol" → symbol replaces existing photo. No remove step required.
**R-03.** Symbol → Photo: tap "Choose photo" → new photo replaces existing symbol. No remove step required.
**R-04.** Symbol → Symbol: tap "Use a symbol" → new symbol replaces existing symbol.

All four replacement directions are available from a single set of consistently visible actions.

### Slider-only rules

**L-01.** Logo rendering is Slider-only. The logo never appears in Side-by-side exports regardless of session branding state.

**L-02.** Logo controls are Slider-only in Share Comparison. The Logo on handle card is absent when Side-by-side is selected.

**L-03.** Session branding files are not affected by the user selecting Side-by-side. The file remains; it is simply not rendered.

**L-04.** No message, warning, or hint about Slider-only behavior is shown anywhere in the UI.

### Preview rules

**P-01.** In the Share Comparison Style card live preview, the branded handle is rendered when `style == SLIDER && useBranding == true && sessionBrandingFile != null`. In all other cases, the standard SameView handle is rendered.

**P-02.** The `BrandingPreviewCircle` must render identically in Settings, Edit Session, and Share Comparison — same size (64 dp), same ring color, same fill color, same logo rendering. Visual consistency across all surfaces is required.

**P-03.** Symbol cells in `BrandingSymbolPickerSheet` must render each symbol using the same visual specification as the actual branding handle: `SameViewAccent` ring, `#F5F7FA` fill, symbol at 72% of circle diameter. Preview = export result.

**P-04.** The placeholder circle must match the `BrandingPreviewCircle` geometry in Settings, Edit Session, and Share Comparison: same 2 dp `SameViewAccent` border, same `#F5F7FA` fill, same `CircleShape`. Only the interior differs.

---

## 10. Implementation Blocks

Each block is independently testable and regression-safe. Blocks build on each other in order.

---

### Block 1 — Settings screen wording and layout

**Goal:** Redesign the Settings logo section with V2 title, always-visible description, placeholder circle in empty state, renamed action labels, and "Remove logo" conditional visibility.

**Affected files:**
- `SettingsScreen.kt`
- `strings.xml` (EN)
- `strings-de.xml` (DE)

**Changes:**
- Card title: `settings_branding_section_title` → `settings_logo_section_title`
- Add always-visible description (`settings_logo_description`)
- Placeholder circle composable (64 dp, `SameViewAccent` ring, `#F5F7FA` fill, image-add icon) shown when `!hasBranding`
- State label beside circle: `settings_logo_none` / `settings_logo_current`
- Rename "Choose image" → "Choose photo" (`settings_logo_choose_photo`)
- Rename "Choose symbol" → "Use a symbol" (`settings_logo_use_symbol`)
- Rename "Remove" → "Remove logo" (`settings_logo_remove`)
- Add new string keys; deprecate old ones

**Risks:** Low. Layout and string changes only. No ViewModel logic changes. Test tags may change — update accordingly.

**Required tests:**
- `SettingsScreenTest`: verify new title visible, description always visible, placeholder circle shown when `hasBranding == false`, preview circle shown when `hasBranding == true`, "Remove logo" visible only when `hasBranding == true`, "Choose photo" and "Use a symbol" always visible.

---

### Block 2 — Symbol BottomSheet migration

**Goal:** Replace `BuiltinSymbolPickerDialog` (AlertDialog) with `BrandingSymbolPickerSheet` (ModalBottomSheet). Render symbol cells as 56 dp handle previews.

**Affected files:**
- New: `BrandingSymbolPickerSheet.kt`
- `SettingsScreen.kt` (replace dialog call with sheet call)
- `EditSessionScreen.kt` (replace dialog call with sheet call)
- `strings.xml`: `branding_symbol_picker_title`

**Changes:**
- New `BrandingSymbolPickerSheet` composable: `ModalBottomSheet`, drag handle, title, 3-column `LazyVerticalGrid`, Cancel `TextButton`
- Each cell: 56 dp handle-style preview circle (ring + fill + symbol at 72%) + name label
- Callback: `onSymbolSelected: (BuiltinBrandingSymbol) -> Unit` — same signature as current dialog

**Risks:** Medium. `ModalBottomSheet` dismissal lifecycle differs from `AlertDialog`. Back gesture and drag-down dismissal must not trigger symbol selection. Test both callers.

**Required tests:**
- `BrandingSymbolPickerSheetTest`: sheet title visible, all 6 symbol cells visible, tapping a cell calls `onSymbolSelected` with correct symbol and dismisses, Cancel button dismisses without calling `onSymbolSelected`, drag-down dismisses without calling `onSymbolSelected`
- `SettingsScreenTest`: "Use a symbol" tap opens sheet (not dialog)
- `EditSessionScreenTest`: "Use a symbol" tap opens sheet (not dialog)

---

### Block 3 — Edit Session logo card redesign

**Goal:** Implement symmetric action model, type indicator, "Use your default logo" rename, placeholder circle, and correct conditional visibility for all actions.

**Affected files:**
- `EditSessionScreen.kt`
- `EditSessionViewModel.kt` (add branding type state)
- `strings.xml`, `strings-de.xml`

**Changes:**
- Card title: "Branding" → "Logo" (`edit_session_card_logo`)
- Add always-visible description (`edit_session_logo_description`)
- Placeholder circle in empty state (same spec as Block 1)
- Add type indicator: read `branding.type` and `branding.builtinId` from session metadata during ViewModel `init`; expose as `sessionLogoType: StateFlow<String?>` and `sessionLogoBuiltinId: StateFlow<String?>`
- "Copy from default branding" → "Use your default logo" (`edit_session_logo_use_default`); visibility condition unchanged
- Add "Choose photo" + "Use a symbol" to populated state (absent in V1 when branding was set)
- Remove "Change branding" button entirely
- "Remove branding" → "Remove logo" (`edit_session_logo_remove`)
- Update test tags accordingly

**Risks:** Medium. ViewModel addition for type state. Symmetric action model requires that "Choose photo" and "Use a symbol" in populated state call the replace path, not an add path — confirm that existing `onImageUriSelectedForBranding()` and `onSetSessionBrandingFromSymbol()` correctly overwrite existing branding without error.

**Required tests:**
- `EditSessionViewModelTest`: `sessionLogoType` returns "image" when photo logo set, "builtin" when symbol set, null when no logo
- `EditSessionScreenTest`: "Choose photo" visible in empty and populated state, "Use a symbol" visible in empty and populated state, type indicator shows "Photo" for `type == "image"`, type indicator shows "Symbol: Heart" for `type == "builtin", builtinId == "heart"`, "Use your default logo" visible only when `!hasBranding && hasGlobalBranding`, "Remove logo" visible only when `hasBranding == true`, placeholder circle visible when no logo, preview circle visible when logo set

---

### Block 4 — Share Comparison logo card extraction and redesign

**Goal:** Remove branding toggle from Style card interior. Create standalone "Logo on handle" card. Implement Slider-only conditional rendering. Rename toggle label.

**Affected files:**
- `ShareComparisonScreen.kt`
- `strings.xml`, `strings-de.xml`

**Changes:**
- Remove `InfoToggleRow` for branding from Style card
- Remove `share_comparison_branding_hint_slider_only` references
- Add new `SettingsCard` composable "Logo on handle" between Style card and Information card
- Conditional rendering: `if (style == ShareComparisonStyle.SLIDER) { LogoOnHandleCard(...) }`
- Empty state: placeholder circle + `share_comparison_logo_none` + `share_comparison_logo_hint`
- Populated state: `BrandingPreviewCircle` (with conditional `alpha`) + `SettingsSwitchRow` (`share_comparison_logo_show`)
- `BrandingPreviewCircle` alpha: `if (useBranding) 1f else 0.4f`
- No ViewModel behavior change — `useBranding` and `hasBranding` StateFlows unchanged

**Risks:** Medium. Scroll height changes (new card). Conditional rendering must be tested for both style states and for the style-switch transition. Verify that removing the branding toggle from the Style card does not break existing `share_comparison_style_control` test tags.

**Required tests:**
- `ShareComparisonScreenTest`: "Logo on handle" card visible when Slider selected, card absent when Side-by-side selected, placeholder circle visible when `hasBranding == false`, preview circle visible when `hasBranding == true`, "Show logo" toggle present when `hasBranding == true`, toggle absent when `hasBranding == false`, no branding toggle inside Style card, no "Only applied in slider style" string visible, no disabled branding toggle in Side-by-side mode

---

### Block 5 — String cleanup

**Goal:** Remove all deprecated V1 branding string keys. Confirm no references remain.

**Affected files:**
- `strings.xml`
- `strings-de.xml`
- Any remaining references to deprecated keys (will surface as compile errors, ensuring completeness)

**Deprecated keys to remove:**
`settings_branding_section_title`, `settings_branding_description`, `settings_branding_choose_image`, `settings_branding_choose_symbol`, `settings_branding_remove`, `settings_branding_load_error`, `edit_session_card_branding`, `edit_session_branding_none`, `edit_session_branding_change`, `edit_session_branding_remove`, `edit_session_branding_copy_global`, `edit_session_branding_error`, `share_comparison_branding_label`, `share_comparison_branding_hint_edit_session`, `share_comparison_branding_hint_slider_only`.

**Risks:** Low. Compile-time detection ensures completeness — any remaining reference causes a build failure, making this block self-verifying.

**Required tests:** Build must succeed cleanly (`assembleDebug`, `assembleRelease`). No new test cases required.

---

## 11. Spec Impact

The following specification documents contain sections that are superseded by this document. They must be updated after V2 is implemented.

| Document | Section | What becomes invalid | Reason |
|---|---|---|---|
| `SESSION_BRANDING_V1.md` | §11 | Card title, description text, action labels, empty state spec | All superseded by §2 of this document |
| `SESSION_BRANDING_V1.md` | §12.2 | Card contents (asymmetric state machine, "Change branding" label) | Superseded by §3 of this document |
| `SESSION_BRANDING_V1.md` | §13.1 | Toggle placement (inside Style card), toggle label ("Use branding"), disabled state with hint | Superseded by §4 of this document |
| `SESSION_BRANDING_V1.md` | §13.3 | Side-by-side: "toggle present but visually indicates Slider-only" | Superseded by §4 and rule V-01 of this document |
| `SESSION_METADATA_EDITOR_V1.md` | §21.2 | Card contents (both state definitions, asymmetric actions) | Superseded by §3 of this document |
| `SESSION_METADATA_EDITOR_V1.md` | §21.5 | Symbol picker as AlertDialog | Superseded by §5 of this document |
| `SHARE_COMPARISON_IMAGE_V1.md` | FD-18 | Toggle "always visible," side-by-side toggle behavior | Superseded by §4 and §9 of this document |
| `SHARE_COMPARISON_IMAGE_V1.md` | §15.3 | Screen layout diagram (branding inside Style card) | Superseded by §4 of this document |
| `SETTINGS_UX_V1.md` | §11.1 | Action labels, description text | Superseded by §2 of this document |

**Documents with no changes required:**
`SESSION_BACKUP_EXPORT_V1.md`, `COMPARE_FLOW_V1.md`, `RESPONSIVE_LAYOUT_SYSTEM_V1.md`, `SESSION_ORIGINALS_V1.md`, `SESSION_ORIGINALS_PRIVACY_V1.md`, `GPS_RECREATION_SYSTEM_V1.md`, `VIDEO_EXPORT_V1.md`. All technical sections of `SESSION_BRANDING_V1.md` (§1–§10, §14–§20) remain valid and are unaffected.
