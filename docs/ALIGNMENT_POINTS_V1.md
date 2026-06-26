# Reference Markers — ALIGNMENT_POINTS_V1.md

## 1. Dokumentstatus

Diese Spezifikation ist das **maßgebliche UX-Dokument** für die Funktion „Reference Markers" (Marker) in SameView.

**Dateiname:** `ALIGNMENT_POINTS_V1.md` (bleibt unverändert, um Verlinkungen nicht zu brechen)
**Feature-Name (intern):** Reference Markers
**Feature-Name (nutzerseitig, Menü):** Markers

Sie ist geschrieben für:
- Analyse- und Planungssessions
- KI-Implementierungssessions
- Produktentscheidungen

Wenn ein späterer Implementierungsvorschlag mit diesem Dokument in Konflikt steht, gewinnt dieses Dokument — es sei denn, der Nutzer trifft explizit eine abweichende Produktentscheidung.

**Revision:** 5 (2026-06-26) — Eigentumsmodell eingeführt: „Reference owns markers." Lifecycle-Kapitel auf Eigentumsprinzip umgeschrieben. Implementierungsplan gegen versehentliche Session-Kopplung abgesichert. Beide Dokumente sind jetzt Implementierungs-Quelldokument. Änderungsprotokoll am Ende.

---

## 2. Zweck der Funktion

### Kernproblem

Beim Nachfotografieren eines Altfotos stört das halbdurchsichtige Overlay die visuelle Wahrnehmung, sobald die grobe Ausrichtung abgeschlossen ist.

Typische Situation:

1. Nutzer lädt ein Referenzbild (z. B. Kirchturm aus den 1970ern)
2. Nutzer richtet das Overlay grob aus
3. Ab diesem Punkt ist das gesamte halbtransparente Bild eher störend als hilfreich
4. Der Nutzer würde gerne nur die 2–4 kritischen Bildpunkte sehen (Turmspitze, Fensterecke, Straßenrand)
5. Das Overlay muss er aber sichtbar lassen, um die Präzisionspunkte überhaupt zu finden

**Reference Markers lösen dieses Problem**, indem der Nutzer wenige präzise Marker auf dem Referenzbild setzen kann. Diese Marker bleiben sichtbar — auch wenn die Opacity des Overlays manuell auf nahezu null reduziert wird. Zusätzlich können Marker vorübergehend ausgeblendet werden, ohne sie zu löschen.

**Reference Markers sind eine Präzisionshilfe und keine Bildbearbeitungsfunktion.**
Sie dienen ausschließlich dazu, markante Punkte eines Referenzbildes für die möglichst exakte Reproduktion eines Motivs hervorzuheben. Sie sind bewusst auf wenige temporäre Marker beschränkt und ausdrücklich nicht zum Annotieren, Zeichnen oder Bearbeiten des Referenzbildes gedacht.

### Zielpublikum

Fotografen, die:
- Altfotos möglichst präzise reproduzieren wollen
- An Orten mit klar erkennbaren Fixpunkten arbeiten (Architektur, Landmarken, Berge, Brücken)
- Bereits die grobe Ausrichtung abgeschlossen haben und nun feinabstimmen

---

## 3. UX-Analyse: Kernfragen

### 3.1 Löst diese Funktion ein echtes Nutzerproblem?

**Ja.** Der Kern-Anwendungsfall — „Ich bin grob ausgerichtet, aber das gesamte Overlay ist nun eher im Weg" — ist ein reales und häufig auftretendes Problem in der Reproduktionsfotografie. Marker auf kritischen Bildpunkten erlauben:

- Präzisere Ausrichtung mit weniger visueller Ablenkung
- Möglichkeit, die Overlay-Opacity auf Minimum zu setzen, ohne die Orientierung zu verlieren
- Fokus auf 2–5 aussagekräftige Punkte statt auf das gesamte Bild
- Temporäres Ausblenden der Marker ohne Datenverlust

### 3.2 Wird ein normaler Nutzer die Funktion verstehen?

**Wahrscheinlich — wenn der Einstieg klar gestaltet ist.**

Die Kernidee ist intuitiv: „Ich setze Marker auf die wichtigen Stellen meines Referenzbilds." Das ist ein vertrautes mentales Modell (Vergleich: Pins auf Karten).

Der Einstiegspunkt im Reference-Menü ist nicht selbst-erklärend. Der Leerstate-Hint beim ersten Betreten des Edit-Modus ist Pflicht.

### 3.3 Ist die Funktion zu fortgeschritten für SameView?

**Nein — wenn sie optional und für nicht-nutzende Nutzer unsichtbar bleibt.**

Sie erscheint nur im Reference-Menü wenn ein Referenzbild geladen ist. Nutzer, die sie nie brauchen, sehen keinen Unterschied.

### 3.4 Bleibt sie konsistent mit der SameView-Philosophie?

**Ja** — als temporärer Edit-Modus mit klarem Entry/Exit. Die Marker selbst sind dauerhaft (solange die Referenz geladen ist) und können sichtbar oder ausgeblendet sein. Der Bearbeitungsmodus ist immer temporär.

### 3.5 Was ist das kleinste sinnvolle Feature?

**Minimum sinnvolles V1:**

1. Entry aus Reference-Menü → „Markers..."
2. Viewport erhält blauen Rand als Edit-Modus-Indikator
3. Leerstate-Hint: „Long press to place a marker" / „Gedrückt halten, um Marker zu setzen"
4. Long-press auf Overlay-Bereich → Marker wird gesetzt (max. 5)
5. Drag auf Marker → Marker direkt verschieben (kein Select-Schritt)
6. Long-press auf bestehenden Marker → Marker löschen
7. „Done" / „Fertig" beendet Edit-Modus; Marker bleiben sichtbar
8. „Hide markers" / „Marker ausblenden" blendet Marker aus ohne zu löschen
9. Marker verschwinden bei Referenz entfernen oder ersetzen

