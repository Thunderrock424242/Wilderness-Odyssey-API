package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

/** Exact snow-water-equivalent storage and rain-on-snow melt partition. */
public final class SnowWaterEquivalentModel {

    private SnowWaterEquivalentModel() {
    }

    public static Result advance(Input input) {
        if (input == null) {
            return Result.EMPTY;
        }
        long previousSwe = Math.max(0L, input.sweMilliUnits);
        long precipitation = Math.max(0L, input.precipitationMilliUnits);
        long snowfall = input.snowing ? precipitation : 0L;
        long rainfall = input.snowing ? 0L : precipitation;
        long availableSwe = Math.addExact(previousSwe, snowfall);
        double warmth = Math.max(0.0, finite(input.airTemperatureCelsius));
        double sunlight = unit(input.sunlight);
        double wind = unit(input.wind);
        double groundHeat = unit(input.groundHeat);
        double rainHeat = rainfall > 0L ? 1.0 + unit(input.rainHeat) : 1.0;
        double meltCubicBlocksPerSecond = Math.max(0.0, finite(input.baseMeltCubicBlocksPerSecond))
                * (warmth > 0 ? warmth * 0.55 + sunlight * 0.25 + wind * 0.10 + groundHeat * 0.10 : 0)
                * rainHeat;
        long requestedMelt = (long) Math.floor(meltCubicBlocksPerSecond
                * Math.max(0.0, finite(input.dtSeconds))
                * HydrologicStorage.MILLI_UNITS_PER_CUBIC_BLOCK);
        long melt = Math.min(availableSwe, Math.max(0L, requestedMelt));
        long remainingSwe = availableSwe - melt;
        long liquidOutput = Math.addExact(rainfall, melt);
        long residual = previousSwe + precipitation - remainingSwe - liquidOutput;
        return new Result(remainingSwe, snowfall, rainfall, melt, liquidOutput, residual);
    }

    public record Input(
            long sweMilliUnits,
            long precipitationMilliUnits,
            boolean snowing,
            double airTemperatureCelsius,
            double sunlight,
            double wind,
            double groundHeat,
            double rainHeat,
            double baseMeltCubicBlocksPerSecond,
            double dtSeconds
    ) {
    }

    public record Result(
            long sweMilliUnits,
            long snowfallMilliUnits,
            long rainfallMilliUnits,
            long meltMilliUnits,
            long liquidOutputMilliUnits,
            long residualMilliUnits
    ) {
        public static final Result EMPTY = new Result(0L, 0L, 0L, 0L, 0L, 0L);
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, finite(value)));
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

}
