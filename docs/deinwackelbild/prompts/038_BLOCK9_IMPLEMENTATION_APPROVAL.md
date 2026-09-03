BLOCK 9 — IMPLEMENTATION APPROVAL

Freigabe erteilt.

Implementiere Block 9 exakt gemäß dem zuletzt bestätigten Block-9B-Scope.

WICHTIG:
Die abgeschlossene Block-9B-Analyse ist verbindliche Grundlage dieser Implementierung. Keine erneute Ausweitung des Scopes und keine zusätzlichen Verbesserungen.

## 1. Erlaubte Dateien

Es dürfen ausschließlich diese sechs Dateien geändert werden:

1. app/build.gradle.kts
2. app/src/main/java/com/isardomains/sameview/net/deinwackelbild/OkHttpDeinWackelbildApiClient.kt
3. app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt
4. app/src/test/java/com/isardomains/sameview/net/deinwackelbild/OkHttpDeinWackelbildApiClientTest.kt
5. docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md
6. docs/IMPLEMENTATION_NOTES.md

Wenn wider Erwarten eine siebte Datei notwendig wäre:
STOPPEN und begründen.
Nicht eigenmächtig erweitern.

## 2. Partner-Key-Provisioning

Implementiere exakt die bestätigte Policy:

### Debug / non-release

Priorität:

1. local.properties
2. Environment Variable
3. leerer String

### Release

Priorität:

1. Environment Variable
2. leerer String

KRITISCH:

Release darf unter keinen Umständen den Wert aus local.properties verwenden.

Ein lokaler Pilot-/Test-Key darf dadurch niemals allein aufgrund seiner Existenz in local.properties in einem Release APK/AAB landen.

Keine Product Flavors.
Kein neues Gradle-File.
Kein neuer Plugin.
Keine neue Dependency.

## 3. local.properties

Die reale local.properties ist user-owned und tabu.

Während Implementierung und Verifikation:

- NICHT verändern
- NICHT überschreiben
- NICHT temporär ersetzen
- NICHT umbenennen
- NICHT sichern/wiederherstellen
- NICHT ergänzen
- NICHT ausgeben
- NICHT ihren Inhalt greppen
- NICHT ihren Key anzeigen

Sie darf ausschließlich durch die normale Gradle-Konfiguration zur Debug-Key-Auflösung verwendet werden.

Keine Testmanipulation dieser Datei.

## 4. BuildConfig

Erzeuge:

BuildConfig.DEINWACKELBILD_PARTNER_KEY

Die bestätigte Struktur ist:

- defaultConfig: Debug/non-release Policy
- buildTypes.release: expliziter Override mit ausschließlich Environment Variable → ""

Der Release-Override darf keinerlei Referenz auf den aus local.properties gelesenen Wert enthalten.

Verwende für buildConfigField einen kleinen lokalen String-Escaping-Helper.

Mindestens korrekt escapen:

1. Backslash
2. Double Quote
3. Newline
4. Carriage Return

Keine zusätzliche Library.

## 5. WackelbildViewModel

Ersetze ausschließlich die bisherige Block-8-Platzhalter-Wiring-Stelle:

partnerKey = ""

durch:

BuildConfig.DEINWACKELBILD_PARTNER_KEY

bzw. die syntaktisch korrekte äquivalente Verwendung inklusive notwendigem Import.

Keine sonstige Änderung an:

- Operation State
- Retry
- Idempotency
- Rendering
- Cleanup
- Sensoren
- Swipe
- Lifecycle
- UI
- Navigation

## 6. Missing-Key Guard

In:

OkHttpDeinWackelbildApiClient.createHandoff()

füge die bestätigte lokale Prüfung ein:

partnerKey.isBlank()

Bei leerem Key:

DeinWackelbildResult.Failure(
    DeinWackelbildApiError(
        DeinWackelbildErrorClassification.INTEGRATION_UNAVAILABLE
    )
)

Die Prüfung muss erfolgen:

- vor Request.Builder()
- vor callFactory.newCall(...)
- ohne Netzwerkaufruf

Nicht:

