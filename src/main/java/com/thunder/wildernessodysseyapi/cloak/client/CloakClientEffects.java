package com.thunder.wildernessodysseyapi.cloak.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.core.ModRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class CloakClientEffects {
    private static final int LOW_OXYGEN_TICKS = 60;

    private CloakClientEffects() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }

        boolean cryoShakes = player.hasEffect(ModRegistries.CRYO_SHAKES_EFFECT);
        boolean echoHypoxia = player.hasEffect(ModRegistries.ECHO_HYPOXIA_EFFECT);
        boolean desynced = player.hasEffect(ModRegistries.DESYNCED_EFFECT);
        boolean cloaked = player.hasEffect(MobEffects.INVISIBILITY);

        if (!cryoShakes && !echoHypoxia && !desynced && !cloaked) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();

        float lowOxygenDarkness = getLowOxygenDarkness(player, cloaked);
        if (lowOxygenDarkness > 0.0F) {
            graphics.fill(0, 0, width, height, argb(lowOxygenDarkness, 0x05000A));
        }

        if (echoHypoxia || desynced) {
            drawTunnelVision(graphics, width, height, desynced ? 0.64F : 0.48F);
        }

        if (cryoShakes || desynced) {
            drawBrightnessPulse(graphics, width, height, minecraft.level == null ? 0L : minecraft.level.getGameTime(), desynced);
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }

        float strength = 0.0F;
        if (player.hasEffect(ModRegistries.CRYO_SHAKES_EFFECT)) {
            strength = 0.9F;
        }
        if (player.hasEffect(ModRegistries.ECHO_HYPOXIA_EFFECT)) {
            strength = Math.max(strength, 1.6F);
        }
        if (player.hasEffect(ModRegistries.DESYNCED_EFFECT)) {
            strength = Math.max(strength, 2.4F);
        }

        if (strength <= 0.0F) {
            return;
        }

        long time = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        float wobble = (Mth.sin(time * 0.77F) + Mth.sin(time * 0.31F + 1.2F)) * strength;
        event.setRoll(event.getRoll() + wobble);
        event.setPitch(event.getPitch() + Mth.sin(time * 0.17F) * strength * 0.18F);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }

        if (player.hasEffect(ModRegistries.DESYNCED_EFFECT)) {
            event.setFOV(event.getFOV() * 0.68D);
        } else if (player.hasEffect(ModRegistries.ECHO_HYPOXIA_EFFECT)) {
            event.setFOV(event.getFOV() * 0.76D);
        }
    }

    private static float getLowOxygenDarkness(Player player, boolean cloaked) {
        if (!cloaked || player.getAirSupply() > LOW_OXYGEN_TICKS) {
            return 0.0F;
        }

        float oxygenMissing = (LOW_OXYGEN_TICKS - player.getAirSupply()) / (float) LOW_OXYGEN_TICKS;
        return Mth.clamp(0.22F + oxygenMissing * 0.35F, 0.0F, 0.57F);
    }

    private static void drawTunnelVision(GuiGraphics graphics, int width, int height, float alpha) {
        int edgeX = Math.max(32, Math.round(width * 0.18F));
        int edgeY = Math.max(24, Math.round(height * 0.18F));
        int color = argb(alpha, 0x020007);

        graphics.fill(0, 0, width, edgeY, color);
        graphics.fill(0, height - edgeY, width, height, color);
        graphics.fill(0, edgeY, edgeX, height - edgeY, color);
        graphics.fill(width - edgeX, edgeY, width, height - edgeY, color);
    }

    private static void drawBrightnessPulse(GuiGraphics graphics, int width, int height, long time, boolean purple) {
        float wave = Mth.sin(time * 0.71F) + Mth.sin(time * 0.19F + 1.7F);
        float alpha = 0.04F + Math.abs(wave) * (purple ? 0.07F : 0.055F);
        int rgb = purple ? 0x7C2BFF : (wave > 0.0F ? 0xFFFFFF : 0x000000);
        graphics.fill(0, 0, width, height, argb(alpha, rgb));
    }

    private static int argb(float alpha, int rgb) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
