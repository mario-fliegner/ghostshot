GPS Recreation — Finaler Implementierungsplan: Analyse

Fortschritt:
Block 1 — metadata.json v3 + SessionScanner-Kompatibilität  ✅ DONE (2026-05-27)
Block 2 — Reference GPS EXIF Extraction                      ✅ DONE (2026-05-27)
Block 3 — Settings + Permission-Grundlage                    ✅ DONE (2026-05-27)
  Unit Tests: testDebugUnitTest — BUILD SUCCESSFUL (190 Tests grün)
  Instrumentation: SettingsRepositoryTest 17/17 grün, SettingsScreenTest 20/20 grün
  Hinweis: runCurrent() im ViewModel-Test erforderlich (SharedFlow-Collector muss vor emit subscribed sein)
Block 4 — LocationProvider + Lifecycle                       ✅ DONE (2026-05-27)
Block 5 — Guidance State Computation                         ✅ DONE (2026-05-27)
Block 6 — Guidance Chip UI                                   ✅ DONE (2026-05-27)
  Unit Tests: testDebugUnitTest — BUILD SUCCESSFUL (243 Tests grün)
  Instrumentation: CameraControlsOverlayTest — kein Device verbunden, Compile erfolgreich
  Neue Datei: GpsGuidanceChip.kt; 5 neue Chip-Tests in CameraControlsOverlayTest
Block 6 Zusatzfix/Hardening                                  ✅ DONE (2026-05-28)
  - HEIC-GPS über SAF verified: EXIF read: hasGps=true lat=45… lon=12…
  - Photo Picker (media URI) kann GPS redaktieren → ACCESS_MEDIA_LOCATION + setRequireOriginal()
  - setRequireOriginal() nur für authority=="media" — SAF/DocumentProvider-URIs direkt
  - SAF-URI darf nicht mit setRequireOriginal geöffnet werden (SecurityException-Schutz)
  - Debug-SAF-Button aus CameraScreen.kt entfernt
  - Diagnose-Probes (SameView.GPS.Diag) aus ReferenceImageMetadataReader.kt entfernt
  - Kompaktes EXIF-Read-Log (SameView.GPS) bewusst behalten
  - updateGpsActivation-Log (SameView.GPS, nur Booleans) bewusst behalten
  - CameraViewModelTest: 4 neue gpsGuidanceState_remainsHidden_when*-Tests
  - Permission-Inkonsistenz dokumentiert: CameraViewModel prüft nur ACCESS_FINE_LOCATION
    (korrekt: LocationManager braucht nur ACCESS_FINE_LOCATION; ACCESS_MEDIA_LOCATION
    wird in Settings gesichert, wirkt indirekt über referenceHasGps())
  - Offene Entscheidung: SAF-Original-Import-UX für spätere Blöcke (kein jetziger Scope)
Block 7 — Capture GPS Freeze + EXIF Writing                  ⬜ offen
Block 8 — Test-Hardening + Release-Vorbereitung              ⬜ offen

1. Kurzfazit: Ist die Implementierung startklar?
Ja — mit zwei konkreten Vorbedingungen die vor Block 1 festzulegen sind.

Die Codebasis ist solide vorbereitet. SessionStorage, SessionScanner, SettingsRepository, CameraViewModel und ReferenceImageMetadataReader folgen alle konsistenten, erweiterbaren Mustern. Kein bestehender Code muss umgebaut werden — alle GPS-Änderungen sind additiv.

Die zwei offenen Punkte (Details unten in Abschnitt 6) sind keine Architekturfragen, sondern Präzisierungen.

2. Größte Risiken
Risiko 1 — GPS-Koordinaten-Format für ExifInterface (mittel):
ExifInterface.setLatLong(lat, lon) existiert erst ab API 33. MinSdk ist 29. Koordinaten müssen manuell als DMS-Rational-String konvertiert werden (setAttribute(TAG_GPS_LATITUDE, "48/1,7/1,24/1000")). Diese Konvertierung ist fehleranfällig und braucht zwingend Unit-Tests mit Randfällen (Südpol, Nullmeridian, Negativwerte).

Risiko 2 — GPS-EXIF-Preservation in reference-original.jpg (mittel):
Die Bitmap-Decode/Encode-Schleife in writeReferenceOriginalAndReference() löscht alle EXIF-Daten. GPS-Preservation bedeutet: das Original-URI mit ExifInterface öffnen, die Roh-GPS-Attribut-Strings lesen, und nach dem Re-Encode in die reference-original.jpg schreiben. Das erfordert ein zusätzliches openInputStream() auf die referenceImageUri während der Session-Save-Pipeline. Fehler hier dürfen den Session-Save nie blockieren.

Risiko 3 — LocationManager Threading (mittel):
LocationManager.requestLocationUpdates() auf Android 29 erfordert einen Looper-Thread oder Main-Thread. Ein direkter Aufruf aus einem IO-Dispatcher würde crashen. LocationListener-Callbacks kommen auf dem registrierten Thread — müssen korrekt auf den Main-Looper gebunden werden.

Risiko 4 — SessionScanner Version-Check (niedrig, aber blocker):
Die aktuelle Prüfung version != EXPECTED_VERSION (Zeile 76 in SessionScanner.kt) würde alle v3-Sessions unsichtbar machen, wenn die Scanner-Änderung nicht als allererstes kommt. Reihenfolge ist entscheidend.

Risiko 5 — Guidance Chip vs. Landscape Side-Rail (niedrig):
In Portrait ist Alignment.TopCenter frei. In Landscape liegen History/Overflow-Actions im Side-Rail. Position muss konkret im CameraScreen-Layout-Baum getestet werden, da die Landscape-Struktur komplexer ist (CameraLandscapeTopActions + frameLeft-Parameter).

3. Empfohlene finale Block-Reihenfolge

Block 1  →  metadata.json v3 + SessionScanner-Kompatibilität  ✅ DONE
Block 2  →  Reference GPS EXIF Extraction
Block 3  →  Settings + Permission-Grundlage
Block 4  →  LocationProvider + Lifecycle
Block 5  →  Guidance State Computation
Block 6  →  Guidance Chip UI
Block 7  →  Capture GPS Freeze + EXIF Writing
Block 8  →  Test-Hardening + Release-Vorbereitung
Zur Reihenfolge-Frage EXIF Writing vor oder nach Guidance UI
Empfehlung: Guidance UI (Block 6) vor EXIF Writing (Block 7).

