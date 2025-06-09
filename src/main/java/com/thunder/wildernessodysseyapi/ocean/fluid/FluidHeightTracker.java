package com.thunder.wildernessodysseyapi.ocean.fluid;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

public class FluidHeightTracker {
    private static final Map<BlockPos, Float> lastHeights = new HashMap<>();
    private static final Map<BlockPos, Float> currentHeights = new HashMap<>();

    public static void onClientTick(Level level) {
        lastHeights.clear();
        lastHeights.putAll(currentHeights);
        currentHeights.clear();

        if (Minecraft.getInstance().cameraEntity == null) return;

        AABB bounds = Minecraft.getInstance().cameraEntity.getBoundingBox().inflate(20);
        BlockPos min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos max = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    FluidState state = level.getFluidState(pos);
                    if (state.getType() == Fluids.WATER) {
                        currentHeights.put(pos.immutable(), state.getOwnHeight());
                    }
                }
            }
        }
    }


    public static float getInterpolated(BlockPos pos, float partialTick) {
        float last = lastHeights.getOrDefault(pos, 0f);
        float curr = currentHeights.getOrDefault(pos, 0f);
        return last + (curr - last) * partialTick;
    }
}
