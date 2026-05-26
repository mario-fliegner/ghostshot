# ABOUT_SCREEN_V2

This document defines the intended V2 About screen behavior and UX philosophy for SameView.

This specification defines:
- About screen purpose
- UX structure
- allowed content
- excluded content
- version/build strategy
- contact strategy
- navigation behavior
- testing expectations

This document complements:
- CAMERA_WORKFLOW_UX_V1
- SETTINGS_UX_V1
- CLAUDE_PROJECT_INSTRUCTION.md

This document does NOT redefine:
- settings architecture
- compare behavior
- session handling
- navigation contracts outside the About route
- privacy/storage architecture

---

# 1. Core Philosophy

The About screen exists to provide:
- lightweight app information
- trust and privacy clarity
- version visibility
- optional support contact access

The About screen must remain:
- calm
- minimal
- trustworthy
- non-technical for normal users
- visually aligned with the rest of the app

The About screen must NOT become:
- a debug screen
- a developer console
- a changelog browser
- a legal-document dumping ground
- a settings replacement
- a diagnostics screen

---

# 2. Navigation Placement

About is an app-level destination.

About is opened from:
- CameraScreen top-right Overflow menu

Current overflow entries:
- Settings
- About

About is intentionally separated from Settings.

Reason:
- Settings represent configuration
- About represents app identity and trust information

About must open as:
- a dedicated fullscreen screen
- using normal Navigation Compose routing

About must NOT open as:
- dialog
- bottom sheet
- overlay panel
- nested settings page

---

# 3. V2 Scope

## Included

V2 About screen may contain:
- App icon
- App name
- Short app description
- Trust/privacy statements
- App version
- App version code
- Optional support email
- Back navigation

## Explicitly excluded

The following are intentionally NOT part of V2:
- changelog
- open-source license browser
- WebView
- diagnostics output
- commit history UI
- crash-reporting UI
- analytics disclosures for systems that do not exist
- hidden debug gestures
- backend/system status
- camera capability reports
- storage path display
- session folder browser
- developer-mode toggles
- export/import features

---

# 4. UX Structure

The About screen should feel visually lighter and calmer than Settings.

Current V2 structure:

1. TopAppBar
2. Bounded content column
3. Hero card
4. Footer card with version and feedback action

---

# 5. TopAppBar

Required:
- Title: About
- Back button

Back behavior:
- returns to previous screen
- must not clear active camera session state
- must not recreate app navigation

---

# 6. Hero Section

The hero section is the visual identity block.

Recommended contents:
- app icon
- app name: SameView
- short one-line description
- short trust/privacy statements

Example tone:
- "Recreate past photos with live overlays."

Rules:
- concise
- calm
- no marketing overload
- no feature list explosion

The hero section should visually anchor the screen.

---

# 7. Trust Section

Purpose:
Provide fast reassurance about privacy and product philosophy.

Current V2 statements:
- No tracking
- No cloud sync
- Photos stay on your device

Presentation:
- short text statements
- visually quiet
- no legal-style paragraphs

Rules:
- statements must remain factually true
- do not imply guarantees that the app cannot enforce
- wording must remain simple and human-readable

Forbidden:
- exaggerated privacy claims
- technical storage explanations
- backend architecture descriptions

---

# 8. Contact / Feedback

Contact is optional.

It should exist ONLY when a real support address is actively maintained.

Current V2 approach:
- lightweight feedback action
- uses ACTION_SENDTO with mailto:
- shows a snackbar fallback when no email app is available

Forbidden:
- embedded feedback forms
- WebView contact forms
- hidden contact methods
- fake placeholder contact buttons

If no real support address exists:
- omit the contact section entirely

---

# 9. Version / Build Information

The About screen should expose lightweight release information.

## Release builds

Recommended:
- Version 1.0 (1)

Version source:
- BuildConfig.VERSION_NAME
- BuildConfig.VERSION_CODE

Rules:
- version and code must remain lightweight footer information
- commit hash must not be shown in normal release builds
- no runtime Git access
- no filesystem Git parsing
- no network dependency
- no additional build automation for About V2

---

# 10. Visual Design Rules

The About screen should visually align with:
- Settings
- CompareScreen
- CameraScreen dark theme

Preferred design:
- centered, max-width bounded content
- hero card aligned with Settings card language
- separate calm footer card
- generous spacing
- calm typography hierarchy
- restrained icon usage
- low visual noise

Avoid:
- dashboard-style cards
- excessive dividers
- giant settings-style lists
- marketing-heavy layouts
- overloaded legal formatting

---

# 11. Accessibility

The About screen must remain accessible.

Requirements:
- all visible text from string resources
- readable contrast
- accessible back navigation
- touch targets remain comfortably tappable
- trust statements understandable without relying only on icons

Rules:
- decorative icon duplication should not create noisy screen-reader output
- avoid duplicate content descriptions

---

# 12. i18n

All visible text must use string resources.

At minimum:
- About title
- subtitle
- trust statements
- support labels
- version labels

English and German must remain aligned.

---

# 13. Architecture Rules

The About screen should remain lightweight.

Preferred V2 architecture:
- dedicated AboutScreen composable
- no repository layer
- no DataStore integration
- no network dependency
- no ViewModel required unless future complexity demands it

Recommended structure:
- AboutScreenRoute reads BuildConfig.VERSION_NAME and BuildConfig.VERSION_CODE
- AboutScreenContent receives plain data parameters

This allows:
- easy previewing
- easy testing
- fake version/build values in tests

---

# 14. Testing Expectations

Minimum expected tests:

Navigation:
- About opens from Overflow
- Back returns correctly

UI:
- app name visible
- description visible
- trust statements visible
- version visible
- version code visible

If support email exists:
- support action visible
- support action invokes callback or intent path correctly
- no-email-app fallback remains visible through snackbar

Preferred:
- AboutScreenContent parameterized for fake version/build testing

Not required in V1:
- screenshot/golden tests
- changelog tests
- license tests
- network tests

---

# 15. Persistence Rules

The About screen stores no persistent user state.

No About preferences are planned.

The About screen must not:
- create onboarding flags
- create hidden settings
- persist acknowledgements

---

# 16. Future Extensions

Possible future additions MAY include:
- acknowledgements
- OSS licenses
- privacy policy link
- help/tutorial link
- beta-feedback links

These are explicitly future scope.

Do not implement infrastructure for them in V1.

---

# 17. Explicit Non-Goals

The About screen is intentionally NOT:
- a diagnostics screen
- a developer portal
- a storage inspector
- a hidden debug area
- a legal compliance dump
- a marketing microsite
- a second settings screen

The About screen should remain small, stable, and trustworthy.

---

# 18. Final UX Intent

The About screen should feel like:
- a calm product identity page
- lightweight and trustworthy
- easy to leave again
- visually consistent with the app

The user should quickly understand:
- what the app is
- that it stays local/offline
- which version is installed
- how to contact support (if available)

without entering a technical or cluttered experience.
