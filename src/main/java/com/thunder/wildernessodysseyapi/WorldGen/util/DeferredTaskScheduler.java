package com.thunder.wildernessodysseyapi.WorldGen.util;

import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Simple priority-queue-based scheduler for deferring structure placement work to future ticks.
 */
public final class DeferredTaskScheduler {
    private static final PriorityQueue<ScheduledTask> TASKS =
            new PriorityQueue<>(Comparator.comparingLong(ScheduledTask::runAtTick));
    private static long currentTick = 0L;

    private DeferredTaskScheduler() {
    }

    public static void schedule(ServerLevel level, Runnable action) {
        schedule(level, action, 1);
    }

    public static void schedule(ServerLevel level, Runnable action, int delayTicks) {
        Runnable enqueue = () -> {
            long runAt = currentTick + Math.max(1, delayTicks);
            TASKS.add(new ScheduledTask(runAt, action));
        };

        if (level.getServer().isSameThread()) {
            enqueue.run();
        } else {
            level.getServer().execute(enqueue);
        }
    }

    public static void tick() {
        currentTick++;
        while (!TASKS.isEmpty() && TASKS.peek().runAtTick() <= currentTick) {
            ScheduledTask task = TASKS.poll();
            if (task != null) {
                task.action().run();
            }
        }
    }

    public static void clear() {
        TASKS.clear();
        currentTick = 0L;
    }

    private record ScheduledTask(long runAtTick, Runnable action) { }
}
