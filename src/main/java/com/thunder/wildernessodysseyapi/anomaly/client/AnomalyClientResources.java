package com.thunder.wildernessodysseyapi.anomaly.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Mod-bus resource lifecycle for the independently owned anomaly post chain. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class AnomalyClientResources {
    private AnomalyClientResources() {
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> AnomalyPostEffectController.reload());
    }
}
