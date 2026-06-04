# VIDEO_EXPORT_IMPLEMENTATION_PLAN.md

**Status:** In Progress — Block 1 Completed / Block 2 Completed / Block 3+4 Implemented — Manual Verification Pending / Block 5 Completed / Block 6 Implemented — Manual Verification Pending / Block 7 Planned
**Grundlage:** VIDEO_EXPORT_V1.md (authoritative), CLAUDE_PROJECT_INSTRUCTION.md, COMPARE_FLOW_V1.md, COMPARE_SESSION_RENDERING_V1.md, SESSION_BACKUP_EXPORT_IMPLEMENTATION_PLAN.md, IMPLEMENTATION_NOTES.md, aktueller Codebestand
**Planerstellt:** 2026-06-02
**Zuletzt aktualisiert:** 2026-06-04

---

## 1. Dokumentstatus

| Eigenschaft | Wert |
|---|---|
| Status | In Progress |
| Autoritative Quelle | `VIDEO_EXPORT_V1.md` |
| Dieses Dokument | Technischer Implementierungsplan — verbindliche Arbeitsgrundlage |
| Block 1 | Completed (2026-06-02) |
| Block 2 | Completed (2026-06-02) |
| Block 3+4 | Completed (2026-06-03) |
| Block 5 | Completed (2026-06-04) |
| Blöcke 6–7 | Planned |

**Konfliktauflösung:** Bei Widerspruch zwischen diesem Dokument und `VIDEO_EXPORT_V1.md` gilt immer `VIDEO_EXPORT_V1.md`.

---

## 2. Scope

Implementierung eines benutzerinitierten, vollständig lokalen MP4-Video-Exports aus einem bestehenden Compare-Session über einen Konfigurations-Wizard.

| Bestandteil | Beschreibung |
|---|---|
| MP4-Export | Vollständiges Video ohne Audio, geschrieben via MediaStore in `Movies/SameView` |
| Compare Slider Modus | Animierter Vergleich mit Divider-Bewegung (links ↔ rechts) |
| Before & After Modus | Sequentielle Darstellung beider Bilder mit Crossfade-Übergang |
| Wizard-Screen | `CreateVideoScreen` mit drei Zuständen: Configuring → Rendering → Preview |
| MediaStore-Integration | IS_PENDING-Lifecycle; IS_PENDING=0 erst nach erfolgreichem Encoding |
| Preview | Automatisch abspielender, gemuteter Video-Player (ExoPlayer / Media3) |
| Share Sheet | Android Share Sheet via `Intent.ACTION_SEND` + MediaStore-URI |
| Delete aus Preview | Sofortiges Löschen aus MediaStore, Rückkehr zu Configuring |
| Branding Endcard | 1,0 s statische Endcard mit "SameView" und "#MadeWithSameView" |
| CompareScreen TopAppBar | Neue Struktur: `← Back | [Create Video] | [Delete Session] | ⋮` |

---

## 3. Explicit Non-Goals (V1)

Diese Features sind explizit von V1 ausgeschlossen und dürfen nicht pre-implementiert werden:

- Audiospur jeder Art (Musik, Sound Effects, leere Audiospur)
- GIF-Export
- Plattform-Picker (kein TikTok, Instagram, WhatsApp, YouTube-Button in der App)
- Exporthistorie / Session-Video-Verknüpfung
- Cloud Upload / Cloud Sync
- Video-Bearbeitung (Trim, Crop, Zoom, Pan, Ken-Burns)
- Titelkarten im Video (Session-Titel nicht im Video, FD-09)
- Blur-Background-Reformatierung (nur `#17202F`-Padding, FD-13)
- Autocrop / Reframe (FD-14)
- Analytics / Tracking
- Hintergrundexport (App muss im Vordergrund bleiben)
- Video-Export aus Compare Library (Einstiegspunkt ist ausschließlich CompareScreen)
- Video re-teilen oder -öffnen nach Schließen des Wizards (FD-10)
- Mehrfacher gleichzeitiger Export
- Frame-Rate-Optionen (24 / 60 FPS; FD-08 fixiert auf 30 FPS)
- Bitrate-Einstellungen
- Custom Aspect Ratios (außer den drei definierten)
- Animierte Endcard (Fade-In/Fade-Out auf Branding-Card)

---

## 4. Architecture Overview

### 4.1 Komponenten-Überblick

```
VideoExportPipeline
├── VideoRenderConfig        (data class; alle Render-Parameter)
├── VideoFrameRenderer       (interface; rendert einen einzelnen Frame)
│   ├── CompareSliderRenderEngine   (VideoFrameRenderer; Timing §14, Easing, Divider §16)
│   └── BeforeAfterRenderEngine     (VideoFrameRenderer; Timing §15, Crossfade)
├── BrandingEndcardRenderer  (standalone; rendert 30 statische Endcard-Frames)
├── VideoEncoder             (MediaCodec-Wrapper; Bitmap-Frames → encoded Video)
└── MediaStoreVideoWriter    (MediaStore-Insertion, IS_PENDING-Lifecycle)

CreateVideoViewModel
└── VideoExportPipeline (einziger Einstiegspunkt)

CreateVideoScreen
├── Configuring-State (Wizard-Konfiguration)
├── Rendering-State   (Fortschrittsanzeige)
└── Preview-State     (ExoPlayer + Share/Delete/Done)
```

### 4.2 Package-Struktur

| Package | Dateien |
|---|---|
| `com.isardomains.sameview.video` | Renderer, Encoder, Pipeline, Writer, Config, Interfaces |
| `com.isardomains.sameview.ui.video` | CreateVideoScreen, CreateVideoViewModel |

### 4.3 Threading-Modell

- Render- und Encoding-Operationen: `Dispatchers.Default` (CPU-bound)
- MediaStore-Schreiben: `Dispatchers.IO`
- Progress-Updates: `StateFlow<Float>` (0.0..1.0) aus `VideoExportPipeline` → `CreateVideoViewModel`
- ViewModel hält `Job?`-Referenz für Cancellation

### 4.4 Branding-Persistenz

Der Branding-Toggle-Zustand wird im bestehenden DataStore `sameview_settings` gespeichert. `SettingsRepository.kt` wird minimal um einen `brandingEnabled`-Schlüssel erweitert (Default = `true`).

---

## 5. Implementation Blocks

---

### Block 1 — Renderer Core

#### Purpose

Alle mathematischen und Bitmap-Rendering-Kernkomponenten implementieren. Keine UI, kein Encoder, keine Video-Ausgabe.

#### Scope

- `VideoRenderConfig` data class mit allen Render-Parametern
- `VideoMode`, `VideoExportFormat`, `VideoQuality` enums
- `VideoFrameRenderer` interface
- `CompareSliderRenderEngine` — vollständige Implementierung: Timing (§14), cubic ease-in-out Easing, Frame-Computation, Divider-Rendering (§16), Canvas-Regeln (§17.4)
- `BeforeAfterRenderEngine` — vollständige Implementierung: Timing (§15), Crossfade-Berechnung (linear), Fit-Scaling, Canvas-Regeln (§17.5)
- Canvas Setup (§17.1): Bitmap-Reuse vor Frame-Loop, kein `Bitmap.createBitmap()` im Frame-Loop
- Session Image Preparation (§17.2): Decode + Scale vor Frame-Loop
- Background-Fill mit `#17202F` (§17.3)
- Memory Management: `try/finally` für Bitmap-Recycle (§17.7)
- Canvas-Dimensions-Berechnung (§8.3): Even-Enforcement (§8.4)
- Unit-Tests: T-U-01 bis T-U-14

#### Expected File Changes

**Neue Produktionsdateien:**

| Datei | Inhalt |
|---|---|
| `app/src/main/java/com/isardomains/sameview/video/VideoRenderConfig.kt` | `VideoRenderConfig` data class; `VideoMode`, `VideoExportFormat`, `VideoQuality` enums |
| `app/src/main/java/com/isardomains/sameview/video/VideoFrameRenderer.kt` | Interface `VideoFrameRenderer` |
| `app/src/main/java/com/isardomains/sameview/video/CompareSliderRenderEngine.kt` | Vollständige Slider-Renderer-Implementierung |
| `app/src/main/java/com/isardomains/sameview/video/BeforeAfterRenderEngine.kt` | Vollständige Before&After-Renderer-Implementierung |

**Neue Testdateien:**

| Datei | Inhalt |
|---|---|
| `app/src/test/java/com/isardomains/sameview/video/CompareSliderRenderEngineTest.kt` | T-U-01 bis T-U-04 |
| `app/src/test/java/com/isardomains/sameview/video/BeforeAfterRenderEngineTest.kt` | T-U-05 bis T-U-08 |
| `app/src/test/java/com/isardomains/sameview/video/VideoRenderConfigTest.kt` | T-U-09 bis T-U-14 |

