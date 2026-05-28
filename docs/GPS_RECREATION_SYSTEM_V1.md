# GPS_RECREATION_SYSTEM_V1.md

## Purpose

This document is the single authoritative specification for GPS and location-related functionality in SameView.

All GPS architecture, UX decisions, permission strategy, data model, lifecycle rules, and product boundaries are defined here.

No other document should contain GPS implementation detail. Cross-references from other documents point here. The GPS architecture must not be fragmented across multiple specification files.

---

# 1. Recreation Philosophy

## The Two-Phase Model

Recreation in SameView is a two-phase process. GPS is relevant only in Phase 1.

**Phase 1 — Location** (GPS-assisted):
The user moves to approximately where the reference photo was taken. GPS provides a distance and bearing indicator. This phase ends when the user is close enough to begin visual alignment.

**Phase 2 — Alignment** (visual, manual):
The user aligns the live camera preview with the reference overlay. Camera angle, framing, zoom level, perspective height, and timing are entirely the photographer's decisions. GPS plays no role in Phase 2. A perfect GPS match does not guarantee a correct recreation. A slightly imperfect GPS match with excellent visual alignment is a successful recreation.

## Core Statement

> GPS finds the place. The human finds the image.

## GPS is Assistive, Not Authoritative

GPS provides one piece of contextual information: approximate distance and bearing to the original capture location.

GPS does not:
- determine whether framing or alignment is correct
- indicate when the photo is "ready" to take
- replace camera angle, zoom, or perspective judgment
- guide the photographer beyond approximate location

The capture button is always available regardless of GPS state. GPS never approves, blocks, or suggests readiness.

---

# 2. Architectural Constraints (Non-Negotiable)

These constraints protect the deterministic compare architecture defined in `COMPARE_SESSION_RENDERING_V1.md`.

**GPS must never affect Compare rendering.**
`ReferenceRenderer.render()` must not receive GPS data. GPS coordinates have no role in rendering geometry.

**GPS must never affect overlay geometry.**
`overlayScale`, `overlayOffsetX`, `overlayOffsetY`, `referenceImageDisplayMode`, and viewport dimensions are frozen at capture time by `CaptureSessionSnapshot` based on user interaction only. GPS data cannot influence these values.

**GPS must never trigger automatic alignment.**
No auto-centering, auto-clamping, or auto-scaling of the overlay based on GPS data is permitted.

**GPS data is architecturally separate from rendering state.**
`GpsSnapshot` is optional metadata attached to a session. It does not participate in the rendering pipeline. Compare rendering remains deterministic and geometry-based regardless of GPS data presence or absence.

**GPS is read-only for UI guidance.**
GPS drives only the distance/bearing indicator on CameraScreen. It does not modify any rendering state.

**GPS is write-once for session metadata.**
At capture time, the GPS fix is frozen and written to EXIF and `metadata.json`. After session save, GPS data in that session is immutable. No subsequent GPS update can alter saved session data.

---

# 3. Feature Boundaries

## What GPS does in SameView

- Reads GPS coordinates from the EXIF of the reference image (passive, no permission required)
- Reads the current device location when CameraScreen is active and Recreation Guidance is enabled
- Shows a minimalist distance and bearing indicator on CameraScreen when reference GPS data is available
- Freezes the GPS fix at capture time into an immutable `GpsSnapshot`
- Writes GPS coordinates to EXIF of the MediaStore capture image and `capture.jpg`
- Writes structured GPS fields to `metadata.json`

## What GPS explicitly does NOT do

- No map display of any kind
- No route calculation or step-by-step navigation guidance
- No address, place-name, or neighborhood display
- No real-time location tracking or location history
- No automatic overlay alignment or geometry correction
- No AR overlay based on GPS coordinates or orientation
- No geofencing or location-triggered behavior
- No multi-point location management
- No background location tracking
- No location sharing functionality
- No cloud storage of location data
- No influence on Compare rendering or overlay geometry

---

# 4. Permission Architecture

## Required Permission

`ACCESS_FINE_LOCATION`

**Why FINE and not COARSE:**
`ACCESS_COARSE_LOCATION` provides approximately 100–500m accuracy via network/WiFi triangulation. This is not useful for recreation guidance where meaningful proximity is within 5–20m. `ACCESS_FINE_LOCATION` delivers 3–15m GPS accuracy in good outdoor conditions, which is appropriate for this use case.

