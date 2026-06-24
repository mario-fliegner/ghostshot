# SESSION_BACKUP_EXPORT_IMPLEMENTATION_PLAN.md

**Status:** Implemented  
**Implementation completed:** 2026-06-01  
**Grundlage:** SESSION_BACKUP_EXPORT_V1.md (authoritative), CLAUDE_PROJECT_INSTRUCTION.md, COMPARE_FLOW_V1.md, COMPARE_SESSION_RENDERING_V1.md, GPS_RECREATION_SYSTEM_V1.md, RELEASE_HARDENING_AUDIT_V1.md, aktueller Codebestand (Kotlin-Quelldateien, strings.xml)  
**Planerstellt:** 2026-06-01

---

## 1. Ziel und Scope

Implementierung eines benutzerinitierten, lokalen, vollständigen Session-Backups als ZIP-Datei, geschrieben in einen benutzerwählbaren Speicherort via Android Storage Access Framework (`ACTION_CREATE_DOCUMENT`).

**Einstiegspunkte:**
- `CompareScreen` → Overflow-Menü → "Backup Session" (einzelne Session)
- `CompareLibraryScreen` → Multi-Select-Aktionsleiste → Backup-Icon (eine oder mehrere Sessions)

**Exportformat:** Standard-ZIP mit einer Subdirectory pro Session (`<sessionId>/capture.jpg`, `reference.jpg`, `reference-original.jpg`, `metadata.json`).

---

## 2. Nicht-Ziele (explizit ausgeschlossen)

- Session-Import (nicht in V1)
- Cloud-Sync, Drive-/OneDrive-Integration
- Share via Android Share Sheet (erfordert FileProvider; separater späterer Scope)
- Create Video (separater Scope; beeinflusst zukünftige TopAppBar-Struktur, wird jetzt nicht pre-implementiert)
- PDF-Export, Bild-Rekodierung, GPS-Entfernung
- Backup-Scheduling, automatische Backups
- `backup.json` / Backup-Manifest-Datei
- „Backup All"-Button als separater Einstiegspunkt (Select All + Backup in Multi-Select erfüllt diesen Zweck)
- Fortschrittsanzeige als Prozentwert (Loading-Indicator genügt)
- Backup-Verifikation oder ZIP-Integritätsprüfung

---

## 3. Relevante bestehende Dateien/Klassen

### Core Session-Infrastruktur

| Datei | Relevanz |
|---|---|
| `ui/camera/CameraViewModel.kt` | Zentrales ViewModel; besitzt Session-State (`savedSessions`), `CameraUiState`, `UiEvent`-Flow; wird für Backup-State und -Operationen erweitert |
| `ui/camera/SessionDeleter.kt` | Architekturelles Vorbild für `SessionBackupExporter` (pure `object`, `TemporaryFolder`-testbar, kein Android-Context) |
| `ui/camera/SessionStorage.kt` | Schreibt Sessions; darf **nicht** modifiziert werden |
| `ui/camera/SessionScanner.kt` | Liest/listet Sessions; darf **nicht** modifiziert werden |
| `ui/camera/ScannedSession.kt` | Datenbasis für CompareLibrary-Tiles (in `CameraViewModel.kt` definiert) |

### UI-Screens

| Datei | Relevanz |
|---|---|
| `ui/compare/CompareScreen.kt` | Erhält neuen Parameter `sessionId`, neues Overflow-Item "Backup Session", SAF-Launcher; bestehende Overflow-Struktur (Edit Title / Remove Title) bleibt unverändert |
| `ui/compare/CompareLibraryScreen.kt` | Erhält neues Backup-Icon in Multi-Select-Aktionsleiste; **Select All / Deselect All ist bereits implementiert** (siehe Abschnitt 6) |
| `MainActivity.kt` | Navigation-Wiring; muss neue Backup-Callbacks von `CameraViewModel` an CompareScreen und CompareLibraryScreen durchleiten |

### Ressourcen

| Datei | Relevanz |
|---|---|
| `res/values/strings.xml` | Erhält neue String-Keys für Backup-Feature |

### Bestehende Tests (Referenz für neue Tests)

| Datei | Referenz für |
|---|---|
| `ui/camera/SessionDeleterTest.kt` | `SessionBackupExporterTest.kt` — JVM-Unit-Test mit `TemporaryFolder` |
| `ui/compare/CompareScreenTest.kt` | Overlay-Menü-Tests, SAF-Integration-Tests |
| `ui/compare/CompareLibraryScreenTest.kt` | Multi-Select-Aktionsleisten-Tests |
| `ui/camera/CameraViewModelTest.kt` | Backup-State-Transitions, ViewModel-Events |
| `storage/SessionStorageMetadataTest.kt` | Instrumentierungstest-Muster mit echtem Dateisystem |

---

## 4. Vorgeschlagene neue Dateien/Klassen

### Neue Produktionsdateien

| Datei | Beschreibung |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/camera/SessionBackupExporter.kt` | `internal object` mit ZIP-Erzeugungslogik; testbar ohne Android-Context; analog zu `SessionDeleter.kt` |

**Interne Struktur `SessionBackupExporter`:**
```
internal object SessionBackupExporter {
    sealed class BackupResult { object Success; data class Failure(cause: Throwable?) }
    
