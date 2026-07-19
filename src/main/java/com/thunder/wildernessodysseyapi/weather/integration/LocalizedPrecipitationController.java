package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.weather.api.WeatherQuery;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Objects;

/**
 * Read-only gameplay service for localized precipitation decisions.
 *
 * <p>Vanilla's position-aware rain and loaded-column precipitation paths now
 * delegate here. Remaining global-only or feature-specific consumers can move
 * one adapter at a time without learning the atmospheric grid layout.</p>
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
        return weather.precipitationTypeAt(level, position) != PrecipitationType.NONE
                && isExposed(level, position);
    }

    /** Mirrors vanilla rain exposure while sourcing rain from the local atmosphere. */
    public boolean isRainingAt(ServerLevel level, BlockPos position) {
        return weather.isRainingAt(level, position)
                && isExposed(level, position);
    }

    /** Returns the physical precipitation type, or none below the wetting threshold. */
    public PrecipitationType precipitationTypeAt(ServerLevel level, BlockPos position) {
        return weather.precipitationTypeAt(level, position);
    }

    /** Returns localized rain strength available to fire or wetness adapters. */
    public double wettingStrength(ServerLevel level, BlockPos position) {
        return weather.isRainingAt(level, position) && isExposed(level, position)
                ? weather.precipitationIntensityAt(level, position) : 0.0;
    }

    /** Returns normalized open-sky rain available above a farmland block. */
    public double hydrationContribution(ServerLevel level, BlockPos farmlandPosition) {
        return farmlandPosition == null
                ? 0.0
                : wettingStrength(level, farmlandPosition.above());
    }

    /** Returns whether atmospheric conditions permit a localized lightning request. */
    public boolean lightningEligible(ServerLevel level, BlockPos position) {
        WeatherSample sample = weather.sample(level, position);
        return sample.lightningEligible() && isExposed(level, position);
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

    private static boolean isExposed(ServerLevel level, BlockPos position) {
        return level != null
                && position != null
                && level.canSeeSky(position)
                && level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, position).getY()
                <= position.getY();
    }
}