**Geänderte Dateien:** Keine.

#### Risks

- **Timing-Präzision (Mittel):** Prozentsatz-basierte Timing-Tabelle aus §14 muss exakt in Frame-Indices übersetzt werden. Rundungsfehler bei Frame-zu-Zeit-Berechnungen können subtile Timing-Abweichungen verursachen. Mitigierung: Unit-Tests für exakte Frame-Positionen.
- **ContentScale-Unterschied (Mittel):** CompareSlider verwendet Fill (beide Bilder decken volle Canvas ab, unabhängig), Before & After verwendet Fit (beide Bilder vollständig sichtbar mit Padding). Diese Skalierungslogik muss klar getrennt und darf nicht gemischt werden.
- **Bitmap-Memory (Mittel):** Session-Bitmaps werden vor der Frame-Loop decoded und für die gesamte Render-Dauer gehalten. Bei 4K-Sessions kann das mehrere hundert MB sein. `try/finally`-Block für Recycle ist Pflicht.
- **Divider-Line Paint Setup (Niedrig):** `Paint.setShadowLayer()` ist verboten (§16.2). Two-Stroke-Ansatz ist Pflicht. Falsches Paint-Setup führt zu Performance-Regression.

#### Required Tests

| ID | Test |
|---|---|
| T-U-01 | `CompareSliderRenderEngine.animationFrameCount` korrekt für alle 3 Presets × branding ON/OFF |
| T-U-02 | Frame 0 (Compare Slider): slider position = 0.0 |
| T-U-03 | Frame am Hold-mid-Start (Compare Slider): slider position = 1.0 |
| T-U-04 | Ende der Slide-back-Phase (Compare Slider): slider position = 0.0 |
| T-U-05 | `BeforeAfterRenderEngine.animationFrameCount` korrekt für alle 3 Presets × branding ON/OFF |
| T-U-06 | Frame 0 (Before & After): alpha_reference = 1.0, alpha_capture = 0.0 |
| T-U-07 | Crossfade-Midpoint (Before & After): alpha_reference ≈ 0.5, alpha_capture ≈ 0.5 |
| T-U-08 | Letzter Animations-Frame (Before & After): alpha_reference = 0.0, alpha_capture = 1.0 |
| T-U-09 | Branding ON: Total Frame Count = Animations-Frames + 30 |
| T-U-10 | Branding OFF: Total Frame Count = Animations-Frames |
| T-U-11 | High Quality + Original: Canvas-Dimensionen aus Session-Viewport |
| T-U-12 | Standard + Portrait 9:16: Canvas = 1080 × 1920 |
| T-U-13 | Standard + Landscape 16:9: Canvas = 1920 × 1080 |
| T-U-14 | Canvas-Breite und -Höhe sind immer gerade Zahlen |

#### Definition of Done

- Alle T-U-01 bis T-U-14 grün
- `CompareSliderRenderEngine` und `BeforeAfterRenderEngine` erzeugen korrekte Bitmap-Outputs (verifizierbar durch Frame-Sampling in Tests)
- Kein bestehender Test ist gebrochen
- Keine UI-Änderungen, kein Encoder-Code

#### Out of Scope

VideoEncoder, MediaStoreVideoWriter, VideoExportPipeline, ViewModel, UI-Screens, Navigation

---

### Block 2 — VideoEncoder + MediaStoreVideoWriter + VideoExportPipeline

#### Purpose

Die vollständige Encoding-Pipeline implementieren: von Bitmap-Frames zu einem validen, abspielbare MP4-Datei in `Movies/SameView`.

#### Scope

- `VideoEncoder` — MediaCodec-Wrapper; nimmt Bitmap-Frames bei 30 FPS; schreibt H.264-encoded Video via `MediaMuxer`; Dateiname nach §18.1 (`SameView_<sessionId>_<mode>.mp4`)
- `MediaStoreVideoWriter` — MediaStore-Insertion mit `IS_PENDING=1`; öffnet `FileDescriptor`; setzt `IS_PENDING=0` nach Erfolg; `contentResolver.delete()` bei Fehler (best-effort)
- `VideoExportPipeline` — orchestriert Renderer + Encoder + Writer; exponiert `StateFlow<Float>` (0.0..1.0) als Progress; `Job?` für Cancellation; exponiert Fallback-Signal für Qualitäts-Downgrade; `brandingEnabled` Parameter wird akzeptiert, hat aber bis Block 6 keine Endcard-Ausgabe-Wirkung
- `BrandingEndcardRenderer` — **bewusst auf Block 6 verschoben;** Datei wird nicht in Block 2 erstellt; Pipeline ignoriert `brandingEnabled` ohne Endcard-Frames bis zum Branding-Block
- Instrumentation-Test T-I-01

#### Expected File Changes

**Neue Produktionsdateien:**

| Datei | Inhalt |
|---|---|
| `app/src/main/java/com/isardomains/sameview/video/VideoEncoder.kt` | MediaCodec + MediaMuxer Wrapper, Bitmap-Frame-Input, H.264 Standard |
| `app/src/main/java/com/isardomains/sameview/video/MediaStoreVideoWriter.kt` | MediaStore-Insertion, IS_PENDING-Lifecycle, Deletion on failure |
| `app/src/main/java/com/isardomains/sameview/video/VideoExportPipeline.kt` | Pipeline-Orchestrierung, Progress StateFlow, Cancellation; `brandingEnabled` ohne Endcard-Ausgabe |

> **Hinweis:** `BrandingEndcardRenderer.kt` wurde entgegen dem ursprünglichen Plan **nicht** in Block 2 erstellt. Die Endcard-Implementierung ist bewusst auf Block 6 verschoben. `brandingEnabled` existiert als Parameter in `VideoRenderConfig`, Endcard-Frames werden jedoch noch nicht gerendert.

**Neue Testdateien:**

| Datei | Inhalt |
|---|---|
| `app/src/androidTest/java/com/isardomains/sameview/video/VideoExportPipelineTest.kt` | T-I-01: End-to-End Compare Slider Standard Original |

**Geänderte Dateien:** Keine.

#### Risks

- **MediaCodec Device Differences (Hoch):** H.264-Encoding-Implementierungen variieren stark zwischen Herstellern (Pixel-Format-Handling, Input-Mode, Encoder-Namen). Fehler können gerätespezifisch zu korrupten MP4s oder Crashes führen. Mitigierung: Frühzeitiger Test auf mehreren Geräten; defensive Fehlerbehandlung in `VideoEncoder`.
- **IS_PENDING Lifecycle (Mittel):** Korrekte Reihenfolge (INSERT → IS_PENDING=1 → FileDescriptor → encode → IS_PENDING=0) muss atomar und fehlerresistent sein. Fehler hinterlassen Phantomeinträge in MediaStore. Mitigierung: `try/finally`-Block, Cleanup bei Fehler.
- **Memory Management im Frame-Loop (Mittel):** `Bitmap.createBitmap()` im Frame-Loop verboten. Frame-Bitmap muss vor der Loop erstellt und wiederverwendet werden.
- **MediaMuxer + MediaCodec Lifetime (Niedrig):** Müssen in korrekter Reihenfolge geöffnet/geschlossen werden. Lifecycle-Fehler führen zu korrupten MP4-Dateien.

#### Required Tests

| ID | Test |
|---|---|
| T-I-01 | End-to-End: Compare Slider, 4s, Standard, Original, branding OFF → valides MP4 in `Movies/SameView` |

Zusätzlich: Manuell verifizieren, dass die erzeugte Datei mit Android Gallery und einem weiteren Player abspielbar ist.

#### Definition of Done

- T-I-01 grün auf Testgerät
- MP4-Datei ist mit Android Gallery abspielbar
- `IS_PENDING=0` nach erfolgreichem Encoding in MediaStore verifiziert
- Bei Encoding-Fehler: Eintrag aus MediaStore gelöscht (best-effort verifiziert)
- Kein bestehender Test ist gebrochen
- Keine UI-Änderungen

#### Real-Device Verifikation (2026-06-02)

Erfolgreich verifiziert auf:

| Eigenschaft | Wert |
|---|---|
| Gerät | Samsung Galaxy S23 SM-S911B |
| Android | 16 |
| T-I-01 (`VideoExportPipelineTest`) | PASSED |
| Instrumentation-Suite gesamt | 329/329 grün |
| MP4-Wiedergabe auf Gerät | verifiziert |
| Bekannte Regressionen | keine |

#### Out of Scope

HEVC / High Quality (Block 5), ViewModel, UI-Screens, Navigation, CompareScreen-Änderungen, `BrandingEndcardRenderer` (bewusst auf Block 6 verschoben; `brandingEnabled` aktuell ohne Endcard-Ausgabe)

