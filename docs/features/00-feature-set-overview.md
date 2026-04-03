# Feature Set: Banking Tutorial Parity

Abgeleitet aus der [Gap Analysis](docs/gap-analysis-banking-tutorial.md). Jedes Feature ist eine eigenständige, implementierbare Einheit. Die Reihenfolge ist so gewählt, dass jedes Feature auf dem vorherigen aufbaut.

## Reihenfolge

| # | Feature | Datei | Abhängig von | Aufwand |
|---|---------|-------|-------------|---------|
| 1 | [Code-Editor für Tool-Bodies](./01-code-editor-tool-bodies.md) | `01` | — | Klein–Mittel |
| 2 | [Shared Code Node](./02-shared-code-node.md) | `02` | — | Mittel |
| 3 | [Edge-Transformation im UI](./03-edge-transformation-ui.md) | `03` | — | Klein |
| 4 | [Erweiterte Graph-Node-Typen](./04-extended-graph-nodes.md) | `04` | — | Mittel |
| 5 | [Custom Node im Graph-Editor](./05-custom-graph-node.md) | `05` | #1 (Code-Editor) | Mittel |
| 6 | [Subgraphen](./06-subgraphs.md) | `06` | #4, #5 | Hoch |
| 7 | [Gradle Build Validation](./07-gradle-build-validation.md) | `07` | — | Klein |
| 8 | [Wasm Codegen](./08-wasm-codegen.md) | `08` | — | Mittel |
| 9 | [Backend Selector UI](./09-backend-selector-ui.md) | `09` | #8 | Klein |
| 10 | [RAG Node](./10-rag-node.md) | `10` | — | Mittel–Hoch |
| 11 | [KotlinPoet Code Generation](./11-kotlinpoet-codegen.md) | `11` | — | Hoch |
| 12 | [KMP Library Codegen](./12-kmp-library-codegen.md) | `12` | #8 (Generator-Backend-Abstraktion) | Mittel |
| 13 | [AWS Bedrock Provider](./13-aws-bedrock-provider.md) | `13` | — | Klein |
