# Where Odyssey Performance Core lives

All shared performance implementations now live under:

`src/main/java/com/thunder/wildernessodysseyapi/opc/`

The Java root is `com.thunder.wildernessodysseyapi.opc`. Tests mirror it under `src/test/java/com/thunder/wildernessodysseyapi/opc/`. This is a source/package reorganization of the already consolidated systems, not an additional performance framework.

```text
opc/
├── core/                    OPC policy session, shared deferred engine, raw JVM samples, rollout mode
├── coordinator/             Governor, watchdogs, disposable admission and coordinator compatibility API
├── tickengine/              Tick measurement, budgets, cadence, debt and deferred-work adapters
├── background/              Background API, activity classification and compatibility batchers
├── async/                   Shared CPU/IO workers and safe server-thread handoff
├── dataengine/              Dirty/scheduled/completed updates, interest, caching and DataDelta delivery
├── config/                  Performance SERVER spec and legacy file migration
├── ram/                     RAM advice and GC notification analysis
├── saving/                  Optional native-save observation/adapter and its tests
├── diagnostics/             Server reports, ownership evidence and /wo perf commands
│   ├── gpu/client/          Explicit client GL/VRAM profiling
│   └── loading/             Loading observations, bounded reports and client recovery integration
│       └── client/
└── simulation/              Shared simulation API, orchestration, region scheduling and events
```

Start with `opc/core/OdysseyPerformanceCore.java` for policy, `opc/async/AsyncTaskManager.java` for shared workers, and `opc/dataengine/DataEngine.java` for feature data processing. The package Javadoc at `opc/package-info.java` provides the same entry points in the IDE.

Weather, water, vegetation and ecosystem authorities/adapters remain with those features. Ecology models/participants remain in `simulation.integration`; the shared Simulation Engine and public contracts moved into OPC. Rendering quality policy and debug-overlay pages remain with their rendering/UI owners. Global three-file config assembly, mod entrypoint, payload registration and native mixins retain their existing locations and import OPC. This preserves ownership boundaries while giving shared performance infrastructure one home.

## Package migration map

In this table, old and new names are relative to `com.thunder.wildernessodysseyapi`:

| Previous package/type | Canonical package/type |
| --- | --- |
| `performance.*` | `opc.*` (same subsystem names: core, coordinator, tickengine, background, ram) |
| `async.*` | `opc.async.*` |
| `dataengine.*` | `opc.dataengine.*` |
| `diagnostics.performance.*` | `opc.diagnostics.*` |
| `saving.coordinator.*` | `opc.saving.*` |
| `gpuprofiler.*` | `opc.diagnostics.gpu.*` |
| `loading.*` | `opc.diagnostics.loading.*` |
| `simulation.api.*` | `opc.simulation.api.*` |
| `simulation.core.*` | `opc.simulation.core.*` |
| `simulation.debug.*` | `opc.simulation.debug.*` |
| `simulation.event.*` | `opc.simulation.event.*` |
| `simulation.region.*` | `opc.simulation.region.*` |
| `config.PerformanceServerConfig` | `opc.config.PerformanceServerConfig` |
| `config.PerformanceConfigMigration` | `opc.config.PerformanceConfigMigration` |

The reorganization moved **148 production Java files and 49 test files**. Java declarations, imports, fully qualified references, Gradle test selectors/compiler exclusions, client mixin bridge imports and architecture source-path guards were updated together. Source moved from the current working tree, retaining the preceding uncommitted OPC implementation.

## External API compatibility

Downstream source integrations must update their imports using the map and recompile. Moving a Java package changes binary type names; an already compiled mod referring to a moved class must be rebuilt. This migration is intentionally documented rather than represented as full binary compatibility.

The old `performance.coordinator.PerformanceCoordinator` class remains as a deprecated stateless forwarding alias. Methods whose signatures use JDK types/primitives, including status, lifecycle and `constrainBudget(long)`, retain that entry point and share the canonical OPC session. Methods accepting `CoordinatorConfig.Values` or `CoordinatorGovernor.Module` use the new OPC types, so callers of those type-bearing APIs also require import updates and recompilation. No duplicate legacy config definitions, workers, queues or event registrations are retained.

## Runtime contracts retained

- Config filenames, TOML keys and defaults stay unchanged, including `performance.opc.mode=LEGACY`, the disabled legacy coordinator master, safe mode and saveDrain OFF.
- Resource locations, payload IDs/codecs, commands, translation/resource identifiers and save-data schemas stay unchanged.
- Server/common/client ownership and registration order stay unchanged. GPU/loading client classes stay beneath client-only packages; moving their path does not make them safe to load on a dedicated server.
- Native saving remains isolated from optional scheduling governors. Architecture guards follow the same protected owners at the new paths, with an added assertion preventing missing directories from silently disabling coverage.
- Existing test task names remain available. `performanceDiagnosticsTest` now selects the full `opc` test tree and validates actual JAR contents; `loadingProfilerTest` follows its new package, and `playtestTest` retains coverage of the moved performance config tests.

The packaged-artifact regression requires representative implementations in every OPC branch and rejects leftover legacy implementation classes. Its only allowed legacy performance class is the stateless coordinator alias. A runtime alias test verifies that both entry points observe and clear the same session.

See [OPC audit](opc-audit.md) for subsystem responsibilities and [package validation](opc-package-validation.md) for commands, results and remaining runtime proof boundaries. Earlier audit/validation documents describe the consolidation stage; use this map when following their historical relative source paths.
