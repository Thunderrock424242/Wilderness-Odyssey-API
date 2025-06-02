package com.thunder.wildernessodysseyapi.WorldVersionChecker.client;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldVersionChecker.client.gui.OutdatedWorldScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "novaapi", value = Dist.CLIENT)
public class ClientWorldLoadHandler {

    @SubscribeEvent
    public static void onWorldLoad(ClientLevelEvent.Load event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = event.getLevel();

        int currentVersion = ModConstants.CURRENT_WORLD_VERSION;
        int worldVersion = WorldVersionManager.getClientWorldVersion(level);

        if (worldVersion < currentVersion && mc.screen == null) {
            mc.execute(() -> mc.setScreen(new OutdatedWorldScreen(() -> {
                // Continue loading — optionally do nothing, or resume game.
            })));
        }
    }
}
