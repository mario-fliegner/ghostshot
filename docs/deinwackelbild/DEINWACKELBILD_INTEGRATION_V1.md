# DeinWackelbild Integration V1

**Status:** Product / UX specification draft for repository validation  
**Date:** 2026-08-28  
**Feature:** SameView → DeinWackelbild.de lenticular-print handoff  
**Scope:** Android V1

---

## 1. Document Status

This document captures the approved SameView product, UX, privacy, and handoff decisions for the DeinWackelbild.de integration.

It is intended to be stored at:

`docs/deinwackelbild/DEINWACKELBILD_INTEGRATION_V1.md`

Before implementation, this document must be validated against the current repository, the current authoritative SameView specifications, and the actual DeinWackelbild API/plugin behavior.

If repository analysis reveals a conflict with an existing authoritative SameView specification, the conflict must be reported explicitly. It must not be silently resolved by changing the behavior defined here.

This document does not authorize implementation by itself. A separate repository-derived implementation plan must be created and reviewed before implementation begins.

---

## 2. Purpose

SameView allows users to recreate an older photograph from the same perspective. The DeinWackelbild integration allows a user to take an existing saved Comparison and use its two aligned images to create a physical lenticular print ("Wackelbild") through DeinWackelbild.de.

SameView is responsible only for:

1. selecting the current Comparison,
2. providing an interactive local preview of the lenticular effect,
3. optionally adding a date overlay,
4. preparing the best suitable pair of print images,
5. removing metadata from the transfer images,
6. explicitly transferring the two images after user action,
7. opening the handoff URL supplied by DeinWackelbild.de.

DeinWackelbild.de is responsible for product configuration, product options, prices, cart, checkout, payment, order processing, and all subsequent commerce behavior.

SameView does not become a shop and does not track the resulting order.

---

## 3. Product Principles

### 3.1 Comparison-first

The feature starts only from an already opened saved Comparison. It is not part of the Camera workflow and is not offered proactively after capture.

### 3.2 Explicit external transfer

Opening the Wackelbild screen is entirely local and causes no network request.

Image preparation and transfer begin only after the user explicitly presses:

**"Bestelle dein Wackelbild"**

### 3.3 Minimal commerce UI inside SameView

SameView does not show product sizes, product variants, prices, shipping costs, cart state, checkout state, or order status.

### 3.4 Preview before transfer

The user can first experience a local approximation of the physical lenticular effect before deciding to transfer any image.

### 3.5 Maximum suitable print quality, unchanged composition

The print handoff should use the highest suitable real source quality available, but the visible image content must remain exactly consistent with the saved Comparison.

Higher quality must never change the visible crop, alignment, orientation, or aspect ratio represented by `reference.jpg` and `capture.jpg`.

### 3.6 Existing session files are immutable

The integration must never modify any existing Comparison file or original source file.

Any files required for transfer are newly generated temporary files.

---

## 4. Scope

V1 includes:

- entry from the existing CompareScreen Share menu,
- a dedicated Wackelbild screen,
- local tilt/swipe preview,
- optional date overlay,
- temporary high-quality print preparation,
- metadata stripping,
- DeinWackelbild API handoff,
- retry/error UX,
- Android Custom Tab handoff.

V1 explicitly does not include:

- CameraScreen entry points,
- Library entry points,
- batch ordering,
- product configuration in SameView,
- prices in SameView,
- cart or checkout in SameView,
- order history,
- order status,
- order success tracking,
- user accounts for the integration,
- persistent handoff state,
- background upload infrastructure,
- deep-link return flow,
- title overlay,
- location overlay,
- SameView branding on the print,
- analytics or telemetry.

---

## 5. Entry Point

The feature is available only from an opened regular saved Comparison.

The existing CompareScreen Share menu is extended in this exact conceptual order:

```text
Share image
Create video
────────────
Wackelbild erstellen
```

("Create video" is the actual current visible label of the existing second Export-menu action, `R.string.export_menu_create_video`; it is referenced here for conceptual placement only and is not renamed by this specification.)

Requirements:

- use the existing Share icon as the entry point;
- do not add another CompareScreen top-bar action;
- place a divider before the Wackelbild item;
- do not add a "New" badge;
- do not show a price;
- do not mention DeinWackelbild.de in the menu item;
- do not add advertising copy;
- follow the existing Share menu visual/icon pattern instead of introducing a special shopping treatment.

The item remains visible for every regular saved Comparison. It is not hidden based on:

- network connectivity,
- reference-date availability,
- original-file availability.

There is no entry from the Library, multi-select, long press, CameraScreen, or any other screen.

---

## 6. Wackelbild Screen

Selecting **"Wackelbild erstellen"** opens a dedicated full SameView destination.

It is not a dialog or bottom sheet.

The screen follows the same general navigation model as the existing Share Image / Share Video destinations:

- standard SameView top app bar,
- Back action,
- title: **"Wackelbild erstellen"**,
- responsive SameView layout behavior,
- no custom orientation lock.

Opening this screen performs no network access and no print/HQ preparation.

The screen is focused only on:

1. the Wackelbild preview,
2. the optional date toggle,
3. the interaction hint,
4. the external-transfer explanation,
5. the order CTA / current handoff state.

Do not show unrelated Comparison metadata or actions on this screen:

- no Comparison title,
- no location,
- no metadata header,
- no favorite action,
- no edit action,
- no additional Share menu,
- no Reference/Capture labels.

---

## 7. Preview Image Presentation

The preview uses the existing stored:

- `reference.jpg`
- `capture.jpg`

These are preview sources only.

The preview:

- preserves the Comparison orientation;
- preserves the exact visible image content;
- shows Portrait as Portrait;
- shows Landscape as Landscape;
- shows the complete saved Comparison image without introducing another crop;
- is centered;
- is intentionally not expanded to the maximum possible screen size;
- has comfortable surrounding space;
- has no decorative frame;
- has no simulated physical-product frame;
- has no 3D effect;
- has no SameView slider;
- has no image-state labels.

