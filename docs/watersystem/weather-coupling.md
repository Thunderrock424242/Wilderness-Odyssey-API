# Weather-water coupling

Weather owns atmosphere, seasons, precipitation type, wind and synchronized
weather snapshots. Regional hydrology owns finite catchment quantities.
`OceanSeaStateField` owns a derived wave response; canonical water remains the
only detailed local volume authority. No client owns gameplay water.

## Shared paths

```text
generated water coverage -> atmospheric terrain/thermal context
atmosphere query -> regional sea state -> existing waves and shore consumers
atmosphere query -> regional hydrology -> SWE/soil/aquifer/river/lake/floodplain
regional accepted ET -> receipt -> accepted atmospheric worker batch
regional SWE -> existing weather snapshot -> cosmetic snow coverage
regional funded parcel <-> canonical water + exact temporary claim
```

The normal watershed and legacy weather-hydrology facades share
`RegionalHydrologyManager`; the old per-player probe ledger no longer runs a
second water cycle. Tick ordering stays with existing server scheduling, while
a same-tick guard prevents duplicate regional advancement.

## Sea state

The existing bounded sea-state field samples player-relevant weather cells,
derives wind/swell/chop/breaking targets and approaches them with asymmetric
storm-build and calm-decay times. Calm wind retains the previous direction.
Clients receive nearby windows and interpolate in space and time. The shared
native surface, immersion, shoreline and entity consumers keep using that
response. There is no new render layer or replacement tide authority.

When localized weather ownership is disabled, the existing sea-state disabled
payload clears stale client state and falls back to dimension rain/thunder.
Finite regional forcing checks weather-coupling and dimension configuration too.

## Finite rain, SWE and evaporation

Accepted precipitation credits runoff or snow SWE. Rain-on-snow increases the
rate of a transfer from existing SWE; it is not another source of liquid.
Regional soil, runoff, lake, river and floodplain stores supply one
availability-limited ET debit. Oceans use an explicit effectively infinite
boundary debit/credit instead of a hidden refill.

`AtmosphericWaterExchange` records the actual rain/ET quantities and remaining
SWE per modeled footprint. Weather captures cumulative receipts for its worker
input and acknowledges them only when that revision is successfully applied.
New receipts during computation remain pending; forecast queries cannot consume
them. Supported terrain suppresses legacy heuristic evaporation/moisture gains.
See [atmospheric exchange](atmospheric-exchange.md) for the receipt and 25-mm
normalized vapor-column conversion.

The atmosphere is still a normalized open-boundary model, not a globally finite
m3 inventory. Precipitation is not debited a second time from its existing cloud
process. Atmospheric transport/clamps and uncovered terrain remain
approximations; exact balance applies to regional water. Unacknowledged feedback
is ephemeral, but losing that feedback on a crash cannot change water inventory.

Admitted regional records evolve without nearby players. They hold cached
forcing over old elapsed intervals, refresh weather for future intervals after
catch-up, and do not load terrain. Detailed blocks remain loaded-only. The model
does not reconstruct missed storms or run while the server is stopped.

## Snow, temperature and freezing

Modeled snow coverage derives from SWE and uses the existing bounded cosmetic
surface renderer. The weather scheduler does not create/remove collectible
vanilla snow on modeled chunks; otherwise harvested snow could duplicate SWE.
Old vanilla snow remains external and untouched. Unmodeled terrain retains its
legacy behavior.

Regional temperature follows air/sun with a thermal inertia approximation;
seasons influence atmospheric inputs, not direct water creation. New funded
canonical parcels inherit aggregate temperature and accepted local movement
mixes temperature by volume. Regional liquid converts to/from exact aggregate
ice storage. No new solid ice projection is created.

The existing custom-water frozen shader response remains cosmetic: it damps
motion and changes normals/roughness/transmission without making a walkable
solid or deleting canonical liquid. The weather scheduler still avoids
replacing Wilderness-owned projections with frosted ice. Shader-pack owners
remain responsible for their own visual response.

## Configuration and compatibility

Sea-state controls remain under `water_simulation.weather_coupling`, including
cell size, sync radius, update cadence, response times and cache cap. Existing
`enableHydrology` participates in enabling the shared scheduler when the normal
watershed path is disabled. Old probe count/rate/remainder settings and saved
files are retained for compatibility but no longer own physical rain/ET.

Unit-bearing hydrology rates, aquifer timescale, thermal toggle, admission,
topology/catch-up budgets and audit tolerance are documented in
[watersheds and flooding](watersheds-and-flooding.md#configuration).
Normalized client packet shapes remain unchanged.

## Validation

Pure tests cover receipt capture/acknowledgement/retry, SWE projection,
availability-limited vapor feedback, sea-state response, tide behavior,
regional budgets and thermal phase conservation. The
[engineering report](realism-upgrade-report.md) records executed commands and
separates JUnit from runtime evidence.

Live checks should cover localized clear/rain/thunder on separated clients,
smooth boat crossings between sea-state cells, snow coverage through thaw,
depleted-reservoir ET, restored SWE after reload, dimension/config switches,
worker retries and a freezing custom shore. A still icy shader surface must
not be mistaken for a new solid or a second frozen-water inventory.