---

## 4. Empfohlene Nutzerworkflows

### 4.1 Hauptworkflow: Marker setzen und aufnehmen

```
Referenzbild laden
Overlay grob ausrichten
Reference-Menü → "Markers..."
  [Edit-Modus aktiv – blauer Viewport-Rand, Leerstate-Hint]
Long-press auf Kirchturmspitze → Marker 1 gesetzt
Long-press auf Dachkante → Marker 2 gesetzt
Long-press auf Straßenrand → Marker 3 gesetzt
"Done" → Edit-Modus verlassen
  [Normaler Kameramodus, 3 Marker sichtbar]
Overlay-Opacity manuell sehr niedrig setzen
Kamera feinabstimmen bis Live-Vorschau mit Markern übereinstimmt
Aufnahme
```

### 4.2 Marker temporär ausblenden

```
Marker vorhanden und sichtbar
Reference-Menü → "Hide markers"
  [Marker ausgeblendet, aber noch vorhanden]
  [Kameraansicht ohne Marker-Overlay]
Aufnahme oder weiterer Workflow
Reference-Menü → "Show markers"
  [Marker wieder sichtbar]
```

### 4.3 Ausgeblendete Marker bearbeiten

```
Marker vorhanden, aber ausgeblendet
Reference-Menü → "Edit markers..."
  [Edit-Modus aktiv, Marker werden automatisch eingeblendet]
  [Blauer Viewport-Rand sichtbar]
Marker bearbeiten
"Done"
  [Edit-Modus verlassen, Marker sichtbar]
  [Um wieder auszublenden: Reference-Menü → "Hide markers"]
```

Hinweis: Entering edit mode always makes markers visible. After Done, they stay visible. The user must explicitly hide them again if desired.

### 4.4 Letzte Aufnahme prüfen, dann weiter Marker bearbeiten

```
Marker gesetzt, Edit-Modus aktiv
Compare-Button tippen → CompareScreen öffnet sich
Letzte Aufnahme prüfen
Back → zurück zu CameraScreen
  [Edit-Modus noch aktiv, blauer Rand]
  [Marker wie vorher vorhanden und sichtbar]
Weiter bearbeiten oder "Done"
```

### 4.5 Aufnahme mit Auto-Open Compare

```
Marker gesetzt, "Done" gedrückt
  [Normaler Kameramodus, Marker sichtbar]
Overlay-Opacity reduzieren, Kamera ausrichten
Aufnahme
Auto-Open Compare → CompareScreen
Back → CameraScreen
  [Normaler Kameramodus, Marker weiterhin sichtbar]
  [Edit-Modus NICHT aktiv — war vor Aufnahme bereits beendet]
```

Bei aktivem Setting „Overlay nach Aufnahme zurücksetzen":

```
Aufnahme
→ Referenz wird entfernt
→ Marker verschwinden (Referenz ist weg)
Auto-Open Compare → CompareScreen
Back → CameraScreen ohne Referenz, ohne Marker
```

### 4.6 Neues Referenzbild laden

```
Marker vorhanden (sichtbar oder ausgeblendet)
Reference-Menü → "Replace" → neues Bild wählen
  [Nutzergedanke: "Ich nehme ein anderes Referenzbild"]
  [Marker verschwinden — sie gehörten zum alten Bild]
  [Edit-Modus endet falls aktiv]
Neues Referenzbild geladen, keine Marker
```

---

## 5. Abgelehnte Konzepte

### 5.1 Abgelehnt: Separater Editor-Screen

Verlässt den Kamera-Kontext. Marker müssen im aktuellen Overlay-Transform-Kontext gesetzt werden — nur die Live-Kamera im Hintergrund gibt die richtige Orientierung.

### 5.2 Abgelehnt: Tap to Place

Versehentliche Erstellung zu wahrscheinlich. Long-press als bewusste Geste verhindert Versehen.

### 5.3 Abgelehnt: Kein expliziter Edit-Modus

Overlay-Gesten (Drag, Pinch) wären im normalen Kamerabetrieb aktiv. Ohne Moduswechsel entstehen Gestenkonflikte.

### 5.4 Abgelehnt: Nummerierte Marker

Erhöhen die visuelle Komplexität ohne konkreten Nutzwert. Schlichte Ringe reichen.

### 5.5 Abgelehnt: Gefüllter Kreis als Form

Ein gefüllter Kreis verdeckt den exakten Bildpunkt, den er markiert. Ein Ring mit Mittelpunkt lässt den Bildpunkt sichtbar — entscheidend für Präzision.

### 5.6 Abgelehnt: Automatische Opacity-Anpassung beim Edit-Modus-Eintritt

Automatische Zustandsänderungen widersprechen dem SameView-Prinzip „ruhig, vorhersagbar". Opacity-Bedürfnisse variieren je nach Nutzer und Situation. Der Slider bleibt im Edit-Modus aktiv.

### 5.7 Abgelehnt: Capture innerhalb des Edit-Modus

Der Edit-Modus signalisiert „noch nicht bereit aufzunehmen". Das explizite „Done" ist der psychologische Übergang. Versehentliche Aufnahmen während der Marker-Bearbeitung wären ein Fehler.

### 5.8 Abgelehnt: Select-Zustand für Marker