The screen must remain responsive according to the existing SameView responsive-layout rules. Device rotation must not alter the Comparison crop, image orientation, or content.

---

## 8. Lenticular Preview Interaction

### 8.1 Core behavior

The preview approximates the real lenticular viewing behavior by switching clearly between the two images.

There is:

- no fade,
- no crossfade,
- no blended transition region,
- no slider reveal,
- no partial image state,
- no transition animation,
- no haptic feedback,
- no sound.

The visible image switches directly between Reference and Capture.

### 8.2 Initial state

Every new screen visit starts with the Reference image visible.

The device orientation at the instant the screen opens must not randomly determine the initial image.

After returning from the Custom Tab to the still-existing Wackelbild screen, the preview resets to the Reference image while the other temporary screen choices defined below may remain.

### 8.3 Tilt interaction

When suitable motion-sensor hardware is available:

- a small deliberate left/right device tilt switches between the two images;
- the neutral position is relative to the device posture when the preview/sensor interaction is initialized;
- the user is not required to hold the device at a specific absolute angle;
- normal hand jitter must not cause repeated switching;
- implementation may use fixed thresholds/hysteresis solely to stabilize the direct switch;
- there must still be no visible transition region or fade;
- left/right must remain intuitive relative to the current display orientation;
- after device rotation, the neutral orientation is recalibrated appropriately.

Exact thresholds are an implementation/tuning detail and require real-device validation.

No additional Android runtime permission is required for this interaction.

### 8.4 Swipe fallback

Horizontal swiping over the preview is always available, including on devices with a suitable sensor.

Each clearly horizontal swipe toggles to the other image:

```text
Reference → swipe → Capture → swipe → Reference
```

Swipe direction itself has no semantic meaning.

The image does not track the finger. There is no drag progress.

Vertical gestures are reserved for screen scrolling and must not trigger an image switch.

### 8.5 Sensor/swipe arbitration

A manual swipe must not be immediately undone by an unchanged sensor reading.

After a swipe:

- the manually selected image remains visible;
- the existing unchanged tilt state does not immediately override it;
- sensor control resumes only after a new sufficiently clear tilt movement is detected.

This behavior requires no visible mode indicator.

### 8.6 Sensor-unavailable UX

If suitable sensor control is available, the primary hint is:

**"Handy leicht neigen"**

with supporting copy:

**"Sieh dir dein Wackelbild an."**

If suitable sensor control is unavailable, do not show a hardware/error message. Replace the interaction hint with:

**"Über das Bild wischen"**

with the same supporting copy.

### 8.7 Accessibility

Users must not be required to physically tilt the device.

The always-available swipe behavior and appropriate accessibility semantics/actions must provide an alternative way to switch the preview.

Do not add a separate visible accessibility mode or setting for this feature.

### 8.8 Sensor lifecycle and privacy

Sensor observation exists only for the local interactive preview.

- activate while the Wackelbild screen is visible/active;
- stop while the app is backgrounded;
- reactivate on return;
- fully release when leaving the screen;
- do not persist motion data;
- do not log motion data;
- do not transmit motion data.

---

## 9. Optional Date Overlay

### 9.1 Only supported print overlay

V1 provides exactly one optional print overlay:

**"Datum anzeigen"**

Default: **OFF**

There are no options for:

- title,
- location,
- description,
- SameView logo,
- `#MadeWithSameView`,
- arbitrary text.

### 9.2 Temporary state only

The date-toggle value is temporary Wackelbild-screen/order state.

It must not:

- modify session metadata,
- modify `reference.jpg`,
- modify `capture.jpg`,
- modify original files,
- create a DataStore preference,
- become a persistent per-session option.

A new visit to the Wackelbild screen starts with the toggle OFF.

If the user returns from the Custom Tab to the still-existing screen, the toggle retains its current value for that screen visit.

### 9.3 Live preview

When the toggle is enabled, the date is rendered immediately as a runtime overlay over the local preview.

- Reference preview shows the Reference date.
- Capture preview shows the Capture date.
- switching by tilt/swipe immediately shows the corresponding date.
- turning the toggle OFF immediately removes the runtime overlay.

The preview files themselves are never modified.

The preview is intended to be WYSIWYG with the eventual print rendering for position, styling, and relative scale.

### 9.4 Date availability

If no usable Reference date exists:

- **"Datum anzeigen"** is disabled;
- ordering remains fully available;
- both transfer images are created without a date overlay.

Supporting text:

**"Referenzdatum hinzufügen, um das Datum anzuzeigen."**

The Wackelbild screen provides no metadata editing.

There is:

- no date picker,
- no "add date" button,
- no metadata-editor navigation from this screen.

### 9.5 Date semantics and localization

The Reference date must preserve the actual date precision known to SameView.

Examples conceptually include:

- year only,
- year + month,
- complete date.

Unknown date components must never be invented.

The Capture date uses the actual Capture date.

Visible date formatting must follow the current SameView app locale/language. Preview rendering and final temporary print rendering must use the same localized representation.

### 9.6 Position

The date is always placed at the bottom-right of both images.

There is no position selector.

Requirements:

- same relative position on Reference and Capture;
- consistent right/bottom margin;
- dimensions and placement scale proportionally with image resolution;
- Portrait and Landscape follow the same relative rule.

### 9.7 Styling

The date overlay uses:

- white text;
- a compact background using the existing dark SameView CI color;
- rounded corners consistent with SameView design language;
- appropriate internal padding;
- no shadow;
- no outline.

The user cannot configure:

- color,
- font,
- font size,
- position,
- padding,
- corner radius.

The dark SameView CI background color (`SameViewAppSurface`, `#17202F`) and white text are existing, established SameView design tokens and must be reused as-is. No single canonical corner-radius token for a bitmap-rendered badge currently exists in the repository; the exact corner-radius value is an implementation/design detail to be chosen consistently with existing SameView visual language during the implementation-plan/design-resolution step, not invented ad hoc and not assumed to be trivially derivable from an existing component, since no existing bitmap badge of this exact form exists today. This does not change the approved appearance defined above (dark background, white text, rounded corners, no shadow, no outline) and does not introduce a user-configurable setting for it.

