# Anomaly wilderness

The anomaly uses a recognizable spruce wilderness with sparse physical faults and stronger effects toward fringe/core zones. Existing `anomaly_forest`, `anomaly_dimension`, gateway travel data, rift monsters and dimension height/night rules remain compatible. New terrain generation affects new chunks only. Existing terrain is not migrated.

## Biomes and terrain

| Biome ID, under `wildernessodysseyapi` | Character | Base atmosphere |
| --- | --- | --- |
| `anomaly_forest` | Dominant spruce forest, grass/stone, sparse faults and wildlife | 18% |
| `anomaly_plains` | Open grassland, very sparse conifers and rock/crystal details | 12% |
| `anomaly_hills` | Native rough terrain, exposed stone and cliff materials | 36% |
| `floating_fringe` | Grounded wilderness with more stationary debris and rare islands | 62% |
| `rift_core` | Sparse fractures, more crystals and rare dark rift formations | 86% |

Minecraft's existing Overworld noise settings still own terrain shape, caves, ravines and natural springs. The anomaly dimension uses a five-biome climate table. Overworld incursions extend the existing Lithostitched anomaly region and region-scoped replacements. Forest remains the broad default. Vegetation differs between dense forest/hills and sparse plains/fringe/core, while shared feature ordering stays consistent. Adjacent loaded biome samples and client interpolation soften atmosphere transitions.

The forest's `anomaly_conifers` placement uses vanilla full-crowned spruce with two tree attempts in open patches and six in denser patches. Spatial noise supplies the openings; it no longer uses the vanilla taiga placement's roughly ten attempts and pine/spruce mix. Existing trees are retained, so compare the forest composition in newly generated chunks.

Surface distortion uses natural dry block allowlists. It does not clear structures, place fluid sources, or simulate moving terrain. Whole chunks with structure starts or references are skipped conservatively. Local details include rocks, crystal growths, suspended stone, shallow faults and a rare noninteractive core formation. A decorative formation does not open a travel portal or invent rift gameplay state.

## Floating island structures

The three authored small variants are `anomaly_island_small_01`, `anomaly_island_small_02`, and `anomaly_island_small_03`. Their source lives in `src/main/structure_blueprints/`; the existing StructureGen task compiles them into compressed Minecraft NBT under the selected build directory's `generated/structuregen/resources/data/wildernessodysseyapi/structure/` and packages them through `processResources`.

Each template is 9×13×9, made from vanilla stone, earth, grass, moss, roots, persistent spruce leaves and small vegetation/crystal accents. Islands never move. The placement owner rejects unavailable chunks, chunk-edge crossings, occupied clearance cells, block entities, unsafe replacement datapacks, structure-bearing chunks, insufficient ground separation, world-border violations and build-height violations before passing the template to Minecraft. A one-block air margin must fit in one chunk. Minecraft's template manager owns reload/lifetime; the anomaly does not retain world references in its own template cache.

Medium/forest/waterfall templates are future content. Flowing island waterfalls are deliberately deferred until they can use the finite-water owner without creating repeatable infinite sources. Natural ground springs remain available. Template accents currently use vanilla blocks so blueprint compilation does not require refreshing the mod-aware registry catalog.

## Distorted blocks and glitch behavior

Eight block/item pairs are available: `distorted_stone`, `distorted_grass`, `distorted_dirt`, `distorted_moss`, `distorted_cliff_stone`, `unstable_rift_stone`, `anomaly_crystal_block`, and `fractured_energy_block`. They have blockstates, models, item models, loot, tool/soil tags and English names. Existing ore/gateway content is preserved.

The initial opaque 16px art uses natural dark materials with thin cyan/blue stable seams, violet spatial faults and selective red instability. `tools/anomaly/generate-terrain-textures.py --check` verifies reproducibility against the editable palette/shape source. Crack motes use nearby client block animation sampling: at most one mote after a rare sample and one exposed loaded-neighbor check. There are no block entities, server tickers or constantly flashing textures.

