package com.thunder.wildernessodysseyapi.globalchat;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;

/**
 * Manages per-player opt-in state for the global chat system.
 */
public final class GlobalChatOptIn {

    private static final String TAG_KEY = ModConstants.MOD_ID + ":global_chat_opt_in";

    private GlobalChatOptIn() {
    }

    public static boolean isOptedIn(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        return data.getBoolean(TAG_KEY);
    }

    public static void setOptIn(ServerPlayer player, boolean enabled) {
        CompoundTag data = player.getPersistentData();
        data.putBoolean(TAG_KEY, enabled);
    }
}
