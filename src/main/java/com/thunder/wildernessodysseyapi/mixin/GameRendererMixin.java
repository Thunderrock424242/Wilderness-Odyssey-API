package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "tryTakeScreenshotIfNeeded", at = @At("TAIL"))
    private void injectTakeScreenshot(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();

        // Only proceed if in singleplayer with access to the world folder
        if (mc.getSingleplayerServer() == null || mc.level == null) return;

        // Get the framebuffer
        RenderTarget framebuffer = mc.getMainRenderTarget();
        int width = framebuffer.width;
        int height = framebuffer.height;

        try {
            NativeImage image = new NativeImage(width, height, true);
            image.downloadTexture(0, false); // read from the framebuffer

            // Resize to 64x64 for world icon
            NativeImage resized = new NativeImage(64, 64, true);
            image.resizeSubRectTo(0, 0, width, height, resized);
            image.close();

            // Save as icon.png to the world folder
            Path worldPath = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            File iconFile = worldPath.resolve("icon.png").toFile();
            resized.writeToFile(iconFile);
            resized.close();

            System.out.println("[WildernessOdysseyAPI] icon.png updated via screenshot hook.");

        } catch (IOException e) {
            System.err.println("[WildernessOdysseyAPI] Failed to save icon.png: " + e.getMessage());
        }
    }
}