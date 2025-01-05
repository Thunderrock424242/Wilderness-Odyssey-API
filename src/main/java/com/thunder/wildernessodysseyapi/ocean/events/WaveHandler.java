package com.thunder.wildernessodysseyapi.ocean.events;

import com.thunder.wildernessodysseyapi.ocean.util.WaveCalculator;
import com.thunder.wildernessodysseyapi.ocean.util.WaveHeightmap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public class WaveHandler {

    @SubscribeEvent
    public void onLevelTickPost(LevelTickEvent.Post event) {
        Level world = event.getLevel();
        long time = world.getDayTime();

        // Iterate through all positions in the wave heightmap
        for (BlockPos pos : WaveHeightmap.getInstance().getAllPositions()) {
            double waveHeight = WaveCalculator.calculateWaveHeight(time, pos.getX(), pos.getZ());
            WaveHeightmap.getInstance().setHeight(pos, waveHeight);

            applyWaveMotionToEntities(world, pos, waveHeight);
        }
    }

    private void applyWaveMotionToEntities(Level world, BlockPos pos, double waveHeight) {
        // Get all entities in the area of the block position
        for (Entity entity : world.getEntities(null, new AABB(pos))) {
            if (entity.isInWater()) { // Check if the entity is in water
                double flowSpeed = WaveCalculator.calculateFlowSpeed(world.getDayTime(), pos.getX(), pos.getZ());
                entity.setDeltaMovement(entity.getDeltaMovement().add(0, waveHeight * 0.1, flowSpeed));
            }
        }
    }
}
