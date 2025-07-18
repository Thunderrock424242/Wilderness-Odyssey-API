package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.AABB;
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
        if (event.getEntity() instanceof Mob mob) {
            if (mob.getType().getCategory() == MobCategory.MONSTER && STRUCTURE_BOUNDING_BOX.contains(mob.blockPosition().getX(), mob.blockPosition().getY(), mob.blockPosition().getZ())) {
                event.setCanceled(true);
            }
        }
    }
}
