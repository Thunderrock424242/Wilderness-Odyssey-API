package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.rendering.GPUCapabilities;
import com.thunder.wildernessodysseyapi.rendering.client.WildernessRenderingFramework;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Owns the single color/depth scene copy sampled by the built-in water shader.
 *
 * <p>Sampling the active framebuffer while writing water back into it is an
 * undefined OpenGL feedback loop. This class instead blits color and depth into
 * an isolated texture target once for a supplied frame key and restores both
 * framebuffer bindings before returning.</p>
 *
 * @deprecated This raw OpenGL framebuffer-copy path exists only for Minecraft
 * 1.21.1. Replace it with backend-provided scene color/depth inputs before the
 * Vulkan-targeted version ships.
 */
@Deprecated(forRemoval = true)
public final class WaterSceneCapture {

    private static TextureTarget sceneTarget;
    private static final WaterSceneCaptureState CAPTURE_STATE = new WaterSceneCaptureState();
    private static long targetBackendGeneration = Long.MIN_VALUE;

    private WaterSceneCapture() {
    }

    /** Captures the current main scene once for the given logical render frame. */
    public static Capture capture(long frameKey) {
        RenderSystem.assertOnRenderThread();
        var frame = WildernessRenderingFramework.currentFrame();
        if (frame.gpuCapabilities().api() != GPUCapabilities.GraphicsApi.OPENGL
                || !frame.gpuCapabilities().supportsAdvancedReflections()) {
            return Capture.UNAVAILABLE;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget source = minecraft.getMainRenderTarget();
        long backendGeneration = frame.backendGeneration();
        if (sceneTarget != null && CAPTURE_STATE.canReuse(
                frameKey, backendGeneration, source.viewWidth, source.viewHeight)) {
            return currentCapture();
        }

        long started = System.nanoTime();
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        CAPTURE_STATE.invalidate();
        try {
            // Allocation and resizing can bind framebuffers too. Restore the
            // caller's bindings around the entire operation, not just the blit.
            ensureTarget(source.viewWidth, source.viewHeight, backendGeneration);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneTarget.frameBufferId);
            GlStateManager._glBlitFrameBuffer(
                    0, 0, source.viewWidth, source.viewHeight,
                    0, 0, sceneTarget.viewWidth, sceneTarget.viewHeight,
                    GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
            CAPTURE_STATE.captured(frameKey, backendGeneration, source.viewWidth, source.viewHeight);
            WaterRenderDiagnostics.recordSceneCopy(System.nanoTime() - started);
            return currentCapture();
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        }
    }

    /**
     * Returns the existing capture only when it still belongs to {@code frameKey}.
     *
     * <p>Overlay hooks run after Minecraft may clear world depth. They must
     * never turn a failed reuse into a new capture of that later framebuffer.</p>
     */
    public static Capture getIfCurrent(long frameKey) {
        RenderSystem.assertOnRenderThread();
        var frame = WildernessRenderingFramework.currentFrame();
        RenderTarget source = Minecraft.getInstance().getMainRenderTarget();
        return sceneTarget != null && CAPTURE_STATE.canReuse(
                frameKey, frame.backendGeneration(), source.viewWidth, source.viewHeight)
                ? currentCapture()
                : Capture.UNAVAILABLE;
    }

    private static Capture currentCapture() {
        return new Capture(
                sceneTarget.getColorTextureId(),
                sceneTarget.getDepthTextureId(),
                sceneTarget.viewWidth,
                sceneTarget.viewHeight,
                true
        );
    }

    /** Releases GPU attachments on shader handoff, reload, or client level teardown. */
    public static void release() {
        TextureTarget previous = sceneTarget;
        sceneTarget = null;
        targetBackendGeneration = Long.MIN_VALUE;
        CAPTURE_STATE.invalidate();
        WaterRenderDiagnostics.setSceneCaptureAvailable(false);
        if (previous != null) {
            previous.destroyBuffers();
        }
    }

    private static void ensureTarget(int width, int height, long backendGeneration) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (sceneTarget != null && targetBackendGeneration != backendGeneration) {
            release();
        }
        if (sceneTarget == null) {
            sceneTarget = new TextureTarget(safeWidth, safeHeight, true, Minecraft.ON_OSX);
            targetBackendGeneration = backendGeneration;
            return;
        }
        if (sceneTarget.viewWidth != safeWidth || sceneTarget.viewHeight != safeHeight) {
            sceneTarget.resize(safeWidth, safeHeight, Minecraft.ON_OSX);
            CAPTURE_STATE.invalidate();
        }
    }

    /** Immutable texture handles and dimensions for one captured frame. */
    public record Capture(int colorTextureId, int depthTextureId, int width, int height, boolean available) {
        private static final Capture UNAVAILABLE = new Capture(-1, -1, 1, 1, false);
    }
}