### 9.8 No direct manipulation

The date overlay cannot be:

- tapped for editing,
- dragged,
- repositioned,
- resized.

The ON/OFF toggle is the only date-overlay control.

### 9.9 Toggle placement

The **"Datum anzeigen"** row is placed directly below the image preview.

It is a simple SameView-style row with the toggle on the right.

Do not wrap it in a special Options card or create a separate editor section.

The interaction hint follows below the date row.

### 9.10 Ordering-state freeze

Before ordering starts, the date toggle is freely changeable.

When the user presses **"Bestelle dein Wackelbild"**, the current date-overlay choice is frozen for that handoff.

During print preparation and upload:

- the toggle is disabled;
- tilt/swipe preview remains interactive;
- the selected date state remains visible and consistent with the transfer files being prepared.

After a final error, the toggle becomes editable again.

After returning from the Custom Tab to the still-existing screen, it becomes editable again while retaining its current value.

---

## 10. External Transfer Disclosure

The screen contains this concise disclosure:

> **Deine beiden Bilder werden zur Gestaltung an DeinWackelbild.de übertragen. Die Bestellung schließt du dort ab.**

`DeinWackelbild.de` is displayed as text only.

It is not:

- a clickable link,
- accompanied by an external-link icon,
- accompanied by a partner logo,
- presented as a "Powered by" block,
- presented as an advertising card.

Do not add another warning such as:

- "You are leaving SameView",
- "An external website will open."

Do not add another confirmation dialog before the transfer.

Do not show the 24-hour handoff-retention detail on the main Wackelbild screen. That lifecycle remains part of the technical/privacy contract.

---

## 11. Primary CTA

Normal state:

**"Bestelle dein Wackelbild"**

This CTA begins the actual print preparation and external transfer.

The wording intentionally expresses the user's ordering intent while the actual product selection and legally relevant order completion remain at DeinWackelbild.de.

No price is displayed anywhere in SameView.

Do not show:

- "from €X",
- shipping costs,
- product sizes,
- product variants,
- price promises.

---

## 12. Preparation and Upload State

After the user presses **"Bestelle dein Wackelbild"**:

1. freeze the current date-overlay choice for the handoff;
2. prevent another order trigger;
3. begin local print preparation;
4. create the external handoff and upload the images according to the API contract;
5. keep the user on the same Wackelbild screen until the checkout URL is ready.

The loading presentation is intentionally simple:

**Spinner + "Wackelbild wird vorbereitet …"**

Do not show:

- percentages,
- progress bars,
- "image 1 of 2",
- API/handoff phases,
- changing technical status messages,
- a special "taking longer than expected" message.

The local Wackelbild preview remains fully interactive during preparation/upload:

- tilt remains active,
- swipe remains active,
- preview is not dimmed or frozen.

Only actions that would mutate the currently prepared order state, such as the date toggle or another order trigger, are disabled as defined above.

When the API returns a valid ready handoff with `checkout_url`, SameView automatically opens that URL in an Android Custom Tab.

There is no intermediate success screen and no separate "Open" button in the normal successful flow.

---

## 13. Back Navigation During Transfer

Before transfer starts, Back behaves normally and returns to CompareScreen.

During an active preparation/upload, Back remains available but requires confirmation.

Dialog:

**Title:** "Übertragung abbrechen?"  
**Message:** "Die Bilder werden gerade an DeinWackelbild.de übertragen."

Actions:

- **"Weiter übertragen"**
- **"Abbrechen"**

If the user aborts:

- stop the active SameView operation;
- return to CompareScreen;
- do not continue uploading in the background;
- do not later open DeinWackelbild.de automatically;
- clean up local temporary transfer files;
- already uploaded server-side temporary data may expire according to DeinWackelbild.de's retention rules.

---

## 14. Custom Tab Handoff and Return

SameView must use the exact `checkout_url` returned by the DeinWackelbild API.

SameView must not construct or modify the checkout URL itself.

The Custom Tab is the boundary into the DeinWackelbild.de product/configuration/checkout experience.

V1 has no return callback and no order-status integration.

When the user closes the Custom Tab or otherwise returns to the still-existing SameView screen:

- return to the Wackelbild screen;
- reset the visible preview image to Reference;
- restore normal interactivity;
- re-enable the date toggle;
- retain the date-toggle value for this screen visit;
- show **"Bestelle dein Wackelbild"** again.

SameView must not display or infer:

- "Order successful",
- "Thank you for your order",
- "Did you order?",
- an order checkmark,
- local purchased/ordered state.

SameView does not know whether the user:

- only viewed the configurator,
- changed options,
- abandoned,
- added to cart,
- completed checkout.

---

## 15. Repeated Ordering

After returning from DeinWackelbild.de, the user may freely press **"Bestelle dein Wackelbild"** again.

Do not warn that the Comparison was already transferred.

Do not prevent another handoff.

A later explicit user action represents a new user intent and therefore a new handoff.

Idempotency applies only to retries belonging to the same active user operation.

SameView stores no order history.

---

## 16. Local Preview vs. Print Pipeline

The feature has two intentionally separate image pipelines.

### 16.1 Preview pipeline

Uses:

- `reference.jpg`
- `capture.jpg`

Purpose:

- fast screen opening,
- lightweight tilt/swipe interaction,
- exact visual representation of the stored Comparison crop.

No HQ reconstruction is performed merely by opening the screen.

### 16.2 Print/transfer pipeline

Begins only after **"Bestelle dein Wackelbild"**.

It uses the best suitable original sources available to create the highest useful real print quality while reproducing exactly the same visible content represented by:

- `reference.jpg`
- `capture.jpg`

The original sources are quality sources only. They must not change the visual composition.

SameView's existing high-quality Share Image reconstruction logic — which independently reconstructs Reference and Capture at higher quality with exact crop/alignment parity — is a validated architectural building block for this requirement. However, the Wackelbild print/transfer pipeline has a different output requirement than that existing feature: two independent JPEG files rather than one composited share image. Implementing this handoff therefore requires new integration work; it is not achieved merely by calling the existing Share Image export unchanged.

