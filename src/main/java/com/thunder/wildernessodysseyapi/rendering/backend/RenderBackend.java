package com.thunder.wildernessodysseyapi.rendering.backend;

import com.thunder.wildernessodysseyapi.rendering.GPUCapabilities;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;

import java.util.Optional;

/**
 * Narrow boundary around backend operations used by Wilderness renderers.
 *
 * <p>This is not a replacement renderer. Minecraft still owns command
 * submission, resources, and render-pass order. A future backend adapter only
 * needs to supply the small capabilities and timing operations requested here.</p>
 */
public interface RenderBackend extends AutoCloseable {

    RenderBackend UNAVAILABLE = new RenderBackend() {
        @Override
        public GPUCapabilities capabilities() {
            return GPUCapabilities.UNAVAILABLE;
        }

        @Override
        public GpuTimer createGpuTimer(int bufferedFrames) {
            return GpuTimer.UNAVAILABLE;
        }

        @Override
        public boolean isShaderUsable(ShaderInstance shader) {
            return shader != null;
        }

        @Override
        public RenderStateSnapshot captureRenderState() {
            return RenderStateSnapshot.DEFAULT;
        }

        @Override
        public RenderStateScope captureRenderStateScope() {
            return RenderStateScope.UNAVAILABLE;
        }
    };

    /** Returns the immutable facts captured once for this backend/context. */
    GPUCapabilities capabilities();

    /** Creates a non-blocking timer when the backend supports timestamp queries. */
    GpuTimer createGpuTimer(int bufferedFrames);

    /** Validates one shader object supplied and owned by Minecraft. */
    boolean isShaderUsable(ShaderInstance shader);

    /** Captures the small state subset a scoped fullscreen pass must restore. */
    RenderStateSnapshot captureRenderState();

    /**
     * Captures render state for a bounded pass and restores it when closed.
     *
     * <p>Callers should prefer this scope over restoring snapshot fields
     * themselves. Backend implementations may need to preserve additional
     * native state that is deliberately absent from the common snapshot.</p>
     */
    default RenderStateScope captureRenderStateScope() {
        RenderStateSnapshot snapshot = captureRenderState();
        return snapshot::restore;
    }

    /** Releases resources created and retained by this backend adapter. */
    @Override
    default void close() {
    }

    record RenderStateSnapshot(boolean depthTestEnabled, boolean depthWriteEnabled, boolean blendEnabled) {
        public static final RenderStateSnapshot DEFAULT = new RenderStateSnapshot(true, true, false);

        /** Restores the backend-neutral state represented by this snapshot. */
        public void restore() {
            RenderSystem.depthMask(depthWriteEnabled);
            if (depthTestEnabled) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (blendEnabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
        }
    }

    /** Idempotent restoration handle for one bounded render-state mutation. */
    interface RenderStateScope extends AutoCloseable {
        RenderStateScope UNAVAILABLE = () -> {
        };

        @Override
        void close();
    }

    /** One newly completed, non-blocking GPU timing sample. */
    record GpuTimingSample(long sequence, long sourceFrame, long durationNanos) {
        public GpuTimingSample {
            sequence = Math.max(0L, sequence);
            sourceFrame = Math.max(-1L, sourceFrame);
            durationNanos = Math.max(0L, durationNanos);
        }
    }

    /** Backend-owned asynchronous timing scope. */
    interface GpuTimer extends AutoCloseable {
        GpuTimer UNAVAILABLE = new GpuTimer() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public void begin() {
            }

            @Override
            public void end() {
            }

            @Override
            public long latestNanos() {
                return 0L;
            }

            @Override
            public void close() {
            }
        };

        boolean available();

        void begin();

        /** Begins a timing scope associated with one logical render frame. */
        default void begin(long sourceFrame) {
            begin();
        }

        void end();

        /**
         * Returns the latest duration for legacy diagnostics.
         *
         * <p>The value may be older than the current frame. New code should use
         * {@link #poll()} when sample freshness matters.</p>
         */
        long latestNanos();

        /** Returns one newly completed sample at most once without waiting. */
        default Optional<GpuTimingSample> poll() {
            return Optional.empty();
        }

        @Override
        void close();
    }
}
