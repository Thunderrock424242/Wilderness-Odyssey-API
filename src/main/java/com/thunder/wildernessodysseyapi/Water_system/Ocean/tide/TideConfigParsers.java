package com.thunder.wildernessodysseyapi.Water_system.Ocean.tide;

import com.thunder.wildernessodysseyapi.Core.ModConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TideConfigParsers {
    private TideConfigParsers() {
    }

    static Map<String, Double> parseMultiplierMap(List<? extends String> entries) {
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> results = new HashMap<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim();
            String[] parts = trimmed.split("=", 2);
            if (parts.length != 2) {
                ModConstants.LOGGER.warn("Invalid tide multiplier entry '{}'; expected format namespace:id=value", trimmed);
                continue;
            }
            String key = parts[0].trim();
            try {
                double value = Double.parseDouble(parts[1].trim());
                results.put(key, value);
            } catch (NumberFormatException ex) {
                ModConstants.LOGGER.warn("Invalid tide multiplier value '{}' in entry '{}'", parts[1], trimmed);
            }
        }
        return Map.copyOf(results);
    }
}
