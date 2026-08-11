package com.thunder.wildernessodysseyapi.structuregen.content;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.internal.CommonModLoader;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Captures StructureGen content from a fully registered NeoForge environment.
 *
 * <p>This implementation must be created after NeoForge has constructed mods
 * and finished every {@code RegisterEvent}. The lifecycle guard prevents a
 * plain Java process from mistaking the vanilla-only bootstrap registry for a
 * complete modpack registry.</p>
 */
public final class RegistryBackedStructureBlockCatalog implements StructureBlockCatalog {

    private final Map<String, String> installedMods;
    private final Map<ResourceLocation, AvailableBlockDescriptor> blocks;

    private RegistryBackedStructureBlockCatalog(
            Map<String, String> installedMods,
            Map<ResourceLocation, AvailableBlockDescriptor> blocks
    ) {
        this.installedMods = immutableOrderedMap(installedMods);
        this.blocks = immutableOrderedMap(blocks);
    }

    /**
     * Captures the fully loaded mod list and current built-in block registry.
     *
     * @throws IllegalStateException when called before NeoForge finishes registry events
     */
    public static RegistryBackedStructureBlockCatalog captureLoadedRegistry() {
        if (!CommonModLoader.areRegistriesLoaded()) {
            throw new IllegalStateException("The NeoForge block registry is not fully loaded; "
                    + "capture the StructureGen catalog from the datagen lifecycle.");
        }
        ModList modList = ModList.get();
        if (modList == null) {
            throw new IllegalStateException("The NeoForge mod list is unavailable.");
        }

        Map<String, String> mods = new TreeMap<>();
        modList.getMods().forEach(mod -> mods.put(mod.getModId(), mod.getVersion().toString()));

        Map<ResourceLocation, AvailableBlockDescriptor> availableBlocks = new TreeMap<>();
        BuiltInRegistries.BLOCK.entrySet().forEach(entry -> {
            ResourceLocation id = entry.getKey().location();
            availableBlocks.put(id, describe(id, entry.getValue()));
        });
        return new RegistryBackedStructureBlockCatalog(mods, availableBlocks);
    }

    @Override
    public Map<String, String> installedMods() {
        return installedMods;
    }

    @Override
    public Map<ResourceLocation, AvailableBlockDescriptor> blocks() {
        return blocks;
    }

    private static AvailableBlockDescriptor describe(ResourceLocation id, Block block) {
        Map<String, List<String>> properties = new TreeMap<>();
        Map<String, String> defaults = new TreeMap<>();
        BlockState defaultState = block.defaultBlockState();

        // Minecraft's StateDefinition is the authoritative source for both
        // third-party property names and every serialized property value.
        for (Property<?> property : block.getStateDefinition().getProperties()) {
            properties.put(property.getName(), propertyValues(property));
            defaults.put(property.getName(), propertyValueName(property, defaultState));
        }
        return new AvailableBlockDescriptor(id, properties, defaults);
    }

    private static <T extends Comparable<T>> List<String> propertyValues(Property<T> property) {
        return property.getPossibleValues().stream()
                .map(property::getName)
                .sorted()
                .toList();
    }

    private static <T extends Comparable<T>> String propertyValueName(Property<T> property, BlockState state) {
        return property.getName(state.getValue(property));
    }

    private static <K, V> Map<K, V> immutableOrderedMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
