package com.thunder.wildernessodysseyapi.BugFixes.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SmokeUtils {

    public static void spawnSmoke(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof CampfireBlock)) return;

        int maxHeight = world.getMaxBuildHeight();
        Vec3 startPosition = Vec3.atCenterOf(pos).add(0, 0.5, 0);

        for (int y = pos.getY(); y <= maxHeight; y++) {
            Vec3 particlePos = startPosition.add(0, y - pos.getY(), 0);
            world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, particlePos.x, particlePos.y, particlePos.z, 1, 0, 0.1, 0, 0.01);
        }
    }
}