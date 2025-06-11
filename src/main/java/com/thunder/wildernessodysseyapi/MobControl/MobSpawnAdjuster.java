package com.thunder.wildernessodysseyapi.MobControl;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result;

/**
 * Adjusts hostile‐mob spawns based on the in‐game day,
 * but ignores non‐natural spawns (spawn eggs, dispensers, commands, etc.).
 */
@EventBusSubscriber
public class MobSpawnAdjuster {

    @SubscribeEvent
    public static void onSpawnPlacementCheck(SpawnPlacementCheck event) {
        // 1) Only throttle NATURAL spawns
        if (event.getSpawnType() != MobSpawnType.NATURAL) {
            return;
        }

        // 2) Only for monsters
        EntityType<?> type = event.getEntityType();
        if (type.getCategory() != MobCategory.MONSTER) {
            return;
        }

        // 3) Must be on the server side
        if (!(event.getLevel() instanceof ServerLevel world)) {
            return;
        }

        // 4) Compute day-based spawn chance
        int   day    = getCurrentDay(world);
        double chance = calculateSpawnChance(day);

        // 5) Randomly deny the placement if roll exceeds chance
        if (world.getRandom().nextDouble() > chance) {
            event.setResult(Result.FAIL);
        }
    }

    /** @return Current in‐game day (1‐based). */
    public static int getCurrentDay(ServerLevel world) {
        return (int)(world.getDayTime() / 24000) + 1;
    }

    /**
     * @param currentDay The current day
     * @return Spawn‐chance fraction (0.0–1.0), scaling at 10% per day
     */
    public static double calculateSpawnChance(int currentDay) {
        return Math.min(currentDay * 0.1, 1.0);
    }
}
