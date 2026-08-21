package com.thunder.wildernessodysseyapi.worldgen.structure;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.worldgen.config.StructureConfig;
import com.thunder.wildernessodysseyapi.worldgen.spawn.CryoSpawnData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Denies hostile spawns inside the durable starter-bunker bounds.
 *
 * <p>The zone is stored with the world instead of in a dimension-keyed static map, so it survives
 * restarts and cannot leak between two integrated servers that both use the Overworld key.</p>
 */
public final class StarterStructureSpawnGuard {

    private StarterStructureSpawnGuard() {
    }

    /**
     * Compatibility no-op retained for older test/integration callers; protection is no longer cached globally.
     *
     * @deprecated world-owned SavedData needs no process-wide clear operation
     */
    @Deprecated(forRemoval = false)
    public static void clearAll() {
        // No process-wide state remains.
    }

    /** Registers a config-sized zone around an origin and persists it in the active world. */
    public static void registerSpawnDenyZone(ServerLevel level, BlockPos origin) {
        if (!StructureConfig.PREVENT_STARTER_STRUCTURE_HOSTILES.get() || level == null || origin == null) {
            return;
        }
        int radius = Math.max(1, StructureConfig.STARTER_STRUCTURE_SPAWN_DENY_RADIUS.get());
        int halfHeight = Math.max(1, StructureConfig.STARTER_STRUCTURE_SPAWN_DENY_HEIGHT.get());
        BlockPos min = origin.offset(-radius, -halfHeight, -radius);
        BlockPos max = origin.offset(radius, halfHeight, radius);
        registerSpawnDenyZone(level, new AABB(
                Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max).add(1.0D, 1.0D, 1.0D)).inflate(0.5D));
    }

    /** Persists exact bounds only after the starter structure has placed successfully. */
    public static void registerSpawnDenyZone(ServerLevel level, AABB bounds) {
        if (!StructureConfig.PREVENT_STARTER_STRUCTURE_HOSTILES.get() || level == null || bounds == null) {
            return;
        }
        CryoSpawnData.get(level).setStarterBunkerBounds(bounds);
        if (StructureConfig.DEBUG_LOG_PLACEMENTS.get()) {
            ModConstants.LOGGER.debug(
                    "[Starter Structure compat] Persisted hostile-spawn deny bounds {} in {}.",
                    bounds, level.dimension().location());
        }
    }

    /** Returns whether the world-owned deny zone contains the proposed spawn position. */
    public static boolean isDenied(ServerLevelAccessor levelAccessor, BlockPos pos) {
        if (levelAccessor == null || pos == null
                || !StructureConfig.PREVENT_STARTER_STRUCTURE_HOSTILES.get()) {
            return false;
        }
        ServerLevel level = levelAccessor.getLevel();
        AABB bounds = resolveBounds(level);
        return bounds != null && bounds.contains(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    /** Returns the durable placement marker rather than process-local cache state. */
    public static boolean hasPlacedBunker(ServerLevel level) {
        return level != null && CryoSpawnData.get(level).hasStarterBunkerPlaced();
    }

    // Older saves did not store exact bounds; reconstruct once around their persisted cryo tubes.
    private static AABB resolveBounds(ServerLevel level) {
        CryoSpawnData data = CryoSpawnData.get(level);
        AABB stored = data.getStarterBunkerBounds().orElse(null);
        if (stored != null || !data.hasStarterBunkerPlaced()) {
            return stored;
        }
        List<BlockPos> cryoPositions = data.getPositions();
        if (cryoPositions.isEmpty()) {
            return null;
        }
        int radius = Math.max(1, StructureConfig.STARTER_STRUCTURE_SPAWN_DENY_RADIUS.get());
        int halfHeight = Math.max(1, StructureConfig.STARTER_STRUCTURE_SPAWN_DENY_HEIGHT.get());
        BlockPos first = cryoPositions.getFirst();
        AABB reconstructed = new AABB(first).inflate(radius, halfHeight, radius);
        for (int index = 1; index < cryoPositions.size(); index++) {
            reconstructed = reconstructed.minmax(
                    new AABB(cryoPositions.get(index)).inflate(radius, halfHeight, radius));
        }
        data.setStarterBunkerBounds(reconstructed);
        return reconstructed;
    }
}
