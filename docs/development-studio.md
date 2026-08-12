# Wilderness Odyssey Development Studio

## Purpose

Wilderness Odyssey Development Studio is a selectable **World Type** for Minecraft 1.21.1. It creates a real naturally generated Wilderness Odyssey world and enables a persistent, server-authoritative developer layer over that world.

It is not a dimension, superflat map, void world, debug generator, or fake simulation. The active level remains `minecraft:overworld`. Outside the bounded Development Campus, the world follows the same terrain, biome, cave, structure, water, ecosystem, weather, entity, and compatible modded-worldgen paths as a normal Wilderness Odyssey world.

Phase 1 establishes a safe foundation. It deliberately does not simulate power, security, weather cells, ecosystem metrics, or other systems that are not exposed by the current gameplay architecture.

## Repository and version basis

The implementation targets the repository's current platform:

- Minecraft 1.21.1
- NeoForge 21.1.248
- Java 21
- Mod namespace `wildernessodysseyapi`

Minecraft 1.21.1 represents the Create World "World Type" choices with the data-driven `WorldPreset` registry. The Create World UI reads entries in `#minecraft:normal` and resolves their display names through `generator.<namespace>.<path>` translation keys. Development Studio uses that supported path and does not mix into the world-creation screen.

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

The normal world-preset tag makes the entry appear in the ordinary World Type cycle as:

```text
Wilderness Odyssey Development Studio
```

Minecraft 1.21.1's built-in World Type selector renders a translated name but does not provide a separate description/tooltip field for arbitrary `WorldPreset` records. The Studio's purpose and status are therefore explained by the in-world Studio interface and this document.

## Natural generation contract

The preset's Overworld uses:

- level stem: `minecraft:overworld`
- chunk generator: `minecraft:noise`
- biome source: `minecraft:multi_noise`
- biome-source preset: `minecraft:overworld`
- noise settings: `wildernessodysseyapi:development_studio`, generated from Minecraft's exact normal Overworld factory

The Nether and End use their standard vanilla stems and generator settings.

This means the Studio preset does not fork Wilderness Odyssey's terrain pipeline. Existing TerraBlender integration, biome modifiers, placed features, structure placement, the custom water attachment/authority pipeline, ecosystem entity controllers, localized weather, mob AI, and compatible mod worldgen see a normal noise-based Overworld and continue through their existing owners.

The real dimension type remains `minecraft:overworld`. This is important because Biolith and other compatibility layers use that registry key to recognize an Overworld. An earlier custom-dimension-type marker was rejected during the dedicated Studio-world smoke test and was removed.

Instead, data generation calls Minecraft's own `NoiseGeneratorSettings.overworld(context, false, false)` factory and writes the result under the key `wildernessodysseyapi:development_studio`. The value contains the same normal noise router, surface rules, spawn targets, aquifers, ore veins, sea level, default blocks, and density-function references; only its holder key differs so it can act as a one-time creation marker. It does not create another dimension or another save directory.

## Persistent world identity

`StudioWorldData` is stored in the Overworld data directory as:

```text
wildernessodysseyapi_development_studio.dat
```

It persists:

- `developmentStudioWorld`
- Development Campus placement state and template origin
- the world bookmark catalog
- a saved-data format version

On the first Overworld load, `StudioWorldAccess` recognizes the vanilla-derived noise-settings holder and writes `developmentStudioWorld = true`. From then on, systems ask `StudioWorldAccess.isDevelopmentStudioWorld(server)`, which reads the persistent world flag. They do not use a dimension ID, teleport destination, name, seed, or generator-class guess.

Normal worlds do not create Studio saved data merely by loading. Data is created in a normal test world only when an authorized Studio operation is explicitly permitted by server config.

## Access and developer mode

Studio access is server-authoritative. A request is allowed only when both conditions pass:

1. The world has the persistent Development Studio flag, or `allowInNormalWorlds` is explicitly enabled in the Development Studio server config.
2. The player is the integrated-server owner or has permission level 2.

The server config is generated at:

```text
config/wildernessodysseyapi/wildernessodysseyapi-development-studio-server.toml
```

