# Water live acceptance plan

> Execute inline in the existing checkout. The main agent owns UI, review and serial Gradle execution. The user approved the next client, multiplayer, natural-storm and performance acceptance pass; repository limits override routine delegation and commit prompts.

**Goal:** Collect actual client/world evidence after the controlled storm checkpoint, repair only reproduced failures, and distinguish passed checks from remaining acceptance.

**Architecture:** Use the existing `runClient`, normal integrated-server lifecycle, generated terrain, water/weather commands and render owners. An ignored init script isolates the game directory and applies the already documented native-JVM C1 diagnostic setting. No new water authority or test scheduler.

**Spec:** `docs/watersystem/testing.md` W01, W04, cinematic/lifecycle cases, W13 and W14; the user's request to do the next checks.

## Constraints and review focus

- Preserve the existing dirty checkout; no commits, dependencies or security changes.
- One Codex-controlled Gradle/Minecraft process at a time, with the user's explicit exception for this one localhost server/two-client test only. Finish single-client work first and stop all three after testing.
- Use `.codex-build/run-water-client-20261029-a`, JDK 21, the wrapper and `--no-parallel`; create only a disposable world there.
- Retain default water/weather ownership. Record changed client presentation settings and forced weather. Forced weather validates runtime coupling, not spontaneous storm formation.
- Compare command budgets at actual game ticks. Do not equate visible generated water with regional inventory or claim a global open-world budget from a single sample.
- Screenshot/visual observations do not prove exact volume, multiplayer agreement, external shader compatibility or representative performance.
- Diagnose any failure before changing source. Add a useful regression when practical; validate the owning subsystem serially.

## Steps

- [x] Start the isolated client and record JVM, graphics, installed mods and world settings.
- [x] Create generated terrain, find natural water, observe initial/advanced regional budgets and client rendering.
- [ ] Sample real weather authority and precipitation/soil/runoff receipts during rain, then clear and observe recovery.
- [x] Check resource reload and sampled integrated wet Save & Quit/reload in the diagnostic configuration.
- [ ] Complete immersion, swimming and disturbance presentation; the camera command was not submitted reliably.
- [x] Prepare and validate concrete dedicated/two-client commands, obtain the required one-test concurrency exception, attempt the test and stop all three processes afterward.
- [ ] Complete two gameplay joins, client-state agreement and reconnect; native failures/timeouts blocked this attempt.
- [ ] Run a representative 30-minute gameplay/performance soak on a stable native runtime. Diagnostic process duration includes loading and disconnects and is not a soak pass.
- [x] Review evidence, repair only proven source failures, and update the water acceptance report/testing guide with exact results and limits. No water source failure was established; native-runtime blockers remain explicit.

## Execution ledger

Preflight found no running development JVM. The existing smoke probe only
certifies startup and auto-exits, and the WAGE probe targets clouds in a separate
copied world; neither is water acceptance. Use the ordinary client with an
isolated working directory, visible window and native-JVM diagnostic flag.
No production code changes are planned unless runtime evidence identifies a bug.

The first client entered a fresh Creative default-terrain world with seed
448188211 and 23 installed mods. Automatic quality selected CINEMATIC on an
RTX 4090 Laptop GPU, i9-14900HX, 31.7 GiB physical RAM and 7.9 GiB heap;
Temurin 21.0.6 uses the session-only C1 diagnostic setting. Render/simulation
distances are 8/6 chunks, 60 FPS cap, VSync enabled. Natural shoreline water
was visible after locating the river near 657/-988 and using spreadplayers to
reach the surface. The ordinary teleport safety adapter had selected an
underground target; no unrelated teleport change was made.

The user stopped computer control with Escape before the first budget sample,
then deferred until after class. No water acceptance was claimed from that
interruption. The client subsequently saved all dimensions and exited normally
(`BUILD SUCCESSFUL in 10m24s`). On resume there is no development JVM. Reopen
the same saved world with the supported quick-play argument; retain the first
log as evidence and write this invocation to water-live-client-b.log.

