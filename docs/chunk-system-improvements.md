# Chunk System Improvement Plan

This document captures an implementation plan for improving the chunk lifecycle, I/O, threading, and networking pipeline for the Wilderness Odyssey API when building with NeoForge and Gradle.

## Goals
- Reduce main-thread stalls by moving serialization, compression, and generation off-thread.
- Keep chunk memory usage predictable through staged lifecycles and caches.
- Improve perceived loading speed for players with better prioritization and delta sync.
- Ensure new threading paths remain debuggable with metrics and tracing.

## Lifecycle and Tickets
- Establish a formal state machine for chunks: `Unloaded → Queued → Loading → Ready → Active → Unloading`.
- Use typed tickets (player, entity, redstone, structure) with short time-to-live values to avoid lingering references that prevent unloading.
- Maintain separate hot and warm caches; hot chunks tick and render, while warm chunks stay available without ticking.

## Async I/O and Compression
- Route NBT read/write and compression/decompression to dedicated I/O worker pools.
- Batch disk writes with a debounce window to coalesce rapid edits and avoid duplicate saves.
- Gate faster compression codecs (zstd/LZ4) behind configuration to preserve vanilla compatibility when necessary.

## Generation, Lighting, and Meshing
- Keep world generation stages resumable and chunk-local so they can be cancelled if tickets expire mid-work.
- Track dirty light columns/blocks and recompute lighting incrementally instead of reflooding entire chunks.
- Build meshes per render layer and cache model quads per block state to minimize draw calls and rebuild time.

## Threading Model
- Use structured concurrency with per-dimension executors and bounded queues for worldgen, lighting, and meshing.
- Allow job stealing between adjacent-chunk tasks to flatten latency spikes during fast player movement.
- Protect shared structures (ticket maps, chunk caches) with lock-free collections or striped locks and avoid holding locks during I/O.

## Memory and Buffer Management
- Pool large byte and mesh buffers via thread-local arenas to reduce garbage collection churn.
- Deduplicate identical biome/noise slices across vertically stacked sections where possible.
- Evict warm-cache chunks with an LRU policy that respects ticket priority.

## Networking and Client Sync
- Prefer delta packets for block and light changes, falling back to full chunk sync only after exceeding a change budget.
- Prioritize packet order by player distance and viewport, streaming eye-level bands first for faster perceived loading.
- Compress light arrays separately so unchanged sections are not resent.

## Ticking and Simulation
- Decouple random ticks from chunk borders by queueing ticks in a simulation band around the player and scaling density with movement speed.
- Batch block-entity tickers by type, and skip ticking for chunks parked in the warm cache.
- Optionally throttle fluid and redstone simulation for distant chunks behind a compatibility-aware allowlist.

## Debugging and Metrics
- Expose per-stage timings, queue depths, and ticket counts via an in-game HUD or `/debugchunk` command.
- Add chunk-trace logging keyed by position to observe lifecycle transitions during diagnostics.

## Build and Config Integration (NeoForge + Gradle)
- Register dedicated executor services in the NeoForge entrypoint and shut them down cleanly on server stop.
- Expose experimental toggles and tuning parameters via `ForgeConfigSpec` with live `/reload` support.
- Add round-trip tests for NBT serializers (JUnit) to guard against regression when changing compression or layout.

## Next Steps
- Prototype the ticket lifecycle and warm/hot cache split behind a config gate.
- Introduce async NBT pipelines with pooled buffers and write coalescing.
- Layer in resumable generation stages and incremental lighting, then expand to delta networking once stability is verified.