Kein Tap-to-Select, kein schwebendes Löschen-Icon. Direktes Drag und Long-press-Delete sind intuitiver und erfordern weniger kognitive Last bei maximal 5 Markern.

---

## 6. Empfohlenes Konzept

### 6.1 Dreizustandsmodell

Die Funktion verwendet **drei unabhängige Zustandsvariablen**:

```
markersExist: Boolean        — gibt es Marker in der Liste?
markersVisible: Boolean      — sollen Marker gerendert werden?
isMarkerEditModeActive: Boolean — ist der Edit-Modus aktiv?
```

**Wichtige Invarianten:**

- `markersVisible` ist nur bedeutsam wenn `markersExist = true`. Wenn keine Marker existieren, wird `markersVisible` auf `true` vorinitialisiert (default für die nächste Nutzung).
- `isMarkerEditModeActive = true` impliziert immer `markersVisible = true`. Der Edit-Modus setzt die Sichtbarkeit beim Eintritt zwingend auf sichtbar.
- Marker können nur im Edit-Modus gesetzt werden → neue Marker werden immer in einem sichtbaren Kontext erstellt.

**Nutzererwartung zur Sichtbarkeit:**

> „Ausblenden bedeutet: kurz aus dem Weg räumen." Nicht: „Löschen".

### 6.2 Einstiegspunkt und Menüstruktur

**Entry-Point:** Reference-Menü (Tap auf Reference-Button)

Das Reference-Menü passt sich dem aktuellen Zustand an.

#### Menü: Keine Marker vorhanden (`markersExist = false`)

```
─────────────────────────────────
  Reset
  Compare mode
─────────────────────────────────
  Markers...
─────────────────────────────────
  Replace
  Remove
─────────────────────────────────
```

#### Menü: Marker vorhanden, sichtbar, Edit-Modus NICHT aktiv

```
─────────────────────────────────
  Reset
  Compare mode
─────────────────────────────────
  Edit markers...
  Hide markers
  Clear markers
─────────────────────────────────
  Replace
  Remove
─────────────────────────────────
```

#### Menü: Marker vorhanden, ausgeblendet, Edit-Modus NICHT aktiv

```
─────────────────────────────────
  Reset
  Compare mode
─────────────────────────────────
  Edit markers...
  Show markers
  Clear markers
─────────────────────────────────
  Replace
  Remove
─────────────────────────────────
```

#### Menü: Edit-Modus aktiv, Marker vorhanden

```
─────────────────────────────────
  Reset
  Compare mode
─────────────────────────────────
  Hide markers
  Clear markers
─────────────────────────────────
  Replace
  Remove
─────────────────────────────────
```

„Edit markers..." entfällt (bereits aktiv, nicht gezeigt statt deaktiviert). „Done" ist im Top-Bar sichtbar — kein Menüeintrag dafür.

**Wichtig:** „Hide markers" aus dem aktiven Edit-Modus → blendet Marker aus UND beendet Edit-Modus automatisch (man kann nicht bearbeiten, was man nicht sieht). „Clear markers" aus dem aktiven Edit-Modus → löscht alle Marker UND beendet Edit-Modus.

#### Menü: Edit-Modus aktiv, KEINE Marker vorhanden

```
─────────────────────────────────
  Reset
  Compare mode
─────────────────────────────────
  [kein Marker-Abschnitt]
─────────────────────────────────
  Replace
  Remove
─────────────────────────────────
```

Kein Marker-Abschnitt — nichts ist vorhanden, was man ausblenden oder löschen könnte. Der Edit-Modus ist aktiv (blauer Rand, Leerstate-Hint sichtbar).

**Verhalten aller Menüeinträge während aktiven Edit-Modus:**

| Eintrag | Wirkung auf Edit-Modus | Wirkung auf Marker | Wirkung auf Sichtbarkeit |
|---|---|---|---|
| Reset | Bleibt aktiv | Unverändert | Unverändert |
| Compare mode | Bleibt aktiv | Unverändert | Unverändert |
| Hide markers | **Beendet** | Unverändert | → false |
| Clear markers | **Beendet** | Gelöscht | → true (default) |
| Replace | **Beendet** | Gelöscht | → true (default) |
| Remove | **Beendet** | Gelöscht | → true (default) |

### 6.3 Edit-Modus-Indikator

- **Viewport-Rand** in SameView-Blau (identisch mit Compare Accent Color)
- Subtil — schmaler Rahmen, kein Glow-Effekt
- Kein Text-Label im Viewport (keine Kamera-Verschmutzung)
- Erscheint **ausschließlich** während `isMarkerEditModeActive = true`
- Verschwindet wenn Edit-Modus endet (via Done, Back, Hide, Clear, Replace, Remove)
- Wird nie in gespeicherte Fotos, Compare, Video-Export oder Share-Image gerendert

**Done-Button:**

- Immer sichtbar während Edit-Modus (unabhängig von Marker-Anzahl)
- Platzierung: Top-Bar-Zone als Textbutton „Done" / „Fertig"
- Beendet Edit-Modus; Marker bleiben sichtbar

**Leerstate-Hint:**

- Sichtbar solange `isMarkerEditModeActive = true` UND `markersExist = false`
- Text: „Long press to place a marker" / „Gedrückt halten, um Marker zu setzen"
- Verschwindet sofort beim Setzen des ersten Markers
- Erscheint erneut wenn alle Marker gelöscht werden (und Edit-Modus noch aktiv)

### 6.4 Marker-Erstellung

