# Jira- und Confluence-Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two new builtin tool nodes, `jira` and `confluence`, that export as `JiraToolSet` / `ConfluenceToolSet` (Atlassian Cloud REST, API-token auth from env) in the Spring Boot export.

**Architecture:** Same path as the existing `fetch` builtin: `BuiltinToolType` enum → `BuiltinToolGenerator` (KotlinPoet) emits the ToolSet → `RegistryGenerator` / `SubAgentGenerator` register it → `GraphValidator` checks config → frontend adds the node type. Both generated classes get the same private HTTP/auth members from one generator helper.

**Tech Stack:** Kotlin 2.2, KotlinPoet 2.2, Spring Boot 4, Koog 1.3.0 (`ToolSet`, `@Tool`, `@LLMDescription`), `java.net.http.HttpClient` and `kotlinx.serialization.json` in generated code, Next.js 16 / React 19 frontend.

**Spec:** `docs/features/17-jira-confluence-tools.md`

## Global Constraints

- Atlassian **Cloud only**, Basic auth `base64("$email:$token")`.
- Env vars, fixed names, shared by both tools: `ATLASSIAN_EMAIL`, `ATLASSIAN_API_TOKEN`. Never store email or token in the graph or in generated code.
- Only node config: `baseUrl` (e.g. `https://firma.atlassian.net`), reusing `BuiltinToolConfig.baseUrl` / `config["baseUrl"]`.
- Graph `toolType` strings: `"jira"`, `"confluence"`. Enum: `JIRA`, `CONFLUENCE`. Files: `JiraToolSet.kt`, `ConfluenceToolSet.kt`, package `generated.tools`.
- Every tool returns `"HTTP <status>\n<body>"` like `FetchToolSet`. HTTP errors are text for the LLM, never exceptions.
- Spring Boot export only. Ktor/Wasm, KMP and live preview stay untouched.
- Generators use KotlinPoet (`FunSpec`, `TypeSpec`, `addStatement`), matching `generateFetch` in `BuiltinToolGenerator.kt`.
- Tests are JUnit 5 + `kotlin.test.assertContains`, the style of `BuiltinToolGeneratorTest`.
- Commits: conventional messages, no co-author or "Generated with" lines.

## Review Focus

1. **JQL/CQL with spaces, quotes, `=` or umlauts** (`project = "Mein Team" AND text ~ "Prüfung"`) must reach Atlassian intact, so the query is URL-encoded. Pinned in Task 1 and Task 2.
2. **Summary, description, comment or page body with `"`, `\`, newlines** must produce valid JSON, so bodies are built with `buildJsonObject`, never string concatenation. Pinned in Task 1.
3. **Missing `ATLASSIAN_EMAIL` / `ATLASSIAN_API_TOKEN`:** the app fails at startup with `"<NAME> is not set"`, not with an NPE on the first call. Pinned in Task 1.
4. **`baseUrl` with trailing slash** (`https://firma.atlassian.net/`) must not produce `//rest/...`. The generated class trims it. Pinned in Task 1.
5. **`transitionIssue` with a status in other case or unknown** ("done", "Fertig"): match case-insensitively on target status or transition name. If none matches, return the available names. Pinned in Task 1.

---

### Task 1: `JIRA` builtin type and `JiraToolSet` generator

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt:61-71`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGenerator.kt`
- Test: `src/test/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGeneratorTest.kt`

**Interfaces:**
- Produces: `BuiltinToolType.JIRA` (`toFileName()` = `"JiraToolSet.kt"`); generated `class JiraToolSet(baseUrl: String) : ToolSet`; private generator helper `atlassianMembers(): Pair<List<PropertySpec>, List<FunSpec>>`, which Task 2 reuses.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `generates JiraToolSet with all jira tools`() {
    val code = generator.generate(KoogBuiltinTool(BuiltinToolType.JIRA, mapOf("baseUrl" to "https://x.atlassian.net")))!!
    assertContains(code, "class JiraToolSet")
    listOf("fun searchIssues(", "fun getIssue(", "fun createIssue(", "fun addComment(", "fun transitionIssue(")
        .forEach { assertContains(code, it) }
    assertContains(code, "/rest/api/3/search/jql")
    assertContains(code, "/rest/api/3/issue")
}

