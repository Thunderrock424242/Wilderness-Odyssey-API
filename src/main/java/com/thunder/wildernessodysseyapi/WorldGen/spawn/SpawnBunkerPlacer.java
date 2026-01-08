package com.thunder.wildernessodysseyapi.WorldGen.spawn;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.processor.BunkerPlacementProcessor;
import com.thunder.wildernessodysseyapi.WorldGen.structure.TerrainReplacerEngine;
import com.thunder.wildernessodysseyapi.WorldGen.structure.NBTStructurePlacer;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureSpawnGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.List;

/**
 * Places the starter bunker during world creation using vanilla structure templates rather than WorldEdit
 * or Starter Structure hooks.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class SpawnBunkerPlacer {
    private static final ResourceLocation BUNKER_ID = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "bunker");
    private static final int LAND_SEARCH_RADIUS = 512;
    private static final int LAND_SEARCH_STEP = 16;
    private static final NBTStructurePlacer BUNKER_PLACER = new NBTStructurePlacer(
            BUNKER_ID,
            List.of(new BunkerPlacementProcessor()));

    private SpawnBunkerPlacer() {
    }

    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        BlockPos anchor = resolveAnchor(level);
        NBTStructurePlacer.PlacementResult result = placeBunker(level, anchor);
        if (result == null) {
            ModConstants.LOGGER.warn("Spawn bunker placement failed at {}; falling back to vanilla spawn selection.", anchor);
            return;
        }

        applySpawnData(level, result);
        event.setCanceled(true);
    }

    /**
     * Places the spawn bunker so that its leveling marker (or template origin) is anchored at the supplied position.
     */
    public static NBTStructurePlacer.PlacementResult placeBunker(ServerLevel level, BlockPos anchor) {
        return BUNKER_PLACER.placeAnchored(level, anchor);
    }

    private static BlockPos resolveAnchor(ServerLevel level) {
        BlockPos baseSpawn = level.getSharedSpawnPos();
        return findLandAnchor(level, baseSpawn);
    }

    private static BlockPos findLandAnchor(ServerLevel level, BlockPos baseSpawn) {
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(baseSpawn.getX(), level.getMinBuildHeight(), baseSpawn.getZ()));
        if (isLandBiome(level, surface)) {
            return surface;
        }

        int baseX = baseSpawn.getX();
        int baseZ = baseSpawn.getZ();
        for (int radius = LAND_SEARCH_STEP; radius <= LAND_SEARCH_RADIUS; radius += LAND_SEARCH_STEP) {
            for (int dx = -radius; dx <= radius; dx += LAND_SEARCH_STEP) {
                for (int dz = -radius; dz <= radius; dz += LAND_SEARCH_STEP) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    int x = baseX + dx;
                    int z = baseZ + dz;
                    BlockPos candidate = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            new BlockPos(x, level.getMinBuildHeight(), z));
                    if (isLandBiome(level, candidate)) {
                        return candidate;
                    }
                }
            }
        }

        ModConstants.LOGGER.warn("Unable to locate a land biome within {} blocks of {}; using base spawn.",
                LAND_SEARCH_RADIUS, baseSpawn);
        return surface;
    }

    private static void applySpawnData(ServerLevel level, NBTStructurePlacer.PlacementResult result) {
        List<BlockPos> cryoPositions = result.cryoPositions();
        if (!cryoPositions.isEmpty()) {
            CryoSpawnData data = CryoSpawnData.get(level);
            data.replaceAll(cryoPositions);
            PlayerSpawnHandler.setSpawnBlocks(cryoPositions);
            WorldSpawnHandler.refreshWorldSpawn(level);
        } else {
            PlayerSpawnHandler.setSpawnBlocks(List.of());
        }

        BlockPos spawnTarget = pickSpawnTarget(result);
        level.setDefaultSpawnPos(spawnTarget, 0.0F);

        StarterStructureSpawnGuard.registerSpawnDenyZone(level, result.bounds());
        sealRoofAndDrainWater(level, result.bounds());

        ModConstants.LOGGER.info("Placed spawn bunker {} at {} with {} cryo tubes.", BUNKER_ID, result.origin(), cryoPositions.size());
    }

    private static BlockPos pickSpawnTarget(NBTStructurePlacer.PlacementResult result) {
        if (!result.cryoPositions().isEmpty()) {
            return result.cryoPositions().get(0);
        }
        return BlockPos.containing(result.bounds().getCenter());
    }

    private static void sealRoofAndDrainWater(ServerLevel level, AABB bounds) {
        int minX = Mth.floor(bounds.minX);
        int minY = Mth.floor(bounds.minY);
        int minZ = Mth.floor(bounds.minZ);
        int maxX = Mth.ceil(bounds.maxX) - 1;
        int maxY = Mth.ceil(bounds.maxY) - 1;
        int maxZ = Mth.ceil(bounds.maxZ) - 1;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (surfaceY <= maxY) {
                    continue;
                }
                BlockState capState = TerrainReplacerEngine.sampleSurfaceBlock(level, new BlockPos(x, surfaceY, z));
                for (int y = maxY; y <= surfaceY; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || state.is(Blocks.DIRT)) {
                        level.setBlock(cursor, capState, 2);
                    }
                }
            }
        }
    }

    private static boolean isLandBiome(ServerLevel level, BlockPos pos) {
        return !level.getBiome(pos).is(BiomeTags.IS_OCEAN);
    }
}
