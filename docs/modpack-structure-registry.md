# Modpack Structure Folder (NBT auto-registration)

You can now drop structure templates in:

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
  "alignToSurface": true
}
```

### Fields

- `enabled`: whether this structure should be loaded.
- `structureId`: command id used by placement commands.
- `displayName`: optional note for pack authors.
- `alignToSurface`: default placement mode recommendation (anchored terrain fit).

## Commands

- `/modpackstructures reload`
- `/modpackstructures list`
- `/modpackstructures place <id> [x y z] [alignToSurface]`

`alignToSurface=true` uses the same anchored placement style as the starter structure logic.
