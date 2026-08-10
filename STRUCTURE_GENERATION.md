# StructureGen

## Overview

StructureGen is the offline authoring and inspection pipeline for Wilderness Odyssey Minecraft structures. It turns reviewable JSON blueprints into standard Minecraft structure-template NBT and reads existing NBT into a shared internal model for inspection, comparison, and supported export.

StructureGen does not call an external AI service and does not generate layouts from natural language. Codex or a developer authors the Blueprint v1 JSON; StructureGen owns parsing, validation, safe NBT serialization, and verification.

The first milestone lives under the Java package `com.thunder.wildernessodysseyapi.structuregen`. It is deliberately separate from the mod's world-generation, structure-placement, and jigsaw systems.

## Architecture

Generation follows one controlled path:

```text
JSON Blueprint
    -> Blueprint parser
    -> Blueprint validation
    -> Internal structure model
    -> Minecraft NBT compiler
    -> Temporary generated NBT
    -> NBT re-read and semantic verification
    -> Generated resource
```

Inspection and supported export use the same internal model:

```text
Minecraft structure NBT
    -> NBT reader
    -> Internal structure model
    -> Inspector / analysis / Blueprint export
```

JSON parsing is not coupled directly to binary serialization. A blueprint must become a valid internal structure model before the compiler is allowed to create an output file.

## Project locations

| Purpose | Location |
| --- | --- |
| Blueprint sources | `src/main/structure_blueprints/` |
| Blueprint v1 fixture | `src/main/structure_blueprints/test_shelter.json` |
| Generated resource root | `build/generated/structuregen/resources/` |
| Generated structure NBT | `build/generated/structuregen/resources/data/wildernessodysseyapi/structure/<name>.nbt` |
| Inspection reports | `build/reports/structuregen/` |
| Read-only bunker fixture | `src/main/resources/data/wildernessodysseyapi/structures/bunker.nbt` |

Generated resources belong to the build output and are kept separate from hand-authored resources. Do not put a blueprint in the generated-resource directory and do not edit a generated NBT by hand.

## Blueprint Format v1

Every blueprint is a JSON object with the following top-level fields.

| Field | Required | Type | Meaning |
| --- | --- | --- | --- |
| `formatVersion` | Yes | Integer | Must be `1` for Blueprint Format v1. |
| `name` | Yes | String | Simple lowercase structure ID used as the output filename. |
| `dataVersion` | No | Integer | Minecraft DataVersion to preserve or request when appropriate. When omitted, the compiler uses its current supported default. |
| `size` | Yes | Three integers | Structure dimensions in `[x, y, z]` order. Every value must be greater than zero and within StructureGen's safety limits. |
| `metadata` | No | Object | Developer-facing string-to-string metadata. |
| `markers` | No | String array | Structure-level StructureGen labels. These do not automatically create Minecraft jigsaw, loot, entity, or processor behavior. |
| `blocks` | Yes | Array | Explicit block records. At least one block is required. |
| `entities` | No | Array | Loss-aware entity records, primarily emitted by NBT export. See the import/export extension notes below. |
| `rawRootSnbt` | No | String | Typed compound SNBT containing unknown root tags that must survive a supported model/NBT round trip. |

A block record supports these fields.

| Field | Required | Type | Meaning |
| --- | --- | --- | --- |
| `pos` | Yes | Three integers | Local `[x, y, z]` position. Coordinates start at zero and must lie inside `size`. |
| `block` | Yes | String | Minecraft resource location, for example `minecraft:stone_bricks`. |
| `properties` | No | Object | String-to-string block-state properties, validated against the selected block when registry data is available. |
| `blockEntitySnbt` | No | String | Loss-preserving SNBT compound for optional block-entity data. |
| `markers` | No | String array | StructureGen labels attached to this block record. |
| `rawEntrySnbt` | No | String | Typed compound SNBT containing unknown fields from an imported block-list entry. |

The `name` is intentionally narrow in v1. It must match `[a-z0-9][a-z0-9_-]{0,63}`. Namespaces, dots, slashes, absolute paths, drive prefixes, `.` segments, and `..` segments are not accepted. The compiler always owns the `wildernessodysseyapi` namespace and confines output beneath the generated structure directory. The name `bunker` is reserved for the read-only fixture.

Every coordinate may appear only once. Duplicate coordinates are rejected rather than using last-write-wins behavior. Explicit air is represented as an ordinary block record whose ID is `minecraft:air`; this is different from omitting the coordinate.

### Minimal example