**Why LocationManager, not FusedLocationProviderClient:**
SameView has no INTERNET permission and no Google Play Services dependency. `LocationManager` with `GPS_PROVIDER` as primary and `NETWORK_PROVIDER` as fallback is fully sufficient. This keeps the app offline-first with no external service dependency and no Play Services requirement.

## Lazy Permission Strategy

Location permission must never be requested on app start or when no GPS feature action has been taken.

Permission is requested when the user first enables "Recreation guidance" in Settings. This is the appropriate contextual trigger — the user has just made an explicit decision to activate GPS features.

**Request flow:**
1. User taps "Recreation guidance" toggle → ON
2. Pre-rationale dialog is shown before the system permission dialog:
   *"SameView uses your location to help you find where the original photo was taken. Your location is never shared or uploaded."*
3. System permission dialog follows
4. On permanent denial: feature is silently unavailable; a brief non-modal explanation in the Settings entry communicates that location access is required

**Why lazy permission matters:**
Requesting location on first launch triggers Play Store policy scrutiny, creates user mistrust ("why does a camera app need GPS already?"), and risks permanent denial before the user understands the feature.

## Foreground-Only Requirement

`BACKGROUND_LOCATION` permission must never be declared in the manifest or requested. GPS is active exclusively while CameraScreen is visible and in the foreground.

## Graceful Degradation Without Permission

If permission is denied or not yet granted:
- No GPS indicator appears on CameraScreen
- No error or warning is shown on CameraScreen
- Session saves do not include GPS data
- The app behaves identically to GPS-disabled state

---

# 5. Data Model

## GPS in EXIF

Standard EXIF GPS tags written to applicable files:

- `GPSLatitude` + `GPSLatitudeRef`
- `GPSLongitude` + `GPSLongitudeRef`
- `GPSAltitude` + `GPSAltitudeRef` (when altitude data is available)

`GPSDateStamp`, `GPSTimeStamp`, and `GPSProcessingMethod` are defined target tags but are not yet written by the current implementation. These are deferred to Block 8 test-hardening.

## GPS per Session File

**MediaStore final image:**
GPS written via `GpsExifWriter.writeGpsToUri()` using `ExifInterface` after the JPEG is saved. This is standard camera-app behavior and what users expect from any camera capturing photos to the device gallery.

**`capture.jpg` (internal session):**
GPS EXIF written via `ExifInterface` during session save. Consistent with the MediaStore copy. Supports future re-alignment and export workflows.

**`reference.jpg` (rendered compare reference):**
No GPS written. `reference.jpg` is a derived rendering product representing frozen overlay geometry. Writing GPS here would misrepresent the file's origin and confuse external tools that read EXIF.

**`reference-original.jpg`:**
Preserves any GPS EXIF already present in the original reference image during the EXIF-oriented re-encode. No new GPS is injected. If the original had GPS EXIF, it is preserved. If it did not, none is added.

**`metadata.json`:**
Structured GPS fields added in schema version 3. Both GPS fields are independently optional. See schema below.

## metadata.json Schema v3

GPS fields are optional. Missing fields are valid state. Parsers must not fail on absent GPS data. The schema remains forward-compatible — unknown fields are ignored.

```json
{
  "version": 3,
  "referenceFile": "reference.jpg",
  "referenceOriginalFile": "reference-original.jpg",
  "captureFile": "capture.jpg",
  "overlayScale": 1.0,
  "overlayOffsetX": 0.0,
  "overlayOffsetY": 0.0,
  "referenceImageDisplayMode": "COMPARE_WITH_PREVIEW",
  "viewportWidth": 1080,
  "viewportHeight": 1920,
  "captureLocation": {
    "latitude": 48.123456,
    "longitude": 11.654321,
    "altitude": 520.0,
    "accuracyMeters": 8.5,
    "provider": "gps",
    "fixTimestampMs": 1748000000000
  },
  "referenceLocation": {
    "latitude": 48.123450,
    "longitude": 11.654320,
    "source": "exif"
  }
}
```

`captureLocation` is present when GPS was active and a fix was available at capture time.

