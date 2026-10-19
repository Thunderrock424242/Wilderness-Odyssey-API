package com.thunder.wildernessodysseyapi.anomaly.client;

import com.thunder.wildernessodysseyapi.anomaly.config.AnomalyClientConfig;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.wage.compat.ShaderPackCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Vector3f;

/** Air-only, bounded atmosphere driven by local server conclusions. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class AnomalyZoneClientEffects {
    private static final DustParticleOptions COOL_MOTE = new DustParticleOptions(new Vector3f(0.45F, 0.72F, 0.79F), 0.55F);
    private static final DustParticleOptions RIFT_MOTE = new DustParticleOptions(new Vector3f(0.62F, 0.48F, 0.78F), 0.55F);

    private AnomalyZoneClientEffects() {
    }

    /** Leaves fluid fog and external shader-pack color ownership intact. */
    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!AnomalyClientConfig.fogEnabled() || !AnomalyClientState.controls(minecraft.level)
                || event.getCamera().getFluidInCamera() != FogType.NONE
                || ShaderPackCompatibility.isExternalShaderPackActive()) {
            return;
        }
        float biome = AnomalyClientState.intensity() * AnomalyClientConfig.intensityScale();
        float rift = AnomalyClientState.riftIntensity() * AnomalyClientConfig.intensityScale();
        float blend = AnomalyEffectPolicy.fogBlend(biome, rift);
        float violet = AnomalyEffectPolicy.violetStrength(biome, rift);
        event.setRed(mix(event.getRed(), mix(0.59F, 0.66F, violet), blend));
        event.setGreen(mix(event.getGreen(), mix(0.70F, 0.62F, violet), blend));
        event.setBlue(mix(event.getBlue(), 0.80F, blend));
    }

    /** A small far-plane reduction preserves existing short-distance weather/fluid visibility. */
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!AnomalyEffectPolicy.canAdjustFog(event.getMode(), event.getType())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!AnomalyClientConfig.fogEnabled() || !AnomalyClientState.controls(minecraft.level)
                || ShaderPackCompatibility.isExternalShaderPackActive()) {
            return;
        }
        float strength = AnomalyEffectPolicy.combinedStrength(AnomalyClientState.intensity(),
                AnomalyClientState.riftIntensity()) * AnomalyClientConfig.intensityScale();
        if (strength <= 0.001F) {
            return;
        }
        float far = AnomalyEffectPolicy.fogFarPlane(event.getFarPlaneDistance(), strength);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.9F));
        event.setCanceled(true);
    }

    /** Smooths visual conclusions and emits at most two nearby motes every twelve ticks. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        float accessibility = minecraft.options.screenEffectScale().get().floatValue();
        AnomalyClientConfig.setAccessibilityScale(accessibility);
        Level level = minecraft.level;
        if (level == null) {
            AnomalyClientState.clear();
            AnomalyPostEffectController.release();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        AnomalyClientState.tick(level);
        if (minecraft.player == null || minecraft.options.particles().get() == ParticleStatus.MINIMAL) {
            return;
        }
        float strength = AnomalyEffectPolicy.screenStrength(AnomalyClientState.intensity(),
                AnomalyClientState.riftIntensity(), AnomalyClientConfig.intensityScale(), accessibility);
        int count = AnomalyEffectPolicy.particleCount(AnomalyClientConfig.particlesEnabled(), strength,
                AnomalyClientState.visualTicks());
        RandomSource random = level.getRandom();
        for (int index = 0; index < count; index++) {
            double x = minecraft.player.getX() + random.nextDouble() * 12.0D - 6.0D;
            double y = minecraft.player.getY() + random.nextDouble() * 4.0D;
            double z = minecraft.player.getZ() + random.nextDouble() * 12.0D - 6.0D;
            level.addParticle(AnomalyClientState.riftIntensity() > 0.4F ? RIFT_MOTE : COOL_MOTE,
                    x, y, z, 0.0D, 0.004D, 0.0D);
        }
    }

    /** Runs before the HUD so text and controls keep their normal colors. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        AnomalyPostEffectController.render(event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        clearPresentation();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearPresentation();
    }

    /** Same-dimension respawns can reuse the client level, so level identity is insufficient. */
    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        clearPresentation();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) {
            AnomalyClientState.clearLevel(level);
            AnomalyPostEffectController.release();
        }
    }

    private static void clearPresentation() {
        AnomalyClientState.clear();
        AnomalyPostEffectController.release();
        AnomalyClientConfig.setAccessibilityScale(Minecraft.getInstance().options.screenEffectScale().get().floatValue());
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * AnomalyEffectPolicy.clampUnit(amount);
    }
}
