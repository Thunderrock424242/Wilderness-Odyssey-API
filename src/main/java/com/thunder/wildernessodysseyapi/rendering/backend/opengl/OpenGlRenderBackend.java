package com.thunder.wildernessodysseyapi.rendering.backend.opengl;

import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuHardwareProbe;
import com.thunder.wildernessodysseyapi.rendering.GPUCapabilities;
import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackend;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLCapabilities;

import java.util.EnumSet;

/** OpenGL implementation of the narrow Wilderness rendering backend boundary. */
public final class OpenGlRenderBackend implements RenderBackend {

    private final GPUCapabilities capabilities;

    private OpenGlRenderBackend(GPUCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    /** Captures static context capabilities once after Minecraft creates OpenGL. */
    public static OpenGlRenderBackend capture() {
        GLCapabilities gl = GL.getCapabilities();
        GpuHardwareProbe.Snapshot hardware = GpuHardwareProbe.capture();
        EnumSet<GPUCapabilities.Feature> features = EnumSet.noneOf(GPUCapabilities.Feature.class);

        if (gl.OpenGL20) {
            features.add(GPUCapabilities.Feature.SHADER_PROGRAMS);
            features.add(GPUCapabilities.Feature.MULTIPLE_RENDER_TARGETS);
        }
        if (gl.OpenGL14 || gl.GL_ARB_depth_texture) {
            features.add(GPUCapabilities.Feature.DEPTH_TEXTURES);
        }
        if (gl.OpenGL30 || gl.GL_ARB_framebuffer_object || gl.GL_EXT_framebuffer_blit) {
            features.add(GPUCapabilities.Feature.FRAMEBUFFER_BLIT);
        }
        if (gl.OpenGL33 || gl.GL_ARB_timer_query) {
            features.add(GPUCapabilities.Feature.GPU_TIMER_QUERIES);
        }
        if (gl.OpenGL43 || gl.GL_ARB_compute_shader) {
            features.add(GPUCapabilities.Feature.COMPUTE_SHADERS);
        }
        if (gl.OpenGL42 || gl.GL_ARB_shader_image_load_store) {
            features.add(GPUCapabilities.Feature.IMAGE_LOAD_STORE);
        }
        if (gl.OpenGL42 || gl.GL_ARB_texture_storage) {
            features.add(GPUCapabilities.Feature.TEXTURE_STORAGE);
        }
        if (gl.OpenGL43 || gl.GL_KHR_debug) {
            features.add(GPUCapabilities.Feature.DEBUG_MARKERS);
        }

        int maximumTextureSize = safeInteger(GL11.GL_MAX_TEXTURE_SIZE, 0);
        int maximumDrawBuffers = gl.OpenGL20 ? safeInteger(GL20.GL_MAX_DRAW_BUFFERS, 1) : 1;
        GPUCapabilities.MemoryEvidence memoryEvidence = switch (hardware.memoryProvider()) {
            case "NVX_gpu_memory_info" -> GPUCapabilities.MemoryEvidence.DEDICATED_TOTAL;
            case "ATI_meminfo" -> GPUCapabilities.MemoryEvidence.AVAILABLE_ONLY;
            default -> GPUCapabilities.MemoryEvidence.UNAVAILABLE;
        };
        return new OpenGlRenderBackend(new GPUCapabilities(
                GPUCapabilities.GraphicsApi.OPENGL,
                hardware.vendor(),
                hardware.renderer(),
                hardware.version(),
                hardware.reportedVideoMemoryBytes(),
                memoryEvidence,
                maximumTextureSize,
                maximumDrawBuffers,
                features
        ));
    }

    @Override
    public GPUCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public GpuTimer createGpuTimer(int bufferedFrames) {
        return capabilities.supportsGpuTiming()
                ? new OpenGlGpuTimer(Math.max(2, Math.min(8, bufferedFrames)))
                : GpuTimer.UNAVAILABLE;
    }

    @Override
    public boolean isShaderUsable(ShaderInstance shader) {
        if (shader == null) {
            return false;
        }
        int nativeProgramHandle = shader.getId();
        return nativeProgramHandle > 0
                && GL20.glIsProgram(nativeProgramHandle)
                && GL20.glGetProgrami(nativeProgramHandle, GL20.GL_LINK_STATUS) != 0;
    }

    @Override
    public RenderStateSnapshot captureRenderState() {
        return new RenderStateSnapshot(
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glIsEnabled(GL11.GL_BLEND)
        );
    }

    private static int safeInteger(int name, int fallback) {
        try {
            return Math.max(0, GL11.glGetInteger(name));
        } catch (RuntimeException unavailable) {
            return fallback;
        }
    }
}
