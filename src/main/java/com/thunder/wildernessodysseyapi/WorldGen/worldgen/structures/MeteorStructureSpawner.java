package com.thunder.wildernessodysseyapi.WorldGen.worldgen.structures;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.BunkerProtectionHandler;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.StructureSpawnTracker;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock.CryoSpawnData;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock.PlayerSpawnHandler;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock.WorldSpawnHandler;
import com.thunder.wildernessodysseyapi.WorldGen.structure.NBTStructurePlacer;
import com.thunder.wildernessodysseyapi.WorldGen.util.DeferredTaskScheduler;
import com.thunder.wildernessodysseyapi.WorldGen.worldgen.configurable.StructureConfig;
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
import java.util.function.Supplier;


/**
 * Handles initial meteor impact site and bunker placement when a world is created.
 */
public class MeteorStructureSpawner {
    private static final TagKey<Biome> IS_PLAINS_TAG =
            TagKey.create(Registries.BIOME, ResourceLocation.parse("c:is_plains"));
    private static final int IMPACT_SITE_COUNT = 3;
    private static final int MIN_CHUNK_SEPARATION = 32;
    private static final int MIN_BLOCK_SEPARATION = MIN_CHUNK_SEPARATION * 16;
    private static final int POSITION_ATTEMPTS = 64;
    private static final int MIN_POSITION_ATTEMPTS = 16;
    private static final int MAX_DEFERRED_ATTEMPTS = 20 * 60 * 5; // five minutes worth of retries
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    private static double plainsHitRate = 0.5D;

    private static final NBTStructurePlacer METEOR_SITE_PLACER =
            new NBTStructurePlacer(ModConstants.MOD_ID, "impact_zone.nbt");
    private static final NBTStructurePlacer BUNKER_PLACER =
            new NBTStructurePlacer(ModConstants.MOD_ID, "bunker.nbt");

    private static boolean placed = false;
    private static final Set<Long> forcedChunks = new HashSet<>();
    private static final Map<ResourceKey<Level>, Set<Long>> failedImpactPositions = new HashMap<>();
    private static final Map<ResourceKey<Level>, Integer> retryAttempts = new HashMap<>();
    private static final Set<ResourceKey<Level>> initialPlacementScheduled = new HashSet<>();
    private static final int INITIAL_PLACEMENT_DELAY_TICKS = 20 * 5; // 5 seconds

    public static void tryPlace(ServerLevel level) {
        tryPlace(level, 0);
    }

    public static void resetState() {
        placed = false;
        forcedChunks.clear();
        failedImpactPositions.clear();
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

        if (StructureConfig.DEBUG_DISABLE_IMPACT_SITES.get()) {
            markPlacementComplete(level);
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
        Set<Long> failedOrigins = failedImpactPositions.computeIfAbsent(level.dimension(), dim -> new HashSet<>());

        List<BlockPos> storedSites = new ArrayList<>(impactData.getImpactPositions());
        int originalCount = storedSites.size();

        for (int i = storedSites.size(); i < IMPACT_SITE_COUNT; i++) {
            BlockPos origin = findImpactOrigin(level, random, spawn, storedSites, failedOrigins);
            if (origin == null) {
                ModConstants.LOGGER.info("Delaying meteor impact placement; no plains biome found near {} yet", spawn);
                scheduleRetry(level, attempt + 1);
                return;
            }
            BlockPos impactPos = placeMeteorSite(level, origin);
            if (impactPos == null) {
                failedOrigins.add(origin.asLong());
                scheduleRetry(level, attempt + 1);
                return;
            }
            storedSites.add(impactPos);
        }

        if (storedSites.size() != originalCount) {
            impactData.setImpactPositions(storedSites);
        }

        if (StructureConfig.DEBUG_DISABLE_BUNKER_SPAWNS.get()) {
            markPlacementComplete(level);
            return;
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
        Map<Long, Boolean> plainsCache = new HashMap<>();
        StructureSpawnTracker tracker = StructureSpawnTracker.get(level);
        int minDistanceBlocks = Math.max(MIN_BLOCK_SEPARATION, StructureConfig.BUNKER_MIN_DISTANCE.get() * 16);

        List<Supplier<BlockPos>> bunkerCandidates = buildBunkerCandidates(impactPos, minDistanceBlocks);
        for (Supplier<BlockPos> candidateSupplier : bunkerCandidates) {
            BlockPos bunkerPos = ensurePlainsSurface(level, candidateSupplier.get(), plainsCache);

            if (!tracker.isFarEnough(bunkerPos, StructureConfig.BUNKER_MIN_DISTANCE.get())) {
                continue;
            }

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
                    continue;
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

        return PlacementState.FAILED;
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
                                             List<BlockPos> existing, Set<Long> failedOrigins) {
        long minDistanceSq = (long) MIN_BLOCK_SEPARATION * (long) MIN_BLOCK_SEPARATION;
        int positionAttempts = getAdaptivePositionAttempts();
        Map<Long, Boolean> plainsCache = new HashMap<>();
        int plainsHits = 0;
        int attemptsUsed = 0;
        double baseAngle = random.nextDouble() * Math.PI * 2.0D;

        for (int attempt = 0; attempt < positionAttempts; attempt++) {
            double angle = baseAngle + attempt * GOLDEN_ANGLE;
            double distanceLerp = (double) attempt / Math.max(1, positionAttempts - 1);
            double distance = MIN_BLOCK_SEPARATION + distanceLerp * (MIN_BLOCK_SEPARATION * 0.5D);
            int x = reference.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = reference.getZ() + (int) Math.round(Math.sin(angle) * distance);

            BlockPos candidate = new BlockPos(x, 0, z);
            attemptsUsed++;
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

            if (farEnough && !failedOrigins.contains(candidate.asLong())) {
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, candidate);
                if (isPlains(level, surface, plainsCache)) {
                    plainsHits++;
                    updatePlainsHitRate(plainsHits, attemptsUsed);
                    return surface;
                }
            }
        }

        updatePlainsHitRate(plainsHits, attemptsUsed);

        int[] searchMultipliers = new int[] {existing.size() + 1, existing.size() + 2, existing.size() + 3};
        for (int multiplier : searchMultipliers) {
            BlockPos fallback = reference.offset(MIN_BLOCK_SEPARATION * multiplier, 0, 0);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, fallback);
            BlockPos plains = findNearbyPlains(level, surface, MIN_BLOCK_SEPARATION * 3, plainsCache);
            if (plains != null && !failedOrigins.contains(plains.asLong())) {
                return plains;
            }
        }

        BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, reference);
        BlockPos plains = findNearbyPlains(level, surface, MIN_BLOCK_SEPARATION * 4, plainsCache);
        if (plains != null && !failedOrigins.contains(plains.asLong())) {
            return plains;
        }

        return null;
    }