    // Testbarer Entry-Point: schreibt ZIP in beliebigen OutputStream
    internal fun exportToStream(sessionsRoot: File, sessionIds: List<String>, outputStream: OutputStream): BackupResult
    
    // Produktiver Entry-Point: öffnet OutputStream via ContentResolver, ruft exportToStream auf
    suspend fun export(sessionsRoot: File, sessionIds: List<String>, destinationUri: Uri, contentResolver: ContentResolver): BackupResult
}
```

### Neue Testdateien

| Datei | Typ | Beschreibung |
|---|---|---|
| `app/src/test/.../ui/camera/SessionBackupExporterTest.kt` | Unit-Test (JVM) | Tests gegen `exportToStream()`: ZIP-Struktur, Byte-Integrität, Fehlerbehandlung |
| `app/src/androidTest/.../storage/SessionBackupExporterInstrumentedTest.kt` | Instrumentation | ZIP-Schreiben auf echtes Dateisystem; Byte-Identität mit Quelldateien |

---

## 5. Exakte UI-Änderungen — CompareScreen

### 5.1 Neue Parameter

Zur bestehenden Signatur werden hinzugefügt:

```kotlin
sessionId: String? = null,                         // Neu: für SAF-Dateiname + Backup-Precondition
onBackupSession: ((destinationUri: Uri) -> Unit)? = null,  // Neu: callback nach SAF-Picker-Bestätigung
isBackupInProgress: Boolean = false                 // Neu: UI-Zustand während Export
```

Bestehende Parameter bleiben unverändert: `referenceImageUri`, `captureImageUri`, `onBack`, `timestamp`, `onDelete`, `sessionTitle`, `onSaveTitle`, `modifier`.

### 5.2 SAF-Launcher

In der CompareScreen-Composable wird registriert:

```kotlin
val createDocumentLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/zip")
) { uri ->
    if (uri != null) onBackupSession?.invoke(uri)
}
```

### 5.3 Overflow-Menü — vollständige neue Struktur

Das bestehende Overflow-Menü (`showMoreMenu`) wird um ein drittes Item erweitert. Die vollständige Struktur nach der Änderung:

```
⋮  (sichtbar wenn: onSaveTitle != null ODER sessionId != null)
├── Edit Title              [wenn onSaveTitle != null]
├── Remove Title            [wenn onSaveTitle != null UND currentTitle nicht leer]
└── Backup Session          [wenn sessionId != null — disabled wenn isBackupInProgress]
```

**Delete Session bleibt unveränderter TopAppBar-Icon-Button.** Keine Änderung an dessen Position oder Verhalten.

### 5.4 Backup-Aktion

Beim Tap auf "Backup Session":
1. `showMoreMenu = false`
2. `createDocumentLauncher.launch("SameView_${sessionId}.zip")`

### 5.5 Loading-State

Während `isBackupInProgress == true`:
- "Backup Session" DropdownMenuItem ist `enabled = false`

### 5.6 Sichtbarkeits-Precondition

"Backup Session" erscheint im Overflow ausschließlich wenn `sessionId != null`. Wenn `CompareScreen` ohne Session-Kontext geöffnet wird (transientes Compare direkt nach Capture ohne gespeicherte Session), bleibt "Backup Session" unsichtbar.

---

## 6. Exakte UI-Änderungen — CompareLibraryScreen

### 6.1 Wichtige Vorab-Feststellung: Select All ist bereits implementiert

**Select All / Deselect All ist im aktuellen Code vollständig implementiert** (`CompareLibraryScreen.kt`, Zeilen 157–176, `testTag = "compare_library_select_all_toggle"`). Die String-Ressourcen `compare_library_select_all` und `compare_library_deselect_all` sind ebenfalls bereits in `strings.xml` vorhanden.

Block 3 muss die Select-All-Funktionalität **nicht neu implementieren**. Scope für Block 3 ist ausschließlich das Backup-Icon.

### 6.2 Neue Parameter

Zur bestehenden Signatur werden hinzugefügt:

```kotlin
onBackupSessions: (sessionIds: List<String>, destinationUri: Uri) -> Unit = { _, _ -> },
isBackupInProgress: Boolean = false,
isDeletionInProgress: Boolean = false
```

Bestehende Parameter bleiben unverändert: `sessions`, `onRefresh`, `onSessionClick`, `onBack`, `onDeleteSessions`, `modifier`.

### 6.3 SAF-Launcher

In der CompareLibraryScreen-Composable wird registriert:

```kotlin
val createDocumentLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/zip")
) { uri ->
    if (uri != null) onBackupSessions(selectedSessionIds.toList(), uri)
}
```

### 6.4 Multi-Select-Aktionsleiste — vollständige neue Struktur

```
[N selected]  ·  [Select All/Deselect All]  [Backup-Icon]  [Delete-Icon]
```

Die topBar-Actions-Reihenfolge in Composable-Code (von links nach rechts in der Leiste):
1. **Select All / Deselect All** — bereits vorhanden; keine Änderung
2. **Backup-Icon** — NEU
3. **Delete-Icon** — unverändert

### 6.5 Backup-Icon-Verhalten

- Icon: `Icons.Default.Archive` oder `Icons.Outlined.FolderZip` (exakte Icon-Wahl: Implementierungsentscheidung)
- `enabled = selectedSessionIds.isNotEmpty() && !isBackupInProgress && !isDeletionInProgress`
- `contentDescription = stringResource(R.string.compare_library_action_backup)`
- `testTag = "compare_library_backup_button"`

### 6.6 Backup-Aktion

Beim Tap auf Backup-Icon:
```kotlin
val ids = selectedSessionIds.toList()
val suggestedFilename = if (ids.size == 1) {
    context.getString(R.string.session_backup_filename_single, ids[0])
} else {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    context.getString(R.string.session_backup_filename_multi, timestamp)
}
createDocumentLauncher.launch(suggestedFilename)
```

### 6.7 Delete-Icon-Verhalten (Änderung)

Das Delete-Icon erhält zusätzliche `enabled`-Einschränkung:
- `enabled = selectedSessionIds.isNotEmpty() && !isBackupInProgress`

### 6.8 Multi-Select-Modus bleibt nach Backup aktiv

Nach erfolgreichem oder fehlgeschlagenem Backup: Multi-Select-Modus bleibt aktiv, Auswahl bleibt erhalten. Keine automatische Deselektierung durch die Backup-Operation.

---

## 7. ViewModel-/State-Erweiterungen

### 7.1 CameraUiState — neue Felder

```kotlin
val isBackupInProgress: Boolean = false,
val isDeletionInProgress: Boolean = false
```

### 7.2 UiEvent — neue Events

Keine neuen Event-Typen erforderlich. Bestehender `UiEvent.ShowSnackbar` wird für Backup-Success und Backup-Error genutzt.

### 7.3 CameraViewModel — neue Funktionen

```kotlin
// Backup-Operation für eine oder mehrere Sessions
fun backupSessions(sessionIds: List<String>, destinationUri: Uri)

