package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thunder.wildernessodysseyapi.ModPackPatches.util.IconCaptureState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

@Mixin(Minecraft.class)
public class MinecraftPauseMixin {

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (IconCaptureState.iconCaptured) return;
        if (screen == null || !screen.isPauseScreen()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() == null || mc.level == null) return;

        IconCaptureState.iconCaptured = true;

        RenderTarget framebuffer = mc.getMainRenderTarget();
        Path iconPath = mc.getSingleplayerServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("icon.png");

        // Use the correct overload: direct save to icon.png
        Screenshot.grab(iconPath.toFile(), framebuffer, (result) -> {
            System.out.println("[WildernessOdysseyAPI] icon.png updated on pause screen open.");
        });
    }
}