package com.thunder.wildernessodysseyapi.gpuprofiler.client;

/**
 * Read-only facade for the GPU identity and memory probe shared by client systems.
 *
 * <p>Callers must invoke {@link #capture()} on Minecraft's render thread after
 * the OpenGL context exists. Unsupported driver memory extensions return
 * {@code -1} without affecting rendering.</p>
 */
public final class GpuHardwareProbe {

    private GpuHardwareProbe() {
    }

    /** Captures one immutable GPU identity and best-effort memory snapshot. */
    public static Snapshot capture() {
        GpuMemoryProbe.GpuInfo info = GpuMemoryProbe.gpuInfo();
        GpuMemoryProbe.Sample memory = GpuMemoryProbe.sample(0L);
        long reportedMemory = memory.totalBytes() > 0L
                ? memory.totalBytes()
                : memory.availableBytes();
        return new Snapshot(
                info.vendor(),
                info.renderer(),
                info.version(),
                reportedMemory,
                memory.provider()
        );
    }

    /** Immutable information available without starting a VRAM profiling session. */
    public record Snapshot(
            String vendor,
            String renderer,
            String version,
            long reportedVideoMemoryBytes,
            String memoryProvider
    ) {
        /** Returns whether a real GL renderer string was available. */
        public boolean rendererAvailable() {
            return renderer != null
                    && !renderer.isBlank()
                    && !renderer.equalsIgnoreCase("unavailable");
        }
    }
}
