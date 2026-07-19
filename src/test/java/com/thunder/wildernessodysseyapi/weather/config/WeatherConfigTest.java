package com.thunder.wildernessodysseyapi.weather.config;

import com.thunder.wildernessodysseyapi.weather.simulation.SimulationSettings;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies unsafe config captures cannot escape their supported ranges. */
class WeatherConfigTest {

    @Test
    void schedulingSettingsClampUnsafeValues() {
        WeatherConfig.SchedulingSettings settings = new WeatherConfig.SchedulingSettings(
                true,
                -1,
                Integer.MAX_VALUE,
                -4,
                Integer.MAX_VALUE,
                0,
                Integer.MAX_VALUE,
                -100,
                List.of(" MINECRAFT:OVERWORLD ", "invalid id", "minecraft:overworld"),
                List.of("minecraft:the_nether", "broken id"),
                null,
                false
        );

        assertEquals(16, settings.cellSize());
        assertEquals(1_200, settings.simulationIntervalTicks());
        assertEquals(0, settings.activeSimulationRadius());
        assertEquals(1_728_000, settings.inactiveCellGracePeriodTicks());
        assertEquals(20, settings.environmentResampleIntervalTicks());
        assertEquals(1_200, settings.snapshotSyncIntervalTicks());
        assertEquals(64, settings.maxPersistedCells());
        assertEquals(List.of("minecraft:overworld"), settings.dimensionAllowlist());
        assertEquals(List.of("minecraft:the_nether"), settings.dimensionDenylist());
        assertEquals(VanillaWeatherCompatibilityMode.PRESERVE_GLOBAL, settings.compatibilityMode());
    }

    @Test
    void simulationSettingsClampNonFiniteAndOutOfRangeValues() {
        SimulationSettings settings = new SimulationSettings(
                Double.POSITIVE_INFINITY,
                -2.0,
                4.0,
                Double.NaN,
                3.0,
                -1.0,
                4.0,
                -0.5,
                7.0,
                2.0
        );

        assertEquals(1.0, settings.simulationSpeed());
        assertEquals(0.0, settings.humidityTransportRate());
        assertEquals(1.0, settings.temperatureTransportRate());
        assertEquals(0.0, settings.pressureEqualizationRate());
        assertEquals(1.0, settings.evaporationStrength());
        assertEquals(0.05, settings.cloudFormationThreshold());
        assertEquals(0.99, settings.precipitationThreshold());
        assertEquals(0.0, settings.stormFormationThreshold());
        assertEquals(1.0, settings.maximumPrecipitationIntensity());
        assertEquals(0.25, settings.randomVariation());
    }

    @Test
    void lightningSettingsClampCadenceBudgetAndProbability() {
        WeatherConfig.LightningSettings unsafe = new WeatherConfig.LightningSettings(
                true,
                -1,
                Integer.MAX_VALUE,
                0,
                Integer.MAX_VALUE,
                0,
                Double.NaN
        );
        WeatherConfig.LightningSettings cellBelowDimension = new WeatherConfig.LightningSettings(
                true,
                20,
                120,
                20,
                96,
                4,
                2.0
        );

        assertEquals(5, unsafe.checkIntervalTicks());
        assertEquals(72_000, unsafe.dimensionCooldownTicks());
        assertEquals(72_000, unsafe.cellCooldownTicks());
        assertEquals(256, unsafe.candidateRadiusBlocks());
        assertEquals(1, unsafe.maxCandidateAttempts());
        assertEquals(0.20, unsafe.maximumChancePerCheck());
        assertEquals(120, cellBelowDimension.cellCooldownTicks());
        assertEquals(1.0, cellBelowDimension.maximumChancePerCheck());
    }

    @Test
    void denylistOverridesAllowlistAndEmptyAllowlistPermitsNormalDimensions() {
        WeatherConfig.SchedulingSettings selected = new WeatherConfig.SchedulingSettings(
                true, 256, 60, 2, 2_400, 400, 60, 4_096,
                List.of("minecraft:overworld", "minecraft:the_nether"),
                List.of("minecraft:the_nether"),
                VanillaWeatherCompatibilityMode.SUPPRESS_GLOBAL,
                false
        );
        assertTrue(selected.dimensionEnabled(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")));
        assertFalse(selected.dimensionEnabled(ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether")));
        assertFalse(selected.dimensionEnabled(ResourceLocation.fromNamespaceAndPath("minecraft", "the_end")));

        WeatherConfig.SchedulingSettings allNormal = WeatherConfig.SchedulingSettings.DEFAULT;
        assertTrue(allNormal.dimensionEnabled(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")));
        assertTrue(allNormal.dimensionEnabled(ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether")));
    }
}
