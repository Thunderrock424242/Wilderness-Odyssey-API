package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.weather.api.WeatherQuery;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/**
 * Read-only gameplay service for localized precipitation decisions.
 *
 * <p>This phase deliberately does not rewrite every vanilla rain interaction.
 * Fire, cauldron, farmland, crops, snow, freezing, fishing, mobs, lightning,
 * structures, soundscapes, wetness, and puddles can migrate one adapter at a
 * time through this service instead of consulting the global rain flag.</p>
 */
public final class LocalizedPrecipitationController {

    private static final LocalizedPrecipitationController INSTANCE =
            new LocalizedPrecipitationController(WeatherServices.query());

    private final WeatherQuery weather;

    /** Creates a controller for tests or optional gameplay adapters. */
    public LocalizedPrecipitationController(WeatherQuery weather) {
        this.weather = Objects.requireNonNull(weather, "weather");
    }

    /** Returns the shared controller backed by the active weather authority. */
    public static LocalizedPrecipitationController get() {
        return INSTANCE;
    }

    /** Returns whether exposed terrain is receiving measurable rain or snow. */
    public boolean isExposedToPrecipitation(ServerLevel level, BlockPos position) {
        WeatherSample sample = weather.sample(level, position);
        return sample.hasPrecipitation()
                && sample.precipitationIntensity() >= 0.02
                && level.canSeeSky(position.above());
    }

    /** Returns localized rain strength available to fire or wetness adapters. */
    public double wettingStrength(ServerLevel level, BlockPos position) {
        WeatherSample sample = weather.sample(level, position);
        return sample.isRaining() && level.canSeeSky(position.above())
                ? sample.precipitationIntensity() : 0.0;
    }

    /** Returns a normalized hydration contribution for farmland and crops. */
    public double hydrationContribution(ServerLevel level, BlockPos position) {
        return wettingStrength(level, position);
    }

    /** Returns whether atmospheric conditions permit a localized lightning request. */
    public boolean lightningEligible(ServerLevel level, BlockPos position) {
        WeatherSample sample = weather.sample(level, position);
        return sample.lightningEligible() && level.canSeeSky(position.above());
    }

    /** Returns the visibility multiplier future AI and structure logic can consume. */
    public double visibilityMultiplier(ServerLevel level, BlockPos position) {
        WeatherSample sample = weather.sample(level, position);
        return Math.max(0.15, 1.0 - sample.fogContribution() * 0.65
                - sample.precipitationIntensity() * 0.25);
    }

    /** Returns authoritative local air temperature for exposure/freezing adapters. */
    public double temperatureCelsius(ServerLevel level, BlockPos position) {
        return weather.temperatureAt(level, position);
    }
}
