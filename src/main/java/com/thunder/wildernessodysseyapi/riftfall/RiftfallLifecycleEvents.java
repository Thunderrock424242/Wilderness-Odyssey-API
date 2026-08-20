package com.thunder.wildernessodysseyapi.riftfall;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

/**
 * Releases Riftfall's server-owned runtime state at NeoForge lifecycle boundaries.
 *
 * <p>Riftfall exposure is intentionally session state, while phase state is
 * dimension-local runtime state. Neither value should cross a logout, level
 * unload, or integrated-server restart.</p>
 */
@EventBusSubscriber(modid = MOD_ID)
public final class RiftfallLifecycleEvents {

    private RiftfallLifecycleEvents() {
    }

    /** Increments loaded Riftfall entity caps after a server-side entity joins. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RiftfallSystem.onEntityAdded(level, event.getEntity().getType());
        }
    }

    /** Decrements loaded Riftfall entity caps after a server-side entity leaves. */
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RiftfallSystem.onEntityRemoved(level, event.getEntity().getType());
        }
    }

    /** Removes session-only player exposure immediately after logout. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RiftfallSystem.clearPlayer(player);
        }
    }

    /** Removes one dimension's transient phase when its server level unloads. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RiftfallSystem.clearLevel(level);
        }
    }

    /** Removes all remaining runtime state before the owning server is discarded. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RiftfallSystem.clearServer(event.getServer());
    }
}
