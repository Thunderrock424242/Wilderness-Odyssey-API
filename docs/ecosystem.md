# Living ecosystem foundation

The ecosystem module adds opt-in, server-authoritative environmental decisions to profiled pathfinding mobs. It augments vanilla goals only while an ecosystem action is active; it does not remove or permanently replace vanilla goals.

## Architecture

- `EnvironmentalContext` is an immutable snapshot for one decision pass. It carries localized weather, biome and time data, plus optional water, shelter, threat, herd, prey, food, and disturbance results.
- `AnimalNeedsState` is an entity attachment. Only thirst, hunger, rest, social motivation, and the predator hunting cooldown are saved. Paths, targets, threat memory, and debug timing are transient.
- `behaviorTagAssignments` in the server config is the primary modpack-facing setup. A short label such as `herbivore`, `bird`, or `wolf` is expanded into a complete runtime `SpeciesBehaviorProfile`.
- Optional profiles loaded from `data/<namespace>/ecosystem/species/*.json` remain available as an advanced fallback when no config assignment matches an entity.
- When `autoDetectModdedAnimals` is enabled, compatible non-Minecraft animals with no config or JSON profile receive a conservative profile inferred from stable Minecraft base types and registered targeting goals.
- `EcosystemBehaviorGoal` is installed at priority 2 only on a server mob with a loaded profile. Vanilla float and panic goals can interrupt it, and the goal releases MOVE/LOOK control whenever it is idle.
- `WaterSourceLocator`, `ShelterLocator`, `ThreatAwarenessService`, and `FoodAvailabilityService` provide small read-only integration boundaries. Default implementations use bounded scans and short-lived per-level caches.
- `EcosystemUpdateBudget` limits expensive animal evaluations across the server each tick. Animals far from players use a slower interval.

Water detection first calls `WaterServices.access()`, which is the stable public boundary for Wilderness-owned water. The normal water fluid tag is checked second for vanilla and compatible modded fluids. Ecosystem code does not read or mutate generated-water spans, canonical volume chunks, projection blocks, synchronization snapshots, or renderer state.

Weather behavior calls `WeatherServices.query()` for localized temperature, precipitation, thunder, and wind. It does not use the global vanilla rain flag as a substitute for localized weather.

## Built-in assignments

The default server config contains only these readable assignments:

```toml
behaviorTagAssignments = [
    "minecraft:cow=herbivore",
    "minecraft:sheep=herbivore",
    "minecraft:pig=omnivore",
    "minecraft:chicken=bird",
    "minecraft:wolf=wolf"
]
```

- Cows, sheep, pigs, and chickens accumulate needs, drink from a safe shoreline position, seek cover during configured weather, regroup with their species, remember threats, and share flight warnings.
- Wild wolves use the same drinking, shelter, and social framework. A wolf hunts only when its hunger threshold and cooldown pass, it has no existing vanilla target, and the local adult prey population meets the profile safeguard. Tamed wolves do not start ecosystem hunts.
- The built-in predator, generic-prey, and wolf-prey entity tags are intentionally replaceable or extendable by data packs.

## Adding or changing a species

Add an exact entity assignment to the generated ecosystem server config:

```toml
behaviorTagAssignments = [
    "examplemod:deer=herbivore",
    "examplemod:blue_jay=bird",
    "examplemod:owl=bird,solitary,nocturnal",
    "examplemod:otter=omnivore,swimmer",
    "examplemod:lynx=predator,solitary"
]
```

The entity only needs to be a pathfinding mob. The ecosystem discovers it when it joins a server level, builds the generated profile, adds the conditional goal, and persists its needs attachment.

### Automatic modded-animal compatibility

`autoDetectModdedAnimals` is enabled by default. It runs only after config and JSON matching fail, and only for registry IDs outside the `minecraft` namespace:

- A third-party `Animal` subclass receives the neutral `animal` archetype: drinking, shelter, and prey awareness without assuming it should herd or hunt.
- Third-party subclasses of vanilla cows, sheep, rabbits, horses, pigs, chickens, and wolves retain the corresponding herbivore, omnivore, bird, or wolf family archetype.
- A compatible `FlyingAnimal` receives `bird` behavior.
- A `WaterAnimal` receives `aquatic` behavior. Drinking and land shelter are disabled because the creature already lives in water.
- An `AbstractSchoolingFish` also receives `flock` behavior.
- A compatible animal with a `NearestAttackableTargetGoal` receives `predator,solitary` modifiers. This reuses the mob's registered AI as evidence instead of guessing from its registry name.
- Mobs implementing Minecraft's hostile `Enemy` contract, and unknown `PathfinderMob` subclasses such as machines, NPCs, or bosses, are not automatically enrolled.

The detector never replaces an explicit pack choice. Its precedence is:

1. Exact config assignment, including `disabled`.
2. First matching config `#entity_type_tag` assignment.
3. Exact JSON profile.
4. Matching JSON entity-type-tag profile.
5. Automatic third-party-animal inference.

If a mod uses an unusual entity hierarchy or custom targeting system, assign it explicitly. To exclude a false positive without disabling automatic support for the rest of that mod, use:

```toml
behaviorTagAssignments = [
    "examplemod:decorative_bird=disabled",
    "examplemod:unusual_grazer=herbivore",
    "examplemod:custom_hunter=predator,solitary"
]
```

An assignment can also target every member of a Minecraft entity-type tag. Prefix the selector with `#`:

```toml
behaviorTagAssignments = [
    "#c:animals/herbivores=herbivore",
    "#examplemod:animals/forest_birds=bird",
    "#examplemod:animals/pack_hunters=predator,pack"
]
```

Exact entity IDs always override `#entity_type_tag` selectors. If several tag selectors match, the first matching rule in config order is used.

### Available behavior labels

| Label | Generated behavior |
|---|---|
| `animal` | Conservative generic animal behavior: drinking, weather shelter, and prey awareness without assumed grouping. |
| `herbivore` | Drinking, forage-aware hunger, weather shelter, herd motivation, prey awareness, threat memory, and herd warning. |
| `omnivore` | Pig-like needs with drinking, shelter, social grouping, forage awareness, and prey response. |
| `bird` | Smaller/faster drinking and shelter timing, flock behavior, and stronger prey flight. It does not add artificial flight navigation. |
| `wolf` | Wolf-like needs, pack behavior, swimming, nocturnal rest timing, and population-protected wild hunting. |
| `aquatic` | Water-native needs and prey awareness. It disables drinking and land-shelter searches and permits aquatic navigation. |
| `herd` | Enables ordinary same-species grouping on a generic or combined archetype. |
| `flock` | Enables tighter bird-style same-species grouping. |
| `pack` | Enables wider pack-style same-species grouping. |
| `prey` | Enables predator/hostile detection, remembered flight, and warning propagation. |
| `predator` | Enables hunger/cooldown/population-gated hunting using the generic ecosystem prey entity tag. |
| `swimmer` | Allows shallow-water approaches for drinking when a dry shoreline is unavailable. |
| `nocturnal` | Reverses the normal day/night rest schedule. |
| `shelter` | Enables weather shelter for an otherwise generic combination. |
| `solitary` | Disables herd/flock/pack behavior even when an archetype would normally enable it. |
| `disabled` | Explicitly excludes a matching entity from config, JSON, and automatic ecosystem behavior. It cannot be combined with other labels. |

Friendly plurals such as `herbivores`, `birds`, `wolves`, `flocks`, and `predators` are also accepted. The common spellings `herbivor` and `herbivors` are accepted as aliases.

Archetype labels are complete defaults, while modifier labels can be combined. For example, `bird,solitary,nocturnal` produces an owl-like schedule without flock regrouping.

Generic `predator` assignments use `wildernessodysseyapi:ecosystem/prey`; `wolf` uses `wildernessodysseyapi:ecosystem/wolf_prey`. Extend those entity-type tags in a data pack to control eligible prey without editing Java or a species profile.

### Optional advanced JSON override

If an archetype is not precise enough, remove that entity's config assignment and create a JSON profile such as `data/examplemod/ecosystem/species/deer.json`:

```json
{
  "entities": ["examplemod:deer"],
  "needs": {
    "thirst_per_minute": 0.02
  },
  "drinking": {
    "thirst_threshold": 0.57
  }
}
```

JSON sections and fields retain safe defaults. JSON may still select `entities`, `entity_tags`, or both. Config assignments deliberately take precedence, so a simple server-config rule can replace an older JSON profile without deleting a data pack. JSON profiles take precedence over automatic modded-animal detection.

All sections and fields have safe defaults. Radius fields are still capped by the server config. Invalid profile files are logged and skipped without preventing other profiles from loading. Running `/reload` republishes profiles and installs controllers on already-loaded matching pathfinding mobs.

## Configuration and diagnostics

Server settings are generated in `config/wildernessodysseyapi/wildernessodysseyapi-ecosystem-server.toml`. They control behavior-tag assignments, automatic modded-animal detection, the master switch, evaluation frequency, far-animal slowdown, radius cap, thirst multiplier, shelter/herd/predator switches, per-tick expensive-evaluation budget, and per-entity behavior multipliers.

Per-entity overrides use `namespace:id=multiplier`. `1.0` keeps the profile rate, values above or below one scale need accumulation, and `0.0` disables the controller for that entity type.

Diagnostics are disabled by default. After enabling `debugCommandsEnabled`, operators may use:

- `/woecosystem status` for config-assignment count, automatic-detection state, generated/auto-detected/JSON profile counts, scheduling settings, and current budget use.
- `/woecosystem profiles` for config rules, generated runtime profiles, inferred third-party profiles, optional JSON profiles, and enabled behavior families.
- `/woecosystem inspect <entity>` for needs, current behavior, navigation/vanilla targets, detected water, shelter and threat, evaluation timing, and level budget use.

## Current limitations

- Drinking uses the vanilla standing pose and look control; no client animation packet is sent yet.
- Food availability is a cached forage estimate that slows ambient hunger growth. A later grazing goal can consume or regrow tagged forage under explicit gamerules.
- Shelter discovery recognizes standable positions with overhead collision. It does not yet understand claimed structures, doors, or species-specific nests.
- Herds are same-entity-type local groups; persistent herd identity, leaders, migration routes, home ranges, breeding pressure, and genetics are future layers.
- Threat memory is deliberately transient. It survives loss of sight for the configured period but not a server restart.
- Predator population safeguards are local, not a global ecology census. Long-term population accounting should use a separate chunk-level sampling system rather than entity NBT.
- Visual behavior still needs development-client observation around irregular shorelines, dense canopies, fences, and modded navigation implementations.
- Automatic predator detection recognizes the standard `NearestAttackableTargetGoal`. A mod that hides predation in a custom goal requires an explicit `predator` assignment; a non-predator using that goal should be assigned `animal` or `disabled` explicitly.
