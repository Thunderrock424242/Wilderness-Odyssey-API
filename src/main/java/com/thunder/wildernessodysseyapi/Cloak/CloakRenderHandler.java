package com.thunder.wildernessodysseyapi.Cloak;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class CloakRenderHandler {
    private static TextureTarget cloakRenderTarget;

    public static void init() {
        Minecraft mc = Minecraft.getInstance();
        cloakRenderTarget = new TextureTarget(256, 256, true, false);
    }

    public static void captureBehindView(Minecraft mc, Player player) {
        if (cloakRenderTarget == null || mc.level == null) return;

        // Calculate position 2 blocks behind the player at eye height
        Vec3 behindPosition = player.position()
                .subtract(player.getLookAngle().scale(2))
                .add(0, player.getEyeHeight(), 0);

        // Adjust camera rotation to look in the opposite direction
        float yaw = player.getYRot() + 180.0f;
        float pitch = -player.getXRot();

        // Configure temporary camera
        Camera tempCamera = new Camera();
        tempCamera.setPosition(behindPosition.x, behindPosition.y, behindPosition.z);
        tempCamera.setRotation(yaw, pitch);
        tempCamera.setup(mc.level, player, false, false, mc.getDeltaFrameTime());

        // Setup projection matrix with 1:1 aspect ratio
        float fov = mc.options.fov().get().floatValue();
        Matrix4f projectionMatrix = new Matrix4f().perspective(
                (float) Math.toRadians(fov), 1.0f, 0.05f, 256.0f
        );

        // Get view matrix from the camera
        Matrix4f viewMatrix = tempCamera.getViewMatrix();

        // Prepare framebuffer
        cloakRenderTarget.clear(Minecraft.ON_OSX);
        cloakRenderTarget.bindWrite(true);
        RenderSystem.viewport(0, 0, 256, 256);

        // Render the scene into the framebuffer
        mc.levelRenderer.renderLevel(
                mc.getDeltaFrameTime(),
                true,
                tempCamera,
                mc.gameRenderer,
                mc.gameRenderer.lightTexture(),
                projectionMatrix,
                viewMatrix
        );

        // Reset framebuffer and viewport
        cloakRenderTarget.unbindWrite();
        RenderSystem.viewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    public static void applyCloakTexture(Player player) {
        if (cloakRenderTarget == null) return;

        // Bind the captured texture and set parameters
        RenderSystem.setShaderTexture(0, cloakRenderTarget.getColorTextureId());
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }
}