---

### Block 3 — CreateVideoScreen + ViewModel (Configuring → Rendering) + CompareScreen Entry Point

#### Implementierungskopplung mit Block 4

> **Block 3 und Block 4 sind eine untrennbare Implementierungseinheit und müssen gemeinsam committed werden.**
>
> - Der `Create Video`-Icon im CompareScreen darf **nicht** committed werden, bevor Block 4 vollständig abgeschlossen ist (Section 26 Compliance).
> - Kein erreichbarer Entry Point ohne vollständigen Preview-State (ExoPlayer, Share, Delete).
> - Ein halbfertiger Entry Point — Tap auf `Create Video` führt zu einem Screen ohne Preview — ist ein Spec-Verstoß und erzeugt einen dauerhaft sichtbaren, nicht nutzbaren Zustand für Tester.
> - Commits dürfen erst erfolgen, wenn Block 3 **und** Block 4 vollständig implementiert sind und alle zugehörigen Tests grün sind.
>
> Dieses Risiko ist als R-04 im Risk Register dokumentiert.

#### Purpose

Vollständigen Wizard-Screen mit Configuring- und Rendering-Zustand implementieren. CompareScreen TopAppBar umstrukturieren und `Create Video`-Icon hinzufügen — erst commitable gemeinsam mit Block 4 (siehe Kopplung oben).

#### Scope

- `CreateVideoViewModel` — vollständige State Machine (`Configuring`, `Rendering`, `Preview`); Progress `StateFlow`; Error-Events; `Job?` für Cancellation; Branding-Präferenz lesen/schreiben via `SettingsRepository`
- `CreateVideoScreen` Composable:
  - Configuring-State vollständig: Mode-Auswahl, Format-Auswahl, Duration-Presets, Quality-Auswahl, Branding-Toggle, `[Create Video]` CTA
  - Rendering-State vollständig: `CircularProgressIndicator`, `LinearProgressIndicator`, "Rendering frame X of Y"
  - Preview-State: Struktur mit korrekten Buttons (Video-Player folgt in Block 4; Entry Point darf erst nach Block 4 committed werden)
- Navigation Route `createVideoRoute` in `MainActivity`
- `CompareScreen.kt` — TopAppBar umstrukturieren: `← Back | [Create Video Icon] | [Delete Session Icon] | ⋮`
- Availability Check: `Create Video`-Icon Zustand basierend auf Session-File-Existenz (§5.2)
- `strings.xml` — alle neuen i18n-Keys für Configuring- und Rendering-Zustände
- `SettingsRepository.kt` — `brandingEnabled`-Schlüssel hinzufügen (DataStore `sameview_settings`)
- Unit-Tests: T-U-15, T-U-16, T-U-17
- Instrumentation-Tests: T-I-05, T-I-06, T-I-07, T-I-08
- Alle bestehenden `CompareScreenTest`-Tests müssen grün bleiben

#### Expected File Changes

**Neue Produktionsdateien:**

| Datei | Inhalt |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoViewModel.kt` | State Machine, Pipeline-Aufruf, Progress, Error-Events, Branding-Persistenz |
| `app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoScreen.kt` | Composable: Configuring + Rendering States vollständig; Preview-Gerüst |

**Neue Testdateien:**

| Datei | Inhalt |
|---|---|
| `app/src/test/java/com/isardomains/sameview/ui/video/CreateVideoViewModelTest.kt` | T-U-15 bis T-U-17 (Block 3); T-U-18 bis T-U-20 (Blöcke 4 und 5) |

**Geänderte Dateien:**

| Datei | Art der Änderung |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt` | TopAppBar-Umstrukturierung; neuer `Create Video`-Icon; neue Parameter `onCreateVideo`, `isCreateVideoAvailable` |
| `app/src/main/java/com/isardomains/sameview/MainActivity.kt` | `createVideoRoute` registrieren; CompareScreen-Parameter durchleiten; Session-File-Existenz für `isCreateVideoAvailable` |
| `app/src/main/res/values/strings.xml` | Neue Keys: Configuring-State, Rendering-State, `create_video_entry_content_description` |
| `app/src/main/java/com/isardomains/sameview/ui/settings/SettingsRepository.kt` | Neuer DataStore-Key `brandingEnabled` (Flow<Boolean>, setBrandingEnabled()) |
| `app/src/androidTest/java/com/isardomains/sameview/ui/compare/CompareScreenTest.kt` | T-I-05 bis T-I-08 hinzufügen |

#### Risks

- **CompareScreen TopAppBar Regression (Hoch):** Die TopAppBar-Umstrukturierung berührt bestehenden Delete-Session-Icon und Overflow-Menü. Alle bestehenden `CompareScreenTest`-Tests müssen unverändert grün bleiben.
- **Section 26 Compliance (Hoch):** Entry Point ohne vollständigen Preview-State ist ein Spec-Verstoß. Block 3 und Block 4 müssen als Einheit committed werden.
- **Navigation Route Wiring (Mittel):** `createVideoRoute` muss korrekt mit `sessionId` parametriert werden. Fehler führen zu Crash oder nicht erreichbarem Screen.
- **SettingsRepository Erweiterung (Niedrig):** Additiver Parameter mit Default-Wert `true`. Kein bestehender Code bricht.

#### Required Tests

| ID | Test |
|---|---|
| T-U-15 | `CreateVideoViewModel`: Transition `Configuring` → `Rendering` bei "Create Video" Tap |
| T-U-16 | `CreateVideoViewModel`: Transition → `Preview` mit MediaStore-URI nach erfolgreichem Encoding |
| T-U-17 | `CreateVideoViewModel`: Transition → `Configuring` mit Error-Snackbar bei Encoding-Failure |
| T-I-05 | `CompareScreen` zeigt `Create Video`-Icon wenn Session gültige Dateien hat |
| T-I-06 | `CompareScreen` `Create Video`-Icon ist nicht ausführbar wenn Session-Dateien fehlen |
| T-I-07 | Tap auf `Create Video` navigiert zu `CreateVideoScreen` |
| T-I-08 | Back von `CreateVideoScreen` kehrt zu `CompareScreen` mit unverändertem Zustand zurück |

#### Definition of Done

- T-U-15, T-U-16, T-U-17 grün
- T-I-05 bis T-I-08 grün
- Alle bestehenden `CompareScreenTest`-Tests grün
- CompareScreen TopAppBar korrekt strukturiert: `← Back | [Create Video] | [Delete Session] | ⋮`
- Navigation zu `CreateVideoScreen` funktioniert
- Configuring-State vollständig funktional; Rendering-State zeigt korrekten Fortschritt
- Entry Point nur committed wenn Block 4 abgeschlossen ist

#### Out of Scope

ExoPlayer-Video-Player (Block 4), Share/Delete aus Preview (Block 4), HEVC/High Quality (Block 5), Branding-Tests (Block 6)

---

### Block 4 — Preview State + Share + Delete

#### Purpose

Vollständige Post-Render-UX implementieren: Video-Preview mit ExoPlayer, Share via Android Share Sheet, Delete aus Preview, Done/Back-Verhalten.

#### Scope

- Preview-State in `CreateVideoScreen` — Media3/ExoPlayer-basierter Video-Player (Auto-Play, Loop, muted)
- `[Share]` — `Intent.ACTION_SEND` mit MediaStore-URI und `FLAG_GRANT_READ_URI_PERMISSION`; Share Sheet öffnet nur auf expliziten Tap (FD-07)
- `[Delete]` — `contentResolver.delete()`, Rückkehr zu Configuring-State, Snackbar `create_video_delete_failed` bei Fehler (§19.3)
- `[Done]` / Back — Screen schließt, Video bleibt gespeichert (§19.4)
- `strings.xml` — verbleibende i18n-Keys: Preview-State-Labels, Error-Strings (`create_video_error_render_failed`, `create_video_quality_fallback_notice`, `create_video_delete_failed`)
- Unit-Tests: T-U-18, T-U-19
- Instrumentation-Tests: T-I-02, T-I-04

#### Expected File Changes

**Geänderte Dateien:**

