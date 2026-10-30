# Live water acceptance record

Date: 2026-10-29. Follows the [controlled storm checkpoint](storm-runoff-checkpoint.md).
Status: partially verified; native runtime failures block the complete client/multiplayer/performance pass.

Source: HEAD `20f903dc` plus the existing shared uncommitted water changes.
This was development-source validation, not a published release.

## Test setup

Use the existing dirty checkout and normal NeoForge `runClient`, Minecraft
1.21.1 / NeoForge 21.1.262, with 23 installed development mods. The disposable
game directory is `.codex-build/run-water-client-20261029-a`; its world is
`Water acceptance 2026-10-29`, Creative, default generated terrain, seed
448188211. Water/weather settings retain their ordinary defaults.

Client settings: render/simulation distances 8/6 chunks, 60 FPS cap, VSync on,
GUI scale 2, music muted. Automatic water quality selected CINEMATIC on an
NVIDIA RTX 4090 Laptop GPU with 16 GiB dedicated memory, i9-14900HX, 31.7 GiB
physical RAM and approximately 7.9 GiB maximum heap. No external shader pack
was selected. These facts describe this machine and do not establish performance.

The ignored init script supplies isolation and a launch-only JVM diagnostic
setting; production build/JVM defaults are unchanged:

```powershell
.\gradlew.bat runClient '-PcodexBuildDir=.codex-build' --no-parallel --init-script .codex-build/water-client-acceptance.init.gradle
```

## Actual observations

The first invocation entered generated terrain and rendered shoreline water.
The ordinary starter sequence ran. `/locate biome minecraft:river` found the
river near 657/-988. The existing teleport adapter selected an underground
destination, so `/spreadplayers 657 -988 0 32 false @s` moved the test player
to the surface. No teleport source changes were made. The user stopped UI
control before the first budget sample and deferred the work until after class.
The world then saved all dimensions and the client exited normally; Gradle
reported success after 10m24s. This is startup/world/render/save evidence,
not exact wet-state persistence or storm accounting acceptance.

The Corretto invocation reopened that same saved world through the normal
quick-play/integrated-server path. Two shoreline samples were ocean-boundary
regions, with matching ocean-input/ET receipts and zero regional residual.
They were not treated as finite inland runoff proof. Read-only inspection of
the normally saved regional catalogue identified the nearby finite river.

At 18:44:45 EDT, `/wowater budget 648 64 -984` reported region [40,-62],
last simulated tick 9768, forcing tick 9770, downstream [39,-63], zero regional
storage/receipts and zero residual. The visible generated water remains a
separate baseline owner; dry regional storage does not mean it disappeared.
The runtime reported 1089 admitted regions and bounded hydrology/river work.

At 18:45:25, `/wilderness weather force rain` was accepted across nine
atmosphere cells. The client crashed natively before the following weather
sample completed. Rain acceptance alone does not prove precipitation reached
regional storage. This debug preset also does not prove spontaneous storm
formation.

## Native runtime failures and diagnostic boundary

| Invocation | JVM and setting | Result |
| --- | --- | --- |
| A | Temurin 21.0.6, C1 only | Startup, generated-world entry, visible water and normal save/exit; interrupted before budget testing. |
| B, PID 26776 | Temurin 21.0.6, C1 only | Native `EXCEPTION_ACCESS_VIOLATION`, `RuntimeStub::ic_miss_stub`, resource worker; failed before world entry, 32s. |
| C, PID 42776 | Installed Corretto 21.0.13, C1 only | Loaded saved world and sampled budgets; native `RuntimeStub::slow_subtype_check` crash on render thread; Gradle failed after 4m47s. |
| D | Corretto 21.0.13, interpreter only (`-Xint`) | Resource reload and diagnostic rain/soil check completed; wet state retained on reload; normal save/exit, Gradle passed in 32m9s. |

The reports do not establish a Java water-code exception or a proven underlying
JVM/hardware cause. The new JDK was already installed and selected through
Gradle's JavaLauncher; no system Java default, permissions, security settings,
global caches or dependency JARs were changed. Interpreter-only execution tests
whether bypassing JIT helps functional progress; its speed cannot certify
normal gameplay performance. Crash reports and logs remain local under the
isolated game/build directories.

## Interpreter isolation observations

With the default workload, interpreter SPH cost was about 190 ms per tick.
The river remained at saved tick 6608, despite an actual RAIN 0.900 atmosphere
sample. `/wo dataengine stats` reported zero submitted/processed updates across
all systems at 21:02:07. These unchanged dry readings were not counted as a
rain-accounting pass. Source tracing found the ordinary live time allowance
gates scheduling; no scheduler source changes were made for the artificial
interpreter load.

Diagnostic changes applied only to this isolated profile: SPH particle work
budget 900 to 0 (owned bodies retained), target tick rate 20 to 5, and watershed
record budget 6 to 64. The normal scheduler and saved catalogue remained the
owners. These are not default-setting performance results. Restore all three
settings after the experiment.

F3+T resource reload completed at 21:09 and shoreline water returned. At
21:12:18, the same finite river record had advanced to tick 11808 with forcing
tick 11827 and reported:

| Quantity | Milli-units |
| --- | ---: |
| Precipitation receipt | 2,449,895 |
| Soil storage | 2,441,753 |
| Evaporation receipt | 8,142 |
| Regional residual | 0 |

No runoff, groundwater, routed discharge or detailed flood was measured in this
short unsaturated-soil case. The input reached the existing regional owner
through real weather coupling; it was not credited directly by a test probe.
`/wilderness weather clear` cleared nine cells afterward. This demonstrates
functional coupling in the diagnostic setup, not a new open-world flood or
spontaneous storm certificate.