// Convenience-Wrapper für CompareScreen (Einzel-Session)
fun backupSingleSession(sessionId: String, destinationUri: Uri) =
    backupSessions(listOf(sessionId), destinationUri)
```

### 7.4 CameraViewModel — testbare Injection

Analog zu `sessionDeleter` und `sessionTitleUpdater`:
```kotlin
private var sessionBackupExporter: suspend (File, List<String>, Uri, ContentResolver) -> SessionBackupExporter.BackupResult =
    { root, ids, uri, cr -> SessionBackupExporter.export(root, ids, uri, cr) }
```

Der sekundäre interne Konstruktor für Unit-Tests wird um einen `sessionBackupExporter`-Parameter erweitert.

### 7.5 Bestehende deleteSessions() — Ergänzung

`deleteSessions()` und `deleteSession()` werden minimal ergänzt:
- Beim Start: `_uiState.update { it.copy(isDeletionInProgress = true) }`
- Am Ende (success oder failure, in `finally`-Block): `_uiState.update { it.copy(isDeletionInProgress = false) }`
- Guard am Anfang: `if (_uiState.value.isBackupInProgress) return` (kein Löschen während Backup)

---

## 8. SAF-Flow mit ACTION_CREATE_DOCUMENT

```
[User]
  │ tippt auf "Backup Session" (CompareScreen) oder Backup-Icon (CompareLibraryScreen)
  ▼
[Composable]
  │ createDocumentLauncher.launch(suggestedFilename)
  │   Intent: ACTION_CREATE_DOCUMENT, MIME: application/zip
  ▼
[OS SAF Picker]
  │ User wählt Speicherort (lokal, SD-Karte, SAF-Provider wie Google Drive)
  │ User bricht ab → null URI → kein weiterer Schritt, kein Feedback
  ▼
[Composable — launcher callback]
  │ uri != null → onBackupSession(uri) oder onBackupSessions(ids, uri)
  ▼
[CameraViewModel.backupSessions(sessionIds, destinationUri)]
  │ Guard: isBackupInProgress → return (no-op)
  │ Guard: isDeletionInProgress → return (no-op)
  │ _uiState.update { it.copy(isBackupInProgress = true) }
  │ launch(ioDispatcher) { ... }
  ▼
[SessionBackupExporter.export(sessionsRoot, sessionIds, destinationUri, contentResolver)]
  │ contentResolver.openOutputStream(destinationUri)
  │   → BufferedOutputStream
  │     → ZipOutputStream
  │       For each sessionId:
  │         For each file in [capture.jpg, reference.jpg, reference-original.jpg, metadata.json]:
  │           ZipEntry("${sessionId}/${filename}")
  │           FileInputStream → 8KB-Chunk-Copy → ZipEntry
  │       ZipOutputStream.close()
  │
  │ Erfolg → BackupResult.Success
  │ Fehler → contentResolver.delete(destinationUri, null, null)  [best-effort]
  │           → BackupResult.Failure(cause)
  ▼
[CameraViewModel — result handling]
  │ Success: emit ShowSnackbar(session_backup_success_single oder session_backup_success_multi, isSuccess=true)
  │ Failure: emit ShowSnackbar(session_backup_error, isSuccess=false)
  │ _uiState.update { it.copy(isBackupInProgress = false) }
