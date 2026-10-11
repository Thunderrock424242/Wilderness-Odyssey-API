package com.thunder.wildernessodysseyapi.diagnostics.performance;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import com.thunder.wildernessodysseyapi.saving.coordinator.SavingCoordinator;
import net.minecraft.server.level.ServerLevel;

/** Logical-server observation hooks, registered once by the existing mod entrypoint. */
public final class PerformanceDiagnosticEvents {
    private PerformanceDiagnosticEvents() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void aboutToStart(ServerAboutToStartEvent event) { PerformanceDiagnostics.start(event.getServer()); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void starting(ServerStartingEvent event) { PerformanceDiagnostics.milestone(event.getServer(), "entry/about_to_starting"); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void started(ServerStartedEvent event) {
        PerformanceDiagnostics.milestone(event.getServer(), "entry/about_to_started");
        PerformanceDiagnostics.serverReady(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stopping(ServerStoppingEvent event) { PerformanceDiagnostics.stopping(event.getServer()); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopped(ServerStoppedEvent event) { PerformanceDiagnostics.stopped(event.getServer()); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preTick(ServerTickEvent.Pre event) { PerformanceDiagnostics.beginTick(event.getServer()); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void postTick(ServerTickEvent.Post event) { PerformanceDiagnostics.endTick(event.getServer(), event::hasTime); }

    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event) { SavingCoordinator.loaded(event.getChunk()); }
    @SubscribeEvent public static void chunkUnloaded(ChunkEvent.Unload event) { SavingCoordinator.unloaded(event.getChunk()); }
    @SubscribeEvent public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) SavingCoordinator.unloadLevel(level);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PerformanceDiagnostics.playerLoggedIn(player.getServer());
    }
}
