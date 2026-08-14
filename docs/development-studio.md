# Wilderness Odyssey Development Studio

## Purpose

Wilderness Odyssey Development Studio is a selectable **World Type** for Minecraft 1.21.1. It creates a real naturally generated Wilderness Odyssey world and enables a persistent, server-authoritative developer layer over that world.

It is not a dimension, superflat map, void world, debug generator, or fake simulation. The active level remains `minecraft:overworld`. Outside the bounded Development Campus, the world follows the same terrain, biome, cave, structure, water, ecosystem, weather, entity, and compatible modded-worldgen paths as a normal Wilderness Odyssey world.

Phases 1-3 provide the world foundation, controlled Structure and Entity Labs, persisted test regions, optional overlays, and real environment adapters. Facility power, keycard/security, dedicated lighting controls, Aether scenarios, performance presets, and other systems without a proven gameplay owner remain explicitly deferred.

## Repository and version basis

- Minecraft 1.21.1
- NeoForge 21.1.248
- Java 21
- Mod namespace `wildernessodysseyapi`

Minecraft 1.21.1 represents Create World's World Type choices with the data-driven `WorldPreset` registry. The Create World UI reads entries in `#minecraft:normal` and resolves names through `generator.<namespace>.<path>` translations. Development Studio uses that supported path and does not mix into the world-creation screen.

## World Type registration

The World Type is registered as:

```text
wildernessodysseyapi:development_studio
```

Its resources are:

- `data/wildernessodysseyapi/worldgen/world_preset/development_studio.json`
- `data/wildernessodysseyapi/worldgen/noise_settings/development_studio.json` (generated)
- `data/minecraft/tags/worldgen/world_preset/normal.json`
- translation key `generator.wildernessodysseyapi.development_studio`

The normal world-preset tag exposes **Wilderness Odyssey Development Studio** in the ordinary World Type cycle. Minecraft 1.21.1's selector supports a translated preset name but no separate arbitrary-preset description field, so the in-world screen and this document explain its purpose.

## Natural generation contract

The preset's Overworld uses:

- level stem `minecraft:overworld`
- chunk generator `minecraft:noise`
- biome source `minecraft:multi_noise`
- biome-source preset `minecraft:overworld`
- noise settings `wildernessodysseyapi:development_studio`, generated from Minecraft's exact normal Overworld factory

The Nether and End use their standard vanilla stems and generator settings.

The Studio preset does not fork Wilderness Odyssey's terrain pipeline. Existing TerraBlender integration, biome modifiers, features, structures, custom water, ecosystem controllers, localized weather, mob AI, and compatible mod worldgen see a normal noise-based Overworld and continue through their existing owners.

The real dimension type remains `minecraft:overworld`. Data generation calls `NoiseGeneratorSettings.overworld(context, false, false)` and writes the result under the Studio key. The value keeps the normal noise router, surface rules, spawn targets, aquifers, ore veins, sea level, blocks, and density functions; only the holder key differs so it can act as a creation marker. It creates neither a second dimension nor another save directory.

## Persistent world identity

`StudioWorldData` is stored in the Overworld data directory as:

```text
wildernessodysseyapi_development_studio.dat
```

It persists:

- `developmentStudioWorld`
- campus placement state and exact template origin
- the world bookmark catalog
- absolute test-region IDs, dimensions, bounds, types, and reset policies
- the first validated Structure Lab block baseline
- a saved-data format version

On first Overworld load, `StudioWorldAccess` recognizes the vanilla-derived Studio noise-settings holder and writes `developmentStudioWorld = true`. Systems subsequently read the saved flag rather than guessing from a dimension ID, name, seed, or generator class.

Saved-data format 2 migrates a format-1 campus by resolving the new region definitions from its already-persisted origin. It never moves or replaces an existing campus during migration. Normal worlds do not create Studio data merely by loading.

## Access and developer mode

Studio access is server-authoritative. Both conditions must pass:

1. The world has the persistent Studio flag, or `allowInNormalWorlds` is explicitly enabled in the Studio server config.
2. The player is the integrated-server owner or has permission level 2.

The server config is generated at:

```text
config/wildernessodysseyapi/wildernessodysseyapi-development-studio-server.toml
```

`allowInNormalWorlds` defaults to `false`. Every request, including read-only refreshes and modifying lab/weather actions, rechecks access.

## Opening Studio

- Configurable key: **F8**, named **Open Development Studio** in Controls.
- Command: `/wilderness studio`.

