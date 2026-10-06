# World-loading diagnostics

Wilderness Odyssey automatically samples long client/world loads. No chat command or Spark installation is required. It covers vanilla world-data reads, world-resource preparation, world creation preparation, spawn generation, terrain reception, progress screens, and resource-loading overlays.

On recognized loading screens, a small panel shows elapsed time, the current stage, spawn percentage when available, the time since the visible stage/percentage last changed, and frequently sampled active code. It appears after the first worker sampling pass. Resource overlays are monitored even when they cover the underlying panel.

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
-Dwilderness.loadingstall.minutes=5
```

This retains the old threshold override, now bounded to 1–60 minutes. Integer minutes, tick values such as `2400t`, and colon times such as `1:30` are accepted; thresholds round up to whole minutes. The default was reduced from five minutes to one minute. Reports for completed loads of at least ten seconds are independent of this threshold.

```text
-Dwilderness.loadingstall.enabled=false
```

This disables the diagnostic worker and loading panel for an overhead comparison.

## Ownership and overhead

`LoadingStallDetector` is registered only on the client. Screen/tick/frame events publish immutable stage/progress observations at most four times per second during normal polling; screen transitions publish immediately. The worker never dereferences Minecraft screens, worlds, chunks, players, or entities.

The server-lifetime shared async pools are unavailable during early world loading, so this diagnostic has one client-lifetime daemon worker. It uses a single fixed-delay task, collects stacks only during recognized loads, and stops at client shutdown without blocking the game thread. Sampling normally runs once per second, with at most 128 captured stacks of 48 frames per pass. History is capped at 128 threads, 256 sites, and 16 stages; exclusions are reported. Reports include at most 32 recent stacks. Saved files are replaced via a temporary sibling file and atomic move where supported.

The sole new mixin is a client-only read accessor for `LevelLoadingScreen`'s existing progress listener, because screen events have no public spawn-percentage getter. It does not modify generation, chunk tickets, loading order, or gameplay state.

## Validation

```powershell
.\gradlew.bat loadingProfilerTest -PcodexBuildDir=.codex-build --no-parallel
.\gradlew.bat build -PcodexBuildDir=.codex-build --no-parallel
```

The focused tests cover stage/progress tracking, ownership, waiting versus runnable samples, CPU counter availability/reset, history bounds, threshold parsing, independent saving without client heartbeats, cancellation/new-session isolation, report retention, write failures, and shutdown flushing/termination.

Live acceptance remains separate from unit tests and packaging:

1. Install the regular mod JAR in a disposable launcher profile and create a world. Check the loading panel and spawn percentage, including small windows/high GUI scale.
2. Reproduce a load lasting over a minute. Check the report while the screen is still open and compare the next update. Confirm it names actual mod modules or explicitly shows unresolved ownership.
3. Let the world open, cancel a load, disconnect, and start a second world. Confirm sampling ends and reports do not carry the previous operation's stage/progress into the new one.
4. Reproduce a blocked client thread after loading has armed. Check that heartbeat age rises and the local report continues updating. A frozen render thread cannot refresh the panel.
5. Compare equivalent loads with diagnostics disabled. Measure overhead before making a performance claim.
6. Check dedicated-server startup separately: the client event bridge and client accessor must remain unloaded.
