package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Applies Phase 8 fog, transition, distortion, and caustic presentation.
 *
 * <p>Fog remains an ordinary NeoForge viewport customization, while the
 * optional overlay replaces vanilla's flat-water texture only when the mod's
 * linked shader owns the effect. Iris/Oculus keeps its normal overlay path, and
 * canonical wave crests remain compatible with vanilla water detection.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class UnderwaterEffectsRenderer {

    private static final ResourceLocation VANILLA_UNDERWATER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/underwater.png");

    private UnderwaterEffectsRenderer() {
    }

    /** Blends biome- and depth-aware underwater color at the animated surface. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!WaterRenderingConfig.ENABLE_UNDERWATER_OPTICS.get()
                || !canOwnWaterFog(event.getCamera().getFluidInCamera())) {
            return;
        }

        ClientWaterImmersion.ImmersionState state = ClientWaterImmersion.sample(
                event.getCamera(),
                (float) event.getPartialTick()
        );
        float blend = state.optics().immersionBlend();
        if (!state.waterColumnPresent()) {
            return;
        }

        UnderwaterOpticsModel.OpticalProperties optics = state.optics();
        float sourceRed = event.getRed();
        float sourceGreen = event.getGreen();
        float sourceBlue = event.getBlue();
        if (event.getCamera().getFluidInCamera() == FogType.WATER) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                var skyColor = minecraft.level.getSkyColor(
                        event.getCamera().getPosition(),
                        (float) event.getPartialTick()
                );
                sourceRed = (float) skyColor.x;
                sourceGreen = (float) skyColor.y;
                sourceBlue = (float) skyColor.z;
            }
        }

        event.setRed(mix(sourceRed, optics.fogRed(), blend));
        event.setGreen(mix(sourceGreen, optics.fogGreen(), blend));
        event.setBlue(mix(sourceBlue, optics.fogBlue(), blend));
    }

    /** Replaces the flat vanilla fog distance with bounded optical visibility. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!WaterRenderingConfig.ENABLE_UNDERWATER_OPTICS.get()
                || !canOwnWaterFog(event.getType())) {
            return;
        }

        ClientWaterImmersion.ImmersionState state = ClientWaterImmersion.sample(
                event.getCamera(),
                (float) event.getPartialTick()
        );
        float blend = state.optics().immersionBlend();
        if (!state.waterColumnPresent()) {
            return;
        }

        float sourceNear = event.getNearPlaneDistance();
        float sourceFar = event.getFarPlaneDistance();
        if (event.getType() == FogType.WATER) {
            float aboveWaterFar = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0f;
            sourceFar = aboveWaterFar;
            sourceNear = event.getMode() == FogRenderer.FogMode.FOG_SKY
                    ? 0.0f
                    : aboveWaterFar * 0.75f;
        }

        float nearDistance = mix(sourceNear, -2.0f, blend);
        float farDistance = mix(
                sourceFar,
                state.optics().visibilityBlocks(),
                blend
        );
        event.setNearPlaneDistance(nearDistance);
        event.setFarPlaneDistance(Math.max(nearDistance + 1.0f, farDistance));
        event.setFogShape(blend >= 0.5f ? FogShape.SPHERE : FogShape.CYLINDER);
        event.setCanceled(true);
    }

    /** Replaces vanilla's underwater overlay when the built-in shader is active. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onWaterOverlay(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() != RenderBlockScreenEffectEvent.OverlayType.WATER
                || !WaterRenderingConfig.ENABLE_UNDERWATER_OPTICS.get()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientWaterImmersion.ImmersionState state = ClientWaterImmersion.sample(
                minecraft.gameRenderer.getMainCamera(),
                minecraft.gameRenderer.getMainCamera().getPartialTickTime()
        );

        // Vanilla can report water while the visible Gerstner trough is below
        // the camera. Suppress that stale overlay during the short transition.
        if (state.waterColumnPresent() && !state.isVisuallySubmerged()) {
            event.setCanceled(true);
            return;
        }
        if (!state.isVisuallySubmerged() || !WaterShaders.shouldUseUnderwaterShader()) {
            return;
        }

        event.setCanceled(true);
        renderBuiltInOverlay(minecraft, event.getPoseStack(), state);
    }

    /** Adds an overlay when a rendered wave crest rises above vanilla's fluid plane. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!WaterRenderingConfig.ENABLE_UNDERWATER_OPTICS.get()
                || minecraft.player == null
                || minecraft.level == null
                || minecraft.player.isSpectator()
                || minecraft.player.isEyeInFluid(FluidTags.WATER)) {
            return;
        }

        ClientWaterImmersion.ImmersionState state = ClientWaterImmersion.sample(
                minecraft.gameRenderer.getMainCamera(),
                minecraft.gameRenderer.getMainCamera().getPartialTickTime()
        );
        if (!state.isVisuallySubmerged()) {
            return;
        }

        PoseStack poseStack = event.getGuiGraphics().pose();
        if (WaterShaders.shouldUseUnderwaterShader()) {
            renderBuiltInOverlay(minecraft, poseStack, state);
        } else {
            // Shader packs retain their own water path; this standard overlay
            // only fills the gap where the animated crest exceeds block water.
            ScreenEffectRenderer.renderFluid(minecraft, poseStack, VANILLA_UNDERWATER_TEXTURE);
        }
    }

    private static void renderBuiltInOverlay(
            Minecraft minecraft,
            PoseStack poseStack,
            ClientWaterImmersion.ImmersionState state
    ) {
        if (minecraft.level == null) {
            return;
        }

        float partialTick = minecraft.gameRenderer.getMainCamera().getPartialTickTime();
        float timeSeconds = (minecraft.level.getGameTime() + partialTick) / 20.0f;
        ShaderInstance shader = WaterShaders.getUnderwaterShader();
        WaterShaders.updateUnderwaterUniforms(timeSeconds, state);

        boolean depthTestWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWriteWasEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        try {
            // Screen-space optics replace the completed scene and must not be
            // clipped by hand/HUD depth or write depth into later UI layers.
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, VANILLA_UNDERWATER_TEXTURE);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            Matrix4f matrix = poseStack.last().pose();
            BufferBuilder buffer = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_TEX
            );
            buffer.addVertex(matrix, -1.0f, -1.0f, -0.5f).setUv(4.0f, 4.0f);
            buffer.addVertex(matrix, 1.0f, -1.0f, -0.5f).setUv(0.0f, 4.0f);
            buffer.addVertex(matrix, 1.0f, 1.0f, -0.5f).setUv(0.0f, 0.0f);
            buffer.addVertex(matrix, -1.0f, 1.0f, -0.5f).setUv(4.0f, 0.0f);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.depthMask(depthWriteWasEnabled);
            if (depthTestWasEnabled) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (blendWasEnabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
        }
    }

    private static boolean canOwnWaterFog(FogType fogType) {
        return fogType == FogType.NONE || fogType == FogType.WATER;
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * Math.max(0.0f, Math.min(1.0f, amount));
    }
}
