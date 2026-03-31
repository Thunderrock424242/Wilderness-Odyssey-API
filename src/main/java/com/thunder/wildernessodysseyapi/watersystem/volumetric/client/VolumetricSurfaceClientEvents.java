package com.thunder.wildernessodysseyapi.watersystem.volumetric.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Lifecycle hooks for surface cache maintenance.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class VolumetricSurfaceClientEvents {
    private VolumetricSurfaceClientEvents() {
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        VolumetricSurfaceClientCache.clearAll();
    }
}
