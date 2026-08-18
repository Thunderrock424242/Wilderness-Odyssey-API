package com.thunder.wildernessodysseyapi.ecosystem.api;

import com.thunder.wildernessodysseyapi.weather.api.WeatherThreatForecast;

import java.util.Optional;
import java.util.UUID;

/**
 * Immutable individual or leader-owned decision shared by a herd or flock.
 *
 * <p>The decision carries only immutable forecast/profile data and an optional
 * result from the existing bounded shelter locator.</p>
 */
public record WeatherReactionDecision(
        WeatherThreatForecast forecast,
        StormSensitivity sensitivity,
        StormReaction response,
        Optional<EnvironmentalContext.ShelterTarget> shelter,
        UUID decisionMakerId,
        int groupSize,
        boolean inherited
) {

    public static final WeatherReactionDecision NONE = new WeatherReactionDecision(
            WeatherThreatForecast.NONE,
            StormSensitivity.GENERIC,
            StormReaction.NORMAL,
            Optional.empty(),
            null,
            1,
            false
    );

    public WeatherReactionDecision {
        forecast = forecast == null ? WeatherThreatForecast.NONE : forecast;
        sensitivity = sensitivity == null ? StormSensitivity.GENERIC : sensitivity;
        response = response == null ? StormReaction.NORMAL : response;
        shelter = shelter == null ? Optional.empty() : shelter;
        groupSize = Math.max(1, groupSize);
    }

    /** Returns a follower view while retaining the leader's forecast and shelter target. */
    public WeatherReactionDecision asInherited() {
        return new WeatherReactionDecision(
                forecast,
                sensitivity,
                response,
                shelter,
                decisionMakerId,
                groupSize,
                true
        );
    }
}
