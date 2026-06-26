# Reference Markers — ALIGNMENT_POINTS_V1_IMPLEMENTATION_PLAN.md

## 1. Dokumentstatus

Dieser Plan ist die **maßgebliche Implementierungsreferenz** für die Funktion „Reference Markers" in SameView.

**Dateiname:** `ALIGNMENT_POINTS_V1_IMPLEMENTATION_PLAN.md` (bleibt unverändert)
**Feature-Name (intern):** Reference Markers
**Feature-Name (nutzerseitig):** Markers

Er ergänzt `ALIGNMENT_POINTS_V1.md` (UX-Spezifikation). Bei Konflikten gewinnt die UX-Spezifikation.

**Wichtig:** Dieser Plan ist ein Analyse-Dokument. Kein Produktionscode darf auf Basis dieses Dokuments allein implementiert werden — eine separate Implementierungssession mit expliziter Nutzerfreigabe ist erforderlich.

**Revision:** 5 (2026-06-26). Eigentumsmodell als bindende Implementierungsregel eingeführt. Marker-State darf nie in Session-Dateien gespeichert werden. Ownership-Tests ergänzt. Änderungsprotokoll am Ende.

---

## 2. Funktionale Übersicht

### Kernfunktion

Der Nutzer kann im Marker-Edit-Modus bis zu 5 Marker auf das Referenzbild setzen. Marker sind in normalisierten Referenzbild-Koordinaten gespeichert und bleiben an ihrem Bildinhalt verankert — unabhängig von Overlay-Zoom, -Verschiebung und Geräterotation. Marker können sichtbar oder ausgeblendet sein, ohne gelöscht zu werden.

### Architektonische Kernentscheidung

**Marker werden in normalisierten Referenzbild-Koordinaten (0.0–1.0) gespeichert** und zur Darstellung durch die gleiche Overlay-Transformation in Screen-Koordinaten umgerechnet.

### Dreizustandsmodell

```kotlin
data class ReferenceMarkersState(
    val markers: List<ReferenceMarker> = emptyList(),
    val markersVisible: Boolean = true,
    val isEditModeActive: Boolean = false
) {
    val markersExist: Boolean get() = markers.isNotEmpty()
}
```

**Wichtige Invarianten:**
- `isEditModeActive = true` impliziert immer `markersVisible = true`
- Neue Marker werden immer in sichtbarem Kontext gesetzt (Edit-Modus setzt visible = true beim Eintritt)
- `markersVisible` bei `markersExist = false` ist irrelevant; Default = `true`

### Interaktionsmodell (unverändert seit Revision 2)

Kein Select-Zustand. Direktes Modell:

- Long-press auf freie Fläche (im Referenzbild) → Marker erstellen
- Long-press auf Marker → Marker löschen (kurzer Warnzustand)
- Drag auf Marker → Marker direkt verschieben
- One-finger-Drag auf freie Fläche → Overlay verschieben
- Two-finger → Overlay skalieren

---

### 2.1 Eigentumsmodell — Bindende Implementierungsregeln

Das Referenzbild ist der **alleinige Eigentümer** der Marker. Diese Regel ist für Implementierer bindend und bestimmt, wo Marker-State gespeichert werden darf und wo nicht.

#### Was Marker-State besitzen darf

Einzig: der **Reference-State im `CameraViewModel`** — als Teil von `CameraUiState.referenceMarkersState`.

Marker-State ist eine direkte Eigenschaft der aktuell geladenen Referenz, nicht der Session, nicht des Overlays, nicht des Kamera-Zustands allgemein.

#### Was Marker-State niemals besitzen darf

| Komponente | Grund |
|---|---|
| `SessionStorage.kt` | Marker sind kein Session-Inhalt; sie werden nicht persistiert |
| `metadata.json` | Marker-Daten dürfen das Schema nie erweitern |
| `capture.jpg` / `reference.jpg` | Marker sind UI-Only; nie gerendert in Dateien |
| `CompareScreen.kt` | CompareScreen ist kein Eigentümer; erhält keine Marker-Daten |
| `CompareLibraryScreen.kt` | Gleich wie CompareScreen |
| `VideoExportViewModel.kt` | Marker erscheinen nie in Video-Exports |
| `ShareComparisonViewModel.kt` | Marker erscheinen nie in Share-Exports |
| `ReferenceRenderer.kt` | Rendering-Pipeline ist Marker-frei |
| Overlay-Transform-State | Transform ≠ Eigentümer; Overlay-Reset löscht nie Marker |

#### Wann Marker-State zurückgesetzt wird

Genau dann, wenn der Eigentümer wechselt oder verschwindet:

- `onReferenceImageSelected()` → `clearMarkersOnReferenceChange()`
- `onReferenceImageRemoveConfirmed()` → `clearMarkersOnReferenceChange()`
- Nach „Reset overlay after capture" das die Referenz entfernt → gleich wie Remove

Der Overlay-Transform-Reset (`onOverlayReset()`) ruft `clearMarkersOnReferenceChange()` **niemals** auf.

#### Fehlerklasse, die verhindert werden muss

Das versehentliche Speichern von Marker-Koordinaten in `metadata.json` oder das Übergeben von Marker-State an `SessionStorage` wäre ein Ownership-Verstoß. Tests in §7.6 stellen sicher, dass diese Klasse von Fehlern erkannt wird.

