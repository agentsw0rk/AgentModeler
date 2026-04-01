# Design: KotlinPoet Code Generation

**Datum:** 2026-04-01
**Status:** Approved

## Zusammenfassung

Migration der 19 Kotlin-Code-Generatoren von `buildString { appendLine(...) }` auf KotlinPoet. Eliminiert manuelles String-Escaping, manuelle Indentation und manuelle Import-Listen. Generator-Signaturen und Pipeline bleiben unveraendert.

## Design-Entscheidungen

| Frage | Entscheidung | Begruendung |
|-------|-------------|-------------|
| Scope | 19 Kotlin-Generatoren migrieren, 9 Infrastruktur-Generatoren nicht | Kotlin-Code profitiert von KotlinPoet, Gradle/Properties/Dockerfile nicht |
| Output-Format | `FileSpec.toString()` als String | Pipeline (`Map<String, String>`) bleibt unveraendert |
| Gradle-Generatoren | Bei buildString belassen | Gradle-DSL profitiert nicht von KotlinPoet |
| Migrationsstruktur | Phasenweise nach Komplexitaet (3 Phasen) | Komplexeste zuerst = hoechster ROI |
| Architektur | Utility-Layer zuerst, dann direkte Ersetzung | DRY ab dem ersten migrierten Generator |
| Utility-Schicht | KoogAnnotations + KoogModels + Sanitization | 3 heute duplizierte Patterns zentralisiert |

## Vollstaendige Spezifikation

Siehe [docs/features/11-kotlinpoet-codegen.md](../../features/11-kotlinpoet-codegen.md) fuer:
- KotlinPoet Kern-API Uebersicht mit Beispielen
- KotlinPoetUtils Utility-Schicht Design
- 3 Migrationsphasen mit allen 19 Generatoren
- Migrationspattern (Vorher/Nachher)
- Teststrategie
- 13 Akzeptanzkriterien

## Risiken

- KotlinPoet-Formatierung kann leicht von buildString-Output abweichen → Test-Assertions ggf. anpassen
- `GraphAgentGenerator` hat komplexe verschachtelte Subgraph-Logik → sorgfaeltige Migration noetig
- Koog-spezifische DSL-Aufrufe (z.B. `edge(source forwardTo target)`) muessen als raw CodeBlock generiert werden, nicht als typisierte FunSpec
