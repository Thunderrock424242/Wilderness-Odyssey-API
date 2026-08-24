package com.thunder.wildernessodysseyapi.simulation.api;

import com.thunder.wildernessodysseyapi.environment.event.WorldDisturbanceType;
import com.thunder.wildernessodysseyapi.simulation.core.SimulationEngine;
import com.thunder.wildernessodysseyapi.simulation.debug.SimulationDebugSnapshot;
import com.thunder.wildernessodysseyapi.simulation.event.SimulationEvent;
import com.thunder.wildernessodysseyapi.simulation.event.SimulationEventDispatcher;
import com.thunder.wildernessodysseyapi.simulation.event.WorldSimulationEvent;
import com.thunder.wildernessodysseyapi.simulation.region.SimulationTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Stable public entry point for simulation registration, requests, and typed facts. */
public final class SimulationServices {
    private static final SimulationEngine ENGINE = SimulationEngine.get();

    private SimulationServices() {
    }

    /** Registers one optional orchestration participant by stable ID. */
    public static void register(SimulationSystem system) {
        ENGINE.registerSystem(system);
    }

    /** Registers one typed consequence listener in deterministic ID order. */
    public static <T extends SimulationEvent> void listen(
            ResourceLocation listenerId,
            Class<T> eventType,
            Consumer<T> listener
    ) {
        ENGINE.registerEventListener(listenerId, eventType, listener);
    }

    /** Requests bounded optional reevaluation of one existing regional cell. */
    public static boolean requestRegion(ServerLevel level, BlockPos position) {
        return ENGINE.requestRegion(level, position, SimulationTrigger.EXPLICIT_REQUEST);
    }

    /** Adapts a successful existing world disturbance into the shared typed event seam. */
    public static SimulationEventDispatcher.DispatchResult publishWorldDisturbance(
            ServerLevel level,
            BlockPos position,
            WorldDisturbanceType type,
            double intensity,
            int radiusBlocks,
            UUID sourceId,
            boolean plantDamageAllowed
    ) {
        return ENGINE.publish(level, new WorldSimulationEvent(
                level.dimension().location(),
                position,
                level.getGameTime(),
                type,
                intensity,
                radiusBlocks,
                Optional.ofNullable(sourceId),
                plantDamageAllowed
        ));
    }

    /** Returns immutable server diagnostics for commands and integrated-server F3. */
    public static SimulationDebugSnapshot debugSnapshot() {
        return ENGINE.debugSnapshot();
    }
}
