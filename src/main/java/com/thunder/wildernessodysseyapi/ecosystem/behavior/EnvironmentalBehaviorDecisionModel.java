package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorState;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeWeatherResponse;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;

/**
 * Allocation-light priority policy for one already-cached environmental snapshot.
 *
 * <p>This class never scans the world or starts navigation, which keeps its
 * schedule and profile decisions directly unit-testable.</p>
 */
public final class EnvironmentalBehaviorDecisionModel {

    private EnvironmentalBehaviorDecisionModel() {
    }

    /** Selects one supported broad state from priority-ordered environmental signals. */
    public static Decision decide(SpeciesBehaviorProfile profile, Signals signals) {
        SpeciesBehaviorProfile.Environment environment = profile.environment();

        if (signals.threatPresent()
                && profile.prey().enabled()
                && environment.supports(EcosystemBehaviorState.FLEE)) {
            return new Decision(EcosystemBehaviorState.FLEE, "nearby or remembered threat");
        }
        if (signals.weatherResponse().requiresShelterResponse()
                && signals.exposedToSky()
                && signals.shelterAvailable()
                && profile.shelter().enabled()
                && environment.supports(EcosystemBehaviorState.SEEK_SHELTER)) {
            return new Decision(EcosystemBehaviorState.SEEK_SHELTER,
                    "localized " + signals.weatherResponse().serializedName());
        }
        if (signals.cold()
                && signals.exposedToSky()
                && signals.shelterAvailable()
                && profile.shelter().enabled()
                && environment.supports(EcosystemBehaviorState.SEEK_SHELTER)) {
            return new Decision(EcosystemBehaviorState.SEEK_SHELTER,
                    "temperature below preferred range");
        }

        double drinkThreshold = profile.drinking().thirstThreshold();
        if (signals.hotOrDry()) {
            drinkThreshold = Math.max(0.05,
                    drinkThreshold - environment.hotDryDrinkThresholdReduction());
        }
        if (profile.drinking().enabled()
                && signals.waterAvailable()
                && signals.thirst() >= drinkThreshold
                && environment.supports(EcosystemBehaviorState.DRINK)) {
            return new Decision(EcosystemBehaviorState.DRINK,
                    signals.hotOrDry() ? "hot or dry conditions increased water priority" : "thirst threshold reached");
        }

        if (signals.schedulePeriod() == WildlifeSchedule.Period.SLEEP
                && environment.supports(EcosystemBehaviorState.SLEEP)) {
            return new Decision(EcosystemBehaviorState.SLEEP, "inactive daily schedule");
        }
        if (signals.schedulePeriod() == WildlifeSchedule.Period.REST
                && environment.supports(EcosystemBehaviorState.REST)) {
            return new Decision(EcosystemBehaviorState.REST,
                    signals.midday() ? "midday activity lull" : "daily schedule rest period");
        }
        if (signals.rest() >= environment.restThreshold()
                && environment.supports(EcosystemBehaviorState.REST)) {
            return new Decision(EcosystemBehaviorState.REST, "rest need threshold reached");
        }
        if (signals.regroupNeeded()
                && environment.supports(EcosystemBehaviorState.TRAVEL)) {
            return new Decision(EcosystemBehaviorState.TRAVEL, "returning toward group center");
        }

        if (signals.hunger() >= environment.forageHungerThreshold()) {
            if (signals.foodAvailability() >= environment.minimumFoodForForage()
                    && environment.supports(EcosystemBehaviorState.FORAGE)) {
                return new Decision(EcosystemBehaviorState.FORAGE, "local forage and hunger available");
            }
            if (signals.groupLeader()
                    && environment.supports(EcosystemBehaviorState.MIGRATE)) {
                return new Decision(EcosystemBehaviorState.MIGRATE, "local ecosystem forage is depleted");
            }
            if (environment.supports(EcosystemBehaviorState.TRAVEL)) {
                return new Decision(EcosystemBehaviorState.TRAVEL, "searching beyond depleted local forage");
            }
        }

        if (signals.disturbancePresent()
                && environment.supports(EcosystemBehaviorState.TRAVEL)) {
            return new Decision(EcosystemBehaviorState.TRAVEL, "preferring a lower-disturbance area");
        }
        if (signals.routineActivityPulse()
                && environment.supports(EcosystemBehaviorState.TRAVEL)) {
            return new Decision(EcosystemBehaviorState.TRAVEL, "scheduled local movement");
        }
        return new Decision(EcosystemBehaviorState.IDLE, "no environmental action needed");
    }

    /** Classifies weather without treating ordinary light rain as a severe response. */
    public static WildlifeWeatherResponse classifyWeather(
            WeatherSample weather,
            WatershedConditions watershed,
            SpeciesBehaviorProfile.Shelter shelter
    ) {
        WeatherSample sample = weather == null ? WeatherSample.CLEAR : weather;
        WatershedConditions water = watershed == null ? WatershedConditions.NONE : watershed;
        if (water.flooding() || water.floodRisk() >= 0.82f) {
            return WildlifeWeatherResponse.FLOODING;
        }
        if (sample.thunderIntensity() >= shelter.thunderThreshold() || sample.lightningEligible()) {
            return WildlifeWeatherResponse.THUNDERSTORM;
        }
        if (sample.wind().magnitude() >= shelter.windThreshold()) {
            return WildlifeWeatherResponse.SEVERE_WIND;
        }
        if (sample.precipitationIntensity() >= shelter.precipitationThreshold()) {
            return WildlifeWeatherResponse.HEAVY_PRECIPITATION;
        }
        if (sample.hasPrecipitation()) {
            return WildlifeWeatherResponse.LIGHT_RAIN_IGNORED;
        }
        return WildlifeWeatherResponse.CLEAR;
    }

    /** Pure inputs prepared from cached services during one major decision. */
    public record Signals(
            WildlifeSchedule.Period schedulePeriod,
            boolean midday,
            WildlifeWeatherResponse weatherResponse,
            boolean exposedToSky,
            boolean waterAvailable,
            boolean shelterAvailable,
            boolean threatPresent,
            boolean regroupNeeded,
            boolean groupLeader,
            boolean cold,
            boolean hotOrDry,
            boolean disturbancePresent,
            boolean routineActivityPulse,
            double thirst,
            double hunger,
            double rest,
            double foodAvailability
    ) {
    }

    /** Selected state and human-readable diagnostic reason. */
    public record Decision(EcosystemBehaviorState state, String reason) {
    }
}
