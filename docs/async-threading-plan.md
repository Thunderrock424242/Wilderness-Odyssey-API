# Multithreaded Task System Proposal

This document outlines how to add an optional, modpack-friendly multithreaded task system to Wilderness Odyssey (NeoForge). It focuses on keeping world mutations safe on the main thread while offloading heavy calculations to worker threads.

## Why this helps
- **Faster modpacks:** Expensive CPU work (pathfinding caches, structure scanning, AI heuristics, compression) can run in parallel, shortening stalls during worldgen and gameplay.
- **Compatibility-first:** World/registry mutations still occur on the logical server thread, avoiding cross-mod desyncs. Other mods can opt in without being forced to adopt threading.
- **Predictable performance:** Bounded executors and queue caps prevent runaway tasks; telemetry exposes queue depth and timing so pack makers can tune settings per machine.
- **Graceful opt-out:** Config toggles let players disable threading if another mod handles it or if hardware is constrained.

## Design goals
- Keep world mutations deterministic and main-thread only.
- Make the system lifecycle-aware (start on server load, shut down on stop/unload).
- Provide small, immutable payloads for main-thread application to reduce lock contention.
- Expose sizing/toggles via common config so pack authors can tune per instance.

## Architecture
- **Executors:** Bounded `ThreadPoolExecutor` with named daemon threads; default pool size `max(1, cores - 1)`.
- **Task submission:** Helper methods for CPU-bound and IO-bound tasks. Tasks wrap results into immutable DTOs.
- **Main-thread queue:** Thread-safe queue drained from a `ServerTickEvent` handler to apply deterministic world mutations.
- **Cancellation:** Outstanding tasks cancelled when the server or world stops; tasks carry world identifiers for validity checks.
- **Error handling:** Tasks log failures and surface counters for completed/failed tasks and queue depth.

## Config surface (suggested)
- `enabled`: master switch for the async system.
- `maxThreads`: pool size (bounded to hardware).
- `queueSize`: backpressure limit; rejected tasks log a warning and return a failure status.
- `taskTimeoutMs`: optional timeout for long-running operations.
- `logLevel` / `telemetry`: enable debug logging or a simple `/asyncstats` command.

## Integration points
1. Initialize the async manager on server start events; shut it down on stop/unload.
2. Subscribe to server tick events to drain and apply main-thread tasks.
3. Provide a small API (e.g., `AsyncTaskManager.submitCpuTask`/`submitIoTask`) so other modules or external mods can opt in.
4. When submitting work, snapshot read-only data (positions, block states, config values) instead of touching live world state off-thread.
5. Apply results on the main thread via the drain loop, keeping mutations minimal and deterministic.

## Observability and modpack guidance
- Expose queue length, active threads, and average task duration; log rejections and timeouts for tuning.
- Document compatibility notes: avoid accessing entities/capabilities off-thread; prefer immutable data and deterministic random seeds.
- Provide a wiki/README link for pack authors so they can tune thread counts and disable the system if another mod already parallelizes similar work.

## Expected impact for a PR
Implementing this design would ship an opt-in async subsystem that improves frame pacing and worldgen throughput on multi-core machines while remaining safe for modpacks. It would also help modders by offering a clear API surface for heavy computations without risking world corruption.

## Current wiring in the mod
- Config file: `config/wildernessodysseyapi/wildernessodysseyapi-async.toml` (enable/disable, pool size, queue limits, per-tick application cap, timeouts, debug logging).
- Runtime hooks: executors are started on server boot and shut down on stop; main-thread tasks are drained each server tick.
- Public entry points: `AsyncTaskManager.submitCpuTask`/`submitIoTask` for worker execution and `MainThreadTask` for safe server-thread mutations.
- Diagnostics: `/asyncstats` prints worker usage, queue depth, and rejection counts for operators.
