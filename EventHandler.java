package com.thunder.wildernessodysseyapi.MobControl;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Mob;
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
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // Check if the level is a ServerLevel
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            // Check if the entity is a Mob and is in the monster tag
            if (event.getEntity() instanceof Mob mob &&
                    mob.getType().is(EntityTypeTags.ZOMBIES)) {  // Uses built-in monster tag
                // Get the current day
                int currentDay = MobSpawnAdjuster.getCurrentDay(serverLevel);

                // Calculate the spawn chance
                int spawnChance = MobSpawnAdjuster.calculateSpawnChance(currentDay);

                // Randomly decide if the spawn should be canceled
                if (serverLevel.random.nextInt(100) > spawnChance) {
                    event.setCanceled(true); // Prevent the mob from spawning
                }
            }
        }
    }
}
