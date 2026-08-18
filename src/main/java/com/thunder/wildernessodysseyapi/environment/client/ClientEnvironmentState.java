package com.thunder.wildernessodysseyapi.environment.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.environment.network.EnvironmentSyncPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Optional;

/** Client-only holder for the newest server-authored player environment summary. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class ClientEnvironmentState {

    private static EnvironmentSyncPayload current;

    private ClientEnvironmentState() {
    }

    /** Accepts a packet only for the active client dimension. */
    public static void accept(ClientLevel level, EnvironmentSyncPayload payload) {
        if (level != null && payload != null
                && level.dimension().location().equals(payload.dimension())) {
            current = payload;
        }
    }

    /** Returns the current dimension's immutable server summary. */
    public static Optional<EnvironmentSyncPayload> current(ClientLevel level) {
        if (level == null || current == null
                || !level.dimension().location().equals(current.dimension())) {
            return Optional.empty();
        }
        return Optional.of(current);
    }

    /** Clears state before connecting to a different server. */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        current = null;
    }

    /** Clears state immediately when leaving a server. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        current = null;
    }

    /** Clears a summary before a client dimension is replaced. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            current = null;
        }
    }
}
