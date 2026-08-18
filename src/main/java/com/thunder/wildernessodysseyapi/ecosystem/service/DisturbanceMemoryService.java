package com.thunder.wildernessodysseyapi.ecosystem.service;

import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.memory.DisturbanceSource;
import com.thunder.wildernessodysseyapi.ecosystem.memory.EnvironmentalMemoryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/**
 * Ecosystem compatibility facade over persistent regional environmental memory.
 *
 * <p>Existing ecosystem services retain this narrow dependency while the
 * authoritative, API-facing storage lives in {@link EnvironmentalMemoryManager}.
 * The facade owns no map, tick loop, or duplicate world state.</p>
 */
public final class DisturbanceMemoryService {

    /** Records a generic disturbance for callers using the original service boundary. */
    public void record(ServerLevel level, BlockPos position, UUID sourceId, double intensity, long gameTime) {
        record(level, position, sourceId, intensity, DisturbanceSource.OTHER);
    }

    /** Records a source-classified disturbance in the authoritative regional ledger. */
    public void record(
            ServerLevel level,
            BlockPos position,
            UUID sourceId,
            double intensity,
            DisturbanceSource source
    ) {
        if (Double.isFinite(intensity) && intensity > 0.0) {
            EnvironmentalMemoryManager.addDisturbance(level, position, intensity, source, sourceId);
        }
    }

    /** Returns the strongest lazily decayed regional disturbance in a bounded radius. */
    public Optional<EnvironmentalContext.Disturbance> nearest(
            ServerLevel level,
            BlockPos position,
            int radius,
            long gameTime
    ) {
        return EnvironmentalMemoryManager.findStrongestDisturbance(level, position, radius);
    }

    /** Level unload does not clear Minecraft-owned persistent SavedData. */
    public void clear(ServerLevel level) {
        // Intentionally empty: the level DataStorage owns persistence and lifecycle.
    }
}
