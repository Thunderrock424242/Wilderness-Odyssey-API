package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Read-only compatibility view of replacement water at a block position.
 *
 * <p>Most third-party mods will continue to query vanilla block/fluid state.
 * This helper centralizes the replacement system's own broader answer so
 * commands, diagnostics, and future API integrations report the same ownership
 * and wetness state.</p>
 */
public final class WaterCompatibility {

    private WaterCompatibility() {
    }

    /** Returns whether vanilla or replacement-owned water occupies this block. */
    public static boolean isWater(Level level, BlockPos pos) {
        return describe(level, pos).wet();
    }

    /** Builds a bounded diagnostic snapshot for one block. */
    public static Snapshot describe(Level level, BlockPos pos) {
        WaterVolumeChunk.WaterCell canonical = CanonicalWater.get(level, pos);
        boolean tracked = CanonicalWater.isTracked(level, pos);
        boolean vanillaWater = level.getFluidState(pos).is(FluidTags.WATER);
        boolean plainWaterBlock = level.getBlockState(pos).is(Blocks.WATER);
        SPHSimulationManager.MobileWaterSample mobile = SPHSimulationManager.get().sampleAt(
                level,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
        );

        return new Snapshot(
                tracked,
                canonical.volumeUnits(),
                canonical.fillFraction(),
                canonical.velocityX(),
                canonical.velocityY(),
                canonical.velocityZ(),
                canonical.imported(),
                (canonical.flags() & WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED) != 0,
                vanillaWater,
                plainWaterBlock,
                mobile.wet(),
                mobile.velocityX(),
                mobile.velocityY(),
                mobile.velocityZ()
        );
    }

    /** Immutable replacement/vanilla water state used by commands and diagnostics. */
    public record Snapshot(
            boolean canonicalTracked,
            int canonicalVolumeUnits,
            float fillFraction,
            float velocityX,
            float velocityY,
            float velocityZ,
            boolean imported,
            boolean compatibilityProjected,
            boolean vanillaWater,
            boolean plainWaterBlock,
            boolean mobileWater,
            float mobileVelocityX,
            float mobileVelocityY,
            float mobileVelocityZ
    ) {
        /** Returns whether any vanilla or replacement water exists here. */
        public boolean wet() {
            return canonicalVolumeUnits > 0 || vanillaWater || mobileWater;
        }

        /** Returns a compact velocity magnitude for debug output. */
        public float canonicalSpeed() {
            return (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
        }

        /** Returns a compact mobile-water velocity magnitude for debug output. */
        public float mobileSpeed() {
            return (float) Math.sqrt(
                    mobileVelocityX * mobileVelocityX
                            + mobileVelocityY * mobileVelocityY
                            + mobileVelocityZ * mobileVelocityZ
            );
        }
    }
}
