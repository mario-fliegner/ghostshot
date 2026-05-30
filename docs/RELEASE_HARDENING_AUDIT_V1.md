# RELEASE_HARDENING_AUDIT_V1.md

## Zweck

Diese Datei ist die zentrale Sammlung aller Release-Hardening-Findings für SameView V1.

Sie dokumentiert ausschließlich Analyse-Ergebnisse.

**Kein Code wurde verändert. Kein Manifest wurde verändert. Keine Permissions wurden verändert. Keine Logs wurden verändert. Keine Dokumentation außerhalb dieser Datei wurde verändert.**

Analysegrundlage: tatsächlicher Codebestand (Kotlin-Quelldateien, AndroidManifest.xml, build.gradle.kts, ProGuard-Regeln, Backup-/Extraction-Regeln, Strings, Ressourcen) sowie die Dokumente CLAUDE_PROJECT_INSTRUCTION.md, IMPLEMENTATION_NOTES.md, CAMERA_WORKFLOW_UX_V1.md, COMPARE_FLOW_V1.md, COMPARE_SESSION_RENDERING_V1.md, SETTINGS_UX_V1.md.

Analysedatum: 2026-05-29.

---

## Zusammenfassung

Der Codebestand ist in weiten Teilen solide. Die Kernarchitektur (lokaler Betrieb, keine Netzwerkzugriffe, kein INTERNET-Permission, MediaStore-basierte Speicherung, Session-Atomizität, Path-Traversal-Schutz, Debug-Log-Gating via BuildConfig.DEBUG) ist korrekt umgesetzt.

Vor einem öffentlichen Play-Store-Upload gibt es zwei K.O.-Kriterien (HIGH): das fehlende Privacy-Policy-URL und die unvollständige Data-Safety-Deklaration. Beide sind Play-Store-seitige Anforderungen, keine Code-Probleme.

Weitere Findings liegen im Bereich Manifest-Konfiguration, Accessibility, Storage-Hardening und Release-Build-Robustheit und sind für ein Beta/Closed-Testing-Upload nicht blockierend, sollten aber vor dem öffentlichen Release adressiert werden.

**Keine CRITICAL-Findings identifiziert.**

---

## Findings — Gesamttabelle

