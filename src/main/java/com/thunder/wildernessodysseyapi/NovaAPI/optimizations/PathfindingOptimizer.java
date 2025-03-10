package com.thunder.wildernessodysseyapi.NovaAPI.optimizations;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@EventBusSubscriber
public class PathfindingOptimizer {
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(4); // Background thread pool

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingUpdate(LivingEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Mob mob)) return; // Only optimize AI for mobs

        // Process AI pathfinding optimizations asynchronously
        threadPool.submit(() -> optimizePathfinding(mob));
    }

    private static void optimizePathfinding(Mob mob) {
        PathNavigation navigator = mob.getNavigation();
        if (navigator == null || !navigator.isDone()) return; // Skip if AI isn't actively navigating

        // Example: Reduce unnecessary recalculations
        navigator.setCanFloat(false); // Prevent mobs from floating randomly
        navigator.setSpeedModifier(1.2f); // Slightly boost pathfinding efficiency

        NovaAPI.LOGGER.debug("[Nova API] Optimized pathfinding for " + mob.getName().getString());
    }
}