# WAGE validation and acceptance

Final local verification: 2026-10-19, Windows, JDK 21, NeoForge 21.1.262.
The initial base was `a9b56c66`; the final PR incorporates upstream `in-dev`
commit `3892addb` before integration verification. Validation uses the checked-in
wrapper, isolated `.codex-build` output and one development process at a time.
The initial clean rendering baseline passed 301 cases before WAGE was applied.

## Final commands and results

```powershell
.\gradlew.bat build test renderingTest questCompatibilityTest gpuRenderingTest '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

The combined command completed successfully in **1 minute 54 seconds** on the
merged tree. Before upstream integration, separate packaging/subsystem/hardware
and aggregate-test invocations also passed in 37 seconds and 1 minute 11 seconds.
This repository intentionally keeps API tests separate from `build`, so `test`
must be requested explicitly. Packaging also completed the Aether standalone verification and
structure-viewer distributions. Unchanged Aether/viewer tests were up-to-date
from their completed passing runs; the API and changed rendering suites executed.

| Task | Cases | Failures / errors | Skips |
| --- | ---: | ---: | ---: |
| API `:test` | 1,662 | 0 / 0 | 5 |
| `:aether-server:test` | 57 | 0 / 0 | 0 |
| `:tools:structure-viewer:test` | 53 | 0 / 0 | 0 |
| `:renderingTest` | 328 | 0 / 0 | 1 |
| `:questCompatibilityTest` | 21 | 0 / 0 | 0 |
| `:gpuRenderingTest` | 1 | 0 / 0 | 0 |

Tasks overlap and these counts must not be added as unique tests. Ordinary API
and rendering tasks skip the explicit GPU case, which executes in its own task.
The other four API skips are the existing three symbolic-link tests without
Windows symlink privilege and the explicit 4 GB bunker-fixture test. No permissions,
security settings or global caches were changed.

Timing regressions were observed failing before correction: elapsed-age expiry
at low FPS, out-of-order samples and impossible future labels. The Aether
executable test's TCP-reset failure was reproduced and repaired in the assertion;
its read timeout and healthy-generation/outage checks remain enforced. Added
pressure-sampler tests verify no full-system probe request, separate caches and
clock rollback. They failed without the API and passed after implementation.

The merged API count includes 24 additional upstream coastal cases; WAGE's PR
diff contains no coastal source changes. Final logs and totals are under
`.codex-build/wage-pr-merged-validation.log`, `wage-pr-merged-results.json` and
`test-results/`. The earlier `wage-pr-final-build.log`, `wage-pr-final-test.log`
and `wage-pr-final-results.json` record the pre-integration verification.
No logs, generated worlds, screenshots or build outputs are committed.

## Actual-driver shader correctness and timing

Hardware: NVIDIA GeForce RTX 4090 Laptop GPU, NVIDIA 617.14, OpenGL 3.2 core
context. The test loads the packaged vertex/fragment shaders, compiles/links them
and compares normal/bounded density paths in a private RGBA32F framebuffer.

- Original 24 fixtures: 5,191 visible cloud pixels, finite output, no GL errors,
  maximum channel difference 0.000000000.
- Additional 576 fixtures: all eight near/distant altitude bands, three atlas
  blends, four morphologies, two coverage values, three detail strengths and
  lightning on/off. All comparisons passed at tolerance 0.000020000, with visible
  cloud output required in each band.
- Framebuffer resize, shader/resource recreation and replacement OpenGL context:
  comparisons and GL-error checks passed.

The fixed shader microbenchmark uses a 128 x 128 target, four draws per batch,
30 alternating-order pairs after ten warm-up pairs, and timestamp queries. The
latest completed run measured:

| GPU interval per batch | Normal path | Bounds enabled |
| --- | ---: | ---: |
| Median | 0.343040 ms | 0.287744 ms |
| p95 | 0.378880 ms | 0.304128 ms |

The median batch interval is about 16% lower for this fixture. A preceding run
measured approximately 14% lower (0.343040 versus 0.294912 ms). This is one
controlled shader workload on one GPU/driver, not a Minecraft FPS result, a
representative occupancy estimate or a universal speedup. No performance threshold
is asserted by the test. Synchronous readback/query waiting occurs only in this
explicit test; production timing remains asynchronous and nonblocking.

## Integrated-client world and lifecycle acceptance

```powershell
.\gradlew.bat runClient '-PwageAcceptanceTest=true' '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

Passed in 2 minutes 25 seconds with exit 0 and the final marker:

```text
[WAGE-ACCEPT] PASS optimizedAdmissions=2695 reload=true resize=true unload=true
```

The profile requires a copied disposable world at
`.codex-build/run-wage-acceptance/saves/wage-acceptance/level.dat`; it refuses the
normal game directory. The completed server-smoke world was copied there before
launch. For a fresh local profile, prepare the options with the existing helper:

