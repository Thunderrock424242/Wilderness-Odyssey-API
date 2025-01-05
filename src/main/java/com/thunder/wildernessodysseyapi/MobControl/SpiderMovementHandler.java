package com.thunder.wildernessodysseyapi.MobControl;

import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public class SpiderMovementHandler {

    @SubscribeEvent
    public void onSpiderMove(EntityTickEvent.Pre event) { // Use Pre phase here
        if (event.getEntity() instanceof Spider spider) {
            Level world = spider.getCommandSenderWorld();
            BlockPos pos = spider.blockPosition();
            Biome biome = world.getBiome(pos).value(); // Retrieve the biome directly

            // Check if the biome is a cave biome
            if (!BiomeUtils.isCaveBiome(world.getBiome(pos))) {
                // Teleport spider back if it tries to leave a cave
                spider.teleportTo(pos.getX(), pos.getY() - 1, pos.getZ());
            }
        }
    }
}
