package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.ModPackPatches.client.LoadingStallDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders a custom background during the early loading overlay.
 */
@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {
    private static final ResourceLocation CUSTOM_LOADING_BACKGROUND = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "textures/gui/loading/custom_loading.png");

    @Inject(method = "render", at = @At("HEAD"))
    private void wildernessodysseyapi$drawCustomBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        LoadingStallDetector.recordProgress();

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        // Only provide a backdrop; the vanilla progress bar and text render afterward.
        guiGraphics.pose().pushPose();
        guiGraphics.blit(CUSTOM_LOADING_BACKGROUND, 0, 0, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);
        guiGraphics.pose().popPose();
    }
}