---

## 17. Print Image Geometry Contract

The two final temporary transfer JPEGs must:

- show exactly the same visible crop/content as the stored `reference.jpg` and `capture.jpg`;
- preserve the saved SameView alignment;
- preserve orientation;
- preserve aspect ratio;
- have identical pixel dimensions to each other;
- be correctly pixel-oriented without relying on EXIF orientation;
- be valid JPEG files.

HQ reconstruction must never:

- reveal additional source-image area,
- introduce a different crop,
- recenter the image differently,
- alter alignment,
- change the Comparison's intended visible composition.

`reference.jpg` and `capture.jpg` are the visual source of truth for the output composition.

---

## 18. Maximum Suitable Print Quality

SameView automatically determines the highest suitable common print resolution that both image sources can genuinely support.

There is no user quality selector.

Rules:

- do not upscale a weaker source merely to match a stronger source;
- both final transfer images must use the same pixel dimensions;
- automatically respect the DeinWackelbild API limits;
- if necessary, reduce dimensions and/or JPEG encoding quality only as far as required to create an accepted pair;
- do not ask the user to resolve API size/dimension limits manually.

### 18.1 API limits are safety constraints, not target resolutions

The DeinWackelbild V1 constraints listed below (20 MiB, 16,000 px, 80 MP) are upper safety/API constraints, not target resolutions to aim for.

"Highest suitable common print resolution" means the highest resolution genuinely supported by the real persisted source images for this Comparison — bounded by the actual quality of the available original sources, not by how close the output can get to the API ceiling. In the large majority of real sessions, the genuinely available source resolution will be well below these limits; the limits exist to cap the rare case where source resolution would otherwise exceed them, not to define a quality goal.

SameView must not upscale source imagery merely to approach these limits.

Current DeinWackelbild V1 constraints supplied for the pilot include:

- exactly two images;
- JPEG;
- maximum 20 MiB per image;
- identical pixel dimensions/orientation/aspect ratio;
- maximum 16,000 pixels per side;
- maximum 80 megapixels;
- fully decodable real JPEG content.

The repository-derived implementation plan must verify these constraints against the final installed pilot API before implementation.

---

## 19. Original-quality Fallback

The public SameView product baseline is expected to contain the original image sources required for HQ preparation.

Legacy-session behavior is therefore not a primary V1 product requirement.

Nevertheless, a defensive fallback is required if required original sources are:

- missing,
- unreadable,
- corrupt,
- otherwise unusable for HQ reconstruction.

If HQ preparation cannot be completed from the originals but valid transfer images can still be created from the stored `reference.jpg` and `capture.jpg`, show a warning only when the user has pressed **"Bestelle dein Wackelbild"**, immediately before transfer proceeds.

Warning:

**Title:** "Originalqualität nicht verfügbar"

Message meaning:

> The original images are not fully available for this Comparison. The Wackelbild can still be created from the available Comparison images, possibly with lower print quality.

Actions:

- **"Abbrechen"**
- **"Trotzdem fortfahren"**

The exact final localized copy may be refined during localization review without changing this meaning.

Do not show this warning merely when opening the Wackelbild screen.

If the user continues:

- create new temporary transfer JPEGs using the fallback sources;
- apply the selected date overlay to those temporary transfer JPEGs if enabled;
- never modify `reference.jpg` or `capture.jpg`.

If even the fallback cannot produce a valid transfer pair, show the permanent preparation error defined below.

---

## 20. Date Rendering into Transfer Images

If **"Datum anzeigen"** is OFF:

- final temporary transfer JPEGs contain no SameView-added visible overlay.

If it is ON:

- Reference transfer JPEG contains the localized Reference date;
- Capture transfer JPEG contains the localized Capture date;
- both use the same relative bottom-right layout and style defined by the preview;
- the visible transfer rendering must match the preview as closely as practical across resolution differences.

The date is rendered only into newly created temporary transfer files.

It must never be rendered into:

- `reference.jpg`,
- `capture.jpg`,
- `reference-original...`,
- `capture-original.jpg`,
- any other persisted session source.

---

## 21. Metadata and Privacy Contract

Every JPEG sent to DeinWackelbild.de must be metadata-clean regardless of any other SameView privacy setting.

Transfer files must not contain:

- GPS metadata,
- EXIF metadata,
- camera/device metadata,
- EXIF capture timestamps,
- SameView session metadata,
- title,
- location,
- description,
- session ID,
- Comparison ID,
- source URI,
- MediaStore URI,
- any other hidden metadata not required by the image-transfer contract.

If the user explicitly enabled the visible date overlay, the date exists as rendered image pixels only.

Local source/original files remain unchanged.

No motion/sensor data is transferred.

---

## 22. Temporary Transfer Files

All prepared print/transfer JPEGs are temporary.

They must not be:

- added to the Comparison session,
- saved to MediaStore,
- shown in the Gallery,
- included in session backup,
- persisted as print artifacts.

Temporary transfer files must be deleted after:

- successful upload,
- user cancellation,
- final preparation/upload failure.

The implementation plan must define a safe cleanup strategy for process-loss/crash leftovers without turning these files into persistent session state.

---

## 23. Network Boundary

The Wackelbild screen itself is local and must work offline.

No network request is performed merely by:

- opening the screen,
- viewing the preview,
- tilting the device,
- swiping,
- toggling the date option.

Network activity begins only after **"Bestelle dein Wackelbild"** and any required quality-fallback confirmation.

Do not disable the CTA based solely on a precomputed connectivity state. The actual request determines whether communication is possible.

No network permission or network behavior introduced for this feature may silently broaden unrelated SameView functionality.

### 23.1 Network-permission governance precondition

DeinWackelbild is an explicitly user-initiated online feature. Its implementation requires the Android `INTERNET` permission.

SameView's master project instructions (`CLAUDE_PROJECT_INSTRUCTION.md`) require any online capability to be explicitly named as an approved exception before the `INTERNET` permission may be added to the manifest. DeinWackelbild V1 is recorded as such an approved exception in the DeinWackelbild network-exception addendum in `CLAUDE_PROJECT_INSTRUCTION.md`.

