package com.thunder.wildernessodysseyapi.client.codex;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.lorebook.CodexClientState;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class CodexClientEvents {
    private CodexClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!CodexClientState.consumeOpenRequest()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !(minecraft.screen instanceof CodexScreen)) {
            minecraft.setScreen(new CodexScreen());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CodexClientState.clear();
    }
}
