# Hybrid anomaly rifts

User brief: a 4–6 block fractured ring with a clear 3 block aperture; real-block
NBT terrain over a 15–30 block composition; server-owned state, swept entry
trigger, survivable impact and subtle physics; local, reducible client effects.

## Existing owners and compatibility

Reuse `TemporalRiftBlocks.RIFT_CORE`, its block entity, renderer and command
registration. Add an `ANOMALY_RING` visual mode. Saved cores without that mode
keep the historical temporal-travel behavior. New scheduled Overworld rifts
use the hybrid mode, configurable through the existing server specification.
Do not modify The Before, Anomaly Gateway destinations, Echo simulation, water,
Riftfall, or existing performance-diagnostics work.

## Implementation sequence

1. Add behavior tests for smooth distance falloff, state danger, swept aperture
   entry, capped survivable impact, rotated footprints and composition spacing.
2. Persist and synchronize hybrid state, facing and composition completion on
   the existing block entity. Loaded server tickers own interactions; skip
   distant entity queries. Provide public NeoForge events for entry and nearby
   warning integration. STABILIZED entry emits a hook without a new destination.
3. Plan ring-sector structure placement deterministically from world seed and
   anchor. Use vanilla StructureTemplate placement, rotation, ignored air/entities,
   and the existing NBT sanitizer. Preflight every footprint, world boundary,
   build height and target block before any write. Decline unloaded or obstructed
   sites; never force-load. Terrain remains after closure so player edits survive.
4. Render the supplied Blockbench geometry and animations with the existing
   GeckoLib dependency, plus its animated emissive texture companion. Clip shell
   faces in aperture coordinates so rotating shards keep the opening clear.
   Draw layered black depth, rotating cyan/violet ribbons, wisps and brief arcs
   separately from rock geometry. Bounds and view distance cover the ring only.
5. Synchronize nearby field summaries at a throttled cadence and impacts on
   entry. Bound client summaries and expire them on unload/travel/stale updates.
   Add sparse particles, quiet ambience, interpolated fog, pulse camera effects
   and an independently owned optional post chain; preserve external shaders.
6. Add operator spawn/ring/composition/state/status commands, client FX/debug
   commands and visible trigger/field bounds. Supply eight simple NBT rock
   variants compiled from authored blueprints; document artist replacement paths.
7. Compile, run focused offline regressions, package and run focused GameTests
   when the environment permits. Review the entire scoped diff. Report GPU,
   multiplayer and original modpack gameplay acceptance separately.

## Review focus

- Elytra crossing an aperture between ticks, including rotated and negative coordinates.
- Cooldowns, spectator/creative bypass, and impact damage leaving health remaining.
- Unloaded/blocked templates must decline without changing any terrain.
- Old block-entity tags retain their previous visual/travel mode.
- Local FX disablement, resource reload, dimension changes and shader-pack ownership.

This plan is executed by the main agent in the existing checkout; only one
Codex Gradle/Minecraft process runs at a time.
