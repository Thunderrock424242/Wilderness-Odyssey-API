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
loads chunks. `clearLevel` releases the optional feedback on dimension unload;
the regional owner must also call it when disabling physical hydrology. The
weather authority clears its copy on level unload and server shutdown.

## Explicit atmospheric approximation

Atmospheric vapor and cloud water remain normalized quantities. They are not
finite cubic-metre stores, and the current weather model is **not** a closed
global water-budget solver. Precipitation enters the regional budget through an
explicit atmospheric boundary. Its receipt is retained for diagnostics and
acknowledged, but is not subtracted a second time from weather's existing
precipitation/cloud process.

Accepted regional evaporation is converted to a normalized column feedback using
25 mm of precipitable water per inventory unit. That feedback is not multiplied
again by weather's simulation-speed setting. For the regional footprint covered
by receipts, weather disables the old coverage-derived evaporation, biome vapor
restoration and heuristic lake/ocean moisture gains. Thus a depleted physical
reservoir cannot keep supplying vapor simply because terrain is categorized as
wet. The uncovered fraction retains legacy open-boundary forcing, including
unmodeled terrain and ocean background. Atmospheric transport, humidity clamps,
cloud heuristics and seasonal forcing still make global atmospheric mass closure
an approximation; exact conservation claims apply to the regional water owner.

Receipts are ephemeral integration state, not separately persisted water. A crash
can lose the most recent not-yet-applied weather feedback but cannot delete or
duplicate regional water. Regional restart snapshots republish the latest SWE.

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
ET-to-vapor conversion. They are part of the normal Gradle test source set.

In-game checks must separately verify synchronized snow patches, reconnect and
dimension-unload behavior, physically modeled versus unmodeled snow columns,
weather-owner/config switches and delayed weather-worker retries. Source review
and unit tests are not substitutes for this rendering and multiplayer proof.
