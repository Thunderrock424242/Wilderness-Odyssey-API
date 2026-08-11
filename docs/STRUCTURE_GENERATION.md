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
| `contentPolicy` | No | Object | Installed-content policy, explicit required mod IDs, and functional-system opt-ins. Defaults preserve ordinary vanilla blueprints. |
| `materials` | No | Object | Named semantic material roles with deterministic preferred-candidate and fallback chains. |

A block record supports these fields.

| Field | Required | Type | Meaning |
| --- | --- | --- | --- |
| `pos` | Yes | Three integers | Local `[x, y, z]` position. Coordinates start at zero and must lie inside `size`. |
| `block` | Yes | String | Concrete Minecraft resource location, such as `minecraft:stone_bricks`, or a declared semantic material reference such as `$industrial_detail`. |
| `properties` | No | Object | String-to-string block-state properties for a concrete literal. A semantic reference takes its properties from the selected material candidate. |
| `blockEntitySnbt` | No | String | Loss-preserving SNBT compound for optional block-entity data. Semantic references cannot add per-position block-entity data. |
| `markers` | No | String array | StructureGen labels attached to this block record. |
| `rawEntrySnbt` | No | String | Typed compound SNBT containing unknown fields from an imported block-list entry. |
| `usageIntent` | No | String | Literal-block classification: `decorative` or `functional`. Required for direct third-party literals; omitted for semantic references. |
| `requiredSystem` | No | String | Namespaced functional-system token for a functional literal. It must also be explicitly enabled by `contentPolicy.enabledFunctionalSystems`. |

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

When an NBT carries a complete, verified StructureGen content manifest, export preserves its `contentPolicy`, but it writes the already selected concrete blocks. An exported file therefore does not reconstruct the original semantic preference/fallback chains; keep the authored Blueprint as the source of truth for those chains. A partial/corrupt manifest is never trusted as author intent. Arbitrary imported modded blocks also remain unclassified: the exporter omits `usageIntent`, so a developer must review and classify them before the JSON can be generated again.

Unknown Blueprint field names are errors rather than being silently ignored. This catches misspelled properties in the JSON schema itself before generation.

## Mod-aware content policy

Blueprint v1 can describe optional installed-mod decoration without making a blueprint depend on a hardcoded mod list. Mod awareness is resolved during validation, before the canonical model is allowed to reach the NBT writer. The writer therefore sees only concrete block IDs and states that the selected content catalog has confirmed.

Two optional root fields control this behavior:

| Field | Type | Meaning |
| --- | --- | --- |
| `contentPolicy` | Object | Controls installed-mod use and explicit functional opt-ins. When omitted, installed decorative blocks are allowed. |
| `materials` | Object | Defines named semantic material roles and deterministic preference/fallback chains. |

`contentPolicy` supports these fields:

| Field | Default | Meaning |
| --- | --- | --- |
| `allowInstalledModBlocks` | `true` | Allows validated third-party blocks to satisfy semantic decorative roles. `false` confines automatic selection to Minecraft and Wilderness Odyssey content. |
| `preferredDecorativeMods` | `[]` | Ordered mod IDs to prefer when more than one valid decorative candidate can satisfy a role. Preference never bypasses block or state validation. |
| `requiredMods` | `[]` | Mod IDs the structure explicitly requires. A missing required mod is an error; StructureGen does not silently substitute different functionality. |
| `enabledFunctionalSystems` | `[]` | Explicit system tokens authorized by the structure request. Merely detecting a mod never adds an entry or activates that mod's gameplay systems. |

Each member of `materials` is a semantic role. A role supports:

| Field | Default | Meaning |
| --- | --- | --- |
| `intent` | `"decorative"` | `"decorative"` permits visual selection. Functional intent must be explicit. |
| `requiredSystem` | Omitted | Functional-system token that must also be present in `enabledFunctionalSystems`. |
| `preferred` | `[]` | Ordered installed-content candidates considered before fallbacks. |
| `fallbacks` | `[]` | Ordered safe alternatives, normally ending with a vanilla block. |

