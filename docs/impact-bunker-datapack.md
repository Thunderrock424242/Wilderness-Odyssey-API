# Datapack-driven impact sites and bunkers

The current worldgen pipeline already looks up `impact_zone.nbt` and `bunker.nbt` from datapacks before falling back to the built-in assets, so you can ship your own terrain-matched builds without touching code. This playbook matches the requested layout of one impact site with a nearby bunker so the two structures are easy to find without overlapping.

## Structure count and spacing
- **Impact sites:** `MeteorStructureSpawner` now targets a single site and enforces at least 8 chunks (128 blocks) between the crater anchor and any nearby bunker placement ring. This keeps the pair close without colliding.
- **Bunkers:** One bunker is placed near the impact site by default, with its ring distance governed by `bunker.spawnDistanceChunks` (defaults to ~8 chunks). You can still raise `bunker.maxSpawnCount` if you ever want additional bunkers elsewhere, but the default flow keeps the first pair together.
- **Built-in structure sets:** Both the `impact_zone` and `bunker` structures are registered under `data/wildernessodysseyapi/worldgen/structure` with corresponding `structure_set` entries. Datapacks can override any of these JSON files if you want different spacing, salts, or biome filters without recompiling the mod.

## Where to put your datapack
- Bundle the structures directly in the mod so they travel with the jar: place `data/<namespace>/structures/impact_zone.nbt` and `data/<namespace>/structures/bunker.nbt` under `src/main/resources/` (e.g., `src/main/resources/data/wildernessodysseyapi/structures/impact_zone.nbt`). Gradle will package them into the final mod and they will load before the fallback templates.
- If you prefer a drop-in override later, you can still ship the same files as a normal datapack (`world/datapacks/<name>.zip`); datapack assets will override the built-in ones at runtime. Keeping the canonical copy in the mod ensures every server sees your custom crater and bunker without managing extra downloads.

## Building with wool height guides
- Build the crater and bunker shells in a flat world, then add wool "survey" pillars at the points where you need to measure surface offsets. The `NBTStructurePlacer` preserves these blocks, so you can read them in-game after placement to verify that the terrain alignment matches your expectations.
- Use different wool colors for key landmarks (e.g., crater rim, bunker doorway, roof corners) to make vertical deltas obvious when the structure is pasted onto varied terrain.
- Once you are satisfied with fit and clearance, replace the wool with the final blocks before exporting the structure to `data/<namespace>/structures/impact_zone.nbt` and `data/<namespace>/structures/bunker.nbt`.

## Placement flow
1. Drop both NBTs into a datapack so they override the bundled templates. No Java changes are required because the placer already references these paths.
2. Start a fresh world. By default the game will pick a solid land biome, generate one impact site within walking distance of spawn, and anchor a bunker a short distance away from that crater (or spawn additional bunkers later if you raise the max count).
3. If you add a datapack anchor (see below), the crater + bunker pair will honor that coordinate before falling back to the normal land-biome scan.
4. If you need to realign heights, adjust the wool markers in your template, re-export, and retry. The mod reuses your datapack assets on the next world creation.

## Pinning anchors with datapacks (first site only)
If you want to lock in the first crater + bunker location, add JSON files under `data/<namespace>/impact_sites/`. Each file can target a dimension and give exact anchors plus optional bunker offsets. You can also pin a **Y height** for the crater if you do not want it to follow the local terrain automatically:

```json
{
  "dimension": "minecraft:overworld",
  "impact": {"x": 1024, "y": 72, "z": -2048},
  "bunker_offset": {"x": 64, "z": 32}
}
```

- The game honors only the **first entry** per dimension as an anchor because there is only one crater+bunker pair by default.
- If `impact.y` is greater than zero, the crater uses that fixed height instead of auto-snapping to the terrain. Update any existing datapacks to include a target Y when you want to preserve a specific build elevation.
- `bunker_offset` is optional; when present the bunker will try to spawn at that offset from its paired impact anchor before trying random rings.
- Leaving these files out keeps the original land-scanning workflow intact; adding them simply locks in the first cluster and reduces scanning for that pair.
- For a ready-made template, copy `docs/examples/impact_sites/overworld.json` into your datapack and tweak the coordinates.

## Tips for wider separation between clusters
- Raise `bunker.spawnDistanceChunks` if you want the bunker farther from the crater. The default 8 chunks (128 blocks) keeps them close without touching.
- If you add more bunkers via config, ensure `spawnDistanceChunks` stays above their footprint so they do not intersect the impact site or one another.
