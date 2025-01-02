package com.thunder.wildernessodysseyapi.MobControl;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;

public class MobSpawnAdjuster {

    public static void adjustMobSpawning(ServerLevel world, int day) {
        // Dynamic adjustment based on day
        int spawnMultiplier = Math.min(day, 10); // Cap the multiplier at 10 for balance
        for (MobCategory category : MobCategory.values()) {
            if (category == MobCategory.MONSTER) {
                world.getChunkSource().getGenerator().getSpawnSettings().getSpawns(category).forEach(spawnData -> {
                    int baseWeight = spawnData.getWeight().get();
                    int newWeight = baseWeight * spawnMultiplier;
                    spawnData.setWeight(newWeight);
                });
            }
        }
    }
}