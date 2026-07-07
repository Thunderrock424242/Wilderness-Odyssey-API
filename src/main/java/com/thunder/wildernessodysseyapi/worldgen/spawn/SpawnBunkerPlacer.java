package com.thunder.wildernessodysseyapi.worldgen.spawn;

import com.mojang.datafixers.util.Pair;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.worldgen.config.StructureConfig;
import com.thunder.wildernessodysseyapi.worldgen.processor.BunkerPlacementProcessor;
import com.thunder.wildernessodysseyapi.worldgen.structure.NBTStructurePlacer;
import com.thunder.wildernessodysseyapi.worldgen.structure.StarterStructureSpawnGuard;
import com.thunder.wildernessodysseyapi.worldgen.structure.TerrainReplacerEngine;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWaterMigrationQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.List;
import java.util.function.Predicate;

/**
 * Selects an ocean world spawn and places the starter bunker during initial world creation.
 *
 * <p>The creation event runs before Minecraft prepares the permanent spawn chunks. Ocean selection therefore
 * uses the generator's biome-noise source without generating every candidate chunk, places the starter island
 * only at the selected location, and then makes that location the world's real spawn-chunk region.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class SpawnBunkerPlacer {
    private static final ResourceLocation BUNKER_ID = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "bunker");
    private static final int DEEP_OCEAN_SEARCH_RADIUS = 6400;
    private static final int OCEAN_SEARCH_RADIUS = 25000;
    private static final int BIOME_SEARCH_COARSE_STEP = 256;
    private static final int BIOME_SEARCH_FINE_STEP = 64;
    private static final int ISLAND_PLATFORM_PADDING = 30;
    private static final int ISLAND_SHORE_RADIUS_PADDING = 48;
    private static final int ISLAND_SLOPE_DEPTH = 4;
    private static final NBTStructurePlacer BUNKER_PLACER = new NBTStructurePlacer(
            BUNKER_ID,
            List.of(new BunkerPlacementProcessor()));

    private SpawnBunkerPlacer() {
    }

    /**
     * Chooses and prepares the initial overworld spawn before Minecraft generates its spawn-chunk region.
     *
     * <p>Canceling this event prevents vanilla from replacing the bunker spawn after placement. Other dimensions
     * and the debug-disabled path are intentionally left to vanilla.</p>
     *
     * @param event the NeoForge initial spawn-position event
     */
    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        if (StructureConfig.DEBUG_DISABLE_STARTER_BUNKER.get()) return;

        CryoSpawnData data = CryoSpawnData.get(level);
        if (data.hasStarterBunkerPlaced()) {
            WorldSpawnHandler.refreshWorldSpawn(level);
            event.setCanceled(true);
            return;
        }

        BlockPos anchor = resolveAnchor(level);
        NBTStructurePlacer.PlacementResult result = placeBunker(level, anchor);
        if (result == null) {
            ModConstants.LOGGER.warn("Spawn bunker placement failed at {}; falling back.", anchor);
            return;
        }
        applySpawnData(level, result);
        preFinalizeSpawnWater(level, result);
        event.setCanceled(true);
    }

    /**
     * Places the spawn bunker so that its leveling marker (or template origin) is anchored at the supplied position.
     */
    public static NBTStructurePlacer.PlacementResult placeBunker(ServerLevel level, BlockPos anchor) {
        Vec3i bunkerSize = BUNKER_PLACER.peekSize(level);
        prepareStarterIsland(level, anchor, bunkerSize);
        return BUNKER_PLACER.placeAnchored(level, anchor);
    }

    static BlockPos resolveAnchor(ServerLevel level) {
        BlockPos baseSpawn = level.getSharedSpawnPos();
        BlockPos searchOrigin = new BlockPos(baseSpawn.getX(), level.getSeaLevel(), baseSpawn.getZ());

        // Deep ocean is preferred because its biome center is normally well clear of coastlines. This query
        // samples climate noise only; unlike heightmaps and block lookups, it does not generate candidate chunks.
        Pair<BlockPos, Holder<Biome>> result = findClosestBiome(
                level,
                searchOrigin,
                DEEP_OCEAN_SEARCH_RADIUS,
                biome -> biome.is(BiomeTags.IS_DEEP_OCEAN));
        String oceanType = "deep ocean";

        if (result == null) {
            result = findClosestBiome(
                    level,
                    searchOrigin,
                    OCEAN_SEARCH_RADIUS,
                    biome -> biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN));
            oceanType = "ocean";
        }

        if (result == null) {
            ModConstants.LOGGER.warn(
                    "The active overworld generator exposed no ocean biome within {} blocks of {}; using the original spawn anchor.",
                    OCEAN_SEARCH_RADIUS,
                    baseSpawn);
            return toOceanAnchor(level, baseSpawn);
        }

        BlockPos anchor = toOceanAnchor(level, result.getFirst());
        ModConstants.LOGGER.info(
                "Selected {} starter spawn at {} using biome-noise lookup; only the final island chunks will be generated.",
                oceanType,
                anchor);
        return anchor;
    }

    private static Pair<BlockPos, Holder<Biome>> findClosestBiome(ServerLevel level,
                                                                  BlockPos origin,
                                                                  int radius,
                                                                  Predicate<Holder<Biome>> predicate) {
        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
        if (biomeSource.possibleBiomes().stream().noneMatch(predicate)) {
            return null;
        }

        // Ocean biomes are surface targets, so a horizontal climate-noise query avoids sampling the full
        // build height. Broad vanilla oceans are found by the coarse pass; the fine pass preserves support
        // for narrow ocean biomes supplied by custom generators.
        Pair<BlockPos, Holder<Biome>> result = findClosestSurfaceBiome(
                level,
                biomeSource,
                predicate,
                origin,
                radius,
                BIOME_SEARCH_COARSE_STEP);
        if (result != null) {
            return result;
        }
        return findClosestSurfaceBiome(
                level,
                biomeSource,
                predicate,
                origin,
                radius,
                BIOME_SEARCH_FINE_STEP);
    }

    private static Pair<BlockPos, Holder<Biome>> findClosestSurfaceBiome(ServerLevel level,
                                                                         BiomeSource biomeSource,
                                                                         Predicate<Holder<Biome>> predicate,
                                                                         BlockPos origin,
                                                                         int radius,
                                                                         int blockStep) {
        return biomeSource.findBiomeHorizontal(
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                radius,
                QuartPos.fromBlock(blockStep),
                predicate,
                level.getRandom(),
                true,
                level.getChunkSource().randomState().sampler());
    }

    private static BlockPos toOceanAnchor(ServerLevel level, BlockPos pos) {
        int anchorY = Math.min(level.getMaxBuildHeight() - 1, level.getSeaLevel() + 2);
        return new BlockPos(pos.getX(), anchorY, pos.getZ());
    }

    static void applySpawnData(ServerLevel level, NBTStructurePlacer.PlacementResult result) {
        List<BlockPos> cryoPositions = result.cryoPositions();
        CryoSpawnData data = CryoSpawnData.get(level);
        data.markStarterBunkerPlaced();
        data.replaceAll(cryoPositions);

        if (!cryoPositions.isEmpty()) {
            PlayerSpawnHandler.setSpawnBlocks(cryoPositions);
            WorldSpawnHandler.refreshWorldSpawn(level);
        } else {
            PlayerSpawnHandler.setSpawnBlocks(List.of());
        }

        BlockPos spawnTarget = pickSpawnTarget(result);
        level.setDefaultSpawnPos(spawnTarget, 0.0F);

        StarterStructureSpawnGuard.registerSpawnDenyZone(level, result.bounds());

        ModConstants.LOGGER.info("Placed spawn bunker {} at {} with {} cryo tubes.", BUNKER_ID, result.origin(), cryoPositions.size());
    }

    private static void preFinalizeSpawnWater(ServerLevel level, NBTStructurePlacer.PlacementResult result) {
        // New-world spawn preparation is the only blocking water takeover path; it trades a bounded
        // loading-screen cost for avoiding visible live migration around the starter bunker.
        CanonicalWaterMigrationQueue.SpawnPreFinalizationResult waterResult =
                CanonicalWaterMigrationQueue.preFinalizeSpawnArea(
                        level,
                        BlockPos.containing(result.bounds().getCenter()));
        if (!waterResult.enabled()) {
            return;
        }

        ModConstants.LOGGER.info(
                "Pre-finalized Wilderness water around spawn bunker: {}/{} chunks complete, {} touched, {} skipped, {} queued, {} columns, {} imported cells, {} hosted cells, {} converted blocks in {} ms{}.",
                waterResult.completedChunks(),
                waterResult.candidateChunks(),
                waterResult.touchedChunks(),
                waterResult.skippedFinalizedChunks(),
                waterResult.queuedUnfinishedChunks(),
                waterResult.scannedColumns(),
                waterResult.importedCells(),
                waterResult.hostedWaterCells(),
                waterResult.convertedBlocks(),
                waterResult.elapsedMs(),
                waterResult.timedOut() ? " (timed out; remaining chunks queued)" : "");
    }

    private static BlockPos pickSpawnTarget(NBTStructurePlacer.PlacementResult result) {
        if (!result.cryoPositions().isEmpty()) {
            return result.cryoPositions().get(0);
        }
        return BlockPos.containing(result.bounds().getCenter());
    }

    private static void prepareStarterIsland(ServerLevel level, BlockPos anchor, Vec3i bunkerSize) {
        BlockPos origin = getPlacementOrigin(level, anchor);
        int sizeX = Math.max(1, bunkerSize.getX());
        int sizeZ = Math.max(1, bunkerSize.getZ());
        int centerX = origin.getX() + (sizeX / 2);
        int centerZ = origin.getZ() + (sizeZ / 2);
        int flatRadius = Math.max(sizeX, sizeZ) / 2 + ISLAND_PLATFORM_PADDING;
        int shoreRadius = flatRadius + ISLAND_SHORE_RADIUS_PADDING;
        int islandTopY = anchor.getY() - 1;
        int seaLevel = level.getSeaLevel();
        double flatRadiusSquared = (double) flatRadius * flatRadius;
        double shoreRadiusSquared = (double) shoreRadius * shoreRadius;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = centerX - shoreRadius; x <= centerX + shoreRadius; x++) {
            for (int z = centerZ - shoreRadius; z <= centerZ + shoreRadius; z++) {
                double dx = x - centerX;
                double dz = z - centerZ;
                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > shoreRadiusSquared) {
                    continue;
                }

                // Reuse one mutable position per island column; this loop covers tens of thousands of blocks.
                cursor.set(x, anchor.getY(), z);
                int seafloorY = TerrainReplacerEngine.sampleSurface(level, cursor).y();
                double distance = distanceSquared <= flatRadiusSquared ? 0.0D : Math.sqrt(distanceSquared);
                int targetTopY = resolveIslandTopY(distance, flatRadius, shoreRadius, islandTopY, seaLevel);
                if (targetTopY <= seafloorY) {
                    targetTopY = Math.min(islandTopY, seafloorY + 1);
                }
                if (targetTopY <= seafloorY) {
                    continue;
                }

                for (int y = seafloorY; y <= targetTopY; y++) {
                    cursor.set(x, y, z);
                    level.setBlock(cursor, selectIslandBlock(
                            distance,
                            flatRadius,
                            shoreRadius,
                            targetTopY,
                            y,
                            seaLevel), 2);
                }
            }
        }
    }

    private static int resolveIslandTopY(double distance,
                                         int flatRadius,
                                         int shoreRadius,
                                         int islandTopY,
                                         int seaLevel) {
        if (distance <= flatRadius) {
            return islandTopY;
        }

        double slopeProgress = (distance - flatRadius) / Math.max(1.0D, shoreRadius - flatRadius);
        int drop = (int) Math.round(slopeProgress * ISLAND_SLOPE_DEPTH);
        return Math.max(seaLevel - 2, islandTopY - drop);
    }

    private static BlockState selectIslandBlock(double distance,
                                                int flatRadius,
                                                int shoreRadius,
                                                int targetTopY,
                                                int y,
                                                int seaLevel) {
        if (y == targetTopY) {
            // The block below was written by this same column pass, so select the equivalent top material
            // directly instead of allocating a position and reading the block back from the level.
            if (distance >= flatRadius || targetTopY <= seaLevel) {
                return Blocks.SANDSTONE.defaultBlockState();
            }
            if (targetTopY > seaLevel && distance < shoreRadius - 4) {
                return Blocks.DIRT.defaultBlockState();
            }
            return (targetTopY <= seaLevel || distance >= shoreRadius - 4)
                    ? Blocks.SAND.defaultBlockState()
                    : Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (y >= targetTopY - 3) {
            return (distance >= flatRadius || targetTopY <= seaLevel)
                    ? Blocks.SANDSTONE.defaultBlockState()
                    : Blocks.DIRT.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    private static BlockPos getPlacementOrigin(ServerLevel level, BlockPos surface) {
        BlockPos levelingOffset = BUNKER_PLACER.peekLevelingOffset(level);
        return levelingOffset == null ? surface : surface.subtract(levelingOffset);
    }

}