A candidate has a required `block` resource ID, an optional string-to-string `properties` object, and an optional `requiresMod` gate. `requiresMod` is useful when the registering mod ID differs from the block's resource namespace: the candidate is skipped when that declared mod is unavailable, and `preferredDecorativeMods` can order it accurately. It is an author-declared availability gate, not proof that the mod owns the namespace. Properties belong to the individual candidate because two fallback blocks may expose entirely different state definitions. A block record refers to a role by putting `$<role>` in its existing `block` field.

Concrete block records may additionally declare `usageIntent` and `requiredSystem`. Existing vanilla records remain compatible without either field. A direct third-party literal must explicitly use `"usageIntent": "decorative"` or `"functional"`; a functional literal must name a namespaced `requiredSystem` that also appears in `contentPolicy.enabledFunctionalSystems`. Any non-vanilla literal carrying `blockEntitySnbt` must take that functional path. Semantic `$role` references inherit intent and system authorization from their material definition and may not repeat these literal-only fields.

```json
{
  "formatVersion": 1,
  "name": "industrial_detail_example",
  "size": [1, 1, 1],
  "contentPolicy": {
    "allowInstalledModBlocks": true,
    "preferredDecorativeMods": ["create"],
    "requiredMods": [],
    "enabledFunctionalSystems": []
  },
  "materials": {
    "industrial_detail": {
      "intent": "decorative",
      "preferred": [
        {"block": "create:industrial_iron_block", "requiresMod": "create"}
      ],
      "fallbacks": [
        {"block": "minecraft:iron_block"}
      ]
    }
  },
  "blocks": [
    {"pos": [0, 0, 0], "block": "$industrial_detail"}
  ]
}
```

The example does not declare that Create is universally available. `create:industrial_iron_block` is eligible only when the selected environment catalog contains that exact block and validates its selected state. Otherwise the role resolves to `minecraft:iron_block`. Do not copy a candidate ID from documentation, another modpack, or an older mod version without refreshing and checking the catalog.

Candidate selection is deterministic. Explicit `requiresMod` affinity, policy preference, and each declared candidate order are used instead of registry iteration order. When `requiresMod` is absent, exact namespace/mod-ID equality may be used only as an ordering affinity if that mod ID is installed; it is never reported as authoritative ownership. The same blueprint and the same catalog must produce the same concrete selections.

### Available-content catalog and registry snapshot

The JavaExec generation command is deliberately offline and does not run NeoForge's complete mod-loading and registry-event lifecycle. A dependency JAR appearing on the classpath is not proof that a particular mod ID, block ID, or state schema is registered. StructureGen must never infer availability from a filename, a dependency declaration, or a hardcoded list of popular mods.

Run the full data-generation environment as its own invocation to refresh the deterministic registry snapshot:

```powershell
.\gradlew.bat runData
```

The direct `runData` invocation intentionally refreshes the catalog before generated structures are required, so a clean checkout or a blueprint that requires modded content cannot deadlock the task graph. Run `generateStructures` as the next, separate invocation. The data run constructs the configured mods and fires their registry events without opening a Minecraft world. It publishes the snapshot selected through internal StructureGen system properties at:

```text
build/generated/structuregen/catalog/available-content.json
```

Catalog schema 2 contains a deterministic environment fingerprint, sorted detected mod IDs and versions, and sorted registered blocks with valid property names, values, and default properties. The Gradle fingerprint covers registry-affecting project sources/resources, resolved common runtime artifacts, and local `run/mods` JARs. It deliberately has no timestamp, making an unchanged environment byte-for-byte deterministic. Data-generation `.cache` bookkeeping is ignored and excluded from packaged resources. Treat the catalog as generated build evidence; do not hand-edit it into agreement with a blueprint.

Offline generation may trust only:

- Minecraft registry content currently bootstrapped in-process; or
- a successfully parsed full-registry snapshot whose environment fingerprint exactly matches the current Gradle source/dependency environment.

