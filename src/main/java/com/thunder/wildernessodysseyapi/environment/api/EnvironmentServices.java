package com.thunder.wildernessodysseyapi.environment.api;

import com.thunder.wildernessodysseyapi.environment.simulation.RegionalEnvironmentManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Stable public door into the shared regional environment integration layer. */
public final class EnvironmentServices {

    private static final RegionalEnvironmentManager MANAGER = RegionalEnvironmentManager.get();

    private EnvironmentServices() {
    }

    /** Returns the process-wide read-only environment query. */
    public static EnvironmentQuery query() {
        return MANAGER;
    }

    /** Invalidates cached conclusions after an authoritative world change succeeds. */
    public static void invalidate(ServerLevel level, BlockPos center, int radiusBlocks) {
        MANAGER.invalidate(level, center, radiusBlocks);
    }

    /** Releases one dimension's ephemeral regional cache. */
    public static void clear(ServerLevel level) {
        MANAGER.clear(level);
    }

    /** Releases all ephemeral caches during server shutdown. */
    public static void clearAll() {
        MANAGER.clearAll();
    }
}