---

## 3. Implementierungsphasen

### Phase 1: Datenmodell + Modus-Toggle + Menü

**Umfang:**

- Datenklasse `ReferenceMarker` (normalisierte Koordinaten + ID)
- State-Klasse `ReferenceMarkersState` mit drei Variablen (`markers`, `markersVisible`, `isEditModeActive`)
- `CameraUiState` erhält `referenceMarkersState: ReferenceMarkersState`
- `CameraViewModel`-Methoden für alle State-Transitionen (§4.1)
- Dynamisches Reference-Menü (5 Zustände gemäß UX-Spec §6.2)
- Viewport-Rand in SameView-Blau wenn Edit-Modus aktiv
- Aufnahme-Button deaktiviert wenn Edit-Modus aktiv
- Compare-Button bleibt aktiv (unverändert)
- „Done" / „Fertig"-Button in Top-Bar-Zone wenn Edit-Modus aktiv
- Tests für alle Menü-Zustände, State-Transitionen und Button-Verfügbarkeit

**Nicht in Phase 1:**
- Marker-Rendering
- Gesten

---

### Phase 2: Zentrale Visual-Komponente + Marker-Rendering

**Umfang:**

- `ReferenceMarkerDefaults` (Kotlin object) mit allen visuellen Parametern (§6.6 der UX-Spec)
- Composable `ReferenceMarkerOverlay` — nutzt ausschließlich `ReferenceMarkerDefaults`
- Transformation: normalisierte Koordinaten → Screen-Koordinaten via Overlay-Transform
- Marker-Rendering: Ring + Mittelpunkt (white ring, SameView-blue center, shadow)
- Marker-Clipping: Screen-Positionen außerhalb des Viewports werden nicht gerendert
- Sichtbarkeit: Marker nur gerendert wenn `markersExist = true` UND `markersVisible = true`
- Edit-Modus: keine visuelle Unterscheidung bei Marker-Darstellung (gleiche Visual in/außer Edit-Modus)
- Leerstate-Hint: sichtbar wenn `isEditModeActive = true` UND `markersExist = false`
- Rotation-Tests: Marker bleiben nach Rotation korrekt positioniert
- Display-Mode-Wechsel-Tests
- Tests: alle Marker-Parameter kommen aus `ReferenceMarkerDefaults`, kein Inline-Hardcoding

**Nicht in Phase 2:**
- Gesten (Placement, Drag, Delete)

---

### Phase 3: Marker-Placement (Long-press auf freie Fläche)

**Umfang:**

- Long-press-Erkennung auf freier Fläche (nur wenn Edit-Modus aktiv)
- Umrechnung: Touch-Position → normalisierte Referenzbild-Koordinaten
- Ablehnung wenn normalisierte Position außerhalb [0, 1] (kein Marker außerhalb des Referenzbildes)
- Marker-Erstellung (max. 5; Snackbar bei Limit)
- Leerstate-Hint verschwindet nach erstem Marker
- Haptisches Feedback beim Setzen
- Abbruch bei Touch-Slop-Überschreitung
- Tests für Placement, Limit, Außerhalb-Referenzbild-Ablehnung

---

### Phase 4: Direktes Drag und Long-press-Delete

**Umfang:**

- Direktes Drag: Finger-Down im Marker-Prioritätsradius + Bewegung → Marker verschiebt sich
- Marker-Prioritätsradius aus `ReferenceMarkerDefaults`; kleiner als 48 dp Touch-Target
- Long-press-Delete: statischer Long-press auf Marker → Warnzustand (Rot aus `ReferenceMarkerDefaults`) → Löschen bei Loslassen
- Haptisches Feedback beim Löschen
- Drag-Priorität: Finger-Down im Prioritätsradius → Marker-Interaktion; sonst → Overlay-Interaktion
- Overlay-Drag (1-Finger, freie Fläche) und Overlay-Scale (2-Finger) bleiben aktiv
- Tests für alle Interaktionspfade und Drag-Diskriminierung

---

### Phase 5: Sichtbarkeitssteuerung

**Umfang:**

- `hideMarkers()` im ViewModel: setzt `markersVisible = false`, beendet Edit-Modus falls aktiv
- `showMarkers()` im ViewModel: setzt `markersVisible = true`
- „Hide markers" / „Marker ausblenden" im Menü (nur wenn `markersExist = true`)
- „Show markers" / „Marker anzeigen" im Menü (nur wenn `markersExist = true` UND `markersVisible = false`)
- Tests für Sichtbarkeits-Lifecycle: Hide, Show, Clear, Edit-Eintritt aus Hidden-State

---

### Phase 6: Lebenszyklus-Integration + Accessibility + i18n

**Umfang:**

- State-Bereinigung bei Reference Replace / Remove: `markers = []`, `markersVisible = true`, `isEditModeActive = false`
- State-Bereinigung wenn „Reset overlay after capture" Referenz entfernt (gleich wie Remove)
- State überlebt: Rotation, Recomposition, App-Hintergrundstellung, Compare-Navigation
- State wird bei App-Neustart NICHT wiederhergestellt
- Capture im Edit-Modus unmöglich (Button deaktiviert); kein Lifecycle-Pfad dafür
- Accessibility: Content Descriptions für Marker und Edit-Modus-Bereich
- Touch-Target Mindestgröße: 48 dp (aus `ReferenceMarkerDefaults`)
- i18n: alle sichtbaren Texte als String-Ressourcen (EN + DE), keine hardcodierten Strings
- Tests für vollständige Lifecycle-Tabellen aus UX-Spec §7.2 und §7.3

