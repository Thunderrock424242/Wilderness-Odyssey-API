package com.thunder.wildernessodysseyapi.meteor.api;

import java.util.Locale;

/** Identifies which authoritative path created a persistent meteor site. */
public enum MeteorSiteSource {
    NATURAL,
    COMMAND,
    RIFTFALL,
    WORLDGEN,
    UNKNOWN;

    /** Returns the stable lowercase value stored in world data. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses older or future data with a safe unknown fallback. */
    public static MeteorSiteSource fromSerializedName(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
