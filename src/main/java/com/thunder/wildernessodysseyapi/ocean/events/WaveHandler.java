package com.thunder.wildernessodysseyapi.ocean.events;

import com.thunder.wildernessodysseyapi.ocean.util.WaveCalculator;
import com.thunder.wildernessodysseyapi.ocean.util.WaveHeightmap;
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

            for (BlockPos pos : WaveHeightmap.getInstance().getAllPositions()) {
                double waveHeight = WaveCalculator.calculateWaveHeight(time, pos.getX(), pos.getZ());
                WaveHeightmap.getInstance().setHeight(pos, waveHeight);

                applyWaveMotionToEntities(world, pos, waveHeight);
            }
        }
    }

    private void applyWaveMotionToEntities(Level world, BlockPos pos, double waveHeight) {
        for (Entity entity : world.getEntities(null, new AABB(pos))) {
            if (entity.isFloating()) {
                double flowSpeed = WaveCalculator.calculateFlowSpeed(world.getDayTime(), pos.getX(), pos.getZ());
                entity.setDeltaMovement(entity.getDeltaMovement().add(0, waveHeight * 0.1, flowSpeed));
            }
        }
    }
}
