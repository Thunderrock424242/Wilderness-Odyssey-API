package com.thunder.wildernessodysseyapi.weather.integration.survival;

import com.thunder.wildernessodysseyapi.weather.api.WeatherQuery;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

/**
 * Discovers and coordinates optional survival mods without owning their state.
 *
 * <p>Cold Sweat retains temperature/body simulation authority and Thirst Was
 * Taken retains thirst state. Wilderness contributes only localized weather
 * exposure while it owns weather in the current dimension.</p>
 */
public final class SurvivalWeatherIntegrations {

    private static volatile boolean bootstrapped;
    private static volatile boolean coldSweatLoaded;
    private static volatile ThirstWasTakenWeatherBridge thirstBridge;

    private SurvivalWeatherIntegrations() {
    }

    /** Discovers optional mods and installs guarded runtime hooks once. */
    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        ModList mods = ModList.get();
        coldSweatLoaded = mods.isLoaded(ColdSweatWeatherBridge.MOD_ID);
        if (coldSweatLoaded) {
            ColdSweatWeatherBridge.bootstrap();
        }
        if (mods.isLoaded(ThirstWasTakenWeatherBridge.MOD_ID)) {
            thirstBridge = ThirstWasTakenWeatherBridge.discover();
        }
        bootstrapped = true;
    }

    /** Runs the bounded thirst contribution after authoritative weather updates. */
    public static void tick(ServerLevel level, WeatherQuery weather) {
        if (!bootstrapped) {
            bootstrap();
        }
        ThirstWasTakenWeatherBridge bridge = thirstBridge;
        if (bridge == null || !bridge.active()) {
            return;
        }
        WeatherConfig.SurvivalIntegrationSettings settings = WeatherConfig.survivalIntegrations();
        bridge.tick(
                level,
                weather,
                settings,
                coldSweatLoaded && settings.coldSweatEnabled() && ColdSweatWeatherBridge.active()
        );
    }
}
