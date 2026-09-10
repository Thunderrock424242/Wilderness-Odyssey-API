package com.thunder.wildernessodysseyapi.watersystem.ocean.shore;

import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSurfaceSample;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Owns bounded shallow-water regions around players in loaded server levels.
 *
 * <p>Regions follow 32-block cells, sample local bathymetry, and couple their
 * open boundary to the same tide and Gerstner spectrum used by ocean entities.
 * No Minecraft water blocks are created or removed by this manager.</p>
 */
public final class ShorelineWaterManager {

    private static final float SHARED_TIDE_SURFACE_SCALE = 0.18f;

    private static final ShorelineWaterManager INSTANCE = new ShorelineWaterManager();

    private static final int REGION_SPAN = 32;
    private static final int GRID_SIZE = REGION_SPAN + 1;
    private static final int MAX_BATHYMETRY_DEPTH = 10;
    private static final int MAX_DRY_SHORE_HEIGHT = 4;
    private static final int BATHYMETRY_REFRESH_TICKS = 100;
    private static final int REGION_EXPIRY_TICKS = 240;
    private static final int MAX_REGIONS_PER_LEVEL = 18;
    private static final int MAX_BATHYMETRY_REFRESHES_PER_TICK = 1;

    private final Map<ServerLevel, Map<Long, Region>> regionsByLevel = new IdentityHashMap<>();
    private final Map<ServerLevel, Integer> nextRegionByLevel = new IdentityHashMap<>();

    private ShorelineWaterManager() {
    }

    /** Returns the global server shoreline manager. */
    public static ShorelineWaterManager get() {
        return INSTANCE;
    }