```json
{
  "formatVersion": 1,
  "name": "example",
  "size": [3, 3, 3],
  "metadata": {
    "purpose": "documentation example"
  },
  "markers": ["example"],
  "blocks": [
    {
      "pos": [0, 0, 0],
      "block": "minecraft:stone_bricks"
    },
    {
      "pos": [1, 0, 0],
      "block": "minecraft:oak_stairs",
      "properties": {
        "facing": "north",
        "half": "bottom",
        "shape": "straight",
        "waterlogged": "false"
      }
    },
    {
      "pos": [1, 1, 1],
      "block": "minecraft:air",
      "markers": ["explicit_air"]
    }
  ]
}
```

For block entities, `blockEntitySnbt` must contain one syntactically valid SNBT compound encoded as a JSON string. StructureGen rejects malformed SNBT rather than emitting partial NBT.

### Import/export extension fields

Entity entries use `pos` (three finite doubles), `blockPos` (three in-bounds integers), `nbtSnbt` (the typed entity compound), and optional `rawEntrySnbt`. This support exists so an imported entity can survive the model/NBT path. Blueprint v1 does not yet provide registry-aware entity validation, spawn rules, or a friendly entity-authoring schema.

The exporter also writes `sourcePalettes`, per-block `sourcePaletteIndex`, and `unsupportedFields` when it reads information that the authoring schema cannot model directly. These fields make the JSON an honest reference artifact, but they are deliberately **export-only in v1**: the parser emits explicit warnings and regenerates blocks from their declared primary `block` and `properties`. Alternate palettes and unknown palette-entry tags therefore do not survive an exported-JSON-to-NBT pass. Unknown root, block-entry, entity-entry, block-entity, and entity payload compounds are importable through their typed SNBT fields.

Unknown Blueprint field names are errors rather than being silently ignored. This catches misspelled properties in the JSON schema itself before generation.

## Creating a structure

1. Copy `src/main/structure_blueprints/test_shelter.json` to a new `.json` file in the same directory.
2. Choose a unique simple lowercase `name`.
3. Set `size` before adding blocks.
4. Add block records using local coordinates from `[0, 0, 0]` through `[sizeX - 1, sizeY - 1, sizeZ - 1]`.
5. Use full resource locations for block IDs.
6. Include every state property needed to reproduce an oriented or otherwise stateful block.
7. Add explicit `minecraft:air` only where placement must intentionally clear a block.
8. Run validation and generation before attempting to load the structure in Minecraft.

Blueprints are untrusted input. StructureGen rejects malformed JSON, unsupported format versions, invalid sizes, invalid names, out-of-bounds or duplicate coordinates, malformed resource locations, invalid block states where registry validation is available, malformed block-entity SNBT, unsafe paths, and collisions with hand-authored structures.

The standalone task bootstraps Minecraft's built-in registry, so vanilla block IDs and properties are checked exactly. If that bootstrap is unavailable, `minecraft:` blocks fail validation rather than compiling with weakened checks. Modded namespaces that are not present in the standalone built-in registry are retained with an explicit warning; their property schemas still require validation in the full mod environment.

## Generating structures

From the repository root in PowerShell, run:

```powershell
.\gradlew.bat generateStructures
```

The task discovers `.json` files under `src/main/structure_blueprints/`, parses and validates them, compiles valid models, re-reads each temporary NBT, and verifies supported semantic fields before publishing it beneath:

```text
build/generated/structuregen/resources/data/wildernessodysseyapi/structure/
```

A validation or verification error fails the task. StructureGen does not publish a partially valid file and does not replace the last valid generated output when a new generation fails.

The build's generated-resource integration packages successful output without copying it into `src/main/resources`.

Run the focused offline unit and codec suite with:

```powershell
.\gradlew.bat structureGenTest
```

Run the separate 4 GB read-only bunker fixture regression with:

```powershell
.\gradlew.bat structureGenBunkerTest
```

These tasks use the project's Minecraft/NeoForge libraries but do not start a Minecraft world or load the full mod set.

## Inspecting structures

Run the inspector with no property to inspect the authoritative bunker fixture:

```powershell
.\gradlew.bat inspectStructure
```

The default input is:

```text
src/main/resources/data/wildernessodysseyapi/structures/bunker.nbt
```

Inspect a different file with:

```powershell
.\gradlew.bat inspectStructure -PstructureFile="C:\path\to\structure.nbt"
```

Request details for one palette entry with:

```powershell
.\gradlew.bat inspectStructure -PpaletteIndex=27
```

Both overrides can be combined:

