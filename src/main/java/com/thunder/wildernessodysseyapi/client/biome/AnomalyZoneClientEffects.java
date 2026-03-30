package com.thunder.wildernessodysseyapi.client.biome;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class AnomalyZoneClientEffects {
    private AnomalyZoneClientEffects() {
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        // Preserve vanilla fog colors in anomaly biomes.
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Preserve vanilla ambience in anomaly biomes.
    }
}
