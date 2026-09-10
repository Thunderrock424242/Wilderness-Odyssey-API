# Water conservation architecture

Regional hydrology now owns persistent finite catchment inventory. Canonical water
remains the detailed local authority. Generated water, SPH and atmosphere keep
their existing owners; this is not a global per-block water solver.

## Ownership and units

```text
normalized weather / explicit atmosphere boundary
        | accepted precipitation        ^ exact ET receipt
        v                               |
+--------------- regional finite inventory ----------------+
| SWE -> runoff <-> soil -> groundwater -> delayed baseflow |
|            |                                              |
|            v                                              |
|    river <-> lake <-> floodplain       liquid <-> ice      |
+--------|-------------|------------------------------------+
         | Qout        | reserve and debit exact parcel
         v             v
 cached downstream    canonical local water <-> mobile SPH
 regional inventory   4096 units/m3           exact represented units
                       |
                       +-> external bucket / supported authority consumer

generated lazy spans -- local disturbance --> sparse canonical override
ocean boundary <------ explicit regional receipts ------> regional inventory
```

A full block is one cubic metre for the model. One canonical unit is 1/4096 m3;
one milli-unit is 1/1000 canonical unit, so 4,096,000 milli-units is one m3.
Regional stores and receipts use nonnegative longs. Normalized saturation, flood
risk, sediment and client snapshots are derived metadata, never spendable water.

`RegionalHydrologyState` owns SWE, soil, groundwater, runoff, river, lake,
floodplain and aggregate ice. `HydrologicStorage.transfer` uses one accepted
quantity for both sides. Boundary counters are checked before mutation.
With zero opening inventory, the per-region equation is:

```text
P + upstream + ocean-in + projection-return + legacy-return
- ET - downstream - ocean-out - projection-out
- (SWE + soil + groundwater + runoff + river + lake + floodplain + ice)
= 0 milli-units
```

Every routed output has an equal downstream input. `HydrologicFlux` describes
the latest interval; cumulative receipts/stores are the audit authority.
`CANONICAL_PROJECTION` is a zero-capacity compatibility slot, not a second
copy of materialized water. `/wowater budget [pos]` reports each store/boundary,
residual/tolerance, funded detailed claims, discharge/stage, temperature,
topology, forcing time and work counters. Detailed claims are a memorandum:
they have already left regional storage. The command is operator-only and
does not import water, scan terrain or repair balances.

## Canonical flow and provenance

Sparse generated baseline remains unchanged until disturbance. Head combines
voxel elevation, depth and at most eight connected overlying water cells.
Orifice-style flow uses head difference, opening fraction and elapsed seconds.
One multi-neighbor equilibrium limiter prevents destinations from independently
spending the same source allowance. Actual accepted credit determines source
debit; integer rounding stays in an owner.

Momentum uses gravity/head acceleration, prior velocity and exponential drag,
with speed/timestep caps. A queued cell runs at most once per level tick.
Elapsed clocks are bounded and cleared on sleep/unload. Local elapsed time is
capped at one second: budget-aware, not an exact arbitrary-time CFD integrator.
Long local stalls intentionally dissipate unresolved motion.

Solid walls are closed. Only existing safe canonical hosts accept flow.
The opening estimate does not add arbitrary fence/waterlogging or external-fluid
import support. There is no expensive voxel CFD scan.

Cell modifier methods preserve provenance during volume/velocity/temperature
changes. Canonical storage repairs position-owned generated/dry flags. Temporary
flags and exact claims move together; ordinary/temporary and incompatible-funded
mixing is rejected. Accepted canonical transfers mix temperature by volume.

## Regional-to-canonical transactions

`RegionalWaterProjection` reserves an exact regional parcel before placement.
Projection-out is recorded only after canonical placement and ledger creation;
closing an uncommitted reservation restores its source. Floods debit floodplain,
ponds/wetlands debit lake and springs debit groundwater storage.

The version-5 temporary ledger stores exact units, funding region, basin, original
terrain, kind and placement time. Funding survives movement across chunks.
Bucket withdrawal releases the corresponding claim without regional credit:
the bucket now owns that water.

Recession requires an exact canonical/flag/ledger match and credits the funding
region's runoff after removal. Player/mod overwrites cannot generate fictitious
returns. Displacement reservoirs retain their claim. Ordinary water cannot pass
the removal gate. Terrain restoration only touches saved, still-valid plants in
empty space, never player construction.

These are server-thread transactions, not a cross-file crash journal. Chunk
attachments and dimension SavedData can still reach disk at different moments
during abrupt termination; do not claim crash-atomic ownership across files.

## SPH

