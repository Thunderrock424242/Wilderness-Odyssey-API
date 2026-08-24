# Reactive Vegetation

Reactive Vegetation gives loaded chunks a compact plant-climate state and lets a small, explicit set of plants react to that state. It is designed for NeoForge 1.21.1 and does not turn ordinary plants into block entities or independently ticking objects.

External hazards reach plants through the bounded disturbance handoff described
in [Shared world-system integration](../environment/world-system-integration.md).
Reactive Vegetation validates and owns every resulting plant mutation.

## Ownership and data flow

Localized weather remains authoritative. Vegetation reads it through `WeatherServices.query()` only when a loaded chunk reaches its scheduled update. The optional season integration is sampled through the same weather boundary and reduced to plant-relevant `UNKNOWN`, `GROWING`, `WET`, `DRY`, or `DORMANT` states.

```text
localized weather + optional season adapter
                 |
                 v
loaded chunk's ReactiveVegetationState attachment
  moisture, recent rainfall, drought, storm, season
                 |
        bounded surface probes
                 |
                 +--> registered block-state reaction
                 +--> sparse vanilla mushroom random-tick opportunity
                 +--> synchronized client drought tint
```

The attachment is persisted with the chunk. Loading restores moisture and drought continuity. Unloading removes the chunk from the scheduler immediately, and the climate model deliberately does not catch up elapsed unloaded time.

## Performance model

The scheduler tracks only loaded `LevelChunk` instances received from normal chunk lifecycle events. Each chunk receives a deterministic staggered due tick. The server drains due work with a per-tick cap based on loaded chunk count and `updateInterval`; rainfall never enqueues a global vegetation pass.

For each due chunk:

1. One localized weather sample and one cached season/environment sample update regional climate.
2. At most `updatesPerChunk` surface columns are selected from the existing chunk heightmap.
3. Only a selected registered block receives a plant behavior callback.
4. Only a changed property state is synchronized with `Block.UPDATE_CLIENTS`; neighbor updates are not broadcast.

The cost depends on loaded chunks and configured probes, not forest density. There are no vegetation block entities, full-chunk block scans, per-plant weather queries, or rain-triggered replacement waves.

Client grass dryness is a tint layered over Minecraft's current biome grass color. That preserves biome and season-mod color ownership. Synchronized climate changes invalidate at most two client chunks per tick, and only their heightmap-identified surface sections are rebuilt.

### Initial snapshot batching

When Minecraft begins tracking a loaded chunk for a player, the server creates
the same immutable, dimension-aware vegetation climate snapshot as before.
With both `performance.enabled` and
`performance.dataEngine.networkBatching` enabled, Phase 4 places that snapshot
in the Data Engine's bounded per-player queue. Chunk-tracking bursts can share
one packet, and a newer complete snapshot for the same dimension and chunk
replaces an older pending one. The configured maximum delay defaults to two
ticks.

This is transport coalescing only. Minecraft still owns chunk tracking and
packet lifecycle, the vegetation attachment remains authoritative, and the
client's existing monotonic revision and dimension checks remain the acceptance
boundary. Later climate changes for an already tracked chunk use the original
direct packet path. If the performance stack or Data Engine batching is
disabled, or the bounded queue rejects the entry, the initial snapshot also
uses that direct path immediately.

## Current behavior

### Moisture and drought

Liquid precipitation, localized surface wetness, humidity, heat, and wind feed a retained regional moisture model. Sustained dry weather gradually raises drought. Rain raises recent rainfall and moisture, then reduces drought according to `rainRecoveryRate`.

Vanilla grass blocks, short/tall grass, and ferns visually blend toward a dry straw color under prolonged drought. Recovery changes the tint back toward the biome-owned color. No grass block is replaced.

### Mushrooms

Brown and red mushrooms are conservatively registered by default. Once retained moisture and recent rainfall produce a high mushroom opportunity, a scheduler-selected mushroom can receive one extra call to its normal vanilla random-tick behavior. Vanilla remains responsible for spread rules and placement validity. Dry regions receive no extra opportunity.

### Flowers

No vanilla or third-party flower is remapped automatically. A compatible flower registers an existing boolean open property. When selected, the registry sets only that property according to suitable daylight and regional storm intensity. The block supplies its own open and closed models.

Custom blocks that already receive vanilla random ticks can call `ReactiveVegetationServices.processRandomTick(...)`. That path consults the chunk attachment only; it does not query weather per plant.

### Reserved future traits

