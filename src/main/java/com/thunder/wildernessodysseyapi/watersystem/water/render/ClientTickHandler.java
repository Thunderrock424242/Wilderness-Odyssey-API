package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveAnimator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * ClientTickHandler
 *
 * Advances lightweight client-only water animation state. SPH simulation is
 * driven by the logical server tick because it queries world collision data.
 */
@EventBusSubscriber(modid = "wildernessodysseyapi", value = Dist.CLIENT)
public class ClientTickHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        GerstnerWaveAnimator.update();
    }
}
