# Code Explorer — Design Spec

## Overview

A virtual file system-backed code explorer for the Koog Agent Editor that shows generated code in real-time as users build their agent graph. Integrates as a left sidebar panel with an Activity Bar toggle and a modal code viewer with tabs.

## Decisions

| Decision | Choice |
|----------|--------|
| Layout | Activity Bar (40px) + Explorer Panel (240px) left side |
| Code Viewer | Modal overlay with tab bar for multiple open files |
| Update Strategy | Hybrid: auto-regenerate when panel open (800ms debounce), stale indicator when closed |
| Backend Architecture | Session-based jimfs, session ID = project name, dedicated VFS endpoints |
| Project Rename | Create new jimfs, old one cleaned up by TTL |
| Syntax Highlighting | Shiki (client-side), one-dark-pro theme |
| Tabs | Multi-tab modal from the start |

## Architecture

### Backend

#### VirtualFileSystemService

Abstraction layer over jimfs. Does not know about code generation.

```kotlin
@Service
class VirtualFileSystemService {
    fun createProject(projectName: String): FileSystem
    fun writeFiles(projectName: String, files: Map<String, String>)
    fun getTree(projectName: String): FileTreeNode
    fun getFileContent(projectName: String, path: String): FileContent
    fun exists(projectName: String): Boolean
    fun deleteProject(projectName: String)
}
```

Internal state:
- `ConcurrentHashMap<String, ManagedProject>` — holds jimfs FileSystem, last access time, generation timestamp per project name
- TTL-based cleanup via `@Scheduled` (30 min inactivity)
- Max 10 concurrent projects
- Path validation: normalization, no traversal beyond project root

#### DTOs

```kotlin
data class FileTreeNode(
    val name: String,
    val path: String,
    val type: FileType,           // FILE or DIRECTORY
    val children: List<FileTreeNode>?,
    val size: Long?
)

enum class FileType { FILE, DIRECTORY }

data class FileContent(
    val path: String,
    val name: String,
    val content: String,
    val language: String,         // "kotlin", "gradle", "dockerfile", "properties"
    val size: Long
)

data class GenerationResult(
    val status: GenerationStatus,  // VALID or INVALID
    val fileCount: Int?,
    val generatedAt: String?,
    val errors: List<String>?
)

enum class GenerationStatus { VALID, INVALID }
```

#### CodeGenerationService

Orchestrates validation, generation, and VFS writing.

```kotlin
@Service
class CodeGenerationService(
    private val exportService: ExportService,
    private val vfsService: VirtualFileSystemService,
    private val graphValidator: GraphValidator
) {
    fun generateForProject(projectName: String, graph: ProjectGraph): GenerationResult
}
```

Flow:
1. Validate graph via `GraphValidator`
2. If invalid → return `GenerationResult(INVALID, errors=...)`
3. If valid → call `ExportService.generateFiles(graph)` to get `Map<String, String>`
4. Write to jimfs via `VirtualFileSystemService.writeFiles(projectName, files)`
5. Return `GenerationResult(VALID, fileCount=..., generatedAt=...)`

#### VirtualFileSystemController

```
POST /api/vfs/{projectName}/generate
  Body: ProjectGraph (JSON)
  Response 200: GenerationResult

GET /api/vfs/{projectName}/tree
  Response 200: { root: FileTreeNode, generatedAt: String }
  Response 404: project not found

GET /api/vfs/{projectName}/files?path=<relative-path>
  Response 200: FileContent
  Response 400: invalid path (traversal, absolute)
  Response 404: project or file not found

DELETE /api/vfs/{projectName}
  Response 204: No Content
```

Path security: the `path` query parameter is normalized and checked against path traversal (`..`, absolute paths). Only paths within the project root are allowed.

### Frontend

#### New Store: `codeExplorerStore`

```typescript
interface CodeExplorerState {
  isOpen: boolean
  tree: FileTreeNode | null
  generatedAt: string | null
  status: "idle" | "generating" | "valid" | "stale" | "invalid"
  openTabs: OpenTab[]              // { path, name, language }
  activeTabPath: string | null
  fileCache: Map<string, FileContent>
  expandedFolders: Set<string>

  togglePanel(): void
  setTree(tree, generatedAt): void
  openFile(path, name, language): void
  closeTab(path): void
  setActiveTab(path): void
  setFileContent(path, content): void
  markStale(): void
  setStatus(status): void
  reset(): void
}
```

Key behaviors:
- `expandedFolders` stays stable across regenerations
- `fileCache` is cleared on regeneration; open tabs reload lazily when activated
- Tab for a deleted file shows "(deleted)" in grey, content stays readable but dimmed

#### New API Client: `vfs-client.ts`

```typescript
generateCode(projectName: string, graph: ProjectGraph): Promise<GenerationResult>
getFileTree(projectName: string): Promise<{ root: FileTreeNode; generatedAt: string }>
getFileContent(projectName: string, path: string): Promise<FileContent>
```

