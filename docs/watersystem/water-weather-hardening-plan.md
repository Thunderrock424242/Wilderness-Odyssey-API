# Water and weather hardening implementation plan

The user approved the seven fixes and four improvement areas from the October 7,
2026 source review. This work keeps canonical fixed-point water, generated spans,
regional hydrology receipts, server-owned weather, existing packets, and the
translucent water render coordinator as their existing owners.

## Constraints

- Minecraft 1.21.1, NeoForge 21.1.256, Java 21; no new dependencies.
- Preserve saved inventories, provenance, waterlogged hosts and external fluids.
- Never load chunks for water disturbances, synchronization or climate probes.
- Preserve unrelated loading, ecosystem, environment, vegetation and meteor
  changes being edited concurrently in the current checkout.
- Run one Gradle or Minecraft development process at a time with isolated output.
- Report automated, world-backed and live visual/multiplayer evidence separately.

## Implementation sequence

1. Add world-backed regressions for an untracked generated source's native tick,
   direct solid replacement, enclosed displacement, repeated placement callbacks,
   and compatibility-disabled conservation. Route disturbed owned water into the
   existing finite ticker and use the shared displacement owner after successful
   external writes. Keep failed/provisional writes from committing water changes.
2. Dispatch weather configuration publication and authority invalidation together
   on the server thread. Gate new atmospheric exchange when coupling is disabled;
   retain previously committed receipts until re-enabled without replay or loss.
3. Exercise continuous changes across more than eight chunks. Add fair bounded
   snapshot scheduling, nearby baseline priority, pending age/backlog diagnostics,
   and logout/unwatch/dimension lifecycle cleanup.
4. Exercise render ownership transitions and restrictive fog. Restore baked water
   when disabling, rebuild custom meshes when enabling, and preserve gameplay
   fog restrictions while retaining native immersion and external shader ownership.
5. Render canonical rain/snow/hail phases, with mixtures supplied by interpolated
   authoritative phases rather than a second surface-temperature classifier.
6. Bound surface-overlay terrain sampling and reuse terrain candidates; retain
   cached climate for all retained cells and stagger expired terrain probes.
   Expose terrain-probe work through the existing diagnostics.
7. Review the complete diff, run focused unit checks, relevant GameTests, and
   broader regression/build checks where they add confidence. Update subsystem
   docs and the manual acceptance checklist with exact evidence and remaining
   live client, shader, multiplayer and performance checks.

## Regression and review focus

Write and run behavior regressions before each implementation group. In-game
world behavior belongs in existing GameTest holders; isolated scheduling,
receipt, visual policy and cache behavior belongs in JUnit. Pay particular
attention to cancelled player placements, recursion guards, source provenance,
unloaded neighboring chunks, paged baselines, config changes during worker work,
saved precipitation across toggles, blindness/darkness, and renderer teardown.

## Progress

- Baseline: weatherPhysicsTest 325 and renderingTest 275 executions passed during
  the preceding read-only review. Live/GameTest proof was not run for that audit.
- All seven fixes and four improvement areas are implemented in their existing
  owners. Final review found and corrected a late placement cancellation gap and
  starvation of outer surface-overlay candidates. Enclosed parcel release also
  received a regression and repair.
- Unit checks: weatherPhysicsTest 338 and renderingTest 287, zero failures/skips.
  These counts overlap in client weather coverage.
- World-backed checks: all 40 required GameTests passed. The larger authored
  water-flow fixture isolates neighboring parcels; the shared legacy fixture
  was preserved.
- Main mod suite: 1431 tests, zero failures/errors and four skips. Broad test/build
  invocations also select the standalone Aether subproject, where an unrelated
  incomplete-header EOF assertion fails with SocketException: Connection reset.
- Explicit root `:build` completed successfully after a concurrently edited
  meteor GameTest's signature mismatch was corrected by its owner. Those files
  were preserved. The regular 5.0.0 JAR includes the shared checkout's other
  edits at packaging time; it is not a water-only release.
- Ruling: keep review/placement work synchronous through NeoForge's final hook
  result instead of adding a second deferred event queue; cancellation remains
  final before canonical water commits.
- Ruling: retain published cosmetic patches between bounded candidate scans;
  restarting from the center would permanently starve the edge over dry ground.