- **Long-press** auf freien Bereich innerhalb des Referenzbildes → Marker gesetzt
- Long-press auf Bereich **außerhalb** des Referenzbildes (z. B. Letterbox-Bereich) → **kein Marker** (semantisch bedeutungslos)
- Long-press endet sofort bei Bewegung über Touch-Slop-Threshold → kein Marker bei Drag
- Haptisches Feedback beim Setzen
- **Maximum:** 5 Marker. Nicht vorab kommuniziert. Snackbar bei Erreichen des Limits.

### 6.5 Interaktionsmodell

Kein Select-Zustand. Kein schwebendes Löschen-Icon. Direktes Modell:

| Geste | Wirkung |
|---|---|
| Long-press auf freie Fläche (im Referenzbild) | Neuen Marker erstellen |
| Long-press auf bestehenden Marker | Marker löschen (kurzer Warnzustand) |
| Finger auf Marker + sofort ziehen | Marker direkt verschieben |
| Tap auf Marker | Keine Aktion |
| Tap auf freie Fläche | Keine Aktion |
| One-finger-Drag auf freie Fläche | Overlay verschieben |
| Two-finger-Geste | Overlay skalieren |

**Drag-Priorität:** Finger-Down im visuellen Marker-Radius + kleiner Puffer → Marker-Drag hat Vorrang über Overlay-Drag.

**Warnzustand beim Long-press-Delete:** Marker wechselt zu Rot bevor er bei Loslassen gelöscht wird. Haptisches Feedback.

**Overlay-Gesten bleiben aktiv:** 1-Finger-Drag auf freier Fläche = Overlay verschieben. 2-Finger = Overlay skalieren. Beide funktionieren parallel zu Marker-Gesten.

### 6.6 Zentrale Marker-Visual-Komponente

Alle Marker-Darstellungs-Parameter müssen in einer **einzigen zentralen Komponente** definiert sein.

**Empfohlener Name:** `ReferenceMarkerDefaults` (Kotlin object)

**Enthält:**

- Outer ring diameter (dp): ~20 dp
- Stroke width (dp): 2 dp
- Center dot diameter (dp): 4 dp
- Ring color: White
- Center dot color: SameView Blue (Compare Accent Color)
- Warning/delete state color: Red
- Drop shadow: weak, for contrast on bright backgrounds
- Touch target (dp): 48 dp (Accessibility)
- Drag priority radius (dp): ~14–16 dp (visueller Ring-Radius + Puffer)

**Zweck:**
- Form kann later per single-point-of-change angepasst werden
- Tests und Previews nutzen dieselben Konstanten
- Visuelle Parameter sind nicht über mehrere Dateien verstreut

Konsequenz: Kein Code darf Marker-Farben, -Größen oder -Touch-Radien inline hardcoden.

### 6.7 Koordinatenmodell

**Nutzererwartung:** „Der Marker soll immer auf der Turmspitze bleiben — egal wie ich das Overlay verschiebe oder skaliere."

**Gespeichertes Format:** Normalisierte Referenzbild-Koordinaten (0.0–1.0 in beide Achsen).

```
// Darstellung auf Screen (konzeptuell):
screenPos = overlayOriginOnScreen + (normalizedPos × referenceImageSize × overlayScale)

// Rücktransformation (Touch → Normalisiert):
normalizedPos = (touchPos − overlayOriginOnScreen) / (referenceImageSize × overlayScale)
```

Wenn `normalizedPos` außerhalb [0, 1] liegt → kein Marker erstellt (kein Clamping auf den Rand).

**Marker außerhalb des sichtbaren Viewports:** Berechnete Screen-Position außerhalb des Viewports → Marker wird geclipt (nicht gerendert). Erscheint wieder wenn Overlay zurückbewegt wird.

### 6.8 Edit-Modus verlassen

| Aktion | Edit-Modus | Marker | Sichtbarkeit |
|---|---|---|---|
| „Done" tippen | → inaktiv | Unverändert | Unverändert (bleibt sichtbar) |
| Back-Geste | → inaktiv | Unverändert | Unverändert (bleibt sichtbar) |
| „Hide markers" im Menü | → inaktiv | Unverändert | → false |
| „Clear markers" im Menü | → inaktiv | Explizit geleert | → true |
| Replace | → inaktiv | Entfallen (Eigentümer wechselt) | → true |
| Remove | → inaktiv | Entfallen (Eigentümer entfällt) | → true |
| App-Neustart | → inaktiv | Entfallen (kein Eigentümer) | → true |

Keine Bestätigung beim Verlassen via Done oder Back.

### 6.9 Camera Zoom Mode im Edit-Modus

**Camera Zoom Mode ist deaktiviert, während der Edit-Modus aktiv ist.**

**Begründung aus Fotografenperspektive:** Wenn die Camera-Zoom-Stufe geändert wird, ändert sich die Perspektive der Live-Kamera auf die Szene. Die bisherige Alignment-Arbeit verschiebt sich; die platzierten Marker passen möglicherweise nicht mehr zum neuen Kamera-Framing.

**Außerhalb des Edit-Modus:** Camera Zoom Mode steht uneingeschränkt zur Verfügung — auch wenn Marker sichtbar oder ausgeblendet sind. Die Sichtbarkeit der Marker hat keinen Einfluss auf Camera Zoom Mode.

### 6.10 Zusammenfassung: Was ist im Edit-Modus aktiv?

