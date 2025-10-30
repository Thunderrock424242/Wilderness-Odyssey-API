package com.thunder.wildernessodysseyapi.WorldGen.worldgen.structures;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.BunkerProtectionHandler;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.StructureSpawnTracker;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock.CryoSpawnData;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock.PlayerSpawnHandler;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock.WorldSpawnHandler;
import com.thunder.wildernessodysseyapi.WorldGen.structure.NBTStructurePlacer;
import com.thunder.wildernessodysseyapi.WorldGen.util.DeferredTaskScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Handles initial meteor impact site and bunker placement when a world is created.
 */
public class MeteorStructureSpawner {
    private static final TagKey<Biome> IS_PLAINS_TAG =
            TagKey.create(Registries.BIOME, ResourceLocation.parse("minecraft:is_plains"));
    private static final int IMPACT_SITE_COUNT = 3;
    private static final int MIN_CHUNK_SEPARATION = 32;
    private static final int MIN_BLOCK_SEPARATION = MIN_CHUNK_SEPARATION * 16;
    private static final int POSITION_ATTEMPTS = 64;
    private static final int MAX_DEFERRED_ATTEMPTS = 20 * 60 * 5; // five minutes worth of retries

    private static final NBTStructurePlacer METEOR_SITE_PLACER =
            new NBTStructurePlacer(ModConstants.MOD_ID, "impact_zone");
    private static final NBTStructurePlacer BUNKER_PLACER =
            new NBTStructurePlacer(ModConstants.MOD_ID, "bunker");

    private static boolean placed = false;
    private static final Set<Long> forcedChunks = new HashSet<>();
    private static final Map<ResourceKey<Level>, Integer> retryAttempts = new HashMap<>();
    private static final Set<ResourceKey<Level>> initialPlacementScheduled = new HashSet<>();
    private static final int INITIAL_PLACEMENT_DELAY_TICKS = 20 * 5; // 5 seconds

    public static void tryPlace(ServerLevel level) {
        tryPlace(level, 0);
    }

    public static void resetState() {
        placed = false;
        forcedChunks.clear();
        retryAttempts.clear();
        DeferredTaskScheduler.clear();
        initialPlacementScheduled.clear();
    }

    public static void scheduleInitialPlacement(ServerLevel level) {
        scheduleInitialPlacement(level, INITIAL_PLACEMENT_DELAY_TICKS);
    }

