package com.thunder.wildernessodysseyapi.structureblock;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Owns bounded file-only post-processing for structure-block saves.
 *
 * <p>World and chunk access remains on the server thread. Tasks submitted here receive only
 * immutable paths and decoded-I/O limits, and the executor is recreated for each server
 * lifecycle so integrated worlds cannot leak queued work into the next world.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class StructureBlockIoExecutor {
    private static final int MAX_QUEUED_TASKS = 32;
    private static ThreadPoolExecutor executor;

    private StructureBlockIoExecutor() {
    }

    /** Creates the feature-owned worker when a server lifecycle begins. */
    @SubscribeEvent
    public static synchronized void onServerStarting(ServerStartingEvent event) {
        stopExecutor();
        executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUED_TASKS),
                runnable -> {
                    Thread thread = new Thread(runnable, "WildernessOdyssey-StructureBlock-IO");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /** Stops queued structure-file work before the current server releases its world paths. */
    @SubscribeEvent
    public static synchronized void onServerStopping(ServerStoppingEvent event) {
        stopExecutor();
    }

    /**
     * Submits one bounded file-only task without ever blocking the server thread.
     *
     * @return {@code true} when accepted, or {@code false} when the server is stopping or the queue is full
     */
    public static synchronized boolean trySubmit(Runnable task) {
        if (task == null || executor == null || executor.isShutdown()) {
            return false;
        }
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException ignored) {
            return false;
        }
    }

    private static void stopExecutor() {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        executor.getQueue().clear();
        executor = null;
    }
}
