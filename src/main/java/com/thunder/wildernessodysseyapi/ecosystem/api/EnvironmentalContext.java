package com.thunder.wildernessodysseyapi.ecosystem.api;

import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;

import java.util.Optional;

/**
 * Immutable environmental snapshot used for one animal decision pass.
 *
 * <p>It contains only read-only service results. Expensive searches are
 * staggered and cached before the controller receives this object.</p>
 */
public record EnvironmentalContext(
        ServerLevel level,
        PathfinderMob animal,
        SpeciesBehaviorProfile profile,
        long gameTime,
        long dayTime,
        ResourceLocation biome,
        WeatherSample weather,
        WatershedConditions watershed,
        boolean exposedToSky,
        double foodAvailability,
        Optional<WaterTarget> water,
        Optional<ShelterTarget> shelter,
        Optional<Threat> threat,
        Optional<HerdCenter> herd,
        Optional<PreyTarget> preyTarget,
        Optional<Disturbance> disturbance
) {

    /** Retains the original context shape for external behavior controllers. */
    public EnvironmentalContext(
            ServerLevel level,
            PathfinderMob animal,
            SpeciesBehaviorProfile profile,
            long gameTime,
            long dayTime,
            ResourceLocation biome,
            WeatherSample weather,
            boolean exposedToSky,
            double foodAvailability,
            Optional<WaterTarget> water,
            Optional<ShelterTarget> shelter,
            Optional<Threat> threat,
            Optional<HerdCenter> herd,
            Optional<PreyTarget> preyTarget,
            Optional<Disturbance> disturbance
    ) {
        this(
                level, animal, profile, gameTime, dayTime, biome, weather,
                WatershedConditions.NONE, exposedToSky, foodAvailability, water,
                shelter, threat, herd, preyTarget, disturbance
        );
    }

    public EnvironmentalContext {
        watershed = watershed == null ? WatershedConditions.NONE : watershed;
    }

    /** Safe dry approach adjacent to a detected water position. */
    public record WaterTarget(
            BlockPos waterPosition,
            BlockPos approachPosition,
            double depth,
            float floodRisk,
            float currentStrength,
            float clarity
    ) {
        /** Retains the original target shape for third-party locators. */
        public WaterTarget(BlockPos waterPosition, BlockPos approachPosition, double depth) {
            this(waterPosition, approachPosition, depth, 0.0f, 0.0f, 1.0f);
        }

        public WaterTarget {
            floodRisk = unit(floodRisk);
            currentStrength = Math.max(0.0f, Float.isFinite(currentStrength) ? currentStrength : 0.0f);
            clarity = unit(clarity);
        }

        private static float unit(float value) {
            return Math.max(0.0f, Math.min(1.0f, Float.isFinite(value) ? value : 0.0f));
        }
    }

    /** Standable position protected from the sky. */
    public record ShelterTarget(BlockPos position, int coverBlocks) {
    }

    /** Currently visible or temporarily remembered source of danger. */
    public record Threat(BlockPos position, java.util.UUID entityId, double distanceSquared, long expiresAt) {
    }

    /** Same-species centroid and group size used for low-priority regrouping. */
    public record HerdCenter(BlockPos position, int members, double distanceSquared) {
    }

    /** Adult prey selected from a population that passed the configured safeguard. */
    public record PreyTarget(java.util.UUID entityId, BlockPos position, int adultPopulation) {
    }

    /** Recent player or animal activity retained in a bounded per-level history. */
    public record Disturbance(BlockPos position, java.util.UUID sourceId, double intensity, long createdAt) {
    }
}
