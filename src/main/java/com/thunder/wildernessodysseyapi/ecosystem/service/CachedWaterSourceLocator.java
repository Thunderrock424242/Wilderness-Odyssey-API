package com.thunder.wildernessodysseyapi.ecosystem.service;

import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.api.WaterSourceLocator;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Finds dry shoreline approaches through the public Wilderness water API.
 *
 * <p>Authority-owned water is queried first. Vanilla and modded water-tag
 * fluids remain a compatibility fallback. The locator never mutates water,
 * reads chunk attachments, or causes chunk loads.</p>
 */
public final class CachedWaterSourceLocator implements WaterSourceLocator {

    private static final long CACHE_TICKS = 200L;
    private static final int MAXIMUM_CACHE_ENTRIES = 384;
    private static final int MAXIMUM_COLUMN_SAMPLES = 768;
    private final WaterAccess water = WaterServices.access();
    private final Map<ServerLevel, Map<Long, Entry>> levels = new WeakHashMap<>();

    @Override
    public Optional<EnvironmentalContext.WaterTarget> find(
            PathfinderMob animal,
            SpeciesBehaviorProfile profile,
            int radius
    ) {
        if (!(animal.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        BlockPos origin = animal.blockPosition();
        long gameTime = level.getGameTime();
        long key = cacheKey(origin, radius);
        Map<Long, Entry> cache = levels.computeIfAbsent(level, ignored -> new HashMap<>());
        Entry cached = cache.get(key);
        if (cached != null && cached.expiresAt() > gameTime && valid(level, cached.target())) {
            return Optional.of(cached.target());
        }

        EnvironmentalContext.WaterTarget target = search(level, origin, radius, profile.drinking());
        if (target != null) {
            cache.put(key, new Entry(gameTime + CACHE_TICKS, target));
            trim(cache);
        }
        return Optional.ofNullable(target);
    }

    /** Releases cached positions for an unloading level. */
    public void clear(ServerLevel level) {
        levels.remove(level);
    }

    private EnvironmentalContext.WaterTarget search(
            ServerLevel level,
            BlockPos origin,
            int radius,
            SpeciesBehaviorProfile.Drinking settings
    ) {
        int samples = 0;
        for (int ring = 0; ring <= radius && samples < MAXIMUM_COLUMN_SAMPLES; ring++) {
            for (int dx = -ring; dx <= ring && samples < MAXIMUM_COLUMN_SAMPLES; dx++) {
                for (int dzSign = -1; dzSign <= 1; dzSign += 2) {
                    int dz = ring * dzSign;
                    if (ring == 0 && dzSign > -1) {
                        continue;
                    }
                    samples++;
                    EnvironmentalContext.WaterTarget target = inspectColumn(
                            level, origin.offset(dx, 0, dz), origin.getY(), settings);
                    if (target != null) {
                        return target;
                    }
                }
            }
            for (int dz = -ring + 1; dz < ring && samples < MAXIMUM_COLUMN_SAMPLES; dz++) {
                for (int dxSign = -1; dxSign <= 1; dxSign += 2) {
                    int dx = ring * dxSign;
                    samples++;
                    EnvironmentalContext.WaterTarget target = inspectColumn(
                            level, origin.offset(dx, 0, dz), origin.getY(), settings);
                    if (target != null) {
                        return target;
                    }
                }
            }
        }
        return null;
    }

    private EnvironmentalContext.WaterTarget inspectColumn(
            ServerLevel level,
            BlockPos horizontal,
            int originY,
            SpeciesBehaviorProfile.Drinking settings
    ) {
        if (!level.hasChunkAt(horizontal)) {
            return null;
        }
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, horizontal.getX(), horizontal.getZ()) - 1;
        int[] candidates = {surfaceY, originY + 2, originY + 1, originY, originY - 1, originY - 2, originY - 3};
        for (int y : candidates) {
            BlockPos waterPosition = new BlockPos(horizontal.getX(), y, horizontal.getZ());
            if (!level.isInWorldBounds(waterPosition) || !isWater(level, waterPosition)) {
                continue;
            }
            double depth = waterDepth(level, waterPosition);
            WatershedConditions conditions = water.getWatershedConditions(level, waterPosition);
            float safeCurrent = settings.canSwim() ? 1.10f : 0.40f;
            if (conditions.flooding()
                    || conditions.floodRisk() >= 0.88f
                    || conditions.currentStrength() > safeCurrent) {
                continue;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos approach = waterPosition.relative(direction);
                if (isStandable(level, approach) && !isWater(level, approach)) {
                    return target(waterPosition, approach, depth, conditions);
                }
            }
            if (settings.canSwim() && depth <= settings.maximumSafeDepth()) {
                return target(waterPosition, waterPosition, depth, conditions);
            }
        }
        return null;
    }

    private static EnvironmentalContext.WaterTarget target(
            BlockPos waterPosition,
            BlockPos approachPosition,
            double depth,
            WatershedConditions conditions
    ) {
        return new EnvironmentalContext.WaterTarget(
                waterPosition,
                approachPosition,
                depth,
                conditions.floodRisk(),
                conditions.currentStrength(),
                conditions.clarity()
        );
    }

    private boolean valid(ServerLevel level, EnvironmentalContext.WaterTarget target) {
        return level.hasChunkAt(target.waterPosition())
                && isWater(level, target.waterPosition())
                && (target.approachPosition().equals(target.waterPosition())
                || isStandable(level, target.approachPosition()));
    }

    private boolean isWater(ServerLevel level, BlockPos position) {
        return water.isWaterAt(level, position) || level.getFluidState(position).is(FluidTags.WATER);
    }

    private double waterDepth(ServerLevel level, BlockPos position) {
        if (water.isWaterAt(level, position)) {
            double depth = water.getDepth(level, Vec3.atCenterOf(position));
            return Double.isFinite(depth) ? Math.max(0.0, depth) : 0.0;
        }
        int depth = 0;
        BlockPos.MutableBlockPos cursor = position.mutable();
        while (depth < 8 && level.getFluidState(cursor).is(FluidTags.WATER)) {
            depth++;
            cursor.move(Direction.DOWN);
        }
        return depth;
    }

    static boolean isStandable(ServerLevel level, BlockPos position) {
        if (!level.hasChunkAt(position)) {
            return false;
        }
        BlockState feet = level.getBlockState(position);
        BlockState head = level.getBlockState(position.above());
        BlockPos floorPosition = position.below();
        BlockState floor = level.getBlockState(floorPosition);
        return feet.getCollisionShape(level, position).isEmpty()
                && head.getCollisionShape(level, position.above()).isEmpty()
                && floor.isFaceSturdy(level, floorPosition, Direction.UP);
    }

    private static long cacheKey(BlockPos position, int radius) {
        long section = SectionPos.asLong(
                SectionPos.blockToSectionCoord(position.getX()),
                SectionPos.blockToSectionCoord(position.getY()),
                SectionPos.blockToSectionCoord(position.getZ())
        );
        return section ^ ((long) Math.max(1, radius / 8) << 56);
    }

    private static void trim(Map<Long, Entry> cache) {
        while (cache.size() > MAXIMUM_CACHE_ENTRIES) {
            cache.remove(cache.keySet().iterator().next());
        }
    }

    private record Entry(long expiresAt, EnvironmentalContext.WaterTarget target) {
    }
}