```powershell
.\gradlew.bat inspectStructure -PstructureFile="C:\path\to\structure.nbt" -PpaletteIndex=27
```

The inspector reports values read from the selected file, including DataVersion, dimensions, bounding volume, stored block records, explicit air, non-air occupancy, palette entries, block-state variants, block entities, entities, frequently used blocks, vertical distribution, inferred material categories, and unknown or unsupported tags. It writes machine-readable and text reports under `build/reports/structuregen/` when reporting is enabled.

Inspection numbers are deliberately not copied into this guide. The task output and generated reports are the authoritative results for the exact NBT being inspected.

The implemented export task reads the bunker by default and writes a reference JSON beneath the report directory:

```powershell
.\gradlew.bat exportStructureBlueprint
```

Override the input and output with:

```powershell
.\gradlew.bat exportStructureBlueprint -PstructureFile="C:\path\to\structure.nbt" -PblueprintOutput="build\reports\structuregen\example.blueprint.json"
```

Blueprint exports are restricted to `build/reports/structuregen/`. Exporting the bunker produces a very large JSON file because every stored block is represented explicitly.

Compare two structures semantically with:

```powershell
.\gradlew.bat compareStructures -PleftStructure="C:\path\to\original.nbt" -PrightStructure="C:\path\to\regenerated.nbt"
```

Comparison covers dimensions, DataVersion, primary and alternate palette semantics, blocks, states, block entities, StructureGen markers, preserved raw entry/root tags, entities, and StructureGen metadata. Neither task changes its input NBT.

## Output and packaging

`test_shelter` compiles to:

```text
build/generated/structuregen/resources/data/wildernessodysseyapi/structure/test_shelter.nbt
```

Its Minecraft structure ID is:

```text
wildernessodysseyapi:test_shelter
```

The source blueprint remains the reviewable authority. Generated NBT can be recreated and should not be used as an editing surface.

Minecraft 1.21.1 data-pack structure resources use the singular `data/<namespace>/structure/` directory, which is why StructureGen publishes there. The existing bunker remains at the historical plural `structures/` path because the project's custom runtime loader reads that exact resource directly; StructureGen does not migrate it.

## Safety

StructureGen is restricted to project-controlled source, generated-output, and report paths.

- `bunker.nbt` is a read-only reference and regression fixture. StructureGen may open it for reading but must never modify, migrate, replace, or use it as an output destination.
- Generated files are staged, closed, re-read, and semantically verified before publication.
- Output paths are normalized and checked to remain beneath the generated structure root.
- Existing symbolic links anywhere between `build/` and the destination are rejected so lexical containment cannot redirect a write outside the checkout.
- Blueprint names cannot contain path separators or traversal segments.
- A generated name that collides with a hand-authored singular/plural structure, the re-namespaced `empty` GameTest fixture, or another blueprint in the same batch is rejected.
- After a complete batch succeeds, obsolete safe-name `.nbt` files from deleted or renamed blueprints are removed from StructureGen's build-owned output directory so stale resources cannot remain packaged or shadow later hand-authored structures. Failed batches do not perform this reconciliation.
- Existing generated output is replaced only after the new temporary file passes verification.
- StructureGen never writes into `run/` worlds, Minecraft saves, `.minecraft`, or an external Minecraft installation.
- The pipeline does not modify structure spawning, placement, biome generation, world generation, or jigsaw pools.

A failed generation should be investigated from its `[StructureGen]` diagnostics. Do not copy a failed temporary output into resources manually.

## The bunker reference fixture

The existing structure at `src/main/resources/data/wildernessodysseyapi/structures/bunker.nbt` is the project's known-good format reference. It is used to exercise real palette entries, block-state properties, block entities, entities, statistics, and supported semantic round trips.

The fixture is never an export destination. Any exported blueprint or analysis report goes beneath `build/reports/structuregen/`; comparisons are read-only. Before and after regression work, its content hash can be compared to confirm that inspection was read-only.

## Testing test_shelter in Minecraft

First generate the NBT and launch the development client:

```powershell
.\gradlew.bat generateStructures
.\gradlew.bat runClient
```

Then perform the manual visual check:

