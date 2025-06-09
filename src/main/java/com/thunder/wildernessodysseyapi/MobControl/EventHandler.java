package com.thunder.wildernessodysseyapi.MobControl;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * The type Event handler.
 */
public class EventHandler {

    /**
     * On entity join level.
     *
     * @param event the event
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // Check if the level is a ServerLevel
        if (event.getLevel() instanceof ServerLevel serverWorld) {
            // Check if the entity is a Mob and belongs to the MONSTER category
            if (event.getEntity() instanceof Mob mob && mob.getType().getCategory() == MobCategory.MONSTER) {
                // Get the current day
                int currentDay = MobSpawnAdjuster.getCurrentDay(serverWorld);

                // Calculate the spawn chance
                int spawnChance = MobSpawnAdjuster.calculateSpawnChance(currentDay);

                // Randomly decide if the spawn should be canceled
                if (serverWorld.random.nextInt(100) > spawnChance) {
                    event.setCanceled(true); // Prevent the mob from spawning
                }
            }
        }
    }
}
