package com.thunder.wildernessodysseyapi.watersystem.ocean.coast;

/**
 * Pure seasonal presentation model fed by server-synchronized climate values.
 *
 * <p>The model does not load Ecliptic Seasons or own a calendar. The existing
 * weather integration applies that calendar to atmospheric temperature and
 * surface freeze state on the server; coastal rendering consumes only those
 * neutral synchronized values.</p>
 */
public final class CoastalSeasonModel {

    public static final Sample NEUTRAL = new Sample(1.0f, 0.0f, 0.0f, 0.0f, 1.0f);

    private CoastalSeasonModel() {
    }

    /**
     * Converts local climate into bounded color, foam, and cold-mist controls.
     *
     * @param shoreType authored shoreline character
     * @param temperatureCelsius synchronized local air temperature
     * @param snowpack synchronized surface snow coverage in {@code [0, 1]}
     * @param frozenFraction synchronized surface freeze fraction in {@code [0, 1]}
     * @param glacialMeltFraction glacial seasonal melt fraction, neutral elsewhere
     */
    public static Sample sample(
            CoastalWaveProfile.ShoreType shoreType,
            double temperatureCelsius,
            double snowpack,
            double frozenFraction,
            double glacialMeltFraction
    ) {
        CoastalWaveProfile.ShoreType type = shoreType == null
                ? CoastalWaveProfile.ShoreType.TEMPERATE : shoreType;
        float temperature = finite((float) temperatureCelsius, 12.0f);
        float snow = unit((float) snowpack);
        float frozen = unit((float) frozenFraction);
        float melt = unit((float) glacialMeltFraction);
        float warmth = unit((temperature - 16.0f) / 18.0f);
        float cold = unit((5.0f - temperature) / 20.0f);
        float winterSurface = Math.max(cold * 0.55f, Math.max(snow, frozen));

        float tropical = type == CoastalWaveProfile.ShoreType.TROPICAL
                ? 0.24f + warmth * 0.76f : 0.0f;
        float coldBlue = switch (type) {
            case GLACIAL -> 0.62f + (1.0f - melt) * 0.28f;
            case COLD -> 0.22f + winterSurface * 0.42f;
            default -> winterSurface * 0.16f;
        };
        float brightness = 1.0f + tropical * 0.08f - winterSurface * 0.055f;
        float mist = switch (type) {
            case GLACIAL -> 0.34f + cold * 0.42f + (1.0f - melt) * 0.18f;
            case COLD -> cold * 0.44f + frozen * 0.22f;
            default -> cold * 0.12f;
        };
        float foamMultiplier = 1.0f + winterSurface * 0.10f
                + (type == CoastalWaveProfile.ShoreType.GLACIAL ? 0.08f : 0.0f);
        return new Sample(
                finiteClamp(brightness, 0.85f, 1.12f, 1.0f),
                unit(tropical),
                unit(coldBlue),
                unit(mist),
                finiteClamp(foamMultiplier, 0.85f, 1.25f, 1.0f)
        );
    }

    private static float unit(float value) {
        return finiteClamp(value, 0.0f, 1.0f, 0.0f);
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float finiteClamp(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }

    /** Bounded presentation controls shared by coastal geometry and effects. */
    public record Sample(
            float brightness,
            float tropicalClarity,
            float coldBlue,
            float mist,
            float foamMultiplier
    ) {
        public Sample {
            brightness = finiteClamp(brightness, 0.85f, 1.12f, 1.0f);
            tropicalClarity = unit(tropicalClarity);
            coldBlue = unit(coldBlue);
            mist = unit(mist);
            foamMultiplier = finiteClamp(foamMultiplier, 0.85f, 1.25f, 1.0f);
        }
    }
}
