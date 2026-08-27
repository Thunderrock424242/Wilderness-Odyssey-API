package com.thunder.wildernessodysseyapi.watersystem.water.authority;

import com.thunder.wildernessodysseyapi.watersystem.water.api.BuoyancySample;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterBuoyancyProvider;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterSample;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Computes reusable multi-point buoyancy samples from the central water API.
 *
 * <p>Four footprint corners and a motion-biased center point resolve partial
 * hull contact without owning or duplicating any water state. Callers should
 * use their cached water-contact state as the hot-path dry prefilter.</p>
 */
public final class AuthorityWaterBuoyancyProvider implements WaterBuoyancyProvider {

    private static final double MIN_SAMPLE_INSET = 0.02;
    private static final double MAX_SAMPLE_INSET = 0.18;
    private static final double LEADING_SAMPLE_BIAS = 0.35;
    private static final double MINIMUM_HULL_ASPECT = 1.45;
    private static final double HULL_WIDTH_SCALE = 0.90;

    private final WaterAccess waterAccess;
    private final ThreadLocal<WorkingState> workingState = ThreadLocal.withInitial(WorkingState::new);

    /** Creates a provider backed by the supplied water service. */
    public AuthorityWaterBuoyancyProvider(WaterAccess waterAccess) {
        this.waterAccess = waterAccess;
    }

    @Override
    public BuoyancySample sample(Level level, AABB bounds, Vec3 velocity) {
        WorkingState working = workingState.get();
        working.accumulator.reset();
        Vec3 motion = velocity == null ? Vec3.ZERO : velocity;

        double centerX = (bounds.minX + bounds.maxX) * 0.5;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
        double halfSampleX = sampleHalfExtent(bounds.getXsize());
        double halfSampleZ = sampleHalfExtent(bounds.getZsize());
        double queryY = bounds.minY + Math.min(0.05, Math.max(0.0, bounds.getYsize() * 0.1));

        // Four hull-footprint samples capture uneven crests while the center
        // sample is biased slightly into motion to react before the bow enters.
        samplePoint(level, bounds, working, centerX - halfSampleX, queryY, centerZ - halfSampleZ);
        samplePoint(level, bounds, working, centerX + halfSampleX, queryY, centerZ - halfSampleZ);
        samplePoint(level, bounds, working, centerX - halfSampleX, queryY, centerZ + halfSampleZ);
        samplePoint(level, bounds, working, centerX + halfSampleX, queryY, centerZ + halfSampleZ);
        double leadX = clamp(motion.x * LEADING_SAMPLE_BIAS, -halfSampleX, halfSampleX);
        double leadZ = clamp(motion.z * LEADING_SAMPLE_BIAS, -halfSampleZ, halfSampleZ);
        samplePoint(level, bounds, working, centerX + leadX, queryY, centerZ + leadZ);
        return working.accumulator.finish();
    }

    /**
     * Samples bow-port, bow-starboard, stern-port, and stern-starboard points
     * in the craft's yaw frame, followed by the existing motion-biased center.
     */
    @Override
    public BuoyancySample sampleOriented(
            Level level,
            AABB bounds,
            Vec3 velocity,
            float yawDegrees
    ) {
        WorkingState working = workingState.get();
        working.accumulator.reset();
        Vec3 motion = velocity == null ? Vec3.ZERO : velocity;

        double centerX = (bounds.minX + bounds.maxX) * 0.5;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
        double horizontalMinimum = Math.min(bounds.getXsize(), bounds.getZsize());
        double hullLength = Math.max(
                Math.max(bounds.getXsize(), bounds.getZsize()),
                horizontalMinimum * MINIMUM_HULL_ASPECT
        );
        double hullWidth = horizontalMinimum * HULL_WIDTH_SCALE;
        double halfLength = sampleHalfExtent(hullLength);
        double halfWidth = sampleHalfExtent(hullWidth);
        double queryY = bounds.minY + Math.min(0.05, Math.max(0.0, bounds.getYsize() * 0.1));
        double forwardX = forwardX(yawDegrees);
        double forwardZ = forwardZ(yawDegrees);
        double rightX = rightX(yawDegrees);
        double rightZ = rightZ(yawDegrees);

        samplePoint(level, bounds, working,
                centerX + forwardX * halfLength - rightX * halfWidth,
                queryY,
                centerZ + forwardZ * halfLength - rightZ * halfWidth);
        samplePoint(level, bounds, working,
                centerX + forwardX * halfLength + rightX * halfWidth,
                queryY,
                centerZ + forwardZ * halfLength + rightZ * halfWidth);
        samplePoint(level, bounds, working,
                centerX - forwardX * halfLength - rightX * halfWidth,
                queryY,
                centerZ - forwardZ * halfLength - rightZ * halfWidth);
        samplePoint(level, bounds, working,
                centerX - forwardX * halfLength + rightX * halfWidth,
                queryY,
                centerZ - forwardZ * halfLength + rightZ * halfWidth);

        double leadForward = clamp(
                motion.x * forwardX + motion.z * forwardZ,
                -halfLength,
                halfLength
        ) * LEADING_SAMPLE_BIAS;
        samplePoint(level, bounds, working,
                centerX + forwardX * leadForward,
                queryY,
                centerZ + forwardZ * leadForward);
        return working.accumulator.finish();
    }

    static double forwardX(float yawDegrees) {
        return -Math.sin(Math.toRadians(yawDegrees));
    }

    static double forwardZ(float yawDegrees) {
        return Math.cos(Math.toRadians(yawDegrees));
    }

    static double rightX(float yawDegrees) {
        return Math.cos(Math.toRadians(yawDegrees));
    }

    static double rightZ(float yawDegrees) {
        return Math.sin(Math.toRadians(yawDegrees));
    }

    static double submergedFraction(double minimumY, double maximumY, double surfaceHeight) {
        double height = maximumY - minimumY;
        if (height <= 0.0 || Double.isNaN(surfaceHeight)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (surfaceHeight - minimumY) / height));
    }

    private void samplePoint(
            Level level,
            AABB bounds,
            WorkingState working,
            double x,
            double queryY,
            double z
    ) {
        waterAccess.sample(level, x, queryY, z, 0.0f, working.sample);
        working.accumulator.add(working.sample, bounds.minY, bounds.maxY);
    }

    private static double sampleHalfExtent(double size) {
        double inset = clamp(size * 0.15, MIN_SAMPLE_INSET, MAX_SAMPLE_INSET);
        return Math.max(0.0, size * 0.5 - inset);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class WorkingState {
        private final WaterSample sample = new WaterSample();
        private final BuoyancySampleAccumulator accumulator = new BuoyancySampleAccumulator();
    }
}