---

## 4. Betroffene Dateien

### Neue Dateien

| Datei | Zweck |
|---|---|
| `ui/camera/ReferenceMarker.kt` | Datenklasse: ID, `normalizedX`, `normalizedY` |
| `ui/camera/ReferenceMarkersState.kt` | State-Klasse: `markers`, `markersVisible`, `isEditModeActive` |
| `ui/camera/ReferenceMarkerDefaults.kt` | Zentrale Visual-Parameter (§6.6 UX-Spec) |
| `ui/camera/ReferenceMarkerOverlay.kt` | Composable für Marker-Darstellung und Gesten |

### Geänderte Dateien

| Datei | Art der Änderung |
|---|---|
| `ui/camera/CameraUiState.kt` | Neues Feld `referenceMarkersState: ReferenceMarkersState` |
| `ui/camera/CameraViewModel.kt` | Neue Methoden (§4.1) |
| `ui/camera/CameraScreen.kt` | `ReferenceMarkerOverlay` einfügen; Viewport-Rand; Capture deaktiviert; Done-Button |
| `ui/camera/ReferenceMenu.kt` (o. ä.) | Menüstruktur mit 5 Zuständen (UX-Spec §6.2) |
| `res/values/strings.xml` | Neue String-Ressourcen EN (§6 dieses Plans) |
| `res/values-de/strings.xml` | Deutsche Übersetzungen (§6 dieses Plans) |

### §4.1 ViewModel-Methoden

| Methode | Wirkung |
|---|---|
| `enterMarkerEditMode()` | `isEditModeActive = true`, `markersVisible = true` |
| `exitMarkerEditMode()` | `isEditModeActive = false` (markersVisible unverändert) |
| `addMarker(normalizedX, normalizedY)` | Marker hinzufügen; ablehnen wenn limit oder außerhalb [0,1] |
| `moveMarker(id, normalizedX, normalizedY)` | Position aktualisieren |
| `removeMarker(id)` | Einzelnen Marker löschen |
| `clearMarkers()` | Alle Marker löschen; `isEditModeActive = false`; `markersVisible = true` |
| `hideMarkers()` | `markersVisible = false`; `isEditModeActive = false` |
| `showMarkers()` | `markersVisible = true` |
| `clearMarkersOnReferenceChange()` | Intern: bei Replace/Remove/Reset-after-capture |

**Nicht enthalten** (kein Select-State):
- `selectMarker()` → nicht benötigt
- `clearMarkerSelection()` → nicht benötigt

### Nicht berührte Dateien

Die folgenden Dateien dürfen durch diese Implementierung nicht verändert werden. Der Grund ist in jedem Fall das Eigentumsmodell (§2.1): Marker-State gehört dem Reference-State im ViewModel, nicht diesen Komponenten.

| Datei | Eigentumsregel |
|---|---|
| `ReferenceRenderer.kt` | Rendering-Pipeline ist Marker-frei; Marker nie gerendert |
| `SessionStorage.kt` | Marker sind kein Session-Inhalt; nie persistiert |
| `SessionScanner.kt` | Liest Session-Dateien; Marker sind dort nicht vorhanden |
| `SessionDeleter.kt` | Löscht Session-Dateien; Marker-State ist unabhängig |
| `CompareScreen.kt` | CompareScreen ist kein Eigentümer; erhält keine Marker-Daten |
| `CompareLibraryScreen.kt` | Gleich wie CompareScreen |
| `VideoExportViewModel.kt` | Marker erscheinen nie in Video-Exports |
| `ShareComparisonViewModel.kt` | Marker erscheinen nie in Share-Exports |
| `metadata.json`-Schema | Marker dürfen das Schema nie erweitern |

---

## 5. `ReferenceMarkerDefaults` — Spezifikation

```kotlin
object ReferenceMarkerDefaults {
    // Visual
    val ringDiameterDp = 20.dp
    val strokeWidthDp = 2.dp
    val centerDotDiameterDp = 4.dp
    val ringColor = Color.White
    val centerDotColor = SameViewBlue   // Compare Accent Color
    val warningColor = Color.Red
    val shadowRadius = 4.dp            // weak shadow for bright backgrounds

    // Interaction
    val touchTargetDp = 48.dp          // Accessibility minimum
    val dragPriorityRadiusDp = 15.dp   // Finger-Down innerhalb → Marker-Drag hat Vorrang
                                        // (kleiner als touchTargetDp)

    // Limits
    const val MAX_MARKERS = 5
}
```

**Regel:** Jede Datei, die Marker zeichnet, interagiert oder testet, muss diese Werte aus `ReferenceMarkerDefaults` beziehen. Kein Inline-Hardcoding von Größen, Farben oder Radien.

---

## 6. String-Ressourcen — Pflichtliste

### EN (`res/values/strings.xml`)

