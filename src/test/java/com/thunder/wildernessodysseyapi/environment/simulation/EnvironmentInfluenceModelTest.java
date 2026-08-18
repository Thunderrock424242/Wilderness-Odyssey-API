package com.thunder.wildernessodysseyapi.environment.simulation;

import com.thunder.wildernessodysseyapi.environment.api.EnvironmentInfluence;
import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteSnapshot;
import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteSource;
import com.thunder.wildernessodysseyapi.riftfall.RiftfallStage;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationDisturbanceSample;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationSeasonState;
import com.thunder.wildernessodysseyapi.weather.api.SeasonalClimateState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherThreatForecast;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the allocation-light cross-system model without loading a world. */
class EnvironmentInfluenceModelTest {

    @Test
    void droughtLowersHabitatAndRaisesMigrationPressure() {
        EnvironmentInfluence temperate = evaluate(
                WatershedConditions.NONE,
                VegetationClimateState.DEFAULT,
                MeteorSiteSnapshot.NONE,
                RiftfallStage.CLEAR,
                null
        );
        VegetationClimateState drought = new VegetationClimateState(
                0.10, 0.0, 0.90, 0.0, VegetationSeasonState.DRY,
                0L, 0L, 0, 0.0
        );
        EnvironmentInfluence stressed = evaluate(
                WatershedConditions.NONE, drought, MeteorSiteSnapshot.NONE,
                RiftfallStage.CLEAR, null
        );

        assertTrue(stressed.habitatProductivity() < temperate.habitatProductivity());
        assertTrue(stressed.migrationPressure() > temperate.migrationPressure());
        assertTrue(stressed.vegetationStress() > temperate.vegetationStress());
    }

    @Test
    void floodingRadiationAndRiftfallBecomeOneBoundedHazardSignal() {
        WatershedConditions flooding = watershed(
                WatershedConditions.WaterFeature.RIVER, 0.95f, true);
        MeteorSiteSnapshot irradiated = new MeteorSiteSnapshot(
                true,
                UUID.randomUUID(),
                BlockPos.ZERO,
                24,
                200L,
                1.0,
                MeteorSiteSource.NATURAL,
                4.0,
                0.82
        );

        EnvironmentInfluence influence = evaluate(
                flooding,
                VegetationClimateState.DEFAULT,
                irradiated,
                RiftfallStage.METEOR_SURGE,
                null
        );

        assertTrue(influence.overallHazard() >= 0.99);
        assertTrue(influence.shelterPressure() >= 0.99);
        assertTrue(influence.migrationPressure() >= 0.99);
        assertTrue(influence.wildlifeActivity() < 0.50);
    }

    @Test
    void changingCoastalTideRaisesAquaticActivityWithoutASecondWaterModel() {
        TideSystem.TideSample activeTide = new TideSystem.TideSample(
                0.0f, 0.01f, 1.8f, 0.5f, 0, 1.0f, 0.0f);
        EnvironmentInfluence coastal = evaluate(
                watershed(WatershedConditions.WaterFeature.COASTAL, 0.10f, false),
                VegetationClimateState.DEFAULT,
                MeteorSiteSnapshot.NONE,
                RiftfallStage.CLEAR,
                activeTide
        );
        EnvironmentInfluence inland = evaluate(
                watershed(WatershedConditions.WaterFeature.RIVER, 0.10f, false),
                VegetationClimateState.DEFAULT,
                MeteorSiteSnapshot.NONE,
                RiftfallStage.CLEAR,
                activeTide
        );

        assertTrue(coastal.aquaticActivity() > inland.aquaticActivity());
    }

    private static EnvironmentInfluence evaluate(
            WatershedConditions watershed,
            VegetationClimateState vegetation,
            MeteorSiteSnapshot meteor,
            RiftfallStage riftfall,
            TideSystem.TideSample tide
    ) {
        return EnvironmentInfluenceModel.evaluate(
                WeatherSample.CLEAR,
                WeatherThreatForecast.NONE,
                SeasonalClimateState.NONE,
                watershed,
                tide,
                vegetation,
                VegetationDisturbanceSample.NONE,
                meteor,
                riftfall
        );
    }

    private static WatershedConditions watershed(
            WatershedConditions.WaterFeature feature,
            float floodRisk,
            boolean flooding
    ) {
        return new WatershedConditions(
                7L,
                64,
                WatershedConditions.DrainageDirection.SOUTH,
                0.65f,
                0.72f,
                0.25f,
                0.20f,
                0.50f,
                0.0f,
                floodRisk,
                0.70f,
                flooding,
                flooding ? 3 : 0,
                0.10f,
                0.90f,
                0.0f,
                0.25f,
                0.05f,
                feature
        );
    }
}