`referenceLocation` is present when the reference image contained GPS EXIF data that was successfully extracted.

`fixTimestampMs` is the timestamp of the GPS fix itself (`Location.getTime()`), not the device capture time.

## Absence of GPS Data in Reference Images is the Expected Normal State

A significant portion of real-world reference images will have no GPS EXIF data:

- Images from WhatsApp, Instagram, and other social platforms typically have EXIF stripped entirely
- Screenshots contain no EXIF
- Scanned physical photos contain no EXIF
- Photos taken without location permission, or with location disabled, contain no GPS EXIF
- Web downloads often have EXIF removed by the hosting service

The absence of `referenceLocation` in `metadata.json` is not an error condition. No part of the app treats missing reference GPS data as a failure state.

---

# 6. GPS Snapshot at Capture Time

## Freeze Principle

At the moment the user triggers capture, the current GPS fix is frozen into an immutable `GpsSnapshot`. This is analogous to how `CaptureSessionSnapshot` freezes overlay geometry — both represent the exact state at the shutter moment.

## Why Freezing is Necessary

GPS coordinates are not static. Between the capture trigger and the completion of the async session save pipeline, the user may move, a new GPS update may arrive, or accuracy may change. The session must record the location at the moment of capture, not the location when the save completes.

## GpsSnapshot Structure

```
GpsSnapshot (optional, null when GPS is unavailable or disabled):
  latitude: Double
  longitude: Double
  altitude: Double?        // null when not available from the fix
  accuracyMeters: Float?   // null when not reported by the location provider
  provider: String?        // "gps" / "network" / "passive"; null if not set by the provider
  fixTimestampMs: Long?    // Location.getTime(); null when getTime() returns 0
```

## Position Within CaptureSessionSnapshot

`GpsSnapshot` is an optional field on `CaptureSessionSnapshot`, separate from the rendering-relevant fields (overlayScale, offsets, displayMode, viewport). The rendering pipeline ignores `GpsSnapshot` entirely. The session save pipeline reads it only for EXIF writing and `metadata.json` construction.

## Immutability After Save

Once written to `metadata.json` and EXIF, GPS data for a session is immutable. No subsequent GPS update, lifecycle event, or app restart may alter saved session GPS data.

---

# 7. Recreation Guidance UI

## Element Description

A small, minimalist chip element on CameraScreen. When active and informative, it contains:

- A small static bearing arrow (see Bearing Model below)
- Distance to the reference location
- Color accent reflecting proximity status
- A small "N" label indicating the North-up orientation model

## Bearing Model: Static Geographic Bearing Arrow

**Final decision: Static geographic bearing arrow, North-up model. No magnetometer. No device-orientation dependency.**

The arrow points in the geographic direction from the user's current position toward the reference location, with geographic North at the top of the indicator. The arrow is computed purely from the GPS bearing between two coordinate pairs. It does not respond to device rotation, tilt, or orientation changes.

### Why Not a Device-Oriented Rotating Arrow

A device-oriented rotating arrow based on `TYPE_ROTATION_VECTOR` would appear more immediately intuitive — the mental model being "hold the phone and walk toward the arrow." However this approach has fundamental reliability problems:

**Android hardware heterogeneity:** `TYPE_ROTATION_VECTOR` is a software-fused sensor built on accelerometer, magnetometer, and gyroscope. Magnetometer quality varies enormously across the Android device ecosystem. Budget and mid-range devices frequently have poor magnetometer hardware causing visible jitter and drift. A feature that works well on a Pixel device can be nearly unusable on a Samsung Galaxy A-series.

**Environmental interference:** Magnetometer is disrupted by cars, metal buildings, metal furniture, power cables, and other common outdoor shooting environments. This interference is not solvable in software.

**Trust erosion:** When an arrow visibly rotates incorrectly or jitters, the user's first reaction is "this app is broken." A stable arrow that requires a small orientation mental step preserves more trust than an unstable arrow that appears more intuitive.

**Navigation association:** A continuously rotating device-oriented arrow is visually identical to navigation apps (Maps, hiking apps). SameView should not look or feel like a navigation tool.

**No additional sensors needed:** The static geographic bearing arrow requires only GPS, which is already necessary for distance computation. No extra sensor lifecycle management, no calibration requirements, no magnetometer permission concerns.

