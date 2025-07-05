package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
@OnlyIn(Dist.CLIENT)
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Unique
    private static boolean wilderness_Odyssey_API$iconCaptured = false;

    @Inject(method = "tryTakeScreenshotIfNeeded", at = @At("TAIL"))
    private void onScreenshotTaken(CallbackInfo ci) {
        if (wilderness_Odyssey_API$iconCaptured) return; // Prevent multiple saves
        wilderness_Odyssey_API$iconCaptured = true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() == null || mc.level == null) return;

        RenderTarget framebuffer = mc.getMainRenderTarget();
        int width = framebuffer.width;
        int height = framebuffer.height;

        try {
            NativeImage image = new NativeImage(width, height, true);
            image.downloadTexture(0, false); // Capture the framebuffer

            NativeImage resized = new NativeImage(64, 64, true);
            image.resizeSubRectTo(0, 0, width, height, resized);
            image.close();

            Path worldPath = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            File iconFile = worldPath.resolve("icon.png").toFile();
            resized.writeToFile(iconFile);
            resized.close();

            System.out.println("[WildernessOdysseyAPI] icon.png updated successfully.");

        } catch (IOException e) {
            System.err.println("[WildernessOdysseyAPI] Failed to save icon.png: " + e.getMessage());
        }
    }
}