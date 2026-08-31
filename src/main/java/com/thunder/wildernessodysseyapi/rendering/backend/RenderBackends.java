package com.thunder.wildernessodysseyapi.rendering.backend;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.rendering.backend.opengl.OpenGlRenderBackend;

import java.util.Locale;
import java.util.Objects;

/** Client render-thread registry for the active Minecraft graphics backend adapter. */
public final class RenderBackends {

    private static volatile Selection active = new Selection(
            RenderBackend.UNAVAILABLE,
            0L,
            new BackendStatus(State.UNDISCOVERED, 0, "Waiting for the client render context")
    );
    private static volatile long nextDiscoveryAttemptNanos;

    private RenderBackends() {
    }

    /**
     * Returns the active adapter, lazily discovering today's OpenGL context.
     * Future Minecraft renderer bootstraps may install another adapter through
     * {@link #install(RenderBackend)} without changing water or weather code.
     */
    @SuppressWarnings("removal") // OpenGL remains the required 1.21.1 fallback until the Vulkan port.
    public static RenderBackend current() {
        return selection().backend();
    }

    /** Returns one coherent adapter, generation, and discovery-status snapshot. */
    @SuppressWarnings("removal") // OpenGL remains the required 1.21.1 fallback until the Vulkan port.
    public static Selection selection() {
        Selection snapshot = active;
        if (snapshot.backend() != RenderBackend.UNAVAILABLE
                || snapshot.status().state() == State.RELEASED
                || !RenderSystem.isOnRenderThreadOrInit()) {
            return snapshot;
        }
        long now = System.nanoTime();
        if (now < nextDiscoveryAttemptNanos) {
            return snapshot;
        }
        synchronized (RenderBackends.class) {
            snapshot = active;
            if (snapshot.backend() != RenderBackend.UNAVAILABLE
                    || snapshot.status().state() == State.RELEASED) {
                return active;
            }
            try {
                RenderBackend discovered = OpenGlRenderBackend.capture();
                active = ready(discovered, snapshot.generation() + 1L,
                        snapshot.status().discoveryAttempts(), "OpenGL adapter discovered");
                nextDiscoveryAttemptNanos = 0L;
            } catch (RuntimeException | LinkageError unavailable) {
                nextDiscoveryAttemptNanos = now + 1_000_000_000L;
                int attempts = snapshot.status().discoveryAttempts() + 1;
                active = new Selection(
                        RenderBackend.UNAVAILABLE,
                        snapshot.generation(),
                        new BackendStatus(
                                State.DEGRADED,
                                attempts,
                                "OpenGL discovery failed: " + unavailable.getClass().getSimpleName()
                        )
                );
            }
            return active;
        }
    }

    /** Installs the adapter selected by Minecraft's renderer bootstrap. */
    public static synchronized void install(RenderBackend backend) {
        RenderSystem.assertOnRenderThreadOrInit();
        RenderBackend replacement = Objects.requireNonNull(backend, "backend");
        if (replacement == RenderBackend.UNAVAILABLE) {
            throw new IllegalArgumentException("Use release() or resetDiscovery() for an unavailable backend");
        }
        Selection previous = active;
        if (previous.backend() != replacement) {
            close(previous.backend(), "replacing render backend");
        }
        active = ready(
                replacement,
                previous.generation() + 1L,
                previous.status().discoveryAttempts(),
                "Adapter installed by renderer bootstrap"
        );
        nextDiscoveryAttemptNanos = 0L;
    }

    /** Releases the active adapter while its graphics context is still valid. */
    public static synchronized void release() {
        RenderSystem.assertOnRenderThreadOrInit();
        Selection previous = active;
        close(previous.backend(), "releasing render backend");
        active = new Selection(
                RenderBackend.UNAVAILABLE,
                previous.generation() + 1L,
                new BackendStatus(State.RELEASED, previous.status().discoveryAttempts(), "Backend released")
        );
        nextDiscoveryAttemptNanos = 0L;
    }

    /** Clears a stale context and permits lazy discovery of its replacement. */
    public static synchronized void resetDiscovery() {
        RenderSystem.assertOnRenderThreadOrInit();
        Selection previous = active;
        close(previous.backend(), "resetting render backend discovery");
        active = new Selection(
                RenderBackend.UNAVAILABLE,
                previous.generation() + 1L,
                new BackendStatus(State.UNDISCOVERED, 0, "Waiting for a replacement render context")
        );
        nextDiscoveryAttemptNanos = 0L;
    }

    /** Returns the latest discovery or lifecycle status without triggering discovery. */
    public static BackendStatus status() {
        return active.status();
    }

    private static Selection ready(RenderBackend backend, long generation, int attempts, String detail) {
        return new Selection(
                backend,
                generation,
                new BackendStatus(
                        State.READY,
                        attempts,
                        detail + ": " + backend.capabilities().api().name().toLowerCase(Locale.ROOT)
                )
        );
    }

    private static void close(RenderBackend backend, String operation) {
        if (backend == RenderBackend.UNAVAILABLE) {
            return;
        }
        try {
            backend.close();
        } catch (RuntimeException failure) {
            ModConstants.LOGGER.warn("Unable to close the active adapter while {}", operation, failure);
        }
    }

    /** One atomically published active-backend selection. */
    public record Selection(RenderBackend backend, long generation, BackendStatus status) {
        public Selection {
            backend = backend == null ? RenderBackend.UNAVAILABLE : backend;
            generation = Math.max(0L, generation);
            status = status == null
                    ? new BackendStatus(State.UNDISCOVERED, 0, "Backend status unavailable")
                    : status;
        }
    }

    /** Diagnostic state for backend discovery and lifecycle transitions. */
    public record BackendStatus(State state, int discoveryAttempts, String detail) {
        public BackendStatus {
            state = state == null ? State.UNDISCOVERED : state;
            discoveryAttempts = Math.max(0, discoveryAttempts);
            detail = detail == null || detail.isBlank() ? "No backend detail available" : detail;
        }
    }

    public enum State {
        UNDISCOVERED,
        READY,
        DEGRADED,
        RELEASED
    }
}