Stability, trustworthiness, and cross-device consistency are prioritized over maximum immediate intuitiveness.

### Future Enhancement Path

A device-oriented rotating arrow using `TYPE_ROTATION_VECTOR` with appropriate exponential smoothing is a valid future enhancement. It must not be added without:
- An explicit product decision
- Testing on a representative range of real Android devices
- Defined smoothing parameters that result in visually stable behavior
- Evaluation of all affected UX states (device rotation, portrait/landscape, near-interference conditions)

## Bearing Display Suppression at Close Range

When distance falls below approximately 15–20m, the bearing arrow is suppressed. Only the distance value and Green proximity status are shown.

**Reason:** At very small distances, GPS position noise of ±3–10m causes large bearing angle changes — a 5m position error at 8m distance can shift the bearing by 30–45°. Showing an unstable bearing arrow at close range creates confusion. At close range, the relevant feedback is proximity confirmation (green status), not direction.

## Distance Display

- Distances ≥ 1000m: "X.Xkm" (one decimal place)
- Distances < 1000m: whole meters, e.g. "47m"
- No sub-meter precision under any circumstances
- Distance values are derived from the current GPS fix and are subject to GPS accuracy limitations

## Proximity Color Model

**Green** ("Close"):
`distance ≤ max(20m, 2 × accuracyMeters)`

Rationale: When GPS accuracy is ±10m, a distance reading of 15m is within measurement uncertainty. Green communicates honest proximity, not false precision. The threshold scales with accuracy to avoid misleading green when GPS is imprecise.

**Orange** ("Getting closer"):
`distance > 20m` and `distance ≤ 100m`, and `accuracyMeters ≤ 50m`

**Red** ("Far away"):
`distance > 100m`, and `accuracyMeters ≤ 100m`

**Neutral** (no color, inactive presentation):
`accuracyMeters > 100m`, or no GPS fix available, or fix is significantly stale

## UI Update Behavior and Stability Rules

The guidance chip must not update more frequently than the GPS update interval (8–10 seconds). Updates driven by GPS position noise — which can produce bearing and distance changes even when the user is stationary — must not cause visible UI oscillation.

**Minimum update thresholds:**
Small changes below the following thresholds are ignored for display purposes to prevent unnecessary UI updates:
- Distance: changes smaller than ~2–3m do not trigger a display update
- Bearing: changes smaller than ~5° do not trigger an arrow redraw

**Color status hysteresis:**
Color status transitions (e.g., Orange → Green) require at least two consecutive GPS updates confirming the new status before the color changes. This prevents flickering when the user is near a threshold boundary.

**Transition behavior:**
State transitions (neutral → informative, informative → neutral, color changes) use a short smooth fade rather than an abrupt switch. This prevents jarring visual changes and maintains the calm quality of the indicator.

**No continuous animations:**
The element is visually static between GPS updates. No pulsing, no spinning, no animated elements when waiting for a fix. The neutral state uses a static presentation.

## Chip Position

The guidance chip is positioned in the top area of CameraScreen, separate from and not occupying the Top-Left Hint Zone (which is reserved for Overlay Coverage Warning and Format Mismatch Hint per `CAMERA_WORKFLOW_UX_V1.md` section 12). Exact positioning is finalized during implementation. The chip must not overlap with or displace existing UI elements.

## Visual Calm

The chip communicates information without demanding attention. No pulsing arrows, no blinking elements, no animated countdowns, no navigation-style prompts. The visual language is informational and bystander, not directive. The chip is the quietest element on the screen.

---

# 8. GPS States

## State: Recreation Guidance is OFF

No GPS-related element appears anywhere in the app. CameraScreen is identical to the GPS-free experience. No GPS chip, no empty placeholder, no GPS icon in a disabled state.

## State: Recreation Guidance is ON — Reference Image Has No GPS Data

No GPS guidance chip appears on CameraScreen. No warning, no empty placeholder, no icon with a strikethrough. The absence of reference GPS data is treated silently.

Optional quiet detail: a "Location: Not available" text entry may appear in the Reference image info section within the Reference menu (accessible by tapping the Reference button). This is discoverable metadata detail for interested users, not a persistent screen-level notification.