| ID | Severity | Bereich | Beschreibung | Empfehlung |
|----|----------|---------|-------------|------------|
| M-01 | High | Manifest | `android.hardware.camera` mit `required="false"` — App kann auf Geräten ohne Kamerahardware installiert werden, obwohl keine Fallback-UX existiert | Evaluieren ob `required="true"` korrekt ist; alternativ explizite camera-less Fallback-UX |
| M-02 | Medium | Manifest | Kein `networkSecurityConfig` deklariert — kein formelles Klartext-Verbot, obwohl keine INTERNET-Permission vorhanden | `network_security_config.xml` mit `cleartextTrafficPermitted="false"` als Defense-in-Depth |
| M-03 | Info | Manifest | `xmlns:tools` deklariert, aber keine `tools:`-Attribute im Manifest verwendet | Namespace entfernen |
| M-04 | Info | Manifest | `<queries>` für `SENDTO/mailto` korrekt für About-Screen-Feedback-Intent; Android-11+-Sichtbarkeitsregel | Korrekt — Dokumentation für Reviewer ausreichend |
| M-05 | Info | Manifest | Kein `android:configChanges` auf MainActivity — korrekt für ViewModel + Compose Rotationsmodell | — |
| M-06 | Info | Manifest | `ACCESS_MEDIA_LOCATION` nicht dangerous, wird zur Laufzeit nicht explizit requested — unkonventionell, für Reviewer möglicherweise erklärungsbedürftig | Begründung in About/Privacy-Policy oder Play-Listing-Text aufnehmen |
| P-01 | High | Privacy | Kein Privacy-Policy-URL in der App vorhanden — Play Store fordert bei CAMERA + ACCESS_FINE_LOCATION einen verlinkbaren Datenschutztext | Privacy Policy URL erstellen und in About-Screen + Play-Listing-Felder eintragen |
| P-02 | Medium | Privacy | `metadata.json` speichert `reference.sourceDisplayName` als vollen URI-String (z. B. Google-Photos-Provider-Pfad + Image-ID) — im app-internen Speicher, vom Backup ausgeschlossen, aber auf gerooteten Geräten/ADB lesbar | Explizit als gewolltes Verhalten dokumentieren oder URI-Herkunft generalisieren |
| P-03 | Low | Privacy | GPS-Koordinaten (lat, lon) werden in `CameraViewModel` auf DEBUG-Level geloggt (`SameView.GPS`-Tag) — korrekt durch `BuildConfig.DEBUG` gegated, kein Leak in Release-Builds | Debug-Dokumentation für das Datenschutz-Logging-Verhalten |
| P-04 | Low | Privacy | Session-Delete entfernt nur den internen Session-Ordner (`filesDir/sessions/<id>/`). Das MediaStore-Foto in `Pictures/SameView` und die darin bereits geschriebenen GPS-EXIF-Tags bleiben vollständig erhalten — der Delete-Dialog kommuniziert diese Unterscheidung nicht | Delete-Bestätigungsdialog erweitern: expliziter Hinweis, dass das Foto im Gerätespeicher erhalten bleibt |
| P-05 | Info | Privacy | Rationale-Dialog (`settings_recreation_guidance_rationale_message`) enthält explizit: "Your location data is never shared or uploaded." — belegbar durch fehlendes INTERNET-Permission | Positiver Befund — konsistent mit Local-only-Modell |
| P-06 | Info | Privacy | Feedback-Intent via `Intent.ACTION_SENDTO / mailto:` — kein Datenversand ohne explizite Nutzeraktion | Positiver Befund |
| S-01 | Medium | Storage | `sameview_settings` DataStore-Datei ist nicht vom Auto-Backup ausgeschlossen — nach einem Geräte-Restore kann `recreation_guidance = true` wiederhergestellt werden, obwohl `ACCESS_FINE_LOCATION` auf dem neuen Gerät noch nicht erteilt ist; GPS-Toggle erscheint dann als aktiv, GPS-Aktivierung schlägt still intern fehl | Evaluieren ob Backup der Settings gewollt ist; ggf. `sameview_settings.preferences_pb` in backup_rules.xml und data_extraction_rules.xml ausschließen |
| S-02 | Medium | Storage | Session-Bilder werden als `file://`-URIs (via `Uri.fromFile()`) referenziert — aktuell sicher (nur interner Coil-Zugriff), aber bei zukünftiger Share-Funktion würde `FileUriExposedException` auf API 24+ auftreten | FileProvider vorbereiten und `file://`-URIs durch `content://`-URIs ersetzen, bevor Sharing implementiert wird |
| S-03 | Low | Storage | Keine Session-Storage-Quota-Verwaltung — Sessions akkumulieren unbegrenzt in `filesDir/sessions/` | Storage-Nutzungshinweis oder Cleanup-Option in Compare Library erwägen |
| S-04 | Low | Storage | `reference-original.jpg` wird mit 90 % JPEG-Qualität pro Session gespeichert (für zukünftige Re-Edit/Export-Features) — bei 12-MP-Referenzbildern ca. 2–5 MB zusätzlich pro Session | Akzeptiertes Verhalten — in Speicherplanung dokumentieren |
| S-05 | Info | Storage | Session-Atomizität korrekt: Partial-Sessions werden bei Schreibfehlern durch `sessionDir.deleteRecursively()` bereinigt; `metadata.json` wird zuletzt geschrieben | Positiver Befund |
| S-06 | Info | Storage | `backup_rules.xml` und `data_extraction_rules.xml` schließen `sessions/` korrekt aus Cloud-Backup und Device-Transfer aus | Positiver Befund |
| S-07 | Info | Storage | DataStore-Korruption wird via `ReplaceFileCorruptionHandler` mit `emptyPreferences()` abgefangen — Settings fallen auf Defaults zurück statt zu crashen | Positiver Befund |
| LC-01 | Low | Lifecycle | `deleteSession()` (Einzel-Delete aus `CompareScreen`) sendet bei Fehlschlag keine Nutzer-Snackbar — `deleteSessions()` (Multi-Delete aus Library) sendet korrekt `R.string.delete_failed` | `deleteSession()` bei Fehlschlag ebenfalls Snackbar emittieren |
| LC-02 | Low | Lifecycle | `captureWatchdogJob` (15-s-Timeout) läuft im `viewModelScope` — korrekte Implementierung, kein Memory-Leak erwartet; erwähnt als Referenz für zukünftige Änderungen | — |
| LC-03 | Info | Lifecycle | CameraX Async-Binding korrekt gegen Late-Bind nach Dispose abgesichert | Positiver Befund |
| LC-04 | Info | Lifecycle | `keepScreenOn` wird korrekt bei `ON_PAUSE`, `ON_STOP`, `ON_DESTROY` und CameraScreen-Dispose zurückgesetzt | Positiver Befund |
| LC-05 | Info | Lifecycle | GPS `startUpdates()` / `stopUpdates()` werden via `onCameraScreenActive()` / `onCameraScreenInactive()` lifecyclekorrrekt gesteuert | Positiver Befund |
| A-01 | High | Accessibility | CameraX-Preview (`PreviewView` via `AndroidView`) hat keine `contentDescription` — TalkBack-Nutzer erhalten für den Kern-Interaction-Bereich keine semantische Beschreibung | `contentDescription` auf den `AndroidView`-Wrapper setzen (z. B. "Live camera preview") |
| A-02 | Medium | Accessibility | Compare-Slider in `CompareScreen` hat korrekte Semantics (`progressBarRangeInfo`, `stateDescription`, `Role.Image`), aber keine TalkBack-kompatible Alternative-Action zum Verschieben des Dividers — nur pointer-basierte Drag-Geste | Accessibility-Action für Slider-Verschiebung via `semantics { onClick / customActions }` ergänzen |
| A-03 | Medium | Accessibility | Overlay-Gesture-Bereich in `CameraScreen` (Drag + Pinch) ist für TalkBack-Nutzer nicht bedienbar — keine Alternative-Actions für Overlay-Repositionierung oder Skalierung | TalkBack-Accessibility-Actions für Reset und Opacity ergänzen; vollständige Overlay-Steuerung via Touch-Gesten ist schwer vollständig zugänglich zu machen |
| A-04 | Low | Accessibility | App-Icon-`AndroidView` in `AboutScreen` hat keine `contentDescription` — dekoratives Element, Accessibility-Guidelines erlauben leere Beschreibung für Dekorationen | `contentDescription = ""` explizit auf `ImageView` setzen (markiert als dekorativ) |
| A-05 | Low | Accessibility | Feedback-Button in `AboutScreen` ist ein `clickable` `Box` ohne explizites `role = Role.Button` — Accessibility-Label wird korrekt aus dem Text-Kind bezogen, aber Role fehlt | `Modifier.semantics { role = Role.Button }` ergänzen |
| A-06 | Info | Accessibility | Alle `IconButton`-Komponenten (Back, History, Overflow, Delete, etc.) haben `contentDescription` via String-Resources | Positiver Befund |
| A-07 | Info | Accessibility | `SettingsSwitchRow`-Elemente sind mit `clickable` + `Switch` korrekt strukturiert — TalkBack kündigt Label und Zustand korrekt an | Positiver Befund |
| A-08 | Info | Accessibility | `compare_library_session_content_description` String-Resource wird für Session-Tiles verwendet | Positiver Befund |
| R-01 | Medium | Release | Kein Crash-Reporting integriert — Produktionsabstürze sind nur via Play Console Vitals analysierbar, was Verzögerung und eingeschränkte Stack-Details bedeutet | Firebase Crashlytics oder alternatives Crash-Reporting evaluieren; auch symbolicated Stacks benötigen Mapping-Datei-Upload |
| R-02 | Low | Release | ProGuard-Regeln sind minimal (nur `SourceFile,LineNumberTable` + `renamesourcefileattribute`) — keine eigenen Keep-Rules für App-Klassen. Setzt voraus, dass CameraX, Hilt, Coil und Compose ihre Consumer-ProGuard-Regeln korrekt liefern | Nach Release-Build explizit prüfen, dass keine kritischen Klassen geshrunkt wurden |
| R-03 | Low | Release | `kotlinx-coroutines-test:1.9.0` ist in `app/build.gradle.kts` hardcoded statt in `gradle/libs.versions.toml` | Version in `libs.versions.toml` centralisieren |
| R-04 | Info | Release | `versionCode = 1`, `versionName = "1.0"` — korrekt für V1; versionCode muss für jeden Upload inkrementiert werden | Version-Management-Prozess dokumentieren |
| R-05 | Info | Release | `isMinifyEnabled = true`, `isShrinkResources = true` — korrekt | Positiver Befund |
| R-06 | Info | Release | `buildFeatures.buildConfig = true` für BuildConfig.DEBUG-Log-Gating korrekt vorhanden | Positiver Befund |
| R-07 | Info | Release | `androidResources { generateLocaleConfig = true }` — automatische Locale-Config-Generierung aktiv | Positiver Befund |
| PS-01 | High | Play Store | Kein Privacy-Policy-URL — Play Store blockiert öffentliche Sichtbarkeit ohne verlinkten Datenschutztext für Apps mit CAMERA + ACCESS_FINE_LOCATION | Privacy Policy URL vor erstem öffentlichem Upload in Play Console eintragen |
| PS-02 | High | Play Store | Data-Safety-Formular in Play Console muss ausgefüllt werden — präziser Standort (optional, nicht weitergegeben, nicht für Tracking) und Kamera (Aufnahme, kein Upload) müssen korrekt deklariert werden | Data-Safety-Formular vollständig und korrekt ausfüllen |
| PS-03 | Medium | Play Store | `android.hardware.camera required="false"` — Play Store schließt Camera-Geräte-Filter nicht automatisch aus; Nutzer auf kameralosen Geräten landen in einem nicht-funktionsfähigen Zustand | Geräte-Targeting-Filter in Play Console prüfen; optional `required="true"` setzen |
| PS-04 | Low | Play Store | `support@sameview.app` hardcoded — Domain und E-Mail-Adresse müssen aktiv sein bevor Listing live ist | Domain-Registrierung und E-Mail-Konfiguration sicherstellen |
| PS-05 | Info | Play Store | Nur eine Activity, keine exported Content Providers, Services oder Broadcast Receivers — minimale Angriffsfläche für Play-Store-Reviewer | Positiver Befund |
| PS-06 | Info | Play Store | Kein INTERNET-Permission, keine Analytics, kein Telemetry — starke Privacy-Position für Review | Positiver Befund |
| PS-07 | Info | Play Store | Android Photo Picker (nicht READ_MEDIA_IMAGES) — entspricht Play-Store-Empfehlungen für Medien-Zugriff | Positiver Befund |

