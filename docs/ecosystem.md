# Living ecosystem foundation

The ecosystem module adds opt-in, server-authoritative environmental decisions to profiled pathfinding mobs. It augments vanilla goals only while an ecosystem action is active; it does not remove or permanently replace vanilla goals.

## Architecture

- `EnvironmentalContext` is an immutable snapshot for one decision pass. It carries localized weather, biome and time data, plus optional water, shelter, threat, herd, prey, food, and disturbance results.
- `AnimalNeedsState` is an entity attachment. Only thirst, hunger, rest, social motivation, and the predator hunting cooldown are saved. Paths, targets, threat memory, and debug timing are transient.
- `SpeciesBehaviorProfile` is loaded from `data/<namespace>/ecosystem/species/*.json`. Profiles select mobs by entity ID or entity-type tag, so no entity-type switch is required.
- `EcosystemBehaviorGoal` is installed at priority 2 only on a server mob with a loaded profile. Vanilla float and panic goals can interrupt it, and the goal releases MOVE/LOOK control whenever it is idle.
- `WaterSourceLocator`, `ShelterLocator`, `ThreatAwarenessService`, and `FoodAvailabilityService` provide small read-only integration boundaries. Default implementations use bounded scans and short-lived per-level caches.
- `EcosystemUpdateBudget` limits expensive animal evaluations across the server each tick. Animals far from players use a slower interval.

Water detection first calls `WaterServices.access()`, which is the stable public boundary for Wilderness-owned water. The normal water fluid tag is checked second for vanilla and compatible modded fluids. Ecosystem code does not read or mutate generated-water spans, canonical volume chunks, projection blocks, synchronization snapshots, or renderer state.

Weather behavior calls `WeatherServices.query()` for localized temperature, precipitation, thunder, and wind. It does not use the global vanilla rain flag as a substitute for localized weather.

## Built-in profiles

The first profiles cover cows, sheep, pigs, chickens, and wolves.

- Cows, sheep, pigs, and chickens accumulate needs, drink from a safe shoreline position, seek cover during configured weather, regroup with their species, remember threats, and share flight warnings.
- Wild wolves use the same drinking, shelter, and social framework. A wolf hunts only when its hunger threshold and cooldown pass, it has no existing vanilla target, and the local adult prey population meets the profile safeguard. Tamed wolves do not start ecosystem hunts.
- The built-in predator and wolf-prey entity tags are intentionally replaceable or extendable by data packs.

## Adding or changing a species

Create a JSON file such as `data/examplemod/ecosystem/species/deer.json`:

```json
{
  "entities": ["examplemod:deer"],
  "entity_tags": [],
  "needs": {
    "thirst_per_minute": 0.02,
    "hunger_per_minute": 0.01,
    "rest_per_minute": 0.006,
    "hot_temperature_celsius": 25.0,
    "heat_thirst_multiplier": 1.8,
    "activity_thirst_multiplier": 1.3,
    "nocturnal": false
  },
  "drinking": {
    "enabled": true,
    "thirst_threshold": 0.6,
    "search_radius": 24,
    "duration_ticks": 60,
    "move_speed": 1.0,
    "thirst_restored": 0.9,
    "can_swim": false,
    "maximum_safe_depth": 1.0
  },
  "shelter": {
    "enabled": true,
    "search_radius": 20,
    "precipitation_threshold": 0.5,
    "thunder_threshold": 0.35,
    "wind_threshold": 0.7,
    "minimum_release_delay_ticks": 80,
    "maximum_release_delay_ticks": 260,
    "move_speed": 1.0
  },
  "herd": {
    "enabled": true,
    "search_radius": 20,
    "preferred_distance": 8.0,
    "motivation_threshold": 0.5,
    "move_speed": 0.9
  },
  "prey": {
    "enabled": true,
    "threat_radius": 20,
    "threat_memory_ticks": 280,
    "propagation_radius": 14,
    "flee_speed": 1.4,
    "threat_tags": ["wildernessodysseyapi:ecosystem/predators"]
  }
}
```

Use either `entities`, `entity_tags`, or both. An entity-tag selector is useful for allowing several modded animals to share one profile. If multiple tag profiles match, the lexicographically first profile resource ID wins; an explicit entity selector always wins over tag selectors.

All sections and fields have safe defaults. Radius fields are still capped by the server config. Invalid profile files are logged and skipped without preventing other profiles from loading. Running `/reload` republishes profiles and installs controllers on already-loaded matching pathfinding mobs.

For a predator, add a `predator` section:

```json
{
  "enabled": true,
  "hunt_radius": 24,
  "hunger_threshold": 0.8,
  "hunt_cooldown_ticks": 12000,
  "minimum_nearby_prey": 4,
  "attack_interval_ticks": 24,
  "move_speed": 1.18,
  "wild_only": true,
  "prey_tags": ["examplemod:ecosystem/deer_prey"]
}
```

The prey tag is an entity-type tag. Population safeguards count living adults in the bounded local query, then select the nearest adult only if the minimum remains.

## Configuration and diagnostics

Server settings are generated in `config/wildernessodysseyapi/wildernessodysseyapi-ecosystem-server.toml`. They control the master switch, evaluation frequency, far-animal slowdown, radius cap, thirst multiplier, shelter/herd/predator switches, per-tick expensive-evaluation budget, and per-entity behavior multipliers.

Per-entity overrides use `namespace:id=multiplier`. `1.0` keeps the profile rate, values above or below one scale need accumulation, and `0.0` disables the controller for that entity type.

Diagnostics are disabled by default. After enabling `debugCommandsEnabled`, operators may use:

- `/woecosystem status` for profile count, scheduling settings, and current budget use.
- `/woecosystem profiles` for loaded selectors and enabled behavior families.
- `/woecosystem inspect <entity>` for needs, current behavior, navigation/vanilla targets, detected water, shelter and threat, evaluation timing, and level budget use.

## Current limitations

- Drinking uses the vanilla standing pose and look control; no client animation packet is sent yet.
- Food availability is a cached forage estimate that slows ambient hunger growth. A later grazing goal can consume or regrow tagged forage under explicit gamerules.
- Shelter discovery recognizes standable positions with overhead collision. It does not yet understand claimed structures, doors, or species-specific nests.
- Herds are same-entity-type local groups; persistent herd identity, leaders, migration routes, home ranges, breeding pressure, and genetics are future layers.
- Threat memory is deliberately transient. It survives loss of sight for the configured period but not a server restart.
- Predator population safeguards are local, not a global ecology census. Long-term population accounting should use a separate chunk-level sampling system rather than entity NBT.
- Visual behavior still needs development-client observation around irregular shorelines, dense canopies, fences, and modded navigation implementations.
