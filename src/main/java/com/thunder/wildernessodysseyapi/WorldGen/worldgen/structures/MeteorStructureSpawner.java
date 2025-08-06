package com.thunder.wildernessodysseyapi.WorldGen.worldgen.structures;

import net.minecraft.core.BlockPos;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.BunkerProtectionHandler;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.StructureSpawnTracker;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Worldedit.WorldEditStructurePlacer;
import com.thunder.wildernessodysseyapi.WorldGen.worldgen.structures.MeteorImpactData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;
import net.neoforged.fml.ModList;

/****
 * MeteorStructureSpawner for the Wilderness Odyssey API mod.
 */
public class MeteorStructureSpawner {
    private static boolean placed = false;

    public static void tryPlace(ServerLevel level) {
        if (!ModList.get().isLoaded("worldedit")) return;
        // Only place once in the entire world
        if (placed) return;
        placed = true;

        // Decide where to place the meteor
        BlockPos spawnPos = new BlockPos(0, level.getHeight(), 0);
        MeteorImpactData.get(level).setImpactPos(spawnPos);

        ResourceLocation structureID = ResourceLocation.tryBuild(MOD_ID, "meteor_bunker");
        StructureTemplateManager manager = level.getStructureManager();
        Optional<StructureTemplate> templateOpt = manager.get(structureID);

        if (templateOpt.isPresent()) {
            StructureTemplate template = templateOpt.get();
            StructurePlaceSettings settings = new StructurePlaceSettings();
            template.placeInWorld(level, spawnPos, spawnPos, settings, level.random, 2);

            // place bunker two chunks east of the meteor
            BlockPos bunkerPos = spawnPos.offset(32, 0, 0);
            bunkerPos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, bunkerPos);
            // Prefer schematics from data packs but fall back to bundled resource
            WorldEditStructurePlacer placer = new WorldEditStructurePlacer(ModConstants.MOD_ID, "bunker.schem");
            var bounds = placer.placeStructure(level, bunkerPos);
            if (bounds != null) {
                BunkerProtectionHandler.addBunkerBounds(bounds);
                StructureSpawnTracker.get(level).addSpawnPos(bunkerPos);
            }
        }
    }
}
