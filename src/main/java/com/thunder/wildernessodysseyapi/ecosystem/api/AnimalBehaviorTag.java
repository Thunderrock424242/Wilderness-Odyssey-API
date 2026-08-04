package com.thunder.wildernessodysseyapi.ecosystem.api;

import java.util.Locale;
import java.util.Optional;

/**
 * High-level behavior label that can be assigned to an animal in server config.
 *
 * <p>Archetypes such as {@link #HERBIVORE}, {@link #BIRD}, and {@link #WOLF}
 * provide complete useful defaults. Modifier tags allow pack authors to adjust
 * the generated behavior without writing a JSON species profile.</p>
 */
public enum AnimalBehaviorTag {
    ANIMAL,
    HERBIVORE,
    OMNIVORE,
    BIRD,
    WOLF,
    AQUATIC,
    HERD,
    FLOCK,
    PACK,
    PREY,
    PREDATOR,
    SWIMMER,
    NOCTURNAL,
    SHELTER,
    SOLITARY,
    DISABLED;

    /** Returns the lowercase name accepted by the server config. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a config label, including friendly singular/plural aliases.
     *
     * @param value raw config label
     * @return recognized behavior tag, or empty for an unknown label
     */
    public static Optional<AnimalBehaviorTag> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        normalized = switch (normalized) {
            case "herbivor", "herbivors", "herbivores" -> "herbivore";
            case "animals" -> "animal";
            case "omnivores" -> "omnivore";
            case "birds" -> "bird";
            case "wolves" -> "wolf";
            case "aquatics", "fish", "fishes" -> "aquatic";
            case "herds" -> "herd";
            case "flocks" -> "flock";
            case "packs" -> "pack";
            case "predators" -> "predator";
            case "swimmers" -> "swimmer";
            case "disable", "excluded", "ignore", "ignored", "off" -> "disabled";
            default -> normalized;
        };
        try {
            return Optional.of(valueOf(normalized.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
