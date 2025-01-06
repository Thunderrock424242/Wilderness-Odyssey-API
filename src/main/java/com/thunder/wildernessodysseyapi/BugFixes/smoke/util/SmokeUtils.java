package com.thunder.wildernessodysseyapi.BugFixes.smoke.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

public class SmokeUtils {

    public static void spawnSmoke(BlockPos pos, int maxBuildHeight) {
        Vec3 startPosition = Vec3.atCenterOf(pos).add(0, 0.5, 0); // Start slightly above the block
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return; // Safety check to avoid crashes
        }

        minecraft.particleEngine.createParticle(
                ParticleTypes.CAMPFIRE_COSY_SMOKE, // Vanilla smoke particle
                startPosition.x,
                startPosition.y,
                startPosition.z,
                0,  // Horizontal motion (natural drift handled by the particle)
                0.01,  // Vertical motion
                0   // Horizontal motion
        );
    }
}