---

## Nach Themen gruppiert

### Manifest

**Gefundene Risiken:**
- `android.hardware.camera required="false"` erlaubt Installation auf kameralosem Gerät (M-01, High)
- Kein `networkSecurityConfig` als formelles Dokument der Netzwerkabstinenz (M-02, Medium)

**Korrekte Umsetzungen:**
- Nur `MainActivity` exportiert, korrekt mit MAIN/LAUNCHER-Intent-Filter
- Keine unnötigen Permissions
- Kein INTERNET-Permission
- `allowBackup="true"` mit expliziten Ausschlussregeln für Sessions
- `dataExtractionRules` korrekt verknüpft
- `queries`-Block korrekt für Feedback-Intent

---

### Privacy

**Gefundene Risiken:**
- Fehlende Privacy Policy (P-01, High)
- Reference-URI-String in `metadata.json` gespeichert (P-02, Medium)
- GPS-Koordinaten in Debug-Logs (P-03, Low)
- Session-Delete kommuniziert GPS-EXIF-Verbleib im MediaStore-Foto nicht (P-04, Low)

**Korrekte Umsetzungen:**
- GPS nur aktiv wenn alle vier Bedingungen erfüllt (Guidance ON + Permission + Reference-GPS + Screen aktiv)
- GPS nie im Hintergrund
- `reference.jpg` erhält niemals GPS-EXIF
- Debug-Logs korrekt durch `BuildConfig.DEBUG` gegated
- Kein Datentransfer, kein Upload, kein Tracking
- Explizite Privacy-Aussage im Rationale-Dialog

