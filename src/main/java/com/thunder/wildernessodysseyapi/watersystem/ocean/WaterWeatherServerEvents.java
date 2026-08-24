package com.thunder.wildernessodysseyapi.watersystem.ocean;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.integration.WaterPerformanceIntegration;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Advances cross-system water response after the normal weather tick.
 *
 * <p>The lowest event priority intentionally observes the atmosphere after its
 * normal-priority handler has published the current server fields. This class
 * owns no weather state and never changes event registration elsewhere.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class WaterWeatherServerEvents {

    private WaterWeatherServerEvents() {
    }

    /** Preserves synchronous regional maintenance when Data Engine is disabled. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        WaterPerformanceIntegration.runFallbackIfDataEngineDisabled(
                event.getServer(),
                event::hasTime
        );
    }

    /** Clears the unloading dimension's ephemeral wave-response cells. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            OceanSeaStateField.clearLevel(level);
        }
    }
}