```powershell
node --input-type=module -e "import { prepareClientOptions } from './.github/scripts/ci-smoke.mjs'; prepareClientOptions('.codex-build/run-wage-acceptance');"
```

The opt-in harness uses normal Minecraft load/reload/resize/save APIs. World
controls execute on the integrated server thread, using the existing cinematic
and weather owners. It captures a disabled baseline, enables WAGE, requires real
optimized cloud admissions, reloads resources, requires fresh post-reset
admissions, resizes the framebuffer, requires fresh admissions again, then uses
vanilla's disconnect sequence and checks policy reset after world unload.

| Stage | Admissions since the last reset | Framebuffer | Policy average / p99 |
| --- | ---: | --- | --- |
| Disabled baseline | 0 | 1024 x 576 | 8.50 / 18.40 us |
| Enabled | 2,695 | 1024 x 576 | 9.58 / 43.90 us |
| After resource reload | 1,298 | 1024 x 576 | 12.28 / 23.30 us |
| After resize | 1,070 | 960 x 540 | 0.93 / 5.10 us |

There were zero timer failures in those snapshots. Policy cost uses the last
240 observations, not a whole-run statistic or a universal overhead bound.
The first acceptance run exposed roughly 30 ms policy-tail observations while
unused system/CPU metrics were read. The final pressure-only sampler removes
those calls; the completed repeat produced the microsecond windows above.

Baseline/enabled/reloaded/resized screenshots were captured under the isolated
profile's `screenshots/` directory and visually inspected. Weather, wind and
cloud coverage continue changing; these views check scene/lifecycle continuity,
not same-frame in-world pixel equivalence or a causal FPS comparison. The first
harness unload missed `level.disconnect()`; a thread snapshot and vanilla's
pause-screen flow identified that omission, and the final normal shutdown passed.
The incomplete fixture/log were preserved separately. No save-engine fix was made.

Log: `.codex-build/wage-pr-world-acceptance-final.log`. Normal gameplay never
collects these test samples, mutates test settings or stops automatically.
These client traces preceded the upstream coastal merge, which changed no WAGE
renderer/harness source. Full API, subsystem and driver tests were rerun after
that merge; no additional in-world acceptance is inferred from the merge itself.

## Startup and dedicated-server smoke

```powershell
.\gradlew.bat runClient '-PciSmokeTest=true' '-PhideSmokeWindow=true' '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
.\gradlew.bat runServer '-PciSmokeTest=true' '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

Client: passed in 2 minutes 10 seconds, exit 0. It rendered TitleScreen without a
loading overlay, logged `CLIENT_READY`, rendered for 60 seconds, logged
`CLIENT_STOP_REQUESTED` and exited normally. The earlier foreground attempts
that stopped before the marker are not counted as successful soaks.

Dedicated server: passed in 5 minutes 22 seconds, exit 0. A new loopback-only flat
`wage-smoke-world`, ephemeral port and view/simulation distance 2 still exercised
the required starter-bunker creation hook. It reached `Done (83.876s)`, answered
`list`, ran 188 seconds after readiness, accepted `stop`, saved every dimension
and exited normally. This empty-server scope does not cover player login,
long-duration multiplayer or the normal terrain generator. Nonfatal dependency,
flat-generator and polar-biome warnings were observed; this is not a warning-free
modpack acceptance claim.

Logs: `.codex-build/wage-pr-startup-soak.log` and `wage-server-smoke.log`.

## Compatibility and rollout limits

The native NeoForge/dev-dependency client, integrated server and empty dedicated
server were exercised. Optional-renderer/shader ownership and unavailable APIs
have conservative source/unit-test gates. Sodium/Embeddium, active Iris/Oculus
packs, AMD/Intel GPUs, other drivers, Fabulous targets, repeated dimension cycles,
long sessions and multiplayer remain separate acceptance combinations.

Keep WAGE disabled by default. To consider changing that default, collect repeated
fixed-seed/location/camera/weather A/B runs with identical settings and alternating
order; measure whole-run median/p95/p99 frame cadence, tail lows, CPU/GPU/cloud
intervals, policy cost and memory, and inspect cloud edges, water/other effects,
lightning, atlas transitions and UI composition. Reject pacing/visual regressions
regardless of mean speed. Debug windows are measurement aids, not full captures.

Independent production review found no confirmed critical/important defects.
Follow-up acceptance review identified the fresh-admission assertion gap, which
was corrected. Its remainder hit the account limit; final harness/pressure-path
changes have implementation-owner review plus the executable evidence above.

This completes the feasible experimental cloud slice. Spatial upscaling, native
DLSS/frame generation, terrain compute and Vulkan remain behind the concrete
renderer contracts in `wage-renderer-roadmap.md`; no unavailable provider or
nonfunctional configuration is shipped. See `wage.md` for architecture and
`wage-stabilization.md` for the completed hardening changes.
