package com.thunder.wildernessodysseyapi.MobControl;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;

public class SpiderSpawnHandler {

    @SubscribeEvent
    public void onSpiderSpawn(EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof Spider spider) {
            Level world = spider.getLevel();
            BlockPos pos = spider.blockPosition();
            Biome biome = world.getBiome(pos).value();

            // Cancel spawn if not in a cave biome
            if (!BiomeUtils.isCaveBiome(biome)) {
                event.setCanceled(true);
            }
        }
    }
}