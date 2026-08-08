package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.CloudType;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;

import java.util.Objects;

/**
 * Builds classifier-safe atmospheric samples for operator cloud testing.
 *
 * <p>The preset changes the same continuous fields used by normal simulation;
 * it never synchronizes a client-only cloud label. Temperature, pressure,
 * surface state, and surface wind are retained so the visual test remains
 * representative of the operator's current location.</p>
 */
final class CloudDebugPreset {

    private CloudDebugPreset() {
    }

    /** Returns a strong visible sample that derives back to the requested cloud genus. */
    static WeatherSample apply(CloudType requestedType, WeatherSample current) {
        CloudType type = Objects.requireNonNullElse(requestedType, CloudType.CLEAR);
        WeatherSample old = Objects.requireNonNullElse(current, WeatherSample.CLEAR);
        Signals signals = signals(type);
        PrecipitationType precipitationType = signals.precipitation() <= 0.0
                ? PrecipitationType.NONE
                : PrecipitationType.RAIN;
        return new WeatherSample(
                old.temperature(),
                signals.humidity(),
                old.pressure(),
                old.wind(),
                signals.cloudWater(),
                signals.instability(),
                signals.stormEnergy(),
                signals.precipitation(),
                precipitationType,
                signals.verticalMotion(),
                signals.cloudDepth(),
                cloudWindForShear(old.wind(), signals.windShear()),
                old.surface()
        );
    }

    // Values stay comfortably inside each classifier region while producing
    // enough cloud mass for the renderer to expose shape and lighting defects.
    private static Signals signals(CloudType type) {
        return switch (type) {
            case CLEAR -> new Signals(0.0, 0.40, 0.05, 0.0, 0.0, 0.0, 0.05, 0.0);
            case CIRRUS -> new Signals(0.25, 0.65, 0.10, 0.0, 0.0, -0.10, 0.12, 0.50);
            case CIRROSTRATUS -> new Signals(0.25, 0.92, 0.03, 0.0, 0.0, -0.25, 0.18, 0.40);
            case CIRROCUMULUS -> new Signals(0.25, 0.72, 0.28, 0.0, 0.0, 0.08, 0.22, 0.35);
            case ALTOSTRATUS -> new Signals(0.48, 0.90, 0.08, 0.0, 0.0, -0.12, 0.34, 0.08);
            case ALTOCUMULUS -> new Signals(0.48, 0.72, 0.28, 0.0, 0.0, 0.04, 0.34, 0.08);
            case STRATUS -> new Signals(0.82, 0.97, 0.05, 0.0, 0.0, -0.22, 0.22, 0.05);
            case STRATOCUMULUS -> new Signals(0.72, 0.84, 0.30, 0.0, 0.0, 0.05, 0.40, 0.20);
            case CUMULUS -> new Signals(0.72, 0.78, 0.58, 0.12, 0.0, 0.34, 0.56, 0.10);
            case NIMBOSTRATUS -> new Signals(0.90, 0.96, 0.15, 0.22, 0.42, 0.04, 0.44, 0.12);
            case CUMULONIMBUS -> new Signals(0.97, 0.96, 0.86, 0.82, 0.78, 0.74, 0.92, 0.48);
        };
    }

    private static WindVector cloudWindForShear(WindVector surfaceWind, double shear) {
        WindVector surface = Objects.requireNonNullElse(surfaceWind, WindVector.ZERO);
        double targetX = surface.x() + shear <= WeatherSample.MAX_WIND_COMPONENT
                ? surface.x() + shear
                : surface.x() - shear;
        return new WindVector(targetX, surface.z());
    }

    private record Signals(
            double cloudWater,
            double humidity,
            double instability,
            double stormEnergy,
            double precipitation,
            double verticalMotion,
            double cloudDepth,
            double windShear
    ) {
    }
}
