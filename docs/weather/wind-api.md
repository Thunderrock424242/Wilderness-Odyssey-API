# Wilderness Odyssey Wind API

The Wind API is a lightweight, query-only layer over Wilderness Odyssey's
localized atmosphere. It does not own another simulation, tick every block, or
load chunks. Server consumers read the authoritative weather grid; client
consumers read the same bounded regional weather snapshots already used by
clouds and precipitation.

## Quick start

```java
import com.thunder.wildernessodysseyapi.weather.api.WindManager;
import com.thunder.wildernessodysseyapi.weather.api.WindSample;

WindSample wind = WindManager.getWind(level, position);

Vec3 direction = wind.direction();
float sustainedBlocksPerSecond = wind.speed();
float gustBlocksPerSecond = wind.gust();
float effectiveBlocksPerSecond = wind.effectiveSpeed();
Vec3 velocityPerTick = wind.velocityPerTick();
```

`level` may be a `ServerLevel` or `ClientLevel`. `position` may be a `BlockPos`
or `Vec3`. A dimension where Wilderness localized weather is not active returns
a calm sample instead of consulting vanilla global rain as a substitute.

## `WindSample` contract

| Value | Meaning |
| --- | --- |
| `direction()` | Normalized Minecraft-space `Vec3`. Positive X is east, positive Z is south, and Y is bounded convective/turbulent motion. |
| `speed()` | Sustained wind in blocks per second, excluding the current gust. |
| `gust()` | Current coherent additive gust in blocks per second. |
| `effectiveSpeed()` | `speed + gust`, capped by `maxWindSpeed`. |
| `weatherContribution()` | Portion of sustained speed contributed by interpolated atmospheric wind and storm state. |
| `gustFactor()` | Current regional gust envelope in `[0, 1]`, useful for animation intensity. |
| `gustPhase()` | Containing region's normalized gust-cycle phase in `[0, 1]`. |
| `gustCycle()` | Deterministic regional cycle number, useful for one-shot effects that must fire once per gust. |
| `region()` | Containing `AtmosphereCellKey` for diagnostics and regional caching. |
| `velocity()` | Full effective velocity in blocks per second. |
| `velocityPerTick()` | Full effective velocity divided by 20 for tick-based motion. |

Use `speed()` for motion that should remain stable through a gust. Use
`effectiveSpeed()` or `velocityPerTick()` when gust response is desired. Client
visuals should normally use the sample as presentation input; authoritative
gameplay such as fire spread must query on the server.

## How wind is derived

Every query combines five bounded inputs:

1. A slowly evolving deterministic ambient direction and strength, giving clear
   weather a weak or moderate natural breeze.
2. The localized atmosphere's bilinearly interpolated pressure-driven
   `WeatherSample.wind()`.
3. Cloud water, instability, pressure deficit, precipitation, and storm energy,
   which make approaching and mature storms progressively windier.
4. A deterministic regional gust envelope derived from atmospheric cell
   coordinates, dimension identity, and synchronized world time.
5. `WeatherSample.verticalMotion()` plus bounded storm turbulence for the Y
   component.

Ambient values and gust envelopes are sampled at atmosphere-cell centers and
bilinearly blended with smooth interpolation. Wind therefore does not step at
chunk borders or atmosphere-cell boundaries. No plant, particle, or other
consumer receives its own random gust timer: objects querying the same area and
tick receive the same gust.

The model performs a fixed amount of arithmetic per query. It stores no block
field, iterates no loaded blocks, and has no wind tick event.

## Networking and multiplayer

The server remains authoritative for weather and configuration. The existing
low-frequency regional weather payload carries one clamp-safe `WindSettings`
profile in addition to interpolated atmosphere cells. Clients derive ambient
wind and gusts locally from:

- synchronized weather cells;
- atmospheric cell size;
- dimension identifier; and
- level game time.

There is no client-to-server wind packet and no per-tick wind synchronization.
Weather snapshot schema version 4 adds the captured wind profile. A config
reload invalidates the existing player weather regions, so connected clients
receive the updated profile on the next scheduled snapshot.

