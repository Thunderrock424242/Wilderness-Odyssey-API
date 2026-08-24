package com.thunder.wildernessodysseyapi.simulation.event;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.environment.event.WorldDisturbanceType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Typed simulation notification adapted from the existing world-disturbance service. */
public record WorldSimulationEvent(
        ResourceLocation dimension,
        BlockPos position,
        long gameTime,
        WorldDisturbanceType disturbanceType,
        double intensity,
        int radiusBlocks,
        Optional<UUID> sourceId,
        boolean plantDamageAllowed
) implements SimulationEvent {
    public WorldSimulationEvent {
        dimension = Objects.requireNonNull(dimension, "Dimension is required");
        position = Objects.requireNonNull(position, "Event position is required").immutable();
        gameTime = Math.max(0L, gameTime);
        disturbanceType = Objects.requireNonNull(disturbanceType, "Disturbance type is required");
        intensity = Double.isFinite(intensity) ? Math.max(0.0D, Math.min(1.0D, intensity)) : 0.0D;
        radiusBlocks = Math.max(0, Math.min(256, radiusBlocks));
        sourceId = sourceId == null ? Optional.empty() : sourceId;
    }

    /** Builds the namespaced event ID without introducing another event-type registry. */
    @Override
    public ResourceLocation type() {
        return ResourceLocation.fromNamespaceAndPath(
                ModConstants.MOD_ID,
                "world_disturbance/" + disturbanceType.name().toLowerCase(Locale.ROOT)
        );
    }
}
