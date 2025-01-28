package com.thunder.wildernessodysseyapi.MobControl;

import net.minecraft.server.level.ServerLevel;

/**
 * The type Mob spawn adjuster.
 */
public class MobSpawnAdjuster {

    /**
     * Calculates the current day in the Minecraft world.
     *
     * @param world The server-level world.
     * @return The current day (1-based index).
     */
    public static int getCurrentDay(ServerLevel world) {
        return (int) (world.getDayTime() / 24000); // Convert ticks to days
    }

    /**
     * Calculates the spawn chance based on the current day.
     *
     * @param currentDay The current day in the Minecraft world.
     * @return The spawn chance percentage (0-100).
     */
    public static int calculateSpawnChance(int currentDay) {
        return Math.min(currentDay * 10, 100); // Scale: 10% per day, max at 100%
    }
}
