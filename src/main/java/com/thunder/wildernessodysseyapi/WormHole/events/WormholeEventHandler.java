package com.thunder.wildernessodysseyapi.WormHole.events;

import com.thunder.wildernessodysseyapi.WormHole.entities.EntityWormhole;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;

public class WormholeEventHandler {
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        Level world = (Level) event.world;
        if (!world.isClientSide && world.random.nextInt(1000) < 10) { // 1% chance per tick
            BlockPos randomPos = new BlockPos(
                    world.random.nextInt(10000) - 5000,
                    world.random.nextInt(256),
                    world.random.nextInt(10000) - 5000
            );
            EntityWormhole wormhole = new EntityWormhole(EntityWormhole.WORMHOLE_ENTITY, world);
            wormhole.setPos(randomPos.getX(), randomPos.getY(), randomPos.getZ());
            world.addFreshEntity(wormhole);
        }
    }
}