Malformed, schema-mismatched, duplicate, internally invalid, or symbolic-link catalog inputs are rejected. A well-formed but stale fingerprint is not authorized: StructureGen warns, discards all modded catalog claims, and falls back to its verified vanilla catalog. Unknown concrete block IDs and invalid property names or values therefore fail closed. If no current full-registry snapshot is available, a missing optional mod candidate may fall through to a registry-confirmed vanilla fallback, but an unresolved literal block, a role with no valid candidate, or an explicitly required mod must fail generation.

Refresh the snapshot after changing Minecraft/NeoForge, adding or removing a mod, changing a mod version, changing registry-affecting project code/resources, or changing registered blocks/states. The fingerprint prevents an old snapshot from silently authorizing removed content, but only `runData` can capture the new registry. `generateStructures` does not start a world or dynamically install/load missing mods.

### Decorative content versus gameplay systems

Third-party content may be selected automatically only when it genuinely serves a visual role such as architecture, furniture, props, lighting, storage, or restrained industrial detailing. Core architecture should normally remain vanilla or theme-driven, with a small coherent set of modded accents rather than one unrelated block from every installed mod.

Installed content never constitutes permission to create a functional system. In particular, StructureGen must not automatically build powered Create networks, moving contraptions, processing lines, or another mod's gameplay loop. A material or literal block with functional intent must name its `requiredSystem`, and that token must be explicitly present in `enabledFunctionalSystems`. Direct third-party literals cannot remain unclassified, which keeps automatic decorative selection on the semantic-material path.

Wilderness Odyssey special systems remain opt-in under the same rule. Keycard access, facility power, progression, quests, custom interactive machinery, special block entities, and custom gameplay devices are never enabled merely because their blocks are registered. The maintained first-party guard currently requires exact system tokens for known gameplay blocks: `anomaly_gateway` uses `wildernessodysseyapi:anomaly`; `cryo_tube` uses `wildernessodysseyapi:cryo_spawn`; rift cores/time capsules use `wildernessodysseyapi:temporal_rift`; and `wilderness_water_block` uses `wildernessodysseyapi:canonical_water`. A decorative semantic role skips these blocks and may use its safe fallback instead.

Registry tags, mod-provided tags, manually configured StructureGen roles, and simple namespace/path suggestions may help populate future theme metadata. Inferred categories remain suggestions. Name-based inspection categories do not prove that a block is decorative-safe, and an arrangement of individually valid blocks can still become functional through adjacency. StructureGen enforces explicit declarations and known first-party rules; Codex and reviewers must still verify that a decorative arrangement does not accidentally form a powered multi-block network.

## Creating a structure

1. Copy `src/main/structure_blueprints/test_shelter.json` to a new `.json` file in the same directory.
2. Choose a unique simple lowercase `name`.
3. Set `size` before adding blocks.
4. Add block records using local coordinates from `[0, 0, 0]` through `[sizeX - 1, sizeY - 1, sizeZ - 1]`.
5. Prefer a declared `$semantic_role` with a validated fallback chain for optional mod content. If a direct third-party literal is necessary, give it an explicit `usageIntent`.
6. Include every state property needed to reproduce an oriented or otherwise stateful candidate.
7. Add explicit `minecraft:air` only where placement must intentionally clear a block.
8. For functional literals or roles, add the exact `requiredSystem` to `enabledFunctionalSystems`; never use required-mod detection as functional permission.
9. Refresh the available-content snapshot before using newly added or updated mod content.
10. Run validation and generation before attempting to load the structure in Minecraft.

Blueprints are untrusted input. StructureGen rejects malformed JSON, unsupported format versions, invalid sizes, invalid names, out-of-bounds or duplicate coordinates, malformed resource locations, invalid block states where registry validation is available, malformed block-entity SNBT, unsafe paths, and collisions with hand-authored structures.

