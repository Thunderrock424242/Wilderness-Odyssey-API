package com.thunder.wildernessodysseyapi.worldgen.spawn;

import com.mojang.datafixers.util.Pair;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.worldgen.configurable.StructureConfig;
import com.thunder.wildernessodysseyapi.worldgen.structure.NBTStructurePlacer;
import com.thunder.wildernessodysseyapi.worldgen.structure.StarterStructureSpawnGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class OceanSpawnLocator {

    private static boolean hasRun = false;

    private OceanSpawnLocator() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        if (StructureConfig.DEBUG_DISABLE_STARTER_BUNKER.get()) return;
        if (StarterStructureSpawnGuard.hasPlacedBunker(level)) return;
        if (hasRun) return;
        hasRun = true;

        BlockPos current = level.getSharedSpawnPos();

        Pair<BlockPos, Holder<Biome>> result = level.findClosestBiome3d(
                biome -> biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN),
                current,
                6400,
                32,
                64
        );

        if (result != null) {
            BlockPos oceanPos = result.getFirst();
            level.setDefaultSpawnPos(oceanPos, 0f);
            ModConstants.LOGGER.info("[OceanSpawnLocator] Moved spawn to ocean at {}.", oceanPos);

            BlockPos anchor = SpawnBunkerPlacer.resolveAnchor(level);
            NBTStructurePlacer.PlacementResult placement = SpawnBunkerPlacer.placeBunker(level, anchor);
            if (placement != null) {
                SpawnBunkerPlacer.applySpawnData(level, placement);
            }
        } else {
            ModConstants.LOGGER.warn("[OceanSpawnLocator] No ocean found — bunker will use fallback search.");
        }
    }
}