`allowInNormalWorlds` defaults to `false`. This provides the planned extension point for an explicitly enabled disposable test world without granting Studio access on production worlds.

Every modifying request rechecks access. The client cannot choose an arbitrary dimension, position, item, entity, block, structure, or region through a packet.

## Opening Studio

The primary key is **F8**. It is a configurable key mapping named **Open Development Studio** in Minecraft Controls.

The command entry point is:

```text
/wilderness studio
```

F8 sends an empty open request. The server validates the player and world, produces a bounded snapshot, and then asks that client to open the UI. The command follows the same service and access policy.

## Modular interface

`StudioScreen` owns navigation and shared layout only. Pages implement `StudioPage`, while categories are registered through `StudioModuleRegistry`.

Phase 1 categories are:

- **World** — available; shows persistent world status, seed, campus state, and natural-generation contract.
- **Locations** — available; manages world bookmarks and campus destinations.
- **Inspector** — available; displays results produced by the server-owned Developer Tool.
- **Debug** — foundation; renderer extension registry exists, with no overlays enabled by default.
- **Structures, Entities, Ecosystem, Weather, Water, Worldgen, Lighting, Power, Security, Aether, Performance, Scenarios** — visible and explicitly deferred.

A deferred module does not run a substitute simulation or issue unsafe commands. Later phases can replace its registered metadata/page while retaining the stable module ID.

## Development Campus

The Phase 1 campus is authored as the reviewable StructureGen blueprint:

```text
src/main/structure_blueprints/development_studio_campus.json
```

The existing validated StructureGen pipeline compiles it to a standard Minecraft structure template in build output. The structure contains:

- a small Main Studio Hub
- Structure Lab pad
- Water Torture Lab pad
- Entity / Mob Lab pad
- Outdoor Test Area pad

The colored pads are controlled-test placeholders, not fake implementations of later gameplay systems.

`StudioCampusSiteFinder` evaluates a bounded set of positions 40–88 blocks around the real spawn. It samples the complete template footprint, rejects fluids and block entities, prefers natural low-slope Overworld surfaces, and scores slope before distance. It does not scan an unbounded radius or flatten a large region.

`StudioCampusPlacer` delegates to the existing `NBTStructurePlacer`, terrain-blending, chunk preparation, and template-loading infrastructure. Placement happens once and persists the exact template origin. An interrupted first creation is retried when a player enters the Studio world.

The current repository also owns an existing starter-bunker spawn workflow. Studio campus placement runs at the lowest spawn-event priority and accepts an already-canceled event so it remains additive to the bunker instead of replacing it.

## Campus locations

`StudioLocationRegistry` stores every destination as a stable ID plus a template-relative offset. No consumer carries scattered absolute coordinates.

Available Phase 1 locations are:

- `wildernessodysseyapi:main_hub`
- `wildernessodysseyapi:structure_lab`
- `wildernessodysseyapi:water_lab`
- `wildernessodysseyapi:entity_lab`
- `wildernessodysseyapi:outdoor_test_area`

IDs for later labs are reserved but marked unavailable. Teleport requests contain only a registered ID. The server rejects missing/deferred locations and derives the final destination from the persisted campus origin.

## World bookmarks

Bookmarks belong to the Studio world, not an individual client. A bookmark records:

- UUID
- name
- dimension
- block coordinates
- yaw and pitch
- biome at creation time
- optional notes
- optional tags
- server creation time

The seed is already world-level data and is shown in the World module, so it is not duplicated in every bookmark.

The Locations page supports:

- Save Here
- select/list
- rename and update notes/tags
- delete
- teleport

Names, notes, and tags are sanitized on payload construction and again by saved record construction. Formatting/control characters are removed, length and tag-count limits are enforced, duplicate tags are removed, and packet collection counts are rejected when out of range. The default world maximum is 128 bookmarks and is configurable up to 512.

Bookmark creation ignores client coordinates, dimension, rotation, biome, UUID, and timestamp. The server records those fields from the requesting player. Teleport resolves a stored dimension through the server registry, checks build height and world border, and then performs a server-side cross-dimension teleport.

## Developer Tool and Inspector

