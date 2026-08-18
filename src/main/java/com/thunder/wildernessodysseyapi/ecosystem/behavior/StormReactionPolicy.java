package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.ecosystem.api.ShelterPreference;
import com.thunder.wildernessodysseyapi.ecosystem.api.StormReaction;
import com.thunder.wildernessodysseyapi.ecosystem.api.StormSensitivity;
import com.thunder.wildernessodysseyapi.weather.api.WeatherThreat;
import com.thunder.wildernessodysseyapi.weather.api.WeatherThreatForecast;

/** Pure species-sensitive policy that converts one regional forecast into a response. */
public final class StormReactionPolicy {

    private StormReactionPolicy() {
    }

    /** Selects normal, alert, or shelter behavior without querying world state. */
    public static StormReaction decide(
            WeatherThreatForecast forecast,
            StormSensitivity sensitivity
    ) {
        WeatherThreatForecast incoming = forecast == null ? WeatherThreatForecast.NONE : forecast;
        StormSensitivity profile = sensitivity == null ? StormSensitivity.GENERIC : sensitivity;
        if (!incoming.incoming()
                || incoming.type() == WeatherThreat.LIGHT_RAIN
                || incoming.distanceBlocks() > profile.detectionDistanceBlocks()
                || incoming.intensity() < profile.minimumIntensity()) {
            return StormReaction.NORMAL;
        }

        long eta = incoming.estimatedArrivalTicks();
        double alertness = profile.alertness();
        long alertWindow = Math.round(3_600.0 + alertness * 3_600.0);
        long severeShelterWindow = Math.round(1_200.0 + alertness * 2_400.0);
        long extremeShelterWindow = Math.round(2_400.0 + alertness * 2_400.0);

        return switch (incoming.type()) {
            case NONE, LIGHT_RAIN -> StormReaction.NORMAL;
            case RAIN -> alertness >= 0.65 && eta <= Math.round(1_200.0 + alertness * 1_800.0)
                    ? StormReaction.ALERT : StormReaction.NORMAL;
            case THUNDERSTORM -> eta <= alertWindow ? StormReaction.ALERT : StormReaction.NORMAL;
            case SEVERE_STORM -> eta <= severeShelterWindow
                    ? shelterOrAlert(profile) : eta <= alertWindow ? StormReaction.ALERT : StormReaction.NORMAL;
            case EXTREME_WEATHER -> eta <= extremeShelterWindow
                    ? shelterOrAlert(profile) : eta <= alertWindow ? StormReaction.ALERT : StormReaction.NORMAL;
        };
    }

    private static StormReaction shelterOrAlert(StormSensitivity sensitivity) {
        return sensitivity.shelterPreference() == ShelterPreference.NONE
                ? StormReaction.ALERT
                : StormReaction.SEEK_SHELTER;
    }
}