Begründung: Wenn Block 7 (EXIF Writing) vor Block 6 (Guidance UI) implementiert und deployed würde, befänden wir uns in einem Zwischenzustand, in dem GPS-Daten in Fotos geschrieben werden, ohne dass der Nutzer je eine sichtbare GPS-Guidance gesehen hätte. Das widerspricht dem Opt-in-Modell: der Nutzer hat "Recreation Guidance" aktiviert, sieht aber keine Guidance — nur unsichtbare Metadaten. Privacy-seitig und UX-seitig ist es sauberer, erst die sichtbare Funktionalität zu liefern, bevor GPS in Captures persistiert wird. Außerdem ist Block 7 technisch von Block 4 (LocationProvider) und Block 2 (Reference GPS Extraction) abhängig — diese sind ohnehin vor Block 6 abgeschlossen, sodass die Reihenfolge keine Abhängigkeiten verletzt.

Die aktuelle Reihenfolge (User-Vorschlag: EXIF vor Guidance) würde nur dann bevorzugt werden, wenn GPS-Metadaten-Korrektheit als wichtiger bewertet würde als das nutzerseitige Feedback. In SameView gilt das Gegenteil.

Abhängigkeitsgraph

Block 1  ──────────────────────────────┐
Block 2  ──────────────────────────────┤
Block 3  ──────────────────────────────┤
Block 4  ← benötigt Block 3            ├──→  Block 5 ← benötigt Block 2, 3, 4
Block 5  ← benötigt Block 2, 3, 4     ├──→  Block 6 ← benötigt Block 5
Block 6  ← benötigt Block 5           ├──→  Block 7 ← benötigt Block 1, 2, 3, 4
Block 7  ← benötigt Block 1,2,3,4     └──→  Block 8 ← benötigt alle
Blocks 1, 2, 3 sind voneinander unabhängig und könnten theoretisch parallel entwickelt werden. In der Praxis: sequentiell, um Konflikte in der selben Datei (CameraViewModel, ReferenceImageMetadata) zu vermeiden.

4. Detailplan pro Block
Block 1 — metadata.json v3 + SessionScanner-Kompatibilität  ✅ DONE
Status: Implementiert und getestet — 2026-05-27
Commit: ausstehend
Unit Tests: testDebugUnitTest — BUILD SUCCESSFUL
Instrumentation: SessionScannerTest 28/28 grün, SessionStorageMetadataTest 27/27 grün

Ziel: Version auf 3 erhöhen, Scanner für v2 + v3 aktualisieren, den semantisch leeren flachen location-Block entfernen. Keine GPS-Daten, keine UI, keine Permissions — reines Schema-Plumbing.

Warum zuerst: Wenn v3-Sessions vor dem Scanner-Update auf Disk geschrieben würden, wären sie unsichtbar in der Library. Die Scanner-Änderung muss als erstes kommen, bevor irgend etwas v3 schreibt. Das ist der einzige harte Sequenzierungsgrund.

Betroffene Dateien:

SessionStorage.kt — Zeile 47: METADATA_VERSION = 2 → 3; writeMetadata(): location-Block (Zeilen 268–274) entfernen; captureLocation und referenceLocation noch nicht schreiben (keine GPS-Inputs vorhanden)
SessionScanner.kt — Zeile 25: EXPECTED_VERSION = 2 → private val SUPPORTED_VERSIONS = setOf(2, 3); Zeile 76: version != EXPECTED_VERSION → version !in SUPPORTED_VERSIONS
SessionScannerTest.kt — wrongVersion_isIgnored weiterhin mit version=1 (bleibt korrekt, da 1 nicht in {2,3}); neuer Test: v3_withoutGpsFields_isAccepted (version=3, keine GPS-Felder → Session valide)
SessionStorageMetadataTest.kt — Versionsnummer-Assertion von 2 auf 3 aktualisieren; prüfen dass kein location-Key im geschriebenen JSON vorhanden ist
Konkrete Änderungen:

In SessionScanner.kt:


// vorher:
private const val EXPECTED_VERSION = 2
...
if (version != EXPECTED_VERSION) { return null }

// nachher:
private val SUPPORTED_VERSIONS = setOf(2, 3)
...
if (version !in SUPPORTED_VERSIONS) { return null }
In SessionStorage.writeMetadata(): Den gesamten location-Block (put("location", JSONObject().apply {...})) ersatzlos entfernen. Kein Placeholder-Eintrag.

Explizite Nicht-Ziele: Keine GPS-Felder schreiben. Keine CaptureSessionSnapshot-Änderungen. Keine ReferenceImageMetadata-Änderungen. Keine Manifest-Änderungen. Keine UI-Änderungen.

Risiken: Niedrig. Der metadataExtraFields_areIgnored-Test in SessionScannerTest bestätigt bereits, dass extra Felder (inklusive dem location-Block) sauber ignoriert werden — der Test enthält explizit put("location", JSONObject()...) als Testfall und erwartet, dass die Session valide bleibt. Bestehende v2-Sessions mit dem alten location-Block bleiben durch den setOf(2, 3)-Check vollständig valide und lesbar.

Betroffene Tests:

SessionScannerTest.wrongVersion_isIgnored — weiterhin grün (version=1 ∉ {2,3})
SessionStorageMetadataTest — Versions-Assertion muss von 2 auf 3 aktualisiert werden; location-Block-Prüfung entfernen falls vorhanden
Neue Tests:

SessionScannerTest.v3_withoutGpsFields_isAccepted — Version 3, keine GPS-Felder → Session wird gescannt
SessionScannerTest.v3_withCaptureLocation_isAccepted — Version 3 mit captureLocation-Objekt → Session valide (Scanner ignoriert GPS-Block)
SessionScannerTest.v4_isRejected — Version 4 → Session wird verworfen
SessionStorageMetadataTest.writtenJson_hasVersion3 — geschriebene JSON hat version: 3
SessionStorageMetadataTest.writtenJson_hasNoLocationBlock — kein location-Key
Gradle-Kommandos:


./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.storage.SessionScannerTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.storage.SessionStorageMetadataTest
Real-Device-Validation: Nein.

Play-Store-/Privacy-Relevanz: Nein.

