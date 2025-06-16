package com.thunder.wildernessodysseyapi.WorldGen.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;

public class WorldgenErrorTracker {

    public static void logError(String type, ResourceLocation id, BlockPos pos, ServerLevel level, Exception e) {
        String msg = String.format("[WorldgenError] [%s] Failed to generate %s at %s in %s: %s",
                type.toUpperCase(), id, pos, level.dimension().location(), e.getMessage());
        LOGGER.error(msg, e);
    }
}