#### Debounce Hook: `useCodeGeneration`

- Subscribes to `editorStore` (nodes, edges) and `codeExplorerStore` (isOpen)
- Panel open + graph changes → `markStale()` immediately, then debounce 800ms → `generateCode()`
- Panel closed → only `markStale()`
- Panel opened while stale → regenerate immediately
- Generation in flight + new change → abort previous (AbortController), restart after debounce
- After successful generation → `getFileTree()`, set tree in store, clear `fileCache`

#### Components

```
ActivityBar                — 40px icon strip, always visible
├── ActivityBarIcon        — single icon (code explorer toggle)

CodeExplorerPanel          — 240px panel next to Activity Bar
├── PanelHeader            — "CODE EXPLORER" + status badge (valid/stale/generating/invalid)
├── FileTree               — recursive file tree
│   └── FileTreeItem       — single row (folder or file), click to expand/open

CodeViewerModal            — centered overlay with backdrop dim
├── ModalHeader            — tab bar + close button
│   └── TabBar             — open tabs with ×, active tab highlighted
├── CodeContent            — Shiki-rendered code with line numbers
└── ModalFooter            — language, line count, encoding, "last valid state" badge
```

#### Layout Integration in `page.tsx`

```
┌──────────────────────────────────────────────────┐
│ ActivityBar │ ExplorerPanel │ Toolbar             │
│    40px     │   240px       ├──────────────────── │
│             │               │ Canvas │ Properties │
│             │               │        │            │
│             │               ├──────────────────── │
│             │               │ PreviewPanel         │
│             │               ├──────────────────── │
│             │               │ ValidationPanel      │
└──────────────────────────────────────────────────┘
          CodeViewerModal (overlay on file click)
```

- Activity Bar always visible (40px)
- Explorer Panel slides in/out with animation (240px)
- Canvas has `flex:1`, adapts automatically
- Modal is `position:fixed` with backdrop dim over everything

#### Shiki Integration

- Install `shiki` as dependency
- Lazy-initialize highlighter on first modal open
- Languages: `kotlin`, `groovy` (Gradle), `dockerfile`, `properties`
- Theme: `one-dark-pro`

## Error Handling

### Empty States
- Panel open, never generated → "Add nodes to your graph to see generated code here"
- Panel open, graph invalid → "Graph has validation errors — showing last valid state" + last valid tree stays visible
- Panel open, generating → spinner in panel header

### Status Badge
- `valid` (green) → `stale` (yellow, pulsing) → `generating` (blue, spinner) → `valid`
- `invalid` (red): last valid tree stays visible

### Deleted Files After Regeneration
- Open tab for a file no longer present → tab marked grey with "(deleted)", content stays readable but dimmed
- User can close tab manually

### Project Name Change
- Frontend detects change in `editorStore.projectName`
- Triggers generation with new project name
- Old jimfs cleaned up by TTL

### Concurrency
- `VirtualFileSystemService` uses `ConcurrentHashMap` + synchronized writes
- Reads are lock-free (jimfs is thread-safe for reads)
- Generation in progress while user reads tree/file → user gets previous state

### Backend Error Responses
- Project not found → 404
- File not found → 404
- Invalid path (traversal) → 400
- Graph invalid → 200 with `status: INVALID` + errors (expected state, not an error)

## Testing

### Backend Tests

**VirtualFileSystemService:**
- Create project and write files
- Nested directory structure
- Repeated writes (overwrite)
- Get tree with correct hierarchy
- Get file content
- Path traversal protection (reject `..`, absolute paths)
- TTL cleanup removes expired projects
- Max project limit

**VirtualFileSystemController:**
- Generate with valid graph → 200, VALID
- Generate with invalid graph → 200, INVALID with errors
- Get tree for existing project → 200
- Get tree for non-existent project → 404
- Get file content → 200
- Get non-existent file → 404
- Invalid path → 400
- Delete project → 204

**CodeGenerationService:**
- Valid graph → generates files and writes to VFS
- Invalid graph → returns errors, does not write
- Repeated generation overwrites previous state

### Frontend
- Component rendering for all states (idle, generating, valid, stale, invalid)
- File tree expand/collapse
- Tab open/close/switch
- Debounce logic (mock timers)
- Stale detection on graph change
- Modal open/close with keyboard (Escape)

## Dependencies

### Backend
- `com.google.jimfs:jimfs:1.3.0` — add to build.gradle.kts

### Frontend
- `shiki` — add to package.json

## Data Flow

```
User edits graph
  → editorStore updates (nodes/edges)
  → useCodeGeneration hook detects change
  → if panel open: markStale() → debounce 800ms → POST /api/vfs/{name}/generate
  → CodeGenerationService validates → ExportService.generateFiles() → VFS writes to jimfs
  → response: GenerationResult(VALID)
  → GET /api/vfs/{name}/tree → store tree
  → clear fileCache
  → user clicks file in tree
  → GET /api/vfs/{name}/files?path=... → store in fileCache → show in modal with Shiki
```
