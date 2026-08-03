# Weather-Water Coupling

The weather and water systems share public boundaries while retaining one
owner for each kind of state. `WeatherQuery` owns atmospheric sampling,
`OceanSeaStateField` owns the derived regional physical response, and
`WaterAccess` remains the only authority allowed to realize water-volume
changes. No coupling component writes directly into the atmosphere simulation
or creates an independent water store.

## Data flow and ownership

The existing atmosphere input path samples generated Wilderness water and
turns nearby ocean and lake coverage into moisture and thermal context. The
return path starts only after the server weather simulation has updated:

```text
GeneratedWaterChunk / WaterAccess
  -> WildernessWeatherWaterInfluence
  -> AtmosphereInputSampler
  -> AtmosphereSimulationEngine
  -> WeatherQuery
       -> OceanSeaStateField
       -> WeatherHydrologyManager
       -> SurfaceWeatheringScheduler
```

`WaterWeatherServerEvents` runs at the lowest server-tick priority. This makes
the tick order explicit: the atmosphere first publishes the current localized
state, then water derives sea state and hydrologic flux from that state. The
coupling does not tick when the water system is disabled.

## Regional sea state

`OceanSeaStateField` divides the player-relevant world into configurable cells,
128 blocks wide by default. At each cell center it samples localized weather
and derives a bounded target containing:

- wind speed and direction;
- swell and chop amplitudes;
- directional blending; and
- breaking-wave intensity.

Wind vector, storm energy, precipitation, thunder, and pressure deficit all
contribute to the target. When wind is nearly calm, the previous direction is
retained to prevent arbitrary wave-axis snapping. Exponential response uses a
shorter build time and a longer decay time so storms arrive decisively but
water settles naturally.

The field creates only the square of cells around active players, expires stale
cells, and enforces a hard entry budget. Server-side waves, shore interactions,
buoyancy inputs, and water queries sample this field at their world position.
Sampling is bilinear across adjacent cell centers.

Every second, `OceanSeaStateSynchronizer` sends each player only their bounded
nearby window. `ClientOceanSeaState` validates the window, retains current and
target samples, interpolates temporally, then performs the same spatial blend
for rendering, immersion, shoreline visuals, and ambient effects. A new
protocol version prevents older clients from decoding the regional payload as
the former single dimension-wide sample.

When localized weather is not authoritative in the dimension, the server sends
an explicit disabled payload. The client clears regional state and uses the
dimension-wide vanilla rain/thunder fallback instead of retaining stale local
conditions.

## Finite-body hydrology

`WeatherHydrologyManager` performs a small deterministic set of surface probes
around each player at a configurable interval. It uses only already-loaded
chunks, deduplicates probes by chunk, and ignores dry locations and large
oceans. Each valid lake or river sample passes through `WaterCycleFluxModel`:

- rain and hail add volume;
- snow waits in surface snowpack and contributes only during thaw;
- hot, dry, windy conditions evaporate volume; and
- rivers use smaller gains and losses than lakes.

The pure flux model returns signed authority units. `HydrologySavedData` stores
the fractional remainder per chunk in milli-units, with strict codec bounds,
finite values, an entry budget, and last-touched metadata. Once a balance
crosses the configured transfer threshold, the manager performs a bounded add
or remove through `WaterAccess` at the representative surface. It consumes only
the amount the authority actually accepted, so failed or partial transfers do
not destroy accumulated volume.

This ledger is accounting, not a second physical simulation. It cannot be
queried as swimmable water, does not render, and does not bypass canonical
projection rules. Large oceans stay neutral to avoid manufacturing or draining
effectively infinite bodies.

## Freeze ownership

`SurfaceWeatheringScheduler` may still place frosted ice over vanilla or
externally tagged water. It first checks `WaterAccess`, however, and never
replaces Wilderness-owned water. Direct replacement would leave canonical
volume behind a solid block and create conflicting authorities.

For custom water, the localized frozen fraction is sent through the existing
weather shader uniform. The vertex stage progressively damps waves and impulses
without abruptly changing the tide level. The fragment stage flattens normals,
raises roughness and opacity, lowers transmission and foam, and blends toward a
pale ice surface. The result preserves canonical liquid behavior and is not a
walkable solid. A future solid-ice feature must add an explicit, reversible
canonical frozen-volume representation rather than replacing projection blocks
opportunistically.

Shader-pack ownership remains respected. If an external pack owns the water
pass, it must implement the frozen visual itself; the server still preserves
volume correctly.

## Configuration

All settings live under `water_simulation.weather_coupling`:

| Setting | Default | Purpose |
| --- | ---: | --- |
| `enabled` | `true` | Enables the localized weather-water connection. |
| `seaStateCellSize` | `128` | Width of each regional sea-state cell. |
| `seaStateSyncRadiusCells` | `2` | Cell radius synchronized around each player. |
| `seaStateUpdateIntervalTicks` | `10` | Server field update cadence. |
| `seaStateBuildTimeSeconds` | `35` | Approximate approach time while roughening. |
| `seaStateDecayTimeSeconds` | `180` | Approximate approach time while calming. |
| `seaStateMaxCells` | `2048` | Hard in-memory regional-cell budget per level. |
| `enableHydrology` | `true` | Enables finite lake and river balance changes. |
| `hydrologyIntervalTicks` | `40` | Hydrology sampling cadence. |
| `hydrologyProbesPerPlayer` | `4` | Bounded surface probes per player and pass. |
| `hydrologyMaxTransfersPerTick` | `4` | Authority mutations allowed per level and pass. |
| `hydrologyRainUnitsPerProbe` | `48` | Maximum wet-weather credit per probe. |
| `hydrologyEvaporationUnitsPerProbe` | `18` | Maximum dry-weather debit per probe. |
| `hydrologyMinTransferUnits` | `64` | Balance required before a physical transfer. |
| `hydrologyMaxLedgerEntries` | `4096` | Runtime persistent-ledger budget per level. |

## Verification

Automated verification covers the pure weather-to-wave response, calm-direction
retention, asymmetric build and decay, payload round trips and decode limits,
rain/evaporation/snowmelt flux, ocean neutrality, persistent signed fractional
balances, and CPU-to-shader freeze inputs.

Use JDK 21:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

For an in-game pass, first apply clear, rain, and thunder with vanilla
`/weather`. Its broad localized override should make nearby waves build, change
direction, break at shores, and decay without snapping. Next use two players far
enough apart to occupy different weather cells. Run `/wilderness weather force
rain` in one area and `/wilderness weather clear` in the other; each player
should retain their local sea condition. Cross the boundary by boat and confirm
the transition is smooth. Observe a finite lake through sustained rain and then
hot, dry, windy weather; the response should be gradual and limited to loaded,
player-relevant chunks. Finally, inspect a freezing custom shore: the water
should become visually still and icy without losing its canonical volume or
turning into a conflicting solid projection.
