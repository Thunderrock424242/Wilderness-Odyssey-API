package com.thunder.wildernessodysseyapi.environment.simulation;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.environment.api.EnvironmentServices;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Owns cleanup for the shared environment's non-persistent regional cache. */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class EnvironmentLifecycleEvents {

    private EnvironmentLifecycleEvents() {
    }

    /** Releases one unloading dimension after all snapshot consumers have stopped. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EnvironmentServices.clear(level);
        }
    }

    /** Releases any remaining weakly keyed entries during server shutdown. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        EnvironmentServices.clearAll();
    }
}
