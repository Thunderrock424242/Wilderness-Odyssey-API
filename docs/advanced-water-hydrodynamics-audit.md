# Advanced water, coastal hydrodynamics and erosion

## Ownership audit (2026-09-05)

This is an extension of the existing system. A class being present is not
evidence of runtime activation. The following classifications are based on
registrations and callers in this checkout, not a visual runtime test.

| Component | State and responsibility |
| --- | --- |
| WildernessWaterAuthority / CanonicalWater / WildernessFluidRegistry | Active, server-authoritative finite volume and actual fluid projection. Bucket transactions enter canonical water immediately. |
| WaterSourceMixin | Absent in this checkout; do not restore a second source-water engine. Finite flow is owned by WildernessFluidRegistry and FiniteWaterFlowPlanner. |
| SPHSimulationManager / SPHSimulator / SPHParticle / kernels / spatial hash | Active. ServerTickHandler advances local authoritative bodies; ClientTickHandler advances client effects and remote mirrors. Explicit body/particle budgets apply. |
| DensityField / MarchingCubes / FluidMesh / FluidRenderer | Local SPH mesh path, invoked through WaterRenderCoordinator. Not an ocean surface solver. |
| GerstnerWaveProfile / animator / vertex consumer | Active large-scale wave foundation. Compatibility chunk-baked displacement is suppressed when replacement geometry owns the top. |
| GerstnerWaveRenderMixin | Registered client mixin. Captures replacement ownership and preserves uncovered vanilla fallback faces. |
| WaterShaders / WaterRenderTypes / gerstner_water.* | Active built-in shader: WaterShaders registers gerstner_water; WaterRenderTypes binds its supplier; WaterRenderCoordinator updates uniforms and renders snapshot meshes. External packs select a compatibility path. |
| WaterSurfaceEquation | Existing CPU mirror of GPU displacement, used for immersion. Changes must preserve matching equations and encoded inputs. |
| TideSystem | Shared deterministic tide offset/rate authority. |
| TideWorldUpdater | Lifecycle cleanup for shoreline flow; legacy block-replacement tide behavior has already been retired. |
| OceanSeaStateField / ClientOceanSeaState | Server weather/sea response and synchronized client presentation. Reuse existing wind, storm and dimension rules. |
| ShorelineWaterManager / ShallowWaterGrid | Active server local flow, scheduled by WaterPerformanceIntegration and consumed by entity physics and shore SPH. Bounded regions, primitive arrays. |
| ShoreWaveSpawner | Active, sparse client SPH shore-wash payloads. Not the breaking-wave surface or an ocean SPH body. |
| ClientCoastalSegmentStore / CoastalSegment | Active cached, loaded-only shore topology and run-up terrain. |
| CoastalWaveModel / CoastalBreakerGeometry / CoastalRunupRenderer | Active deterministic incoming, shoaling, breaking, run-up and retreat presentation, using the coordinator's translucent batch. |
| CoastalBreakEffects / RiverSoundscape / WaterAmbientEffects | Active positional coast/river/rapids/waterfall sound and particle presentation. |
| WaterEntryEventHandler / RippleRenderer / WaterSurfaceDisplacement | Active entry impact, ripples and bounded movement disturbances, including persistent GPU wake foam. |
| WaveEntityPhysics / ClientWaveEntityEffects / BoatTiltStore / BoatRenderMixin | Active authoritative forces and client visual hull response. |
| WatershedSimulationModel sediment | Active normalized turbidity metadata. It is not a conserved inventory and must never be spent to create terrain blocks. |
| Terrain erosion / deposition | Missing at audit time. Added by this change through ErosionManager/ErosionSavedData; see scope and limitations below. |

## Integration constraints

The shared surface query must compose WildernessWaterAuthority and cached
topology, not claim fluid ownership. Client effects must not send whole grids.
Terrain mutations belong on the server thread and must notify existing water
owners explicitly; setBlock does not trigger player placement/break events.

Unknown legacy terrain cannot be proven natural. Player placement tracking
alone cannot protect builds made before installation. Natural eligibility must
be explicit, persisted and fail closed; structure starts **and references**,
block entities, support blocks and immune tags need protection. Conserved
sediment is distinct from normalized watershed turbidity.

## Validation baseline

The unmodified checkout compiled successfully with JDK 21 and isolated Gradle
output after an outside-sandbox retry. The first attempt failed in NeoForm
recompilation with AccessDeniedException before project compilation. No ACL,
security configuration or dependency JAR was changed. This is compile evidence,
not a Minecraft runtime, shader, multiplayer or performance result.

The phase is not complete until runtime acceptance covers buckets, finite flow,
SPH, boats, swimming, waterlogging, aquatic life, tides, coast and inland water,
terrain protection, resource reload, shader fallback, chunk/dimension lifecycle
and a measured long-running multiplayer storm session.

## Implemented extensions

* `WaterSurfaceSampler` composes the existing authoritative surface and actual
  finite-water impact velocity into reusable hydrodynamic response. It owns no
  duplicate fluid, weather or network state.
* `DepthWaveResponse` modulates the existing Gerstner spectrum. The active GPU
  path and CPU immersion mirror use the same encoded depth envelope; physical
  surface queries use actual column depth. Carrier time stays stable. This is
  shoaling/dissipation, not a new spatial refraction solver.
* Shoreline bathymetry now uses bounded authority depth rather than treating
  columns deeper than the scan cap as dry. Shallow-water catch-up, input values,
  velocities and surface excursion are capped, including malformed forcing.