| Key | Text (EN) | Verwendung |
|---|---|---|
| `markers_menu_entry` | „Markers…" | Menü: keine Marker vorhanden |
| `markers_menu_edit` | „Edit markers…" | Menü: Marker vorhanden, Edit-Modus nicht aktiv |
| `markers_menu_hide` | „Hide markers" | Menü: Marker sichtbar |
| `markers_menu_show` | „Show markers" | Menü: Marker vorhanden, ausgeblendet |
| `markers_menu_clear` | „Clear markers" | Menü: Marker vorhanden |
| `markers_hint_place` | „Long press to place a marker" | Leerstate-Hint im Edit-Modus |
| `markers_limit_reached` | „Marker limit reached" | Snackbar: max. 5 erreicht |
| `markers_done` | „Done" | Top-Bar-Button zum Edit-Modus-Verlassen |
| `markers_a11y_marker` | „Marker. Drag to reposition, long press to remove." | Content Description je Marker |
| `markers_a11y_overlay_area` | „Long press to place a marker" | Overlay-Bereich Accessibility Hint im Edit-Modus |
| `markers_a11y_edit_mode_indicator` | „Marker edit mode active" | Content Description Viewport-Rand |

### DE (`res/values-de/strings.xml`)

| Key | Text (DE) |
|---|---|
| `markers_menu_entry` | „Marker…" |
| `markers_menu_edit` | „Marker bearbeiten…" |
| `markers_menu_hide` | „Marker ausblenden" |
| `markers_menu_show` | „Marker anzeigen" |
| `markers_menu_clear` | „Marker löschen" |
| `markers_hint_place` | „Gedrückt halten, um Marker zu setzen" |
| `markers_limit_reached` | „Marker-Limit erreicht" |
| `markers_done` | „Fertig" |
| `markers_a11y_marker` | „Marker. Ziehen zum Verschieben, gedrückt halten zum Entfernen." |
| `markers_a11y_overlay_area` | „Gedrückt halten, um Marker zu setzen" |
| `markers_a11y_edit_mode_indicator` | „Marker-Bearbeitungsmodus aktiv" |

**Pflichtregeln:**
- Keine User-facing Strings außerhalb der String-Ressourcen
- Die Begriffe „Ausrichtungspunkte", „Alignment Points", „Anchor Points", „Reference Points" dürfen in keiner nutzer-sichtbaren Ressource vorkommen
- Beide Sprachen müssen vollständig vorhanden sein bevor Release

---

## 7. Teststrategie

### 7.1 Unit-Tests (CameraViewModel)

**State-Transitionen:**

| Test | Bedingung |
|---|---|
| `enterMarkerEditMode()` | `isEditModeActive = true`, `markersVisible = true` (auch wenn vorher false) |
| `exitMarkerEditMode()` | `isEditModeActive = false`, `markersVisible` unverändert |
| `addMarker()` | Marker zur Liste hinzugefügt |
| Marker-Limit | `addMarker()` lehnt 6. Marker ab |
| Außerhalb Referenzbild | `addMarker()` lehnt ab wenn normalisierte Koordinate außerhalb [0,1] |
| `removeMarker(id)` | Spezifischer Marker entfernt |
| `clearMarkers()` | Alle Marker gelöscht, `isEditModeActive = false`, `markersVisible = true` |
| `hideMarkers()` | `markersVisible = false`, `isEditModeActive = false` |
| `hideMarkers()` aus Edit-Modus | Edit-Modus endet; Marker bleiben |
| `showMarkers()` | `markersVisible = true`, `isEditModeActive` unverändert |
| `showMarkers()` ohne Marker | Keine Fehler; `markersVisible = true` |
| `clearMarkersOnReferenceChange()` | Alle drei States auf Default zurückgesetzt |

**Lifecycle-Tests:**

| Test | Bedingung |
|---|---|
| Marker bei Referenz-Remove | `markers = []`, `markersVisible = true`, `isEditModeActive = false` |
| Marker bei Referenz-Replace | Gleich wie Remove |
| Marker bei Reset-after-capture | Gleich wie Remove |
| Capture deaktiviert im Edit-Modus | Capture-Action abgelehnt |
| Capture außerhalb Edit-Modus | Normal; State unverändert |
| Compare nicht deaktiviert im Edit-Modus | Compare-Aktion erlaubt |
| Edit-Modus überlebt Compare-Navigation | `isEditModeActive` nach Navigate und Rückkehr erhalten |
| Alle States überleben Rotation | State nach Rotation unverändert |
| Marker überleben Overlay-Reset | Marker vorhanden; Positionen recalc |
| Edit-Modus bleibt bei Overlay-Reset | `isEditModeActive` unverändert |
| Marker überleben Capture | Nach Aufnahme: Marker unverändert |
| Marker nicht wiederhergestellt bei App-Neustart | Initialzustand nach Neustart |

### 7.2 Unit-Tests (Koordinatentransformation)

| Test | Bedingung |
|---|---|
| Normalisierung | Touch → normalisierte Koordinate korrekt |
| Denormalisierung | Normalisiert → Screen-Position korrekt |
| Invarianz bei Overlay-Scale | Normalisierte Koordinaten konstant bei Scale-Änderung |
| Invarianz bei Overlay-Offset | Normalisierte Koordinaten konstant bei Offset-Änderung |
| Außerhalb-Ablehnung | Touch außerhalb Referenzbild → kein Marker, keine Exception |
| Rotation-Invarianz | Normalisiert korrekt nach Viewport-Größenänderung |
| Display-Mode-Wechsel | Screen-Position korrekt nach COMPARE_WITH_PREVIEW ↔ SHOW_FULL_IMAGE |
| Overlay-Reset | Screen-Position korrekt nach Transform-Reset |

