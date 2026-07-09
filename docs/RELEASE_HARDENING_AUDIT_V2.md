# RELEASE_HARDENING_AUDIT_V2.md

## SameView — Pre-Go-Live Audit

**Eyebrow:** Release Audit · Read-only Analyse

Vollständige Prüfung von App-Code, 19 Spec-Dokumenten und Website-Texten (EN/DE) gegen den tatsächlichen Implementierungsstand, vor dem öffentlichen Play-Store-Release.

**Analysedatum:** 2026-07-08
**App-Version:** versionCode 3 · versionName 1.0
**SDK:** minSdk 29 · targetSdk 35 · compileSdk 35
**Vorheriger Audit:** RELEASE_HARDENING_AUDIT_V1.md, 2026-05-29

**Verdict:** 1 Showstopper offen (Privacy-Policy-Verlinkung / Data-Safety-Formular) — Rest ist adressierbar

---

## Inhalt

1. Executive Summary
2. Showstopper
3. Kritische Probleme
4. Privacy / Play Store Findings
5. Website Findings
6. UX Findings
7. Quick Wins
8. Spec vs. Code Abweichungen
9. Offene Fragen

---

## 01 — Executive Summary

Die wichtigsten 18 Punkte, priorisiert. Details und Belege in den folgenden Abschnitten.

