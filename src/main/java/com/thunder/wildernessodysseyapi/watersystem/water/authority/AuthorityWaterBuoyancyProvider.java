package com.thunder.wildernessodysseyapi.watersystem.water.authority;

import com.thunder.wildernessodysseyapi.watersystem.water.api.BuoyancySample;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterBuoyancyProvider;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterSample;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Computes reusable buoyancy samples from the central water query API. */
public final class AuthorityWaterBuoyancyProvider implements WaterBuoyancyProvider {

    private final WaterAccess waterAccess;
    private final ThreadLocal<WaterSample> scratch = ThreadLocal.withInitial(WaterSample::new);

    /** Creates a provider backed by the supplied water service. */
    public AuthorityWaterBuoyancyProvider(WaterAccess waterAccess) {
        this.waterAccess = waterAccess;
    }

    @Override
    public BuoyancySample sample(Level level, AABB bounds, Vec3 velocity) {
        double x = (bounds.minX + bounds.maxX) * 0.5;
        double z = (bounds.minZ + bounds.maxZ) * 0.5;
        WaterSample water = scratch.get();
        waterAccess.sample(level, x, bounds.minY, z, 0.0f, water);
        if (!water.water()) {
            return BuoyancySample.DRY;
        }

        double fraction = submergedFraction(bounds.minY, bounds.maxY, water.surfaceHeight());
        if (fraction <= 0.0) {
            return BuoyancySample.DRY;
        }
        return new BuoyancySample(
                true,
                fraction >= 1.0,
                water.surfaceHeight(),
                fraction,
                new Vec3(water.currentX(), water.currentY(), water.currentZ()),
                new Vec3(water.normalX(), water.normalY(), water.normalZ())
        );
    }

    static double submergedFraction(double minimumY, double maximumY, double surfaceHeight) {
        double height = maximumY - minimumY;
        if (height <= 0.0 || Double.isNaN(surfaceHeight)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (surfaceHeight - minimumY) / height));
    }
}