The standalone task bootstraps Minecraft's built-in registry, so vanilla block IDs and properties are checked exactly. If that bootstrap is unavailable, `minecraft:` blocks fail validation rather than compiling with weakened checks. Authored modded IDs are accepted only through a current loaded registry or a valid full-registry snapshot; StructureGen no longer treats an unknown modded namespace as safe merely because its resource-location syntax is valid.

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

The build workflow runs catalog refresh, focused StructureGen verification, and packaging as three separate Gradle invocations in that order. Keeping `runData` separate is required because an exact direct catalog-refresh invocation intentionally bypasses generated-structure resource gating; the later verification and build invocations restore that gate and consume the current snapshot.

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

The inspector reports values read from the selected file, including DataVersion, dimensions, bounding volume, stored block records, explicit air, non-air occupancy, palette entries, block-state variants, block entities, entities, frequently used blocks, concrete block usage by resource namespace, vertical distribution, inferred material categories, and unknown or unsupported tags. It writes machine-readable and text reports under `build/reports/structuregen/` when reporting is enabled.

Inspection numbers are deliberately not copied into this guide. The task output and generated reports are the authoritative results for the exact NBT being inspected.

### Mod and namespace reporting

Every inspection report groups the concrete block references actually stored in the structure by namespace. Each namespace receives two independent totals:

- unique block types, such as `create:industrial_iron_block` counting once; and
- stored block records, such as 200 placed industrial-iron blocks counting 200 times.

`minecraft` and `wildernessodysseyapi` are reported separately from sorted external namespaces. A namespace is not automatically treated as a mod ID: one mod can own multiple namespaces, and APIs may share content boundaries. The current catalog/reporting model records registry namespaces separately from loaded mod IDs; a future schema may add distinct authoritative owner IDs where NeoForge exposes them reliably.

For a generated structure with a verified content manifest, reporting additionally lists explicitly required mod IDs, enabled functional systems, resolved semantic roles, rejected candidates, and whether each role had a fallback. Manifest status is `verified`, `partial`, or `absent`; partial values are labeled incomplete rather than being silently defaulted to "none." When inspecting arbitrary third-party NBT that carries no StructureGen manifest, policy facts are **unknown**, not `false`; the inspector still reports concrete external namespaces accurately. Resource namespaces are never subtracted merely because the same spelling appears in `requiredMods`.

Example summary shape:

```text
Block usage by namespace (unique types / stored records):
  minecraft: 31 block types / 1842 records
  wildernessodysseyapi: 2 block types / 14 records
  create: 6 block types / 73 records

Content manifest status: verified (schemaVersion 1)
External namespaces used:
  create
Required external mod IDs:
  (none)
Semantic material resolutions:
  industrial_detail -> create:industrial_iron_block [decorative, preferred, fallback available: yes]
```

The first section is derived directly from the final concrete structure. The dependency and fallback sections come from validated generation provenance and must not be reconstructed from block-name heuristics.

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
- After a complete batch succeeds, obsolete `.nbt` files are removed recursively from StructureGen's build-owned singular and historical plural structure-output trees, so renamed, nested, or legacy generated resources cannot remain packaged or shadow later hand-authored structures. Other generated resource types are untouched, and failed batches do not perform this reconciliation.
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

### Required mod is unavailable

Refresh the full-registry snapshot with `.\gradlew.bat runData` and confirm that the exact mod ID appears in its `mods` array. If the mod is intentionally optional, remove it from `requiredMods` and supply a valid fallback for every semantic role that prefers its blocks. Do not make required functionality silently decorative or vanilla.

### Optional mod candidate did not resolve

Check the candidate's exact block ID and properties against `build/generated/structuregen/catalog/available-content.json`. Mod updates can rename blocks or change state properties. An unavailable optional candidate is safe only when the role resolves to another registered candidate; otherwise generation fails.

### Catalog fingerprint is stale

Run `.\gradlew.bat runData` as a separate invocation, then run `.\gradlew.bat generateStructures`. StructureGen refuses modded claims from a catalog produced by different project sources, common runtime artifacts, or local `run/mods` content. Do not edit the fingerprint or catalog by hand.

