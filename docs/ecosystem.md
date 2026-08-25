# Living ecosystem foundation

The ecosystem module adds opt-in, server-authoritative environmental decisions to profiled pathfinding mobs. It augments vanilla goals only while an ecosystem action is active; it does not remove or permanently replace vanilla goals.

Weather, water, vegetation, tide, meteor, radiation, and Riftfall conclusions
now meet at the read-only regional snapshot described in
[Shared world-system integration](environment/world-system-integration.md).
The ecosystem remains the behavior and population owner.

## Architecture

- `EnvironmentalContext` is an immutable snapshot for one decision pass. It carries localized weather, biome and time data, plus optional water, shelter, threat, herd, prey, food, and disturbance results.
- `AnimalNeedsState` is an entity attachment. Only thirst, hunger, rest, social motivation, and the predator hunting cooldown are saved. Paths, targets, threat memory, and debug timing are transient.
- `EnvironmentalBehaviorDecisionModel` chooses a supported broad state (`IDLE`, `FORAGE`, `TRAVEL`, `DRINK`, `REST`, `SLEEP`, `SEEK_SHELTER`, `FLEE`, or `MIGRATE`) from that snapshot. It is allocation-light, runs only after the animal's cooldown and server budget admit a decision, and never replaces vanilla AI while idle.
- `WildlifeSchedule` applies a stable UUID-derived offset to each animal's diurnal, nocturnal, crepuscular, or flexible schedule so a species does not switch behavior on one synchronized game tick.
- `behaviorTagAssignments` in the server config is the primary modpack-facing setup. A short label such as `herbivore`, `bird`, or `wolf` is expanded into a complete runtime `SpeciesBehaviorProfile`.
- Optional profiles loaded from `data/<namespace>/ecosystem/species/*.json` remain available as an advanced fallback when no config assignment matches an entity.
- When `autoDetectModdedAnimals` is enabled, compatible non-Minecraft animals with no config or JSON profile receive a conservative profile inferred from stable Minecraft base types and registered targeting goals.
- `EcosystemBehaviorGoal` is installed at priority 2 only on a server mob with a loaded profile. Vanilla float and panic goals can interrupt it, and the goal releases MOVE/LOOK control whenever it is idle.
- `WaterSourceLocator`, `ShelterLocator`, `ThreatAwarenessService`, and `FoodAvailabilityService` provide small read-only integration boundaries. Default implementations use bounded scans and short-lived per-level caches.
- `EcosystemUpdateBudget` limits expensive animal evaluations across the server each tick. Animals far from players use a slower interval.
- `AnimalGroupManager` caches transient same-type herd, flock, and pack membership. One leader runs broad environmental decisions; followers consume the published state and destination without rebuilding `EnvironmentalContext`.
- `GroupFollowerGoal` uses direct relative movement around stable loose offsets. It requests an individual navigation path only when a member is far behind or direct movement has made no progress.
- `EnvironmentalMemoryManager` is the shared world-memory API. Each dimension stores normalized chunk-sized cells in its own Minecraft `SavedData`; no block grid, chunk attachment, or second ecosystem manager is created.

## Distance-based simulation zones

`EcosystemSimulationManager` is the server-thread authority for ecosystem LOD. It classifies fixed 64-block cells from the nearest alive, non-spectating player in the same dimension. A cell has one level even when several players overlap it, so multiplayer coverage is a union rather than duplicated simulation work.

The manager deliberately tracks only cells containing a relevant player, a loaded profiled animal, or a persisted distant-wildlife group. It does not build a square or circle of empty cells around each player, which keeps large simulation and render distances from turning the coverage pass into an area-sized scan. Player coverage is rebuilt every `regionalUpdateInterval`, requested cells are processed through a `maxRegionUpdatesPerTick` queue, and callers may use `requestRegionalUpdate` after a teleport or another system changes a region materially.

The default bands are intentionally not four equal rings:

| Level | Nearest-player range | Server behavior |
|---|---:|---|
| `ACTIVE` | through 96 blocks | Real wildlife, normal ecosystem decision frequency, full vanilla AI. |
| `NEAR` | through 224 blocks | Real wildlife and normal behavior choices, with ecosystem environment evaluations slowed by `farAnimalUpdateMultiplier`. |
| `DISTANT` | through 512 blocks | Eligible wildlife is gradually moved into the population ledger; individual ecosystem AI is not evaluated. Groups keep coarse position, migration, and environmental state. |
| `DORMANT` | beyond 512 blocks or without a relevant player | Only known abstract groups remain. Optional daily population work is requested from the bounded group ledger and catches up analytically when admitted. |

Distances are measured to the closest point on a cell's horizontal footprint, so a cell touching a nearer band is conservatively promoted. Hand-edited radii are normalized into increasing bands at runtime. The 224-block `NEAR` default leaves a wider stability band than the conceptual 192-block example and avoids churn around the distant-wildlife transition while players fly quickly.

`DistantWildlifeSavedData` remains the only persistent population authority. Each group records species population, a bounded fractional population remainder, group identity, migration direction, food availability, water availability, food pressure, disturbance, weather impact, and the game time of the last population calculation. `PopulationEcologySimulationSystem` derives carrying pressure from immutable ecosystem/environment snapshots, computes through the existing Data Engine, and lets the ledger revalidate and commit on the server thread. Catch-up is one bounded formula over elapsed Minecraft days; three unloaded days do not become 72,000 simulated ticks. Weather, public `WaterServices`, persistent environmental memory, migrations, herds, seasons, wildfire, and the client distant-representation layer therefore meet at coarse snapshots without creating a second population count.

Transitions are deliberately gradual. `entityTransitionRate` bounds materialization in each dimension tick and absorption in each infrequent population scan, and materialization decrements population only after a loaded, habitat-valid entity was successfully added. Abstraction never removes named, tamed, vanilla-persistent, custom-persistent, mounted, ridden, leashed, combat-active, player-targeting, or externally `NoAI` wildlife. Mods and data packs can also add entity types to `#wildernessodysseyapi:ecosystem/never_abstract`. Babies, breeding animals, injured animals, scoreboard-tagged animals, and recently materialized animals receive additional transition vetoes.

Eligible `DISTANT` and `DORMANT` entities are temporarily marked `NoAI` while waiting for the absorption budget. The attachment stores both ownership of that suspension and the entity's earlier `NoAI` value, so entering `ACTIVE`/`NEAR`, disabling zones, config reload, or normal shutdown restores only state owned by this system. Disabling distant-wildlife transitions also prevents new zone-owned suspension.

The manager exposes `getSimulationLevel`, `isFullySimulated`, `getNearestPlayerDistance`, `requestRegionalUpdate`, `getRegionSnapshot`, and `requestMigration` for later systems. `/woecosystem status` reports known cells by level, fully simulated entity count, abstract population, processed and pending regional updates, the transition limit, and ecosystem update time. The dormant-cell metric counts known ecosystem cells, not the infinite empty world.

## Optimized herd, flock, and pack AI

Any profile with `herd.enabled=true` participates when `groupAIEnabled` and `herdBehaviorEnabled` are both enabled. Config archetypes such as `herbivore`, `bird`, `wolf`, `herd`, `flock`, and `pack` already produce social profiles unless combined with `solitary`.

Group creation is lazy. The first admitted ecosystem decision reuses the section-keyed nearby-entity cache to join an existing compatible group or form a same-entity-type local group. That membership is then addressed by UUID. The manager does not own a tick handler, scan all loaded entities, or persist group data. Only the leader performs a cooldown-gated validation/recruitment lookup; unload events remove members immediately and elect the healthiest loaded adult before falling back to deterministic UUID order.