This exception is narrow and purpose-limited to the approved DeinWackelbild handoff/order flow described in this document. It does not authorize:

- unrelated network calls,
- telemetry,
- analytics,
- tracking,
- background networking,
- or any expansion of other SameView features' network behavior.

The `INTERNET` permission itself remains an app-level Android capability once declared — Android grants it to the whole process, not to one feature in isolation. SameView code must nonetheless use it only for explicitly approved online features (currently Hosted Comparison and DeinWackelbild V1).

---

## 24. Background and Process Lifecycle

V1 does not implement a persistent background upload system for DeinWackelbild.

Do not introduce a WorkManager or foreground-service upload solely for this feature.

A short temporary app backgrounding does not need to be artificially cancelled if the current in-memory operation naturally remains alive.

However, if screen/process state is lost:

- do not reconstruct the handoff later;
- do not resume it automatically;
- do not show a notification;
- do not later open DeinWackelbild.de automatically;
- the user may explicitly start a new handoff later.

---

## 25. Handoff State Persistence

Handoff state is ephemeral.

Do not persist:

- `handoff_id`,
- `handoff_token`,
- `upload_url`,
- `checkout_url`.

Do not add them to:

- DataStore,
- session metadata,
- local handoff files,
- backup data.

If the process is lost after a server-side handoff has been created, that temporary server-side handoff is allowed to expire under DeinWackelbild.de's retention policy.

---

## 26. API Handoff Contract

The pilot API supplied by DeinWackelbild defines the high-level sequence:

1. create a partner handoff using a stable Idempotency-Key;
2. upload the Reference JPEG to slot `one`;
3. upload the Capture JPEG to slot `two`;
4. wait for `status=ready` and a non-null `checkout_url`;
5. open the exact returned URL in an Android Custom Tab.

**Confirmed (Block 7B doc sync):** V1 requires **no separate polling/status endpoint**. The second successful upload's own response directly carries `status=ready` and the non-null `checkout_url` — "wait for" in step 4 above means reading that same response, not a follow-up request.

**Confirmed slot mapping (Block 7B doc sync):** SameView's Reference image maps to slot `one`; the Capture image maps to slot `two`. The API defines only the slot names; this assignment is SameView's own locked V1 product/technical decision.

Retries belonging to the same active user operation reuse the same Idempotency-Key as required by the API.

A later explicit order action after the previous handoff flow has ended is a new user operation and may create a new handoff.

Unknown additive response fields must be tolerated according to the API versioning contract.

The final repository-derived implementation plan must validate the exact endpoint/schema behavior against the installed pilot API before coding.

---

## 27. Data Minimization in Create Request

The optional DeinWackelbild `external_reference` field is deliberately not used in V1.

Do not transmit:

- local `sessionId`,
- `comparisonId`,
- any other SameView Comparison identifier.

The create request may contain only the data actually required by the agreed handoff, including:

- partner identifier `sameview`,
- supported locale,
- technical Idempotency-Key.

The Idempotency-Key is a technical operation identifier and must not contain personal data.

---

## 28. Partner Identification and Commerce Data

SameView may send the technical partner identifier:

`sameview`

This may be carried by DeinWackelbild.de into its internal order processing for partner attribution.

SameView V1 receives no customer/order information back.

In particular SameView must not receive through this integration:

- customer name,
- email,
- postal address,
- cart contents,
- order value,
- payment data,
- order status.

The SameView feature UI does not display:

- affiliate wording,
- commission amount,
- partner badge,
- partner logo.

Whether a commercial partner/commission relationship requires disclosure elsewhere is a compliance-review item and must be checked before release rather than guessed in implementation.

---

## 29. Locale Behavior

The Wackelbild SameView UI follows the current SameView app language.

SameView automatically maps the current app language/locale to a locale supported by the DeinWackelbild integration.

There is no manual language selector.

If DeinWackelbild.de supports the corresponding language, use it.

If it does not, V1 falls back to German / `de-DE`.

The feature remains visible in SameView regardless of the app language. Lack of a matching DeinWackelbild shop localization does not hide the feature.

The exact supported DeinWackelbild locale matrix must be verified before implementation.

---

## 30. Error UX

Technical API terminology is never shown to the user.

Do not expose:

- HTTP codes,
- handoff IDs,
- handoff tokens,
- upload slots,
- API keys,
- WordPress REST error codes.

### 30.1 Automatic retry

Retryable network/server errors may be retried automatically according to the API contract.

During automatic retry the user continues to see the normal:

**Spinner + "Wackelbild wird vorbereitet …"**

### 30.2 No internet / temporary failure

For a user-actionable temporary failure, remain on the Wackelbild screen and show concise copy such as:

**"Keine Internetverbindung"**

or:

**"Übertragung nicht möglich"**

with actions:

- **"Abbrechen"**
- **"Erneut versuchen"**

Exact error copy may vary by correctly classified user-facing cause, but must remain non-technical.

### 30.3 Permanent image/preparation failure

If SameView cannot create a valid transfer pair even after the allowed fallback:

**"Wackelbild kann nicht erstellt werden"**

Provide only the normal route back. Do not offer a pointless retry for a known non-retryable image/preparation condition.

### 30.4 Partner/integration unavailable

A partner-authentication/integration failure such as API HTTP 401 is non-retryable for that attempt.

User-facing meaning:

**"DeinWackelbild.de ist derzeit nicht verfügbar. Bitte versuche es später erneut."**

Do not expose "API key invalid".

Do not permanently disable the feature locally because of one such response.

Do not add telemetry to report it.

### 30.5 Expired/invalid handoff

Handoff-specific recovery follows the supplied API semantics internally. The user does not need to understand handoff tokens or expiration.

Where the API requires a new handoff, SameView may create a new operation as part of the appropriate retry flow.

---

## 31. Custom Tab Open Failure

If both images were successfully uploaded and a valid `checkout_url` was received, but the Custom Tab cannot be opened:

