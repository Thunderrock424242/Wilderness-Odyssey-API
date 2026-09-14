package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.*;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.AtmosphericWaterExchange;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Behavioral physical contracts; no Minecraft level, chunk, player or worker is required. */
class AtmosphericPhysicsTest {
    @Test
    void theSamePhysicalFrontGradientHasTheSameClassificationAtDifferentCellWidths() {
        var center = state(10, .8, 0, 0);
        AtmosphericFrontModel.FrontState reference = null;
        for (int width : new int[]{128, 256, 512}) {
            var warm = state(10 + width * .04, .8, 0, 0);
            var cold = state(10 - width * .04, .8, 0, 0);
            var front = AtmosphericFrontModel.analyze(center,
                    new AtmosphereSimulationEngine.PhysicalNeighborhood(center, warm, center, cold), width);
            if (reference == null) reference = front;
            assertEquals(reference.type(), front.type());
            assertEquals(reference.strength(), front.strength(), 1.0E-10);
            assertEquals(reference.lift(), front.lift(), 1.0E-10);
        }
        assertTrue(reference.strength() > 0);
    }
    private static final AtmosphereSimulationEngine ENGINE = new AtmosphereSimulationEngine();
    private static final AtmosphericWaterExchange.Receipt CLOSED = new AtmosphericWaterExchange.Receipt(
            0, 0, 1, 0, 256 * 256, List.of());

    @Test
    void closedMoistureBudgetConservesVaporLiquidIceAndAccumulatedPrecipitation() {
        double vapor = 20, liquid = 3, ice = 2, rain = 0;
        for (int i = 0; i < 400; i++) {
            var result = AtmosphericMoistureBudget.advance(vapor, liquid, ice, 10, -12, 5, 2200,
                    0.5, SimulationSettings.DEFAULT);
            vapor = result.vapor(); liquid = result.liquid(); ice = result.ice(); rain += result.precipitation();
            assertEquals(25, vapor + liquid + ice + rain, 1.0E-10);
        }
        assertTrue(rain > 0);
        assertTrue(ice > 0);
    }

    @Test
    void dryCloudDissipationReturnsItsMassToVapor() {
        var result = AtmosphericMoistureBudget.advance(0.1, 0.2, 0.1, 25, 5, 0, 0, 2,
                SimulationSettings.DEFAULT);
        assertTrue(result.vapor() > 0.1);
        assertTrue(result.cloudEvaporation() > 0);
        assertEquals(0.4, result.vapor() + result.liquid() + result.ice() + result.precipitation(), 1.0E-12);
    }

    @Test
    void acceptedSurfaceDebitEqualsAtmosphericCreditEvenAtDifferentSimulationSpeeds() {
        var start = state(15, 0.2, 0, 0);
        long debit = 4096000;
        var receipt = new AtmosphericWaterExchange.Receipt(0, debit, 1, 0, 256 * 256, List.of());
        for (double speed : new double[]{0.5, 1, 4}) {
            var defaults = SimulationSettings.DEFAULT;
            var settings = new SimulationSettings(speed, 0, 0, 0, 0, .99, .99, 1, 0, 0, 0);
            var result = ENGINE.simulatePhysical(start, AtmosphereEnvironment.TEMPERATE,
                    AtmosphereSimulationEngine.PhysicalNeighborhood.uniform(start), settings, .1, 256, receipt);
            double expectedMm = debit / (double) AtmosphericWaterExchange.MILLI_UNITS_PER_CUBIC_METRE / (256 * 256) * 1000;
            assertEquals(expectedMm, result.flux().evaporationMm(), 1.0E-12);
            assertEquals(start.totalWaterKgPerSquareMetre() + expectedMm,
                    result.state().totalWaterKgPerSquareMetre() + result.flux().precipitationMm(), 1.0E-12);
        }
    }

