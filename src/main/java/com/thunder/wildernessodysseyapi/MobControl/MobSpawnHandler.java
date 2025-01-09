package com.thunder.wildernessodysseyapi.MobControl;

import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

@EventBusSubscriber(modid = "wildernessodysseyapi")
public class MobSpawnHandler {

    @SubscribeEvent
    public static void onMobSpawn(MobSpawnEvent event) {
        // Check if the mob is a Villager or Zombie
        if (event.getEntity().getType() == EntityType.VILLAGER || event.getEntity().getType() == EntityType.ZOMBIE) {
            // Cancel the spawn
            event.setCanceled(true);
            System.out.println("Spawn canceled for: " + event.getEntity().getType());
        }
    }
}
