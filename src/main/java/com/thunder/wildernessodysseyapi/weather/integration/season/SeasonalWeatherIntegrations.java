package com.thunder.wildernessodysseyapi.weather.integration.season;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.integration.SeasonalWeatherInfluence;
import net.neoforged.fml.ModList;

/**
 * Selects one optional calendar authority for Wilderness weather.
 *
 * <p>Ecliptic takes priority when both mods are present because its optional
 * Serene API bridge can otherwise make one calendar appear through two APIs.
 * Seasonal values influence Wilderness calculations once and never transfer
 * weather ownership to the integration.</p>
 */
public final class SeasonalWeatherIntegrations {

    private SeasonalWeatherIntegrations() {
    }

    /** Discovers the installed season provider during dimension runtime creation. */
    public static SeasonalWeatherInfluence discover() {
        ModList mods = ModList.get();
        if (mods.isLoaded("eclipticseasons")) {
            ModConstants.LOGGER.info("Enabling Ecliptic Seasons influence for localized weather");
            return EclipticSeasonsWeatherInfluence.create();
        }
        if (mods.isLoaded("sereneseasons")) {
            ModConstants.LOGGER.info("Enabling Serene Seasons influence for localized weather");
            return SereneSeasonsWeatherInfluence.create();
        }
        return SeasonalWeatherInfluence.NONE;
    }
}
