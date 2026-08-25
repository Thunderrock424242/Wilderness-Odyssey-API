package com.thunder.wildernessodysseyapi.ecosystem.debug.map.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.client.screen.StudioScreen;
import com.thunder.wildernessodysseyapi.ecosystem.debug.map.EcosystemDebugMapPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opens and refreshes the map on the client thread after its snapshot arrives. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class EcosystemDebugMapClientEvents {
    private EcosystemDebugMapClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!EcosystemDebugMapClientState.consumeOpenRequest()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        EcosystemDebugMapPayload snapshot = EcosystemDebugMapClientState.snapshot();
        if (minecraft.player == null || snapshot == null) {
            return;
        }
        if (minecraft.screen instanceof EcosystemDebugMapScreen screen) {
            screen.applySnapshot(snapshot);
        } else {
            net.minecraft.client.gui.screens.Screen parent = minecraft.screen instanceof StudioScreen
                    ? minecraft.screen
                    : null;
            minecraft.setScreen(new EcosystemDebugMapScreen(snapshot, parent));
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        EcosystemDebugMapClientState.clear();
    }
}