    public static void scheduleInitialPlacement(ServerLevel level, int delayTicks) {
        if (placed) {
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        if (!initialPlacementScheduled.add(dimension)) {
            return;
        }

        if (shouldSkipForExistingWorld(level)) {
            placed = true;
            return;
        }

        enqueueTask(level, () -> tryPlace(level), Math.max(1, delayTicks));
    }

    private static void tryPlace(ServerLevel level, int attempt) {
        if (placed) {
            retryAttempts.remove(level.dimension());
            return;
        }

        MeteorImpactData impactData = MeteorImpactData.get(level);
        if (impactData.getBunkerPos() != null) {
            markPlacementComplete(level);
            return;
        }

        long seed = level.getSeed() ^ (31L * attempt + 0x9E3779B97F4A7C15L);
        RandomSource random = RandomSource.create(seed);
        BlockPos spawn = level.getSharedSpawnPos();

        List<BlockPos> storedSites = new ArrayList<>(impactData.getImpactPositions());
        int originalCount = storedSites.size();

        for (int i = storedSites.size(); i < IMPACT_SITE_COUNT; i++) {
            BlockPos origin = findImpactOrigin(level, random, spawn, storedSites);
            BlockPos impactPos = placeMeteorSite(level, origin);
            if (impactPos == null) {
                scheduleRetry(level, attempt + 1);
                return;
            }
            storedSites.add(impactPos);
        }

        if (storedSites.size() != originalCount) {
            impactData.setImpactPositions(storedSites);
        }

        if (impactData.getBunkerPos() == null && !storedSites.isEmpty()) {
            BlockPos bunkerAnchor = storedSites.get(random.nextInt(storedSites.size()));
            PlacementState bunkerState = placeBunker(level, bunkerAnchor, impactData);
            if (bunkerState == PlacementState.SUCCESS) {
                markPlacementComplete(level);
                return;
            }
        }

        if (impactData.getBunkerPos() != null) {
            markPlacementComplete(level);
        } else {
            scheduleRetry(level, attempt + 1);
        }
    }

    private static void markPlacementComplete(ServerLevel level) {
        placed = true;
        retryAttempts.remove(level.dimension());
    }

    private static void scheduleRetry(ServerLevel level, int nextAttempt) {
        if (placed || nextAttempt > MAX_DEFERRED_ATTEMPTS) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        int current = retryAttempts.getOrDefault(dimension, 0);
        if (current >= nextAttempt) {
            return;
        }
        retryAttempts.put(dimension, nextAttempt);
        enqueueTask(level, () -> tryPlace(level, nextAttempt));
    }

    private static BlockPos placeMeteorSite(ServerLevel level, BlockPos origin) {
        ChunkPos impactChunk = new ChunkPos(origin);
        forceChunk(level, impactChunk);
        try {
            NBTStructurePlacer.PlacementResult result = METEOR_SITE_PLACER.place(level, origin);
            if (result != null) {
                return BlockPos.containing(result.bounds().getCenter());
            }
            ModConstants.LOGGER.error("Failed to place meteor impact structure at {}", origin);
            return null;
        } finally {
            releaseForcedChunk(level, impactChunk);
        }
    }

    private enum PlacementState {
        SUCCESS,
        FAILED
    }

    private static PlacementState placeBunker(ServerLevel level, BlockPos impactPos, MeteorImpactData impactData) {
        BlockPos bunkerPos = impactPos.offset(32, 0, 0);
        bunkerPos = ensurePlainsSurface(level, bunkerPos);

        StructureSpawnTracker tracker = StructureSpawnTracker.get(level);
        if (tracker.hasSpawnedAt(bunkerPos)) {
            impactData.setBunkerPos(bunkerPos);
            WorldSpawnHandler.refreshWorldSpawn(level);
            markPlacementComplete(level);
            return PlacementState.SUCCESS;
        }

        ChunkPos bunkerChunk = new ChunkPos(bunkerPos);
        forceChunk(level, bunkerChunk);
        try {
            NBTStructurePlacer.PlacementResult result = BUNKER_PLACER.place(level, bunkerPos);
            if (result == null) {
                ModConstants.LOGGER.error("Failed to place bunker structure at {}", bunkerPos);
                return PlacementState.FAILED;
            }

            BunkerProtectionHandler.addBunkerBounds(result.bounds());
            tracker.addSpawnPos(bunkerPos);
            impactData.setBunkerPos(bunkerPos);

            List<BlockPos> cryoPositions = result.cryoPositions();
            if (!cryoPositions.isEmpty()) {
                CryoSpawnData data = CryoSpawnData.get(level);
                data.replaceAll(cryoPositions);
                PlayerSpawnHandler.setSpawnBlocks(cryoPositions);
            }

            WorldSpawnHandler.refreshWorldSpawn(level);
            return PlacementState.SUCCESS;
        } finally {
            releaseForcedChunk(level, bunkerChunk);
        }
    }

    private static void enqueueTask(ServerLevel level, Runnable task) {
        enqueueTask(level, task, 1);
    }

    private static void enqueueTask(ServerLevel level, Runnable task, int delayTicks) {
        DeferredTaskScheduler.schedule(level, task, delayTicks);
    }

    private static void forceChunk(ServerLevel level, ChunkPos chunkPos) {
        long key = chunkPos.toLong();
        if (forcedChunks.contains(key)) {
            return;
        }
        if (level.getForcedChunks().contains(key)) {
            forcedChunks.add(key);
            return;
        }
        level.setChunkForced(chunkPos.x, chunkPos.z, true);
        forcedChunks.add(key);
    }

    private static void releaseForcedChunk(ServerLevel level, ChunkPos chunkPos) {
        long key = chunkPos.toLong();
        if (forcedChunks.remove(key)) {
            level.setChunkForced(chunkPos.x, chunkPos.z, false);
        }
    }

    private static BlockPos findImpactOrigin(ServerLevel level, RandomSource random, BlockPos reference,
                                             List<BlockPos> existing) {
        long minDistanceSq = (long) MIN_BLOCK_SEPARATION * (long) MIN_BLOCK_SEPARATION;

        for (int attempt = 0; attempt < POSITION_ATTEMPTS; attempt++) {
            int distance = MIN_BLOCK_SEPARATION + random.nextInt(MIN_BLOCK_SEPARATION / 2);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int x = reference.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = reference.getZ() + (int) Math.round(Math.sin(angle) * distance);

            BlockPos candidate = new BlockPos(x, 0, z);
            boolean farEnough = true;
            for (BlockPos existingPos : existing) {
                long dx = (long) candidate.getX() - existingPos.getX();
                long dz = (long) candidate.getZ() - existingPos.getZ();
                long distSq = dx * dx + dz * dz;
                if (distSq < minDistanceSq) {
                    farEnough = false;
                    break;
                }
            }

            if (farEnough) {
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, candidate);
                if (isPlains(level, surface)) {
                    return surface;
                }
            }
        }

        BlockPos fallback = reference.offset(MIN_BLOCK_SEPARATION * (existing.size() + 1), 0, 0);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, fallback);
        BlockPos plains = findNearbyPlains(level, surface, MIN_BLOCK_SEPARATION * 2);
        return plains != null ? plains : surface;
    }

    private static BlockPos ensurePlainsSurface(ServerLevel level, BlockPos position) {
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, position);
        if (isPlains(level, surface)) {
            return surface;
        }
        BlockPos plains = findNearbyPlains(level, surface, MIN_BLOCK_SEPARATION);
        return plains != null ? plains : surface;
    }

    private static BlockPos findNearbyPlains(ServerLevel level, BlockPos center, int radius) {
        int chunkRadius = Math.max(0, radius >> 4);
        ChunkPos originChunk = new ChunkPos(center);
        BlockPos closestLand = null;
        double closestLandDistSq = Double.MAX_VALUE;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkPos chunkPos = new ChunkPos(originChunk.x + dx, originChunk.z + dz);
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, chunkPos.getWorldPosition());
                if (isPlains(level, surface)) {
                    double distSq = surface.distSqr(center);
                    if (distSq < closestLandDistSq) {
                        closestLandDistSq = distSq;
                        closestLand = surface;
                    }
                }
            }
        }
        return closestLand;
    }

    private static boolean isPlains(ServerLevel level, BlockPos pos) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        return biomeHolder.is(IS_PLAINS_TAG)
                || biomeHolder.is(Biomes.PLAINS)
                || biomeHolder.is(Biomes.SUNFLOWER_PLAINS)
                || biomeHolder.is(Biomes.SAVANNA)
                || biomeHolder.is(Biomes.SAVANNA_PLATEAU)
                || biomeHolder.is(Biomes.WINDSWEPT_SAVANNA)
                || biomeHolder.is(Biomes.SNOWY_PLAINS);
    }

    private static boolean shouldSkipForExistingWorld(ServerLevel level) {
        ServerLevelData data = level.getLevelData();
        if (!data.isInitialized()) {
            return false;
        }

        long gameTime = data.getGameTime();
        long dayTime = data.getDayTime();
        if (gameTime <= INITIAL_PLACEMENT_DELAY_TICKS && dayTime <= INITIAL_PLACEMENT_DELAY_TICKS) {
            return false;
        }

        return true;
    }
}
