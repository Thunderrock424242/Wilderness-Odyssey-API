
Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources:
==========
Community Documentation: https://docs.neoforged.net/
NeoForged Discord: https://discord.neoforged.net/

Loading Stall Detector:
-----------------------
If the loading screen stays up for 10+ minutes (common with 200+ mod packs), the mod now writes a snapshot to `logs/loading-stalls/`.
Each report includes a thread dump and the active mod list so you can spot which thread/mod was executing when the hang occurred.
Use `-Dwilderness.loadingstall.minutes=5` (for example) to lower the timeout; the suspects section lists the jar path for the top threads to speed up mod identification during loader hangs.

Client VRAM Profiler:
---------------------
Use the opt-in `/wovram` client command to correlate VRAM increases with live OpenGL allocations, mod IDs, resources, and Java source lines:

1. Run `/wovram start` immediately before reproducing the increase.
2. Run `/wovram top` to list the largest live allocation sites.
3. Run `/wovram snapshot before_action`, reproduce one action, then run `/wovram diff`.
4. Run `/wovram gpu` to rank mods by sampled GPU time, including other mods when their render stack is still present.
5. Run `/wovram errors` for OpenGL driver/shader messages and `/wovram leaks` for Wilderness Odyssey render-state leaks.
6. Run `/wovram stop` and `/wovram export` to write detailed `.json` and flamegraph-compatible `.folded` reports under `logs/wildernessodysseyapi/gpu-profiler/`.

While a session is active, the F3 system panel shows the driver VRAM delta, top tracked allocations, sampled draw counts, driver messages, state leaks, and the current top GPU-time mod. NVIDIA and AMD OpenGL memory extensions are sampled when available. KHR_debug messages and asynchronous timestamp-query timings are enabled only when supported and do not replace another mod's existing debug callback. The JSON export includes mod rollups, detailed callsites, and folded Java stacks weighted by sampled GPU nanoseconds for flamegraph tooling.

Per-object byte counts are logical estimates because drivers may compress, align, share, cache, or delay releasing GPU resources. GPU-time attribution is sampled to keep profiler overhead bounded. Direct render calls can normally be mapped to a mod JAR and source line; geometry combined into a shared Minecraft batch is intentionally reported as shared because assigning the whole batch to one contributing mod would be misleading. Raw LWJGL calls that bypass Minecraft's wrappers can still be absent.

Discord-to-Minecraft Playtest Verification:
-------------------------------------------
On the official playtest server, players can link their Minecraft identity through the private Discord verification relay:

1. In Discord, run `/minecraft link`.
2. Copy the one-time code returned by the bot.
3. On the playtest server, run `/wo link CODE`.

The server config lives at `config/wildernessodysseyapi/wildernessodysseyapi-server.toml`
under `[verificationRelay]`:

```toml
[verificationRelay]
enableServerVerificationRelay = true
discordVerificationWebhookUrl = ""
requestTimeoutSeconds = 10
```

The server sends a Discord webhook message to the private verification relay channel. The payload contains only the one-time code, Minecraft UUID, and Minecraft profile name. No Discord bot token is stored in the mod, and the webhook URL belongs only in server config, never in client config.

Multithreaded Task System:
--------------------------
An opt-in async task system now ships with the mod. Enable or tune it in
`config/wildernessodysseyapi/wildernessodysseyapi-common.toml` under `[asyncThreading]`.
Use `/asyncstats` (level 2 permission) to view worker usage, queue depth, and rejected tasks. See `docs/async-threading-plan.md`
for architecture and tuning notes, including guidance on keeping main-thread mutations safe when scheduling heavy jobs.

A.E.T.H.E.R scripted companion (current MVP):
-------------------------------------
AI purpose checklist: see `docs/ai/purpose-scope.md` for A.E.T.H.E.R core + subsystem scope, boundaries, and MVP definition.

A.E.T.H.E.R is currently a scripted recovered-intent system, not a full LLM chatbot. Player messages are cleaned, scored against intent keywords, checked against lightweight game context, and answered from authored response banks. This keeps the first version safer, cheaper, lore-consistent, and easier to expand through updates.