```

---

## 9. ZIP-Erzeugungslogik

### 9.1 SessionBackupExporter.exportToStream()

```
Input:  sessionsRoot: File, sessionIds: List<String>, outputStream: OutputStream
Output: BackupResult

1. Erstelle BufferedOutputStream(outputStream) und ZipOutputStream
2. Für jede sessionId in sessionIds:
   a. Validiere sessionId (gleiche Regeln wie SessionDeleter: kein .., kein /, kein absoluter Pfad)
   b. sessionDir = File(sessionsRoot, sessionId)
   c. Prüfe: sessionDir.exists() && sessionDir.isDirectory — sonst IOException
   d. Für jeden Dateinamen in ["capture.jpg", "reference.jpg", "reference-original.jpg", "metadata.json"]:
      - sourceFile = File(sessionDir, filename)
      - Prüfe: sourceFile.exists() — sonst IOException
      - ZipEntry("${sessionId}/${filename}")
      - zipOut.putNextEntry(entry)
      - FileInputStream(sourceFile).use { input ->
          val buffer = ByteArray(8192)
          var bytesRead: Int
          while (input.read(buffer).also { bytesRead = it } != -1) {
              zipOut.write(buffer, 0, bytesRead)
          }
        }
      - zipOut.closeEntry()
3. zipOut.close() — schreibt EOCD-Record
4. BackupResult.Success
```

Jede Exception (`IOException`, `SecurityException`, `IllegalStateException`) wird gefangen und als `BackupResult.Failure(cause)` zurückgegeben. Das `try/catch` umfasst die gesamte Schleife. Kein Partial-Success.

### 9.2 Komprimierung

DEFLATED für alle Einträge (`ZipEntry.DEFLATED`). Für JPEGs ist die Kompressionsrate vernachlässigbar, aber konsistent mit der Spezifikation (Section 4.1).

### 9.3 Byte-Integrität

Keine Transformation. `capture.jpg`, `reference.jpg`, `reference-original.jpg` und `metadata.json` werden byteweise kopiert. Kein Re-Encoding, keine EXIF-Modifikation, keine GPS-Entfernung.

---

## 10. Operation-Lock / Race-Condition-Schutz

### 10.1 Lockregeln (aus der Produktentscheidung)

| Auslöser | Bedingung | Verhalten |
|---|---|---|
| Backup-Start | `isDeletionInProgress == true` | Abbruch; kein Backup; kein Feedback nötig (UI verhindert dies durch disabled Backup-Icon) |
| Backup-Start | `isBackupInProgress == true` | Abbruch; kein zweites Backup |
| Delete-Start | `isBackupInProgress == true` | Abbruch; kein Löschen; kein Feedback nötig (UI verhindert dies durch disabled Delete-Icon) |
| SAF-Picker cancelled | URI == null | No-op; kein State-Change |

### 10.2 Implementierung in CameraViewModel

```kotlin
fun backupSessions(sessionIds: List<String>, destinationUri: Uri) {
    val current = _uiState.value
    if (current.isBackupInProgress || current.isDeletionInProgress) return
    _uiState.update { it.copy(isBackupInProgress = true) }
    viewModelScope.launch(ioDispatcher) {
        val result = try {
            sessionBackupExporter(sessionsRoot, sessionIds, destinationUri, context.contentResolver)
        } catch (e: Exception) {
            SessionBackupExporter.BackupResult.Failure(e)
        }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(isBackupInProgress = false) }
            when (result) {
                is SessionBackupExporter.BackupResult.Success -> {
                    val msgRes = if (sessionIds.size == 1) R.string.session_backup_success_single
                                 else R.string.session_backup_success_multi
                    _uiEvents.emit(UiEvent.ShowSnackbar(msgRes, isSuccess = true,
                        formatArgs = if (sessionIds.size > 1) sessionIds.size else null))
                }
                is SessionBackupExporter.BackupResult.Failure -> {
                    _uiEvents.emit(UiEvent.ShowSnackbar(R.string.session_backup_error, isSuccess = false))
                }
            }
        }
    }
}
```

_Hinweis: `UiEvent.ShowSnackbar` unterstützt aktuell keinen `formatArgs`-Parameter. Wenn `session_backup_success_multi` einen Integer-Platzhalter `%d` hat, muss entweder `UiEvent.ShowSnackbar` erweitert werden (z. B. `count: Int? = null`) oder eine separate Event-Variante verwendet werden. Im Plan wird eine minimale Erweiterung um `count: Int? = null` vorgesehen._

### 10.3 UI-seitige Lock-Enforcement

CompareScreen:
- "Backup Session" DropdownMenuItem: `enabled = !isBackupInProgress`

CompareLibraryScreen:
- Backup-Icon: `enabled = selectedSessionIds.isNotEmpty() && !isBackupInProgress && !isDeletionInProgress`
- Delete-Icon: `enabled = selectedSessionIds.isNotEmpty() && !isBackupInProgress`

---

## 11. Fehlerbehandlung

### 11.1 All-or-Nothing

Kein Partial-ZIP wird ausgeliefert. Jede Exception während der ZIP-Erzeugung:
1. Fängt die gesamte Backup-Operation ab
2. Versucht `contentResolver.delete(destinationUri, null, null)` (best-effort; Fehlschlag wird ignoriert)
3. Emittiert `session_backup_error` Snackbar
4. Setzt `isBackupInProgress = false`

### 11.2 Spezifische Fehlerfälle

| Fehlerfall | Behandlung |
|---|---|
| Session-Verzeichnis fehlt zur Export-Zeit | IOException → Backup-Abbruch |
| Session-Datei fehlt (inkomplette Session) | IOException → Backup-Abbruch |
| SAF OutputStream nicht öffenbar | IOException → Backup-Abbruch; kein delete() nötig (nichts geschrieben) |
| SAF OutputStream mitten im Schreiben geschlossen | IOException → Backup-Abbruch; delete() attempt |
| Gerät out of storage | IOException → Backup-Abbruch; delete() attempt |
| SecurityException (SAF-Permission abgelaufen) | SecurityException → Backup-Abbruch; delete() attempt |
| User bricht SAF-Picker ab | null URI → no-op; kein Snackbar; kein State-Change |
| Backup läuft bereits | isBackupInProgress Guard → no-op |
| Löschen läuft bereits | isDeletionInProgress Guard → no-op |

### 11.3 Kein Crash, kein Silent Failure

Jeder Fehler erzeugt eine sichtbare Nutzer-Snackbar. Kein stiller Fehlschlag erlaubt. Nach jedem Fehler bleibt die App in einem vollständig nutzbaren Zustand.

---

## 12. i18n / String-Keys

### 12.1 Neue String-Ressourcen (müssen in strings.xml hinzugefügt werden)

```xml
<!-- Session Backup Export -->
<string name="compare_screen_overflow_backup_session">Backup session</string>
<string name="compare_library_action_backup">Backup selected</string>
<string name="session_backup_success_single">Session backed up</string>
<string name="session_backup_success_multi">%d sessions backed up</string>
<string name="session_backup_error">Backup failed</string>
<string name="session_backup_filename_single" translatable="false">SameView_%s.zip</string>
<string name="session_backup_filename_multi" translatable="false">SameView_Backup_%s.zip</string>
```

### 12.2 Bereits vorhandene Strings (kein Hinzufügen nötig)

Die folgenden Strings sind **bereits in strings.xml vorhanden** und werden wiederverwendet:

```xml
<string name="compare_library_select_all">Select all</string>      <!-- bereits vorhanden, Zeile 29 -->
<string name="compare_library_deselect_all">Clear selection</string>  <!-- bereits vorhanden, Zeile 30 -->
```

Der Select-All/Deselect-All-Toggle ist auch im Code bereits implementiert — keine neuen Strings für dieses Feature nötig.

### 12.3 Naming-Convention

Die Schlüssel folgen der bestehenden Konvention des Projekts (`<screen>_<bereich>_<aktion>`). Keine zweite Namenskonvention wird eingeführt.

---

## 13. Tests

### 13.1 Unit Tests (JVM, `app/src/test/...`)

**Neue Datei: `ui/camera/SessionBackupExporterTest.kt`**

| # | Test |
|---|---|
| T-U-01 | Einzelne Session: ZIP enthält genau ein Subdirectory mit korrekter sessionId |
| T-U-02 | Mehrere Sessions: ZIP enthält N Subdirectories, eine pro sessionId |
| T-U-03 | Jedes Subdirectory enthält alle vier Pflichtdateien (capture.jpg, reference.jpg, reference-original.jpg, metadata.json) |
| T-U-04 | Dateien im ZIP sind Byte-identisch mit den Quelldateien |
| T-U-05 | Einzel-Session-Dateiname: `SameView_<sessionId>.zip` (via `session_backup_filename_single`) |
| T-U-06 | Multi-Session-Dateiname: `SameView_Backup_<timestamp>.zip` (via `session_backup_filename_multi`) |
| T-U-07 | `session.id` in metadata.json stimmt mit dem ZIP-Subdirectory-Namen überein |
| T-U-08 | IOException beim Lesen einer Session-Datei → BackupResult.Failure |
| T-U-09 | Fehlende Session-Datei → BackupResult.Failure; kein malformed ZIP-Entry |
| T-U-10 | Nicht existentes Session-Verzeichnis → BackupResult.Failure |
| T-U-11 | Path-Traversal-Versuch als sessionId (z. B. `../other`) → BackupResult.Failure (Validierung analog SessionDeleter) |

**Erweiterte Datei: `ui/camera/CameraViewModelTest.kt`**

| # | Test |
|---|---|
| T-U-12 | `backupSessions()`: isBackupInProgress ist true während Export, false danach |
| T-U-13 | `backupSessions()` mit Erfolg: emittiert `session_backup_success_single` für 1 Session |
| T-U-14 | `backupSessions()` mit Erfolg: emittiert `session_backup_success_multi` mit korrekter Anzahl für N Sessions |
| T-U-15 | `backupSessions()` bei Fehler: emittiert `session_backup_error` Snackbar |
| T-U-16 | Operation-Lock: `backupSessions()` wenn `isDeletionInProgress == true` → no-op |
| T-U-17 | Operation-Lock: `deleteSessions()` wenn `isBackupInProgress == true` → no-op |
| T-U-18 | Operation-Lock: zweiter `backupSessions()`-Call während laufendem Backup → no-op |

### 13.2 Instrumentation Tests (`app/src/androidTest/...`)

**Neue Datei: `storage/SessionBackupExporterInstrumentedTest.kt`**

| # | Test |
|---|---|
| T-I-01 | Einzelne Session: schreibt ZIP in temporäre Datei, öffnet mit ZipInputStream, verifiziert Subdirectory + 4 Dateien |
| T-I-02 | Mehrere Sessions: ZIP enthält N korrekte Session-Subdirectories |
| T-I-03 | Byte-Integrität: Quelldateien und extrahierte ZIP-Entries sind byte-identisch |
| T-I-04 | Fehler-Cleanup: bei simuliertem IOException wird delete() auf destinationUri aufgerufen |

**Erweiterte Datei: `ui/compare/CompareScreenTest.kt`**

| # | Test |
|---|---|
| T-I-05 | "Backup Session" erscheint im Overflow-Menü wenn sessionId != null |
| T-I-06 | "Backup Session" erscheint NICHT im Overflow-Menü wenn sessionId == null |
| T-I-07 | "Backup Session" ist disabled wenn isBackupInProgress == true |
| T-I-08 | Bestehende Tests (Delete, Edit Title, Remove Title, Slider, Fullscreen, Navigation) bleiben grün |

**Erweiterte Datei: `ui/compare/CompareLibraryScreenTest.kt`**

| # | Test |
|---|---|
| T-I-09 | Backup-Icon erscheint in Multi-Select-Aktionsleiste |
| T-I-10 | Backup-Icon ist disabled wenn keine Sessions ausgewählt |
| T-I-11 | Backup-Icon ist disabled wenn isBackupInProgress == true |
| T-I-12 | Backup-Icon ist disabled wenn isDeletionInProgress == true |
| T-I-13 | Delete-Icon ist disabled wenn isBackupInProgress == true |
| T-I-14 | Select-All-Toggle (bereits implementiert): Bestehende Tests bleiben grün |

### 13.3 Regression-Schutz

Diese Tests dürfen durch das Backup-Feature **nicht rot werden**:

- Alle bestehenden `CompareScreenTest`-Tests
- Alle bestehenden `CompareLibraryScreenTest`-Tests
- Alle bestehenden `CompareNavigationTest`-Tests
- Alle bestehenden `CameraViewModelTest`-Tests
- Alle bestehenden `SessionDeleterTest`-Tests
- Alle bestehenden Snackbar-Replay-Protection-Tests
- Alle bestehenden SessionStorage- und SessionScanner-Tests

---

## 14. Release-/Privacy-/Play-Store-Bewertung

| Aspekt | Bewertung |
|---|---|
| Neue Permissions | Keine. `ACTION_CREATE_DOCUMENT` benötigt keine zusätzliche Permission. |
| INTERNET-Permission | Nicht hinzugefügt, nicht verwendet. |
| Netzwerkzugriffe | Keine. SAF-Destination ist OS-Sache; ggf. Cloud-Upload geschieht außerhalb des App-Prozesses. |
| GPS-Daten im Export | Enthalten (no-stripping). Gemäß SESSION_BACKUP_EXPORT_V1.md Abschnitt 5.3 und 12.2 — kein zusätzlicher Warning-Dialog. Konsistent mit bestehendem Verhalten (GPS wird bereits in Session-Dateien gespeichert). |
| Data Safety Form | Kein neuer Eintrag erforderlich. Backup ist user-initiated lokale Schreiboperation; bestehende Camera- und GPS-Deklarationen decken das Feature ab (SESSION_BACKUP_EXPORT_V1.md Abschnitt 12.3). |
| FileProvider (RELEASE_HARDENING_AUDIT Block D) | Kein Prerequisite für dieses Feature. Die `file://`-URIs werden nicht an externe Apps weitergegeben; Streaming direkt via SAF OutputStream. Block D bleibt für zukünftiges Share-Feature offen. |
| Closed-Testing-Impact | Kein neuer Play-Store-Blocker eingeführt. Bestehende offene Blocker (PS-01/PS-02 Privacy Policy + Data Safety) bleiben unverändert. |

