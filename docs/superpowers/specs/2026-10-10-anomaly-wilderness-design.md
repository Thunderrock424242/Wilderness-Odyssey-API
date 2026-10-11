# Anomaly wilderness design

## Intent and scope

Revamp the anomaly for NeoForge 1.21.1 into a recognizable conifer wilderness where reality progressively malfunctions. Preserve `anomaly_forest`, `anomaly_dimension`, existing travel/save data, and the temporal-rift gameplay owner. Changes apply to new chunks; do not rewrite existing terrain. Do not implement the future quest, creature, weather, or Aether systems in this change.

## Biomes and terrain

Use five data-pack biomes: `anomaly_forest`, `anomaly_plains`, `anomaly_hills`, `floating_fringe`, and `rift_core`. Keep Minecraft's Overworld noise terrain. Use a climate-driven multi-noise source in the anomaly dimension and the existing Lithostitched anomaly region in the Overworld. Forest is the dominant variant; plains provide openings, hills use rough native terrain, and fringe/core increase sparse distortion. Keep grass, stone, dirt and spruce dominant. Reuse caves, ravines, springs and vegetation features; share feature ordering between variants.

Biome strength is a common read-only policy exposed as `AnomalyZone` with biome path, display name, and base intensity. Server-local sampling blends nearby loaded biomes for atmosphere. Clients receive only local conclusions, never the world seed. Visual interpolation smooths transitions.

## Floating structures and safety

Provide three small stationary structure templates named `anomaly_island_small_01`, `anomaly_island_small_02`, and `anomaly_island_small_03`. Author them in the existing structure-blueprint pipeline using vanilla blocks so generation does not depend on a mod-aware catalog refresh. Keep the medium template names reserved for later authored variants; do not advertise unavailable templates as usable.

The worldgen feature works within its origin chunk. Skip structure starts/references and block entities, validate the entire template bounding box and air clearance before placement, require build-height margins and ground separation, and never force-load chunks. Place through Minecraft's template API with quiet generation flags. Decorative suspended roots/debris are stationary blocks. Avoid automatic flowing waterfalls until finite-water integration is defined; existing natural springs supply ground waterfalls.

## Distorted blocks

Register eight block/item pairs in `AnomalyBlocks`: distorted stone, grass, dirt, moss, cliff stone, unstable rift stone, anomaly crystal block, and fractured energy block. Preserve existing ore/gateway registrations. Supply blockstates, models, loot, tool tags, translations, and subdued textures. Natural/dark surfaces dominate; cyan/blue indicates stable energy, violet spatial distortion, and sparse red critical instability. No block entities or server tickers. Low-frequency client particles provide crack pulses; expose a clean visual policy for future material shader support.

## Client effects

Replace the purple dimension override with strength-driven cool fog that respects fluid fog. Keep optional fog, particles, color grading and motion distortion separately configurable in the unified client config. Respect Minecraft's screen-effect accessibility scale. Own an independent optional post chain, never take the global `GameRenderer` effect slot, skip conflicts with active shader integrations, close resources on reload/unload, and disable with a diagnostic after an effect failure. No constant blur or heavy chromatic aberration. Rift influence layers above biome strength using the existing temporal-rift state.

## Developer tools and lifecycle

Add an operator-only `/woanomaly` command for status, locating variants, forcing a temporary local zone/intensity, safely placing a named island in empty loaded space, receiving preview blocks, and inspecting placement rejection reasons. Use the existing `/wildernessrift debug_open_near` for a real test rift. Client-only commands toggle post effects and preview visual intensity. Temporary server overrides are bounded, expire, and clear on shutdown; client state clears on disconnect/dimension changes. Locate may use Minecraft's existing `/locate biome` path rather than a new unbounded search.

## Validation and acceptance

Use one Gradle process and `-PcodexBuildDir=.codex-build --no-parallel`. Compile first, then focused offline anomaly JUnit coverage and resource/template semantic checks; build for packaged resources. Tests cover biome reachability/resource contracts, safe placement rejection, effect clamps/accessibility, payload validation and lifecycle. Actual terrain composition, visual quality, GPU compatibility, multiplayer transitions, and generation performance require a disposable Minecraft test world; compilation and unit tests do not prove these.
