package com.thunder.wildernessodysseyapi.NovaAPI.ai;

import net.minecraft.world.entity.Mob;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PathfindingManager {
    private static final ExecutorService pathfindingExecutor = Executors.newFixedThreadPool(4);

    public static void calculatePath(Mob mob, Runnable pathTask) {
        pathfindingExecutor.submit(() -> {
            try {
                pathTask.run();
                System.out.println("[NovaAPI] Pathfinding calculated for: " + mob.getName().getString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}