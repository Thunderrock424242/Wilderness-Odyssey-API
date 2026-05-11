package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * ServerTickHandler
 *
 * Advances the server-side SPH simulation each server tick.
 * The server simulation is what ultimately places real water blocks
 * when the fluid settles.
 */
@EventBusSubscriber(modid = "wildernessodysseyapi", bus = EventBusSubscriber.Bus.GAME)
public class ServerTickHandler {

    private static final float SERVER_TICK_DELTA = 0.05f;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer() == null) return;

        for (var level : event.getServer().getAllLevels()) {
            SPHSimulationManager.get().tickLevel(level, SERVER_TICK_DELTA);
        }
    }
}
