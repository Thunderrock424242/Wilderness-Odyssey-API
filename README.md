
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

Spawn Behavior:
----------
Players spawn inside a cryo tube when joining the world for the first time. Leaving the tube prevents re-entry.
An introductory title sequence plays as they wake, which can be replaced with a custom cinematic in the future.

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
`bunker.maxSpawnCount` limits how many bunkers can generate per world.

The meteor impact zone structure is generated when a new world loads, creating a crater and a bunker nearby.
Secret Order villages may rarely appear in jungle biomes, using the bundled schematic.

Using Data Pack Schematics
-------------------------
The mod now supports loading WorldEdit `.schem` files from data packs in the same
way vanilla handles structure NBTs. Place your schematics under
`data/<namespace>/structures/` inside a data pack. Referencing the structure by
its namespace and path (without the `.schem` extension) will cause it to be
loaded from the data pack when structures generate. If a matching data pack file
is not found, the bundled schematic under
`assets/<namespace>/schematics/` is used instead.

Loot tables defined inside schematics work the same way as with vanilla
`nbt` structures. The scanner now detects loot table references in both
`.nbt` and `.schem` files so datapacks can supply their own chest contents.

World Generation Diagnostics
--------------------------
Use `/worldgenscan <radius>` to count nearby structures, features, and biomes and identify which mods add them.
