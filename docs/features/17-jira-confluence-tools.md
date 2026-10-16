# Feature 17: Jira- und Confluence-Tools (Cloud)

## Problem

Agenten sollen Jira-Issues und Confluence-Seiten lesen und bearbeiten. Heute geht das nur ueber das generische Fetch-Builtin. Das kann aber keinen Auth-Header und keinen Request-Body senden, und das LLM muss die REST-Pfade selbst kennen. Ein eigener Node mit fertigen Aktionen ist fuer den Nutzer einfacher und fuer das LLM zuverlaessiger.

## Ziel

Zwei neue Builtin-Tools fuer Atlassian Cloud, die wie Shell oder Fetch als Node auf den Canvas gezogen werden:

1. **Builtin `jira`**: `JiraToolSet` mit `searchIssues`, `getIssue`, `createIssue`, `addComment`, `transitionIssue`
2. **Builtin `confluence`**: `ConfluenceToolSet` mit `searchPages`, `getPage`, `createPage`, `updatePage`
3. **Auth per API-Token** aus Umgebungsvariablen, nie im Graph-JSON oder im Export

## Voraussetzungen

| Abhaengigkeit | Status | Blocker? |
|---|---|---|
| Builtin-Tool-Mechanik (`BuiltinToolType`, `BuiltinToolGenerator`, `RegistryGenerator`) | Implementiert | Nein |
| `java.net.http.HttpClient` (wie `FetchToolSet`) | Verfuegbar (JVM) | Nein |
| `kotlinx.serialization.json` im generierten Projekt (kommt mit Koog) | Verfuegbar | Nein |
| Atlassian-Cloud-Account mit API-Token | Extern | Nein |

## Architektur

### Konfiguration und Auth

Pro Node gibt es ein Config-Feld: `baseUrl`, z.B. `https://firma.atlassian.net`. Jira und Confluence Cloud liegen unter derselben Adresse und nutzen denselben Token.

Die Zugangsdaten kommen zur Laufzeit aus zwei festen Umgebungsvariablen, die beide Tools teilen:

| Env-Variable | Inhalt |
|---|---|
| `ATLASSIAN_EMAIL` | E-Mail des Atlassian-Accounts |
| `ATLASSIAN_API_TOKEN` | API-Token (id.atlassian.com → Security → API tokens) |

Der generierte Code baut daraus `Authorization: Basic base64(email:token)`. Fehlt eine Variable, wirft der Konstruktor beim Start einen klaren Fehler und nicht erst beim ersten Tool-Aufruf.

Die Namen der Umgebungsvariablen sind bewusst fest und nicht konfigurierbar, solange niemand mehrere Atlassian-Accounts in einem Agenten braucht.

### Datenmodell

```kotlin
enum class BuiltinToolType {
    THINK, SHELL, FILESYSTEM, FETCH, MEMORY, TODO, USER_PROMPT, JIRA, CONFLUENCE;
    // toFileName(): JIRA -> "JiraToolSet.kt", CONFLUENCE -> "ConfluenceToolSet.kt"
}
```

Frontend: `"jira" | "confluence"` in `BuiltinToolType`. `baseUrl` gibt es schon in `BuiltinToolConfig` (vom Fetch-Tool).

### Jira-Aktionen (REST API v3)

| Tool | LLM-Parameter | Endpoint |
|---|---|---|
| `searchIssues` | `jql`, `maxResults` | `GET /rest/api/3/search/jql?jql=…&fields=summary,status,assignee` |
| `getIssue` | `issueKey` | `GET /rest/api/3/issue/{key}` |
| `createIssue` | `projectKey`, `issueType`, `summary`, `description` | `POST /rest/api/3/issue` |
| `addComment` | `issueKey`, `text` | `POST /rest/api/3/issue/{key}/comment` |
| `transitionIssue` | `issueKey`, `statusName` | `GET` dann `POST /rest/api/3/issue/{key}/transitions` |

Hinweise:
- v3 verlangt fuer `description` und Kommentare das Atlassian Document Format (ADF). Der generierte Code verpackt Klartext in ein minimales ADF-Dokument (ein Absatz pro Zeile). Das LLM liefert nur Text.
- `transitionIssue` nimmt den Statusnamen ("Done"), holt die verfuegbaren Transitions und waehlt die passende. Gibt es keine, kommt eine Fehlermeldung mit den moeglichen Namen zurueck.
- Das alte `/rest/api/3/search` ist bei Atlassian abgekuendigt. Deshalb wird `/search/jql` verwendet.

### Confluence-Aktionen

| Tool | LLM-Parameter | Endpoint |
|---|---|---|
| `searchPages` | `cql`, `limit` | `GET /wiki/rest/api/search?cql=…` (v1, v2 hat keine CQL-Suche) |
| `getPage` | `pageId` | `GET /wiki/api/v2/pages/{id}?body-format=storage` |
| `createPage` | `spaceKey`, `title`, `body`, `parentId?` | `GET /wiki/api/v2/spaces?keys=…` (Space-ID) dann `POST /wiki/api/v2/pages` |
| `updatePage` | `pageId`, `title`, `body` | `GET` (aktuelle Version) dann `PUT /wiki/api/v2/pages/{id}` mit `version.number + 1` |

`body` ist Confluence-Storage-Format (XHTML). Das steht so in der `@LLMDescription`, damit das LLM passendes Markup schreibt.

### Generierter Code (Ausschnitt)

