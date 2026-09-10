package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

/**
 * Pure bounded recharge, aquifer-storage, and baseflow model.
 *
 * <p>Groundwater is retained as three normalized chunk-scale values. It does
 * not create underground block entities or scan caves. Rain and snowmelt
 * percolate into storage, slow seepage removes water, and connected surface
 * water receives delayed baseflow during otherwise dry weather.</p>
 */
public final class GroundwaterModel {

    private GroundwaterModel() {
    }

    /** Advances groundwater by one watershed simulation pass. */
    public static Result advance(Input input) {
        if (input == null) {
            return Result.EMPTY;
        }
        if (!input.enabled) {
            return new Result(input.previousRecharge, input.previousStorage, 0.0f);
        }
        float storage = unit(input.previousStorage);
        float previousRecharge = unit(input.previousRecharge);
        float liquidInput = unit(input.precipitation + input.snowmelt);
        float infiltrationRoom = 0.24f + (1.0f - unit(input.soilSaturation)) * 0.56f;
        float terrainInfiltration = 0.58f + (1.0f - unit(input.drainageAccumulation)) * 0.22f;
        float generatedRecharge = liquidInput
                * unit(input.rechargeRate)
                * infiltrationRoom
                * terrainInfiltration;
        float recharge = approach(previousRecharge, generatedRecharge,
                generatedRecharge > previousRecharge ? 0.28f : 0.10f);

        // A small deep-seepage loss prevents abandoned wet regions from
        // retaining a permanently full aquifer after the weather turns dry.
        float seepageRate = unit(input.seepageRate);
        float deepSeepage = storage * seepageRate * 0.18f;
        float outletScale = input.surfaceOutlet
                ? 0.55f + unit(input.drainageAccumulation) * 0.45f
                : storage >= unit(input.springThreshold)
                ? 0.22f + (storage - unit(input.springThreshold)) * 1.4f
                : 0.0f;
        float discharge = Math.min(storage + recharge,
                (storage * storage * seepageRate * outletScale)
                        + Math.max(0.0f, storage - unit(input.springThreshold)) * 0.035f);
        float nextStorage = unit(storage + generatedRecharge - deepSeepage - discharge);
        return new Result(recharge, nextStorage, unit(discharge));
    }

    /**
     * Advances a physical high-precision aquifer without replacing the legacy normalized view.
     */
    public static PhysicalResult advancePhysical(PhysicalInput input) {
        if (input == null) {
            return PhysicalResult.EMPTY;
        }
        long capacity = Math.max(0L, input.capacityMilliUnits);
        long initial = Math.max(0L, input.storageMilliUnits);
        long requestedRecharge = Math.max(0L, input.rechargeMilliUnits);
        long acceptedRecharge = Math.min(requestedRecharge, Math.max(0, capacity - initial));
        long storage = initial + acceptedRecharge;
        double dt = Math.max(0.0, Double.isFinite(input.dtSeconds) ? input.dtSeconds : 0.0);
        double conductivity = Math.max(0.0, Double.isFinite(input.conductivityPerSecond)
                ? input.conductivityPerSecond : 0.0);
        double head = Math.max(0.0, Double.isFinite(input.headExcess) ? input.headExcess : 0.0);
        long potentialDischarge = (long) Math.floor(storage * -Math.expm1(-conductivity * head * dt));
        long discharge = input.surfaceOutlet
                ? Math.min(storage, Math.max(0L, potentialDischarge))
                : 0L;
        storage -= discharge;
        double deepRate = Double.isFinite(input.deepSeepageFractionPerSecond)
                ? Math.max(0.0, input.deepSeepageFractionPerSecond) : 0;
        long deepSeepage = Math.min(storage, (long) Math.floor(storage * -Math.expm1(-deepRate * dt)));
        storage -= deepSeepage;
        return new PhysicalResult(
                storage,
                acceptedRecharge,
                requestedRecharge - acceptedRecharge,
                discharge,
                deepSeepage,
                initial + acceptedRecharge - storage - discharge - deepSeepage
        );
    }

    private static float approach(float current, float target, float response) {
        return current + (target - current) * unit(response);
    }

    private static float unit(float value) {
        return Math.max(0.0f, Math.min(1.0f, Float.isFinite(value) ? value : 0.0f));
    }

    /** Immutable groundwater inputs for one chunk-scale pass. */
    public record Input(
            float previousRecharge,
            float previousStorage,
            float precipitation,
            float snowmelt,
            float soilSaturation,
            float drainageAccumulation,
            float rechargeRate,
            float seepageRate,
            float springThreshold,
            boolean surfaceOutlet,
            boolean enabled
    ) {
    }

    /** Normalized groundwater fields committed to the packed climate word. */
    public record Result(float recharge, float storage, float discharge) {
        public static final Result EMPTY = new Result(0.0f, 0.0f, 0.0f);

        public Result {
            recharge = unit(recharge);
            storage = unit(storage);
            discharge = unit(discharge);
        }
    }

    /** Unit-bearing aquifer inputs used by the regional conservation layer. */
    public record PhysicalInput(
            long storageMilliUnits,
            long capacityMilliUnits,
            long rechargeMilliUnits,
            double conductivityPerSecond,
            double headExcess,
            double deepSeepageFractionPerSecond,
            double dtSeconds,
            boolean surfaceOutlet
    ) {
    }

    /** Exact physical aquifer outputs; rejected recharge remains with its caller. */
    public record PhysicalResult(
            long storageMilliUnits,
            long acceptedRechargeMilliUnits,
            long rejectedRechargeMilliUnits,
            long dischargeMilliUnits,
            long deepBoundaryMilliUnits,
            long residualMilliUnits
    ) {
        public static final PhysicalResult EMPTY = new PhysicalResult(0L, 0L, 0L, 0L, 0L, 0L);
    }
}