1. **[BLOCKER]** Keine Privacy-Policy-Verlinkung in der App (kein Link, keine URL-Ressource) und voraussichtlich unvollständiges Play-Data-Safety-Formular — verhindert den öffentlichen Play-Store-Upload einer App mit CAMERA + ACCESS_FINE_LOCATION.
2. **[POSITIV]** Die Website-Privacy-Policy selbst ist bereits inhaltlich sehr detailliert und korrekt — der Fix ist im Kern nur Verlinkung, kein Neuschreiben.
3. **[HOCH]** `OutOfMemoryError` beim HQ-Bild-Export wird nicht abgefangen (`catch (_: Exception)` fängt keine `Error`-Subklassen) — möglicher App-Crash statt der spezifizierten Fehlermeldung.
4. **[HOCH]** „Clear markers" ist in `ALIGNMENT_POINTS_V1.md` spezifiziert und im ViewModel implementiert, aber nirgends in der UI verdrahtet — bei 5 gesetzten Markern gibt es keinen Weg, in einem Schritt zurückzusetzen.
5. **[MEDIUM]** Privacy-Mode („Strip metadata from originals") entfernt GPS/EXIF bei bestimmten Referenzbildformaten (altes AVIF, unbekannte MIME-Typen) nicht — ohne jede Anzeige dieses Fallback-Zustands in der UI.
6. **[MEDIUM]** `filesDir/branding/` ist entgegen der ausdrücklichen Spec-Behauptung nicht vom Android-Auto-Backup ausgeschlossen.
7. **[MEDIUM]** `sameview_settings`-DataStore ist weiterhin nicht vom Backup ausgeschlossen — GPS-Einstellung kann nach Geräte-Restore inkonsistent mit der tatsächlichen Permission erscheinen (aus Voraudit, unverändert).
8. **[MEDIUM]** Backup-Export mischt privacy-gestrippte und normale Sessions in einem ZIP, ohne jeden Warnhinweis.
9. **[MEDIUM]** „Recreation Guidance" (GPS-Toggle) erklärt sich dem Nutzer erst *nach* dem Antippen über einen reaktiven Dialog, nicht vorher über einen Beschreibungstext.
10. **[MEDIUM]** Video-Export-Dateiname enthält den exakten Aufnahme-Zeitstempel (Datum + Uhrzeit) im Klartext im MediaStore — der Bild-Export vermeidet dieses Muster bewusst aus Datenschutzgründen.
11. **[MEDIUM]** Guide-Tips erfüllen die eigenen Accessibility-Vorgaben (Live-Region, dynamische „Learn more"-Beschreibung) nicht.
12. **[NIEDRIG]** Mehrere tote String-Ressourcen, u. a. ein deutscher String, der fälschlich „Keine KI-generierten Bilder" behauptet — unbenutzt heute, aber eine Stolperfalle, falls reaktiviert.
13. **[NIEDRIG]** Lösch-Bestätigungstext unterscheidet sich zwischen EN und DE inhaltlich, nicht nur sprachlich.
14. **[NIEDRIG]** Walkthrough nutzt Raster-Bilder (WEBP) entgegen der eigenen Spec-Vorgabe „nur Compose-Mockups, keine Screenshots/PNG" — undokumentierte Abweichung.
15. **[POSITIV]** Kernarchitektur weiterhin sauber: kein INTERNET-Permission, kein Tracking, GPS-Datenfluss wurde Datei für Datei nachvollzogen und ist spec-konform, Branding-Pipeline ist nachweislich metadatenfrei, keine Restore-/Import-Angriffsfläche vorhanden.
16. **[POSITIV]** Deutsche Übersetzung ist für alle elf geprüften Feature-Bereiche vollständig — keine fehlenden Strings gefunden (bis auf zwei korrekt als `translatable="false"` markierte URL/E-Mail-Werte).
17. **[POSITIV]** Reference-Marker-Drag-Loupe ist eine über weite Strecken exakte Umsetzung ihrer eigenen (komplexen) Spec — keine Lücken gefunden.
18. **[POSITIV]** M-01 (Kamera als Pflichtfeature) sowie A-04/A-05 (Accessibility im About-Screen) aus dem Voraudit vom 2026-05-29 sind inzwischen nachweislich behoben.
19. **[INFO]** Mehrere Info-/Low-Findings aus dem Voraudit (R-01 Crash-Reporting, R-02/R-03 ProGuard/Gradle, M-02 networkSecurityConfig, A-02/A-03 Slider-/Overlay-Accessibility) wurden in diesem Durchgang nicht erneut geprüft — Status wird als unverändert offen angenommen, siehe Abschnitt 03.

---

## 02 — Showstopper

Echte Go-Live-Blocker für den öffentlichen Play-Store-Release.

### [BLOCKER] Fehlende Privacy-Policy-Verlinkung + voraussichtlich unvollständiges Data-Safety-Formular
*Status: bestätigt*

Google Play verlangt für Apps, die CAMERA und ACCESS_FINE_LOCATION anfragen, eine verlinkbare, öffentlich erreichbare Privacy Policy sowie ein vollständig ausgefülltes Data-Safety-Formular in der Play Console. Beides ist aktuell nicht erfüllt bzw. nicht auffindbar in der App.

**Beleg:** `AboutScreen.kt` enthält nur „Visit website" (`about_website_url` = `https://sameview.app`) und „Send feedback" (mailto). Repo-weite Suche nach `privacy_policy`, `PrivacyPolicy`, `privacy-policy` in `app/src/main` ergibt keine Treffer außer dem unrelated `settings_privacy_title` (bezieht sich auf den Metadaten-Strip-Toggle, nicht auf eine Rechtsseite).

**Einordnung:** Der Fix ist klein — die Website-Privacy-Policy (siehe Abschnitt 05) ist inhaltlich bereits gut. Es fehlt: (1) ein Link/Button im About-Screen auf `sameview.app/privacy`, (2) das Eintragen dieser URL im Play-Console-Listing, (3) das Ausfüllen des Data-Safety-Formulars (Standort: optional, nicht weitergegeben, nicht für Tracking; Kamera: lokal, kein Upload). Ohne diese drei Schritte ist kein Public-Track-Upload möglich — deshalb Showstopper, trotz geringem Umsetzungsaufwand.

---

Keine weiteren echten Blocker gefunden. Kein Crash-on-Launch, keine Manifest-Verstöße, keine offensichtlichen Content-Policy-Verstöße identifiziert. Der einzige gefundene Crash-Risiko-Kandidat (OOM bei HQ-Export, siehe 03) betrifft nicht den Kernablauf Aufnahme → Speichern und wird daher als kritisch, nicht als Showstopper eingestuft.

---

## 03 — Kritische Probleme

Kein Blocker im engeren Sinne, aber vor einem öffentlichen Release dringend empfohlen. Priorisiert.

### [HOCH] OutOfMemoryError beim HQ-Bild-Export nicht abgefangen
*Status: neu*

Der HQ-„Original"-Qualitätspfad im Share-Comparison-Image-Feature dekodiert große Originalbilder. Der Fehlerpfad fängt `catch (_: Exception)`, aber `OutOfMemoryError` ist ein `Error`, keine `Exception` — es propagiert ungefangen und crasht die App, statt die spezifizierte `share_comparison_error_render_failed`-Snackbar zu zeigen (Spec-Anforderung HQ-FD-11: „No silent failure").

**Beleg:** `ShareComparisonViewModel.kt`, `onShare()`-Fehlerbehandlung (Zeilen ~544–548).

**Fix:** `catch (_: Exception)` um einen zusätzlichen `catch (_: OutOfMemoryError)`-Zweig (oder `catch (_: Throwable)`) ergänzen, der denselben Snackbar-Pfad auslöst.

### [HOCH] „Clear markers" spezifiziert, implementiert, aber nicht verdrahtet
*Status: neu*

`ALIGNMENT_POINTS_V1.md §6.2/§6.8/§7.3/§7.4` verlangt einen „Clear markers"-Menüeintrag, der alle Referenzmarker auf einmal entfernt. `CameraViewModel.clearMarkers()` existiert und implementiert das korrekte Zielverhalten — es wird nur von keiner UI-Komponente aufgerufen. Bei einer harten Obergrenze von 5 Markern muss ein Nutzer, der neu beginnen will, jeden Marker einzeln per Long-Press löschen.

**Beleg:** `CameraScreen.kt`, `ReferenceActionStack` (Zeilen ~2372–2491) implementiert Add/Hide/Show/Edit, aber keinen Clear-Eintrag; kein passender String in `strings.xml`.

**Fix:** Menüeintrag in `ReferenceActionStack` ergänzen (sichtbarer und versteckter Marker-Zustand sowie Edit-Modus), verdrahtet auf `viewModel.clearMarkers()`, plus zwei neue String-Ressourcen (EN/DE).

### [MEDIUM] Privacy-Mode-Fallback preist Wirkung an, die er nicht immer erbringt
*Status: neu*

Der Settings-Toggle „Strip metadata from session originals" verspricht in seiner Beschreibung pauschal Entfernung von EXIF/GPS/Kamera-Metadaten. Für nicht dekodierbare Referenzformate (u. a. AVIF auf API 29–30, unbekannte/fehlende MIME-Typen) kopiert der Code die Originaldatei jedoch byte-identisch inklusive vorhandener GPS/EXIF-Daten und markiert dies intern lediglich als `preservation = "not_possible"` in `metadata.json` — ohne dass dieser Zustand irgendwo in der UI angezeigt wird.

**Beleg:** `SessionStorage.kt`, `copyReferenceSourceAsIs()` (~Zeile 1359–1374), erreicht über `writeReferenceSourceOriginalStrippedByMime()` (~1258–1273); `metadata.json`-Feld `originals.referenceSourcePreservation` wird geschrieben (~Zeile 816), aber repo-weit kein UI-Code gefunden, der dieses Feld liest.

**Fix:** Entweder UI-Hinweis ergänzen, wenn ein Fallback aktiv war (z. B. Badge/Hinweistext in Edit Session oder beim Backup), oder Settings-Beschreibung präzisieren („in den meisten Fällen" statt pauschal).

### [MEDIUM] `filesDir/branding/` nicht vom Backup ausgeschlossen — Spec-Behauptung stimmt nicht
*Status: neu*

`SESSION_BRANDING_V1.md §5.3` behauptet explizit, globales Branding sei „consistent with filesDir/sessions/ exclusion" vom Android-Auto-Backup ausgeschlossen. Tatsächlich schließen `backup_rules.xml` und `data_extraction_rules.xml` ausschließlich `path="sessions"` aus — `branding/` als Geschwister-Verzeichnis unter `filesDir` ist nicht abgedeckt.

**Beleg:** `app/src/main/res/xml/backup_rules.xml` Zeile 3; `data_extraction_rules.xml` Zeilen 8 und 11 — beide nur `exclude domain="file" path="sessions"`.

**Fix:** Eine `exclude`-Zeile für `path="branding"` in beiden XML-Dateien ergänzen. Datenschutzrisiko ist gering (Branding-Pipeline ist nachweislich metadatenfrei, s. Abschnitt 04), aber die Spec-Aussage muss entweder stimmen oder korrigiert werden.

### [MEDIUM] Settings-DataStore weiterhin nicht vom Backup ausgeschlossen
*Status: Voraudit S-01 · unverändert*

Nach einem Geräte-Restore kann `recreation_guidance = true` wiederhergestellt werden, obwohl `ACCESS_FINE_LOCATION` auf dem neuen Gerät noch nicht erteilt ist — der GPS-Toggle erscheint als aktiv, die GPS-Aktivierung schlägt intern still fehl.

**Beleg:** Gleiche zwei XML-Dateien wie oben — `sameview_settings.preferences_pb` ist in keiner der beiden Exclude-Listen enthalten.

**Fix:** Produktentscheidung treffen (siehe Offene Fragen), dann ggf. DataStore-Datei ebenfalls ausschließen.

### [MEDIUM] Backup-ZIP mischt Privacy-Status ohne Warnung
*Status: neu, spec-konform aber UX-Lücke*

Ein Mehrfach-Backup aus der Compare Library kann Sessions enthalten, die vor Aktivierung von „Privacy mode" erstellt wurden (voller EXIF/GPS) gemeinsam mit neueren, gestrippten Sessions — in einem ZIP, ohne jede Kennzeichnung. Das entspricht der Spec-Absicht (`SESSION_BACKUP_EXPORT_V1.md`: Backup ist immer „full-fidelity", per Design), ist aber aus Nutzersicht überraschend: Wer „Privacy mode" einschaltet, könnte annehmen, dies schütze auch rückwirkend bereits gesicherte Sessions.

**Fix:** Kein Codefehler, sondern eine Produkt-/UX-Entscheidung — siehe Offene Fragen.

### [MEDIUM] GPS-Toggle erklärt sich erst nach dem Antippen
*Status: neu*

„Recreation guidance" in den Settings zeigt nur das Switch-Label („Show reference location"), keinen permanenten Beschreibungstext. Die Erklärung, dass dies eine Standortabfrage auslöst, erscheint erst reaktiv im Rationale-Dialog nach dem Antippen. Das untergeordnete „Live direction arrow" hat dagegen eine permanente Beschreibungszeile.

