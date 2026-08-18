package com.thunder.wildernessodysseyapi.ecosystem.memory;

import java.util.Locale;

/**
 * Identifies the latest activity type contributing to one environmental-memory cell.
 *
 * <p>The enum is intentionally broader than the first event hooks. Machines,
 * vehicles, weapons, and other future simulations can publish through the same
 * memory API without adding another world-state manager.</p>
 */
public enum DisturbanceSource {
    PLAYER_MOVEMENT,
    PLAYER_ACTIVITY,
    COMBAT,
    EXPLOSION,
    FIRE,
    LOUD_EVENT,
    MACHINE,
    VEHICLE,
    OTHER;

    /** Returns the stable lowercase name written to world data and diagnostics. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses saved or command-facing names while safely handling future values. */
    public static DisturbanceSource fromSerializedName(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return OTHER;
        }
    }
}
