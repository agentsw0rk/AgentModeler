# Built-in Tools for Koog Export

**Date:** 2026-03-23
**Status:** Approved

## Summary

Add 7 built-in tool types to the modeler — Think, Shell, Filesystem, Fetch, Memory, Todo, User Prompt. These appear as a new node type `"builtin-tool"` in the visual editor with tool-specific configuration properties. On export, they generate as Koog built-in tool registrations or ready-to-use Kotlin ToolSet classes in the Koog project ZIP.

## Scope

- 7 built-in tool types (Think, Shell, Filesystem, Fetch, Memory, Todo, User Prompt)
- Frontend: new node type, dropdown in "+Tool" button, tool-specific properties panel
- Backend: new model types, adapter extension, new generator, registry/agent/build-gradle generator extensions
- Multi-agent tools (Transfer Task, Background Agents, Handoff, A2A) are out of scope
- Script, LSP, API are deferred to a future iteration

## Built-in Tool Types

| Tool Type | Config Properties | Koog Implementation |
|---|---|---|
| `think` | — (none) | SystemPrompt extension, no ToolSet |
| `shell` | `workingDir?: String` | Custom `ProcessBuilder`-based ToolSet |
| `filesystem` | `rootDir?: String` | Koog built-in `ReadFileTool`, `WriteFileTool`, `ListDirectoryTool` |
| `fetch` | `baseUrl?: String` | Custom `java.net.http.HttpClient` ToolSet |
| `memory` | `path?: String` (DB path) | Custom SQLite key-value ToolSet |
| `todo` | — (none) | Custom in-memory task list ToolSet |
| `user_prompt` | — (none) | Koog built-in `AskUser`/`SayToUser` |

**Three categories of tool implementation:**
1. **Koog built-in** (filesystem, user_prompt): No generated ToolSet file. Uses framework-provided tools registered directly in ToolRegistry.
2. **Custom ToolSet** (shell, fetch, memory, todo): Generated ToolSet file with real implementation.
3. **Prompt-only** (think): No ToolSet, modifies system prompt.

## Frontend Changes

### Type System (`types/graph.ts`)

New discriminated types alongside existing ones:

```typescript
type BuiltinToolType = "think" | "shell" | "filesystem" | "fetch" | "memory" | "todo" | "user_prompt"

interface BuiltinToolConfig {
  toolType: BuiltinToolType
  workingDir?: string       // shell
  rootDir?: string          // filesystem
  baseUrl?: string          // fetch
  path?: string             // memory
}
```

Update existing `NodeType` type alias:
```typescript
type NodeType = "agent" | "tool" | "builtin-tool"
```

`GraphNode.data` becomes `AgentConfig | ToolConfig | BuiltinToolConfig`.
`BuiltinToolConfig` is defined in `types/graph.ts` (alongside `BuiltinToolType`), since it is a graph-level concept not a Koog-specific one.

### Rete Node (`lib/rete-nodes.ts`)

New `BuiltinToolReteNode` class:
- Same dimensions as `ToolReteNode` (200x80)
- Same output socket (`ToolSocket` → connects to Agent input)
- Label shows human-readable type name (e.g. "Shell", "Memory")
- Stores `storeNodeId` for store linkage
- Visually distinct from custom tools (different background color or badge to distinguish at a glance)

Update `AppNode` type union: `type AppNode = AgentReteNode | ToolReteNode | BuiltinToolReteNode`

### Toolbar (`components/panels/Toolbar.tsx`)

Note: Toolbar lives in `components/panels/Toolbar.tsx`, not `components/editor/`.

"+Tool" button becomes a dropdown:
```
+Tool ▾
├── Custom Tool        (existing behavior)
├── ─────────────
├── Think
├── Shell
├── Filesystem
├── Fetch
├── Memory
├── Todo
└── User Prompt
```

Each built-in option calls `addNode("builtin-tool", position, toolType)` with the appropriate `BuiltinToolType`.

### Editor Store (`store/editorStore.ts`)

`addNode` signature extended with optional `toolType` parameter:

```typescript
addNode: (type: NodeType, position: { x: number; y: number }, toolType?: BuiltinToolType) => void
```

Update `EditorState` interface accordingly.

Default configs per tool type:
```typescript
const builtinToolDefaults: Record<BuiltinToolType, BuiltinToolConfig> = {
  think:       { toolType: "think" },
  shell:       { toolType: "shell", workingDir: undefined },
  filesystem:  { toolType: "filesystem", rootDir: undefined },
  fetch:       { toolType: "fetch", baseUrl: undefined },
  memory:      { toolType: "memory", path: undefined },
  todo:        { toolType: "todo" },
  user_prompt: { toolType: "user_prompt" },
}
```

### Properties Panel

