# Runtime water query and entity cache safety

The October 13, 2026 loading report captured a generation worker saving an ocean-ruin mob. `Zombie.addAdditionalSaveData` called `Entity.isInWater`, and the Wilderness entity-water hook sampled the runtime `ServerLevel`. Its block read requested a completed chunk while the server was already waiting for the generation task. Spawn generation remained at 0 percent.

## Ownership and fallback

`EntityWaterCompat.stateFor` admits only added, non-removed entities. On the server it also requires the owning server thread and an active server. The check precedes cache access, so a generation worker cannot consume a cached mutable state either. Rejected calls receive an unknown-contact state; the existing entity mixins retain the original vanilla fluid result.

Supported gameplay entities keep the existing once-per-tick footprint and eye sampling. Client callers remain owned by the existing client tick/render hooks. No common class references client-only Minecraft classes.

## Reads cannot request generation

`LoadedWaterReadAccess` implements the existing loaded-chunk pattern in one water-owned read boundary:

- Server queries use `ServerChunkCache.getChunkNow` on the owning thread. A missing chunk returns unavailable without a ticket or a future wait.
- Client queries use the received chunk source with `requireChunk=false`.
- Cell, generated-span, surface-column, shoreline, and depth reads use the returned chunk directly.
- Fluid-flow and collision-shape queries receive a `BlockGetter` that reads available chunks and treats unavailable neighbors as empty. Callbacks cannot load another chunk through that view.

`hasChunkAt` is not an availability guarantee: in the active NeoForge implementation it checks chunk-holder admission and ticket level, even when the completed chunk is still pending.

The initial starter-bunker owner keeps its bounded construction tickets and generation requests. Water reads do not acquire tickets, change placement order, or take over generation. Canonical water amounts, generated metadata, persistence formats, and authority activation remain unchanged.

## Cache lifecycle

An `EntityWaterState` holds its sampled level through a weak reference. It allocates that reference only when the level changes, not on every sample. This breaks the static cache value's strong path through a level's entity manager back to its weak entity key.

The registered adapter removes entries on `EntityLeaveLevelEvent`, removes an unloading level's entries on `LevelEvent.Unload`, and removes a stopped server's entries on `ServerStoppedEvent`. Cleanup synchronizes iteration of the shared weak-key map and preserves entries belonging to another logical side or server.

## Regression checks

`WaterQuerySafetyGameTests` runs in the existing isolated water-safety namespace and fixture. Its cases cover:

1. Saving an unadded structure mob without runtime water sampling.
2. Rejecting a worker-thread entity query even when a cached runtime state exists.
3. Returning unavailable from a worker-thread authority query.
4. Releasing an entity's cache entry when it leaves its level.
5. Reading fluid flow at a naturally loaded chunk boundary without loading the missing neighbor.

These five assertions failed against the original code. Run the focused checks serially:

```powershell
.\gradlew.bat compileJava '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat waterSimulationTest '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat runGameTestServer '-PcodexBuildDir=.codex-build' '-PwaterSafetyGameTests=true' '-PwaterSafetyProfile=baseline' --no-parallel
```

The GameTests also retain the existing canonical-volume and projection safety cases. They do not replace a client acceptance run: repeat the affected new-world creation, verify loading advances past 0 percent and opens the world, then leave and reopen worlds in the same JVM. The default water settings do not need to be disabled for this repair.
