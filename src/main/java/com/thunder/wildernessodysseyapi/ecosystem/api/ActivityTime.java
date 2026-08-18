package com.thunder.wildernessodysseyapi.ecosystem.api;

import java.util.Locale;

/** Defines the part of the Minecraft day in which a species is normally active. */
public enum ActivityTime {
    DIURNAL,
    NOCTURNAL,
    CREPUSCULAR,
    FLEXIBLE;

    /** Returns the lowercase value accepted by species-profile JSON. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses a case-insensitive profile value. */
    public static ActivityTime parse(String value, ActivityTime fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
