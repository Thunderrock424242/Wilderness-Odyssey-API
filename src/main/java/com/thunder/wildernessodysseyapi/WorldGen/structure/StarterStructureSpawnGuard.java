package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.configurable.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks recently placed starter bunkers and denies hostile mob spawns inside them.
 */
public final class StarterStructureSpawnGuard {
    private static final Map<ResourceKey<Level>, List<SpawnDenyZone>> ZONES = new ConcurrentHashMap<>();

    private StarterStructureSpawnGuard() {
    }

    /** Clears all tracked spawn deny zones. Primarily used by automated tests. */
    public static void clearAll() {
        ZONES.clear();
    }

    public static void registerSpawnDenyZone(ServerLevel level, BlockPos origin) {
        if (!StructureConfig.PREVENT_STARTER_STRUCTURE_HOSTILES.get()) {
            return;
        }
        if (level == null || origin == null) {
            return;
        }

        int radius = Math.max(1, StructureConfig.STARTER_STRUCTURE_SPAWN_DENY_RADIUS.get());
        int halfHeight = Math.max(1, StructureConfig.STARTER_STRUCTURE_SPAWN_DENY_HEIGHT.get());
        BlockPos min = origin.offset(-radius, -halfHeight, -radius);
        BlockPos max = origin.offset(radius, halfHeight, radius);
        Vec3 minCorner = Vec3.atLowerCornerOf(min);
        Vec3 maxCorner = Vec3.atLowerCornerOf(max).add(1.0D, 1.0D, 1.0D);
        registerSpawnDenyZone(level, new AABB(minCorner, maxCorner).inflate(0.5D));
    }

    public static void registerSpawnDenyZone(ServerLevel level, AABB bounds) {
        if (!StructureConfig.PREVENT_STARTER_STRUCTURE_HOSTILES.get()) {
            return;
        }
        if (level == null || bounds == null) {
            return;
        }

        SpawnDenyZone zone = new SpawnDenyZone(bounds);
        ZONES.computeIfAbsent(level.dimension(), key -> new CopyOnWriteArrayList<>()).add(zone);

        if (StructureConfig.DEBUG_LOG_PLACEMENTS.get()) {
            ModConstants.LOGGER.debug(
                    "[Starter Structure compat] Blocking hostile spawns within {}x{}x{} around bunker in {}.",
                    Math.round(bounds.getXsize()),
                    Math.round(bounds.getYsize()),
                    Math.round(bounds.getZsize()),
                    level.dimension().location());
        }
    }

    public static boolean isDenied(ServerLevelAccessor level, BlockPos pos) {
        if (level == null || pos == null) return false;

        ResourceKey<Level> dim = level.getLevel().dimension();
        List<SpawnDenyZone> zones = ZONES.get(dim);

        if (zones == null || zones.isEmpty()) return false;

        for (SpawnDenyZone zone : zones) {
            if (zone.contains(pos)) return true;
        }
        return false;
    }

    private record SpawnDenyZone(AABB bounds) {
        boolean contains(BlockPos pos) {
            return bounds.contains(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        }
    }
}