* Cached run-up ribbons can make two lateral detours around obstructions. Tide
  modulates reach without introducing another tide clock. Existing breaker
  audio retains priority over the quieter retreat wash accent.
* Existing environmental foam emitters advect against synchronized loaded
  water snapshots, stop at dry obstacles, persist briefly and fade. Boat wakes
  add two aft lobes through the existing bounded displacement/foam pool.
* Erosion is a low-priority lane of WaterPerformanceIntegration. The default
  budget is eight candidate evaluations per dimension per second, four combined
  erosion/deposition changes per rolling minute, and one change per chunk per
  minute. Runtime candidates cap at 256. No catch-up burst follows an unload.
* New-chunk natural eligibility and sediment are persisted in a level ledger.
  Player/automation placement and player excavation protect a surrounding 3x3
  chunk area. Structure starts **or references**, block entities, unsupported
  material and immune tags prevent edits. `ErosionEvents.protectAround` is the
  explicit integration hook for claims/story/automation systems.
* Erosion credits one material unit only on successful removal. Transport
  moves existing units; deposition spends a unit only on successful placement.
  The ledger retains up to 64 units per category per chunk. It enrolls at most
  8,192 chunks, then fails closed instead of forgetting protection or mass.
* Direct environmental edits notify finite-water scheduling, classification,
  local shoreline cache invalidation and watershed terrain refresh. Refresh
  preserves basin identity, accumulated hydrology, flooding and dynamic water
  features. A bounded server depth cache and immutable client snapshot floor
  resolution reflect runtime bed changes without rewriting generated baselines.
* Shallow deposits use canonical solid-placement displacement, preserving water
  volume, including trapped residuals. Conserved suspended inventory supplies a
  normalized sediment/clarity floor through the existing watershed sync path.
  The starter bunker's persisted bounds plus a chunk buffer are excluded even
  when hostile-spawn protection is disabled.
* Existing sparse shore SPH events contribute aggregate impact pressure, not
  per-particle block edits. Network checks are bounded and rotated among players.

## Configuration and practical limits

Controls live in the existing server water specification at
`water_simulation.erosion`: `enabled`, `checksPerSecond`, `changesPerMinute`,
and `resistanceScale`. Erosion defaults on **only for eligible new terrain**.
Legacy chunks are never automatically enrolled. Disabling pauses changes; it
does not discard sediment/protection. Soft/medium/hard resistance is 600/1800/
7200 normalized exposure-seconds before the configured multiplier. Calm forces,
sampling cadence and the mutation budgets generally make changes much slower
than these lower bounds.

This is deliberately conservative: candidates may have air or standalone water
above, but solid supports and hosted-water plants are never undercut. Riverbeds
and waterfall beds can receive pressure through actual finite-water flow.
Deposition fills air or one-cell-deep water above supported natural terrain;
it does not grow deep underwater sediment columns. Canonical displacement
preserves fluid units. Sediment transport is a coarse chunk inventory transfer
along the sampled current, not per-grain trajectory tracking. Clarity reflects
inventory through existing normalized watershed metadata and may retain that
system's gradual recovery after material settles.

Generic command/world-edit tools and claims that bypass NeoForge placement
events must call the protection hook. Natural eligibility is conservative
event provenance, not proof against every possible third-party world writer.
Whole structure/block-entity chunks are excluded instead of querying/loading
distant structure starts. Runtime exposure resets when the dimension unloads;
material and revoked eligibility persist. The minute budget is session-local.

The foam enhancement is a bounded advecting emitter field plus the existing
GPU wake foam and coastal sheets, not a new dense ocean-wide foam texture.
Visual lateral ribbon detours are a bounded approximation, not flow splitting
or a fluid-volume simulation. Existing river/rapids/waterfall effects remain
the inland presentation owners. Adaptive scheduling is inherited from the
existing water scheduler; this change does not claim a measured frame/MSPT gain.

## Verification and remaining acceptance

Final compilation, the 318-case water-subsystem JUnit selection, and packaging
completed successfully with JDK 21 using:

```powershell
.\gradlew.bat test --tests '*watersystem*' build '-PcodexBuildDir=.codex-build' --no-daemon --no-parallel
```

This selected water tests; it is not a claim that every repository JUnit test
ran. The installable output is `.codex-build/libs/wildernessodysseyapi-4.2.0.jar`.
The packaged erosion classes, depth sampler, erosion tag and water vertex
shader were verified. Final `git diff --check` passed. Validation used the
approved outside-sandbox path following the earlier NeoForm access denial;
no security settings or generated dependency JARs were altered.

The isolated `runGameTestServer` completed all 29 registered required tests
successfully, including shallow sediment placement conserving one exact bucket
of canonical water. Snapshot regressions cover raised and eroded floors;
watershed regressions cover retained hydrology and sediment-driven clarity.
Those checks are not a live long-term erosion/deposition acceptance test.
The dedicated solver regression
advances 12,000 numerical updates with repeated extreme impulses; that is not
12,000 Minecraft gameplay ticks or an MSPT benchmark.

Manual acceptance must still cover new eligible coast/bank terrain, gradual
storm erosion, low-energy deposition, protection before/after restart, player
and story builds, walls/logs/rocks, moving boats, wading/swimming, resource and
shader reload, Iris enabled/disabled, long sessions and separated multiplayer
players. Compare clear/rain/thunder and rising/falling tide. Use `/wowater
erosion` for candidate, change, CPU-time, eligibility and sediment counters, plus
the existing Rendering F3 coastal and water pages. Watch loaded-chunk counts,
world changes, particle counts and MSPT. Do not mark the entire requested phase
complete until these runtime acceptance checks are resolved.