| Datei | Art der Änderung |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoScreen.kt` | Preview-State vollständig: ExoPlayer-Player, Share/Delete/Done-Buttons |
| `app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoViewModel.kt` | Delete-Logik (`contentResolver.delete()`), Done/Back-Handling |
| `app/src/main/res/values/strings.xml` | Verbleibende Keys: Preview-State, alle Error-Strings |
| `app/src/androidTest/java/com/isardomains/sameview/video/VideoExportPipelineTest.kt` | T-I-02, T-I-04 hinzufügen |
| `app/src/test/java/com/isardomains/sameview/ui/video/CreateVideoViewModelTest.kt` | T-U-18, T-U-19 hinzufügen |

**Neue Produktionsdateien:** Keine.

#### Risks

- **ExoPlayer Lifecycle (Mittel):** Media3-Player muss korrekt mit dem Compose-Lifecycle verbunden werden. Rotation während Preview kann zu doppeltem Player-Binding führen. `DisposableEffect`-Pattern ist Pflicht.
- **MediaStore-URI Lifecycle (Niedrig):** Die MediaStore-URI muss über ViewModel-State Rotation überleben. Verlust der URI nach Rotation würde Preview-State invalidieren.
- **Share Sheet Cancel (Niedrig):** Abbrechen des Share Sheets ist kein Fehlerfall. App muss in Preview-State bleiben. Keine ungewollten State-Transitionen.
- **MediaStore Delete (Niedrig):** App ist Owner des Eintrags (API 29+); keine zusätzliche Permission. Fehlerfall (`delete()` gibt 0 zurück oder wirft) muss korrekt behandelt werden.

#### Required Tests

| ID | Test |
|---|---|
| T-U-18 | `CreateVideoViewModel`: Delete aus Preview → Transition zu Configuring bei Erfolg |
| T-U-19 | `CreateVideoViewModel`: Delete-Fehler → `create_video_delete_failed` Snackbar; Preview-State unverändert |
| T-I-02 | End-to-End: Before & After, 6s, Standard, Portrait 9:16, branding ON → valides MP4, Dauer ≈ 6s |
| T-I-04 | Delete aus Preview: `contentResolver.query` bestätigt Eintrag nicht mehr in MediaStore |

#### Definition of Done

- T-U-18, T-U-19 grün
- T-I-02, T-I-04 grün
- Vollständige Post-Render-UX funktional: Auto-Play, Loop, Muted
- Share Sheet öffnet sich nur bei Tap auf `[Share]` — nie automatisch (FD-07)
- Delete löscht Video aus MediaStore ohne Bestätigungs-Dialog (§19.3)
- Done / Back schließt Screen; Video bleibt gespeichert
- Kein bestehender Test ist gebrochen

#### Out of Scope

HEVC/High Quality (Block 5), Branding-Tests (Block 6)

#### Implementierungsnotizen Block 3+4 (2026-06-03)

##### Section 26 Compliance

Block 3 und Block 4 wurden als untrennbare Einheit implementiert und gemeinsam committed. Der `Create Video`-Einstieg in CompareScreen wurde erst aktiviert, als der vollständige Preview-State (ExoPlayer, Share, Delete, Done) fertig war. Kein halbfertiger Entry Point, kein Dummy-State, kein "coming soon".

##### Implementierte Komponenten

| Komponente | Beschreibung |
|---|---|
| `CreateVideoViewModel` | Vollständige State Machine (Configuring → Rendering → Preview); Progress StateFlow; Error Events; Job für Cancellation; brandingEnabled DataStore-Persistenz |
| `CreateVideoScreen` | Configuring-, Rendering- und Preview-State vollständig implementiert |
| Configuring-State | Mode-, Format-, Duration-, Quality-Auswahl; Branding-Toggle; Create Video CTA |
| Rendering-State | CircularProgressIndicator, LinearProgressIndicator, Fortschrittstext; Back öffnet Cancel Export Dialog |
| Preview-State | ExoPlayer/Media3 Auto-Play, Loop, Muted; Share / Done / Delete Video Aktionen |
| ExoPlayer/Media3 | Video-Preview mit korrektem DisposableEffect-Lifecycle |
| Share via Android Share Sheet | Intent.ACTION_SEND mit MediaStore-URI; nur bei explizitem Tap |
| Delete Video aus Preview | Löscht MP4 aus MediaStore; Confirmation Dialog vor Ausführung; Rückkehr zu Configuring bei Erfolg |
| Delete Confirmation Dialog | Explizite Bestätigung vor dem Löschen |
| Done / Back aus Preview | Screen schließt; Video bleibt gespeichert |
| Rendering Cancel Dialog | Back aus Rendering-State zeigt Bestätigungs-Dialog vor Abbruch des Exports |
| CompareScreen Entry Point | TopAppBar umstrukturiert: Back \| Create Video (Slideshow-Icon) \| Delete Session \| Overflow |
| Slideshow Icon | Create Video Icon im CompareScreen ist das Slideshow-Icon |
| `createVideoRoute` | Navigation Compose Route in MainActivity registriert |
| `isCreateVideoAvailable` | File-Existenz-Check (reference.jpg + capture.jpg) in MainActivity/Route-Schicht |
| brandingEnabled DataStore-Persistenz | Persistiert über `sameview_settings`; Default = true |
| SettingsComponents-Integration | CreateVideoScreen nutzt SettingsCard, SettingsSwitchRow, SameViewSegmentControl aus SettingsComponents.kt |

##### UX-Polish-Entscheidungen

| Entscheidung | Beschreibung |
|---|---|
| CreateVideoScreen Layout | An Settings-UX angeglichen; verwendet SettingsCard / SettingsSwitchRow / SameViewSegmentControl |
| Create Video Button | In normalen Content verschoben (nicht floating) |
| Format-Labels | Verkürzt auf: Original / Portrait / Landscape |
| Duration-Abschnitt | Label: "Duration" |
| Preview Button-Hierarchie | Share primär; Done sekundär; Delete Video als zurückgenommene destruktive Text-Aktion mit Confirmation Dialog |
| Preview TopBar | Verwendet Back-Pfeil; Back-Verhalten entspricht Done |
| Rendering Back | Zeigt Cancel Export Confirmation Dialog |
| Create Video Icon | Slideshow-Icon im CompareScreen |

##### Teststatus (2026-06-03)

| Test | Status |
|---|---|
| `testDebugUnitTest` | PASSED |
| `CompareScreenTest` | 82/82 PASSED |
| `VideoExportPipelineTest` | 2/2 PASSED |
| `assembleRelease` | BUILD SUCCESSFUL |
| `ReferenceImageMetadataReaderTest` | 2 Failures — pre-existing, nicht Block 3+4 zugehörig |
| Manueller Device-Flow | Pending — vollständige Verifikation auf realem Gerät ausstehend |

##### Offene manuelle Verifikation

Folgende Flows müssen auf einem realen Gerät verifiziert werden, bevor Block 3+4 als Completed gilt:

- [ ] Configuring-State vollständig bedienbar
- [ ] Rendering-State + Fortschrittsanzeige
- [ ] Cancel Export Dialog (Back aus Rendering)
- [ ] Preview Playback (Auto-Play, Loop, Muted)
- [ ] Share Sheet (öffnet sich; Abbrechen ist kein Fehlerfall)
- [ ] Delete Video Confirmation + Delete
- [ ] Done / Back aus Preview
- [ ] Portrait-Rendering korrekt
- [ ] Landscape-Rendering korrekt
- [ ] Gallery/Movies/SameView Sichtprüfung nach Export

---

### Block 5 — High Quality + Device Codec Limit Fallback

#### Purpose

High-Quality-Option vollständig durchverdrahten: HEVC-Verfügbarkeitsprüfung, 4K-Resolution, Silent-Fallback auf Standard 1080p bei Gerätegrenzen.

#### Scope

- High Quality-Option vollständig durch `VideoRenderConfig` → `VideoEncoder` verdrahtet
- HEVC-Encoder-Verfügbarkeitsprüfung via `MediaCodecList` und `MediaCodecInfo.VideoCapabilities`
- Resolution-Limit-Check + Silent-Fallback auf Standard 1080p (§10.3)
- `create_video_quality_fallback_notice` Snackbar bei Fallback
- 4K-Canvas-Dimensionen (§8.3) für High Quality
- Even-Dimensions-Enforcement auch bei 4K (§8.4)
- Unit-Test T-U-20
- Instrumentation-Test T-I-03

#### Expected File Changes

**Geänderte Dateien:**

| Datei | Art der Änderung |
|---|---|
| `app/src/main/java/com/isardomains/sameview/video/VideoEncoder.kt` | HEVC-Check via `MediaCodecList`; Fallback-Logik; Fallback-Signal an Pipeline |
| `app/src/main/java/com/isardomains/sameview/video/VideoExportPipeline.kt` | Fallback-Ergebnis konsumieren und an ViewModel weiterleiten |
| `app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoViewModel.kt` | `create_video_quality_fallback_notice` Snackbar emittieren |
| `app/src/test/java/com/isardomains/sameview/ui/video/CreateVideoViewModelTest.kt` | T-U-20 hinzufügen |
| `app/src/androidTest/java/com/isardomains/sameview/video/VideoExportPipelineTest.kt` | T-I-03 hinzufügen |

#### Risks

- **HEVC Availability (Hoch):** Nicht alle API 29+-Geräte unterstützen Hardware-HEVC-Encoding. Der Fallback-Pfad muss zuverlässig und ohne Crash funktionieren. Mitigierung: Test auf Low-End-Gerät.
- **4K MediaCodec Limit (Hoch):** `MediaCodecInfo.VideoCapabilities.isSizeSupported()` muss korrekt abgefragt werden. Falsche Größenprüfung führt zu `MediaCodec.CodecException` mitten im Rendering. Mitigierung: `MediaCodecList.findEncoderForFormat()` mit vollständigem `MediaFormat`.
- **Even Dimensions bei 4K (Niedrig):** Auch bei 4K-Output müssen alle Dimensionen gerade sein. High-Quality-Path muss identische Even-Rounding-Logik anwenden wie Standard-Quality.

#### Required Tests

| ID | Test |
|---|---|
| T-U-20 | `CreateVideoViewModel`: Quality-Fallback emittiert `create_video_quality_fallback_notice` Snackbar |
| T-I-03 | High Quality: Output-Video hat erwartete Resolution (innerhalb codec-reported max) |

#### Definition of Done

- T-U-20 grün
- T-I-03 grün auf Testgerät
- High Quality erzeugt HEVC-Video auf unterstützenden Geräten
- Auf Geräten ohne HEVC: Fallback auf H.264; Snackbar erscheint; kein Crash
- Bei 4K-Limit: Fallback auf 1080p; Snackbar erscheint
- Kein bestehender Test ist gebrochen

#### Out of Scope

Branding-Tests (Block 6)

#### Implementierungsnotizen Block 5 (2026-06-03)

##### Architekturentscheidung: Callback (Variante A)

`VideoExportPipeline.run()` erhält `onQualityFallback: suspend () -> Unit = {}` als 4. Parameter (Default-Wert). `VideoExportPipelineTest` T-I-01 und T-I-02 bleiben unverändert (Default deckt sie ab). `pipelineRunner` in `CreateVideoViewModel` erweitert auf 4 Parameter. Bestehende Test-Lambdas syntaktisch um `_, _` ergänzt.

##### Implementierte Komponenten

| Komponente | Beschreibung |
|---|---|
| `VideoEncoder.findHevcEncoder()` | Scannt HEVC-Encoder mit YUV420-ByteBuffer-Support (NV12 bevorzugt, I420 Fallback) |
| `VideoEncoder.isResolutionSupported()` | Prüft via `VideoCapabilities.isSizeSupported()` ob Ziel-Resolution unterstützt wird |
| `VideoEncoder.codecMimeType` | Neuer Parameter; `init`-Block nutzt `findHevcEncoder()` / `findAvcEncoder()` je nach MIME-Type |
| `VideoEncoder.start()` | Nutzt `codecMimeType` statt hartcodiertem AVC |
| `VideoExportPipeline.resolveEncoderParams()` | Entscheidet Codec (HEVC bevorzugt), Bitrate, Canvas-Dimensionen und Fallback-Flag vor MediaStore-Insert |
| `VideoExportPipeline.EncoderParams` | Privates Data-Class; kapselt width/height/mimeType/bitRate/qualityFallbackApplied |
| `VideoExportPipeline` Bitraten | `BITRATE_STANDARD_BPS = 7_000_000`; `BITRATE_HIGH_QUALITY_BPS = 20_000_000` |
| Quality-Fallback-Snackbar | `onQualityFallback()` wird nach Phase-6-Commit aufgerufen wenn Resolution gecappt; ViewModel emittiert `create_video_quality_fallback_notice` |

##### Codec-Logik (HIGH_QUALITY)

1. `findHevcEncoder()` != null → MIME = HEVC; sonst MIME = AVC (stille Fallback, kein Snackbar)
2. `isResolutionSupported(MIME, 4K)` = true → 4K-Canvas, 20 Mbps, kein Snackbar
3. `isResolutionSupported(MIME, 4K)` = false → Standard-1080p-Canvas, 7 Mbps, `onQualityFallback()` → Snackbar

STANDARD_1080P: unverändert AVC, 7 Mbps.

##### Teststatus (2026-06-04)

| Test | Status |
|---|---|
| `testDebugUnitTest` | PASSED |
| T-U-20 (`startExport_qualityFallback_emitsFallbackNoticeSnackbar`) | PASSED |
| `CreateVideoViewModelTest` gesamt | 12/12 PASSED |
| `assembleDebug` | BUILD SUCCESSFUL |
| `assembleRelease` | BUILD SUCCESSFUL |
| T-I-03 (`VideoExportPipelineTest`) | PASSED on SM-S911B (Android 16) |
| T-I-01 (`VideoExportPipelineStandardTest`) | PASSED on SM-S911B (Android 16) |
| T-I-02 (`VideoExportPipelineStandardTest`) | PASSED on SM-S911B (Android 16) |

##### Real-Device Verifikation (2026-06-04)

| Eigenschaft | Wert |
| --- | --- |
| Gerät | Samsung Galaxy S23 SM-S911B |
| Android | 16 |
| T-I-03 (`VideoExportPipelineTest`) | PASSED |
| T-I-01 (`VideoExportPipelineStandardTest`) | PASSED |
| T-I-02 (`VideoExportPipelineStandardTest`) | PASSED |
| `testDebugUnitTest` | PASSED |
| Bekannte Regressionen | keine |

##### Hinweis: Test-Klassen-Aufteilung (2026-06-04)

Aufgrund eines ART-Klassen-Ladefehlers (ClassNotFoundException beim Laden von `VideoExportPipelineTest` durch kollidierende Coroutine-Lambda-Klassen im selben DEX-Shard) wurden T-I-01 und T-I-02 in eine separate Testklasse `VideoExportPipelineStandardTest.kt` verschoben. `VideoExportPipelineTest.kt` enthält ausschließlich T-I-03. Beide Dateien befinden sich im selben Package `com.isardomains.sameview.video` und sind vollständig Black-Box-Tests ohne Referenz auf interne Implementierungsdetails.

---

### Block 6 — Branding Endcard

#### Purpose

Branding-Toggle vollständig testen und finalisieren: Endcard-Rendering, korrektes Timing-Modell (Animation = Total − 1.0s), DataStore-Persistenz.

**Hinweis:** `BrandingEndcardRenderer.kt` existiert noch nicht — die Datei wird in Block 6 neu erstellt. `brandingEnabled` ist bereits als Parameter in `VideoRenderConfig` und `VideoExportPipeline` vorhanden, hat aber bis Block 6 keine Endcard-Ausgabe-Wirkung. Block 6 umfasst Erstellung, vollständiges Testing, finale Typographie-Entscheidungen (§13.4) und Verifikation des Timing-Modells über die gesamte Pipeline.

#### Scope

- Branding-Toggle vollständig durch `VideoRenderConfig` → `VideoExportPipeline` → `BrandingEndcardRenderer` verdrahtet und verifiziert
- `BrandingEndcardRenderer` bei `brandingEnabled = true` aktiv; Timing-Modell korrekt: Animations-Dauer = Total − 1.0s
- Finale visuelle Details gemäß §13.4 abschließen: Text-Positionierung, Typography-Tokens (Material 3), ggf. App-Icon-Entscheidung
- DataStore-Persistenz: Toggle-Zustand überlebt App-Neustart; Default = `true` bei erster Verwendung
- Unit-Tests T-U-09, T-U-10
- Instrumentation-Test T-I-02 (branding ON Scenario; End-to-End-Pipeline mit Endcard)

#### Expected File Changes

**Neue Produktionsdateien:**

| Datei | Inhalt |
|---|---|
| `app/src/main/java/com/isardomains/sameview/video/BrandingEndcardRenderer.kt` | Statische Endcard-Frame-Erzeugung (30 Frames); Background `#17202F`; "SameView" + "#MadeWithSameView"; finale Typographie gemäß §13.4 |