New `BuiltinToolProperties.tsx` component in `components/panels/`:
- Read-only label showing tool type name
- Static description text per tool type
- Dynamic fields based on `toolType`:
  - `shell` → `workingDir` input
  - `filesystem` → `rootDir` input
  - `fetch` → `baseUrl` input
  - `memory` → `path` input
  - `think`, `todo`, `user_prompt` → no additional fields, just description

`PropertiesPanel.tsx` gets a third case: `selectedNode.type === "builtin-tool"` → renders `BuiltinToolProperties`.

### Validation (`lib/validation.ts`)

New block for `"builtin-tool"` nodes:
- Must be connected to at least one Agent (same as custom tools)
- `toolType` must be present and a valid `BuiltinToolType` value
- No name/parameter validation needed (everything is predefined)

### Canvas Sync (`components/editor/Canvas.tsx`)

The existing ternary node creation must become a three-way branch:

```typescript
const reteNode =
  node.type === "agent" ? new AgentReteNode(...) :
  node.type === "builtin-tool" ? new BuiltinToolReteNode(...) :
  new ToolReteNode(...);
```

Connection pipe `instanceof` checks: `BuiltinToolReteNode` uses the same `ToolSocket`, so existing connection logic works without changes. If any `instanceof ToolReteNode` checks exist for non-connection purposes, they must also match `BuiltinToolReteNode` — resolve by checking socket type rather than node class.

### Persistence (`lib/persistence.ts`)

No changes needed — `ProjectGraph` serialization already handles arbitrary `GraphNode` types via JSON.

### Export (`lib/export-client.ts`, `components/dialogs/ExportDialog.tsx`)

No changes needed — `ProjectGraph` is sent as-is to the backend. The backend handles `"builtin-tool"` nodes.

## Backend Changes

### Koog Model (`export/model/KoogModel.kt`)

New types:

```kotlin
enum class BuiltinToolType {
    THINK, SHELL, FILESYSTEM, FETCH, MEMORY, TODO, USER_PROMPT
}

data class KoogBuiltinTool(
    val type: BuiltinToolType,
    val config: Map<String, Any?> = emptyMap()
)
```

Extended `KoogAgent`:

```kotlin
data class KoogAgent(
    val name: String,
    val systemPrompt: String,
    val modelProvider: ModelProvider,
    val modelId: String?,
    val temperature: Double?,
    val tools: List<KoogTool>,
    val builtinTools: List<KoogBuiltinTool>  // NEW
)
```

### GraphToKoogAdapter (`export/adapter/GraphToKoogAdapter.kt`)

Extended to handle `"builtin-tool"` nodes:

```kotlin
val builtinToolNodes = graph.nodes.filter { it.type == "builtin-tool" }

// For each agent: find connected builtin-tool nodes
val connectedBuiltinToolIds = graph.edges
    .filter { it.target == agentNode.id }
    .map { it.source }

val builtinTools = builtinToolNodes
    .filter { it.id in connectedBuiltinToolIds }
    .map { convertBuiltinTool(it) }
```

`convertBuiltinTool` parses `toolType` string from `node.data["toolType"]` and passes remaining data fields as `config`.

### GraphValidator (`export/validation/GraphValidator.kt`)

New `"builtin-tool"` branch in the existing `when (node.type)` block:
- `toolType` must be present and a valid `BuiltinToolType` value
- Must be connected to an Agent (edge check)

### BuiltinToolGenerator (NEW)

New `@Component` class: `com.agentwork.modeler.export.generator.BuiltinToolGenerator`

Method signature: `fun generate(tool: KoogBuiltinTool): String?`

Returns `null` for tools that use Koog framework built-ins (no file to generate):
- `THINK` — handled as system prompt extension by AgentGenerator
- `USER_PROMPT` — uses Koog built-in `AskUser`/`SayToUser`
- `FILESYSTEM` — uses Koog built-in `ReadFileTool`/`WriteFileTool`/`ListDirectoryTool`

Returns a complete Kotlin ToolSet class for custom implementations:

**SHELL → `ShellToolSet.kt`:**
```kotlin
package generated.tools

import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.reflect.ToolSet
import java.io.File

@LLMDescription("Tools for executing shell commands")
class ShellToolSet(private val workingDir: String = ".") : ToolSet {
    @Tool
    @LLMDescription("Execute a shell command and return its output")
    fun executeCommand(
        @LLMDescription("The shell command to execute") command: String
    ): String {
        val process = ProcessBuilder("sh", "-c", command)
            .directory(File(workingDir))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
        return output.take(100_000)
    }
}
```

**FETCH → `FetchToolSet.kt`:**
One tool: `fetch(url, method, body?): String` using `java.net.http.HttpClient`

**MEMORY → `MemoryToolSet.kt`:**
Three tools: `store(key, value): String`, `retrieve(key): String`, `listKeys(): String` using SQLite via `java.sql`

