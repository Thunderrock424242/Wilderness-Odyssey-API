# Water mesh detail live acceptance

Date: 2026-11-02. Source: clean `in-dev` HEAD `7b800ed6` at the start of
this pass. Follows [distance-based mesh detail](distance-based-mesh-detail.md)
and the earlier [live client record](live-client-acceptance.md).

Follow-up: [the foam-pattern fix](foam-pattern-fix.md) isolates and corrects the
repeating crest mask. The observations below retain the original pre-fix run.

## Setup

One normal NeoForge `runClient` process owns the integrated server. The ignored
profile `.codex-build/water-lod-acceptance-20261102.init.gradle` selects the
already installed Corretto 21.0.13, a 1–6 GiB heap, and the isolated game
directory `.codex-build/run-water-lod-20261102`. It does not use `-Xint` or
restrict compilation to C1. The build's existing two narrow compiler exclusions
for `TickEngine.finishTick` and Biolith biome search remain active.

The normally stopped disposable world `Water acceptance 2026-10-29` was copied
into that new profile. Only its save and display options were copied; mod
configuration was generated fresh. No diagnostic SPH, watershed or tick-rate
override was applied. The development runtime reports 23 mods, Minecraft
1.21.1 and NeoForge 21.1.262. No external shader pack or optional replacement
terrain renderer was added for this pass.

The window was maximized. The framebuffer reports 2560 × 1494 on the NVIDIA
RTX 4090 Laptop GPU, OpenGL 4.6 / driver 621.01. Render/simulation distances
are 8/6 chunks, VSync is enabled, and the frame cap is 60 FPS. Automatic water
selection chose **HIGH** because of the display workload, with GPU/CPU/memory
capability reported as Cinematic. This is the selected preference, not proof
that no adaptive quality clamp occurred afterward.

```powershell
.\gradlew.bat compileJava :test '-PcodexBuildDir=.codex-build' --no-parallel --tests '*WaterMeshLodPolicyTest' --tests '*WaterMeshTopologyTest' --tests '*WaterSurfaceMeshSamplesTest' --tests '*WaterSurfaceVertexGpuTest' --tests '*ClientWaterSurfaceInvalidationTest' --tests '*WaterRenderingConfigTest'
.\gradlew.bat runClient '-PcodexBuildDir=.codex-build' --no-parallel --init-script .codex-build/water-lod-acceptance-20261102.init.gradle
```

Compilation and the focused run completed successfully in **43s**. XML results
contain **37 tests / six suites**, with zero failures, errors or skips.

## Observations

- The client entered the copied world and rendered the existing generated
  shoreline near 628/63/-957. Sampled views showed continuous animated water
  without an obvious open edge crack. This is visual spot coverage, not an
  exhaustive seam or temporal-pop certificate.
- The installed teleport command redirected an explicit above-ground request
  underground, as in the earlier pass. A vanilla `spreadplayers` surface search
  returned the player to shore. No teleport code or configuration was changed.
- One tagged, persistent, invulnerable cod with no AI or gravity was summoned
  at 648/61/-984 in this disposable world. Spectating it provided an actual
  underwater camera view over Wilderness water, including fog and the underside
  of the surface. Moving the fixture to height 64 provided an air-side view.
  This does not verify normal player swimming, buoyancy or wake behavior.
- F3+T completed between 16:01:16 and 16:01:19 local time. The client displayed
  the reloaded-resource message and the water surface returned.
- A later fixture move to 748/70/-984 required detaching the spectator camera
  to refresh its client position. The General HUD then confirmed the ocean
  position. That discontinuous camera move does not test a continuous flight
  through the detail boundary.
- The elevated ocean view showed conspicuous repeating light/dark bands and
  checker-like bright patches. They were also visible with adaptive detail
  disabled. The fragment shader has a periodic sine/cosine foam mask, which
  is a possible contributor, but this pass did not isolate material shading,
  geometry, scene capture or distant fallback as the cause. This remains a
  visual follow-up, not a proven LOD regression or a clean visual-acceptance
  result. In-game F2 evidence is local under
  `.codex-build/run-water-lod-20261102/screenshots/2026-11-02_16.06.55.png`.

Client quality preference changes are made only in the isolated profile's
generated TOML. Its default file is backed up under ignored build output.
The NeoForge file watcher logs reloads after each requested change. Effective
water tier and actual mesh detail must still be distinguished from those
requests because the global adaptive ceiling remains active.

