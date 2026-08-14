package com.thunder.wildernessodysseyapi.developmentstudio.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.client.screen.StudioScreen;
import com.thunder.wildernessodysseyapi.developmentstudio.network.OpenStudioPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.network.OpenStudioRequestPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.debug.client.StudioDebugRendererRegistry;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client bridge for F8 requests and safe screen refreshes after server sync. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class StudioClientEvents {
    private StudioClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        StudioDebugRendererRegistry.bootstrapDefaults();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.screen == null && StudioKeyMappings.OPEN_STUDIO.consumeClick()) {
            PacketDistributor.sendToServer(new OpenStudioRequestPayload());
        }

        if (!StudioClientState.consumeOpenRequest()) {
            return;
        }
        OpenStudioPayload snapshot = StudioClientState.snapshot();
        if (snapshot == null || minecraft.player == null) {
            return;
        }
        if (minecraft.screen instanceof StudioScreen studioScreen) {
            studioScreen.applySnapshot(snapshot);
        } else {
            minecraft.setScreen(new StudioScreen(snapshot));
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        StudioClientState.clear();
        StudioDebugRendererRegistry.disableAll();
    }
}
