# Hybrid anomaly rifts

The existing `RIFT_CORE` block has an additional `ANOMALY_RING` mode. Its server
block entity owns gameplay and saved state. The supplied Blockbench shell,
vortex, particles and local screen effects are client presentation. Explorable
rocks are ordinary Minecraft blocks placed from vanilla structure templates.

## Model and animation

The supplied geometry and selected `model.animation.json` are preserved in:

- `assets/wildernessodysseyapi/geo/anomaly_rift_shell.geo.json`
- `assets/wildernessodysseyapi/animations/anomaly_rift_shell.animation.json`
- `assets/wildernessodysseyapi/textures/block/anomaly_rift_shell.png`
- `assets/wildernessodysseyapi/textures/block/anomaly_rift_shell_glow.png`
- Corresponding texture animation `.mcmeta` files.

The existing GeckoLib dependency renders the shell at scale 0.8. The model's
center at 48 pixels becomes a world center 2.5 blocks above its anchor. Facing
is horizontal. `idle` loops normally; `critical` plays in the CRITICAL state.
The original editable Blockbench project is in `docs/temporalrift/assets/`.

Every shell face is checked in the unanimated aperture coordinate frame. Faces
that cross the central 3-by-3 block square are clipped, including emissive
passes, so an animated rock shard cannot cover the opening. This is a visual
guard; shell geometry has no collision. The small invisible anchor also has no
collision. The procedural black/cyan/violet vortex remains inside the aperture
and has independently moving layers, restrained critical red and brief rim arcs.

## Real-block composition

Eight authored blueprints under `src/main/structure_blueprints/rift/` compile
through the existing StructureGen task to compressed Minecraft NBT. Packaged
IDs are:

- `wildernessodysseyapi:rift_rock_small_01` and `_02`
- `wildernessodysseyapi:rift_rock_medium_01` and `_02`
- `wildernessodysseyapi:rift_platform_01` and `_02`
- `wildernessodysseyapi:rift_anomaly_island_small_01` and `_02`

The island prefix avoids a collision with the anomaly biome's separate island
templates. Generated files live under the selected build directory and are
packaged in `data/wildernessodysseyapi/structure/`; generated NBT is not checked
in. Edit the blueprint source or override the same template IDs in a data pack.
Do not edit the generator-owned build output.

The planner derives its seed from world seed and anchor. It places candidates
in sectors around the ring, with four rotations, restrained angular/radial
variation and stepped elevation. Full rotated bounding volumes must fit the
configured diameter, avoid the reserved ring volume and satisfy minimum spacing.
The requested rock count is an upper bound, not a guarantee of eight pieces.

Before the first rock is placed, every selected footprint must be loaded, within
build height/world border and completely clear of occupied blocks and block
entities. Missing, oversized and air-only templates decline explicitly. Placement
ignores template air/entities and uses the existing block-entity NBT sanitizer.
Unexpected synchronous placement failure restores the preflight volume. Rocks
use normal Minecraft collision and remain after the ring closes; player edits
are preserved. No hybrid placement operation loads chunks to inspect terrain.

The current eight variants use deepslate, cobbled deepslate, blackstone and sparse
crying obsidian, with flat tops and tapered undersides. They are functional
starter terrain assets that can receive a later art pass. Other registered block
materials are supported by data-pack replacements.

## Server authority and lifecycle

Persisted states are DORMANT, BUILDUP, ACTIVE, CRITICAL, DECAY and STABILIZED.
New cores start ACTIVE. An external gameplay system or the debug command can
set a state; this implementation does not invent story progression or an
automatic stabilization sequence.

The trigger is a 3-by-3 block aperture, 0.9 blocks deep, centered at anchor +
`(0.5, 2.5, 0.5)`. Player feet are swept against an entity-expanded trigger each
tick so walking, jumping and Elytra crossings between ticks are detected.
Teleports or stale movement histories are not swept across the world.

BUILDUP, ACTIVE, CRITICAL and DECAY reject survival/adventure entry. Entry posts
a cancellable `RiftInteractionEvent`, applies a local impact pulse, capped magic
damage, replacement outward velocity and optional two-second Slowness. Default
damage is 4 health points plus 2 per block/tick of speed, capped at 8 and further
capped against current health to leave at least 1 health point before external
damage modifiers. Other mods can modify damage after this calculation. Entry
has a 30-tick cooldown. Creative and spectator players bypass these mechanics.
DORMANT/STABILIZED emit the entry hook without default rejection or travel.

Player/item force runs every 5 ticks within 10 blocks, with smooth falloff and
state scaling. Default player acceleration is 0.018 per application and pulses
with the same anchor phase as the visuals. Item movement has a small inward and
upward influence; normal item gravity remains active. Each item spatial query
aborts after 32 candidates, and an entity receives at most one rift force per
tick across overlapping cores. The existing pull-effect toggle also applies.