---

### Storage

**Gefundene Risiken:**
- `sameview_settings` DataStore nicht vom Backup ausgeschlossen (S-01, Medium)
- `file://`-URIs für Session-Bilder (S-02, Medium)
- Keine Session-Storage-Quota (S-03, Low)
- `reference-original.jpg` Speicheroverhead (S-04, Low)

**Korrekte Umsetzungen:**
- Session-Atomizität korrekt (Partial-Session-Cleanup)
- `metadata.json` zuletzt geschrieben
- Path-Traversal-Schutz in `SessionDeleter` und `SessionStorage.resolveDirectSessionDir()`
- `isSafeFilename()` in `SessionScanner`
- Backup-Ausschluss von `sessions/` korrekt
- DataStore-Korruptionshandling

---

### Lifecycle

**Gefundene Risiken:**
- Einzel-Delete-Fehlschlag ohne Nutzer-Feedback (LC-01, Low)

**Korrekte Umsetzungen:**
- Capture-Watchdog (15 s) verhindert dauerhaft gesperrten Capture-Lock
- CameraX Async-Binding-Guard gegen Late-Bind nach Dispose
- `keepScreenOn` korrekt auf alle Lifecycle-Events reagierend
- GPS Start/Stop korrekt auf Screen-Active/Inactive-Events