### 7.3 Unit-Tests (ReferenceMarkerDefaults)

| Test | Bedingung |
|---|---|
| Keine Inline-Hardcoding | Grep-Test: kein hardcodierter Marker-Radius/Farbe außerhalb ReferenceMarkerDefaults |
| `dragPriorityRadiusDp` < `touchTargetDp` | Prioritätsradius kleiner als Accessibility-Target |
| `MAX_MARKERS == 5` | Konstante korrekt |

### 7.4 UI / Instrumentation Tests

**String-Ressourcen und Terminologie:**

| Test | Bedingung |
|---|---|
| Kein „Alignment Points" in EN-Strings | Grep auf `strings.xml`: kein Vorkommen |
| Kein „Ausrichtungspunkte" in DE-Strings | Grep auf `strings-de/strings.xml`: kein Vorkommen |
| Alle Marker-Keys in EN vorhanden | Vollständige Schlüsselliste aus §6 |
| Alle Marker-Keys in DE vorhanden | Vollständige Schlüsselliste aus §6 |

**Menüstruktur (5 Zustände):**

| Test | Bedingung |
|---|---|
| Kein Marker, kein Edit-Modus | Nur „Markers…" als Marker-Eintrag |
| Marker vorhanden, sichtbar, kein Edit-Modus | „Edit markers…", „Hide markers", „Clear markers" |
| Marker vorhanden, ausgeblendet, kein Edit-Modus | „Edit markers…", „Show markers", „Clear markers" |
| Edit-Modus aktiv, Marker vorhanden | „Hide markers", „Clear markers"; kein „Edit markers…" |
| Edit-Modus aktiv, keine Marker | Kein Marker-Abschnitt im Menü |
| Kein Marker-Menü ohne Referenz | Kein Marker-Eintrag wenn keine Referenz geladen |

**Edit-Modus:**

| Test | Bedingung |
|---|---|
| Edit-Modus-Einstieg | Nach „Markers…": Viewport-Rand sichtbar, Done-Button sichtbar |
| Leerstate-Hint sichtbar | Hint sichtbar wenn Edit-Modus aktiv und keine Marker |
| Leerstate-Hint weg nach Marker | Hint verschwindet nach erstem Marker |
| Leerstate-Hint erneut bei Clear | Hint erscheint wieder wenn alle Marker gelöscht |
| Done beendet Edit-Modus | Viewport-Rand verschwindet; Marker bleiben sichtbar |
| Back beendet Edit-Modus | Wie Done |
| Aufnahme blockiert | Capture-Button deaktiviert im Edit-Modus |
| Aufnahme aktiv nach Done | Capture-Button nach Done wieder aktiv |
| Compare aktiv | Compare-Button ansteuerbar im Edit-Modus |
| Edit-Modus nach Compare und zurück | Modus noch aktiv nach Compare-Navigation |

**Sichtbarkeit:**

| Test | Bedingung |
|---|---|
| Marker nach Done sichtbar | Marker sichtbar nach Verlassen des Edit-Modus via Done |
| Hide markers: nicht sichtbar | Nach „Hide markers": Marker nicht gerendert |
| Hide markers: noch vorhanden | Nach „Hide markers": `markersExist = true` |
| Show markers: wieder sichtbar | Nach „Show markers": Marker gerendert |
| Edit-Modus aus Hidden: Marker sichtbar | „Edit markers…" von hidden State → Marker erscheinen |
| Done nach Edit aus Hidden: sichtbar | Marker sichtbar nach Done (kein Rücksprung in Hidden-State) |
| Clear markers: nicht vorhanden, nicht sichtbar | Nach „Clear markers": keine Marker, kein Rendering |

**Placement und Interaktion:**

| Test | Bedingung |
|---|---|
| Long-press erzeugt Marker | Long-press auf freie Fläche im Referenzbild → Marker erscheint |
| Kein Marker bei Drag | Drag-Geste erzeugt keinen Marker |
| Kein Marker außerhalb Referenzbild | Long-press auf Letterbox → kein Marker |
| Limit-Snackbar | 6. Marker-Versuch → Snackbar mit korrektem String |
| Direktes Drag | Drag auf Marker (kein Tap vorher) → Marker verschiebt sich |
| Long-press-Delete | Long-press auf Marker → Warnzustand (Rot) → Marker gelöscht |
| Overlay-Drag im Edit-Modus | Overlay verschiebbar während Edit-Modus |
| Overlay-Scale im Edit-Modus | Overlay skalierbar während Edit-Modus |

**Lifecycle (UI):**

