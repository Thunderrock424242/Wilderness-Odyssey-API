package com.thunder.wildernessodysseyapi.developmentstudio;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.campus.StudioCampusPlacer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Server lifecycle hooks that initialize Studio identity and campus state. */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class StudioLifecycleEvents {
    private StudioLifecycleEvents() {
    }

    /** Converts the preset's Overworld-equivalent marker into durable world metadata. */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(Level.OVERWORLD)) {
            StudioWorldAccess.initializeFromPreset(level);
        }
    }

    /**
     * Runs after the existing starter-bunker spawn handler, including when that
     * handler canceled vanilla spawn selection, so Studio placement is additive.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        StudioWorldAccess.initializeFromPreset(level);
        StudioCampusPlacer.placeIfNeeded(level);
    }

    /** Retries a missing campus on first authorized world entry after an interrupted creation. */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && StudioWorldAccess.isDevelopmentStudioWorld(player.getServer())) {
            StudioCampusPlacer.placeIfNeeded(player.getServer().overworld());
        }
    }
}
