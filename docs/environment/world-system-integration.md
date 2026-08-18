# Shared world-system integration

## Purpose

The environment integration layer makes weather, wind, water, seasons, tides,
vegetation, wildlife, meteors, radiation, and Riftfall describe one world while
preserving exactly one authority for each kind of state. It does not introduce
a second weather simulation, a second water volume, or a replacement animal AI.

The central rule is:

```text
authoritative owners -> short-lived regional snapshot -> bounded consumers
successful hazards   -> typed disturbance fact     -> ecosystem/vegetation
regional snapshot    -> compact server summary     -> client ambience/debug
```

## Ownership boundaries

| State or action | Authoritative owner | Shared integration may do | Shared integration must not do |
|---|---|---|---|
| Atmosphere, wind, precipitation, forecast, seasons | `WeatherServices.query()` | Read samples and derive pressure/activity | Tick or rewrite atmospheric cells |
| Physical water, watershed, flooding, currents | `WaterServices.access()` and the existing water managers | Read hydrologic conditions and tide | Place/remove water as a substitute owner |
| Plant climate and plant mutation | Reactive Vegetation | Publish typed pressure and request owner-validated damage | Replace arbitrary foliage directly |
| Animal behavior and populations | Ecosystem services and vanilla mob AI | Supply habitat, hazard, migration, and activity conclusions | Move mobs or maintain a second population ledger |
| Meteor sites | `MeteorSavedData` through `MeteorSiteServices` | Query indexed immutable site summaries | Infer sites by scanning crying obsidian |
| Riftfall stage and exposure | `RiftfallSystem` | Include the stage in regional conclusions | Recreate the stage machine elsewhere |
| Client presentation | Existing renderers and soundscapes | Consume a server-authored summary | infer gameplay authority from particles or blocks |

## Phase 1: shared regional snapshot

`EnvironmentServices.query()` exposes one `RegionalEnvironmentSnapshot` for a
server position. `RegionalEnvironmentManager` gathers public, immutable values
from the existing owners and caches them by chunk for 20 ticks. The cache is
bounded to 4,096 entries per loaded dimension and never loads chunks.

The snapshot contains:

- localized weather, wind, forecast, and season;
- watershed conditions, local flow, and the moon-driven tide;
- reactive-vegetation climate and the strongest current plant disturbance;
- the nearest indexed meteor site and local radiation;
- the dimension's Riftfall stage; and
- derived habitat productivity, water availability, shelter pressure,
  migration pressure, wildlife/aquatic activity, plant stress, and overall
  hazard.

`EnvironmentInfluenceModel` is a pure model. Its outputs are normalized advice
for consumers; they cannot mutate an owning system.

## Phase 2: dimension participation profiles

`EnvironmentDimensionProfile` centralizes stable dimension policy and then
layers current configuration onto it:

| Dimension | Atmosphere/water/ecology/plants | Natural meteors | Radiation | Riftfall |
|---|---:|---:|---:|---:|
| Overworld | yes | yes | yes | no |
| The Echo | yes | no | yes | yes |
| Anomaly dimension | yes | no | yes | no |
| The Before | no | no | no | no |
| Other ordinary dimensions | yes by default | no | yes | no |

The Development Studio remains a normal Overworld consumer. The profile does
not replace the real Overworld, water APIs, or weather APIs with preview data.

## Phase 3: typed world disturbances

`WorldDisturbanceService` is the successful-event handoff. Lightning, severe
weather, wildfire, flood transitions, drought transitions, meteor impacts,
radiation-zone entry, and Riftfall publish a `WorldDisturbanceType` only after
the owning condition exists.

One publication can:

1. record bounded ecosystem memory through `EcosystemServices`;
2. request a coarse ecosystem-region refresh;
3. add a short-lived `PlantDisturbance` to the vegetation ledger; and
4. invalidate intersecting regional snapshots.

Intensity is server-configurable in the ecosystem config. A publication carries
facts, not commands: it cannot create a storm, alter water, or move animals.

## Phase 4: vegetation-owned physical results

`VegetationDisturbanceLedger` retains at most 256 short-lived requests per
dimension. Intensity falls with both distance and time. The existing staggered
vegetation scheduler samples the strongest request while it performs its normal
bounded surface probes, so hazards never trigger a forest-wide scan.

`ReactiveVegetationServices.applyDisturbanceAt()` validates whether the selected
block is a supported plant and whether block damage was authorized. Severe wind,
meteor pressure, and Riftfall can therefore affect plants without those systems
directly owning plant mutation. Riftfall still owns its non-plant terrain
corrosion. Crop damage remains protected by the existing Riftfall config.

