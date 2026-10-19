package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.wage.GPUCapabilities;
import com.thunder.wildernessodysseyapi.wage.client.WildernessRenderingFramework;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
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
    private static int requestedDepthFormat;
    private static int targetDepthFormat;

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
        if (!source.useDepth || source.frameBufferId <= 0 || source.getDepthTextureId() <= 0
                || source.viewWidth <= 0 || source.viewHeight <= 0) {
            CAPTURE_STATE.invalidate();
            return Capture.UNAVAILABLE;
        }
        long backendGeneration = frame.backendGeneration();
        if (sceneTarget != null && CAPTURE_STATE.canReuse(
                frameKey, backendGeneration, source.viewWidth, source.viewHeight)) {
            return currentCapture();
        }

        long started = System.nanoTime();
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        CAPTURE_STATE.invalidate();
        try {
            // GL_SAMPLES describes the draw framebuffer. Bind the source to
            // both targets before checking it, then restore both in finally.
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, source.frameBufferId);
            int sourceSamples = GL11.glGetInteger(GL13.GL_SAMPLES);
            if (sourceSamples != 0
                    || GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE
                    || GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11.GL_TEXTURE
                    || GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) != source.getDepthTextureId()) {
                return Capture.UNAVAILABLE;
            }
            int sourceDepthFormat = depthTextureFormat(source.getDepthTextureId());
            DepthFormat depthFormat = DepthFormat.forInternalFormat(sourceDepthFormat);
            if (depthFormat == null) {
                return Capture.UNAVAILABLE;
            }

            // Allocation and resizing can bind framebuffers too. Restore the
            // caller's bindings around the entire operation, not just the blit.
            ensureTarget(source.viewWidth, source.viewHeight, backendGeneration, depthFormat);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneTarget.frameBufferId);
            if (!DepthFormat.canBlit(sourceDepthFormat, targetDepthFormat, sourceSamples)
                    || GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                // GL errors do not throw Java exceptions. Reject incompatible
                // storage before the blit rather than publishing a failed copy.
                return Capture.UNAVAILABLE;
            }
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
            GlStateManager._bindTexture(previousTexture);
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
        requestedDepthFormat = 0;
        targetDepthFormat = 0;
        CAPTURE_STATE.invalidate();
        WaterRenderDiagnostics.setSceneCaptureAvailable(false);
        if (previous != null) {
            previous.destroyBuffers();
        }
    }

    private static void ensureTarget(int width, int height, long backendGeneration, DepthFormat depthFormat) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (sceneTarget != null && targetBackendGeneration != backendGeneration) {
            release();
        }
        if (sceneTarget == null) {
            sceneTarget = new TextureTarget(safeWidth, safeHeight, true, Minecraft.ON_OSX);
            targetBackendGeneration = backendGeneration;
        } else if (sceneTarget.viewWidth != safeWidth || sceneTarget.viewHeight != safeHeight) {
            sceneTarget.resize(safeWidth, safeHeight, Minecraft.ON_OSX);
            requestedDepthFormat = 0;
            targetDepthFormat = 0;
            CAPTURE_STATE.invalidate();
        }
        if (requestedDepthFormat != depthFormat.internalFormat()) {
            targetDepthFormat = depthTextureFormat(sceneTarget.getDepthTextureId());
            if (targetDepthFormat != depthFormat.internalFormat()) {
                // NeoForge enableStencil() always chooses DEPTH32F_STENCIL8;
                // an optional renderer may instead use DEPTH24_STENCIL8 or a
                // different depth precision. Keep Minecraft's target lifecycle
                // and parameters, replacing only our private texture's storage.
                GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, depthFormat.internalFormat(),
                        sceneTarget.width, sceneTarget.height, 0,
                        depthFormat.pixelFormat(), depthFormat.pixelType(), null);
                targetDepthFormat = depthTextureFormat(sceneTarget.getDepthTextureId());
            }
            // Cache the attempted layout too: unsupported driver storage must
            // fall back without reallocating the texture every rendered frame.
            requestedDepthFormat = depthFormat.internalFormat();
        }
    }

    private static int depthTextureFormat(int textureId) {
        // RenderTarget exposes a level-zero 2D depth texture. Callers preserve
        // its binding through GlStateManager so Minecraft's cache stays valid.
        GlStateManager._bindTexture(textureId);
        return GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
    }

    /** Allocation and copy policy kept inside the deprecated OpenGL owner. */
    enum DepthFormat {
        DEFAULT(GL11.GL_DEPTH_COMPONENT, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT),
        DEPTH16(GL14.GL_DEPTH_COMPONENT16, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_SHORT),
        DEPTH24(GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT),
        DEPTH32(GL14.GL_DEPTH_COMPONENT32, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT),
        DEPTH32F(GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT),
        DEPTH24_STENCIL8(GL30.GL_DEPTH24_STENCIL8, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8),
        DEPTH32F_STENCIL8(GL30.GL_DEPTH32F_STENCIL8, GL30.GL_DEPTH_STENCIL,
                GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV);

        private final int internalFormat;
        private final int pixelFormat;
        private final int pixelType;

        DepthFormat(int internalFormat, int pixelFormat, int pixelType) {
            this.internalFormat = internalFormat;
            this.pixelFormat = pixelFormat;
            this.pixelType = pixelType;
        }

        static DepthFormat forInternalFormat(int internalFormat) {
            return switch (internalFormat) {
                case GL11.GL_DEPTH_COMPONENT -> DEFAULT;
                case GL14.GL_DEPTH_COMPONENT16 -> DEPTH16;
                case GL14.GL_DEPTH_COMPONENT24 -> DEPTH24;
                case GL14.GL_DEPTH_COMPONENT32 -> DEPTH32;
                case GL30.GL_DEPTH_COMPONENT32F -> DEPTH32F;
                case GL30.GL_DEPTH24_STENCIL8 -> DEPTH24_STENCIL8;
                case GL30.GL_DEPTH32F_STENCIL8 -> DEPTH32F_STENCIL8;
                default -> null;
            };
        }

        static boolean canBlit(int sourceFormat, int targetFormat, int sourceSamples) {
            // A depth blit cannot convert formats. Multisample resolution is
            // outside this capture's contract and uses the optical fallback.
            return sourceSamples == 0 && forInternalFormat(sourceFormat) != null
                    && sourceFormat == targetFormat;
        }

        int internalFormat() {
            return internalFormat;
        }

        int pixelFormat() {
            return pixelFormat;
        }

        int pixelType() {
            return pixelType;
        }
    }

    /** Immutable texture handles and dimensions for one captured frame. */
    public record Capture(int colorTextureId, int depthTextureId, int width, int height, boolean available) {
        private static final Capture UNAVAILABLE = new Capture(-1, -1, 1, 1, false);
    }
}