- Key-Prefix validieren
- Key-Länge validieren
- Key loggen
- Exception werfen
- App-Startup blockieren

## 7. Test

In:

OkHttpDeinWackelbildApiClientTest.kt

additiv testen:

blank partner key
→ INTEGRATION_UNAVAILABLE
→ exakt 0 Call.Factory-/newCall-Aufrufe

Bestehende Tests nicht abschwächen oder unnötig ändern.

Insbesondere müssen die bestehenden Tests mit Fake-Key und die Security-Prüfungen weiterhin funktionieren.

Keine echte Netzwerkverbindung.

## 8. Dokumentation

DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md:

Partner-Key-Provisioning korrigieren auf:

Debug/non-release:
local.properties → ENV → blank

Release:
ENV → blank

Explizit dokumentieren:

- Release liest keinen Partner-Key aus local.properties.
- Pilot/Test-Key kann für lokale Debug-Nutzung in local.properties liegen.
- Produktions-Key für Release kommt extern über Environment Variable.
- Fehlender Release-Key verhindert den Build nicht.
- Die Integration liefert beim Start eines Vorgangs lokal INTEGRATION_UNAVAILABLE.
- Falls der Debug/Release-Split noch als offen geführt wird: diesen Punkt schließen.
- Der konkrete spätere CI-Injection-Mechanismus darf weiterhin als extern/offen dokumentiert bleiben.

IMPLEMENTATION_NOTES.md:

Nur einen knappen Block-9-Implementationseintrag ergänzen.

KEIN neues PARTNER_KEY_SETUP.md.
KEINE weitere Dokumentationsdatei.

## 9. Explizit verboten

Nicht ändern:

- AndroidManifest.xml
- INTERNET permission
- Strings
- UI
- WackelbildScreen.kt
- WackelbildViewModelTest.kt
- WackelbildHandoffOrchestrator
- WackelbildPrintRenderer
- WackelbildTempFileManager
- Navigation
- Camera-Code
- Session-Code
- CI-Konfiguration

Außerdem:

- kein echter API-Key in Git
- kein echter API-Key in Tests
- kein echter API-Key in Docs
- kein Key in URL
- kein Logging
- keine Live-API-Anfrage
- kein Block-10-Code
- kein Custom Tab

## 10. Verifikation

Nach Implementierung vollständig ausführen:

./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
git diff --check
git status --short

Zusätzlich darf eine Release-Verifikation mit einem ausschließlich synthetischen Environment-Wert erfolgen, z. B.:

DEINWACKELBILD_PARTNER_KEY=synthetic_release_value

Dabei niemals echte Credential-Werte ausgeben.

Die reale local.properties darf dafür nicht verändert werden.

Nicht erforderlich:

- connectedDebugAndroidTest
- Physical-Device-Test
- Live-API-Test

## 11. Release-Sicherheitsprüfung

Im Abschlussbericht explizit bestätigen:

A)
local.properties enthält Key, ENV fehlt
→ Release enthält blank
→ lokaler Key gelangt nicht in Release

B)
Release-ENV enthält Key
→ Release verwendet ENV-Key

C)
kein Release-ENV-Key
→ Release-Build erfolgreich
→ BuildConfig-Key blank
→ createHandoff() endet lokal mit INTEGRATION_UNAVAILABLE

## 12. Abschlussbericht

Liefere danach strukturiert:

1. Repository Baseline
2. Files Modified
3. BuildConfig / Provisioning Implementation
4. Release Isolation from local.properties
5. ViewModel Wiring
6. Missing-Key Guard
7. Tests
8. Documentation
9. Security Verification
10. Build / Verification Results
11. Diff Scope
12. Remaining Work
13. Gate Result

Bei Files Modified exakt bestätigen, dass nur die sechs freigegebenen Dateien verändert wurden.

Falls während der Implementierung ein Problem auftaucht, das eine Scope-Erweiterung erfordert:
NICHT selbstständig lösen.
STOPPEN und melden.

Erwarteter Gate Result bei erfolgreicher Umsetzung:

BLOCK 9 IMPLEMENTED — READY FOR REVIEW