## State: Recreation Guidance is ON — Reference GPS Exists — No Current Device Fix

The guidance chip is visible in a neutral/inactive presentation: GPS indicator icon only, no bearing arrow, no distance value, no proximity color. This indicates the GPS feature is active and waiting for a fix. No error message. No "GPS signal lost" text.

## State: Recreation Guidance is ON — Fix Exists — Accuracy Poor (> 80–100m)

The guidance chip remains in neutral/inactive presentation. No distance value is displayed. No accuracy number is shown to the user. The chip communicates "active but not yet precise enough" without technical language.

## State: Recreation Guidance is ON — Fix Good (≤ ~80m accuracy)

Full guidance chip: bearing arrow (suppressed when distance < 15–20m) + distance value + proximity color. This is the informative state.

## State: Fix Lost (entered building, tunnel, or signal blocked)

The chip transitions back to neutral/inactive presentation. No alert or "GPS lost" text. Silent transition using the defined fade behavior. If the user returns to GPS coverage, the chip transitions back to the informative state when a new qualifying fix arrives.

## What Is Never Shown

- "GPS weak" or "GPS signal lost" banners or persistent messages
- Accuracy values (meters) presented directly to the user
- Signal strength indicators (bars, dashes)
- Technical provider information ("Using GPS" / "Using Network")
- Aggressive error states or flashing warning indicators
- Accuracy circles or uncertainty radii

## Real-World Accuracy Expectations

Recreation Guidance is designed for outdoor use. In outdoor conditions with clear sky visibility, GPS typically provides 3–15m accuracy, which is appropriate for locating a reference shooting position.

Accuracy degrades predictably in certain environments: indoors, in dense urban canyons, near large metal structures, in tunnels, under heavy tree canopy, or inside vehicles. In these situations the chip remains in neutral/inactive presentation — reflecting the actual uncertainty of the fix rather than displaying a misleading distance value.

This behavior is by design. Degraded GPS accuracy is a property of the environment and the underlying hardware, not an app failure. The neutral state is the honest state.

---

# 9. Lifecycle and Battery Rules

## GPS Active Conditions

GPS location updates are requested only when all of the following are simultaneously true:

1. CameraScreen is currently visible and composed
2. Recreation Guidance setting is ON
3. Location permission is granted
4. The loaded reference image contains GPS EXIF data that was successfully extracted

If any condition is false, no GPS updates are requested. This avoids battery drain when the feature can provide no guidance value.

## GPS Deactivation Conditions

GPS updates stop when any of the following occurs:

- CameraScreen leaves composition (dispose/onDestroy)
- CameraScreen moves to background (onPause/onStop)
- The reference image is removed or replaced with an image that has no GPS data
- Recreation Guidance setting is switched OFF
- Location permission is revoked

## Update Parameters

- Minimum time interval between updates: 8000–10000ms
- Minimum distance threshold: 3–5m (updates only when the user has meaningfully moved)
- Initial value: `LocationManager.getLastKnownLocation()` is used as an immediate starting value before the first live update arrives

## No Background Location

`BACKGROUND_LOCATION` permission is never declared in the manifest and never requested. There is no location activity outside of an active, visible CameraScreen session.

---

# 10. Settings Architecture

## Single Setting: "Recreation guidance"

The entire GPS Recreation System is controlled by a single boolean setting. There are no sub-settings for GPS metadata saving or guidance display visibility as separate controls.

**When OFF:**
- No active GPS/location updates
- Location permission is not used
- No GPS EXIF written to new captures
- No `captureLocation` in `metadata.json`
- No guidance chip on CameraScreen
- No location-related behavior of any kind

**When ON:**
- GPS activates when all lifecycle conditions are met (see section 9)
- New captures receive GPS EXIF when a fix is available at capture time
- `metadata.json` receives `captureLocation` when a fix is available
- Guidance chip is available on CameraScreen when reference GPS exists and current fix is available

**Default:** OFF

## Why One Setting, Not Two

Two separate settings ("Save GPS" and "Show guidance") would create four logical states. Two of those states have no meaningful use case in SameView:

- Saving GPS without ever showing guidance: GPS data accumulates in files the user never benefits from in the app
- Showing guidance without saving GPS to captures: the recreation system shows guidance for the current session but the resulting photo has no location metadata for future re-use

In SameView both behaviors serve one purpose: enabling location-aware recreation. They are two expressions of one feature, not two independent features. A single toggle is clearer for users, eliminates inconsistent intermediate states, and simplifies implementation.

## Settings Placement

Settings → GPS Guidance category (Category 4 as reserved in `SETTINGS_UX_V1.md`).

## What Is Deliberately Not a Setting

- GPS accuracy threshold — too technical, hidden in color model logic
- GPS update interval — too technical, defined in implementation
- Distance unit (m vs ft) — system locale is sufficient for V1
- Bearing model selection (static vs device-oriented) — static is the V1 decision; the rotating-arrow variant is not implemented

---

# 11. Privacy Rules

- No GPS data is transmitted over any network. The app has no INTERNET permission.
- No location history is stored. Only the GPS fix at the moment of capture is persisted in that session's files.
- Session data (including `metadata.json` with GPS fields) is excluded from Android Auto Backup and device transfer via existing `backup_rules.xml` and `data_extraction_rules.xml` exclusions covering the `sessions/` directory.
- The "Recreation guidance" toggle gives the user complete control over whether the GPS chip is ever used.
- Reading GPS EXIF from a reference image is a passive file metadata read that requires no location permission.
- EXIF GPS in saved photos reflects only the capture location at the exact moment of shutter press.

---

# 12. Explicit Forbidden Behaviors

The following must never be implemented, regardless of future feature requests:

- No map view, map tile rendering, or map-adjacent visualization
- No route calculation or step-by-step navigation
- No address, neighborhood, or place-name resolution (forward or reverse geocoding)
- No real-time location tracking or location history logging
- No geofencing or proximity-based triggers beyond the guidance chip
- No background location access or `BACKGROUND_LOCATION` permission
- No automatic overlay adjustment based on GPS data
- No GPS influence on `ReferenceRenderer.render()`
- No GPS influence on rendering-relevant fields of `CaptureSessionSnapshot`
- No location data sharing or upload of any kind
- No cloud storage of location data
- No AR overlay, camera-plane orientation, or scene anchoring based on GPS
- No multi-point location management or "saved spots" system
- No "you've been here before" pattern recognition
- No Bluetooth or WiFi location scanning beyond standard Android `LocationManager`
- No modification of saved session GPS data after session save is complete

---

# 13. Future Compatibility Notes

The current architecture preserves compatibility for future work without requiring changes to the Compare rendering pipeline:

- A device-oriented rotating bearing arrow (TYPE_ROTATION_VECTOR + exponential smoothing) as a UX enhancement, requiring explicit product decision and device validation
- Re-alignment workflows using `referenceLocation` from `metadata.json` to locate the original shooting position
- Export workflows that embed `captureLocation` GPS data
- Optional local-only grouping of sessions by approximate capture location within the Compare Library
- Optional per-session accuracy display showing the distance between capture location and reference location, using locally stored metadata only

These directions are all additive to the current architecture.

---

# 14. Relationship to Other Specifications

| Document | Relationship to GPS |
|---|---|
| `COMPARE_SESSION_RENDERING_V1.md` | Defines the rendering pipeline. GPS must never enter it. `GpsSnapshot` is not a rendering input. |
| `CAMERA_WORKFLOW_UX_V1.md` | Defines CameraScreen layout and hint zones. GPS chip placement must not conflict with the Top-Left Hint Zone (section 12). |
| `SETTINGS_UX_V1.md` | Reserves Settings Category 4 for GPS Guidance. The single "Recreation guidance" toggle is defined in this document. |
| `CLAUDE_PROJECT_INSTRUCTION.md` | Defines the allowed permission set. `ACCESS_FINE_LOCATION` is added when GPS Recreation is implemented. |
| `IMPLEMENTATION_NOTES.md` | Tracks implementation state. GPS Recreation Blocks 1–7 (Reference EXIF extraction, Settings, Permission flow, LocationProvider, Guidance computation, Guidance Chip UI, Smart SAF Fallback, Capture GPS Freeze + EXIF Writing) are implemented. Block 8 (additional EXIF tags, test hardening) is open. |