The **Wilderness Developer Tool** is available in the Tools & Utilities creative tab. It is intentionally not an unrestricted remote inspector:

- use in air opens the Studio overview;
- use on a block inspects that server-resolved block;
- interact with a living entity to inspect that server-resolved entity.

The tool first passes the normal Minecraft interaction target to `StudioInspectionRegistry`. The registry selects a compatible `StudioInspectionProvider<T>`, which returns a bounded immutable result.

The Phase 1 block provider exposes real values:

- registry ID
- position
- dimension
- complete block state
- fluid state
- block-entity type and removed state, when present

The Phase 1 entity provider exposes real values:

- entity type and UUID
- runtime entity ID
- position and velocity
- on-ground state
- health for living entities
- target, navigation active/idle state, and `NoAI` state for mobs

The provider does not use fragile reflection to enumerate private goals or fabricate AI labels. Water, ecosystem, security, power, structure, and weather inspection providers are deferred until their actual owners expose safe server-side diagnostics. Water integration must use `WaterServices` or a deliberately added diagnostic API, not internal storage duplication.

## Debug rendering

`StudioDebugRendererRegistry` is the Phase 1 overlay extension point. Each renderer has:

- a stable ID
- an independent enabled state
- a render stage
- a focused render callback

Every renderer defaults off. If no renderer is enabled, the render event performs a single boolean check and returns. No chunk, entity, water, weather, path, or structure data is collected while overlays are disabled.

No Phase 1 overlay is fabricated. Chunk boundaries, structure bounds, water spans, paths, weather cells, power networks, and campus bounds can be added as focused renderers when their required server/client data path exists.

## Test regions, reset, and snapshots

Test-region and reset systems are Phase 2 work. The registered campus pads provide stable future locations, but Phase 1 does not expose destructive reset operations.

Region snapshot restoration is deferred. The current water, ecosystem, block-entity, attachment, and chunk persistence systems make blind chunk-file replacement unsafe. No code deletes chunk files, rewrites region files, or claims that a block-only snapshot would restore authoritative subsystem state.

## Networking and safety model

Studio's network version is part of the mod's shared payload registration. Client-to-server messages are limited to high-level actions:

- request the Studio screen
- create/update/delete/teleport a bookmark by bounded fields and stored UUID
- teleport to a registered campus location ID

The client never receives an arbitrary world-edit primitive. Server handlers validate:

- player type
- persistent Studio-world flag or explicit normal-test-world config
- integrated owner or permission level 2
- action enum
- bounded strings and collection sizes
- stored bookmark UUID
- registered and available campus location
- target dimension availability
- target build height and world border
- per-world bookmark maximum

Inspection targets are obtained from server-side item interactions rather than a client target packet.

## Existing architecture integration

Phase 1 preserves the current repository owners:

- terrain and biomes: normal noise generator plus existing TerraBlender/Biolith/Lithostitched integration
- structures: existing template manager, `NBTStructurePlacer`, processors, terrain blending, placement diagnostics, and StructureGen
- water: existing `WaterServices`, attachments, authority, synchronization, hydrology, SPH, and rendering pipelines
- ecosystem: existing entity attachments, profile manager, controllers, and `EcosystemServices`
- weather: existing server `WeatherAuthority`, `WeatherServices`, simulation, sync, and rendering
- mob AI: vanilla/NeoForge entities plus current ecosystem and Rift entity goals
- Aether: current chat/fallback story layer only
- developer diagnostics: existing paged F3 overlay, water/weather debug commands, worldgen scan, placement diagnostics, and verification relay
- networking: existing NeoForge payload registrar
- configs: existing validated client/common/server registration path
- commands: existing `ModCommands` dispatcher composition

The repository does not currently contain a complete facility power network, keycard/security-door system, or dedicated Studio lighting controller. Those modules remain deferred rather than receiving fake replacements.

## Build and automated validation