| Element | Zustand | Begründung |
|---|---|---|
| Aufnahme | **Deaktiviert** | „Done" = Übergang zum Aufnehmen |
| Compare-Button | **Aktiv** | Letzte Aufnahme prüfen und zurückkehren |
| Overlay-Drag (1-Finger, freie Fläche) | **Aktiv** | Overlay-Feinabstimmung direkt im Modus |
| Overlay-Scale (2-Finger) | **Aktiv** | Overlay-Feinabstimmung direkt im Modus |
| Camera Zoom Mode | **Deaktiviert** | Zoom-Änderung invalidiert Marker-Positionen |
| Opacity-Slider | **Aktiv** | Nutzer steuert Opacity selbst |
| Reference-Button | **Aktiv** | Hide, Clear, Replace, Remove, Reset zugänglich |
| Grid | **Aktiv** (falls eingestellt) | |
| GPS-Chip | **Aktiv** (falls aktiv) | |
| Compare Display Mode | **Wählbar** | Marker folgen dem Overlay |

---

## 7. Eigentumsmodell und vollständiger Lebenszyklus

### 7.1 Eigentumsmodell

**Das Referenzbild ist der alleinige Eigentümer der Marker.**

```
Reference owns markers.
```

Marker gehören ausschließlich dem aktuell geladenen Referenzbild. Sie gehören **nicht**:

- der Session oder Session-Dateien
- dem Overlay-Transform
- dem Edit-Modus
- CompareScreen
- der Kamera
- metadata.json
- Video-Exporten oder Share-Exporten
- Session-Backups

Dieses Eigentumsmodell ist die einzige Erklärung, die für alle Lifecycle-Entscheidungen benötigt wird:

**Solange dasselbe Referenzbild geladen ist, existieren die Marker.**

Wenn das Referenzbild wechselt oder verschwindet, entfallen auch seine Marker — weil ihr Eigentümer nicht mehr vorhanden ist.

#### Was „dasselbe Referenzbild bleibt" bedeutet

Die folgenden Ereignisse ändern das geladene Referenzbild nicht. Daher bleiben Marker in jedem dieser Fälle erhalten:

- Overlay verschoben, skaliert oder zurückgesetzt
- Aufnahme (außerhalb des Edit-Modus)
- Auto-open Compare nach Aufnahme
- Navigation zu CompareScreen und zurück
- Compare Display Mode gewechselt
- Edit-Modus betreten oder verlassen (Done / Back / Hide)
- Marker ausgeblendet oder eingeblendet
- App in den Hintergrund gegangen und zurückgekehrt
- Geräterotation
- Recomposition

#### Was „Referenzbild wechselt oder verschwindet" bedeutet

Die folgenden Ereignisse entfernen oder ersetzen das Referenzbild. Der Eigentümer existiert nicht mehr oder ist ein anderer. Daher entfallen die Marker:

- Replace: Der Nutzer wählt ein anderes Referenzbild. Der neue Eigentümer hat keine Marker.
- Remove: Der Nutzer entfernt das Referenzbild. Es gibt keinen Eigentümer mehr.
- „Reset overlay after capture" (Setting): Das Setting entfernt das Referenzbild nach der Aufnahme. Gleicher Fall wie Remove.
- App-Neustart: Kein Referenzbild geladen. Kein Eigentümer.

#### Der Edit-Modus ist kein Eigentümer

Der Edit-Modus ist ein temporäres Bearbeitungswerkzeug. Er begründet kein Eigentum. Marker überleben den Edit-Modus (Done, Back, Hide) — weil das Referenzbild weiterhin geladen ist.

#### Die Session ist kein Eigentümer

Marker werden nie in Session-Dateien gespeichert. Sie werden nie in `metadata.json`, `capture.jpg`, `reference.jpg` oder einem Export-Format persistiert. Sie sind reine UI-State-Daten, die an den aktuell geladenen Reference-State im ViewModel gebunden sind.

---

### 7.2 Zustandsvariablen

```
markersExist: Boolean           — gibt es Marker in der Liste?
markersVisible: Boolean         — sollen vorhandene Marker gerendert werden?
isMarkerEditModeActive: Boolean — ist der Edit-Modus UI aktiv?
```

**Initialzustand:** `markersExist = false`, `markersVisible = true`, `isMarkerEditModeActive = false`

Invarianten:
- `isMarkerEditModeActive = true` impliziert immer `markersVisible = true`
- `markersVisible` ist nur bedeutsam wenn `markersExist = true`; Default = `true`

---

### 7.3 Zustandsübergänge im Edit-Modus (`isMarkerEditModeActive = true`)

Legende: „Eigentümer unverändert" = Referenzbild bleibt geladen → Marker bleiben. „Eigentümer entfällt/wechselt" = Referenzbild verschwindet oder wird ersetzt → Marker entfallen.

