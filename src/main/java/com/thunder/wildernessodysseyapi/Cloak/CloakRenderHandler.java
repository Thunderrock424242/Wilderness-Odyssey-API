package com.thunder.wildernessodysseyapi.Cloak;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class CloakRenderHandler {
    private static Framebuffer cloakFramebuffer;

    public static void init() {
        Minecraft mc = Minecraft.getInstance();
        cloakFramebuffer = new SimpleFramebuffer(256, 256, true, Minecraft.ON_OSX);
    }

    public static void captureBehindView(Minecraft mc, Player player) {
        if (cloakFramebuffer == null) return;

        // Get the player's position and set the camera behind them
        Vec3 behindPosition = player.position().add(player.getLookAngle().scale(-2));

        cloakFramebuffer.bindWrite(true);
        mc.levelRenderer.renderLevel(1.0f, System.nanoTime(), false, behindPosition);
        cloakFramebuffer.unbindWrite();
    }

    public static void applyCloakTexture(Player player) {
        if (cloakFramebuffer == null) return;

        // Bind the framebuffer texture to the player's cloak
        RenderSystem.setShaderTexture(0, cloakFramebuffer.getColorTextureId());
    }
}
