package com.thunder.wildernessodysseyapi.WorldGen.debug;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;

@EventBusSubscriber
public abstract class BiomeLoadErrorCatcher extends SimplePreparableReloadListener<Map<ResourceKey<Biome>, Holder<Biome>>> {
    @Override
    protected Map<ResourceKey<Biome>, Holder<Biome>> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return Map.of(); // Not used
    }

    @Override
    protected void apply(Map<ResourceKey<Biome>, Holder<Biome>> data, ResourceManager resourceManager, ProfilerFiller profiler) {
        try {
            Registry<Biome> biomeRegistry = (Registry<Biome>) Registries.BIOME;
            for (ResourceLocation key : biomeRegistry.keySet()) {
                biomeRegistry.getHolder(key).ifPresentOrElse(
                        b -> {}, // OK
                        () -> LOGGER.error("[WorldgenError] Missing biome holder: " + key)
                );
            }
        } catch (Exception e) {
            LOGGER.error("[WorldgenError] Exception during biome registry validation", e);
        }
    }
}