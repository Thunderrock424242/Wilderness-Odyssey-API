package com.thunder.wildernessodysseyapi.BunkerStructure;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * The type Mob spawn handler.
 */
public class MobSpawnHandler {
    private static final AABB STRUCTURE_BOUNDING_BOX = new AABB(
            -50, 0, -50, 50, 256, 50 // Replace with actual BunkerStructure coordinates
    );

    /**
     * Instantiates a new Mob spawn handler.
     */
    public MobSpawnHandler() {
        NeoForge.EVENT_BUS.register(this);
    }

    /**
     * On mob spawn.
     *
     * @param event the event
     */
    @SubscribeEvent
    public void onMobSpawn(EntityJoinLevelEvent event) {
        // Check if the entity is a mob instance.
        if (event.getEntity() instanceof Mob mob) {
            //verify if it's a hostile mob.
            if (mob instanceof Monster && STRUCTURE_BOUNDING_BOX.contains(Vec3.atLowerCornerOf(mob.blockPosition()))) {
                // Cancel the spawn event if conditions are met.
                event.setCanceled(true);
            }
        }
    }
}