---

### Accessibility

**Gefundene Risiken:**
- Camera-Preview ohne `contentDescription` (A-01, High)
- Compare-Slider ohne TalkBack-Alternative-Action (A-02, Medium)
- Overlay-Gestures ohne TalkBack-Alternative-Actions (A-03, Medium)
- Dekoratives App-Icon ohne explizit leere `contentDescription` (A-04, Low)
- Feedback-Button ohne `Role.Button` (A-05, Low)

**Korrekte Umsetzungen:**
- Alle `IconButton`-Komponenten mit `contentDescription`
- Settings-Rows semantisch korrekt strukturiert
- Compare-Slider hat Semantics (progressBarRangeInfo, stateDescription)
- Session-Tiles haben contentDescription

---

### Release Build

**Gefundene Risiken:**
- Kein Crash-Reporting (R-01, Medium)
- Minimale ProGuard-Regeln ohne eigene Keep-Rules (R-02, Low)
- Hardcoded Coroutines-Test-Version (R-03, Low)

**Korrekte Umsetzungen:**
- R8 Minify + Resource Shrinking aktiv
- BuildConfig aktiviert für Debug-Log-Gating
- `SourceFile,LineNumberTable` für lesbare Stack-Traces in Release
- versionCode = 1 für ersten Upload korrekt

---

### Play Store

**Gefundene Risiken:**
- Kein Privacy-Policy-URL (PS-01, High — Blocker)
- Data-Safety-Formular nicht ausgefüllt (PS-02, High — Blocker)
- Camera-required-false und Device-Targeting (PS-03, Medium)

**Korrekte Umsetzungen:**
- Minimale Permissions
- Kein INTERNET-Permission = klare Privacy-Position
- Photo Picker statt READ_MEDIA_IMAGES
- Keine exported Content Providers/Services

---

## Umsetzungsblöcke

Die folgenden Blöcke sind so dimensioniert, dass sie einzeln und unabhängig umgesetzt werden können.

---

