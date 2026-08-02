package com.thunder.wildernessodysseyapi.weather.client.cloud;

/** Approximates broad cloud shadows by sampling both overhead and toward the sun. */
public final class CloudShadowModel {

    private CloudShadowModel() {
    }

    /** Combines local cover with projected sun-path cover into one bounded shadow. */
    public static double evaluate(
            CloudFieldSample overhead,
            CloudFieldSample towardSun,
            double projectionWeight,
            double configuredStrength
    ) {
        double local = CloudLightingModel.evaluate(overhead).shadow();
        double projected = CloudLightingModel.evaluate(towardSun).shadow();
        double broadStorm = Math.max(
                overhead == null ? 0.0 : overhead.stormEnergy() * overhead.support(),
                towardSun == null ? 0.0 : towardSun.stormEnergy() * towardSun.support()
        );
        double combined = Math.max(local, projected * unit(projectionWeight))
                + broadStorm * 0.16;
        return unit(combined * unit(configuredStrength));
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }
}
