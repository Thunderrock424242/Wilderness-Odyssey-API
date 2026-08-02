package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import net.neoforged.fml.ModList;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NeoForge-facing ownership coordinator for mutually exclusive weather systems.
 *
 * <p>External integrations may claim ownership during mod setup. The server
 * config then resolves AUTO, forced Wilderness, or forced external ownership
 * into the allocation-free dimension gate used by tick and mixin hooks.</p>
 */
public final class WeatherOwnershipCoordinator {

    private static final Set<String> EXTERNAL_CLAIMS = ConcurrentHashMap.newKeySet();
    private static volatile WeatherOwnershipPolicy.Decision decision =
            WeatherOwnershipPolicy.Decision.WILDERNESS;

    private WeatherOwnershipCoordinator() {
    }

    /** Registers an external mod as a potential weather authority. */
    public static void claimExternal(String modId) {
        if (modId != null && !modId.isBlank()) {
            EXTERNAL_CLAIMS.add(modId.trim().toLowerCase(Locale.ROOT));
        }
    }

    /** Re-resolves ownership after server config load or reload. */
    public static WeatherOwnershipPolicy.Decision resolve(WeatherConfig.SchedulingSettings settings) {
        WeatherConfig.SchedulingSettings safe = settings == null
                ? WeatherConfig.SchedulingSettings.DEFAULT
                : settings;
        Set<String> configured = Set.copyOf(safe.externalWeatherModIds());
        Set<String> installed = new HashSet<>();
        for (String modId : configured) {
            if (ModList.get().isLoaded(modId)) {
                installed.add(modId);
            }
        }
        decision = WeatherOwnershipPolicy.resolve(
                safe.ownershipMode(),
                installed,
                configured,
                Set.copyOf(EXTERNAL_CLAIMS)
        );
        ModConstants.LOGGER.info(
                "Weather ownership: {} ({})",
                decision.wildernessOwnsWeather() ? "Wilderness Odyssey" : "external/vanilla",
                decision.owner()
        );
        return decision;
    }

    /** Returns the latest resolved ownership decision. */
    public static WeatherOwnershipPolicy.Decision decision() {
        return decision;
    }
}
