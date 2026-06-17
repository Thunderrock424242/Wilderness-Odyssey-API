
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

Global Chat:
------------
See `docs/globalchat/beginner.md` for a quickstart on hosting the relay, binding servers, and getting players talking.
Read `docs/globalchat/operations.md` for the full operations guide (anchoring, cluster tokens, moderation, opt-in controls, and whitelisting external tools).

Discord-to-Minecraft Playtest Verification:
-------------------------------------------
On the official playtest server, players can link their Minecraft identity through the private Discord verification relay:

1. In Discord, run `/minecraft link`.
2. Copy the one-time code returned by the bot.
3. On the playtest server, run `/wo link CODE`.

The server config lives at `config/wildernessodysseyapi/wildernessodysseyapi-verification-relay-server.toml`:

```toml
[verificationRelay]
enableServerVerificationRelay = true
discordVerificationWebhookUrl = ""
requestTimeoutSeconds = 10
```

The server sends a Discord webhook message to the private verification relay channel. The payload contains only the one-time code, Minecraft UUID, and Minecraft profile name. No Discord bot token is stored in the mod, and the webhook URL belongs only in server config, never in client config.

Multithreaded Task System:
--------------------------
An opt-in async task system now ships with the mod. Enable or tune it in `config/wildernessodysseyapi/wildernessodysseyapi-async.toml`.
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

The optional local-model sidecar remains available for a future funded version, but `local_model.enabled` and `local_model.auto_start` default to `false`. This repo does **not** bundle custom-trained LLM model weight files.

Future sidecar admin commands:
- `/aetherbackend status`
- `/aetherbackend probe`
- `/aetherbackend start`

The old `/atlasbackend` command remains registered as a compatibility alias.
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
An introductory title sequence plays as they wake, which can be replaced with a custom cinematic in the future.
On first join, each player is assigned a random cryo tube and teleported directly into it.

Custom Assets
-------------
Blockbench models and textures for blocks should be placed under:
`src/main/resources/assets/wildernessodysseyapi/models/block` and `src/main/resources/assets/wildernessodysseyapi/textures/block` respectively.
You can replace the placeholder cryo tube files with your own to customize the look.

World Generation
----------------
Impact zones are fully data pack driven. The bundled `impact_zone` structure, structure set, and template pool live under `data/wildernessodysseyapi/worldgen/`, and you can drop additional impact zone structures or tweak spacing in a datapack without any Java hooks.

Multiple impact zones can now exist—add more structure set entries via datapack to control how many spawn and where.

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

For a datapack-only workflow that still keeps the wool height markers you
mentioned, drop replacement structures under `data/<namespace>/structures/` in your datapack and override the bundled `impact_zone` worldgen JSON.

The meteor impact site looks for the `wildernessodysseyapi:impact_zone`
template. Drop your finished build at
`data/wildernessodysseyapi/structures/impact_zone.nbt` or ship additional impact zone structures in another namespace and update the structure set JSON to include them.

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