The leader publishes one of `IDLE`, `ALERT`, `TRAVEL`, `FLEE`, `SEEK_WATER`, `SEEK_SHELTER`, `FEED`, or `REST`, plus an optional destination. Followers:

- wait for a small stable per-animal reaction delay;
- occupy UUID-derived golden-angle offsets inside `groupFormationRadius`;
- vary their catch-up distance and take short independent idle/grazing pauses outside emergencies;
- steer directly with move control while nearby;
- invoke their own navigation only after falling far behind or failing to make direct progress;
- retain all vanilla and mod-authored goals when group movement is inactive.

Threat reports wake cached members and publish one broad escape direction. Followers reuse that direction through leader-relative movement instead of each scanning for threats and calculating an independent escape destination. Tamed animals are deliberately excluded from wild herds and packs.

### Group integration API

Weather, wildfire, migration, disturbance, or other server systems can request broad behavior without owning navigation:

```java
GroupServices.groupFor(animal).ifPresent(group -> {
    group.requestState(GroupBehavior.SEEK_SHELTER);
    // A system that already owns an authoritative target may also provide it:
    group.requestDestination(shelterPosition);
});
```

`requestState(SEEK_WATER)` and `requestState(SEEK_SHELTER)` without a destination ask the leader's existing ecosystem services to find a valid water or shelter target. `requestDestination(...)` uses `TRAVEL` when no explicit state is pending. `group.getLeader()` returns the cached leader UUID; `GroupServices.manager().resolveLeader(group)` resolves the loaded pathfinding entity without a scan. Serious threat integrations may call `group.reportThreat(threatPosition, threatEntityId, escapeTarget)` when they already own an escape decision.

Calls must run on the logical server thread. Requests and destinations are transient by design; an entity or profile reload safely forms new local groups rather than writing fragile membership into animal NBT.

## Environmental memory

Environmental memory gives wildlife a small, persistent record of player and environmental activity. The first implementation records rate-limited player movement, block breaking/placement, combat, explosions, and successful Wilderness weather wildfire ignition. Future guns, machines, vehicles, group activity, and loud-event systems can publish through the same API without changing persistence:

```java
double disturbance = EnvironmentalMemoryManager.getDisturbance(level, position);
Optional<EnvironmentalMemory> memory = EnvironmentalMemoryManager.getMemory(level, position);
EnvironmentalMemoryManager.addDisturbance(
        level, position, amount, DisturbanceSource.LOUD_EVENT, sourceEntityId);
EnvironmentalMemoryManager.clearRegion(level, new ChunkPos(position));
```

Each memory cell keeps normalized general disturbance, player traffic, recent combat, recent fire activity, the last update time, source kind, exact source position, and optional source entity UUID. Values combine additively up to `maximumDisturbance`.

Decay does not run every tick. A read at game time `T` calculates the effective value from the stored value, `lastUpdatedGameTime`, and `disturbanceDecayPerDay`. Reads do not rewrite a surviving cell. Once every channel reaches `cleanupThreshold`, the cell is removed. Every ordinary API access also checks at most one rotating stored cell, so abandoned near-zero cells are eventually pruned without a full-map cleanup pass.

Movement sampling uses the existing per-player NeoForge tick event but performs only a UUID lookup and timestamp comparison on most ticks. It samples at most once every 40 ticks and records only after at least four blocks of movement. It never scans players, entities, loaded chunks, or blocks.

Natural and chunk-generation spawns for profiled wildlife use configurable response bands:

| Disturbance | Default response |
|---|---|
| `0.00-0.25` | Normal natural spawn probability. |
| `0.25-0.50` | Mild caution; default spawn multiplier `0.85`. |
| `0.50-0.75` | Reduced activity; default spawn multiplier `0.55`. |
| `0.75-1.00` | Strong avoidance; default spawn multiplier `0.25`. |

