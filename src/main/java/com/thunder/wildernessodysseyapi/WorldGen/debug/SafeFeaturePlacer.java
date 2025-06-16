package com.thunder.wildernessodysseyapi.WorldGen.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.core.registries.BuiltInRegistries;

public class SafeFeaturePlacer {
    public static boolean safePlace(PlacedFeature feature, WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos) {
        try {
            return feature.place(level, generator, random, pos);
        } catch (Exception e) {
            ResourceLocation key = BuiltInRegistries.PLACED_FEATURE.getKey(feature);
            if (level instanceof ServerLevel serverLevel) {
                WorldgenErrorTracker.logError("feature", key, pos, serverLevel, e);
            }
            return false;
        }
    }
}