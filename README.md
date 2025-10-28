
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
