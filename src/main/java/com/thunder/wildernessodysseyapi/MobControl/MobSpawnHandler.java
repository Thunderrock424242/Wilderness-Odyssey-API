package com.thunder.wildernessodysseyapi.MobControl;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;

public class MobSpawnHandler {
    private int currentDay = 0;

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.world instanceof ServerLevel serverWorld) {
            long dayTime = serverWorld.getDayTime() / 24000; // Convert ticks to days
            if (dayTime > currentDay) {
                currentDay = (int) dayTime;
                MobSpawnAdjuster.adjustMobSpawning(serverWorld, currentDay);
            }
        }
    }
}
