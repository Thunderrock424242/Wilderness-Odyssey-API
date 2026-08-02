package com.thunder.wildernessodysseyapi.watersystem.water.api;

/**
 * Describes how an entity exchanges momentum with authoritative water.
 *
 * <p>Profiles are immutable and may be registered by optional integrations.
 * Minecraft entity velocity uses blocks per tick, while currents exposed by
 * {@link WaterAccess} use blocks per second; the built-in solver owns that
 * conversion so adapters only provide physical coefficients.</p>
 *
 * @param baseMassKg minimum effective mass
 * @param massPerCubicBlockKg additional mass per bounding-box cubic block
 * @param massPerPayloadUnitKg additional mass for caller-supplied payload units
 * @param displacedVolumeScale fraction of the bounds that displaces water
 * @param longitudinalDragCoefficient drag parallel to a watercraft's heading
 * @param lateralDragCoefficient drag perpendicular to a watercraft's heading
 * @param verticalDragCoefficient vertical drag coefficient
 * @param longitudinalAreaScale forward-facing wetted-area scale
 * @param lateralAreaScale side-facing wetted-area scale
 * @param verticalAreaScale top/bottom wetted-area scale
 * @param planingLiftCoefficient dynamic lift for fast, partially submerged craft
 * @param slammingCoefficient entry impulse when a hull strikes the surface
 * @param angularStabilityCoefficient yaw stability supplied by lateral water flow
 * @param maximumDeltaVelocityPerTick hard safety cap for one solver step
 * @param rigidWatercraft whether hull-oriented planing and angular response apply
 */
public record WaterPhysicsProfile(
        double baseMassKg,
        double massPerCubicBlockKg,
        double massPerPayloadUnitKg,
        double displacedVolumeScale,
        double longitudinalDragCoefficient,
        double lateralDragCoefficient,
        double verticalDragCoefficient,
        double longitudinalAreaScale,
        double lateralAreaScale,
        double verticalAreaScale,
        double planingLiftCoefficient,
        double slammingCoefficient,
        double angularStabilityCoefficient,
        double maximumDeltaVelocityPerTick,
        boolean rigidWatercraft
) {

    /** Validates and normalizes a profile before it enters the hot-path registry. */
    public WaterPhysicsProfile {
        baseMassKg = nonNegativeFinite(baseMassKg, "baseMassKg");
        massPerCubicBlockKg = nonNegativeFinite(massPerCubicBlockKg, "massPerCubicBlockKg");
        massPerPayloadUnitKg = nonNegativeFinite(massPerPayloadUnitKg, "massPerPayloadUnitKg");
        displacedVolumeScale = nonNegativeFinite(displacedVolumeScale, "displacedVolumeScale");
        longitudinalDragCoefficient = nonNegativeFinite(
                longitudinalDragCoefficient, "longitudinalDragCoefficient");
        lateralDragCoefficient = nonNegativeFinite(lateralDragCoefficient, "lateralDragCoefficient");
        verticalDragCoefficient = nonNegativeFinite(verticalDragCoefficient, "verticalDragCoefficient");
        longitudinalAreaScale = nonNegativeFinite(longitudinalAreaScale, "longitudinalAreaScale");
        lateralAreaScale = nonNegativeFinite(lateralAreaScale, "lateralAreaScale");
        verticalAreaScale = nonNegativeFinite(verticalAreaScale, "verticalAreaScale");
        planingLiftCoefficient = nonNegativeFinite(planingLiftCoefficient, "planingLiftCoefficient");
        slammingCoefficient = nonNegativeFinite(slammingCoefficient, "slammingCoefficient");
        angularStabilityCoefficient = nonNegativeFinite(
                angularStabilityCoefficient, "angularStabilityCoefficient");
        maximumDeltaVelocityPerTick = nonNegativeFinite(
                maximumDeltaVelocityPerTick, "maximumDeltaVelocityPerTick");
    }

    /** Returns effective mass for the supplied hull volume and payload. */
    public double effectiveMass(double volume, int payloadUnits) {
        return Math.max(0.01,
                baseMassKg
                        + Math.max(0.0, volume) * massPerCubicBlockKg
                        + Math.max(0, payloadUnits) * massPerPayloadUnitKg);
    }

    private static double nonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }
}
