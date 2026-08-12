package com.thunder.wildernessodysseyapi.developmentstudio.inspection;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Server-owned block target passed to block inspection providers. */
public record StudioBlockTarget(ServerLevel level, BlockPos position) {
    public StudioBlockTarget {
        if (level == null || position == null) {
            throw new IllegalArgumentException("Block inspection target must be complete");
        }
        position = position.immutable();
    }
}
