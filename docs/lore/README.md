# Wilderness Odyssey lore v1.0: API alignment

Reviewed 2026-11-02. This is the API repository; findings describe inspected source,
not fresh runtime acceptance or the implementation state of the separate modpack.

- [Core Lore](WILDERNESS_ODYSSEY_CORE_LORE.md) is the binding story foundation.
- [Expanded Lore](WILDERNESS_ODYSSEY_EXPANDED_LORE.md) supplies supporting context.
  Only Established material is canon; Concept and Open material is not approved work.
  Core takes precedence in a conflict.

Both references were copied intact from the supplied files. Keep the same complete
pair in the main modpack repository; repository-specific findings belong outside
them. This task adds these references, this report, and links in the root README
and AGENTS.md. It changes no gameplay, prompts, quests, configuration, or worldgen.

## Conflicts and proposals for review

These are review proposals, not approved story changes or instructions to rewrite
systems. Older design notes do not override Core Lore.

| Finding | Exact source locations | Proposed follow-up for review |
| --- | --- | --- |
| **Story conflict:** the old Echo account makes an experiment directly connect two Earths and force them toward synchronization. Core sections 1, 5 and 7 instead establish the meteor's unique rift, accidental evacuation to Echo Earth and later controlled reconnection. A pre-contact planet is compatible; an independent human civilization or mandatory world convergence is not established. | `docs/temporalrift/the-echo.md:3` and `:9` | Revise the old narrative description to the canonical sequence after review. Do not silently reinterpret or remove the existing dimension. |
| **Unapproved supporting history:** prompt defaults assert Atlas's military origin and Eden/cryo role, and say Atlas redacted the origin of plural fractures. Expanded section F treats Atlas as a concept whose role needs a decision. Aether's expedition framing also omits its established Eden preservation role, although the cryo narration already describes facility care and damaged memory. | `src/main/resources/ai_config.yaml:1`, `:6`, `:12`, `:94`; `aether-server/src/main/resources/aether-prompts.yml:1`, `:6`, `:12`, `:94`; `docs/ai/purpose-scope.md:3` and `:17` | Review prompt canon and introductory wording separately. Keep Atlas's origin/role unresolved until approved; preserve Aether's uncertainty and evidence boundaries. The local ignored `src/main/resources/aether/aether-prompts.yml` repeats this text and would need an operator-managed review too. No local behavior file was changed. |
| **Unapproved narrative mechanism:** player discoveries and gateway context present material synchronization and offer a world-alignment hypothesis. Core does not establish that mechanism; the hypothesis is explicitly uncertain, but the underlying material transfer is implemented. | `src/main/java/com/thunder/wildernessodysseyapi/temporalrift/echo/EchoDiscoveryStage.java:12` and `:13`; `aether-server/src/main/java/com/thunder/aether/server/model/PromptCatalog.java:117` and `:120`; `docs/temporalrift/the-echo.md:3` | Decide how these existing effects fit the setting without declaring forced convergence canonical. Retain evidence gating; no gameplay or saved discovery migration is authorized here. |
| **Conditional single-rift conflict:** the scheduler repeatedly opens/closes sites, broadcasts that a fracture opened, and records major fracture locations. Technical anomaly effects and unrelated travel are allowed; portraying each as another true cosmological rift would contradict Core section 1. | `src/main/java/com/thunder/wildernessodysseyapi/temporalrift/TemporalRiftManager.java:50`, `:104`, `:130`, `:178`; `docs/temporalrift/the-echo.md:59`; `docs/temporalrift/hybrid-rifts.md` (Server authority and lifecycle) | Decide the narrative classification and player-facing wording for these mechanics. Do not delete portals, The Before, anomaly gateways, or other travel to enforce the lore rule. Hybrid STABILIZED entry currently supplies a hook, not an automatic destination or story unlock. |
| **Content integration constraint, not proof of a lore contradiction:** Echo currently removes villagers, wandering traders and zombie villagers. That blocks using those vanilla entities as inhabitants, but does not establish that all possible human settlements are absent. Core section 5 requires varied human communities. | `src/main/java/com/thunder/wildernessodysseyapi/temporalrift/EchoDimensionEvents.java:48`; `src/main/java/com/thunder/wildernessodysseyapi/temporalrift/config/TemporalRiftConfig.java:126`; `docs/temporalrift/the-echo.md:88` | Choose an approved settlement representation in a future content task before changing entity rules. Do not generate NPCs now. |
| **Overcommitted content guidance:** the player guide connects Echo Earth to prehistoric incursions, lists major wormhole locations, and describes Reservoir-9 as a lethal investigation. Those specifics are not established by the references; multiple true rift sites would conflict with Core. Reservoir-9's water/research identity must remain distinct from the dimensional experiment. | `WILDERNESS_ODYSSEY_PLAYER_EXPERIENCE_GUIDE.md:90`, `:91`, `:114`, `:158` | Mark unsupported details as proposals in a later review; do not assume lethal conditions, additional cosmological passages or a new origin for Reservoir-9. Preserve optional exploration and restoration. |