`RiftProximityEvent` provides a warning/integration hook at most once every
10 seconds per tracked nearby player/core. It makes no external AI request.
Future Aether, weather, quests, research and stabilization code can subscribe on
the server game thread; integrations retain responsibility for their own async
and privacy boundaries.

Transient player histories belong to the loaded block entity, are capped at 256
entries and clear on core removal/chunk unload. Nearby presentation summaries
are clientbound every 10 ticks, with immediate impact updates. Client summaries
are limited to 32 and expire after 30 ticks or world/core removal.

New scheduled Overworld rifts use this mode by default. The existing temporal
schedule/duration still owns opening/closure. A persisted saved-data discriminator
separates legacy cleanup from hybrid cleanup. Unloaded managed hybrid cores
check their persisted deadline and active saved anchor when they load again.
Legacy tags without the new mode/discriminator keep their established paired
travel and closing behavior, including its historical chunk access. The Before
and unrelated anomaly gateway destinations are not replaced. Setting
`hybridScheduledRifts=false` restores legacy creation for scheduled/open commands;
existing hybrid debug cores retain their explicitly selected mode.

## Client effects and accessibility

Smoothly interpolated distance/state intensity controls sparse inward dust,
sparks, motes, occasional debris and a quiet local portal ambience placeholder.
Fog composes over the incoming biome fog in air. The optional independent post
chain runs after the level and ordinary biome effects, with pulse lens warping,
chromatic separation, slight grading, a critical vignette and a small spatial
echo. The echo samples the current scene, not retained historical frames. It
does not produce constant blur.
The short personal entry pulse remains visible after a fast player has left the
field or an impact packet arrives late, and fades without extending ambience.

The pass yields to active/unknown external shader-pack ownership, vanilla global
post effects, menus, underwater/lava fog and unsupported graphics backends.
It preserves incoming framebuffer, viewport, depth and blend state. Resource
reload/world entry clear stale resources; the first nearby-field client tick
constructs the chain outside the per-frame pass. Disabling FX/post processing or
world unload releases it. Failure disables the optional pass until reload while geometry,
fog and particles remain available.

Client settings are in `[anomaly_rift]` of `wildernessodysseyapi-client.toml`:
`localFx`, `postProcessing`, `cameraMotion`, `fxStrength`, `particleDensity`.
Camera roll/FOV motion defaults **off**; FX strength defaults to 0.5. Minecraft's
screen-effect slider and particle settings are respected. Local FX disablement
leaves the ring/core visible and suppresses optional core pulses/arcs. Reduced
mode lowers local effects and disables camera motion/brief rim flashes.

Server settings are in `[anomaly_rift]` of the existing server specification:
`hybridScheduledRifts`, `formationDiameter` (15–30, default 24), `rockCount`
(1–12, default 8), `rockSpacing` (0–4, default 1), `fieldRadius` (10–48,
default 30), `playerForce`, `itemForce`, `baseDamage`, `velocityDamage`,
`maximumDamage`, `entryStatus`. Server gameplay values are authoritative.

## Development commands

Operator commands require permission level 2. Coordinates identify the invisible
anchor, not the visual center. Stand in loaded clear air several blocks above
terrain, with a clear 24-block area for starter rocks. Spawning faces the player's
horizontal direction. A refused composition reports its reason; the safely
placed ring remains available for inspection or later composition placement.

```text
/wildernessrift hybrid spawn ~ ~8 ~
/wildernessrift hybrid ring ~ ~8 ~
/wildernessrift hybrid composition <x> <y> <z>
/wildernessrift hybrid state dormant <x> <y> <z>
/wildernessrift hybrid state buildup <x> <y> <z>
/wildernessrift hybrid state active <x> <y> <z>
/wildernessrift hybrid state critical <x> <y> <z>
/wildernessrift hybrid state decay <x> <y> <z>
/wildernessrift hybrid state stabilized <x> <y> <z>
/wildernessrift hybrid inspect <x> <y> <z>
/wildernessrift hybrid remove <x> <y> <z>
/riftfx debug true
/riftfx status
/riftfx reduced true
/riftfx enabled false
```

Client debug bounds show a cyan trigger box and a violet bounding cube for the
field radius (actual distance falloff is spherical). Status shows distance,
scaled intensity, selected state and post availability. The existing
`/wildernessrift debug_open_near` uses the configured scheduled mode. Removing a
managed core also closes its scheduler record; placed terrain remains.

## Verification and manual acceptance

Run one Gradle/Minecraft process at a time:

```powershell
.\gradlew.bat compileJava '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat riftTest '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat runGameTestServer '-PcodexBuildDir=.codex-build' -PriftGameTests --no-parallel
```

