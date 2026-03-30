# Feature 09: Backend-Auswahl im Tools-Menü

## Problem

Der Modeler hat jetzt zwei Generator-Backends (Spring Boot, Ktor/Wasm), aber der User kann nur über die API zwischen ihnen wechseln. Im UI gibt es keine Möglichkeit, das aktive Backend auszuwählen. Export, Code-Generierung im VFS und Build nutzen immer den Default (Spring Boot).

## Ziel

Im Tools-Menü (wo Export, Save/Load etc. stehen) kann der User das aktive Generator-Backend auswählen. Bei Wechsel wird das virtuelle Dateisystem gecleared und der Code mit dem neuen Backend neu generiert.

## Verhalten

1. User öffnet Tools-Menü → sieht aktuell aktives Backend (z.B. "Spring Boot (JVM)")
2. User wählt anderes Backend (z.B. "Ktor (WasmJS)")
3. VFS wird gecleared (alle generierten Dateien entfernt)
4. Code wird mit dem neuen Backend neu generiert
5. Code Explorer zeigt die neue Dateistruktur
6. Export und Build verwenden automatisch das gewählte Backend

## Architektur

```
Tools-Menü
  └── Backend: [Spring Boot (JVM) ▼]     ← Dropdown
        ├── Spring Boot (JVM)             ← Optionen aus GET /api/generators
        └── Ktor (WasmJS)

Bei Wechsel:
  1. Store: activeBackend = "ktor-wasm"
  2. VFS: DELETE /api/vfs/{project}
  3. Generate: POST /api/vfs/{project}/generate { graph, backend: "ktor-wasm" }
  4. Export: POST /api/export { graph, backend: "ktor-wasm" }
  5. Build: POST /api/build { graph, platform, backend: "ktor-wasm" }
```

### State Management

Neuer Zustand im Editor-Store (Zustand):

```typescript
interface EditorStore {
  // ... existing
  activeBackend: string           // "spring-boot" | "ktor-wasm"
  setActiveBackend: (id: string) => void
}
```

Der `activeBackend` wird bei jedem API-Call (Export, Build, VFS Generate) mitgeschickt.

## Scope

### Frontend

| Datei | Änderung |
|---|---|
| `store/editorStore.ts` | `activeBackend` State + `setActiveBackend` Action |
| `components/dialogs/ToolsMenuDialog.tsx` | Backend-Dropdown im Menü, über dem Divider vor Save/Load |
| `lib/vfs-client.ts` | `backend` Parameter bei `generateCode()` |
| `lib/export-client.ts` | `backend` Parameter bei `exportToKotlin()` |
| `lib/build-client.ts` | `backend` Parameter bei `buildProject()` |
| `components/panels/CodeExplorerPanel.tsx` | Reagiert auf Backend-Wechsel (Refresh nach Regeneration) |

### Backend

Keine Änderungen nötig — die API-Endpunkte akzeptieren bereits den `backend` Parameter (Feature 08).

## UI-Design

Im Tools-Menü, als erster Eintrag (noch vor Validate):

```
┌─────────────────────────────┐
│ Tools                       │
├─────────────────────────────┤
│ ⚙  Backend                  │
│    [Spring Boot (JVM)    ▼] │
├─────────────────────────────┤
│ ✓  Validate (Local)         │
│ 🔨 Build (JVM)              │
│ 🔨 Build (Native)           │
├─────────────────────────────┤
│ 💾 Save / Load              │
│ ⚙  Workspace Settings       │
│ 📦 Export                    │
├─────────────────────────────┤
│ ⌨  Keyboard Shortcuts       │
├─────────────────────────────┤
│ 📡 MCP Status    ● Connected│
└─────────────────────────────┘
```

Der Dropdown wird aus `GET /api/generators` befüllt. Die Build-Buttons passen sich dynamisch an die `supportedPlatforms` des gewählten Backends an:
- Spring Boot → "Build (JVM)", "Build (Native)"
- Ktor/Wasm → "Build (Wasm)"

## Ablauf bei Backend-Wechsel

1. User wählt neues Backend im Dropdown
2. `setActiveBackend("ktor-wasm")` im Store
3. Automatisch: `DELETE /api/vfs/{projectName}` (VFS clearen)
4. Automatisch: `POST /api/vfs/{projectName}/generate` mit neuem Backend
5. Code Explorer refresht die Dateiansicht
6. Toast-Notification: "Backend gewechselt zu Ktor (WasmJS)"

## Dynamische Build-Buttons

Die Build-Buttons im Menü werden aus den `supportedPlatforms` des aktiven Backends generiert:

```typescript
const activeBackendInfo = generators.find(g => g.id === activeBackend)
const platforms = activeBackendInfo?.supportedPlatforms ?? ["jvm"]

// Render:
// platforms.includes("jvm") → "Build (JVM)" Button
// platforms.includes("native") → "Build (Native)" Button
// platforms.includes("wasm") → "Build (Wasm)" Button
```

## Akzeptanzkriterien

- [ ] Backend-Dropdown im Tools-Menü zeigt alle verfügbaren Backends
- [ ] Dropdown-Optionen werden aus `GET /api/generators` geladen
- [ ] Bei Backend-Wechsel: VFS wird gecleared und Code neu generiert
- [ ] Code Explorer zeigt nach Wechsel die neue Dateistruktur
- [ ] Export verwendet das gewählte Backend
- [ ] Build-Buttons passen sich an die supportedPlatforms des Backends an
- [ ] `activeBackend` wird im Store persistiert (Session-übergreifend optional)
- [ ] Default-Backend ist "spring-boot"
