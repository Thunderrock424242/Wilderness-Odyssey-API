package com.thunder.wildernessodysseyapi.rendering.backend.opengl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuHardwareProbe;
import com.thunder.wildernessodysseyapi.rendering.GPUCapabilities;
import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackend;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLCapabilities;

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * OpenGL implementation of the narrow Wilderness rendering backend boundary.
 *
 * @deprecated Current Minecraft 1.21.1 compatibility adapter. Install a
 * backend supported by the Vulkan-targeted Minecraft renderer instead of
 * calling this implementation from new code.
 */
@Deprecated(forRemoval = true)
public final class OpenGlRenderBackend implements RenderBackend {

    private final GPUCapabilities capabilities;
    private final Set<OpenGlGpuTimer> timers = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean closed;

    private OpenGlRenderBackend(GPUCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    /** Captures static context capabilities once after Minecraft creates OpenGL. */
    public static OpenGlRenderBackend capture() {
        RenderSystem.assertOnRenderThreadOrInit();
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
    @SuppressWarnings("removal") // The timer is part of this same legacy OpenGL adapter.
    public GpuTimer createGpuTimer(int bufferedFrames) {
        RenderSystem.assertOnRenderThread();
        if (closed || !capabilities.supportsGpuTiming()) {
            return GpuTimer.UNAVAILABLE;
        }
        OpenGlGpuTimer timer = new OpenGlGpuTimer(
                Math.max(2, Math.min(8, bufferedFrames)),
                timers::remove
        );
        timers.add(timer);
        return timer;
    }

    @Override
    public boolean isShaderUsable(ShaderInstance shader) {
        RenderSystem.assertOnRenderThread();
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
        RenderSystem.assertOnRenderThread();
        return new RenderStateSnapshot(
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glIsEnabled(GL11.GL_BLEND)
        );
    }

    @Override
    public RenderStateScope captureRenderStateScope() {
        RenderSystem.assertOnRenderThread();
        RenderStateSnapshot snapshot = captureRenderState();
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        return new OpenGlRenderStateScope(
                snapshot,
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                shaderColor
        );
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;
        for (OpenGlGpuTimer timer : List.copyOf(timers)) {
            timer.close();
        }
        timers.clear();
    }

    private static int safeInteger(int name, int fallback) {
        try {
            return Math.max(0, GL11.glGetInteger(name));
        } catch (RuntimeException unavailable) {
            return fallback;
        }
    }

    /** Restores the exact blend factors and common state borrowed by one fullscreen pass. */
    private static final class OpenGlRenderStateScope implements RenderStateScope {
        private final RenderStateSnapshot snapshot;
        private final int sourceRgb;
        private final int destinationRgb;
        private final int sourceAlpha;
        private final int destinationAlpha;
        private final float[] shaderColor;
        private boolean closed;

        private OpenGlRenderStateScope(
                RenderStateSnapshot snapshot,
                int sourceRgb,
                int destinationRgb,
                int sourceAlpha,
                int destinationAlpha,
                float[] shaderColor
        ) {
            this.snapshot = snapshot;
            this.sourceRgb = sourceRgb;
            this.destinationRgb = destinationRgb;
            this.sourceAlpha = sourceAlpha;
            this.destinationAlpha = destinationAlpha;
            this.shaderColor = shaderColor;
        }

        @Override
        public void close() {
            RenderSystem.assertOnRenderThread();
            if (closed) {
                return;
            }
            closed = true;
            try {
                GlStateManager._blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            } finally {
                snapshot.restore();
                RenderSystem.setShaderColor(
                        shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]
                );
            }
        }
    }
}
