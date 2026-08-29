package com.thunder.wildernessodysseyapi.rendering;

import java.util.Set;

/**
 * Immutable, cached description of the active rendering device and backend.
 *
 * <p>Vendor and renderer strings are diagnostics only. Rendering policy must
 * request features through {@link #supports(Feature)} and the semantic helper
 * methods instead of branching on a company or product name.</p>
 */
public record GPUCapabilities(
        GraphicsApi api,
        String vendor,
        String renderer,
        String driverVersion,
        long reportedVideoMemoryBytes,
        MemoryEvidence memoryEvidence,
        int maximumTextureSize,
        int maximumDrawBuffers,
        Set<Feature> features
) {
    public static final GPUCapabilities UNAVAILABLE = new GPUCapabilities(
            GraphicsApi.UNKNOWN,
            "unavailable",
            "unavailable",
            "unavailable",
            -1L,
            MemoryEvidence.UNAVAILABLE,
            0,
            0,
            Set.of()
    );

    public GPUCapabilities {
        api = api == null ? GraphicsApi.UNKNOWN : api;
        vendor = available(vendor);
        renderer = available(renderer);
        driverVersion = available(driverVersion);
        reportedVideoMemoryBytes = Math.max(-1L, reportedVideoMemoryBytes);
        memoryEvidence = memoryEvidence == null ? MemoryEvidence.UNAVAILABLE : memoryEvidence;
        maximumTextureSize = Math.max(0, maximumTextureSize);
        maximumDrawBuffers = Math.max(0, maximumDrawBuffers);
        features = features == null || features.isEmpty() ? Set.of() : Set.copyOf(features);
    }

    /** Returns whether a usable graphics context supplied this snapshot. */
    public boolean available() {
        return api != GraphicsApi.UNKNOWN && supports(Feature.SHADER_PROGRAMS);
    }

    /** Returns whether the active backend reported one concrete feature. */
    public boolean supports(Feature feature) {
        return feature != null && features.contains(feature);
    }

    public boolean supportsComputeShaders() {
        return supports(Feature.COMPUTE_SHADERS);
    }

    public boolean supportsGpuTiming() {
        return supports(Feature.GPU_TIMER_QUERIES);
    }

    /** Returns whether the current water reflection path has its required primitives. */
    public boolean supportsAdvancedReflections() {
        return supports(Feature.SHADER_PROGRAMS)
                && supports(Feature.FRAMEBUFFER_BLIT)
                && supports(Feature.DEPTH_TEXTURES);
    }

    /** Returns whether the backend can support the current high-quality cloud path. */
    public boolean supportsHighQualityVolumetrics() {
        return supports(Feature.SHADER_PROGRAMS)
                && supports(Feature.MULTIPLE_RENDER_TARGETS)
                && maximumTextureSize >= 8_192
                && maximumDrawBuffers >= 4;
    }

    private static String available(String value) {
        return value == null || value.isBlank() ? "unavailable" : value;
    }

    public enum GraphicsApi {
        OPENGL,
        VULKAN,
        UNKNOWN
    }

    /** Describes what a driver memory value actually proves. */
    public enum MemoryEvidence {
        DEDICATED_TOTAL,
        AVAILABLE_ONLY,
        UNAVAILABLE
    }

    public enum Feature {
        SHADER_PROGRAMS,
        FRAMEBUFFER_BLIT,
        DEPTH_TEXTURES,
        MULTIPLE_RENDER_TARGETS,
        GPU_TIMER_QUERIES,
        COMPUTE_SHADERS,
        IMAGE_LOAD_STORE,
        TEXTURE_STORAGE,
        DEBUG_MARKERS
    }
}