Both send an empty/high-level request. The server validates the player and world, produces a bounded snapshot, and asks only that client to open the interface.

## Modular interface

`StudioScreen` owns navigation and shared layout. Focused `StudioPage` implementations own individual module presentation, while `StudioModuleRegistry` owns stable module IDs.

Available modules:

- **World** - persistent world status, seed, campus, and natural-generation contract.
- **Locations** - shared world bookmarks and campus destinations.
- **Structures** - template selection, rotation/mirror, server-computed previews, exact lab placement, reload, and reset.
- **Entities** - allowlisted tagged spawning and scoped Entity Lab controls.
- **Water** - real Water authority, generated spans, bodies, watershed state, and diagnostics.
- **Ecosystem** - bounded nearby profile/needs inspection and real update-budget state.
- **Weather** - local atmosphere samples/forecast and bounded clear/rain/snow/hail experiments.
- **Worldgen** - current loaded chunk, biome, generator, height, and structures-at-position inspection.
- **Inspector** - server-produced Developer Tool results.
- **Debug** - independent test-region and structure-preview overlays.

Visible but deferred modules are **Lighting, Power, Security, Aether, Performance, and Scenarios**. A deferred module does not run a substitute simulation or issue unsafe commands.

## Development Campus

The campus is authored as:

```text
src/main/structure_blueprints/development_studio_campus.json
```

The existing StructureGen pipeline compiles it to standard Minecraft structure NBT. It contains a small hub plus Structure, Water, Entity, and Outdoor pads. These pads are controlled-test locations, not fake subsystem implementations.

`StudioCampusSiteFinder` evaluates a bounded set of positions 40-88 blocks around the real spawn. It samples the complete footprint, rejects fluids and block entities, prefers natural low-slope surfaces, and scores slope before distance. It does not flatten a large area or scan without a bound.

`StudioCampusPlacer` delegates to the existing `NBTStructurePlacer`, terrain-blending, chunk-preparation, and template-loading path. Placement happens once and persists the exact origin. Its lowest-priority spawn hook remains additive to the existing starter-bunker workflow.

## Campus locations and bookmarks

`StudioLocationRegistry` stores destinations as stable IDs and template-relative offsets. The server derives absolute coordinates from the saved campus origin.

Current destinations are:

- `wildernessodysseyapi:main_hub`
- `wildernessodysseyapi:structure_lab`
- `wildernessodysseyapi:water_lab`
- `wildernessodysseyapi:entity_lab`
- `wildernessodysseyapi:outdoor_test_area`

Phase 3 Weather, Ecosystem, and Worldgen pages link to the Outdoor pad rather than claiming dedicated finished buildings exist.

Bookmarks are world-shared and store UUID, name, dimension, block position, yaw/pitch, biome, notes, tags, and server creation time. Creation ignores client coordinates, dimension, orientation, biome, UUID, and timestamp. The server supplies those fields. Strings and collection sizes are bounded and sanitized; teleport checks dimension availability, build height, and world border.

## Developer Tool and Inspector

The **Wilderness Developer Tool** is available in Tools & Utilities:

- use in air opens Studio;
- use on a block inspects the server-resolved block;
- interact with a living entity to inspect the server-resolved entity.

`StudioInspectionRegistry` chooses a typed provider and returns bounded immutable rows. Blocks report real registry/state/fluid/block-entity facts. Entities report type, UUID, position, velocity, health, target, navigation, and `NoAI`. Profiled pathfinding mobs additionally report their real ecosystem profile and existing needs state. The inspector does not reflect private goal internals or fabricate AI labels.

## Test regions and reset policy

`StudioTestRegionRegistry` resolves four small definitions from the persisted campus origin:

- **Structure Lab** - `BLOCK_SNAPSHOT`, 5x8x5 over the stone-brick pad.
- **Entity Lab** - `TAGGED_ENTITIES`, 5x7x5 over the red pad.
- **Water Lab** - `NONE`, inspection-only.
- **Outdoor Lab** - `NONE`, inspection-only.

Only the Structure Lab permits block restoration. Before its first placement, the server verifies exact bounds and volume, requires loaded chunks, rejects every vanilla fluid state and `WaterServices`-owned water cell, and captures block states plus optional full block-entity metadata. The baseline is stored once.

A restore is accepted only when region ID, dimension, min/max bounds, entry count, and reset policy still match. It never manipulates chunk/region files and never claims to restore custom water, ecosystem persistence, or unrelated attachments. Entity reset simply discards Studio-tagged entities still inside the Entity Lab.

