# Water and weather hardening

Implemented October 7–8, 2026 following the water/weather review. Canonical
fixed-point water, generated spans, regional SavedData receipts, server-owned
weather, existing packets and `WaterRenderCoordinator` retain their responsibilities.

## Changes

| Area | Result |
| --- | --- |
| Generated water disturbance | An owned untracked source hands its native fluid tick to finite water. A broken block materializes only its loaded 3x3x3 neighborhood. |
| Solid replacement | Successful direct solid writes displace the exact parcel. Repeated callbacks cannot re-import that parcel. Enclosed volume remains a hidden, undrainable reservoir and becomes ordinary water when the solid is removed. |
| Placement cancellation | Snapshot capture/restoration does not commit displacement. Narrow hooks after NeoForge's final single/multi-placement acceptance result avoid displacement before a later LOWEST listener cancels. |
| Weather config reload | Cached config publication and authority invalidation share the server-thread closure; direct watcher callbacks also enqueue invalidation. |
| Disabled weather coupling | Pending committed precipitation stays saved; application and new exchange pause. Re-enabling applies the retained amount exactly once. |
| Snapshot fairness | Pending chunks rotate within eight payloads/4096 changed cells per pass. A nearby missing baseline has priority. Existing paged baselines and delta revision checks are preserved. |
| Renderer transitions | Disable restores baked fluid geometry and releases custom resources. Enable dirties all snapshot meshes. External shader transitions follow the same policy. |
| Underwater fog | Blindness, darkness and another cancelled fog owner retain their fog restrictions. |
| Precipitation visuals | Server rain/snow/hail phases determine visual channels. Neighbor and packet transitions blend those channels by precipitation intensity; client surface temperature cannot classify them again. |
| Terrain work | Surface height probes are lazy and cached. Bounded candidate scans resume across refreshes and preserve published patches, so a dry inner area cannot permanently hide outer wet terrain. |
| Climate retention | Climate and water coverage retain admitted grid cells and refresh at most 64 cells per lattice per capture; missing chunks retain last-known context. |

No packet or persistence schema change is required. Machine capability bridging
can be disabled without disabling solid displacement conservation. Waterlogged
hosts and external fluids retain their existing handling.

## Work bounds and diagnostics

`/wowater summary` includes queued finite cells, generated materializations,
aggregate pending chunk count, oldest pending age in ticks and unfinished paged
baselines. These are diagnostic reads of existing state.

`/wilderness weather physics` includes the last input capture's terrain probe
attempts and the existing shared worker rejection count. The two climate/water
lattices attempt at most 64*(9+64)=4672 loaded-column checks per capture.

The weather F3 page includes surface candidate/probe counts and cached heights.
Each surface refresh scans at most min(4096,max(256,4*maximumSurfacePatches))
candidates and probes at most min(4096,max(256,5*maximumSurfacePatches)) heights.
Published patches stay capped at the configured patch count, with nearest
patches preferred. Refreshing the entire radius can take several bounded passes;
this is gradual cosmetic work, not a measured frame-time improvement claim.

## Automated validation

Run commands separately with JDK 21:

```powershell
.\gradlew.bat weatherPhysicsTest renderingTest -PcodexBuildDir=.codex-build --no-parallel
.\gradlew.bat :test -PcodexBuildDir=.codex-build --no-parallel
.\gradlew.bat runGameTestServer -PcodexBuildDir=.codex-build --no-parallel
.\gradlew.bat :build -PcodexBuildDir=.codex-build --no-parallel
```

Unit regressions cover phase identity and spatial/temporal mixtures, receipt
pause/save/resume, more than eight continuously dirty chunks, render ownership,
fog restrictions, more than 2048 retained climate cells, staggered refresh,
lazy height budgets and progression past a dry inner terrain ring.

GameTests cover generated native ticks, direct/repeated/enclosed displacement,
reservoir release, provisional restoration, final accepted and cancelled
single/multi-placement hooks, disabled machine compatibility, sediment and
temporary claim preservation, and worker-thread reload invalidation. Flow tests
use a dedicated empty 16x12x16 template to isolate neighboring parcels. Its
source asset can be regenerated with `tools/gametests/generate-water-fixture.ps1`;
the shared legacy empty template is preserved.

