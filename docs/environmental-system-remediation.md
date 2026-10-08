# Environmental system remediation

This change covers meteor generation and exposure, wildlife/ecosystem participation and lifecycle, reactive vegetation, and glacial terrain protection/presentation. Water simulation and weather authorities are outside this remediation. The current checkout also contains separate work on those systems; the build uses the combined checkout.

## Corrected behavior

| Area | Correction | Existing owner retained |
| --- | --- | --- |
| Natural craters | Deterministic structure pieces write only in the chunk being generated. The compatibility feature refuses an unwritable footprint before changing blocks. Promoted starts enter the server meteor index. | Minecraft structure generation, `MeteorSiteServices`, `MeteorSavedData` |
| Dimension policy | Natural crater candidates respect the existing dimension capability. The Before's ecosystem and vegetation producers remain inert without removing historical data. | `EnvironmentDimensionProfile`, existing structure-policy seam |
| Radiation | Strongest overlapping exposure drives regional influence and presentation independently of nearest-site metadata. Legacy saved radii participate in the indexed exposure query. | `MeteorSavedData`, `RegionalEnvironmentManager` |
| Wildlife AI | Profile removal, disablement, tracking loss and unload release feature-owned NoAI. External NoAI and unrelated AI goals are preserved. Suspension and abstraction use the same protection rules, including young/breeding/injured/tagged animals and materialization cooldowns. | `AnimalNeedsState`, `EcosystemSimulationManager`, `DistantWildlifeManager` |
| Wildlife candidates | Cancellable joins are unconfirmed until the server's visible entity identity agrees. Rejected joins cannot enter simulation or population transitions; duplicate UUIDs preserve the accepted animal. Live hidden candidates survive tracking loss and resume when visible without requiring another Join event. | Entity lifecycle events and the server's in-memory entity lookup |
| Vegetation | Public mutation APIs respect the master toggle. Selected updates reach exposed plants and bounded ground candidates beneath leaves, using the occupied height returned by `ChunkAccess`. Disturbance is sampled at each plant. | `ReactiveVegetationServices`, loaded-chunk scheduler |
| Vegetation presentation | Climate changes refresh occupied sections, including vegetation below roofs. Dirty requests are deduplicated, unload removes entries, and each tick inspects at most the existing two-chunk budget. | Client climate store and render-section invalidation |
| Glacial terrain | Late crevasse/river writes protect local starts and conservatively protect chunks containing neighboring structure references. Every affected wall, water, bed and foundation position passes protection checks. | Existing glacial features and structure protection |
| Glacial presentation | Login, dimension change and respawn send initial season state; stale client dirty entries consume the same bounded work budget. | Existing glacial season sync service |

## Performance and lifecycle

Wildlife discovery performs a complete visible entity pass at initialization and explicit profile/configuration refresh, then uses event-maintained PathfinderMob identities. Identity lifetime is independent of profile eligibility: this allows a newly enabled species to recover after hidden tracking without another join event. Periodic and player-cell-driven passes filter those candidates rather than inspecting every loaded entity. Hidden and ineligible candidates never enter visible simulation or abstraction. A round-robin housekeeping pass inspects at most 128 candidate/queue entries per level tick, even while disabled; stale entries count against the cap. It prunes cancelled and physically removed identities without profile queries or chunk access. Level unload clears the owner cache.

Cell counts and represented abstract population are cached with their owners. Abstract-group cell coverage uses the saved ledger revision and the existing simulation cadence. Vegetation scheduling uses a removable ordered set instead of linear priority-queue removal, and diagnostic/timestamp-only updates no longer mark chunks dirty. Client queues bound inspected entries, including stale ones. Craters use seeded randomness and heightmaps instead of unseeded random values and repeated vertical surface scans.

These changes remove unnecessary work and enforce bounds. They are not a measured FPS, MSPT, world-generation time or allocation benchmark.

## Generation and compatibility

Natural craters now use `wildernessodysseyapi:meteor_crater` and the `meteor_craters` structure set. Random-spread placement uses spacing 28 and separation 17, approximately one candidate per 784 chunks, subject to biome and generation checks. It replaces the old approximately 1-in-800 feature attempt rather than preserving its exact seeded distribution.

Core radii remain 60–125 blocks. The complete footprint is capped at 127 blocks from the center so every touched chunk can receive the start through Minecraft's eight-chunk structure-reference radius. The outer rim/ejecta of the largest craters is consequently clipped. Piece geometry and random choices are global to the persisted plan, so chunk processing order does not select different fragment sizes. The final world-generation stage uses the current surface heightmap.

The legacy feature/configured/placed identifiers remain loadable. Its explicit placement path returns false if the whole footprint cannot be safely written, with no partial crater. Old meteor saved records remain usable; new chunks receive the new natural placement. Existing terrain is not retrofitted. Existing packet encoding and compatible public snapshot constructors remain unchanged. Vegetation NBT remains compatible; timestamp/diagnostic values persist with the next normal chunk save.

Regional exposure is a cached sample at the existing regional anchor and cadence. It selects the strongest source at that sample; it does not turn the regional packet into a continuously sampled player-position measurement.