@Test
fun `jira tool set authenticates with basic auth from env and fails fast when missing`() {
    val code = generator.generate(KoogBuiltinTool(BuiltinToolType.JIRA))!!
    assertContains(code, "\"ATLASSIAN_EMAIL\"")
    assertContains(code, "\"ATLASSIAN_API_TOKEN\"")
    assertContains(code, "is not set")
    assertContains(code, "\"Basic \"")
    assertContains(code, "Base64")
}

@Test
fun `jira tool set encodes queries, builds json safely and trims base url`() {
    val code = generator.generate(KoogBuiltinTool(BuiltinToolType.JIRA))!!
    assertContains(code, "URLEncoder.encode(")
    assertContains(code, "buildJsonObject")
    assertContains(code, "trimEnd('/')")
}

@Test
fun `jira tool set wraps text in ADF and resolves transitions by name`() {
    val code = generator.generate(KoogBuiltinTool(BuiltinToolType.JIRA))!!
    assertContains(code, "\"doc\"")
    assertContains(code, "\"paragraph\"")
    assertContains(code, "/transitions")
    assertContains(code, "ignoreCase = true")
    assertContains(code, "Available:")
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "BuiltinToolGeneratorTest"`
Expected: compilation FAIL, `Unresolved reference 'JIRA'`

- [ ] **Step 3: Add `JIRA` to `BuiltinToolType`** with `JIRA -> "JiraToolSet.kt"` in `toFileName()`. Add the `when` branch `BuiltinToolType.JIRA -> generateJira(tool)` in `BuiltinToolGenerator.generate`.

- [ ] **Step 4: Implement `private fun atlassianMembers()` in `BuiltinToolGenerator`.** It returns the members both ToolSets share:
  - constructor param `baseUrl: String`; property `private val baseUrl = baseUrl.trimEnd('/')`
  - `private val client = HttpClient.newHttpClient()`
  - `private val auth = "Basic " + Base64.getEncoder().encodeToString("${env("ATLASSIAN_EMAIL")}:${env("ATLASSIAN_API_TOKEN")}".toByteArray())`
  - `private fun env(name: String): String = System.getenv(name)?.takeIf { it.isNotBlank() } ?: error("$name is not set")`
  - `private fun call(method: String, path: String, body: JsonObject? = null): HttpResponse<String>`: headers `Authorization: auth`, `Accept: application/json`, plus `Content-Type: application/json` when `body != null`; `BodyPublishers.ofString(body.toString())` or `noBody()`
  - `private fun text(r: HttpResponse<String>): String = "HTTP " + r.statusCode() + "\n" + r.body()`
  - `private fun enc(s: String): String = URLEncoder.encode(s, Charsets.UTF_8)`

  Declare the property order so `auth` initializes in the constructor. That makes a missing env var fail at registry creation (Review Focus 3).

- [ ] **Step 5: Implement `private fun generateJira(tool: KoogBuiltinTool): String`.** Class `JiraToolSet`, `@LLMDescription("Tools for searching, reading, creating and updating Jira issues")`, members from `atlassianMembers()`, plus:
  - `private fun adf(text: String): JsonObject`: `{"type":"doc","version":1,"content":[...]}`, one `{"type":"paragraph","content":[{"type":"text","text":line}]}` per line. A blank line becomes `{"type":"paragraph"}` without `content`, because ADF forbids empty text nodes.
  - Tools. Each is `@Tool` + `@LLMDescription` on function and params, and returns `String`:

| Function | Params | Request |
|---|---|---|
| `searchIssues` | `jql: String`, `maxResults: Int` | `GET /rest/api/3/search/jql?jql=${enc(jql)}&maxResults=${maxResults.coerceIn(1, 50)}&fields=summary,status,assignee` |
| `getIssue` | `issueKey: String` | `GET /rest/api/3/issue/$issueKey` |
| `createIssue` | `projectKey`, `issueType`, `summary`, `description: String` | `POST /rest/api/3/issue` body `{"fields":{"project":{"key"},"issuetype":{"name"},"summary","description":adf(description)}}` |
| `addComment` | `issueKey`, `text: String` | `POST /rest/api/3/issue/$issueKey/comment` body `{"body":adf(text)}` |
| `transitionIssue` | `issueKey`, `statusName: String` | see below |

  `transitionIssue`: `GET /rest/api/3/issue/$issueKey/transitions`. If not 2xx, return `text(r)`. Parse with `Json.parseToJsonElement`. Pick the first transition whose `to.name` or `name` equals `statusName` with `ignoreCase = true`. If none matches, return `"No transition to '$statusName'. Available: " + names.joinToString()`. Otherwise `POST` the same path with `{"transition":{"id":id}}` and return `text(...)`.

  Param descriptions for the LLM: `jql` "JQL query, e.g. project = ABC AND status = 'In Progress'"; `maxResults` "Maximum number of issues (1-50)"; `description` / `text` "Plain text; line breaks start new paragraphs"; `statusName` "Target status name, e.g. Done".

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests "BuiltinToolGeneratorTest"`
Expected: PASS, including the existing Shell/Fetch/Memory/Todo tests

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt src/main/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGeneratorTest.kt
git commit -m "feat(export): generate JiraToolSet for Atlassian Cloud"
```

### Task 2: `CONFLUENCE` builtin type and `ConfluenceToolSet` generator

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGenerator.kt`
- Test: `src/test/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGeneratorTest.kt`

**Interfaces:**
- Consumes: `atlassianMembers()` (`call`, `text`, `enc`, `baseUrl`, `auth`) from Task 1.
- Produces: `BuiltinToolType.CONFLUENCE` (`toFileName()` = `"ConfluenceToolSet.kt"`); generated `class ConfluenceToolSet(baseUrl: String) : ToolSet`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `generates ConfluenceToolSet with all confluence tools`() {
    val code = generator.generate(KoogBuiltinTool(BuiltinToolType.CONFLUENCE))!!
    assertContains(code, "class ConfluenceToolSet")
    listOf("fun searchPages(", "fun getPage(", "fun createPage(", "fun updatePage(")
        .forEach { assertContains(code, it) }
    assertContains(code, "/wiki/rest/api/search?cql=")
    assertContains(code, "/wiki/api/v2/pages")
    assertContains(code, "body-format=storage")
    assertContains(code, "\"ATLASSIAN_API_TOKEN\"")
    assertContains(code, "URLEncoder.encode(")
}

@Test
fun `confluence tool set resolves space id and bumps page version`() {
    val code = generator.generate(KoogBuiltinTool(BuiltinToolType.CONFLUENCE))!!
    assertContains(code, "/wiki/api/v2/spaces?keys=")
    assertContains(code, "\"representation\"")
    assertContains(code, "\"storage\"")
    assertContains(code, "+ 1")
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "BuiltinToolGeneratorTest"`
Expected: compilation FAIL, `Unresolved reference 'CONFLUENCE'`

- [ ] **Step 3: Add `CONFLUENCE` to the enum and `toFileName()`, and the `when` branch to `generateConfluence(tool)`.**

- [ ] **Step 4: Implement `private fun generateConfluence(tool: KoogBuiltinTool): String`.** Class `ConfluenceToolSet`, `@LLMDescription("Tools for searching, reading, creating and updating Confluence pages")`, members from `atlassianMembers()`, plus these tools:

| Function | Params | Request |
|---|---|---|
| `searchPages` | `cql: String`, `limit: Int` | `GET /wiki/rest/api/search?cql=${enc(cql)}&limit=${limit.coerceIn(1, 50)}` |
| `getPage` | `pageId: String` | `GET /wiki/api/v2/pages/$pageId?body-format=storage` |
| `createPage` | `spaceKey`, `title`, `body`, `parentId: String` | `GET /wiki/api/v2/spaces?keys=${enc(spaceKey)}` → `results[0].id`; if empty → `"Space '$spaceKey' not found"`; then `POST /wiki/api/v2/pages` with `{"spaceId","status":"current","title","body":{"representation":"storage","value":body}}`, plus `"parentId"` only when `parentId.isNotBlank()` |
| `updatePage` | `pageId`, `title`, `body: String` | `GET /wiki/api/v2/pages/$pageId` → `version.number`; then `PUT /wiki/api/v2/pages/$pageId` with `{"id":pageId,"status":"current","title","body":{...storage...},"version":{"number": number + 1}}` |

  A non-2xx response on any first `GET` returns `text(r)` directly. Param descriptions: `cql` "CQL query, e.g. type = page AND text ~ \"onboarding\""; `body` "Page content in Confluence storage format (XHTML, e.g. <p>Text</p>)"; `parentId` "ID of the parent page, empty for a top-level page".

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "BuiltinToolGeneratorTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt src/main/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGeneratorTest.kt
git commit -m "feat(export): generate ConfluenceToolSet for Atlassian Cloud"
```

### Task 3: Register both ToolSets in agent and sub-agent registries

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt:25-27,70-85`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/SubAgentGenerator.kt:74-76,105-115`
- Test: `src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt`, `src/test/kotlin/com/agentwork/modeler/export/generator/SubAgentGeneratorTest.kt`

**Interfaces:**
- Consumes: `BuiltinToolType.JIRA`, `CONFLUENCE`; constructor `(baseUrl: String)`.
- Produces: registry line `tools(JiraToolSet(baseUrl = "<baseUrl>"))` / `tools(ConfluenceToolSet(baseUrl = "<baseUrl>"))`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// RegistryGeneratorTest
@Test
fun `generates registry with jira and confluence tool sets`() {
    val builtinTools = listOf(
        KoogBuiltinTool(BuiltinToolType.JIRA, mapOf("baseUrl" to "https://x.atlassian.net")),
        KoogBuiltinTool(BuiltinToolType.CONFLUENCE, mapOf("baseUrl" to "https://x.atlassian.net"))
    )
    val code = generator.generate(emptyList(), builtinTools)
    assertContains(code, "tools(JiraToolSet(baseUrl = \"https://x.atlassian.net\"))")
    assertContains(code, "tools(ConfluenceToolSet(baseUrl = \"https://x.atlassian.net\"))")
}
```

Add the same assertion for a sub-agent with a `JIRA` builtin to `SubAgentGeneratorTest`. Build the `KoogSubAgent` the way the existing tests in that file do, and assert on `generator.generateRegistry(subAgent)`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "RegistryGeneratorTest" --tests "SubAgentGeneratorTest"`
Expected: FAIL, the registry does not contain `JiraToolSet`

- [ ] **Step 3: Add `JIRA` and `CONFLUENCE` to the `customBuiltins` type set in both generators.** Add a `when` branch each, reading `config["baseUrl"] as? String ?: ""`, the same way the `FETCH` branch does.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "RegistryGeneratorTest" --tests "SubAgentGeneratorTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt src/main/kotlin/com/agentwork/modeler/export/generator/SubAgentGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt src/test/kotlin/com/agentwork/modeler/export/generator/SubAgentGeneratorTest.kt
git commit -m "feat(export): register Jira and Confluence tool sets"
```

### Task 4: Validate `jira` / `confluence` nodes

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt:70-86`
- Test: `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt`

**Interfaces:**
- Produces: error message `"[<id>] <Jira|Confluence> requires an https:// Atlassian URL"`.

- [ ] **Step 1: Write the failing tests** (same graph shape as `valid builtin-tool passes validation`)

```kotlin
@Test
fun `valid jira builtin passes validation`() {
    // toolType "jira", baseUrl "https://x.atlassian.net", connected to agent a1
    assertTrue(validator.validate(graph).isEmpty())
}

@Test
fun `reports confluence builtin without https base url`() {
    // two nodes: toolType "confluence" with no baseUrl, and with baseUrl "http://x.atlassian.net"
    assertTrue(errors.any { it.contains("requires an https:// Atlassian URL") })
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "GraphValidatorTest"`
Expected: FAIL, `Invalid toolType: jira`

- [ ] **Step 3: Add `"jira"` and `"confluence"` to `validTypes`.** For those two types, add the error when `(node.data["baseUrl"] as? String).orEmpty().startsWith("https://")` is false.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "GraphValidatorTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt
git commit -m "feat(validation): require https Atlassian URL for Jira and Confluence tools"
```

### Task 5: Pass Atlassian env vars in docker-compose

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/DockerComposeGenerator.kt`
- Test: `src/test/kotlin/com/agentwork/modeler/export/generator/DockerComposeGeneratorTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `passes atlassian credentials when agent uses jira`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        builtinTools = listOf(KoogBuiltinTool(BuiltinToolType.JIRA, mapOf("baseUrl" to "https://x.atlassian.net"))))
    val compose = generator.generate(agent)
    assertContains(compose, "ATLASSIAN_EMAIL: \${ATLASSIAN_EMAIL}")
    assertContains(compose, "ATLASSIAN_API_TOKEN: \${ATLASSIAN_API_TOKEN}")
}

@Test
fun `omits atlassian credentials without atlassian tools`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList())
    assertFalse(generator.generate(agent).contains("ATLASSIAN"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "DockerComposeGeneratorTest"`
Expected: FAIL on the first test

- [ ] **Step 3: Emit both lines in the `app` `environment:` block** when any builtin in `agent.builtinTools + agent.subAgents.flatMap { it.builtinTools }` is `JIRA` or `CONFLUENCE`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "DockerComposeGeneratorTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/DockerComposeGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/DockerComposeGeneratorTest.kt
git commit -m "feat(export): pass Atlassian credentials to docker-compose"
```

### Task 6: Frontend nodes "Jira" and "Confluence"

**Files:**
- Modify: `frontend/types/graph.ts:10-22`
- Modify: `frontend/components/panels/Toolbar.tsx:22-30`
- Modify: `frontend/store/editorStore.ts:119-127`
- Modify: `frontend/components/panels/BuiltinToolProperties.tsx:9-17,58-68`

**Interfaces:**
- Consumes: toolType strings `"jira"`, `"confluence"`, config field `baseUrl`.

- [ ] **Step 1: Add `"jira" | "confluence"` to `BuiltinToolType`, `BUILTIN_TOOL_TYPES` and `BUILTIN_TOOL_LABELS`** (labels `"Jira"`, `"Confluence"`). Also add Toolbar options and `editorStore` defaults `{ toolType: "jira" }` / `{ toolType: "confluence" }`.

- [ ] **Step 2: In `BuiltinToolProperties` add the descriptions:**
  - jira: `"Search, read, create, comment on and transition Jira Cloud issues."`
  - confluence: `"Search, read, create and update Confluence Cloud pages."`

  Then render a `baseUrl` input for `jira` and `confluence` (label `"Atlassian URL"`, placeholder `"https://your-company.atlassian.net"`). Below it, a hint: `"Credentials are read at runtime from ATLASSIAN_EMAIL and ATLASSIAN_API_TOKEN."` Reuse the Fetch input markup and widen its condition, rather than copying it.

- [ ] **Step 3: Verify types and lint**

Run: `cd frontend && npx tsc --noEmit && npm run lint`
Expected: no errors. `Record<BuiltinToolType, …>` objects fail type-checking if a key is missing, so this proves all maps were updated.

- [ ] **Step 4: Verify in the running app** (skill `run`): drag a Jira node onto the canvas and connect it to an agent. Set the URL, and check the validation panel shows no error. Clear the URL, and check the https error appears.

- [ ] **Step 5: Commit**

```bash
git add frontend/types/graph.ts frontend/components/panels/Toolbar.tsx frontend/store/editorStore.ts frontend/components/panels/BuiltinToolProperties.tsx
git commit -m "feat(frontend): add Jira and Confluence builtin tool nodes"
```

### Task 7: Exported project compiles, docs updated

**Files:**
- Modify: `README.md` (Tool-Node row: add Jira, Confluence to the builtin list)
- Modify: `docs/features/17-jira-confluence-tools.md` (tick acceptance criteria that are proven)

- [ ] **Step 1: Run the full backend suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. The test count is the previous count + the new tests.

- [ ] **Step 2: Export and compile a real project.** Start `./gradlew bootRun`. Then `POST /api/export` a graph with one OpenAI agent plus one `jira` and one `confluence` builtin (both `baseUrl: "https://x.atlassian.net"`, connected to the agent). Unzip into the scratchpad and run `<repo>/gradlew -p <dir> compileKotlin --no-daemon`.
Expected: BUILD SUCCESSFUL. This proves the KotlinPoet output, the imports (`kotlinx.serialization.json.*`, `java.net.http.*`, `java.util.Base64`) and the Koog `@Tool` param types (`Int`) compile against Koog 1.3.0. If `kotlinx.serialization.json` is not on the exported classpath, add it to `BuildGradleGenerator` only when an Atlassian tool is present, with a test.

- [ ] **Step 3: Check that no secrets were written:** `grep -r "ATLASSIAN_API_TOKEN" <dir>` shows only the env lookup and docker-compose, never a value.

- [ ] **Step 4: Update README and the spec checklist**, then commit

```bash
git add README.md docs/features/17-jira-confluence-tools.md
git commit -m "docs: document Jira and Confluence builtin tools"
```

The manual test against a real Atlassian Cloud instance (last acceptance criterion) needs the user's credentials. Leave it unticked and hand it over.
