package com.thunder.wildernessodysseyapi.skytorch;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Rough Java translation of the original Kotlin plugin laser effect.
 * Clears a cylindrical tunnel along the beam path and scorches its edges.
 */
public class SkyTorchLaser {
    public static class Options {
        public double boreRadius = 3.0;
        public double boreBurnRadius = 5.0;
        public double boreDistance = 256.0;
    }

    private final Level level;
    private final Vec3 origin;
    private final Vec3 hit;
    private final Options options;

    public SkyTorchLaser(Level level, Vec3 origin, Vec3 hit, Options options) {
        this.level = level;
        this.origin = origin;
        this.hit = hit;
        this.options = options;
    }

    public void fire() {
        boreHole();
    }

    private void boreHole() {
        Vec3 direction = hit.subtract(origin).normalize();
        double step = options.boreRadius;
        int steps = (int) Math.ceil(options.boreDistance / step);
        for (int i = 0; i <= steps; i++) {
            Vec3 pos = origin.add(direction.scale(i * step));
            BlockPos center = BlockPos.containing(pos);

            int bore = (int) Math.ceil(options.boreRadius);
            for (int dx = -bore; dx <= bore; dx++) {
                for (int dy = -bore; dy <= bore; dy++) {
                    for (int dz = -bore; dz <= bore; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= options.boreRadius * options.boreRadius) {
                            BlockPos current = center.offset(dx, dy, dz);
                            if (!level.getBlockState(current).isAir()) {
                                level.destroyBlock(current, false);
                            }
                        }
                    }
                }
            }

            int burn = (int) Math.ceil(options.boreBurnRadius);
            for (int dx = -burn; dx <= burn; dx++) {
                for (int dy = -burn; dy <= burn; dy++) {
                    for (int dz = -burn; dz <= burn; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= options.boreBurnRadius * options.boreBurnRadius) {
                            BlockPos firePos = center.offset(dx, dy, dz);
                            if (level.isEmptyBlock(firePos) && !level.isEmptyBlock(firePos.below())) {
                                level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 11);
                            }
                        }
                    }
                }
            }
        }
    }
}

