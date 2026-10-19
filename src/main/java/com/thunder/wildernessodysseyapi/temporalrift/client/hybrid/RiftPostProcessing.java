package com.thunder.wildernessodysseyapi.temporalrift.client.hybrid;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.wage.GPUCapabilities;
import com.thunder.wildernessodysseyapi.wage.backend.RenderBackends;
import com.thunder.wildernessodysseyapi.wage.client.WildernessRenderingFramework;
import com.thunder.wildernessodysseyapi.wage.compat.ShaderPackCompatibility;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftConfig;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Independent scene pass. Does not replace GameRenderer's effect or take ownership from a shader pack. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class RiftPostProcessing {
    private static PostChain chain;
    private static RenderTarget target;
    private static ClientLevel owner;
    private static boolean failed;
    private static int width;
    private static int height;

    private RiftPostProcessing() { }

    /** Invalidates cached resources; a nearby-field tick rebuilds them outside the per-frame pass. */
    static void reload(ResourceManager manager) {
        owner = Minecraft.getInstance().level;
        close();
        failed = false;
    }

    private static void initialize(ResourceManager manager) {
        close();
        failed = false;
        try {
            var minecraft = Minecraft.getInstance();
            // World unload clears the frame snapshot; the cached backend remains valid before the first new frame.
            if (RenderBackends.selection().backend().capabilities().api() != GPUCapabilities.GraphicsApi.OPENGL) return;
            target = minecraft.getMainRenderTarget();
            chain = new PostChain(minecraft.getTextureManager(), manager, target,
                    ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "shaders/post/rift_local.json"));
            resize();
        } catch (Exception exception) {
            failed = true;
            close();
            ModConstants.LOGGER.warn("Optional local rift post-processing unavailable; retaining geometry, fog and particles", exception);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (owner != minecraft.level) {
            owner = minecraft.level;
            close();
            failed = false;
        }
        if (owner == null) return;
        if (!RiftConfig.POST_PROCESSING.get() || !RiftClientEffects.enabled()
                || ShaderPackCompatibility.isExternalShaderPackActive()) {
            close();
            return;
        }
        // Allocate only when a nearby field needs the pass. This cold path runs on a client tick, never each frame.
        if (chain == null && !failed && RiftClientEffects.intensity(1) > 0.003F) initialize(minecraft.getResourceManager());
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() == owner) { close(); owner = null; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderGuiEvent.Pre event) {
        // The anomaly biome pass uses this event's normal priority. Apply local instability afterward, before HUD text.
        if (chain == null || failed
                || !RiftConfig.POST_PROCESSING.get() || !RiftClientEffects.enabled()
                || ShaderPackCompatibility.isExternalShaderPackActive()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float amount = RiftClientEffects.intensity(partial);
        if (amount < 0.003F) return;
        var minecraft = Minecraft.getInstance();
        var frame = WildernessRenderingFramework.currentFrame();
        if (minecraft.level == null || minecraft.level != owner || target != minecraft.getMainRenderTarget()
                || frame.gpuCapabilities().api() != GPUCapabilities.GraphicsApi.OPENGL
                || minecraft.screen != null || minecraft.gameRenderer.currentEffect() != null
                || minecraft.gameRenderer.getMainCamera().getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) return;
        var targetState = frame.backend().capturePostProcessingTargetScope();
        try (var ignored = frame.backend().captureRenderStateScope()) {
            if (width != minecraft.getMainRenderTarget().width || height != minecraft.getMainRenderTarget().height) resize();
            chain.setUniform("Intensity", amount * minecraft.options.screenEffectScale().get().floatValue());
            chain.setUniform("Pulse", RiftClientEffects.pulse(partial));
            chain.setUniform("Critical", RiftClientEffects.state() == RiftState.CRITICAL ? 1F : 0F);
            chain.setUniform("Time", (minecraft.level.getGameTime() % 24000 + partial) / 20F);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            chain.process(partial);
        } catch (RuntimeException exception) {
            failed = true;
            close();
            ModConstants.LOGGER.warn("Local rift post-processing failed; disabled until resource reload", exception);
        } finally {
            targetState.close();
        }
    }

    private static void resize() {
        var target = Minecraft.getInstance().getMainRenderTarget();
        width = target.width; height = target.height;
        chain.resize(width, height);
    }

    /** Releases this owner's render targets on disablement, world unload or resource reload. */
    public static void close() {
        PostChain closing = chain;
        chain = null;
        target = null;
        if (closing != null) {
            try { closing.close(); }
            catch (RuntimeException exception) {
                failed = true;
                ModConstants.LOGGER.warn("Local rift render targets could not be released", exception);
            }
        }
    }

    public static String status() {
        if (failed) return "unavailable";
        if (ShaderPackCompatibility.isExternalShaderPackActive()) return "external shader pack";
        return !RiftConfig.POST_PROCESSING.get() ? "off" : chain != null ? "ready" : "idle";
    }
}
