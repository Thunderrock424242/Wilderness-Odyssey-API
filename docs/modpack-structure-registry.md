# Modpack Structure Folder (NBT auto-registration)

You can drop structure templates in:

`config/wildernessodysseyapi/modpack_structures`

On server start (or `/modpackstructures reload`), the mod will:

1. Discover every `*.nbt` file in that folder.
2. Auto-create a matching `<name>.json` config if missing.
3. Register a runtime structure entry with an id (default: `wildernessodysseyapi:modpack/<name>`).
4. Allow listing and placement through commands.

## Per-structure config

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

### Fields

- `enabled`: whether this structure should be loaded.
- `structureId`: command id used by placement commands and scaffold output.
- `displayName`: optional note for pack authors.
- `alignToSurface`: default placement mode for `/modpackstructures place` when no override is passed.
- `biomeTag`, `generationStep`, `terrainAdaptation`, `spacing`, `separation`, `salt`: used by scaffold generation for worldgen json templates.

## Commands

- `/modpackstructures reload`
- `/modpackstructures list`
- `/modpackstructures place <id> [x y z] [alignToSurface]`
- `/modpackstructures scaffold [id]`

`alignToSurface=true` uses anchored placement.

## Worldgen scaffold generation

Use `/modpackstructures scaffold` to generate a datapack template at:

`config/wildernessodysseyapi/modpack_structures/generated_datapack`

For each structure id, it writes:

- `data/<namespace>/structures/<path>.nbt` (copy of your source template)
- `data/<namespace>/worldgen/structure/<path>.json`
- `data/<namespace>/worldgen/structure_set/<path>.json`
- `data/<namespace>/tags/worldgen/biome/has_structure/<path>.json`

This gives you a ready-to-edit base to move into your real datapack/modpack pack for actual worldgen registration.