---

## 15. Empfohlene Implementierungsblöcke

### Block 1 — Core Exporter + Unit Tests

**Scope:**
- `SessionBackupExporter.kt` anlegen (neue Datei)
- `BackupResult` sealed class definieren
- `exportToStream()` implementieren (JVM-pure, kein Android-Context)
- `export()` implementieren (ContentResolver-Wrapper)
- `SessionBackupExporterTest.kt` anlegen (T-U-01 bis T-U-11)
- `SessionBackupExporterInstrumentedTest.kt` anlegen (T-I-01 bis T-I-04)

**Berührt:** Nur neue Dateien. Kein bestehender Code verändert.  
**Testbar:** `./gradlew :app:testDebugUnitTest --tests "*.SessionBackupExporterTest"`  
**Risiko:** Niedrig — isolierter neuer Code ohne UI-Kopplung.

---

### Block 2 — CompareScreen Backup Session + SAF Flow + ViewModel

**Scope:**
- `CameraUiState` um `isBackupInProgress` und `isDeletionInProgress` erweitern
- `UiEvent.ShowSnackbar` um `count: Int? = null` erweitern (für `%d sessions backed up`)
- `CameraViewModel`: `backupSessions()`, `backupSingleSession()`, testbare Injection anlegen
- `CameraViewModel`: `deleteSessions()` und `deleteSession()` um `isDeletionInProgress`-Flag erweitern
- `CompareScreen.kt`: neue Parameter (`sessionId`, `onBackupSession`, `isBackupInProgress`), SAF-Launcher, "Backup Session" DropdownMenuItem
- `MainActivity.kt`: neue Parameter und Callbacks an `CompareScreen` durchleiten
- `strings.xml`: Backup-Strings hinzufügen
- `CameraViewModelTest.kt` erweitern (T-U-12 bis T-U-18)
- `CompareScreenTest.kt` erweitern (T-I-05 bis T-I-08)