- do not upload the images again;
- keep the existing `checkout_url` for the current in-memory screen operation;
- show:

**"DeinWackelbild.de konnte nicht geöffnet werden."**

- provide:

**"DeinWackelbild.de öffnen"**

This action retries only opening the already received URL.

No extra Cancel button is required because normal Back navigation remains available.

---

## 32. Orientation Contract

SameView does not expose a Portrait/Landscape selector for this feature.

The final transfer images themselves define orientation:

- Portrait Comparison → Portrait pair;
- Landscape Comparison → Landscape pair.

Both transfer JPEGs are correctly oriented at pixel level and have identical dimensions.

DeinWackelbild.de may infer its product orientation from the first image as defined by its API/configurator contract.

---

## 33. Pricing and Product Configuration Boundary

SameView shows no:

- prices,
- "from" prices,
- shipping information,
- sizes,
- material variants,
- product options.

All such information belongs to DeinWackelbild.de.

The Wackelbild SameView screen is not a product configurator.

---

## 34. DeinWackelbild Branding Boundary

DeinWackelbild.de is named only where required for transparent external-transfer/order context.

Do not add:

- DeinWackelbild logo,
- partner card,
- "Powered by",
- advertising banner,
- promotional text.

The user's own Comparison preview remains the visual focus.

---

## 35. Security Requirements from Pilot API

The supplied pilot contract includes a SameView partner API key used only to create a handoff.

The API key:

- must never be placed in a URL;
- must never be logged;
- must never be included in analytics/crash reports;
- must never be committed to a public repository;
- must be scoped only to the limited handoff/upload integration;
- must be replaceable/rotatable.

A secret embedded in a released Android app must be treated as extractable. Security must therefore not depend on the key being permanently secret.

The implementation plan must inspect the repository/build/release setup and propose the smallest safe integration consistent with the actual project.

No implementation choice is authorized by this section yet.

---

## 36. Server-side Retention

The supplied DeinWackelbild V1 pilot contract states that incomplete handoffs and their temporary files expire and are automatically deleted after 24 hours.

This is part of the technical/privacy contract.

The 24-hour detail is not displayed on the main Wackelbild screen.

SameView does not attempt to manage or delete server-side abandoned handoffs in V1 unless the final API explicitly adds and requires such behavior.

---

## 37. API Error/Retry Expectations

The supplied pilot contract currently defines, among others:

- `400` invalid parameter/slot — do not repeat unchanged;
- `401` invalid partner key — integration failure for that attempt;
- `403` invalid handoff token — start/recover with a new handoff as appropriate;
- `409` missing/incomplete files — upload the missing slot;
- `410` expired handoff — create a new handoff with a new Idempotency-Key;
- `413` file too large — locally prepare a compliant pair/new operation;
- `415` invalid JPEG — locally create a real valid JPEG;
- `422` invalid/mismatched dimensions — locally create a valid matched pair;
- `429` temporary rate limit — retry later;
- `5xx` temporary server failure — retry with backoff.

The user-facing UI must collapse these technical conditions into the small error model defined in this document.

Create/upload retry behavior must remain idempotent.

The implementation plan must derive exact retry counts, delays, cancellation behavior, and networking details from the final API contract rather than expanding the product UX.

---

## 38. No Connectivity Gate

SameView does not perform a blocking "internet available" gate when the screen opens.

The user can always:

- open the screen,
- use the preview,
- change the date toggle when available.

Connectivity is tested by the real handoff request after the CTA.

This avoids a false or stale network-state gate and preserves the local preview.

---

## 39. No Persistent Feature Disablement

A temporary service or integration failure must not write a local flag that permanently hides/disables **"Wackelbild erstellen"**.

The Share-menu item remains a stable product entry point.

---

## 40. No Additional SameView File Mutation

The following are hard non-regression rules:

- never modify `reference.jpg`;
- never modify `capture.jpg`;
- never modify any reference original;
- never modify `capture-original.jpg`;
- never write the date overlay into persisted session files;
- never add transfer artifacts to the session;
- never change Comparison rendering metadata as part of ordering;
- never change the user's saved Comparison because of the Wackelbild flow.

All transformations happen on newly generated temporary transfer output only.

---

## 41. Relationship to Existing SameView Features

The integration must not change the behavior of:

- Camera workflow,
- Compare rendering,
- Share Image,
- Share Video,
- metadata editing,
- Favorites,
- Library filters,
- GPS Recreation,
- session backup/export,
- original-file storage/privacy,
- Hosted Comparison,
- normal local/offline workflows.

The new network behavior is limited to the explicit Wackelbild ordering action.

This network behavior is explicitly approved via the DeinWackelbild network-exception addendum in `CLAUDE_PROJECT_INSTRUCTION.md` (see §23.1).

---

## 42. Release / Privacy / Play Compliance Review

Before release of this feature, repository and product documentation must be reviewed for statements that may no longer be globally true once SameView can explicitly upload images to DeinWackelbild.de.

At minimum review:

- SameView Privacy Policy,
- Google Play Data Safety declarations,
- in-app privacy wording,
- project-level "offline/no upload/no internet" claims,
- partner/commission disclosure obligations,
- network-security implications,
- release build handling of the partner key.

Do not silently weaken existing privacy guarantees for unrelated features.

The integration must remain explicit, user-initiated, purpose-limited, and metadata-clean.

---

## 43. Accessibility Requirements

The feature must preserve SameView accessibility expectations.

At minimum:

- Back action is accessible;
- date toggle exposes correct label/state/disabled state;
- CTA and retry actions have clear semantics;
- preview has an accessible non-tilt way to switch images;
- sensor absence does not make the feature unusable;
- error messages are understandable without technical knowledge;
- responsive layouts remain usable with supported font scaling.

Exact semantics/tests should be derived from the current SameView accessibility patterns.

---

## 44. Responsive Layout Requirements

The screen must follow SameView's existing responsive-layout system rather than introducing a standalone layout architecture.

Requirements include:

