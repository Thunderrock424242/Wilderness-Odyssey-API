package com.thunder.wildernessodysseyapi.structureblock;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Carries the player currently submitting a structure-block action so mixins can report bounded-work diagnostics.
 */
public final class StructureBlockDetectionContext {
    private static final ThreadLocal<ServerPlayer> CURRENT_PLAYER = new ThreadLocal<>();

    private StructureBlockDetectionContext() {
    }

    public static void begin(ServerPlayer player) {
        CURRENT_PLAYER.set(player);
    }

    public static void clear() {
        CURRENT_PLAYER.remove();
    }

    public static void send(String message) {
        ServerPlayer player = CURRENT_PLAYER.get();
        if (player == null) {
            return;
        }
        player.displayClientMessage(Component.literal(message), false);
    }
}