    private static BlockPos ensurePlainsSurface(ServerLevel level, BlockPos position, Map<Long, Boolean> plainsCache) {
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, position);
        if (isPlains(level, surface, plainsCache)) {
            return surface;
        }
        BlockPos plains = findNearbyPlains(level, surface, MIN_BLOCK_SEPARATION, plainsCache);
        return plains != null ? plains : surface;
    }

    private static BlockPos findNearbyPlains(ServerLevel level, BlockPos center, int radius) {
        return findNearbyPlains(level, center, radius, new HashMap<>());
    }

    private static BlockPos findNearbyPlains(ServerLevel level, BlockPos center, int radius, Map<Long, Boolean> plainsCache) {
        int chunkRadius = Math.max(0, radius >> 4);
        ChunkPos originChunk = new ChunkPos(center);
        BlockPos closestLand = null;
        double closestLandDistSq = Double.MAX_VALUE;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkPos chunkPos = new ChunkPos(originChunk.x + dx, originChunk.z + dz);
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, chunkPos.getWorldPosition());
                if (isPlains(level, surface, plainsCache)) {
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

    private static List<Supplier<BlockPos>> buildBunkerCandidates(BlockPos impactPos, int minDistanceBlocks) {
        List<Supplier<BlockPos>> candidates = new ArrayList<>();
        RandomSource random = RandomSource.create(impactPos.asLong());

        int baseRadius = Math.max(minDistanceBlocks, 32);
        int[] radii = new int[] {baseRadius, baseRadius + 8, baseRadius + 16};
        int[] angles = new int[] {0, 45, 90, 135, 180, 225, 270, 315};

        for (int radius : radii) {
            for (int angle : angles) {
                double radians = Math.toRadians(angle);
                int xOffset = (int) Math.round(Math.cos(radians) * radius);
                int zOffset = (int) Math.round(Math.sin(radians) * radius);
                candidates.add(() -> impactPos.offset(xOffset, 0, zOffset));
            }
        }

        candidates.add(() -> impactPos.offset(minDistanceBlocks, 0, 0));
        java.util.Collections.shuffle(candidates, new java.util.Random(random.nextLong()));
        return candidates;
    }

    private static int getAdaptivePositionAttempts() {
        double effortFactor = 1.0D - Math.min(0.6D, plainsHitRate * 0.6D);
        int attempts = (int) Math.round(MIN_POSITION_ATTEMPTS
                + (POSITION_ATTEMPTS - MIN_POSITION_ATTEMPTS) * effortFactor);
        return Math.max(MIN_POSITION_ATTEMPTS, Math.min(POSITION_ATTEMPTS, attempts));
    }

    private static void updatePlainsHitRate(int plainsHits, int attemptsUsed) {
        if (attemptsUsed <= 0) {
            return;
        }
        double sampleRate = (double) plainsHits / attemptsUsed;
        plainsHitRate = (plainsHitRate * 0.75D) + (sampleRate * 0.25D);
    }

    private static boolean isPlains(ServerLevel level, BlockPos pos) {
        return isPlains(level, pos, new HashMap<>());
    }

    private static boolean isPlains(ServerLevel level, BlockPos pos, Map<Long, Boolean> plainsCache) {
        long key = pos.asLong();
        Boolean cached = plainsCache.get(key);
        if (cached != null) {
            return cached;
        }

        Holder<Biome> biomeHolder = level.getBiome(pos);
        boolean result = biomeHolder.is(IS_PLAINS_TAG)
                || biomeHolder.is(Biomes.PLAINS)
                || biomeHolder.is(Biomes.SUNFLOWER_PLAINS)
                || biomeHolder.is(Biomes.SAVANNA)
                || biomeHolder.is(Biomes.SAVANNA_PLATEAU)
                || biomeHolder.is(Biomes.WINDSWEPT_SAVANNA)
                || biomeHolder.is(Biomes.SNOWY_PLAINS);
        plainsCache.put(key, result);
        return result;
    }

    private static boolean shouldSkipForExistingWorld(ServerLevel level) {
        ServerLevelData data = (ServerLevelData) level.getLevelData();
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
