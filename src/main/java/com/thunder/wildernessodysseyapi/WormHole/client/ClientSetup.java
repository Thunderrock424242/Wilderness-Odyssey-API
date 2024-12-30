package com.thunder.wildernessodysseyapi.WormHole.client;

import com.thunder.wildernessodysseyapi.WormHole.entities.EntityWormhole;
import com.thunder.wildernessodysseyapi.WormHole.entities.WormholeRenderer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class ClientSetup {
    @SubscribeEvent
    public void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityWormhole.WORMHOLE_ENTITY, WormholeRenderer::new);
    }
}