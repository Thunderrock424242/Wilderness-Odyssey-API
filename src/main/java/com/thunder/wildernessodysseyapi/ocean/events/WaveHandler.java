package com.thunder.wildernessodysseyapi.ocean.events;

import com.thunder.wildernessodysseyapi.ocean.util.WaveCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;

public class WaveHandler {
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Level world = event.world;
            long time = world.getDayTime();

            for (BlockPos pos : getWaveAffectedPositions(world)) {
                String waveType = isNearBeach(world, pos) ? "beach" : "ocean";
                double waveHeight = WaveCalculator.calculateWaveHeight(time, pos.getX(), pos.getZ(), waveType);

                // Update water height
                updateWaterHeight(world, pos, waveHeight);

                // Apply wave motion to nearby entities
                applyWaveMotionToEntities(world, pos, waveHeight, waveType);
            }
        }
    }

    private void updateWaterHeight(Level world, BlockPos pos, double waveHeight) {
        // Update water block's visual height
        world.getBlockState(pos).getBlock();// Apply height as part of rendering logic
    }

    private void applyWaveMotionToEntities(Level world, BlockPos pos, double waveHeight, String waveType) {
        for (Entity entity : world.getEntities(null, new AABB(pos))) {
            if (entity.isFloating()) { // Custom logic to check if entity is floating
                double flowSpeed = WaveCalculator.calculateWaveFlowSpeed(world.getDayTime(), pos.getX(), pos.getZ());
                entity.setDeltaMovement(entity.getDeltaMovement().add(flowSpeed, waveHeight * 0.1, flowSpeed));
            }
        }
    }

    private boolean isNearBeach(Level world, BlockPos pos) {
        return world.getBiome(pos).getRegistryName().toString().contains("beach");
    }

    private Iterable<BlockPos> getWaveAffectedPositions(Level world) {
        return world.getLoadedChunks().stream()
                .flatMap(chunk -> chunk.getBlocks().stream())
                .filter(pos -> {
                    world.getBlockState(pos).getBlock();
                    return true;
                })
                .toList();
    }
}