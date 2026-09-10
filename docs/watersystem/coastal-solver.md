# Conservative coastal current model

`ShorelineWaterManager` continues to own bounded, loaded-terrain shoreline
regions. `ShallowWaterGrid` is a derived current model inside those regions,
not an additional canonical reservoir. Its represented cubic-block volume
must **not** be added to watershed storage, generated ocean water, finite
canonical cells, or SPH ownership. It never places water blocks or terrain,
never adds a new rendering layer, and does not own the tide or wave spectrum.

## Transport and wet/dry behaviour

Each interior face has a single signed discharge. The exact same transferred
amount leaves its donor cell and enters its receiver. All outgoing faces from
one cell share an availability limiter, so several neighbours cannot each
spend the same water. Computation uses double precision internally. There is
no minimum-water refill and no upper elevation clipping; only a roundoff-sized
negative remainder is clamped at zero after limiting.

Momentum follows a local-inertial shallow-water approximation: pressure from
the free-surface gradient accelerates face discharge, time-scaled bottom drag
damps it, and a bounded velocity keeps coupling suitable for gameplay.
Reconstruction uses the higher adjoining bed as the interface sill. A still
lake over a stepped bed therefore remains still. A dry cell can receive water
only when its bed is overtopped. Unknown/blocked cells differ from known,
wettable but currently dry cells.

This is not a full shock-capturing shallow-water solver: nonlinear momentum
advection, resolved turbulence, hydraulic-jump calibration, wave breaking and
SPH spray remain outside this model. The canonical wave/SPH owners continue
to handle their existing responsibilities. The face velocity bound dissipates
momentum, not represented water.

## Boundaries, terrain and accounting

Outer faces may be individually opened or closed. The manager opens a face
only when its outside neighbour is loaded, Wilderness-owned water and
classified as ocean/coast by the existing classifier. Land, inland freshwater
and unknown borders are not an infinite tide reservoir. Known low dry terrain
inside the grid can wet from adjacent represented water without itself gaining
an open ocean boundary.

The boundary level reuses the current tide at the existing shared visual scale
and the existing Gerstner ocean spectrum. This remains a coarse region-centre
boundary sample. Adjacent cached regions are independent derived models;
their overlapping samples do not exchange or claim physical water ownership.

The balance is:

`represented volume = bathymetry exchange + ocean inflow - ocean outflow + residual`

First admission samples baseline depth and counts it as bathymetry exchange.
Refreshing an admitted bed preserves represented water depth. Closing a cell
after it becomes unknown or out of range removes its derived volume through
the same explicit exchange. `resetMotion` also records its datum resampling
exchange. None of these operations is an atmospheric, regional, or canonical
water transfer.

`ShallowWaterGrid.balance()` and `ShorelineWaterManager.balanceAt(...)` expose
represented volume, cumulative ocean inflow/outflow, signed bathymetry
exchange, floating-point residual, elapsed simulated time, and deferred time.
Residual is a numerical diagnostic, not an automatic top-up or deletion.

## Timing, lifecycle and performance

The solver recomputes a conservative two-dimensional CFL bound each substep.
A call processes at most 32 substeps and retains the unprocessed elapsed time.
A zero-time call can drain that queue. Repeated manager evaluation in the same
world tick does not advance a region twice. The manager passes all elapsed
time since that region's prior scheduled update instead of silently dropping
time above a quarter-second or half-second threshold.

Catch-up holds the supplied current ocean boundary sample constant; it does
not reconstruct historical weather/wave forcing. Sustained overload can leave
a measurable time backlog. These regions are ephemeral derived caches and are
discarded on normal expiry or dimension unload, not persistent catchment
simulation. No canonical water is lost when a derived cache expires.

Existing limits remain: 32-block regions, a bounded active-region count,
round-robin updates, one bathymetry refresh per level tick, ten-block capped
offshore depth and four-block dry-shore elevation. Terrain sampling is
loaded-chunk-only and uses integer-column heightmaps for dry beds, not a full
sub-block obstacle mesh. The shared native Gerstner surface remains the
rendering owner; shoreline wash and boat/current coupling remain consumers.

## Validation

The JUnit regressions cover:

- Closed-basin conservation with strong impulses in very shallow water.
- Still water over uneven beds without spurious currents.
- A dam release into dry cells without adding minimum-depth water.
- A high dry sill that cannot be crossed before overtopping.
- Individually open ocean boundaries with accounted inflow and outflow.
- Retention and eventual processing of time above the CFL work cap.
- Conservative bed refresh and explicitly accounted removal of blocked cells.
- Bounded finite storm responses and fair region scheduling.

Run the normal isolated Gradle tests for
`com.thunder.wildernessodysseyapi.watersystem.ocean.shore.ShallowWaterGridTest`
and `com.thunder.wildernessodysseyapi.watersystem.ocean.shore.ShorelineWaterManagerTest`.
Compilation/testing is distinct from live proof: inspect ebb/flood currents,
dry beach transitions, a sea-level freshwater lake, chunk-edge unloads,
terrain edits and boat motion in-game before claiming those behaviours have
been visually or physically verified.