### Installed mod blocks are disabled

When `allowInstalledModBlocks` is `false`, semantic roles skip third-party candidates and use approved Minecraft or Wilderness Odyssey fallbacks. A literal third-party reference is not silently rewritten because no fallback intent is available; use a semantic role if the blueprint must work in both configurations.

### Third-party literal has no usageIntent

Prefer a semantic role with a fallback for automatically selected decoration. If the concrete block is intentional, add `"usageIntent": "decorative"`. For functional use, declare `"usageIntent": "functional"`, add an exact namespaced `requiredSystem`, and include the same token in `contentPolicy.enabledFunctionalSystems`.

### Functional system was not enabled

Installed or required mods do not authorize gameplay. Add the exact system token only when the structure request explicitly calls for that functional integration. Do not relabel functional machinery as decorative to bypass validation; known Wilderness Odyssey gameplay blocks are independently guarded.

### Malformed blockEntitySnbt

The value must be a JSON string containing one valid SNBT compound. Check both JSON escaping and SNBT types/suffixes. Non-vanilla literal block-entity data also requires functional usage intent and an explicitly enabled system because StructureGen cannot prove an arbitrary third-party payload is decoration-only.

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
- Mod-aware resolution is build-environment-specific. The generated NBT stores the selected concrete block IDs, so an already-built JAR cannot switch to a fallback when its runtime mod set changes. Regenerate resources for each target mod environment; future runtime/worldgen phases need conditional variants or runtime resolution for one artifact to adapt dynamically.
- The registry snapshot is an explicit capture, not live dependency introspection. Its deterministic fingerprint prevents reuse after the tracked source/dependency environment changes, but offline generation still cannot discover new registrations until `runData` captures them.
- Automatic selection is intentionally conservative. Semantic roles, explicit literal intent, exact functional tokens, and maintained first-party rules enforce author intent; no static tool can prove that an arbitrary arrangement of third-party mechanical blocks is topologically inert.
- A Blueprint exported from arbitrary NBT cannot recover original fallback chains or per-position functional intent. Unclassified modded literals intentionally require developer review before re-import; when any functional system was enabled, the exporter conservatively declines to infer decorative intent from a matching resolved state elsewhere in the structure.
- Blueprint v1 resolves declared semantic material chains but does not perform complicated AI classification of every block in an installed modpack.
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

### Mod-aware content rule across the existing roadmap

This rule extends the roadmap above; it does not remove, replace, reorder, or redefine its phases.

- **Phase 2:** Modules, themes, deterministic transforms, and composition may use compatible installed blocks through semantic roles and validated fallback chains.
- **Phase 3:** Third-party gameplay integrations still require explicit functional intent. Detecting a mod or using its decorative blocks never opts a structure into its power, automation, movement, processing, quest, or progression systems.
- **Phase 4:** World-generation structures may contain optional modded decoration only when dependency and fallback behavior is safe for the target runtime. A prebuilt concrete NBT cannot provide a runtime fallback by itself.
- **Phase 5:** Advanced facilities may use broader mod-aware visual palettes while retaining a coherent theme, restrained dependency footprint, and separate opt-in for Wilderness Odyssey special systems.
- **Phase 6:** A future Structure Director should inspect the authoritative available-content catalog before proposing a design and should prefer core vanilla/theme architecture with selective modded detail.
- **Phase 7:** Developer tools should display concrete namespaces, explicitly declared mod dependencies, semantic selections, rejected candidates, and fallback status; where NeoForge later exposes reliable registration ownership, they should add supplying-mod IDs without inferring them from namespaces.
- **Phase 8:** Quality tooling should detect unnecessary external dependencies, excessive namespace count, palette complexity, incoherent decoration, and accidental functional-system use.

Across every phase, a referenced concrete block state must exist in the selected registry/catalog. Missing optional content uses an explicitly declared valid fallback; missing required content fails clearly. No phase may restore warning-only compilation of unknown modded IDs.
