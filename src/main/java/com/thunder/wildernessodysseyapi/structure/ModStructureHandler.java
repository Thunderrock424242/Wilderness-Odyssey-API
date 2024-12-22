package com.thunder.wildernessodysseyapi.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Random;

public class ModStructureHandler {
    @SubscribeEvent
    public static void onBiomeLoading(BiomeLoadingEvent event) {
        if (event.getName() != null && event.getName().equals(Biomes.PLAINS.location())) {
            event.getGeneration().getFeatures(GenerationStep.Decoration.SURFACE_STRUCTURES).add(() -> context -> {
                ServerLevel world = context.level();
                Random random = context.random();

                StructureGenerationData data = StructureGenerationData.get(world);
                if (data.isStructureGenerated()) {
                    return false; // Skip if already generated
                }

                int x = random.nextInt(1000) - 500;
                int z = random.nextInt(1000) - 500;
                BlockPos pos = new BlockPos(x, 0, z);

                WorldEditStructurePlacer placer = new WorldEditStructurePlacer("wildernessodyssey", "schematics/my_structure.schem");
                if (placer.placeStructure(world, pos)) {
                    data.setStructureGenerated(true); // Mark as generated
                    return true;
                }

                return false;
            });
        }
    }
}