`riftTest` checks falloff/damage/state policy, fast/rotated sweeps, deterministic
spacing/rotation bounds, aperture clipping and source assets. Focused GameTests
check real template collision, unobstructed center, preserving mined/edited
terrain, refusing an occupied final footprint before any writes, legacy/hybrid
NBT round trips, capped impact, cooldown and stabilized rejection bypass. The
impact test ticks a native Survival ServerPlayer past spawn immunity, using a
test connection stub, and verifies high-speed entry reduces 3 health points to
exactly 1. These server tests do not prove real packet delivery or multiplayer
movement.

Validation on 2026-10-11 completed successfully: `compileJava`, all 13 focused
`riftTest` cases, all four required rift GameTests, and root `:build`. The last
client presentation adjustment was compiled, unit tested and packaged after the
GameTest run; it did not change server behavior. The packaged JAR contains the
geometry, animations, both animated textures and metadata, all three shader
resources and all eight generated rock NBT templates. Root `build` currently
assembles the project; it does not run the full repository test suite. That
suite and a graphical client acceptance run were not performed for this change.

Client acceptance still needs: view both supplied animations and emissive frames;
check north/east/south/west alignment and animated aperture clipping; approach at
30/20/10/5 blocks; walk/jump/fly through in Survival; test reduced/off settings,
resource reload, resize, unload/rejoin, anomaly biome layering and shader-pack
fallback. Multiplayer acceptance should repeat entry/cooldown/forces with two
real clients and check for jitter or conflicting mod movement effects.

Performance is bounded per core, not measured in the target modpack. The shell
has both normal/emissive passes and clips intersecting animated faces. One local
post chain uses one temporary scene target and a copy pass. Nearby item queries
and particles are limited; player lists still receive a cheap per-loaded-core
distance/sweep check each tick. Many manually spawned loaded cores multiply
that cost. Profile representative rifts before increasing budgets.

Custom hum/pulse sounds and final authored NBT terrain art remain asset work.
Destination gameplay, automatic stabilization/story progression and external
service integrations remain future hook consumers.

## Changed-file map

Java paths below are relative to
`src/main/java/com/thunder/wildernessodysseyapi/`.

| Responsibility | Files |
| --- | --- |
| Existing lifecycle | `temporalrift/TemporalRiftManager.java`, `temporalrift/TemporalRiftSavedData.java` |
| Existing block/entity | `temporalrift/block/RiftCoreBlock.java`, `temporalrift/blockentity/RiftCoreBlockEntity.java` |
| Existing renderer/commands | `temporalrift/client/RiftCoreBlockEntityRenderer.java`, `temporalrift/command/TemporalRiftCommand.java` |
| Shared registration additions | `config/WildernessConfigSpecs.java`, `network/ModPayloads.java`, repository `build.gradle` |
| Gameplay/state policies | `temporalrift/hybrid/RiftState.java`, `RiftConfig.java`, `RiftFieldMath.java`, `RiftTrigger.java`, `RiftServerController.java`, `RiftSites.java` |
| Sync/integration hooks | `temporalrift/hybrid/RiftFieldPayload.java`, `RiftInteractionEvent.java`, `RiftProximityEvent.java` |
| Composition | `temporalrift/hybrid/RiftCompositionPlan.java`, `worldgen/structure/rift/RiftRockPlacement.java` |
| Renderer/FX | `temporalrift/hybrid/RiftApertureClip.java`, `temporalrift/client/hybrid/RiftShellRenderer.java`, `RiftVortexRenderer.java`, `RiftClientEffects.java`, `RiftClientResources.java`, `RiftPostProcessing.java` |
| Operator tools | `temporalrift/command/HybridRiftCommands.java` |
| Loaded-world tests | `temporalrift/hybrid/RiftHybridGameTests.java` |

Other added files:

- Five focused test classes under `src/test/java/com/thunder/wildernessodysseyapi/temporalrift/hybrid/`:
  `RiftFieldMathTest`, `RiftTriggerTest`, `RiftCompositionPlanTest`, `RiftApertureClipTest`, `RiftAssetsTest`.
- Eight rock blueprints and `rift_test_clear.json` under `src/main/structure_blueprints/rift/`.
- The geometry, animation and four animated base/emissive texture files listed above.
- `assets/wildernessodysseyapi/shaders/post/rift_local.json` and
  `shaders/program/rift_local.json`, `shaders/program/rift_local.fsh` under `src/main/resources/`.
- `docs/temporalrift/hybrid-rift-plan.md`, this guide and the editable `.bbmodel`.

Concurrent anomaly biome and performance changes in this checkout are outside
this implementation's ownership. The shared registration/build additions retain
those changes. No generated build output is intended for commit.