## Structure Lab and natural previews

The controlled fixture is:

```text
src/main/structure_blueprints/development_studio_lab_fixture.json
```

It is vanilla-only and exactly 5x4x5 so every rotation fits the lab. This internal fixture is the only lab-placeable definition. `test_shelter` and loaded modpack templates are preview-only.

Requests contain a registered template ID, rotation, mirror, and high-level action - never arbitrary bounds or coordinates. Natural **Preview Here** derives an origin eight blocks along the player's look vector. Lab preview/placement derives a centered origin from persisted bounds. `NBTStructurePlacer.placeExact` performs no terrain leveling or blending and refuses a transformed box outside the allowed lab.

The baseline is restored before every lab placement and on Reset. Natural-terrain placement/removal remains intentionally unavailable because a bounding-box preview is safe while general terrain rollback is not.

## Entity Lab

The fixed spawn allowlist contains cow, pig, zombie, and skeleton. Requests are limited to 10 entities and a configurable total lab cap (48 by default). Spawn points are derived inside the saved lab, and every spawn receives `wildernessodysseyapi.studio_test_entity`.

Clear, freeze/unfreeze, and invulnerability actions query the registered lab and affect only entities with that tag. Packets cannot choose arbitrary entity types, positions, targets, NBT, or entity UUIDs.

## Debug rendering

`StudioDebugRendererRegistry` gives each overlay a stable ID, independent enabled state, render stage, and focused callback. Every renderer defaults off. With none enabled, the level-render event performs one boolean check and returns without collecting data.

Phase 2 renderers are:

- persisted test-region bounds, colored by type;
- the requesting player's server-computed structure-preview bounds.

Both read only the last authorized Studio snapshot and are disabled on disconnect. They render bounds, not fabricated ghost blocks or inferred world state.

## Phase 3 environment integrations

All environment inspection uses the server player's current position and returns at most 64 bounded rows.

### Water

The Water page calls `WaterServices.access()` for ownership, units, surface, depth, current, body metadata, watershed conditions, local flow, and capacity. It reads the current loaded chunk's existing `GENERATED_WATER` attachment without creating one, reporting revision, span count, approximate size, sampled span, and column-top span. It also reports `WatershedSimulationDiagnostics` and `WildernessWaterRules.status`.

The Water Lab is inspection-only. A block snapshot cannot safely restore canonical water volume or attachments, so Studio does not attempt it.

### Ecosystem

The Ecosystem page inspects at most 128 pathfinding mobs in a 32-block box. It applies the real `SpeciesBehaviorProfileManager`, reports the nearest profiled mob's existing needs attachment when initialized, and exposes the actual `EcosystemServices` update budget. Accelerated simulation is deferred; Studio never changes the global tick rate.

### Weather

The Weather page samples `WeatherServices.query()` and reports physical fields, derived cloud/storm state, surface memory, atmosphere-cell revision, forecast, and tracked-system count. Clear, rain, snow, and hail call `WeatherAuthority.clearLocalWeather` or `forcePrecipitation`, retaining their existing 3x3-cell scope, persistence, and synchronization. Studio does not manufacture unsupported severe storms, fog, blizzards, or heatwaves.

### Worldgen

The Worldgen page reports the current loaded chunk and current block only: seed, chunk/region, status, biome, surface height, sea/build height, generator and biome-source classes, and registered structures valid at the player's position. It never performs a broad synchronous scan or generates distant chunks.

## Networking and safety

The shared payload protocol version is 15. Client-to-server messages expose only:

- open Studio;
- create/update/delete/teleport a bookmark;
- teleport to a registered campus ID;
- preview/place/reset/reload a registered template with rotation/mirror;
- spawn/control a fixed allowlisted entity in the Entity Lab;
- inspect one environment owner at the player's current position;
- request clear/rain/snow/hail through existing Weather authority methods.

Server handlers validate access, enums, string/collection bounds, registered IDs, persisted region ownership/reset policy, exact transformed structure containment, lab-placeable status, fixed entity allowlist, request/total entity caps, tags, containment, and server-owned sample locations. No packet exposes arbitrary world-edit primitives.

## Existing owner boundaries

- Terrain/biomes: normal noise generator and existing compatibility integrations.
- Structures: template manager, `NBTStructurePlacer`, processors, diagnostics, and StructureGen.
- Water: `WaterServices`, authority attachments, hydrology, SPH, sync, and rendering.
- Ecosystem: entity attachments, profile manager, controllers, and `EcosystemServices`.
- Weather: `WeatherAuthority`, `WeatherServices`, simulation, sync, and rendering.
- Networking/config/commands: existing NeoForge registrar and repository registration paths.