| Ereignis | markersExist | markersVisible | isMarkerEditModeActive | Eigentumsregel |
|---|---|---|---|---|
| Long-press freie Fläche (im Referenzbild) | +1 Marker | `true` | bleibt `true` | Eigentümer unverändert |
| Long-press außerhalb Referenzbild | Unverändert | Unverändert | bleibt `true` | Eigentümer unverändert |
| Long-press auf Marker | -1 Marker | `true` | bleibt `true` | Eigentümer unverändert |
| Drag auf Marker | Position aktualisiert | `true` | bleibt `true` | Eigentümer unverändert |
| Tap „Done" | Unverändert | Unverändert | → `false` | Eigentümer unverändert |
| Back-Geste | Unverändert | Unverändert | → `false` | Eigentümer unverändert |
| Reference-Menü → „Hide markers" | Unverändert | → `false` | → `false` | Eigentümer unverändert |
| Reference-Menü → „Clear markers" | → `false` | → `true` | → `false` | Nutzer löscht explizit |
| Reference-Menü → Reset (Overlay-Transform) | Unverändert (Positionen recalc) | Unverändert | bleibt `true` | Eigentümer unverändert; Transform ≠ Eigentümer |
| Reference-Menü → Compare mode | Unverändert | Unverändert | bleibt `true` | Eigentümer unverändert |
| Reference-Menü → Replace | Entfallen | → `true` | → `false` | **Eigentümer wechselt** |
| Reference-Menü → Remove | Entfallen | → `true` | → `false` | **Eigentümer entfällt** |
| Navigate zu CompareScreen | Unverändert | Unverändert | bleibt `true` | Eigentümer unverändert |
| Rückkehr von CompareScreen | Unverändert | Unverändert | bleibt `true` | Eigentümer unverändert |
| App in Hintergrund | Unverändert | Unverändert | bleibt `true` | Eigentümer unverändert |
| App aus Hintergrund zurück | Unverändert | Unverändert | bleibt `true` | Eigentümer unverändert |
| Geräterotation | Unverändert (Positionen recalc) | Unverändert | bleibt `true` | Eigentümer unverändert |
| Compare Display Mode wechseln | Unverändert (Positionen recalc) | Unverändert | bleibt `true` | Eigentümer unverändert |
| Overlay-Drag / -Scale | Unverändert (Positionen recalc) | Unverändert | bleibt `true` | Eigentümer unverändert; Transform ≠ Eigentümer |
| **Aufnahme** | **UNMÖGLICH** (Capture deaktiviert) | — | — | — |
| App-Neustart | Entfallen | → `true` | → `false` | **Kein Eigentümer geladen** |

---

### 7.4 Zustandsübergänge außerhalb des Edit-Modus (`isMarkerEditModeActive = false`)

| Ereignis | markersExist | markersVisible | isMarkerEditModeActive | Eigentumsregel |
|---|---|---|---|---|
| Reference-Menü → „Markers..." | Unverändert | → `true` | → `true` | Eigentümer unverändert |
| Reference-Menü → „Edit markers..." | Unverändert | → `true` | → `true` | Eigentümer unverändert |
| Reference-Menü → „Show markers" | Unverändert | → `true` | bleibt `false` | Eigentümer unverändert |
| Reference-Menü → „Hide markers" | Unverändert | → `false` | bleibt `false` | Eigentümer unverändert |
| Reference-Menü → „Clear markers" | → `false` | → `true` | bleibt `false` | Nutzer löscht explizit |
| Reference-Menü → Reset (Overlay-Transform) | Unverändert (Positionen recalc) | Unverändert | bleibt `false` | Eigentümer unverändert; Transform ≠ Eigentümer |
| Reference-Menü → Replace | Entfallen | → `true` | bleibt `false` | **Eigentümer wechselt** |
| Reference-Menü → Remove | Entfallen | → `true` | bleibt `false` | **Eigentümer entfällt** |
| Aufnahme (erfolgreich) | Unverändert | Unverändert | bleibt `false` | Eigentümer unverändert |
| Auto-open Compare (nach Aufnahme) | Unverändert | Unverändert | bleibt `false` | Eigentümer unverändert |
| Rückkehr von CompareScreen | Unverändert | Unverändert | bleibt `false` | Eigentümer unverändert |
| „Reset overlay after capture" | Entfallen | → `true` | bleibt `false` | **Eigentümer entfällt** (Setting entfernt Referenz) |
| Overlay-Reset / -Drag / -Scale | Unverändert (Positionen recalc) | Unverändert | bleibt `false` | Eigentümer unverändert; Transform ≠ Eigentümer |
| Compare Display Mode wechseln | Unverändert (Positionen recalc) | Unverändert | bleibt `false` | Eigentümer unverändert |
| App in Hintergrund / zurück | Unverändert | Unverändert | bleibt `false` | Eigentümer unverändert |
| Geräterotation | Unverändert (Positionen recalc) | Unverändert | bleibt `false` | Eigentümer unverändert |
| App-Neustart | Entfallen | → `true` | bleibt `false` | **Kein Eigentümer geladen** |

---

### 7.5 Erläuterungen einzelner Übergänge

**Overlay-Reset löscht keine Marker:**
Das Referenzbild bleibt geladen; der Eigentümer wechselt nicht. Der Overlay-Transform ist nicht der Eigentümer — er ist nur die Darstellungsgeometrie. Marker sind in normalisierten Referenzbild-Koordinaten gespeichert; ihre Screen-Positionen aktualisieren sich korrekt nach dem Reset.

**Capture löscht keine Marker:**
Das Referenzbild bleibt geladen. Die Aufnahme gehört zum gleichen Referenzbild-Kontext. Marker sind Ausrichtungswerkzeuge für das Referenzbild, nicht für eine einzelne Aufnahme.

**Compare-Navigation löscht keine Marker:**
CompareScreen ist kein Eigentümer. Navigation zu CompareScreen und zurück berührt das geladene Referenzbild nicht.

**Replace entfernt Marker:**
Der Nutzer lädt ein anderes Referenzbild. Das neue Bild ist ein anderer Eigentümer ohne Marker. Das alte Bild und seine Marker existieren nicht mehr im aktiven Kontext. Der Nutzer denkt: „Ich nehme ein anderes Referenzbild." Die Marker des alten Bildes entfallen selbstverständlich.

**Remove entfernt Marker:**
Das Referenzbild existiert nicht mehr. Kein Eigentümer → keine Marker. Der Nutzer denkt: „Ich entferne meine Referenz." Er erwartet nicht, dass Marker ohne Referenz überleben.