```kotlin
@LLMDescription("Tools for reading and updating Jira issues")
class JiraToolSet(private val baseUrl: String) : ToolSet {
    private val client = HttpClient.newHttpClient()
    private val auth = "Basic " + Base64.getEncoder().encodeToString(
        "${requireEnv("ATLASSIAN_EMAIL")}:${requireEnv("ATLASSIAN_API_TOKEN")}".toByteArray()
    )

    @Tool
    @LLMDescription("Search Jira issues with JQL. Returns key, summary, status and assignee per issue.")
    fun searchIssues(
        @LLMDescription("JQL query, e.g. project = ABC AND status = 'In Progress'") jql: String,
        @LLMDescription("Maximum number of issues (1-50)") maxResults: Int,
    ): String = get("/rest/api/3/search/jql?jql=${encode(jql)}&maxResults=$maxResults&fields=summary,status,assignee")

    // ... weitere Tools, private get/post/put mit Auth- und JSON-Headern
}
```

Rueckgabe ist immer `"HTTP <status>\n<body>"` wie bei `FetchToolSet`. Fehler (4xx/5xx) gehen also als Text an das LLM, ohne Exception. Das LLM kann reagieren, z.B. auf eine falsche JQL.

Registry: `tools(JiraToolSet(baseUrl = "…"))` bzw. `tools(ConfluenceToolSet(baseUrl = "…"))`, in `RegistryGenerator` und `SubAgentGenerator` (dort steht die Builtin-Liste doppelt).

### Docker / Deployment

`DockerComposeGenerator` nimmt `ATLASSIAN_EMAIL` und `ATLASSIAN_API_TOKEN` in `environment:` auf, wenn ein Jira- oder Confluence-Node im Graph ist (wie heute die Provider-API-Keys).

### Frontend

- Toolbar: Eintraege "Jira" und "Confluence" neben "Fetch"
- `BuiltinToolProperties`: Feld "Atlassian URL" (`baseUrl`), Hinweistext zu den beiden Env-Variablen
- `editorStore`: Defaults `jira: { toolType: "jira" }`, `confluence: { toolType: "confluence" }`

### Validierung

Fehler, wenn `baseUrl` leer ist oder nicht mit `https://` beginnt. Keine Warnung fuer Ktor/Wasm: `GraphValidator` kennt das Export-Ziel nicht, und Fetch, Memory und Todo werden dort heute ebenso still uebergangen.

## Betroffene Dateien

### Backend

| Datei | Aenderung |
|---|---|
| `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt` | `JIRA`, `CONFLUENCE` in `BuiltinToolType` + `toFileName()` |
| `src/main/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGenerator.kt` | `generateJira`, `generateConfluence` |
| `src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt` | Registrierung der ToolSets |
| `src/main/kotlin/com/agentwork/modeler/export/generator/SubAgentGenerator.kt` | Registrierung der ToolSets |
| `src/main/kotlin/com/agentwork/modeler/export/generator/DockerComposeGenerator.kt` | Env-Variablen |
| `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt` | `validTypes` erweitern, `baseUrl`-Pruefung |

### Frontend

| Datei | Aenderung |
|---|---|
| `frontend/types/graph.ts` | `"jira"`, `"confluence"` in `BuiltinToolType`, `BUILTIN_TOOL_TYPES`, Labels |
| `frontend/components/panels/Toolbar.tsx` | Eintraege |
| `frontend/components/panels/BuiltinToolProperties.tsx` | Beschreibung + `baseUrl`-Feld |
| `frontend/store/editorStore.ts` | Defaults |

### Tests

| Datei | Aenderung |
|---|---|
| `src/test/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGeneratorTest.kt` | Generierter Code enthaelt alle Tools, Basic-Auth aus Env, ADF-Verpackung |
| `src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt` | Registrierung mit `baseUrl` |
| `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt` | Leere/ungueltige `baseUrl` |

## Platform-Einschraenkungen

| Backend | Verfuegbar? | Grund |
|---|---|---|
| Spring Boot (JVM) | Ja | |
| KMP Library | Nein (vorerst) | `java.net.http` ist JVM-only; braeuchte Ktor-Client |
| Ktor/Wasm | Nein (vorerst) | Wie KMP; `KtorRegistryGenerator` uebernimmt ohnehin nur Shell-Builtins |
| Live-Preview | Nein (vorerst) | `PreviewAgentBuilder` muesste die ToolSets zur Laufzeit bauen |

## Nicht im Scope

- Atlassian Data Center/Server (Bearer-PAT, REST v2)
- OAuth 2.0 (3LO) und Atlassian Remote MCP Server
- Anhaenge, Sprints/Boards (Agile API), Seiten loeschen
- Eigene Felder (Custom Fields) bei `createIssue`
- Mehrere Atlassian-Accounts pro Agent

## Akzeptanzkriterien

- [ ] Jira- und Confluence-Node lassen sich auf den Canvas ziehen, mit einem Agenten verbinden und konfigurieren
- [ ] Export (Spring Boot) enthaelt `JiraToolSet.kt` / `ConfluenceToolSet.kt`, registriert mit der konfigurierten `baseUrl`, und baut (Gradle Build Validation, Feature 07)
- [ ] Kein Token und keine E-Mail im Graph-JSON oder im exportierten Code; nur die Env-Variablennamen
- [ ] Fehlende Env-Variable fuehrt zu einer klaren Fehlermeldung beim Start
- [ ] Manueller Test gegen eine echte Cloud-Instanz: Issue suchen, anlegen, kommentieren, Status wechseln; Seite suchen, anlegen, aendern
- [ ] Bestehende Builtin-Tools exportieren unveraendert