## Phase 5: meteor lifecycle

Every completed meteor route now publishes through `MeteorSiteServices`:

- naturally scheduled falling meteors are tagged `NATURAL`;
- `/meteor summon` sites are tagged `COMMAND` after crater creation;
- Riftfall meteor surges request real falling meteors tagged `RIFTFALL`; and
- world-generated sites are tagged `WORLDGEN`.

The saved record includes a stable ID, center, crater radius, creation time,
intensity, and source. Older worlds containing only center/radius entries still
load with compatible defaults. An in-memory 256-block spatial index supports
bounded nearest/within queries without changing the saved-data authority.

Radiation, AI story context, environment summaries, impact-site music, and
ecosystem pressure all query the index. They no longer iterate every saved site
or scan nearby blocks to rediscover the crater.

## Phase 6: ecosystem and distant wildlife

Loaded animal decisions reuse the shared chunk snapshot. Habitat productivity
scales forage, drought and regional pressure can increase migration priority,
and shelter pressure participates in severe-condition decisions. High hazard
reduces optional routine activity while leaving vanilla goals in control when
the ecosystem goal is idle.

Distant wildlife consumes the same weather, water, habitat, activity, tide,
and hazard conclusions. Aquatic groups react to changing coastal tides through
a normalized activity signal; the tide does not create a second population or
water simulation.

## Phase 7: server-to-client summary and ambience

`EnvironmentSyncManager` sends one staggered, fixed-size
`EnvironmentSyncPayload` per player each second. The protocol version is bumped
because the payload is required by clients and servers using this build.

The payload includes bounded local wind, precipitation, drought, water,
habitat, shelter/migration pressure, wildlife/aquatic activity, plant stress,
hazard, tide, flooding/coastal flags, nearest meteor/radiation, and Riftfall
stage. It contains conclusions only, not simulation cells or mutable owner data.

Current consumers are:

- impact-site music, using the synchronized meteor location rather than a 3D
  block scan;
- river ambience, scaled by water availability and aquatic activity; and
- the F3 debug provider's `SHARED WORLD ENVIRONMENT` section.

The synchronized state is cleared on client logout and level unload so one
world cannot leak ambience into another.

## Performance and persistence guarantees

- No new per-block or per-animal tick loop is introduced.
- Regional queries are cached for 20 ticks and invalidated after real events.
- Environment sync is staggered and limited to once per player per second.
- Plant disturbances are bounded, expire, and are consumed by existing probes.
- Meteor lookups use a bounded spatial index with a maximum public query radius.
- Dimension and player state use weak or lifecycle-cleared transient maps.
- Only meteor-site records are newly persistent; existing world data remains
  readable and no migration destroys or replaces old records.

## Configuration

The main balancing points are:

- ecosystem disturbance intensities for lightning, severe weather, wildfire,
  flood, drought, meteor, radiation, and Riftfall;
- weather's existing severe foliage-damage permission;
- Riftfall's existing terrain/crop permissions; and
- Riftfall `realMeteorSurges` plus `meteorSurgeMeteorCount` (0 through 5).

Physical mutations remain opt-in where they were already opt-in. Raising a
disturbance intensity changes ecological pressure but does not bypass a block
damage permission.

## Verification

Automated coverage is centered on the stable seams:

- dimension participation profiles;
- cross-system influence conclusions;
- plant disturbance falloff and expiry;
- indexed meteor-site queries and duplicate publication; and
- migration selection from regional pressure.

For a manual client pass:

1. Start a development client with `./gradlew runClient`.
2. Enable the F3 debug HUD and verify `SHARED WORLD ENVIRONMENT` changes while
   moving between dry/wet terrain, storms, coastlines, and a meteor site.
3. Run `/meteor summon 6 48`, wait for the site to finish, and confirm radiation,
   story proximity, debug location, and impact music agree on the same center.
4. Observe profiled terrestrial and aquatic wildlife in calm, drought, flood,
   storm, and coastal-tide conditions. Vanilla panic/combat behavior must still
   interrupt ecosystem behavior.
5. Exercise a Riftfall-capable dimension through `METEOR_SURGE` and confirm real
   meteors appear when enabled, with the existing visual event as fallback when
   no meteor can be accepted.
6. Enter The Before and verify the integration reports an inert environment and
   does not start weather, ecology, plant, meteor, radiation, or Riftfall work.

Dedicated-server testing should additionally use `./gradlew runServer` with two
players in separate regions to confirm per-player summaries and independent
dimension state.