## Verification

The first regression runs reproduced unsafe crater bounds, overlap exposure, vegetation persistence/queue failures and glacial protection failures before their corrections. A later loaded-world regression reproduced rejected-join retention and tracking-only removal against the prior wildlife cache.

Focused JUnit validation after the lifecycle fixes: **209 tests across 74 classes, zero failures/errors/skips**. It covers ecosystem, vegetation, environmental, meteor, simulation-integration and temporal-dimension policy tests. Production and test sources compiled through the normal Gradle pipeline.

Final root-mod JUnit validation: **1,435 tests across 392 classes; 1,431 passed, four skipped, zero failures/errors**. The skips are the existing three symbolic-link capability tests and the tracked-bunker fixture test. The final independent source review found no remaining Critical or Important issue in the candidate lifecycle changes.

The isolated root `:build` completed successfully. Its regular installable artifact is `.codex-build/libs/wildernessodysseyapi-5.0.0.jar`. Read-only package inspection confirmed the new crater structure/set and registered implementation classes, retained legacy configured feature, removal of unsafe feature injection, and gzip-compressed environmental GameTest template. The artifact contains the current combined checkout, including other in-progress work.

The isolated loaded-world namespace passed **all 10 required GameTests**, with successful server shutdown and Gradle exit. It exercises real cow safety, profile removal, disablement/NoAI ownership, approaching-player distance classification, rejected/duplicate joins, temporary section visibility loss, profile removal/restoration and first-time eligibility while hidden, disabled housekeeping, promoted crater indexing, overlapping regional radiation/packet values, and vegetation toggle mutation behavior. The visibility fixture moves actual animals into an inaccessible section and back while confirming the distant chunk stays unloaded. The player-distance fixture temporarily adds a real `ServerPlayer` to the level roster within one synchronous test call; it does not perform client login or prove multiplayer networking. Crater-index testing installs a real registered structure start and exercises promotion/indexing; complete natural terrain generation remains a separate acceptance check.

Use the checked-in wrapper, one development process at a time:

```powershell
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat runGameTestServer '-PcodexBuildDir=.codex-build' --no-parallel '-Dforge.enabledGameTestNamespaces=wildernessodysseyapi_environment_tests'
.\gradlew.bat :build '-PcodexBuildDir=.codex-build' --no-parallel
```

The aggregate `build` command reached successful root-mod assembly/packaging, then failed in the separate, unchanged `:aether-server:test` suite: `AetherServerTest.executableJarStartsAndHandlesHealthGenerationAndOutage` received `java.net.SocketException: Connection reset` at line 298. That assertion expects EOF after an intentionally incomplete HTTP header times out. Its executable had already passed the health check before the failing socket read. This failure was also recorded before this remediation; it is not an environmental regression and is not repaired in this scope. Of 53 Aether tests, 52 passed and one failed. The aggregate build must not be described as successful.

StructureGen also reports an existing catalog fingerprint mismatch and does not use modded catalog content from that snapshot. Its three blueprints still validate and generate successfully. No catalog refresh or unrelated Aether source change is included.

Task-specific console logs are `.codex-build/environment-test-console.log`, `.codex-build/environment-full-test-console.log`, `.codex-build/environment-gametest-console.log`, `.codex-build/environment-build-console.log` and `.codex-build/environment-root-build-console.log`. Reports and compiled output are local generated artifacts.

## Regular-world and client acceptance

The GameTest server uses a flat preset without the project's custom dimensions or normal natural structure placement. The remaining acceptance checks require a disposable regular development world and, for presentation/network behavior, real clients:

1. Generate fresh Overworld terrain with natural meteors enabled. Inspect crater seams, lava/ejecta and decoration near adjacent structures; confirm the promoted site is indexed once and survives saving/reloading. Check that structure discovery works and no crater generation starts appear in The Before, Echo, Anomaly or Nether. Existing generated chunks should remain unchanged.
2. Visit The Before with ecosystem and vegetation enabled globally. Confirm no new environmental-memory records, abstraction, plant damage or climate mutation occurs, while historical records remain stored. In a living dimension, toggle vegetation off and confirm random ticks, selected updates and queued damage remain inactive until reenabled.
3. Move real players between ecosystem cells, reload profiles, disable simulation and restart a saved world. Confirm visible wildlife restores feature-owned AI immediately while external NoAI, protected animals and unrelated goals remain intact. Test dedicated-server/two-client login and dimension transitions separately from the roster-based distance regression.
4. Change vegetation climate beneath foliage and a roof; observe ground vegetation tint refresh. Leave/reenter the area and switch worlds to check cache cleanup visually. Test glacial login, respawn and dimension entry for initial season presentation, and inspect protected structures beside late glacial cuts.
5. Compare the same seed, route, view/simulation distance and population before/after using existing environmental timing diagnostics and a profiler. Record tick distribution, candidate count, queue backlog, world-generation time and allocations. Avoid claiming a speedup from static bounds alone.

No existing user world is migrated, deleted or launched by this remediation.
