package com.thunder.wildernessodysseyapi.environment.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Public read-only boundary for combined regional environment queries. */
@FunctionalInterface
public interface EnvironmentQuery {

    /** Returns one short-lived immutable snapshot without exposing subsystem storage. */
    RegionalEnvironmentSnapshot sample(ServerLevel level, BlockPos position);
}
