# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.
---

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Agent Modeler — a visual editor for modeling AI agents as graph nodes and exporting them as Kotlin code targeting the JetBrains Koog framework (1.3.0). Spring Boot 4 backend + Next.js 16 frontend.

## Build & Run Commands

### Backend (Kotlin / Spring Boot)
```bash
./gradlew build                          # Full build with tests
./gradlew bootRun                        # Start dev server on :8080
./gradlew test                           # Run all tests
./gradlew test --tests "ClassName"       # Run specific test class
./gradlew test --tests "ClassName.method name*"  # Run specific test method
./gradlew compileKotlin                  # Compile only (no tests)
./gradlew ollamaE2eTest                  # E2E: modeled Ollama agent + shell tool, exported (Spring Boot + Ktor/Wasm), built and run (needs `ollama serve` + `ollama pull llama3.2:3b`; not part of `test`). Wasm uses Docker if available (E2E_WASM_RUNTIME=docker|node|auto); in this container run it via `.devenv/bin/with-libs` (Kotlin/Wasm's Node needs libatomic)
```

### Frontend (Next.js)
```bash
cd frontend
npm run dev       # Dev server on :3000
npm run build     # Production build
npm run lint      # ESLint
```

Full-stack dev: run `./gradlew bootRun` and `cd frontend && npm run dev` in parallel. Frontend proxies `/api/*` to :8080 via rewrites in `frontend/next.config.ts` (override target with `BACKEND_URL`); clients use relative URLs, so no CORS in dev.

## Architecture

### Backend (`src/main/kotlin/com/agentwork/modeler/`)

The pipeline is: **Editor Graph → GraphToKoogAdapter → KoogModel → Generators → ZIP/VFS**

- **`export/model/`** — Two data models: `WorkspaceGraph` (editor graph with nodes/edges) and `KoogAgent` (Koog-compatible output)
- **`export/adapter/`** — `GraphToKoogAdapter` bridges editor graph → Koog model
- **`export/generator/`** — 13+ KotlinPoet-based code generators (Agent, Tool, Registry, Controller, BuildGradle, Dockerfile, SubAgent, A2A, Planner, RAG, MCP, GraphAgent, etc.)
- **`export/generator/ktor/`** — 8 alternative generators for Ktor/WASM browser deployment
- **`export/validation/`** — `GraphValidator` validates graph structure server-side
- **`export/zip/`** — `ZipBuilder` packages generated files
- **`vfs/`** — JimFS-based in-memory filesystem for live code exploration without disk writes
- **`preview/`** — Live agent preview: instantiates Koog agents from graph data, streams execution via WebSocket
- **`agentchat/`** — Chat interface for preview sessions
- **`review/`** — LLM-based code review of generated exports
- **`build/`** — Gradle build orchestration with error parsing
- **`mcp/`** — MCP server discovery
- **`mcpserver/`** — MCP server endpoints (SSE, streamable HTTP at `/mcp`)

### Frontend (`frontend/`)

- **`app/`** — Next.js 16 App Router (single-page graph editor)
- **`components/`** — React 19 components, Rete.js node renderers, shadcn/ui
- **`store/`** — Zustand stores (editor, validation, codeExplorer, preview, agentChat, review, clipboard, history)
- **`lib/`** — API clients (`*-client.ts`), Rete.js setup, client-side validation
- **`types/`** — TypeScript types mirroring backend models

### Key API Endpoints
- `POST /api/export` — Generate ZIP from graph
- `/api/vfs/{project}/*` — Virtual filesystem CRUD
- `/api/preview` — WebSocket live agent preview
- `/api/agent-chat` — Chat with preview agent
- `/api/review` — LLM code review
- `/api/build` — Gradle build orchestration
- `/api/mcp` — MCP server discovery

## Tech Stack & Versions

| Layer | Stack |
|-------|-------|
| Backend | Kotlin 2.2.21, Spring Boot 4.0.4, Java 21, Koog 1.3.0 (Spring starter 1.3.0-beta), KotlinPoet 2.2.0, JimFS 1.3.0, Spring AI (OpenAI + MCP) |
| Frontend | Next.js 16.2.1, React 19.2.4, Rete.js 2.x, Zustand 5, shadcn 4.1, Tailwind 4, Monaco Editor, Shiki, Zod 4 |
| Testing | JUnit 5, Mockito 5.4, Spring Boot Test |
| Build | Gradle 9.4.0 |

## Conventions

- All code generators use **KotlinPoet** (type-safe builders, no string templates)
- Graph node types: `agent`, `tool`, `sub-agent`, `mcp-server`, `planner-agent`, `rag-agent`, `graph-agent`, `a2a-agent`, `input`, `output`
- `ModelProvider` enum: OPENAI, ANTHROPIC, GOOGLE, BEDROCK, OLLAMA — each carries import path, executor factory, and default model. Ollama needs no API key (base URL via `OLLAMA_BASE_URL`, default `http://localhost:11434`); Ollama model tags map through `OllamaModelMapping`
- Frontend uses `@base-ui/react` (not standard headless UI) and `styled-components` alongside Tailwind

## Important Notes

- **Next.js 16 has breaking changes** from earlier versions. Read `node_modules/next/dist/docs/` before making frontend changes.
- `OPENAI_API_KEY` env var required for preview/review features (fallback in application.yaml is insecure).
- Kotlin compiler flags: `-Xjsr305=strict`, `-Xannotation-default-target=param-property`