The repository does not currently expose a complete facility power network, keycard/security-door system, or Studio lighting controller. Those modules remain deferred instead of receiving fake replacements.

## Build and automated validation

Use JDK 21 and the checked-in wrapper:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
.\gradlew.bat runData
.\gradlew.bat generateStructures
.\gradlew.bat test --tests 'com.thunder.wildernessodysseyapi.developmentstudio.*'
.\gradlew.bat build
```

`runData` and `generateStructures` are separate repository tasks. Studio tests cover the preset/resources, saved identity/bookmarks/regions/snapshot migration, text sanitation, module states, campus destinations, and controlled-fixture dimensions. StructureGen validates and semantically re-reads all three generated templates.

A prior dedicated-server Phase 1 smoke test created the Studio preset, prepared a normal Overworld, passed TerraBlender/Biolith recognition, and placed the campus. Phases 2-3 still require the live checks below; compilation is not proof of screen, renderer, entity, structure, or localized environment behavior.

## In-game validation

1. Run `runClient`.
2. Select **Singleplayer -> Create New World -> World -> World Type**.
3. Confirm **Wilderness Odyssey Development Studio** appears.
4. Create the world with structures enabled; Creative and cheats are useful but not forced.
5. Confirm the normal starter-bunker workflow remains additive.
6. Confirm the campus occupies only a limited natural area near spawn.
7. Explore beyond it and compare terrain, biomes, caves, structures, water, ecosystems, Weather, and modded generation with a normal same-seed world.
8. Press F8 and run `/wilderness studio`.
9. Exercise Locations and inspect blocks, block entities, passive mobs, hostile mobs, and a profiled animal.
10. Preview `test_shelter` eight blocks ahead and enable Structure Preview. Confirm only bounds render.
11. Preview, rotate/mirror, and place **Studio Lab Fixture** in the Structure Lab. Reset and confirm the stone-brick pad returns.
12. Spawn Entity Lab cows/zombies; freeze/unfreeze, toggle invulnerability, then Clear. Confirm untagged entities are untouched.
13. Enable Test Region Bounds and confirm the four campus regions toggle independently from structure preview.
14. Inspect a natural river/ocean through Water, nearby animals through Ecosystem, current Weather, and a generated chunk through Worldgen.
15. Use Weather Clear/Rain/Snow/Hail and confirm only the real local atmosphere changes.
16. Quit/reload and repeat Structure Lab Reset to prove baseline persistence. Confirm campus, bookmarks, and regions are not duplicated.
17. Restart a dedicated server and repeat operator/non-operator checks.
18. Create a normal same-seed world. Confirm Studio is denied by default and no campus is placed.

## Current limitations

- The 1.21.1 World Type selector has no custom tooltip field.
- The campus is functional controlled-test infrastructure, not final art or dedicated Phase 3 buildings.
- Campus site search is bounded and may decline an exceptionally hostile spawn region.
- F8 does not enable cheats, Creative, or unrelated gamerules.
- Bookmarks are world-shared and operator-authorized, not per-developer.
- Overlays draw bounds only; ghost blocks, intersection heat maps, water spans, paths, and weather cells remain future work.
- Only the internal fixture may be placed. Natural and modpack templates remain preview-only.
- Block snapshot restoration is deliberately fluid-free and Structure-Lab-only. General chunk, Water, and ecosystem snapshots are not implemented.
- The Water Torture Lab is an inspection destination, not yet a reproducible multi-chunk scenario library.
- Ecosystem stepping, terrain discovery, broad structure location, and controlled nearby chunk generation remain deferred.
- Weather controls expose only real supported authority actions: clear, rain, snow, and hail.
- Facility systems, stress presets, scenarios, and Aether event controls remain deferred.
- Third-party mods that compare the noise-settings holder key instead of actual Overworld dimension/generator behavior remain a compatibility risk.

## Recommended next phase

1. Audit real Power, Security, and Lighting owners before exposing any facility controls.
2. Add dedicated Weather, Ecosystem, and Worldgen campus buildings without changing subsystem authority.
3. Add server GameTests for access denial, structure containment, tagged-entity scoping, one-time campus placement, and normal-world no-op behavior.
4. Add a Water-owned reproducible torture-lab/reset API only if it can restore canonical volume and attachments safely.
5. Add independent bounded water-span and atmosphere-cell overlays using synchronized diagnostic data.
