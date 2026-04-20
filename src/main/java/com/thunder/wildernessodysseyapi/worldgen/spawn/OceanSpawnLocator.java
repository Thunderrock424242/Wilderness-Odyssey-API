package com.thunder.wildernessodysseyapi.worldgen.spawn;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.worldgen.configurable.StructureConfig;
import com.thunder.wildernessodysseyapi.worldgen.structure.StarterStructureSpawnGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * OceanSpawnLocator
 *
 * On first world load, uses Minecraft's built-in biome locator
 * (the same logic as /locate biome) to find the nearest ocean
 * and move the world spawn there before SpawnBunkerPlacer runs.
 *
 * Runs on LevelEvent.Load so biomes are fully queryable.
 * Skips if the bunker has already been placed (not a fresh world).
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class OceanSpawnLocator {

    private OceanSpawnLocator() {}

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        if (StructureConfig.DEBUG_DISABLE_STARTER_BUNKER.get()) return;

        // Skip if bunker already placed — this is not a fresh world
        if (StarterStructureSpawnGuard.hasPlacedBunker(level)) return;

        locateAndSetOceanSpawn(level);
    }

    private static void locateAndSetOceanSpawn(ServerLevel level) {
        BlockPos current = level.getSharedSpawnPos();

        // Build a HolderSet of ocean biomes using the tag
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);

        HolderSet<Biome> oceanBiomes = biomeRegistry.getTag(BiomeTags.IS_OCEAN)
                .map(tag -> (HolderSet<Biome>) tag)
                .orElse(null);

        if (oceanBiomes == null || oceanBiomes.size() == 0) {
            ModConstants.LOGGER.warn("[OceanSpawnLocator] Could not resolve ocean biome tag — spawn not moved.");
            return;
        }

        // findClosestBiome3D is the same method /locate biome uses internally
        var found = level.findClosestBiome3d(
                holder -> holder.is(BiomeTags.IS_OCEAN) || holder.is(BiomeTags.IS_DEEP_OCEAN),
                current,
                6400,  // search radius in blocks
                32,    // horizontal resolution (every 32 blocks)
                64     // vertical resolution
        );

        if (found != null) {
            BlockPos oceanPos = found.getFirst();
            level.setDefaultSpawnPos(oceanPos, 0f);
            ModConstants.LOGGER.info("[OceanSpawnLocator] Moved spawn to ocean at {} (was {}).", oceanPos, current);
        } else {
            ModConstants.LOGGER.warn("[OceanSpawnLocator] No ocean found within 6400 blocks of {} — bunker will search normally.", current);
        }
    }
}