    @Test
    void physicalStateKeepsSupersaturatedMassWhenDisplayHumidityClips() {
        var legacy = state(0, 1, 0, 0);
        var excess = new AtmosphericPhysicalState(0, 1013.25, 30, 2, 1, legacy.column(), 0, 0, 0, 0, 0,
                SurfaceWeatherState.DRY);
        assertEquals(1, excess.toWeatherSample(excess.surface(), PrecipitationType.NONE).humidity());
        assertEquals(33, excess.totalWaterKgPerSquareMetre());
        var result = ENGINE.simulatePhysical(excess, AtmosphereEnvironment.TEMPERATE,
                AtmosphereSimulationEngine.PhysicalNeighborhood.uniform(excess), SimulationSettings.DEFAULT,
                .5, 256, CLOSED);
        assertEquals(33, result.state().totalWaterKgPerSquareMetre() + result.flux().precipitationMm(), 1.0E-10);
    }

    @Test
    void supportedTimestepsConvergeOverTheSameSimulatedDuration() {
        var start = state(12, .9, .8, .3);
        var fine = evolve(start, .25, 60);
        var coarse = evolve(start, .5, 60);
        assertEquals(fine.temperatureCelsius(), coarse.temperatureCelsius(), .1);
        assertEquals(fine.totalWaterKgPerSquareMetre(), coarse.totalWaterKgPerSquareMetre(), .02);
        assertEquals(fine.cloudWaterKgPerSquareMetre(), coarse.cloudWaterKgPerSquareMetre(), .03);
        assertEquals(fine.verticalVelocityMetresPerSecond(), coarse.verticalVelocityMetresPerSecond(), .1);
    }

    @Test
    void strongerPhysicalGradientAcceleratesWindMoreAndDistanceScalingIsInvariant() {
        var layer = new AtmosphericLayer(0, 1013.25, 15, .5, 0, 0);
        var weak = WindPhysicsModel.advance(layer, 1014, 1012, 1013, 1013, 256, 1, .2, 0, 1);
        var strong = WindPhysicsModel.advance(layer, 1015, 1011, 1013, 1013, 256, 1, .2, 0, 1);
        var halfDistance = WindPhysicsModel.advance(layer, 1013.5, 1012.5, 1013, 1013, 128, 1, .2, 0, 1);
        assertTrue(strong.x() > weak.x());
        assertEquals(weak.x(), halfDistance.x(), 1.0E-12);
        assertEquals(0, strong.z(), 1.0E-12);
    }

    @Test
    void boundaryLayerFrictionIsStrongerThanUpperLayerFriction() {
        var lower = new AtmosphericLayer(0, 1013, 15, .5, 10, 0);
        var upper = new AtmosphericLayer(1500, 1013, 15, .5, 10, 0);
        var lowWind = WindPhysicsModel.advance(lower, 1013, 1013, 1013, 1013, 256, 20, .8, 0, 1);
        var highWind = WindPhysicsModel.advance(upper, 1013, 1013, 1013, 1013, 256, 20, .8, 0, 1);
        assertTrue(lowWind.magnitude() < highWind.magnitude());
    }

    @Test
    void phaseFollowsColdWarmAndMeltingRefreezingProfiles() {
        assertEquals(PrecipitationType.SNOW, phase(column(-5, -8, -15, -30)));
        assertEquals(PrecipitationType.RAIN, phase(column(12, 7, 2, -20)));
        assertEquals(PrecipitationType.SLEET, phase(column(-8, 3, 1, -20)));
        assertEquals(PrecipitationType.FREEZING_RAIN, phase(column(-1, 8, 2, -20)));
        assertEquals(PrecipitationType.NONE, PrecipitationPhaseModel.classify(0, column(-5, -8, -15, -30), 0, 0, 0));
    }

    @Test
    void hailRequiresDeepFreezingCloudAndStrongConvection() {
        var column = column(20, 6, -5, -25);
        assertEquals(PrecipitationType.HAIL, PrecipitationPhaseModel.classify(10, column, 2500, 15, 4500));
        assertEquals(PrecipitationType.RAIN, PrecipitationPhaseModel.classify(10, column, 300, 1, 1200));
    }