| Test | Bedingung |
|---|---|
| Marker folgen Overlay | Marker korrekt nach Overlay-Verschiebung |
| Marker folgen Overlay-Reset | Marker korrekt nach Overlay-Reset |
| Marker nach Capture sichtbar | Nach Aufnahme außerhalb Edit-Modus: Marker erhalten |
| Auto-open: Marker nach Rückkehr | Marker nach CompareScreen-Rückkehr unverändert |
| Rotation: Marker korrekt | Marker nach Rotation korrekt positioniert |
| Rotation: Edit-Modus erhalten | Modus-Flag nach Rotation unverändert |
| Display-Mode-Wechsel | Marker korrekt nach COMPARE_WITH_PREVIEW ↔ SHOW_FULL_IMAGE |
| Referenz-Remove: Marker weg | Marker verschwinden; Edit-Modus endet |
| Referenz-Replace: Marker weg | Marker verschwinden; Edit-Modus endet |
| Camera Zoom Mode blockiert | Camera-Zoom-Toggle nicht verfügbar im Edit-Modus |
| Camera Zoom Mode aktiv außerhalb | Camera-Zoom verfügbar im normalen Modus (auch mit sichtbaren Markern) |
| Viewport-Rand nur im Edit-Modus | Blauer Rand sichtbar nur wenn `isEditModeActive = true` |
| Viewport-Rand nach Done weg | Rand verschwindet nach Done |

### 7.5 Regressionsschutz

Die folgenden bestehenden Tests dürfen nicht rot werden:

- Alle bestehenden CameraScreen-State-Tests
- Overlay-Transform-Tests
- Capture-Pipeline-Tests
- Snackbar-Replay-Schutz-Tests
- Bottom-Control-Layout-Tests
- Compare-Navigation-Tests
- Session-Storage-Tests
- Reference-Menü-Tests (falls vorhanden)

---

### 7.6 Eigentumsmodell-Tests

Diese Tests validieren das Eigentumsmodell aus §2.1 und UX-Spec §7.1. Sie sind eigenständig formuliert — als Eigentumsaussagen, nicht als UI-Aktionen — damit ein Implementierer nicht versehentlich die Eigentumsregeln bricht.

**Prinzip: Gleicher Eigentümer → Marker bleiben**

| Test | Bedingung |
|---|---|
| Overlay-Drag löscht keine Marker | `markers` nach Overlay-Drag identisch |
| Overlay-Scale löscht keine Marker | `markers` nach Overlay-Scale identisch |
| Overlay-Reset löscht keine Marker | `markers` nach `onOverlayReset()` identisch |
| Capture löscht keine Marker | `markers` nach erfolgreicher Aufnahme identisch |
| Compare-Navigation löscht keine Marker | `markers` nach Navigate-zu-Compare und Rückkehr identisch |
| Display-Mode-Wechsel löscht keine Marker | `markers` nach Display-Mode-Wechsel identisch |
| Hide markers löscht keine Marker | `markers.size` nach `hideMarkers()` identisch; `markersVisible = false` |
| Show markers löscht keine Marker | `markers.size` nach `showMarkers()` identisch |
| App-Hintergrund löscht keine Marker | `markers` nach Hintergrund und Rückkehr identisch |
| Rotation löscht keine Marker | `markers` nach Rotation identisch |
| Edit-Modus-Exit (Done) löscht keine Marker | `markers` nach `exitMarkerEditMode()` identisch |
| Edit-Modus-Exit (Back) löscht keine Marker | `markers` nach Back identisch |

**Prinzip: Eigentümer wechselt/entfällt → Marker entfallen**

| Test | Bedingung |
|---|---|
| Replace löscht alle Marker | `markers.isEmpty()` nach `clearMarkersOnReferenceChange()` via Replace |
| Remove löscht alle Marker | `markers.isEmpty()` nach `clearMarkersOnReferenceChange()` via Remove |
| Reset-after-capture löscht Marker | `markers.isEmpty()` nach Referenz-Entfernung durch Setting |
| App-Neustart löscht Marker | `markers.isEmpty()` nach Neustart (Initialzustand) |
| Replace löscht Edit-Modus | `isEditModeActive = false` nach Replace |
| Remove löscht Edit-Modus | `isEditModeActive = false` nach Remove |

**Prinzip: Session-Dateien niemals Marker-Owner**

| Test | Bedingung |
|---|---|
| `SessionStorage` enthält keine Marker | `SessionStorage.saveSession()` Aufruf → `metadata.json` enthält kein Marker-Feld |
| `clearMarkersOnReferenceChange()` wird nicht von Overlay-Reset aufgerufen | Nach `onOverlayReset()`: `clearMarkersOnReferenceChange()` wurde nicht aufgerufen |
| Marker-State wird nicht an CompareScreen übergeben | `CompareScreen`-Navigation-Parameter enthalten kein Marker-Feld |
| Marker erscheinen nicht in `reference.jpg` | `ReferenceRenderer.render()` erhält keine Marker-Daten |

---

## 8. Regressionsrisiken

### 8.1 Overlay-Gesten

**Risiko:** Long-press-Erkennung könnte Overlay-Drag stören.

**Mitigierung:** Long-press und Marker-Gesten nur im Edit-Modus aktiv. Touch-Slop-Abbruch.

### 8.2 Capture-Button dauerhaft deaktiviert

**Risiko:** Capture bleibt nach Exit des Edit-Modus deaktiviert.

**Mitigierung:** Deaktivierung ausschließlich an `isEditModeActive` gebunden. Alle Exit-Pfade setzen Flag sofort zurück. Expliziter Test.

### 8.3 State-Konsistenz `markersVisible` bei `markersExist = false`

**Risiko:** `hideMarkers()` wenn keine Marker vorhanden → `markersVisible = false` → neue Marker würden unsichtbar erscheinen.

