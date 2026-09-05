package com.thunder.wildernessodysseyapi.watersystem.water.surface;

import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.world.level.Level;

/**
 * Composes the existing authoritative surface and environmental response.
 * This facade owns no water, weather, clock, region map or network state.
 * Call on the level thread, outside per-vertex rendering; renderers retain
 * their snapshot/GPU mirror rather than reading live terrain during a draw.
 */
public final class WaterSurfaceSampler {
    private WaterSurfaceSampler() { }

    /** Fills a caller-owned result from the established water and sea authorities. */
    public static void sample(Level level, double x, double z, float partialTick, Sample result) {
        result.clear();
        if (level == null || !Double.isFinite(x) || !Double.isFinite(z)) {
            return;
        }
        var surface = WildernessWaterAuthority.sampleSurface(level, x, z, partialTick);
        if (!surface.valid()) {
            return;
        }
        var sea = OceanSeaState.sampleAt(level, x, z, partialTick);
        result.storm = sea.strength();
        result.surface = surface;
        result.speed = (float) Math.hypot(surface.currentX(), surface.currentZ());
        float slope = (float) Math.hypot(surface.normalX(), surface.normalZ())
                / Math.max(0.05f, Math.abs(surface.normalY()));
        boolean ocean = "OCEAN".equals(surface.waterType()) || "COAST".equals(surface.waterType());
        result.breaking = HydrodynamicResponse.breaking(
                ocean ? 0.25f + sea.strength() * 1.5f : result.speed * 0.12f,
                surface.depth(), slope);
        result.turbulence = HydrodynamicResponse.unit(result.breaking + result.speed * 0.15f);
        result.erosionPressure = HydrodynamicResponse.erosionPressure(
                result.speed, result.breaking, 0.0f, sea.strength());
    }

    /** Adds actual finite falling-water velocity, including columns without a generated baseline. */
    public static void sampleAt(Level level, net.minecraft.core.BlockPos water, float partialTick, Sample result) {
        if (level == null) { result.clear(); return; }
        sample(level, water.getX() + 0.5, water.getZ() + 0.5, partialTick, result);
        var cell = WildernessWaterAuthority.sample(level, water);
        if (!cell.water() || !cell.authorityOwned()) {
            result.clear();
            return;
        }
        if (!result.surface.valid()) {
            float depth = WildernessWaterAuthority.getWaterDepth(level, water, 32);
            result.surface = new WildernessWaterAuthority.SurfaceAuthority(true,
                    water.getY() + cell.surfaceFillHeight(), cell.velocityX(), cell.velocityY(), cell.velocityZ(),
                    0, 1, 0, water.getX() >> 4, water.getZ() >> 4, water.getY(),
                    water.getY() - (int) Math.ceil(depth), depth, cell.volumeUnits(), "POND");
            result.storm = OceanSeaState.sampleAt(level, water.getX() + 0.5, water.getZ() + 0.5, partialTick).strength();
        }
        result.speed = Math.max(result.speed, (float) Math.hypot(cell.velocityX(), cell.velocityZ()));
        float fallingSpeed = Math.max(0.0f, -cell.velocityY());
        result.turbulence = HydrodynamicResponse.unit(result.breaking + result.speed * 0.15f + fallingSpeed * 0.12f);
        result.erosionPressure = HydrodynamicResponse.erosionPressure(result.speed, result.breaking, fallingSpeed, result.storm);
    }

    /** Reusable derived context; the nested authority record remains the surface owner. */
    public static final class Sample {
        public WildernessWaterAuthority.SurfaceAuthority surface = WildernessWaterAuthority.SurfaceAuthority.INVALID;
        public float speed;
        public float breaking;
        public float turbulence;
        public float erosionPressure;
        public float storm;

        /** Drops the previous column, including all derived response fields. */
        public void clear() {
            surface = WildernessWaterAuthority.SurfaceAuthority.INVALID;
            speed = breaking = turbulence = erosionPressure = storm = 0.0f;
        }
    }
}