`AnomalyBlockMaterial` and `DistortedTerrainBlock.material()` expose stable/spatial/critical visual intent for a future optional material renderer. Animated emissive cracks, UV jitter and chromatic texture offsets are not implemented; client particles provide the current glitch effect without a material-rendering hack.

## Client atmosphere and post effects

Anomaly haze shortens only the air terrain-fog distance. The sky pass retains its native fog distances, and fluid visibility remains with the fluid renderer. Air-fog color blending still supplies the restrained anomaly tint.

The post chain returns its graded scene through `anomaly_copy`, using Minecraft's blit shaders with replacement blending (`one`, `zero`). Minecraft's standard blit program uses source-alpha blending, and each post pass clears its destination before drawing. A visible sky background can have zero framebuffer alpha; the standard blend therefore turned that background black. The replacement copy preserves both RGB and alpha without interpreting scene alpha as coverage. The resource regression checks visible sky colors at zero, partial and full source alpha. In-game acceptance still requires comparing the same anomaly view with `/woanomalyvisual post true` and `false`; `/woanomalyvisual reset` restores the configured presentation. The independent local-rift shader already emits opaque output and is unchanged.

The server sends local conclusions, not its seed or undiscovered coordinates. Every second it samples nine already-loaded biome columns, layers the existing managed rift state, and sends only changes plus a ten-second refresh. Temporary operator previews expire after sixty seconds and clear on travel, logout, respawn and shutdown. Hybrid-ring presentation reuses the rift owner's dimension-safe, bounded packet state and distance falloff on the client; the anomaly does not run a second block-entity search.

The client smooths strength, discards packets for another dimension, and fades stale summaries. Login/logout, level unload and respawn clear cached presentation and previews. Air fog receives a restrained cool tint with stronger violet toward core/rift influence; water/lava fog remains under its existing owner. Extra fog distance reduction is capped at twelve percent.

The optional post chain grades scene color before the HUD. It owns its own render targets/resources and never replaces Minecraft's global post slot. It releases resources on reload/disconnect and skips active external shader ownership, unsupported backends, fluid views, open screens and a pre-existing global post effect. A failure disables the pass until resource reload while ordinary fog/particles remain available. Motion distortion is off by default; when enabled it is a slow, very small local-rift pulse. There is no blur or constant chromatic split.

## Configuration

Generation settings live in `wildernessodysseyapi-common.toml`, category `[anomalyWorldgen]`:

| Key | Default | Meaning |
| --- | --- | --- |
| `enableSurfaceDistortion` | `true` | Sparse natural surface replacement in new chunks |
| `enableDetails` | `true` | Bounded rocks, crystals, fractures and remnants |
| `enableFloatingIslands` | `true` | Allow preflighted small template islands |
| `distortionDensity` | `0.30` | Density multiplier in `[0,1]` |
| `islandChance` | `0.035` | Maximum per-chunk chance in fringe/core, reduced elsewhere |

Client settings live in `wildernessodysseyapi-client.toml`, category `[anomaly]`: `fog`, `particles`, `colorGrading`, `postEffects`, and `pulses` default to `true`; `motionDistortion` defaults to `false`; `intensityScale` defaults to `1.0` in `[0,1]`. Screen Effects at zero suppresses screen motion and particle pulses. These settings change presentation only.

## Developer commands

Use a disposable creative test world. Server commands require permission level 2. Tab completion supplies biome/template names.