Commit-Fähigkeit: Nach diesem Block: v3-Sessions werden geschrieben und gescannt. v2-Sessions bleiben vollständig lesbar. Kein GPS-Inhalt, kein UI-Effekt. Sauber abgeschlossen.

Block 2 — Reference GPS EXIF Extraction  ✅ DONE
Status: Implementiert und getestet — 2026-05-27
Commit: ausstehend
Unit Tests: testDebugUnitTest — BUILD SUCCESSFUL
Instrumentation: ReferenceImageMetadataReaderTest 8/8 grün (4 bestehende + 4 neue GPS-Tests)
Hinweis: ExifInterface.getLatLong(FloatArray) verwendet statt no-arg-Variante (erst ab API 34)

Ziel: GPS-Koordinaten passiv aus dem EXIF des Referenzbildes lesen. Keine Permission benötigt. Fehlende GPS-Daten sind expliziter Normalzustand.

Warum an dieser Stelle: Unabhängig von Settings, Permission, LocationProvider. Kann parallel zu Block 3 entwickelt, aber vorsichtshalber sequentiell gehalten werden um Konflikte in CameraViewModel.kt/ReferenceImageMetadata zu vermeiden. Muss vor Block 5 (Guidance Computation) kommen, weil referenceHasGps eine der vier GPS-Aktivierungsbedingungen ist.

Betroffene Dateien:

ReferenceImageMetadataReader.kt — GPS-EXIF-Lesen ergänzen
CameraViewModel.kt — ReferenceImageMetadata um drei neue optionale Felder erweitern (default null)
ReferenceImageMetadataReaderTest.kt — neue GPS-Tests
Test-Assets: Test-JPEG mit eingebetteten GPS-EXIF-Daten erforderlich
Konkrete Änderungen:

ReferenceImageMetadata (in CameraViewModel.kt, Zeile 59):


data class ReferenceImageMetadata(
    val rawWidth: Int,
    val rawHeight: Int,
    val orientedWidth: Int,
    val orientedHeight: Int,
    val exifOrientation: Int?,
    val gpsLatitude: Double? = null,   // neu
    val gpsLongitude: Double? = null,  // neu
    val gpsAltitude: Double? = null    // neu
)
ReferenceImageMetadataReader.read(): dritter openInputStream()-Call nach den bestehenden zwei Calls für bounds und orientation. Liest mit ExifInterface(stream).getLatLong() — gibt FloatArray? zurück, Array[0] = Latitude, Array[1] = Longitude. Altitude separat via ExifInterface.getAltitude(defaultValue). Fehler → alle drei Felder bleiben null, kein throw.

Warum drei separate InputStream-Öffnungen: ExifInterface auf einem InputStream liest das EXIF in einem Pass. Bounds und EXIF-Orientation werden bereits in zwei separaten Passes gelesen (bestehender Code). GPS kommt als dritter Pass, konsistent mit dem bestehenden Muster. Alternativ: Orientation und GPS in einem Pass kombinieren — das würde den Reader leicht refaktorieren, was außerhalb des Scope liegt. Drei Passes ist die minimale, regressionssichere Variante.

Explizite Nicht-Ziele: Keine UI-Anzeige von GPS-Koordinaten. Kein Warning wenn GPS fehlt. Keine Änderungen an CameraUiState über referenceImageMetadata hinaus (das Feld existiert bereits). Kein Schreiben in metadata.json (kommt Block 7).

Risiken:

ExifInterface.getLatLong() gibt für Social-Media-Bilder ohne GPS zuverlässig null zurück — kein Crash-Risiko
Der URI für das Referenzbild kann eine content://-URI sein (Photo Picker) — openInputStream() via ContentResolver ist bereits der bestehende Ansatz in der Reader-Implementierung, funktioniert sauber
Drei InputStream-Öffnungen bei einem Photo-Picker-URI: Android erlaubt wiederholtes openInputStream() auf content://-URIs, allerdings ohne Garantie auf alle Content-Providers. Bei Failure → GPS-Felder bleiben null, kein Crash
Betroffene Tests:

ReferenceImageMetadataReaderTest — bestehende Tests prüfen Orientation; GPS-Felder sind default null, alle Tests weiterhin grün
Alle Tests die buildTestSnapshot() mit ReferenceImageMetadata aufrufen — Kotlin-Default null hält alle bestehenden Aufrufer kompatibel
Neue Tests:

ReferenceImageMetadataReaderTest.read_imageWithGpsExif_returnsCoordinates — Test-JPEG mit bekannten GPS-Koordinaten → exakt diese Werte zurück
ReferenceImageMetadataReaderTest.read_imageWithoutGpsExif_gpsFieldsAreNull — normales JPEG ohne GPS → gpsLatitude == null
ReferenceImageMetadataReaderTest.read_imageFromSocialMedia_gpsFieldsAreNull — JPEG mit gestripptem EXIF → null, kein Crash
ReferenceImageMetadataReaderTest.read_screenshotWithNoExif_gpsFieldsAreNull
Gradle-Kommandos:


./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.ReferenceImageMetadataReaderTest
Real-Device-Validation: Nein (InputStream-basierter Test ausreichend).

Play-Store-/Privacy-Relevanz: Nein — passives EXIF-Lesen benötigt keine Permission und erzeugt keine Play-Store-Pflicht.

Commit-Fähigkeit: GPS-Koordinaten werden aus Referenzbildern gelesen und in referenceImageMetadata gehalten. Kein UI-Effekt, keine Permission. Vollständig getestet und reversibel.

Block 3 — Settings + Permission-Grundlage
Ziel: DataStore-Key recreation_guidance anlegen (Default: false), SettingsScreen um Kategorie 4 mit Toggle erweitern, ACCESS_FINE_LOCATION ins Manifest, Permission-Request-Flow implementieren (Rationale-Dialog → System-Dialog → Permanent-Denial-Handling).

Warum an dieser Stelle: Muss vor LocationProvider (Block 4) kommen, da der Provider die Setting-State benötigt. Kann nach Block 2 kommen (keine Abhängigkeit von GPS-Reading). Manifest-Änderung mit ACCESS_FINE_LOCATION gehört genau in diesen Block — nicht früher (keine Permission ohne Feature).

Betroffene Dateien:

app/src/main/AndroidManifest.xml
SettingsRepository.kt — neuer Key + Flow + Setter
SettingsViewModel.kt — neues StateFlow + Handler; Permission-Request-Trigger-Logik
SettingsScreen.kt — neue Kategorie 4, Toggle, Rationale-Dialog, Permanent-Denial-Hinweis
app/src/main/res/values/strings.xml — neue Strings EN
app/src/main/res/values-de/strings.xml — neue Strings DE
Konkrete Änderungen:

SettingsRepository: Folgt exakt dem bestehenden Muster:


private object Keys {
    // ... bestehende Keys ...
    val RECREATION_GUIDANCE = booleanPreferencesKey("recreation_guidance")
}
val recreationGuidance: Flow<Boolean> = preferences.map { it[Keys.RECREATION_GUIDANCE] ?: false }
suspend fun setRecreationGuidance(enabled: Boolean) { dataStore.edit { it[Keys.RECREATION_GUIDANCE] = enabled } }
SettingsViewModel: Neues recreationGuidance: StateFlow<Boolean> mit stateIn(..., false). Handler onRecreationGuidanceChanged(enabled: Boolean): wenn enabled == true und Permission nicht granted → requestLocationPermission()-Event emittieren. SettingsScreen abonniert diesen Event und triggert ActivityResultLauncher.

Permission-Flow exakt:

User tappt Toggle → ON
ViewModel prüft: ContextCompat.checkSelfPermission(ACCESS_FINE_LOCATION) != GRANTED
Falls nicht granted → ViewModel emittiert RequestLocationPermission-Event
SettingsScreen empfängt Event → zeigt Rationale-Dialog: "SameView uses your location to help you find where the original photo was taken. Your location is never shared or uploaded."
User bestätigt Rationale → permissionLauncher.launch(ACCESS_FINE_LOCATION)
Permission granted → Toggle bleibt ON, Setting wird gespeichert
Permission denied (abbrechen) → Toggle geht zurück auf OFF, Setting bleibt OFF
Permission permanent denied: shouldShowRequestPermissionRationale == false + nicht granted → Toggle bleibt OFF; Inline-Hinweis unter Toggle: "Location access required. Grant access in system Settings." (nicht aggressiv, kleiner Text)
Wann wird Permission NICHT angefragt:

App-Start
CameraScreen-Öffnung
Referenzbild-Laden
Kein Referenzbild hat GPS-Daten
Beim Aktivieren des Settings wenn Permission bereits granted
Explizite Nicht-Ziele: Keine Permission in CameraScreen-Pfad. Keine BACKGROUND_LOCATION. Kein GPS-Update auf CameraScreen ohne Block 4. Kein UI-Chip ohne Block 6.

Risiken:

ActivityResultLauncher muss in SettingsScreen registriert werden (nicht im ViewModel — Android-Anforderung). Der Trigger-Event vom ViewModel zum Screen muss als SharedFlow/UiEvent implementiert werden.
Permanent-Denial-Erkennung: auf Android 11+ ist shouldShowRequestPermissionRationale nach permanenter Ablehnung false. Aber auch beim ersten Request-Abbruch kann dieses Flag false sein (je nach Android-Version). Zuverlässigere Methode: Tracking eines "hat die App schon einmal Permission angefragt?"-Flags in DataStore, kombiniert mit dem aktuellen Berechtigungsstatus.
SettingsScreen bekommt onBack-Parameter — der Permission-Launcher muss per rememberLauncherForActivityResult im Composable registriert werden
Betroffene Tests:

SettingsRepositoryTest — ergänzen
SettingsViewModelTest — ergänzen
SettingsScreenTest — ergänzen
Neue Tests:

SettingsRepositoryTest.recreationGuidance_defaultIsFalse
SettingsRepositoryTest.recreationGuidance_togglePersists
SettingsViewModelTest.onRecreationGuidanceOn_withoutPermission_emitsRequestPermissionEvent
SettingsViewModelTest.onRecreationGuidanceOn_withPermissionGranted_savesSettingDirectly
SettingsScreenTest.recreationGuidanceToggle_isVisible_inCategory4
SettingsScreenTest.recreationGuidanceToggle_defaultIsOff
Gradle-Kommandos:


./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.settings.SettingsRepositoryTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.settings.SettingsScreenTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.settings.SettingsViewModelTest
Real-Device-Validation: Ja — Permission-Dialoge können nur auf echtem Device oder Emulator vollständig getestet werden: Rationale-Dialog sichtbar, System-Dialog erscheint, Permanent-Denial-Hinweis korrekt.

Play-Store-/Privacy-Relevanz: Ja — ACCESS_FINE_LOCATION im Manifest löst die Pflicht aus, die Data Safety Section im Play Store zu aktualisieren. Diese Aktualisierung muss spätestens mit dem ersten Upload nach diesem Block erfolgen. Privacy Policy muss GPS-EXIF-Verhalten beschreiben.

Commit-Fähigkeit: "Recreation guidance"-Toggle in Settings, funktionierender Permission-Flow, kein GPS-Effekt auf CameraScreen. Feature existiert als konfigurierbare Option, tut aber noch nichts.

Block 4 — LocationProvider + Lifecycle  ✅ DONE (2026-05-27)
Status: Implementiert und getestet — 2026-05-27
Unit Tests: testDebugUnitTest — BUILD SUCCESSFUL (218 Tests grün, 0 Fehler)
  LocationProviderTest: 14/14 grün
  CameraViewModelTest GPS-Tests: 15 neue GPS-Tests grün
Hinweis: Looper.getMainLooper() gibt null zurück in Android Unit-Test-Stubs. Fix: looperProvider: () -> Looper?-Lambda als injizierbarer Constructor-Parameter mit @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS").

Ziel: LocationManager-basierter Location-Provider als eigenständige, injizierbare Klasse. GPS-Aktivierungsbedingungen in CameraViewModel implementieren. currentLocation als internes ViewModel-Feld.

Warum an dieser Stelle: Benötigt Block 3 (Setting-State + Permission-State). Muss vor Block 5 (Guidance Computation) kommen, weil aktuelle Location die Eingabe der Berechnung ist.

Betroffene Dateien:

Neue Klasse: LocationProvider.kt (in ui/camera/)
CameraViewModel.kt — GPS-Aktivierungslogik, currentLocation-Feld (internes android.location.Location?), Settings-Collector für recreationGuidance
CameraScreen.kt — DisposableEffect für GPS-Stop auf Dispose, LifecycleEventObserver für Pause/Resume
Konkrete Änderungen:

LocationProvider:


