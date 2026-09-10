package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

/** Lightweight, exact regional infiltration/runoff/evapotranspiration partition. */
public final class SoilHydrologyModel {

    private SoilHydrologyModel() {
    }

    /** Advances one regional soil store without block-scale ticking. */
    public static Result advance(Input input) {
        if (input == null) {
            return Result.EMPTY;
        }
        SoilProfile profile = input.profile == null ? SoilProfile.LOAM : input.profile;
        long capacity = Math.max(0L, input.capacityMilliUnits);
        long initial = Math.max(0L, input.storedMilliUnits);
        long liquidInput = Math.max(0L, input.liquidInputMilliUnits);
        double dt = bounded(input.dtSeconds, 0.0, 86_400.0);
        double vegetation = bounded(input.vegetationFraction, 0.0, 1.0);
        double infiltrationRate = profile.conductivityCubicBlocksPerSecond
                * HydrologicStorage.MILLI_UNITS_PER_CUBIC_BLOCK
                * (1.0 + vegetation * 0.35) * bounded(input.areaSquareMetres, 0.0, 256.0);
        long rateLimited = nonNegativeFloor(infiltrationRate * dt);
        long infiltration = Math.min(liquidInput,
                Math.min(Math.max(0, capacity - initial), rateLimited));
        long runoff = liquidInput - infiltration;
        long wetStorage = initial + infiltration;

        long fieldCapacity = Math.round(capacity * profile.fieldCapacityFraction / profile.porosity);
        long drainable = Math.max(0L, wetStorage - fieldCapacity);
        long drainageLimit = nonNegativeFloor(-Math.expm1(-profile.drainageFractionPerSecond * dt) * drainable);
        long groundwaterRecharge = Math.min(drainable, drainageLimit);
        wetStorage -= groundwaterRecharge;

        double availability = capacity <= 0L ? 0.0 : wetStorage / (double) capacity;
        long requestedEt = nonNegativeFloor(Math.max(0L, input.potentialEtMilliUnits)
                * (0.25 + vegetation * 0.75)
                * bounded(availability / Math.max(0.01, profile.wiltingPointFraction), 0.0, 1.0));
        long evapotranspiration = Math.min(wetStorage, requestedEt);
        long finalStorage = wetStorage - evapotranspiration;

        return new Result(
                finalStorage,
                infiltration,
                runoff,
                groundwaterRecharge,
                evapotranspiration,
                initial + liquidInput - finalStorage - runoff
                        - groundwaterRecharge - evapotranspiration
        );
    }

    /** Coarse terrain profiles derived from block/tag sampling by the runtime owner. */
    public enum SoilProfile {
        SAND(0.36, 0.10, 0.035, 0.0004, 0.0022),
        GRAVEL(0.28, 0.07, 0.025, 0.0008, 0.0035),
        LOAM(0.48, 0.26, 0.12, 0.00012, 0.0008),
        CLAY(0.52, 0.34, 0.18, 0.000005, 0.00018),
        ROCK(0.08, 0.025, 0.01, 0.000001, 0.00004),
        IMPERMEABLE(0.02, 0.005, 0.0, 0.0000001, 0.0);

        private final double porosity;
        private final double fieldCapacityFraction;
        private final double wiltingPointFraction;
        private final double conductivityCubicBlocksPerSecond;
        private final double drainageFractionPerSecond;

        SoilProfile(double porosity, double fieldCapacityFraction, double wiltingPointFraction,
                    double conductivityCubicBlocksPerSecond, double drainageFractionPerSecond) {
            this.porosity = porosity;
            this.fieldCapacityFraction = fieldCapacityFraction;
            this.wiltingPointFraction = wiltingPointFraction;
            this.conductivityCubicBlocksPerSecond = conductivityCubicBlocksPerSecond;
            this.drainageFractionPerSecond = drainageFractionPerSecond;
        }

        public double porosity() {
            return porosity;
        }
    }

    public record Input(
            SoilProfile profile,
            long storedMilliUnits,
            long capacityMilliUnits,
            long liquidInputMilliUnits,
            long potentialEtMilliUnits,
            double vegetationFraction,
            double dtSeconds,
            double areaSquareMetres
    ) {
        /** Compatibility constructor for a one-square-metre test/integration cell. */
        public Input(SoilProfile profile, long storedMilliUnits, long capacityMilliUnits,
                     long liquidInputMilliUnits, long potentialEtMilliUnits,
                     double vegetationFraction, double dtSeconds) {
            this(profile, storedMilliUnits, capacityMilliUnits, liquidInputMilliUnits,
                    potentialEtMilliUnits, vegetationFraction, dtSeconds, 1.0);
        }
    }

    public record Result(
            long storedMilliUnits,
            long infiltrationMilliUnits,
            long runoffMilliUnits,
            long groundwaterRechargeMilliUnits,
            long evapotranspirationMilliUnits,
            long residualMilliUnits
    ) {
        public static final Result EMPTY = new Result(0L, 0L, 0L, 0L, 0L, 0L);
    }

    private static long nonNegativeFloor(double value) {
        return (long) Math.floor(Math.max(0.0, Double.isFinite(value) ? value : 0.0));
    }

    private static double bounded(double value, double minimum, double maximum) {
        double finite = Double.isFinite(value) ? value : 0.0;
        return Math.max(minimum, Math.min(maximum, finite));
    }
}
