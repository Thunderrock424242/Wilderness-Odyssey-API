package com.thunder.wildernessodysseyapi.RenderEngine.Threading;

import java.util.concurrent.*;

public class RenderThreadManager {
    private static final ExecutorService RENDER_THREAD = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Render-Thread");
        thread.setDaemon(true);
        return thread;
    });

    public static void execute(Runnable task) {
        RENDER_THREAD.submit(task);
    }

    public static void shutdown() {
        RENDER_THREAD.shutdown();
    }
}