Spawn eggs, commands, breeding, spawners, and other non-natural sources are unchanged. The strong multiplier is constrained above zero, so memory never completely disables wildlife. Profiled wild animals already in a strongly disturbed cell select a bounded retreat target, and water, shelter, or herd destinations in strongly disturbed cells are skipped. Tamed animals keep their normal owner-directed behavior.

Water detection first calls `WaterServices.access()`, which is the stable public boundary for Wilderness-owned water. The normal water fluid tag is checked second for vanilla and compatible modded fluids. Ecosystem code does not read or mutate generated-water spans, canonical volume chunks, projection blocks, synchronization snapshots, or renderer state.

Weather behavior calls `WeatherServices.query()` for localized temperature, precipitation, thunder, and wind. It does not use the global vanilla rain flag as a substitute for localized weather.

## Pre-storm wildlife reactions

Wildlife can react before localized precipitation reaches its current region. The weather authority projects the existing persistent storm and front identities along their tracker-owned motion vectors, applies the configured movement speed and weakening-system dissipation, and reports the strongest system predicted to intersect the region. This is a forecast over authoritative weather state, not a second weather simulation.

Gameplay integrations may use the public query without depending on tracker internals:

```java
WeatherThreatForecast forecast = WeatherServices.query().getApproachingWeather(
        level, position, 7_200);
```

The forecast reports `NONE`, `LIGHT_RAIN`, `RAIN`, `THUNDERSTORM`, `SEVERE_STORM`, or `EXTREME_WEATHER`, plus projected intensity, distance to the system edge, ETA, confidence, source-system ID/type, and lifecycle stage. `LIGHT_RAIN` is intentionally ignored by the animal reaction policy. `ambientWildlifeActivityScale()` is an optional signal for future bird, insect, or soundscape integrations; light rain leaves that signal at full activity.

Forecast work is shared by atmospheric region for 100 ticks and is invalidated when tracked systems change. Animal decisions are cached for 200 ticks. A grouped herd or flock uses its elected leader as the decision owner: followers inherit the leader's response and shelter destination, and wait rather than issuing an independent weather query when the leader has not evaluated yet.

`StormSensitivityRegistry.register(...)` lets another mod assign an exact entity type a `StormSensitivity` containing detection distance, minimum intensity, shelter preference, and alertness. Without an exact registration, the ecosystem selects conservative generic, herd, bird, or aquatic defaults from the existing species profile and entity family. Birds favor canopy, ordinary land animals accept cover, and aquatic animals become alert without searching for land shelter.

Severe near-term forecasts reuse the existing shelter locator. Its search remains limited to loaded terrain, at most 640 candidate samples, and a short-lived section cache; canopy and solid-overhead preferences refine that bounded result without adding a large-radius block scan. If preferred cover is unavailable, the nearest safe cover is used, and if no cover is found the animal remains alert instead of pathing to an invalid destination.

With debug commands enabled, `/wilderness weather forecast` reports the incoming threat, intensity, distance, ETA, source, lifecycle stage, and optional ambient-wildlife scale. `/woecosystem inspect <entity>` reports that animal's cached forecast, leader/follower ownership, selected response, and resolved storm-sensitivity values without triggering a new forecast or shelter scan.

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