Normal Save & Quit finished saving all dimensions at 21:14:03. A read-only copy
of the native regional file (`.codex-build/water-live-d-wet-regional.dat`) retained
exactly the table's soil/precipitation/ET values and tick 11808. It is evidence
only and was never restored into gameplay state. A normal UI reload followed.

At 21:21:09 after that normal reload, the same region's first budget sample
matched the saved tick, forcing time, wet soil and both receipts exactly, with
zero residual. This is a sampled integrated reload check; it does not establish
every canonical attachment field or immediate post-load catch-up. The SPH and
watershed budgets were restored to 900/6 and read back. The integrated server
saved all dimensions again at 21:24:14, then Quit Game ended the process normally;
Gradle reported success after 32m9s. No altered diagnostic tick target remains
running. This duration includes loading, reload and disconnected intervals and
is not a 30-minute gameplay/performance soak.

The desktop text-entry connection became unreliable. Unexpected input was
cleared without submission; resetting the control kernel temporarily restored
it. One tagged cod was summoned for an underwater camera check, but the
spectate command was not submitted. Underwater/swimming/disturbance acceptance
therefore remains incomplete. The tagged fish is confined to the disposable
world; no production code was changed.

## Remaining acceptance

Rain-to-soil receipts/storage, resource reload and sampled wet Save & Quit/reload
were observed in the diagnostic configuration above. Default-runtime acceptance,
positive river runoff/discharge/flood, immersion, swimming and disturbances remain
incomplete. Spontaneous weather formation, external shaders, freezing, historical-
world migration and a representative 30-minute performance soak remain separate
checks.

The user explicitly approved an exception to the repository's one-process rule
for this one localhost server plus two isolated clients, each capped at 4 GiB
and stopped after testing. The ignored init script's three normal NeoForge
profiles passed a Gradle configuration check (`help --task
runWaterAcceptanceServer`, 1s). The normally stopped generated world was copied
to the isolated server directory after verifying both absolute paths remain
inside `.codex-build/` and refusing an existing destination. No expected ledger
snapshot was restored as gameplay water. All three profiles were then launched.
All three profiles use interpreter mode and 4 GiB JVM caps; this copied server
uses SPH work budget 0 / watershed budget 64 to isolate networking from the
interpreter load. Native-tier performance remains environment-blocked.

```powershell
.\gradlew.bat runWaterAcceptanceServer '-PcodexBuildDir=.codex-build' --no-parallel --init-script .codex-build/water-multiplayer-acceptance.init.gradle
.\gradlew.bat runWaterAcceptanceAlice '-PcodexBuildDir=.codex-build' --no-parallel --init-script .codex-build/water-multiplayer-acceptance.init.gradle
.\gradlew.bat runWaterAcceptanceBob '-PcodexBuildDir=.codex-build' --no-parallel --init-script .codex-build/water-multiplayer-acceptance.init.gradle
```

They use distinct profile/game directories and development names, loopback
127.0.0.1:25578, and the existing build pipeline.

## Multiplayer attempt and cleanup

The dedicated server reported ready at 21:30:58, bound to 127.0.0.1:25578, and
accepted the normal console command `tick rate 5` at 21:31:39. Separate real
Alice and Bob client processes used distinct development names. Both reached
the server's configuration phase, then timed out (Alice 21:34:34; Bob 21:36:12).
The server's `list` command confirmed zero of two players online at 21:36:34.
Neither reached a completed gameplay join. This is a failed connection attempt,
not multiplayer water synchronization or reconnect acceptance.

Alice then crashed at 21:36:21 (PID 19036) with native
`EXCEPTION_ACCESS_VIOLATION` in `glfw.dll+0x101ab`. Its report confirms interpreter
mode. The Java stack enters `GLFW.glfwPollEvents` from Minecraft's normal render
loop. Thus interpreter mode did not resolve every native failure. The evidence
does not establish a water-source defect or the underlying JVM, windowing,
driver, desktop-control or hardware cause. No speculative production repair,
dependency replacement or machine/security change was made.

The dedicated startup also logged a nonfatal invalid-side attempt to load
`TextureSheetParticle`; startup continued to ready. Its ownership was not proven,
and this pass does not certify a clean optional-mod compatibility profile.

After the native failure, further connection retries stopped. The dedicated
server accepted `stop`, saved all dimensions and exited normally; Gradle passed
in 11m2s. Bob displayed Connection Lost, then exited through its normal window
close; Gradle passed in 7m4s. Alice's Gradle task failed after 6m with the native
process exit. These successful server/Bob task exits mean cleanup completed,
not that multiplayer passed. All three Minecraft test processes exited. The
copied server's water budgets were restored to 900/6 after stop; the explicitly
selected diagnostic init script sets 0/64 again only on a future test launch.

No production Java or resource changes were made during this acceptance pass.
No new JUnit suite was needed for documentation and ignored local launch helpers.
The prior storm checkpoint's unit/GameTest/build results remain its own record;
they are not a substitute for the blocked live checks here.
The documentation/profile whitespace and conflict checks and `git diff --check`
passed. Existing source changes were preserved; no commits or remote actions
were performed.

Local evidence: `water-live-client-a.log` through `water-live-client-d.log`,
`water-multiplayer-profile-check.log`, `water-multiplayer-server.log`,
`water-multiplayer-alice.log`, `water-multiplayer-bob.log`, and the native crash
reports in their isolated game directories. Logs, worlds, launch helpers and
assertion copies remain ignored local output.

The next acceptance run needs a stable native client/runtime environment before
default settings, two completed gameplay joins, reconnect, client-state agreement
and representative performance can be certified. The one-test concurrency
exception applies only to this acceptance test; cleanup is complete and the
ordinary one-process rule still applies to other repository work. The user's
authorization remains recorded for continuation of this same test scope.
