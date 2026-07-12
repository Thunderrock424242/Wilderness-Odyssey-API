package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
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
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneTarget.frameBufferId);
            GlStateManager._glBlitFrameBuffer(
                    0, 0, source.viewWidth, source.viewHeight,
                    0, 0, sceneTarget.viewWidth, sceneTarget.viewHeight,
                    GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            capturedFrameKey = frameKey;
            WaterRenderDiagnostics.recordSceneCopy(System.nanoTime() - started);
        }

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