1. Create or open a disposable development world with cheats enabled.
2. Move to a clear area with at least 7 by 5 by 7 blocks of space.
3. Run `/give @s minecraft:structure_block`.
4. Place the structure block and open its interface.
5. Change the structure block to **Load** mode.
6. Enter `wildernessodysseyapi:test_shelter` as the structure name.
7. Select **Load** once so Minecraft reads the structure size and shows the placement outline.
8. Adjust the relative position if the outline intersects the ground or another build.
9. Select **Load** again to place the structure.
10. Verify the 7 by 5 by 7 envelope, stone-brick shell, oak roof, two-block oak door, oriented oak-stair bench, hanging lantern, and intentionally clear explicit-air positions.
11. Break the disposable test structure or delete the development world when finished.

StructureGen's automated tests prove parsing and supported semantic serialization. They do not prove visual appearance in a live client; the steps above are the required final visual verification.

## Troubleshooting

### Unsupported formatVersion

Use `"formatVersion": 1`. A newer or missing version is rejected so the parser cannot reinterpret data silently.

### Position outside the structure

Each coordinate must be non-negative and smaller than its matching `size` component. For a size of `[7, 5, 7]`, the largest valid position is `[6, 4, 6]`.

### Duplicate block position

Blueprint v1 permits one block record per coordinate. Remove or merge the duplicate.

### Invalid block or property

Use a full block ID such as `minecraft:oak_stairs`. Property names and values must match Minecraft 1.21.1. For example, stairs use `facing`, `half`, `shape`, and `waterlogged`.

### Malformed blockEntitySnbt

The value must be a JSON string containing one valid SNBT compound. Check both JSON escaping and SNBT types/suffixes.

### Hand-authored structure collision

Choose a different blueprint `name`. StructureGen intentionally has no default override for hand-authored structure resources.

### Structure not found in the client

Confirm that `generateStructures` succeeded before launching the client, that the generated file is beneath the documented resource path, and that the structure block uses `wildernessodysseyapi:test_shelter` exactly. Restart the development client after regenerating resources if its active resource set is stale.

### Inspection reports unsupported tags

Unsupported does not mean ignored. Preserve the warning and raw data when available, and consult the report before attempting an export or semantic round trip.

## Blueprint v1 limitations

Blueprint Format v1 is a safe block-by-block foundation, not a procedural structure language.

- Names are simple IDs in the fixed `wildernessodysseyapi` namespace; nested or cross-namespace output is not authored.
- Blocks are listed individually. There are no fill regions, reusable rooms, inheritance, or composition.
- Entity records are a low-level preservation/import feature; v1 does not registry-validate them or provide a high-level entity-authoring schema.
- Block entities are supplied as raw, typed SNBT rather than a specialized schema for every block-entity type.
- Metadata and markers are StructureGen annotations; they do not automatically create loot tables, mobs, quests, jigsaw connectors, processors, or placement behavior.
- The exporter describes multiple palettes and source indices for inspection, but v1 import does not reconstruct them; the parser warns and uses the primary block/state declarations. Weighted-palette authoring is unsupported.
- There is no rotation, mirroring, biome material substitution, randomized palette, or seeded variation.
- There is no connectivity, pathfinding, structural-support, or automatic lighting proof.
- Existing NBT may contain alternate palettes, unknown palette fields, malformed extension fields, or entity semantics that Blueprint v1 cannot re-import completely. The reader preserves supported raw compounds where practical and reports every unsupported path; StructureGen does not claim a lossless bunker JSON round trip.
- Semantic equality is the target. Compressed NBT bytes, compound ordering, and palette index ordering may differ.
- Safe staging, semantic re-read, replacement, and rollback apply to each generated structure. A multi-blueprint invocation validates and preflights the complete batch first, but publication is not a single filesystem transaction: if a later file encounters an unexpected I/O failure, an earlier verified file may already have been published.
- StructureGen creates structure resources only; it does not register, spawn, or place them through world generation.

## Phase 2 recommendations

Phase 2 should begin only after the first milestone's reader, validator, compiler, fixture regression tests, and file-safety checks are stable. Recommended next work is:

1. Strengthen lossless NBT-to-Blueprint export for typed raw tags and entities.
2. Add reusable components and explicit composition without weakening coordinate and path validation.
3. Add rotations and mirroring as deterministic model transforms.
4. Define typed marker schemas for connectors, loot, lighting, and Wilderness Odyssey authoring metadata.
5. Add semantic rules such as doorway/connectivity checks and lighting analysis.
6. Explore palettes, deterministic variation, weathering, and decay passes.
7. Integrate jigsaw pools or world placement only as a separate, reviewed subsystem.
8. Add Codex-assisted blueprint authoring after the deterministic offline pipeline is proven.

Natural-language generation, external AI APIs, procedural facilities, automatic spawning, and world-generation changes remain outside the first milestone.
