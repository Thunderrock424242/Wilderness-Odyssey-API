# Datapack-driven impact sites and bunkers

The worldgen pipeline now relies entirely on datapacks: structure JSON, structure sets, template pools, and processors live under `data/wildernessodysseyapi/`. The only Java-side registration that remains is a tiny structure processor that preserves the wool-level terrain blending behaviour, so you can swap in new NBTs or spacing rules without touching code. This playbook matches the requested layout of one impact site with a nearby bunker so the two structures are easy to find without overlapping.

## Structure count and spacing
- **Impact sites:** The built-in `structure_set/impact_zone.json` uses random spread placement with a default spacing of 24 and separation of 12. Override this JSON in a datapack to change spacing, salts, or biome filters without recompiling the mod.
- **Bunkers:** The `structure_set/bunker.json` file places bunker starts with a spacing of 16 and separation of 8. Adjust those values in a datapack if you want more breathing room or additional sites.
- **Data-driven everything:** Both the `impact_zone` and `bunker` structures are registered under `data/wildernessodysseyapi/worldgen/structure` and point at template pools located under `worldgen/template_pool`. The only code dependency is the `terrain_survey` processor, which blends your `terrain_replacer` markers into the local ground state when enabled.
- **No biome modifiers required:** The built-in structure sets already point at the overworld via `biomes: #minecraft:is_overworld`. Adding NeoForge biome modifiers on top of this can stop the structures from generating, so stick to the provided structure JSON and spacing rules instead.

## Where to put your datapack
- Bundle the structures directly in the mod so they travel with the jar: place `data/<namespace>/structures/impact_zone.nbt` and `data/<namespace>/structures/bunker.nbt` under `src/main/resources/` (e.g., `src/main/resources/data/wildernessodysseyapi/structures/impact_zone.nbt`). Gradle will package them into the final mod so you can verify your NBTs without relying on any bundled fallback.
- If you prefer a drop-in override later, you can still ship the same files as a normal datapack (`world/datapacks/<name>.zip`); datapack assets will override the built-in ones at runtime. Keeping the canonical copy in the mod ensures every server sees your custom crater and bunker without managing extra downloads.

## Building with wool height guides
- Build the crater and bunker shells in a flat world, then add wool "survey" pillars at the points where you need to measure surface offsets. The `NBTStructurePlacer` preserves these blocks, so you can read them in-game after placement to verify that the terrain alignment matches your expectations.
- Use different wool colors for key landmarks (e.g., crater rim, bunker doorway, roof corners) to make vertical deltas obvious when the structure is pasted onto varied terrain.
- Once you are satisfied with fit and clearance, replace the wool with the final blocks before exporting the structure to `data/<namespace>/structures/impact_zone.nbt` and `data/<namespace>/structures/bunker.nbt`.

## Placement flow
1. Drop both NBTs into a datapack so they override the bundled templates. The new template pools reference `structure/impact_zone.json` and `structure/bunker.json` directly, so nothing else is required.
2. Start a fresh world. Vanilla structure placement will pick sites based on your `structure_set` spacing and the biome filters defined in the structure JSON.
3. If you need to realign heights or tweak terrain blending, adjust the wool markers and `terrain_replacer` blocks inside your NBT files; when blending is enabled the `terrain_survey` processor will strip the markers and copy nearby ground blocks automatically at generation time.

## Turn off terrain sampling while you validate NBT placement
- By default the `placement.enableTerrainReplacer` config value is now **false**, so any `terrain_replacer` markers in the template are left untouched and no sampled ground is injected. This makes it easy to confirm whether your NBT actually places in-game without the mod filling the volume with dirt or other terrain.
- Once you are satisfied that the structure loads, flip `placement.enableTerrainReplacer` back to `true` to re-enable blending for the final build.

## Why a dirt pile might appear instead of your structure
- Blocks tagged as `wildernessodysseyapi:terrain_replacer` inside an NBT are **deliberately** overwritten with sampled ground by `NBTStructurePlacer` and `TerrainReplacerEngine`. Those samples default to dirt when no solid surface is found, so a template exported with only terrain replacer markers will paste as a mound of terrain instead of your build.
- To avoid the fallback, ensure your exported NBT contains the actual structure blocks and reserve `terrain_replacer` only for the patches you want to blend into the landscape.
- Quick sanity check: open the exported NBT in an editor and confirm it still contains your bunker or crater blocks in addition to any `terrain_replacer` markers. If the file only contains markers, re-export after swapping the placeholder blocks back to the intended materials.