**Mitigierung:** `enterMarkerEditMode()` setzt immer `markersVisible = true`. Neue Marker werden nur im Edit-Modus platziert. Invariante ist strukturell garantiert.

### 8.4 Drag-Diskriminierung

**Risiko:** Versehentlicher Marker-Drag statt Overlay-Drag wenn Finger nah an Marker.

**Mitigierung:** Drag-Prioritätszone (`dragPriorityRadiusDp` aus `ReferenceMarkerDefaults`) deutlich kleiner als 48 dp. In UI-Tests mit verschiedenen Finger-Positionen validieren.

### 8.5 `ReferenceMarkerDefaults` Inline-Bypass

**Risiko:** Entwickler codiert Größen oder Farben inline anstatt `ReferenceMarkerDefaults` zu nutzen.

**Mitigierung:** Grep-Test in CI: kein numerischer Marker-Radius / Marker-Farbe außerhalb `ReferenceMarkerDefaults.kt`.

### 8.6 Koordinatentransformation bei Display-Mode-Wechsel

**Risiko:** Marker springen nach Display-Mode-Wechsel.

**Mitigierung:** Normalisierte Koordinaten sind Display-Mode-agnostisch. Transformation nutzt immer die Parameter des aktuellen Display-Modes. Explizite Tests.

---

## 9. Dokumentations-Updates bei Implementierung

| Dokument | Änderung |
|---|---|
| `CAMERA_WORKFLOW_UX_V1.md` | Marker Edit-Modus; Capture deaktiviert; Modus-Indikator; Camera Zoom Mode; Long-press-Definition |
| `CLAUDE_PROJECT_INSTRUCTION.md` | Addendum: Reference Markers State; Lifecycle; Menü-Struktur |
| `SETTINGS_UX_V1.md` | „Reset overlay after capture" entfernt Referenz → Marker-Konsequenz |

Keine Änderungen nötig: `COMPARE_FLOW_V1.md`, `COMPARE_SESSION_RENDERING_V1.md`, `SESSION_METADATA_V1.md`, `SESSION_ORIGINALS_V1.md`, `VIDEO_EXPORT_V1.md`, `SHARE_COMPARISON_IMAGE_V1.md`, `SESSION_BACKUP_EXPORT_V1.md`, `GPS_RECREATION_SYSTEM_V1.md`.

---

## 10. Release-Überlegungen

### Voraussetzungen für Release

- Alle Unit-Tests grün (neue + bestehende)
- Alle Instrumentation-Tests grün
- Grep-Tests: kein „Alignment Points" / „Ausrichtungspunkte" in User-facing Strings
- Smoke-Tests: Marker setzen, verschieben, löschen
- Smoke-Tests: Hide / Show Marker
- Smoke-Tests: Clear Marker
- Smoke-Tests: Rotation mit Markern
- Smoke-Tests: Overlay-Reset mit Markern
- Smoke-Tests: Display-Mode-Wechsel mit Markern
- Smoke-Tests: Referenz Replace / Remove → Marker weg
- Smoke-Tests: App-Hintergrund und zurück → State erhalten
- Smoke-Tests: Compare-Navigation → Edit-Modus erhalten
- Smoke-Tests: Aufnahme außerhalb Edit-Modus → Marker überleben
- Smoke-Tests: Auto-open Compare → Marker nach Rückkehr
- Smoke-Tests: Kein Marker in gespeichertem Foto
- Smoke-Tests: Kein Marker in CompareScreen
- Smoke-Tests: Viewport-Rand nur im Edit-Modus sichtbar
- Smoke-Tests: Camera Zoom Mode blockiert im Edit-Modus, aktiv außerhalb

### Nicht erforderlich für Release

- Persistenz über App-Neustart (Nicht-Ziel)
- Barrierefreiheits-Audit (wünschenswert, kein Blocker für V1)

### Empfohlene Release-Reihenfolge

**Phase 1–3** als erste releaseable Einheit: Menü, Edit-Modus, Marker setzen (noch kein Drag/Delete).

**Phase 4–5** als zweite Einheit: Drag, Delete, Sichtbarkeitssteuerung.

**Phase 6** (Lifecycle, Accessibility, i18n-Vollständigkeit) muss in eine der beiden Einheiten integriert sein — kein separates Release.

---

## 11. Offene Entscheidungen

| Entscheidung | Phase | Status |
|---|---|---|
| Konkretes Design des Warnzustands (Rot ohne Animation — empfohlen) | Phase 4 | Offen — auf Gerät bestätigen |
| Exakter `dragPriorityRadiusDp` (~15 dp — empfohlen) | Phase 4 | Offen — in UI-Tests validieren |
| Marker-Sichtgröße auf kleinen Screens (≤ 360 dp) | Phase 2 | Offen — auf Referenzgerät testen |
| Accessibility Actions für Long-press-Gesten (TalkBack) | Phase 6 | Offen — Accessibility-Implementierung festlegen |

### Alle entschiedenen Punkte (kumuliert Rev 1–5)