### Block A — Play-Store-Blocker (vor öffentlichem Upload zwingend)

**Scope:**
- Privacy Policy URL erstellen (externe Webseite oder In-App-Text)
- Privacy Policy URL in About-Screen verlinken
- Play Console: Privacy Policy URL im App-Listing eintragen
- Play Console: Data-Safety-Formular ausfüllen (präziser Standort optional, Kamera, keine Weitergabe, kein Tracking)

**Findings:** P-01, PS-01, PS-02

**Abhängigkeiten:** keine Code-Änderungen zwingend für das Formular selbst; About-Screen-Link ist optional aber empfohlen

---

### Block B — Manifest-Hardening

**Scope:**
- `android.hardware.camera required="true"` evaluieren und ggf. umstellen
- `network_security_config.xml` mit `cleartextTrafficPermitted="false"` erstellen und in Manifest referenzieren
- Unused `xmlns:tools` aus Manifest entfernen

**Findings:** M-01, M-02, M-03

**Abhängigkeiten:** M-01 beeinflusst Play-Store-Geräte-Targeting; Entscheidung benötigt Produkt-Input

---

### Block C — Storage-Backup-Hardening

**Scope:**
- Evaluieren ob `sameview_settings.preferences_pb` vom Backup ausgeschlossen werden soll
- Falls ja: `backup_rules.xml` und `data_extraction_rules.xml` anpassen
- Test: Settings nach Device-Restore-Simulation auf gewünschtes Verhalten prüfen

**Findings:** S-01

**Abhängigkeiten:** keine; isolierte Änderung an XML-Dateien

---

### Block D — FileProvider-Vorbereitung

**Scope:**
- `FileProvider` in Manifest deklarieren
- `file_paths.xml` für `filesDir/sessions/` anlegen
- `SessionStorage` und `SessionScanner` auf `FileProvider.getUriForFile()` umstellen
- `CompareInput` und `ScannedSession` URIs entsprechend umstellen

**Findings:** S-02

**Abhängigkeiten:** Breaking change an der internen URI-Struktur; erfordert sorgfältige Test-Absicherung; sollte vor jeder Share-Feature-Implementierung abgeschlossen sein

---

### Block E — Privacy-Kommunikation

**Scope:**
- Session-Delete-Bestätigungsdialog erweitern: Hinweis, dass das Foto in `Pictures/SameView` erhalten bleibt
- `metadata.json` Reference-URI-Speicherung als intentional dokumentieren (Code-Kommentar oder Docs-Update)

**Findings:** P-04, P-02

**Abhängigkeiten:** kleiner Dialog-Text-Änderung + String-Resource; keine Architekturänderung

---

### Block F — Accessibility Core (Camera Preview)

**Scope:**
- `contentDescription` auf Camera-Preview `AndroidView`-Wrapper setzen
- `contentDescription = ""` auf App-Icon-`ImageView` in AboutScreen setzen (dekorativ)
- `role = Role.Button` auf Feedback-Button in AboutScreen ergänzen

**Findings:** A-01, A-04, A-05

**Abhängigkeiten:** isolierte Compose-Änderungen ohne Architektureinfluss

---

### Block G — Accessibility Extended (Gestures)

**Scope:**
- Compare-Slider: TalkBack-Accessibility-Action zum Verschieben des Dividers ergänzen (z. B. "Move divider left/right" als Custom Actions)
- Overlay-Gestures: TalkBack-Accessibility-Actions für Reset und Opacity-Änderung via `semantics { customActions }` evaluieren und ggf. implementieren

**Findings:** A-02, A-03

**Abhängigkeiten:** Erfordert sorgfältige UX-Entscheidung zur TalkBack-Bedienbarkeit des Kamera-Workflows; komplexer als Block F; separater Scope sinnvoll

---

### Block H — Release Build Robustheit

**Scope:**
- Crash-Reporting evaluieren und ggf. integrieren (Firebase Crashlytics oder Alternative)
- Post-Release-Build-Smoke-Test: ProGuard-Stripping-Verhalten für App-Klassen verifizieren
- `kotlinx-coroutines-test`-Version in `libs.versions.toml` centralisieren