**Geänderte Dateien:**

| Datei | Art der Änderung |
|---|---|
| `app/src/main/java/com/isardomains/sameview/video/VideoExportPipeline.kt` | `BrandingEndcardRenderer` einbinden; Branding-Timing-Korrektheit verifizieren/korrigieren falls nötig |
| `app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoScreen.kt` | Branding-Toggle-Wiring zum ViewModel verifizieren/korrigieren falls nötig |
| `app/src/test/java/com/isardomains/sameview/video/VideoRenderConfigTest.kt` | T-U-09, T-U-10 bestätigen (ggf. bereits in Block 1 vorhanden) |

#### Risks

- **Endcard Typography (Niedrig):** Die exakten Typographie-Details aus §13.4 sind bewusst nicht final im Spec. Diese müssen vor Block 6 entschieden werden. Keine Timing- oder Architektur-Auswirkung.
- **Timing-Fehler über Pipeline (Mittel):** T-U-09 und T-U-10 wurden in Block 1 unit-getestet. Der End-to-End-Test T-I-02 verifiziert, dass das Timing korrekt über die gesamte Pipeline propagiert wird.
- **DataStore Default (Niedrig):** `brandingEnabled` Default = `true` muss auch dann gelten, wenn der Key noch nie in DataStore geschrieben wurde (erster Export).

