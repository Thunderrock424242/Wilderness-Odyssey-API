# Beach and dynamic coastline system

The coastal system extends Wilderness Water without introducing another water
authority. It has two deliberately separate responsibilities:

1. data-driven beach biome placement, bounded surface bands, and sparse authored details;
2. client-only presentation of waves shoaling, breaking, washing up terrain,
   retreating, leaving foam and wetness, and producing sparse local effects.

No coastal renderer changes block or fluid state. No per-wave packet is sent.
The water attachment, ocean sea state, weather service, seasonal integration,
and existing coordinated water renderer remain the owners of their respective
state.

## Runtime data flow

```text
WeatherServices and regional weather synchronization
  -> OceanSeaStateField
  -> ClientOceanSeaState
  -> CoastalWaveModel

GeneratedWaterChunk attachment synchronization
  -> immutable ClientWaterChunkSnapshot
  -> ClientCoastalSegmentStore
  -> CoastalSegment shoreline, bathymetry, and terrain run-up samples
  -> TideSystem visual surface offset
  -> CoastalRunupRenderer through WaterRenderCoordinator
  -> CoastalBreakEffects for bounded local particles and positional audio
```

`ClientCoastalSegmentStore` scans only synchronized water snapshots and chunks
already loaded by the normal client view. It does not request chunk tickets or
walk into unloaded terrain. Each cached segment records a stable identifier,
landward shoreline normal, beach slope, average nearshore depth, underwater
slope, terrain-following run-up cells, and seaward depth samples.

`CoastalWaveModel` is a pure deterministic model. Stable segment identity,
synchronized game time, the synchronized regional sea state, shore profile,
bathymetry, beach slope, and onshore wind produce the same incoming, shoaling,
breaking, run-up, and retreat phase on every client. The model controls crest
position, breaker distance, height, foam, run-up distance, wetness, and spray.
Calm local weather retains a background ocean swell: ordinary sandy-shore
crests rise roughly one block, while strong storm crests reach several blocks.
Incoming height eases in, shoaling joins the breaking crest continuously, and
the crest collapses before run-up instead of disappearing at break onset.
The game tick is promoted to double before adding frame interpolation, so old
worlds retain tick and partial-tick motion instead of rounding to a frozen wave.
It does not own gameplay water volume. Per-shore presentation values are loaded
from `assets/wildernessodysseyapi/coastal_wave_profiles.json`. Resource packs
can tune height, frequency, breaker distance and strength, run-up, retreat,
foam, crash audio, turbulence, and wetness duration. A malformed or missing
resource falls back to the bounded built-in profiles and invalidates only the
loaded client coastline cache.

The stable segment identifier includes dimension identity and topology buckets.
It intentionally does not require the server world seed, which vanilla does not
expose to ordinary multiplayer clients. This keeps nearby clients aligned from
data they already share and avoids adding a seed-disclosure packet solely for a
cosmetic animation.

`CoastalRunupRenderer` is a detail subpass of `WaterRenderCoordinator`. It
appends nearshore crests, a thin terrain-aware wash sheet, foam tint, and a dark
wet-surface overlay to the coordinator's shared translucent batch. Each crest
uses three faces: a water-colored seaward slope, a foamy lip, and a landward
slope that stays visible when viewed from the beach. Height is not attenuated
again in the renderer. Its position and base height interpolate between cached
water cells; the feet stay within that sampled strip. All three faces count
against the existing quad budget, which is not increased. This avoids
a competing render event or a second water pipeline. `CoastalBreakEffects`
emits only the strongest nearby breaker under a hard cadence and particle
budget. Its `CoastalWaveBreakEvent` is a local immutable result for diagnostics
and future ambience consumers, not a network or gameplay event.

## Beach biome family

The six authored biome identifiers are:

- `wildernessodysseyapi:temperate_beach`
- `wildernessodysseyapi:dune_beach`
- `wildernessodysseyapi:rocky_coast`
- `wildernessodysseyapi:cold_beach`
- `wildernessodysseyapi:glacial_beach`
- `wildernessodysseyapi:tropical_beach`

Lithostitched partial biome replacements select the family from vanilla beach
and stony or snowy shore climate points. The polar region gets first choice for
`glacial_beach`; the remaining snowy coast becomes `cold_beach`. Temperate,
dune, and tropical beaches split the ordinary beach climate by temperature and
humidity. `rocky_coast` replaces stony shore climates.

