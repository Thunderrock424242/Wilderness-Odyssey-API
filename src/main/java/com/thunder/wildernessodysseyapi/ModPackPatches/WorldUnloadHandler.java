/*package com.thunder.wildernessodysseyapi.ModPackPatches;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.io.File;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

@EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
public class WorldUnloadHandler {

    private static boolean alreadyRequestedScreenshot = false;

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;

        if (!(event.getLevel() instanceof ClientLevel clientLevel)) return;
        if (!clientLevel.dimension().equals(Level.OVERWORLD)) return;

        if (alreadyRequestedScreenshot) return;
        alreadyRequestedScreenshot = true;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen != null) return;

        RenderTarget framebuffer = mc.getMainRenderTarget();
        File screenshotsDir = mc.gameDirectory.toPath().resolve("screenshots").toFile();

        // Queue the screenshot on the render thread
        mc.execute(() -> {
            Screenshot.grab(screenshotsDir, null, framebuffer, (Component result) -> {
                System.out.println("[WildernessOdysseyAPI] Screenshot triggered before exit.");
            });
        });
    }
}
*/