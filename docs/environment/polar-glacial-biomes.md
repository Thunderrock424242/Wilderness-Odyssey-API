# Polar Glacial Biome System

## Overview

The polar glacial system adds a connected five-biome family to cold Overworld climate outcomes. Lithostitched owns region selection and region-constrained biome-source injection; data-pack biome resources own feature ordering; the glacial runtime owns only bounded seasonal surface changes. It does not replace the localized weather authority, the Wilderness water authority, or an external season calendar.

The intended coast-to-interior order is:

1. `wildernessodysseyapi:iceberg_coast`
2. `wildernessodysseyapi:glacial_meltwater_valley`
3. `wildernessodysseyapi:polar_glacial_basin`
4. `wildernessodysseyapi:glacial_highlands`
5. `wildernessodysseyapi:polar_ice_sheet`

The data-driven `wildernessodysseyapi:polar_glacial_region` keeps the family together at a large geographic scale. Lithostitched injectors then map related vanilla cold outcomes only inside that region instead of registering a competing biome-source implementation: frozen oceans become the coast, frozen rivers become meltwater valleys, ice spikes and groves become basins, snowy slopes and frozen/jagged peaks become highlands, and snowy plains become the interior sheet. Individual biome switches are evaluated when the injectors are registered for a server, so disabled family members remain vanilla.

## Visual identity

The family deliberately shares white snow, packed glacier mass, and deep blue compression ice without making every member look interchangeable:

- The Polar Ice Sheet uses long, low-frequency rolls and broad wind-packed snow drifts. Its profile remains open enough for a distant horizon instead of becoming another spike biome.
- Glacial Highlands add a separate mountain-mass signal above sharp local ridges. Tall faces expose sparse tuff and stone buttresses plus coherent blue-ice compression bands, keeping the mountains readable against the snow.
- The Polar Glacial Basin folds a broad noise field around a low center. This creates wide U-shaped corridors with high ice walls rather than applying the same raised ice cap to every column.
- The Glacial Meltwater Valley stays comparatively low and uses bright seasonal channels as its primary contrast. Summer surface water shifts toward saturated cyan while underwater absorption remains deep cobalt.
- The Iceberg Coast mixes irregular spires with iconic tabular Antarctic icebergs. Large forms can have steep calved faces, submerged keels, blue pressure bands, top clefts, caves, wind-shaped snow caps, and continuous cracked shelf patterns instead of isolated checkerboard holes.

Snow depth varies from thin scoured crust to deep seven-layer drifts using broad and fine deterministic fields. Crevasses curve and pulse in width instead of remaining ruler-straight; rare ice caves gain coherent blue compression bands and small stalactite/stalagmite silhouettes. Frozen waterfall curtains taper and bulge away from cliff faces, retain a permanent blue core, and terminate in a grounded frozen apron.

Client ambience reinforces those identities without becoming a second simulation owner. Exposed sheets and highlands receive slightly denser blowing snow and occasional low spindrift puffs. During melt periods, valleys, basins, and coasts can show a single nearby splash or cave drip sample. These effects remain player-local and bounded.

## Generation ownership

Six registered features compose the terrain:

- `glacial_terrain` adds deterministic snow, packed-ice, and blue-ice layers with family-specific relief and coastal shelves.
- `glacial_river` follows a bounded downhill path through valleys, basins, and highlands.
- `glacial_crevasse` cuts variable-width surface fissures, including rare deep cracks.
- `glacial_ice_cave` forms curved ellipsoid chambers and short connecting tunnels below the glacier.
- `glacial_waterfall` creates narrow ordinary-ice cliff curtains with permanent blue-ice cores. Ordinary ice is intentionally the seasonally reversible part.
- `iceberg_formation` builds coastal icebergs with larger underwater mass, blue-ice cores, occasional caves, and broken shelf ice.

Every feature checks the active world-generation write boundary and existing structure bounding boxes before accessing or changing a block. Most passes remain inside their owning chunk; large coastal icebergs may extend only into the writable generation region and are clipped at its edge rather than requesting a neighbor chunk. Packed ice and blue ice represent permanent glacier mass; the loaded-chunk season runtime only thaws ordinary ice.

The biome tag API is available to future structures and exploration content:

- `#wildernessodysseyapi:is_glacial`
- `#wildernessodysseyapi:is_glacial_inland`
- `#wildernessodysseyapi:has_glacial_rivers`
- `#wildernessodysseyapi:has_glacial_waterfalls`

## Seasons and water

