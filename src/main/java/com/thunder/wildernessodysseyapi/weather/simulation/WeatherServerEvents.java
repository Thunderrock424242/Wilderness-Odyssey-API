package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.integration.WeatherPerformanceIntegration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Connects the server-owned atmosphere authority to NeoForge lifecycle events.
 *
 * <p>The event class is annotation-registered exactly once. All live level,
 * player, biome, chunk, and water reads therefore remain on the server thread.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class WeatherServerEvents {

    private WeatherServerEvents() {
    }

    /** Advances world effects and the disabled-Data-Engine fallback path. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        WeatherPerformanceIntegration.runFallbackIfDataEngineDisabled(
                event.getServer(),
                event::hasTime
        );
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!event.hasTime()) {
                break;
            }
            WeatherAuthority.get().tickWorldEffects(level, event::hasTime);
        }
    }

    /** Marks login state for a complete authoritative regional snapshot. */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WeatherAuthority.get().markPlayerDirty(player);
        }
    }

    /** Resynchronizes after the player's active dimension identity changes. */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WeatherAuthority.get().markPlayerDirty(player);
        }
    }

    /** Resynchronizes after respawn creates or rebinds the server player. */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WeatherAuthority.get().markPlayerDirty(player);
        }
    }

    /** Releases bounded per-player revision state on disconnect. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WeatherAuthority.get().forgetPlayer(player);
        }
    }

    /** Clears only the unloading dimension's non-persistent sampler caches. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            WeatherPerformanceIntegration.forgetLevel(level);
            WeatherAuthority.get().unload(level);
        }
    }

    /** Releases process-scoped network and cache state after worlds have saved. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        WeatherPerformanceIntegration.shutdown();
        WeatherAuthority.get().shutdown();
    }
}