The entity only needs to be a pathfinding mob. The ecosystem discovers it when it joins a server level, builds the generated profile, adds the conditional leader and follower goals, and persists its needs attachment. To opt a new animal into optimized grouping, assign an archetype that enables grouping (for example `examplemod:deer=herbivore`, `examplemod:bison=herbivore,herd`, or `examplemod:dire_wolf=predator,pack`) or set `"herd": { "enabled": true }` in its advanced JSON profile. No custom entity base class or replacement of its registered goals is required.

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
5. A profile registered by a compatibility module, first by exact entity and then by entity-type tag.
6. Automatic third-party-animal inference.

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
| `diurnal` | Makes daytime the main activity window and night the primary sleep window. |
| `nocturnal` | Reverses the normal day/night rest schedule. |
| `crepuscular` | Favors dawn and dusk activity, with lower activity around midday and night. |
| `flexible` | Allows activity in every time period while needs and environmental signals choose the routine. |
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
  },
  "environment": {
    "active_time": "crepuscular",
    "preferred_temperature_min_celsius": -8.0,
    "preferred_temperature_max_celsius": 26.0,
    "hot_dry_drink_threshold_reduction": 0.16,
    "forage_hunger_threshold": 0.35,
    "rest_threshold": 0.62,
    "minimum_food_for_forage": 0.16,
    "local_travel_radius": 14,
    "migration_radius": 32,
    "schedule_jitter_ticks": 1100,
    "rest_duration_ticks": 120,
    "sleep_duration_ticks": 260,
    "supported_states": [
      "idle", "forage", "travel", "drink", "rest", "sleep",
      "seek_shelter", "flee", "migrate"
    ]
  }
}
```

JSON sections and fields retain safe defaults. JSON may still select `entities`, `entity_tags`, or both. Config assignments deliberately take precedence, so a simple server-config rule can replace an older JSON profile without deleting a data pack. JSON profiles take precedence over automatic modded-animal detection.

Compatibility modules can register the same immutable profile type during common setup. The generated-profile factory is the shortest route when its archetypes are sufficient:

```java
ResourceLocation deerId = ResourceLocation.fromNamespaceAndPath("examplemod", "deer");
SpeciesBehaviorProfile deerProfile = BehaviorTagProfileFactory.create(
        deerId,
        Set.of(AnimalBehaviorTag.HERBIVORE, AnimalBehaviorTag.CREPUSCULAR)
);
SpeciesBehaviorProfileManager.registerCompatibilityProfile(deerProfile);
```

For unusual species, construct `SpeciesBehaviorProfile` directly and choose only the environmental states the animal can actually perform. Registration requires at least one exact entity or entity-type-tag selector, rejects duplicate profile IDs, survives data-pack reloads, and remains below server config and data-pack profiles in precedence. Registering a profile does not grant new navigation capabilities; aquatic, flying, burrowing, or custom-moving animals retain their own navigation implementation.

All sections and fields have safe defaults. Radius fields are still capped by the server config. Invalid profile files are logged and skipped without preventing other profiles from loading. Running `/reload` republishes profiles and installs controllers on already-loaded matching pathfinding mobs.

## Distant wildlife representation

Distant wildlife is a lightweight visual form of the same ecosystem population, not a second spawn system. A represented animal has one owner at a time: either a real `PathfinderMob` in a loaded level or a count inside dimension-scoped `DistantWildlifeSavedData`. `DistantWildlifeManager` commits conversions between those forms, while `EcosystemSimulationManager` supplies the nearest-player simulation zone and the shared `EcosystemEntitySafety` policy vetoes named, tamed, persistent, tagged, mounted, leashed, combat-active, or externally frozen animals.

One `DistantWildlifeGroup` stores a species ID, population estimate, fractional analytical remainder, anchor, normalized movement direction, speed, deterministic seed, and reference game time. It also stores coarse food, water, disturbance, weather, and population-update state. Movement is calculated analytically from the anchor and elapsed time, and dormant population pressure is advanced lazily, so the server never creates a pathfinder, collision body, goal selector, or tick object for every represented animal.

The default LOD contract is:

- Inside `realEntityDistance` (96 blocks), wildlife should be real entities.
- The next `transitionBuffer` (32 blocks) is a cross-fade and early-materialization band.
- Beyond the transition band and inside `distantWildlifeDistance` (512 blocks), the client renders distant groups.
- The final transition buffer fades the silhouettes out before the maximum distance. Beyond it the population remains abstract and is not sent to that player.

These values are server config, not renderer constants. The `distantWildlife` section contains `enableDistantWildlife`, `realEntityDistance`, `distantWildlifeDistance`, `maxDistantGroups`, `maxRepresentedAnimals`, `updateInterval`, and `transitionBuffer`. Its nested `populationEcology` category contains `enabled`, `updateIntervalTicks` (24,000 by default), and `regionalCarryingCapacity` (96 by default). The simulation-zone `entityTransitionRate` separately bounds materialization per dimension tick and absorption per infrequent population scan.

### Rendering and synchronization

The server sends a bounded full snapshot at `updateInterval` (100 ticks by default), filtered to groups within each player's maximum representation distance. While a nearby group is actively becoming real, a dirty flag permits a short burst of corrective group snapshots at no more than four per second so the client does not keep drawing population that has already materialized. There is still one packet entry per group, never one packet per visual animal, and there is no client-to-server distant-wildlife packet. The client extrapolates the anchor from server time and uses the group's seed to keep each simplified animal in a stable formation between updates.

`DistantWildlifeRenderer` uses the normal NeoForge level-render stage, the existing terrain depth buffer, one frustum test per group, and a single batched low-poly silhouette render type. It does not instantiate entity renderers, raycast per animal, request chunk loads, or access Distant Horizons internals. At the far LOD it omits legs, wings, and tails. A caught renderer/linkage failure trips a client-session safety fuse: only this optional renderer is disabled, the server population ledger and ordinary entity rendering continue.

### Transition safety

Real wildlife is considered for abstraction only on an infrequent scan, only when its species already has an ecosystem profile, and only after it is sufficiently far away and outside every player's view cone for a sustained interval. Babies, breeding, injured, command-tagged, protected, or recently materialized animals remain real. The manager does not force-load terrain while evaluating groups.

Approaching players trigger bounded materialization at the outside of the transition buffer. The manager tries loaded, habitat-valid spawn positions outside player view first, applies vanilla spawn-rule and obstruction checks, and decrements the abstract count only after `addFreshEntity` succeeds. If placement fails, the abstract population remains unchanged. When players leave, eligible real entities are not removed immediately; the unobserved timer, zone distance, safety policy, and transition budget must all pass before the population is absorbed.

### Performance and diagnostics

Server cost is proportional to loaded wildlife during the configured absorption scan plus one O(1) update per abstract group. Between scans, a 30-animal group has no 30-entity AI, pathfinding, collision, or per-animal server/network tick cost. Client cost is proportional to the number of visible simplified silhouettes, with one frustum decision per group and no terrain raycast loop. Caps on groups and represented population provide hard memory, snapshot, and draw-work bounds.

The ecosystem is the first production consumer of the shared performance infrastructure. Player cell changes still refresh zone ownership and restore manager-owned `NoAI` state immediately on the server thread. Periodic full loaded-entity scans, abstract-group advancement, materialization, and ordinary snapshot publication run through the bounded `ecosystem_runtime` Data Engine registration. Tick Engine pressure expands or suspends that optional cadence, and recovery performs one current pass instead of replaying missed intervals. Login, respawn, dimension-change, and config refreshes mark one coalescible Data Engine dirty key; the handler sends only players whose existing distant-wildlife snapshot was invalidated.

Disabling the Data Engine retains the same optional work through a direct live-`hasTime()` fallback. Neither path loads chunks, changes tickets, replaces entity base ticks, or moves population/world authority out of `EcosystemSimulationManager`, `DistantWildlifeManager`, and `DistantWildlifeSavedData`. The first integration also retains `DistantWildlifeSyncPayload`; migrating that established codec into generic Data Engine deltas requires separate packet-size and compatibility evidence.

With debug commands enabled, `/woecosystem distant` reports persisted groups, represented count (the approximate number of real entities avoided), recent absorption/materialization totals, LOD per group, distances, update interval, packet count, and last-pass time. The F3 **Rendering** page reports visible/frustum-culled group counts, represented animals, LOD counts, configured distances, snapshot frequency, and renderer safety-fuse status.

## Configuration and diagnostics

Server settings are generated in `config/wildernessodysseyapi/wildernessodysseyapi-server.toml`
under `[ecosystem]`. Environmental-memory values are grouped under
`[ecosystem.environmentalMemory]`, while distant-wildlife values remain under
`[ecosystem.distantWildlife]`. They control behavior-tag assignments, automatic
modded-animal detection, the master switch, simulation zones, evaluation
frequency, radius caps, thirst multiplier, shelter/herd/predator switches,
per-tick expensive-evaluation budget, per-entity behavior multipliers, and the
environmental-memory decay/source/cleanup/wildlife response values.

Zone controls are `simulationZonesEnabled`, `activeRadius`, `nearRadius`, `distantRadius`, `regionalUpdateInterval`, `maxRegionUpdatesPerTick`, and `entityTransitionRate`. `farAnimalUpdateMultiplier` controls only the reduced ecosystem-decision frequency of real `NEAR` wildlife. With zones disabled, loaded animals remain real and use the legacy nearest-player slowdown.

Group-specific controls are `groupAIEnabled`, `maxGroupSize`, `leaderDecisionInterval`, `memberValidationInterval`, `followDistance`, and `groupFormationRadius`. Disabling `groupAIEnabled` leaves the earlier independent ecosystem behavior path available; it does not remove vanilla or mod-authored AI.

Per-entity overrides use `namespace:id=multiplier`. `1.0` keeps the profile rate, values above or below one scale need accumulation, and `0.0` disables the controller for that entity type.

Diagnostics are disabled by default. After enabling `debugCommandsEnabled`, operators may use:

- `/woecosystem status` for config-assignment count, automatic-detection state, generated/auto-detected/JSON profile counts, scheduling settings, current budget use, cached group/member totals, and leader decisions per minute.
- `/woecosystem profiles` for config rules, generated runtime profiles, inferred third-party profiles, optional JSON profiles, and enabled behavior families.
- `/woecosystem inspect <entity>` for needs, current broad state, state reason, next decision time, destination, weather response, navigation/vanilla targets, detected water, shelter and threat, group ID/role/leader/member count/state/destination/decision rate, simulation LOD, and level budget use.
- `/woecosystem memory` for the executing player's cell disturbance, activity channels, last update, elapsed decay, source kind/position/entity, and dimension-local stored-cell count.
- `/woecosystem memory <x> <y> <z>` for the same information at a loaded position.
- `/woecosystem memory clear` to remove the executing player's current chunk cell.
- `/woecosystem distant` for distant groups, represented population/remainder/reference time, actual entities avoided, LOD state, transition distances, update frequency, and recent conversion/packet work.
- `/wo simulation population` for population-region requests, async batches, stale validation, additions/removals, in-flight work, cadence, and regional carrying capacity.
- `/wo simulation map` for a bounded, on-demand 17 by 17 regional view of abstract groups, population, simulation LOD, food, water, food pressure, disturbance, weather impact, and migration direction. The same read-only map is available from the Development Studio Ecosystem page.

When `debugCommandsEnabled` is true, the existing categorized F3 **World** page also shows the current chunk's server-owned environmental-memory snapshot. Synchronization is limited to one small current-cell packet per player per second and is completely absent while diagnostics are disabled.

## Current limitations

- Drinking uses the vanilla standing pose and look control; no client animation packet is sent yet.
- Food availability is a cached forage estimate that slows ambient hunger growth. A later grazing goal can consume or regrow tagged forage under explicit gamerules.
- Shelter discovery recognizes standable positions with overhead collision. It does not yet understand claimed structures, doors, or species-specific nests.
- Herds now have transient same-entity-type identity and elected loaded leaders. Cross-species groups, persistent migration routes, home ranges, breeding pressure, and genetics remain future layers.
- Individual animal threat targets remain transient. Regional environmental disturbance is a separate persistent signal and survives server restart through per-dimension `SavedData`.
- Predator hunting safeguards still inspect nearby loaded prey; they do not yet consume the abstract regional ecology counts or simulate a predator/prey food web.
- Visual behavior still needs development-client observation around irregular shorelines, dense canopies, fences, and modded navigation implementations.
- Automatic predator detection recognizes the standard `NearestAttackableTargetGoal`. A mod that hides predation in a custom goal requires an explicit `predator` assignment; a non-predator using that goal should be assigned `animal` or `disabled` explicitly.
- Fire activity currently records successful Wilderness weather wildfire ignition, not every vanilla fire-spread block. Guns, vehicles, machine operation, large-player-group weighting, and other loud-event publishers are intentionally left for later integrations through `DisturbanceSource` and `EnvironmentalMemoryManager`.
- Abstract population uses bounded regional carrying pressure and pressure-driven growth or decline per species group rather than breeding pairs, genetics, predation networks, or a global carrying-capacity solver. The stored fields and snapshot API are intended integration points for those later models.
- Materialization requires an already-loaded, obstruction-free, habitat-valid spawn position. A failed placement leaves population abstract and retries on a later bounded pass; the zone manager never force-loads a chunk to create wildlife.

## Manual simulation-zone test matrix

1. In single player, enable debug commands, run `/woecosystem status`, and cross each configured radius while observing one profiled wild group. Confirm `ACTIVE` decisions, slower `NEAR` evaluations, gradual abstraction in `DISTANT`, and no mass spawn on return.
2. Join with two players hundreds of blocks apart. Confirm cells near either player are promoted by the nearer player while the status totals and saved group population are not duplicated where coverage overlaps.
3. Teleport, fly quickly across several cells, change dimensions, and use a large render distance. Confirm the regional queue drains at its configured bound, old cells become dormant, and newly active wildlife materializes at no more than the transition budget.
4. Stop the dedicated server with real wildlife waiting in a distant transition and with abstract groups in unloaded chunks. Restart, revisit both areas, and confirm zone-owned `NoAI` state is restored safely and abstract population catches up once from elapsed game time.
5. Name, tame, leash, ride, damage, breed, command-tag, or data-pack-tag representative animals and move all players away. Confirm every protected individual remains a real entity. Also test an entity that another mod marks persistent or keeps `NoAI`.
6. Temporarily disable `simulationZonesEnabled` and `enableDistantWildlife` independently. Confirm zone-owned AI suspension is released and no new abstraction occurs. Re-enable each setting and verify gradual transitions resume.
7. With at least one abstract group, temporarily lower `populationEcology.updateIntervalTicks` to 1,200 and run `/wo simulation population`. Confirm requested, submitted, and applied counters advance while in-flight work returns to zero.
8. Compare `/woecosystem distant` before and after several ecology intervals in a productive region and in a high-disturbance or overcrowded region. Confirm population changes are gradual, each group stays between 1 and 64, and the dimension total never exceeds `maxRepresentedAnimals`.
9. Materialize or absorb a group while an ecology batch is pending. Confirm the owner reports a stale group rather than overwriting the newer real/abstract transition, and that a later bounded pass retries normally.
10. Save and restart after a fractional update, then advance several intervals. Confirm the population reference and fractional remainder persist, catch-up happens once, and two overlapping players do not duplicate the regional update.
11. Run `/wo simulation map` and cycle every layer. Confirm the center marker follows the requesting player, LOD bands match configured distances, each abstract group appears in exactly one cell, cell totals match `/woecosystem distant`, and hover/click details show the same population and pressure values.
12. Open the same map from Development Studio's Ecosystem page, move across a cell boundary, and use Refresh. Confirm the map recenters only on request, does not keep sending packets while left open, and does not create chunk tickets or materialize wildlife.
