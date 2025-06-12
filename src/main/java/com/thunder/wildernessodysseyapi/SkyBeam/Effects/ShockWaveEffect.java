package com.thunder.wildernessodysseyapi.SkyBeam.Effects;

import com.thunder.wildernessodysseyapi.SkyBeam.Utilities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ShockWaveEffect {
    public static void schedule(ServerLevel w, BlockPos c,
                                double radius, double strength, int delay) {
        Utilities.runLater(delay, () -> {
            AABB area = new AABB(c).inflate(radius);
            for (Entity e : w.getEntities(null, area)) {
                Vec3 diff = e.position().subtract(Vec3.atCenterOf(c)).normalize();
                e.setDeltaMovement(diff.scale(strength).add(0,0.5,0));
            }
        });
    }
}
