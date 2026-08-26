package com.thunder.wildernessodysseyapi.cinematic;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server event bridge for cinematic ticking, safety validation, and cleanup. */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class CinematicEvents {
    private CinematicEvents() {
    }

    /** Advances only players with active sessions; idle players pay one map lookup. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CinematicManager.tick(player);
        }
    }

    /** Denies block breaking while the authoritative server lock is active. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelInteraction(event);
    }

    /** Denies block use while the authoritative server lock is active. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelInteraction(event);
    }

    /** Denies item use while the authoritative server lock is active. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelInteraction(event);
    }

    /** Denies general entity use while the authoritative server lock is active. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelInteraction(event);
    }

    /** Denies location-specific entity use while the authoritative server lock is active. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        cancelInteraction(event);
    }

    /** Denies entity attacks independently from right-click interaction events. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && CinematicManager.controlsLocked(player)) {
            event.setCanceled(true);
        }
    }

    private static void cancelInteraction(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && CinematicManager.controlsLocked(player)
                && event instanceof ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CinematicManager.stop(player, CinematicStopReason.PLAYER_DIED);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CinematicManager.stop(player, CinematicStopReason.PLAYER_DISCONNECTED);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CinematicManager.stop(player, CinematicStopReason.DIMENSION_CHANGED);
        }
    }

    /** Copies durable progress and ensures no transient lock crosses a player-entity replacement. */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original) {
            CinematicManager.stop(original, CinematicStopReason.PLAYER_DIED);
        }
        CinematicPlayerData.copy(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStopping(ServerStoppingEvent event) {
        CinematicManager.stopAll(event.getServer());
    }
}
