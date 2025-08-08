package com.thunder.wildernessodysseyapi.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;

/**
 * Utility methods for managing cryo sleep.
 * An external mod can call {@link #wakePlayer(ServerPlayer)} once its
 * cutscene finishes to release the player from the tube.
 */
public class CryoSleepAPI {
    private CryoSleepAPI() {}

    /**
     * Wakes the given player from cryostasis and displays the wake message.
     */
    public static void wakePlayer(ServerPlayer player) {
        player.stopSleeping();
        player.setPose(Pose.STANDING);
        player.displayClientMessage(Component.translatable("message.wildernessodysseyapi.wake_up"), true);
    }
}