Resume B failed before world entry (PID 26776), with native
EXCEPTION_ACCESS_VIOLATION in HotSpot RuntimeStub::ic_miss_stub on a resource
worker. This happened on Temurin 21.0.6 despite the existing C1 diagnostic flag;
it does not establish a water-source defect or a quick-play argument failure.
An installed Amazon Corretto 21.0.13 is available. Select the Amazon JDK 21
through the normal Gradle JavaLauncher for this ignored client profile only,
retain C1, and perform one controlled runtime retest. No JDK installation,
system Java default, security setting or production build default changes.

Prepared an ignored multiplayer init script with separate normal NeoForge
server/Alice/Bob profiles, isolated directories, distinct development names,
4 GiB JVM caps and loopback port 25578. Proposed commands are
`runWaterAcceptanceServer`, `runWaterAcceptanceAlice` and
`runWaterAcceptanceBob`, each with `'-PcodexBuildDir=.codex-build' --no-parallel
--init-script .codex-build/water-multiplayer-acceptance.init.gradle`.
At this point the profile was prepared but had not yet been Gradle-validated or
launched. The user granted the concurrency exception later, as recorded below.

Corretto resume C loaded the saved shoreline successfully (PID 42776). Native
saved-catalogue inspection found a finite RIVER record at chunk [40,-62]; the
first two shoreline samples were correctly identified as ocean boundaries and
were not used as finite runoff proof. At 18:44:45, the finite river at
648/64/-984 was dry with zero receipts/residual, last simulated tick 9768 and
forcing tick 9770, downstream [39,-63]. The localized force-rain command was
accepted across nine atmosphere cells at 18:45:25. Before the next weather
sample completed, Corretto crashed in RuntimeStub::slow_subtype_check on the
render thread. This is native failure evidence, not a verified water defect.
Gradle exited failed after 4m47s and no development JVM remained.

Ruling: perform one interpreter-only (-Xint) diagnostic run on the same installed
JDK 21.0.13 and unchanged disposable world, bypassing JIT compilation. It may
permit functional observations but cannot certify performance. Do not keep
repeating native failures unchanged, alter machine security or install JDKs.

The user answered the concurrency question explicitly: "Allow this test only".
That authorizes the prepared localhost server plus Alice and Bob concurrently
for this test, with their 4 GiB caps and shutdown afterward. No further
concurrency permission is required for that scope. Functional client stability
still precedes launching the three-process test.

Interpreter D entered the saved world at 20:57. The baseline river sample
(20:58:13) was still tick 6608/forcing 6630 and dry. Rain was accepted at
20:58:51; the actual atmosphere sample at 20:59:30 reported RAIN 0.900,
temperature 30.10 C and humidity 0.920. At 20:59:59 the river clock and stores
had not advanced. This is not a successful zero-budget rain test.

Read-only source tracing found WaterPerformanceIntegration uses the normal
Data Engine allowance, and DataEngine.tick returns before scheduling when the
live server allowance is exhausted. SPH cost was about 190 ms per tick under
the interpreter. `/wo dataengine stats` at 21:02:07 confirmed enabled but zero
updates submitted/processed across all systems; no update failures were logged.
No scheduler repair is justified from this artificially slow runtime alone.

Ruling: temporarily set the existing serverSphParticleTickBudget from 900 to 0
only in the isolated profile, to test whether deferring SPH work frees the
normal hydrology lane. The manager retains owned bodies and its zero-budget
path performs no particle advancement; do not clear/discard inventory. Restore
900 before finishing. Any result is an interpreter/paused-SPH isolation result,
not default-load or performance certification.

At 21:03:25 the zero SPH budget was confirmed (0 particles, 423 microseconds)
but the finite river remained at tick 6608. Removing that cost alone did not
prove recovery. At 21:04:14 the ordinary `/tick rate 5` command was accepted,
giving the interpreter a larger wall-time allowance without jumping game time
or calling hydrology directly. Restore the normal 20 TPS target before exit.
These setup changes are explicitly diagnostic, not a default-runtime pass.

The resource reload completed at 21:09 and water returned. The finite river at
21:12:18 had advanced to tick 11808 / forcing 11827: precipitation 2,449,895,
soil 2,441,753, ET 8,142 milli-units, residual zero. Runoff/groundwater/flood
remained zero in this short unsaturated-soil case and are not claimed as
tested rise/recession. Clear weather was accepted across nine cells.
Normal Save & Quit saved all dimensions at 21:14:03. The native regional file
retained those exact wet soil/receipt values; the evidence copy is
water-live-d-wet-regional.dat, never restored. A normal UI reload followed.

