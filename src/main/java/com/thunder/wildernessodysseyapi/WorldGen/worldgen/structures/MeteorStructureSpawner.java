package com.thunder.wildernessodysseyapi.WorldGen.worldgen.structures;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.BunkerProtectionHandler;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.StructureSpawnTracker;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock.WorldSpawnHandler;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Worldedit.WorldEditStructurePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;

import java.util.Set;
import net.neoforged.fml.ModList;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/****
 * MeteorStructureSpawner for the Wilderness Odyssey API mod.
 */
public class MeteorStructureSpawner {
    private static final int IMPACT_SITE_COUNT = 3;
    private static final int MIN_CHUNK_SEPARATION = 32;
    private static final int MIN_BLOCK_SEPARATION = MIN_CHUNK_SEPARATION * 16;
    private static final int POSITION_ATTEMPTS = 64;
    private static final long WORLD_EDIT_GRACE_PERIOD_TICKS = 5L * 60L * 20L;
    private static final int MAX_DEFERRED_ATTEMPTS = (int) WORLD_EDIT_GRACE_PERIOD_TICKS;
    private static long worldEditCountdownExpiryTick = -1L;

    private static final ResourceLocation METEOR_TEMPLATE_ID = ResourceLocation.tryBuild(MOD_ID, "impact_zone");
    private static final WorldEditStructurePlacer METEOR_SITE_PLACER =
            new WorldEditStructurePlacer(ModConstants.MOD_ID, "impact_zone.schem");
    private static final WorldEditStructurePlacer BUNKER_PLACER =
            new WorldEditStructurePlacer(ModConstants.MOD_ID, "bunker.schem");

    private static boolean placed = false;
    private static final Set<Long> forcedChunks = new HashSet<>();
    private static final Map<ResourceKey<Level>, Integer> retryAttempts = new HashMap<>();
    private static boolean missingWorldEditLogged = false;
    private static final ArrayDeque<Runnable> scheduledTasks = new ArrayDeque<>();

    public static void tryPlace(ServerLevel level) {
        tryPlace(level, 0);
    }

    public static void resetState() {
        placed = false;
        forcedChunks.clear();
        worldEditCountdownExpiryTick = -1L;
        retryAttempts.clear();
        missingWorldEditLogged = false;
        scheduledTasks.clear();
    }

    public static void tick(MinecraftServer server) {
        int tasksToRun = scheduledTasks.size();
        for (int i = 0; i < tasksToRun; i++) {
            Runnable task = scheduledTasks.poll();
            if (task != null) {
                task.run();
            }
        }
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

        if (isCountdownExpired(level)) {
            markPlacementComplete(level);
            return;
        }

        boolean worldEditLoaded = ModList.get().isLoaded("worldedit");
        boolean worldEditReady = !worldEditLoaded || WorldEditStructurePlacer.isWorldEditReady();

        if (worldEditLoaded && !worldEditReady) {
            scheduleRetry(level, attempt + 1);
            return;
        }

        long seed = level.getSeed() ^ (31L * attempt + 0x9E3779B97F4A7C15L);
        RandomSource random = RandomSource.create(seed);
        BlockPos spawn = level.getSharedSpawnPos();

        List<BlockPos> storedSites = new ArrayList<>(impactData.getImpactPositions());
        int originalCount = storedSites.size();

        for (int i = storedSites.size(); i < IMPACT_SITE_COUNT; i++) {
            BlockPos origin = findImpactOrigin(level, random, spawn, storedSites);
            BlockPos impactPos = placeMeteorSite(level, origin, worldEditLoaded, worldEditReady);
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
            if (bunkerState == PlacementState.DEFERRED) {
                return;
            }
        }

        if (impactData.getBunkerPos() != null) {
            markPlacementComplete(level);
        } else if (worldEditLoaded) {
            scheduleRetry(level, attempt + 1);
        }
    }

    private static void markPlacementComplete(ServerLevel level) {
        placed = true;
        retryAttempts.remove(level.dimension());
    }

