package com.thunder.wildernessodysseyapi.rendering.backend;

import com.thunder.wildernessodysseyapi.rendering.GPUCapabilities;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Narrow boundary around backend operations used by Wilderness renderers.
 *
 * <p>This is not a replacement renderer. Minecraft still owns command
 * submission, resources, and render-pass order. A future backend adapter only
 * needs to supply the small capabilities and timing operations requested here.</p>
 */
public interface RenderBackend {

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
    };

    /** Returns the immutable facts captured once for this backend/context. */
    GPUCapabilities capabilities();

    /** Creates a non-blocking timer when the backend supports timestamp queries. */
    GpuTimer createGpuTimer(int bufferedFrames);

    /** Validates one shader object supplied and owned by Minecraft. */
    boolean isShaderUsable(ShaderInstance shader);

    /** Captures the small state subset a scoped fullscreen pass must restore. */
    RenderStateSnapshot captureRenderState();

    record RenderStateSnapshot(boolean depthTestEnabled, boolean depthWriteEnabled, boolean blendEnabled) {
        public static final RenderStateSnapshot DEFAULT = new RenderStateSnapshot(true, true, false);
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

        void end();

        long latestNanos();

        @Override
        void close();
    }
}