All six identifiers are in `#minecraft:is_beach`, the mod's
`#wildernessodysseyapi:is_coastal` tag, and the project's overworld tags. They
therefore remain visible to existing beach-aware gameplay while retaining an
exact authored identity for their wave profile.

Vanilla surface rules check exact vanilla beach keys, so the configured
`coastal_terrain` feature owns the custom family's top surface. It operates
only during generation, only inside the feature's origin chunk, and only on
natural surface blocks. A bounded water-distance query and smooth seeded noise
select strandline, open beach, dune or meadow, rocky slope, cold gravel, or
glacial ice and snow bands. This affects newly generated chunks; it is not an
existing-world retrofit.

Tropical beaches use a wider ten-block open-sand band and a green back-beach,
not the temperate dune band. Low natural banks are graded toward a one-block
rise per four shoreline-distance blocks, with a configurable maximum cut
(four blocks by default). This never raises terrain, changes ocean water, cuts
high cliffs, or enters a cave. The whole cut and its foundation are checked
before writing; chunks with structure starts or references are conservatively
excluded. These changes also require new chunks.

After the bands are complete, the same feature evaluates sixteen deterministic
four-by-four anchors rather than simulating every beach block. Eligible anchors
can place terrain-aligned driftwood, calcite or dead-coral shell beds, rock
clusters, contained two-by-two tide pools, beach scrub or grass, cold ice
fragments, and rare nearshore sea stacks. All placements stay in the feature's
origin chunk and use vanilla blocks, so no new block registry or texture atlas
is required. Tide pools are persistent worldgen water; the moving surf itself
never places or removes fluid blocks.

The tropical back-beach can also receive one small palm-shaped tree per chunk,
using connected jungle logs and drooping jungle-leaf fronds. The full tree must
fit within the origin chunk and clear air before any block is placed. Ordinary
water-edge trees and plains-grass features are not injected into this biome,
and the coastal detail placer never uses dead bushes on tropical sand. The
biome supplies explicit lush grass/foliage colors and a turquoise water palette.

## Configuration

Common world-generation settings live under `coastalWorldgen`:

- the entire family and each individual biome replacement can be disabled;
- terrain bands can be disabled independently;
- maximum dune rise is bounded to four blocks.
- `maximumTropicalBankCutBlocks` controls tropical grading (0 to disable, at most 6).
- sparse details have a global density control and separate vegetation,
  driftwood, tide-pool, rock/outcrop, and ice-fragment switches.

Client presentation settings live with the existing water rendering config:

- coastal waves, run-up, foam, wetness, spray, audio, regional weather, and
  seasonal presentation influence have independent switches;
- topology refresh cadence and maximum visual run-up distance are bounded;
- foam, wetness, and sound have global multipliers;
- the existing water quality profile selects hard segment, quad, scan-stride,
  and spray budgets.

The seasonal integration remains indirect by design. Ecliptic Seasons feeds
the established weather and environment authority on the server; the client
coast consumes synchronized temperature, snowpack, freeze state, and the
existing glacial-season snapshot. Warm tropical surf becomes slightly clearer
and brighter, winter wash cools toward blue, and cold or glacial breaker events
gain bounded mist and foam. The coast never directly loads optional Ecliptic
classes or creates a second calendar. `glacial_beach` also maps to the existing
iceberg-coast environmental behavior for seasonal freezing checks, water tint,
blowing snow, and sparse ice ambience. It remains outside the stable five-biome
glacial exploration family and its serialized tag order.

Actual `CoastalWaveBreakEvent` selections choose one of four registered,
positional sound identities: soft wash, normal break, rocky impact, or storm
break. Their current sound definitions select among Minecraft splash samples;
entries in a `sounds.json` list are alternatives, not simultaneous layers.
`CoastalBreakAudioModel` gives calm surf an audible floor independent of spray,
and `CoastalBreakEffects` adds a quieter water-wash layer explicitly at the
same breaker. Crashes use the Ambient/Environment category, with default
profile selection radii of 34–44 blocks and fixed sound attenuation ranges
of 40–56 blocks. The existing client volume multiplier and resource-pack mute
still apply to both layers. The raised breaking phase, not a minimum spray
amount, triggers sound. Only the strongest nearby unplayed cycle wins, at most
once per 28–36 ticks, so long loaded coastlines do not produce an audio wall.
Resource packs can replace the stable crash identities with recorded surf;
the bundled sounds remain vanilla-sample approximations, not ocean recordings.

