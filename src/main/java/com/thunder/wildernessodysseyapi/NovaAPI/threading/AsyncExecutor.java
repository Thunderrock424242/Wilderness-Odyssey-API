package com.thunder.wildernessodysseyapi.NovaAPI.threading;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncExecutor {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    /**
     * Runs a task asynchronously.
     * @param task The task to run on a separate thread.
     */
    public static void runAsync(Runnable task) {
        EXECUTOR.execute(task);
    }

    /**
     * Gracefully shuts down the async executor.
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
    }
}