# OPC package organization plan

The user requests one recognizable Java package for performance infrastructure. The current implementation is already consolidated at runtime; this follow-up organizes source ownership without changing behavior. Work in the existing dirty checkout and preserve the preceding OPC implementation.

Canonical root: `com.thunder.wildernessodysseyapi.opc`. Preserve subsystem names beneath it: core, coordinator, tickengine, background, async, dataengine, config, ram, saving, diagnostics, and simulation. Client GPU/loading code stays client-isolated beneath diagnostics. Shared Simulation API/core/debug/events/regions move; ecology participation/model adapters stay feature-owned under their existing integration package. Global three-file config assembly stays in the global config package; performance-specific config classes move.

No config keys/filenames, payload IDs, commands, resources, save schemas, JVM property names or lifecycle order change. Update Java declarations/imports/FQCN references, source path references, Gradle test filters/compiler exclusions, and architecture guards together. Keep the chunk/saving and client/common ownership boundaries equally strict at the new paths.

The old `performance.coordinator.PerformanceCoordinator` class remains a stateless forwarding alias. All other moved Java APIs require documented import updates and recompilation for downstream mods; this is a package migration, not a promise that old binary type names still exist. Do not add duplicate stateful compatibility engines for every moved type.

- [x] Inspect dirty state, current owners, registrations, build wiring and architecture tests.
- [x] Add an artifact regression for canonical OPC classes and absence of duplicate legacy implementations; observe failure before moves.
- [x] Move source/tests and update references mechanically, checking every destination for collisions and workspace containment.
- [x] Retain the legacy coordinator alias, update config imports, boundary tests and Gradle selectors; add a package index/migration guide.
- [x] Compile, run focused/full tests, build, and run isolated coordinator GameTests serially with Java 21.
- [x] Review the relocation diff for accidental runtime/content changes and record evidence.

Final evidence: 176 focused tests, 1,623 full-suite tests (four existing skips), build and both required native GameTests passed. All 148 moved production bodies matched after package/import/reference normalization. See opc-package-validation.md; the independent reviewer was unavailable due to a usage limit, so no independent-review success is claimed.
