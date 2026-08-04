package com.thunder.wildernessodysseyapi.ecosystem.api;

import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
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
        boolean exposedToSky,
        double foodAvailability,
        Optional<WaterTarget> water,
        Optional<ShelterTarget> shelter,
        Optional<Threat> threat,
        Optional<HerdCenter> herd,
        Optional<PreyTarget> preyTarget,
        Optional<Disturbance> disturbance
) {

    /** Safe dry approach adjacent to a detected water position. */
    public record WaterTarget(BlockPos waterPosition, BlockPos approachPosition, double depth) {
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
