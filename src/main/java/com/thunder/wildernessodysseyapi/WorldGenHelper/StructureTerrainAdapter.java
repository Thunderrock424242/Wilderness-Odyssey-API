package com.thunder.wildernessodysseyapi.WorldGenHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;



public class StructureTerrainAdapter {

    @SubscribeEvent
    public void onStructureSpawn(StructureSpawnEvent event) {
        Structure structure = event.getStructure();
        ResourceLocation id = Registries.STRUCTURES.getKey(structure);

        if (id != null) {
            logger.info("Adapting terrain for structure: {}", id);
            adaptStructureToTerrain(event.getWorld(), event.getPos());
        }
    }

    private void adaptStructureToTerrain(Level world, BlockPos pos) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                mutable.set(pos.getX() + x, pos.getY(), pos.getZ() + z);
                int terrainHeight = world.getHeight();
                if (terrainHeight < pos.getY()) {
                    world.setBlock(mutable, Blocks.DIRT.defaultBlockState(), 3);
                } else if (terrainHeight > pos.getY()) {
                    world.setBlock(mutable, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }
}
