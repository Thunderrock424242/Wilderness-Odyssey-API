package com.thunder.wildernessodysseyapi.anomaly.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.anomaly.config.AnomalyClientConfig;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.rendering.GPUCapabilities;
import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackend;
import com.thunder.wildernessodysseyapi.rendering.client.WildernessRenderingFramework;
import com.thunder.wildernessodysseyapi.rendering.compat.ShaderPackCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FogType;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;

/**
 * Owns only its own optional post chain. It never calls loadEffect, shutdownEffect,
 * or replaces GameRenderer's global post slot, and yields to external ownership.
 */
final class AnomalyPostEffectController {
    private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID, "shaders/post/anomaly_atmosphere.json");
    private static final int[] VIEWPORT = new int[4];
    private static PostChain chain;
    private static RenderTarget target;
    private static int width;
    private static int height;
    private static boolean failed;

    private AnomalyPostEffectController() {
    }

    static void render(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        var frame = WildernessRenderingFramework.currentFrame();
        float strength = AnomalyEffectPolicy.screenStrength(AnomalyClientState.intensity(),
                AnomalyClientState.riftIntensity(), AnomalyClientConfig.intensityScale(),
                minecraft.options.screenEffectScale().get().floatValue());
        if (minecraft.level == null || !AnomalyClientState.controls(minecraft.level)
                || !AnomalyClientState.postEnabled(AnomalyClientConfig.postEffectsEnabled())
                || strength <= 0.001F || failed || minecraft.screen != null
                || minecraft.gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE
                || minecraft.gameRenderer.currentEffect() != null
                || ShaderPackCompatibility.isExternalShaderPackActive()
                || frame.gpuCapabilities().api() != GPUCapabilities.GraphicsApi.OPENGL) {
            closeChain();
            return;
        }
        float seconds = (AnomalyClientState.visualTicks() + partialTick) / 20.0F;
        float motion = AnomalyEffectPolicy.motionAmount(strength, AnomalyClientState.riftIntensity(),
                AnomalyClientConfig.motionDistortionEnabled(), AnomalyClientConfig.pulsesEnabled(), seconds);
        float grade = AnomalyClientConfig.colorGradingEnabled() ? strength : 0.0F;
        if (grade <= 0.001F && motion <= 0.000001F) {
            closeChain();
            return;
        }

        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousDepthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, VIEWPORT);
        RenderBackend backend = frame.backend();
        try (RenderBackend.RenderStateScope ignored = backend.captureRenderStateScope()) {
            ensureChain(minecraft);
            chain.setUniform("GradeAmount", grade);
            chain.setUniform("RiftAmount", AnomalyEffectPolicy.violetStrength(AnomalyClientState.intensity(),
                    AnomalyClientState.riftIntensity()));
            chain.setUniform("MotionAmount", motion);
            chain.setUniform("MotionPhase", seconds * 0.5F);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            chain.process(partialTick);
        } catch (IOException | RuntimeException failure) {
            failed = true;
            closeChain();
            ModConstants.LOGGER.warn("Anomaly post effects disabled until resource reload; fog and particles remain available", failure);
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            RenderSystem.viewport(VIEWPORT[0], VIEWPORT[1], VIEWPORT[2], VIEWPORT[3]);
            RenderSystem.depthFunc(previousDepthFunction);
        }
    }

    /** Reload releases old GPU resources and allows one retry with the new pack. */
    static void reload() {
        runOnRenderThread(() -> {
            closeChain();
            failed = false;
        });
    }

    static void release() {
        runOnRenderThread(AnomalyPostEffectController::closeChain);
    }

    static String status() {
        return failed ? "failed; reload resources to retry" : chain == null ? "inactive" : "active";
    }

    private static void ensureChain(Minecraft minecraft) throws IOException {
        RenderTarget currentTarget = minecraft.getMainRenderTarget();
        if (chain != null && currentTarget != target) {
            closeChain();
        }
        if (chain == null) {
            chain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), currentTarget, EFFECT);
            target = currentTarget;
            width = -1;
            height = -1;
        }
        if (width != currentTarget.width || height != currentTarget.height) {
            chain.resize(currentTarget.width, currentTarget.height);
            width = currentTarget.width;
            height = currentTarget.height;
        }
    }

    private static void closeChain() {
        PostChain closing = chain;
        chain = null;
        target = null;
        width = 0;
        height = 0;
        if (closing != null) {
            try {
                closing.close();
            } catch (RuntimeException failure) {
                if (!failed) {
                    ModConstants.LOGGER.warn("Anomaly post resources could not be closed; post effects disabled until resource reload", failure);
                }
                failed = true;
            }
        }
    }

    private static void runOnRenderThread(Runnable action) {
        if (RenderSystem.isOnRenderThread()) {
            action.run();
        } else {
            RenderSystem.recordRenderCall(action::run);
        }
    }
}
