package com.thunder.wildernessodysseyapi.ModPackPatches.client;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class DebugOverlayAnimationHandler {
    private DebugOverlayAnimationHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        DebugOverlayAnimator.tick(Minecraft.getInstance());
    }
}
