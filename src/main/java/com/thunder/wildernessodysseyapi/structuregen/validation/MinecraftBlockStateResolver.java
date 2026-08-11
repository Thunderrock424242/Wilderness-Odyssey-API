package com.thunder.wildernessodysseyapi.structuregen.validation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.fml.loading.LoadingModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates vanilla block IDs and state properties against the bootstrapped 1.21.1 registry.
 *
 * <p>A standalone Gradle task does not run the Wilderness Odyssey mod-loading lifecycle. Unknown
 * namespaced IDs therefore fail closed; mod-aware authoring must use a verified registry snapshot
 * through {@code StructureBlockCatalog}.</p>
 */
public final class MinecraftBlockStateResolver implements BlockStateResolver {

    private final boolean registryAvailable;
    private final String registryUnavailableReason;

    public MinecraftBlockStateResolver() {
        boolean available = false;
        String unavailableReason = null;
        try {
            // NeoForge patches feature-flag discovery through LoadingModList. A
            // plain JavaExec has no mod-loading phase, so supply an explicit empty
            // context before bootstrapping the vanilla built-in registries.
            if (LoadingModList.get() == null) {
                LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
            }
            Bootstrap.bootStrap();
            available = true;
        } catch (RuntimeException | LinkageError failure) {
            // Registry absence must be visible, but it must not weaken the
            // parser, bounds, duplicate, SNBT, or output-safety checks.
            unavailableReason = failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
        }
        registryAvailable = available;
        registryUnavailableReason = unavailableReason;
    }

    @Override
    public Resolution validate(String blockId, Map<String, String> properties) {
        if (!registryAvailable) {
            ResourceLocation id = ResourceLocation.tryParse(blockId);
            String message = "The offline Minecraft registry is unavailable (" + registryUnavailableReason
                    + "); block existence and state properties for '" + blockId + "' were not verified.";
            return new Resolution(List.of(message), List.of());
        }
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) {
            return new Resolution(List.of("Invalid Minecraft resource location '" + blockId + "'."), List.of());
        }
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            return new Resolution(
                    List.of("Block '" + blockId + "' is not available in the offline built-in registry. "
                            + "Generate and use a verified StructureGen content catalog for modded authoring."),
                    List.of()
            );
        }

        Block block = BuiltInRegistries.BLOCK.get(id);
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                errors.add(blockId + " has no property named '" + entry.getKey() + "'.");
                continue;
            }
            if (property.getValue(entry.getValue()).isEmpty()) {
                errors.add("Invalid value '" + entry.getValue() + "' for property '" + entry.getKey()
                        + "' on " + blockId + ". Allowed values: " + allowedValues(property) + ".");
            }
        }
        return new Resolution(errors, List.of());
    }

    private String allowedValues(Property<?> property) {
        return property.getPossibleValues().stream()
                .map(value -> propertyValueName(property, value))
                .sorted()
                .toList()
                .toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String propertyValueName(Property property, Comparable value) {
        return property.getName(value);
    }
}