## Server configuration

Wind settings live under `[weather.wind]` in
`wildernessodysseyapi-server.toml`.

| Setting | Default | Unit and effect |
| --- | ---: | --- |
| `windEnabled` | `true` | Master switch for non-zero Wind API samples. |
| `baseWindStrength` | `2.5` | Clear-weather sustained speed in blocks per second. |
| `gustFrequency` | `2.0` | Average regional gust cycles per Minecraft minute (1,200 ticks). Zero disables gust pulses. |
| `gustStrength` | `5.0` | Maximum additive gust speed in blocks per second before the global cap. |
| `stormWindMultiplier` | `1.8` | Amplification reached at maximum localized storm severity. |
| `maxWindSpeed` | `24.0` | Hard cap for sustained wind plus gusts in blocks per second. |

`windEnabled` is subordinate to localized weather ownership. If Wilderness
weather yields the dimension to another full weather mod, the Wind API returns
calm instead of manufacturing an unrelated fallback simulation.

## Debugging

- `/wilderness weather wind` reports direction, sustained speed, gust strength
  and phase, effective speed, weather contribution, and atmosphere region.
- `/wilderness weather sample` includes the same resolved wind line after the
  underlying atmospheric values.
- The Wilderness weather F3 page shows raw surface/cloud wind and the resolved
  three-dimensional Wind API result separately.

These diagnostics make it possible to distinguish pressure-driven weather wind
from ambient strength and the current deterministic gust.

## Consumer guidance

- Grass, foliage, flags, smoke, dust, rain, snow, particles, ambient sounds, and
  water rendering may query on the client. Reuse one sample per effect region or
  frame when many vertices share a location.
- Fire spread or other gameplay consequences must query on the server and apply
  their own bounded cadence and safety policy.
- Do not create per-object random gust timers. Use `gustFactor()`,
  `gustPhase()`, or `gustCycle()` so nearby effects react together.
- Do not interpret blocks per second as an unconditional entity push. Convert
  through `velocityPerTick()` and apply a consumer-specific response factor.
- Ordinary atmospheric wind does not replace the existing mature
  tornado/cyclone entity-force policy. That severe-weather scheduler retains its
  own server authority and maturity gate.

## Verification checklist

Automated tests cover determinism, negative cell coordinates, clear-to-storm
scaling, coherent gust timing, vertical motion, maximum-speed clamping,
different dimension fields, high-speed coordinate queries, and continuity at
both chunk and atmosphere-cell borders. The weather payload test verifies that
server wind settings survive the multiplayer wire format.

For an in-game pass:

1. Run `./gradlew runClient` and use `/wilderness weather wind` while crossing
   chunk and atmosphere-cell borders. Direction and speed should change
   continuously.
2. Move rapidly with spectator flight or an Elytra and confirm the F3 wind line
   remains finite and follows the current region without delayed per-chunk
   state.
3. Compare clear weather, an approaching front, forced rain, thunder, and a
   mature severe system. Sustained speed and weather contribution should rise
   progressively; nearby effects should share gust timing.
4. Change dimensions and confirm the old snapshot is cleared, the new
   dimension receives a distinct ambient field, and unsupported dimensions are
   calm.
5. Join a dedicated server with two clients standing in the same region. Their
   gust factor and phase should agree apart from normal snapshot interpolation
   during initial join.
6. Increase render distance and profile client rendering. Wind itself should
   add no scan proportional to chunks, blocks, or render distance; consumer
   renderers remain responsible for their own query budgets.

## Current limits

The current persistent cyclone model does not synchronize an explicit eye
radius to clients, so the Wind API does not invent a client-only calm eye.
Pressure, precipitation, and storm fields can still produce locally reduced
wind where the authoritative atmosphere does. If explicit cyclone eyes are
added later, their calm factor should become a server-authored weather field or
bounded snapshot value used by this same model.