**„Reset overlay after capture" entfernt Marker:**
Dieses Setting entfernt das Referenzbild nach einer Aufnahme — identisch mit einem expliziten Remove. Der Eigentümer entfällt. Marker entfallen als direkte Konsequenz. Nicht: „Marker werden gelöscht." Sondern: „Das Referenzbild ist weg."

**App-Neustart entfernt Marker:**
Nach einem Neustart ist kein Referenzbild geladen. Kein Eigentümer geladen → kein Marker-State. Konsistent mit allen anderen Overlay-States in SameView (Position, Skalierung, Opacity-Einstellungen werden nicht über Neustarts hinaus gespeichert).

**Edit-Modus (Done / Back / Hide) löscht keine Marker:**
Der Edit-Modus ist kein Eigentümer. Er ist ein temporäres Bearbeitungswerkzeug. Verlassen des Edit-Modus via Done oder Back ändert das geladene Referenzbild nicht — Marker bleiben. „Hide markers" blendet Marker aus, ohne den Eigentümer zu wechseln — Marker bleiben im State erhalten.

---

## 8. Wechselwirkungen mit bestehenden Einstellungen

| Einstellung / Funktion | Im Edit-Modus | Außerhalb |
|---|---|---|
| Aufnahme | Deaktiviert | Normal; Marker überleben |
| Compare-Button | Aktiv; Edit-Modus bleibt durch Navigate erhalten | Normal |
| Auto-open Compare | Nicht direkt relevant (Capture im Modus unmöglich) | Marker überleben; Edit-Modus nicht aktiv nach Rückkehr |
| Reset overlay after capture | Nicht direkt relevant (Capture im Modus unmöglich) | Referenz entfällt → Marker entfallen (Eigentümer entfällt) |
| Overlay-Reset (Menüaktion) | Edit-Modus bleibt; Marker bleiben; Positionen recalc | Marker bleiben; Positionen recalc |
| Compare Display Mode | Wählbar; Marker recalculate | Gleich |
| Grid | Aktiv | Aktiv |
| GPS Guidance | Aktiv | Aktiv |
| Camera Zoom Mode | Deaktiviert | Aktiv (auch wenn Marker sichtbar oder ausgeblendet) |
| Opacity-Slider | Aktiv | Aktiv |

---

## 9. Offene Fragen (minimale Restliste)

Alle wesentlichen UX-Entscheidungen sind in Revision 4 geschlossen.

### 9.1 Visueller Warnzustand beim Long-press-Delete

**Offen:** Konkrete Farbe und Animation. Empfehlung: Rot, keine Pulsanimation.

### 9.2 Exakter Drag-Prioritätsradius

**Offen:** Festzulegen als Teil von `ReferenceMarkerDefaults`. Empfehlung: ~14–16 dp. In UI-Tests validieren.

### 9.3 Accessibility Actions

**Offen:** Welche Accessibility Actions werden für Long-press-Create und Long-press-Delete angeboten (TalkBack)?

### 9.4 Marker-Sichtgröße auf kleinen Screens

**Offen:** Ring (~20 dp) auf ≤ 360 dp Breite — auf Referenzgeräten testen.

---

## 10. Risiken

### 10.1 Drag-Diskriminierung Marker vs. Overlay

**Risiko:** Versehentlicher Marker-Drag statt Overlay-Drag.

**Mitigierung:** Drag-Prioritätszone = visueller Ring-Radius + kleiner Puffer (~14–16 dp), nicht das 48 dp Accessibility-Target. In UI-Tests validieren.

### 10.2 Long-press-Konflikt bei langsamem Drag-Start

**Risiko:** Long-press-Timer feuert während langsamem Overlay-Drag-Start.

**Mitigierung:** Long-press-Erkennung endet sofort bei Bewegung über Touch-Slop-Threshold.

### 10.3 Koordinatentransformation bei Display-Mode-Wechsel

**Risiko:** Marker-Positionen falsch nach COMPARE_WITH_PREVIEW ↔ SHOW_FULL_IMAGE.

**Mitigierung:** Normalisierte Koordinaten sind Display-Mode-agnostisch; Transformation verwendet immer die Parameter des aktuellen Display-Modes. Explizite Tests erforderlich.

### 10.4 `markersVisible` vs. `markersExist` Konsistenz

**Risiko:** State-Kombination `markersExist = false, markersVisible = false` nach Clear könnte zu unerwartetem Verhalten führen wenn neue Marker gesetzt werden.

**Mitigierung:** Clear setzt `markersVisible = true` (default). Neue Marker erscheinen immer sichtbar, weil Edit-Modus bei Eintritt immer `markersVisible = true` setzt.

---

## 11. Explizite Nicht-Ziele

- **Freihand-Zeichnen oder Annotation**
- **Liniensegmente zwischen Markern**
- **Export von Markern** — nie in Bilder, Videos, Compare, Share oder Session Backup
- **Persistenz über App-Neustart**
- **Marker in CompareScreen** — nur CameraScreen
- **Automatische Marker-Vorschläge (AI/CV)**
- **Mehr als 5 Marker**
- **Beschriftung / Nummerierung der Marker**
- **Undo-System**
- **Marker in metadata.json**
- **Automatische Opacity-Anpassung**
- **Select-Zustand**
- **Schwebende Löschen-Icons**
- **Capture im Edit-Modus**
- **Camera Zoom Mode im Edit-Modus**
- **Sichtbarkeit außerhalb des Referenzbildbereichs** — Marker außerhalb des Viewports werden geclipt, nicht gerendert

---

## 12. Betroffene Spezifikationen