Scripted response data lives in:
- `src/main/resources/ai_config.yaml`
- `src/main/resources/ai_fallback/aether.yaml`
- `src/main/resources/ai_fallback/aegis.yaml`
- `src/main/resources/ai_fallback/eclipse.yaml`
- `src/main/resources/ai_fallback/terra.yaml`
- `src/main/resources/ai_fallback/helios.yaml`
- `src/main/resources/ai_fallback/enforcer.yaml`
- `src/main/resources/ai_fallback/requiem.yaml`

A.E.T.H.E.R uses only authored local responses and does not call an online AI service. The old backend management commands were removed so the CurseForge build stays scripted-only.
Secrets:
-------
For local development, copy `.env.example` to `.env` and fill in required tokens.

For CI workflows, add secrets in GitHub's repository Secret Manager (Settings → Secrets and variables → Actions) and reference them in workflow files as `${{ secrets.NAME }}`.

CodeQL:
-------
GitHub's CodeQL workflow runs on every push and pull request.
It analyzes both Java sources under `src/main/java` and the repository's GitHub Actions workflows.

Spawn Behavior:
----------
Players spawn inside a cryo tube when joining the world for the first time. Leaving the tube prevents re-entry.
On first join, each player is assigned a random cryo tube and teleported directly into it.

Custom Assets
-------------
Blockbench models and textures for blocks should be placed under:
`src/main/resources/assets/wildernessodysseyapi/models/block` and `src/main/resources/assets/wildernessodysseyapi/textures/block` respectively.
You can replace the placeholder cryo tube files with your own to customize the look.

World Generation
----------------
Meteor impact terrain is data-pack driven through the
`wildernessodysseyapi:meteor_impact` configured and placed feature. Its
Overworld injection lives at
`data/neoforge/biome_modifier/add_meteor_impact.json`, so a datapack can replace
the placement frequency or biome selection without a Java registration hook.

The legacy `c:impact_site` structure tag and
`wildernessodysseyapi:has_structure/impact_zone` biome tag remain available as
compatibility aliases for existing packs. They do not register or generate an
`impact_zone` structure in the current mod.

Modpack Structure Staging (drop-in NBT)
-----------------------------------------
For a step-by-step flow that starts from `config/wildernessodysseyapi/modpack_structures/*.nbt` and scaffolds a full datapack layout, see `docs/modpack/structure-registry.md`.

Using Data Pack Structures
--------------------------
The mod now loads vanilla structure templates from data packs using the
standard `data/<namespace>/structures/<path>.nbt` layout. Reference the
structure by its namespace and path (without the `.nbt` extension) to have it
placed during world generation. If no data pack override exists, the bundled
templates under `data/<namespace>/structures/` in the mod resources are used.

The meteor feature does not currently consume an `impact_zone` structure
template. A datapack that adds a structure-based impact site must register its
own structure, structure set, and placement integration; the retained legacy
tags alone do not create that world-generation route.

Loot tables defined inside the structure templates work the same way as vanilla
NBT structures. The scanner now reads loot table references directly from the
template data so datapacks can supply their own chest contents.

Managed server.properties template
--------------------------
The mod now maintains `config/wildernessodysseyapi/server.properties` as a pack-shippable template and also mirrors a world-local copy at `<world>/server.properties.wildernessodyssey`.

- On first dedicated-server boot, the global managed file is created from bundled defaults.
- A world-local template is created from the global template (or current live file if needed).
- On startup, the world-local template is preferred and synced into the live root `server.properties`, with a timestamped backup of the previous live file.
- If the bundled template in the mod jar changes after an update, managed global/world templates are automatically replaced so new defaults switch over.
- This means the managed settings can travel with the world folder when moving that world to another server.
- Synced values still apply on the next restart because Minecraft reads `server.properties` before mod startup.

Ship your own defaults with `config/wildernessodysseyapi/server.properties`, or distribute a preconfigured world containing `<world>/server.properties.wildernessodyssey`.

World Generation Diagnostics
--------------------------
Use `/worldgenscan <radius>` to count nearby structures, features, and biomes and identify which mods add them.

For a roadmap on chunk lifecycle, async I/O, and networking improvements, see `docs/chunk-system-improvements.md`.