Numerical particle mass/count is not represented quantity.
`SPHSimulator.canonicalVolumeUnits` owns the exact canonical debit. Merges accept
the entire quantity or reject without integer saturation. Capture ensures a
residual location marker; invalid positive-volume saves without a location are
rejected instead of silently dropped.

Settlement credits every represented unit or rolls back all partial canonical
writes and replaceable terrain. Failure leaves the SPH body owning its full
inventory. Restored water-bearing bodies are not discarded solely because the
runtime body budget was lowered. Temporary floodwater remains canonical because
funding provenance is not yet part of the SPH persistence payload.

## Regional models and lifecycle

Admitted 16x16 records survive chunk unload and save/reload. Round-robin coarse
evolution has no player-distance gate. Terrain/soil admission uses normally
loaded chunks. Cached D8 priority-flood topology resolves area, outlets, basins
and depression spills across admitted chunks. Unknown frontier remains closed;
no missing terrain is loaded. An edited DEM cell invalidates topology; the
admitted-node rebuild is time-sliced without resampling unrelated terrain.

Soil tags select sand, gravel, loam, clay, rock or impermeable profiles.
Conductivity, porosity, field capacity, wilting point and vegetation control
infiltration, ET and drainage. A nominal four-metre catchment aquifer retains
recharge and releases exponential baseflow. Saturated aquifers keep rejected
recharge in soil. SWE melt transfers existing snow to runoff; rain-on-snow
changes melt rate without creating a second source.

A reach per record derives width from contributing area, rectangular section,
Manning discharge in m3/s, velocity, stage and bankfull capacity. Lakes fill a
coarse depression before spilling; actual excess funds floodplain storage.

Normal intervals are two simulation seconds. Bounded catch-up intervals can
coarsen to one simulation hour; remaining elapsed time is retained. Exponential
stores avoid missed-tick replay. Historical forcing is a zero-order hold of the
last weather sample, not reconstructed storm history. Once caught up, cached
atmosphere supplies the next interval even on unloaded terrain. Mass remains
exact, but nonlinear partitioning/hydrograph timing is approximate under long
intervals. There is no wall-clock simulation while the server is stopped.

## Atmosphere, snow, ocean and thermal boundaries

One regional ET debit supplies the matching weather receipt. Weather acknowledges
only accepted worker batches and suppresses duplicate heuristic vapor gains over
the modeled footprint. Vapor/cloud quantities are still normalized: precipitation
and ocean exchange are explicit open boundaries, not closed global atmospheric
mass. See [atmospheric exchange](atmospheric-exchange.md).

Modeled snow uses SWE and synchronized cosmetic coverage. The weather scheduler
does not repeatedly create collectible snow there; old vanilla snow is external
and untouched. Aggregate liquid/ice exchange conserves water without placing a
solid over duplicate hidden liquid. Regional temperature follows air/sun with
thermal inertia and initializes funded canonical parcels; local movement mixes
temperature. This is not enthalpy conservation or temperature-complete SPH.

Oceans stay effectively infinite with named input/output receipts. Generated
rivers/lakes retain the separate lazy baseline. New finite incremental catchment
stores start dry: old normalized metadata is not a recoverable water quantity,
and generated/canonical/SPH water is neither deleted nor copied.

## Waves and existing consumers

Native Gerstner crest geometry, the shared CPU/GPU equation, tides and regional
sea-state synchronization remain. No detached breaker ramps or new render owner.
The coastal grid adds conservative shared-face discharge, positivity limiting,
side-specific ocean boundaries and retained CFL time. Its represented volume
is derived, never physical inventory. See [coastal solver](coastal-solver.md).

Existing entity and erosion owners consume shared physical currents. Regional
current is Manning-derived; local drainage metadata no longer amplifies it into
a second heuristic force. Existing erosion material/exposure/sediment, structure
protection and mutation budgets stay in charge.

## Persistence and compatibility

- New regional SavedData version 1 persists stores, receipts, fractional forcing
  remainders, weather, temperature, timestamps and DEM/topology.
- Admission stops at the cap; existing physical records are never evicted even
  if that cap is reduced.
- Flood versions 1-3 migrate full-block claims except version-3 wetlands
  (2048 units). Version 4 retains exact units. Old claims have no invented
  regional debit; their return uses a named legacy input.
- Unknown regional/flood schemas, oversized/truncated inventories and duplicate
  claims are rejected rather than replaced with empty owners.
- Old normalized metadata, basin aliases and old hydrology remainder files remain
  preserved. The old probe model is not advanced alongside the new owner.
- Public APIs, registries, finite buckets, waterlogging safeguards, generated
  spans and packet shapes remain. Public basin aliases are compatibility
  identities; the diagnostic basin is the physical graph identity.

See [the engineering report](realism-upgrade-report.md) for exact validation,
changed files, approximations and remaining live checks.
