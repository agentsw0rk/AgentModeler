# Repository Guidelines

## Project Structure & Module Organization
`src/main/kotlin/com/agentwork/modeler/` contains the Spring Boot 4 backend: `export/` handles graph-to-Kotlin generation, `preview/` and `agentchat/` support live execution, `vfs/` provides in-memory file access, and `mcp/` plus `mcpserver/` expose MCP features. Backend resources live in `src/main/resources/`.

`src/test/kotlin/com/agentwork/modeler/` mirrors backend packages with JUnit 5 tests such as `ExportServiceTest.kt` and `GraphValidatorIOTest.kt`. The frontend is isolated in `frontend/`: `app/` for the Next.js App Router, `components/` for UI, `store/` for Zustand state, `lib/` for clients and editor wiring, and `types/` for shared TypeScript models. Supporting specs and feature notes live in `docs/`.

## Build, Test, and Development Commands
```bash
./gradlew build          # backend compile + test
./gradlew bootRun        # backend dev server on :8080
./gradlew test           # backend test suite
./gradlew test --tests "ClassName"

cd frontend && npm run dev    # frontend dev server on :3000
cd frontend && npm run build  # production frontend build
cd frontend && npm run lint   # ESLint for Next.js/TypeScript
```
Run backend and frontend in parallel for full-stack work. Keep generated output in `build/` and `frontend/.next/` out of commits.

## Coding Style & Naming Conventions
Follow existing package boundaries and keep changes surgical. Kotlin uses 4-space indentation, `PascalCase` types, and descriptive suffixes such as `*Controller`, `*Service`, `*Generator`, and `*Test`. Prefer KotlinPoet builders for generated Kotlin code instead of string templates.

TypeScript uses 2-space indentation, `PascalCase` React components, and `camelCase` helpers/hooks/stores such as `useCodeGeneration` and `editorStore`. Lint with `frontend/eslint.config.mjs`; no Prettier config is present. If you touch frontend behavior, verify against the installed Next.js 16 docs noted in `frontend/AGENTS.md`.

## Testing Guidelines
Backend coverage is the main safety net. Add or update JUnit 5 tests beside the affected package and name files `*Test.kt`. Prefer focused tests for adapters, generators, validators, and controllers before broad integration changes.

There is no dedicated frontend test runner configured yet, so at minimum run `npm run lint` and document manual verification for UI changes.

## Commit & Pull Request Guidelines
Recent history uses Conventional Commit prefixes: `feat:`, `test:`, and `docs:`. Keep commits scoped, imperative, and specific, for example `feat: add pgvector storage generator`.

PRs should include the problem statement, implementation summary, verification steps run, and screenshots or recordings for frontend changes. Link the relevant issue or feature doc when available.

## Security & Configuration Tips
Do not commit secrets. `OPENAI_API_KEY` is required for preview/review flows and should come from your environment, not hard-coded config.
