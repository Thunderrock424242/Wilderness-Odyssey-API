package com.thunder.wildernessodysseyapi.ocean.client;

import com.thunder.wildernessodysseyapi.ocean.events.WaterSystem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public class ClientTickHandler {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        WaterSystem.tick(1.0f / 20.0f);
    }
}