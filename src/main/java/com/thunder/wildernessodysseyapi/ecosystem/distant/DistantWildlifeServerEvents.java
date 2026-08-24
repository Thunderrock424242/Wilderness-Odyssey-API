package com.thunder.wildernessodysseyapi.ecosystem.distant;

import com.thunder.wildernessodysseyapi.ecosystem.integration.EcosystemPerformanceIntegration;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemSimulationManager;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickEngine;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Connects distant ecosystem population ownership to NeoForge lifecycle events. */
public final class DistantWildlifeServerEvents {

    private DistantWildlifeServerEvents() {
    }

    /** Runs bounded group and transition work after ordinary server simulation. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Player-driven cell changes own AI restoration and remain immediate.
        // Periodic scans and distant-population work use the bounded engines.
        TickEngine.metrics().time(
                "ecosystem",
                () -> EcosystemSimulationManager.get().tick(event.getServer()),
                event.getServer().getTickCount()
        );
        EcosystemPerformanceIntegration.runFallbackIfDataEngineDisabled(
                event.getServer(),
                event::hasTime
        );
    }

    /** Requests the first full snapshot after login. */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DistantWildlifeManager.get().markPlayerDirty(player);
        }
    }

    /** Requests a dimension-correct replacement snapshot after travel. */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DistantWildlifeManager.get().markPlayerDirty(player);
        }
    }

    /** Requests a fresh snapshot for a recreated player after respawn. */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DistantWildlifeManager.get().markPlayerDirty(player);
        }
    }

    /** Drops the weak synchronization cursor immediately on logout. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DistantWildlifeManager.get().forgetPlayer(player);
        }
    }

    /** Releases transient observation timers while SavedData remains level-owned. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EcosystemSimulationManager.get().unload(level);
            DistantWildlifeManager.get().unload(level);
        }
    }

    /** Clears process-scoped cursors only after the server has saved its worlds. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        EcosystemPerformanceIntegration.shutdown();
        EcosystemSimulationManager.get().shutdown(event.getServer());
        DistantWildlifeManager.get().shutdown();
    }
}
