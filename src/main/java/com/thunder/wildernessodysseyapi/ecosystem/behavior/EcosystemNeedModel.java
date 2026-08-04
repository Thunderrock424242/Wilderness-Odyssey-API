package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;

/** Pure normalized need integration used by runtime code and deterministic tests. */
public final class EcosystemNeedModel {

    private EcosystemNeedModel() {
    }

    /** Advances needs using elapsed ticks and a bounded environmental summary. */
    public static Values advance(
            Values current,
            SpeciesBehaviorProfile.Needs profile,
            long elapsedTicks,
            double temperatureCelsius,
            boolean moving,
            boolean preferredActivePeriod,
            boolean sheltered,
            double foodAvailability,
            boolean herdNearby,
            boolean threatened,
            double thirstRateMultiplier,
            double speciesMultiplier
    ) {
        double minutes = Math.max(0L, Math.min(24_000L, elapsedTicks)) / 1_200.0;
        double heat = temperatureCelsius <= profile.hotTemperatureCelsius()
                ? 1.0
                : 1.0 + Math.min(1.0, (temperatureCelsius - profile.hotTemperatureCelsius()) / 12.0)
                * (profile.heatThirstMultiplier() - 1.0);
        double activity = moving ? profile.activityThirstMultiplier() : 1.0;
        double thirst = current.thirst()
                + profile.thirstPerMinute() * minutes * heat * activity
                * Math.max(0.0, thirstRateMultiplier) * Math.max(0.0, speciesMultiplier);

        // Forage represents occasional ambient grazing in this foundation; it
        // slows rather than instantly erases hunger and never consumes blocks.
        double forageRelief = Math.max(0.0, Math.min(1.0, foodAvailability)) * 0.45;
        double hunger = current.hunger()
                + profile.hungerPerMinute() * minutes * (1.0 - forageRelief) * Math.max(0.0, speciesMultiplier);

        double restDelta = profile.restPerMinute() * minutes * (preferredActivePeriod ? 1.0 : 0.25);
        double rest = sheltered && !preferredActivePeriod
                ? current.rest() - restDelta * 2.0
                : current.rest() + restDelta;
        double social = herdNearby
                ? current.social() - minutes * 0.10
                : current.social() + minutes * 0.06 * Math.max(0.0, speciesMultiplier);
        double safety = threatened
                ? 1.0
                : current.safetyConcern() - minutes * 0.25;
        return new Values(unit(thirst), unit(hunger), unit(rest), unit(social), unit(safety));
    }

    /** Normalized motivation tuple shared with the attachment without world references. */
    public record Values(double thirst, double hunger, double rest, double social, double safetyConcern) {
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }
}
