package com.thunder.wildernessodysseyapi.weather.system;

/** Persistent moving atmospheric-system archetype. */
public enum WeatherSystemType {
    STORM(Family.STORM, false),
    WARM_FRONT(Family.FRONT, false),
    COLD_FRONT(Family.FRONT, false),
    STATIONARY_FRONT(Family.FRONT, false),
    OCCLUDED_FRONT(Family.FRONT, false),
    TORNADO(Family.STORM, true),
    CYCLONE(Family.STORM, true);

    private final Family family;
    private final boolean severe;

    WeatherSystemType(Family family, boolean severe) {
        this.family = family;
        this.severe = severe;
    }

    /** Returns whether two types may retain identity while changing subtype. */
    public boolean compatibleWith(WeatherSystemType other) {
        return other != null && family == other.family;
    }

    /** Returns whether this system uses optional severe-weather effects. */
    public boolean severe() {
        return severe;
    }

    /** Returns whether this identity represents a storm rather than an atmospheric front. */
    public boolean storm() {
        return family == Family.STORM;
    }

    private enum Family {
        STORM,
        FRONT
    }
}
