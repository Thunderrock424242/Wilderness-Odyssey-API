# Water system testing checklist

Use this checklist to test the connected finite-water, regional hydrology,
weather exchange, flooding, SPH and coastal systems. Implementation completion
does not mean every visual, multiplayer or long-running scenario has passed.
Check a box only after running that test on the build recorded below.

For the design, see [conservation architecture](conservation-architecture.md),
[regional configuration](watersheds-and-flooding.md#configuration),
[atmospheric exchange](atmospheric-exchange.md), and
[the coastal solver](coastal-solver.md).

## 1. Record the build and prepare safely

| Field | Record for this run |
| --- | --- |
| Date and tester | |
| Commit and any uncommitted changes | |
| Mod JAR filename and optional SHA-256 | |
| Minecraft / NeoForge / Java versions | |
| Other mods and shader pack | |
| World seed, dimension and test coordinates | |
| Fresh world or backup copy of an older world | |
| Water/weather settings changed from defaults | |
| Client hardware, render distance and simulation distance | |

- [ ] Use a disposable Creative world with cheats, or a backup copy. Never use
  the only copy of an important world for migration or flood experiments.
- [ ] Use normal generated terrain for regional tests. A Superflat tray is useful
  for local flow, but is not representative of natural drainage or shorelines.
- [ ] Find a natural river/lake with low banks and an open coast. Avoid the
  starter bunker, villages and other structures for the first flood test.
- [ ] Begin with default settings and no external shader pack. Test optional
  rendering/mod combinations separately after establishing a baseline.
- [ ] Use the regular mod JAR, not a `-sources.jar`, and keep one matching version
  in each test installation.
- [ ] Keep the game unpaused while observing change. Hydrology follows server
  game ticks, not time spent in a paused menu or with the server stopped.

Do not use `/time add` to simulate elapsed hydrology. Do not use `/fill` water,
`/wowater convert`, or `/wowater repair` to prepare a conservation measurement:
those are world edits/repairs, not controlled reservoir transfers. Do not toggle
the water gamerule off and on to reset state; authority mode is persisted.

## 2. Automated checks

Use JDK 21 and the checked-in wrapper from the repository root. Run commands one
at a time. Do not run another Gradle/NeoForm task alongside a development client,
server or GameTest task controlled by the same validation session.

Compile first:

```powershell
.\gradlew.bat compileJava -PcodexBuildDir=.codex-build --no-parallel
```

Run focused water and weather-simulation tests:

```powershell
.\gradlew.bat test -PcodexBuildDir=.codex-build --no-parallel --tests 'com.thunder.wildernessodysseyapi.watersystem.*' --tests 'com.thunder.wildernessodysseyapi.weather.simulation.*'
```

Run world-backed tests after compilation succeeds:

```powershell
.\gradlew.bat runGameTestServer -PcodexBuildDir=.codex-build --no-parallel
```

This uses the separate `run-gametest` directory. It starts a real test server,
changes its disposable test world, and should exit with a test summary. A
server-ready message alone is not a passing GameTest result.

For wider regression coverage and packaging, run these separately:

```powershell
.\gradlew.bat test -PcodexBuildDir=.codex-build --no-parallel
.\gradlew.bat build -PcodexBuildDir=.codex-build --no-parallel
```

The current repository build task packages through `assemble`; it does not imply
that JUnit ran. Reports are under `.codex-build/reports/tests/test/`, artifacts
under `.codex-build/libs/`, and GameTest logs under `run-gametest/logs/`. Test
reports can be replaced by subsequent runs, so retain relevant results locally.

For a development client, after other Gradle work has stopped:

```powershell
.\gradlew.bat runClient -PcodexBuildDir=.codex-build --no-parallel
```

Do not clean/delete caches or change Windows permissions to bypass a failure.
An access-denied error before compilation is an environment-blocked result, not
evidence that water logic failed. Follow the repository recovery instructions.

### Historical baseline, not current certification

The [engineering report](realism-upgrade-report.md#11-tests-and-actual-results)
records a 2026-09-10 pass with 407 focused JUnit tests, all 31 required GameTests,
and successful packaging. Its broader suite had 1,107 total tests, five failures
and three skips. These are historical results for that source/artifact, not
fresh validation of subsequent edits. Record new results below.

## 3. Commands and interpreting the numbers

Run these individually at the test location:

```mcfunction
/wowater mode
/wowater watershed
/wowater budget
/wilderness weather sample
```

Budget and weather controls require cheats/operator permission. The default
position is where the command source is standing, not the block under the
crosshair. To inspect a particular loaded water cell, replace `X Y Z` with its
coordinates:

```mcfunction
/wowater inspect X Y Z
/wowater budget X Y Z
```

| Readout | Meaning / useful check |
| --- | --- |
| `precipitation`, `upstream`, `evaporation`, `downstream` | Cumulative boundary receipts, not instantaneous rates. Compare before/after values. |
| `snow`, `soil`, `groundwater`, `surface_runoff`, `river`, `lake`, `floodplain`, `ice` | Current regional storage. |
| `projection_out` | Water debited from the region into tracked detailed water. |
| `projection_in` | Water returned from a matching detailed claim. |
| `legacy_return` | Explicit input from an older temporary claim that had no regional funding record. |
| `canonical detailed claims` | Remaining funded canonical claims; already debited from regional stores, not another reservoir. |
| `regional residual` | Expected zero milli-units with the default zero tolerance. Preserve evidence of any warning. |
| `Q`, `velocity`, `stage` | Regional discharge in m3/s, velocity in m/s and stage above the coarse bed. |
| Simulated / forcing timestamps | Show whether the region advances and refreshes its weather input. |

One full canonical cell is 4096 units, or 4,096,000 regional milli-units.
New regional stores start dry even beside generated water: the generated
baseline is a separate owner. `/wowater budget` is not a total-volume meter for
an arbitrary bucket tray, every generated ocean block, or SPH particles.
Exact local/SPH transaction arithmetic requires the automated tests, not just
counting visible blocks or particles.

## 4. In-game cases

### W01 - Startup and regional admission

1. Enter the fresh world, visit the river/lake, and run the four initial commands.
2. Wait with the game unpaused and check the budget again.

- [ ] Authority is enabled and the region has an inventory record.
- [ ] Its simulated timestamp advances and residual remains zero.
- [ ] No crashes, repeated water errors or runaway console output.

If there is no physical inventory, check the enabled watershed/weather settings,
dimension support and admission cap. Do not treat a missing inventory as a zero
budget pass. Revisit normally loaded terrain after correcting setup.

### W02 - Bucket flow, closed dam and breach

1. Build an enclosed stone channel away from natural water, with a solid floor,
   side walls and a divider. Keep the setup small and loaded.
2. Place a known number of bucketfuls on one side. Avoid adding/removing water
   while observing a measurement interval.
3. Verify the closed divider blocks transfer, then remove one divider block.
4. Compare a nearly level channel with a short drop/chute. Inspect specific cells.

- [ ] The closed wall does not leak; opening it allows outflow.
- [ ] The donor loses water as receivers gain it, without infinite-source refill.
- [ ] Steeper flow is visibly more energetic without explosive oscillation.
- [ ] Settled shallow water calms instead of moving forever.

A partially filled cell is not a full block of quantity just because its
compatibility projection resembles vanilla water. Narrow modded/waterlogged
hosts are not automatically supported canonical openings.

### W03 - Solid placement and SPH ownership

1. Place a solid into the channel water and inspect neighboring cells.
2. Remove the solid and inspect any water that had no available destination.
3. Create a sufficiently energetic short pour/drop; SPH may be used when its
   configured thresholds and budgets allow it.
4. Run the world-backed tests for exact successful/failed SPH settlement.

- [ ] Placement redistributes water or retains an owned displacement remainder.
- [ ] No visible duplicated body remains after successful settlement.
- [ ] An ordinary low-energy trickle staying canonical is not marked as failure.
- [ ] Automated SPH merge/persistence/settlement/rollback tests pass for this build.

Particle count and numerical particle mass do not measure owned water. Existing
SPH float positions have reduced sub-block precision far from the world origin;
record coordinates when reporting a spatial issue.

### W04 - Rain, soil and delayed groundwater

At a natural river/lake, run:

```mcfunction
/wilderness weather set temperature 15
/wilderness weather force rain
/wilderness weather sample
```

Observe `/wowater budget` every 20-30 seconds for several minutes, then run
`/wilderness weather clear` and continue observing. Sample actual weather if a
debug setting evolves or the expected rain is absent.

- [ ] Accepted precipitation rises during rain.
- [ ] Soil, runoff and receiving stores respond while residual remains zero.
- [ ] Groundwater retains storm memory and releases gradually after recharge.
- [ ] Clearing rain does not instantly empty the catchment.

For a soil comparison, use separate regions with broadly sandy versus
concrete/impermeable surfaces and comparable rain/elevation. Soil is sampled
regionally; a single changed block is not a meaningful permeability test. The
automated soil test is the controlled comparison when natural terrain differs.

### W05 - River routing, lake fill and spill

1. Explore adjacent chunks normally so their terrain can be admitted.
2. Choose a depression and a known lower receiving region; record both budgets.
3. Observe rain accumulation, rising stage, and outflow after containment fills.

- [ ] Known downstream routing and contributing area are coherent.
- [ ] A contained lake retains water before it spills.
- [ ] Routed source output and receiving input are consistent, allowing for
  other upstream contributors and different sampling times.
- [ ] Each region retains a zero residual; a closed unknown frontier does not
  silently export water.

No visible spill at a dry, unfilled or unknown-outlet site is not by itself a
failure. Record the topology and storage that existed during the observation.

### W06 - Funded flooding, protection and recession

1. Use low connected banks outside protected structure/bunker bounds.
2. Sustain rain and watch stage, floodplain, `projection_out` and detailed claims.
3. Check solid walls, a block entity and nearby player construction for damage.
4. After recording the flood, clear rain and apply warm, dry local conditions:

```mcfunction
/wilderness weather clear
/wilderness weather set temperature 25
/wilderness weather set humidity 0.25
```

- [ ] New temporary water has matching regional projection-out receipts.
- [ ] Placement favors safe connected low candidates rather than arbitrary terrain.
- [ ] Structures, walls, block entities and protected blocks remain intact.
- [ ] Normal recession returns water through `projection_in`; residual stays zero.
- [ ] Saved replaceable terrain restores only where it remains safe to restore.

Clear weather is not a command to delete floods. Storage must discharge,
infiltrate or evaporate; a closed basin can legitimately stay wet. No flooding
at a protected site, or before excess exists, is not a failure.

### W07 - Moving and extracting temporary water

1. Let a small tracked flood/pond spread or cross a loaded chunk boundary.
2. Inspect the original funding region, not just the destination chunk.
3. Withdraw water with a bucket; record claims and projection receipts.
4. Place a solid into temporary water and edit one replaceable claimed plant.
5. Observe later recession.

- [ ] Claims retain their funding source when moved/split.
- [ ] Bucket withdrawal reduces detailed claims without a duplicate regional return.
- [ ] Unresolved displacement retains ownership until it can move.
- [ ] Recession never removes ordinary water or overwrites the player's new block.

### W08 - Snow, thaw and rain-on-snow

Run at one location:

```mcfunction
/wilderness weather set temperature -10
/wilderness weather force snow
```

Wait for `snow` storage, then run:

```mcfunction
/wilderness weather clear
/wilderness weather set temperature 15
```

For rain-on-snow, force rain while SWE remains and verify the weather sample.

- [ ] Snowfall increases SWE; thaw decreases it while liquid stores/flows respond.
- [ ] Rain-on-snow does not introduce a nonzero mass residual.
- [ ] Cosmetic snow coverage responds without endlessly recreating collectible snow.
- [ ] Old vanilla snow is not silently claimed as additional regional SWE.

### W09 - Evaporation, temperature and aggregate ice

1. Use a wet region, then compare dry/warm conditions with cold conditions.
2. Observe evaporation receipts, temperature, liquid stores and `ice` over time.
3. Repeat in a depleted region and at an ocean boundary.

- [ ] ET reflects available finite water; ocean exchange has explicit receipts.
- [ ] Temperature responds gradually and regional liquid/ice exchange keeps residual zero.
- [ ] Existing canonical water is not replaced by a conflicting solid ice block.
- [ ] Receipt coupling tests pass; visual humidity changes alone are not proof
  of exact ET-to-weather acknowledgement.

Water appearance may become icy without being walkable. Aggregate thermal state
is not a latent-heat model, and ocean/atmospheric approximations are documented
in the architecture rather than treated as closed global mass.

### W10 - Save/reload, unloaded state and older-world compatibility

1. Record wet-region budgets/coordinates and a small controlled local setup.
2. Save, exit normally, reload, and compare with elapsed simulation accounted for.
3. Travel far enough to unload the region; keep the server running, then return.
4. Repeat on a backup copy of an older world containing ordinary and temporary water.

- [ ] Stores and ownership do not reset on normal reload.
- [ ] Admitted regional state evolves without a nearby player.
- [ ] Unknown terrain is not force-loaded to complete a water simulation.
- [ ] Legacy temporary returns are explicit; permanent/player water survives.
- [ ] Client snapshots recover after reconnect/dimension changes.

Historical weather is held approximately during catch-up; equal final weather
does not guarantee identical past hydrographs. Missing time while the server was
stopped is not replayed. Do not crash/kill the process to claim crash-journal
coverage: cross-file crash atomicity is not implemented.

### W11 - Coast, waves, boats and swimming

At an open coast, compare `/weather clear` with `/weather thunder`, allowing
time for the sea-state response. Also visit an inland freshwater shore.

- [ ] Native crests remain continuous, with no detached translucent breaker ramps.
- [ ] Storm roughness builds and decays smoothly; calm water does not snap.
- [ ] Near-shore currents and wet/dry transitions remain stable.
- [ ] A freshwater shoreline is not treated as an infinite ocean tide inlet.
- [ ] Boats/swimmers respond without violent jitter, launch impulses or desync.
- [ ] Entry splashes, underwater transitions and chunk-edge crossings look coherent.
- [ ] Existing spring/neap tide tests pass; visible wave motion alone is not tide proof.

After the baseline, repeat with the usual shader pack and renderer compatibility
mods. Record each combination separately; a shader-pack-owned water pass may
not show all native visual effects.

### W12 - Erosion and compatibility regression

Use a disposable natural shoreline and `/wowater erosion`. Keep normal erosion
protection/budgets; do not disable safety to force an effect.

- [ ] Eligible material responds to hydrodynamic exposure without runaway destruction.
- [ ] Protected/player structures stay intact; lack of erosion on ineligible terrain is expected.
- [ ] Vanilla and Wilderness buckets retain finite supported placement/extraction.
- [ ] Boats, swimming, waterlogged hosts and installed supported fluid-handler
  integrations behave consistently without importing arbitrary external fluids.

### W13 - Dedicated server and two clients

Use the built artifact in separate matching test installations; finish repository
build work before launching this test. Both clients should have the same mod
version. Start near each other, then occupy separated weather regions.

- [ ] Both clients agree on physical water, floods and entity movement.
- [ ] Local weather/sea-state differences remain local and transitions are smooth.
- [ ] Reconnecting a client restores current water/SWE/region state.
- [ ] Dimension changes and server restart do not leave stale effects or ghost water.
- [ ] No dedicated-server client-class crash or repeating packet errors.

### W14 - Work budgets and soak

Run at least 30 minutes through rain, travel, unload/reload and calm periods.
Record tick-time observations and `/wowater budget` counters. If comparing work
budgets, use comparable world copies, change one setting at a time and record it.

- [ ] Canonical cells/units, SPH particle work and region intervals stay bounded.
- [ ] Regions continue advancing; persistent catch-up debt is reported, not ignored.
- [ ] Water residuals stay zero throughout sampled intervals.
- [ ] Frame/tick performance recovers after storms and local activity subside.
- [ ] No unbounded growth in active bodies/claims or repeated logging after travel.

Absence of a visible lag spike does not prove no chunk loads. A formal no-ticket
claim requires instrumentation/log evidence plus the loaded-only code contracts.
Exact hydrograph equality under arbitrary long catch-up is not promised.

## 5. Optional faster storm profile

Only after a default baseline, stop the test world and locate its active
`wildernessodysseyapi-server.toml`. Water physics belongs to the server config,
not the common/client config. Record the original values and edit existing keys
under `[water_simulation.regional_hydrology]`; do not duplicate the table.

| Setting | Normal default | Optional stress-test value |
| --- | ---: | ---: |
| `rainfallMillimetresPerGameHour` | 12.0 | 120.0 |
| `budgetToleranceMilliUnits` | 0 | Keep 0; do not hide accounting errors. |

Restart and repeat W04-W07. This accelerates input, not every hydrologic process,
and does not guarantee flooding on unsuitable terrain. Restore the original
rainfall value before normal play. Do not simultaneously raise every budget or
weaken protections; that destroys the usefulness of the comparison.

## 6. Results and release gates

Use `PASS`, `FAIL`, `BLOCKED`, or `NOT RUN`, with evidence. Do not prefill passes
from an older report. Keep results/logs in a local test record or attach them to
the relevant issue; do not commit generated worlds, private configs or raw logs
containing personal paths/secrets.

| Check | Status | Evidence / issue / notes |
| --- | --- | --- |
| Production compilation | NOT RUN | |
| Focused JUnit | NOT RUN | Tests / failures / skips: |
| Full JUnit | NOT RUN | Record unrelated failures separately. |
| Packaging and artifact identity | NOT RUN | |
| GameTest server completed | NOT RUN | Required passed / failed: |
| W01 startup/admission | NOT RUN | |
| W02 bucket/dam/breach | NOT RUN | |
| W03 displacement/SPH | NOT RUN | |
| W04 rain/soil/groundwater | NOT RUN | |
| W05 routing/lake/spill | NOT RUN | |
| W06 flood/protection/recession | NOT RUN | |
| W07 temporary ownership/extraction | NOT RUN | |
| W08 SWE/thaw/rain-on-snow | NOT RUN | |
| W09 ET/thermal/ice | NOT RUN | |
| W10 persistence/unload/legacy | NOT RUN | |
| W11 coast/entities/rendering | NOT RUN | |
| W12 erosion/compatibility | NOT RUN | |
| W13 dedicated/two-client | NOT RUN | |
| W14 budget/soak | NOT RUN | Duration / workload: |

- [ ] No unresolved water creation/loss, ownership, destructive restoration or
  dedicated-server failure in the tested scenarios.
- [ ] Automated results match the recorded artifact/source state.
- [ ] Client, multiplayer, persistence and performance evidence are reported
  separately; skipped/blocked cases are explicit.
- [ ] Temporary test settings were restored and remaining limitations documented.

If a failure occurs, capture the first relevant error and a short reproduction:

```text
Build / commit / local edits:
World seed / dimension / coordinates:
Test ID and setup:
Changed settings / other mods / shader pack:
Actions and elapsed unpaused time:
Expected result:
Actual result:
Before/after budget and weather samples:
Screenshot or clip:
Relevant log excerpt (redacted):
Reproduces after normal reload? Yes / No / Not tested:
```

Collect `/wowater mode`, `/wowater budget`, `/wowater watershed`, and
`/wilderness weather sample`. Add `/wowater inspect X Y Z` for a particular
cell, or `/wowater erosion` for terrain changes. Do not repair, convert or erase
the failing setup before retaining enough evidence to reproduce it.