After that reload, the 21:21:09 budget matched the saved region's tick 11808,
forcing 11827, soil 2,441,753, precipitation 2,449,895 and ET 8,142 exactly,
with zero residual. This verifies retained wet state at the sampled boundary;
it is not an assertion of all-field chunk attachment equivalence or immediate
post-reload catch-up. Text injection became unreliable; unsubmitted input was
cleared, the control kernel reset and fresh window selection restored correct
text entry. No unverified command text was submitted. Restored the isolated
file settings to SPH budget 900 and watershed record budget 6 before final UI
checks and shutdown. An explicit rate-20 confirmation was still pending at this
point; final server shutdown and the remaining limits are recorded below.

Interpreter D saved all dimensions again at 21:24:14 and exited through Quit
Game normally at 21:24:29. Gradle passed after 32m9s. The restored file values
(SPH 900, chunks 6) were read back. An explicit rate-20 command could not be
submitted reliably, so the diagnostic server was closed; no altered running
tick target remains. One tagged cod was spawned for a camera check, but the
spectate command was not submitted because text input failed again. Camera/
swimming/disturbance acceptance therefore remains incomplete. The control
kernel reset temporarily restored typing; do not submit unverified text.

Multiplayer will use the normal wrapper/NeoForge profiles with the stable
interpreter setting and a copied, normally stopped generated world. Configure
only that copied server for SPH work budget 0 and watershed budget 64, then use
the supported console for operators, rate and weather commands. It is a
diagnostic multiplayer smoke test, not normal-tier performance certification.

The profile configuration check passed in 1s. The stopped generated world was
copied to the isolated server directory after absolute-path boundary checks and
refusing an existing destination. Server was ready on loopback at 21:30:58 and
accepted `tick rate 5` through its normal console. Alice and Bob each reached
configuration but timed out at 21:34:34 / 21:36:12. The server reported zero
players online; neither gameplay join nor client-state agreement was verified.

Alice crashed at 21:36:21 in glfw.dll+0x101ab, PID 19036, with interpreted mode
confirmed. The Java stack is the ordinary Minecraft render loop through
GLFW.glfwPollEvents. This does not establish the underlying source/JVM/windowing/
driver/desktop-control/hardware cause. Stop further retries rather than patching
water speculatively. A nonfatal TextureSheetParticle invalid-side startup log
was also retained without attributing ownership from adjacent library logs.

Cleanup complete: server accepted stop and saved all dimensions (Gradle passed
11m2s); Bob closed normally (passed 7m4s); Alice's task failed after 6m with its
native process exit. All three Minecraft processes exited. Restored copied-
server water budgets to 900/6 as well. No security, global runtime, dependency
or production-code changes. Default-runtime, multiplayer and representative
performance acceptance remain environment-blocked and explicitly unchecked.

Final review: documentation and local helper whitespace/conflict checks passed,
and git diff --check passed. Source HEAD remains 20f903dc with the prior shared
dirty water changes preserved. No production Java/resources, dependencies,
global Java defaults, permissions, security settings, commits or remote state
were changed in this live acceptance pass. Updated the main overview, checkpoint
and testing guide to link the partial results. The same-test concurrency
authorization remains recorded; it does not authorize unrelated parallel work.

At 21:05:09 the hydrology lane was running (24 coarse intervals and 1089 admitted
regions), but the selected river had not yet been revisited. Game time was
10633 at 21:06:31, so clock rollback was not assumed. Source inspection confirms
round-robin regional selection, six records per invocation, and a pressure-
governed water poll interval capped at 100 ticks. In this diagnostic setup the
full catalogue can therefore take a long time to revisit.

Ruling: raise only the existing isolated watershed chunksPerTick budget from
6 to its supported maximum of 64, retaining the same saved catalogue, scheduler,
owners and receipts. This is a work-budget experiment, not a new ticker or an
elapsed-time jump. Restore 6, SPH budget 900 and target 20 TPS before exit.