| Dokument | Art der Änderung |
|---|---|
| `CAMERA_WORKFLOW_UX_V1.md` | Neuer Abschnitt: Marker Edit-Modus; Capture deaktiviert; Modus-Indikator; Camera Zoom Mode; Long-press für Marker (gilt nicht für normalen Betrieb) |
| `SETTINGS_UX_V1.md` | Hinweis: „Reset overlay after capture" entfernt Referenz → Marker verschwinden als Konsequenz |
| `CLAUDE_PROJECT_INSTRUCTION.md` | Neues Addendum: Reference Markers State; Lifecycle; Menü-Struktur; drei Zustandsvariablen |
| `COMPARE_FLOW_V1.md` | Klarstellung: Marker erscheinen nicht in CompareScreen; kein Teil des Rendering-Pfads |
| `COMPARE_SESSION_RENDERING_V1.md` | Klarstellung: Marker von ReferenceRenderer ausgeschlossen |

Keine Änderungen nötig: `SESSION_METADATA_V1.md`, `SESSION_ORIGINALS_V1.md`, `VIDEO_EXPORT_V1.md`, `SHARE_COMPARISON_IMAGE_V1.md`, `SESSION_BACKUP_EXPORT_V1.md`, `GPS_RECREATION_SYSTEM_V1.md`.

---

## 13. Änderungsprotokoll

### Revision 5 — 2026-06-26

**Eigentumsmodell eingeführt (§7.1):**

Neues zentrales Erklärungsprinzip: „Reference owns markers." Das Referenzbild ist der alleinige Eigentümer der Marker. Dieser Grundsatz ersetzt alle bisherigen Einzelbegründungen im Lifecycle-Kapitel und macht die Spezifikation für Implementierer eindeutig navigierbar.

**Lifecycle-Kapitel umgeschrieben (§7):**

- §7.1 „Eigentumsmodell" als neuer Einstieg mit expliziten Eigentumsregeln
- Lifecycle-Tabellen §7.3 und §7.4 um Spalte „Eigentumsregel" erweitert
- Sprache geändert: „Gelöscht" → „Entfallen (Eigentümer wechselt/entfällt)" bei Replace / Remove / Neustart
- „Explizit geleert" als Unterschied zu eigentumsgetriebenen Entfällen bei Clear
- §7.5 Erläuterungen auf Eigentumsprinzip ausgerichtet

**Wording-Korrekturen:**

- §6.8 Tabelle: „Gelöscht" bei Replace/Remove/Neustart → „Entfallen (Eigentümer...)"
- §8 Wechselwirkungstabelle: „Reset overlay after capture" auf Eigentümer-Sprache aktualisiert

**Implementierungsplan:**

- Neues §2.1 „Eigentumsregeln für die Implementierung"
- Neue Ownership-Tests in §7
- Explizite Regel: Marker-State nie in Session-Dateien speichern

### Revision 4 — 2026-06-26

**Umbenennung:**
- Feature-Name: „Alignment Points" → „Reference Markers" (intern), „Markers" (nutzerseitig)
- Deutsche Bezeichnung: „Ausrichtungspunkte" → „Marker"
- Dateiname bleibt: `ALIGNMENT_POINTS_V1.md`
- Alle nutzerseitigen Begriffe wie „Ausrichtungspunkte", „Ausrichtungspunkte bearbeiten" etc. wurden ersetzt

**Neues Zustandsmodell:**
- Drei unabhängige Variablen: `markersExist`, `markersVisible`, `isMarkerEditModeActive`
- Bisher: implizit immer sichtbar
- Jetzt: Marker können existieren ohne sichtbar zu sein

**Neue Menüstruktur (5 statt 3 Zustände):**
- Neu: „Hide markers" / „Marker ausblenden"
- Neu: „Show markers" / „Marker anzeigen"
- Umbenennt: „Ausrichtungspunkte löschen" → „Clear markers" / „Marker löschen"
- Umbenennt: „Ausrichtungspunkte bearbeiten..." → „Edit markers..." / „Marker bearbeiten..."
- Umbenennt: „Ausrichtungspunkte..." → „Markers..." / „Marker..."
- Edit-Modus mit Markern: kein „Edit markers..." (entfällt, nicht deaktiviert)
- Edit-Modus ohne Marker: kein Marker-Abschnitt im Menü

**Neues Lifecycle-Kapitel (§7):**
- Vollständige Tabellen für beide Kontexte (im Modus / außerhalb)
- Sichtbarkeits-Spalte in allen Tabellen
- „Hide markers" und „Show markers" vollständig abgedeckt
- Begründungen für alle Entscheidungen ergänzt

**Neue Abschnitte:**
- §6.6: Zentrale Marker-Visual-Komponente (`ReferenceMarkerDefaults`)
- §6.10: Camera Zoom Mode außerhalb des Edit-Modus jetzt explizit aktiv

**Entfernte Nicht-Ziele:**
- „Sichtbarkeits-Toggle" — ist jetzt implementiert

**i18n:**
- Alle deutschen Strings auf „Marker..." Terminologie aktualisiert
- Englische Strings eingeführt (DE/EN parallel)

### Revision 3 — 2026-06-26

Widerspruch Capture/Edit-Modus behoben. Lifecycle in zwei Tabellen. Fertig-Button-Platzierung entschieden. Leerstate-Hint vereinfacht.

### Revision 2 — 2026-06-26

Compare-Button aktiv im Modus. Ring-Form. Direktes Interaktionsmodell. Dynamisches Menü. Vollständige Lifecycle-Tabelle.

### Revision 1 — 2026-06-26

Erstversion.
