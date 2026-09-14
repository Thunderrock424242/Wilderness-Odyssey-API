# Atmospheric exchange and snow projection

Regional hydrology owns finite soil, groundwater, runoff and snow-water-equivalent
storage. Weather continues to own atmospheric temperature, humidity, clouds,
precipitation type, wind, seasonal sampling and its existing synchronized snapshots.
`AtmosphericWaterExchange` connects those owners with receipts, not another reservoir.

## Transfer contract

After completing a hydrology interval, the regional owner calls
`AtmosphericWaterExchange.publish(level, chunk, precipitation, evaporation, swe, area, throughTick)`.
Amounts are nonnegative long milli-canonical units: 1,000 per canonical unit and
4,096,000 per cubic metre. `precipitation` is the amount actually accepted by
regional storage; `evaporation` is the amount actually removed. `swe` is the
remaining snow-water equivalent, not a rate. The terrain-node footprint is at
most 256 square metres. Repeated or older `throughTick` values are ignored.

Weather captures cumulative per-chunk watermarks while preparing its immutable
worker input. A capture does not consume feedback. Only a successfully applied,
revision-checked weather batch acknowledges its captured watermarks. A receipt
published during calculation remains pending for the next batch; rejected work
does not discard it. Catch-up applies the captured flux once, while retaining the
same regional surface ownership on subsequent catch-up steps. Forecasts and
ordinary queries cannot acknowledge or drain receipts.

The book is indexed by atmospheric cell rather than scanning all terrain on each
query. A cell-size change rebuilds only the in-memory index. Its entry count is
bounded by the regional owner's admitted nodes. Neither capture nor publication
loads chunks. The receipt book lives in regional hydrology SavedData, so
`clearLevel` and server shutdown do not discard pending transfers.

## Physical atmospheric transfers

Weather retains vapor, cloud liquid and cloud ice in column kg/m². One kg/m²
equals one millimetre of liquid water. The normalized humidity/cloud API is
derived from that state and cannot truncate its inventory.

Accepted regional evaporation is converted by dividing milli-units by
4,096,000, then by atmospheric cell area, then multiplying by 1,000 to obtain
mm. The legacy 25 mm-per-inventory-unit helper remains available, but the
physical solver stores the complete accepted amount. Simulation speed and
numerical substeps never multiply the same receipt again.

For covered terrain, the atmosphere disables independent bulk evaporation and
biome vapor restoration. Unmodeled terrain retains explicitly reported open
boundary forcing. A depleted finite store cannot keep supplying vapor simply
because the terrain has water coverage.

Atmospheric cloud sedimentation removes water before a committed weather batch
calls `publishPrecipitation`. Typed integrated depths multiply each admitted
node's area; rain enters runoff, snow/sleet/hail enter SWE, and freezing rain
enters ice. Every pending sub-unit fraction survives save/load and repeated
application. Capacity-rejected amounts remain pending. Replayed publication
clocks are ignored. The regional normalized rain calculation is disabled for
physically coupled nodes, preventing a second precipitation source. Modelled
SWE/ice melt transfers existing stored mass into liquid.

Only admitted terrain receives finite precipitation credits. The rest of a
weather cell's precipitation is explicit export to unmodeled terrain.
Hydrologic scheduling applies pending precipitation at the next bounded regional
step; it does not reconstruct a sub-step rainfall history during catch-up.
Conservation holds over atmosphere plus regional stores plus pending transfers
and named open-boundary exchanges, not an infinite global ocean/atmosphere.

## Persistence and recovery boundary

Regional SavedData contains the versioned receipt book alongside water stores.
It saves cumulative evaporation, acknowledgement watermarks, current SWE
observations, typed pending precipitation fractions and replay clocks.
An orderly save/restart retains pending transfers and does not recredit an
acknowledged receipt. Older regional saves without a book initialize it empty;
version 1–3 atmospheric saves migrate once to physical state.

Atmosphere and regional water remain separate Minecraft SavedData files.
An abrupt process crash between their writes is not an atomic cross-file
transaction and can replay or lose the latest exchange. This implementation
does not claim a crash-proof distributed journal. Existing water inventory
validation remains fail-closed for invalid regional data.

## Snow appearance and compatibility

For modeled terrain, the surface weather model derives its snow coverage from
regional SWE instead of independently adding precipitation or melting a second
snowpack. Full visual coverage corresponds to 50 mm liquid equivalent. Existing
weather snapshots carry the atmospheric-cell-area average; there is no new
client-authoritative store or per-block scan. A partially covered atmospheric
cell blends this SWE projection with the existing unmodeled surface response.

The existing bounded surface-overlay renderer now draws connected white snow
contours using that synchronized snow value, alongside wetness and puddles. These
patches are cosmetic, loaded-terrain-only, and use the existing render type,
cache cadence and patch budget. They have no collectible volume.

The loaded-column weather scheduler does not create or remove harvestable vanilla
snow layers on physically modeled chunks. Repeatedly replacing harvested snow
without a block/SWE ownership ledger would duplicate the regional reservoir.
Pre-existing vanilla snow is left untouched and is not silently imported into
SWE. Unmodeled chunks retain their prior snow-layer behavior. Canonical water is
still never replaced by frosted ice in the weather scheduler.

## Validation

Focused tests cover outstanding receipt capture, duplicate acknowledgement,
publications during calculation, duplicate interval rejection, negative amounts,
negative chunk coordinates, atmospheric resolution changes, SWE projection during
warm and snowy weather, dry-reservoir evaporation suppression and the accepted
ET-to-vapor conversion. Physical tests also cover saved pending transfers, exact
regional debit/atmospheric credit, all five precipitation destinations, capacity
rejection, fractional carry, area weighting and closed-grid conservation. They
run with `weatherPhysicsTest` through the checked-in Gradle wrapper.

In-game checks must separately verify synchronized snow patches, reconnect and
dimension-unload behavior, physically modeled versus unmodeled snow columns,
weather-owner/config switches and delayed weather-worker retries. Source review
and unit tests are not substitutes for this rendering and multiplayer proof.
