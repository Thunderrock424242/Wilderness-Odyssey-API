package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackends;
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
 */
public final class WaterSceneCapture {

    private static TextureTarget sceneTarget;
    private static long capturedFrameKey = Long.MIN_VALUE;

    private WaterSceneCapture() {
    }

    /** Captures the current main scene once for the given logical render frame. */
    public static Capture capture(long frameKey) {
        RenderSystem.assertOnRenderThread();
        if (!RenderBackends.current().capabilities().supportsAdvancedReflections()) {
            return Capture.UNAVAILABLE;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget source = minecraft.getMainRenderTarget();
        ensureTarget(source.viewWidth, source.viewHeight);
        if (sceneTarget == null) {
            return Capture.UNAVAILABLE;
        }

        if (capturedFrameKey != frameKey) {
            long started = System.nanoTime();
            int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            try {
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneTarget.frameBufferId);
                GlStateManager._glBlitFrameBuffer(
                        0, 0, source.viewWidth, source.viewHeight,
                        0, 0, sceneTarget.viewWidth, sceneTarget.viewHeight,
                        GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
                        GL11.GL_NEAREST
                );
            } finally {
                // A failed resize or driver blit must not strand Minecraft on
                // the water target for the rest of the frame.
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            }
            capturedFrameKey = frameKey;
            WaterRenderDiagnostics.recordSceneCopy(System.nanoTime() - started);
        }

        return currentCapture();
    }

    /**
     * Returns the existing capture only when it still belongs to {@code frameKey}.
     *
     * <p>Overlay hooks run after Minecraft may clear world depth. They must
     * never turn a failed reuse into a new capture of that later framebuffer.</p>
     */
    public static Capture getIfCurrent(long frameKey) {
        RenderSystem.assertOnRenderThread();
        return sceneTarget != null && capturedFrameKey == frameKey
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

    /** Releases GPU attachments during a future renderer shutdown or reload. */
    public static void release() {
        if (sceneTarget != null) {
            sceneTarget.destroyBuffers();
            sceneTarget = null;
        }
        capturedFrameKey = Long.MIN_VALUE;
        WaterRenderDiagnostics.setSceneCaptureAvailable(false);
    }

    private static void ensureTarget(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (sceneTarget == null) {
            sceneTarget = new TextureTarget(safeWidth, safeHeight, true, Minecraft.ON_OSX);
            return;
        }
        if (sceneTarget.viewWidth != safeWidth || sceneTarget.viewHeight != safeHeight) {
            sceneTarget.resize(safeWidth, safeHeight, Minecraft.ON_OSX);
            capturedFrameKey = Long.MIN_VALUE;
        }
    }

    /** Immutable texture handles and dimensions for one captured frame. */
    public record Capture(int colorTextureId, int depthTextureId, int width, int height, boolean available) {
        private static final Capture UNAVAILABLE = new Capture(-1, -1, 1, 1, false);
    }
}
