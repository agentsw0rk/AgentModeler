# Structured I/O Types for Agent Editor

**Date:** 2026-03-28
**Status:** Approved

## Problem

Input/Output nodes currently support only a single flat field (name, dataType, description, required). Real Koog agents need structured data classes with multiple fields, each with its own type, description (`@LLMDescription`), and optionality.

## Decision

Replace the flat single-field I/O model with a multi-field structure. Every Input/Output node holds a list of typed fields.

## Data Model

### Frontend

```typescript
interface IOField {
  id: string;           // UUID
  name: string;         // e.g. "totalPrice"
  type: "String" | "Int" | "Double" | "Boolean" | "List" | "Map";
  description: string;  // → @LLMDescription(...)
  optional: boolean;    // → Type? = null
}

interface InputNodeConfig {
  className?: string;   // Override, otherwise auto-derived: AgentNameInput
  fields: IOField[];
}

interface OutputNodeConfig {
  className?: string;   // Override, otherwise auto-derived: AgentNameOutput
  fields: IOField[];
}
```

### Backend (Koog model)

```kotlin
data class KoogField(
    val name: String,
    val type: String,        // "String", "Int", "Double", "Boolean", "List", "Map"
    val description: String,
    val optional: Boolean
)

data class KoogInput(
    val className: String?,
    val fields: List<KoogField>
)

data class KoogOutput(
    val className: String?,
    val fields: List<KoogField>
)
```

## Type Mapping (Kotlin generation)

| Editor Type | Kotlin Type (required) | Kotlin Type (optional) |
|-------------|----------------------|----------------------|
| String      | String               | String? = null       |
| Int         | Int                  | Int? = null          |
| Double      | Double               | Double? = null       |
| Boolean     | Boolean              | Boolean? = null      |
| List        | List\<Any>           | List\<Any>? = null   |
| Map         | Map\<String, Any>    | Map\<String, Any>? = null |

## UI Design

### PropertiesPanel for Input/Output Nodes

1. **Class Name** (optional text field) — placeholder shows auto-generated name (e.g. "OrderAgentInput")
2. **Field List** — each field is a collapsible row:
   - Collapsed: shows field name + type badge
   - Expanded: name input, type dropdown, description textarea, optional toggle
3. **Add Field** button at bottom
4. **Drag-to-reorder** via handle on each row
5. **Delete** button per field

### Visual Style

Follows existing dark-theme PropertiesPanel patterns. Field rows use the same input/select/toggle components already in use.

## Code Generation

For an Output node named "FinalOrderResult" with fields `successful (Boolean)`, `comment (String, optional)`, `totalPrice (Int)`, `waitingTime (Int)`:

```kotlin
@Serializable
data class FinalOrderResult(
    @property:LLMDescription("Was the order successful?")
    val successful: Boolean,
    @property:LLMDescription("Optional comment containing information why the order couldn't be placed")
    val comment: String? = null,
    @property:LLMDescription("Total price of the order (in US dollars)")
    val totalPrice: Int,
    @property:LLMDescription("How long to wait (in minutes)")
    val waitingTime: Int
)
```

Rules:
- `@Serializable` annotation always present
- `@property:LLMDescription(...)` only when description is non-empty
- Required fields first, then optional fields (with `= null`)
- Class name: custom `className` if set, otherwise `{AgentName}Input` / `{AgentName}Output`

## Backend Changes

### GraphToKoogAdapter

- Extract `fields` array from node data map
- Extract optional `className` from node data map
- Each field entry: `{ id, name, type, description, optional }`
- Legacy migration: if node data has old format (`name`, `dataType`, `description`, `required` at top level), convert to single-field format

### InputOutputGenerator

- Rewrite to iterate over `KoogField` list
- Generate `@Serializable` data class with `@property:LLMDescription` per field
- Apply type mapping table
- Order: required fields first, then optional

### ExportService

- `ioClassNames()` now checks for custom `className` before auto-deriving
- Pass className through to generators

## Migration

Existing graphs with old single-field I/O format are migrated on load:

```
Old: { name: "query", description: "Search query", dataType: "string", required: true }
New: { fields: [{ id: uuid(), name: "query", type: "String", description: "Search query", optional: false }] }
```

This happens in the frontend (editorStore) when deserializing graph data.

## Scope

### In scope
- Multi-field I/O data model (frontend + backend)
- Field editor UI in PropertiesPanel
- Custom class name support
- Updated Kotlin code generation with `@LLMDescription`
- Legacy format migration
- Updated IOInspection component to show fields

### Out of scope
- Nested object types (fields within fields)
- Enum types
- Custom default values beyond null
- Validation rules per field
