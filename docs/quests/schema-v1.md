# Quest definition schema v1

Phase 1 implements this immutable candidate format, publication and Minecraft adapters. Candidate validation alone does not activate a campaign or grant progress/rewards. Existing-world activation requires an accepted publication; a valid candidate may seed a genuinely unused store after server-thread rechecks. See [implementation and validation](phase-1-validation.md) for evidence and remaining acceptance work.

Resource paths define portable identities:

- `data/<namespace>/wo_quests/campaigns/<path>.json`
- `data/<namespace>/wo_quests/chapters/<path>.json`
- `data/<namespace>/wo_quests/quests/<path>.json`

Each source identity includes its kind and namespaced ID. A chapter and a quest may share an ID without overwriting one another. A publication contains exactly one campaign. Campaign-to-chapter and chapter-to-quest membership must agree in both directions. Unknown fields and unsupported schema versions fail validation. `definitionVersion` is a positive author-controlled integer; it is separate from schema and publication identity.

Campaign fields: `schemaVersion`, `definitionVersion`, `title`, optional `description`, and `chapters` (namespaced IDs). Chapter fields: `schemaVersion`, `definitionVersion`, `campaign`, `title`, `quests`, optional `requiredMods` and `optionalMods` (lowercase mod IDs). A missing required mod blocks publication. A missing explicitly optional mod disables its chapter while retaining its definitions. Structural checks still apply to disabled chapters.

Quest fields: `schemaVersion`, `definitionVersion`, `chapter`, `title`, optional `description`, `icon` (item ID, default `minecraft:book`), `optional` (default true), `hidden` (default false), `repeatable` (default false), `cooldownTicks` (default zero), `prerequisites`, `objectives` and optional `rewards`. A cooldown is allowed only on a repeatable quest. Prerequisites use `mode: "all"` or `"any"` and a `quests` list. Quest and objective dependency graphs must be acyclic.

Every objective has a stable namespaced `id`, a `type`, optional positive integer `goal` (default one), and optional `dependsOn` objective IDs within the same quest:

| Type | Parameters | Evidence contract |
| --- | --- | --- |
| `possession` | `items` and/or `tags` lists | Complete server inventory observation; union matching counts each stack once |
| `dimension` | `dimension` ID; goal one | Server-confirmed dimension presence |
| `lore` | `lore` ID; goal one | Existing lore unlock, mapped to its namespaced ID |
| `custom_event` | `producer` ID | A registered trusted server producer; availability is required |

Every reward has a stable namespaced `id` and one supported type: `item` (`item`, `count` 1–64), `xp` (`amount` 1–1,000,000), or `lore` (`lore`). Reward commands and arbitrary item NBT are unsupported. Earned values and protected delivery are handled by the later reward foundation, not by this codec.

This quest is the hand-authored passing fixture at `src/test/resources/quests/valid_campaign/quest.json`. Its campaign lists `test:start`; that chapter belongs to `test:campaign` and lists `test:first`:

```json
{
  "schemaVersion": 1,
  "definitionVersion": 1,
  "chapter": "test:start",
  "title": "Prepare for travel",
  "description": "Keep supplies, visit the Nether and recover a record.",
  "icon": "minecraft:compass",
  "optional": true,
  "hidden": false,
  "repeatable": false,
  "cooldownTicks": 0,
  "prerequisites": {"mode": "all", "quests": []},
  "objectives": [
    {"id": "test:supplies", "type": "possession", "goal": 4, "items": ["minecraft:oak_log"], "tags": ["minecraft:logs"]},
    {"id": "test:travel", "type": "dimension", "dimension": "minecraft:the_nether"},
    {"id": "test:record", "type": "lore", "lore": "wildernessodysseyapi:lore_001"},
    {"id": "test:signal", "type": "custom_event", "producer": "test:beacon", "goal": 2, "dependsOn": ["test:record"]}
  ],
  "rewards": [
    {"id": "test:compass", "type": "item", "item": "minecraft:compass", "count": 1},
    {"id": "test:experience", "type": "xp", "amount": 5},
    {"id": "test:memory", "type": "lore", "lore": "wildernessodysseyapi:lore_002"}
  ]
}
```

This test fixture requires a registered `test:beacon` producer and lore availability supplied by the test lookup. It is not packaged production story content.

Limits are 2,000 quests, 128 chapters, 32 objectives and 16 rewards per quest, 4,096 description characters, 256 title characters, 256 KiB per source, 16 MiB total source/snapshot bytes, 64 JSON nesting levels and 65,536 JSON values per source. Input is strict UTF-8 JSON with duplicate fields rejected. Counts must be numeric integers; fractional, negative and overflowing goals fail validation.

Canonical snapshots sort source identities and object keys while retaining array order. SHA-256 identifies canonical content; runtime registry generation and pack labels do not alter that content identity. Reordering sources/object keys cannot create a new publication hash. Source/model collections are defensively copied.

Run focused codec/validation checks with JDK 21 using the checked-in wrapper:

```powershell
.\gradlew.bat questTest --tests '*QuestDefinitionCodecTest' --tests '*QuestValidatorTest' '-PcodexBuildDir=.codex-build' --no-parallel
```

These are offline definition checks. They do not prove resource reloads, multiplayer, UI, real reward delivery or loaded-world persistence.
