package com.thunder.wildernessodysseyapi.weather.api;

/** Player-facing derived weather phenomena and severe-weather archetypes. */
public enum WeatherPhenomenon {
    NONE("Normal weather", false),
    DENSE_FOG("Dense fog", false),
    LAKE_EFFECT_SNOW("Lake-effect snow", false),
    OCEAN_STORM("Ocean-fed storm", false),
    DROUGHT("Drought", false),
    HEAT_WAVE("Heat wave", false),
    HAIL("Hail", false),
    BLIZZARD("Blizzard", false),
    TORNADO("Tornado", true),
    CYCLONE("Cyclone", true);

    private final String displayName;
    private final boolean severe;

    WeatherPhenomenon(String displayName, boolean severe) {
        this.displayName = displayName;
        this.severe = severe;
    }

    /** Returns a concise player-facing name. */
    public String displayName() {
        return displayName;
    }

    /** Returns whether this phenomenon uses the optional severe-weather effects path. */
    public boolean severe() {
        return severe;
    }
}
