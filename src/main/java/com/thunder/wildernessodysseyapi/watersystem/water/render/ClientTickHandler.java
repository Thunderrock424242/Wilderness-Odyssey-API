package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wilderness.water.sph.SPHSimulationManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * ClientTickHandler
 *
 * Advances the client-side SPH simulation on every client tick.
 * The simulation runs ahead of the render tick on a thread pool,
 * so this just feeds the elapsed time as a delta.
 *
 * 1 Minecraft client tick = 1/20 second = 0.05s
 */
@EventBusSubscriber(modid = "wilderness", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientTickHandler {

    private static final float CLIENT_TICK_DELTA = 0.05f; // 20 TPS

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        SPHSimulationManager.get().tickAll(CLIENT_TICK_DELTA);
    }
}