**Berührt:** `CameraViewModel.kt`, `CompareScreen.kt`, `MainActivity.kt`, `strings.xml` (Änderungen), neue String-Ressourcen.  
**Risiko:** Mittel. Die `CompareScreen`-Parameteränderung muss an allen Call-Sites in `MainActivity.kt` korrekt durchgeleitet werden. `CameraUiState`-Erweiterungen sind additiv und nicht breaking.

---

### Block 3 — CompareLibrary Multi-Select Backup

**Scope:**
- `CompareLibraryScreen.kt`: neue Parameter (`onBackupSessions`, `isBackupInProgress`, `isDeletionInProgress`), SAF-Launcher, Backup-Icon
- `MainActivity.kt`: neue Parameter und Callbacks an `CompareLibraryScreen` durchleiten
- `CompareLibraryScreenTest.kt` erweitern (T-I-09 bis T-I-14)

**Hinweis Select All:** Select All / Deselect All und die zugehörigen Strings sind bereits vollständig implementiert. Kein Änderungsbedarf.

**Berührt:** `CompareLibraryScreen.kt`, `MainActivity.kt` (Änderungen).  
**Risiko:** Niedrig. Die bestehende Multi-Select-Infrastruktur bleibt unverändert; nur das Backup-Icon wird hinzugefügt.

