package com.thunder.wildernessodysseyapi.rendering.backend;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.rendering.backend.opengl.OpenGlRenderBackend;

import java.util.Objects;

/** Client render-thread registry for the active Minecraft graphics backend adapter. */
public final class RenderBackends {

    private static volatile RenderBackend active = RenderBackend.UNAVAILABLE;
    private static volatile long nextDiscoveryAttemptNanos;

    private RenderBackends() {
    }

    /**
     * Returns the active adapter, lazily discovering today's OpenGL context.
     * Future Minecraft renderer bootstraps may install another adapter through
     * {@link #install(RenderBackend)} without changing water or weather code.
     */
    public static RenderBackend current() {
        RenderBackend backend = active;
        if (backend != RenderBackend.UNAVAILABLE || !RenderSystem.isOnRenderThreadOrInit()) {
            return backend;
        }
        long now = System.nanoTime();
        if (now < nextDiscoveryAttemptNanos) {
            return backend;
        }
        synchronized (RenderBackends.class) {
            if (active != RenderBackend.UNAVAILABLE) {
                return active;
            }
            try {
                active = OpenGlRenderBackend.capture();
            } catch (RuntimeException | LinkageError unavailable) {
                nextDiscoveryAttemptNanos = now + 1_000_000_000L;
            }
            return active;
        }
    }

    /** Installs the adapter selected by Minecraft's renderer bootstrap. */
    public static synchronized void install(RenderBackend backend) {
        active = Objects.requireNonNull(backend, "backend");
        nextDiscoveryAttemptNanos = 0L;
    }
}
