package com.thunder.wildernessodysseyapi.weather.client;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientWeatherTimelineTest {

    @Test
    void authoritativeServerTickDeltaControlsTheVisualInterval() {
        assertEquals(3_000_000_000L,
                ClientWeatherTimeline.transitionDurationNanos(100L, 160L, 3_000_000_000L));
    }

    @Test
    void packetDelayUsesOnlyBoundedShortTermProjection() {
        long duration = 3_000_000_000L;
        assertEquals(1.0D, ClientWeatherTimeline.amount(duration, 0L, duration), 1.0E-12D);
        assertEquals(1.25D, ClientWeatherTimeline.amount(duration + 750_000_000L, 0L, duration), 1.0E-12D);
        assertEquals(1.35D, ClientWeatherTimeline.amount(duration * 4L, 0L, duration), 1.0E-12D);
    }

    @Test
    void extrapolationProjectsContinuousFieldsButKeepsNewestCanonicalPhase() {
        WeatherSample from = sample(0.20D, PrecipitationType.RAIN);
        WeatherSample to = sample(0.40D, PrecipitationType.SNOW);

        WeatherSample projected = ClientWeatherTimeline.sample(from, to, 1.25D);

        assertEquals(0.45D, projected.precipitationIntensity(), 1.0E-12D);
        assertEquals(PrecipitationType.SNOW, projected.precipitationType());
    }

    @Test
    void precipitationBlendProducesAContinuousWintryMixWithoutChangingGameplayType() {
        WeatherSample sample = new WeatherSample(
                1.0D, 0.9D, 0.98D, WindVector.ZERO,
                0.8D, 0.4D, 0.3D, 0.7D, PrecipitationType.RAIN
        );

        PrecipitationBlend blend = PrecipitationBlend.from(sample);

        assertTrue(blend.rain() > 0.0F);
        assertTrue(blend.snow() > 0.0F);
        assertEquals(1.0F, blend.rain() + blend.snow() + blend.hail(), 1.0E-6F);
        assertEquals(PrecipitationType.RAIN, sample.precipitationType());
    }

    private static WeatherSample sample(double intensity, PrecipitationType type) {
        return new WeatherSample(
                0.0D, 0.8D, 1.0D, WindVector.ZERO,
                0.7D, 0.4D, 0.5D, intensity, type
        );
    }
}
