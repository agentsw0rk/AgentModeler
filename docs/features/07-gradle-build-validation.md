# Feature 07: Gradle Build Validation

## Problem

Der Modeler generiert Kotlin-Code, aber der User hat kein Feedback ob der Code tatsächlich kompiliert. Die bestehende LLM-Validierung kann Syntax-Fehler übersehen. Echte Compile-Fehler werden erst nach dem Export sichtbar.

## Lösung

Die generierten Sources aus dem virtuellen Dateisystem werden in einem Docker-Container mit Gradle gebaut. Compile-Fehler werden geparst und im UI als strukturierte Fehlerliste angezeigt, gemappt auf die entsprechenden Nodes.

## Architektur

```
UI (Button) → Backend API → Docker Container (Gradle Build) → Parse Errors → UI (Fehlerliste)
```

### Docker Container

- **Dockerfile** im Projekt mitgeliefert — User baut das Image selbst (`docker build -t modeler-builder .`)
- Container enthält: JDK 21, Gradle, Kotlin Compiler, Kotlin/Native Toolchain
- Backend mountet die generierten Sources als Volume oder kopiert sie rein
- Gradle baut das Projekt, stdout/stderr wird captured
- Container wird nach dem Build entfernt (ephemeral)

### Plattformen

| Plattform | Gradle Task | Beschreibung |
|-----------|-------------|-------------|
| JVM | `compileKotlin` | Standard Kotlin/JVM Compilation |
| Native | `compileKotlinNative` | Kotlin/Native für CLI-Agents |

Der User wählt die Zielplattform (oder beide) im UI.

## Scope

### Backend

| Datei | Änderung |
|-------|----------|
| `Dockerfile.builder` | Neues Dockerfile mit JDK 21, Gradle, Kotlin/Native |
| `export/BuildService.kt` | Neuer Service: generiert Sources → startet Docker Container → parsed Output |
| `export/BuildController.kt` | REST Endpoint: `POST /api/build` mit Workspace-Graph als Body |
| `export/model/BuildResult.kt` | Datenmodell: `BuildResult` mit `errors: List<BuildError>`, `success: Boolean`, `log: String` |
| `export/model/BuildError.kt` | Datenmodell: `BuildError` mit `file`, `line`, `column`, `message`, `severity` |
| `export/parser/GradleErrorParser.kt` | Parser für Kotlin Compiler Errors aus Gradle Output |

### Frontend

| Datei | Änderung |
|-------|----------|
| Validate-Button | Dropdown mit "LLM Validate" (bestehend) und "Gradle Build" (neu) |
| `BuildErrorPanel.tsx` | Neues Panel: Liste der Compile-Errors, gemappt auf Node-IDs |
| API-Client | `POST /api/build` Call mit Workspace-Graph |

### Error-Mapping

Kotlin Compiler Errors referenzieren Dateipfade wie `src/main/kotlin/generated/AgentKt.kt:15:10`. Der Parser mappt:
- Dateiname → Node-ID (über den Generator, der weiß welcher Node welche Datei erzeugt)
- Zeile im generierten Code → ungefähre Position im Node (Body-Feld, Parameter, etc.)

### Validate-Button Umbau

Der bestehende Validate-Button wird zu einem Split-Button oder Dropdown:
- **LLM Validate** — bestehende Funktionalität (LLM prüft den Code)
- **Gradle Build (JVM)** — kompiliert mit `compileKotlin`
- **Gradle Build (Native)** — kompiliert mit `compileKotlinNative`

## Ablauf

1. User klickt "Gradle Build (JVM)" im Validate-Dropdown
2. Frontend sendet Workspace-Graph an `POST /api/build`
3. Backend generiert alle Files (wie beim Export)
4. Backend erstellt temp-Verzeichnis mit den Sources + `build.gradle.kts`
5. Backend startet Docker Container mit gemounteten Sources
6. Container führt `gradle compileKotlin` aus
7. Backend captured stdout/stderr
8. `GradleErrorParser` extrahiert strukturierte Errors
9. Backend mappt Datei/Zeile → Node-ID
10. Response: `BuildResult` mit Errors + Success-Flag
11. Frontend zeigt Errors im `BuildErrorPanel`, Nodes mit Fehlern werden markiert

## Akzeptanzkriterien

- [ ] Dockerfile im Projekt, baut ein Image mit JDK 21 + Gradle + Kotlin/Native
- [ ] `POST /api/build` Endpoint akzeptiert Workspace-Graph
- [ ] Gradle Build läuft im Docker Container (JVM + Native)
- [ ] Compile-Errors werden geparst und als strukturierte Liste zurückgegeben
- [ ] Errors werden auf Nodes gemappt (Dateiname → Node-ID)
- [ ] UI zeigt Fehler mit Datei, Zeile und Nachricht
- [ ] Validate-Button bietet Auswahl zwischen LLM und Gradle Build
- [ ] Nodes mit Compile-Errors werden visuell markiert (z.B. roter Rand)
- [ ] Build funktioniert für JVM und Native Plattformen
