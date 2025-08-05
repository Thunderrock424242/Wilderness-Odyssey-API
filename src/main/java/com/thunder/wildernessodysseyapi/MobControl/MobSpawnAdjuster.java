package com.thunder.wildernessodysseyapi.MobControl;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;

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
            LOGGER.debug("Ignoring non-natural spawn type: {}", event.getSpawnType());
            return;
        }

        // 2) Only for monsters
        EntityType<?> type = event.getEntityType();
        if (type.getCategory() != MobCategory.MONSTER) {
            LOGGER.debug("Ignoring non-monster entity: {}", type);
            return;
        }

        // 3) Must be on the server side
        if (!(event.getLevel() instanceof ServerLevel world)) {
            LOGGER.debug("Spawn event not on server level for entity: {}", type);
            return;
        }

        // 4) Compute day-based spawn chance
        int day = getCurrentDay(world);
        double chance = calculateSpawnChance(day);
        LOGGER.debug("Evaluating spawn for {} on day {} with chance {}", type, day, chance);

        // 5) Randomly deny the placement if roll exceeds chance
        if (world.getRandom().nextDouble() > chance) {
            LOGGER.debug("Denied spawn for {} due to random roll", type);
            event.setResult(Result.FAIL);
        } else {
            LOGGER.debug("Allowed spawn for {}", type);
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
        double chance = Math.min(currentDay * 0.1, 1.0);
        LOGGER.debug("Calculated spawn chance for day {}: {}", currentDay, chance);
        return chance;
    }
}
