package com.thunder.wildernessodysseyapi.structuregen.content;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable description of one block that StructureGen may safely reference.
 *
 * <p>The descriptor records the complete property domain and the registered
 * default state. It deliberately carries no live {@code Block} instance, so a
 * catalog snapshot can be consumed by the ordinary offline StructureGen
 * process without constructing mods or starting Minecraft gameplay.</p>
 */
public record AvailableBlockDescriptor(
        ResourceLocation id,
        Map<String, List<String>> properties,
        Map<String, String> defaultProperties
) {

    /** Creates a defensively copied descriptor with deterministic map and value ordering. */
    public AvailableBlockDescriptor {
        Objects.requireNonNull(id, "id");
        Map<String, List<String>> normalizedProperties = immutablePropertyDomains(properties);
        Map<String, String> normalizedDefaults = immutableDefaults(defaultProperties);

        if (!normalizedDefaults.keySet().equals(normalizedProperties.keySet())) {
            throw new IllegalArgumentException("Default-state properties for " + id
                    + " must exactly match its property definitions.");
        }
        normalizedDefaults.forEach((property, value) -> {
            if (!normalizedProperties.get(property).contains(value)) {
                throw new IllegalArgumentException("Default value '" + value + "' is not valid for property '"
                        + property + "' on " + id + ".");
            }
        });
        properties = normalizedProperties;
        defaultProperties = normalizedDefaults;
    }

    /**
     * Validates a partial structure-template state against this block's exact property domains.
     *
     * <p>Structure palettes may omit properties to request the registered default state, so this
     * method validates supplied entries without requiring every property to be present.</p>
     */
    public List<String> validateProperties(Map<String, String> selectedProperties) {
        Objects.requireNonNull(selectedProperties, "selectedProperties");
        List<String> errors = new ArrayList<>();
        new TreeMap<>(selectedProperties).forEach((property, value) -> {
            List<String> allowedValues = properties.get(property);
            if (allowedValues == null) {
                errors.add(id + " has no property named '" + property + "'.");
            } else if (!allowedValues.contains(value)) {
                errors.add("Invalid value '" + value + "' for property '" + property + "' on " + id
                        + ". Allowed values: " + allowedValues + ".");
            }
        });
        return List.copyOf(errors);
    }

    private static Map<String, List<String>> immutablePropertyDomains(Map<String, List<String>> source) {
        Objects.requireNonNull(source, "properties");
        Map<String, List<String>> ordered = new LinkedHashMap<>();
        new TreeMap<>(source).forEach((property, values) -> {
            if (property == null || property.isBlank()) {
                throw new IllegalArgumentException("Block property names must not be blank.");
            }
            Objects.requireNonNull(values, "values for property " + property);
            List<String> sortedValues = values.stream()
                    .map(value -> Objects.requireNonNull(value, "value for property " + property))
                    .distinct()
                    .sorted()
                    .toList();
            if (sortedValues.isEmpty()) {
                throw new IllegalArgumentException("Block property '" + property + "' has no allowed values.");
            }
            ordered.put(property, sortedValues);
        });
        return Collections.unmodifiableMap(ordered);
    }

    private static Map<String, String> immutableDefaults(Map<String, String> source) {
        Objects.requireNonNull(source, "defaultProperties");
        Map<String, String> ordered = new LinkedHashMap<>();
        new TreeMap<>(source).forEach((property, value) -> {
            if (property == null || property.isBlank()) {
                throw new IllegalArgumentException("Default-state property names must not be blank.");
            }
            ordered.put(property, Objects.requireNonNull(value, "default value for property " + property));
        });
        return Collections.unmodifiableMap(ordered);
    }
}
