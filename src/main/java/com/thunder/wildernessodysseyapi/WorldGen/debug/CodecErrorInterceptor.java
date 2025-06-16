package com.thunder.wildernessodysseyapi.WorldGen.debug;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public class CodecErrorInterceptor {
    public static <T> Codec<T> wrapCodec(ResourceLocation id, Codec<T> originalCodec, ServerLevel level) {
        return originalCodec.comapFlatMap(
                t -> {
                    try {
                        return DataResult.success(t);
                    } catch (Exception e) {
                        WorldgenErrorTracker.logError("codec", id, level.getSharedSpawnPos(), level, e);
                        return DataResult.error(() -> "Failed to decode codec for " + id + ": " + e.getMessage());
                    }
                },
                t -> t
        );
    }
}