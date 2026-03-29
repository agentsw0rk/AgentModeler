# Feature 02: Shared Code Node

## Problem

Im Banking Tutorial referenzieren mehrere Tools geteilte Daten (`contactList`, `contactById`) und Domain-Klassen (`data class Contact`). Der Modeler hat kein Konzept für Code, der **zwischen Tools geteilt** wird.

## Lösung

Ein neuer Node-Typ **"Code"** auf dem Canvas. Der Inhalt wird als separate Kotlin-Datei exportiert und von allen verbundenen Tools automatisch importiert.

## Scope

### Neuer Node-Typ: `code`

- Platzierung auf dem Canvas wie andere Nodes
- Verbindung: `code` → `agent` (oder `code` → `tool`)
- Properties Panel:
  - **Dateiname** (z.B. `Domain`, `Contacts`) → wird zu `Domain.kt`
  - **Code** — Code-Editor (aus Feature 01) für freien Kotlin-Code

### Beispiel

Ein Code-Node mit Name `Domain` und Inhalt:
```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    val id: Int,
    val name: String,
    val surname: String? = null,
    val phoneNumber: String
)

val contactList = listOf(
    Contact(100, "Alice", "Smith", "+1 415 555 1234"),
    Contact(101, "Bob", "Johnson", "+49 151 23456789"),
)

val contactById = contactList.associateBy(Contact::id)
```

### Export

- Generierte Datei: `src/main/kotlin/generated/Domain.kt` mit `package generated`
- Der Code wird 1:1 in die Datei geschrieben (nach dem Package-Statement)
- Tools, die mit dem gleichen Agent verbunden sind, können den Code referenzieren (gleicher Package-Namespace)

### Frontend

| Datei | Änderung |
|-------|----------|
| `frontend/types/graph.ts` | `NodeType` um `"code"` erweitern |
| `frontend/components/nodes/CodeNode.tsx` | Neue Node-Komponente (Rete.js) |
| `frontend/components/panels/PropertiesPanel.tsx` | Panel für Code-Node (Dateiname + Code-Editor) |
| `frontend/store/editorStore.ts` | Default-Daten für `code` Node |
| `frontend/components/editor/Canvas.tsx` | Node-Factory für `code` Node |
| `frontend/components/editor/Toolbar.tsx` | "+ Code" Button |

### Backend

| Datei | Änderung |
|-------|----------|
| `export/model/KoogModel.kt` | `KoogSharedCode` Datenklasse (name, code) |
| `export/adapter/GraphToKoogAdapter.kt` | `code`-Nodes konvertieren |
| `export/generator/SharedCodeGenerator.kt` | Neuer Generator: `{Name}.kt` mit Code-Inhalt |
| `export/ExportService.kt` | SharedCode-Dateien in ZIP aufnehmen |

### Validierung

- Code-Node ohne Dateiname → Fehler
- Code-Node ohne verbundenen Agent → Warnung
- Doppelte Dateinamen → Fehler

## Akzeptanzkriterien

- [ ] Code-Node auf Canvas platzierbar und mit Agent verbindbar
- [ ] Freier Kotlin-Code im Code-Editor editierbar
- [ ] Export erzeugt separate `.kt`-Datei mit dem Code
- [ ] Tools im gleichen Agent können den Code referenzieren (gleicher Package)
- [ ] Banking Tutorial `contactList`/`contactById` nachbaubar
