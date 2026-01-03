# Starter Structure schematic pipeline

Wilderness Odyssey now treats Starter Structure schematics through a single abstraction so both `.schem` and `.nbt` exports behave the same during placement.

## Detection and parsing

- `StarterStructureSchematic` detects formats by file extension and falls back to `ClipboardFormats.findByFile` to keep odd or renamed schematics working.
- Footprint dimensions come from the parsed schematic when available, then from the WorldEdit clipboard, and finally from entity offsets if nothing else is present. This ensures terrain blending always has a consistent target size.
- Entities are collected for both formats:
  - `.schem` files still use `SchematicEntityRestorer.backfillEntitiesFromSchem` to pull Create contraptions and other missing entities from the schematic payload.
  - `.nbt` files read the `Entities` tag directly so they populate the same `List<Pair<BlockPos, Entity>>` used for restoration.

Keep entity data in every exported bunker schematic. The shared entity list is the source of truth for post-placement spawning, and it is what preserves Create contraptions even when WorldEdit is unavailable.

## Placement pipeline

1. Load the schematic abstraction.
2. If `starterStructureUseWorldEdit` is enabled *and* WorldEdit is present, attempt a WorldEdit paste. Otherwise, fall back to the vanilla Starter Structure placement.
3. Run the common post-processing steps for **both** formats and paste paths:
   - Spawn any restored entities from the unified list.
   - Register the hostile-spawn deny zone around the bunker.
   - Run the terrain blender (terrain replacer toggles remain format-agnostic).
   - When WorldEdit pasted the bunker, parsed schematic buffers are cleared so Starter Structure will not double-place it.

The WorldEdit toggle now only decides how the bunker is pasted; blending, spawn-guarding, and entity restoration always run.