`SNOW_REACTIVE` and `LEAF_LITTER_SOURCE` are registration metadata only in this first version. They reserve a stable compatibility vocabulary for environment-dependent snow persistence and seasonal ground litter without pretending those systems are already implemented.

## Configuration

Server settings are written to `wildernessodysseyapi/wildernessodysseyapi-server.toml`
under `[reactiveVegetation]`.

| Setting | Default | Meaning |
| --- | ---: | --- |
| `vegetationUpdatesEnabled` | `true` | Master switch for regional climate and registered plant reactions |
| `updatesPerChunk` | `4` | Maximum selected surface columns in one due chunk pass |
| `updateInterval` | `200` | Target ticks between passes of the same loaded chunk |
| `droughtSensitivity` | `1.0` | Multiplier for drying and accumulated drought stress |
| `rainRecoveryRate` | `0.06` | Moisture/drought recovery per due rainy update |
| `flowerWeatherClosing` | `true` | Close registered flowers in severe localized storms |
| `flowerNightClosing` | `true` | Close registered flowers without suitable daylight |

Changing the interval rebuilds only the in-memory due schedule. Persisted chunk climate remains intact.

## Diagnostics

Operator commands are under `/wilderness vegetation`:

- `/wilderness vegetation sample` reports regional moisture, recent rainfall, drought, storm intensity, season, mushroom opportunity, last climate update, last vegetation pass, registered plants processed, and average regional processing time.
- `/wilderness vegetation stats` reports loaded/scheduled chunks plus the most recent tick's chunk count, bounded probes, registered plants, visual state changes, smoothed chunk-pass time, and initial snapshot counts split between Data Engine and direct fallback transport.
- `/wilderness vegetation registry` reports registered blocks and trait counts.

`/wo dataengine stats` provides the matching `reactive_vegetation` packet
batch, entry, and estimated-byte totals. These counters establish which path
ran; representative multiplayer profiling is still required before claiming a
network or tick-time improvement.

Useful validation scenarios:

1. Set `updatesPerChunk=64` temporarily in a test world and use `/wilderness vegetation stats` in a dense forest. Work must remain capped by due chunks and configured probes rather than tree or grass count.
2. Force localized rain for several in-game days, sample the region, then clear the weather. Moisture/recent rain should rise, drought should fall, and grass tint should recover gradually.
3. Leave a chunk, let it unload, and note its saved state. The chunk must not advance while absent; after reload it resumes one scheduled step at a time without catch-up.
4. Save and restart after establishing drought or wetness. `/wilderness vegetation sample` should report the restored values once the chunk is loaded.
5. Register a test flower with an `OPEN` property and distinct blockstate models. Confirm night/storm closure and daylight reopening without any block entity.

## Extension API

Compatibility modules should register during common setup after their blocks exist.

### Property-backed flowers

```java
ReactivePlantRegistry.registerFlower(
        ExampleBlocks.MOON_BLOOM.get(),
        MoonBloomBlock.OPEN
);
```

The block's default state must contain the supplied `BooleanProperty`. The registration never replaces the block; it toggles that property with a client-only block update. Open and closed model variants remain owned by the registering mod.

### Moisture- or season-reactive model properties

```java
ReactivePlantRegistry.register(
        ExampleBlocks.PRAIRIE_GRASS.get(),
        ReactivePlantDefinition.of(
                Set.of(
                        ReactivePlantTrait.MOISTURE_REACTIVE,
                        ReactivePlantTrait.SEASON_REACTIVE
                ),
                context -> context.state().setValue(
                        PrairieGrassBlock.DRY,
                        context.climate().droughtLevel() >= 0.65
                )
        )
);
```

A behavior must return the same block with different existing properties. Cross-block replacement is rejected. Keep the callback local and bounded: do not scan neighbors, load chunks, or query weather.

### Metadata-only compatibility

```java
ReactivePlantRegistry.register(
        ExampleBlocks.SEASONAL_LEAVES.get(),
        ReactivePlantDefinition.observe(Set.of(
                ReactivePlantTrait.SEASON_REACTIVE,
                ReactivePlantTrait.LEAF_LITTER_SOURCE
        ))
);
```

This advertises future compatibility without changing block state today.

### Vanilla random-tick reuse

A custom plant that already opts into vanilla random ticks may delegate its selected tick:

```java
@Override
protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    ReactiveVegetationServices.processRandomTick(level, pos, state, random.nextLong());
}
```

The call reads only the already initialized loaded-chunk attachment. `ReactiveVegetationServices.climateAt(...)` and `mushroomOpportunity(...)` are also available to render or gameplay integrations that need read-only regional values.