`GlacialSeasonManager` consumes only `WeatherServices.query().seasonalClimateAt(...)`. The existing Ecliptic Seasons integration is therefore reused without linking glacial code to Ecliptic classes. Serene Seasons remains the secondary adapter. When neither calendar is installed, the system uses a stable polar-cold fallback and does not invent a calendar date.

The shared seasonal climate API exposes an optional normalized cycle phase. Existing callers using the older five-value seasonal record remain source-compatible. A finite phase maps to spring, summer, autumn, and winter quarters; bounded climate factors remain the fallback for calendars without a temperate phase.

Server block changes are deliberately gradual:

- chunk load/unload events retain only keys for loaded glacial chunks;
- `getChunkNow` prevents the scheduler from loading terrain;
- work is skipped when no players are in the dimension;
- only chunks within the configured player radius are eligible;
- every inspected position consumes the per-level tick budget;
- positions inside structure bounding boxes are skipped;
- exposed water may become ordinary ice during cold periods;
- ordinary surface ice may become Wilderness water during melt periods;
- packed and blue glacier mass never seasonally disappears.

Thawing first delegates to `WildernessWaterAuthority.addWaterVolume`. Freezing drains authority-owned water before placing ordinary ice. A namespaced-fluid projection is used only as the safe fallback when detailed volume is unavailable.

The server synchronizes a compact dimension-aware season snapshot when its visual signature changes, with a sparse refresh for joining clients. Clients apply that state to all existing water paths: the namespaced fluid fallback, mobile/SPH rendering, enhanced chunk meshes, and underwater optics. Surface colors interpolate from winter cobalt toward distinct summer cyan/blue colors for each family; underwater optics use a deeper cobalt absorption color, longer visibility, and reduced turbidity to retain clear Antarctic meltwater. Season transitions queue at most two glacial surface-chunk rebuilds per client tick and reuse the water renderer's incremental mesh queue.

## Ambience

Glacial ambience is client-local and never changes authoritative world state. In a glacial biome it can emit a small number of wind-transported snowflake particles using the existing localized wind sample. Sounds are intentionally sparse: deep wind/cave ambience, distant low-volume ice cracks, cave drips, and summer meltwater. Particle and sound toggles are client config values.

## Configuration

Common generation settings are under `polarGlacialBiomes`:

- master system toggle;
- one placement toggle per biome;
- crevasse, ice-cave, river, and waterfall toggles.

Server runtime settings are under `seasonalGlaciers`:

- seasonal effects master toggle;
- river/surface-water freezing toggle;
- meltwater toggle;
- maximum inspected positions per level tick;
- target interval between chunk passes;
- maximum distance from a player.

Client presentation settings are under `glacialAmbience`:

- ambient sounds;
- blowing snow particles.

Generation settings affect newly generated terrain only. Runtime and client settings are read directly during operation and do not create a second cached configuration owner.

## Operator commands

The following permission-level-2 commands are available through both `/wilderness glacier` and `/wo glacier`:

- `info` reports the biome and family, calendar source, season/override state, temperature offset, melt/freeze strength, resolved water tint, and bounded scheduler counters.
- `season spring|summer|autumn|winter|polar_cold` sets a non-persistent Wilderness-only development override.
- `season clear` returns control to the installed calendar or polar fallback.

Overrides never mutate Ecliptic Seasons or Serene Seasons state and are discarded when the dimension unloads or the server stops.

## Validation

Automated tests cover the pure calendar mapping, fallback and override semantics, seasonal water-color interpolation, work-budget ceiling, all five biome JSON contracts, feature registration data, and stable family-tag order.

Manual in-game acceptance should use a fresh world for generation evidence and should separately verify:

1. `/locate biome` can find all five biome IDs and the transitions read naturally at cold coast, river, basin, mountain, and plain outcomes.
2. Icebergs have submerged mass and occasional cavities; highlands/basins show crevasses, caves, channels, and frozen curtains without cutting registered structures.
3. `/wilderness glacier info` reports the correct family and bounded scheduler activity without increasing loaded chunk counts.
4. A summer override gradually thaws only ordinary ice and produces Wilderness meltwater; packed and blue ice remain intact.
5. A winter override gradually refreezes exposed water near players without scanning distant or unloaded regions.
6. Seasonal cyan/cobalt changes agree between standard fluid rendering, enhanced water surfaces, SPH/mobile effects, and underwater fog.
7. Blowing snow follows wind direction, ambience remains sparse, and both client toggles disable their effects.
8. Dedicated-server startup and two clients confirm the payload channel, dimension changes, late joining, and override synchronization.

A successful compile, JVM test, packaged JAR, or title-screen launch does not by itself prove these live terrain, visual, command, multiplayer, or gameplay behaviors.
