package com.thunder.wildernessodysseyapi.weather.api;

import java.util.Objects;

/**
 * Classifies continuous atmosphere fields into standard cloud genera.
 *
 * <p>The thresholds form a stable vocabulary for diagnostics and presentation;
 * they do not replace the continuous values that own simulation behavior.
 * Precipitating layered clouds and deep convection take priority, followed by
 * rising cellular clouds and then stable high, middle, or low decks.</p>
 */
public final class CloudTypeClassifier {

    private CloudTypeClassifier() {
    }

    /** Returns the dominant cloud genus represented by one weather sample. */
    public static CloudType classify(WeatherSample sample) {
        WeatherSample weather = Objects.requireNonNullElse(sample, WeatherSample.CLEAR);
        return classify(
                weather.cloudWater(),
                weather.humidity(),
                weather.instability(),
                weather.stormEnergy(),
                weather.precipitationIntensity(),
                weather.verticalMotion(),
                weather.cloudDepth(),
                weather.windShear()
        );
    }

    /**
     * Returns a cloud genus from normalized render or simulation fields.
     *
     * @param cloudWater condensed moisture
     * @param humidity relative humidity
     * @param instability convective instability
     * @param stormEnergy accumulated severe-weather energy
     * @param precipitation active precipitation intensity
     * @param verticalMotion rising or sinking motion
     * @param cloudDepth vertical development
     * @param windShear difference between surface and cloud-level wind
     */
    public static CloudType classify(
            double cloudWater,
            double humidity,
            double instability,
            double stormEnergy,
            double precipitation,
            double verticalMotion,
            double cloudDepth,
            double windShear
    ) {
        double water = unit(cloudWater);
        double moisture = unit(humidity);
        double convection = unit(instability);
        double storm = unit(stormEnergy);
        double rain = unit(precipitation);
        double lift = clamp(verticalMotion, -1.0, 1.0);
        double depth = unit(cloudDepth);
        double shear = unit(windShear / 1.5);

        if (water < 0.045 && rain < 1.0E-4 && moisture < 0.72) {
            return CloudType.CLEAR;
        }

        // Deep moist ascent produces a tower and, when shear is present, an
        // upper anvil. Broad stable precipitation remains nimbostratus.
        if (rain >= 0.12 && (storm >= 0.48 || depth >= 0.66 || lift >= 0.42)) {
            return CloudType.CUMULONIMBUS;
        }
        if (rain >= 1.0E-4) {
            return CloudType.NIMBOSTRATUS;
        }
        if (convection >= 0.46 || lift >= 0.20 || depth >= 0.52) {
            return storm >= 0.58 && water >= 0.68
                    ? CloudType.CUMULONIMBUS
                    : CloudType.CUMULUS;
        }

        double stability = unit((1.0 - convection) * 0.58
                + Math.max(0.0, -lift) * 0.28
                + moisture * 0.20);
        boolean upperDeck = water < 0.27
                && depth < 0.32
                && (shear >= 0.18 || moisture < 0.82);
        if (upperDeck) {
            if (stability >= 0.72 && moisture >= 0.82) {
                return CloudType.CIRROSTRATUS;
            }
            if (convection >= 0.22 || lift > 0.02) {
                return CloudType.CIRROCUMULUS;
            }
            return CloudType.CIRRUS;
        }

        boolean middleDeck = water < 0.50 && depth < 0.46;
        if (middleDeck) {
            return stability >= 0.66 && moisture >= 0.78
                    ? CloudType.ALTOSTRATUS
                    : CloudType.ALTOCUMULUS;
        }

        if (stability >= 0.72 && lift <= 0.02) {
            return CloudType.STRATUS;
        }
        if (convection < 0.42 && depth < 0.56) {
            return CloudType.STRATOCUMULUS;
        }
        return CloudType.CUMULUS;
    }

    private static double unit(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        double finite = Double.isFinite(value) ? value : minimum;
        return Math.max(minimum, Math.min(maximum, finite));
    }
}