**Beleg:** `SettingsScreen.kt`, `RecreationGuidanceRow` (Zeilen ~300–313) vs. `settings_live_direction_arrow_description` (Zeilen ~320–326).

**Fix:** Permanente einzeilige Beschreibung unter dem Toggle ergänzen, analog zum Live-direction-arrow-Muster.

### [MEDIUM] Video-Dateiname enthält exakten Aufnahme-Zeitstempel
*Status: neu*

Exportierte Videos heißen `SameView_<sessionId>_<mode>.mp4`, wobei `sessionId` das Format `YYYY-MM-DD_HH-mm-ss` hat — der exakte Aufnahmezeitpunkt steht also im Klartext im Dateinamen, sichtbar überall dort, wo die Datei landet (Downloads-Ordner, E-Mail-Anhangsname, Chat-Dateiliste). Der Bild-Export vermeidet dieses Muster bewusst und nutzt stattdessen den Export-Zeitpunkt statt des Aufnahme-Zeitpunkts — laut eigener Spec (`SHARE_COMPARISON_IMAGE_V1.md §27`) explizit aus Datenschutzgründen. Die Video-Export-Spec (`VIDEO_EXPORT_V1.md §18.1`) wurde nicht auf dasselbe Muster angehoben.

**Beleg:** `VideoExportPipeline.kt`, `buildDisplayName()` (~Zeilen 202–210); Session-ID-Format bestätigt in `SessionStorage.kt` (~Zeile 120).