**Findings:** R-01, R-02, R-03

**Abhängigkeiten:** R-01 erfordert Dependency + Privacy-Policy-Erweiterung (wenn Crashlytics, dann Data-Safety-Formular aktualisieren); unabhängig von anderen Blöcken

---

### Block I — Minor Polish

**Scope:**
- `deleteSession()` in `CameraViewModel` bei Fehlschlag `R.string.delete_failed` Snackbar emittieren (Parity mit `deleteSessions()`)
- Session-Storage-Nutzungsindikator in Compare Library evaluieren
- Play-Console: Geräte-Targeting für kameralose Geräte prüfen und ausschließen (falls `required="false"` behalten wird)

**Findings:** LC-01, S-03, PS-03 (Teil)

**Abhängigkeiten:** LC-01 ist eine kleine ViewModel-Änderung mit einer Test-Erweiterung; isoliert umsetzbar

---

## Empfohlene Reihenfolge

1. **Block A** — Play-Store-Blocker zuerst, da ohne Privacy Policy kein öffentlicher Upload möglich
2. **Block B** — Manifest-Hardening früh klären (M-01-Entscheidung beeinflusst Play-Targeting)
3. **Block E** — Privacy-Kommunikation (kleine Änderung, hoher User-Trust-Wert)
4. **Block F** — Accessibility Core (isoliert, geringer Aufwand, hoher Impact)
5. **Block C** — Storage-Backup-Hardening (Entscheidung notwendig, dann kleine Änderung)
6. **Block I** — Minor Polish (kleine Fixes)
7. **Block H** — Release Build Robustheit (Crash-Reporting ist eine Architekturentscheidung)
8. **Block D** — FileProvider-Vorbereitung (vor Share-Feature-Implementierung)
9. **Block G** — Accessibility Extended (komplexer, eigener Sprint)

---

## Findings nach Severity

| Severity | Anzahl | IDs |
|----------|--------|-----|
| Critical | 0 | — |
| High | 5 | M-01, P-01, A-01, PS-01, PS-02 |
| Medium | 8 | M-02, P-02, S-01, S-02, A-02, A-03, R-01, PS-03 |
| Low | 10 | P-03, P-04, S-03, S-04, LC-01, LC-02, A-04, A-05, R-02, R-03, PS-04 |
| Info | 23 | M-03, M-04, M-05, M-06, P-05, P-06, S-05, S-06, S-07, LC-03, LC-04, LC-05, A-06, A-07, A-08, R-04, R-05, R-06, R-07, PS-05, PS-06, PS-07 |
| **Gesamt** | **46** | |

---

## Wichtigste Risiken

1. **Play-Store-Blocker**: Privacy Policy URL und Data-Safety-Formular fehlen (PS-01, PS-02, P-01) — ohne diese ist kein öffentlicher Upload möglich
2. **Accessibility-Lücke Camera-Preview**: TalkBack-Nutzer erhalten für den Kern-Interaction-Bereich keine semantische Beschreibung (A-01)
3. **Camera required="false"**: Kein funktionsfähiger Fallback für kameralose Geräte (M-01)
4. **Session-Delete-Missverständnis**: GPS-EXIF im MediaStore-Foto bleibt nach Session-Delete bestehen — wird nicht kommuniziert (P-04)
5. **Backup-State nach Restore**: Recreation Guidance könnte als ON erscheinen ohne aktive GPS-Permission (S-01)

---

## Bestätigung

**Keine Codeänderungen wurden durchgeführt.**

**Keine Manifest-Änderungen wurden durchgeführt.**

**Keine Permission-Änderungen wurden durchgeführt.**

**Keine Logging-Änderungen wurden durchgeführt.**

**Keine Dokumentation außerhalb dieser neuen Datei wurde verändert.**

Diese Datei wurde neu angelegt: `docs/RELEASE_HARDENING_AUDIT_V1.md`.
