
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