#### Required Tests

| ID | Test |
|---|---|
| T-U-09 | Branding ON: Total Frame Count = Animations-Frames + 30 |
| T-U-10 | Branding OFF: Total Frame Count = Animations-Frames |
| T-I-02 | End-to-End: Before & After, 6s, Standard, Portrait 9:16, branding ON → MP4 mit Dauer ≈ 6s |

#### Definition of Done

- T-U-09, T-U-10 grün
- T-I-02 grün (branding ON Scenario bestätigt)
- Branding-Toggle in Wizard korrekt mit DataStore verbunden
- Default = ON bei erster Verwendung
- Kein bestehender Test ist gebrochen

#### Out of Scope

Analytics, Tracking, Animierte Endcard (§28 explizit ausgeschlossen)

---

#### Implementierungsnotizen Block 6 (2026-06-04)

##### Endcard-Timing-Entscheidung

Die Endcard-Dauer wurde auf **1.5 Sekunden** (45 Frames) festgelegt, abweichend von der ursprünglichen Spec (1.0 s / 30 Frames):

| Phase | Dauer | Frames |
|---|---|---|
| Fade-in | 200 ms | 6 |
| Static | 1100 ms | 33 |
| Fade-out | 200 ms | 6 |
| **Gesamt** | **1500 ms** | **45** |

##### Implementierte Komponenten

| Komponente | Beschreibung |
|---|---|
| `BrandingEndcardRenderer` | Neue Klasse; rendert 45 statische/fade-animierte Endcard-Frames in Canvas; Logo aus `R.mipmap.ic_launcher_foreground`; Background `#0D1424`; "Made with ❤️" (kleinere Schrift) + "#MadeWithSameView" (dominante Schrift); pre-skaliertes Logo; `release()` für Bitmap-Cleanup |
| `VideoRenderConfig.BRANDING_DURATION_MS` | 1500 (geändert von implizit 1000) |
| `VideoRenderConfig.BRANDING_FRAME_COUNT` | 45 (geändert von implizit 30) |
| `VideoRenderConfig.BRANDING_FADE_IN/STATIC/FADE_OUT_FRAMES` | Neue Konstanten (6/33/6) |
| `VideoExportPipeline` | Konstruktor geändert von `ContentResolver` auf `Context`; Render-Loop getrennt in Animation-Frames + Endcard-Frames; `BrandingEndcardRenderer` in `finally`-Block recycliert |
| `CreateVideoViewModel` | `pipelineRunner` nutzt `VideoExportPipeline(context)` statt `.contentResolver`; `totalFrames = config.totalFrameCount` (inkl. Endcard-Frames für korrekten Fortschrittsbalken) |

##### Visuelle Hierarchie

```
[SameView Logo]        ← ic_launcher_foreground, 20% von min(canvasW, canvasH)
Made with ❤️           ← kleiner (3.3% baseFontSize), ❤️ systemeigene Emoji-Farbe = Rot
#MadeWithSameView      ← dominant (6.5% baseFontSize, Bold)
```

Alle Elemente vertikal und horizontal zentriert. Keine formatspezifischen Varianten.

##### Teststatus (2026-06-04)

| Test | Status |
|---|---|
| `testDebugUnitTest` (387 Tests gesamt) | PASSED |
| T-U-09 (`totalFrameCount_brandingOn_*_animationPlusFortyFive`) | PASSED (3 Tests) |
| T-U-10 (`totalFrameCount_brandingOff_*_equalsAnimationFrames`) | PASSED (3 Tests) |
| T-U-01 / T-U-05 (animationFrameCount branding ON aktualisiert) | PASSED |
| `assembleDebug` | BUILD SUCCESSFUL |
| `assembleRelease` | BUILD SUCCESSFUL |
| T-I-02 (branding ON + Dauer-Check) | Ausstehend — Geräteverifikation erforderlich |

##### Offene manuelle Verifikation (Block 6)

- [ ] Video mit brandingEnabled = true: Endcard erscheint nach dem Inhalt
- [ ] Video mit brandingEnabled = false: kein Endcard
- [ ] Fade-in (200 ms) und Fade-out (200 ms) sichtbar
- [ ] Logo sichtbar und korrekt skaliert (Portrait + Landscape + Original)
- [ ] "#MadeWithSameView" dominant; "Made with ❤️" kleiner; ❤️ = rot
- [ ] Hintergrundfarbe #0D1424 korrekt
- [ ] Gesamtdauer des Videos korrekt (Animation + 1.5 s Endcard)

---

### Block 7 — Final Verification

#### Purpose

Vollständige Feature-Verifikation, Regression-Überprüfung, Release-Smoke-Test.

#### Scope

- Vollständiger `testDebugUnitTest`-Lauf grün
- Vollständiger `connectedDebugAndroidTest`-Lauf grün
- Manueller Geräte-Smoke-Test:
  - Compare Slider, 6s, Standard, Original, branding OFF
  - Before & After, 8s, High Quality, Portrait 9:16, branding ON
  - Share-Flow (Share Sheet öffnet sich; Abbrechen ist kein Fehlerfall)
  - Delete-Flow (Video aus MediaStore gelöscht; Rückkehr zu Configuring)
  - Done-Flow (Screen schließt; Video bleibt gespeichert)
  - Back aus Preview (identisch zu Done)
  - Back aus Configuring (kehrt zu CompareScreen zurück)
  - Rotation während Rendering (ViewModel überlebt; kein Progress-Verlust)
  - Rotation in Preview (Video-Player re-attached; MediaStore-URI erhalten)
- `assembleRelease` erfolgreich; R8-Minify auf neue Klassen verifiziert
- Regression-Verifikation: alle unten genannten bestehenden Tests grün

**Berührt:** Ggf. kleinere Korrekturen aus Integrationsproblemen vorheriger Blöcke.

#### Definition of Done

- Kein roter Test
- Manueller Smoke-Test vollständig bestanden
- Release Build erfolgreich
- Keine bekannten Regressionen

---

## 6. Testing Strategy

### Unit Tests

Alle JVM-Tests unter `app/src/test/...`:

| Test-IDs | Testdatei | Bereich |
|---|---|---|
| T-U-01–T-U-04 | `video/CompareSliderRenderEngineTest.kt` | Frame Count, Slider-Position bei Key-Frames |
| T-U-05–T-U-08 | `video/BeforeAfterRenderEngineTest.kt` | Frame Count, Alpha-Werte bei Key-Frames |
| T-U-09–T-U-14 | `video/VideoRenderConfigTest.kt` | Branding Frame Count, Canvas-Dimensionen, Even-Enforcement |
| T-U-15–T-U-20 | `ui/video/CreateVideoViewModelTest.kt` | State Machine, Snackbar-Events, Fallback |

### Instrumentation Tests

Alle unter `app/src/androidTest/...`:

| Test-IDs | Testdatei | Bereich |
|---|---|---|
| T-I-01, T-I-02 | `video/VideoExportPipelineStandardTest.kt` | End-to-End MP4: Standard-Qualität (Compare Slider, Before & After) |
| T-I-03 | `video/VideoExportPipelineTest.kt` | End-to-End MP4: High Quality + Fallback-Resolution-Verifikation |
| T-I-04 | `video/VideoExportPipelineTest.kt` _(geplant)_ | Delete aus Preview: Eintrag nicht mehr in MediaStore |
| T-I-05–T-I-08 | `ui/compare/CompareScreenTest.kt` (Erweiterung) | Create Video Icon Sichtbarkeit, Navigation |

### Manual Device Tests (Block 7)

- Vollständiger Wizard-Durchlauf auf realem Gerät (SM-S911B oder gleichwertig)
- ~8 repräsentative Kombinationen aus 2 Modi × 3 Formaten × 2 Qualitäten × 2 Branding-Zuständen
- Rotation während Rendering und Preview
- Share, Delete, Done, Back in allen Varianten

### Release Verification (Block 7)

```powershell
.\gradlew.bat :app:assembleRelease
```

Release-APK auf realem Gerät installieren: Video-Export vollständig funktional; kein ProGuard-Warning auf `MediaCodec`, `MediaMuxer`, `BitmapCanvas`.

---

## 7. Regression Checklist

### CompareScreen

