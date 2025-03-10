package com.thunder.wildernessodysseyapi.NovaAPI.optimizations;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@EventBusSubscriber
public class PathfindingOptimizer {
    private static final ExecutorService pathfindingThreadPool = Executors.newFixedThreadPool(4); // More threads for AI

    @SubscribeEvent
    public static void onEntityTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return; // Only run on the server

        ServerLevel world = (ServerLevel) entity.level();
        PathNavigation navigation = entity.getNavigation();

        if (navigation.isDone()) return; // Ignore if entity is not moving

        pathfindingThreadPool.submit(() -> {
            try {
                navigation.tick();
                NovaAPI.LOGGER.debug("[Nova API] Optimized AI pathfinding for " + entity.getName().getString());
            } catch (Exception e) {
                NovaAPI.LOGGER.error("[Nova API] AI Pathfinding Error: " + e.getMessage());
            }
        });
    }
}