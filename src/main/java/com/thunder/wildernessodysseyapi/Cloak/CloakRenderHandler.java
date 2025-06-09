package com.thunder.wildernessodysseyapi.Cloak;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class CloakRenderHandler {
    /** Offscreen 256×256 target for rendering “behind‐the‐player.” */
    private static TextureTarget cloakRenderTarget = null;

    /** Call once from ClientSetup to create our TextureTarget. */
    public static void init() {
        cloakRenderTarget = new TextureTarget(256, 256, /*useDepth=*/ true, /*useStencil=*/ false);
    }

    /**
     * Every client tick (Post phase), if the player's “cloakEnabled” NBT is true,
     * move the camera 2 blocks behind the player and render the world into our offscreen texture.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || cloakRenderTarget == null) {
            return;
        }

        // 1) Check “cloakEnabled” in player NBT (default=false if not set).
        boolean cloakEnabled = player.getPersistentData().getBoolean("cloakEnabled");
        if (!cloakEnabled) {
            return;
        }

        // 2) Compute a point exactly 2 blocks behind the player's eye position:
        Vec3 behindPos = player.position()
                .add(player.getLookAngle().scale(-2.0))
                .add(0, player.getEyeHeight(), 0);

        // 3) Grab Minecraft’s main camera (no reflection needed).
        Camera camera = mc.gameRenderer.getMainCamera();

        // 4) Save the camera’s original position & rotation so we can restore them.
        Vec3 origPos = camera.getPosition();
        float origYaw = camera.getYRot();
        float origPitch = camera.getXRot();

        // 5) Move the camera to “behind‐player” and rotate it 180° so it faces forward.
        camera.setPosition(behindPos.x, behindPos.y, behindPos.z);
        camera.setRotation(player.getYRot() + 180.0F, -player.getXRot());

        // 6) Prepare the offscreen buffer, set viewport to 256×256:
        cloakRenderTarget.clear(Minecraft.ON_OSX);
        cloakRenderTarget.bindWrite(true);
        RenderSystem.viewport(0, 0, 256, 256);

        // 7) Get the existing DeltaTracker from Minecraft (via getTimer()):
        DeltaTracker tracker = mc.getTimer();

        // 8) Render the world from the “behind‐player” camera into our offscreen target:
        mc.levelRenderer.renderLevel(
                tracker,
                /*shouldRenderBlockOutline=*/ true,
                camera,
                mc.gameRenderer,
                mc.gameRenderer.lightTexture(),
                new Matrix4f(),  // Let LevelRenderer compute its own projection
                new Matrix4f()   // Let LevelRenderer compute its own view
        );

        // 9) Unbind offscreen and restore main viewport:
        cloakRenderTarget.unbindWrite();
        RenderSystem.viewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());

        // 10) Restore the camera’s original position & rotation:
        camera.setPosition(origPos.x, origPos.y, origPos.z);
        camera.setRotation(origYaw, origPitch);
    }

    /**
     * In your CloakLayer.render(...), call this so that the color texture (just captured)
     * is bound to texture unit 0 with linear filtering and clamp‐to‐edge.
     */
    public static void applyCloakTexture() {
        if (cloakRenderTarget == null) {
            return;
        }
        RenderSystem.setShaderTexture(0, cloakRenderTarget.getColorTextureId());
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }
}
