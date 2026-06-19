package com.thunder.wildernessodysseyapi.gpuprofiler.client;

import org.lwjgl.opengl.ATIMeminfo;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.NVXGPUMemoryInfo;

final class GpuMemoryProbe {

    private static final long KIB = 1024L;

    private GpuMemoryProbe() {
    }

    static GpuInfo gpuInfo() {
        try {
            return new GpuInfo(
                    safeString(GL11.glGetString(GL11.GL_VENDOR)),
                    safeString(GL11.glGetString(GL11.GL_RENDERER)),
                    safeString(GL11.glGetString(GL11.GL_VERSION))
            );
        } catch (Throwable ignored) {
            return new GpuInfo("unavailable", "unavailable", "unavailable");
        }
    }

    static Sample sample(long elapsedNanos) {
        try {
            GLCapabilities capabilities = GL.getCapabilities();
            if (capabilities.GL_NVX_gpu_memory_info) {
                long total = kibToBytes(GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX));
                long available = kibToBytes(GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX));
                long used = total >= 0L && available >= 0L ? Math.max(0L, total - available) : -1L;
                return new Sample(elapsedNanos, "NVX_gpu_memory_info", total, available, used);
            }

            if (capabilities.GL_ATI_meminfo) {
                int[] values = new int[4];
                GL11.glGetIntegerv(ATIMeminfo.GL_TEXTURE_FREE_MEMORY_ATI, values);
                return new Sample(elapsedNanos, "ATI_meminfo", -1L, kibToBytes(values[0]), -1L);
            }
        } catch (Throwable ignored) {
            // A missing/currently detached GL context should not affect rendering.
        }
        return new Sample(elapsedNanos, "unavailable", -1L, -1L, -1L);
    }

    private static String safeString(String value) {
        return value == null || value.isBlank() ? "unavailable" : value;
    }

    private static long kibToBytes(int kibibytes) {
        return kibibytes <= 0 ? -1L : (long) kibibytes * KIB;
    }

    record GpuInfo(String vendor, String renderer, String version) {
    }

    record Sample(long elapsedNanos, String provider, long totalBytes, long availableBytes, long usedBytes) {
        boolean available() {
            return usedBytes >= 0L || availableBytes >= 0L;
        }
    }
}