- Slider-Interaktion unverändert
- Fullscreen-Toggle unverändert
- Delete Session (eigener Icon-Button) unverändert
- Overflow-Menü (Edit Title, Remove Title, Backup Session) unverändert
- Back-Navigation unverändert
- Timestamp-Anzeige unverändert
- Session-Title-Anzeige unverändert

### Compare Library

- Grid-Darstellung unverändert
- Multi-Select-Modus unverändert
- Backup-Icon, Delete-Icon, Select All / Deselect All unverändert
- Navigation von Library zu CompareScreen unverändert

### Session Storage

- `SessionStorage`, `SessionScanner`, `SessionDeleter` nicht modifiziert
- Keine Auswirkung auf Session-Verzeichnis-Struktur oder Dateinamen

### MediaStore

- Bestehender `MediaStoreWriter` (für Foto-Captures in `Pictures/SameView`) unverändert
- `MediaStoreWriterGpsTest` weiterhin grün
- Neuer Video-MediaStore-Schreibpfad (`Movies/SameView`) interferiert nicht mit Foto-Schreibpfad

### Camera Workflow

- CameraScreen unverändert
- Capture-Workflow unverändert
- `CameraViewModel` nicht modifiziert (außer ggf. `isCreateVideoAvailable` in `MainActivity`)
- GPS-Snapshot-Freeze unverändert

### Navigation

- Back-Stack bei CameraScreen → CompareScreen → CreateVideoScreen → (Preview → Done) → CompareScreen korrekt
- Keine ungewollten Back-Stack-Einträge durch Video-Export

### Rotation

- ViewModel überlebt Rotation in allen drei `CreateVideoScreen`-Zuständen
- Kein Progress-Verlust während Rendering bei Rotation
- MediaStore-URI überlebt Rotation in Preview-Zustand

### Lifecycle

- Background → Foreground während Rendering: Pipeline läuft weiter oder wird korrekt abgebrochen
- App-Kill während Rendering: IS_PENDING=1-Eintrag bleibt nicht als valides Video sichtbar

### Existing Share Features

- `SessionBackupExporter` und SAF-Flow unverändert
- "Backup Session" im CompareScreen-Overflow unverändert
- Beide Share-Flows (Backup und Video) dürfen nicht interferieren

### Backup Export

- Alle `SessionBackupExporterTest` weiterhin grün
- Alle `SessionBackupExporterInstrumentedTest` weiterhin grün
- `CompareLibraryScreenTest` Backup-Tests weiterhin grün

---

## 8. Privacy / Play Store Impact

| Aspekt | Bewertung |
|---|---|
| Neue Permissions | **Keine.** App-owned MediaStore-Eintrag auf API 29+ benötigt keine `WRITE_EXTERNAL_STORAGE`. `READ_MEDIA_VIDEO` wird nicht hinzugefügt. |
| Audio | **Keine Audiospur.** MP4 enthält keinen `MediaFormat` Audio-Track (FD-05). |
| Netzwerkkommunikation | **Keine.** Vollständig offline. Share Sheet ist OS-Sache. |
| GPS in Video-Output | **Keine.** `MediaFormat.KEY_LOCATION` wird nicht gesetzt. Video-Frames tragen keine Location-Daten (§23.2). |
| Neue Data-Safety-Einträge | **Keine.** Video-Export ist user-initiated, vollständig lokal, kein GPS im Output, Sharing unter Nutzer-Kontrolle (§23.3). |
| FileProvider | **Nicht benötigt.** MediaStore-URI direkt nutzbar für `Intent.ACTION_SEND` (FD-11, §18.3). |
| Play Store Compliance | **Kein neuer Blocker.** Bestehende offene Punkte bleiben unverändert (§23.4). |
| `INTERNET` Permission | **Nicht deklariert, nicht verwendet.** |

---

## 9. Risk Register

### Hoch

| # | Risiko | Mitigierung |
|---|---|---|
| R-01 | **MediaCodec Device Differences** — H.264/HEVC-Encoding-Implementierungen variieren stark zwischen Geräteherstellern (Pixel-Format, Input-Mode, Encoder-Namen). Gerätespezifische Fehler können zu korrupten MP4s oder Crashes führen. | Frühzeitiger Test auf mehreren Geräten in Block 2; defensive Fehlerbehandlung; Capability-Checks via `MediaCodecList` |
| R-02 | **HEVC Availability** — Nicht alle API 29+-Geräte unterstützen Hardware-HEVC. Falsch implementierter Fallback kann zu Crashes bei High Quality führen. | `MediaCodecInfo.VideoCapabilities.isSizeSupported()` korrekt nutzen; Silent Fallback mit Snackbar; Test auf Low-End-Gerät |
| R-03 | **4K Resolution Cap** — Codec-Limit-Check muss vor Encoding-Start laufen. Falscher Check führt zu `MediaCodec.CodecException` mitten im Rendering. | `MediaCodecList.findEncoderForFormat()` mit vollständigem `MediaFormat`; Catch in Pipeline mit Fallback |
| R-04 | **Section 26 Compliance** — Entry Point (CompareScreen Icon) darf nicht ohne vollständigen Preview-State committed werden. Falsch geplante Block-Sequenz erzeugt halbfertigen Zustand. | Blocks 3 und 4 in derselben Session implementieren; Entry Point erst am Ende von Block 4 committen |

### Mittel

| # | Risiko | Mitigierung |
|---|---|---|
| R-05 | **Memory Usage bei High Quality** — 4K-Canvas-Bitmap + zwei skalierte Session-Bitmaps können 200+ MB belegen. OOM-Crash auf Low-RAM-Geräten möglich. | `try/finally`-Recycle-Block; Session-Bitmaps nach Render freigeben; keine Bitmap-Allokation im Frame-Loop |
| R-06 | **CompareScreen TopAppBar Regression** — Umstrukturierung des TopAppBar berührt bestehenden Code mit hoher Test-Coverage. | Alle `CompareScreenTest`-Tests als Pass-Kriterium für Block 3 definiert |
| R-07 | **ExoPlayer Lifecycle in Compose** — Media3-Player muss korrekt mit Compose-Lifecycle verbunden werden. Fehler führen zu Memory-Leak oder schwarzem Preview-Screen. | `DisposableEffect`-Pattern für Player-Lifecycle; `rememberSaveable` für VideoUri |
| R-08 | **IS_PENDING Lifecycle** — Fehlerhafter Cleanup bei Encoding-Fehler hinterlässt IS_PENDING=1-Einträge. | `try/finally`-Block mit `contentResolver.delete()` bei Fehler; Instrumentation-Test für Fehlerfall |
| R-09 | **Timing-Präzision** — Rundungsfehler in Frame-Index-Berechnung können zu sichtbar falschem Timing führen. | Unit-Tests für exakte Frame-Positionen (T-U-02–T-U-04, T-U-06–T-U-08) |

### Niedrig

| # | Risiko | Mitigierung |
|---|---|---|
| R-10 | **Divider Line Paint Setup** — `setShadowLayer()` ist verboten (§16.2). Falsche Paint-Konfiguration verlangsamt Rendering. | Explizites Two-Stroke-Drawing wie in §16.2 spezifiziert |
| R-11 | **Navigation BackStack** — Back-Stack-Tiefe bei CreateVideoScreen muss korrekt sein. | Navigation-Tests T-I-07, T-I-08; manueller Test in Block 7 |
| R-12 | **Share Sheet Cancel** — Abbrechen des Share Sheets ist kein Fehlerfall; App muss in Preview-State bleiben. | ViewModel: kein State-Change nach Share-Intent-Abbruch |
| R-13 | **Branding Default** — Default = ON gilt auch wenn `brandingEnabled`-Key noch nie in DataStore geschrieben wurde. | Unit-Test für Default-Wert; `SettingsRepository`-Implementierung mit `firstOrNull() ?: true` Pattern |
| R-14 | **ProGuard/R8 auf MediaCodec** — Release-Build kann MediaCodec/MediaMuxer-Klassen und native Methoden korrekt prozessieren. | `assembleRelease` als Teil von Block 7; Release-APK manuell testen |

---

## 10. Progress Tracking Table

