package com.thunder.wildernessodysseyapi.watersystem.ocean.shore;

import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSurfaceSample;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

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

    private static final ShorelineWaterManager INSTANCE = new ShorelineWaterManager();

    private static final int REGION_SPAN = 32;
    private static final int GRID_SIZE = REGION_SPAN + 1;
    private static final int MAX_BATHYMETRY_DEPTH = 10;
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
            float boundarySurface = TideSystem.getTideOffset(level) + oceanBoundary.height();
            grid.step(0.05f, boundarySurface);
            return refreshed;
        }

        private void refreshBathymetry(ServerLevel level) {
            int seaSurfaceBlockY = level.getSeaLevel() - 1;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();

            for (int localZ = 0; localZ < GRID_SIZE; localZ++) {
                for (int localX = 0; localX < GRID_SIZE; localX++) {
                    int worldX = originX + localX;
                    int worldZ = originZ + localZ;
                    pos.set(worldX, seaSurfaceBlockY, worldZ);
                    if (!level.hasChunkAt(pos) || !touchesOceanSurface(level, pos, neighbour)) {
                        grid.setRestDepth(localX, localZ, 0.0f);
                        continue;
                    }

                    float depth = 0.0f;
                    for (int offsetY = 0; offsetY <= MAX_BATHYMETRY_DEPTH; offsetY++) {
                        pos.set(worldX, seaSurfaceBlockY - offsetY, worldZ);
                        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                            depth = offsetY;
                            break;
                        }
                    }
                    grid.setRestDepth(localX, localZ, depth);
                }
            }
        }

        private FlowSample sample(int worldX, int worldZ) {
            int localX = Math.max(0, Math.min(REGION_SPAN, worldX - originX));
            int localZ = Math.max(0, Math.min(REGION_SPAN, worldZ - originZ));
            return new FlowSample(
                    grid.surface(localX, localZ),
                    grid.velocityX(localX, localZ),
                    grid.velocityZ(localX, localZ),
                    grid.restDepth(localX, localZ)
            );
        }

        private static boolean touchesOceanSurface(
                ServerLevel level,
                BlockPos.MutableBlockPos pos,
                BlockPos.MutableBlockPos neighbour
        ) {
            if (WildernessWaterAuthority.isWaterAt(level, pos)) {
                return true;
            }
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            return WildernessWaterAuthority.isWaterAt(level, neighbour.set(x + 1, y, z))
                    || WildernessWaterAuthority.isWaterAt(level, neighbour.set(x - 1, y, z))
                    || WildernessWaterAuthority.isWaterAt(level, neighbour.set(x, y, z + 1))
                    || WildernessWaterAuthority.isWaterAt(level, neighbour.set(x, y, z - 1));
        }
    }

    private static long regionKey(int regionX, int regionZ) {
        return ((long) regionX & 0xFFFFFFFFL) | (((long) regionZ & 0xFFFFFFFFL) << 32);
    }
}