---

### Block 4 — Operation-Lock Hardening + Regression Tests

**Scope:**
- Vollständige manuelle Verifikation der Lock-Guards in CameraViewModel
- UI-seitige Lock-Enforcement (enabled-Zustände für alle betroffenen Buttons) nochmals prüfen
- Regression-Testlauf: alle bestehenden Tests grün
- Sicherstellen: keine Snackbar-Replay-Regression durch neue Backup-Events

**Berührt:** Ggf. kleinere Korrekturen in vorherigen Blöcken.  
**Risiko:** Niedrig wenn Blöcke 1–3 sauber implementiert wurden.

---

### Block 5 — Final Verification / Release Smoke

**Scope:**
- Vollständiger `testDebugUnitTest` + `connectedDebugAndroidTest` grün
- Manueller Geräte-Smoke-Test:
  - Einzelne Session von CompareScreen backuppen
  - Mehrere Sessions von CompareLibrary backuppen (Select All + Backup)
  - SAF-Picker abbrechen → kein Snackbar
  - SAF-Picker bestätigen → Snackbar "Session backed up"
  - ZIP-Datei auf Gerät öffnen und Struktur prüfen
  - Backup während Löschoperation versuchen → kein Backup gestartet
- Release-Build-Smoke: Proguard-gebaute APK verifizieren

---

### Begründung der Block-Reihenfolge

Die gewählte Reihenfolge (1→2→3→4→5) entspricht dem Prinzip "Kern zuerst, UI danach":

- Block 1 schafft den testbaren Exporter ohne jede UI-Kopplung
- Block 2 fügt den wichtigsten Einstiegspunkt (CompareScreen) hinzu; hier liegt die größte Code-Komplexität (ViewModel-State, SAF-Flow)
- Block 3 ist leichter als Block 2 weil der Exporter und die ViewModel-Infrastruktur bereits existieren
- Block 4 ist explizit als Hardening-Block reserviert, damit Regression-Risiken isoliert adressiert werden können
- Block 5 ist der Verification-Checkpoint vor Release

---

## 16. Zusammenfassung: Geänderte und neue Dateien

### Geänderte Dateien (bestehend → modifiziert)

