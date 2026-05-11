# ABOUT_SCREEN_V1

This document defines the intended V1 About screen behavior and UX philosophy for GhostShot / Then & Now Camera.

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

# 3. V1 Scope

## Included

V1 About screen may contain:
- App icon
- App name
- Short subtitle/tagline
- Short app description
- Trust/privacy statements
- App version
- Optional build information
- Optional support email
- Back navigation

## Explicitly excluded

The following are intentionally NOT part of V1:
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

Recommended structure:

1. TopAppBar
2. Hero section
3. Trust section
4. Optional feedback/contact section
5. Footer/version section

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
- app name: GhostShot
- subtitle: Then & Now Camera
- short one-line description

Example tone:
- "Recreate photos with a reference overlay."

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

Recommended V1 statements:
- No tracking
- No cloud sync
- Photos stay on your device

Presentation:
- short rows
- optional icons
- no nested cards
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

Recommended V1 approach:
- visible support email address
- optional "Email support" action
- uses ACTION_SENDTO with mailto:

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
- Version 1.0
- optional small Build number

Version source:
- BuildConfig.VERSION_NAME

Optional:
- BuildConfig.VERSION_CODE shown as small secondary text

## Debug/internal builds

Optional:
- short Git SHA

Rules:
- commit hash must not dominate the UI
- commit hash should remain hidden in public releases unless explicitly required
- no runtime Git access
- no filesystem Git parsing
- no network dependency

Recommended implementation:
- optional buildConfigField generated during build
- fallback values allowed for local builds

---

# 10. Visual Design Rules

The About screen should visually align with:
- Settings
- CompareScreen
- CameraScreen dark theme

Preferred design:
- centered hero block
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

Preferred V1 architecture:
- dedicated AboutScreen composable
- no repository layer
- no DataStore integration
- no network dependency
- no ViewModel required unless future complexity demands it

Recommended structure:
- AboutScreenRoute reads BuildConfig values
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
- subtitle visible
- trust rows visible
- version visible

If support email exists:
- support action visible
- support action invokes callback or intent path correctly

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