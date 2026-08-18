package com.thunder.wildernessodysseyapi.environment.api;

import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteSnapshot;
import com.thunder.wildernessodysseyapi.riftfall.RiftfallStage;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationDisturbanceSample;
import com.thunder.wildernessodysseyapi.weather.api.SeasonalClimateState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherThreatForecast;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedLocalFlow;
import net.minecraft.core.BlockPos;

/**
 * Immutable regional view assembled from the project's existing world owners.
 *
 * <p>The snapshot contains no mutable storage references. It may be cached for
 * a short time and safely shared by wildlife, ambience, survival adapters, and
 * diagnostics without those consumers querying subsystem internals.</p>
 */
public record RegionalEnvironmentSnapshot(
        BlockPos anchor,
        long gameTime,
        EnvironmentDimensionProfile dimensionProfile,
        WeatherSample weather,
        WeatherThreatForecast forecast,
        SeasonalClimateState season,
        WatershedConditions watershed,
        WatershedLocalFlow localFlow,
        TideSystem.TideSample tide,
        VegetationClimateState vegetation,
        VegetationDisturbanceSample vegetationDisturbance,
        MeteorSiteSnapshot meteorSite,
        RiftfallStage riftfallStage,
        EnvironmentInfluence influence
) {

    /** Shared neutral fallback used by compatibility constructors and isolated tests. */
    public static final RegionalEnvironmentSnapshot EMPTY = new RegionalEnvironmentSnapshot(
            BlockPos.ZERO,
            0L,
            EnvironmentDimensionProfile.LIVING_WORLD,
            WeatherSample.CLEAR,
            WeatherThreatForecast.NONE,
            SeasonalClimateState.NONE,
            WatershedConditions.NONE,
            WatershedLocalFlow.NONE,
            new TideSystem.TideSample(0.0F, 0.0F, 0.0F, 0.5F, 0, 0.0F, 0.0F),
            VegetationClimateState.DEFAULT,
            VegetationDisturbanceSample.NONE,
            MeteorSiteSnapshot.NONE,
            RiftfallStage.CLEAR,
            EnvironmentInfluence.NEUTRAL
    );

    /** Validates optional owner results while retaining a completely immutable view. */
    public RegionalEnvironmentSnapshot {
        anchor = anchor == null ? BlockPos.ZERO : anchor.immutable();
        gameTime = Math.max(0L, gameTime);
        dimensionProfile = dimensionProfile == null
                ? EnvironmentDimensionProfile.LIVING_WORLD : dimensionProfile;
        weather = weather == null ? WeatherSample.CLEAR : weather;
        forecast = forecast == null ? WeatherThreatForecast.NONE : forecast;
        season = season == null ? SeasonalClimateState.NONE : season;
        watershed = watershed == null ? WatershedConditions.NONE : watershed;
        localFlow = localFlow == null ? WatershedLocalFlow.NONE : localFlow;
        tide = tide == null ? new TideSystem.TideSample(0.0F, 0.0F, 0.0F, 0.5F, 0, 0.0F, 0.0F) : tide;
        vegetation = vegetation == null ? VegetationClimateState.DEFAULT : vegetation;
        vegetationDisturbance = vegetationDisturbance == null
                ? VegetationDisturbanceSample.NONE : vegetationDisturbance;
        meteorSite = meteorSite == null ? MeteorSiteSnapshot.NONE : meteorSite;
        riftfallStage = riftfallStage == null ? RiftfallStage.CLEAR : riftfallStage;
        influence = influence == null ? EnvironmentInfluence.NEUTRAL : influence;
    }

    /** Returns whether the authoritative watershed classifies this region as coastal. */
    public boolean coastal() {
        return watershed.waterFeature() == WatershedConditions.WaterFeature.COASTAL;
    }
}
