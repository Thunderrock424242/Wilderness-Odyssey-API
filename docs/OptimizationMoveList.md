# Optimization Classes to Migrate to Nova API

This list groups the classes that should move from Wilderness Odyssey API into the Nova API performance project. Migrate these together so the main mod can consume them from Nova.

- `src/main/java/com/thunder/wildernessodysseyapi/chunk/*` — Chunk streaming/lighting throttling stack.
- `src/main/java/com/thunder/wildernessodysseyapi/async/*` — Async framework (threading config, task manager, main-thread tasks).
- `src/main/java/com/thunder/wildernessodysseyapi/io/*` — IO helpers (buffer pool, compression codecs, IO executors) that support chunk streaming.
- Commands: `command/ChunkStatsCommand.java`, `command/DebugChunkCommand.java`, `command/AsyncStatsCommand.java` — Metrics and debug entry points for chunk/async systems.
- `ModPackPatches/client/LoadingStallDetector.java` — Client load stall watcher.
- `util/NbtDataCompactor.java` — Chunk payload compaction helper.
- `util/LightweightMath.java` — Standalone math micro-utilities used by optimization code.
