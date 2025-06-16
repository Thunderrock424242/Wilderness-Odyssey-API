package com.thunder.wildernessodysseyapi.WorldGen.worldgen.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

public class MeteorStructureSpawner {
    private static boolean placed = false;

    public static void tryPlace(ServerLevel level) {
        // Only place once in the entire world
        if (placed) return;
        placed = true;

        // Decide where to place the bunker. For example: at (0, surfaceY(0,0), 0)
        BlockPos spawnPos = new BlockPos(0, level.getHeight(), 0);

        ResourceLocation structureID = ResourceLocation.tryBuild(MOD_ID, "meteor_bunker");
        StructureTemplateManager manager = level.getStructureManager();
        Optional<StructureTemplate> templateOpt = manager.get(structureID);

        if (templateOpt.isPresent()) {
            StructureTemplate template = templateOpt.get();
            StructurePlaceSettings settings = new StructurePlaceSettings();
            template.placeInWorld(
                    level,
                    spawnPos,
                    spawnPos,
                    settings,
                    level.random,
                    2
            );
        }
    }
}
