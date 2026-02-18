package com.thunder.wildernessodysseyapi.watersystem.ocean.tide;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.Map;

final class TideAmplitudeHelper {
    private TideAmplitudeHelper() {
    }

    static double getLocalAmplitude(ServerLevel level, BlockPos pos, TideConfig.TideConfigValues config) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        double amplitude;
        if (biomeHolder.is(BiomeTags.IS_OCEAN)) {
            amplitude = config.oceanAmplitudeBlocks();
        } else if (biomeHolder.is(BiomeTags.IS_RIVER)) {
            amplitude = config.riverAmplitudeBlocks() * computeRiverOceanFactor(level, pos, config);
        } else {
            amplitude = 0.0D;
        }
        amplitude *= TideAstronomy.getMoonPhaseAmplitudeFactor(level);
        amplitude *= getDimensionMultiplier(level, config.dimensionAmplitudeOverrides());
        amplitude *= getBiomeMultiplier(biomeHolder, config.biomeAmplitudeOverrides());
        return amplitude;
    }

    private static double getDimensionMultiplier(ServerLevel level, Map<String, Double> overrides) {
        if (overrides.isEmpty()) {
            return 1.0D;
        }
        return overrides.getOrDefault(level.dimension().location().toString(), 1.0D);
    }

    private static double getBiomeMultiplier(Holder<Biome> biomeHolder, Map<String, Double> overrides) {
        if (overrides.isEmpty()) {
            return 1.0D;
        }
        return biomeHolder.unwrapKey()
                .map(resourceKey -> overrides.getOrDefault(resourceKey.location().toString(), 1.0D))
                .orElse(1.0D);
    }

    private static double computeRiverOceanFactor(ServerLevel level, BlockPos pos, TideConfig.TideConfigValues config) {
        int radius = config.riverOceanSearchRadius();
        if (radius <= 0) {
            return 1.0D;
        }
        int step = Math.max(4, config.riverOceanSearchStep());
        int radiusSq = radius * radius;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int closestSq = Integer.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                int distanceSq = dx * dx + dz * dz;
                if (distanceSq > radiusSq) {
                    continue;
                }
                cursor.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
                Holder<Biome> nearbyBiome = level.getBiome(cursor);
                if (nearbyBiome.is(BiomeTags.IS_OCEAN)) {
                    closestSq = Math.min(closestSq, distanceSq);
                }
            }
        }
        if (closestSq == Integer.MAX_VALUE) {
            return config.riverInlandMinFactor();
        }
        double distance = Math.sqrt(closestSq);
        double factor = 1.0D - (distance / radius);
        return Math.max(config.riverInlandMinFactor(), factor);
    }
}
