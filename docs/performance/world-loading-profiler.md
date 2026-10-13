# World-loading diagnostics

Wilderness Odyssey automatically samples long client/world loads. No chat command or Spark installation is required. It covers vanilla world-data reads, world-resource preparation, world creation preparation, spawn generation, terrain reception, progress screens, and resource-loading overlays.

The loading diagnostics panel is hidden by default. Automatic sampling and local reports remain enabled. When explicitly enabled, the panel shows elapsed time, the current stage, spawn percentage when available, the time since the visible stage/percentage last changed, and frequently sampled active code on recognized loading screens. It appears after the first worker sampling pass. Resource overlays are monitored even when they cover the underlying panel.

## Five-minute warning and ten-minute world failure

Initial world loading has a wall-clock deadline, measured with the monotonic JVM clock across world-data/resource preparation, integrated-server startup, spawn generation, and initial terrain reception. Stage changes, percentage changes and continuing client frames do not restart it. Launcher resource loading alone, generic progress/save screens and dimension travel within an already open world do not arm this deadline. A resource overlay appearing during an already armed world load keeps the deadline running.

At five minutes, a one-time WARN in `logs/latest.log` includes the current stage, visible progress, sampled thread stacks and the bounded loading diagnostic report. A report is written even if the optional periodic-report threshold is longer than five minutes. At ten minutes, an ERROR records the failure and another diagnostic snapshot. These checks run on the existing independent worker even without further client ticks or frames.

The client fails the loading operation at its next safe cancellation point and presents **Failed to load world** with a **Back to Title Screen** button. The message says:

> The world took longer than 10 minutes to load. Try creating a new world, or contact the modpack developers and submit an issue report. Include logs/latest.log and the loading report from logs/loading-stalls.

Integrated-server startup exits before opening its local connection. A cooperative server stop request unwinds spawn preparation at a native server wait boundary. Native server cleanup still owns saving, chunk I/O, resource closure and releasing the world lock. The timeout never deletes a save. World selection/creation and multiplayer entry are blocked while this cleanup is still running, preventing overlapping server and registry lifecycles; the failure screen explains the wait. Ordinary server crashes retain their normal crash behavior.

This is not forced thread termination. A JVM/process crash or a third-party method permanently blocking the render thread cannot display a new screen. Early synchronous data/resource work must return to Minecraft before its timeout screen can be presented. A server permanently blocked outside its cooperative wait boundary may keep the save locked; the menu stays available after the client startup wait is cancelled, but another world must wait for cleanup or a Minecraft restart. The independent diagnostics remain useful for an issue report. No unsafe background world writes, thread stopping or premature world-lock release are used.

## Finding a report

Inside the launcher's Minecraft instance folder, open:

```text
logs/loading-stalls/loading-profile-<timestamp>-<session>.log
```

A load still in progress produces its first report after one minute by default. The same file is replaced about once per minute, and updated once more when loading finishes or is cancelled. Completed loads lasting at least ten seconds also produce a report. The newest ten profiler reports are retained; legacy `loading-stall-*.log` files are untouched. `logs/latest.log` records the full report path and any write failure.

Reports contain observed stage durations, visible progress, client heartbeat age, sampled code sites, separate runnable and waiting/blocked counts, measured thread CPU increases where the JVM supports them, recent thread stacks, and loaded mod IDs/versions. A rising heartbeat age means the client has stopped publishing screen observations; the independent worker can continue sampling and writing.

## Reading attribution

Start with the Server thread, Render thread, and generation workers. Compare repeated reports and look for increasing CPU time together with repeatedly sampled code sites. A waiting Server thread may be waiting for work on a generation worker; inspect both stacks.

The nearest identifiable mod frame in a sampled stack is a **suspect**, not a proven cause. Counts describe sampled thread observations, not percentages of total load time. The RUNNABLE JVM state does not prove CPU consumption. Waiting samples are kept separate and CPU time is reported by thread, not assigned to mods. Mods sharing a module are named together. Unknown ownership remains unresolved. Mixin code inside transformed Minecraft methods can remain attributed to Minecraft.

This client can see an integrated single-player server's threads. It cannot profile generation happening on a separate multiplayer server. Screens replaced completely by another mod may not be recognized. JVM sampling cannot diagnose a hang that occurs before the client event bridge has armed, or recover diagnostics after a JVM/process crash.

## Optional launcher JVM arguments

```text
-Dwilderness.loadingstall.showPanel=true
```

