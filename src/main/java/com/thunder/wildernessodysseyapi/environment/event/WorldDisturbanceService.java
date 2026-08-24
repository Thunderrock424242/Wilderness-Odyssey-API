package com.thunder.wildernessodysseyapi.environment.event;

import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemSimulationManager;
import com.thunder.wildernessodysseyapi.environment.api.EnvironmentDimensionProfile;
import com.thunder.wildernessodysseyapi.environment.api.EnvironmentServices;
import com.thunder.wildernessodysseyapi.simulation.api.SimulationServices;
import com.thunder.wildernessodysseyapi.vegetation.api.PlantDisturbance;
import com.thunder.wildernessodysseyapi.vegetation.api.ReactiveVegetationServices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Publishes one successful world event to bounded downstream consumers.
 *
 * <p>The service records facts only after the owning hazard succeeds. It never
 * starts weather, changes water, moves animals, or mutates plants directly.</p>
 */
public final class WorldDisturbanceService {

    private WorldDisturbanceService() {
    }

    /** Publishes a configured-intensity event to ecology, vegetation, and cache invalidation. */
    public static void publish(
            ServerLevel level,
            BlockPos center,
            WorldDisturbanceType type,
            int radiusBlocks,
            UUID sourceId,
            boolean allowPlantDamage
    ) {
        if (level == null || center == null || type == null) {
            return;
        }
        publish(
                level,
                center,
                type,
                configuredIntensity(type),
                radiusBlocks,
                sourceId,
                allowPlantDamage
        );
    }

    /** Publishes an explicitly scaled event while retaining the same ownership boundaries. */
    public static void publish(
            ServerLevel level,
            BlockPos center,
            WorldDisturbanceType type,
            double intensity,
            int radiusBlocks,
            UUID sourceId,
            boolean allowPlantDamage
    ) {
        if (level == null || center == null || type == null) {
            return;
        }
        double bounded = Math.max(0.0, Math.min(1.0,
                Double.isFinite(intensity) ? intensity : 0.0));
        if (bounded <= 0.0) {
            return;
        }
        BlockPos position = center.immutable();
        int radius = Math.max(0, Math.min(256, radiusBlocks));
        EnvironmentDimensionProfile profile = EnvironmentDimensionProfile.forLevel(level);

        if (profile.ecosystem() && EcosystemConfig.ENABLED.get()) {
            EcosystemServices.disturbances().record(
                    level,
                    position,
                    sourceId,
                    bounded,
                    type.ecosystemSource()
            );
            EcosystemSimulationManager.get().requestRegionalUpdate(level, position);
        }
        if (profile.reactiveVegetation()) {
            ReactiveVegetationServices.recordDisturbance(
                    level,
                    PlantDisturbance.lasting(
                            type.plantSource(),
                            position,
                            radius,
                            bounded,
                            level.getGameTime(),
                            type.plantDurationTicks(),
                            allowPlantDamage
                    )
            );
        }
        EnvironmentServices.invalidate(level, position, radius);
        SimulationServices.publishWorldDisturbance(
                level,
                position,
                type,
                bounded,
                radius,
                sourceId,
                allowPlantDamage
        );
    }

    private static double configuredIntensity(WorldDisturbanceType type) {
        return switch (type) {
            case LIGHTNING -> EcosystemConfig.LIGHTNING_DISTURBANCE.get();
            case SEVERE_WEATHER -> EcosystemConfig.SEVERE_WEATHER_DISTURBANCE.get();
            case WILDFIRE -> EcosystemConfig.FIRE_DISTURBANCE.get();
            case FLOOD -> EcosystemConfig.FLOOD_DISTURBANCE.get();
            case DROUGHT -> EcosystemConfig.DROUGHT_DISTURBANCE.get();
            case METEOR_IMPACT -> EcosystemConfig.METEOR_DISTURBANCE.get();
            case RADIATION -> EcosystemConfig.RADIATION_DISTURBANCE.get();
            case RIFTFALL -> EcosystemConfig.RIFTFALL_DISTURBANCE.get();
        };
    }
}
