package com.thunder.wildernessodysseyapi.vegetation.client;

import com.thunder.wildernessodysseyapi.dataengine.DataEngineIds;
import com.thunder.wildernessodysseyapi.dataengine.network.DataDeltaHandlerRegistry;
import com.thunder.wildernessodysseyapi.vegetation.network.ReactiveVegetationDataDeltaCodec;
import net.minecraft.client.Minecraft;

/** Client-only bridge from Data Engine batches to the existing vegetation store. */
public final class ReactiveVegetationDataEngineClient {
    private static boolean registered;

    private ReactiveVegetationDataEngineClient() {
    }

    /** Registers the handler during client setup, before play packets can arrive. */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        DataDeltaHandlerRegistry.register(DataEngineIds.REACTIVE_VEGETATION, delta -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                ClientVegetationClimateStore.accept(
                        minecraft.level,
                        ReactiveVegetationDataDeltaCodec.decode(delta)
                );
            }
        });
        registered = true;
    }
}
