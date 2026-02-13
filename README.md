
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
See `docs/global-chat-beginner.md` for a quickstart on hosting the relay, binding servers, and getting players talking.
Read `docs/global-chat.md` for the full operations guide (anchoring, cluster tokens, moderation, opt-in controls, and whitelisting external tools).

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

Self-hosted AI chatbot (custom local LLM required)
-------------------------------------
Atlas now requires a local custom LLM backend for runtime responses. There is no deterministic offline fallback path in chat mode.

Local LLM support is sidecar-based:
- This repo does **not** bundle custom-trained LLM model weight files.
- You must run/provide a compatible local model server (for example an Ollama-style `/api/generate` endpoint).
- `local_model.base_url` is honored (defaults to `http://127.0.0.1:11434` if omitted).
- `local_model.auto_start` can launch a configured sidecar command/resource, but only if you provide that executable.
- For a better packaging workflow, you can set `model_download_url`, `model_download_sha256`, and `model_file_name` so the model artifact is bootstrapped into `config/wildernessodysseyapi/local-model/models/` at runtime.
- You can use `{model_path}`, `{model_name}`, and `{base_url}` placeholders in `start_command` / `bundled_server_args`.

If the local model is unavailable, Atlas returns a backend-unavailable message and instructs operators to restore the local model service.

Admin commands for sidecar operations:
- `/atlasbackend status`
- `/atlasbackend probe`
- `/atlasbackend start`
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
For a step-by-step flow that starts from `config/wildernessodysseyapi/modpack_structures/*.nbt` and scaffolds a full datapack layout, see `docs/modpack-structure-registry.md`.

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

World Generation Diagnostics
--------------------------
Use `/worldgenscan <radius>` to count nearby structures, features, and biomes and identify which mods add them.

For a roadmap on chunk lifecycle, async I/O, and networking improvements, see `docs/chunk-system-improvements.md`.
