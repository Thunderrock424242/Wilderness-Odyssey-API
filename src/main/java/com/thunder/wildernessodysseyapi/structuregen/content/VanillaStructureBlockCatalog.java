package com.thunder.wildernessodysseyapi.structuregen.content;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.fml.loading.LoadingModList;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Exact vanilla-only catalog used when no full NeoForge registry snapshot has been generated.
 *
 * <p>This safe fallback keeps ordinary Blueprints buildable while treating every modded candidate
 * as unavailable. It must never claim that classpath-visible mod JARs have registered blocks.</p>
 */
public final class VanillaStructureBlockCatalog implements StructureBlockCatalog {

    private final Map<String, String> installedMods;
    private final Map<ResourceLocation, AvailableBlockDescriptor> blocks;

    /** Bootstraps Minecraft's built-in registry and captures only the {@code minecraft} namespace. */
    public VanillaStructureBlockCatalog() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        Map<ResourceLocation, AvailableBlockDescriptor> captured = new TreeMap<>();
        BuiltInRegistries.BLOCK.entrySet().forEach(entry -> {
            ResourceLocation id = entry.getKey().location();
            if ("minecraft".equals(id.getNamespace())) {
                captured.put(id, describe(id, entry.getValue()));
            }
        });
        this.blocks = Collections.unmodifiableMap(new LinkedHashMap<>(captured));
        this.installedMods = Map.of("minecraft", SharedConstants.getCurrentVersion().getName());
    }

    @Override
    public Map<String, String> installedMods() {
        return installedMods;
    }

    @Override
    public Map<ResourceLocation, AvailableBlockDescriptor> blocks() {
        return blocks;
    }

    private AvailableBlockDescriptor describe(ResourceLocation id, Block block) {
        Map<String, List<String>> properties = new TreeMap<>();
        Map<String, String> defaults = new TreeMap<>();
        BlockState defaultState = block.defaultBlockState();
        for (Property<?> property : block.getStateDefinition().getProperties()) {
            properties.put(property.getName(), propertyValues(property));
            defaults.put(property.getName(), propertyValueName(property, defaultState));
        }
        return new AvailableBlockDescriptor(id, properties, defaults);
    }

    private <T extends Comparable<T>> List<String> propertyValues(Property<T> property) {
        return property.getPossibleValues().stream().map(property::getName).sorted().toList();
    }

    private <T extends Comparable<T>> String propertyValueName(Property<T> property, BlockState state) {
        return property.getName(state.getValue(property));
    }
}
