# Datapack-driven impact sites and bunkers

The current worldgen pipeline already looks up `impact_zone.nbt` and `bunker.nbt` from datapacks before falling back to the built-in assets, so you can ship your own terrain-matched builds without touching code. This playbook matches the requested layout of three impact sites and three nearby bunkers while keeping each cluster far apart from the others.

## Structure count and spacing
- **Impact sites:** `MeteorStructureSpawner` always targets three sites and enforces at least 32 chunks (512 blocks) between crater anchors. This keeps each cluster far apart while still letting you tune the exact shape in your datapack.
- **Bunkers:** One bunker is placed adjacent to a random impact site by default, but you can raise `bunker.maxSpawnCount` in `config/wildernessodysseyapi/wildernessodysseyapi-common.toml` to allow up to three bunkers (one per crater) while keeping a minimum spacing set by `bunker.spawnDistanceChunks`.

## Where to put your datapack
- Bundle the structures directly in the mod so they travel with the jar: place `data/<namespace>/structures/impact_zone.nbt` and `data/<namespace>/structures/bunker.nbt` under `src/main/resources/` (e.g., `src/main/resources/data/wildernessodysseyapi/structures/impact_zone.nbt`). Gradle will package them into the final mod and they will load before the fallback templates.
- If you prefer a drop-in override later, you can still ship the same files as a normal datapack (`world/datapacks/<name>.zip`); datapack assets will override the built-in ones at runtime. Keeping the canonical copy in the mod ensures every server sees your custom crater and bunker without managing extra downloads.

## Building with wool height guides
- Build the crater and bunker shells in a flat world, then add wool "survey" pillars at the points where you need to measure surface offsets. The `NBTStructurePlacer` preserves these blocks, so you can read them in-game after placement to verify that the terrain alignment matches your expectations.
- Use different wool colors for key landmarks (e.g., crater rim, bunker doorway, roof corners) to make vertical deltas obvious when the structure is pasted onto varied terrain.
- Once you are satisfied with fit and clearance, replace the wool with the final blocks before exporting the structure to `data/<namespace>/structures/impact_zone.nbt` and `data/<namespace>/structures/bunker.nbt`.

## Placement flow
1. Drop both NBTs into a datapack so they override the bundled templates. No Java changes are required because the placer already references these paths.
2. Start a fresh world. By default the game will pick solid land biomes, generate three impact sites ~16,000 blocks apart, and anchor a bunker beside one of them (or up to three if you raise the max count).
3. If you add a datapack anchor (see below), the **first crater + bunker** will honor that coordinate while the other two craters continue using land-biome scanning so you keep wide spacing without hand-curating every site.
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

- The game honors only the **first entry** per dimension as an anchor; the other two craters still use land-biome scanning so you do not need to curate multiple coordinates.
- If `impact.y` is greater than zero, the crater uses that fixed height instead of auto-snapping to the terrain. Update any existing datapacks to include a target Y when you want to preserve a specific build elevation.
- `bunker_offset` is optional; when present the bunker will try to spawn at that offset from its paired impact anchor before trying random rings.
- Leaving these files out keeps the original land-scanning workflow intact; adding them simply locks in the first cluster and reduces scanning for that pair.
- For a ready-made template, copy `docs/examples/impact_sites/overworld.json` into your datapack and tweak the coordinates.

## Tips for wider separation between clusters
- Keep `bunker.spawnDistanceChunks` at or above the default 32 to maintain distance from the nearby crater while avoiding overlap with the other two clusters.
- To maximize exploration distance between clusters, avoid lowering the 32-chunk impact separation baked into `MeteorStructureSpawner`.
