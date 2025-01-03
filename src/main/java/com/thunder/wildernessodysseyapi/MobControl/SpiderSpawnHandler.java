package com.thunder.wildernessodysseyapi.MobControl;

import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public class SpiderSpawnHandler {

    @SubscribeEvent
    public void onSpiderSpawn(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Spider spider) {
            // Use getCommandSenderWorld to access the world
            Level world = spider.getCommandSenderWorld();
            BlockPos pos = spider.blockPosition();
            Biome biome = world.getBiome(pos).value(); // Adjusted for correct type

            // Cancel spawn if not in a cave biome
            if (!BiomeUtils.isCaveBiome(world.getBiome(pos))) {
                event.setCanceled(true);
            }
        }
    }
}
