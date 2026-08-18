package com.thunder.wildernessodysseyapi.ecosystem.simulation;

/**
 * Constant-time lazy population model for distant and dormant regions.
 *
 * <p>Elapsed time is converted directly into an aggregate growth factor. A
 * region absent for three days therefore performs one calculation rather than
 * 72,000 per-tick updates.</p>
 */
public final class AbstractEcosystemModel {

    public static final long TICKS_PER_DAY = 24_000L;
    private static final double MAXIMUM_LAZY_DAYS = 360.0;
    private static final int MAXIMUM_SPECIES_POPULATION = 4_096;

    private AbstractEcosystemModel() {
    }

    /** Advances one species population with bounded habitat pressure. */
    public static int advancePopulation(int population, long elapsedTicks, Environment environment) {
        int safePopulation = Math.max(0, Math.min(MAXIMUM_SPECIES_POPULATION, population));
        if (safePopulation == 0 || elapsedTicks <= 0L) {
            return safePopulation;
        }
        double days = Math.min(MAXIMUM_LAZY_DAYS, (double) elapsedTicks / TICKS_PER_DAY);
        double habitat = environment.foodAvailability() * 0.55 + environment.waterAvailability() * 0.45;
        double dailyRate = 0.012
                + (habitat - 0.5) * 0.04
                - environment.foodPressure() * 0.025
                - environment.disturbance() * 0.03
                - environment.weatherImpact() * 0.02;
        int advanced = (int) Math.round(safePopulation * Math.exp(dailyRate * days));
        return Math.max(0, Math.min(MAXIMUM_SPECIES_POPULATION, advanced));
    }

    /** Advances environmental memory with analytic decay instead of tick loops. */
    public static Environment advanceEnvironment(Environment environment, long elapsedTicks, int totalPopulation) {
        if (elapsedTicks <= 0L) {
            return environment;
        }
        double days = Math.min(MAXIMUM_LAZY_DAYS, (double) elapsedTicks / TICKS_PER_DAY);
        double targetPressure = unit(totalPopulation / 96.0);
        double foodPressure = approach(environment.foodPressure(), targetPressure, 1.0 - Math.exp(-days * 0.35));
        double foodAvailability = approach(environment.foodAvailability(), 1.0 - foodPressure * 0.65,
                1.0 - Math.exp(-days * 0.18));
        double disturbance = environment.disturbance() * Math.exp(-days * 0.75);
        double weatherImpact = environment.weatherImpact() * Math.exp(-days * 0.30);
        return new Environment(
                foodAvailability,
                environment.waterAvailability(),
                foodPressure,
                disturbance,
                weatherImpact
        );
    }

    /** Normalized abstract inputs reserved for future forage, water, fire, season, and weather owners. */
    public record Environment(
            double foodAvailability,
            double waterAvailability,
            double foodPressure,
            double disturbance,
            double weatherImpact
    ) {
        public static final Environment NEUTRAL = new Environment(0.65, 0.60, 0.0, 0.0, 0.0);

        public Environment {
            foodAvailability = unit(foodAvailability);
            waterAvailability = unit(waterAvailability);
            foodPressure = unit(foodPressure);
            disturbance = unit(disturbance);
            weatherImpact = unit(weatherImpact);
        }
    }

    private static double approach(double current, double target, double amount) {
        return unit(current + (target - current) * unit(amount));
    }

    private static double unit(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