**Fix:** Video-Dateinamen analog zum Bild-Export auf einen Export-Zeitstempel statt Session-ID umstellen.

### [MEDIUM] Guide-Tips erfüllen eigene Accessibility-Vorgaben nicht
*Status: neu*

`GUIDE_TIPS_UX_V1.md §25` verlangt `liveRegion = LiveRegionMode.Polite`-Semantics auf der Tip-Card sowie eine dynamische Content-Description auf „Learn more", die das Navigationsziel ansagt. Beides fehlt im Code — TalkBack-Nutzer werden weder proaktiv über einen neuen Tipp informiert noch erfahren sie, wohin „Learn more" führt.

**Beleg:** `GuideTipHost.kt`, Card-Definition (~Zeilen 176–256) ohne `semantics`-Modifier für Live-Region; Learn-More-Action (~225–239) nutzt nur das statische Label.

**Fix:** `liveRegion`-Semantics und dynamische Content-Description ergänzen, oder bewusste Abweichung im Spec-Dokument dokumentieren.

### [NIEDRIG] Voraudit-Findings ohne erneute Prüfung in diesem Durchgang
*Status: nicht erneut verifiziert*

Folgende Punkte aus `RELEASE_HARDENING_AUDIT_V1.md` wurden in diesem Audit-Durchgang nicht gezielt erneut geprüft und daher als unverändert offen übernommen: R-01 (kein Crash-Reporting), R-02 (minimale ProGuard-Keep-Rules), R-03 (hardcodierte Coroutines-Test-Version), M-02 (kein networkSecurityConfig), A-02/A-03 (Compare-Slider und Overlay-Gesten ohne TalkBack-Alternative), LC-01 (Einzel-Delete ohne Fehler-Snackbar), S-03 (keine Speicher-Quota für Sessions). Direkt neu bestätigt wurden dagegen: M-01 (behoben), A-04/A-05 (behoben), P-01/P-02/P-03/P-04 (weiterhin offen), S-01/S-02 (weiterhin offen).

---

## 04 — Privacy / Play Store Findings

Datenschutzbeauftragter- und Play-Reviewer-Perspektive, getrennt von den allgemeinen kritischen Punkten.

**Legende:**
- *bestätigt* = in diesem Audit direkt im Code verifiziert
- *neu* = erstmals in diesem Audit gefunden
- *Voraudit* = aus 2026-05-29 übernommen
- *nicht verifizierbar* = außerhalb des Repos (Play Console, Domain-Status)

### [BLOCKER] P-01 / PS-01 — Keine Privacy-Policy-Verlinkung
*Status: bestätigt*

Siehe Abschnitt 02. Website-Policy existiert und ist inhaltlich gut — es fehlt nur die Verlinkung in der App und in der Play Console.

### [BLOCKER] PS-02 — Play Data-Safety-Formular
*Status: nicht verifizierbar*

Kann nicht aus dem Repository geprüft werden (liegt in der Play Console). Muss vor Go-Live vollständig und korrekt ausgefüllt werden: präziser Standort (optional, nicht weitergegeben, kein Tracking), Kamera (lokale Aufnahme, kein Upload).

### [INFO] P-02 — Vollständige Geräte-URI in metadata.json, jetzt auch in Backup-ZIPs
*Status: Voraudit, jetzt erweitert*