## Diagnostics and performance boundaries

The water rendering debug page reports cached and rendered coastal segments,
raw boundary candidates, coastal quad count, the nearest shore profile and
normal, beach and underwater slope, nearshore depth, live wave phase, breaker
and crest distances, run-up, foam and wetness, seasonal color and mist controls,
and the last emitted break event. While F3 is open on the Rendering page, the
coordinator also draws a hard-capped in-world overlay from the existing segment
cache:

- cyan: shoreline samples;
- yellow-to-red arrows: landward wave direction and live energy;
- orange: breaker zone;
- blue: sampled water depth;
- green: terrain and slope samples;
- magenta: maximum run-up;
- white: active water and foam footprint;
- navy: recently washed wetness footprint;
- red: last actual break event.

The hot paths have explicit bounds:

- loaded chunks and immutable snapshots only;
- radius and scan stride inherited from water rendering quality;
- movement and time gated topology refreshes;
- a fixed number of segments and points per segment;
- a fixed per-frame coastal quad budget;
- a fixed break cadence and direct-particle budget;
- stable deterministic wave evaluation instead of per-wave synchronization.

Distance LOD is independent of the shared wave clock. Outside the shoreline
cache radius only the normal ocean animation remains. Medium-distance cached
segments retain the moving breaker surface, near segments add terrain run-up
and wetness, and only very-near events spend direct splash-particle budget.
Positional crash audio keeps its larger profile radius and relies on natural
attenuation.

### Fallback surface handoff

Water mesh rebuilds that retain the same surface-ownership mask also retain the
pending upload generation and sections already acknowledged. Refreshing sea
state or snapshots must not invalidate a build that has already omitted the
baked water top. A completed section build receives an upload receipt even if
the renderer skipped some fluid callbacks for occluded cells; rejecting such a
receipt after suppressing other tops could leave rectangular ocean gaps.
Changed masks and builds begun before the ownership request still reject stale
receipts. Nothing in this handshake removes server water or edits terrain.

## Validation checklist

Automated source tests cover deterministic phases, calm swell, continuous
crest transitions, full-height three-face geometry and cached-water bounds,
spray-independent audio, user mute and gain limits, run-up and retreat,
bathymetry-sensitive breaker distance, weather scaling, profile differences,
defensive segment snapshots, terrain zones, biome resources, and config
defaults. Live testing should still include:

1. generate a fresh world and locate every authored beach biome;
2. inspect sand, gravel, dune, meadow, snow, and ice transitions at chunk edges;
3. compare calm weather with strong onshore and offshore storms;
4. compare low, medium, high, and cinematic water quality budgets;
5. confirm sheets conform to flat sand, stepped dunes, rock, snow, and ice;
6. watch unload, dimension-change, respawn, and reconnect cleanup;
7. compare two clients on a dedicated server at the same shore and game time;
8. profile topology refreshes and render quads while flying along a long coast;
9. test shader-pack and renderer compatibility through the existing coordinator.

Wave and audio tuning takes effect on already generated Wilderness ocean
coastlines after restarting with the new mod JAR; it does not require new
chunks. Stand on the sand at normal player height as well as viewing from
above, listen within 8–16 blocks with Ambient/Environment volume enabled, and
compare calm surf to storms. Terrain and palm changes still require new chunks.

## Scope boundaries

All requested coastal phases are represented without adding a second water,
weather, season, networking, or render authority. Decorative motifs and surf
layers deliberately reuse vanilla blocks and sound samples so the feature ships
without placeholder binary art; resource packs can replace the wave profile and
sound resources. There is no fake global bird loop or invisible beach ambience:
turtles and other authored biome spawns remain normal entities, while surf
originates from actual deterministic breakers.

The surf is cosmetic and cannot flood, damage, or push entities. A future storm
surge, tsunami, or gameplay-danger phase would require explicit server-owned
water state and synchronization rather than silently promoting this renderer.
Dynamic breakers also require an authoritative synchronized Wilderness ocean
snapshot. Legacy vanilla-water coastlines remain unchanged until handled by the
existing explicit water-conversion workflow; the client does not scan or
silently convert them. Worldgen additions affect only newly generated chunks.