Executed October 8, 2026:

| Check | Result |
| --- | --- |
| `weatherPhysicsTest` | 338 tests, zero failures/skips |
| `renderingTest` | 287 tests, zero failures/skips; overlaps client weather coverage above |
| Root `test` task in the broad test invocation | 1431 tests, zero failures/errors, four skips; includes `WaterSyncScheduleTest` |
| `runGameTestServer` | All 40 required tests passed; clean server shutdown |
| Explicit root `:build` | Completed successfully; regular mod JAR produced |
| Broad `test` and `build` invocations | Failed in the separate `aether-server:test`, 53 tests with one incomplete-HTTP-header EOF assertion receiving `SocketException: Connection reset` |

The wrapper's unqualified `test`/`build` selectors also select subproject tasks;
the `:test`/`:build` forms above name the mod's root tasks explicitly. The Aether
failure is outside water/weather and was preserved. An intermediate explicit
root build hit a concurrently edited meteor GameTest using an older
`EnvironmentSyncPayload.from(snapshot)` signature. After its owner corrected that
call, the explicit root build completed successfully. No unrelated code was
repaired or reverted by this water/weather pass.

The preceding red runs reproduced native-tick ownership, phantom water after
direct solid placement, temperature reclassification, late event cancellation
and a hidden parcel failing to resume. Those failures are distinguished from
missing-helper API compilation during test-first work and from concurrent
unrelated source changes.

Local reports are under `.codex-build/reports/tests/`. The final successful
GameTest log is `.codex-build/water-weather-gametest-final.log`. The regular
artifact is `.codex-build/libs/wildernessodysseyapi-5.0.0.jar`; it includes the
shared checkout's other concurrent edits at packaging time. Water/weather code
was unchanged after its successful GameTest/unit runs; subsequent unrelated
changes were compiled by the successful root build. Live client, multiplayer
and performance proof remains separate. This is the regular mod artifact.

SHA-256 at packaging: `226595625BBB9EF8FF7731CB26CC5115F8FA5AF3765126630BF0E91AF9CA27AC`.

## Manual acceptance still required

Use a disposable fresh world or a backed-up older world and record the exact
artifact/settings. These cases require a real client or multiple clients:

1. Break a bank beside untouched generated water. Confirm finite flow starts
   without a whole-chunk import or extra source creation.
2. Place a solid in bucket water, generated water and funded floodwater. Repeat
   through a protection mod that cancels at LOWEST, and through a piston/machine
   or command write. Check exact inventory and temporary claims; cancelled
   placement must preserve the original parcel.
3. Enclose water, replace it with a solid, unload/reload, then remove that solid.
   The hidden parcel must stay undrainable while covered and reappear once opened.
4. Disable coupling with precipitation pending, save/restart and re-enable.
   Confirm the saved amount applies once and regional residual remains zero.
5. Keep more than eight watched chunks changing with two clients. Inspect sync
   pending age, approach a new chunk, cross dimensions, unwatch/rewatch and logout.
   Pure scheduling tests do not prove delivery latency over a live connection.
6. Toggle replacement rendering off/on while viewing a shore, switch an external
   shader pack on/off and reload resources. Confirm no holes, duplicate tops or
   missing custom meshes during the baked/custom handoff.
7. Apply blindness and darkness above and below a moving wave. Confirm native
   optics preserve the gameplay fog restriction; repeat with an external pack.
8. View rain/snow/hail transitions across a warm surface layer and an atmosphere
   cell boundary. Phase mixtures should fade smoothly without temperature-based
   conversion. Verify near columns and distant shafts together.
9. Stay still beside dry inner terrain with wet/snowy patches near the configured
   overlay radius. Outer patches should appear after bounded scans. Compare F3
   probe counts and recorded frame/server timings before/after on the same setup.
10. Hot-reload weather config during a worker calculation, then switch dimensions
    and disconnect. Confirm no stale apply, concurrent mutation or retained GPU
    resources. Automated server tests do not certify shader visuals or performance.
