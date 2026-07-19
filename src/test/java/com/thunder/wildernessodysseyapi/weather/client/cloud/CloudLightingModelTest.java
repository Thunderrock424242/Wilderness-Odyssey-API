package com.thunder.wildernessodysseyapi.weather.client.cloud;

import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudLightingModelTest {

    @Test
    void clearUnsupportedFieldLeavesDaylightAndFogUntouched() {
        CloudLightingModel.OpticalState optics = CloudLightingModel.evaluate(CloudFieldSample.CLEAR);

        assertEquals(0.0, optics.shadow(), 1.0E-12);
        assertEquals(0.0, optics.stormFog(), 1.0E-12);
        assertEquals(WeatherSample.CLEAR.skyDarkening(),
                CloudLightingModel.skyDarkening(WeatherSample.CLEAR, CloudFieldSample.CLEAR),
                1.0E-12);
    }

    @Test
    void denseDryCloudsCastAVisibleBoundedShadow() {
        CloudFieldSample overcast = field(0.95, 0.0, 0.10, 1.0);
        CloudLightingModel.OpticalState optics = CloudLightingModel.evaluate(overcast);

        assertTrue(optics.coverage() > 0.95);
        assertTrue(optics.shadow() > 0.45);
        assertTrue(optics.shadow() <= 1.0);
    }

    @Test
    void RainDeepensBothShadowAndFog() {
        CloudLightingModel.OpticalState dry = CloudLightingModel.evaluate(field(0.75, 0.0, 0.10, 1.0));
        CloudLightingModel.OpticalState storm = CloudLightingModel.evaluate(field(0.95, 0.85, 0.90, 1.0));

        assertTrue(storm.shadow() > dry.shadow());
        assertTrue(storm.stormFog() > dry.stormFog());
        assertTrue(storm.skyDarkeningSignal() <= 1.0);
    }

    @Test
    void networkSupportFadesCloudOptics() {
        CloudLightingModel.OpticalState full = CloudLightingModel.evaluate(field(0.95, 0.70, 0.80, 1.0));
        CloudLightingModel.OpticalState edge = CloudLightingModel.evaluate(field(0.95, 0.70, 0.80, 0.20));

        assertTrue(edge.shadow() < full.shadow());
        assertTrue(edge.stormFog() < full.stormFog());
    }

    @Test
    void weatherFogNeverExpandsAStatusEffectFarPlane() {
        assertEquals(10.0, CloudLightingModel.attenuatedFogFarPlane(10.0, 1.0), 1.0E-12);
        assertEquals(512.0, CloudLightingModel.attenuatedFogFarPlane(512.0, 0.0), 1.0E-12);
        assertTrue(CloudLightingModel.attenuatedFogFarPlane(512.0, 1.0) < 512.0);
    }

    private static CloudFieldSample field(
            double cloudWater,
            double precipitation,
            double storm,
            double support
    ) {
        return new CloudFieldSample(cloudWater, precipitation, storm, 0.4, 0.0, 0.0, support);
    }
}