This opts into the loading diagnostics panel. Omit this argument or set it to `false` to keep the panel hidden. Restart Minecraft after changing JVM arguments. Panel visibility does not change automatic sampling or report collection.

```text
-Dwilderness.loadingstall.minutes=5
```

This retains the old threshold override, now bounded to 1–60 minutes. Integer minutes, tick values such as `2400t`, and colon times such as `1:30` are accepted; thresholds round up to whole minutes. The default was reduced from five minutes to one minute. Reports for completed loads of at least ten seconds are independent of this threshold.

```text
-Dwilderness.loadingstall.enabled=false
```

This disables the diagnostic worker, loading panel and five/ten-minute world-loading guard for an overhead comparison. The guard is enabled by default and is independent of panel visibility and the periodic-report delay.

## Ownership and overhead

`LoadingStallDetector` is registered only on the client. Screen/tick/frame events publish immutable stage/progress observations at most four times per second during normal polling; screen transitions publish immediately. The worker never dereferences Minecraft screens, worlds, chunks, players, or entities.

The server-lifetime shared async pools are unavailable during early world loading, so this diagnostic has one client-lifetime daemon worker. It uses a single fixed-delay task, collects stacks only during recognized loads, and stops at client shutdown without blocking the game thread. Sampling normally runs once per second, with at most 128 captured stacks of 48 frames per pass. History is capped at 128 threads, 256 sites, and 16 stages; exclusions are reported. Reports include at most 32 recent stacks. Saved files are replaced via a temporary sibling file and atomic move where supported.

The client-only `LevelLoadingScreen` accessor reads spawn progress because screen events have no public percentage getter. The client startup mixin wraps `doWorldLoad` and cancels only its progress-listener/spawn-readiness waits or rejects a load before server ownership transfers. Screen events cannot unwind those synchronous startup loops. An integrated-server mixin suppresses the whole-client delayed crash only for our timeout exception; the native server crash report and cleanup still run. A common server mixin mirrors the base server's volatile stop request and checks for cancellation at `waitUntilNextTick`; `IntegratedServer.halt(false)` is unsuitable here because it first blocks on queued server work. The stop check does not abort native final saving. No client classes are referenced by the common mixin, and no dedicated-server watchdog is introduced.

## Validation

```powershell
.\gradlew.bat loadingProfilerTest -PcodexBuildDir=.codex-build --no-parallel
.\gradlew.bat build -PcodexBuildDir=.codex-build --no-parallel
```

The focused tests cover stage/progress tracking, ownership, waiting versus runnable samples, CPU counter availability/reset, history bounds, threshold parsing, independent saving without client heartbeats, five/ten-minute diagnostic reports, total deadline preservation across phases/progress/overlays, cancellation/retry isolation, launcher/dimension exclusions, report retention, write failures, and shutdown flushing/termination. Compilation and these tests do not establish live timeout-screen or native shutdown behavior.

Live acceptance remains separate from unit tests and packaging:

1. Install the regular mod JAR in a disposable launcher profile and create a world. Confirm the loading panel is hidden by default. Restart with `-Dwilderness.loadingstall.showPanel=true` and check the panel and spawn percentage, including small windows/high GUI scale.
2. Reproduce a load lasting over a minute. Check the report while the screen is still open and compare the next update. Confirm it names actual mod modules or explicitly shows unresolved ownership.
3. Let the world open, cancel a load, disconnect, and start a second world. Confirm sampling ends and reports do not carry the previous operation's stage/progress into the new one.
4. Reproduce a blocked client thread after loading has armed. Check that heartbeat age rises and the local report continues updating. A frozen render thread cannot refresh the panel.
5. Compare equivalent loads with diagnostics disabled. Measure overhead before making a performance claim.
6. Check dedicated-server startup separately: the client event bridge and client accessor must remain unloaded.
7. In a disposable launcher profile, reproduce an initial world load lasting five minutes, then ten minutes. Check the one-time warning and its stacks at five minutes, the error/report at ten minutes, the failure message and return-to-title button, and that another world can load after native server cleanup finishes. Check a stall before the spawn-progress listener is published as well as spawn generation and initial terrain reception. Preserve the report and test a second load to confirm its timer starts fresh.
8. During a timed-out server's delayed shutdown, try world selection and multiplayer entry. Confirm the waiting message appears until the native server thread exits. Check that normal completed loads, dimension travel, resource reloads and Save & Quit retain their existing behavior. Client freezes outside the cancellation boundaries require a separate blocked-thread acceptance check; do not infer recovery from the clock/report unit tests.
