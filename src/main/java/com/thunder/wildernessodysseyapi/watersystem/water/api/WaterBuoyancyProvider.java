package com.thunder.wildernessodysseyapi.watersystem.water.api;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Supplies water-surface physics without depending on a specific entity class. */
public interface WaterBuoyancyProvider {

    /** Samples buoyancy for boats, items, mobs, or future custom vehicles. */
    BuoyancySample sample(Level level, AABB bounds, Vec3 velocity);

    /**
     * Samples a yaw-oriented rigid-watercraft footprint. Providers that do not
     * model hull orientation retain the ordinary bounds sample by default.
     */
    default BuoyancySample sampleOriented(
            Level level,
            AABB bounds,
            Vec3 velocity,
            float yawDegrees
    ) {
        return sample(level, bounds, velocity);
    }
}
