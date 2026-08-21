package com.thunder.wildernessodysseyapi.faq;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

/** Releases FAQ command session state at its owning player and server lifecycle boundaries. */
@EventBusSubscriber(modid = MOD_ID)
public final class FaqLifecycleEvents {

    private FaqLifecycleEvents() {
    }

    /** Removes a disconnected player's cooldown entry immediately. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FaqCommand.clearCooldown(player.getUUID());
        }
    }

    /** Clears any remaining UUIDs before another integrated server starts in the same JVM. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FaqCommand.clearCooldowns();
    }
}