`metadata.json` speichert `reference.sourceUri` als vollen `content://`-URI-String (kann z. B. Google-Photos-Provider-IDs enthalten). Dies ist spec-akzeptiertes Verhalten für maximale Backup-Treue, bedeutet aber: jedes vom Nutzer erstellte Backup-ZIP trägt diesen geräte- und ggf. konto-nahen Bezeichner unverändert mit sich — eine neue Angriffsfläche seit Einführung des Backup-Exports.

**Beleg:** `SessionStorage.kt` Zeile 774; `SessionBackupExporter.kt` Zeile 154 übernimmt `metadata.json` unverändert in jedes ZIP.

### [INFO] P-03 — GPS-Koordinaten in Debug-Logs
*Status: Voraudit, bestätigt unverändert*

Alle gefundenen GPS-Log-Stellen sind korrekt durch `BuildConfig.DEBUG` gegated, kein Leak in Release-Builds. Kein Regressions-Hinweis gefunden.

### [INFO] P-04 — Session-Delete löscht nicht das Galerie-Foto / dessen GPS-EXIF
*Status: Voraudit, weiterhin offen*

Der Lösch-Dialog kommuniziert diese Unterscheidung weiterhin nicht (der geprüfte Dialogtext „This compare will be deleted." erwähnt weder das Galerie-Foto noch GPS).

### [POSITIV] GPS-Datenfluss vollständig nachvollzogen — spec-konform, keine Lecks
*Status: bestätigt*

`reference.jpg` erhält nachweislich nie GPS-EXIF; kein GPS-Schreibpfad berührt Branding-Dateien; Aktivierungsbedingungen (Guidance an + Permission + Referenz-GPS + Screen aktiv) korrekt implementiert.

### [POSITIV] Export-Pipelines (Share Image, Video) sind metadatenfrei by construction
*Status: bestätigt*

Weder `ShareImageRenderer` noch die Video-Encoding-Pipeline schreiben EXIF/GPS/Standortdaten in Ausgabedateien — verifiziert durch Code-Lesung, nicht nur Spec-Vertrauen (kein `ExifInterface`-Schreibaufruf, kein `MediaFormat.KEY_LOCATION`, kein `MediaMuxer.setLocation()`).

### [POSITIV] Branding-Pipeline nachweislich metadatenfrei, kein Import/Restore-Angriffsvektor
*Status: bestätigt*

`BrandingNormalizer` arbeitet ausschließlich auf In-Memory-`Bitmap`-Objekten (strukturell EXIF-frei). Repo-weite Suche nach Zip-Import/Restore-Funktionalität ergab keine Treffer — Backup ist reine Export-Funktion ohne Rückweg, reduziert die Angriffsfläche zusätzlich.

### [INFO] Weitere Voraudit-Punkte ohne Regressionshinweis
*Status: Voraudit*

M-02 (kein networkSecurityConfig), M-06 (ACCESS_MEDIA_LOCATION ggf. erklärungsbedürftig für Reviewer), PS-04 (Domain/E-Mail `support@sameview.app` muss vor Listing aktiv sein — nicht verifizierbar von hier aus), S-02 (interne `file://`-URIs; Risiko bleibt latent, wird aber durch die aktuelle Architektur — Exporte laufen über frisch gerenderte MediaStore-Dateien, nicht über Rohdateizugriff — derzeit nicht ausgelöst).

---

## 05 — Website Findings

Geprüfte Dateien: `en/privacy/_privacy.md`, `de/privacy/_privacy.md`, `en/terms/_terms.md`, `de/terms/_terms.md`, `en/imprint/_imprint.md`, `de/imprint/_imprint.md` (tatsächliche Pfade weichen leicht von den in der Aufgabenstellung genannten ab — Präfix `_`, Unterordner pro Dokumenttyp). Gesamturteil: ungewöhnlich gründlich und faktisch korrekt — keine Widersprüche zur tatsächlichen App-Funktion gefunden. Offene Punkte sind Auslassungen, keine Fehler.

| Datei | Problem | Empfohlene Änderung |
|---|---|---|
| en/privacy/_privacy.md<br>de/privacy/_privacy.md | „Privacy mode" / Metadaten-Strip-Toggle für Session-Originals wird in Abschnitt 6 nicht erwähnt, obwohl die Policy GPS/EXIF sonst sehr detailliert behandelt. | Kurzen Absatz ergänzen: Toggle beschreiben und explizit klarstellen, dass er das Galerie-Foto nicht beeinflusst. |
| en/privacy/_privacy.md<br>de/privacy/_privacy.md | Branding-/Wasserzeichen-Feature (eigenes Logo in Exporten) wird nicht erwähnt. | Ein Satz im Abschnitt zu Sharing/Export ergänzen (Feature ist lokal verarbeitet, metadatenfrei). |
| en/privacy/_privacy.md<br>de/privacy/_privacy.md | In-App-„Send Feedback" (mailto-Intent) wird nicht erwähnt; Abschnitt 10 verweist nur auf die Impressum-Seite. | Eine Zeile ergänzen: Feedback öffnet die eigene E-Mail-App des Nutzers, Inhalt wird erst mit expliziter Sende-Aktion übertragen. |
| en/terms/_terms.md vs. de/terms/_terms.md | DE-Fassung enthält eine zusätzliche Haftungsausschluss-Klausel (Vorsatz/grobe Fahrlässigkeit/Personenschaden), die in EN fehlt — rechtlich sinnvolle Lokalisierung, aber Asymmetrie. | Optional: sinngemäß auch in EN ergänzen, für Symmetrie zwischen den Sprachversionen. |
| en/imprint/_imprint.md<br>de/imprint/_imprint.md | Keine USt-ID im Impressum sichtbar (nur in der statischen .md — E-Mail wird separat per Komponente injiziert). Kann korrekt sein (Kleinunternehmerregelung §19 UStG), aber nicht von hier aus verifizierbar. | Prüfen, ob Kleinunternehmerstatus zutrifft; falls nicht, USt-ID ergänzen. |
| App (nicht Website) — `AboutScreen.kt` | Die inhaltlich gute Privacy-Policy-Seite wird von der App aus aktuell nirgends verlinkt (siehe Showstopper Abschnitt 02). | Link „Privacy Policy" im About-Screen ergänzen, sobald die URL final feststeht. |

### Play-Store-Relevanz

Keiner der Website-Punkte oben stellt für sich genommen einen wahrscheinlichen Play-Review-Blocker dar — die Standort-Offenlegung ist bereits ungewöhnlich konkret (Zweck, Aktivierungsbedingungen, kein Verkauf/keine Weitergabe, nur im Vordergrund), was die Play-Anforderungen an Standort-Transparenz voraussichtlich gut erfüllt. Der eigentliche Blocker bleibt die fehlende Verlinkung (Abschnitt 02), nicht der Inhalt der Policy selbst.

---

## 06 — UX Findings

Priorisiert, unabhängig von den Specs geprüft — Camera, Compare, Library, Settings, Guide, Walkthrough, Edit Session, Share, Video Export.

### [HOCH] „Clear markers" fehlt in der UI

Siehe Abschnitt 03 — hier zusätzlich als reine UX-Sackgasse eingeordnet: 5-Marker-Limit ohne Mehrfach-Reset zwingt zu wiederholtem Long-Press-Löschen.

### [MEDIUM] GPS-Toggle ohne permanente Erklärung

Siehe Abschnitt 03.

### [MEDIUM] Guide-Tip-Accessibility-Lücken

Siehe Abschnitt 03 — TalkBack-Nutzer verpassen neue Kontext-Tipps und wissen nicht, wohin „Learn more" führt.

### [MEDIUM] Compare-Slider und Overlay-Gesten ohne TalkBack-Alternative
*Status: Voraudit A-02/A-03, weiterhin offen*

Reines Touch-/Drag-Modell für Slider-Verschiebung und Overlay-Reposition/Skalierung — keine Custom-Actions als Alternative für Screenreader-Nutzer.

### [NIEDRIG] EN/DE-Lösch-Dialog unterscheiden sich inhaltlich
*Status: neu*

EN: „This compare will be deleted." DE: „Diese Aktion kann nicht rückgängig gemacht werden." — keine Übersetzung derselben Aussage, sondern zwei unterschiedliche Inhalte. Deutsche Nutzer erhalten einen Unwiderruflichkeits-Hinweis, englische nicht.

**Beleg:** `values/strings.xml:73` vs. `values-de/strings.xml:66`, `compare_screen_delete_dialog_message`.

### [NIEDRIG] Toter String `markers_outside_image` deutet auf fallengelassenen Hinweis hin
*Status: neu*

String ist definiert und in beiden Sprachen übersetzt, wird aber nirgends verwendet. Der tatsächliche Code behandelt Long-Press außerhalb des Referenzbilds korrekt als No-Op (spec-konform), aber die Existenz des ungenutzten Strings legt nahe, dass ursprünglich ein sichtbarer Hinweis geplant war.

**Fix:** Entweder verdrahten oder als toten String entfernen.

### [NIEDRIG] Walkthrough nutzt Raster-Bilder entgegen eigener Spec
*Status: neu, undokumentiert*

`FIRST_RUN_WALKTHROUGH_GUIDE_V1.md §9` verbietet ausdrücklich Screenshots/PNG-Illustrationen zugunsten reiner Compose-Mockups. Der Code rendert stattdessen WEBP-Bilder aus `drawable-nodpi/walkthrough_step{1-4}.webp`. Keine dokumentierte Produktentscheidung dazu gefunden (anders als bei vergleichbaren Abweichungen in `RESPONSIVE_LAYOUT_SYSTEM_V1.md`, die per Addendum nachgezogen wurden).

**Fix:** Mit Produktverantwortlichem klären, ob dies eine bewusste, spätere Entscheidung war (siehe Offene Fragen), dann Spec nachziehen.

### [NIEDRIG] Tote Strings aus abgelöstem „Edit Title"-Dialog
*Status: neu*

Sechs Strings (`compare_screen_edit_title*`, `compare_screen_remove_title`) sind vollständig in EN und DE übersetzt, aber seit der Ablösung durch „Edit Session" nirgends mehr referenziert. Reine Aufräumschuld.

### [NIEDRIG] Toter String mit widersprüchlichem DE-Inhalt
*Status: neu*

`about_no_account_required`: EN = „Your memories.", DE = „Keine KI-generierten Bilder." (deutlich andere Aussage). Aktuell unbenutzt, aber die App macht an keiner anderen Stelle KI-bezogene Aussagen — eine Stolperfalle, sollte der String je reaktiviert werden.

### [NIEDRIG] Share-Comparison-Screen-Label weicht von Spec-Wortlaut ab
*Status: neu, kosmetisch*

UI zeigt „Extras" (`share_comparison_extras_label`), Spec verlangt „Information" (`share_comparison_info_label` — dieser Key existiert im Code gar nicht). Vermutlich ein nach dem Branding-V2-Rework nicht nachgezogener Spec-Text, keine funktionale Auswirkung.

### [NIEDRIG] Video-Dateiname kollidiert bei wiederholtem Export
*Status: neu*

Gleicher Session/Modus-Export erzeugt denselben angeforderten Dateinamen; Android dedupliziert automatisch mit „(1)"-Suffix. Kein Datenverlust, aber vermeidbare Unschärfe — der Bild-Export löst dies bereits über einen Export-Zeitstempel.

### Positiv verifiziert (keine Maßnahme nötig)

- Favoriten-Empty-State ist korrekt vom „keine Sessions überhaupt"-Zustand unterschieden, mit eigenem Icon/Titel/Text.
- Walkthrough ist nicht endgültig einmalig — „Show tips again" und „Show walkthrough again" sind über den Guide-Screen jederzeit erreichbar, ohne den Abschluss-Status zurückzusetzen.
- Reference-Marker-Drag-Loupe (Lupe beim Verschieben von Markern) entspricht ihrer eigenen, sehr detaillierten Spec praktisch vollständig — Clamping, Skalierung, Button-Kollisionsvermeidung, Bitmap-Recycling.
- Deutsche Übersetzung ist für alle geprüften Bereiche (Camera, Compare, Session-Metadata-Editor, Guide, Walkthrough, Settings, Favorites) vollständig; kein hardcodierter englischer UI-Text gefunden.
- Share Sheet (Bild wie Video) öffnet sich ausschließlich nach explizitem Nutzer-Tap, nie automatisch.
- Video-Export-Abbruch räumt MediaStore-Pending-Einträge und Bitmaps zuverlässig auf, auch bei Coroutine-Cancellation.

---

## 07 — Quick Wins

Kleine Änderungen mit überproportional hoher Wirkung vor dem Release.

1. **Privacy-Policy-Link im About-Screen ergänzen** (`sameview.app/privacy`) — löst den Kern des Showstoppers.
2. **„Clear markers" verdrahten** — ViewModel-Methode existiert bereits, es fehlt nur ein UI-Aufrufer plus zwei Strings.
3. **`catch (_: OutOfMemoryError)` in `ShareComparisonViewModel.onShare()` ergänzen** — verhindert einen konkreten Crash-Pfad mit einer Zeile.
4. **Tote Strings entfernen**: `compare_screen_edit_title*`, `markers_outside_image`, `about_no_account_required`.
5. **Permanente Beschreibungszeile unter „Recreation Guidance"** ergänzen, analog zum bereits vorhandenen Muster bei „Live direction arrow".
6. **`filesDir/branding/` in `backup_rules.xml` und `data_extraction_rules.xml` aufnehmen** — Ein-Zeilen-Fix je Datei, macht die Spec-Aussage wieder wahr.
7. **EN/DE-Lösch-Dialogtext angleichen** — entweder DE-Warnung auch in EN übernehmen oder bewusst als Differenz dokumentieren.
8. **Drei kurze Sätze in beiden Privacy-Policy-Sprachversionen ergänzen**: Privacy-Mode-Toggle, Branding-Feature, Feedback-Mailto.

---

## 08 — Spec vs. Code Abweichungen

Alle in diesem Audit gefundenen Abweichungen zwischen Spec-Dokumenten und tatsächlichem Code.

| Spec-Dokument | Abweichung |
|---|---|
| ALIGNMENT_POINTS_V1.md §6.2 | „Clear markers"-Menüeintrag gefordert, ViewModel-seitig vorhanden, UI-seitig nicht implementiert. |
| SESSION_BRANDING_V1.md §5.3 | Behauptet Backup-Ausschluss von `filesDir/branding/` — trifft im aktuellen Code nicht zu. |
| SESSION_ORIGINALS_PRIVACY_V1.md | Verspricht pauschale Metadaten-Entfernung; Code hat einen stillen „not_possible"-Fallback für bestimmte Referenzformate, der Metadaten unverändert lässt. |
| SHARE_COMPARISON_IMAGE_V1.md §15.3 | Spezifiziert String-Key `share_comparison_info_label` („Information"); Code nutzt `share_comparison_extras_label` („Extras") — Key aus der Spec existiert im Code nicht. |
| VIDEO_EXPORT_V1.md §18.1 vs. SHARE_COMPARISON_IMAGE_V1.md §27 | Bild-Export-Spec vermeidet Session-ID-basierte Dateinamen bewusst aus Datenschutzgründen; Video-Export-Spec schreibt weiterhin genau dieses Muster vor — die beiden Spec-Dokumente widersprechen sich, und der Code folgt der (aus Datenschutzsicht schwächeren) Video-Spec. |
| FIRST_RUN_WALKTHROUGH_GUIDE_V1.md §9 | Verbietet Raster-Illustrationen/Screenshots zugunsten reiner Compose-Mockups; Code verwendet WEBP-Bilddateien ohne dokumentiertes Addendum. |
| GUIDE_TIPS_UX_V1.md §25 | Fordert Live-Region-Semantics und dynamische Learn-More-Beschreibung; im Code nicht implementiert. |
| SESSION_METADATA_EDITOR_V1.md §4/§15 | Dokumentiert korrekt die Ablösung von „Edit Title" durch „Edit Session" — der Code hat diese Migration funktional vollzogen, aber die alten Strings nicht entfernt (Doku ist hier korrekt, Code hat Restschulden). |
| RELEASE_HARDENING_AUDIT_V1.md R-04 | Nennt versionCode = 1; aktueller Stand ist versionCode = 3. Kein Code-Fehler, das Voraudit-Dokument selbst ist veraltet und sollte bei Gelegenheit aktualisiert werden. |

---

## 09 — Offene Fragen an den Produktverantwortlichen

Fragen, die vor dem Go-Live eine bewusste Entscheidung brauchen — keine davon ist aus dem Code allein beantwortbar.

1. **Ist die Privacy-Policy-URL bereits final, und sind Domain/E-Mail aktiv?**
   Ist `sameview.app/privacy` die vorgesehene Play-Console-URL? Sind `sameview.app` und `support@sameview.app` bereits live/erreichbar? Beides ist von hier aus nicht verifizierbar.

2. **Ist das Play-Store Data-Safety-Formular bereits ausgefüllt?**
   Liegt außerhalb des Repositorys — Status unbekannt, muss vor Go-Live bestätigt werden.

3. **War der Wechsel von Compose-Mockups zu WEBP-Bildern im Walkthrough eine bewusste Entscheidung?**
   Falls ja: Spec entsprechend aktualisieren. Falls nein: zu klären, ob ein Rückbau gewünscht ist.

4. **Ist der stille Privacy-Mode-Fallback für bestimmte Referenzformate ein akzeptiertes Risiko?**
   Falls nicht: Soll dies dem Nutzer angezeigt werden, oder soll der Import in diesem Fall abgelehnt werden?

5. **Soll ein Warnhinweis ergänzt werden, wenn ein Backup-ZIP gemischte Privacy-Stati enthält?**
   Aktuell rein spec-konformes „full-fidelity always"-Verhalten ohne jede Nutzer-Kommunikation.

6. **Ist Crash-Reporting (z. B. Firebase Crashlytics) für den öffentlichen Release weiterhin geplant?**
   Aus dem Voraudit als offener Punkt R-01 übernommen; hier nicht erneut technisch geprüft, aber als Produktentscheidung mit Datenschutz-Trade-off relevant für einen Public-Release.

7. **Gilt für den Impressum-Betreiber die Kleinunternehmerregelung (§19 UStG)?**
   Falls nicht, fehlt eine USt-ID im Impressum. Nicht aus dem Code/Repo verifizierbar.

8. **War „Clear markers" eine bewusste Scope-Kürzung oder ein übersehener Implementierungsrest?**
   ViewModel-Methode ist fertig implementiert und ungenutzt — wirkt eher nach einer vergessenen letzten UI-Verdrahtung als nach einer Produktentscheidung.

9. **Soll der DE-Lösch-Dialogtext („kann nicht rückgängig gemacht werden") auch ins Englische übernommen werden?**
   Oder war die zusätzliche Warnung in der deutschen Fassung beabsichtigt und soll dort bleiben?

---

## Colophon

SameView Release Audit · Read-only Analyse, keine Code-, Manifest- oder Dokumentationsänderungen vorgenommen · Basis: App-Code, 19 Spec-Dokumente in `docs/`, Website-Texte EN/DE, Voraudit `RELEASE_HARDENING_AUDIT_V1.md` (2026-05-29)
