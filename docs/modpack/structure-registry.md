# Modpack Structure Folder (NBT auto-registration)

This workflow lets pack authors treat loose `.nbt` files like a staging datapack:

1. Drop structure templates into a config folder.
2. Let the mod auto-register them for commands/testing.
3. Generate a scaffold datapack from those templates.
4. Copy scaffold output into your real modpack datapack.

## 1) Drop `.nbt` files into the modpack structure folder

Use this directory on the server or single-player instance:

`config/wildernessodysseyapi/modpack_structures`

Example:

- `config/wildernessodysseyapi/modpack_structures/crashed_pod.nbt`
- `config/wildernessodysseyapi/modpack_structures/abandoned_outpost.nbt`

On server start (or when you run reload), the mod will:

1. Discover every `*.nbt` file in that folder.
2. Auto-create a matching `<name>.json` config if missing.
3. Register a runtime structure entry with an id (default: `wildernessodysseyapi:modpack/<name>`).
4. Allow listing and placement through commands.

## 2) Configure each structure with optional JSON

For each `my_base.nbt`, create (or edit) `my_base.json` in the same folder:

```json
{
  "enabled": true,
  "structureId": "wildernessodysseyapi:modpack/my_base",
  "displayName": "My Base",
  "alignToSurface": true,
  "biomeTag": "minecraft:is_overworld",
  "generationStep": "surface_structures",
  "terrainAdaptation": "beard_thin",
  "spacing": 36,
  "separation": 12,
  "salt": 150001
}
```

### Field reference

- `enabled`: load/unload a structure without deleting files.
- `structureId`: namespaced id used by placement commands and scaffold output.
- `displayName`: pack-author note shown in list output.
- `alignToSurface`: default placement mode for `/modpackstructures place`.
- `biomeTag`: biome tag used in generated `has_structure` biome tag file.
- `generationStep`: worldgen step for generated structure JSON.
- `terrainAdaptation`: structure terrain adaptation mode in generated JSON.
- `spacing`, `separation`, `salt`: structure set placement parameters in generated JSON.

## 3) Reload and test in-game

Commands:

- `/modpackstructures reload`
- `/modpackstructures list`
- `/modpackstructures place <id> [x y z] [alignToSurface]`
- `/modpackstructures scaffold [id]`

Tips:

- Use `list` after adding files to verify ids were registered.
- Use `place` to validate rotation/offset and marker blocks before worldgen integration.
- `alignToSurface=true` uses anchored placement.

## 4) Generate datapack scaffolding

Run:

- `/modpackstructures scaffold` (all structures)
- `/modpackstructures scaffold wildernessodysseyapi:modpack/my_base` (single structure)

Output path:

`config/wildernessodysseyapi/modpack_structures/generated_datapack`

For each structure id, scaffold writes:

- `data/<namespace>/structures/<path>.nbt` (copy of source template)
- `data/<namespace>/worldgen/structure/<path>.json`
- `data/<namespace>/worldgen/structure_set/<path>.json`
- `data/<namespace>/tags/worldgen/biome/has_structure/<path>.json`

## 5) Move scaffold output into your modpack datapack

In your pack, copy from `generated_datapack` into your actual datapack folder, e.g.:

`<instance>/saves/<world>/datapacks/<your_pack>/`

or for dedicated servers:

`<server>/world/datapacks/<your_pack>/`

Keep/edit the generated files as needed (spacing, biomes, step, etc.), then reload datapacks:

- `/reload`

## Recommended pack-author workflow

1. Build/export structure in NBT format.
2. Drop `.nbt` into `modpack_structures`.
3. Run `/modpackstructures reload`.
4. Run `/modpackstructures place ...` until it looks correct.
5. Run `/modpackstructures scaffold`.
6. Copy scaffold into your real datapack.
7. Tune JSON and test worldgen in a fresh world or unexplored chunks.

## Troubleshooting

- Structure missing from `list`:
  - Confirm the file extension is `.nbt`.
  - Re-run `/modpackstructures reload`.
  - Check if `<name>.json` has `enabled: false`.
- Datapack generates files but nothing spawns naturally:
  - Verify the datapack is enabled (`/datapack list`).
  - Verify biome tag and spacing/separation are reasonable.
  - Test in new terrain/chunks.
- Command id mismatch:
  - Ensure `structureId` in JSON matches what you pass to `place`/`scaffold`.

This gives you a modpack-friendly bridge: fast local NBT iteration with commands, then clean vanilla-style datapack worldgen artifacts for release.