The targeted review covered the root player guide, AI and temporal/anomaly/riftfall
docs, quest docs/examples, prompt/fallback resources, lore-book defaults, English
cryo narration, and the corresponding AI, lore, quest and temporal code. No
additional named-story contradiction was found in the inspected quest examples
or placeholder `src/main/resources/config/wildernessodysseyapi/lore_books.json`.
This is not an exhaustive audit of binary structures, every translation, external
datapacks, live server configuration, or the separate modpack repository.

## Existing API foundations and future opportunities

Paths below are repository-relative. "Existing" means source is present; it does
not mean the complete canonical story or a new integration has been implemented.

| Area | Existing implementation | Future opportunity; not implemented or approved by this task |
| --- | --- | --- |
| Aether | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/AIChatListener.java` captures server-owned lore/discovery context and schedules external I/O; `ai/story/provider/AetherBackendClient.java` under the same base package handles gateway requests. The gateway prompt catalog and scripted fallbacks already separate evidence from missing records. See [AI ownership](../ai/purpose-scope.md). | Review prompt facts against Core and disclose only legitimately recovered history. Copying Markdown does not load it into the model or update deployed prompts. |
| Narrative Engine | No named Narrative Engine component/interface was found in the inspected production source/docs. Existing foundations include `src/main/java/com/thunder/wildernessodysseyapi/cinematic/sequence/CryoWakeupSequence.java`, lore discovery, and the quest runtime. | A future narrative coordinator could consume those foundations. There is no implemented canonical chronology coordinator or approved new interface contract here; AI dialogue must not decide authoritative progress. |
| World events | `src/main/java/com/thunder/wildernessodysseyapi/temporalrift/hybrid/RiftProximityEvent.java` and `RiftInteractionEvent.java` in that directory expose server hooks. `src/main/java/com/thunder/wildernessodysseyapi/quest/integration/QuestCustomEventRegistry.java` accepts registered trusted producers with session/occurrence checks on the server thread. | Future approved research/communications events could emit trusted quest evidence. These hooks do not establish the unique rift's story identity, permission for transit, or a completed research sequence. |
| Persistent discoveries | `src/main/java/com/thunder/wildernessodysseyapi/lorebook/LoreBookManager.java` owns collected record IDs and Codex writing; `src/main/java/com/thunder/wildernessodysseyapi/temporalrift/echo/EchoDiscoveryManager.java` persists independent evidence. `src/main/java/com/thunder/wildernessodysseyapi/quest/world/QuestWorldSavedData.java` stores shared facts, and `quest/progress/QuestPlayerProgressStore.java` under the same base package stores player progress. | Approved Eden/Reservoir-9 records and research findings could use these owners. Personal journal writing or AI memory must not grant canonical discoveries. Current Echo evidence wording needs the review above. |
| Quests | `src/main/java/com/thunder/wildernessodysseyapi/quest/runtime/QuestServerEvents.java` and `QuestRuntime.java` provide server-owned evaluation. `src/main/java/com/thunder/wildernessodysseyapi/quest/integration/LoreQuestBridge.java` maps existing lore unlocks. [Schema v1](../quests/schema-v1.md) supports possession, dimension, lore and trusted custom events; the [Workshop](../quests/phase-2-validation.md) supplies authoring tools. | Future authored campaigns could track research and reconnection. Technical examples and generic dimension objectives are not story quests, and a dimension visit alone must not stand in for three-world communication or authorize physical transit. |

## Unresolved decisions and content boundaries

- Keep Aether's preservation priority and the awakening signal's source/purpose
  unresolved. The contaminated preservation-medium narration is not an explanation
  of the still-open biological exposure route; closed cooling pipes alone do not
  establish one.
- Meteor origin/physics, exact dates, evacuation numbers, settlement names and
  politics, Atlas's role, memorial details and companions' later fates remain
  decisions for separate review. Beal and Nathan remain historical figures; early
  Earth records must preserve the boarding mystery.
- Eden restoration, Reservoir-9 archives and environmental storytelling are future
  content consumers of existing systems, not completed locations/campaigns proven
  by this audit. Keep the ordinary civilian's pre-impact identity flexible.
- The main milestone is reliable communication and regulated scientific contact
  among Earth, Mars and Echo Earth. It does not end/reset a save, approve routine
  human travel, resolve the unknown signal, or replace the council's initial travel
  restrictions. Later limited transit needs separate research/content decisions.
- Reuse the one original lore-level rift for canonical reconnection. Vanilla
  portals/dimensions and unrelated gameplay travel remain available; naming a
  technical effect "rift" does not by itself settle its cosmological meaning.

## Verification scope

Documentation checks for this task are byte equality/SHA-256 against both supplied
references, local link/path checks, `git diff --check`, and review of the changed
file list. Gradle, data generation and Minecraft launches are unnecessary for this
documentation-only change and were not run. No new gameplay validation is claimed.
Cross-repository equality still requires copying/checking this same source pair in
the separate main modpack checkout.
