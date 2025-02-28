package com.thunder.wildernessodysseyapi.NovaAPI.RenderEngine.Threading;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RenderSystemInterceptor {
    private static final ConcurrentLinkedQueue<Runnable> queuedRenderTasks = new ConcurrentLinkedQueue<>();

    public static void hookIntoRenderSystem() {
        RenderSystem.recordRenderCall(() -> {
            while (!queuedRenderTasks.isEmpty()) {
                queuedRenderTasks.poll().run();
            }
        });
    }

    public static void interceptRenderCall(Runnable task) {
        queuedRenderTasks.add(task);
    }
}