**TODO → `TodoToolSet.kt`:**
Four tools: `addTask(description): String`, `completeTask(id): String`, `listTasks(): String`, `removeTask(id): String` with in-memory `MutableList`

Config properties are passed as constructor parameters with defaults:
- `ShellToolSet(workingDir = ".")`
- `FetchToolSet(baseUrl = "")`
- `MemoryToolSet(path = "memory.db")`

### AgentGenerator Extension

When `THINK` is in `agent.builtinTools`, the generator appends `"\nThink step by step before answering."` to the system prompt in the generated `AgentConfig.kt`.

No signature change — `KoogAgent` already carries `builtinTools`.

### RegistryGenerator Extension

Signature changes to accept both tool lists:

```kotlin
fun generate(tools: List<KoogTool>, builtinTools: List<KoogBuiltinTool>): String
```

**Two registration patterns used:**

1. **`tools(instance)`** — for ToolSet-based tools (custom tools and custom built-in ToolSets):
   ```kotlin
   tools(ShellToolSet(workingDir = "/app"))
   tools(MemoryToolSet(path = "data.db"))
   ```

2. **`tool(singleton)`** — for Koog framework built-in tool objects:
   ```kotlin
   tool(AskUser)
   tool(SayToUser)
   tool(ReadFileTool(JVMFileSystemProvider.ReadWrite))
   tool(WriteFileTool(JVMFileSystemProvider.ReadWrite))
   tool(ListDirectoryTool(JVMFileSystemProvider.ReadWrite))
   ```

**Required imports for Koog built-ins:**
```kotlin
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.ext.tool.file.WriteFileTool
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.rag.base.files.JVMFileSystemProvider
```

**Per-type registration logic:**
- `THINK` → skipped (not a tool)
- `SHELL` → `tools(ShellToolSet(workingDir = "${config}"))`
- `FILESYSTEM` → `tool(ReadFileTool(...))`, `tool(WriteFileTool(...))`, `tool(ListDirectoryTool(...))`
- `FETCH` → `tools(FetchToolSet(baseUrl = "${config}"))`
- `MEMORY` → `tools(MemoryToolSet(path = "${config}"))`
- `TODO` → `tools(TodoToolSet())`
- `USER_PROMPT` → `tool(AskUser)`, `tool(SayToUser)`

### BuildGradleGenerator Extension

Adds conditional dependencies based on `agent.builtinTools`:

```kotlin
if (agent.builtinTools.any { it.type == BuiltinToolType.MEMORY }) {
    appendLine("""    implementation("org.xerial:sqlite-jdbc:$SQLITE_VERSION")""")
}
```

`SQLITE_VERSION` added as a companion object constant (same pattern as other version constants).

All other built-in tools use JDK standard library only (no extra dependencies).

### ExportService Extension

Extended to generate built-in tool files:

```kotlin
// Built-in tools
for (builtinTool in agent.builtinTools) {
    val code = builtinToolGenerator.generate(builtinTool)
    if (code != null) {
        val fileName = builtinTool.type.toFileName()
        files["$pkg/tools/$fileName"] = code
    }
}

// Registry: both custom and built-in
if (agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty()) {
    files["$pkg/ToolRegistry.kt"] = registryGenerator.generate(agent.tools, agent.builtinTools)
}
```

File naming uses a dedicated mapping function `BuiltinToolType.toFileName()` that returns PascalCase names:
- `SHELL` → `ShellToolSet.kt`
- `FETCH` → `FetchToolSet.kt`
- `MEMORY` → `MemoryToolSet.kt`
- `TODO` → `TodoToolSet.kt`
- Others → no file (generator returns null)

New constructor dependency: `builtinToolGenerator: BuiltinToolGenerator`

## Testing Strategy

### Backend
- `BuiltinToolGeneratorTest` — one test per tool type verifying generated code contains key elements (annotations, method names, correct config parameter usage)
- `BuiltinToolGeneratorTest` — test for `THINK`, `USER_PROMPT`, and `FILESYSTEM` returning `null`
- Updated `GraphToKoogAdapterTest` — test converting builtin-tool nodes
- Updated `GraphValidatorTest` — test builtin-tool validation (missing toolType, not connected, invalid toolType)
- Updated `RegistryGeneratorTest` — test mixed custom + built-in registration, verify correct imports for Koog built-ins
- Updated `AgentGeneratorTest` — test Think prompt extension
- Updated `BuildGradleGeneratorTest` — test Memory adds SQLite dependency, test without Memory omits it
- Updated `ExportServiceTest` — test ZIP contains built-in tool files, test Koog built-in tools don't create files

### Frontend
- Manual testing of dropdown, node creation, properties panel, connection, validation, export
