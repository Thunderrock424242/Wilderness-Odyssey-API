package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.configurable.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.server.level.ServerLevel;

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

    public static void registerSpawnDenyZone(ServerLevel level, BlockPos origin) {
        if (!StructureConfig.PREVENT_STARTER_STRUCTURE_HOSTILES.get()) {
            return;
        }
        if (level == null || origin == null) {
            return;
        }

        int radius = Math.max(1, StructureConfig.STARTER_STRUCTURE_SPAWN_DENY_RADIUS.get());
        int halfHeight = Math.max(1, StructureConfig.STARTER_STRUCTURE_SPAWN_DENY_HEIGHT.get());

        SpawnDenyZone zone = new SpawnDenyZone(origin.immutable(), radius, halfHeight);
        ZONES.computeIfAbsent(level.dimension(), key -> new CopyOnWriteArrayList<>()).add(zone);

        if (StructureConfig.DEBUG_LOG_PLACEMENTS.get()) {
            ModConstants.LOGGER.debug(
                    "[Starter Structure compat] Blocking hostile spawns within {}x{}x{} around bunker at {} in {}.",
                    radius * 2 + 1, halfHeight * 2 + 1, radius * 2 + 1, origin, level.dimension().location());
        }
    }

    public static boolean isDenied(LevelAccessor level, BlockPos pos) {
        List<SpawnDenyZone> zones = ZONES.get(level.dimension());
        if (zones == null || zones.isEmpty()) {
            return false;
        }

        for (SpawnDenyZone zone : zones) {
            if (zone.contains(pos)) {
                return true;
            }
        }

        return false;
    }

    private record SpawnDenyZone(BlockPos center, int radius, int halfHeight) {
        boolean contains(BlockPos pos) {
            int dx = Math.abs(pos.getX() - center.getX());
            int dz = Math.abs(pos.getZ() - center.getZ());
            int dy = Math.abs(pos.getY() - center.getY());
            return dx <= radius && dz <= radius && dy <= halfHeight;
        }
    }
}