| Command | Effect |
| --- | --- |
| `/woanomaly status` | Current biome, zone and local anomaly/rift intensity |
| `/woanomaly locate anomaly_forest` | Delegates to native biome locate; accepts all five biome IDs |
| `/woanomaly zone rift_core 0.8` | Sixty-second local presentation preview, without terrain edits |
| `/woanomaly reset` | Restore natural local atmosphere immediately |
| `/woanomaly terrain anomaly_hills` | Apply only that zone's bounded detail pass to the player's loaded chunk; modifies natural terrain, skips protected chunks, does not regenerate trees/terrain shape or change its biome |
| `/woanomaly blocks` | Give one of each new block; inventory overflow drops nearby |
| `/woanomaly inspect_island anomaly_island_small_01 <x> <y> <z>` | Full read-only placement decision and rejection reason |
| `/woanomaly island anomaly_island_small_01 <x> <y> <z>` | Repeat preflight and place a stationary island |
| `/woanomaly spawn_rift` | Delegate to the existing real Overworld rift debug command; its terrain/gameplay consequences remain owned by that system |
| `/woanomalyvisual` | Client presentation status |
| `/woanomalyvisual post false` | Disable the client's post preview |
| `/woanomalyvisual intensity 0.5` | Client-only visual intensity preview |
| `/woanomalyvisual zone floating_fringe` | Client-only zone preview |
| `/woanomalyvisual reset` | Clear client previews |

For an island, choose an already-loaded chunk and use an origin with local X/Z around 3, at least twelve blocks above its highest surface/tree canopy. The complete template and air margin must fit inside that chunk. Run `inspect_island` first; its rejection reason identifies the unmet constraint. Commands do not bypass safety to force a placement.

## Validation and manual acceptance

Use JDK 21 and the checked-in wrapper, with only one Gradle/Minecraft development process running. Quote the isolation property in PowerShell:

```powershell
.\gradlew.bat compileJava '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat anomalyTest '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat playtestTest --tests '*WildernessConfigSpecsTest' '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat build '-PcodexBuildDir=.codex-build' --no-parallel
```

Automated checks cover climate matching, resource contracts, blueprint bounds, full island-preflight rejection, particle/effect budgets, accessibility, payload codec validation, interpolation and temporary-override expiry. The normal StructureGen pipeline validates, writes and re-reads NBT before packaging.

Validation completed on 2026-10-11 with JDK 21:

- `compileJava`: passed.
- `anomalyTest`: 54 tests across 14 suites, zero failures/errors/skips.
- Focused `WildernessConfigSpecsTest` through `playtestTest`: two tests, zero failures/errors/skips.
- `build`: passed, including normal resource processing and StructureGen verification.
- Final JAR inspection: all three compressed small-island NBT entries, all four new biome resources and the anomaly post chain are present.
- Terrain texture generator `--check`: all nine textures match the editable source.
- `git diff --check`: passed.

StructureGen reported a stale mod-aware catalog fingerprint and ignored modded catalog content; all fifteen current blueprints still validated, generated and passed NBT re-read verification. The new anomaly islands use vanilla block IDs. No catalog refresh or Minecraft launch was needed for these checks. The repository's normal `build` does not run the root mod's full JUnit suite; the focused test results above are the anomaly test evidence.

Manual acceptance remains necessary: locate each biome in new chunks; compare long-distance normality and close-range faults; inspect native slopes/openings, tree density and flora; check islands against cliffs/structures; test attempted occupied/chunk-edge placement; travel and respawn in multiplayer; toggle Screen Effects, particles and post settings; reload resources and resize the window; compare vanilla/Fabulous and shader-pack modes; inspect generation time with representative modpack profiling. Unit tests and a successful build do not prove visuals, gameplay, multiplayer or performance.

## Remaining art and next step

The initial terrain textures and three small NBT islands are functional assets. Custom ambient audio, authored medium/waterfall islands and a dedicated animated emissive material pass remain future work. Aether warnings, anomaly weather, research structures, custom creatures, quests and stabilized-rift progression remain outside this change.

The next development step is acceptance in a disposable modpack world: record biome distribution, representative screenshots and chunk-generation timings, then tune climate slots, density and authored island compositions from that evidence before adding finite-water waterfalls or stronger visual materials.
