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

`CoastalRunupRenderer` is a terrain-detail subpass of `WaterRenderCoordinator`.
It appends only thin terrain-aware wash and wetness. The former three-face
crests and floating foam cards were removed after they appeared as detached
translucent ramps. Ocean crests now belong entirely to the native Gerstner
water mesh, so they share its optical material, depth and reflection path.
`DepthWaveResponse.shapeCrest` smoothly sharpens shallow ocean crests, with
matching shader height, analytic derivative, CPU immersion and server-profile
vertical velocity. Deep water and inland profiles retain their previous shape.
Dry-boundary continuity and horizontal fold limits still apply. This is a
single-valued surface approximation, not overturning fluid geometry. The
existing coastal clock still drives terrain wash and ambience; it does not
introduce a second ocean displacement timeline. `CoastalBreakEffects`
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
The sand band also covers supported tropical seabed within four water blocks
of the surface and within the existing twenty-block shoreline reach. It checks
the complete four-block natural foundation before resurfacing and leaves water
blocks, seafloor height, deep ocean, and other shore profiles intact.
The cut returns gradually to the original bank between fourteen and twenty
blocks from water so its inland cutoff cannot create a sudden four-block wall.

The feature now samples its available terrain once in a bounded 56 by 56 area,
with a twenty-block shore search. Height and biome queries are cached for the
invocation. Contacts use the nearest sampled open-water boundary instead of
eight sparse rays, and are fixed before the feature writes terrain or pools.
Two-by-two pools cannot seed a new coastline. Unknown chunks are skipped;
there is no height-based guessed shoreline or chunk-edge terrain fallback.
The placed feature has no random single-position biome gate: it runs once for
its biome generation stage and checks each origin-chunk column itself.

Temperate sand extends into gentle dunes and patchy coastal meadow. Dune
beaches use a broader ridge with continuous distance-to-shore taper and two
noise scales, keeping the added rise bounded by the existing configuration.
Rocky coasts retain their existing landform while gaining coherent stone,
andesite and gravel bands and small preflighted boulders. Cold shores emphasize
gray gravel, coarse sediment and patchy snow, with at most one ice fragment
formation per chunk. Their vanilla `freeze_top_layer` feature is omitted so it
does not replace that patchwork with a uniform generation-time snow blanket;
normal later weather remains authoritative. Glacial shores layer snow and
packed ice with occasional blue-ice accents. Full offshore icebergs remain
owned by `IcebergFormationFeature` in `iceberg_coast`; coastal details no longer
select stone sea stacks for glacial beaches. All earthwork tapers at actual
land-biome boundaries rather than at chunk coordinates.

After the bands, thirty-two irregular deterministic detail candidates can place
shore-aligned driftwood, calcite or dead-coral shell beds, compact boulders,
contained tide pools, grouped vegetation, small ice outcrops, and rare rocky
sea stacks. The hard limits are twelve successful small details, two fallen
logs, and three rock/ice formations per chunk. Driftwood tries both directions
along the shoreline and progressively shorter connected logs on supported,
level ground. Tide pools verify their floor and rim; sea stacks preflight every
column against its own ocean floor before any write. Structure starts or
references veto both surface work and details. All placements stay in the
origin chunk and use native blocks. Tide pools are persistent generation-time
water; moving surf never places or removes fluids.

Tropical palms have a separate budget of twenty suitable-root attempts and at
most two successes. Roots are sampled where a complete crown can fit, seven to
twenty blocks from water, with six-block root spacing and five-to-eight-block
trunk heights. Crowns have connected rising inner leaves and drooping frond
tips; leaning direction varies deterministically. A density-dependent grove
roll keeps some chunks open. The full tree must fit in the origin chunk, have
natural solid root support, and clear its complete crown before any write.
Ordinary water-edge trees and plains-grass features are not injected into this biome,
and the coastal detail placer never uses dead bushes on tropical sand. The
biome supplies explicit lush grass/foliage colors and a turquoise water palette.

## Configuration

Common world-generation settings live under `coastalWorldgen`:

- `enableBeachBiomeFamily` defaults to `true`: custom beaches are enabled for new configurations;
- saved explicit values remain in effect; existing installations with `false`
  must change that value to `true` and restart to enable future placement;