Use JDK 21 and the checked-in wrapper:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
.\gradlew.bat runData
.\gradlew.bat generateStructures
.\gradlew.bat test --tests 'com.thunder.wildernessodysseyapi.developmentstudio.*'
.\gradlew.bat build
```

`runData` and `generateStructures` are separate invocations by repository design. Refreshing the StructureGen content catalog is useful after changing registry-affecting sources, even though the Phase 1 campus is deliberately vanilla-only.

Automated Phase 1 tests cover:

- data-driven preset structure
- normal Overworld noise and multi-noise biome delegation
- generated normal-Overworld noise marker behavior fields
- Create World normal-preset tag inclusion and display translation
- saved Studio identity, campus origin, and bookmark round trip
- text sanitation and tag bounds
- available/foundation/deferred module states
- available and reserved campus locations
- StructureGen blueprint parsing, validation, NBT compilation, and semantic re-read

A dedicated-server smoke test also created a disposable Studio world from the
`wildernessodysseyapi:development_studio` level preset. The server prepared a
normal Overworld, TerraBlender and Biolith recognized the Overworld path, and
the Development Campus placed near spawn. That smoke test does not replace the
interactive client and reload checks below.

## In-game validation

Automated compilation is not proof of Create World UI, placement, rendering, or reload behavior. Perform this release smoke test:

1. Run `runClient`.
2. Select **Singleplayer → Create New World → World → World Type**.
3. Confirm **Wilderness Odyssey Development Studio** appears.
4. Create the world with structures enabled. Creative and cheats are useful but not silently forced.
5. Confirm the normal starter-bunker workflow still runs if enabled.
6. Confirm the Development Campus appears only in a limited area near spawn.
7. Explore beyond it and compare mountains, rivers, oceans, caves, biomes, structures, water, ecosystem behavior, weather, and compatible modded generation with a normal world using the same seed.
8. Press F8 and run `/wilderness studio`.
9. Verify World, Locations, Inspector, and Debug status pages; confirm deferred modules do not modify the world.
10. Save, rename, retag, teleport to, and delete a bookmark.
11. Obtain the Wilderness Developer Tool from the creative tab. Inspect a block, block entity, passive mob, and hostile mob.
12. Teleport to each available campus location.
13. Quit and reload. Confirm the Studio flag, campus, origin, and bookmarks persist and the campus is not placed twice.
14. Restart a dedicated development server and repeat command/permission checks with an operator and non-operator.
15. Create a normal world using the same seed. Confirm Studio access is denied by default, no campus is placed, and natural generation remains unchanged.

## Current limitations

- No custom World Type tooltip exists because the 1.21.1 selector exposes only a translated preset name.
- The campus is a functional Phase 1 hub and four controlled-test pads, not final art.
- Campus placement samples a bounded candidate set and can decline placement in an exceptionally hostile spawn region; login retry remains safe but uses the same bounds.
- F8 does not automatically enable cheats, Creative mode, or unrelated gamerules.
- Bookmarks are world-shared and operator-authorized; per-developer ownership/filtering is not implemented.
- The Inspector intentionally exposes a conservative subset of entity and block-entity state.
- Debug renderer architecture exists, but Phase 1 supplies no active overlay.
- Test regions, reset, structure preview/place/remove, water diagnostics, environmental controls, stress presets, scenarios, and safe snapshots are deferred.
- Third-party mods that compare the noise-settings holder key instead of the real Overworld dimension key or generator behavior remain a compatibility risk; the bundled Biolith/TerraBlender path is part of the dedicated Studio-world smoke test.

## Recommended Phase 2

1. Add a persisted `StudioTestRegion` registry for the four Phase 1 pads, including type and reset-policy metadata.
2. Implement a Structure Lab service around registered structure templates with bounded rotation/mirror/offset requests and bounding-box preview first.
3. Add a safe block/entity reset abstraction scoped to registered test regions; do not manipulate chunk files.
4. Add an Entity Lab controller that tags Studio-spawned test entities and limits freeze/AI/invulnerability/clear operations to those tagged entities within the lab.
5. Implement independent campus/structure/test-region bounding-box renderers, all default off.
6. Add GameTests for access denial, bookmark server ownership, location-ID validation, campus one-time placement, and normal-world no-op behavior.
7. Only after those boundaries are proven, begin Phase 3 with read-only water, ecosystem, weather, and worldgen inspection adapters that use their existing public owners.