class LocationProvider(private val context: Context) {
    fun startUpdates(listener: LocationListener, looper: Looper = Looper.getMainLooper())
    fun stopUpdates(listener: LocationListener)
    fun getLastKnown(): Location?  // GPS_PROVIDER first, NETWORK_PROVIDER fallback
}
GPS_PROVIDER primary, NETWORK_PROVIDER Fallback. Update-Interval: 8000ms min, 3m min-Distanz. getLastKnown() gibt die frischeste verfügbare Location von beiden Providern zurück.

Vier GPS-Aktivierungsbedingungen (alle müssen gleichzeitig true sein):

recreationGuidanceEnabled (aus DataStore)
locationPermissionGranted (via ContextCompat.checkSelfPermission)
referenceHasGps (aus _uiState.value.referenceImageMetadata?.gpsLatitude != null)
cameraScreenActive (via Lifecycle-Callbacks in CameraScreen)
CameraViewModel: fun updateGpsActivation() — prüft alle vier, startet oder stoppt GPS. Wird aufgerufen wenn sich eine der Bedingungen ändert. isGpsActive-Flag verhindert Duplicate-Starts.

CameraScreen: DisposableEffect(Unit) { onDispose { viewModel.onCameraScreenInactive() } } + LifecycleEventObserver für ON_PAUSE/ON_RESUME.

Explicit threading: LocationListener registriert auf Looper.getMainLooper(). LocationListener-Callbacks aktualisieren currentLocation über _uiState.update {}. Kein IO-Dispatcher für Location-Callbacks.

Explizite Nicht-Ziele: Kein UI-Element. CameraUiState bekommt in diesem Block noch kein GPS-sichtbares Feld. currentLocation bleibt ein internes ViewModel-Feld (nicht in CameraUiState). Guidance-Berechnung kommt in Block 5.

Risiken:

LocationManager.GPS_PROVIDER ist auf Emulator ohne GPS-Injection nicht verfügbar → Tests brauchen eine Injectable LocationProvider-Abstraktion oder Mock
Permission kann zwischen updateGpsActivation() und dem tatsächlichen Location-Request revoked werden → alle LocationManager-Calls in try-catch
Double-Start: isGpsActive-Flag atomar behandeln (CameraViewModel läuft auf Main-Thread für State-Updates; Location-Start/-Stop ebenfalls Main-Thread → kein Concurrency-Problem)
getLastKnown() kann SecurityException werfen wenn Permission während des Calls revoked wurde → catch
Neue Tests:

Unit: LocationProvider mit Mock-LocationManager — Start, Stop, Duplicate-Stop-safe
Unit: updateGpsActivation() — alle 16 Kombinationen der vier Bedingungen (nur die eine mit allen true startet GPS)
Unit: referenceHasGps korrekt wenn Metadata GPS hat / nicht hat / null
Unit: GPS stoppt wenn recreationGuidance auf OFF schaltet
Unit: GPS startet nicht wenn Permission nicht granted
Gradle-Kommandos:


./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.CameraControlsOverlayTest
Real-Device-Validation: Ja — GPS-Start bestätigen (Logcat), Stop bei App-Background, Stop wenn Reference-Image entfernt wird.

Play-Store-/Privacy-Relevanz: Kein zusätzlicher Punkt — Permission ist in Block 3 gesetzt. Kein Background-Tracking.

Commit-Fähigkeit: GPS-Provider ist aktiv wenn alle vier Bedingungen erfüllt, stoppt sauber. currentLocation wird befüllt aber nicht angezeigt. Feature ist funktional verborgen.

Block 5 — Guidance State Computation  ✅ DONE (2026-05-27)
Status: Implementiert und getestet — 2026-05-27
Unit Tests: testDebugUnitTest — BUILD SUCCESSFUL (243 Tests grün, 0 Fehler)
  GuidanceComputerTest: 17 neue Tests grün (Proximity-Farben, Bearing, Hysterese, Schwellwerte, Neutral, formatDistance)
  CameraViewModelTest: 4 neue Guidance-State-Tests grün, alle 218 Vorherigen weiterhin grün
Neue Dateien: GpsGuidanceState.kt, GuidanceComputer.kt

Ziel: Reine Berechnungsschicht ohne UI. GpsGuidanceState als Sealed Interface. Bearing-, Distanz-, Farbmodell- und Hysterese-Logik als testbare pure Functions. CameraUiState um gpsGuidanceState-Feld erweitern.

Warum an dieser Stelle: Computation vor UI trennt Logik klar ab, erlaubt extensive Unit-Tests ohne Android-Composable-Infrastruktur. CameraViewModel kann currentLocation-Updates sofort in GpsGuidanceState umrechnen, bevor irgend ein UI-Element das liest.

Betroffene Dateien:

Neue Klasse: GuidanceComputer.kt (in ui/camera/) — pure Kotlin, keine Android-Abhängigkeit
CameraViewModel.kt — GpsGuidanceState in CameraUiState, Hysterese-Tracking-State, Computation-Aufruf bei Location-Updates
Neue Datei: GpsGuidanceState.kt (Sealed Interface + ProximityColor Enum)
Konkrete Änderungen:


sealed interface GpsGuidanceState {
    data object Hidden : GpsGuidanceState        // Guidance OFF oder kein Ref-GPS
    data object Neutral : GpsGuidanceState       // ON, wartet auf Fix oder zu ungenau
    data class Informative(
        val distanceMeters: Float,
        val bearingDegrees: Float?,              // null wenn < ~15-20m
        val proximityColor: ProximityColor
    ) : GpsGuidanceState
}

enum class ProximityColor { GREEN, ORANGE, RED, NEUTRAL }
GuidanceComputer — pure Functions:

computeDistance(current: LatLon, reference: LatLon): Float — Haversine oder Location.distanceBetween()
computeBearing(current: LatLon, reference: LatLon): Float — Location.bearingTo() → normalisieren auf [0°, 360°]
computeProximityColor(distance: Float, accuracyMeters: Float): ProximityColor — exakt Spec-Logik
computeGuidanceState(currentLocation, referenceLatLon, accuracyMeters, previousState, hysteresisCounter): GpsGuidanceState
Hysterese: ViewModel hält private var hysteresisCounter: Int = 0 und private var pendingColor: ProximityColor? = null. Farbwechsel erst nach 2 konsekutiven Updates mit neuem Status.

Update-Schwellen in ViewModel:

Distanz-Änderung < 2m → kein _uiState.update
Bearing-Änderung < 5° → kein _uiState.update
CameraUiState erhält:


val gpsGuidanceState: GpsGuidanceState = GpsGuidanceState.Hidden
Explizite Nicht-Ziele: Kein Composable in diesem Block. Kein UI-Test. CameraScreen.kt unverändert (liest gpsGuidanceState noch nicht).

Risiken: Niedrig. GuidanceComputer ist pure Kotlin ohne Android-Abhängigkeiten — vollständig unit-testbar auf der JVM ohne Device.

Neue Tests (alle Unit Tests, kein Device nötig):

GuidanceComputerTest.proximityColor_green_whenDistanceBelowThreshold
GuidanceComputerTest.proximityColor_green_scalesWithAccuracy
GuidanceComputerTest.proximityColor_orange_range
GuidanceComputerTest.proximityColor_red_range
GuidanceComputerTest.proximityColor_neutral_whenAccuracyPoor
GuidanceComputerTest.bearing_suppressedWhenDistanceLessThan15m
GuidanceComputerTest.bearing_normalizedToPositiveRange
GuidanceComputerTest.hysteresis_colorDoesNotChangeOnSingleUpdate
GuidanceComputerTest.hysteresis_colorChangesAfterTwoConsecutiveUpdates
GuidanceComputerTest.updateThreshold_smallDistanceChangeIgnored
GuidanceComputerTest.updateThreshold_smallBearingChangeIgnored
CameraViewModelTest.* — bestehende Tests weiterhin grün (Default Hidden)
Gradle-Kommandos:


./gradlew testDebugUnitTest
Real-Device-Validation: Nein — reine Logik.

Play-Store-/Privacy-Relevanz: Nein.

Commit-Fähigkeit: Komplette Guidance-Berechnungsschicht, vollständig getestet. CameraUiState.gpsGuidanceState vorhanden aber unsichtbar. Clean commit: "Add GPS guidance state computation layer with proximity model and hysteresis".

Block 6 — Guidance Chip UI
Ziel: GpsGuidanceChip-Composable implementieren, in CameraScreen einbetten. Bearing-Pfeil, Distanzanzeige, Farb-Akzent, North-up-"N"-Label, Fade-Transitions.

Warum an dieser Stelle: Nach der Computation-Schicht — der Chip konsumiert nur gpsGuidanceState aus CameraUiState. Vor EXIF Writing — Nutzer sieht was die Funktion tut, bevor GPS-Daten in Fotos geschrieben werden.

Betroffene Dateien:

Neue Datei: GpsGuidanceChip.kt (in ui/camera/) — @Composable internal fun GpsGuidanceChip(state: GpsGuidanceState, modifier: Modifier)
CameraScreen.kt — GpsGuidanceChip einbetten
app/src/main/res/values/strings.xml + values-de/strings.xml — Distanzformat-Strings
Konkrete Änderungen:

Chip-Aufbau (wenn Informative):

Bearing-Pfeil: Canvas-Zeichnung, rotiert um bearingDegrees. Pfeil entfällt bei bearingDegrees == null
"N"-Label: kleines Text-Label neben der Pfeil-Zeichnung
Distanztext: "47m" oder "1.2km" — Formatierung via GuidanceComputer.formatDistance(meters)
Farb-Akzent: Surface-Farbe oder Border-Farbe je nach proximityColor
Chip-Position in CameraScreen:

Portrait: Alignment.TopCenter, statusBarsPadding() + kleines padding(top = 8.dp)
Diese Position ist im aktuellen Layout frei: TopStart ist der Hint-Zone (FormatMismatchHint, CoverageWarning), TopEnd ist History/Overflow
Landscape: gesondertes Positioning — der Chip muss außerhalb des CameraLandscapeTopActions-Bereichs bleiben. Konkret: Alignment.TopCenter mit entsprechendem padding vom Viewport-Top sollte ausreichen, da die Side-Rail vertikal links/rechts und nicht oben platziert ist
Fade-Transition: AnimatedVisibility mit fadeIn()/fadeOut() für den Übergang zwischen Hidden und sichtbar. Keine Animation zwischen Neutral und Informative — stattdessen kurze Fade beim State-Wechsel via Crossfade.

Chip ist Hidden → komplett nicht gerendert (if (state !is GpsGuidanceState.Hidden)).

Explizite Nicht-Ziele: Kein Magnetometer. Kein Rotating Arrow. Kein Maps-Aussehen. Kein Overlapping mit Top-Left Hint Zone. Keine neue UI-Logik im ViewModel (kommt aus Block 5). GpsGuidanceState-Berechnung nicht duplizieren.

Risiken:

Landscape-Positionierung muss real auf Device geprüft werden (Overlap-Risiko)
AnimatedVisibility kann bei schnellen GPS-Updates zu visuellen Artefakten führen → Update-Schwellen aus Block 5 schützen davor
Material 3 Chip-Komponente vs. eigenes Surface-Layout: Material 3 AssistChip könnte als Basis dienen, aber GPS-Bearing-Pfeil erfordert Custom-Content → eigenes Surface + Row ist sicherer
Neue Tests:

CameraControlsOverlayTest: GPS-Chip sichtbar wenn gpsGuidanceState = Informative, nicht sichtbar wenn Hidden
CameraControlsOverlayTest: Chip in Portrait nicht mit Top-Left Hint Zone überlappend
Instrumentation: Chip verschwindet wenn gpsGuidanceState = Hidden
Gradle-Kommandos:


./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.camera.CameraControlsOverlayTest
Real-Device-Validation: Ja — Pflicht. Portrait und Landscape prüfen. Chip-Position, Überlappungen, Fade-Verhalten, Farben auf echtem Screen.

Play-Store-/Privacy-Relevanz: Nein (reine UI-Änderung).

Commit-Fähigkeit: GPS Guidance Chip vollständig sichtbar und funktional. End-to-end: Recreation Guidance ON + Reference mit GPS + Location fix → Chip zeigt Bearing + Distanz + Farbe.

Block 7 — Capture GPS Freeze + EXIF Writing
Ziel: GPS-Fix beim Capture-Trigger einfrieren (GpsSnapshot), in CaptureSessionSnapshot einbetten, captureLocation und referenceLocation in metadata.json schreiben, GPS-EXIF in capture.jpg und MediaStore-Bild, GPS-Preservation in reference-original.jpg.