- biome registrations remain available for existing worlds;
- the entire family and each individual biome replacement can be disabled;
- terrain bands can be disabled independently;
- maximum dune rise is bounded to four blocks.
- `maximumTropicalBankCutBlocks` controls tropical grading (0 to disable, at most 6).
- sparse details have a global density control and separate vegetation,
  driftwood, tide-pool, rock/outcrop, and ice-fragment switches.
- `coastalDetailDensity` retains its default of `0.55` (range 0–1).
- `palmDensity`, `driftwoodDensity`, and `rockClusterDensity` are new multipliers,
  each defaulting to `1.0` (range 0–2); zero disables that content. The existing
  vegetation switch and global density also gate palms. Rock density controls
  boulders and rocky sea stacks; the existing rock switch also controls shell beds.

The development preset in [coastal-development.toml](coastal-development.toml)
explicitly enables all six beaches and the existing polar system. Merge its
sections into `config/wildernessodysseyapi-common.toml`, restart, and generate a
disposable world or explore new chunks. Do not replace the whole config file
with the excerpt. The family is enabled by default; saved configuration values remain in effect.
Glacial placement still requires `polarGlacialBiomes.enablePolarBiomeSystem`;
the remaining snowy beaches use the cold profile. Startup diagnostics now
verify enabled coastal identifiers alongside the polar family in the active
Overworld biome source, rather than only confirming listener registration.

Client presentation settings live with the existing water rendering config:

Surf discovers loaded ocean-to-land boundaries independently of beach biome
tags. Vanilla and modded forest, plains, rocky and other shores can participate;
biome climate and surface material still choose the presentation profile.
Cliffs and built obstacles stop run-up, while lakes and rivers remain outside
the ocean-column eligibility check. Disabling custom beaches does not disable surf.

Crest foam in the built-in water shader responds to steep compressed swell
even without storm wind, and to the raised native shallow-ocean crest where
shoreline horizontal taper reduces compression. It is shaded on the water
surface rather than floating translucent cards. Run-up whitening emphasizes
its front and varies across terrain. External shader packs still control their
own water material and displacement; the removed ramp geometry is not used
as a fallback. No persistent foam state or new packets are added.

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

## Decoration diagnosis and regression commands

The old missing-detail behavior had several cumulative causes:

1. The beach family previously defaulted to disabled, and saved configurations
   can still contain `false`. Registered biome resources alone do not insert a
   biome into a running world's source. Existing chunks are not decorated again
   when the setting changes.
2. A random `in_square` position, heightmap and `biome` filter gated a feature
   that already scans its whole chunk. Sampling an inland or ocean point could
   therefore reject a chunk containing a perfectly valid authored beach.
3. Sixteen four-by-four detail anchors shared one presence roll and one variant
   roll. A tropical palm needed the meadow band beyond ten blocks, a water
   contact within fourteen blocks, and a complete crown inside the origin chunk.
   That narrow intersection rejected most otherwise reasonable roots. The
   all-air preflight also rejected harmless low plants, and only one successful
   palm was allowed. The new placement retains complete-crown safety, samples
   fitting roots directly and allows a replaceable low plant only at the root.
4. Driftwood tried one tangent direction and one length, so chunk edges or a
   single unsuitable support discarded the whole opportunity. Retrying the
   other direction and shorter, level logs improves success without partial logs.

Run the pure profile/resource/surf regressions with:

```powershell
.\gradlew.bat :coastalTest '-PcoastalTestsOnly=true' '-PcodexBuildDir=.codex-build' --no-parallel
```

Run block placement, structure metadata, configuration and real Lithostitched
codec checks through the existing NeoForge JUnit launch with:

```powershell
.\gradlew.bat :test '-PcoastalTestsOnly=true' '-PcodexBuildDir=.codex-build' --no-parallel
```

The optional source filter only changes which tests Gradle compiles and selects.
It does not replace the Minecraft compiler, dependencies or mod loader. The
scoped NeoForge report is under `.codex-build/reports/tests/coastalIntegration/`;
the pure report is under `.codex-build/reports/tests/coastalTest/`. Ordinary
`test` without the property retains the full suite. Keep Gradle runs serial.

The controlled shore fixture uses 128 seeds and a flat, unobstructed, fully
tropical back-beach. Its placement counts are discoverability regressions, not
measurements of natural-biome frequency or proof of in-game appearance. The
six material-profile checks bound height queries to at most 3,136 and biome
queries to at most 576 per invocation; sea-stack floor checks have their own
five-column bound. No worldgen timing or render/GPU speedup is established by
these counts.

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
