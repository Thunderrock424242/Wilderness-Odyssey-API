package com.thunder.wildernessodysseyapi.MobControl;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;

public class SpiderMovementHandler {

    @SubscribeEvent
    public void onSpiderMove(LivingEvent.LivingUpdateEvent event) {
        if (event.getEntity() instanceof Spider spider) {
            Level world = spider.getLevel();
            BlockPos pos = spider.blockPosition();
            Biome biome = world.getBiome(pos).value();

            // Teleport spider back if it tries to leave a cave
            if (!BiomeUtils.isCaveBiome(biome)) {
                spider.teleportTo(pos.getX(), pos.getY() - 1, pos.getZ());
            }
        }
    }
}