    @Test
    void terrainProducesSignedLiftAndWindwardCondensation() {
        var air = AtmosphericPhysicalState.fromLegacy(new WeatherSample(15, .9, 1, new WindVector(.4, 0),
                .7, .5, .4, .2, PrecipitationType.RAIN));
        var windward = environment(.5, 0, .15);
        var leeward = environment(.5, 0, -.15);
        assertTrue(WindPhysicsModel.orographicVelocity(windward, air.column().surface()) > 0);
        assertTrue(WindPhysicsModel.orographicVelocity(leeward, air.column().surface()) < 0);
        double wet = 0, dry = 0;
        var up = air; var down = air;
        for (int i = 0; i < 120; i++) {
            var u = ENGINE.simulatePhysical(up, windward, AtmosphereSimulationEngine.PhysicalNeighborhood.uniform(up),
                    SimulationSettings.DEFAULT, .5, 256, CLOSED);
            var d = ENGINE.simulatePhysical(down, leeward, AtmosphereSimulationEngine.PhysicalNeighborhood.uniform(down),
                    SimulationSettings.DEFAULT, .5, 256, CLOSED);
            up = u.state(); down = d.state(); wet += u.flux().condensationMm(); dry += d.flux().condensationMm();
        }
        assertTrue(up.verticalVelocityMetresPerSecond() > down.verticalVelocityMetresPerSecond());
        assertTrue(wet > dry);
    }

    @Test
    void waterModeratesSolarHeatingAndCloudsReduceNightCooling() {
        var clear = state(15, .4, 0, 0);
        double land = SurfaceEnergyModel.surfaceTemperature(clear, environment(1, 0, 0), 60, 0);
        double lake = SurfaceEnergyModel.surfaceTemperature(clear, environment(1, 1, 0), 60, 0);
        assertTrue(land > lake);
        var cloudy = state(15, .4, 1, 0);
        assertTrue(SurfaceEnergyModel.surfaceTemperature(cloudy, environment(0, 0, 0), 60, 0)
                > SurfaceEnergyModel.surfaceTemperature(clear, environment(0, 0, 0), 60, 0));
        var snowy = new AtmosphericPhysicalState(clear.temperatureCelsius(), clear.pressureHpa(), clear.vaporKgPerSquareMetre(),
                0, 0, clear.column(), 0, 0, 0, 15, 0, new SurfaceWeatherState(0, 0, 1, 0));
        assertTrue(SurfaceEnergyModel.surfaceTemperature(snowy, environment(1, 0, 0), 60, 0) < land);
    }

    private static AtmosphericPhysicalState evolve(AtmosphericPhysicalState state, double dt, double duration) {
        for (int i = 0; i < Math.round(duration / dt); i++) {
            state = ENGINE.simulatePhysical(state, AtmosphereEnvironment.TEMPERATE,
                    AtmosphereSimulationEngine.PhysicalNeighborhood.uniform(state), SimulationSettings.DEFAULT,
                    dt, 256, CLOSED).state();
        }
        return state;
    }

    private static AtmosphericPhysicalState state(double t, double humidity, double cloud, double storm) {
        return AtmosphericPhysicalState.fromLegacy(new WeatherSample(t, humidity, 1, WindVector.ZERO,
                cloud, .3, storm, 0, PrecipitationType.NONE));
    }

    private static AtmosphereEnvironment environment(double sun, double water, double slope) {
        return new AtmosphereEnvironment(15, .5, 64, water, sun, 0, 0, 0,
                0, 1, slope, 0, 0, water, 0);
    }

    private static AtmosphericColumn column(double ground, double low, double middle, double high) {
        return new AtmosphericColumn(new AtmosphericLayer(0, 1013, ground, 1, 0, 0),
                new AtmosphericLayer(1500, 850, low, 1, 0, 0), new AtmosphericLayer(3000, 700, middle, 1, 0, 0),
                new AtmosphericLayer(5500, 500, high, 1, 0, 0));
    }

    private static PrecipitationType phase(AtmosphericColumn column) {
        return PrecipitationPhaseModel.classify(10, column, 0, 0, 2000);
    }
}
