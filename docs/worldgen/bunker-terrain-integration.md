# Starter bunker terrain-integration checklist

Use this checklist when editing the `wildernessodysseyapi:bunker` template so the top floor blends into terrain more naturally.

## 1) Edit the bunker NBT template

- Put a **blue wool** leveling marker at the exact point where you want terrain contact to happen.
  - Prefer a marker near the center of the bunker footprint.
  - Keep it on the intended top-floor contact band (not on decorative roof details).
- Shape the outer top-floor edge as a transition zone:
  - Use irregular edges instead of a perfect rectangle.
  - Add a 1-3 block-wide ring of terrain-friendly blocks around the exterior edge.
  - Avoid thin floating lips at corners.

## 2) Validate anchoring in-game

Use placement commands while iterating on the NBT:

- `/modpackstructures reload`
- `/modpackstructures place wildernessodysseyapi:modpack/<your_bunker_id> <x> <y> <z> true`

`alignToSurface=true` ensures anchored placement is used while testing.

## 3) Tune terrain blending config for bunker tests

In `serverconfig/wildernessodysseyapi-server.toml`:

- `enableAutoTerrainBlend = true`
- `enableSmartAutoTerrainBlend = true`
- Start with:
  - `autoTerrainBlendMaxDepth = 6`
  - `autoTerrainBlendRadius = 2`

If bunker edges still look abrupt, increase blend radius first, then max depth.

## 4) What code now does automatically for the starter bunker

When the starter bunker is placed, the placer now also runs a dedicated perimeter pass that blends surface blocks in a short ring (3 blocks wide) around the bunker footprint. This helps the top floor visually merge with surrounding terrain instead of ending in a hard border.

The generated ocean island is also landscaped as an overgrown jungle ruin:

- The raised bunker platform uses grass and dirt instead of exposed dirt across the whole island.
- The sloped perimeter remains sandstone and sand so the jungle ends in a readable beach.
- Broad terrain hummocks, seeded ground patches, mossy boulders, and fallen logs break up the flat platform.
- Jungle trees, shrubs, hanging vines, bamboo, and undergrowth share randomized grove centers, producing dense pockets and irregular clearings instead of an even perimeter ring.
- The bunker NBT contains a 157 x 76 x 182 underground facility. The landscape scan protects only its above-ground shell, allowing vegetation over the buried footprint while keeping trunks out of the visible entrance and roof.
- A protected clearing and a worn path on the above-ground shell's negative-Z/front side keep the door and player approach accessible.

Pack authors can tune or disable this pass in `serverconfig/wildernessodysseyapi-server.toml`:

- `starterIslandJungleEnabled = true`
- `starterIslandJungleDensity = 0.75`

These changes apply when the starter bunker is first generated. Existing worlds whose starter bunker has already been placed are intentionally not redecorated.

## Required starter bunker placement

During initial overworld spawn creation, starter bunker placement is required
unless `debugDisableStarterBunker` is enabled. If placement returns no result or
throws a runtime exception, `SpawnBunkerPlacer` logs a fatal error and throws a
Minecraft `ReportedException` to abort world startup. The crash report includes
the structure ID, attempted spawn anchor, and this recovery instruction:

> Starter bunker failed to spawn. Please create a different world and try again.

An existing Minecraft placement crash report retains its original diagnostics
and receives the same recovery instruction. Failure does not mark the bunker as
placed or continue with vanilla spawn. Successful placements, saved bunker
markers, other dimensions, and the explicit debug-disable path keep their
existing behavior. The failed world is not automatically deleted or rolled back;
placement may already have written some terrain before failing.

`SpawnBunkerPlacerTest` covers missing placement results, runtime exceptions,
existing Minecraft crash reports, and successful results. For client acceptance,
use a disposable test instance with a data pack overriding
`wildernessodysseyapi:bunker` with an empty structure template, then create a
new world with starter placement enabled. Confirm startup aborts, the log has a
fatal starter-bunker error, and the crash report tells you to create a different
world. Remove the override and confirm a fresh world starts normally.

## Starter construction chunk lifetime

Initial world creation owns the selected starter footprint through
`StarterBunkerChunkPreparation`. It validates the existing template and
256-chunk placement budgets before requesting any chunks, adds temporary
non-ticking region tickets for that footprint, and prepares its full chunks
before island construction starts. Those tickets remain through island fill,
bunker placement, and decoration, and are removed in `finally`, including when
construction fails. Each scope has its own ticket value; no persistent
forced-chunk state is created. Minecraft still owns generation and unloading.

