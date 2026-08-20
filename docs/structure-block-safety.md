# Expanded structure-block safety

Wilderness Odyssey keeps its expanded 512-block per-axis structure metadata and packet fields, but no longer treats
that axis limit as permission to perform an unbounded synchronous operation. Save, Load, Detect, auto-fit, loaded
chunk inspection, and post-save NBT processing now have independent server-configured budgets.

## Server settings

The settings remain under `structure_blocks` in the server config:

- `maxStructureSize` and `maxStructureOffset` preserve the representable structure dimensions and offsets.
- `maxOperationVolume` limits the total blocks accepted for a Save, Load, or Detect request. The default is 4,194,304
  blocks and the hard configurable ceiling is 16,777,216 blocks.
- `maxSynchronousScanBlocks` limits Detect traversal and the optional save auto-fit pass. The default is 1,048,576
  inspected blocks.
- `chunkWarmupBudget` is retained as a deprecated, ignored compatibility key so existing configs continue to load.
- `maxLoadedChunksPerOperation` limits how many **already-loaded** chunks may be inspected. Structure-block operations
  never warm or force-load chunks.
- `maxStructureNbtBytes` limits both the compressed file accepted for post-processing and the decoded NBT accounting
  quota. The default is 16 MiB and the hard configurable ceiling is 64 MiB.

Changing these budgets does not rewrite or delete existing structure files. An oversized operation is rejected with a
message to the operator, and an over-budget post-processing pass leaves the original saved NBT file in place. Raising
the per-axis size alone does not bypass the independent safety ceilings.

## Behavior and compatibility

The custom packet extension is unchanged: it still appends three offset values, three size values, and the hostile-spawn
toggle. The server validates expensive update types after Minecraft has moved packet handling to the server thread and
after confirming that the player may use game-master blocks. Ordinary metadata updates remain available so an existing
expanded structure block can still display and edit its stored bounds.

Detect searches loaded chunks only. Cached CORNER markers remain the preferred way to find distant bounds. If a scan
would exceed a block or chunk budget, Detect stops and tells the operator to reduce the radius or add matching CORNER
markers. Save auto-fit is optional: when its scan budget is exceeded, the exact operator-entered bounds are passed to
the normal save path instead of performing a second full-volume scan.

Post-save hostile filtering uses a bounded `NbtAccounter`. Rewrites are staged to a sibling temporary file and moved
over the destination only after a complete write, preserving the last valid structure if filtering or compression fails.
Optional recompression uses the mod's bounded I/O executor and is skipped if that executor is unavailable or full.

## Validation checklist

After the isolated NeoForm output lock is released, run:

```powershell
.\gradlew.bat test -PcodexBuildDir=.codex-build --tests "com.thunder.wildernessodysseyapi.structureblock.StructureBlockWorkBudgetTest"
.\gradlew.bat compileJava -PcodexBuildDir=.codex-build
```

Then verify in a copied development world:

1. Save and load a normal vanilla-sized structure.
2. Save an expanded structure below `maxOperationVolume`, with every intersecting chunk already loaded.
3. Submit a 512 x 512 x 512 Save and Load request and confirm the operation is rejected without a watchdog stall.
4. Run Detect with matching near CORNER blocks and confirm its bounds are applied.
5. Run Detect across unloaded chunks and across a deliberately low block/chunk budget; confirm it stops with a readable
   message and does not load those chunks.
6. Save with hostile filtering enabled and confirm monsters are absent, non-hostile entities remain, and the NBT loads.
7. Lower `maxStructureNbtBytes` below a test structure's file size and confirm the structure remains intact while
   post-processing is skipped.
8. Connect a client and dedicated server on the same mod version to confirm the unchanged extended packet round trip.

A GameTest should eventually cover save/load behavior around the volume boundary and assert that an unloaded neighbor
chunk remains unloaded after Detect. Packet compatibility and the structure-block screen still require a live client.
