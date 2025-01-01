package com.thunder.wildernessodysseyapi.WormHole.client;

import com.thunder.wildernessodysseyapi.WormHole.entities.EntityWormhole;
import com.thunder.wildernessodysseyapi.WormHole.entities.WormholeRenderer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityWormhole.WORMHOLE_ENTITY.get(), WormholeRenderer::new);
    }
}