- usable Portrait and Landscape device layouts;
- no forced device orientation;
- Comparison preview retains its own Portrait/Landscape orientation;
- preview remains intentionally moderate in size instead of automatically filling all available width/height;
- screen may scroll where required;
- vertical scrolling must coexist correctly with horizontal preview swipes.

The exact compact/medium/expanded behavior must be derived from the authoritative responsive-layout specification and current implementation.

---

## 45. User-visible German V1 Copy Decisions

The following German product wording has been explicitly approved in this UX decision round.

### Menu / screen

- **Wackelbild erstellen**
- **Datum anzeigen**
- **Handy leicht neigen**
- **Sieh dir dein Wackelbild an.**
- sensor fallback: **Über das Bild wischen**

### External transfer

> **Deine beiden Bilder werden zur Gestaltung an DeinWackelbild.de übertragen. Die Bestellung schließt du dort ab.**

### Primary CTA

- **Bestelle dein Wackelbild**

### Loading

- **Wackelbild wird vorbereitet …**

### Missing reference date

- **Referenzdatum hinzufügen, um das Datum anzuzeigen.**

### Cancel active transfer

- **Übertragung abbrechen?**
- **Die Bilder werden gerade an DeinWackelbild.de übertragen.**
- **Weiter übertragen**
- **Abbrechen**

### Custom Tab open failure

- **DeinWackelbild.de konnte nicht geöffnet werden.**
- **DeinWackelbild.de öffnen**

### Integration unavailable

- **DeinWackelbild.de ist derzeit nicht verfügbar. Bitte versuche es später erneut.**

### General errors

- **Keine Internetverbindung**
- **Übertragung nicht möglich**
- **Erneut versuchen**
- **Wackelbild kann nicht erstellt werden**

### Quality fallback

- **Originalqualität nicht verfügbar**
- **Trotzdem fortfahren**

The exact explanatory sentence for the quality-fallback dialog still needs localization/copy review, but its approved meaning is defined in §19.

---

## 46. English Localization Requirements

All user-facing strings require English equivalents in the normal SameView localization system.

English wording must preserve the product meaning rather than mechanically translate German terminology.

In particular, the English term for the physical product should be reviewed against the actual DeinWackelbild offering and natural user terminology (for example "lenticular print") before finalizing strings.

Date formatting must follow the current app locale, not German formatting.

The final English copy is subject to localization review; the German wording in §45 is the currently approved source wording for product intent.

---

## 47. Acceptance Criteria — UX

A V1 UX implementation is acceptable only if all of the following hold:

1. A regular saved Comparison exposes **"Wackelbild erstellen"** under the existing Share menu after Share Image and Share Video with a divider.
2. Opening the screen performs no network request.
3. Reference is initially visible.
4. Tilt switches directly between Reference and Capture without fade/animation.
5. Horizontal swipe toggles the image and works without sensor hardware.
6. Vertical scrolling does not accidentally switch images.
7. Swipe is not immediately undone by an unchanged tilt reading.
8. No sound or haptic feedback occurs during image switching.
9. Preview remains interactive during preparation/upload.
10. Date toggle defaults OFF.
11. Date toggle immediately changes the runtime preview when usable.
12. Date toggle is disabled if no Reference date exists.
13. Missing Reference date does not block ordering.
14. No metadata can be edited on this screen.
15. No title/location/branding overlay option exists.
16. No prices/product variants are shown.
17. The approved external-transfer sentence is visible before the CTA.
18. Transfer begins only after the CTA.
19. Active transfer shows a spinner and **"Wackelbild wird vorbereitet …"** without fake progress.
20. Back during transfer requires confirmation.
21. Successful preparation opens the returned checkout URL automatically.
22. Return from the Custom Tab does not imply an order result.
23. The user may initiate another handoff.
24. A Custom Tab open failure does not repeat the upload.
25. Temporary/retryable and permanent errors are presented without technical API terminology.

---

## 48. Acceptance Criteria — Image / Privacy

1. Preview uses persisted `reference.jpg` and `capture.jpg` without modifying them.
2. Print preparation begins only after explicit ordering intent.
3. Normal print preparation uses the best suitable original sources.
4. Transfer images reproduce exactly the visible crop/alignment represented by `reference.jpg` and `capture.jpg`.
5. Both transfer JPEGs have identical pixel dimensions and orientation.
6. No artificial upscaling is used to make a weaker source match a stronger source.
7. API size/dimension limits are handled automatically where feasible.
8. Every transfer JPEG is metadata-clean.
9. No GPS/EXIF/device/session identifiers are transferred in the images.
10. `external_reference` is not used.
11. Date overlay, when enabled, is rendered only into new temporary transfer images.
12. No persisted Comparison/original file is modified.
13. Transfer files are never saved to Gallery or the Comparison session.
14. Transfer files are cleaned up after success, cancellation, or final failure.
15. If HQ cannot be produced but valid fallback output can, the user is warned before transfer.
16. If neither HQ nor fallback can produce a valid pair, ordering fails safely without modifying the session.

---

## 49. Acceptance Criteria — Lifecycle / Security

1. No persistent DeinWackelbild background-upload worker/service is introduced.
2. No Handoff ID/token/URL is persisted.
3. Process loss does not later resume the handoff or unexpectedly open the shop.
4. Sensor listeners stop while the screen/app is inactive and are released when leaving.
5. Sensor data is neither logged nor transmitted.
6. Partner/API secrets are never logged or placed in URLs.
7. A partner-authentication failure does not permanently disable the feature locally.
8. Retry operations preserve API idempotency.
9. A new explicit order action after a completed/abandoned previous flow is treated as a new user operation.

---

## 50. DeinWackelbild Pilot API Baseline

The integration is based on the partner specification supplied by DeinWackelbild.de on 2026-08-18:

- production base URL: `https://deinwackelbild.de/wp-json/dwb/v1`;
- create handoff via `POST /partner-handoffs`;
- SameView partner authentication on handoff creation;
- stable `Idempotency-Key`;
- one-image-per-request multipart uploads;
- slots `one` and `two`;
- handoff token authentication for file upload;
- 24-hour temporary handoff lifetime;
- `checkout_url` returned after both images are ready;
- Android Custom Tab handoff;
- no V1 return callback/order-status API;
- server-side validation and rate limiting;
- internal partner attribution;
- temporary handoff token removed from the visible browser address by DeinWackelbild.de.

