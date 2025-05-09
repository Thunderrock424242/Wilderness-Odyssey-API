package com.thunder.wildernessodysseyapi.worldgen.structures;

import com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;

public class MeteorStructureSpawner {
    private static boolean placed = false;

    public static void tryPlace(ServerLevel level) {
        if (placed) return;
        placed = true;

        BlockPos pos = new BlockPos(0, level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0), 0);
        ResourceLocation structureID = new ResourceLocation(WildernessOdysseyAPIMainModClass.MOD_ID, "meteor_base"); // your NBT structure

        StructureTemplateManager manager = level.getStructureManager();
        Optional<StructureTemplate> templateOpt = manager.get(structureID);

        if (templateOpt.isPresent()) {
            StructureTemplate template = templateOpt.get();
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setIgnoreEntities(false);

            template.placeInWorld(level, pos, pos, settings, level.random, 2);
        }
    }
}
