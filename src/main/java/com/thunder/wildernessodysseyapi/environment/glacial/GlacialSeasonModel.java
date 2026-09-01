package com.thunder.wildernessodysseyapi.environment.glacial;

import com.thunder.wildernessodysseyapi.weather.api.SeasonalClimateState;

/** Pure mapping from the shared season API into glacier-specific melt and freeze strength. */
public final class GlacialSeasonModel {

    private GlacialSeasonModel() {
    }

    /** Converts a calendar sample without depending on Ecliptic or Serene classes. */
    public static GlacialSeasonSnapshot evaluate(SeasonalClimateState climate) {
        SeasonalClimateState state = climate == null ? SeasonalClimateState.NONE : climate;
        if (!state.calendarAvailable()) {
            return GlacialSeasonSnapshot.POLAR_COLD;
        }

        double phase = state.cyclePhase();
        if (Double.isFinite(phase)) {
            double wrapped = phase - Math.floor(phase);
            double temperatureWave = Math.cos(Math.PI * 2.0 * (wrapped - 0.375));
            double melt = unit(
                    0.5
                            + temperatureWave * 0.42
                            + state.fireSeasonFactor() * 0.08
                            - state.snowSeasonFactor() * 0.08
            );
            return new GlacialSeasonSnapshot(
                    seasonAt(wrapped),
                    melt,
                    1.0 - melt,
                    true,
                    false
            );
        }

        // Tropical or third-party calendars may supply bounded climate effects
        // without a four-season phase. Keep the glacier polar and classify only
        // the strongest warm/cold conclusions instead of inventing dates.
        double melt = unit(
                0.42
                        + state.temperatureOffsetCelsius() / 24.0
                        + state.fireSeasonFactor() * 0.28
                        - state.snowSeasonFactor() * 0.35
        );
        GlacialSeason season = melt >= 0.68
                ? GlacialSeason.SUMMER
                : melt <= 0.28 ? GlacialSeason.WINTER : GlacialSeason.SPRING;
        return new GlacialSeasonSnapshot(season, melt, 1.0 - melt, true, false);
    }

    /** Returns a fixed development-only state without mutating an external calendar. */
    public static GlacialSeasonSnapshot override(GlacialSeason season) {
        GlacialSeason safe = season == null ? GlacialSeason.POLAR_COLD : season;
        double melt = switch (safe) {
            case WINTER -> 0.04;
            case SPRING -> 0.42;
            case SUMMER -> 0.94;
            case AUTUMN -> 0.34;
            case POLAR_COLD -> 0.08;
        };
        return new GlacialSeasonSnapshot(safe, melt, 1.0 - melt, false, true);
    }

    static GlacialSeason seasonAt(double wrappedPhase) {
        double phase = wrappedPhase - Math.floor(wrappedPhase);
        if (phase < 0.25) {
            return GlacialSeason.SPRING;
        }
        if (phase < 0.50) {
            return GlacialSeason.SUMMER;
        }
        if (phase < 0.75) {
            return GlacialSeason.AUTUMN;
        }
        return GlacialSeason.WINTER;
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }
}