    private static void scheduleRetry(ServerLevel level, int nextAttempt) {
        if (placed) {
            return;
        }
        if (nextAttempt > MAX_DEFERRED_ATTEMPTS) {
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

    private static BlockPos placeMeteorSite(ServerLevel level, BlockPos origin,
                                            boolean worldEditLoaded, boolean worldEditReady) {
        if (worldEditLoaded) {
            if (!worldEditReady) {
                return null;
            }

            ChunkPos impactChunk = new ChunkPos(origin);
            forceChunk(level, impactChunk);
            try {
                AABB bounds = METEOR_SITE_PLACER.placeStructure(level, origin);
                if (bounds != null) {
                    return BlockPos.containing(bounds.getCenter());
                }
            } finally {
                releaseForcedChunk(level, impactChunk);
            }
        }

        if (METEOR_TEMPLATE_ID == null) {
            return origin;
        }

        StructureTemplateManager manager = level.getStructureManager();
        Optional<StructureTemplate> templateOpt = manager.get(METEOR_TEMPLATE_ID);
        if (templateOpt.isEmpty()) {
            return origin;
        }

        StructureTemplate template = templateOpt.get();
        StructurePlaceSettings settings = new StructurePlaceSettings();
        BlockPos spawnPos = origin;
        template.placeInWorld(level, spawnPos, spawnPos, settings, level.random, 2);
        return spawnPos;
    }

    private enum PlacementState {
        SUCCESS,
        DEFERRED,
        FAILED
    }

    private static PlacementState placeBunker(ServerLevel level, BlockPos impactPos, MeteorImpactData impactData) {
        if (!ModList.get().isLoaded("worldedit")) {
            if (!missingWorldEditLogged) {
                ModConstants.LOGGER.warn("WorldEdit is required to place the bunker structure; skipping placement");
                missingWorldEditLogged = true;
            }
            return PlacementState.FAILED;
        }

        if (isCountdownExpired(level)) {
            return PlacementState.FAILED;
        }

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

        if (!WorldEditStructurePlacer.isWorldEditReady()) {
            scheduleDeferredPlacement(level, bunkerPos, bunkerChunk, 0);
            return PlacementState.DEFERRED;
        }

        AABB bounds = BUNKER_PLACER.placeStructure(level, bunkerPos);
        if (bounds != null) {
            BunkerProtectionHandler.addBunkerBounds(bounds);
            tracker.addSpawnPos(bunkerPos);
            impactData.setBunkerPos(bunkerPos);
            WorldSpawnHandler.refreshWorldSpawn(level);
            releaseForcedChunk(level, bunkerChunk);
            markPlacementComplete(level);
            return PlacementState.SUCCESS;
        }

        scheduleDeferredPlacement(level, bunkerPos, bunkerChunk, 1);
        return PlacementState.DEFERRED;
    }

    private static void scheduleDeferredPlacement(ServerLevel level, BlockPos bunkerPos,
                                                  ChunkPos bunkerChunk, int attempt) {
        enqueueTask(level, () -> {
            if (isCountdownExpired(level)) {
                releaseForcedChunk(level, bunkerChunk);
                scheduleRetry(level, attempt + 1);
                return;
            }

            StructureSpawnTracker tracker = StructureSpawnTracker.get(level);
            MeteorImpactData impactData = MeteorImpactData.get(level);

            if (tracker.hasSpawnedAt(bunkerPos)) {
                impactData.setBunkerPos(bunkerPos);
                WorldSpawnHandler.refreshWorldSpawn(level);
                releaseForcedChunk(level, bunkerChunk);
                markPlacementComplete(level);
                return;
            }

            if (!WorldEditStructurePlacer.isWorldEditReady()) {
                if (attempt == 40) {
                    ModConstants.LOGGER.warn(
                            "WorldEdit still initializing; meteor bunker placement at {} delayed",
                            bunkerPos
                    );
                }
                if (attempt < MAX_DEFERRED_ATTEMPTS) {
                    scheduleDeferredPlacement(level, bunkerPos, bunkerChunk, attempt + 1);
                } else {
                    releaseForcedChunk(level, bunkerChunk);
                    scheduleRetry(level, attempt + 1);
                }
                return;
            }

            AABB bounds = BUNKER_PLACER.placeStructure(level, bunkerPos);
            if (bounds != null) {
                BunkerProtectionHandler.addBunkerBounds(bounds);
                tracker.addSpawnPos(bunkerPos);
                impactData.setBunkerPos(bunkerPos);
                WorldSpawnHandler.refreshWorldSpawn(level);
                releaseForcedChunk(level, bunkerChunk);
                markPlacementComplete(level);
            } else if (attempt < MAX_DEFERRED_ATTEMPTS) {
                // Placement failed even though WorldEdit reported ready; try again next tick.
                scheduleDeferredPlacement(level, bunkerPos, bunkerChunk, attempt + 1);
            } else {
                releaseForcedChunk(level, bunkerChunk);
                scheduleRetry(level, attempt + 1);
            }
        });
    }

    private static void enqueueTask(ServerLevel level, Runnable task) {
        if (level.getServer().isSameThread()) {
            scheduledTasks.add(task);
        } else {
            level.getServer().execute(() -> scheduledTasks.add(task));
        }
    }

    private static void forceChunk(ServerLevel level, ChunkPos chunkPos) {
        long key = chunkPos.toLong();
        if (forcedChunks.contains(key)) {
            return;
        }
        if (level.getForcedChunks().contains(key)) {
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

    private static boolean isCountdownExpired(ServerLevel level) {
        if (!WorldEditStructurePlacer.isWorldEditReady()) {
            worldEditCountdownExpiryTick = -1L;
            return false;
        }
        if (worldEditCountdownExpiryTick < 0L) {
            worldEditCountdownExpiryTick = level.getGameTime() + WORLD_EDIT_GRACE_PERIOD_TICKS;
            return false;
        }
        return level.getGameTime() > worldEditCountdownExpiryTick;
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
        for (int r = 0; r <= chunkRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r != 0 && Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    ChunkPos chunkPos = new ChunkPos(originChunk.x + dx, originChunk.z + dz);
                    if (!level.getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) {
                        continue;
                    }
                    BlockPos sample = new BlockPos(chunkPos.getMiddleBlockX(), center.getY(), chunkPos.getMiddleBlockZ());
                    BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, sample);
                    if (isPlains(level, surface)) {
                        return surface;
                    }
                    if (isLand(level, surface)) {
                        double distSq = surface.distSqr(center);
                        if (distSq < closestLandDistSq) {
                            closestLand = surface;
                            closestLandDistSq = distSq;
                        }
                    }
                }
            }
        }
        return closestLand;
    }

    private static boolean isPlains(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        for (ResourceKey<Biome> allowed : ALLOWED_PLAINS) {
            if (biome.is(allowed)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLand(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER);
    }

    private static final Set<ResourceKey<Biome>> ALLOWED_PLAINS = Set.of(
            Biomes.PLAINS,
            Biomes.SUNFLOWER_PLAINS
    );
}