| Requested client setting | Observed outcome |
| --- | --- |
| Manual LOW while paused | File watcher reloaded at 16:01:50; water remained visible after resuming. |
| Manual MEDIUM | File watcher reloaded at 16:02:21; the sampled surface remained visible. |
| Manual HIGH | File watcher reloaded at 16:03:06; river/ocean views continued rendering. |
| Manual CINEMATIC and adaptive detail off while paused | File watcher reloaded at 16:04:50; the ocean surface remained visible after resuming. |
| Adaptive detail on again | File watcher reloaded at 16:05:30; the sampled ocean view remained visible. |

These are config-reload and continued-rendering checks. Without the Rendering
page counters they do not certify the effective tier, individual fine/coarse
mesh counts or successful detail convergence in every setting.

At 16:05:28 the existing weather authority accepted forced rain across nine
cells. At 16:06:09 the actual sample at 748/70/-984 instead reported `NONE 0.000`,
humidity 0.058, storm energy 0.657 and effective wind 14.84 blocks/s. This
does not prove a sustained rain/storm visual sequence, storm-driven LOD changes,
runoff or spontaneous weather formation. The local weather was cleared through
the normal command afterward. The isolated client defaults were restored from
the backup and compared byte-for-byte before save/reload.

The initial shoreline/underwater spot readings showed approximately 58–60 FPS
at the 60 FPS cap and roughly 4–6 ms integrated-server ticks. Heap usage varied
with garbage collection within the 6 GiB cap. These are visible spot readings,
not a benchmark, percentile distribution, memory-leak test or measured LOD gain.
Automated arrow input did not advance the custom debug panel from General, so
water mesh counts, rebuild timings, scene-copy timings and GPU optical timings
have not been captured in this pass.

## Acceptance boundary

Normal Save & Quit logged all dimensions saved at 16:07:31. The saved world
was reopened through the Singleplayer UI. It completed loading at 16:09:21,
rendered water again and retained the sampled player position 748/70/-984.
The General HUD showed the active world clock advancing afterward. This
verifies ordinary reload and render lifecycle, not a field-by-field water
ledger comparison or historical-world migration.

The reloaded world was saved and left normally a second time. All dimensions
were saved at 16:10:15, and Quit Game logged `Stopping!` at 16:10:35. Gradle
reported **BUILD SUCCESSFUL in 16m 13s** and the command completed with exit
code zero. No native JVM/GLFW crash occurred in this invocation. The duration
includes startup, pauses, resource reload, disconnection and saved-world loading.
The client and its integrated server are stopped. Client defaults were read
back after world reload: automatic quality on, manual fallback Cinematic,
adaptive surface detail on and near-detail distance 64 blocks. The tagged cod
remains confined to the disposable test save.

| Check | Result |
| --- | --- |
| Compile and focused automated tests | Passed, 37 tests. |
| Normal-JIT world entry, shoreline and river/ocean views | Observed. |
| Underwater and air-side camera views | Observed with the tagged spectator fixture. |
| F3+T resource reload | Passed; water returned. |
| Four tier preferences and adaptive-detail off/on | Reloaded and kept rendering; effective tiers/counts not certified. |
| Normal Save & Quit and UI reload | Passed; water returned at the saved sampled position. |
| Client/integrated-server shutdown | Passed; both stopped normally, Gradle exit zero. |
| Documentation review | Passed `git diff --check`, new-report whitespace/conflict checks and screenshot-reference check. |
| Sustained storm and storm-specific detail response | Incomplete; the later sample had no precipitation. |
| Continuous distance-transition flight and temporal popping | Incomplete; tested camera positions were discontinuous. |
| Detailed render timings, mesh counts and performance gain | Not measured; debug-page navigation did not respond to automated input. |
| Final visual quality | Open; periodic bands/bright patches need investigation. |
| Lake transitions, swimming/boats, optional renderers and multiplayer | Not exercised in this single-client pass. |

No production source was changed during this acceptance pass. The next focused
investigation should reproduce the periodic surface pattern and obtain reliable
render counters before treating the new detail system as visually/performance
accepted. Earlier native JVM/GLFW failures are not considered generally resolved
by one successful invocation. This is not a representative 30-minute performance
soak.

Local runtime evidence remains ignored: `.codex-build/water-lod-live-20261102.log`,
the isolated profile's logs/world and the client-config backup. Do not commit
these files or private diagnostic archives.

Only this report and its link in the mesh-detail checkpoint were changed.
No Java, shader, registration, dependency, production config, system Java,
driver, permission or security changes were made. No commits or remote actions
were performed.
