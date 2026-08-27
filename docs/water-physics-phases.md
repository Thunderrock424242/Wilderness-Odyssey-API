# Water Physics and Rendering Phases

This note records the owner boundaries and validation expectations for the
finite-water, SPH, wave, entity, optical, and environmental layers. It is not a
second implementation specification: the named runtime classes remain the
source of truth.

## Ownership

- `CanonicalWater` and `WaterVolumeChunk` own conserved fixed-point water.
- `WildernessFluidRegistry` advances only disturbed canonical cells through a
  bounded active queue.
- `SPHSimulationManager` owns short-range mobile pours and splashes. SPH does
  not replace generated oceans, rivers, or lakes.
- `GeneratedWaterChunk` owns compact generated-body metadata.
- `WildernessWaterAuthority` composes generated metadata, sparse overrides,
  waves, tide, watershed flow, and local disturbance for read-only consumers.
- `OceanSeaState`, weather services, `TideSystem`, and watershed services keep
  their existing state ownership. `WaterEnvironmentState` is only a bounded,
  immutable composition of their outputs.
- The logical server owns movement. Client boat pitch, roll, bobbing, wakes,
  surface meshes, and shaders are presentation only.

## Phase 1: Conserved Finite Water and Tides

Canonical flow plans gravity first. Lateral targets are evaluated from one
immutable source/neighbor snapshot so direction iteration cannot change the
requested distribution. Runtime commits a destination first and subtracts only
the amount actually accepted. Rejected volume therefore remains at the source.

Wilderness water never uses vanilla infinite-source conversion. Tidal height
uses the lunar spring/neap envelope. Tidal current needs a sampled coastline
normal: flood moves toward land, ebb moves offshore, and missing coast data
produces no invented global-axis current.

## Phase 2: SPH Calibration and Meshes

The particle mass is calibrated against the actual three-dimensional bucket
spawn distribution and smoothing kernel. Pressure uses a bounded Tait equation
of state. The small floor-contact assist is allowed only for compressed,
slow-moving particles and follows horizontal pressure acceleration; a radial
body-center direction is only the symmetric fallback.

SPH mesh normals come from the outward density gradient sampled at each emitted
vertex. Degenerate gradients fall back to a valid face/up normal. Mesh rebuilds
remain revision-driven rather than rebuilding unchanged fields every frame.

## Phase 3: Shared Wave Profiles and Classification

Gerstner profiles provide full horizontal and vertical displacement,
finite-depth dispersion, analytic tangents/normals, orbital velocity, slope,
and crest compression. A combined horizontal-steepness budget prevents surface
folding under extreme spectrum energy.

Generated body metadata wins classification. Compatibility water uses a
bounded local biome/shape probe with no flood fill, whole-world scan, or forced
chunk load. Ocean, coast, river, lake, and pond have separate CPU profiles.
The active block-format GPU mesh intentionally retains three packed body blend
channels for compatibility: ocean/coast share the open-water channel and
lake/pond share the enclosed-water channel. Shore and depth cues provide the
visual distinction without changing the vertex format.

## Phase 4: Entity and Boat Response

Rigid craft sample yaw-relative bow-port, bow-starboard, stern-port, and
stern-starboard points plus a motion-biased center. The server aggregates these
samples into displaced volume, current, and surface-normal inputs for the
existing hydrodynamic force solver. It applies buoyancy, drag, planing,
slamming, and bounded angular response without a competing water state.

The client samples the same corner layout for pitch and roll. `BoatTiltStore`
integrates pitch, roll, and heave with a spring-damper response and never moves
the authoritative entity position.

## Phase 5: Optical Surface

The core surface shader uses captured scene depth for optical travel distance,
Beer-Lambert absorption, Schlick Fresnel with a water-air base reflectance,
analytic/micro normals for directional specular, bounded refraction, and
energy-gated crest foam. A merely tilted calm surface is not enough to create
broad white foam. Shore breakers, current shear, and bounded impulse wakes are
separate cues.

External shader packs retain their own visual ownership and use the safe
fallback path.

## Phase 6: Environment Coupling

Wind direction and energy come from synchronized regional sea/weather state.
Ocean and coast use the open-water spectrum. Lakes use a depth/volume/shoreline
fetch proxy in authority and a depth-only proxy in the packed client mesh.
Ponds remain sheltered and receive only subtle rain-driven chop. Rivers align
their authored wave spread to canonical current.

Rain adds two bounded high-frequency normal layers at higher quality settings;
it does not create an SPH body per raindrop. Turbidity composes body type,
rain, sea energy, and current while the existing watershed and optical systems
retain sediment/clarity ownership.

## Performance Invariants

- No whole-world or whole-body flood fills.
- No forced neighboring chunk loads for classification, tide, or fetch.
- No per-block ocean tick grid.
- No per-raindrop SPH simulations.
- Canonical flow remains queue- and config-budgeted.
- SPH bodies, particles, substeps, meshes, snapshots, and persistence are
  bounded by existing caps.
- Rendering reads immutable chunk snapshots and revision-gated meshes. Dirty
  chunk construction/upload has both a per-frame count and soft time budget;
  SPH extraction uses a rotating per-frame budget so several bodies cannot
  bunch all marching-cubes work into one frame.
- Client visual code never writes server-authoritative water or movement.

## Validation Matrix

Automated checks should cover:

1. Exact vertical capacity, symmetric lateral flow, rejected destinations, and
   hundreds of conserved planning steps.
2. Spring/neap lunar phases plus coastline-relative flood and ebb.
3. Bucket density range, positive-pressure fraction, bounded Tait pressure,
   and ground-assist gating.
4. Smooth outward density normals and non-degenerate fallback normals.
5. Gerstner dispersion, period, velocity derivative, unit normal, fold budget,
   classification, and shader/source contracts.
6. Yaw axes, footprint aggregation, explicit enclosed-water weather energy,
   coast/lake current rules, hydrology weighting, and environment fetch/tide.

Manual client/server checks should use a disposable test world and include:

- bucket transfer into exact partial capacities and four-way level spreading;
- a vertical pour transitioning into SPH and settling without lost water;
- calm, windy, rainy, and storm conditions on ocean, coast, river, lake, and
  pond surfaces;
- a boat crossing oblique crests at several yaw angles, including multiplayer
  observation for correction or drift;
- shore flood/ebb direction through a lunar cycle;
- shallow/deep refraction, underwater transitions, Fresnel at grazing angles,
  crest foam, rain ripples, and external shader fallback;
- reload, chunk unload/reload, world save/reopen, LAN, and dedicated-server
  startup to confirm lifecycle and physical-side separation.