| Block | Name | Status | Completion Date | Notes |
| --- | --- | --- | --- | --- |
| Block 1 | Renderer Core | **Completed** | 2026-06-02 | T-U-01–T-U-14 grün; `testDebugUnitTest` PASSED |
| Block 2 | VideoEncoder + MediaStoreVideoWriter + Pipeline | **Completed** | 2026-06-02 | T-I-01 PASSED; 329/329 Instrumentation-Tests grün; MP4-Wiedergabe auf SM-S911B (Android 16) verifiziert; `BrandingEndcardRenderer.kt` bewusst auf Block 6 verschoben |
| Block 3 | CreateVideoScreen + ViewModel + Entry Point | **Implemented — Manual Verification Pending** | 2026-06-03 | Gemeinsam mit Block 4 als gekoppelte Einheit implementiert; Section 26 Compliance erfüllt; UX-Polish abgeschlossen |
| Block 4 | Preview State + Share + Delete | **Implemented — Manual Verification Pending** | 2026-06-03 | ExoPlayer/Media3; Share Sheet; Delete mit Confirmation Dialog; Done/Back; vollständige manuelle Device-Verifikation ausstehend |
| Block 5 | High Quality + Device Limit Fallback | **Completed** | 2026-06-04 | T-U-20 grün; T-I-01/T-I-02/T-I-03 PASSED on SM-S911B (Android 16); Debug + Release BUILD SUCCESSFUL; T-I-01/T-I-02 in VideoExportPipelineStandardTest.kt nach DEX-Shard-Isolationsfix |
| Block 6 | Branding Endcard | **Implemented — Manual Verification Pending** | 2026-06-04 | Endcard 1.5 s / 45 frames; fade-in/out; logo + "Made with ❤️" + "#MadeWithSameView"; T-U-09–T-U-10 grün; T-I-02 enhanced with duration check |
| Block 7 | Final Verification | Planned | — | Full suite + Manual Smoke + Release Build |

---

## 11. Future Extensions (Not V1)

Folgende Features sind dokumentiert, aber explizit **nicht** Bestandteil von V1:

| Feature | Anmerkung |
|---|---|
| Weitere Video-Modi (Overlay Opacity, Zoom/Pan) | V2 Candidate; `VideoFrameRenderer`-Interface ist Erweiterungspunkt |
| Blur-Background-Reformatierung | V2 Candidate; §8.2: nur `#17202F`-Padding in V1 |
| Titelkarten / Session-Titel im Video | FD-09 explizit ausgeschlossen |
| Animierte Endcard (Fade-In/Fade-Out) | §28 explizit ausgeschlossen |
| GIF-Export | §28 explizit ausgeschlossen |
| Exporthistorie / Video-Neu-Teilen | FD-10 explizit ausgeschlossen |
| Ken-Burns / Pan / Zoom-Effekte | §28 explizit ausgeschlossen |
| Frame-Rate-Optionen (24 / 60 FPS) | FD-08 fixiert auf 30 FPS |
| Hintergrundexport (Foreground Service) | §28 explizit ausgeschlossen |
| Video-Export aus Compare Library | §28 explizit ausgeschlossen |
| Video-Reopen aus der App | §28 explizit ausgeschlossen |

---

## 12. Betroffene Dateien — Zusammenfassung

### Neue Produktionsdateien

| Datei | Block |
|---|---|
| `app/src/main/java/com/isardomains/sameview/video/VideoRenderConfig.kt` | 1 |
| `app/src/main/java/com/isardomains/sameview/video/VideoFrameRenderer.kt` | 1 |
| `app/src/main/java/com/isardomains/sameview/video/CompareSliderRenderEngine.kt` | 1 |
| `app/src/main/java/com/isardomains/sameview/video/BeforeAfterRenderEngine.kt` | 1 |
| `app/src/main/java/com/isardomains/sameview/video/VideoEncoder.kt` | 2 |
| `app/src/main/java/com/isardomains/sameview/video/MediaStoreVideoWriter.kt` | 2 |
| `app/src/main/java/com/isardomains/sameview/video/VideoExportPipeline.kt` | 2 |
| `app/src/main/java/com/isardomains/sameview/video/BrandingEndcardRenderer.kt` | 6 |
| `app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoViewModel.kt` | 3 |
| `app/src/main/java/com/isardomains/sameview/ui/video/CreateVideoScreen.kt` | 3+4 |

### Geänderte Produktionsdateien

| Datei | Block | Art der Änderung |
|---|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt` | 3 | TopAppBar-Umstrukturierung; Create Video Icon; neue Parameter |
| `app/src/main/java/com/isardomains/sameview/MainActivity.kt` | 3 | `createVideoRoute` registrieren; CompareScreen-Callbacks |
| `app/src/main/res/values/strings.xml` | 3+4 | ~20 neue String-Keys (vollständige Liste §24.1 in VIDEO_EXPORT_V1.md) |
| `app/src/main/java/com/isardomains/sameview/ui/settings/SettingsRepository.kt` | 3 | Neuer `brandingEnabled` DataStore-Key |

### Neue Testdateien

| Datei | Block |
|---|---|
| `app/src/test/java/com/isardomains/sameview/video/CompareSliderRenderEngineTest.kt` | 1 |
| `app/src/test/java/com/isardomains/sameview/video/BeforeAfterRenderEngineTest.kt` | 1 |
| `app/src/test/java/com/isardomains/sameview/video/VideoRenderConfigTest.kt` | 1 |
| `app/src/test/java/com/isardomains/sameview/ui/video/CreateVideoViewModelTest.kt` | 3 |
| `app/src/androidTest/java/com/isardomains/sameview/video/VideoExportPipelineTest.kt` | 2 |
| `app/src/androidTest/java/com/isardomains/sameview/video/VideoExportPipelineStandardTest.kt` | 5 |

### Erweiterte Testdateien

| Datei | Block | Neue Tests |
|---|---|---|
| `app/src/androidTest/java/com/isardomains/sameview/ui/compare/CompareScreenTest.kt` | 3 | T-I-05–T-I-08 |

### Explizit NICHT geänderte Dateien

`SessionStorage.kt`, `SessionScanner.kt`, `SessionDeleter.kt`, `SessionBackupExporter.kt`, `ReferenceRenderer.kt`, `MediaStoreWriter.kt` (Foto-Captures), `CameraScreen.kt`, `CameraViewModel.kt`, alle GPS-Klassen (`GpsExifWriter.kt`, `GpsSnapshot.kt`, `LocationProvider.kt`, `GuidanceComputer.kt`, `GpsGuidanceChip.kt`), `CompareLibraryScreen.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`.

---

## 13. Relevante Testbefehle

```powershell
# Alle Unit-Tests
.\gradlew.bat :app:testDebugUnitTest

# Video-spezifische Unit-Tests
.\gradlew.bat :app:testDebugUnitTest --tests "*.CompareSliderRenderEngineTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*.BeforeAfterRenderEngineTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*.VideoRenderConfigTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*.CreateVideoViewModelTest"

# Alle Instrumentation-Tests
.\gradlew.bat :app:connectedDebugAndroidTest

# High Quality Instrumentation-Test (T-I-03)
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.video.VideoExportPipelineTest

# Standard Pipeline Instrumentation-Tests (T-I-01, T-I-02)
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.video.VideoExportPipelineStandardTest

# CompareScreen Regression
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.compare.CompareScreenTest

# Release Build
.\gradlew.bat :app:assembleRelease
```

---

## 14. Selbstprüfung der Block-Struktur

**1. Stimmen alle Blöcke mit VIDEO_EXPORT_V1.md überein?**
Ja. Die Blöcke 1–7 sind direkt aus §29 von `VIDEO_EXPORT_V1.md` abgeleitet. Alle Features aus §6 (Modi), §7 (Wizard UX), §8 (Export Format), §9 (Duration), §10 (Quality), §13 (Branding), §14–§15 (Animation), §16 (Divider), §17 (Canvas Rendering), §18 (MediaStore), §19 (Post-Render UX) und §20 (Renderer Architecture) sind vollständig in den Blöcken abgedeckt.

**2. Fehlt ein notwendiger Implementierungsblock?**
Nein. Alle Kernkomponenten sind abgedeckt: Renderer, Encoder, Pipeline, UI (Screen + ViewModel), Entry Point, Preview, Share, Delete, High Quality, Branding-Endcard.

**3. Ist ein Block zu groß und sollte geteilt werden?**
Block 3 ist der aufwändigste Block (ViewModel + Screen + Navigation + CompareScreen-Änderung). Eine weitere Teilung würde §26 verletzen (kein Entry Point ohne vollständigen Feature-Zustand). Blocks 3 und 4 werden empfohlen, in derselben Implementation-Session abgeschlossen zu werden, bevor der Entry Point committed wird.

**4. Gibt es Blöcke, die unnötig sind?**
Nein. Jeder Block liefert einen klar abgrenzbaren, testbaren Implementierungsschritt.

**5. Entsteht irgendwo ein Dummy-Zustand oder halbfertiger Zustand?**
Nein — mit folgender dokumentierter Bedingung: Der `Create Video`-Icon im CompareScreen darf nicht committed werden, bevor Block 4 abgeschlossen ist (§26 Compliance). Dieses Risiko ist als R-04 dokumentiert und durch die Empfehlung gemindert, Blocks 3 und 4 gemeinsam zu liefern.
