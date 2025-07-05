package com.thunder.wildernessodysseyapi.ModPackPatches;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class ClientWorldIconSaver {

    private static boolean shouldCapture = false;

    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event) {
        // Only capture from the client world
        if (!event.getLevel().isClientSide()) return;
        shouldCapture = true;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!shouldCapture) return;
        shouldCapture = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getMainRenderTarget() == null) return;

        int width = mc.getMainRenderTarget().width;
        int height = mc.getMainRenderTarget().height;

        NativeImage image = new NativeImage(width, height, true);
        image.downloadTexture(0, false); // false = don't flip

        NativeImage resized = new NativeImage(64, 64, true);
        image.resizeSubRectTo(0, 0, width, height, resized);
        image.close();

        Path worldPath = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
        File iconFile = worldPath.resolve("icon.png").toFile();

        try {
            resized.writeToFile(iconFile);
        } catch (IOException e) {
            System.err.println("[ClientWorldIconSaver] Failed to save icon.png: " + e.getMessage());
        } finally {
            resized.close();
        }
    }
}