This baseline is an external integration contract, not a reason to copy server implementation details into SameView.

Before implementation, Claude/repository analysis must verify that the installed pilot endpoint still matches the supplied contract and identify any unanswered integration questions.

---

## 51. Required DeinWackelbild Pilot Acceptance Checks

The supplied partner API requests joint pilot validation for at least:

1. Landscape image pair is correctly prefilled.
2. Portrait image pair selects Portrait correctly.
3. Retry after a simulated connection interruption does not create a duplicate.
4. Over-limit files are handled correctly.
5. Invalid/non-JPEG files are rejected correctly.
6. Expired handoffs cannot be reused.
7. Test order carries internal partner identifier `sameview`.
8. Customer email and invoice expose neither partner nor handoff tokens.
9. Checkout completes without a SameView return flow.

SameView-specific pilot validation must additionally cover the UX/image/privacy acceptance criteria in this document.

---

## 52. Repository Validation Required Before Implementation

Claude must inspect the actual current repository and relevant authoritative MD specifications before an implementation plan is accepted.

At minimum validate:

- actual CompareScreen Share-menu implementation;
- Share Image / Share Video navigation pattern;
- actual responsive layout conventions;
- current design tokens / dark SameView CI color / typography / corner radii;
- session schema and actual original-file availability;
- HQ/Original-quality image rendering pipeline;
- exact crop/geometry parity feasibility for both transfer images;
- metadata stripping implementation/capabilities;
- existing temporary-file patterns and cleanup;
- existing network capability and project-level network restrictions;
- Custom Tab/browser dependencies;
- localization infrastructure;
- lifecycle/state patterns;
- accessibility patterns;
- tests covering affected areas;
- build/release secret handling;
- Play/privacy documentation impact.

Claude must report any inconsistency between this desired behavior and current authoritative SameView specifications.

Claude must not silently reinterpret this product specification to fit existing code.

---

## 53. Documentation Structure

This feature should be grouped under:

```text
docs/
└── deinwackelbild/
    ├── DEINWACKELBILD_INTEGRATION_V1.md
    └── DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md
```

`DEINWACKELBILD_INTEGRATION_V1.md` defines the approved feature/product/UX contract.

`DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` is created later, after repository validation, and describes the concrete implementation against the real codebase.

The implementation plan must not be created by guessing from this document alone.

---

## 54. Implementation-Plan Gate

After this specification is repository-validated and accepted:

1. Claude performs analysis only.
2. Claude creates the implementation plan under the same `docs/deinwackelbild/` directory.
3. The plan lists all affected/new files and exact implementation phases.
4. It identifies spec conflicts, privacy/release risks, tests, and real-device validation.
5. No production code is modified while creating the plan.
6. The plan is reviewed before any implementation prompt is issued.

---

## 55. Hard Non-Goals

Do not expand V1 into any of the following without a new explicit product decision:

- in-app DeinWackelbild product catalog,
- in-app prices,
- in-app checkout,
- order status,
- order history,
- user accounts,
- persistent partner/order IDs in sessions,
- background order processing,
- automatic post-capture upsell,
- Library upsell,
- notifications,
- analytics,
- tracking,
- additional print overlays,
- editable overlay layout,
- image-position editor,
- quality selector,
- orientation selector,
- sensor-sensitivity setting.

---

## 56. Final V1 User Flow

```text
CompareScreen
    ↓
Existing Share icon
    ↓
Share image
Create video
────────────
Wackelbild erstellen
    ↓
Dedicated Wackelbild screen
    ↓
Reference image initially visible
    ↕
Tilt device / swipe image
    ↕
Capture image
    ↓
Optional: Datum anzeigen [OFF by default]
    ↓
"Deine beiden Bilder werden zur Gestaltung an
DeinWackelbild.de übertragen. Die Bestellung
schließt du dort ab."
    ↓
Bestelle dein Wackelbild
    ↓
If HQ originals unavailable but fallback possible:
Originalqualität nicht verfügbar
Abbrechen / Trotzdem fortfahren
    ↓
Temporary HQ/fallback transfer images are created
with exact Comparison crop and optional date overlay
    ↓
All metadata removed
    ↓
Spinner: "Wackelbild wird vorbereitet …"
Preview remains interactive
    ↓
Create idempotent partner handoff
    ↓
Upload image one
    ↓
Upload image two
    ↓
Receive ready checkout_url
    ↓
Open exact URL in Android Custom Tab
    ↓
DeinWackelbild.de handles product configuration,
prices, cart, checkout, payment and order
    ↓
User returns to SameView
    ↓
Same Wackelbild screen
Reference preview visible
No order status inferred
User may order again
```

---

## 57. Open Validation Items

These are not product decisions to invent during implementation. They require repository/API/compliance validation:

1. Exact English product wording, especially the natural English equivalent of "Wackelbild".
2. Exact DeinWackelbild-supported locale matrix.
3. Exact SameView design tokens for the date overlay.
4. Exact repository mechanism for reconstructing both HQ transfer images with strict crop parity.
5. Exact common-resolution selection algorithm compatible with both source quality and API limits.
6. Exact temporary-file location and crash/process-loss cleanup mechanism.
7. Exact networking library/dependency impact.
8. Exact Custom Tab implementation/dependency impact.
9. Exact secure release-time partner-key provisioning mechanism.
10. Exact retry/backoff/timeouts consistent with the final pilot API.
11. Required SameView Privacy Policy changes.
12. Required Google Play Data Safety changes.
13. Whether the partner/commission relationship requires disclosure outside the feature UI.
14. Whether any current project-level "offline/no upload/no INTERNET" statements require authoritative documentation updates.
15. Real-device tilt threshold/hysteresis tuning.

None of these items authorizes a change to the approved UX contract without an explicit product decision.