Warum zuletzt unter den Feature-Blöcken: Nutzer sieht bereits die Guidance (Block 6), versteht was GPS bedeutet. Erst dann wird GPS auch in Fotos persistiert. Privacy-seitig der sauberere Ablauf.

Betroffene Dateien:

CameraViewModel.kt — GpsSnapshot Data Class + Freeze in onPhotoCaptured() + CaptureSessionSnapshot-Erweiterung
SessionStorage.kt — writeMetadata(): captureLocation + referenceLocation; writeCapture(): GPS-EXIF in capture.jpg; writeReferenceOriginalAndReference(): GPS-Preservation
MediaStoreWriter.kt — save(): GPS via ContentValues + ExifInterface
SessionStorageMetadataTest.kt — neue GPS-Assertions
Test-Assets: JPEG mit GPS-EXIF für reference-original.jpg Preservation Test
Konkrete Änderungen:

GpsSnapshot Data Class (in CameraViewModel.kt neben anderen Datenklassen):


data class GpsSnapshot(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracyMeters: Float,
    val provider: String,
    val fixTimestampMs: Long
)
CaptureSessionSnapshot Erweiterung:


internal data class CaptureSessionSnapshot(
    // ... alle bestehenden Felder unverändert ...
    val gpsSnapshot: GpsSnapshot? = null   // Kotlin Default → alle Aufrufer bleiben kompatibel
)
GPS-Freeze in onPhotoCaptured() (Zeilen 632–647 in CameraViewModel.kt):
Der Snapshot wird VOR dem IO-Launch aus currentState gebaut. An exakt dieser Stelle:


val gpsSnapshot: GpsSnapshot? = if (recreationGuidanceEnabled && permissionGranted) {
    currentLocation?.let { loc ->
        GpsSnapshot(
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitude = if (loc.hasAltitude()) loc.altitude else null,
            accuracyMeters = loc.accuracy,
            provider = loc.provider ?: "unknown",
            fixTimestampMs = loc.time
        )
    }
} else null
currentLocation ist das interne ViewModel-Feld aus Block 4. Die Freeze-Semantik ist durch val-Immutabilität von CaptureSessionSnapshot garantiert.

EXIF-GPS-Konvertierung: Da ExifInterface.setLatLong() erst ab API 33 verfügbar, manuelle Implementierung für minSdk 29:

Konvertierung Double → DMS-Rational-String ("48/1,7/1,24/1000")
Helper-Funktion fun decimalDegreesToDmsRational(decimal: Double): String
TAG_GPS_LATITUDE_REF = "N" / "S" basierend auf Vorzeichen
TAG_GPS_LONGITUDE_REF = "E" / "W" basierend auf Vorzeichen
TAG_GPS_PROCESSING_METHOD = Bytes "GPS\u0000" oder "NETWORK\u0000" + Provider aus GpsSnapshot
Diese Funktion erhält eigenen Unit-Test
GPS in capture.jpg: Nach writeBitmapAsJpeg() in writeCapture():


if (snapshot.gpsSnapshot != null) {
    writeGpsExif(file, snapshot.gpsSnapshot)  // silent fail on IOException
}
GPS in MediaStore: MediaStoreWriter.save() erhält optionalen gpsSnapshot: GpsSnapshot? = null-Parameter. GPS via ContentValues (LATITUDE/LONGITUDE) beim Insert + ExifInterface via FileDescriptor nach compress.

GPS-Preservation in reference-original.jpg: In writeReferenceOriginalAndReference() nach dem writeBitmapAsJpeg(oriented, File(sessionDir, FILE_REFERENCE_ORIGINAL)):


if (snapshot.referenceImageMetadata.gpsLatitude != null) {
    copyGpsExifFromSource(
        sourceUri = snapshot.referenceImageUri,
        context = context,
        targetFile = File(sessionDir, FILE_REFERENCE_ORIGINAL)
    )
}
copyGpsExifFromSource(): öffnet Source-URI mit ContentResolver, liest raw GPS-Attribute-Strings (kein getLatLong() — raw String-Werte), schreibt diese Strings unverändert in die target-Datei. Kein Umweg über Double.

reference.jpg bekommt kein GPS: ReferenceRenderer.render() Signatur bleibt unverändert (kein GPS-Parameter). In writeReferenceOriginalAndReference() wird nach dem Rendern kein GPS-EXIF in reference.jpg geschrieben — kein Code nötig, da es schlicht nicht geschrieben wird.

Explizite Nicht-Ziele: ReferenceRenderer.render() bleibt ohne GPS-Parameter. Overlay-Geometrie unverändert. GpsSnapshot darf nicht als Rendering-Input verwendet werden. Nach Session-Save sind GPS-Daten immutable.

Risiken:

DMS-Rational-Format für ExifInterface (höchstes Risiko in diesem Block): benötigt zwingend Unit-Test mit Randfällen: negative Koordinaten, Koordinate = 0, sehr hohe Präzision
reference-original.jpg GPS-Preservation: URI ggf. nicht erneut öffenbar in manchen Content-Provider-Implementierungen → try-catch, silent fail; referenceOriginal bekommt dann kein GPS, was akzeptabel ist
GPS-EXIF-Fehler dürfen Session-Save nicht abbrechen: alle writeGpsExif()-Calls in try-catch; writeCapture() / writeReferenceOriginalAndReference() propagieren keine EXIF-Exceptions nach oben
MediaStoreWriter: IS_PENDING-Flag und GPS: LATITUDE/LONGITUDE in ContentValues müssen beim Insert gesetzt sein (bevor IS_PENDING=0); ExifInterface via FileDescriptor nach compress; ExifInterface.saveAttributes() muss VOR dem IS_PENDING=0-Update kommen
Neue Tests:

Unit: decimalDegreesToDmsRational() Randfälle (0°, 90°, -45.5°, 179.9°)
SessionStorageMetadataTest.withGpsSnapshot_captureLocationWritten
SessionStorageMetadataTest.withoutGpsSnapshot_captureLocationAbsent
SessionStorageMetadataTest.withReferenceGps_referenceLocationWritten
SessionStorageMetadataTest.withoutReferenceGps_referenceLocationAbsent
Instrumentation: capture.jpg nach Save hat GPS-EXIF wenn gpsSnapshot != null
Instrumentation: reference.jpg nach Save hat kein GPS-EXIF
Instrumentation: CaptureSessionSnapshot.gpsSnapshot spiegelt Location zum Capture-Trigger-Zeitpunkt (nicht einen späteren Update)
Unit: GPS-Snapshot ist nach dem Freeze unverändert, wenn weitere Location-Updates kommen
Gradle-Kommandos:


./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.storage.SessionStorageMetadataTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.storage.SessionStorageReferenceOrientationTest
./gradlew assembleDebug
Real-Device-Validation: Ja — MediaStore-Bild mit GPS in Galerie prüfen (Google Fotos zeigt Ort), capture.jpg GPS-EXIF via externem Tool prüfen, reference.jpg ohne GPS bestätigen.

Play-Store-/Privacy-Relevanz: Ja — ab diesem Block werden GPS-Koordinaten in Nutzerfotos geschrieben. Data Safety Form muss vor Upload aktualisiert sein (war bereits ab Block 3 fällig).

Commit-Fähigkeit: Vollständige GPS-Persistenz. End-to-end: Recreation Guidance ON + Referenzbild mit GPS + Fix → Capture → GPS in MediaStore-Bild + capture.jpg + metadata.json. Reference.jpg ohne GPS.

Block 8 — Test-Hardening + Release-Vorbereitung
Ziel: Edge-Cases absichern, Privacy-Dokumentation finalisieren, Outdoor-Validation, Regressionstests für bestehende Features.

Betroffene Bereiche:

Neue Testfälle (unit + instrumentation)
docs/IMPLEMENTATION_NOTES.md — GPS Recreation System als implemented dokumentieren
docs/CLAUDE_PROJECT_INSTRUCTION.md — ACCESS_FINE_LOCATION in Permissions-Abschnitt ergänzen
Play-Store-Metadaten (extern)
Neue Tests:

Permission während CameraScreen aktiv revoked → GPS stoppt, kein Crash, kein Error-Banner
Permission permanent denied → Inline-Hinweis in Settings, kein GPS
App backgrounded während GPS aktiv → GPS stoppt, onPause korrekt
App foregrounded → GPS startet neu wenn alle Bedingungen erfüllt
Rotation während GPS aktiv → keine Duplicate-Starts, State überlebt
Reference-Bild wechseln (mit GPS → ohne GPS) → GPS-Chip verschwindet
Reference-Bild wechseln (ohne GPS → mit GPS) → GPS-Chip erscheint
Recreation-Guidance Toggle OFF während GPS aktiv → GPS stoppt sofort
Session mit GPS öffnen in CompareScreen → kein GPS in Rendering, deterministische Compare-Darstellung unverändert
Vollständige Regression: alle 329 bestehenden Instrumentation-Tests weiterhin grün
Outdoor-Validation:

Bearing-Pfeil zeigt korrekte Richtung (durch Bewegung verifikation)
Farbwechsel bei Annäherung
Bearing-Suppression wenn < 20m (Pfeil verschwindet, nur Grün bleibt)
GPS-Chip Neutralzustand in Gebäude
Chip verschwindet wenn Recreation Guidance OFF
GPS stoppt wenn App in Hintergrund (Batterie-/Privacy-Verifikation via Logcat)
Play-Store-Checkliste vor Upload:

Data Safety Form: "Precise location — collected, not shared with third parties, not used for tracking, optional"
Privacy Policy: GPS-EXIF in captures beschreiben, "when Recreation Guidance is enabled", "stored locally only"
Keine BACKGROUND_LOCATION → kein Enhanced Review-Risiko
Keine INTERNET permission → keine Cloud-Upload-Bedenken
Gradle-Kommandos:


./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease
Real-Device-Validation: Ja — vollständige Outdoor-Session.

Play-Store-/Privacy-Relevanz: Ja — finaler Prüfpunkt vor Release.

Commit-Fähigkeit: Vollständige Implementierung, gehärtet und dokumentiert. Release-ready.

5. Empfohlener erster Implementierungsblock
Block 1 — metadata.json v3 + SessionScanner-Kompatibilität.

Zwei Dateien, drei Konstanten-Änderungen, eine Bedingungsänderung, einige Testanpassungen. Kein Benutzer-sichtbarer Effekt. Null Risiko für bestehende Funktionalität. Vollständig reversibel. Schafft das Fundament, auf das alle anderen Blöcke aufbauen.

6. Vor Block 1 zu klärende Punkte
Entscheidung 1 — v3-Vollschema bestätigen:
Bestätigen, dass v3 = bestehende v2-Nested-Struktur + optionale captureLocation/referenceLocation Top-Level-Felder (kein Schema-Redesign). Die GPS_RECREATION_SYSTEM_V1.md-Darstellung in Abschnitt 5 ist ein GPS-fokussierter Auszug, nicht das vollständige Schema. Das vollständige v3-Schema wäre: alle v2-Felder (außer location-Block) + optional captureLocation + optional referenceLocation.

Entscheidung 2 — Permission-Trigger exakt bestätigen:
Default OFF ist klar. Aber: wenn der Nutzer "Recreation Guidance" aktiviert und Permission gewährt, dann das Toggle vorübergehend ausschaltet und später wieder einschaltet — wird Permission erneut angefragt oder nicht? Antwort: Nein, Permission ist bereits granted; ViewModel prüft checkSelfPermission vor dem Request. Nur beim ersten Einschalten mit PERMISSION_NOT_GRANTED-Zustand wird angefragt. Bitte bestätigen.

7. Was explizit NICHT jetzt umgesetzt werden darf
Kein Magnetometer / Rotating Arrow — nicht in V1
Keine Maps, kein Routing, keine Geocoding
Keine Background Location
Kein getrenntes "Save GPS"-Setting
Kein GPS in reference.jpg — unter keinen Umständen
Keine GPS-Aktivierung beim App-Start oder beim Laden eines Referenzbildes ohne alle vier Bedingungen
Keine Änderung der Compare-Rendering-Pipeline (ReferenceRenderer.render() bleibt ohne GPS-Parameter)
Keine Änderung der Compare-Navigation oder CompareScreen-Darstellung
Keine Modifikation gespeicherter Sessions nach Session-Save
Kein User-facing "GPS lost"- oder "GPS weak"-Banner
Keine Accuracy-Werte direkt im UI
Keine Location History, kein Tracking
Kein Cloud-Upload irgendeiner GPS-Daten