    /**
     * Advances regions surrounding players in one server dimension.
     *
     * @param level loaded server dimension
     */
    public void tick(ServerLevel level) {
        Map<Long, Region> regions = regionsByLevel.computeIfAbsent(level, ignored -> new HashMap<>());
        long gameTime = level.getGameTime();

        for (var player : level.players()) {
            int regionX = Math.floorDiv(player.blockPosition().getX(), REGION_SPAN);
            int regionZ = Math.floorDiv(player.blockPosition().getZ(), REGION_SPAN);
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    int targetX = regionX + offsetX;
                    int targetZ = regionZ + offsetZ;
                    long key = regionKey(targetX, targetZ);
                    Region region = regions.get(key);
                    if (region == null && regions.size() < MAX_REGIONS_PER_LEVEL) {
                        region = new Region(targetX, targetZ);
                        regions.put(key, region);
                    }
                    if (region == null) {
                        continue;
                    }
                    region.lastSeenTick = gameTime;
                }
            }
        }

        // Expiry is independent of the simulation budget so stale entries do
        // not block active regions from entering the round-robin schedule.
        Iterator<Region> iterator = regions.values().iterator();
        while (iterator.hasNext()) {
            Region region = iterator.next();
            if (gameTime - region.lastSeenTick > REGION_EXPIRY_TICKS) {
                iterator.remove();
            }
        }

        if (regions.isEmpty()) {
            nextRegionByLevel.remove(level);
            return;
        }

        // HashMap iteration order is not a scheduler. Sort stable region keys
        // and rotate the starting index so every active shoreline receives a
        // bounded update even when the per-tick budget is smaller than the set.
        List<Map.Entry<Long, Region>> activeRegions = new ArrayList<>(regions.entrySet());
        activeRegions.sort(Comparator.comparingLong(Map.Entry::getKey));
        int cursor = nextRegionByLevel.getOrDefault(level, 0);
        int[] updateOrder = roundRobinOrder(
                activeRegions.size(),
                cursor,
                Math.max(0, WaterSimulationConfig.waterBodyUpdatesPerTick())
        );
        int bathymetryRefreshes = 0;
        for (int index : updateOrder) {
            Region region = activeRegions.get(index).getValue();
            if (region.tick(level, gameTime, hasBathymetryRefreshCapacity(bathymetryRefreshes))) {
                bathymetryRefreshes++;
            }
        }
        nextRegionByLevel.put(level, Math.floorMod(cursor + updateOrder.length, activeRegions.size()));
    }

    /**
     * Samples the local depth-averaged shoreline flow for entity coupling.
     *
     * @param level authoritative level
     * @param worldX world X coordinate
     * @param worldZ world Z coordinate
     * @return current flow and elevation, or a dry sample when no region exists
     */
    public FlowSample sample(ServerLevel level, double worldX, double worldZ) {
        Map<Long, Region> regions = regionsByLevel.get(level);
        if (regions == null) {
            return FlowSample.dry();
        }

        int blockX = (int) Math.floor(worldX);
        int blockZ = (int) Math.floor(worldZ);
        int regionX = Math.floorDiv(blockX, REGION_SPAN);
        int regionZ = Math.floorDiv(blockZ, REGION_SPAN);
        Region region = regions.get(regionKey(regionX, regionZ));
        return region == null ? FlowSample.dry() : region.sample(blockX, blockZ);
    }

    /**
     * Returns a local derived-grid balance for diagnostics, or {@code null}
     * outside cached regions. This volume must not be added to the canonical
     * or watershed water budget because the grid owns only current estimates.
     */
    public ShallowWaterGrid.Balance balanceAt(ServerLevel level, BlockPos position) {
        Map<Long, Region> regions = regionsByLevel.get(level);
        if (regions == null) return null;
        Region region = regions.get(regionKey(Math.floorDiv(position.getX(), REGION_SPAN),
                Math.floorDiv(position.getZ(), REGION_SPAN)));
        return region == null ? null : region.grid.balance();
    }

    /** Invalidates only cached regions touching an edited terrain column. */
    public void invalidate(ServerLevel level, BlockPos position) {
        Map<Long, Region> regions = regionsByLevel.get(level);
        if (regions == null) return;
        for (Region region : regions.values()) {
            if (position.getX() >= region.originX - 1 && position.getX() <= region.originX + REGION_SPAN + 1
                    && position.getZ() >= region.originZ - 1 && position.getZ() <= region.originZ + REGION_SPAN + 1) {
                region.lastBathymetryRefresh = Long.MIN_VALUE;
            }
        }
    }

    /** Clears runtime regions for an unloading dimension. */
    public void clearLevel(ServerLevel level) {
        regionsByLevel.remove(level);
        nextRegionByLevel.remove(level);
    }

    static int[] roundRobinOrder(int regionCount, int cursor, int updateBudget) {
        if (regionCount <= 0 || updateBudget <= 0) {
            return new int[0];
        }

        int updateCount = Math.min(regionCount, updateBudget);
        int start = Math.floorMod(cursor, regionCount);
        int[] order = new int[updateCount];
        for (int offset = 0; offset < updateCount; offset++) {
            order[offset] = (start + offset) % regionCount;
        }
        return order;
    }

    static boolean hasBathymetryRefreshCapacity(int refreshesThisTick) {
        return Math.max(0, refreshesThisTick) < MAX_BATHYMETRY_REFRESHES_PER_TICK;
    }

    static boolean isBathymetryRefreshDue(long gameTime, long lastRefreshTick) {
        return lastRefreshTick == Long.MIN_VALUE
                || gameTime < lastRefreshTick
                || gameTime - lastRefreshTick >= BATHYMETRY_REFRESH_TICKS;
    }

    /** Describes one shoreline-grid sample in world units. */
    public record FlowSample(float surfaceOffset, float velocityX, float velocityZ, float depth) {
        private static final FlowSample DRY = new FlowSample(0.0f, 0.0f, 0.0f, 0.0f);

        /** Returns whether the sample represents a wet bathymetric cell. */
        public boolean wet() {
            return depth > 0.01f;
        }

        private static FlowSample dry() {
            return DRY;
        }
    }

    private static final class Region {
        private final int originX;
        private final int originZ;
        private final ShallowWaterGrid grid = new ShallowWaterGrid(GRID_SIZE, GRID_SIZE, 1.0f);
        private long lastSeenTick;
        private long lastBathymetryRefresh = Long.MIN_VALUE;
        private long lastSimulatedTick = Long.MIN_VALUE;

        private Region(int regionX, int regionZ) {
            this.originX = regionX * REGION_SPAN;
            this.originZ = regionZ * REGION_SPAN;
        }

        /**
         * Advances this region while respecting the manager's level-wide scan budget.
         *
         * @return whether this call consumed one bathymetry refresh slot
         */
        private boolean tick(ServerLevel level, long gameTime, boolean bathymetryRefreshAllowed) {
            boolean refreshDue = isBathymetryRefreshDue(gameTime, lastBathymetryRefresh);
            boolean refreshed = false;
            if (refreshDue && bathymetryRefreshAllowed) {
                refreshBathymetry(level);
                lastBathymetryRefresh = gameTime;
                refreshed = true;
            }

            // A new region remains dry until its first bounded scan. Existing
            // regions may safely use their previous depth grid for a few ticks
            // while another due region consumes this tick's refresh slot.
            if (lastBathymetryRefresh == Long.MIN_VALUE) {
                return refreshed;
            }

            if (lastSimulatedTick == gameTime) return refreshed;

            float centerX = originX + REGION_SPAN * 0.5f;
            float centerZ = originZ + REGION_SPAN * 0.5f;
            float timeSeconds = gameTime / 20.0f;
            WaveSurfaceSample oceanBoundary = GerstnerWaveProfile.OCEAN.sampleAt(
                    centerX,
                    centerZ,
                    timeSeconds,
                    GerstnerWaveProfile.OCEAN.waveCount,
                    OceanSeaState.sampleAt(level, centerX, centerZ, 0.0f).spectrum()
            );
            float boundarySurface = TideSystem.getTideOffset(level) * SHARED_TIDE_SURFACE_SCALE
                    + oceanBoundary.height();
            float dtSeconds = elapsedStepSeconds(gameTime, lastSimulatedTick);
            lastSimulatedTick = gameTime;
            grid.step(dtSeconds, boundarySurface);
            return refreshed;
        }

        private void refreshBathymetry(ServerLevel level) {
            int seaSurfaceBlockY = level.getSeaLevel() - 1;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

            for (int localZ = 0; localZ < GRID_SIZE; localZ++) {
                for (int localX = 0; localX < GRID_SIZE; localX++) {
                    int worldX = originX + localX;
                    int worldZ = originZ + localZ;
                    pos.set(worldX, seaSurfaceBlockY, worldZ);
                    LevelChunk chunk = level.getChunkSource().getChunkNow(worldX >> 4, worldZ >> 4);
                    if (chunk == null) {
                        grid.setBlocked(localX, localZ);
                        continue;
                    }

                    if (WildernessWaterAuthority.isWOWaterAt(level, pos)) {
                        // Generated-column floors are reused without a global
                        // ocean scan. The capped offshore column stays wet.
                        float depth = WildernessWaterAuthority.getWaterDepth(level, pos, MAX_BATHYMETRY_DEPTH);
                        grid.setRestDepth(localX, localZ, depth);
                    } else {
                        int floorSurfaceY = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,
                                worldX & 15, worldZ & 15) + 1;
                        int relativeBed = floorSurfaceY - level.getSeaLevel();
                        if (relativeBed < -MAX_BATHYMETRY_DEPTH || relativeBed > MAX_DRY_SHORE_HEIGHT) {
                            grid.setBlocked(localX, localZ);
                        } else {
                            // Known dry terrain can wet from a neighbouring
                            // cell, but never acquires an ocean ghost by itself.
                            grid.setTerrain(localX, localZ, relativeBed, 0.0f);
                        }
                    }
                }
            }

            // Only loaded, ocean-classified neighbours are open boundaries.
            // A lake at sea level, beach, wall or unloaded chunk is not an
            // infinite tide reservoir. Corners have two distinct faces.
            for (int coordinate = 0; coordinate < GRID_SIZE; coordinate++) {
                grid.setBoundaryOpen(ShallowWaterGrid.Side.WEST, coordinate, isOceanBoundary(level,
                        pos.set(originX - 1, seaSurfaceBlockY, originZ + coordinate)));
                grid.setBoundaryOpen(ShallowWaterGrid.Side.EAST, coordinate, isOceanBoundary(level,
                        pos.set(originX + GRID_SIZE, seaSurfaceBlockY, originZ + coordinate)));
                grid.setBoundaryOpen(ShallowWaterGrid.Side.NORTH, coordinate, isOceanBoundary(level,
                        pos.set(originX + coordinate, seaSurfaceBlockY, originZ - 1)));
                grid.setBoundaryOpen(ShallowWaterGrid.Side.SOUTH, coordinate, isOceanBoundary(level,
                        pos.set(originX + coordinate, seaSurfaceBlockY, originZ + GRID_SIZE)));
            }
        }

        private FlowSample sample(int worldX, int worldZ) {
            int localX = Math.max(0, Math.min(REGION_SPAN, worldX - originX));
            int localZ = Math.max(0, Math.min(REGION_SPAN, worldZ - originZ));
            return new FlowSample(
                    grid.surface(localX, localZ),
                    grid.velocityX(localX, localZ),
                    grid.velocityZ(localX, localZ),
                    grid.waterDepth(localX, localZ)
            );
        }

        private static boolean isOceanBoundary(ServerLevel level, BlockPos pos) {
            return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null
                    && WildernessWaterAuthority.isWOWaterAt(level, pos)
                    && WaterBodyClassifier.isOceanic(WaterBodyClassifier.classify(level, pos));
        }
    }

    private static long regionKey(int regionX, int regionZ) {
        return ((long) regionX & 0xFFFFFFFFL) | (((long) regionZ & 0xFFFFFFFFL) << 32);
    }

    static float elapsedStepSeconds(long gameTime, long previousSimulationTick) {
        if (previousSimulationTick == Long.MIN_VALUE) return 0.05f;
        if (gameTime <= previousSimulationTick) return 0.0f;
        // The grid retains unprocessed elapsed time behind its CFL/work cap.
        // A delayed region must not run slower simply because its turn was late.
        return (float) (((double) gameTime - previousSimulationTick) / 20.0);
    }
}
