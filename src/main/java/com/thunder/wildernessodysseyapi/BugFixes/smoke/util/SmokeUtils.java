package com.thunder.wildernessodysseyapi.BugFixes.smoke.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

public class SmokeUtils {

    public static void spawnSmoke(BlockPos pos, int maxHeight) {
        Vec3 startPosition = Vec3.atCenterOf(pos).add(0, 0.5, 0);
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return; // Safety check to avoid crashes
        }

        for (int y = pos.getY(); y <= maxHeight; y++) {
            Vec3 particlePos = startPosition.add(0, y - pos.getY(), 0);
            minecraft.particleEngine.createParticle(
                    ParticleTypes.CAMPFIRE_COSY_SMOKE, // Vanilla smoke particle
                    particlePos.x,
                    particlePos.y,
                    particlePos.z,
                    0,  // Motion X
                    0.05,  // Motion Y (slow rise)
                    0   // Motion Z
            );
        }
    }
}
