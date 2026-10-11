package com.thunder.wildernessodysseyapi.temporalrift.client.hybrid;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Client mod-bus resource lifecycle; gameplay-bus handlers remain in RiftPostProcessing. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class RiftClientResources {
    private RiftClientResources() { }

    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) RiftPostProcessing::reload);
    }
}
