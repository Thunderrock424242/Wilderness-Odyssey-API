package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.weather.config.WeatherOwnershipMode;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Pure ownership arbitration for Wilderness, vanilla, and external weather mods.
 *
 * <p>Only one weather authority should simulate and render a dimension. AUTO
 * yields whenever a configured external owner is installed or has claimed the
 * public integration boundary.</p>
 */
public final class WeatherOwnershipPolicy {

    private WeatherOwnershipPolicy() {
    }

    /** Resolves ownership without touching NeoForge runtime state. */
    public static Decision resolve(
            WeatherOwnershipMode mode,
            Set<String> installedModIds,
            Set<String> configuredExternalIds,
            Set<String> claimedExternalIds
    ) {
        WeatherOwnershipMode selected = Objects.requireNonNullElse(mode, WeatherOwnershipMode.AUTO);
        if (selected == WeatherOwnershipMode.WILDERNESS) {
            return Decision.WILDERNESS;
        }
        if (selected == WeatherOwnershipMode.EXTERNAL) {
            return new Decision(false, "configured external ownership");
        }

        Set<String> installed = normalize(installedModIds);
        Set<String> configured = normalize(configuredExternalIds);
        Set<String> claimed = normalize(claimedExternalIds);
        for (String modId : configured) {
            if (installed.contains(modId) || claimed.contains(modId)) {
                return new Decision(false, modId);
            }
        }
        if (!claimed.isEmpty()) {
            return new Decision(false, claimed.stream().sorted().findFirst().orElse("external"));
        }
        return Decision.WILDERNESS;
    }

    private static Set<String> normalize(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        java.util.HashSet<String> normalized = new java.util.HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(normalized);
    }

    /** Immutable result exposed to diagnostics and tests. */
    public record Decision(boolean wildernessOwnsWeather, String owner) {
        public static final Decision WILDERNESS = new Decision(true, "wildernessodysseyapi");

        public Decision {
            owner = owner == null || owner.isBlank() ? "external" : owner;
        }
    }
}
