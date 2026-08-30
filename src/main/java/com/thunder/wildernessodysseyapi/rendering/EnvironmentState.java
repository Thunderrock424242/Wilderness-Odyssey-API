package com.thunder.wildernessodysseyapi.rendering;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindSample;
import com.thunder.wildernessodysseyapi.weather.client.WeatherVisualState;
import net.minecraft.world.phys.Vec3;

/**
 * Lightweight, immutable camera-local environment shared by client renderers.
 *
 * <p>It is derived once per frame from synchronized weather and wind. It owns
 * no simulation, performs no block scan, and cannot become gameplay authority.</p>
 */
public record EnvironmentState(
        float rainIntensity,
        float snowIntensity,
        float stormIntensity,
        float windSpeed,
        float windDirectionX,
        float windDirectionY,
        float windDirectionZ,
        float wetness,
        float frozenFraction,
        float temperature,
        float humidity,
        float lightningActivity
) {
    public static final EnvironmentState CLEAR = new EnvironmentState(
            0.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 15.0F, 0.35F, 0.0F
    );

    public EnvironmentState {
        rainIntensity = unit(rainIntensity);
        snowIntensity = unit(snowIntensity);
        stormIntensity = unit(stormIntensity);
        windSpeed = finiteClamp(windSpeed, 0.0F, 80.0F);
        wetness = unit(wetness);
        frozenFraction = unit(frozenFraction);
        temperature = finiteClamp(temperature, -80.0F, 60.0F);
        humidity = unit(humidity);
        lightningActivity = unit(lightningActivity);

        float lengthSquared = windDirectionX * windDirectionX
                + windDirectionY * windDirectionY
                + windDirectionZ * windDirectionZ;
        if (!Float.isFinite(lengthSquared) || lengthSquared <= 1.0E-8F || windSpeed <= 1.0E-5F) {
            windDirectionX = 0.0F;
            windDirectionY = 0.0F;
            windDirectionZ = 0.0F;
        } else {
            float inverseLength = 1.0F / (float) Math.sqrt(lengthSquared);
            windDirectionX *= inverseLength;
            windDirectionY *= inverseLength;
            windDirectionZ *= inverseLength;
        }
    }

    /** Creates presentation state from existing immutable weather/wind owners. */
    public static EnvironmentState from(WeatherSample weather, WindSample wind) {
        WeatherSample safeWeather = weather == null ? WeatherSample.CLEAR : weather;
        WindSample safeWind = wind == null ? WindSample.calm(null) : wind;
        float precipitation = (float) safeWeather.precipitationIntensity();
        float rain = safeWeather.precipitationType() == PrecipitationType.SNOW ? 0.0F : precipitation;
        float snow = safeWeather.precipitationType() == PrecipitationType.SNOW ? precipitation : 0.0F;
        float thunder = (float) safeWeather.thunderIntensity();
        Vec3 direction = safeWind.direction();
        return new EnvironmentState(
                rain,
                snow,
                (float) Math.max(safeWeather.stormEnergy(), thunder),
                safeWind.effectiveSpeed(),
                (float) direction.x,
                (float) direction.y,
                (float) direction.z,
                (float) safeWeather.surface().wetness(),
                (float) safeWeather.surface().frozenFraction(),
                (float) safeWeather.temperature(),
                (float) safeWeather.humidity(),
                thunder
        );
    }

    /** Creates shared renderer state from the unified per-frame weather interpretation. */
    public static EnvironmentState from(WeatherVisualState visual) {
        WeatherVisualState state = visual == null ? WeatherVisualState.CLEAR : visual;
        float speed = (float) state.surfaceWind().length();
        Vec3 direction = speed <= 1.0E-5F ? Vec3.ZERO : state.surfaceWind().normalize();
        return new EnvironmentState(
                state.precipitationIntensity()
                        * (state.precipitationBlend().rain() + state.precipitationBlend().hail()),
                state.precipitationIntensity() * state.precipitationBlend().snow(),
                state.stormSeverity(),
                speed,
                (float) direction.x,
                (float) direction.y,
                (float) direction.z,
                state.wetness(),
                (float) state.weather().surface().frozenFraction(),
                (float) state.weather().temperature(),
                (float) state.weather().humidity(),
                Math.max(state.lightningIllumination(), (float) state.weather().thunderIntensity())
        );
    }

    /** Creates a graceful fallback when vanilla owns global precipitation. */
    public static EnvironmentState vanilla(float rain, float thunder, WindSample wind) {
        WindSample safeWind = wind == null ? WindSample.calm(null) : wind;
        Vec3 direction = safeWind.direction();
        return new EnvironmentState(
                rain,
                0.0F,
                Math.max(rain * thunder, thunder),
                safeWind.effectiveSpeed(),
                (float) direction.x,
                (float) direction.y,
                (float) direction.z,
                rain,
                0.0F,
                15.0F,
                0.35F + unit(rain) * 0.55F,
                thunder
        );
    }

    /** Coarse, deterministic input suitable for water-wave and spray presentation. */
    public float waterTurbulence() {
        return unit(stormIntensity * 0.55F
                + rainIntensity * 0.20F
                + Math.min(1.0F, windSpeed / 24.0F) * 0.45F);
    }

    private static float unit(float value) {
        return finiteClamp(value, 0.0F, 1.0F);
    }

    private static float finiteClamp(float value, float minimum, float maximum) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : minimum;
    }
}
