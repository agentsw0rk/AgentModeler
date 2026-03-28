# Advanced Custom Tools (ToolSet with Multiple Functions)

**Date:** 2026-03-28
**Status:** Approved

## Problem

Custom Tool nodes currently represent a single function with flat parameters. Real Koog ToolSets group multiple `@Tool` functions under one class, each with its own `@LLMDescription`, parameters with descriptions, and optional default values. The editor cannot model this.

## Decision

Replace the single-function tool model with a multi-function ToolSet model. Each tool node holds a ToolSet name, description, and a list of functions — each function with its own parameters.

## Data Model

### Frontend

```typescript
interface ToolParameter {
  id: string;
  name: string;
  type: "String" | "Int" | "Double" | "Float" | "Boolean" | "List<String>" | "Map<String, String>";
  description: string;
  required: boolean;
  defaultValue?: string;
}

interface ToolFunction {
  id: string;
  name: string;
  description: string;
  parameters: ToolParameter[];
}

interface ToolConfig {
  name: string;
  description: string;
  functions: ToolFunction[];
}
```

### Backend (Koog model)

```kotlin
enum class KoogParamType(val kotlinType: String) {
    STRING("String"),
    INT("Int"),
    DOUBLE("Double"),
    FLOAT("Float"),
    BOOLEAN("Boolean"),
    LIST_STRING("List<String>"),
    MAP_STRING_STRING("Map<String, String>")
}

data class KoogToolParameter(
    val name: String,
    val type: KoogParamType,
    val description: String,
    val required: Boolean,
    val defaultValue: String?
)

data class KoogToolFunction(
    val name: String,
    val description: String,
    val parameters: List<KoogToolParameter>
)

data class KoogTool(
    val name: String,
    val description: String,
    val functions: List<KoogToolFunction>
)
```

## Parameter Type Mapping

| Editor Type | Kotlin Type | Default Literal Example |
|---|---|---|
| String | String | `"hello"` |
| Int | Int | `42` |
| Double | Double | `3.14` |
| Float | Float | `1.0f` |
| Boolean | Boolean | `false` |
| List\<String> | List\<String> | `emptyList()` |
| Map\<String, String> | Map\<String, String> | `emptyMap()` |

## UI Design

### ToolProperties Panel (two-level collapsible)

1. **ToolSet Name** — text input (e.g. "MoneyTransferTools")
2. **ToolSet Description** — textarea (e.g. "Tools for money transfer operations.")
3. **Functions list** — collapsible rows with "Add Function" button
   - Collapsed: function name + parameter count badge
   - Expanded:
     - Function name input
     - Function description textarea
     - **Parameters list** — nested collapsible rows with "Add Parameter" button
       - Collapsed: parameter name + type badge
       - Expanded: name, type dropdown, description, required toggle, default value (when not required)
4. Drag-to-reorder on both function and parameter levels
5. Delete button on each function and parameter

### Visual Style

Follows existing dark-theme PropertiesPanel patterns. Reuses same collapsible row pattern from I/O field editor. Functions use slightly different background shade to visually distinguish nesting levels.

## Code Generation

For a ToolSet "MoneyTransferTools" with functions `getBalance` and `sendMoney`:

```kotlin
package generated.tools

import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.reflect.ToolSet

@LLMDescription("Tools for money transfer operations.")
class MoneyTransferTools : ToolSet {

    @Tool
    @LLMDescription("Returns the current balance.")
    fun getBalance(
        @LLMDescription("The unique identifier of the user.") userId: Int
    ): String {
        // TODO: Implement tool logic
        return "Not implemented"
    }

    @Tool
    @LLMDescription("Sends money from the user to a contact.")
    fun sendMoney(
        @LLMDescription("Sender user id.") senderId: Int,
        @LLMDescription("Amount in sender's default currency.") amount: Double,
        @LLMDescription("Whether the user confirmed.") confirmed: Boolean = false
    ): String {
        // TODO: Implement tool logic
        return "Not implemented"
    }
}
```

Rules:
- `@LLMDescription` on class = ToolSet description (always present if non-empty)
- `@Tool` + `@LLMDescription` on each function
- `@LLMDescription` on each parameter (only if description non-empty)
- Required parameters first, then optional with default values
- Default value literals: strings in quotes, numbers bare, booleans bare, collections use `emptyList()`/`emptyMap()`
- All functions return `String` (stub implementation)
- ClassName derived from ToolSet name: `name.replaceFirstChar { it.uppercase() }` (no extra "ToolSet" suffix if user already includes it)

## Backend Changes

### KoogModel.kt
- Remove old `KoogTool`, `KoogToolParameter`, `KoogParamType`
- Add new `KoogToolFunction`, updated `KoogToolParameter` (with `required`, `defaultValue`), updated `KoogParamType` (add DOUBLE, LIST_STRING, MAP_STRING_STRING), updated `KoogTool` (with `functions`)

### GraphToKoogAdapter
- `convertTool()` extracts `functions[]` from node data, each with nested `parameters[]`
- `parseParamType()` updated for new types

### ToolGenerator
- Iterate over `tool.functions` instead of generating single function
- Generate per-function `@Tool` + `@LLMDescription`
- Handle default values for optional parameters
- Class-level `@LLMDescription` from `tool.description`

### RegistryGenerator
- No changes needed — still instantiates ToolSet classes by name

### ExportService
- File naming: `${tool.name}.kt` (class name already is the ToolSet name)

## Migration

Legacy tool nodes (old format with `parameters` at top level, no `functions`) are migrated on load:

```
Old: { name: "search", description: "Search tool", parameters: [...] }
New: { name: "search", description: "Search tool", functions: [{ id: uuid(), name: "search", description: "Search tool", parameters: [...] }] }
```

Single function inherits the tool name and description. Parameters retain their existing structure but gain `required: true` and `defaultValue: undefined` defaults.

## Scope

### In scope
- Multi-function ToolSet data model (frontend + backend)
- Two-level collapsible function/parameter editor UI
- ToolSet-level and function-level descriptions
- Parameter default values
- Extended parameter types (Double, Float, List\<String>, Map\<String, String>)
- Updated Kotlin code generation
- Legacy format migration
- Updated tests

### Out of scope
- Custom return types (always String)
- Suspend functions
- Enum parameter types
- Nested object parameters
- Tool implementation code editing (always TODO stub)