| Datei | Art der Änderung |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/camera/CameraViewModel.kt` | `CameraUiState` + `UiEvent` erweitern; `backupSessions()` + `backupSingleSession()` hinzufügen; `deleteSessions()` + `deleteSession()` um Lock-Flag erweitern |
| `app/src/main/java/com/isardomains/sameview/ui/compare/CompareScreen.kt` | 3 neue Parameter; SAF-Launcher; "Backup Session" DropdownMenuItem |
| `app/src/main/java/com/isardomains/sameview/ui/compare/CompareLibraryScreen.kt` | 3 neue Parameter; SAF-Launcher; Backup-Icon in Multi-Select-Aktionsleiste; enabled-Guard für Delete-Icon |
| `app/src/main/java/com/isardomains/sameview/MainActivity.kt` | Backup-Callbacks an CompareScreen und CompareLibraryScreen durchleiten; isBackupInProgress/isDeletionInProgress aus uiState beobachten und übergeben |
| `app/src/main/res/values/strings.xml` | 7 neue String-Keys |
| `app/src/test/java/com/isardomains/sameview/ui/camera/CameraViewModelTest.kt` | Backup-Tests T-U-12 bis T-U-18 hinzufügen |
| `app/src/androidTest/java/com/isardomains/sameview/ui/compare/CompareScreenTest.kt` | Backup-Tests T-I-05 bis T-I-08 hinzufügen |
| `app/src/androidTest/java/com/isardomains/sameview/ui/compare/CompareLibraryScreenTest.kt` | Backup-Tests T-I-09 bis T-I-14 hinzufügen |

### Neue Dateien

| Datei | Inhalt |
|---|---|
| `app/src/main/java/com/isardomains/sameview/ui/camera/SessionBackupExporter.kt` | Core-Exporter-Logik, `BackupResult` sealed class |
| `app/src/test/java/com/isardomains/sameview/ui/camera/SessionBackupExporterTest.kt` | Unit-Tests T-U-01 bis T-U-11 |
| `app/src/androidTest/java/com/isardomains/sameview/storage/SessionBackupExporterInstrumentedTest.kt` | Instrumentation-Tests T-I-01 bis T-I-04 |

### Explizit NICHT veränderte Dateien

`SessionDeleter.kt`, `SessionStorage.kt`, `SessionScanner.kt`, `ReferenceRenderer.kt`, alle GPS-bezogenen Klassen, `MediaStoreWriter.kt`, alle Settings-Klassen, alle Camera-bezogenen Klassen außer `CameraViewModel.kt`.

---

## 17. Offene Risiken

| # | Risiko | Einschätzung | Mitigierung |
|---|---|---|---|
| R-01 | `UiEvent.ShowSnackbar` unterstützt keinen `count`-Parameter für `%d sessions backed up` | Mittel | Im Plan: `count: Int? = null` zu `ShowSnackbar` hinzufügen; muss mit bestehenden Tests kompatibel bleiben (optionaler Parameter mit Default) |
| R-02 | `ContentResolver.delete()` schlägt bei manchen SAF-Providern still fehl (z. B. externer Provider verweigert Delete) | Niedrig | Dokumentiert in Spezifikation als best-effort; kein Auswirkung auf User-Feedback |
| R-03 | `CompareScreen` hat aktuell keinen `sessionId`-Parameter; alle Call-Sites in `MainActivity.kt` müssen erweitert werden | Mittel | `sessionId: String? = null` als default-Parameter; kein bestehender Call-Site bricht |
| R-04 | Sehr große Sessions (z. B. 10 Sessions × 15 MB = 150 MB) → Streaming in Chunks ist zwingend; OOM-Risiko bei naiver Implementierung | Mittel | Spezifikation schreibt Chunk-basiertes Kopieren vor; 8 KB-Buffer ist gesetzt |
| R-05 | `isDeletionInProgress` als neues CameraUiState-Feld: Modifikation von `deleteSessions()` und `deleteSession()` muss bestehende Delete-Tests nicht brechen | Mittel | Additiver Flag; bestehende Logik nicht verändert; Test-Erweiterung in Block 4 |
| R-06 | SAF-Launcher muss zur Compose-Kompositionszeit registriert sein (nicht lazy) — gilt für beide Screens | Niedrig | Standard-Compose-Constraint; mit `rememberLauncherForActivityResult` korrekt umsetzbar |

---

## 18. Relevante Testbefehle

```powershell
# Alle Unit-Tests (JVM)
.\gradlew.bat :app:testDebugUnitTest

# Spezifischer Unit-Test
.\gradlew.bat :app:testDebugUnitTest --tests "*.SessionBackupExporterTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*.CameraViewModelTest"

# Alle Instrumentierungstests (Gerät/Emulator erforderlich)
.\gradlew.bat :app:connectedDebugAndroidTest

# Spezifischer Instrumentierungstest
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.storage.SessionBackupExporterInstrumentedTest
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.compare.CompareScreenTest
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isardomains.sameview.ui.compare.CompareLibraryScreenTest

# Release-Build für Smoke-Test
.\gradlew.bat :app:assembleRelease
```

---

**Implementation Status: All Blocks 1–5 completed. 2026-06-01.**

---

## 19. Implementation Status

All Blocks 1–5 completed.

| Block | Scope | Status |
|---|---|---|
| Block 1 | SessionBackupExporter + Unit Tests | Completed |
| Block 2 | CompareScreen + ViewModel + SAF Flow | Completed |
| Block 3 | CompareLibrary Multi-Select Backup | Completed |
| Block 4 | Operation-Lock Hardening + Regression | Completed |
| Block 5 | Final Verification / Release Smoke | Completed |

Manual device smoke test passed.  
Completion date: 2026-06-01.
