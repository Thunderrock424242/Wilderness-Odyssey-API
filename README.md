
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

Global Chat:
------------
See `docs/global-chat.md` for how to anchor the relay to a dedicated host, bind participating servers, opt players in, secure membership with a cluster token, and run moderation commands.

Multithreaded Task System:
--------------------------
An opt-in async task system now ships with the mod. Enable or tune it in `config/wildernessodysseyapi/wildernessodysseyapi-async.toml`.
Use `/asyncstats` (level 2 permission) to view worker usage, queue depth, and rejected tasks. See `docs/async-threading-plan.md`
for architecture and tuning notes, including guidance on keeping main-thread mutations safe when scheduling heavy jobs.

AI Helper Dependencies (Optional):
-------------------------
The build already includes lightweight HTTP/JSON and fault-tolerance libraries for optional AI advisors:
- OkHttp (HTTP client)
- Moshi (JSON serialization)
- Resilience4j Circuit Breaker

These are pulled from Maven Central and can be used by any local or sidecar AI helper you run alongside the mod. The game still runs normally if you choose not to enable an AI helper.

Self-hosted AI chatbot (free/offline)
-------------------------------------
Atlas already ships with a local, offline chatbot pipeline—no external services or API keys are needed. The AI client loads its lore, survival tips, and wake word from `src/main/resources/ai_config.yaml` at server startup and generates deterministic replies in code without calling any cloud model. If you want to retheme it, edit the `story`, `survival_tips`, or `wake_word` entries in that config file and restart the integrated server. The core class that powers this behavior is `src/main/java/com/thunder/wildernessodysseyapi/AI_story/AIClient.java`, which notes that all data is loaded from resources and "no external service or hosting is required." Running the mod on a local or integrated server therefore gives you a self-hosted chatbot for free; you can also layer your own sidecar process that speaks HTTP using the bundled OkHttp/Moshi libs if you prefer another local model.
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
Set `bunker.debugIgnoreCryoTubeSpawns` to `true` in the common config when testing to bypass the cryo tubes and drop
players at a random safe location inside the bunker instead.

Custom Assets
-------------
Blockbench models and textures for blocks should be placed under:
`src/main/resources/assets/wildernessodysseyapi/models/block` and `src/main/resources/assets/wildernessodysseyapi/textures/block` respectively.
You can replace the placeholder cryo tube files with your own to customize the look.

World Generation
----------------
The bunker now spawns via the normal world generation pipeline. Two config options
control its frequency:
`bunker.spawnDistanceChunks` sets the minimum chunk distance between bunkers and
`bunker.maxSpawnCount` limits how many bunkers can generate per world. Bunker structure templates are
validated to ensure at least one cryo tube is present, and any missing tubes are restored after placement.

Multiple meteor impact zones are generated the first time a new world loads, each roughly 1,000 chunks (16,000 blocks) apart to encourage long-range exploration. A single bunker is placed adjacent to one of these craters to serve as the player's first destination.
Secret Order villages may rarely appear in jungle biomes, using the bundled structure template.

Using Data Pack Structures
--------------------------
The mod now loads vanilla structure templates from data packs using the
standard `data/<namespace>/structures/<path>.nbt` layout. Reference the
structure by its namespace and path (without the `.nbt` extension) to have it
placed during world generation. If no data pack override exists, the bundled
templates under `data/<namespace>/structures/` in the mod resources are used.

The meteor impact site looks for the `wildernessodysseyapi:impact_zone`
template. Drop your finished build at
`data/wildernessodysseyapi/structures/impact_zone.nbt` so the crash craters are
pasted before the bunker generates, or bundle a structure in another namespace
and update the configuration to match.

Loot tables defined inside the structure templates work the same way as vanilla
NBT structures. The scanner now reads loot table references directly from the
template data so datapacks can supply their own chest contents.

World Generation Diagnostics
--------------------------
Use `/worldgenscan <radius>` to count nearby structures, features, and biomes and identify which mods add them.