| Entscheidung | Entscheidung | Rev |
|---|---|---|
| Select-State | Kein Select-State | 2 |
| Compare-Button im Edit-Modus | Aktiv | 2 |
| Overlay-Opacity im Edit-Modus | Keine Automatik | 2 |
| Marker-Form | Ring + Mittelpunkt | 2 |
| Limit | 5; nicht vorab kommuniziert | 2 |
| Camera Zoom Mode im Edit-Modus | Deaktiviert | 2 |
| Fertig-Button Platzierung | Top-Bar als Textbutton | 3 |
| Long-press außerhalb Referenzbild | Kein Marker | 3 |
| Leerstate-Hint-Text | Nur Create-Geste | 3 |
| Capture im Edit-Modus | Unmöglich (Button deaktiviert) | 3 |
| Sichtbarkeits-Toggle | Implementiert (Hide/Show) | 4 |
| Feature-Name (Nutzer) | „Markers" | 4 |
| Feature-Name (intern) | „Reference Markers" | 4 |
| Zentrale Visual-Komponente | `ReferenceMarkerDefaults` | 4 |
| Dreizustandsmodell | `markersExist`, `markersVisible`, `isEditModeActive` | 4 |
| Enter-Edit-Modus aus Hidden | Marker werden automatisch eingeblendet | 4 |
| Done aus Edit-Modus | Marker bleiben sichtbar (kein Rücksprung in Hidden) | 4 |
| Camera Zoom Mode außerhalb Edit-Modus | Aktiv (auch wenn Marker sichtbar/ausgeblendet) | 4 |
| Clear markers | Löscht Marker + beendet Edit-Modus + setzt visible=true | 4 |
| Hide markers | Blendet aus + beendet Edit-Modus | 4 |
| Edit-Modus aktiv, keine Marker | Kein Marker-Abschnitt im Reference-Menü | 4 |
| Eigentumsmodell: Referenz = alleiniger Eigentümer | Marker-State niemals in Session-Dateien | 5 |
| `clearMarkersOnReferenceChange()` nicht bei Overlay-Reset | Overlay-Transform ≠ Eigentümer | 5 |
| Marker-State nicht an CompareScreen übergeben | CompareScreen ≠ Eigentümer | 5 |

---

## 12. Änderungsprotokoll

### Revision 5 — 2026-06-26

**Neuer Abschnitt §2.1 — Eigentumsmodell:**

Bindende Implementierungsregel: Das Referenzbild ist der alleinige Eigentümer des Marker-States. Tabelle aller Komponenten, die Marker-State niemals besitzen dürfen, mit Begründung.

**Aktualisierter Abschnitt §4 — Nicht berührte Dateien:**

Jede gesperrte Datei hat jetzt eine explizite Eigentumsbegründung.

**Neuer Abschnitt §7.6 — Eigentumsmodell-Tests:**

Drei Testgruppen:
1. Gleicher Eigentümer → Marker bleiben (12 Tests)
2. Eigentümer wechselt/entfällt → Marker entfallen (6 Tests)
3. Session-Dateien niemals Marker-Owner (4 Tests)

Diese Tests sind als Eigentumsaussagen formuliert, nicht als UI-Aktionen, damit Ownership-Verletzungen beim Implementieren direkt erkannt werden.

**Aktualisierte Entscheidungstabelle §11:**

Zwei neue Einträge für eigentumsrelevante Implementierungsregeln.

### Revision 4 — 2026-06-26

**Umbenennung:**

- Alle internen Klassen- und Dateinamen: `AlignmentPoint*` → `ReferenceMarker*`
- ViewModel-Methoden: `enterAlignmentPointsMode()` → `enterMarkerEditMode()`, etc.
- String-Keys: `alignment_points_*` → `markers_*`
- Kein „Ausrichtungspunkte" mehr in nutzer-sichtbaren Strings

**Neue Zustandsvariable:**

- `markersVisible: Boolean` zu `ReferenceMarkersState` hinzugefügt
- `hideMarkers()` und `showMarkers()` als neue ViewModel-Methoden

**Neue Dateien:**

- `ReferenceMarkerDefaults.kt` (zentrale Visual-Parameter)
- `ReferenceMarker.kt` (umbenannt aus `AlignmentPoint.kt`)
- `ReferenceMarkersState.kt` (umbenannt; enthält neue `markersVisible`-Variable)
- `ReferenceMarkerOverlay.kt` (umbenannt aus `AlignmentPointsOverlay.kt`)

**Neue String-Keys:**

- `markers_menu_hide` / „Hide markers" / „Marker ausblenden"
- `markers_menu_show` / „Show markers" / „Marker anzeigen"
- Alle Keys von `alignment_points_*` auf `markers_*` umgestellt

**Aktualisierte Phasen:**

- Phase 5 neu: Sichtbarkeitssteuerung (Hide/Show)
- Phase 6 neu: Vollständige Lifecycle-Integration mit 3 Zuständen

**Aktualisierte Tests:**

- Alle 5 Menü-Zustände getestet
- Vollständige Sichtbarkeits-Tests (Hide, Show, Edit-aus-Hidden, Done-nach-Hidden)
- String-Terminologie-Tests (grep auf obsolete Begriffe)
- `ReferenceMarkerDefaults`-Konsistenz-Tests

---

### Revision 3 — 2026-06-26

Lifecycle-Widerspruch behoben. Zwei-Tabellen-Ansatz. Fertig-Platzierung entschieden. Hint vereinfacht.

### Revision 2 — 2026-06-26

Compare aktiv. Ring-Form. Direktes Interaktionsmodell. Dynamisches Menü.

### Revision 1 — 2026-06-26

Erstversion.