The general `LargeStructurePlacementOptimizer` guard remains loaded-only. Its
caller must arrange availability. Island surface sampling alone was insufficient:
Minecraft's transient lookup tickets could expire while later island columns
were generated, causing the subsequent bunker guard to reject an otherwise
valid placement. The October 12 client log recorded exactly this failure for
132 touched chunks, below the 256-chunk limit, followed by vanilla spawn fallback.

Three native GameTests cover missing-chunk preparation, retention after transient
lookup tickets expire, release after successful or throwing construction, and
rejection before loading an over-budget footprint. All three failed before
preparation was implemented; the corrected run passed all 49 required tests,
including the existing starter-water regressions. The server log is
`.codex-build/starter-chunk-regression-green.log`.

The final combined root `:test` run passed 25 checks across starter spawn,
surface/decorating behavior, placement limits, and chunk-ownership architecture,
including all eight `SpawnBunkerPlacerTest` checks for the coordinated fatal
placement policy. Its log is `.codex-build/starter-spawn-integration-test.log`.
That task rebuilt the regular `.codex-build/libs/wildernessodysseyapi-5.0.0.jar`,
SHA-256 `A7C0915E91D8A7C10741071F91A7AC919D3C3F64577902BD00129510DC59EBB7`.
The new preparation classes are packaged, and the packaged bunker still matches
the unchanged source SHA-256
`81D61F883D056FC9BB8D9F0AC7BDC3E653CBA9B23D1FB468844CF3E4331E6900`.

The selected footprint is prepared only when initially constructing a starter.
This does not place a new bunker automatically into an already-created world
whose initial placement failed. Full-sized new-world placement and the first
player's cryo handoff remain live-client acceptance checks.

## Starter water placement boundary

The initial starter island and bunker are constructed through `ServerLevel`
after the selected columns have generated. Those writes use a synchronous,
bounded starter-generation scope in `WorldFluidMutationReconciler`. It covers
the selected island shore, buried bunker, and landscaping within build height.
The context is level-specific and restored in `finally`, including on failure.

In this scope, final terrain replaces generated water instead of performing a
runtime water-displacement transaction. Successful writes update the existing
`GenerationWaterStateMapper` metadata and remove sparse water overrides at the
same position. This prevents ocean fill from piling conserved water over the
island, and prevents carved bunker rooms from releasing hidden displacement
parcels. Authored template water is still mapped into generated Wilderness
water. The bunker NBT asset is unchanged.

The water boundary only accepts an already-loaded target chunk. It uses the
existing loaded shape view for synchronous callbacks, which returns the bounded
boundary approximation for absent neighboring columns instead of loading them.
The starter constructor retains ownership of generating its selected island
columns; the water adapter adds no tickets, futures, worker, or chunk scan.
Normal block flags and callbacks still run. Ordinary player/machine writes,
writes outside the selected footprint, and later gameplay retain finite-volume
conservation and the existing live-water admission guards.

The October 12 regression pass reproduced island-fill displacement and retained
interior parcels before the fix. Five GameTests cover terrain fill, interior
carving and subsequent server ticks, authored water, cleanup after an exception,
and writes outside the generation footprint. All 46 required GameTests passed,
as did all 130 water simulation unit tests and four focused
`SpawnBunkerPlacerTest` checks, including build-height and shore bounds. The
targeted root `:test` task also completed normal JAR packaging. The final water server log is
`.codex-build/starter-water-regression-green.log`.

The October 12 water-repair artifact was `.codex-build/libs/wildernessodysseyapi-5.0.0.jar`,
SHA-256 `D72D3F48CB3F61A7394CE30768280AEA8CE4DB9065E12B98B1050135FBB56409`.
The packaged bunker asset still matches the source SHA-256
`81D61F883D056FC9BB8D9F0AC7BDC3E653CBA9B23D1FB468844CF3E4331E6900`.
This shared-checkout artifact includes concurrent changes from other work;
the checks above establish this starter-water repair's scope.

This prevents flooding during new starter placement. Updating the mod does not
automatically remove water already saved by an older placement; automatic
cleanup cannot safely distinguish those parcels from later player water.
Validate the updated artifact in a fresh test world: the raised platform and
authored dry bunker rooms should stay dry, while the surrounding ocean and any
template-authored water remain present. Full-size starter placement in the
original modpack remains a live-client acceptance check.
